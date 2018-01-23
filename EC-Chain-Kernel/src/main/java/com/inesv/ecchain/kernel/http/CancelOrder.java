package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.Order;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_ORDER;


public final class CancelOrder extends CreateTransaction {

    static final CancelOrder instance = new CancelOrder();

    private CancelOrder() {
        super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, "order");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long orderId = ParameterParser.getUnsignedLong(req, "order", true);
        Account account = ParameterParser.getSenderAccount(req);
        Order.Ask orderData = Order.Ask.getAskOrder(orderId);
        if (orderData == null || orderData.getAccountId() != account.getId()) {
            return UNKNOWN_ORDER;
        }
        Mortgaged mortgaged = new Mortgaged.ColoredCoinsAskOrderCancellation(orderId);
        return createTransaction(req, account, mortgaged);
    }

}
