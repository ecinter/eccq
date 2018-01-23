package com.inesv.ecchain.wallet.core;

import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.inesv.ecchain.wallet.core.JSONResponse.EC_LOCK_ACCOUNT;


public final class LockForAccount extends UserRequestHandler {

    static final LockForAccount instance = new LockForAccount();

    private LockForAccount() {
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws IOException {

        user.lockAccount();

        return EC_LOCK_ACCOUNT;
    }
}
