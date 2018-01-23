package com.inesv.ecchain.kernel.deploy;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Component
public class RuntimeEnvironment {

    @Autowired
    private WindowsServiceModeEc windowsServiceModeEc;
    @Autowired
    private EcCommandLineMode ecCommandLineMode;
    @Autowired
    private EcDesktopMode ecDesktopMode;

    private static final boolean isHeadless;

    private static final boolean hasJavaFX;

    static {
        boolean b;
        try {
            // Load by reflection to prevent exception in case java.awt does not exist
            Class graphicsEnvironmentClass = Class.forName("java.awt.GraphicsEnvironment");
            Method isHeadlessMethod = graphicsEnvironmentClass.getMethod("isHeadless");
            b = (Boolean) isHeadlessMethod.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            b = true;
        }
        isHeadless = b;//false
        try {
            Class.forName("javafx.application.Application");
            b = true;
        } catch (ClassNotFoundException e) {
            LoggerUtil.logInfo("javafx not supported");
            b = false;
        }
        hasJavaFX = b;//true
    }

    private static boolean ecisWindowsRuntime() {
        return Constants.OSNAME.startsWith("windows");
    }

    private static boolean ecisUnixRuntime() {
        return Constants.OSNAME.contains("nux") || Constants.OSNAME.contains("nix") || Constants.OSNAME.contains("aix") || Constants.OSNAME.contains("bsd") || Constants.OSNAME.contains("sunos");
    }

    private static boolean ecisMacRuntime() {
        return Constants.OSNAME.contains("mac");
    }

    private static boolean ecisWindowsService() {
        return "service".equalsIgnoreCase(System.getProperty(Constants.RUNTIME_MODE_ARG)) && ecisWindowsRuntime();
    }

    private static boolean ecisHeadless() {
        return isHeadless;
    }

    private static boolean ecisDesktopEnabled() {
        return "desktop".equalsIgnoreCase(System.getProperty(Constants.RUNTIME_MODE_ARG));
    }

    public static boolean ecisDesktopApplicationEnabled() {
        return ecisDesktopEnabled() && hasJavaFX;
    }

    public RuntimeMode getRuntimeMode() {
        if (ecisDesktopEnabled()) {
            return ecDesktopMode;
        } else if (ecisWindowsService()) {
            return windowsServiceModeEc;
        } else {
            return ecCommandLineMode;
        }
    }

    public static DirProvider getDirProvider() {
        String dirProvider = System.getProperty(Constants.DIRPROVIDER_ARG);
        if (dirProvider != null) {
            try {
                return (DirProvider) Class.forName(dirProvider).newInstance();
            } catch (ReflectiveOperationException e) {
                LoggerUtil.logInfo("Failed to instantiate dirProvider " + dirProvider);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        if (ecisDesktopEnabled()) {
            if (ecisWindowsRuntime()) {
                return new WindowsUserDirProviderEc();
            }
            if (ecisUnixRuntime()) {
                return new UnixUserDirProviderEc();
            }
            if (ecisMacRuntime()) {
                return new MacUserDirProviderEc();
            }
        }
        return new EcDirProvider();
    }
}
