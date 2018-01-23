package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.GOODS_NOT_DELIVERED;
import static com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_PURCHASE;


public final class EcFeedback extends CreateTransaction {

    static final EcFeedback instance = new EcFeedback();

    private EcFeedback() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "purchase");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        ElectronicProductStore.Purchase purchase = ParameterParser.getPurchase(req);

        Account buyerAccount = ParameterParser.getSenderAccount(req);
        if (buyerAccount.getId() != purchase.getBuyerId()) {
            return INCORRECT_PURCHASE;
        }
        if (purchase.getEncryptedGoods() == null) {
            return GOODS_NOT_DELIVERED;
        }

        Account sellerAccount = Account.getAccount(purchase.getSellerId());
        Mortgaged mortgaged = new Mortgaged.DigitalGoodsFeedback(purchase.getId());
        return createTransaction(req, buyerAccount, sellerAccount.getId(), 0, mortgaged);
    }

}
