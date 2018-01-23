package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_GOODS;

public final class EcPriceChange extends CreateTransaction {

    static final EcPriceChange instance = new EcPriceChange();

    private EcPriceChange() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "goods", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Account account = ParameterParser.getSenderAccount(req);
        ElectronicProductStore.Goods goods = ParameterParser.getGoods(req);
        long priceNQT = ParameterParser.getPriceNQT(req);
        if (goods.isDelisted() || goods.getSellerId() != account.getId()) {
            return UNKNOWN_GOODS;
        }
        Mortgaged mortgaged = new Mortgaged.DigitalGoodsPriceChange(goods.getId(), priceNQT);
        return createTransaction(req, account, mortgaged);
    }

}
