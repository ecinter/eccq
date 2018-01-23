package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.*;
import com.inesv.ecchain.kernel.deploy.RuntimeEnvironment;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;

@Component
public final class API {
    @Autowired
    private static RuntimeEnvironment runtimeEnvironment;
    public static int openecapiport;
    public static int openecapisslport;
    public static List<String> disabledAPIs;
    public static List<APITag> disabledAPITags;
    public static String adminPassword;
    public static int apiServerIdleTimeout;
    public static boolean apiservercors;
    static boolean disableAdminPassword;
    static int maxRecords;
    static boolean enableAPIUPnP;
    private static String[] DISABLED_HTTP_METHODS = {"TRACE", "OPTIONS", "HEAD"};
    private static Set<String> allowedBotHosts;
    private static List<NetworkAddress> allowedBotNets;
    private static final Map<String, PasswordCount> incorrectPasswords = new HashMap<>();
    private static Server apiecserver;
    private static URI welcomeecpageuri;

    @PostConstruct
    private static void initPostConstruct() {
        maxRecords = PropertiesUtil.getKeyForInt("ec.maxAPIRecords", 0);
        enableAPIUPnP = PropertiesUtil.getKeyForBoolean("ec.enableAPIUPnP");
        apiservercors = PropertiesUtil.getKeyForBoolean("ec.apiServerCORS");
        apiServerIdleTimeout = PropertiesUtil.getKeyForInt("ec.apiServerIdleTimeout", 0);
        adminPassword = PropertiesUtil.getKeyForString("ec.adminPassword", "");
        List<String> disabled = new ArrayList<>(PropertiesUtil.getStringListProperty("ec.disabledAPIs"));
        Collections.sort(disabled);
        disabledAPIs = Collections.unmodifiableList(disabled);//获取禁用的API
        disabled = PropertiesUtil.getStringListProperty("ec.disabledAPITags");
        Collections.sort(disabled);
        List<APITag> apiTags = new ArrayList<>(disabled.size());
        disabled.forEach(tagName -> apiTags.add(APITag.fromDisplayName(tagName)));
        disabledAPITags = Collections.unmodifiableList(apiTags);//获取禁用的API标签
        List<String> allowedBotHostsList = PropertiesUtil.getStringListProperty("ec.allowedBotHosts");
        if (!allowedBotHostsList.contains("*")) {
            Set<String> hosts = new HashSet<>();
            List<NetworkAddress> nets = new ArrayList<>();
            for (String host : allowedBotHostsList) {
                if (host.contains("/")) {
                    try {
                        nets.add(new NetworkAddress(host));
                    } catch (UnknownHostException e) {
                        LoggerUtil.logError("Unknown network " + host, e);
                        throw new RuntimeException(e.toString(), e);
                    }
                } else {
                    hosts.add(host);
                }
            }
            allowedBotHosts = Collections.unmodifiableSet(hosts);
            allowedBotNets = Collections.unmodifiableList(nets);
        } else {
            allowedBotHosts = null;
            allowedBotNets = null;
        }//获取允许访问的网络

        boolean enableAPIServer = PropertiesUtil.getKeyForBoolean("ec.enableAPIServer");//接受http / json API请求 true/false
        if (enableAPIServer) {
            final int port = PropertiesUtil.getKeyForInt("ec.apiServerPort", 0);
            final int sslPort = PropertiesUtil.getKeyForInt("ec.apiServerSSLPort", 0);
            final String host = PropertiesUtil.getKeyForString("ec.apiServerHost", null);
            disableAdminPassword = PropertiesUtil.getKeyForBoolean("ec.disableAdminPassword") || ("127.0.0.1".equals(host) && adminPassword.isEmpty());//true
            apiecserver = new Server();
            apiecserver.setRequestLog(null);
            ServerConnector connector;
            boolean enableSSL = PropertiesUtil.getKeyForBoolean("ec.apiSSL");//false
            //
            // Create the HTTP connector
            //
            if (!enableSSL || port != sslPort) {
                HttpConfiguration configuration = new HttpConfiguration();
                configuration.setSendDateHeader(false);
                configuration.setSendServerVersion(false);

                connector = new ServerConnector(apiecserver, new HttpConnectionFactory(configuration));
                connector.setPort(port);
                connector.setHost(host);
                connector.setIdleTimeout(apiServerIdleTimeout);
                connector.setReuseAddress(true);
                apiecserver.addConnector(connector);
                LoggerUtil.logInfo("API server using HTTP port " + port);
            }
            //
            // Create the HTTPS connector
            //
            final SslContextFactory sslContextFactory;
            if (enableSSL) {
                HttpConfiguration https_config = new HttpConfiguration();
                https_config.setSendDateHeader(false);
                https_config.setSendServerVersion(false);
                https_config.setSecureScheme("https");
                https_config.setSecurePort(sslPort);
                https_config.addCustomizer(new SecureRequestCustomizer());
                sslContextFactory = new SslContextFactory();
                String keyStorePath = Paths.get(runtimeEnvironment.getDirProvider().getEcUserHomeDir()).resolve(Paths.get(PropertiesUtil.getKeyForString("ec.keyStorePath", null))).toString();
                LoggerUtil.logInfo("Using keystore: " + keyStorePath);
                sslContextFactory.setKeyStorePath(keyStorePath);
                sslContextFactory.setKeyStorePassword(PropertiesUtil.getKeyForString("ec.keyStorePassword", null));
                sslContextFactory.addExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_DSS_WITH_DES_CBC_SHA", "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
                sslContextFactory.addExcludeProtocols("SSLv3");
                sslContextFactory.setKeyStoreType(PropertiesUtil.getKeyForString("ec.keyStoreType", null));
                List<String> ciphers = PropertiesUtil.getStringListProperty("ec.apiSSLCiphers");
                if (!ciphers.isEmpty()) {
                    sslContextFactory.setIncludeCipherSuites(ciphers.toArray(new String[ciphers.size()]));
                }
                connector = new ServerConnector(apiecserver, new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(https_config));
                connector.setPort(sslPort);
                connector.setHost(host);
                connector.setIdleTimeout(apiServerIdleTimeout);
                connector.setReuseAddress(true);
                apiecserver.addConnector(connector);
                LoggerUtil.logInfo("API server using HTTPS port " + sslPort);
            } else {
                sslContextFactory = null;
            }//sslContextFactory=null
            String localhost = "0.0.0.0".equals(host) || "127.0.0.1".equals(host) ? "localhost" : host;
            try {
                welcomeecpageuri = new URI(enableSSL ? "https" : "http", null, localhost, enableSSL ? sslPort : port, "/index.html", null, null);//http://localhost:7876/index.html
            } catch (URISyntaxException e) {
                LoggerUtil.logError("Cannot resolve browser URI", e);
            }
            openecapiport = !Constants.IS_LIGHT_CLIENT && "0.0.0.0".equals(host) && allowedBotHosts == null && (!enableSSL || port != sslPort) ? port : 0;//0
            openecapisslport = !Constants.IS_LIGHT_CLIENT && "0.0.0.0".equals(host) && allowedBotHosts == null && enableSSL ? sslPort : 0;//0
            HandlerList apiHandlers = new HandlerList();
            ServletContextHandler apiHandler = new ServletContextHandler();


            LoggerUtil.logInfo("apiccc:"+Thread.currentThread().getContextClassLoader().getResource(""));
            String classpath = Thread.currentThread().getContextClassLoader().getResource("").toString();
            String apiResourceBase = classpath + PropertiesUtil.getKeyForString("ec.apiResourceBase", null);///html/www
            LoggerUtil.logInfo("apiresource"+apiResourceBase);
            if (apiResourceBase != null) {
                ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
                defaultServletHolder.setInitParameter("dirAllowed", "false");
                defaultServletHolder.setInitParameter("resourceBase", apiResourceBase);
                defaultServletHolder.setInitParameter("welcomeServlets", "true");
                defaultServletHolder.setInitParameter("redirectWelcome", "true");
                defaultServletHolder.setInitParameter("gzip", "true");
                defaultServletHolder.setInitParameter("etags", "true");
                apiHandler.addServlet(defaultServletHolder, "/*");
                apiHandler.setWelcomeFiles(new String[]{PropertiesUtil.getKeyForString("ec.apiWelcomeFile", null)});
            }//设置资源文件及首页

            String javadocResourceBase = classpath + PropertiesUtil.getKeyForString("ec.javadocResourceBase", null);//./html/doc
            if (javadocResourceBase != null) {
                ContextHandler contextHandler = new ContextHandler("/doc");
                ResourceHandler docFileHandler = new ResourceHandler();
                docFileHandler.setDirectoriesListed(false);
                docFileHandler.setWelcomeFiles(new String[]{"index.html"});
                docFileHandler.setResourceBase(javadocResourceBase);
                contextHandler.setHandler(docFileHandler);
                apiHandlers.addHandler(contextHandler);
            }//设置doc资源文件及首页

            ServletHolder servletHolder = apiHandler.addServlet(APIServlet.class, "/ec");
            servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(
                    null, Math.max(PropertiesUtil.getKeyForInt("ec.maxUploadFileSize", 0), Constants.EC_MAX_TAGGED_DATA_DATA_LENGTH), -1L, 0));

            servletHolder = apiHandler.addServlet(APIProxyServlet.class, "/ec-proxy");
            servletHolder.setInitParameters(Collections.singletonMap("idleTimeout",
                    "" + Math.max(apiServerIdleTimeout - Constants.PROXY_IDLE_TIMEOUT_DELTA, 0)));
            servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(
                    null, Math.max(PropertiesUtil.getKeyForInt("ec.maxUploadFileSize", 0), Constants.EC_MAX_TAGGED_DATA_DATA_LENGTH), -1L, 0));
            apiHandler.addServlet(ShapeShiftProxyServlet.class, ShapeShiftProxyServlet.SHAPESHIFT_TARGET + "/*");

