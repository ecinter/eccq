package com.inesv.ecchain.kernel.http;

import com.inesv.ecchain.common.core.EcException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @Author:Lin
 * @Description:
 * @Date:16:31 2017/12/22
 * @Modified by:
 */
public abstract class APIRequestHandler {

    private final List<String> parameters;
    private final String fileParameter;
    private final Set<APITag> apiTags;

    protected APIRequestHandler(APITag[] apiTags, String... parameters) {
        this(null, apiTags, parameters);
    }

    protected APIRequestHandler(String fileParameter, APITag[] apiTags, String... origParameters) {
        List<String> parameters = new ArrayList<>();
        Collections.addAll(parameters, origParameters);
        if ((requirePassword() || parameters.contains("lastIndex")) && !API.disableAdminPassword) {
            parameters.add("adminPassword");
        }
        if (allowRequiredBlockParameters()) {
            parameters.add("requireBlock");
            parameters.add("requireLastBlock");
        }
        this.parameters = Collections.unmodifiableList(parameters);
        this.apiTags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(apiTags)));
        this.fileParameter = fileParameter;
    }

    public final List<String> getParameters() {
        return parameters;
    }

    public final Set<APITag> getAPITags() {
        return apiTags;
    }

    public final String getFileParameter() {
        return fileParameter;
    }

    protected abstract JSONStreamAware processRequest(HttpServletRequest request) throws EcException;

    protected JSONStreamAware processRequest(HttpServletRequest request, HttpServletResponse response) throws EcException {
        return processRequest(request);
    }

    protected boolean requirePost() {
        return false;
    }

    protected boolean startDbTransaction() {
        return false;
    }

    protected boolean requirePassword() {
        return false;
    }

    protected boolean allowRequiredBlockParameters() {
        return true;
    }

    protected boolean requireBlockchain() {
        return true;
    }

    protected boolean requireFullClient() {
        return false;
    }

}
