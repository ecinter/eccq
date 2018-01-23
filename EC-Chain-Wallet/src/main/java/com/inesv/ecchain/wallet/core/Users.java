package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.common.util.ThreadPool;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import com.inesv.ecchain.kernel.peer.Peer;
import com.inesv.ecchain.kernel.peer.PeerState;
import com.inesv.ecchain.kernel.peer.Peers;
import com.inesv.ecchain.kernel.peer.PeersEvent;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class Users {
    private static Server usersServer;
    public static Set<String> allowedUsersHosts;
    private static final ConcurrentMap<String, User> USER_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Integer> BLOCK_INDEX_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Integer> TRANSACTION_INDEX_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> PEER_INDEX_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, String> PEER_ADDRESS_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger PEER_COUNTER = new AtomicInteger();
    private static final AtomicInteger BLOCK_COUNTER = new AtomicInteger();
    private static final AtomicInteger TRANSACTION_COUNTER = new AtomicInteger();
    private static final Collection<User> ALL_USERS = Collections.unmodifiableCollection(USER_CONCURRENT_HASH_MAP.values());


    @PostConstruct
    public static void initPostConstruct() {

        List<String> allowedUserHostsList = PropertiesUtil.getStringListProperty("ec.allowedUserHosts");
        if (!allowedUserHostsList.contains("*")) {
            allowedUsersHosts = Collections.unmodifiableSet(new HashSet<>(allowedUserHostsList));
        } else {
            allowedUsersHosts = null;
        }

        boolean enableUIServer = PropertiesUtil.getKeyForBoolean("ec.enableUIServer");
        if (enableUIServer) {
            final int port = PropertiesUtil.getKeyForInt("ec.uiServerPort", 0);
            final String host = PropertiesUtil.getKeyForString("ec.uiServerHost", null);
            usersServer = new Server();
            ServerConnector connector;

            boolean enableSSL = PropertiesUtil.getKeyForBoolean("ec.uiSSL");
            if (enableSSL) {
                LoggerUtil.logInfo("Using SSL (https) for the user interface server");
                HttpConfiguration https_config = new HttpConfiguration();
                https_config.setSecureScheme("https");
                https_config.setSecurePort(port);
                https_config.addCustomizer(new SecureRequestCustomizer());
                SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.setKeyStorePath(PropertiesUtil.getKeyForString("ec.keyStorePath", null));
                sslContextFactory.setKeyStorePassword(PropertiesUtil.getKeyForString("ec.keyStorePassword", null));
                sslContextFactory.addExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
                sslContextFactory.addExcludeProtocols("SSLv3");
                connector = new ServerConnector(usersServer, new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(https_config));
            } else {
                connector = new ServerConnector(usersServer);
            }

            connector.setPort(port);
            connector.setHost(host);
            connector.setIdleTimeout(PropertiesUtil.getKeyForInt("ec.uiServerIdleTimeout", 0));
            connector.setReuseAddress(true);
            usersServer.addConnector(connector);


            HandlerList userHandlers = new HandlerList();

            ResourceHandler userFileHandler = new ResourceHandler();
            userFileHandler.setDirectoriesListed(false);
            userFileHandler.setWelcomeFiles(new String[]{"index.html"});
            userFileHandler.setResourceBase(PropertiesUtil.getKeyForString("ec.uiResourceBase", null));

            userHandlers.addHandler(userFileHandler);
            String classpath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
            String javadocResourceBase = classpath + PropertiesUtil.getKeyForString("ec.javadocResourceBase", null);
            if (javadocResourceBase != null) {
                ContextHandler contextHandler = new ContextHandler("/doc");
                ResourceHandler docFileHandler = new ResourceHandler();
                docFileHandler.setDirectoriesListed(false);
                docFileHandler.setWelcomeFiles(new String[]{"index.html"});
                docFileHandler.setResourceBase(javadocResourceBase);
                contextHandler.setHandler(docFileHandler);
                userHandlers.addHandler(contextHandler);
            }

            ServletHandler userHandler = new ServletHandler();
            ServletHolder userHolder = userHandler.addServletWithMapping(UserServlet.class, "/ec");
            userHolder.setAsyncSupported(true);

            if (PropertiesUtil.getKeyForBoolean("ec.uiServerCORS")) {
                FilterHolder filterHolder = userHandler.addFilterWithMapping(CrossOriginFilter.class, "/*", FilterMapping.DEFAULT);
                filterHolder.setInitParameter("allowedHeaders", "*");
                filterHolder.setAsyncSupported(true);
            }

            userHandlers.addHandler(userHandler);

            userHandlers.addHandler(new DefaultHandler());

            usersServer.setHandler(userHandlers);
            usersServer.setStopAtShutdown(true);

            ThreadPool.runBeforeStart(() -> {
                try {
                    usersServer.start();
                    LoggerUtil.logInfo("Started user interface server at " + host + ":" + port);
                } catch (Exception e) {
                    LoggerUtil.logError("Failed to start user interface server", e);
                    throw new RuntimeException(e.toString(), e);
                }
            }, true);

        } else {
            usersServer = null;
            LoggerUtil.logInfo("User interface server not enabled");
        }


        if (usersServer != null) {
            Account.addListener(account -> {
                JSONObject response = new JSONObject();
                response.put("response", "setBalance");
                response.put("balanceNQT", account.getUnconfirmedBalanceNQT());
                byte[] accountPublicKey = Account.getPublicKey(account.getId());
                Users.USER_CONCURRENT_HASH_MAP.values().forEach(user -> {
                    if (user.getSecretECPhrase() != null && Arrays.equals(user.getPublicECKey(), accountPublicKey)) {
                        user.send(response);
                    }
                });
            }, Event.UNCONFIRMED_BALANCE);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray removedActivePeers = new JSONArray();
                JSONObject removedActivePeer = new JSONObject();
                removedActivePeer.put("index", Users.getIndex(peer));
                removedActivePeers.add(removedActivePeer);
                response.put("removedActivePeers", removedActivePeers);
                JSONArray removedKnownPeers = new JSONArray();
                JSONObject removedKnownPeer = new JSONObject();
                removedKnownPeer.put("index", Users.getIndex(peer));
                removedKnownPeers.add(removedKnownPeer);
                response.put("removedKnownPeers", removedKnownPeers);
                JSONArray addedBlacklistedPeers = new JSONArray();
                JSONObject addedBlacklistedPeer = new JSONObject();
                addedBlacklistedPeer.put("index", Users.getIndex(peer));
                addedBlacklistedPeer.put("address", peer.getPeerHost());
                addedBlacklistedPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                addedBlacklistedPeer.put("software", peer.getSoftware());
                addedBlacklistedPeers.add(addedBlacklistedPeer);
                response.put("addedBlacklistedPeers", addedBlacklistedPeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.BLACKLIST);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray removedActivePeers = new JSONArray();
                JSONObject removedActivePeer = new JSONObject();
                removedActivePeer.put("index", Users.getIndex(peer));
                removedActivePeers.add(removedActivePeer);
                response.put("removedActivePeers", removedActivePeers);
                JSONArray addedKnownPeers = new JSONArray();
                JSONObject addedKnownPeer = new JSONObject();
                addedKnownPeer.put("index", Users.getIndex(peer));
                addedKnownPeer.put("address", peer.getPeerHost());
                addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                addedKnownPeer.put("software", peer.getSoftware());
                addedKnownPeers.add(addedKnownPeer);
                response.put("addedKnownPeers", addedKnownPeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.DEACTIVATE);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray removedBlacklistedPeers = new JSONArray();
                JSONObject removedBlacklistedPeer = new JSONObject();
                removedBlacklistedPeer.put("index", Users.getIndex(peer));
                removedBlacklistedPeers.add(removedBlacklistedPeer);
                response.put("removedBlacklistedPeers", removedBlacklistedPeers);
                JSONArray addedKnownPeers = new JSONArray();
                JSONObject addedKnownPeer = new JSONObject();
                addedKnownPeer.put("index", Users.getIndex(peer));
                addedKnownPeer.put("address", peer.getPeerHost());
                addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                addedKnownPeer.put("software", peer.getSoftware());
                addedKnownPeers.add(addedKnownPeer);
                response.put("addedKnownPeers", addedKnownPeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.UNBLACKLIST);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray removedKnownPeers = new JSONArray();
                JSONObject removedKnownPeer = new JSONObject();
                removedKnownPeer.put("index", Users.getIndex(peer));
                removedKnownPeers.add(removedKnownPeer);
                response.put("removedKnownPeers", removedKnownPeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.REMOVE);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray changedActivePeers = new JSONArray();
                JSONObject changedActivePeer = new JSONObject();
                changedActivePeer.put("index", Users.getIndex(peer));
                changedActivePeer.put("downloaded", peer.getDownloadedVolume());
                changedActivePeers.add(changedActivePeer);
                response.put("changedActivePeers", changedActivePeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.DOWNLOADED_VOLUME);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray changedActivePeers = new JSONArray();
                JSONObject changedActivePeer = new JSONObject();
                changedActivePeer.put("index", Users.getIndex(peer));
                changedActivePeer.put("uploaded", peer.getUploadedVolume());
                changedActivePeers.add(changedActivePeer);
                response.put("changedActivePeers", changedActivePeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.UPLOADED_VOLUME);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray changedActivePeers = new JSONArray();
                JSONObject changedActivePeer = new JSONObject();
                changedActivePeer.put("index", Users.getIndex(peer));
                changedActivePeer.put("weight", peer.getPeerWeight());
                changedActivePeers.add(changedActivePeer);
                response.put("changedActivePeers", changedActivePeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.WEIGHT);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray removedKnownPeers = new JSONArray();
                JSONObject removedKnownPeer = new JSONObject();
                removedKnownPeer.put("index", Users.getIndex(peer));
                removedKnownPeers.add(removedKnownPeer);
                response.put("removedKnownPeers", removedKnownPeers);
                JSONArray addedActivePeers = new JSONArray();
                JSONObject addedActivePeer = new JSONObject();
                addedActivePeer.put("index", Users.getIndex(peer));
                if (peer.getState() != PeerState.CONNECTED) {
                    addedActivePeer.put("disconnected", true);
                }
                addedActivePeer.put("address", peer.getPeerHost());
                addedActivePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                addedActivePeer.put("weight", peer.getPeerWeight());
                addedActivePeer.put("downloaded", peer.getDownloadedVolume());
                addedActivePeer.put("uploaded", peer.getUploadedVolume());
                addedActivePeer.put("software", peer.getSoftware());
                addedActivePeers.add(addedActivePeer);
                response.put("addedActivePeers", addedActivePeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.ADDED_ACTIVE_PEER);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray changedActivePeers = new JSONArray();
                JSONObject changedActivePeer = new JSONObject();
                changedActivePeer.put("index", Users.getIndex(peer));
                changedActivePeer.put(peer.getState() == PeerState.CONNECTED ? "connected" : "disconnected", true);
                changedActivePeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                changedActivePeers.add(changedActivePeer);
                response.put("changedActivePeers", changedActivePeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.CHANGED_ACTIVE_PEER);

            Peers.addPeersListener(peer -> {
                JSONObject response = new JSONObject();
                JSONArray addedKnownPeers = new JSONArray();
                JSONObject addedKnownPeer = new JSONObject();
                addedKnownPeer.put("index", Users.getIndex(peer));
                addedKnownPeer.put("address", peer.getPeerHost());
                addedKnownPeer.put("announcedAddress", Convert.truncate(peer.getAnnouncedAddress(), "-", 25, true));
                addedKnownPeer.put("software", peer.getSoftware());
                addedKnownPeers.add(addedKnownPeer);
                response.put("addedKnownPeers", addedKnownPeers);
                Users.sendNewDataToAll(response);
            }, PeersEvent.NEW_PEER);

            TransactionProcessorImpl.getInstance().addECListener(transactions -> {
                JSONObject response = new JSONObject();
                JSONArray removedUnconfirmedTransactions = new JSONArray();
                for (Transaction transaction : transactions) {
                    JSONObject removedUnconfirmedTransaction = new JSONObject();
                    removedUnconfirmedTransaction.put("index", Users.getIndex(transaction));
                    removedUnconfirmedTransactions.add(removedUnconfirmedTransaction);
                }
                response.put("removedUnconfirmedTransactions", removedUnconfirmedTransactions);
                Users.sendNewDataToAll(response);
            }, TransactionProcessorEvent.REMOVED_UNCONFIRMED_TRANSACTIONS);

            TransactionProcessorImpl.getInstance().addECListener(transactions -> {
                JSONObject response = new JSONObject();
                JSONArray addedUnconfirmedTransactions = new JSONArray();
                for (Transaction transaction : transactions) {
                    JSONObject addedUnconfirmedTransaction = new JSONObject();
                    addedUnconfirmedTransaction.put("index", Users.getIndex(transaction));
                    addedUnconfirmedTransaction.put("timestamp", transaction.getTimestamp());
                    addedUnconfirmedTransaction.put("deadline", transaction.getDeadline());
                    addedUnconfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
                    addedUnconfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
                    addedUnconfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
                    addedUnconfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
                    addedUnconfirmedTransaction.put("id", transaction.getStringId());
                    addedUnconfirmedTransactions.add(addedUnconfirmedTransaction);
                }
                response.put("addedUnconfirmedTransactions", addedUnconfirmedTransactions);
                Users.sendNewDataToAll(response);
            }, TransactionProcessorEvent.ADDED_UNCONFIRMED_TRANSACTIONS);

            TransactionProcessorImpl.getInstance().addECListener(transactions -> {
                JSONObject response = new JSONObject();
                JSONArray addedConfirmedTransactions = new JSONArray();
                for (Transaction transaction : transactions) {
                    JSONObject addedConfirmedTransaction = new JSONObject();
                    addedConfirmedTransaction.put("index", Users.getIndex(transaction));
                    addedConfirmedTransaction.put("blockTimestamp", transaction.getBlockTimestamp());
                    addedConfirmedTransaction.put("transactionTimestamp", transaction.getTimestamp());
                    addedConfirmedTransaction.put("sender", Long.toUnsignedString(transaction.getSenderId()));
                    addedConfirmedTransaction.put("recipient", Long.toUnsignedString(transaction.getRecipientId()));
                    addedConfirmedTransaction.put("amountNQT", transaction.getAmountNQT());
                    addedConfirmedTransaction.put("feeNQT", transaction.getFeeNQT());
                    addedConfirmedTransaction.put("id", transaction.getStringId());
                    addedConfirmedTransactions.add(addedConfirmedTransaction);
                }
                response.put("addedConfirmedTransactions", addedConfirmedTransactions);
                Users.sendNewDataToAll(response);
            }, TransactionProcessorEvent.ADDED_CONFIRMED_TRANSACTIONS);

            EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
                JSONObject response = new JSONObject();
                JSONArray addedOrphanedBlocks = new JSONArray();
                JSONObject addedOrphanedBlock = new JSONObject();
                addedOrphanedBlock.put("index", Users.getIndex(block));
                addedOrphanedBlock.put("timestamp", block.getTimestamp());
                addedOrphanedBlock.put("numberOfTransactions", block.getTransactions().size());
                addedOrphanedBlock.put("totalAmountNQT", block.getTotalAmountNQT());
                addedOrphanedBlock.put("totalFeeNQT", block.getTotalFeeNQT());
                addedOrphanedBlock.put("payloadLength", block.getPayloadLength());
                addedOrphanedBlock.put("generator", Long.toUnsignedString(block.getFoundryId()));
                addedOrphanedBlock.put("height", block.getHeight());
                addedOrphanedBlock.put("version", block.getECVersion());
                addedOrphanedBlock.put("block", block.getStringECId());
                addedOrphanedBlock.put("baseTarget", BigInteger.valueOf(block.getBaseTarget()).multiply(BigInteger.valueOf(100000)).divide(BigInteger.valueOf(Constants.EC_INITIAL_BASE_TARGET)));
                addedOrphanedBlocks.add(addedOrphanedBlock);
                response.put("addedOrphanedBlocks", addedOrphanedBlocks);
                Users.sendNewDataToAll(response);
            }, EcBlockchainProcessorEvent.BLOCK_POPPED);

            EcBlockchainProcessorImpl.getInstance().addECListener(block -> {
                JSONObject response = new JSONObject();
                JSONArray addedRecentBlocks = new JSONArray();
                JSONObject addedRecentBlock = new JSONObject();
                addedRecentBlock.put("index", Users.getIndex(block));
                addedRecentBlock.put("timestamp", block.getTimestamp());
                addedRecentBlock.put("numberOfTransactions", block.getTransactions().size());
                addedRecentBlock.put("totalAmountNQT", block.getTotalAmountNQT());
                addedRecentBlock.put("totalFeeNQT", block.getTotalFeeNQT());
                addedRecentBlock.put("payloadLength", block.getPayloadLength());
                addedRecentBlock.put("generator", Long.toUnsignedString(block.getFoundryId()));
                addedRecentBlock.put("height", block.getHeight());
                addedRecentBlock.put("version", block.getECVersion());
                addedRecentBlock.put("block", block.getStringECId());
                addedRecentBlock.put("baseTarget", BigInteger.valueOf(block.getBaseTarget()).multiply(BigInteger.valueOf(100000)).divide(BigInteger.valueOf(Constants.EC_INITIAL_BASE_TARGET)));
                addedRecentBlocks.add(addedRecentBlock);
                response.put("addedRecentBlocks", addedRecentBlocks);
                Users.sendNewDataToAll(response);
            }, EcBlockchainProcessorEvent.BLOCK_PUSHED);

            FoundryMachine.addFoundryMachineListener(generator -> {
                JSONObject response = new JSONObject();
                response.put("response", "setBlockGenerationDeadline");
                response.put("deadline", generator.getDeadline());
                USER_CONCURRENT_HASH_MAP.values().forEach(user -> {
                    if (Arrays.equals(generator.getPublicKey(), user.getPublicECKey())) {
                        user.send(response);
                    }
                });
            }, FoundryMachineEvent.GENERATION_DEADLINE);
        }


    }

    static Collection<User> getAllEcUsers() {
        return ALL_USERS;
    }

    static User getEcUser(String userId) {
        User user = USER_CONCURRENT_HASH_MAP.get(userId);
        if (user == null) {
            user = new User(userId);
            User oldUser = USER_CONCURRENT_HASH_MAP.putIfAbsent(userId, user);
            if (oldUser != null) {
                user = oldUser;
                user.setInactive(false);
            }
        } else {
            user.setInactive(false);
        }
        return user;
    }

    static User remove(User user) {
        return USER_CONCURRENT_HASH_MAP.remove(user.getEcUserId());
    }

    private static void sendNewDataToAll(JSONObject response) {
        response.put("response", "processNewData");
        sendToAll(response);
    }

    private static void sendToAll(JSONStreamAware response) {
        for (User user : USER_CONCURRENT_HASH_MAP.values()) {
            user.send(response);
        }
    }

    static int getIndex(Peer peer) {
        Integer index = PEER_INDEX_MAP.get(peer.getPeerHost());
        if (index == null) {
            index = PEER_COUNTER.incrementAndGet();
            PEER_INDEX_MAP.put(peer.getPeerHost(), index);
            PEER_ADDRESS_MAP.put(index, peer.getPeerHost());
        }
        return index;
    }

    static int getIndex(EcBlock ecBlock) {
        Integer index = BLOCK_INDEX_MAP.get(ecBlock.getECId());
        if (index == null) {
            index = BLOCK_COUNTER.incrementAndGet();
            BLOCK_INDEX_MAP.put(ecBlock.getECId(), index);
        }
        return index;
    }

    static int getIndex(Transaction transaction) {
        Integer index = TRANSACTION_INDEX_MAP.get(transaction.getTransactionId());
        if (index == null) {
            index = TRANSACTION_COUNTER.incrementAndGet();
            TRANSACTION_INDEX_MAP.put(transaction.getTransactionId(), index);
        }
        return index;
    }

    static Peer getEcPeer(int index) {
        String peerAddress = PEER_ADDRESS_MAP.get(index);
        if (peerAddress == null) {
            return null;
        }
        return Peers.getPeer(peerAddress);
    }

    public static void start() {
    }

    public static void shutdown() {
        if (usersServer != null) {
            try {
                usersServer.stop();
            } catch (Exception e) {
                LoggerUtil.logError("Failed to stop user interface server", e);
            }
        }
    }

}
