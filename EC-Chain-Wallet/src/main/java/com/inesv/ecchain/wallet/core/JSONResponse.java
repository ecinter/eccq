package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public final class JSONResponse {

    public static final JSONStreamAware EC_INVALID_SECRET_PHRASE;
    public static final JSONStreamAware EC_LOCK_ACCOUNT;
    public static final JSONStreamAware EC_LOCAL_USERS_ONLY;
    public static final JSONStreamAware EC_NOTIFY_OF_ACCEPTED_TRANSACTION;
    public static final JSONStreamAware EC_DENY_ACCESS;
    public static final JSONStreamAware EC_INCORRECT_REQUEST;
    public static final JSONStreamAware EC_POST_REQUIRED;

    static {
        JSONObject response = new JSONObject();
        response.put("response", "showMessage");
        response.put("message", "Invalid secret phrase!");
        EC_INVALID_SECRET_PHRASE = JSON.prepare(response);
        response.clear();
        response.put("response", "lockAccount");
        EC_LOCK_ACCOUNT = JSON.prepare(response);
        response.clear();
        response.put("response", "showMessage");
        response.put("message", "This operation is allowed to local host users only!");
        EC_LOCAL_USERS_ONLY = JSON.prepare(response);
        response.clear();
        response.put("response", "notifyOfAcceptedTransaction");
        EC_NOTIFY_OF_ACCEPTED_TRANSACTION = JSON.prepare(response);
        response.clear();
        response.put("response", "denyAccess");
        EC_DENY_ACCESS = JSON.prepare(response);
        response.clear();
        response.put("response", "showMessage");
        response.put("message", "Incorrect request!");
        EC_INCORRECT_REQUEST = JSON.prepare(response);
        response.clear();
        response.put("response", "showMessage");
        response.put("message", "This request is only accepted using POST!");
        EC_POST_REQUIRED = JSON.prepare(response);
    }
}
