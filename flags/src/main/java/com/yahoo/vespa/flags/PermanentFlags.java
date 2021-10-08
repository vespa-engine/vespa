// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.FetchVector.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.FetchVector.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.ZONE_ID;

/**
 * Definition for permanent feature flags
 *
 * @author bjorncs
 */
public class PermanentFlags {

    static final List<String> OWNERS = List.of();
    static final Instant CREATED_AT = Instant.EPOCH;
    static final Instant EXPIRES_AT = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

    public static final UnboundBooleanFlag USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER = defineFeatureFlag(
            "use-alternative-endpoint-certificate-provider", false,
            "Whether to use an alternative CA when provisioning new certificates",
            "Takes effect only on initial application deployment - not on later certificate refreshes!");

    public static final UnboundStringFlag JVM_GC_OPTIONS = defineStringFlag(
            "jvm-gc-options", "",
            "Sets default jvm gc options",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag FLEET_CANARY = defineFeatureFlag(
            "fleet-canary", false,
            "Whether the host is a fleet canary.",
            "Takes effect on next host admin tick.",
            HOSTNAME);

    public static final UnboundListFlag<ClusterCapacity> PREPROVISION_CAPACITY = defineListFlag(
            "preprovision-capacity", List.of(), ClusterCapacity.class,
            "Specifies the resources that ought to be immediately available for additional cluster " +
                    "allocations.  If the resources are not available, additional hosts will be provisioned. " +
                    "Only applies to dynamically provisioned zones.",
            "Takes effect on next iteration of DynamicProvisioningMaintainer.");

    public static final UnboundIntFlag REBOOT_INTERVAL_IN_DAYS = defineIntFlag(
            "reboot-interval-in-days", 15,
            "No reboots are scheduled 0x-1x reboot intervals after the previous reboot, while reboot is " +
                    "scheduled evenly distributed in the 1x-2x range (and naturally guaranteed at the 2x boundary).",
            "Takes effect on next run of NodeRebooter");

    public static final UnboundJacksonFlag<SharedHost> SHARED_HOST = defineJacksonFlag(
            "shared-host", SharedHost.createDisabled(), SharedHost.class,
            "Specifies whether shared hosts can be provisioned, and if so, the advertised " +
                    "node resources of the host, the maximum number of containers, etc.",
            "Takes effect on next iteration of DynamicProvisioningMaintainer.");

    public static final UnboundBooleanFlag SKIP_MAINTENANCE_DEPLOYMENT = defineFeatureFlag(
            "node-repository-skip-maintenance-deployment", false,
            "Whether PeriodicApplicationMaintainer should skip deployment for an application",
            "Takes effect at next run of maintainer",
            APPLICATION_ID);

    public static final UnboundListFlag<String> INACTIVE_MAINTENANCE_JOBS = defineListFlag(
            "inactive-maintenance-jobs", List.of(), String.class,
            "The list of maintenance jobs that are inactive.",
            "Takes effect immediately, but any currently running jobs will run until completion.");

    public static final UnboundListFlag<String> OUTBOUND_BLOCKED_IPV4 = defineListFlag(
            "container-outbound-blocked-ipv4", List.of(), String.class,
            "List of IPs or CIDRs that are blocked for outbound connections",
            "Takes effect on next tick");

    public static final UnboundListFlag<String> OUTBOUND_BLOCKED_IPV6 = defineListFlag(
            "container-outbound-blocked-ipv6", List.of(), String.class,
            "List of IPs or CIDRs that are blocked for outbound connections",
            "Takes effect on next tick");

    public static final UnboundIntFlag TENANT_BUDGET_QUOTA = defineIntFlag(
            "tenant-budget-quota", -1,
            "The budget in cents/hr a tenant is allowed spend per instance, as calculated by NodeResources",
            "Only takes effect on next deployment, if set to a value other than the default for flag!",
            TENANT_ID);

    public static final UnboundDoubleFlag CONTAINER_CPU_CAP = defineDoubleFlag(
            "container-cpu-cap", 0,
            "Hard limit on how many CPUs a container may use. This value is multiplied by CPU allocated to node, so " +
                    "to cap CPU at 200%, set this to 2, etc. 0 disables the cap to allow unlimited CPU.",
            "Takes effect on next node agent tick. Change is orchestrated, but does NOT require container restart",
            HOSTNAME, APPLICATION_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundListFlag<String> DISABLED_HOST_ADMIN_TASKS = defineListFlag(
            "disabled-host-admin-tasks", List.of(), String.class,
            "List of host-admin task names (as they appear in the log, e.g. root>main>UpgradeTask), or some node-agent " +
                    "functionality (see NodeAgentTask), that should be skipped",
            "Takes effect on next host admin tick",
            HOSTNAME, NODE_TYPE);

    public static final UnboundStringFlag DOCKER_IMAGE_REPO = defineStringFlag(
            "docker-image-repo", "",
            "Override default docker image repo. Docker image version will be Vespa version.",
            "Takes effect on next deployment from controller",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag ZOOKEEPER_SERVER_VERSION = defineStringFlag(
            "zookeeper-server-version", "3.6.3",
            "ZooKeeper server version, a jar file zookeeper-server-<ZOOKEEPER_SERVER_VERSION>-jar-with-dependencies.jar must exist",
            "Takes effect on restart of Docker container",
            NODE_TYPE, APPLICATION_ID, HOSTNAME);

    public static final UnboundBooleanFlag ENABLE_PUBLIC_SIGNUP_FLOW = defineFeatureFlag(
            "enable-public-signup-flow", false,
            "Show the public signup flow for a user in the console",
            "takes effect on browser reload of api/user/v1/user",
            CONSOLE_USER_EMAIL);

    public static final UnboundLongFlag INVALIDATE_CONSOLE_SESSIONS = defineLongFlag(
            "invalidate-console-sessions", 0,
            "Invalidate console sessions (cookies) issued before this unix timestamp",
            "Takes effect on next api request"
    );

    public static final UnboundBooleanFlag JVM_OMIT_STACK_TRACE_IN_FAST_THROW = defineFeatureFlag(
            "jvm-omit-stack-trace-in-fast-throw", true,
            "Controls JVM option OmitStackTraceInFastThrow (default feature flag value is true, which is the default JVM option value as well)",
            "takes effect on JVM restart",
            CLUSTER_TYPE, APPLICATION_ID);

    public static final UnboundIntFlag MAX_TRIAL_TENANTS = defineIntFlag(
            "max-trial-tenants", -1,
            "The maximum nr. of tenants with trial plan, -1 is unlimited",
            "Takes effect immediately"
    );

    public static final UnboundBooleanFlag ALLOW_DISABLE_MTLS = defineFeatureFlag(
            "allow-disable-mtls", true,
            "Allow application to disable client authentication",
            "Takes effect on redeployment",
            APPLICATION_ID);

    public static final UnboundIntFlag MAX_REBUILDS = defineIntFlag(
            "max-host-rebuilds", 10,
            "The maximum number of hosts allowed to rebuild at a time",
            "Takes effect immediately, but any current excess rebuilds will not be cancelled"
    );

    public static final UnboundListFlag<String> EXTENDED_TRIAL_TENANTS = defineListFlag(
            "extended-trial-tenants", List.of(), String.class,
            "Tenants that will not be expired from their trial plan",
            "Takes effect immediately, used by the CloudTrialExpirer maintainer",
            TENANT_ID
    );

    public static final UnboundListFlag<String> TLS_CIPHERS_OVERRIDE = defineListFlag(
            "tls-ciphers-override", List.of(), String.class,
            "Override TLS ciphers enabled for port 4443 on hosted application containers",
            "Takes effect on redeployment",
            APPLICATION_ID
    );

    public static final UnboundStringFlag ENDPOINT_CERTIFICATE_ALGORITHM = defineStringFlag(
            "endpoint-certificate-algorithm", "",
            // Acceptable values are: "rsa_2048", "rsa_4096", "ecdsa_p256"
            "Selects algorithm used for an applications endpoint certificate, or use provider default if blank",
            "Takes effect when a new endpoint certificate is requested (first deployment of new application/instance)",
            APPLICATION_ID);

    public static final UnboundDoubleFlag RESOURCE_LIMIT_DISK = defineDoubleFlag(
            "resource-limit-disk", 0.8,
            "Resource limit (between 0.0 and 1.0) for disk used by cluster controller for when to block feed",
            "Takes effect on next deployment",
            APPLICATION_ID
    );

    public static final UnboundDoubleFlag RESOURCE_LIMIT_MEMORY = defineDoubleFlag(
            "resource-limit-memory", 0.8,
            "Resource limit (between 0.0 and 1.0) for memory used by cluster controller for when to block feed",
            "Takes effect on next deployment",
            APPLICATION_ID
    );

    public static final UnboundListFlag<String> LOGCTL_OVERRIDE = defineListFlag(
            "logctl-override", List.of(), String.class,
            "Run vespa-logctl statements on container startup. Should be on the form <service>:<component> <level>=on",
            "Takes effect on container restart",
            APPLICATION_ID, HOSTNAME
    );

    public static final UnboundStringFlag CONFIG_PROXY_JVM_ARGS = defineStringFlag(
            "config-proxy-jvm-args", "",
            "Sets jvm args for config proxy (added at the end of startup command, will override existing ones)",
            "Takes effect on restart of Docker container",
            ZONE_ID, APPLICATION_ID);

    private PermanentFlags() {}

    private static UnboundBooleanFlag defineFeatureFlag(
            String flagId, boolean defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineFeatureFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundIntFlag defineIntFlag(
            String flagId, int defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineIntFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundLongFlag defineLongFlag(
            String flagId, long defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineLongFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundDoubleFlag defineDoubleFlag(
            String flagId, double defaultValue, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineDoubleFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundJacksonFlag<T> defineJacksonFlag(
            String flagId, T defaultValue, Class<T> jacksonClass,  String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineJacksonFlag(flagId, defaultValue, jacksonClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static String toString(Instant instant) { return DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC).format(instant); }
}
