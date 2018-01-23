package com.inesv.ecchain.kernel.http;

import java.util.*;

public enum APIEnum {
    APPROVE_TRANSACTION("approveTransaction", ApproveTransaction.instance),
    BROADCAST_TRANSACTION("broadcastTransaction", BroadcastTransaction.instance),
    CALCULATE_FULL_HASH("calculateFullHash", CalculateFullHash.instance),
    CANCEL_ASK_ORDER("cancelAskOrder", CancelOrder.instance),
    CANCEL_BID_ORDER("cancelBidOrder", CancelBidOrder.instance),
    CAST_VOTE("castVote", CastVote.instance),
    CREATE_POLL("createPoll", CreateVote.instance),
    CURRENCY_BUY("currencyBuy", CoinBuy.instance),
    CURRENCY_SELL("currencySell", CoinSell.instance),
    CURRENCY_RESERVE_INCREASE("currencyReserveIncrease", CoinReserveIncrease.instance),
    CURRENCY_RESERVE_CLAIM("currencyReserveClaim", CoinReserveClaim.instance),
    CURRENCY_MINT("currencyMint", CoinMint.instance),
    DECRYPT_FROM("decryptFrom", DecryptFrom.instance),
    DELETE_ASSET_SHARES("deleteAssetShares", DelAssetShares.instance),
    DGS_LISTING("dgsListing", EcListing.instance),
    DGS_DELISTING("dgsDelisting", Ecelisting.instance),
    DGS_DELIVERY("dgsDelivery", Ecelivery.instance),
    DGS_FEEDBACK("dgsFeedback", EcFeedback.instance),
    DGS_PRICE_CHANGE("dgsPriceChange", EcPriceChange.instance),
    DGS_PURCHASE("dgsPurchase", EcPurchase.instance),
    DGS_QUANTITY_CHANGE("dgsQuantityChange", EcQuantityChange.instance),
    DGS_REFUND("dgsRefund", EcRefund.instance),
    DECODE_HALLMARK("decodeHallmark", DecipherHallmark.instance),
    DECODE_TOKEN("decodeToken", DecipherToken.instance),
    DECODE_FILE_TOKEN("decodeFileToken", DecipherFileToken.instance),
    DECODE_Q_R_CODE("decodeQRCode", DecipherQRCode.instance),
    ENCODE_Q_R_CODE("encodeQRCode", EncodeQRCode.instance),
    ENCRYPT_TO("encryptTo", EncryptTo.instance),
    EVENT_REGISTER("eventRegister", EventRegister.instance),
    EVENT_WAIT("eventWait", EventWait.instance),
    GENERATE_TOKEN("generateToken", CreateToken.instance),
    GENERATE_FILE_TOKEN("generateFileToken", CreateFileToken.instance),
    GET_ACCOUNT("getAccount", GetAccount.instance),
    GET_ACCOUNT_BLOCK_COUNT("getAccountBlockCount", GetAccountBlockCount.instance),
    GET_ACCOUNT_BLOCK_IDS("getAccountBlockIds", GetAccountBlockIds.instance),
    GET_ACCOUNT_BLOCKS("getAccountBlocks", GetAccountBlocks.instance),
    GET_ACCOUNT_ID("getAccountId", GetAccountId.instance),
    GET_ACCOUNT_LEDGER("getAccountLedger", GetAccountLedger.instance),
    GET_ACCOUNT_LEDGER_ENTRY("getAccountLedgerEntry", GetAccountLedgerEntry.instance),
    GET_VOTER_PHASED_TRANSACTIONS("getVoterPhasedTransactions", GetVoterPhasedTransactions.instance),
    GET_LINKED_PHASED_TRANSACTIONS("getLinkedPhasedTransactions", GetLinkedPhasedTransactions.instance),
    GET_POLLS("getPolls", GetVotes.instance),
    GET_ACCOUNT_PHASED_TRANSACTIONS("getAccountPhasedTransactions", GetAccountPhasedTransactions.instance),
    GET_ACCOUNT_PHASED_TRANSACTION_COUNT("getAccountPhasedTransactionCount", GetAccountPhasedTransactionCount.instance),
    GET_ACCOUNT_PUBLIC_KEY("getAccountPublicKey", GetAccountPublicKey.instance),
    GET_ACCOUNT_LESSORS("getAccountLessors", GetAccountLessors.instance),
    GET_ACCOUNT_ASSETS("getAccountPropertys", GetAccountProperty.instance),
    GET_ACCOUNT_CURRENCIES("getAccountCoins", GetAccountCoin.instance),
    GET_ACCOUNT_CURRENCY_COUNT("getAccountCoinCount", GetAccountCoinCount.instance),
    GET_ACCOUNT_ASSET_COUNT("getAccountPropertyCount", GetAccountPropertyCount.instance),
    GET_ACCOUNT_PROPERTIES("getAccountProperties", GetAccountProperties.instance),
    SELL_ALIAS("sellAlias", SellAccountName.instance),
    BUY_ALIAS("buyAlias", BuyAccountName.instance),
    GET_ALIAS("getAlias", GetAccountName.instance),
    GET_ALIAS_COUNT("getAliasCount", GetAccountNameCount.instance),
    GET_ALIASES("getAliases", GetAccountNames.instance),
    GET_ALIASES_LIKE("getAliasesLike", GetAccountNameLike.instance),
    GET_ALL_ASSETS("getAllAssets", GetAllAccountNamets.instance),
    GET_ALL_CURRENCIES("getAllCurrencies", GetAllCoin.instance),
    GET_ASSET("getAsset", GetCoin.instance),
    GET_ASSETS("getPropertys", GetCoins.instance),
    GET_ASSET_IDS("getAssetIds", GetCoinIds.instance),
    GET_ASSETS_BY_ISSUER("getAssetsByIssuer", GetCoinsByIssuer.instance),
    GET_ASSET_ACCOUNTS("getPropertyAccounts", GetCoinAccounts.instance),
    GET_ASSET_ACCOUNT_COUNT("getPropertyAccountCount", GetCoinAccountCount.instance),
    GET_ASSET_PHASED_TRANSACTIONS("getAssetPhasedTransactions", GetCoinPhasedTransactions.instance),
    GET_BALANCE("getBalance", GetBalance.instance),
    GET_BLOCK("getBlock", GetBlock.instance),
    GET_BLOCK_ID("getBlockId", GetBlockId.instance),
    GET_BLOCKS("getBlocks", GetBlocks.instance),
    GET_BLOCKCHAIN_STATUS("getBlockchainStatus", GetBlockchainStatus.instance),
    GET_BLOCKCHAIN_TRANSACTIONS("getBlockchainTransactions", GetBlockchainTransactions.instance),
    GET_REFERENCING_TRANSACTIONS("getReferencingTransactions", GetReferencingTransactions.instance),
    GET_CONSTANTS("getConstants", GetConstants.instance),
    GET_CURRENCY("getCoin", GetCurrency.instance),
    GET_CURRENCIES("getCurrencies", GetCurrencies.instance),
    GET_CURRENCY_FOUNDERS("getCurrencyFounders", GetCurrencyFounders.instance),
    GET_CURRENCY_IDS("getCurrencyIds", GetCurrencyIds.instance),
    GET_CURRENCIES_BY_ISSUER("getCurrenciesByIssuer", GetCurrenciesByIssuer.instance),
    GET_CURRENCY_ACCOUNTS("getCoinAccounts", GetCurrencyAccounts.instance),
    GET_CURRENCY_ACCOUNT_COUNT("getCoinAccountCount", GetCurrencyAccountCount.instance),
    GET_CURRENCY_PHASED_TRANSACTIONS("getCurrencyPhasedTransactions", GetCurrencyPhasedTransactions.instance),
    GET_DGS_GOODS("getDGSGoods", GetEcGoods.instance),
    GET_DGS_GOODS_COUNT("getDGSGoodsCount", GetEcGoodsCount.instance),
    GET_DGS_GOOD("getDGSGood", GetEcGood.instance),
    GET_DGS_GOODS_PURCHASES("getDGSGoodsPurchases", GetEcGoodsPurchases.instance),
    GET_DGS_GOODS_PURCHASE_COUNT("getDGSGoodsPurchaseCount", GetEcGoodsPurchaseCount.instance),
    GET_DGS_PURCHASES("getDGSPurchases", GetEcPurchases.instance),
    GET_DGS_PURCHASE("getDGSPurchase", GetEcPurchase.instance),
    GET_DGS_PURCHASE_COUNT("getDGSPurchaseCount", GetEcPurchaseCount.instance),
    GET_DGS_PENDING_PURCHASES("getDGSPendingPurchases", GetEcPendingPurchases.instance),
    GET_DGS_EXPIRED_PURCHASES("getDGSExpiredPurchases", GetEcExpiredPurchases.instance),
    GET_DGS_TAGS("getDGSTags", GetEcTags.instance),
    GET_DGS_TAG_COUNT("getDGSTagCount", GetEcTagCount.instance),
    GET_DGS_TAGS_LIKE("getDGSTagsLike", GetEcTagsLike.instance),
    GET_GUARANTEED_BALANCE("getGuaranteedBalance", GetGuaranteedBalance.instance),
    GET_E_C_BLOCK("getECBlock", GetECBlock.instance),
    GET_INBOUND_PEERS("getInboundPeers", GetInboundPeers.instance),
    GET_PLUGINS("getPlugins", GetPlugins.instance),
    GET_MY_INFO("getMyInfo", GetMyInfo.instance),
    GET_PEER("getPeer", GetPeer.instance),
    GET_PEERS("getPeers", GetPeers.instance),
    GET_PHASING_POLL("getPhasingPoll", GetPhasingVote.instance),
    GET_PHASING_POLLS("getPhasingPolls", GetPhasingVotes.instance),
    GET_PHASING_POLL_VOTES("getPhasingPollVotes", GetPhasingPollVotes.instance),
    GET_PHASING_POLL_VOTE("getPhasingPollVote", GetPhasingPollVote.instance),
    GET_POLL("getPoll", GetVote.instance),
    GET_POLL_RESULT("getPollResult", GetVoteResult.instance),
    GET_POLL_VOTES("getPollVotes", GetPollVotes.instance),
    GET_POLL_VOTE("getPollVote", GetPollVote.instance),
    GET_STATE("getState", GetState.instance),
    GET_TIME("getTime", GetTime.instance),
    GET_TRADES("getTrades", GetTrades.instance),
    GET_LAST_TRADES("getLastTrades", GetLastTrades.instance),
    GET_EXCHANGES("getConverts", GetConversions.instance),
    GET_EXCHANGES_BY_EXCHANGE_REQUEST("getExchangesByExchangeRequest", GetConversionsByExchangeRequest.instance),
    GET_EXCHANGES_BY_OFFER("getExchangesByOffer", GetConversionsByOffer.instance),
    GET_LAST_EXCHANGES("getLastConvert", GetLastConversions.instance),
    GET_ALL_TRADES("getAllTrades", GetAllTrades.instance),
    GET_ALL_EXCHANGES("getAllExchanges", GetAllConversion.instance),
    GET_ASSET_TRANSFERS("getPropertyTransfers", GetCoinTransfers.instance),
    GET_ASSET_DELETES("getAssetDeletes", GetCoinDeletes.instance),
    GET_EXPECTED_ASSET_TRANSFERS("getExpectedAssetTransfers", GetAnticipateCoinTransfers.instance),
    GET_EXPECTED_ASSET_DELETES("getExpectedAssetDeletes", GetAnticipateCoinDeletes.instance),
    GET_CURRENCY_TRANSFERS("getCoinTransfers", GetCurrencyTransfers.instance),
    GET_EXPECTED_CURRENCY_TRANSFERS("getExpectedCurrencyTransfers", GetAnticipateCurrencyTransfers.instance),
    GET_TRANSACTION("getTransaction", GetTransaction.instance),
    GET_TRANSACTION_BYTES("getTransactionBytes", GetTransactionBytes.instance),
    GET_UNCONFIRMED_TRANSACTION_IDS("getUnconfirmedTransactionIds", GetUnconfirmedTransactionIds.instance),
    GET_UNCONFIRMED_TRANSACTIONS("getUnconfirmedTransactions", GetUnconfirmedTransactions.instance),
    GET_EXPECTED_TRANSACTIONS("getExpectedTransactions", GetAnticipateTransactions.instance),
    GET_ACCOUNT_CURRENT_ASK_ORDER_IDS("getAccountCurrentAskOrderIds", GetAccountCoinOrderIds.instance),
    GET_ACCOUNT_CURRENT_BID_ORDER_IDS("getAccountCurrentBidOrderIds", GetAccountCoinBidOrderIds.instance),
    GET_ACCOUNT_CURRENT_ASK_ORDERS("getAccountCurrentAskOrders", GetAccountCoinOrders.instance),
    GET_ACCOUNT_CURRENT_BID_ORDERS("getAccountCurrentBidOrders", GetAccountCoinBidOrders.instance),
    GET_ALL_OPEN_ASK_ORDERS("getAllOpenAskOrders", GetAllOpenOrders.instance),
    GET_ALL_OPEN_BID_ORDERS("getAllOpenBidOrders", GetAllOpenBidOrders.instance),
    GET_BUY_OFFERS("getBuyOffers", GetBuyOffers.instance),
    GET_EXPECTED_BUY_OFFERS("getExpectedBuyOffers", GetAnticipateBuyOffers.instance),
    GET_SELL_OFFERS("getSellOffers", GetSellOffers.instance),
    GET_EXPECTED_SELL_OFFERS("getExpectedSellOffers", GetAnticipateSellOffers.instance),
    GET_OFFER("getOffer", GetOffer.instance),
    GET_AVAILABLE_TO_BUY("getAvailableToBuy", GetCanToBuy.instance),
    GET_AVAILABLE_TO_SELL("getAvailableToSell", GetCanToSell.instance),
    GET_ASK_ORDER("getAskOrder", GetOrder.instance),
    GET_ASK_ORDER_IDS("getAskOrderIds", GetOrderIds.instance),
    GET_ASK_ORDERS("getAskOrders", GetOrders.instance),
    GET_BID_ORDER("getBidOrder", GetBidOrder.instance),
    GET_BID_ORDER_IDS("getBidOrderIds", GetBidOrderIds.instance),
    GET_BID_ORDERS("getBidOrders", GetBidOrders.instance),
    GET_EXPECTED_ASK_ORDERS("getExpectedAskOrders", GetAnticipateOrders.instance),
    GET_EXPECTED_BID_ORDERS("getExpectedBidOrders", GetAnticipateBidOrders.instance),
    GET_EXPECTED_ORDER_CANCELLATIONS("getExpectedOrderCancellations", GetAnticipateOrderCancellations.instance),
    GET_ORDER_TRADES("getOrderTrades", GetOrderTrades.instance),
    GET_ACCOUNT_EXCHANGE_REQUESTS("getAccountExchangeRequests", GetAccountConversionRequests.instance),
    GET_EXPECTED_EXCHANGE_REQUESTS("getExpectedExchangeRequests", GetAnticipateExchangeRequests.instance),
    GET_MINTING_TARGET("getMintingTarget", GetMintingTarget.instance),
    GET_ALL_SHUFFLINGS("getAllShufflings", GetAllShufflings.instance),
    GET_ACCOUNT_SHUFFLINGS("getAccountShufflings", GetAccountShufflings.instance),
    GET_ASSIGNED_SHUFFLINGS("getAssignedShufflings", GetAssignedShufflings.instance),
    GET_HOLDING_SHUFFLINGS("getHoldingShufflings", GetHoldingShufflings.instance),
    GET_SHUFFLING("getShuffling", GetShuffling.instance),
    GET_SHUFFLING_PARTICIPANTS("getShufflingParticipants", GetShufflingParticipants.instance),
    GET_PRUNABLE_MESSAGE("getPrunableMessage", GetPrunableMessage.instance),
    GET_PRUNABLE_MESSAGES("getPrunableMessages", GetPrunableMessages.instance),
    GET_ALL_PRUNABLE_MESSAGES("getAllPrunableMessages", GetAllPrunableMessages.instance),
    VERIFY_PRUNABLE_MESSAGE("verifyPrunableMessage", VerifyPrunableMessage.instance),
    ISSUE_ASSET("issueAsset", IssueAsset.instance),
    ISSUE_CURRENCY("issueCurrency", IssueCurrency.instance),
    LEASE_BALANCE("leaseBalance", LeaseBalance.instance),
    LONG_CONVERT("longConvert", LongConvert.instance),
    HEX_CONVERT("hexConvert", HexConvert.instance),
    MARK_HOST("markHost", MarkHost.instance),
    PARSE_TRANSACTION("parseTransaction", ParseTransaction.instance),
    PLACE_ASK_ORDER("placeAskOrder", PlaceAskOrder.instance),
    PLACE_BID_ORDER("placeBidOrder", PlaceBidOrder.instance),
    PUBLISH_EXCHANGE_OFFER("publishExchangeOffer", PublishExchangeOffer.instance),
    RS_CONVERT("rsConvert", RSConvert.instance),
    READ_MESSAGE("readMessage", ReadMessage.instance),
    SEND_MESSAGE("sendMessage", SendMessage.instance),
    SEND_MONEY("sendMoney", SendMoney.instance),
    SET_ACCOUNT_INFO("setAccountInfo", SetAccountInfo.instance),
    SET_ACCOUNT_PROPERTY("setAccountProperty", SetAccountProperty.instance),
    DELETE_ACCOUNT_PROPERTY("deleteAccountProperty", DelAccountProperty.instance),
    SET_ALIAS("setAlias", SetAccountName.instance),
    SHUFFLING_CREATE("shufflingCreate", ShufflingCreate.instance),
    SHUFFLING_REGISTER("shufflingRegister", ShufflingRegister.instance),
    SHUFFLING_PROCESS("shufflingProcess", ShufflingProcess.instance),
    SHUFFLING_VERIFY("shufflingVerify", ShufflingVerify.instance),
    SHUFFLING_CANCEL("shufflingCancel", ShufflingCancel.instance),
    START_SHUFFLER("startShuffler", StartShuffler.instance),
    STOP_SHUFFLER("stopShuffler", StopShuffler.instance),
    GET_SHUFFLERS("getShufflers", GetShufflers.instance),
    DELETE_ALIAS("deleteAlias", DelAccountName.instance),
    SIGN_TRANSACTION("signTransaction", SignTransaction.instance),
    START_FORGING("startForging", StartForging.instance),
    STOP_FORGING("stopForging", StopForging.instance),
    GET_FORGING("getForging", GetForging.instance),
    TRANSFER_ASSET("transferAsset", TransferAsset.instance),
    TRANSFER_CURRENCY("transferCurrency", TransferCurrency.instance),
    CAN_DELETE_CURRENCY("canDeleteCurrency", CanDeleteCoin.instance),
    DELETE_CURRENCY("deleteCurrency", DelCoin.instance),
    DIVIDEND_PAYMENT("dividendPayment", DividendPayment.instance),
    SEARCH_DGS_GOODS("searchDGSGoods", SearchDGSGoods.instance),
    SEARCH_ASSETS("searchAssets", SearchAssets.instance),
    SEARCH_CURRENCIES("searchCoins", SearchCurrencies.instance),
    SEARCH_POLLS("searchPolls", SearchPolls.instance),
    SEARCH_ACCOUNTS("searchAccounts", SearchAccounts.instance),
    SEARCH_TAGGED_DATA("searchTaggedData", SearchTaggedData.instance),
    UPLOAD_TAGGED_DATA("uploadTaggedData", UploadTaggedData.instance),
    EXTEND_TAGGED_DATA("extendTaggedData", ExtendTaggedData.instance),
    GET_ACCOUNT_TAGGED_DATA("getAccountTaggedData", GetAccountTaggedData.instance),
    GET_ALL_TAGGED_DATA("getAllTaggedData", GetAllTaggedData.instance),
    GET_CHANNEL_TAGGED_DATA("getChannelTaggedData", GetChannelTaggedData.instance),
    GET_TAGGED_DATA("getTaggedData", GetTaggedData.instance),
    DOWNLOAD_TAGGED_DATA("downloadTaggedData", DownloadTaggedData.instance),
    GET_DATA_TAGS("getDataTags", GetDataTags.instance),
    GET_DATA_TAG_COUNT("getDataTagCount", GetDataTagCount.instance),
    GET_DATA_TAGS_LIKE("getDataTagsLike", GetDataTagsLike.instance),
    VERIFY_TAGGED_DATA("verifyTaggedData", VerifyTaggedData.instance),
    GET_TAGGED_DATA_EXTEND_TRANSACTIONS("getTaggedDataExtendTransactions", GetTaggedDataExtendTransactions.instance),
    CLEAR_UNCONFIRMED_TRANSACTIONS("clearUnconfirmedTransactions", ClearUnconfirmedTransactions.instance),
    REQUEUE_UNCONFIRMED_TRANSACTIONS("requeueUnconfirmedTransactions", RequeueUnconfirmedTransactions.instance),
    REBROADCAST_UNCONFIRMED_TRANSACTIONS("rebroadcastUnconfirmedTransactions", RebroadcastUnconfirmedTransactions.instance),
    GET_ALL_WAITING_TRANSACTIONS("getAllWaitingTransactions", GetAllWaitingTransactions.instance),
    GET_ALL_BROADCASTED_TRANSACTIONS("getAllBroadcastedTransactions", GetAllBroadcastedTransactions.instance),
    FULL_RESET("fullReset", FullReset.instance),
    POP_OFF("popOff", PopOff.instance),
    SCAN("scan", Scan.instance),
    LUCENE_REINDEX("luceneReindex", LuceneReindex.instance),
    ADD_PEER("addPeer", AddPeer.instance),
    BLACKLIST_PEER("blacklistPeer", BlacklistPeer.instance),
    DUMP_PEERS("dumpPeers", DumpPeers.instance),
    GET_LOG("getLog", GetLog.instance),
    GET_STACK_TRACES("getStackTraces", GetStackTraces.instance),
    RETRIEVE_PRUNED_DATA("retrievePrunedData", RetrievePrunedData.instance),
    RETRIEVE_PRUNED_TRANSACTION("retrievePrunedTransaction", RetrievePrunedTransaction.instance),
    SET_LOGGING("setLogging", SetLogging.instance),
    SHUTDOWN("shutdown", Shutdown.instance),
    TRIM_DERIVED_TABLES("TRIM_DERIVED_TABLES", TrimDerivedTables.instance),
    HASH("hash", Hash.instance),
    FULL_HASH_TO_ID("fullhashtoid", FullHashToId.instance),
    SET_PHASING_ONLY_CONTROL("setPhasingOnlyControl", SetPhasingOnlyControl.instance),
    GET_PHASING_ONLY_CONTROL("getPhasingOnlyControl", GetPhasingOnlyControl.instance),
    GET_ALL_PHASING_ONLY_CONTROLS("getAllPhasingOnlyControls", GetAllPhasingOnlyControls.instance),
    DETECT_MIME_TYPE("checkMimeType", CheckMimeType.instance),
    START_FUNDING_MONITOR("startFundingMonitor", StartFundingMonitor.instance),
    STOP_FUNDING_MONITOR("stopFundingMonitor", StopFundingMonitor.instance),
    GET_FUNDING_MONITOR("getFundingMonitor", GetFundingMonitor.instance),
    DOWNLOAD_PRUNABLE_MESSAGE("downloadPrunableMessage", DownloadPrunableMessage.instance),
    GET_SHARED_KEY("getSharedKey", GetSharedKey.instance),
    SET_API_PROXY_PEER("setAPIProxyPeer", SetAPIProxyPeer.instance),
    SEND_TRANSACTION("sendTransaction", SendTransaction.instance),
    GET_ASSET_DIVIDENDS("getAssetDividends", GetCoinDividends.instance),
    BLACKLIST_API_PROXY_PEER("blacklistAPIProxyPeer", BlacklistAPIProxyPeer.instance),
    GET_NEXT_BLOCK_GENERATORS("getNextBlockGenerators", GetNextBlockGeneratorsTemp.instance);