            GzipHandler gzipHandler = new GzipHandler();
            if (!PropertiesUtil.getKeyForBoolean("ec.enableAPIServerGZIPFilter")) {
                gzipHandler.setExcludedPaths("/ec", "/ec-proxy");
            }
            gzipHandler.setIncludedMethods("GET", "POST");
            gzipHandler.setMinGzipSize(Constants.EC_MIN_COMPRESS_SIZE);
            apiHandler.setGzipHandler(gzipHandler);

            apiHandler.addServlet(APITestServlet.class, "/test");
            apiHandler.addServlet(APITestServlet.class, "/test-proxy");
            apiHandler.addServlet(H2ShellServlet.class, "/dbshell");

            if (apiservercors) {
                FilterHolder filterHolder = apiHandler.addFilter(CrossOriginFilter.class, "/*", null);
                filterHolder.setInitParameter("allowedHeaders", "*");
                filterHolder.setAsyncSupported(true);
            }

            if (PropertiesUtil.getKeyForBoolean("ec.apiFrameOptionsSameOrigin")) {
                FilterHolder filterHolder = apiHandler.addFilter(XFrameOptionsFilter.class, "/*", null);
                filterHolder.setAsyncSupported(true);
            }
            disableHttpMethods(apiHandler);

            apiHandlers.addHandler(apiHandler);
            apiHandlers.addHandler(new DefaultHandler());

