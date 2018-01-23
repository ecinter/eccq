package com.inesv.ecchain.kernel.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;


class ClientRequestWrapper extends HttpServletRequestWrapper {

    private final HttpServletRequest request;

    ClientRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }

    @Override
    public String getRequestURI() {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(ShapeShiftProxyServlet.SHAPESHIFT_TARGET)) {
            uri = uri.replaceFirst(ShapeShiftProxyServlet.SHAPESHIFT_TARGET, "");
        }
        return uri;
    }
}
