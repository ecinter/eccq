package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.core.EcInsufficientBalanceExceptionEcEc;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Property;
import com.inesv.ecchain.kernel.core.Mortgaged;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class DividendPayment extends CreateTransaction {

    static final DividendPayment instance = new DividendPayment();

    private DividendPayment() {
        super(new APITag[]{APITag.AE, APITag.CREATE_TRANSACTION}, "asset", "height", "amountNQTPerQNT");
    }

    @Override
    protected JSONStreamAware processRequest(final HttpServletRequest request)
            throws EcException {
        final int height = ParameterParser.getHeight(request);
        final long amountNQTPerQNT = ParameterParser.getAmountNQTPerQNT(request);
        final Account account = ParameterParser.getSenderAccount(request);
        final Property property = ParameterParser.getAsset(request);
        if (Property.getAsset(property.getId(), height) == null) {
            return JSONResponses.ASSET_NOT_ISSUED_YET;
        }
        final Mortgaged mortgaged = new Mortgaged.ColoredCoinsDividendPayment(property.getId(), height, amountNQTPerQNT);
        try {
            return this.createTransaction(request, account, mortgaged);
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            return JSONResponses.NOT_ENOUGH_FUNDS;
        }
    }

}
