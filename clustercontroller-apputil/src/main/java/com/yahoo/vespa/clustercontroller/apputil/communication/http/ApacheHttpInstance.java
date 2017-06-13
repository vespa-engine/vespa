// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import com.yahoo.vespa.clustercontroller.utils.communication.http.SyncHttpClient;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

// NOTE: these are all deprecated:
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Synchronous http client using Apache commons.
 */
public class ApacheHttpInstance implements SyncHttpClient {
    private static final Logger log = Logger.getLogger(ApacheHttpInstance.class.getName());
    DefaultHttpClient client;

    public ApacheHttpInstance(String proxyHost, int proxyPort, long timeoutMs) {
        if (timeoutMs > Integer.MAX_VALUE) throw new IllegalArgumentException("Cannot handle timeout not contained in an integer");
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, (int) timeoutMs);
        HttpConnectionParams.setSoTimeout(httpParams, (int) timeoutMs);

        if (proxyHost != null && !proxyHost.isEmpty()) {
            httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, proxyPort, "http"));
        }

        client = new DefaultHttpClient(httpParams);
    }

    /** This function is not threadsafe. */
    public HttpResult execute(HttpRequest r) {
        HttpRequest.HttpOp op = r.getHttpOperation();
        if (op == null) {
            if (r.getPostContent() != null) {
                log.fine("Request " + r + " has no HTTP function specified. Assuming POST as post content is set.");
                op = HttpRequest.HttpOp.POST;
            } else {
                log.fine("Request " + r + " has no HTTP function specified. Assuming GET as post content is set.");
                op = HttpRequest.HttpOp.GET;
            }
        }
        if (r.getPostContent() != null
            && !(op.equals(HttpRequest.HttpOp.POST) || op.equals(HttpRequest.HttpOp.PUT)))
        {
            throw new IllegalStateException("A " + op + " operation can't have content");
        }
        try {
            HttpHost target = new HttpHost(r.getHost(), r.getPort(), "http");
            org.apache.http.HttpRequest req = null;

            String path = r.getPath();
            int uriOption = 0;
            for (HttpRequest.KeyValuePair option : r.getUrlOptions()) {
                path += (++uriOption == 1 ? '?' : '&');
                path += option.getKey() + '=' + option.getValue();
            }

            switch (op) {
                case POST:
                    HttpPost post = new HttpPost(path);
                    if (r.getPostContent() != null) {
                        post.setEntity(new StringEntity(r.getPostContent().toString()));
                    }
                    req = post;
                    break;
                case GET:
                    req = new HttpGet(path);
                    break;
                case PUT:
                    HttpPut put = new HttpPut(path);
                    put.setEntity(new StringEntity(r.getPostContent().toString()));
                    req = put;
                    break;
                case DELETE:
                    req = new HttpDelete(path);
                    break;
            }

            for (HttpRequest.KeyValuePair header : r.getHeaders()) {
                req.addHeader(header.getKey(), header.getValue());
            }

            HttpResponse rsp = client.execute(target, req);
            HttpEntity entity = rsp.getEntity();

            HttpResult result = new HttpResult();
            result.setHttpCode(rsp.getStatusLine().getStatusCode(), rsp.getStatusLine().getReasonPhrase());

            if (entity != null) {
                result.setContent(EntityUtils.toString(entity));
            }
            for (Header header : rsp.getAllHeaders()) {
                result.addHeader(header.getName(), header.getValue());
            }

            return result;
        } catch (Exception e) {
            HttpResult result = new HttpResult();

            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            writer.flush();

            result.setHttpCode(500, "Got exception " + writer.toString() + " when sending message.");
            return result;
        }
    }

    public void close() {
        client.getConnectionManager().shutdown();
    }
}
