package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.core.EcBlockchain;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.*;


public final class APIServlet extends HttpServlet {

    static final Map<String, APIRequestHandler> API_REQUEST_HANDLERS;

    static final Map<String, APIRequestHandler> DISABLED_REQUEST_HANDLERS;

    static {

        Map<String, APIRequestHandler> map = new HashMap<>();
        Map<String, APIRequestHandler> disabledMap = new HashMap<>();

        for (APIEnum api : APIEnum.values()) {
            if (!api.getAPIEnumName().isEmpty() && api.getAPIHandler() != null) {
                map.put(api.getAPIEnumName(), api.getAPIHandler());
            }
        }

        PlugIns.registerAPIRequestHandlers(map);

        API.disabledAPIs.forEach(api -> {
            APIRequestHandler handler = map.remove(api);
            if (handler == null) {
                throw new RuntimeException("Invalid API in ec.disabledAPIs: " + api);
            }
            disabledMap.put(api, handler);
        });
        API.disabledAPITags.forEach(apiTag -> {
            Iterator<Map.Entry<String, APIRequestHandler>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, APIRequestHandler> entry = iterator.next();
                if (entry.getValue().getAPITags().contains(apiTag)) {
                    disabledMap.put(entry.getKey(), entry.getValue());
                    iterator.remove();
                }
            }
        });
        if (!API.disabledAPIs.isEmpty()) {
            LoggerUtil.logInfo("Disabled APIs: " + API.disabledAPIs);
        }
        if (!API.disabledAPITags.isEmpty()) {
            LoggerUtil.logInfo("Disabled APITags: " + API.disabledAPITags);
        }

        API_REQUEST_HANDLERS = Collections.unmodifiableMap(map);
        DISABLED_REQUEST_HANDLERS = disabledMap.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(disabledMap);
    }

    public static APIRequestHandler getAPIServletRequestHandler(String requestType) {
        return API_REQUEST_HANDLERS.get(requestType);
    }

    static void initClass() {
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        apiServletProcess(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        apiServletProcess(req, resp);
    }

    private void apiServletProcess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Set response values now in case we create an asynchronous context
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType("text/plain; charset=UTF-8");

        JSONStreamAware response = JSON.EMPTY_JSON;
        long startTime = System.currentTimeMillis();

        try {

            if (!API.isAllowed(req.getRemoteHost())) {
                response = JSONResponses.ERROR_NOT_ALLOWED;
                return;
            }

            String requestType = req.getParameter("requestType");
            if (requestType == null) {
                response = JSONResponses.ERROR_INCORRECT_REQUEST;
                return;
            }

            APIRequestHandler apiRequestHandler = API_REQUEST_HANDLERS.get(requestType);
            if (apiRequestHandler == null) {
                if (DISABLED_REQUEST_HANDLERS.containsKey(requestType)) {
                    response = JSONResponses.ERROR_DISABLED;
                } else {
                    response = JSONResponses.ERROR_INCORRECT_REQUEST;
                }
                return;
            }

            if (Constants.IS_LIGHT_CLIENT && apiRequestHandler.requireFullClient()) {
                response = JSONResponses.LIGHT_CLIENT_DISABLED_API;
                return;
            }

            if (Constants.API_SERVLET_ENFORCE_POST && apiRequestHandler.requirePost() && !"POST".equals(req.getMethod())) {
                response = JSONResponses.POST_REQUIRED;
                return;
            }

            if (apiRequestHandler.requirePassword()) {
                API.verifyPassword(req);
            }
            final long requireBlockId = apiRequestHandler.allowRequiredBlockParameters() ?
                    ParameterParser.getUnsignedLong(req, "requireBlock", false) : 0;
            final long requireLastBlockId = apiRequestHandler.allowRequiredBlockParameters() ?
                    ParameterParser.getUnsignedLong(req, "requireLastBlock", false) : 0;
            if (requireBlockId != 0 || requireLastBlockId != 0) {
                EcBlockchainImpl.getInstance().readECLock();
            }
            try {
                try {
                    if (apiRequestHandler.startDbTransaction()) {
                        H2.H2.beginTransaction();
                    }
                    if (requireBlockId != 0 && !EcBlockchainImpl.getInstance().hasBlock(requireBlockId)) {
                        response = JSONResponses.REQUIRED_BLOCK_NOT_FOUND;
                        return;
                    }
                    EcBlockchain bc = EcBlockchainImpl.getInstance();
                    if (requireLastBlockId != 0 && requireLastBlockId != bc.getLastECBlock().getECId()) {
                        response = JSONResponses.REQUIRED_LAST_BLOCK_NOT_FOUND;
                        return;
                    }
                    response = apiRequestHandler.processRequest(req, resp);
                    if (requireLastBlockId == 0 && requireBlockId != 0 && response instanceof JSONObject) {
                        ((JSONObject) response).put("lastBlock", bc.getLastECBlock().getStringECId());
                    }
                } finally {
                    if (apiRequestHandler.startDbTransaction()) {
                        H2.H2.endTransaction();
                    }
                }
            } finally {
                if (requireBlockId != 0 || requireLastBlockId != 0) {
                    EcBlockchainImpl.getInstance().readECUnlock();
                }
            }
        } catch (ParameterException e) {
            response = e.getErrorResponse();
        } catch (EcException | RuntimeException e) {
            LoggerUtil.logError("Error processing API request", e);
            JSONObject json = new JSONObject();
            JSONData.putException(json, e);
            response = JSON.prepare(json);
        } catch (ExceptionInInitializerError err) {
            LoggerUtil.logError("Initialization Error", err.getCause());
            response = JSONResponses.ERROR_INCORRECT_REQUEST;
        } catch (Exception e) {
            LoggerUtil.logError("Error processing request", e);
            response = JSONResponses.ERROR_INCORRECT_REQUEST;
        } finally {
            // The response will be null if we created an asynchronous context
            if (response != null) {
                if (response instanceof JSONObject) {
                    ((JSONObject) response).put("requestProcessingTime", System.currentTimeMillis() - startTime);
                }
                try (Writer writer = resp.getWriter()) {
                    JSON.writeECJSONString(response, writer);
                }
            }
        }

    }

}
