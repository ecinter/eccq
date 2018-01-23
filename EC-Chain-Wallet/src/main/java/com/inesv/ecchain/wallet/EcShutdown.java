package com.inesv.ecchain.wallet;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.ThreadPool;
import com.inesv.ecchain.kernel.core.PlugIns;
import com.inesv.ecchain.kernel.core.EcBlockchainProcessorImpl;
import com.inesv.ecchain.kernel.core.H2;
import com.inesv.ecchain.kernel.core.FundMonitoring;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.kernel.peer.Peers;
import com.inesv.ecchain.wallet.core.Users;
import org.springframework.stereotype.Component;

@Component
public class EcShutdown {
    public static void shutdown() {
        LoggerUtil.logInfo("Shutting down...");
        PlugIns.shutdown();
        API.shutdown();
        Users.shutdown();
        FundMonitoring.shutdown();
        ThreadPool.shutdown();
        EcBlockchainProcessorImpl.getInstance().shutdown();
        Peers.shutdown();
        H2.shutdown();
        LoggerUtil.logInfo("Ec server " + Constants.EC_VERSION + " stopped.");
    }
}
