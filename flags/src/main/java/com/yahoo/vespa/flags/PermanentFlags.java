// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.yahoo.vespa.flags.Dimension.APPLICATION;
import static com.yahoo.vespa.flags.Dimension.ARCHITECTURE;
import static com.yahoo.vespa.flags.Dimension.CERTIFICATE_PROVIDER;
import static com.yahoo.vespa.flags.Dimension.CLAVE;
import static com.yahoo.vespa.flags.Dimension.CLOUD_ACCOUNT;
import static com.yahoo.vespa.flags.Dimension.FLAVOR;
import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.Dimension.VESPA_VERSION;
import static com.yahoo.vespa.flags.Dimension.ZONE_ID;

/**
 * Definition for permanent feature flags
 *
 * @author bjorncs
 */
public class PermanentFlags {

    static final List<String> OWNERS = List.of();
    static final Instant CREATED_AT = Instant.EPOCH;
    static final Instant EXPIRES_AT = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

    // TODO(mpolden): Remove this flag
    public static final UnboundBooleanFlag USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER = defineFeatureFlag(
            "use-alternative-endpoint-certificate-provider", false,
            "Whether to use an alternative CA when provisioning new certificates",
            "Takes effect only on initial application deployment - not on later certificate refreshes!");

    public static final UnboundStringFlag ENDPOINT_CERTIFICATE_PROVIDER = defineStringFlag(
            "endpoint-certificate-provider", "digicert", "The CA to use for endpoint certificates. Must be 'digicert', 'globalsign' or 'zerossl'",
            "Takes effect on initial deployment", TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundStringFlag JVM_GC_OPTIONS = defineStringFlag(
            "jvm-gc-options", "",
            "Sets default jvm gc options",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag HEAP_SIZE_PERCENTAGE = defineIntFlag(
            "heap-size-percentage", 70,
            "Sets default jvm heap size percentage",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundDoubleFlag QUERY_DISPATCH_WARMUP = defineDoubleFlag(
            "query-dispatch-warmup", 5,
            "Warmup duration for query dispatcher",
            "Takes effect at redeployment (requires restart)",
            INSTANCE_ID);

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
            "Takes effect on next iteration of HostCapacityMaintainer.");

    public static final UnboundIntFlag REBOOT_INTERVAL_IN_DAYS = defineIntFlag(
            "reboot-interval-in-days", 30,
            "No reboots are scheduled 0x-1x reboot intervals after the previous reboot, while reboot is " +
                    "scheduled evenly distributed in the 1x-2x range (and naturally guaranteed at the 2x boundary).",
            "Takes effect on next run of NodeRebooter");

    public static final UnboundJacksonFlag<SharedHost> SHARED_HOST = defineJacksonFlag(
            "shared-host", SharedHost.createDisabled(), SharedHost.class,
            "Specifies whether shared hosts can be provisioned, and if so, the advertised " +
                    "node resources of the host, the maximum number of containers, etc.",
            "Takes effect on next iteration of HostCapacityMaintainer.");

    public static final UnboundStringFlag HOST_FLAVOR = defineStringFlag(
            "host-flavor", "",
            "Specifies the Vespa flavor name that the hosts of the matching nodes should have.",
            "Takes effect on next deployment (including internal redeployment).",
            INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID);

    public static final UnboundBooleanFlag SKIP_MAINTENANCE_DEPLOYMENT = defineFeatureFlag(
            "node-repository-skip-maintenance-deployment", false,
            "Whether PeriodicApplicationMaintainer should skip deployment for an application",
            "Takes effect at next run of maintainer",
            INSTANCE_ID);

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
            HOSTNAME, INSTANCE_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag MIN_DISK_THROUGHPUT_MB_S = defineIntFlag(
            "min-disk-throughput-mb-s", 0,
            "Minimum required disk throughput performance, 0 = default, Only when using remote disk",
            "Takes effect when node is provisioned",
            INSTANCE_ID, TENANT_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag MIN_DISK_IOPS_K = defineIntFlag(
            "min-disk-iops-k", 0,
            "Minimum required disk I/O operations per second, unit is kilo, 0 = default, Only when using remote disk",
            "Takes effect when node is provisioned",
            INSTANCE_ID, TENANT_ID, CLUSTER_ID, CLUSTER_TYPE);

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
            INSTANCE_ID);

    public static final UnboundStringFlag METRIC_SET = defineStringFlag(
            "metric-set", "Vespa",
            "Determines which metric set we should use for the given application",
            "Takes effect on next host admin tick",
            INSTANCE_ID);

    private static final String VERSION_QUALIFIER_REGEX = "[a-zA-Z0-9_-]+";
    private static final Pattern QUALIFIER_PATTERN = Pattern.compile("^" + VERSION_QUALIFIER_REGEX + "$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d\\.\\d\\.\\d(\\." + VERSION_QUALIFIER_REGEX + ")?$");

    public static final UnboundStringFlag WANTED_DOCKER_TAG = defineStringFlag(
            "wanted-docker-tag", "",
            "If non-empty the flag value overrides the docker image tag of the wantedDockerImage of the node object. " +
            "If the flag value contains '.', it must specify a valid Vespa version like '8.83.42'.  " +
            "Otherwise a '.' + the flag value will be appended.",
            "Takes effect on the next host admin tick.  The upgrade to the new wanted docker image is orchestrated.",
            value -> value.isEmpty() || QUALIFIER_PATTERN.matcher(value).find() || VERSION_PATTERN.matcher(value).find(),
            HOSTNAME, NODE_TYPE, TENANT_ID, INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID, VESPA_VERSION);

    public static final UnboundStringFlag ZOOKEEPER_SERVER_VERSION = defineStringFlag(
            "zookeeper-server-version", "3.9.1",
            "ZooKeeper server version, a jar file zookeeper-server-<ZOOKEEPER_SERVER_VERSION>-jar-with-dependencies.jar must exist",
            "Takes effect on restart of Docker container",
            NODE_TYPE, INSTANCE_ID, HOSTNAME);

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
            CLUSTER_TYPE, INSTANCE_ID);

    public static final UnboundIntFlag MAX_TRIAL_TENANTS = defineIntFlag(
            "max-trial-tenants", -1,
            "The maximum nr. of tenants with trial plan, -1 is unlimited",
            "Takes effect immediately"
    );

    public static final UnboundBooleanFlag ALLOW_DISABLE_MTLS = defineFeatureFlag(
            "allow-disable-mtls", true,
            "Allow application to disable client authentication",
            "Takes effect on redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_OS_UPGRADES = defineIntFlag(
            "max-os-upgrades", 30,
            "The maximum number hosts that can perform OS upgrade at a time",
            "Takes effect immediately, but any current excess upgrades will not be cancelled"
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
            INSTANCE_ID
    );

    public static final UnboundStringFlag ENDPOINT_CERTIFICATE_ALGORITHM = defineStringFlag(
            "endpoint-certificate-algorithm", "ecdsa_p256",
            // Acceptable values are: "rsa_4096", "ecdsa_p256"
            "Selects algorithm used for an applications endpoint certificate",
            "Takes effect when a new endpoint certificate is requested (on first deployment or deployment adding new endpoints)",
            INSTANCE_ID);

    public static final UnboundDoubleFlag RESOURCE_LIMIT_DISK = defineDoubleFlag(
            "resource-limit-disk", 0.75,
            "Resource limit (between 0.0 and 1.0) for disk usage on content nodes, used by cluster controller for when to block feed",
            "Takes effect on next deployment",
            INSTANCE_ID
    );

    public static final UnboundDoubleFlag RESOURCE_LIMIT_MEMORY = defineDoubleFlag(
            "resource-limit-memory", 0.8,
            "Resource limit (between 0.0 and 1.0) for memory usage on content nodes, used by cluster controller for when to block feed",
            "Takes effect on next deployment",
            INSTANCE_ID
    );

    public static final UnboundListFlag<String> LOGCTL_OVERRIDE = defineListFlag(
            "logctl-override", List.of(), String.class,
            "A list of vespa-logctl statements that are run on container startup. " +
                    "Each item should be on the form <service>:<component> <level>=on",
            "Takes effect on Podman container (re)start",
            INSTANCE_ID, HOSTNAME
    );

    public static final UnboundListFlag<String> ENVIRONMENT_VARIABLES = defineListFlag(
            "environment-variables", List.of(), String.class,
            "A list of environment variables set for all services. " +
                    "Each item should be on the form <ENV_VAR>=<VALUE>",
            "Takes effect on service restart",
            INSTANCE_ID
    );

    public static final UnboundStringFlag CONFIG_PROXY_JVM_ARGS = defineStringFlag(
            "config-proxy-jvm-args", "",
            "Sets jvm args for config proxy (added at the end of startup command, will override existing ones)",
            "Takes effect on restart of Docker container",
            INSTANCE_ID
    );

    // This must be set in a feature flag to avoid flickering between the new and old value during config server upgrade
    public static final UnboundDoubleFlag HOST_MEMORY = defineDoubleFlag(
            "host-memory", -1.0,
            "The memory in GB required by a host's management processes. " +
            "A negative value falls back to hard-coded defaults.",
            "Affects future deployments, JVM settings for new config server Podman containers, auto scaling modelling.",
            ARCHITECTURE, CLAVE, CLOUD_ACCOUNT, FLAVOR);

    // This must be set in a feature flag to avoid flickering between the new and old value during config server upgrade
    public static final UnboundDoubleFlag HOST_MEMORY_RATIO = defineDoubleFlag(
            "host-memory-ratio", -1.0,
            "The ratio of MemTotal reserved for Linux or host processes, and not available to the Podman containers. " +
            "A value outside the range [0.0, 1.0] will use a hard-coded ratio.",
            "Affects future deployments, JVM settings for new config server Podman containers, auto scaling modelling.",
            ARCHITECTURE, CLAVE, CLOUD_ACCOUNT, FLAVOR);

    public static final UnboundBooleanFlag FORWARD_ISSUES_AS_ERRORS = defineFeatureFlag(
            "forward-issues-as-errors", true,
            "When the backend detects a problematic issue with a query, it will by default send it as an error message to the QRS, which adds it in an ErrorHit in the result.  May be disabled using this flag.",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundBooleanFlag DEACTIVATE_ROUTING = defineFeatureFlag(
            "deactivate-routing", false,
            "Deactivates routing for an application by removing all reals from its load balancers. Used in " +
            "cases where we immediately need to stop serving an application, i.e. in case of service violations",
            "Takes effect on next redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> IGNORED_HTTP_USER_AGENTS = defineListFlag(
            "ignored-http-user-agents", List.of(), String.class,
            "List of user agents to ignore (crawlers etc)",
            "Takes effect immediately.",
            INSTANCE_ID);

    public static final UnboundListFlag<String> INCOMPATIBLE_VERSIONS = defineListFlag(
            "incompatible-versions", List.of("8"), String.class,
            "A list of versions which are binary-incompatible with earlier versions. " +
            "A platform version A and an application package compiled against version B are thus incompatible if this " +
            "list contains a version X such that (A >= X) != (B >= X). " +
            "A version specifying only major, or major and minor, imply 0s for the unspecified parts." +
            "This list may also contain '*' wildcards for any suffix of its version number; see the VersionCompatibility " +
            "class for further details. " +
            "The controller will attempt to couple platform upgrades to application changes if their compile versions are " +
            "incompatible with any current deployments. " +
            "The config server will refuse to serve config to nodes running a version which is incompatible with their " +
            "current wanted node version, i.e., nodes about to upgrade to a version which is incompatible with the current.",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundStringFlag ADMIN_CLUSTER_NODE_ARCHITECTURE = defineStringFlag(
            "admin-cluster-node-architecture", "x86_64",
            "Architecture to use for node resources. Used when implicitly creating admin clusters " +
            "(logserver and clustercontroller clusters).",
            "Takes effect on next redeployment",
            value -> Set.of("any", "arm64", "x86_64").contains(value),
            INSTANCE_ID);

    public static final UnboundListFlag<String> CLOUD_ACCOUNTS = defineListFlag(
            "cloud-accounts", List.of(), String.class,
            "A list of 12-digit AWS account IDs that are valid for the given tenant",
            "Takes effect on next deployment through controller",
            TENANT_ID);

    public static final UnboundStringFlag APPLICATION_FILES_WITH_UNKNOWN_EXTENSION = defineStringFlag(
            "fail-deployment-for-files-with-unknown-extension", "FAIL",
            "Whether to log or fail for deployments when app has a file with unknown extension (valid values: LOG, FAIL)",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> DISABLED_DEPLOYMENT_ZONES = defineListFlag(
            "disabled-deployment-zones", List.of(), String.class,
            "The zones, e.g., prod.norway-71, where deployments jobs are currently disabled",
            "Takes effect immediately",
            INSTANCE_ID
    );

    public static final UnboundBooleanFlag ALLOW_USER_FILTERS = defineFeatureFlag(
            "allow-user-filters", true,
            "Allow user filter (chains) in application",
            "Takes effect on next redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag PRE_PROVISIONED_LB_COUNT = defineIntFlag(
            "pre-provisioned-lb-count", 0,
            "Number of application load balancers to have pre-provisioned at any time",
            "Takes immediate effect");

    public static final UnboundLongFlag CONFIG_SERVER_SESSION_EXPIRY_TIME = defineLongFlag(
            "config-server-session-expiry-time", 3600,
            "Expiry time in seconds for remote sessions (session in ZooKeeper). Default should be equal to session lifetime, " +
            "but can be lowered if there are incidents/bugs where one needs to delete sessions",
            "Takes effect immediately"
    );

    public static final UnboundBooleanFlag NOTIFICATION_DISPATCH_FLAG = defineFeatureFlag(
            "dispatch-notifications", true,
            "Whether we should send notification for a given tenant",
            "Takes effect immediately",
            TENANT_ID);

    public static final UnboundIntFlag KEEP_FILE_REFERENCES_DAYS = defineIntFlag(
            "keep-file-references-days", 30,
            "How many days to keep file references on tenant nodes (based on last modification time)",
            "Takes effect on restart of Docker container",
            INSTANCE_ID
    );

    public static final UnboundIntFlag KEEP_FILE_REFERENCES_COUNT = defineIntFlag(
            "keep-file-references-count", 20,
            "How many file references to keep on tenant nodes (no matter what last modification time is)",
            "Takes effect on restart of Docker container",
            ZONE_ID, INSTANCE_ID
    );

    public static final UnboundIntFlag ENDPOINT_CONNECTION_TTL = defineIntFlag(
            "endpoint-connection-ttl", 45,
            "Time to live for connections to endpoints in seconds",
            "Takes effect on next redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag AUTOSCALING = defineFeatureFlag(
            "autoscaling", true,
            "Whether to enable autoscaling",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_HOSTS_PER_HOUR = defineIntFlag(
            "max-hosts-per-hour", 40,
            "The number of hosts that can be provisioned per hour in a zone, before throttling is " +
            "triggered",
            "Takes effect immediately");

    public static final UnboundIntFlag MAX_CERTIFICATES_PER_HOUR = defineIntFlag(
            "max-certificates-per-hour", 10,
            "The number of certificates can be provisioned per hour, before throttling is triggered",
            "Takes effect immediately");

    public static final UnboundBooleanFlag DROP_CACHES = defineFeatureFlag(
            "drop-caches", true,
            "Drop pagecache. " +
            "This is combined with the drop-dentries-and-inodes flag for a single write to /proc/sys/vm/drop_caches.",
            "Takes effect on next tick",
            // The application ID is the exclusive application ID associated with the host,
            // if any, or otherwise hosted-vespa:tenant-host:default.
            INSTANCE_ID, TENANT_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag DROP_DENTRIES = defineIntFlag(
            "drop-dentries", -1,
            "Drop dentries and inodes every N minutes.  0 means every tick. -1 means disabled. " +
            "This is combined with the drop-caches flag for a single write to /proc/sys/vm/drop_caches.",
            "Takes effect on next tick",
            // The application ID is the exclusive application ID associated with the host,
            // if any, or otherwise hosted-vespa:tenant-host:default.
            INSTANCE_ID, TENANT_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag CERT_POOL_SIZE = defineIntFlag(
            "cert-pool-size", 0,
            "Target number of preprovisioned endpoints certificates to maintain",
            "Takes effect on next run of CertificatePoolMaintainer",
            CERTIFICATE_PROVIDER
    );

    public static final UnboundBooleanFlag ENCLAVE_WITHOUT_WIREGUARD = defineFeatureFlag(
            "enclave-without-wireguard", false,
            "Do not use wireguard for inclave. This should only be set for a single legacy account, " +
            "and removed once that account is no longer in use with us",
            "Affects generated terraform code, and ip allocation on host provisioning",
            CLOUD_ACCOUNT
    );

    public static final UnboundStringFlag REFRESH_IDENTITY_BOUNDARY = defineStringFlag(
            "refresh-identity-after", "",
            "Refresh the identity document and certificates issued before this timestamp. Timestamp in ISO8601 format",
            "Takes effect on next host admin tick",
            HOSTNAME
    );

    private PermanentFlags() {}

    private static UnboundBooleanFlag defineFeatureFlag(
            String flagId, boolean defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineFeatureFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, Predicate<String> validator, Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    private static UnboundIntFlag defineIntFlag(
            String flagId, int defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineIntFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundLongFlag defineLongFlag(
            String flagId, long defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineLongFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static UnboundDoubleFlag defineDoubleFlag(
            String flagId, double defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineDoubleFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundJacksonFlag<T> defineJacksonFlag(
            String flagId, T defaultValue, Class<T> jacksonClass,  String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineJacksonFlag(flagId, defaultValue, jacksonClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static String toString(Instant instant) { return DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC).format(instant); }
}
