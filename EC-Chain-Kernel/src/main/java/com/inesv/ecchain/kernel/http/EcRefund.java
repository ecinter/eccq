package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class EcRefund extends CreateTransaction {

    static final EcRefund instance = new EcRefund();

    private EcRefund() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "purchase", "refundNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account sellerAccount = ParameterParser.getSenderAccount(req);
        ElectronicProductStore.Purchase purchase = ParameterParser.getPurchase(req);
        if (sellerAccount.getId() != purchase.getSellerId()) {
            return INCORRECT_PURCHASE;
        }
        if (purchase.getRefundNote() != null) {
            return DUPLICATE_REFUND;
        }
        if (purchase.getEncryptedGoods() == null) {
            return GOODS_NOT_DELIVERED;
        }

        String refundValueNQT = Convert.emptyToNull(req.getParameter("refundNQT"));
        long refundNQT = 0;
        try {
            if (refundValueNQT != null) {
                refundNQT = Long.parseLong(refundValueNQT);
            }
        } catch (RuntimeException e) {
            return INCORRECT_DGS_REFUND;
        }
        if (refundNQT < 0 || refundNQT > Constants.EC_MAX_BALANCE_NQT) {
            return INCORRECT_DGS_REFUND;
        }

        Account buyerAccount = Account.getAccount(purchase.getBuyerId());

        Mortgaged mortgaged = new Mortgaged.DigitalGoodsRefund(purchase.getId(), refundNQT);
        return createTransaction(req, sellerAccount, buyerAccount.getId(), 0, mortgaged);

    }

}
