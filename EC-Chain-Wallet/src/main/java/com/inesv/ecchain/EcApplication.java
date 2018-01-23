package com.inesv.ecchain;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import com.inesv.ecchain.kernel.deploy.RuntimeMode;
import com.inesv.ecchain.wallet.EcInit;
import com.inesv.ecchain.wallet.EcShutdown;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class EcApplication {
    public static RuntimeEnvironment runtimeEnvironment;
    private static RuntimeMode runtimeMode;

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(EcApplication.class);
        runtimeEnvironment = (RuntimeEnvironment) context.getBean("runtimeEnvironment");
        LoggerUtil.logInfo("Initializing Ec server version " + Constants.EC_VERSION);
        //System.setProperty("ec.runtime.mode","desktop");
        runtimeMode = runtimeEnvironment.getRuntimeMode();
        LoggerUtil.logInfo("runtimeMode" + runtimeMode);
        if (!Constants.EC_VERSION.equals(PropertiesUtil.getKeyForString("ec.version", null))) {
            throw new RuntimeException("Using an ec.properties file from a version other than " + Constants.EC_VERSION + " is not supported!!!");
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(EcShutdown::shutdown));
            EcInit.init(runtimeMode);
        } catch (Throwable t) {
            LoggerUtil.logInfo("Fatal error: " + t.toString());
            t.printStackTrace();
        }
    }
}
