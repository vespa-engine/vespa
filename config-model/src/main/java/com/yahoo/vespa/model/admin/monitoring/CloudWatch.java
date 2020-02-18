package com.yahoo.vespa.model.admin.monitoring;

import java.util.Optional;

/**
 * Helper object for CloudWatch configuration.
 *
 * @author gjoranv
 */
public class CloudWatch {
    private final String region;
    private final String namespace;
    private final MetricsConsumer consumer;

    private HostedAuth hostedAuth;
    private String profile;

    public CloudWatch(String region, String namespace, MetricsConsumer consumer) {
        this.region = region;
        this.namespace = namespace;
        this.consumer = consumer;
    }

    public String region() { return region; }
    public String namespace() { return namespace; }
    public String consumer() { return consumer.getId(); }

    public Optional<HostedAuth> hostedAuth() {return Optional.ofNullable(hostedAuth); }
    public Optional<String> profile() { return Optional.ofNullable(profile); }

    public void setHostedAuth(HostedAuth hostedAuth) {
        this.hostedAuth = hostedAuth;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public static class HostedAuth {
        public final String accessKeyName;
        public final String secretKeyName;

        public HostedAuth(String accessKeyName, String secretKeyName) {
            this.accessKeyName = accessKeyName;
            this.secretKeyName = secretKeyName;
        }
    }

}