    private static final Map<String, APIEnum> apiByName = new HashMap<>();

    static {
        final EnumSet<APITag> tagsNotRequiringBlockchain = EnumSet.of(APITag.UTILS);
        for (APIEnum api : values()) {
            if (apiByName.put(api.getAPIEnumName(), api) != null) {
                AssertionError assertionError = new AssertionError("Duplicate API name: " + api.getAPIEnumName());
                assertionError.printStackTrace();
                throw assertionError;
            }

            final APIRequestHandler handler = api.getAPIHandler();
            if (!Collections.disjoint(handler.getAPITags(), tagsNotRequiringBlockchain)
                    && handler.requireBlockchain()) {
                AssertionError assertionError = new AssertionError("API " + api.getAPIEnumName()
                        + " is not supposed to require blockchain");
                assertionError.printStackTrace();
                throw assertionError;
            }
        }
    }

    private final String name;
    private final APIRequestHandler handler;
    APIEnum(String name, APIRequestHandler handler) {
        this.name = name;
        this.handler = handler;
    }

    public static APIEnum fromEcName(String name) {
        return apiByName.get(name);
    }

    public static EnumSet<APIEnum> base64ECStringToEnumSet(String apiSetBase64) {
        byte[] decoded = Base64.getDecoder().decode(apiSetBase64);
        BitSet bs = BitSet.valueOf(decoded);
        EnumSet<APIEnum> result = EnumSet.noneOf(APIEnum.class);
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            result.add(APIEnum.values()[i]);
            if (i == Integer.MAX_VALUE) {
                break; // or (i+1) would overflow
            }
        }
        return result;
    }

    public static String enumSetToBase64ECString(EnumSet<APIEnum> apiSet) {
        BitSet bitSet = new BitSet();
        for (APIEnum api : apiSet) {
            bitSet.set(api.ordinal());
        }
        return Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }

    public String getAPIEnumName() {
        return name;
    }

    public APIRequestHandler getAPIHandler() {
        return handler;
    }
}
