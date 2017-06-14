// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import com.yahoo.vespa.clustercontroller.utils.communication.http.AsyncHttpClient;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import com.yahoo.vespa.clustercontroller.utils.communication.http.SyncHttpClient;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * There are some stuff to work around with the apache client.
 *   - Whether to use a proxy or not is global, not per request.
 *   - Timeout is not handled per request (and is not a request timeout but seems like a "something happening on TCP" timeout.
 *   - It is not thread safe.
 *
 * This class gets around these issues by creating one instance per unique setting, and ensuring only one request use a given instance at a time.
 */
public class ApacheAsyncHttpClient implements AsyncHttpClient<HttpResult> {
    private static final Logger log = Logger.getLogger(ApacheAsyncHttpClient.class.getName());
    public interface SyncHttpClientFactory {
        SyncHttpClient createInstance(String proxyHost, int proxyPort, long timeoutMs);
    }
    public static class Settings {
        String proxyHost;
        int proxyPort;
        long timeout;

        Settings(HttpRequest request) {
            timeout = request.getTimeoutMillis();
            if (request.getPath() != null
                && !request.getPath().isEmpty()
                && request.getPath().charAt(0) != '/')
            {
                proxyHost = request.getHost();
                proxyPort = request.getPort();
                int colo = request.getPath().indexOf(':');
                int slash = request.getPath().indexOf('/', colo);
                if (colo < 0 && slash < 0) {
                    throw new IllegalStateException("Http path '" + request.getPath() + "' looks invalid. "
                                                  + "Cannot extract proxy server data. Is it a regular request that "
                                                  + "should start with a slash?");
                }
                if (colo < 0) {
                    request.setPort(80);
                    request.setHost(request.getPath().substring(0, slash));
                } else {
                    request.setHost(request.getPath().substring(0, colo));
                    request.setPort(Integer.valueOf(request.getPath().substring(colo + 1, slash)));
                }
                request.setPath(request.getPath().substring(slash));
            }
        }

        @Override
        public boolean equals(Object other) {
            Settings o = (Settings) other;
            if (timeout != o.timeout || proxyPort != o.proxyPort
                || (proxyHost == null ^ o.proxyHost == null)
                || (proxyHost != null && !proxyHost.equals(o.proxyHost)))
            {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return (proxyHost == null ? 0 : proxyHost.hashCode()) ^ proxyPort ^ Long.valueOf(timeout).hashCode();
        }
    }
    private final Executor executor;
    private final SyncHttpClientFactory clientFactory;
    private boolean closed = false;
    private final int maxInstanceCacheSize; // Maximum number of currently unused instances.
    private final Map<Settings, LinkedList<SyncHttpClient>> apacheInstances = new LinkedHashMap<Settings, LinkedList<SyncHttpClient>>() {
        protected @Override boolean removeEldestEntry(Map.Entry<Settings,LinkedList<SyncHttpClient>> eldest) {
            return getUnusedCacheSize() > maxInstanceCacheSize;
        }
    };

    public ApacheAsyncHttpClient(Executor executor) {
        this(executor, new SyncHttpClientFactory() {
            @Override
            public SyncHttpClient createInstance(String proxyHost, int proxyPort, long timeoutMs) {
                return new ApacheHttpInstance(proxyHost, proxyPort, timeoutMs);
            }
        });
    }

    public ApacheAsyncHttpClient(Executor executor, SyncHttpClientFactory clientFactory) {
        this.executor = executor;
        this.clientFactory = clientFactory;
        maxInstanceCacheSize = 16;
        log.fine("Starting apache com.yahoo.vespa.clustercontroller.utils.communication.async HTTP client");
    }

    private SyncHttpClient getFittingInstance(Settings settings) {
        synchronized (apacheInstances) {
            if (closed) throw new IllegalStateException("Http client has been closed for business.");
            LinkedList<SyncHttpClient> fittingInstances = apacheInstances.get(settings);
            if (fittingInstances == null) {
                fittingInstances = new LinkedList<>();
                apacheInstances.put(settings, fittingInstances);
            }
            if (fittingInstances.isEmpty()) {
                return clientFactory.createInstance(settings.proxyHost, settings.proxyPort, settings.timeout);
            } else {
                return fittingInstances.removeFirst();
            }
        }
    }
    private void insertInstance(Settings settings, SyncHttpClient instance) {
        synchronized (apacheInstances) {
            LinkedList<SyncHttpClient> fittingInstances = apacheInstances.get(settings);
            if (closed || fittingInstances == null) {
                instance.close();
                return;
            }
            fittingInstances.addLast(instance);
        }
    }
    private int getUnusedCacheSize() {
        int size = 0;
        synchronized (apacheInstances) {
            for (LinkedList<SyncHttpClient> list : apacheInstances.values()) {
                size += list.size();
            }
        }
        return size;
    }

    @Override
    public AsyncOperation<HttpResult> execute(HttpRequest r) {
        final HttpRequest request = r.clone(); // Gonna modify it to extract proxy information
        final Settings settings = new Settings(request);
        final SyncHttpClient instance = getFittingInstance(settings);
        final AsyncOperationImpl<HttpResult> op = new AsyncOperationImpl<>(r.toString(), r.toString(true));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpResult result;
                Exception failure = null;
                try{
                    result = instance.execute(request);
                } catch (Exception e) {
                    result = new HttpResult().setHttpCode(500, "Apache client failed to execute request.");
                    failure = e;
                }
                insertInstance(settings, instance);
                // Must insert back instance before tagging operation complete to ensure a following
                // call can reuse same instance
                if (failure != null) {
                    op.setFailure(failure, result);
                } else {
                    op.setResult(result);
                }
            }
        });
        return op;
    }

    @Override
    public void close() {
        synchronized (apacheInstances) {
            closed = true;
            for (LinkedList<SyncHttpClient> list : apacheInstances.values()) {
                for (SyncHttpClient instance : list) {
                    instance.close();
                }
            }
            apacheInstances.clear();
        }
    }
}
