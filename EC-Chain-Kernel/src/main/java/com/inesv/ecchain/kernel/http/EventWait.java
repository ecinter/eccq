package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public class EventWait extends APIRequestHandler {

    static final EventWait instance = new EventWait();

    private static final JSONObject INCORRECT_TIMEOUT = new JSONObject();

    private static final JSONObject NO_EVENTS_REGISTERED = new JSONObject();

    static {
        INCORRECT_TIMEOUT.put("errorCode", 4);
        INCORRECT_TIMEOUT.put("errorDescription", "Wait timeout is not valid");
        NO_EVENTS_REGISTERED.put("errorCode", 8);
        NO_EVENTS_REGISTERED.put("errorDescription", "No events registered");
    }

    private EventWait() {
        super(new APITag[]{APITag.INFO}, "timeout");
    }


    static JSONObject formatResponse(List<PendingEvent> events) {
        JSONArray eventsJSON = new JSONArray();
        events.forEach(event -> {
            JSONArray idsJSON = new JSONArray();
            if (event.isList())
                idsJSON.addAll(event.getIdList());
            else
                idsJSON.add(event.getId());
            JSONObject eventJSON = new JSONObject();
            eventJSON.put("name", event.getName());
            eventJSON.put("ids", idsJSON);
            eventsJSON.add(eventJSON);
        });
        JSONObject response = new JSONObject();
        response.put("events", eventsJSON);
        return response;
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = null;
        //
        // Get the timeout value
        //
        long timeout = Constants.EVENT_TIMEOUT;
        String value = req.getParameter("timeout");
        if (value != null) {
            try {
                timeout = Math.min(Long.valueOf(value), timeout);
            } catch (NumberFormatException exc) {
                response = INCORRECT_TIMEOUT;
            }
        }
        //
        // Wait for an event
        //
        if (response == null) {
            EventListener listener = EventListener.EVENT_LISTENERS.get(req.getRemoteAddr());
            if (listener == null) {
                response = NO_EVENTS_REGISTERED;
            } else {
                try {
                    List<PendingEvent> events = listener.eventWait(req, timeout);
                    if (events != null)
                        response = formatResponse(events);
                } catch (EventListenerException exc) {
                    response = new JSONObject();
                    response.put("errorCode", 7);
                    response.put("errorDescription", "Unable to wait for an event: " + exc.getMessage());
                }
            }
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }
}
