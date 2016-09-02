package com.yahoo.vespa.config.server.host;

import com.google.inject.Inject;
import com.yahoo.config.provision.HostLivenessTracker;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps track of the last config request made by each hostname.
 * This always remembers all requests forever since the moment is is constructed.
 * This is the implementation which will be injected to components who request a HostLivenessTracker.
 * 
 * @author bratseth
 */
public class ConfigRequestHostLivenessTracker implements HostLivenessTracker {

    private final Clock clock;
    private final Map<String, Instant> lastRequestFromHost = new HashMap<>();

    @Inject
    @SuppressWarnings("unused")
    public ConfigRequestHostLivenessTracker() {
        this(Clock.systemUTC());
    }
    
    public ConfigRequestHostLivenessTracker(Clock clock) {
        this.clock = clock;
    }
            
    @Override
    public void receivedRequestFrom(String hostname) {
        lastRequestFromHost.put(hostname, clock.instant());
    }

    @Override
    public Optional<Instant> lastRequestFrom(String hostname) {
        return Optional.ofNullable(lastRequestFromHost.get(hostname));
    }

}
