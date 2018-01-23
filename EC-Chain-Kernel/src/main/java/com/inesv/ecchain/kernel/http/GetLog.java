package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.MemoryProcessor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.logging.Handler;
import java.util.logging.Logger;


public final class GetLog extends APIRequestHandler {


    static final GetLog instance = new GetLog();


    private GetLog() {
        super(new APITag[]{APITag.DEBUG}, "count");
    }


    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        //
        // Get the number of log messages to return
        //
        int count;
        String value = req.getParameter("count");
        if (value != null)
            count = Math.max(Integer.valueOf(value), 0);
        else
            count = Integer.MAX_VALUE;
        //
        // Get the log messages
        //
        JSONArray logJSON = new JSONArray();
        Logger logger = Logger.getLogger("");
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof MemoryProcessor) {
                logJSON.addAll(((MemoryProcessor) handler).getMessages(count));
                break;
            }
        }
        //
        // Return the response
        //
        JSONObject response = new JSONObject();
        response.put("messages", logJSON);
        return response;
    }

    @Override
    protected boolean requirePassword() {
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
