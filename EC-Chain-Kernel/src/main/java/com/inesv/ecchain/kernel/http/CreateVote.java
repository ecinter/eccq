package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.EcBlockchainImpl;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class CreateVote extends CreateTransaction {

    static final CreateVote instance = new CreateVote();

    private CreateVote() {
        super(new APITag[]{APITag.VS, APITag.CREATE_TRANSACTION},
                "name", "description", "finishHeight", "votingModel",
                "minNumberOfOptions", "maxNumberOfOptions",
                "minRangeValue", "maxRangeValue",
                "minBalance", "minBalanceModel", "holding",
                "option00", "option01", "option02");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        String nameValue = Convert.emptyToNull(req.getParameter("name"));
        String descriptionValue = req.getParameter("description");

        if (nameValue == null || nameValue.trim().isEmpty()) {
            return MISSING_NAME;
        } else if (descriptionValue == null) {
            return MISSING_DESCRIPTION;
        }

        if (nameValue.length() > Constants.EC_MAX_POLL_NAME_LENGTH) {
            return INCORRECT_POLL_NAME_LENGTH;
        }

        if (descriptionValue.length() > Constants.EC_MAX_POLL_DESCRIPTION_LENGTH) {
            return INCORRECT_POLL_DESCRIPTION_LENGTH;
        }

        List<String> options = new ArrayList<>();
        while (options.size() < Constants.EC_MAX_POLL_OPTION_COUNT) {
            int i = options.size();
            String optionValue = Convert.emptyToNull(req.getParameter("option" + (i < 10 ? "0" + i : i)));
            if (optionValue == null) {
                break;
            }
            if (optionValue.length() > Constants.EC_MAX_POLL_OPTION_LENGTH || (optionValue = optionValue.trim()).isEmpty()) {
                return INCORRECT_POLL_OPTION_LENGTH;
            }
            options.add(optionValue);
        }

        byte optionsSize = (byte) options.size();
        if (options.size() == 0) {
            return INCORRECT_ZEROOPTIONS;
        }

        int currentHeight = EcBlockchainImpl.getInstance().getHeight();
        int finishHeight = ParameterParser.getInt(req, "finishHeight",
                currentHeight + 2,
                currentHeight + Constants.EC_MAX_POLL_DURATION + 1, true);

        byte votingModel = ParameterParser.getByte(req, "votingModel", (byte) 0, (byte) 3, true);

        byte minNumberOfOptions = ParameterParser.getByte(req, "minNumberOfOptions", (byte) 1, optionsSize, true);
        byte maxNumberOfOptions = ParameterParser.getByte(req, "maxNumberOfOptions", minNumberOfOptions, optionsSize, true);

        byte minRangeValue = ParameterParser.getByte(req, "minRangeValue", Constants.EC_MIN_VOTE_VALUE, Constants.EC_MAX_VOTE_VALUE, true);
        byte maxRangeValue = ParameterParser.getByte(req, "maxRangeValue", minRangeValue, Constants.EC_MAX_VOTE_VALUE, true);

        Mortgaged.MessagingPollCreation.PollBuilder builder = new Mortgaged.MessagingPollCreation.PollBuilder(nameValue.trim(), descriptionValue.trim(),
                options.toArray(new String[options.size()]), finishHeight, votingModel,
                minNumberOfOptions, maxNumberOfOptions, minRangeValue, maxRangeValue);

        long minBalance = ParameterParser.getLong(req, "minBalance", 0, Long.MAX_VALUE, false);

        if (minBalance != 0) {
            byte minBalanceModel = ParameterParser.getByte(req, "minBalanceModel", (byte) 0, (byte) 3, true);
            builder.minBalance(minBalanceModel, minBalance);
        }

        long holdingId = ParameterParser.getUnsignedLong(req, "holding", false);
        if (holdingId != 0) {
            builder.holdingId(holdingId);
        }

        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = builder.build();
        return createTransaction(req, account, mortgaged);
    }
}
