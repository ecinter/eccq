package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.peer.Peer;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.proxy.AsyncMiddleManServlet;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

public final class APIProxyServlet extends AsyncMiddleManServlet {

    private static final String REMOTE_URL = APIProxyServlet.class.getName() + ".remoteUrl";
    private static final String REMOTE_SERVER_IDLE_TIMEOUT = APIProxyServlet.class.getName() + ".remoteServerIdleTimeout";

    static void initClass() {
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        config.getServletContext().setAttribute("apiServlet", new APIServlet());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONStreamAware responseJson = null;
        try {
            if (!API.isAllowed(request.getRemoteHost())) {
                responseJson = JSONResponses.ERROR_NOT_ALLOWED;
                return;
            }
            MultiMap<String> parameters = getEcRequestParameters(request);
            String requestType = getEcRequestType(parameters);
            if (APIProxy.isActivated() && isForwardable(requestType)) {
                if (parameters.containsKey("secretPhrase") || parameters.containsKey("adminPassword") || parameters.containsKey("sharedKey")) {
                    throw new ParameterException(JSONResponses.PROXY_SECRET_DATA_DETECTED);
                }
                if (!initRemotesRequest(request, requestType)) {
                    responseJson = JSONResponses.API_PROXY_NO_OPEN_API_PEERS;
                } else {
                    super.service(request, response);
                }
            } else {
                APIServlet apiServlet = (APIServlet) request.getServletContext().getAttribute("apiServlet");
                apiServlet.service(request, response);
            }
        } catch (ParameterException e) {
            responseJson = e.getErrorResponse();
        } finally {
            if (responseJson != null) {
                try {
                    try (Writer writer = response.getWriter()) {
                        JSON.writeECJSONString(responseJson, writer);
                    }
                } catch (IOException e) {
                    LoggerUtil.logError("Failed to write response to client", e);
                }
            }
        }
    }

    @Override
    protected void addProxyHeaders(HttpServletRequest clientRequest, Request proxyRequest) {

    }

    @Override
    protected HttpClient newHttpClient() {
        return HttpClientFactory.newHttpClient();
    }

    @Override
    protected String rewriteTarget(HttpServletRequest clientRequest) {

        Integer timeout = (Integer) clientRequest.getAttribute(REMOTE_SERVER_IDLE_TIMEOUT);
        HttpClient httpClient = getHttpClient();
        if (timeout != null && httpClient != null) {
            httpClient.setIdleTimeout(Math.max(timeout - Constants.PROXY_IDLE_TIMEOUT_DELTA, 0));
        }

        String remoteUrl = (String) clientRequest.getAttribute(REMOTE_URL);
        URI rewrittenURI = URI.create(remoteUrl).normalize();
        return rewrittenURI.toString();
    }

    @Override
    protected void onClientRequestFailure(HttpServletRequest clientRequest, Request proxyRequest,
                                          HttpServletResponse proxyResponse, Throwable failure) {
        if (failure instanceof PasswordDetectedException) {
            PasswordDetectedException passwordDetectedException = (PasswordDetectedException) failure;
            try (Writer writer = proxyResponse.getWriter()) {
                JSON.writeECJSONString(passwordDetectedException.errorResponse, writer);
                sendProxyResponseError(clientRequest, proxyResponse, HttpStatus.OK_200);
            } catch (IOException e) {
                e.addSuppressed(failure);
                super.onClientRequestFailure(clientRequest, proxyRequest, proxyResponse, e);
            }
        } else {
            super.onClientRequestFailure(clientRequest, proxyRequest, proxyResponse, failure);
        }
    }

