package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Property;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.NOT_ENOUGH_ASSETS;


public final class TransferAsset extends CreateTransaction {

    static final TransferAsset instance = new TransferAsset();

    private TransferAsset() {
        super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, "recipient", "asset", "quantityQNT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        long recipient = ParameterParser.getAccountId(req, "recipient", true);

        Property property = ParameterParser.getAsset(req);
        long quantityQNT = ParameterParser.getQuantityQNT(req);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.ColoredCoinsAssetTransfer(property.getId(), quantityQNT);
        try {
            return createTransaction(req, account, recipient, 0, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return NOT_ENOUGH_ASSETS;
        }
    }

}
