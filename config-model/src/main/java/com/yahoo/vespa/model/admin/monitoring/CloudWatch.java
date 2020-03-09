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
    private SharedCredentials sharedCredentials;

    public CloudWatch(String region, String namespace, MetricsConsumer consumer) {
        this.region = region;
        this.namespace = namespace;
        this.consumer = consumer;
    }

    public String region() { return region; }
    public String namespace() { return namespace; }
    public String consumer() { return consumer.getId(); }

    public Optional<HostedAuth> hostedAuth() {return Optional.ofNullable(hostedAuth); }
    public Optional<SharedCredentials> sharedCredentials() {return Optional.ofNullable(sharedCredentials); }

    public void setHostedAuth(String accessKeyName, String secretKeyName) {
        hostedAuth = new HostedAuth(accessKeyName, secretKeyName);
    }

    public void setSharedCredentials(String file, Optional<String> profile) {
        sharedCredentials = new SharedCredentials(file, profile);
    }

    public static class HostedAuth {
        public final String accessKeyName;
        public final String secretKeyName;

        HostedAuth(String accessKeyName, String secretKeyName) {
            this.accessKeyName = accessKeyName;
            this.secretKeyName = secretKeyName;
        }
    }

    public static class SharedCredentials {
        public final String file;
        public final Optional<String> profile;

        SharedCredentials(String file, Optional<String> profile) {
            this.file = file;
            this.profile = profile;
        }
    }

}
