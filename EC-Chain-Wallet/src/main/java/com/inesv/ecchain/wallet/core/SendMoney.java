package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static com.inesv.ecchain.wallet.core.JSONResponse.EC_NOTIFY_OF_ACCEPTED_TRANSACTION;


public final class SendMoney extends UserRequestHandler {

    static final SendMoney instance = new SendMoney();

    private SendMoney() {
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws EcValidationException, IOException {
        if (user.getSecretECPhrase() == null) {
            return null;
        }

        String recipientValue = req.getParameter("recipient");
        String amountValue = req.getParameter("amountEC");
        String feeValue = req.getParameter("feeEC");
        String deadlineValue = req.getParameter("deadline");
        String secretPhrase = req.getParameter("secretPhrase");

        long recipient;
        long amountNQT = 0;
        long feeNQT = 0;
        short deadline = 0;

        try {

            recipient = Convert.parseUnsignedLong(recipientValue);
            if (recipient == 0) throw new IllegalArgumentException("invalid recipient");
            amountNQT = Convert.parseEC(amountValue.trim());
            feeNQT = Convert.parseEC(feeValue.trim());
            deadline = (short) (Double.parseDouble(deadlineValue) * 60);

        } catch (RuntimeException e) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "One of the fields is filled incorrectly!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;
        }

        if (!user.getSecretECPhrase().equals(secretPhrase)) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Wrong secret phrase!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (amountNQT <= 0 || amountNQT > Constants.EC_MAX_BALANCE_NQT) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Amount\" must be greater than 0!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (feeNQT < Constants.ONE_EC || feeNQT > Constants.EC_MAX_BALANCE_NQT) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Fee\" must be at least 1 EC!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (deadline < 1 || deadline > 1440) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Deadline\" must be greater or equal to 1 minute and less than 24 hours!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        }

        Account account = Account.getAccount(user.getPublicECKey());
        if (account == null || Math.addExact(amountNQT, feeNQT) > account.getUnconfirmedBalanceNQT()) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Not enough funds!");
            response.put("recipient", recipientValue);
            response.put("amountEC", amountValue);
            response.put("feeEC", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else {

            final Transaction transaction = new TransactionImpl.BuilderImpl((byte) 1, user.getPublicECKey(),
                    amountNQT, feeNQT, deadline, Mortgaged.ORDINARY_PAYMENT).recipientId(recipient).build(secretPhrase);

            TransactionProcessorImpl.getInstance().broadcast(transaction);

            return EC_NOTIFY_OF_ACCEPTED_TRANSACTION;

        }
    }

    @Override
    boolean requirePost() {
        return true;
    }

}
