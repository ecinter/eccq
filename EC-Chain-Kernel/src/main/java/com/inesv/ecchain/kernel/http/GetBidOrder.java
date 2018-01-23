package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Order;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_ORDER;


public final class GetBidOrder extends APIRequestHandler {

    static final GetBidOrder instance = new GetBidOrder();

    private GetBidOrder() {
        super(new APITag[]{APITag.AE}, "order");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        long orderId = ParameterParser.getUnsignedLong(req, "order", true);
        Order.Bid bidOrder = Order.Bid.getBidOrder(orderId);
        if (bidOrder == null) {
            return UNKNOWN_ORDER;
        }
        return JSONData.bidOrder(bidOrder);
    }

}
