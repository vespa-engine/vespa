// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.cloud.config.deploy.plugin.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.*;


/**
 * @author hmusum
 * @since 5.1.22
 */
@org.apache.maven.plugins.annotations.Mojo(name = "deploy", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ApplicationDeployMojo extends AbstractMojo {

    @Parameter(property = "plugin.configuration.project",
    defaultValue = "${project}")
    protected org.apache.maven.project.MavenProject project;

    /**
     * Config server hostname.
     *
     */
    @Parameter(property = "plugin.configuration.configServerHostname",
            alias = "hostname")
    private String configServerHostname;

    /**
     * Config server port (default is 19071).
     *
     */
    @Parameter(property = "plugin.configuration.configServerPort",
            defaultValue = "19071",
            alias = "port")
    private Integer configServerPort;

    /**
     * Activate after preparing (default is false)
     *
     */
    @Parameter(property = "plugin.configuration.activate",
            defaultValue = "false")
    private Boolean activate;

    /**
     * Application file to deploy
     */
    @Parameter(property = "plugin.configuration.applicationFile", defaultValue="${project.basedir}/target/application.zip")
    private File applicationFile;

    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (configServerPort == null) {
            configServerPort = 19071;
        }
        if (!applicationFile.exists() || !applicationFile.canRead()) {
            log.warn("Cannot find or read '" + applicationFile + "', skipping");
        } else {
            log.info("Using application zip file '" + applicationFile + "'");
            deploy(applicationFile, log);
        }
    }

    private void deploy(File applicationFile, Log log) throws MojoExecutionException {
        String url = configServerHostname + ":" + configServerPort;
        log.info("Using " + url);
        try {
            InputStream is = new FileInputStream(applicationFile);
            HttpClient client = new HttpClient(configServerHostname, configServerPort, log);
            String response = client.deployApplication(is);
            if (response == null) {
                log.error("Unable to deploy to " + url);
                System.exit(1);
            }
            long sessionId = getSessionIdFromResponse(response);
            client.prepareApplication(sessionId);
            if (activate) {
                client.activateApplication(sessionId);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private long getSessionIdFromResponse(String response) {
        long sessionId = 0;
        try {
            JSONObject json = new JSONObject(response);
            sessionId = Long.parseLong(json.getString("session-id"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sessionId;
    }

    /**
     * HTTP client
     */
    private static class HttpClient {
        static final String APPLICATION_ZIP = "application/zip";
        static final String APPLICATION_JSON = "application/json";

        private static final String path = "/application/v1/session";

        private final String userAgent;
        private final String host;
        private final Log log;
        private final int port;
        private final String urlPrefix;
        private int connectTimeout = 10000;
        private int readTimeout = 10000;

        /**
         * Create a new client instance
         *
         * @param host gateway host
         * @param port gateway port
         */
        public HttpClient(String host, int port, Log log) {
            this.host = host;
            this.port = port;
            this.log = log;
            this.urlPrefix = "http://" + host + ":" + port;
            this.userAgent = getClass().getSimpleName() + "/" + "1.0";
            log.debug("Set user agent string to '" + userAgent + "'");
        }

        /**
         * Post data to a HTTP connection
         *
         * @param inputStream for data to be sent
         * @param http        an HTTPConnection instance
         * @return Response or null if the response could not be parsed
         * @throws IOException if not able to post application or read response
         */
        String doPost(InputStream inputStream, HttpURLConnection http) throws IOException {
            http.setRequestMethod("POST");
            write(inputStream, http);
            String response = parseResponse(http);
            log.info(response);
            http.disconnect();
            return response;
        }

        /**
         * Do a HTTP PUT
         *
         * @param http an HTTPConnection instance
         * @return Response or null if the response could not be parsed
         * @throws IOException if not able to perform operation
         */
        String doPut(HttpURLConnection http) throws IOException {
            http.setRequestMethod("PUT");
            http.connect();
            String response = parseResponse(http);
            log.info(response);
            http.disconnect();
            return response;
        }

        protected void write(InputStream inputStream, HttpURLConnection http) throws IOException {
            http.connect();
            OutputStream out = http.getOutputStream();
            byte[] app = new byte[1024];
            int i;
            while ((i = inputStream.read(app)) != -1) {
                log.debug("Writing  " + i + " bytes");
                out.write(app);
            }
            out.flush();
        }

        public String deployApplication(InputStream inputStream) throws IOException {
            HttpURLConnection http = getHttpConnection(path);
            log.info("Deploying application to " + http.getURL());
            http.setRequestProperty("Content-Type", APPLICATION_ZIP);
            return doPost(inputStream, http);
        }

        public String prepareApplication(long sessionId) throws IOException {
            HttpURLConnection http = getHttpConnection(path + "/" + sessionId + "/prepared");
            http.setRequestProperty("Content-Type", APPLICATION_JSON);
            log.info("Preparing application with session id " + sessionId + " to " + http.getURL());
            return doPut(http);
        }

        public String activateApplication(long sessionId) throws IOException {
            HttpURLConnection http = getHttpConnection(path + "/" + sessionId + "/active");
            http.setRequestProperty("Content-Type", APPLICATION_JSON);
            log.info("Activating application with session id " + sessionId + " to " + http.getURL());
            return doPut(http);
        }

        /**
         * Set up a HTTP connection to the config server
         *
         * @param path application API path
         * @return a HTTP connection
         * @throws java.io.IOException if an unhandled I/O error occurred
         */
        private HttpURLConnection getHttpConnection(String path) throws IOException {
            StringBuilder urlString = new StringBuilder(urlPrefix);
            if (!path.startsWith("/")) urlString.append('/');
            urlString.append(path);

            URL url = new URL(urlString.toString());
            HttpURLConnection http = openConnection(url);
            http.setRequestProperty("User-Agent", userAgent);
            http.setDoInput(true);
            http.setDoOutput(true);
            http.setUseCaches(false);
            http.setConnectTimeout(connectTimeout);
            http.setReadTimeout(readTimeout);

            return http;
        }

        protected HttpURLConnection openConnection(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }

        /**
         * Parse the response
         *
         * @param http HTTP connection
         * @return DOM document
         * @throws java.io.IOException if an unhandled I/O error occurred
         */
        private String parseResponse(HttpURLConnection http) throws IOException {
            String response = null;
            String contentType = http.getContentType();
            log.debug("Content type=" + contentType);
            if (contentType != null && contentType.startsWith(APPLICATION_JSON)) {
                BufferedReader reader;
                StringBuilder stringBuilder;
                try {
                    // read the output from the server
                    reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
                    stringBuilder = new StringBuilder();

                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    response = stringBuilder.toString();
                    reader.close();
                } catch (IOException e) {
                    log.warn(e.getClass().getSimpleName() +
                            " while reading HTTP response: " + e.getMessage());
                    InputStream es = http.getErrorStream();
                    if (es != null) {
                        final String message = readAll(new InputStreamReader(es));
                        es.close();
                        throw new IOException(message);
                    }
                }
            }
            return response;
        }
    }

    /**
     * Reads all data from a reader into a string.
     */
    private static String readAll(Reader reader) throws IOException {
        StringBuilder ret = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1)
            ret.append((char) c);
        return ret.toString();
    }
}
