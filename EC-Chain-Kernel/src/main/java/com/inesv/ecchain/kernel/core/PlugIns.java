package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import com.inesv.ecchain.kernel.http.APIRequestHandler;
import com.inesv.ecchain.kernel.http.APITag;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public final class PlugIns {

    private static List<PlugIn> plugIns;

    @PostConstruct
    public static void initPostConstruct() {
        PropertiesUtil.getProperties();
        List<PlugIn> addOnsList = new ArrayList<>();
        PropertiesUtil.getStringListProperty("ec.plugIns").forEach(addOn -> {
            try {
                addOnsList.add((PlugIn) Class.forName(addOn).newInstance());
            } catch (ReflectiveOperationException e) {
                LoggerUtil.logError(e.getMessage(), e);
            }
        });
        plugIns = Collections.unmodifiableList(addOnsList);
        if (!plugIns.isEmpty() && !PropertiesUtil.getKeyForBoolean("ec.disableSecurityPolicy")) {
            System.setProperty("java.security.policy", RuntimeEnvironment.ecisDesktopApplicationEnabled() && PropertiesUtil.getKeyForBoolean("ec.launchDesktopApplication") ? "ecdesktop.policy" : "ec.policy");
            LoggerUtil.logInfo("Setting security manager with policy " + System.getProperty("java.security.policy"));
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkConnect(String host, int port) {
                    // Allow all connections
                }

                @Override
                public void checkConnect(String host, int port, Object context) {
                    // Allow all connections
                }
            });
        }
        plugIns.forEach(addOn -> {
            LoggerUtil.logInfo("Initializing " + addOn.getClass().getName());
            addOn.init();
        });
    }

    private PlugIns() {
    }

    public static void start() {
    }

    public static void shutdown() {
        plugIns.forEach(addOn -> {
            LoggerUtil.logInfo("Shutting down " + addOn.getClass().getName());
            addOn.shutdown();
        });
    }

    public static void registerAPIRequestHandlers(Map<String, APIRequestHandler> map) {
        for (PlugIn plugIn : plugIns) {
            APIRequestHandler requestHandler = plugIn.getAPIRequestHandler();
            if (requestHandler != null) {
                if (!requestHandler.getAPITags().contains(APITag.ADDONS)) {
                    LoggerUtil.logError("Add-on " + plugIn.getClass().getName()
                            + " attempted to register request handler which is not tagged as APITag.ADDONS, skipping");
                    continue;
                }
                String requestType = plugIn.getAPIRequestType();
                if (requestType == null) {
                    LoggerUtil.logError("Add-on " + plugIn.getClass().getName() + " requestType not defined");
                    continue;
                }
                if (map.get(requestType) != null) {
                    LoggerUtil.logError("Add-on " + plugIn.getClass().getName() + " attempted to override requestType " + requestType + ", skipping");
                    continue;
                }
                LoggerUtil.logInfo("Add-on " + plugIn.getClass().getName() + " registered new API: " + requestType);
                map.put(requestType, requestHandler);
            }
        }
    }

}
