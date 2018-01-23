package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.BadgeData;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetDataTagCount extends APIRequestHandler {

    static final GetDataTagCount instance = new GetDataTagCount();

    private GetDataTagCount() {
        super(new APITag[]{APITag.DATA});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        JSONObject response = new JSONObject();
        response.put("numberOfDataTags", BadgeData.Tag.getTagCount());
        return response;
    }

}
