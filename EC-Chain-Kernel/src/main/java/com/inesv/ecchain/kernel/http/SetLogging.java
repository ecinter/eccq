package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class SetLogging extends APIRequestHandler {

    static final SetLogging instance = new SetLogging();

    private static final JSONStreamAware EC_LOGGING_UPDATED;

    private static final JSONStreamAware EC_INCORRECT_LEVEL =
            JSONResponses.incorrect("logLevel", "Log level must be DEBUG, INFO, WARN or ERROR");

    private static final JSONStreamAware EC_INCORRECT_EVENT =
            JSONResponses.incorrect("communicationEvent",
                    "Communication event must be EXCEPTION, HTTP-ERROR or HTTP-OK");

    static {
        JSONObject response = new JSONObject();
        response.put("loggingUpdated", true);
        EC_LOGGING_UPDATED = JSON.prepare(response);
    }


    private SetLogging() {
        super(new APITag[]{APITag.DEBUG}, "logLevel", "communicationEvent", "communicationEvent", "communicationEvent");
    }


    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONStreamAware response = null;
        //
        // Get the log level
        //
        String value = req.getParameter("logLevel");
        if (value != null) {
            switch (value) {
                case "DEBUG":
                    LoggerUtil.setLevel(LoggerUtil.Level.DEBUG);
                    break;
                case "INFO":
                    LoggerUtil.setLevel(LoggerUtil.Level.INFO);
                    break;
                case "WARN":
                    LoggerUtil.setLevel(LoggerUtil.Level.WARN);
                    break;
                case "ERROR":
                    LoggerUtil.setLevel(LoggerUtil.Level.ERROR);
                    break;
                default:
                    response = EC_INCORRECT_LEVEL;
            }
        } else {
            LoggerUtil.setLevel(LoggerUtil.Level.INFO);
        }
        //
        // Get the communication events
        //
        if (response == null) {
            String[] events = req.getParameterValues("communicationEvent");
            if (!Peers.setCommunicationLoggingMask(events))
                response = EC_INCORRECT_EVENT;
        }
        //
        // Return the response
        //
        if (response == null)
            response = EC_LOGGING_UPDATED;
        return response;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
