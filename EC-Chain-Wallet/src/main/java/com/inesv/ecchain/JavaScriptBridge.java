package com.inesv.ecchain;

import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.http.API;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.json.simple.JSONObject;

/**
 * The class itself and methods in this class are invoked from JavaScript therefore has to be public
 */
@SuppressWarnings("WeakerAccess")
public class JavaScriptBridge {

    DesktopApplication application;
    private Clipboard clipboard;

    public JavaScriptBridge(DesktopApplication application) {
        this.application = application;
    }

    public void log(String message) {
        LoggerUtil.logInfo(message);
    }

    @SuppressWarnings("unused")
    public void openBrowser(String account) {
        final String url = API.getWelcomeecpageuri().toString() + "?account=" + account;
        Platform.runLater(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                LoggerUtil.logInfo("Cannot open " + API.getWelcomeecpageuri().toString() + " error " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unused")
    public String readContactsFile() {
        String fileName = "contacts.json";
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get("", fileName));
        } catch (IOException e) {
            LoggerUtil.logInfo("Cannot read file " + fileName + " error " + e.getMessage());
            JSONObject response = new JSONObject();
            response.put("error", "contacts_file_not_found");
            response.put("file", fileName);
            response.put("folder", "");
            response.put("type", "1");
            return JSON.toECJSONString(response);
        }
        try {
            return new String(bytes, "utf8");
        } catch (UnsupportedEncodingException e) {
            LoggerUtil.logInfo("Cannot parse file " + fileName + " content error " + e.getMessage());
            JSONObject response = new JSONObject();
            response.put("error", "unsupported_encoding");
            response.put("type", "2");
            return JSON.toECJSONString(response);
        }
    }

    public String getAdminPassword() {
        return API.adminPassword;
    }

    @SuppressWarnings("unused")
    public void popupHandlerURLChange(String newValue) {
        application.popupHandlerURLChange(newValue);
    }

    @SuppressWarnings("unused")
    public boolean copyText(String text) {
        if (clipboard == null) {
            clipboard = Clipboard.getSystemClipboard();
            if (clipboard == null) {
                return false;
            }
        }
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        return clipboard.setContent(content);
    }

}