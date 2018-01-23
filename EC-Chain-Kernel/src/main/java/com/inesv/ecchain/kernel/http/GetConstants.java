package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.HashFunction;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.peer.PeerState;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

public final class GetConstants extends APIRequestHandler {

    static final GetConstants instance = new GetConstants();

    private GetConstants() {
        super(new APITag[]{APITag.INFO});
    }

    public static JSONStreamAware getConstants() {
        return Holder.CONSTANTS;
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        return Holder.CONSTANTS;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    private static final class Holder {

        private static final JSONStreamAware CONSTANTS;

        static {
            try {
                JSONObject response = new JSONObject();
                response.put("genesisBlockId", Long.toUnsignedString(Genesis.EC_GENESIS_BLOCK_ID));
                response.put("genesisAccountId", Long.toUnsignedString(Genesis.EC_CREATOR_ID));
                response.put("epochBeginning", Constants.EC_EPOCH_BEGINNING);
                response.put("maxBlockPayloadLength", Constants.EC_MAX_PAYLOAD_LENGTH);
                response.put("maxArbitraryMessageLength", Constants.EC_MAX_ARBITRARY_MESSAGE_LENGTH);
                response.put("maxPrunableMessageLength", Constants.EC_MAX_PRUNABLE_MESSAGE_LENGTH);

                JSONObject transactionJSON = new JSONObject();
                JSONObject transactionSubTypesJSON = new JSONObject();
                outer:
                for (int type = 0; ; type++) {
                    JSONObject typeJSON = new JSONObject();
                    JSONObject subtypesJSON = new JSONObject();
                    for (int subtype = 0; ; subtype++) {
                        TransactionType transactionType = TransactionType.selectTransactionType((byte) type, (byte) subtype);
                        if (transactionType == null) {
                            if (subtype == 0) {
                                break outer;
                            } else {
                                break;
                            }
                        }
                        JSONObject subtypeJSON = new JSONObject();
                        subtypeJSON.put("name", transactionType.getName());
                        subtypeJSON.put("canHaveRecipient", transactionType.canHaveRecipient());
                        subtypeJSON.put("mustHaveRecipient", transactionType.mustHaveRecipient());
                        subtypeJSON.put("isPhasingSafe", transactionType.isPhasingSafe());
                        subtypeJSON.put("isPhasable", transactionType.isPhasable());
                        subtypeJSON.put("type", type);
                        subtypeJSON.put("subtype", subtype);
                        subtypesJSON.put(subtype, subtypeJSON);
                        transactionSubTypesJSON.put(transactionType.getName(), subtypeJSON);
                    }
                    typeJSON.put("subtypes", subtypesJSON);
                    transactionJSON.put(type, typeJSON);
                }
                response.put("transactionTypes", transactionJSON);
                response.put("transactionSubTypes", transactionSubTypesJSON);

                JSONObject currencyTypes = new JSONObject();
                for (CoinType coinType : CoinType.values()) {
                    currencyTypes.put(coinType.toString(), coinType.getCode());
                }
                response.put("currencyTypes", currencyTypes);

                JSONObject votingModels = new JSONObject();
                for (VoteWeighting.VotingModel votingModel : VoteWeighting.VotingModel.values()) {
                    votingModels.put(votingModel.toString(), votingModel.getCode());
                }
                response.put("votingModels", votingModels);

                JSONObject minBalanceModels = new JSONObject();
                for (VoteWeighting.MinBalanceModel minBalanceModel : VoteWeighting.MinBalanceModel.values()) {
                    minBalanceModels.put(minBalanceModel.toString(), minBalanceModel.getCode());
                }
                response.put("minBalanceModels", minBalanceModels);

                JSONObject hashFunctions = new JSONObject();
                for (HashFunction hashFunction : HashFunction.values()) {
                    hashFunctions.put(hashFunction.toString(), hashFunction.getId());
                }
                response.put("hashAlgorithms", hashFunctions);

                JSONObject phasingHashFunctions = new JSONObject();
                for (HashFunction hashFunction : PhasingPoll.ACCEPTED_HASH_FUNCTIONS) {
                    phasingHashFunctions.put(hashFunction.toString(), hashFunction.getId());
                }
                response.put("phasingHashAlgorithms", phasingHashFunctions);

                response.put("maxPhasingDuration", Constants.EC_MAX_PHASING_DURATION);

                JSONObject mintingHashFunctions = new JSONObject();
                for (HashFunction hashFunction : CoinMinting.ACCEPTED_HASH_FUNCTIONS) {
                    mintingHashFunctions.put(hashFunction.toString(), hashFunction.getId());
                }
                response.put("mintingHashAlgorithms", mintingHashFunctions);

                JSONObject peerStates = new JSONObject();
                for (PeerState peerState : PeerState.values()) {
                    peerStates.put(peerState.toString(), peerState.ordinal());
                }
                response.put("peerStates", peerStates);
                response.put("maxTaggedDataDataLength", Constants.EC_MAX_TAGGED_DATA_DATA_LENGTH);

                JSONObject requestTypes = new JSONObject();
                for (Map.Entry<String, APIRequestHandler> handlerEntry : APIServlet.API_REQUEST_HANDLERS.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", true);
                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                for (Map.Entry<String, APIRequestHandler> handlerEntry : APIServlet.DISABLED_REQUEST_HANDLERS.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", false);
                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                response.put("requestTypes", requestTypes);

                JSONObject holdingTypes = new JSONObject();
                for (HoldingType holdingType : HoldingType.values()) {
                    holdingTypes.put(holdingType.toString(), holdingType.getCode());
                }
                response.put("holdingTypes", holdingTypes);

                JSONObject shufflingStages = new JSONObject();
                for (Shuffling.Stage stage : Shuffling.Stage.values()) {
                    shufflingStages.put(stage.toString(), stage.getCode());
                }
                response.put("shufflingStages", shufflingStages);

                JSONObject shufflingParticipantStates = new JSONObject();
                for (ShufflingParticipantState state : ShufflingParticipantState.values()) {
                    shufflingParticipantStates.put(state.toString(), state.getCode());
                }
                response.put("shufflingParticipantStates", shufflingParticipantStates);

                JSONObject apiTags = new JSONObject();
                for (APITag apiTag : APITag.values()) {
                    JSONObject tagJSON = new JSONObject();
                    tagJSON.put("name", apiTag.getDisplayName());
                    tagJSON.put("enabled", !API.disabledAPITags.contains(apiTag));
                    apiTags.put(apiTag.name(), tagJSON);
                }
                response.put("apiTags", apiTags);

                JSONArray disabledAPIs = new JSONArray();
                Collections.addAll(disabledAPIs, API.disabledAPIs);
                response.put("disabledAPIs", disabledAPIs);

                JSONArray disabledAPITags = new JSONArray();
                API.disabledAPITags.forEach(apiTag -> disabledAPITags.add(apiTag.getDisplayName()));
                response.put("disabledAPITags", disabledAPITags);

                JSONArray notForwardedRequests = new JSONArray();
                notForwardedRequests.addAll(APIProxy.NOT_FORWARDED_REQUESTS);
                response.put("proxyNotForwardedRequests", notForwardedRequests);

                CONSTANTS = JSON.prepare(response);
            } catch (Exception e) {
                LoggerUtil.logError(e.toString(), e);
                throw e;
            }
        }
    }
}
