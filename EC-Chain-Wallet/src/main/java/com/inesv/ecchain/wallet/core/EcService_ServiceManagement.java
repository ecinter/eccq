package com.inesv.ecchain.wallet.core;

import com.inesv.ecchain.kernel.deploy.LookAndFeel;
import com.inesv.ecchain.EcApplication;

import javax.swing.*;

@SuppressWarnings("UnusedDeclaration")
public class EcService_ServiceManagement {

    public static boolean ecServiceInit() {
        LookAndFeel.init();
        new Thread(() -> {
            String[] args = {};
            EcApplication.main(args);
        }).start();
        return true;
    }

    public static String[] ecServiceGetInfo() {
        return new String[]{
                "EC Server", // Long name
                "Manages the EC cryptographic currency protocol", // Description
                "true", // IsAutomatic
                "true", // IsAcceptStop
                "", // failure exe
                "", // args failure
                "", // dependencies
                "NONE/NONE/NONE", // ACTION = NONE | REBOOT | RESTART | RUN
                "0/0/0", // ActionDelay in seconds
                "-1", // Reset time in seconds
                "", // Boot Message
                "false" // IsAutomatic Delayed
        };
    }

    public static boolean ecServiceIsCreate() {
        return JOptionPane.showConfirmDialog(null, "Do you want to install the EC service ?", "Create Service", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public static boolean ecServiceIsLaunch() {
        return true;
    }

    public static boolean ecServiceIsDelete() {
        return JOptionPane.showConfirmDialog(null, "This EC service is already installed. Do you want to delete it ?", "Delete Service", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public static boolean ecServiceControl_Pause() {
        return false;
    }

    public static boolean ecServiceControl_Continue() {
        return false;
    }

    public static boolean ecServiceControl_Stop() {
        return true;
    }

    public static boolean ecServiceControl_Shutdown() {
        return true;
    }

    public static void ecServiceFinish() {
        System.exit(0);
    }

}
