package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.CoinType;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class IssueCurrency extends CreateTransaction {

    static final IssueCurrency instance = new IssueCurrency();

    private IssueCurrency() {
        super(new APITag[]{APITag.MS, APITag.CREATE_TRANSACTION},
                "name", "code", "description", "type", "initialSupply", "reserveSupply", "maxSupply", "issuanceHeight", "minReservePerUnitNQT",
                "minDifficulty", "maxDifficulty", "ruleset", "algorithm", "decimals");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {
        String name = Convert.nullToEmpty(req.getParameter("name"));
        String code = Convert.nullToEmpty(req.getParameter("code"));
        String description = Convert.nullToEmpty(req.getParameter("description"));

        if (name.length() < Constants.EC_MIN_CURRENCY_NAME_LENGTH || name.length() > Constants.EC_MAX_CURRENCY_NAME_LENGTH) {
            return JSONResponses.INCORRECT_CURRENCY_NAME_LENGTH;
        }
        if (code.length() < Constants.EC_MIN_CURRENCY_CODE_LENGTH || code.length() > Constants.EC_MAX_CURRENCY_CODE_LENGTH) {
            return JSONResponses.INCORRECT_CURRENCY_CODE_LENGTH;
        }
        if (description.length() > Constants.EC_MAX_CURRENCY_DESCRIPTION_LENGTH) {
            return JSONResponses.INCORRECT_CURRENCY_DESCRIPTION_LENGTH;
        }
        String normalizedName = name.toLowerCase();
        for (int i = 0; i < normalizedName.length(); i++) {
            if (Constants.EC_ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                return JSONResponses.INCORRECT_CURRENCY_NAME;
            }
        }
        for (int i = 0; i < code.length(); i++) {
            if (Constants.ALLOWED_CURRENCY_CODE_LETTERS.indexOf(code.charAt(i)) < 0) {
                return JSONResponses.INCORRECT_CURRENCY_CODE;
            }
        }

        int type = 0;
        if (Convert.emptyToNull(req.getParameter("type")) == null) {
            for (CoinType coinType : CoinType.values()) {
                if ("1".equals(req.getParameter(coinType.toString().toLowerCase()))) {
                    type = type | coinType.getCode();
                }
            }
        } else {
            type = ParameterParser.getInt(req, "type", 0, Integer.MAX_VALUE, false);
        }

        long maxSupply = ParameterParser.getLong(req, "maxSupply", 1, Constants.EC_MAX_CURRENCY_TOTAL_SUPPLY, false);
        long reserveSupply = ParameterParser.getLong(req, "reserveSupply", 0, maxSupply, false);
        long initialSupply = ParameterParser.getLong(req, "initialSupply", 0, maxSupply, false);
        int issuanceHeight = ParameterParser.getInt(req, "issuanceHeight", 0, Integer.MAX_VALUE, false);
        long minReservePerUnit = ParameterParser.getLong(req, "minReservePerUnitNQT", 1, Constants.EC_MAX_BALANCE_NQT, false);
        int minDifficulty = ParameterParser.getInt(req, "minDifficulty", 1, 255, false);
        int maxDifficulty = ParameterParser.getInt(req, "maxDifficulty", 1, 255, false);
        byte ruleset = ParameterParser.getByte(req, "ruleset", (byte) 0, Byte.MAX_VALUE, false);
        byte algorithm = ParameterParser.getByte(req, "algorithm", (byte) 0, Byte.MAX_VALUE, false);
        byte decimals = ParameterParser.getByte(req, "decimals", (byte) 0, Byte.MAX_VALUE, false);
        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.MonetarySystemCurrencyIssuance(name, code, description, (byte) type, initialSupply,
                reserveSupply, maxSupply, issuanceHeight, minReservePerUnit, minDifficulty, maxDifficulty, ruleset, algorithm, decimals);

        return createTransaction(req, account, mortgaged);
    }
}
