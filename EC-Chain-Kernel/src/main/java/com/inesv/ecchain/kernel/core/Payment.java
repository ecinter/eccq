package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class Payment extends TransactionType {

    public static final TransactionType ORDINARY = new com.inesv.ecchain.kernel.core.Payment() {

        @Override
        public final byte getSubtype() {
            return Constants.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
        }

        @Override
        public final LedgerEvent getLedgerEvent() {
            return LedgerEvent.ORDINARY_PAYMENT;
        }

        @Override
        public String getName() {
            return "OrdinaryPayment";
        }

        @Override
        Mortgaged.EmptyMortgaged parseAttachment(ByteBuffer buffer, byte transactionVersion) throws EcNotValidExceptionEc {
            return Mortgaged.ORDINARY_PAYMENT;
        }

        @Override
        Mortgaged.EmptyMortgaged parseAttachment(JSONObject attachmentData) throws EcNotValidExceptionEc {
            return Mortgaged.ORDINARY_PAYMENT;
        }

        @Override
        void validateAttachment(Transaction transaction) throws EcValidationException {
            if (transaction.getAmountNQT() <= 0 || transaction.getAmountNQT() >= Constants.EC_MAX_BALANCE_NQT) {
                throw new EcNotValidExceptionEc("Invalid ordinary payment");
            }
        }

    };

    private Payment() {
    }

    @Override
    public final byte getType() {
        return Constants.TYPE_PAYMENT;
    }

    @Override
    final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return true;
    }

    @Override
    final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        if (recipientAccount == null) {
            Account.getAccount(Genesis.EC_CREATOR_ID).addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(),
                    transaction.getTransactionId(), transaction.getAmountNQT());
        }
    }

    @Override
    final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
    }

    @Override
    public final boolean canHaveRecipient() {
        return true;
    }

    @Override
    public final boolean isPhasingSafe() {
        return true;
    }

}
