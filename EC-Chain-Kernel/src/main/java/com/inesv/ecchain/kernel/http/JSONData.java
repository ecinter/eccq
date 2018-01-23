package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.crypto.EncryptedData;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.Filter;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.peer.PeerService;
import com.inesv.ecchain.kernel.peer.QualityProof;
import com.inesv.ecchain.kernel.peer.Peer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JSONData {

    static JSONObject alias(AccountName accountName) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", accountName.getAccountId());
        json.put("aliasName", accountName.getPropertysName());
        json.put("aliasURI", accountName.getPropertysURI());
        json.put("timestamp", accountName.getTimestamp());
        json.put("accountName", Long.toUnsignedString(accountName.getId()));
        AccountName.Offer offer = AccountName.getOffer(accountName);
        if (offer != null) {
            json.put("priceNQT", String.valueOf(offer.getPriceNQT()));
            if (offer.getBuyerId() != 0) {
                json.put("buyer", Long.toUnsignedString(offer.getBuyerId()));
            }
        }
        return json;
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance) {
        return accountBalance(account, includeEffectiveBalance, EcBlockchainImpl.getInstance().getHeight());
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance, int height) {
        JSONObject json = new JSONObject();
        if (account == null) {
            json.put("balanceNQT", "0");
            json.put("unconfirmedBalanceNQT", "0");
            json.put("forgedBalanceNQT", "0");
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceEC", "0");
                json.put("guaranteedBalanceNQT", "0");
            }
        } else {
            json.put("balanceNQT", String.valueOf(account.getBalanceNQT()));
            json.put("unconfirmedBalanceNQT", String.valueOf(account.getUnconfirmedBalanceNQT()));
            json.put("forgedBalanceNQT", String.valueOf(account.getForgedBalanceNQT()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceEC", account.getEffectiveBalanceEC(height));
                json.put("guaranteedBalanceNQT", String.valueOf(account.getGuaranteedBalanceNQT(Constants.EC_GUARANTEED_BALANCE_CONFIRMATIONS, height)));
            }
        }
        return json;
    }

    static JSONObject lessor(Account account, boolean includeEffectiveBalance) {
        JSONObject json = new JSONObject();
        Account.AccountLease accountLease = account.getAccountLease();
        if (accountLease.getCurrentLesseeId() != 0) {
            putAccount(json, "currentLessee", accountLease.getCurrentLesseeId());
            json.put("currentHeightFrom", String.valueOf(accountLease.getCurrentLeasingHeightFrom()));
            json.put("currentHeightTo", String.valueOf(accountLease.getCurrentLeasingHeightTo()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceEC", String.valueOf(account.getGuaranteedBalanceNQT() / Constants.ONE_EC));
            }
        }
        if (accountLease.getNextLesseeId() != 0) {
            putAccount(json, "nextLessee", accountLease.getNextLesseeId());
            json.put("nextHeightFrom", String.valueOf(accountLease.getNextLeasingHeightFrom()));
            json.put("nextHeightTo", String.valueOf(accountLease.getNextLeasingHeightTo()));
        }
        return json;
    }

    static JSONObject asset(Property property, boolean includeCounts) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", property.getAccountId());
        json.put("name", property.getName());
        json.put("description", property.getDescription());
        json.put("decimals", property.getDecimals());
        json.put("initialQuantityQNT", String.valueOf(property.getInitialQuantityQNT()));
        json.put("quantityQNT", String.valueOf(property.getQuantityQNT()));
        json.put("property", Long.toUnsignedString(property.getId()));
        if (includeCounts) {
            json.put("numberOfTrades", Trade.getTradeCount(property.getId()));
            json.put("numberOfTransfers", PropertyTransfer.getTransferCount(property.getId()));
            json.put("numberOfAccounts", Account.getPropertyAccountCount(property.getId()));
        }
        return json;
    }

    static JSONObject currency(Coin coin, boolean includeCounts) {
        JSONObject json = new JSONObject();
        json.put("coin", Long.toUnsignedString(coin.getId()));
        putAccount(json, "account", coin.getAccountId());
        json.put("name", coin.getName());
        json.put("code", coin.getCoinCode());
        json.put("description", coin.getDescription());
        json.put("type", coin.getCoinType());
        json.put("initialSupply", String.valueOf(coin.getInitialSupply()));
        json.put("currentSupply", String.valueOf(coin.getCurrentSupply()));
        json.put("reserveSupply", String.valueOf(coin.getReserveSupply()));
        json.put("maxSupply", String.valueOf(coin.getMaxSupply()));
        json.put("creationHeight", coin.getCreationHeight());
        json.put("issuanceHeight", coin.getIssuanceHeight());
        json.put("minReservePerUnitNQT", String.valueOf(coin.getMinReservePerUnitNQT()));
        json.put("currentReservePerUnitNQT", String.valueOf(coin.getCurrentReservePerUnitNQT()));
        json.put("minDifficulty", coin.getMinDifficulty());
        json.put("maxDifficulty", coin.getMaxDifficulty());
        json.put("algorithm", coin.getAlgorithm());
        json.put("decimals", coin.getDecimals());
        if (includeCounts) {
            json.put("numberOfExchanges", Conversion.getConvertCount(coin.getId()));
            json.put("numberOfTransfers", CoinTransfer.getTransferCount(coin.getId()));
        }
        JSONArray types = new JSONArray();
        for (CoinType type : CoinType.values()) {
            if (coin.is(type)) {
                types.add(type.toString());
            }
        }
        json.put("types", types);
        return json;
    }

    static JSONObject currencyFounder(CoinFounder founder) {
        JSONObject json = new JSONObject();
        json.put("currency", Long.toUnsignedString(founder.getCurrencyId()));
        putAccount(json, "account", founder.getAccountId());
        json.put("amountPerUnitNQT", String.valueOf(founder.getAmountPerUnitNQT()));
        return json;
    }

    static JSONObject accountAsset(Account.AccountPro accountPro, boolean includeAccount, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "account", accountPro.getAccountId());
        }
        json.put("asset", Long.toUnsignedString(accountPro.getAssetId()));
        json.put("quantityQNT", String.valueOf(accountPro.getQuantityQNT()));
        json.put("unconfirmedQuantityQNT", String.valueOf(accountPro.getUnconfirmedQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, accountPro.getAssetId());
        }
        return json;
    }

    static JSONObject accountCurrency(Account.AccountCoin accountCoin, boolean includeAccount, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "account", accountCoin.getAccountId());
        }
        json.put("currency", Long.toUnsignedString(accountCoin.getCurrencyId()));
        json.put("units", String.valueOf(accountCoin.getUnits()));
        json.put("unconfirmedUnits", String.valueOf(accountCoin.getUnconfirmedUnits()));
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, accountCoin.getCurrencyId());
        }
        return json;
    }

    static JSONObject accountProperty(Account.AccountProperty accountProperty, boolean includeAccount, boolean includeSetter) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "recipient", accountProperty.getRecipientId());
        }
        if (includeSetter) {
            putAccount(json, "setter", accountProperty.getSetterId());
        }
        json.put("property", accountProperty.getProperty());
        json.put("value", accountProperty.getValue());
        return json;
    }

    static JSONObject askOrder(Order.Ask order) {
        JSONObject json = order(order);
        json.put("type", "ask");
        return json;
    }

    static JSONObject bidOrder(Order.Bid order) {
        JSONObject json = order(order);
        json.put("type", "bid");
        return json;
    }

    private static JSONObject order(Order order) {
        JSONObject json = new JSONObject();
        json.put("order", Long.toUnsignedString(order.getId()));
        json.put("asset", Long.toUnsignedString(order.getAssetId()));
        putAccount(json, "account", order.getAccountId());
        json.put("quantityQNT", String.valueOf(order.getQuantityQNT()));
        json.put("priceNQT", String.valueOf(order.getPriceNQT()));
        json.put("height", order.getHeight());
        json.put("transactionIndex", order.getTransactionIndex());
        json.put("transactionHeight", order.getTransactionHeight());
        return json;
    }

    static JSONObject expectedAskOrder(Transaction transaction) {
        JSONObject json = expectedOrder(transaction);
        json.put("type", "ask");
        return json;
    }

    static JSONObject expectedBidOrder(Transaction transaction) {
        JSONObject json = expectedOrder(transaction);
        json.put("type", "bid");
        return json;
    }

    private static JSONObject expectedOrder(Transaction transaction) {
        JSONObject json = new JSONObject();
        Mortgaged.ColoredCoinsOrderPlacement attachment = (Mortgaged.ColoredCoinsOrderPlacement) transaction.getAttachment();
        json.put("order", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "account", transaction.getSenderId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        json.put("priceNQT", String.valueOf(attachment.getPriceNQT()));
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject expectedOrderCancellation(Transaction transaction) {
        JSONObject json = new JSONObject();
        Mortgaged.ColoredCoinsOrderCancellation attachment = (Mortgaged.ColoredCoinsOrderCancellation) transaction.getAttachment();
        json.put("order", Long.toUnsignedString(attachment.getOrderId()));
        putAccount(json, "account", transaction.getSenderId());
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject offer(CoinExchangeOffer offer) {
        JSONObject json = new JSONObject();
        json.put("offer", Long.toUnsignedString(offer.getId()));
        putAccount(json, "account", offer.getAccountId());
        json.put("height", offer.getHeight());
        json.put("expirationHeight", offer.getExpirationHeight());
        json.put("currency", Long.toUnsignedString(offer.getCurrencyId()));
        json.put("rateNQT", String.valueOf(offer.getRateNQT()));
        json.put("limit", String.valueOf(offer.getLimit()));
        json.put("supply", String.valueOf(offer.getSupply()));
        return json;
    }

    static JSONObject expectedBuyOffer(Transaction transaction) {
        JSONObject json = expectedOffer(transaction);
        Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
        json.put("rateNQT", String.valueOf(attachment.getBuyRateNQT()));
        json.put("limit", String.valueOf(attachment.getTotalBuyLimit()));
        json.put("supply", String.valueOf(attachment.getInitialBuySupply()));
        return json;
    }

    static JSONObject expectedSellOffer(Transaction transaction) {
        JSONObject json = expectedOffer(transaction);
        Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
        json.put("rateNQT", String.valueOf(attachment.getSellRateNQT()));
        json.put("limit", String.valueOf(attachment.getTotalSellLimit()));
        json.put("supply", String.valueOf(attachment.getInitialSellSupply()));
        return json;
    }

    private static JSONObject expectedOffer(Transaction transaction) {
        Mortgaged.MonetarySystemPublishExchangeOffer attachment = (Mortgaged.MonetarySystemPublishExchangeOffer) transaction.getAttachment();
        JSONObject json = new JSONObject();
        json.put("offer", transaction.getStringId());
        putAccount(json, "account", transaction.getSenderId());
        json.put("expirationHeight", attachment.getExpirationHeight());
        json.put("currency", Long.toUnsignedString(attachment.getCurrencyId()));
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject availableOffers(CoinExchangeOffer.AvailableOffers availableOffers) {
        JSONObject json = new JSONObject();
        json.put("rateNQT", String.valueOf(availableOffers.getRateNQT()));
        json.put("units", String.valueOf(availableOffers.getUnits()));
        json.put("amountNQT", String.valueOf(availableOffers.getAmountNQT()));
        return json;
    }

    static JSONObject shuffling(Shuffling shuffling, boolean includeHoldingInfo) {
        JSONObject json = new JSONObject();
        json.put("shuffling", Long.toUnsignedString(shuffling.getId()));
        putAccount(json, "issuer", shuffling.getIssuerId());
        json.put("holding", Long.toUnsignedString(shuffling.getHoldingId()));
        json.put("holdingType", shuffling.getHoldingType().getCode());
        if (shuffling.getAssigneeAccountId() != 0) {
            putAccount(json, "assignee", shuffling.getAssigneeAccountId());
        }
        json.put("amount", String.valueOf(shuffling.getAmount()));
        json.put("blocksRemaining", shuffling.getBlocksRemaining());
        json.put("participantCount", shuffling.getParticipantCount());
        json.put("registrantCount", shuffling.getRegistrantCount());
        json.put("stage", shuffling.getStage().getCode());
        json.put("shufflingStateHash", Convert.toHexString(shuffling.getStateHash()));
        json.put("shufflingFullHash", Convert.toHexString(shuffling.getFullHash()));
        JSONArray recipientPublicKeys = new JSONArray();
        for (byte[] recipientPublicKey : shuffling.getRecipientPublicKeys()) {
            recipientPublicKeys.add(Convert.toHexString(recipientPublicKey));
        }
        if (recipientPublicKeys.size() > 0) {
            json.put("recipientPublicKeys", recipientPublicKeys);
        }
        if (includeHoldingInfo && shuffling.getHoldingType() != HoldingType.EC) {
            JSONObject holdingJson = new JSONObject();
            if (shuffling.getHoldingType() == HoldingType.ASSET) {
                putAssetInfo(holdingJson, shuffling.getHoldingId());
            } else if (shuffling.getHoldingType() == HoldingType.CURRENCY) {
                putCurrencyInfo(holdingJson, shuffling.getHoldingId());
            }
            json.put("holdingInfo", holdingJson);
        }
        return json;
    }

    static JSONObject participant(ShufflingParticipant participant) {
        JSONObject json = new JSONObject();
        json.put("shuffling", Long.toUnsignedString(participant.getShufflingId()));
        putAccount(json, "account", participant.getAccountId());
        putAccount(json, "nextAccount", participant.getNextAccountId());
        json.put("state", participant.getState().getCode());
        return json;
    }

    static JSONObject shuffler(Shuffler shuffler, boolean includeParticipantState) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", shuffler.getAccountId());
        putAccount(json, "recipient", Account.getId(shuffler.getRecipientPublicKey()));
        json.put("shufflingFullHash", Convert.toHexString(shuffler.getShufflingFullHash()));
        json.put("shuffling", Long.toUnsignedString(Convert.fullhashtoid(shuffler.getShufflingFullHash())));
        if (shuffler.getFailedTransaction() != null) {
            json.put("failedTransaction", unconfirmedTransaction(shuffler.getFailedTransaction()));
            json.put("failureCause", shuffler.getFailureCause().getMessage());
        }
        if (includeParticipantState) {
            ShufflingParticipant participant = ShufflingParticipant.getParticipant(Convert.fullhashtoid(shuffler.getShufflingFullHash()), shuffler.getAccountId());
            if (participant != null) {
                json.put("participantState", participant.getState().getCode());
            }
        }
        return json;
    }

    static JSONObject block(EcBlock ecBlock, boolean includeTransactions, boolean includeExecutedPhased) {
        JSONObject json = new JSONObject();
        json.put("ecBlock", ecBlock.getStringECId());
        json.put("height", ecBlock.getHeight());
        putAccount(json, "generator", ecBlock.getFoundryId());
        json.put("generatorPublicKey", Convert.toHexString(ecBlock.getFoundryPublicKey()));
        json.put("timestamp", ecBlock.getTimestamp());
        json.put("numberOfTransactions", ecBlock.getTransactions().size());
        json.put("totalAmountNQT", String.valueOf(ecBlock.getTotalAmountNQT()));
        json.put("totalFeeNQT", String.valueOf(ecBlock.getTotalFeeNQT()));
        json.put("payloadLength", ecBlock.getPayloadLength());
        json.put("version", ecBlock.getECVersion());
        json.put("baseTarget", Long.toUnsignedString(ecBlock.getBaseTarget()));
        json.put("cumulativeDifficulty", ecBlock.getCumulativeDifficulty().toString());
        if (ecBlock.getPreviousBlockId() != 0) {
            json.put("previousBlock", Long.toUnsignedString(ecBlock.getPreviousBlockId()));
        }
        if (ecBlock.getNextBlockId() != 0) {
            json.put("nextBlock", Long.toUnsignedString(ecBlock.getNextBlockId()));
        }
        json.put("payloadHash", Convert.toHexString(ecBlock.getPayloadHash()));
        json.put("generationSignature", Convert.toHexString(ecBlock.getFoundrySignature()));
        if (ecBlock.getECVersion() > 1) {
            json.put("previousBlockHash", Convert.toHexString(ecBlock.getPreviousBlockHash()));
        }
        json.put("blockSignature", Convert.toHexString(ecBlock.getBlockSignature()));
        JSONArray transactions = new JSONArray();
        if (includeTransactions) {
            ecBlock.getTransactions().forEach(transaction -> transactions.add(transaction(transaction)));
        } else {
            ecBlock.getTransactions().forEach(transaction -> transactions.add(transaction.getStringId()));
        }
        json.put("transactions", transactions);
        if (includeExecutedPhased) {
            JSONArray phasedTransactions = new JSONArray();
            try (H2Iterator<PhasingPoll.PhasingPollResult> phasingPollResults = PhasingPoll.getApproved(ecBlock.getHeight())) {
                for (PhasingPoll.PhasingPollResult phasingPollResult : phasingPollResults) {
                    long phasedTransactionId = phasingPollResult.getId();
                    if (includeTransactions) {
                        phasedTransactions.add(transaction(EcBlockchainImpl.getInstance().getTransaction(phasedTransactionId)));
                    } else {
                        phasedTransactions.add(Long.toUnsignedString(phasedTransactionId));
                    }
                }
            }
            json.put("executedPhasedTransactions", phasedTransactions);
        }
        return json;
    }

    static JSONObject encryptedData(EncryptedData encryptedData) {
        JSONObject json = new JSONObject();
        json.put("data", Convert.toHexString(encryptedData.getData()));
        json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
        return json;
    }

    static JSONObject goods(ElectronicProductStore.Goods goods, boolean includeCounts) {
        JSONObject json = new JSONObject();
        json.put("goods", Long.toUnsignedString(goods.getId()));
        json.put("name", goods.getName());
        json.put("description", goods.getDescription());
        json.put("quantity", goods.getQuantity());
        json.put("priceNQT", String.valueOf(goods.getPriceNQT()));
        putAccount(json, "seller", goods.getSellerId());
        json.put("tags", goods.getTags());
        JSONArray tagsJSON = new JSONArray();
        Collections.addAll(tagsJSON, goods.getParsedTags());
        json.put("parsedTags", tagsJSON);
        json.put("delisted", goods.isDelisted());
        json.put("timestamp", goods.getTimestamp());
        json.put("hasImage", goods.hasImage());
        if (includeCounts) {
            json.put("numberOfPurchases", ElectronicProductStore.Purchase.getGoodsPurchaseCount(goods.getId(), false, true));
            json.put("numberOfPublicFeedbacks", ElectronicProductStore.Purchase.getGoodsPurchaseCount(goods.getId(), true, true));
        }
        return json;
    }

    static JSONObject tag(ElectronicProductStore.Tag tag) {
        JSONObject json = new JSONObject();
        json.put("tag", tag.getTag());
        json.put("inStockCount", tag.getInStockCount());
        json.put("totalCount", tag.getTotalCount());
        return json;
    }

    static JSONObject hallmark(QualityProof qualityProof) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.getId(qualityProof.getPublicKey()));
        json.put("host", qualityProof.getHost());
        json.put("port", qualityProof.getPort());
        json.put("weight", qualityProof.getWeight());
        String dateString = QualityProof.formatDate(qualityProof.getDate());
        json.put("date", dateString);
        json.put("valid", qualityProof.isValid());
        return json;
    }

    static JSONObject token(Token token) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.getId(token.getPublicKey()));
        json.put("timestamp", token.getTimestamp());
        json.put("valid", token.isValid());
        return json;
    }

    static JSONObject peer(Peer peer) {
        JSONObject json = new JSONObject();
        json.put("address", peer.getPeerHost());
        json.put("port", peer.getPeerPort());
        json.put("state", peer.getState().ordinal());
        json.put("announcedAddress", peer.getAnnouncedAddress());
        json.put("shareAddress", peer.shareAddress());
        if (peer.getQualityProof() != null) {
            json.put("hallmark", peer.getQualityProof().getHallmarkString());
        }
        json.put("weight", peer.getPeerWeight());
        json.put("downloadedVolume", peer.getDownloadedVolume());
        json.put("uploadedVolume", peer.getUploadedVolume());
        json.put("application", peer.getApplication());
        json.put("version", peer.getPeerVersion());
        json.put("platform", peer.getPlatform());
        if (peer.getApiPort() != 0) {
            json.put("apiPort", peer.getApiPort());
        }
        if (peer.getApiSSLPort() != 0) {
            json.put("apiSSLPort", peer.getApiSSLPort());
        }
        json.put("blacklisted", peer.isBlacklisted());
        json.put("lastUpdated", peer.getLastUpdated());
        json.put("lastConnectAttempt", peer.getLastConnectAttempt());
        json.put("inbound", peer.isInbound());
        json.put("inboundWebSocket", peer.isInboundWebSocket());
        json.put("outboundWebSocket", peer.isOutboundWebSocket());
        if (peer.isBlacklisted()) {
            json.put("blacklistingCause", peer.getBlacklistingCause());
        }
        JSONArray servicesArray = new JSONArray();
        for (PeerService service : PeerService.values()) {
            if (peer.providesService(service)) {
                servicesArray.add(service.name());
            }
        }
        json.put("services", servicesArray);
        json.put("blockchainState", peer.getBlockchainState());
        return json;
    }

    static JSONObject poll(Poll poll) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", poll.getAccountId());
        json.put("poll", Long.toUnsignedString(poll.getId()));
        json.put("name", poll.getName());
        json.put("description", poll.getDescription());
        JSONArray options = new JSONArray();
        Collections.addAll(options, poll.getOptions());
        json.put("options", options);
        json.put("finishHeight", poll.getFinishHeight());
        json.put("minNumberOfOptions", poll.getMinNumberOfOptions());
        json.put("maxNumberOfOptions", poll.getMaxNumberOfOptions());
        json.put("minRangeValue", poll.getMinRangeValue());
        json.put("maxRangeValue", poll.getMaxRangeValue());
        putVoteWeighting(json, poll.getVoteWeighting());
        json.put("finished", poll.isFinished());
        json.put("timestamp", poll.getTimestamp());
        return json;
    }

    static JSONObject pollResults(Poll poll, List<Poll.OptionResult> results, VoteWeighting voteWeighting) {
        JSONObject json = new JSONObject();
        json.put("poll", Long.toUnsignedString(poll.getId()));
        if (voteWeighting.getMinBalanceModel() == VoteWeighting.MinBalanceModel.ASSET) {
            json.put("decimals", Property.getAsset(voteWeighting.getHoldingId()).getDecimals());
        } else if (voteWeighting.getMinBalanceModel() == VoteWeighting.MinBalanceModel.CURRENCY) {
            Coin coin = Coin.getCoin(voteWeighting.getHoldingId());
            if (coin != null) {
                json.put("decimals", coin.getDecimals());
            } else {
                Transaction currencyIssuance = EcBlockchainImpl.getInstance().getTransaction(voteWeighting.getHoldingId());
                Mortgaged.MonetarySystemCurrencyIssuance currencyIssuanceAttachment = (Mortgaged.MonetarySystemCurrencyIssuance) currencyIssuance.getAttachment();
                json.put("decimals", currencyIssuanceAttachment.getDecimals());
            }
        }
        putVoteWeighting(json, voteWeighting);
        json.put("finished", poll.isFinished());
        JSONArray options = new JSONArray();
        Collections.addAll(options, poll.getOptions());
        json.put("options", options);

        JSONArray resultsJson = new JSONArray();
        for (Poll.OptionResult option : results) {
            JSONObject optionJSON = new JSONObject();
            if (option != null) {
                optionJSON.put("result", String.valueOf(option.getResult()));
                optionJSON.put("weight", String.valueOf(option.getWeight()));
            } else {
                optionJSON.put("result", "");
                optionJSON.put("weight", "0");
            }
            resultsJson.add(optionJSON);
        }
        json.put("results", resultsJson);
        return json;
    }

    static JSONObject vote(Vote vote, VoteWeighter weighter) {
        JSONObject json = new JSONObject();
        putAccount(json, "voter", vote.getVoterId());
        json.put("transaction", Long.toUnsignedString(vote.getId()));
        JSONArray votesJson = new JSONArray();
        for (byte v : vote.getVoteBytes()) {
            if (v == Constants.EC_NO_VOTE_VALUE) {
                votesJson.add("");
            } else {
                votesJson.add(Byte.toString(v));
            }
        }
        json.put("votes", votesJson);
        if (weighter != null) {
            json.put("weight", String.valueOf(weighter.calcWeight(vote.getVoterId())));
        }
        return json;
    }

    static JSONObject phasingPoll(PhasingPoll poll, boolean countVotes) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(poll.getId()));
        json.put("transactionFullHash", Convert.toHexString(poll.getFullHash()));
        json.put("finishHeight", poll.getFinishHeight());
        json.put("quorum", String.valueOf(poll.getQuorum()));
        putAccount(json, "account", poll.getAccountId());
        JSONArray whitelistJson = new JSONArray();
        for (long accountId : poll.getWhitelist()) {
            JSONObject whitelisted = new JSONObject();
            putAccount(whitelisted, "whitelisted", accountId);
            whitelistJson.add(whitelisted);
        }
        json.put("whitelist", whitelistJson);
        List<byte[]> linkedFullHashes = poll.getLinkedFullHashes();
        if (linkedFullHashes.size() > 0) {
            JSONArray linkedFullHashesJSON = new JSONArray();
            for (byte[] hash : linkedFullHashes) {
                linkedFullHashesJSON.add(Convert.toHexString(hash));
            }
            json.put("linkedFullHashes", linkedFullHashesJSON);
        }
        if (poll.getHashedSecret() != null) {
            json.put("hashedSecret", Convert.toHexString(poll.getHashedSecret()));
        }
        putVoteWeighting(json, poll.getVoteWeighting());
        PhasingPoll.PhasingPollResult phasingPollResult = PhasingPoll.getResult(poll.getId());
        json.put("finished", phasingPollResult != null);
        if (phasingPollResult != null) {
            json.put("approved", phasingPollResult.isApproved());
            json.put("result", String.valueOf(phasingPollResult.getResult()));
            json.put("executionHeight", phasingPollResult.getHeight());
        } else if (countVotes) {
            json.put("result", String.valueOf(poll.countVotes()));
        }
        return json;
    }

    static JSONObject phasingPollResult(PhasingPoll.PhasingPollResult phasingPollResult) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(phasingPollResult.getId()));
        json.put("approved", phasingPollResult.isApproved());
        json.put("result", String.valueOf(phasingPollResult.getResult()));
        json.put("executionHeight", phasingPollResult.getHeight());
        return json;
    }

    static JSONObject phasingPollVote(PhasingVote vote) {
        JSONObject json = new JSONObject();
        JSONData.putAccount(json, "voter", vote.getVoterId());
        json.put("transaction", Long.toUnsignedString(vote.getVoteId()));
        return json;
    }

    private static void putVoteWeighting(JSONObject json, VoteWeighting voteWeighting) {
        json.put("votingModel", voteWeighting.getVotingModel().getCode());
        json.put("minBalance", String.valueOf(voteWeighting.getMinBalance()));
        json.put("minBalanceModel", voteWeighting.getMinBalanceModel().getCode());
        if (voteWeighting.getHoldingId() != 0) {
            json.put("holding", Long.toUnsignedString(voteWeighting.getHoldingId()));
        }
    }

    static JSONObject phasingOnly(AccountRestrictions.PhasingOnly phasingOnly) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", phasingOnly.getAccountId());
        json.put("quorum", String.valueOf(phasingOnly.getPhasingParams().getQuorum()));
        JSONArray whitelistJson = new JSONArray();
        for (long accountId : phasingOnly.getPhasingParams().getWhitelist()) {
            JSONObject whitelisted = new JSONObject();
            putAccount(whitelisted, "whitelisted", accountId);
            whitelistJson.add(whitelisted);
        }
        json.put("whitelist", whitelistJson);
        json.put("maxFees", String.valueOf(phasingOnly.getMaxFees()));
        json.put("minDuration", phasingOnly.getMinDuration());
        json.put("maxDuration", phasingOnly.getMaxDuration());
        putVoteWeighting(json, phasingOnly.getPhasingParams().getVoteWeighting());
        return json;
    }

    static JSONObject purchase(ElectronicProductStore.Purchase purchase) {
        JSONObject json = new JSONObject();
        json.put("purchase", Long.toUnsignedString(purchase.getId()));
        json.put("goods", Long.toUnsignedString(purchase.getGoodsId()));
        ElectronicProductStore.Goods goods = ElectronicProductStore.Goods.getGoods(purchase.getGoodsId());
        json.put("name", goods.getName());
        json.put("hasImage", goods.hasImage());
        putAccount(json, "seller", purchase.getSellerId());
        json.put("priceNQT", String.valueOf(purchase.getPriceNQT()));
        json.put("quantity", purchase.getQuantity());
        putAccount(json, "buyer", purchase.getBuyerId());
        json.put("timestamp", purchase.getTimestamp());
        json.put("deliveryDeadlineTimestamp", purchase.getDeliveryDeadlineTimestamp());
        if (purchase.getNote() != null) {
            json.put("note", encryptedData(purchase.getNote()));
        }
        json.put("pending", purchase.isPending());
        if (purchase.getEncryptedGoods() != null) {
            json.put("goodsData", encryptedData(purchase.getEncryptedGoods()));
            json.put("goodsIsText", purchase.goodsIsText());
        }
        if (purchase.getFeedbackNotes() != null) {
            JSONArray feedbacks = new JSONArray();
            for (EncryptedData encryptedData : purchase.getFeedbackNotes()) {
                feedbacks.add(0, encryptedData(encryptedData));
            }
            json.put("feedbackNotes", feedbacks);
        }
        if (purchase.getPublicFeedbacks() != null) {
            JSONArray publicFeedbacks = new JSONArray();
            for (String publicFeedback : purchase.getPublicFeedbacks()) {
                publicFeedbacks.add(0, publicFeedback);
            }
            json.put("publicFeedbacks", publicFeedbacks);
        }
        if (purchase.getRefundNote() != null) {
            json.put("refundNote", encryptedData(purchase.getRefundNote()));
        }
        if (purchase.getDiscountNQT() > 0) {
            json.put("discountNQT", String.valueOf(purchase.getDiscountNQT()));
        }
        if (purchase.getRefundNQT() > 0) {
            json.put("refundNQT", String.valueOf(purchase.getRefundNQT()));
        }
        return json;
    }

    static JSONObject trade(Trade trade, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("timestamp", trade.getTimestamp());
        json.put("quantityQNT", String.valueOf(trade.getQuantityQNT()));
        json.put("priceNQT", String.valueOf(trade.getPriceNQT()));
        json.put("asset", Long.toUnsignedString(trade.getAssetId()));
        json.put("askOrder", Long.toUnsignedString(trade.getOrderId()));
        json.put("bidOrder", Long.toUnsignedString(trade.getBidOrderId()));
        json.put("askOrderHeight", trade.getOrderHeight());
        json.put("bidOrderHeight", trade.getBidOrderHeight());
        putAccount(json, "seller", trade.getSellerId());
        putAccount(json, "buyer", trade.getBuyerId());
        json.put("block", Long.toUnsignedString(trade.getBlockId()));
        json.put("height", trade.getHeight());
        json.put("tradeType", trade.isBuy() ? "buy" : "sell");
        if (includeAssetInfo) {
            putAssetInfo(json, trade.getAssetId());
        }
        return json;
    }

    static JSONObject assetTransfer(PropertyTransfer propertyTransfer, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("propertyTransfer", Long.toUnsignedString(propertyTransfer.getId()));
        json.put("asset", Long.toUnsignedString(propertyTransfer.getAssetId()));
        putAccount(json, "sender", propertyTransfer.getSenderId());
        putAccount(json, "recipient", propertyTransfer.getRecipientId());
        json.put("quantityQNT", String.valueOf(propertyTransfer.getQuantityQNT()));
        json.put("height", propertyTransfer.getHeight());
        json.put("timestamp", propertyTransfer.getTimestamp());
        if (includeAssetInfo) {
            putAssetInfo(json, propertyTransfer.getAssetId());
        }
        return json;
    }

    static JSONObject expectedAssetTransfer(Transaction transaction, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        Mortgaged.ColoredCoinsAssetTransfer attachment = (Mortgaged.ColoredCoinsAssetTransfer) transaction.getAttachment();
        json.put("assetTransfer", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "sender", transaction.getSenderId());
        putAccount(json, "recipient", transaction.getRecipientId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, attachment.getAssetId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject assetDelete(PropertyDelete propertyDelete, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("propertyDelete", Long.toUnsignedString(propertyDelete.getId()));
        json.put("asset", Long.toUnsignedString(propertyDelete.getAssetId()));
        putAccount(json, "account", propertyDelete.getAccountId());
        json.put("quantityQNT", String.valueOf(propertyDelete.getQuantityQNT()));
        json.put("height", propertyDelete.getHeight());
        json.put("timestamp", propertyDelete.getTimestamp());
        if (includeAssetInfo) {
            putAssetInfo(json, propertyDelete.getAssetId());
        }
        return json;
    }

    static JSONObject expectedAssetDelete(Transaction transaction, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        Mortgaged.ColoredCoinsAssetDelete attachment = (Mortgaged.ColoredCoinsAssetDelete) transaction.getAttachment();
        json.put("assetDelete", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "account", transaction.getSenderId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, attachment.getAssetId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject assetDividend(PropertyDividend propertyDividend) {
        JSONObject json = new JSONObject();
        json.put("propertyDividend", Long.toUnsignedString(propertyDividend.getId()));
        json.put("asset", Long.toUnsignedString(propertyDividend.getAssetId()));
        json.put("amountNQTPerQNT", String.valueOf(propertyDividend.getAmountNQTPerQNT()));
        json.put("totalDividend", String.valueOf(propertyDividend.getTotalDividend()));
        json.put("dividendHeight", propertyDividend.getDividendHeight());
        json.put("numberOfAccounts", propertyDividend.getNumAccounts());
        json.put("height", propertyDividend.getHeight());
        json.put("timestamp", propertyDividend.getTimestamp());
        return json;
    }

    static JSONObject currencyTransfer(CoinTransfer transfer, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        json.put("transfer", Long.toUnsignedString(transfer.getId()));
        json.put("currency", Long.toUnsignedString(transfer.getCurrencyId()));
        putAccount(json, "sender", transfer.getSenderId());
        putAccount(json, "recipient", transfer.getRecipientId());
        json.put("units", String.valueOf(transfer.getUnits()));
        json.put("height", transfer.getHeight());
        json.put("timestamp", transfer.getTimestamp());
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, transfer.getCurrencyId());
        }
        return json;
    }

    static JSONObject expectedCurrencyTransfer(Transaction transaction, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        Mortgaged.MonetarySystemCurrencyTransfer attachment = (Mortgaged.MonetarySystemCurrencyTransfer) transaction.getAttachment();
        json.put("transfer", transaction.getStringId());
        json.put("currency", Long.toUnsignedString(attachment.getCurrencyId()));
        putAccount(json, "sender", transaction.getSenderId());
        putAccount(json, "recipient", transaction.getRecipientId());
        json.put("units", String.valueOf(attachment.getUnits()));
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, attachment.getCurrencyId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject exchange(Conversion conversion, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(conversion.getTransactionId()));
        json.put("timestamp", conversion.getTimestamp());
        json.put("units", String.valueOf(conversion.getUnits()));
        json.put("rateNQT", String.valueOf(conversion.getRate()));
        json.put("currency", Long.toUnsignedString(conversion.getCurrencyId()));
        json.put("offer", Long.toUnsignedString(conversion.getOfferId()));
        putAccount(json, "seller", conversion.getSellerId());
        putAccount(json, "buyer", conversion.getBuyerId());
        json.put("block", Long.toUnsignedString(conversion.getBlockId()));
        json.put("height", conversion.getHeight());
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, conversion.getCurrencyId());
        }
        return json;
    }

    static JSONObject exchangeRequest(ConversionRequest conversionRequest, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(conversionRequest.getId()));
        json.put("subtype", conversionRequest.isBuy() ? Coinage.EC_EXCHANGE_BUY.getSubtype() : Coinage.EC_EXCHANGE_SELL.getSubtype());
        json.put("timestamp", conversionRequest.getTimestamp());
        json.put("units", String.valueOf(conversionRequest.getUnits()));
        json.put("rateNQT", String.valueOf(conversionRequest.getRate()));
        json.put("height", conversionRequest.getHeight());
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, conversionRequest.getCurrencyId());
        }
        return json;
    }

    static JSONObject expectedExchangeRequest(Transaction transaction, boolean includeCurrencyInfo) {
        JSONObject json = new JSONObject();
        json.put("transaction", transaction.getStringId());
        json.put("subtype", transaction.getTransactionType().getSubtype());
        Mortgaged.MonetarySystemExchange attachment = (Mortgaged.MonetarySystemExchange) transaction.getAttachment();
        json.put("units", String.valueOf(attachment.getUnits()));
        json.put("rateNQT", String.valueOf(attachment.getRateNQT()));
        if (includeCurrencyInfo) {
            putCurrencyInfo(json, attachment.getCurrencyId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject unconfirmedTransaction(Transaction transaction) {
        return unconfirmedTransaction(transaction, null);
    }

    static JSONObject unconfirmedTransaction(Transaction transaction, Filter<Enclosure> filter) {
        JSONObject json = new JSONObject();
        json.put("type", transaction.getTransactionType().getType());
        json.put("subtype", transaction.getTransactionType().getSubtype());
        json.put("phased", transaction.getPhasing() != null);
        json.put("timestamp", transaction.getTimestamp());
        json.put("deadline", transaction.getDeadline());
        json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
        if (transaction.getRecipientId() != 0) {
            putAccount(json, "recipient", transaction.getRecipientId());
        }
        json.put("amountNQT", String.valueOf(transaction.getAmountNQT()));
        json.put("feeNQT", String.valueOf(transaction.getFeeNQT()));
        String referencedTransactionFullHash = transaction.getReferencedTransactionFullHash();
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", referencedTransactionFullHash);
        }
        byte[] signature = Convert.emptyToNull(transaction.getSignature());
        if (signature != null) {
            json.put("signature", Convert.toHexString(signature));
            json.put("signatureHash", Convert.toHexString(Crypto.sha256().digest(signature)));
            json.put("fullHash", transaction.getFullHash());
            json.put("transaction", transaction.getStringId());
        }
        JSONObject attachmentJSON = new JSONObject();
        if (filter == null) {
            for (Enclosure appendage : transaction.getAppendages(true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        } else {
            for (Enclosure appendage : transaction.getAppendages(filter, true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        }
        if (!attachmentJSON.isEmpty()) {
            for (Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    entry.setValue(String.valueOf(entry.getValue()));
                }
            }
            json.put("attachment", attachmentJSON);
        }
        putAccount(json, "sender", transaction.getSenderId());
        json.put("height", transaction.getTransactionHeight());
        json.put("version", transaction.getVersion());
        if (transaction.getVersion() > 0) {
            json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
            json.put("ecBlockHeight", transaction.getECBlockHeight());
        }

        return json;
    }

    static JSONObject transaction(Transaction transaction) {
        return transaction(transaction, false);
    }

    static JSONObject transaction(Transaction transaction, boolean includePhasingResult) {
        JSONObject json = transaction(transaction, null);
        if (includePhasingResult && transaction.getPhasing() != null) {
            PhasingPoll.PhasingPollResult phasingPollResult = PhasingPoll.getResult(transaction.getTransactionId());
            if (phasingPollResult != null) {
                json.put("approved", phasingPollResult.isApproved());
                json.put("result", String.valueOf(phasingPollResult.getResult()));
                json.put("executionHeight", phasingPollResult.getHeight());
            }
        }
        return json;
    }

    static JSONObject transaction(Transaction transaction, Filter<Enclosure> filter) {
        JSONObject json = unconfirmedTransaction(transaction, filter);
        json.put("block", Long.toUnsignedString(transaction.getBlockId()));
        json.put("confirmations", EcBlockchainImpl.getInstance().getHeight() - transaction.getTransactionHeight());
        json.put("blockTimestamp", transaction.getBlockTimestamp());
        json.put("transactionIndex", transaction.getTransactionIndex());
        return json;
    }

    static JSONObject generator(FoundryMachine foundryMachine, int elapsedTime) {
        JSONObject response = new JSONObject();
        long deadline = foundryMachine.getDeadline();
        putAccount(response, "account", foundryMachine.getAccountId());
        response.put("deadline", deadline);
        response.put("hitTime", foundryMachine.getHitTime());
        response.put("remaining", Math.max(deadline - elapsedTime, 0));
        return response;
    }

    static JSONObject accountMonitor(FundMonitoring monitor, boolean includeMonitoredAccounts) {
        JSONObject json = new JSONObject();
        json.put("holdingType", monitor.getHoldingType().getCode());
        json.put("account", Long.toUnsignedString(monitor.getAccountId()));
        json.put("accountRS", monitor.getAccountName());
        json.put("holding", Long.toUnsignedString(monitor.getHoldingId()));
        json.put("property", monitor.getProperty());
        json.put("amount", String.valueOf(monitor.getAmount()));
        json.put("threshold", String.valueOf(monitor.getThreshold()));
        json.put("interval", monitor.getInterval());
        if (includeMonitoredAccounts) {
            JSONArray jsonAccounts = new JSONArray();
            List<FundMonitoring.MonitoredAccount> accountList = FundMonitoring.getMonitoredAccounts(monitor);
            accountList.forEach(account -> jsonAccounts.add(JSONData.monitoredAccount(account)));
            json.put("monitoredAccounts", jsonAccounts);
        }
        return json;
    }

    static JSONObject monitoredAccount(FundMonitoring.MonitoredAccount account) {
        JSONObject json = new JSONObject();
        json.put("account", Long.toUnsignedString(account.getAccountId()));
        json.put("accountRS", account.getAccountName());
        json.put("amount", String.valueOf(account.getAmount()));
        json.put("threshold", String.valueOf(account.getThreshold()));
        json.put("interval", account.getInterval());
        return json;
    }

    static JSONObject prunableMessage(PrunableMessage prunableMessage, String secretPhrase, byte[] sharedKey) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(prunableMessage.getId()));
        if (prunableMessage.getMessage() == null || prunableMessage.getEncryptedData() == null) {
            json.put("isText", prunableMessage.getMessage() != null ? prunableMessage.messageIsText() : prunableMessage.encryptedMessageIsText());
        }
        putAccount(json, "sender", prunableMessage.getSenderId());
        if (prunableMessage.getRecipientId() != 0) {
            putAccount(json, "recipient", prunableMessage.getRecipientId());
        }
        json.put("transactionTimestamp", prunableMessage.getTransactionTimestamp());
        json.put("blockTimestamp", prunableMessage.getBlockTimestamp());
        EncryptedData encryptedData = prunableMessage.getEncryptedData();
        if (encryptedData != null) {
            json.put("encryptedMessage", encryptedData(prunableMessage.getEncryptedData()));
            json.put("encryptedMessageIsText", prunableMessage.encryptedMessageIsText());
            byte[] decrypted = null;
            try {
                if (secretPhrase != null) {
                    decrypted = prunableMessage.decrypt(secretPhrase);
                } else if (sharedKey != null && sharedKey.length > 0) {
                    decrypted = prunableMessage.decrypt(sharedKey);
                }
                if (decrypted != null) {
                    json.put("decryptedMessage", Convert.toString(decrypted, prunableMessage.encryptedMessageIsText()));
                }
            } catch (RuntimeException e) {
                putException(json, e, "Decryption failed");
            }
            json.put("isCompressed", prunableMessage.isCompressed());
        }
        if (prunableMessage.getMessage() != null) {
            json.put("message", Convert.toString(prunableMessage.getMessage(), prunableMessage.messageIsText()));
            json.put("messageIsText", prunableMessage.messageIsText());
        }
        return json;
    }

    static JSONObject taggedData(BadgeData badgeData, boolean includeData) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(badgeData.getId()));
        putAccount(json, "account", badgeData.getAccountId());
        json.put("name", badgeData.getName());
        json.put("description", badgeData.getDescription());
        json.put("tags", badgeData.getTags());
        JSONArray tagsJSON = new JSONArray();
        Collections.addAll(tagsJSON, badgeData.getParsedTags());
        json.put("parsedTags", tagsJSON);
        json.put("type", badgeData.getType());
        json.put("channel", badgeData.getChannel());
        json.put("filename", badgeData.getFilename());
        json.put("isText", badgeData.isText());
        if (includeData) {
            json.put("data", badgeData.isText() ? Convert.toString(badgeData.getData()) : Convert.toHexString(badgeData.getData()));
        }
        json.put("transactionTimestamp", badgeData.getTransactionTimestamp());
        json.put("blockTimestamp", badgeData.getBlockTimestamp());
        return json;
    }

    static JSONObject dataTag(BadgeData.Tag tag) {
        JSONObject json = new JSONObject();
        json.put("tag", tag.getTag());
        json.put("count", tag.getCount());
        return json;
    }

    static JSONObject apiRequestHandler(APIRequestHandler handler) {
        JSONObject json = new JSONObject();
        json.put("allowRequiredBlockParameters", handler.allowRequiredBlockParameters());
        if (handler.getFileParameter() != null) {
            json.put("fileParameter", handler.getFileParameter());
        }
        json.put("requireBlockchain", handler.requireBlockchain());
        json.put("requirePost", handler.requirePost());
        json.put("requirePassword", handler.requirePassword());
        json.put("requireFullClient", handler.requireFullClient());
        return json;
    }

    static void putPrunableAttachment(JSONObject json, Transaction transaction) {
        JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
        if (prunableAttachment != null) {
            json.put("prunableAttachmentJSON", prunableAttachment);
        }
    }

    static void putException(JSONObject json, Exception e) {
        putException(json, e, "");
    }

    static void putException(JSONObject json, Exception e, String error) {
        json.put("errorCode", 4);
        if (error.length() > 0) {
            error += ": ";
        }
        json.put("error", e.toString());
        json.put("errorDescription", error + e.getMessage());
    }

    static void putAccount(JSONObject json, String name, long accountId) {
        json.put(name, Long.toUnsignedString(accountId));
        json.put(name + "RS", Convert.rsAccount(accountId));
    }

    private static void putCurrencyInfo(JSONObject json, long currencyId) {
        Coin coin = Coin.getCoin(currencyId);
        if (coin == null) {
            return;
        }
        json.put("name", coin.getName());
        json.put("code", coin.getCoinCode());
        json.put("type", coin.getCoinType());
        json.put("decimals", coin.getDecimals());
        json.put("issuanceHeight", coin.getIssuanceHeight());
        putAccount(json, "issuerAccount", coin.getAccountId());
    }

    private static void putAssetInfo(JSONObject json, long assetId) {
        Property property = Property.getAsset(assetId);
        json.put("name", property.getName());
        json.put("decimals", property.getDecimals());
    }

    private static void putExpectedTransaction(JSONObject json, Transaction transaction) {
        json.put("height", EcBlockchainImpl.getInstance().getHeight() + 1);
        json.put("phased", transaction.getPhasing() != null);
        if (transaction.getBlockId() != 0) { // those values may be wrong for unconfirmed transactions
            json.put("transactionHeight", transaction.getTransactionHeight());
            json.put("confirmations", EcBlockchainImpl.getInstance().getHeight() - transaction.getTransactionHeight());
        }
    }

    static void ledgerEntry(JSONObject json, AccountLedger.LedgerEntry entry, boolean includeTransactions, boolean includeHoldingInfo) {
        putAccount(json, "account", entry.getAccountId());
        json.put("ledgerId", Long.toUnsignedString(entry.getLedgerId()));
        json.put("block", Long.toUnsignedString(entry.getBlockId()));
        json.put("height", entry.getHeight());
        json.put("timestamp", entry.getTimestamp());
        json.put("eventType", entry.getEvent().name());
        json.put("event", Long.toUnsignedString(entry.getEventId()));
        json.put("isTransactionEvent", entry.getEvent().isTransaction());
        json.put("change", String.valueOf(entry.getChange()));
        json.put("balance", String.valueOf(entry.getBalance()));
        LedgerHolding ledgerHolding = entry.getHolding();
        if (ledgerHolding != null) {
            json.put("holdingType", ledgerHolding.name());
            if (entry.getHoldingId() != null) {
                json.put("holding", Long.toUnsignedString(entry.getHoldingId()));
            }
            if (includeHoldingInfo) {
                JSONObject holdingJson = null;
                if (ledgerHolding == LedgerHolding.ASSET_BALANCE
                        || ledgerHolding == LedgerHolding.UNCONFIRMED_ASSET_BALANCE) {
                    holdingJson = new JSONObject();
                    putAssetInfo(holdingJson, entry.getHoldingId());
                } else if (ledgerHolding == LedgerHolding.CURRENCY_BALANCE
                        || ledgerHolding == LedgerHolding.UNCONFIRMED_CURRENCY_BALANCE) {
                    holdingJson = new JSONObject();
                    putCurrencyInfo(holdingJson, entry.getHoldingId());
                }
                if (holdingJson != null) {
                    json.put("holdingInfo", holdingJson);
                }
            }
        }
        if (includeTransactions && entry.getEvent().isTransaction()) {
            Transaction transaction = EcBlockchainImpl.getInstance().getTransaction(entry.getEventId());
            json.put("transaction", JSONData.transaction(transaction));
        }
    }

    interface VoteWeighter {
        long calcWeight(long voterId);
    }

}
