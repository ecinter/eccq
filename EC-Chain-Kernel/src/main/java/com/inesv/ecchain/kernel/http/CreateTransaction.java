package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.*;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.kernel.core.*;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;

abstract class CreateTransaction extends APIRequestHandler {

    private static final String[] COMMON_PARAMETERS = new String[]{"secretPhrase", "publicKey", "feeNQT",
            "deadline", "referencedTransactionFullHash", "broadcast",
            "message", "messageIsText", "messageIsPrunable",
            "messageToEncrypt", "messageToEncryptIsText", "encryptedMessageData", "encryptedMessageNonce", "encryptedMessageIsPrunable", "compressMessageToEncrypt",
            "messageToEncryptToSelf", "messageToEncryptToSelfIsText", "encryptToSelfMessageData", "encryptToSelfMessageNonce", "compressMessageToEncryptToSelf",
            "phased", "phasingFinishHeight", "phasingVotingModel", "phasingQuorum", "phasingMinBalance", "phasingHolding", "phasingMinBalanceModel",
            "phasingWhitelisted", "phasingWhitelisted", "phasingWhitelisted",
            "phasingLinkedFullHash", "phasingLinkedFullHash", "phasingLinkedFullHash",
            "phasingHashedSecret", "phasingHashedSecretAlgorithm",
            "recipientPublicKey",
            "ecBlockId", "ecBlockHeight"};

