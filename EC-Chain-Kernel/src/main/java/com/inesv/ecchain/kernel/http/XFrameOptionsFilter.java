package com.inesv.ecchain.kernel.http;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public final class XFrameOptionsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        ((HttpServletResponse) response).setHeader("X-FRAME-OPTIONS", "SAMEORIGIN");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

}
