package com.inesv.ecchain.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtil {

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static Logger log = LoggerFactory.getLogger("EC-LOG");

    public static void logInfo(String str) {
        log.info(str);
    }

    public static void logDebug(String str) {
        log.debug(str);
    }

    public static void logError(String str) {
        log.error(str);
    }

    public static void logError(String str, Throwable e) {
        log.error(str, e);
    }

    public static void setLevel(Level level) {
        java.util.logging.Logger jdkLogger = java.util.logging.Logger.getLogger(log.getName());
        switch (level) {
            case DEBUG:
                jdkLogger.setLevel(java.util.logging.Level.FINE);
                break;
            case INFO:
                jdkLogger.setLevel(java.util.logging.Level.INFO);
                break;
            case WARN:
                jdkLogger.setLevel(java.util.logging.Level.WARNING);
                break;
            case ERROR:
                jdkLogger.setLevel(java.util.logging.Level.SEVERE);
                break;
        }
    }
}