    CreateTransaction(APITag[] apiTags, String... parameters) {
        super(apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    CreateTransaction(String fileParameter, APITag[] apiTags, String... parameters) {
        super(fileParameter, apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    private static String[] addCommonParameters(String[] parameters) {
        String[] result = Arrays.copyOf(parameters, parameters.length + COMMON_PARAMETERS.length);
        System.arraycopy(COMMON_PARAMETERS, 0, result, parameters.length, COMMON_PARAMETERS.length);
        return result;
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, Mortgaged mortgaged)
            throws EcException {
        return createTransaction(req, senderAccount, 0, 0, mortgaged);
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId, long amountNQT)
            throws EcException {
        return createTransaction(req, senderAccount, recipientId, amountNQT, Mortgaged.ORDINARY_PAYMENT);
    }

    private Phasing parsePhasing(HttpServletRequest req) throws ParameterException {
        int finishHeight = ParameterParser.getInt(req, "phasingFinishHeight",
                EcBlockchainImpl.getInstance().getHeight() + 1,
                EcBlockchainImpl.getInstance().getHeight() + Constants.EC_MAX_PHASING_DURATION + 1,
                true);

        PhasingParams phasingParams = parsePhasingParams(req, "phasing");

        byte[][] linkedFullHashes = null;
        String[] linkedFullHashesValues = req.getParameterValues("phasingLinkedFullHash");
        if (linkedFullHashesValues != null && linkedFullHashesValues.length > 0) {
            linkedFullHashes = new byte[linkedFullHashesValues.length][];
            for (int i = 0; i < linkedFullHashes.length; i++) {
                linkedFullHashes[i] = Convert.parseHexString(linkedFullHashesValues[i]);
                if (Convert.emptyToNull(linkedFullHashes[i]) == null || linkedFullHashes[i].length != 32) {
                    throw new ParameterException(INCORRECT_LINKED_FULL_HASH);
                }
            }
        }

        byte[] hashedSecret = Convert.parseHexString(Convert.emptyToNull(req.getParameter("phasingHashedSecret")));
        byte algorithm = ParameterParser.getByte(req, "phasingHashedSecretAlgorithm", (byte) 0, Byte.MAX_VALUE, false);

        return new Phasing(finishHeight, phasingParams, linkedFullHashes, hashedSecret, algorithm);
    }

    final PhasingParams parsePhasingParams(HttpServletRequest req, String parameterPrefix) throws ParameterException {
        byte votingModel = ParameterParser.getByte(req, parameterPrefix + "VotingModel", (byte) -1, (byte) 5, true);
        long quorum = ParameterParser.getLong(req, parameterPrefix + "Quorum", 0, Long.MAX_VALUE, false);
        long minBalance = ParameterParser.getLong(req, parameterPrefix + "MinBalance", 0, Long.MAX_VALUE, false);
        byte minBalanceModel = ParameterParser.getByte(req, parameterPrefix + "MinBalanceModel", (byte) 0, (byte) 3, false);
        long holdingId = ParameterParser.getUnsignedLong(req, parameterPrefix + "Holding", false);
        long[] whitelist = null;
        String[] whitelistValues = req.getParameterValues(parameterPrefix + "Whitelisted");
        if (whitelistValues != null && whitelistValues.length > 0) {
            whitelist = new long[whitelistValues.length];
            for (int i = 0; i < whitelistValues.length; i++) {
                whitelist[i] = Convert.parseAccountId(whitelistValues[i]);
                if (whitelist[i] == 0) {
                    throw new ParameterException(INCORRECT_WHITELIST);
                }
            }
        }
        return new PhasingParams(votingModel, holdingId, quorum, minBalance, minBalanceModel, whitelist);
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId,
                                            long amountNQT, Mortgaged mortgaged) throws EcException {
        String deadlineValue = req.getParameter("deadline");
        String referencedTransactionFullHash = Convert.emptyToNull(req.getParameter("referencedTransactionFullHash"));
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        String publicKeyValue = Convert.emptyToNull(req.getParameter("publicKey"));
        boolean broadcast = !"false".equalsIgnoreCase(req.getParameter("broadcast")) && secretPhrase != null;
        Enclosure.EncryptedMessage encryptedMessage = null;
        Enclosure.PrunableEncryptedMessage prunableEncryptedMessage = null;
        if (mortgaged.getTransactionType().canHaveRecipient() && recipientId != 0) {
            Account recipient = Account.getAccount(recipientId);
            if ("true".equalsIgnoreCase(req.getParameter("encryptedMessageIsPrunable"))) {
                prunableEncryptedMessage = (Enclosure.PrunableEncryptedMessage) ParameterParser.getEncryptedMessage(req, recipient, true);
            } else {
                encryptedMessage = (Enclosure.EncryptedMessage) ParameterParser.getEncryptedMessage(req, recipient, false);
            }
        }
        Enclosure.EncryptToSelfMessage encryptToSelfMessage = ParameterParser.getEncryptToSelfMessage(req);
        Message message = null;
        PrunablePlainMessage prunablePlainMessage = null;
        if ("true".equalsIgnoreCase(req.getParameter("messageIsPrunable"))) {
            prunablePlainMessage = (PrunablePlainMessage) ParameterParser.getPlainMessage(req, true);
        } else {
            message = (Message) ParameterParser.getPlainMessage(req, false);
        }
        PublicKeyAnnouncement publicKeyAnnouncement = null;
        String recipientPublicKey = Convert.emptyToNull(req.getParameter("recipientPublicKey"));
        if (recipientPublicKey != null) {
            publicKeyAnnouncement = new PublicKeyAnnouncement(Convert.parseHexString(recipientPublicKey));
        }

        Phasing phasing = null;
        boolean phased = "true".equalsIgnoreCase(req.getParameter("phased"));
        if (phased) {
            phasing = parsePhasing(req);
        }

        if (secretPhrase == null && publicKeyValue == null) {
            return MISSING_SECRET_PHRASE;
        } else if (deadlineValue == null) {
            return MISSING_DEADLINE;
        }

        short deadline;
        try {
            deadline = Short.parseShort(deadlineValue);
            if (deadline < 1) {
                return INCORRECT_DEADLINE;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_DEADLINE;
        }

        long feeNQT = ParameterParser.getFeeNQT(req);
        int ecBlockHeight = ParameterParser.getInt(req, "ecBlockHeight", 0, Integer.MAX_VALUE, false);
        long ecBlockId = ParameterParser.getUnsignedLong(req, "ecBlockId", false);
        if (ecBlockId != 0 && ecBlockId != EcBlockchainImpl.getInstance().getBlockIdAtHeight(ecBlockHeight)) {
            return INCORRECT_EC_BLOCK;
        }
        if (ecBlockId == 0 && ecBlockHeight > 0) {
            ecBlockId = EcBlockchainImpl.getInstance().getBlockIdAtHeight(ecBlockHeight);
        }

        JSONObject response = new JSONObject();

        // shouldn't try to get publicKey from senderAccount as it may have not been set yet
        byte[] publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase) : Convert.parseHexString(publicKeyValue);

        try {
            Builder builder = new TransactionImpl.BuilderImpl((byte) 1, publicKey, amountNQT, feeNQT,
                    deadline, (Mortgaged.AbstractMortgaged) mortgaged).referencedTransactionFullHash(referencedTransactionFullHash);
            if (mortgaged.getTransactionType().canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            builder.appendix(encryptedMessage);
            builder.appendix(message);
            builder.appendix(publicKeyAnnouncement);
            builder.appendix(encryptToSelfMessage);
            builder.appendix(phasing);
            builder.appendix(prunablePlainMessage);
            builder.appendix(prunableEncryptedMessage);
            if (ecBlockId != 0) {
                builder.ecBlockId(ecBlockId);
                builder.ecBlockHeight(ecBlockHeight);
            }
            Transaction transaction = builder.build(secretPhrase);
            try {
                if (Math.addExact(amountNQT, transaction.getFeeNQT()) > senderAccount.getUnconfirmedBalanceNQT()) {
                    return NOT_ENOUGH_FUNDS;
                }
            } catch (ArithmeticException e) {
                return NOT_ENOUGH_FUNDS;
            }
            JSONObject transactionJSON = JSONData.unconfirmedTransaction(transaction);
            response.put("transactionJSON", transactionJSON);
            try {
                response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
            } catch (EcNotYetEncryptedException ignore) {
            }
            if (secretPhrase != null) {
                response.put("transaction", transaction.getStringId());
                response.put("fullHash", transactionJSON.get("fullHash"));
                response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
                response.put("signatureHash", transactionJSON.get("signatureHash"));
            }
            if (broadcast) {
                TransactionProcessorImpl.getInstance().broadcast(transaction);
                response.put("broadcasted", true);
            } else {
                transaction.validate();
                response.put("broadcasted", false);
            }
        } catch (EcNotYetEnabledExceptionEc e) {
            return FEATURE_NOT_AVAILABLE;
        } catch (EcInsufficientBalanceExceptionEcEc e) {
            throw e;
        } catch (EcValidationException e) {
            if (broadcast) {
                response.clear();
            }
            response.put("broadcasted", false);
            JSONData.putException(response, e);
        }
        return response;

    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
