package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.ShufflingParticipant;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetShufflingParticipants extends APIRequestHandler {

    static final GetShufflingParticipants instance = new GetShufflingParticipants();

    private GetShufflingParticipants() {
        super(new APITag[]{APITag.SHUFFLING}, "shuffling");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long shufflingId = ParameterParser.getUnsignedLong(req, "shuffling", true);
        JSONObject response = new JSONObject();
        JSONArray participantsJSONArray = new JSONArray();
        response.put("participants", participantsJSONArray);
        try (H2Iterator<ShufflingParticipant> participants = ShufflingParticipant.getParticipants(shufflingId)) {
            for (ShufflingParticipant participant : participants) {
                participantsJSONArray.add(JSONData.participant(participant));
            }
        }
        return response;
    }

}