            apiecserver.setHandler(apiHandlers);
            apiecserver.setStopAtShutdown(true);
            ThreadPool.runBeforeStart(() -> {
                try {
                    if (enableAPIUPnP) {
                        Connector[] apiConnectors = apiecserver.getConnectors();
                        for (Connector apiConnector : apiConnectors) {
                            if (apiConnector instanceof ServerConnector)
                                UPnP.addPort(((ServerConnector) apiConnector).getPort());
                        }
                    }
                    APIServlet.initClass();
                    APIProxyServlet.initClass();
                    APITestServlet.initClass();
                    apiecserver.start();
                    if (sslContextFactory != null) {
                        LoggerUtil.logDebug("API SSL Protocols: " + Arrays.toString(sslContextFactory.getSelectedProtocols()));
                        LoggerUtil.logDebug("API SSL Ciphers: " + Arrays.toString(sslContextFactory.getSelectedCipherSuites()));
                    }
                    LoggerUtil.logInfo("Started API server at " + host + ":" + port + (enableSSL && port != sslPort ? ", " + host + ":" + sslPort : ""));
                } catch (Exception e) {
                    LoggerUtil.logError("Failed to start API server", e);
                    throw new RuntimeException(e.toString(), e);
                }

            }, true);

        } else {
            apiecserver = null;
            disableAdminPassword = false;
            openecapiport = 0;
            openecapisslport = 0;
            LoggerUtil.logInfo("API server not enabled");
        }


    }

    public static void start() {
    }

    public static void shutdown() {
        if (apiecserver != null) {
            try {
                apiecserver.stop();
                if (enableAPIUPnP) {
                    Connector[] apiConnectors = apiecserver.getConnectors();
                    for (Connector apiConnector : apiConnectors) {
                        if (apiConnector instanceof ServerConnector) {
                            UPnP.delPort(((ServerConnector) apiConnector).getPort());
                        }
                    }
                }
            } catch (Exception e) {
                LoggerUtil.logError("Failed to stop API server", e);
            }
        }
    }

    public static void verifyPassword(HttpServletRequest req) throws ParameterException {
        if (API.disableAdminPassword) {
            return;
        }
        if (API.adminPassword.isEmpty()) {
            throw new ParameterException(com.inesv.ecchain.kernel.http.JSONResponses.NO_PASSWORD_IN_CONFIG);
        }
        checkOrLockPassword(req);
    }

    public static boolean checkPassword(HttpServletRequest req) {
        if (API.disableAdminPassword) {
            return true;
        }
        if (API.adminPassword.isEmpty()) {
            return false;
        }
        if (Convert.emptyToNull(req.getParameter("adminPassword")) == null) {
            return false;
        }
        try {
            checkOrLockPassword(req);
            return true;
        } catch (ParameterException e) {
            return false;
        }
    }

    private static void checkOrLockPassword(HttpServletRequest req) throws ParameterException {
        int now = new EcTime.EpochEcTime().getTime();
        String remoteHost = req.getRemoteHost();
        synchronized (incorrectPasswords) {
            PasswordCount passwordCount = incorrectPasswords.get(remoteHost);
            if (passwordCount != null && passwordCount.count >= 3 && now - passwordCount.time < 60 * 60) {
                LoggerUtil.logInfo("Too many incorrect admin password attempts from " + remoteHost);
                throw new ParameterException(com.inesv.ecchain.kernel.http.JSONResponses.LOCKED_ADMIN_PASSWORD);
            }
            if (!API.adminPassword.equals(req.getParameter("adminPassword"))) {
                if (passwordCount == null) {
                    passwordCount = new PasswordCount();
                    incorrectPasswords.put(remoteHost, passwordCount);
                }
                passwordCount.count++;
                passwordCount.time = now;
                LoggerUtil.logInfo("Incorrect adminPassword from " + remoteHost);
                throw new ParameterException(com.inesv.ecchain.kernel.http.JSONResponses.INCORRECT_ADMIN_PASSWORD);
            }
            if (passwordCount != null) {
                incorrectPasswords.remove(remoteHost);
            }
        }
    }

    static boolean isAllowed(String remoteHost) {
        if (API.allowedBotHosts == null || API.allowedBotHosts.contains(remoteHost)) {
            return true;
        }
        try {
            BigInteger hostAddressToCheck = new BigInteger(InetAddress.getByName(remoteHost).getAddress());
            for (NetworkAddress network : API.allowedBotNets) {
                if (network.contains(hostAddressToCheck)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            // can't resolve, disallow
            LoggerUtil.logInfo("Unknown remote host " + remoteHost);
        }
        return false;

    }

    private static void disableHttpMethods(ServletContextHandler servletContext) {
        SecurityHandler securityHandler = servletContext.getSecurityHandler();
        if (securityHandler == null) {
            securityHandler = new ConstraintSecurityHandler();
            servletContext.setSecurityHandler(securityHandler);
        }
        disableHttpMethods(securityHandler);
    }

    private static void disableHttpMethods(SecurityHandler securityHandler) {
        if (securityHandler instanceof ConstraintSecurityHandler) {
            ConstraintSecurityHandler constraintSecurityHandler = (ConstraintSecurityHandler) securityHandler;
            for (String method : DISABLED_HTTP_METHODS) {
                disableHttpMethod(constraintSecurityHandler, method);
            }
            ConstraintMapping enableEverythingButTraceMapping = new ConstraintMapping();
            Constraint enableEverythingButTraceConstraint = new Constraint();
            enableEverythingButTraceConstraint.setName("Enable everything but TRACE");
            enableEverythingButTraceMapping.setConstraint(enableEverythingButTraceConstraint);
            enableEverythingButTraceMapping.setMethodOmissions(DISABLED_HTTP_METHODS);
            enableEverythingButTraceMapping.setPathSpec("/");
            constraintSecurityHandler.addConstraintMapping(enableEverythingButTraceMapping);
        }
    }

    private static void disableHttpMethod(ConstraintSecurityHandler securityHandler, String httpMethod) {
        ConstraintMapping mapping = new ConstraintMapping();
        Constraint constraint = new Constraint();
        constraint.setName("Disable " + httpMethod);
        constraint.setAuthenticate(true);
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/");
        mapping.setMethod(httpMethod);
        securityHandler.addConstraintMapping(mapping);
    }

    public static URI getWelcomeecpageuri() {
        return welcomeecpageuri;
    }

    private static class PasswordCount {
        private int count;
        private int time;
    }

    private static class NetworkAddress {

        private BigInteger netAddress;
        private BigInteger netMask;

        private NetworkAddress(String address) throws UnknownHostException {
            String[] addressParts = address.split("/");
            if (addressParts.length == 2) {
                InetAddress targetHostAddress = InetAddress.getByName(addressParts[0]);
                byte[] srcBytes = targetHostAddress.getAddress();
                netAddress = new BigInteger(1, srcBytes);
                int maskBitLength = Integer.valueOf(addressParts[1]);
                int addressBitLength = (targetHostAddress instanceof Inet4Address) ? 32 : 128;
                netMask = BigInteger.ZERO
                        .setBit(addressBitLength)
                        .subtract(BigInteger.ONE)
                        .subtract(BigInteger.ZERO.setBit(addressBitLength - maskBitLength).subtract(BigInteger.ONE));
            } else {
                throw new IllegalArgumentException("Invalid address: " + address);
            }
        }

        private boolean contains(BigInteger hostAddressToCheck) {
            return hostAddressToCheck.and(netMask).equals(netAddress);
        }

    }
}