    @Override
    protected Response.Listener newProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
        return new APIProxyResponseListener(request, response);
    }

    @Override
    protected ContentTransformer newClientRequestContentTransformer(HttpServletRequest clientRequest, Request proxyRequest) {
        String contentType = clientRequest.getContentType();
        if (contentType != null && contentType.contains("multipart")) {
            return super.newClientRequestContentTransformer(clientRequest, proxyRequest);
        } else {
            if (APIProxy.isActivated() && isForwardable(clientRequest.getParameter("requestType"))) {
                return new PasswordFilteringContentTransformer();
            } else {
                return super.newClientRequestContentTransformer(clientRequest, proxyRequest);
            }
        }
    }

    private String getEcRequestType(MultiMap<String> parameters) throws ParameterException {
        String requestType = parameters.getString("requestType");
        if (Convert.emptyToNull(requestType) == null) {
            throw new ParameterException(JSONResponses.PROXY_MISSING_REQUEST_TYPE);
        }

        APIRequestHandler apiRequestHandler = APIServlet.API_REQUEST_HANDLERS.get(requestType);
        if (apiRequestHandler == null) {
            if (APIServlet.DISABLED_REQUEST_HANDLERS.containsKey(requestType)) {
                throw new ParameterException(JSONResponses.ERROR_DISABLED);
            } else {
                throw new ParameterException(JSONResponses.ERROR_INCORRECT_REQUEST);
            }
        }
        return requestType;
    }

    private boolean initRemotesRequest(HttpServletRequest clientRequest, String requestType) {
        StringBuilder uri;
        if (!Constants.FORCED_SERVER_URL.isEmpty()) {
            uri = new StringBuilder();
            uri.append(Constants.FORCED_SERVER_URL);
        } else {
            Peer servingPeer = APIProxy.getInstance().getEcServingPeer(requestType);
            if (servingPeer == null) {
                return false;
            }
            uri = servingPeer.getPeerApiUri();
            clientRequest.setAttribute(REMOTE_SERVER_IDLE_TIMEOUT, servingPeer.getApiServerIdleTimeout());
        }
        uri.append("/ec");
        String query = clientRequest.getQueryString();
        if (query != null) {
            uri.append("?").append(query);
        }
        clientRequest.setAttribute(REMOTE_URL, uri.toString());
        return true;
    }

    private boolean isForwardable(String requestType) {
        APIRequestHandler apiRequestHandler = APIServlet.API_REQUEST_HANDLERS.get(requestType);
        if (!apiRequestHandler.requireBlockchain()) {
            return false;
        }
        if (apiRequestHandler.requireFullClient()) {
            return false;
        }
        if (APIProxy.NOT_FORWARDED_REQUESTS.contains(requestType)) {
            return false;
        }

        return true;
    }

    private MultiMap<String> getEcRequestParameters(HttpServletRequest request) {
        MultiMap<String> parameters = new MultiMap<>();
        String queryString = request.getQueryString();
        if (queryString != null) {
            UrlEncoded.decodeUtf8To(queryString, parameters);
        }
        return parameters;
    }

    private static class PasswordDetectedException extends RuntimeException {
        private final JSONStreamAware errorResponse;

        private PasswordDetectedException(JSONStreamAware errorResponse) {
            this.errorResponse = errorResponse;
        }
    }

    private static class PasswordFilteringContentTransformer implements ContentTransformer {

        ByteArrayOutputStream os;

        @Override
        public void transform(ByteBuffer input, boolean finished, List<ByteBuffer> output) throws IOException {
            if (finished) {
                ByteBuffer allInput;
                if (os == null) {
                    allInput = input;
                } else {
                    byte[] b = new byte[input.remaining()];
                    input.get(b);
                    os.write(b);
                    allInput = ByteBuffer.wrap(os.toByteArray());
                }
                int tokenPos = PasswordFinder.process(allInput, new String[]{"secretPhrase=", "adminPassword=", "sharedKey="});
                if (tokenPos >= 0) {
                    JSONStreamAware error = JSONResponses.PROXY_SECRET_DATA_DETECTED;
                    throw new PasswordDetectedException(error);
                }
                output.add(allInput);
            } else {
                if (os == null) {
                    os = new ByteArrayOutputStream();
                }
                byte[] b = new byte[input.remaining()];
                input.get(b);
                os.write(b);
            }
        }
    }

    private class APIProxyResponseListener extends ProxyResponseListener {

        APIProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
            super(request, response);
        }

        @Override
        public void onFailure(Response response, Throwable failure) {
            super.onFailure(response, failure);
            LoggerUtil.logError("proxy failed", failure);
            APIProxy.getInstance().blacklistHost(response.getRequest().getHost());
        }
    }
}