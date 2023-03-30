// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import ai.vespa.http.DomainName;
import com.yahoo.vespa.service.executor.RunletExecutor;
import com.yahoo.vespa.service.monitor.ServiceId;

import java.net.URL;
import java.time.Duration;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
class StateV1HealthEndpoint implements HealthEndpoint {
    private final ServiceId serviceId;
    private final URL url;
    private final Duration requestTimeout;
    private final Duration connectionKeepAlive;
    private final Duration delay;
    private final RunletExecutor executor;

    StateV1HealthEndpoint(ServiceId serviceId,
                          DomainName hostname,
                          int port,
                          Duration delay,
                          Duration requestTimeout,
                          Duration connectionKeepAlive,
                          RunletExecutor executor) {
        this.serviceId = serviceId;
        this.delay = delay;
        this.executor = executor;
        this.url = uncheck(() -> new URL("http", hostname.value(), port, "/state/v1/health"));
        this.requestTimeout = requestTimeout;
        this.connectionKeepAlive = connectionKeepAlive;
    }

    @Override
    public ServiceId getServiceId() {
        return serviceId;
    }

    @Override
    public HealthMonitor startMonitoring() {
        var updater = new StateV1HealthUpdater(url, requestTimeout, connectionKeepAlive);
        return new StateV1HealthMonitor(updater, executor, delay);
    }

    @Override
    public String description() {
        return url.toString();
    }

    @Override
    public String toString() {
        return description();
    }
}
