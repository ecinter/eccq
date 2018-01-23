package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.inesv.ecchain.kernel.http.JSONResponses.DECRYPTION_FAILED;

public final class GetEcPurchase extends APIRequestHandler {

    static final GetEcPurchase instance = new GetEcPurchase();

    private GetEcPurchase() {
        super(new APITag[]{APITag.DGS}, "purchase", "secretPhrase", "sharedKey");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        ElectronicProductStore.Purchase purchase = ParameterParser.getPurchase(req);
        JSONObject response = JSONData.purchase(purchase);

        byte[] sharedKey = ParameterParser.getBytes(req, "sharedKey", false);
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        if (sharedKey.length != 0 && secretPhrase != null) {
            return JSONResponses.either("secretPhrase", "sharedKey");
        }
        if (sharedKey.length == 0 && secretPhrase == null) {
            return response;
        }
        if (purchase.getEncryptedGoods() != null) {
            byte[] data = purchase.getEncryptedGoods().getData();
            try {
                byte[] decrypted = Convert.EC_EMPTY_BYTE;
                if (data.length != 0) {
                    if (secretPhrase != null) {
                        byte[] readerPublicKey = Crypto.getPublicKey(secretPhrase);
                        byte[] sellerPublicKey = Account.getPublicKey(purchase.getSellerId());
                        byte[] buyerPublicKey = Account.getPublicKey(purchase.getBuyerId());
                        byte[] publicKey = Arrays.equals(sellerPublicKey, readerPublicKey) ? buyerPublicKey : sellerPublicKey;
                        if (publicKey != null) {
                            decrypted = Account.decryptFrom(publicKey, purchase.getEncryptedGoods(), secretPhrase, true);
                        }
                    } else {
                        decrypted = Crypto.aesDecrypt(purchase.getEncryptedGoods().getData(), sharedKey);
                        decrypted = Convert.uncompress(decrypted);
                    }
                }
                response.put("decryptedGoods", Convert.toString(decrypted, purchase.goodsIsText()));
            } catch (RuntimeException e) {
                LoggerUtil.logDebug(e.toString());
                return DECRYPTION_FAILED;
            }
        }
        return response;
    }

}
