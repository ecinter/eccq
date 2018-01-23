package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.inesv.ecchain.wallet.core.JSONResponse.*;


public final class UserServlet extends HttpServlet {

    private static final Map<String, UserRequestHandler> USER_REQUEST_HANDLERS;

    static {
        Map<String, UserRequestHandler> map = new HashMap<>();
        map.put("generateAuthorizationToken", CreateAuthorizationToken.instance);
        map.put("getInitialData", GetInitlData.instance);
        map.put("getNewData", GetNewData.instance);
        map.put("lockAccount", LockForAccount.instance);
        map.put("removeActivePeer", DelActivePeer.instance);
        map.put("removeBlacklistedPeer", DelBlacklistedPeer.instance);
        map.put("removeKnownPeer", DelKnownPeer.instance);
        map.put("sendMoney", SendMoney.instance);
        map.put("unlockAccount", UnlockForAccount.instance);
        USER_REQUEST_HANDLERS = Collections.unmodifiableMap(map);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        UserServletprocess(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        UserServletprocess(req, resp);
    }


    private void UserServletprocess(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        User user = null;

        try {

            String userPasscode = req.getParameter("user");
            if (userPasscode == null) {
                return;
            }
            user = Users.getEcUser(userPasscode);

            if (Users.allowedUsersHosts != null && !Users.allowedUsersHosts.contains(req.getRemoteHost())) {
                user.enqueue(EC_DENY_ACCESS);
                return;
            }

            String requestType = req.getParameter("requestType");
            if (requestType == null) {
                user.enqueue(EC_INCORRECT_REQUEST);
                return;
            }

            UserRequestHandler userRequestHandler = USER_REQUEST_HANDLERS.get(requestType);
            if (userRequestHandler == null) {
                user.enqueue(EC_INCORRECT_REQUEST);
                return;
            }

            if (Constants.ENFORCE_POST && userRequestHandler.requirePost() && !"POST".equals(req.getMethod())) {
                user.enqueue(EC_POST_REQUIRED);
                return;
            }

            JSONStreamAware response = userRequestHandler.processRequest(req, user);
            if (response != null) {
                user.enqueue(response);
            }

        } catch (RuntimeException | EcException e) {

            LoggerUtil.logError("Error processing GET request", e);
            if (user != null) {
                JSONObject response = new JSONObject();
                response.put("response", "showMessage");
                response.put("message", e.toString());
                user.enqueue(response);
            }

        } finally {

            if (user != null) {
                user.processPendingResponses(req, resp);
            }

        }

    }

}
