package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.proxy.AsyncMiddleManServlet;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

public final class ShapeShiftProxyServlet extends AsyncMiddleManServlet {

    static final String SHAPESHIFT_TARGET = "/shapeshift";

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            super.service(new ClientRequestWrapper(request), response);
        } catch (Exception e) {
            JSONObject errorJson = new JSONObject();
            errorJson.put("errorDescription", e.getMessage());
            try {
                try (Writer writer = response.getWriter()) {
                    JSON.writeECJSONString(JSON.prepare(errorJson), writer);
                }
            } catch (IOException ioe) {
                LoggerUtil.logError("Failed to write response to client", ioe);
            }
        }
    }

    @Override
    protected HttpClient newHttpClient() {
        return HttpClientFactory.newHttpClient();
    }

    @Override
    protected String rewriteTarget(HttpServletRequest clientRequest) {
        return "https://shapeshift.io" + clientRequest.getRequestURI();
    }

    @Override
    protected void onClientRequestFailure(HttpServletRequest clientRequest, Request proxyRequest,
                                          HttpServletResponse proxyResponse, Throwable failure) {
        super.onClientRequestFailure(clientRequest, proxyRequest, proxyResponse, failure);
    }

    @Override
    protected Response.Listener newProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
        return new APIProxyResponseListener(request, response);
    }

    private class APIProxyResponseListener extends ProxyResponseListener {

        APIProxyResponseListener(HttpServletRequest request, HttpServletResponse response) {
            super(request, response);
        }

        @Override
        public void onFailure(Response response, Throwable failure) {
            super.onFailure(response, failure);
            LoggerUtil.logError("shape shift proxy failed", failure);
        }
    }
}