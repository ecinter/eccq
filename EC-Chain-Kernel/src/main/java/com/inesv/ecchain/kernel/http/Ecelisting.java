package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.ElectronicProductStore;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.UNKNOWN_GOODS;


public final class Ecelisting extends CreateTransaction {

    static final Ecelisting instance = new Ecelisting();

    private Ecelisting() {
        super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION}, "goods");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Account account = ParameterParser.getSenderAccount(req);
        ElectronicProductStore.Goods goods = ParameterParser.getGoods(req);
        if (goods.isDelisted() || goods.getSellerId() != account.getId()) {
            return UNKNOWN_GOODS;
        }
        Mortgaged mortgaged = new Mortgaged.DigitalGoodsDelisting(goods.getId());
        return createTransaction(req, account, mortgaged);
    }

}
