package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.UPnP;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.peer.Peers;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

public final class GetState extends APIRequestHandler {

    static final GetState instance = new GetState();

    private GetState() {
        super(new APITag[]{APITag.INFO}, "includeCounts", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = GetBlockchainStatus.instance.processRequest(req);

        if ("true".equalsIgnoreCase(req.getParameter("includeCounts")) && API.checkPassword(req)) {
            response.put("numberOfTransactions", EcBlockchainImpl.getInstance().getTransactionCount());
            response.put("numberOfAccounts", Account.getCount());
            response.put("numberOfAssets", Property.getCount());
            int askCount = Order.Ask.getCount();
            int bidCount = Order.Bid.getCount();
            response.put("numberOfOrders", askCount + bidCount);
            response.put("numberOfAskOrders", askCount);
            response.put("numberOfBidOrders", bidCount);
            response.put("numberOfTrades", Trade.getCount());
            response.put("numberOfTransfers", PropertyTransfer.getCount());
            response.put("numberOfCurrencies", Coin.getCount());
            response.put("numberOfOffers", CoinBuyOffer.getCount());
            response.put("numberOfExchangeRequests", ConversionRequest.getCount());
            response.put("numberOfExchanges", Conversion.getCount());
            response.put("numberOfCurrencyTransfers", CoinTransfer.getCount());
            response.put("numberOfAliases", AccountName.getCount());
            response.put("numberOfGoods", ElectronicProductStore.Goods.getCount());
            response.put("numberOfPurchases", ElectronicProductStore.Purchase.getCount());
            response.put("numberOfTags", ElectronicProductStore.Tag.getCount());
            response.put("numberOfPolls", Poll.getCount());
            response.put("numberOfVotes", Vote.getCount());
            response.put("numberOfPrunableMessages", PrunableMessage.getCount());
            response.put("numberOfTaggedData", BadgeData.getCount());
            response.put("numberOfDataTags", BadgeData.Tag.getTagCount());
            response.put("numberOfAccountLeases", Account.getAccountLeaseCount());
            response.put("numberOfActiveAccountLeases", Account.getActiveLeaseCount());
            response.put("numberOfShufflings", Shuffling.getCount());
            response.put("numberOfActiveShufflings", Shuffling.getActiveCount());
            response.put("numberOfPhasingOnlyAccounts", AccountRestrictions.PhasingOnly.getCount());
        }
        response.put("numberOfPeers", Peers.getAllPeers().size());
        response.put("numberOfActivePeers", Peers.getActivePeers().size());
        response.put("numberOfUnlockedAccounts", FoundryMachine.getAllFoundryMachines().size());
        response.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        response.put("maxMemory", Runtime.getRuntime().maxMemory());
        response.put("totalMemory", Runtime.getRuntime().totalMemory());
        response.put("freeMemory", Runtime.getRuntime().freeMemory());
        response.put("peerPort", Peers.getDefaultPeerPort());
        response.put("IS_OFFLINE", Constants.IS_OFFLINE);
        response.put("needsAdminPassword", !API.disableAdminPassword);
        InetAddress externalAddress = UPnP.getExternalAddress();
        if (externalAddress != null) {
            response.put("upnpExternalAddress", externalAddress.getHostAddress());
        }
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
