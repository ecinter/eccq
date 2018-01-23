package com.inesv.ecchain;

import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.common.util.PropertiesUtil;
import com.inesv.ecchain.kernel.core.Transaction;
import com.inesv.ecchain.kernel.http.API;
import com.inesv.ecchain.wallet.EcShutdown;
import com.sun.javafx.scene.web.Debugger;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.net.ssl.HttpsURLConnection;
import netscape.javascript.JSObject;
import org.springframework.beans.factory.annotation.Autowired;


public class DesktopApplication extends Application {

    private static final Set DOWNLOAD_REQUEST_TYPES = new HashSet<>(Arrays.asList("downloadTaggedData", "downloadPrunableMessage"));
    private static final boolean ENABLE_JAVASCRIPT_DEBUGGER = false;
    private static volatile boolean isLaunched;
    private static volatile Stage stage;
    private static volatile WebEngine webEngine;
    private JSObject nrs;
    private volatile long updateTime;
    private volatile List<Transaction> unconfirmedTransactionUpdates = new ArrayList<>();
    private JavaScriptBridge javaScriptBridge;



    public static void launch() {
        if (!isLaunched) {
            isLaunched = true;
            Application.launch(DesktopApplication.class);
            return;
        }
        if (stage != null) {
            Platform.runLater(() -> showStage(false));
        }
    }

    @SuppressWarnings("unused")
    public static void refresh() {
        Platform.runLater(() -> showStage(true));
    }

    private static void showStage(boolean isRefresh) {
        if (isRefresh) {
            webEngine.load(getUrl());
        }
        if (!stage.isShowing()) {
            stage.show();
        } else if (stage.isIconified()) {
            stage.setIconified(false);
        } else {
            stage.toFront();
        }
    }

    public static void shutdown() {
        System.out.println("shutting down JavaFX platform");
        Platform.exit();
        if (ENABLE_JAVASCRIPT_DEBUGGER) {
            try {
                Class<?> aClass = Class.forName("com.mohamnag.fxwebview_debugger.DevToolsDebuggerServer");
                aClass.getMethod("stopDebugServer").invoke(null);
            } catch (Exception e) {
                LoggerUtil.logError("Error shutting down webview debugger", e);
            }
        }
        System.out.println("JavaFX platform shutdown complete");
    }

    @Override
    public void start(Stage stage) throws Exception {
        DesktopApplication.stage = stage;
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        WebView browser = new WebView();
        browser.setOnContextMenuRequested(new WalletContextMenu());
        WebView invisible = new WebView();

        int height = (int) Math.min(primaryScreenBounds.getMaxY() - 100, 1000);
        int width = (int) Math.min(primaryScreenBounds.getMaxX() - 100, 1618);
        browser.setMinHeight(height);
        browser.setMinWidth(width);
        webEngine = browser.getEngine();
        webEngine.setUserDataDirectory(new File(""));

        Worker<Void> loadWorker = webEngine.getLoadWorker();
        loadWorker.stateProperty().addListener(
                (ov, oldState, newState) -> {
                    LoggerUtil.logDebug("loadWorker old state " + oldState + " new state " + newState);
                    if (newState != Worker.State.SUCCEEDED) {
                        LoggerUtil.logDebug("loadWorker state change ignored");
                        return;
                    }
                    JSObject window = (JSObject)webEngine.executeScript("window");
                    javaScriptBridge = new JavaScriptBridge(this); // Must be a member variable to prevent gc
                    window.setMember("java", javaScriptBridge);
                    Locale locale = Locale.getDefault();
                    String language = locale.getLanguage().toLowerCase() + "-" + locale.getCountry().toUpperCase();
                    window.setMember("javaFxLanguage", language);
                    webEngine.executeScript("console.log = function(msg) { java.log(msg); };");
                    stage.setTitle("EC Desktop");

                    if (ENABLE_JAVASCRIPT_DEBUGGER) {
                        try {
                            // Add the javafx_webview_debugger lib to the classpath
                            // For more details, check https://github.com/mohamnag/javafx_webview_debugger
                            Class<?> aClass = Class.forName("com.mohamnag.fxwebview_debugger.DevToolsDebuggerServer");
                            @SuppressWarnings("deprecation") Debugger debugger = webEngine.impl_getDebugger();
                            Method startDebugServer = aClass.getMethod("startDebugServer", Debugger.class, int.class);
                            startDebugServer.invoke(null, debugger, 51742);
                        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            LoggerUtil.logError("Cannot start JavaFx debugger", e);
                        }
                    }
               });

        // Invoked by the webEngine popup handler
        // The invisible webView does not show the link, instead it opens a browser window
        invisible.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> popupHandlerURLChange(newValue));

        // Invoked when changing the document.location property, when issuing a download request
        webEngine.locationProperty().addListener((observable, oldValue, newValue) -> webViewURLChange(newValue));

        // Invoked when clicking a link to external site like Help or API console
        webEngine.setCreatePopupHandler(
            config -> {
                LoggerUtil.logInfo("popup request from webEngine");
                return invisible.getEngine();
            });

        webEngine.load(getUrl());

        Scene scene = new Scene(browser);
        String address = getUrl();
        stage.getIcons().add(new Image(address + "/img/ec-icon-32x32.png"));
        stage.initStyle(StageStyle.DECORATED);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.show();
        //Platform.setImplicitExit(true); // So that we can reopen the application in case the user closed it
    }


    private static String getUrl() {
        String url = API.getWelcomeecpageuri().toString();
        return url;
    }

    @SuppressWarnings("WeakerAccess")
    public void popupHandlerURLChange(String newValue) {
        LoggerUtil.logInfo("popup request for " + newValue);
        Platform.runLater(() -> {
            try {
                Desktop.getDesktop().browse(new URI(newValue));
            } catch (Exception e) {
                LoggerUtil.logInfo("Cannot open " + newValue + " error " + e.getMessage());
            }
        });
    }

    private void webViewURLChange(String newValue) {
        LoggerUtil.logInfo("webview address changed to " + newValue);
        URL url;
        try {
            url = new URL(newValue);
        } catch (MalformedURLException e) {
            LoggerUtil.logError("Malformed URL " + newValue, e);
            return;
        }
        String query = url.getQuery();
        if (query == null) {
            return;
        }
        String[] paramPairs = query.split("&");
        Map<String, String> params = new HashMap<>();
        for (String paramPair : paramPairs) {
            String[] keyValuePair = paramPair.split("=");
            if (keyValuePair.length == 2) {
                params.put(keyValuePair[0], keyValuePair[1]);
            }
        }
        String requestType = params.get("requestType");
        if (DOWNLOAD_REQUEST_TYPES.contains(requestType)) {
            //download(requestType, params);
        } else {
            LoggerUtil.logInfo(String.format("requestType %s is not a download request", requestType));
        }
    }


    @Override
    public void stop() throws Exception {
        System.out.println("systemout");
        System.exit(0);
        super.stop();
    }


}
