package com.inesv.ecchain.wallet;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.common.util.ThreadPool;
import com.inesv.ecchain.kernel.core.*;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import com.inesv.ecchain.kernel.deploy.RuntimeMode;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.kernel.http.APIProxy;
import com.inesv.ecchain.kernel.peer.Peers;
import com.inesv.ecchain.wallet.core.Users;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;

@Component
public class EcInit {
    private static volatile boolean initialized = false;

    @PostConstruct
    public static void initPostConstruct() {
        String[] systemProperties = new String[]{
                "socksProxyHost",
                "socksProxyPort"
        };

        for (String propertyName : systemProperties) {
            String propertyValue;
            if ((propertyValue = PropertiesUtil.getKeyForString(propertyName, null)) != null) {
                LoggerUtil.logInfo(propertyName + ":" + propertyValue);
                System.setProperty(propertyName, propertyValue);
            }
        }

        String[] loggedProperties = new String[]{
                "java.version",
                "java.vm.version",
                "java.vm.name",
                "java.vendor",
                "java.vm.vendor",
                "java.home",
                "java.library.path",
                "java.class.path",
                "os.arch",
                "sun.arch.data.model",
                "os.name",
                "file.encoding",
                "java.security.policy",
                "java.security.manager",
                Constants.RUNTIME_MODE_ARG,
                Constants.DIRPROVIDER_ARG
        };
        for (String property : loggedProperties) {
            LoggerUtil.logInfo(String.format("%s = %s", property, System.getProperty(property)));
        }
        LoggerUtil.logDebug(String.format("availableProcessors = %s", Runtime.getRuntime().availableProcessors()));
        LoggerUtil.logDebug(String.format("maxMemory = %s", Runtime.getRuntime().maxMemory()));
        LoggerUtil.logDebug(String.format("processId = %s", getProcessId()));
    }

    public static void init(RuntimeMode runtimeMode) {
        try {
            long startTime = System.currentTimeMillis();
            Thread secureRandomInitThread = new Thread(() -> Crypto.getEcSecureRandom().nextBytes(new byte[1024]));
            secureRandomInitThread.setDaemon(true);
            secureRandomInitThread.start();
            H2.start();//初始化数据库,事务阈值和时间间隔、数据库链接参数、更新数据库版本
            TransactionProcessorImpl.getInstance();//获取交易处理器 添加线程processTransactionsThread，rebroadcastAllUnconfirmedTransactions，rebroadcastTransactionsThread，removeUnconfirmedTransactionsThread，processWaitingTransactionsThread
            EcBlockchainProcessorImpl.getInstance();//获取区块链处理器 添加监听器Event.BLOCK_SCANNED,Event.BLOCK_PUSHED,Event.BLOCK_PUSHED,Event.RESCAN_END,添加runBeforeStart线程(加载创世块 扫描表数据),getMoreBlocksThread
            Account.start();//账号初始化 区块链处理器添加监听器Event.AFTER_BLOCK_APPLY,Event.BLOCK_POPPED,Event.RESCAN_BEGIN
            AccountRestrictions.start();
            AccountLedger.start();//初始化账号分类（*：跟踪全部账号）且（记录2：只记录未确认的更改），初始LedgerEvent集合，初始LedgerHolding集合
            AccountName.start();
            Property.start();
            ElectronicProductStore.start();//电子产品商店初始化 区块链处理器添加监听器Event.AFTER_BLOCK_APPLY（暂时无用）
            Heart.start();
            Order.start();
            Poll.start();//投票初始化 区块链处理器添加监听器Event.AFTER_BLOCK_APPLY（暂时无用）
            PhasingPoll.start();
            Trade.start();
            PropertyTransfer.start();
            PropertyDelete.start();
            PropertyDividend.start();
            Vote.start();
            PhasingVote.start();
            Coin.start();//货币初始化 区块链处理器添加监听器CrowdFundingListener
            CoinBuyOffer.start();
            CoinSellOffer.start();
            CoinFounder.start();
            CoinMint.start();
            CoinTransfer.start();
            Conversion.start();
            ConversionRequest.start();
            Shuffling.start();//（疑）区块链处理器添加监听器Event.AFTER_BLOCK_APPLY
            ShufflingParticipant.start();
            PrunableMessage.start();
            BadgeData.start();
            FxtDistribution.start();//添加FxtDistribution监听器 初始化FxtDistribution对象
            Peers.start();//获取对等点地址（疑：获取获取地址非从数据库获取） 添加runBeforeStart线程、runAfterStart线程 分享地址 添加监听器（获取地址）
            APIProxy.start();//代理初始化 获取不转发请求的集合 添加peersUpdateThread线程（建立连接）
            FoundryMachine.start();//添加generateBlocksThread线程（疑：产生区块？）
            PlugIns.start();//插件初始化，设置系统参数（疑视弃用）
            API.start();//过滤禁用的API及API标签，根据设定的请求类型创建请求（http,https,ssl)添加API服务并启动
            Users.start();//启用用户服务 添加runBeforeStart线程 添加Account监听器、Peers监听器、TransactionProcessor监听器、BlockchainProcessor监听器、Generator监听器
            DebugTrace.start();//初始化headers
            ThreadPool.start(1);
            try {
                secureRandomInitThread.join(10000);
            } catch (InterruptedException ignore) {
            }
            long currentTime = System.currentTimeMillis();
            LoggerUtil.logInfo("Initialization took " + (currentTime - startTime) / 1000 + " seconds");
            LoggerUtil.logInfo("Ec server " + Constants.EC_VERSION + " started successfully.");
            LoggerUtil.logInfo("Copyright © 2013-2016 The Ec Core Developers.");
            LoggerUtil.logInfo("Distributed under GPLv2, with ABSOLUTELY NO WARRANTY.");
            if (API.getWelcomeecpageuri() != null) {
                LoggerUtil.logInfo("Client UI is at " + API.getWelcomeecpageuri());
            }
            if (isDesktopApplicationEnabled()) {
                runtimeMode.launchDesktopApplication();
            }
        } catch (Exception e) {
            LoggerUtil.logError(e.getMessage(), e);
            System.exit(1);
        }

        if (initialized) {
            throw new RuntimeException("Ec.start has already been called");
        }
        initialized = true;
    }

    public static String getProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if (runtimeName == null) {
            return "";
        }
        String[] tokens = runtimeName.split("@");
        if (tokens.length == 2) {
            return tokens[0];
        }
        return "";
    }

    public static boolean isDesktopApplicationEnabled() {
        return RuntimeEnvironment.ecisDesktopApplicationEnabled() && PropertiesUtil.getKeyForBoolean("ec.launchDesktopApplication");
    }
}
