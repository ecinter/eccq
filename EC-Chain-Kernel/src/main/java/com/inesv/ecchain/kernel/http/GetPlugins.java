package com.inesv.ecchain.kernel.http;

import com.inesv.ecchain.common.core.Constants;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.EnumSet;

public final class GetPlugins extends APIRequestHandler {

    static final GetPlugins instance = new GetPlugins();

    private GetPlugins() {
        super(new APITag[]{APITag.INFO});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        JSONObject response = new JSONObject();
        if (!Files.isReadable(Constants.PLUGINS_HOME)) {
            return JSONResponses.fileNotFound(Constants.PLUGINS_HOME.toString());
        }
        PluginDirListing pluginDirListing = new PluginDirListing();
        try {
            Files.walkFileTree(Constants.PLUGINS_HOME, EnumSet.noneOf(FileVisitOption.class), 2, pluginDirListing);
        } catch (IOException e) {
            return JSONResponses.fileNotFound(e.getMessage());
        }
        JSONArray plugins = new JSONArray();
        pluginDirListing.getDirectories().forEach(dir -> plugins.add(Paths.get(dir.toString()).getFileName().toString()));
        response.put("plugins", plugins);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
