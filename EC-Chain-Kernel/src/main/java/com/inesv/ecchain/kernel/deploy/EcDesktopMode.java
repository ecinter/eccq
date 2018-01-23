package com.inesv.ecchain.kernel.deploy;


import com.inesv.ecchain.common.util.LoggerUtil;
import org.springframework.stereotype.Component;

import javax.swing.*;

@Component
public class EcDesktopMode implements RuntimeMode {

    private EcDesktopSystemTray ecDesktopSystemTray;
    private Class desktopApplication;

    @Override
    public void init() {
        com.inesv.ecchain.kernel.deploy.LookAndFeel.init();
        ecDesktopSystemTray = new EcDesktopSystemTray();
        SwingUtilities.invokeLater(ecDesktopSystemTray::getGUI);
    }

    @Override
    public void launchDesktopApplication() {
        LoggerUtil.logInfo("Launching desktop wallet");
        try {
            desktopApplication = Class.forName("com.inesv.ecchain.DesktopApplication");
            desktopApplication.getMethod("launch").invoke(null);
        } catch (ReflectiveOperationException e) {
            LoggerUtil.logError("com.inesv.ecchain.DesktopApplication failed to launch", e);
        }
    }

    @Override
    public void shutdown() {
        ecDesktopSystemTray.shutdown();
        if (desktopApplication == null) {
            return;
        }
        try {
            desktopApplication.getMethod("shutdown").invoke(null);
        } catch (ReflectiveOperationException e) {
            LoggerUtil.logError("com.inesv.ecchain.DesktopApplication failed to shutdown", e);
        }
    }

    @Override
    public void alert(String message) {
        ecDesktopSystemTray.alert(message);
    }
}
