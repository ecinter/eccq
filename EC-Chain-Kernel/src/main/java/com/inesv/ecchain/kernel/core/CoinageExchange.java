package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotCurrentlyValidExceptionEc;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;

abstract class CoinageExchange extends Coinage {

    @Override
    final void validateAttachment(Transaction transaction) throws EcValidationException {
        Mortgaged.MonetarySystemExchange attachment = (Mortgaged.MonetarySystemExchange) transaction.getAttachment();
        if (attachment.getRateNQT() <= 0 || attachment.getUnits() == 0) {
            throw new EcNotValidExceptionEc("Invalid exchange: " + attachment.getJSONObject());
        }
        Coin coin = Coin.getCoin(attachment.getCurrencyId());
        CoinType.validate(coin, transaction);
        if (!coin.isActive()) {
            throw new EcNotCurrentlyValidExceptionEc("Coin not active: " + attachment.getJSONObject());
        }
    }

    @Override
    public final boolean canHaveRecipient() {
        return false;
    }

}
