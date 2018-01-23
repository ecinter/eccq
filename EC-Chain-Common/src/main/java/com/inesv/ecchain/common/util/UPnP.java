package com.inesv.ecchain.common.util;


import com.inesv.ecchain.common.core.Constants;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;

import java.net.InetAddress;
import java.util.Map;


public class UPnP {


    private static boolean init_Done = false;


    private static GatewayDevice gateecway = null;


    private static InetAddress local_Address;


    private static InetAddress external_Address;


    public static synchronized void addPort(int port) {
        if (!init_Done)
            init();
        //
        // Ignore the request if we didn't find a gateecway device
        //
        if (gateecway == null)
            return;
        //
        // Forward the port
        //
        try {
            if (gateecway.addPortMapping(port, port, local_Address.getHostAddress(), "TCP",
                                       Constants.EC_APPLICATION + " " + Constants.EC_VERSION)) {
                LoggerUtil.logDebug("Mapped port [" + external_Address.getHostAddress() + "]:" + port);
            } else {
                LoggerUtil.logDebug("Unable to map port " + port);
            }
        } catch (Exception exc) {
            LoggerUtil.logError("Unable to map port " + port + ": " + exc.toString());
        }
    }


    public static synchronized void delPort(int port) {
        if (!init_Done || gateecway == null)
            return;
        //
        // Delete the port
        //
        try {
            if (gateecway.deletePortMapping(port, "TCP")) {
                LoggerUtil.logDebug("Mapping deleted for port " + port);
            } else {
                LoggerUtil.logDebug("Unable to delete mapping for port " + port);
            }
        } catch (Exception exc) {
            LoggerUtil.logError("Unable to delete mapping for port " + port + ": " + exc.toString());
        }
    }

    public static synchronized InetAddress getExternalAddress() {
        if (!init_Done)
            init();
        return external_Address;
    }

    private static void init() {
        init_Done = true;
        //
        // Discover the gateecway devices on the local network
        //
        try {
            LoggerUtil.logInfo("Looking for UPnP gateecway device...");
            GatewayDevice.setHttpReadTimeout(PropertiesUtil.getKeyForInt("ec.upnpGatewayTimeout", GatewayDevice.getHttpReadTimeout()));
            GatewayDiscover discover = new GatewayDiscover();
            discover.setTimeout(PropertiesUtil.getKeyForInt("ec.upnpDiscoverTimeout", discover.getTimeout()));
            Map<InetAddress, GatewayDevice> gatewayMap = discover.discover();
            if (gatewayMap == null || gatewayMap.isEmpty()) {
                LoggerUtil.logDebug("There are no UPnP gateecway devices");
            } else {
                gatewayMap.forEach((addr, device) ->
                        LoggerUtil.logDebug("UPnP gateecway device found on " + addr.getHostAddress()));
                gateecway = discover.getValidGateway();
                if (gateecway == null) {
                    LoggerUtil.logDebug("There is no connected UPnP gateecway device");
                } else {
                    local_Address = gateecway.getLocalAddress();
                    external_Address = InetAddress.getByName(gateecway.getExternalIPAddress());
                    LoggerUtil.logDebug("Using UPnP gateecway device on " + local_Address.getHostAddress());
                    LoggerUtil.logInfo("External IP address is " + external_Address.getHostAddress());
                }
            }
        } catch (Exception exc) {
            LoggerUtil.logError("Unable to discover UPnP gateecway devices: " + exc.toString());
        }
    }
}
