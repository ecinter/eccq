package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

public class EventRegister extends APIRequestHandler {

    static final EventRegister instance = new EventRegister();

    private static final JSONObject EVENTS_REGISTERED = new JSONObject();

    private static final JSONObject EXCLUSIVE_PARAMS = new JSONObject();

    private static final JSONObject INCORRECT_EVENT = new JSONObject();

    private static final JSONObject UNKNOWN_EVENT = new JSONObject();

    private static final JSONObject NO_EVENTS_REGISTERED = new JSONObject();

    static {
        EVENTS_REGISTERED.put("registered", true);
        EXCLUSIVE_PARAMS.put("errorCode", 4);
        EXCLUSIVE_PARAMS.put("errorDescription", "Mutually exclusive 'add' and 'remove'");
        INCORRECT_EVENT.put("errorCode", 4);
        INCORRECT_EVENT.put("errorDescription", "Incorrect event name format");
        UNKNOWN_EVENT.put("errorCode", 5);
        UNKNOWN_EVENT.put("errorDescription", "Unknown event name");
        NO_EVENTS_REGISTERED.put("errorCode", 8);
        NO_EVENTS_REGISTERED.put("errorDescription", "No events registered");
    }

    private EventRegister() {
        super(new APITag[]{APITag.INFO}, "event", "event", "event", "add", "remove");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response;
        //
        // Get 'add' and 'remove' parameters
        //
        boolean addEvents = Boolean.valueOf(req.getParameter("add"));
        boolean removeEvents = Boolean.valueOf(req.getParameter("remove"));
        if (addEvents && removeEvents)
            return EXCLUSIVE_PARAMS;
        //
        // Build the event list from the 'event' parameters
        //
        List<EventRegistration> events = new ArrayList<>();
        String[] params = req.getParameterValues("event");
        if (params == null) {
            //
            // Add all events if no events are supplied
            //
            EventListener.PEER_EVENTS.forEach(event -> events.add(new EventRegistration(event, 0)));
            EventListener.BLOCK_EVENTS.forEach(event -> events.add(new EventRegistration(event, 0)));
            EventListener.TX_EVENTS.forEach(event -> events.add(new EventRegistration(event, 0)));
            EventListener.EVENT_LIST.forEach(event -> events.add(new EventRegistration(event, 0)));
        } else {
            for (String param : params) {
                //
                // The Ledger event can have 2 or 3 parts.  All other events have 2 parts.
                //
                long accountId = 0;
                String[] parts = param.split("\\.");
                if (parts[0].equals("Ledger")) {
                    if (parts.length == 3) {
                        try {
                            accountId = Convert.parseAccountId(parts[2]);
                        } catch (RuntimeException e) {
                            return INCORRECT_EVENT;
                        }
                    } else if (parts.length != 2) {
                        return INCORRECT_EVENT;
                    }
                } else if (parts.length != 2) {
                    return INCORRECT_EVENT;
                }
                //
                // Add the event
                //
                List<? extends Enum> eventList;
                switch (parts[0]) {
                    case "EcBlock":
                        eventList = EventListener.BLOCK_EVENTS;
                        break;
                    case "Peer":
                        eventList = EventListener.PEER_EVENTS;
                        break;
                    case "Transaction":
                        eventList = EventListener.TX_EVENTS;
                        break;
                    case "Ledger":
                        eventList = EventListener.EVENT_LIST;
                        break;
                    default:
                        return UNKNOWN_EVENT;
                }
                boolean eventAdded = false;
                for (Enum<? extends Enum> event : eventList) {
                    if (event.name().equals(parts[1])) {
                        events.add(new EventRegistration(event, accountId));
                        eventAdded = true;
                        break;
                    }
                }
                if (!eventAdded)
                    return UNKNOWN_EVENT;
            }
        }
        //
        // Register the event listener
        //
        try {
            if (addEvents || removeEvents) {
                EventListener listener = EventListener.EVENT_LISTENERS.get(req.getRemoteAddr());
                if (listener != null) {
                    if (addEvents)
                        listener.putEvents(events);
                    else
                        listener.delEvents(events);
                    response = EVENTS_REGISTERED;
                } else {
                    response = NO_EVENTS_REGISTERED;
                }
            } else {
                EventListener listener = new EventListener(req.getRemoteAddr());
                listener.activateListener(events);
                response = EVENTS_REGISTERED;
            }
        } catch (EventListenerException exc) {
            response = new JSONObject();
            response.put("errorCode", 7);
            response.put("errorDescription", "Unable to register events: " + exc.getMessage());
        }
        //
        // Return the response
        //
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
