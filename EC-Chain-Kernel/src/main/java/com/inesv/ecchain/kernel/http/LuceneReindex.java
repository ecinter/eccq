package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.kernel.core.H2;
import com.inesv.ecchain.kernel.H2.FullTextTrigger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.SQLException;

public final class LuceneReindex extends APIRequestHandler {

    static final LuceneReindex instance = new LuceneReindex();

    private LuceneReindex() {
        super(new APITag[]{APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        try (Connection con = H2.H2.getConnection()) {
            FullTextTrigger.reindex(con);
            response.put("done", true);
        } catch (SQLException e) {
            JSONData.putException(response, e);
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
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
