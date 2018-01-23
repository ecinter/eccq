package com.inesv.ecchain.wallet.core;

import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

abstract class UserRequestHandler {
    abstract JSONStreamAware processRequest(HttpServletRequest request, User user) throws EcException, IOException;

    boolean requirePost() {
        return false;
    }
}
