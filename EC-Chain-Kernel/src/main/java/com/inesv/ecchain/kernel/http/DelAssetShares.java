package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Property;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.NOT_ENOUGH_ASSETS;


public final class DelAssetShares extends CreateTransaction {

    static final DelAssetShares instance = new DelAssetShares();

    private DelAssetShares() {
        super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, "asset", "quantityQNT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Property property = ParameterParser.getAsset(req);
        long quantityQNT = ParameterParser.getQuantityQNT(req);
        Account account = ParameterParser.getSenderAccount(req);

        Mortgaged mortgaged = new Mortgaged.ColoredCoinsAssetDelete(property.getId(), quantityQNT);
        try {
            return createTransaction(req, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return NOT_ENOUGH_ASSETS;
        }
    }

}
