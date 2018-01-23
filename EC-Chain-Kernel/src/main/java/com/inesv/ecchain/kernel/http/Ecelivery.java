package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class Ecelivery extends CreateTransaction {

    static final Ecelivery instance = new Ecelivery();

    private Ecelivery() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "purchase", "discountNQT", "goodsToEncrypt", "goodsIsText", "goodsData", "goodsNonce");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account sellerAccount = ParameterParser.getSenderAccount(req);
        ElectronicProductStore.Purchase purchase = ParameterParser.getPurchase(req);
        if (sellerAccount.getId() != purchase.getSellerId()) {
            return INCORRECT_PURCHASE;
        }
        if (!purchase.isPending()) {
            return ALREADY_DELIVERED;
        }

        String discountValueNQT = Convert.emptyToNull(req.getParameter("discountNQT"));
        long discountNQT = 0;
        try {
            if (discountValueNQT != null) {
                discountNQT = Long.parseLong(discountValueNQT);
            }
        } catch (RuntimeException e) {
            return INCORRECT_DGS_DISCOUNT;
        }
        if (discountNQT < 0
                || discountNQT > Constants.EC_MAX_BALANCE_NQT
                || discountNQT > Math.multiplyExact(purchase.getPriceNQT(), (long) purchase.getQuantity())) {
            return INCORRECT_DGS_DISCOUNT;
        }

        Account buyerAccount = Account.getAccount(purchase.getBuyerId());
        boolean goodsIsText = !"false".equalsIgnoreCase(req.getParameter("goodsIsText"));
        EncryptedData encryptedGoods = ParameterParser.getEncryptedData(req, "goods");
        byte[] goodsBytes = null;
        boolean broadcast = !"false".equalsIgnoreCase(req.getParameter("broadcast"));

        if (encryptedGoods == null) {
            try {
                String plainGoods = Convert.nullToEmpty(req.getParameter("goodsToEncrypt"));
                if (plainGoods.length() == 0) {
                    return INCORRECT_DGS_GOODS;
                }
                goodsBytes = goodsIsText ? Convert.toBytes(plainGoods) : Convert.parseHexString(plainGoods);
            } catch (RuntimeException e) {
                return INCORRECT_DGS_GOODS;
            }
            String secretPhrase = ParameterParser.getSecretPhrase(req, broadcast);
            if (secretPhrase != null) {
                encryptedGoods = buyerAccount.encryptTo(goodsBytes, secretPhrase, true);
            }
        }

        Mortgaged mortgaged = encryptedGoods == null ?
                new Mortgaged.UnencryptedDigitalGoodsDelivery(purchase.getId(), goodsBytes,
                        goodsIsText, discountNQT, Account.getPublicKey(buyerAccount.getId())) :
                new Mortgaged.DigitalGoodsDelivery(purchase.getId(), encryptedGoods,
                        goodsIsText, discountNQT);
        return createTransaction(req, sellerAccount, buyerAccount.getId(), 0, mortgaged);

    }

}
