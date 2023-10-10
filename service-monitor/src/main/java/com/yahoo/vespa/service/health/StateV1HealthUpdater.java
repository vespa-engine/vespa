// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author hakonhall
 */
class StateV1HealthUpdater implements HealthUpdater {
    private static final Logger logger = Logger.getLogger(StateV1HealthUpdater.class.getName());

    private static final Pattern CONFIG_SERVER_ENDPOINT_PATTERN = Pattern.compile("^http://(cfg[0-9]+)\\.");


    private final String endpoint;
    private final StateV1HealthClient healthClient;

    private volatile ServiceStatusInfo serviceStatusInfo = new ServiceStatusInfo(ServiceStatus.UNKNOWN);

    StateV1HealthUpdater(URL url, Duration requestTimeout, Duration connectionKeepAlive) {
        this(url.toString(), new StateV1HealthClient(url, requestTimeout, connectionKeepAlive));
    }

    StateV1HealthUpdater(String endpoint, StateV1HealthClient healthClient) {
        this.endpoint = endpoint;
        this.healthClient = healthClient;
    }

    @Override
    public ServiceStatusInfo getServiceStatusInfo() {
        return serviceStatusInfo;
    }

    @Override
    public void run() {
        // Get time before fetching rather than after, to make the resulting age be an upper limit.
        Instant now = Instant.now();

        HealthInfo healthInfo;
        try {
            healthInfo = healthClient.get();
        } catch (Exception e) {
            healthInfo = HealthInfo.fromException(e);
        }

        ServiceStatus newServiceStatus = healthInfo.isHealthy() ? ServiceStatus.UP : ServiceStatus.DOWN;

        final Optional<Instant> newSince;
        if (newServiceStatus == serviceStatusInfo.serviceStatus()) {
            newSince = serviceStatusInfo.since();
        } else {
            newSince = Optional.of(now);

            Matcher matcher = CONFIG_SERVER_ENDPOINT_PATTERN.matcher(endpoint);
            if (matcher.find()) {
                logger.info("New health status for " + matcher.group(1) + ": " + healthInfo.toString());
            }
        }

        serviceStatusInfo = new ServiceStatusInfo(newServiceStatus, newSince, Optional.of(now),
                healthInfo.getErrorDescription(), Optional.of(endpoint));
    }

    @Override
    public void close() {
        healthClient.close();
    }

}
