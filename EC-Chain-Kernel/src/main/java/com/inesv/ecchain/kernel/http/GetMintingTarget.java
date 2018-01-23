package com.inesv.ecchain.kernel.http;

import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Coin;
import com.inesv.ecchain.kernel.core.CoinMint;
import com.inesv.ecchain.kernel.core.CoinMinting;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;

public final class GetMintingTarget extends APIRequestHandler {

    static final GetMintingTarget instance = new GetMintingTarget();

    private GetMintingTarget() {
        super(new APITag[]{APITag.MS}, "currency", "account", "units");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        Coin coin = ParameterParser.getCurrency(req);
        JSONObject json = new JSONObject();
        json.put("coin", Long.toUnsignedString(coin.getId()));
        long units = ParameterParser.getLong(req, "units", 1, coin.getMaxSupply() - coin.getReserveSupply(), true);
        BigInteger numericTarget = CoinMinting.getNumericTarget(coin, units);
        json.put("difficulty", String.valueOf(BigInteger.ZERO.equals(numericTarget) ? -1 : BigInteger.valueOf(2).pow(256).subtract(BigInteger.ONE).divide(numericTarget)));
        json.put("targetBytes", Convert.toHexString(CoinMinting.getTarget(numericTarget)));
        json.put("counter", CoinMint.getCounter(coin.getId(), ParameterParser.getAccountId(req, true)));
        return json;
    }

}
