package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.EcTime;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class EcPurchase extends CreateTransaction {

    static final EcPurchase instance = new EcPurchase();

    private EcPurchase() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "goods", "priceNQT", "quantity", "deliveryDeadlineTimestamp");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        ElectronicProductStore.Goods goods = ParameterParser.getGoods(req);
        if (goods.isDelisted()) {
            return UNKNOWN_GOODS;
        }

        int quantity = ParameterParser.getGoodsQuantity(req);
        if (quantity > goods.getQuantity()) {
            return INCORRECT_PURCHASE_QUANTITY;
        }

        long priceNQT = ParameterParser.getPriceNQT(req);
        if (priceNQT != goods.getPriceNQT()) {
            return INCORRECT_PURCHASE_PRICE;
        }

        String deliveryDeadlineString = Convert.emptyToNull(req.getParameter("deliveryDeadlineTimestamp"));
        if (deliveryDeadlineString == null) {
            return MISSING_DELIVERY_DEADLINE_TIMESTAMP;
        }
        int deliveryDeadline;
        try {
            deliveryDeadline = Integer.parseInt(deliveryDeadlineString);
            if (deliveryDeadline <= new EcTime.EpochEcTime().getTime()) {
                return INCORRECT_DELIVERY_DEADLINE_TIMESTAMP;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_DELIVERY_DEADLINE_TIMESTAMP;
        }

        Account buyerAccount = ParameterParser.getSenderAccount(req);
        Account sellerAccount = Account.getAccount(goods.getSellerId());

        Mortgaged mortgaged = new Mortgaged.DigitalGoodsPurchase(goods.getId(), quantity, priceNQT,
                deliveryDeadline);
        try {
            return createTransaction(req, buyerAccount, sellerAccount.getId(), 0, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return JSONResponses.NOT_ENOUGH_FUNDS;
        }

    }

}
