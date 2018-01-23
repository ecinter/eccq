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


public final class EcQuantityChange extends CreateTransaction {

    static final EcQuantityChange instance = new EcQuantityChange();

    private EcQuantityChange() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "goods", "deltaQuantity");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        Account account = ParameterParser.getSenderAccount(req);
        ElectronicProductStore.Goods goods = ParameterParser.getGoods(req);
        if (goods.isDelisted() || goods.getSellerId() != account.getId()) {
            return UNKNOWN_GOODS;
        }

        int deltaQuantity;
        try {
            String deltaQuantityString = Convert.emptyToNull(req.getParameter("deltaQuantity"));
            if (deltaQuantityString == null) {
                return MISSING_DELTA_QUANTITY;
            }
            deltaQuantity = Integer.parseInt(deltaQuantityString);
            if (deltaQuantity > Constants.EC_MAX_DGS_LISTING_QUANTITY || deltaQuantity < -Constants.EC_MAX_DGS_LISTING_QUANTITY) {
                return INCORRECT_DELTA_QUANTITY;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_DELTA_QUANTITY;
        }

        Mortgaged mortgaged = new Mortgaged.DigitalGoodsQuantityChange(goods.getId(), deltaQuantity);
        return createTransaction(req, account, mortgaged);

    }

}
