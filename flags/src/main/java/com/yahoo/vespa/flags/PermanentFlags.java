// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.vespa.flags.custom.EnclaveAccountProfiles;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.CustomerRpmServiceList;
import com.yahoo.vespa.flags.custom.RoleList;
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
import static com.yahoo.vespa.flags.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.Dimension.ENVIRONMENT;
import static com.yahoo.vespa.flags.Dimension.FLAVOR;
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;
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

    public static final UnboundStringFlag ENDPOINT_CERTIFICATE_PROVIDER = defineStringFlag(
            "endpoint-certificate-provider", "digicert", "The CA to use for endpoint certificates. Must be 'digicert', 'globalsign' or 'zerossl'",
            "Takes effect on initial deployment", TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundStringFlag JVM_GC_OPTIONS = defineStringFlag(
            "jvm-gc-options", "",
            "Sets default jvm gc options",
            "Takes effect at redeployment",
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID);

    public static final UnboundIntFlag HEAP_SIZE_PERCENTAGE = defineIntFlag(
            "heap-size-percentage", 69,
            "Sets default jvm heap size percentage",
            "Takes effect at redeployment (requires restart)",
            INSTANCE_ID, CLUSTER_ID);

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

    public static final UnboundIntFlag KEEP_PROVISIONED_EXPIRED_HOSTS_MAX = defineIntFlag(
            "keep-provisioned-expired-hosts-max", 0,
            "The maximum number of provisioned expired hosts to keep for investigation of provisioning issues.",
            "Takes effect on next run of ProvisionedExpirer");

    public static final UnboundIntFlag OS_MAJOR_VERSION = defineIntFlag(
            "os-major-version", 0,
            "The OS major version to use for provisioned hosts. " +
            "Value 0 means use lowest major version available. " +
            "Common values: 8 (AlmaLinux 8), 9 (AlmaLinux 9).",
            "Takes effect when a host is provisioned",
            CLAVE, NODE_TYPE, CLOUD_ACCOUNT, HOSTNAME);

    public static final UnboundJacksonFlag<SharedHost> SHARED_HOST = defineJacksonFlag(
            "shared-host", SharedHost.createDisabled(), SharedHost.class,
            "Specifies whether shared hosts can be provisioned, and if so, the advertised " +
                    "node resources of the host, the maximum number of containers, etc.",
            "Takes effect on next iteration of HostCapacityMaintainer.",
            __ -> true);

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

    public static final UnboundDoubleFlag GLOBAL_HOURLY_TRIAL_CREDIT_SPENDING_LIMIT = defineDoubleFlag(
            "global-hourly-trial-credit-spending-limit", 0.0,
            "The global maximum credit limit per hour (for all tenants). If this limit is exceeded, trial tenant deployments are blocked. Zero/negative value indicates no limit.",
            "Takes effect immediately");

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
            HOSTNAME, NODE_TYPE, CLAVE);

    public static final UnboundStringFlag DOCKER_IMAGE_REPO = defineStringFlag(
            "docker-image-repo", "",
            "Override default docker image repo. Docker image version will be Vespa version.",
            "Takes effect on next deployment from controller",
            ZONE_ID, TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundStringFlag METRIC_SET = defineStringFlag(
            "metric-set", "Vespa9",
            "Determines which metric set we should use for the given application",
            "Takes effect on next host admin tick",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundBooleanFlag VERBOSE_DEPLOY_PARAMETER = defineFeatureFlag(
            "verbose-deploy-parameter", false,
            "Whether (external) deployments should set verbose flag (will mean more logging in practice). " +
                    "Will only be used if flag in request is false (which is the default)",
            "Takes effect at next deployment");

    public static final UnboundJacksonFlag<CustomerRpmServiceList> CUSTOMER_RPM_SERVICES = defineJacksonFlag(
            "customer-rpm-services", CustomerRpmServiceList.empty(), CustomerRpmServiceList.class,
            "Specifies customer rpm services to run on enclave tenant hosts.",
            "Takes effect on next host admin tick.",
            __ -> true,
            TENANT_ID, APPLICATION, INSTANCE_ID, ARCHITECTURE, CLUSTER_ID, CLUSTER_TYPE);


    public static final UnboundBooleanFlag DEFER_OS_UPGRADE = defineFeatureFlag(
            "defer-os-upgrade", false,
            "Whether OS upgrade should be deferred",
            "Takes effect immediately",
            CLOUD_ACCOUNT
    );


    public static final UnboundListFlag<String> OTELCOL_LOGS = defineListFlag(
            "otelcol-logs", List.of(), String.class,
            "Determines log files handled by the OpenTelemetry collector",
            "Takes effect at next tick",
            TENANT_ID, APPLICATION, INSTANCE_ID
    );

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
            HOSTNAME, NODE_TYPE, TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID, VESPA_VERSION);

    public static final UnboundStringFlag ZOOKEEPER_SERVER_VERSION = defineStringFlag(
            "zookeeper-server-version", "3.9.4",
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

    public static final UnboundIntFlag MAX_TRIAL_TENANTS = defineIntFlag(
            "max-trial-tenants", -1,
            "The maximum nr. of tenants with trial plan, -1 is unlimited",
            "Takes effect immediately"
    );

    public static final UnboundIntFlag MAX_TENANTS_PER_USER = defineIntFlag(
            "max-tenants-per-user", 3,
            "The maximum nr. of tenants a user can create",
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

    public static final UnboundDoubleFlag RESOURCE_LIMIT_ADDRESS_SPACE = defineDoubleFlag(
            "resource-limit-address-space", 0.80,
            "Resource limit (between 0.0 and 1.0) for memory address space on content nodes, used by cluster controller for when to block feed",
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

    public static final UnboundIntFlag DELAY_HOST_SECURITY_AGENT_START_MINUTES = defineIntFlag(
            "delay-host-security-agent-start-minutes", 5,
            "The number of minutes (from host admin start) to delay the start of the host security agent",
            "Takes effect on next host-admin tick",
            NODE_TYPE);

    public static final UnboundStringFlag HOST_SECURITY_AGENT_VERSION = defineStringFlag(
            "host-security-agent-version", "",
            "Upgrades/downgrades the host security agent to the specified version, does nothing if empty. Only effective in public systems.",
            "Takes effect on next host-admin tick",
            NODE_TYPE);

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
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundStringFlag ADMIN_CLUSTER_NODE_ARCHITECTURE = defineStringFlag(
            "admin-cluster-node-architecture", "x86_64",
            "Architecture to use for node resources. Used when implicitly creating admin clusters " +
            "(logserver and clustercontroller clusters).",
            "Takes effect on next redeployment",
            value -> Set.of("any", "arm64", "x86_64").contains(value),
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundDoubleFlag LOGSERVER_NODE_MEMORY = defineDoubleFlag(
            "logserver-node-memory", 0.0,
            "Amount of memory (in GiB) to allocate for logserver nodes",
            "Takes effect on allocation from node repository",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundDoubleFlag CLUSTER_CONTROLLER_NODE_MEMORY = defineDoubleFlag(
            "cluster-controller-node-memory", 0.0,
            "Amount of memory (in GiB) to allocate for cluster-controller nodes",
            "Takes effect on allocation from node repository",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundListFlag<String> CLOUD_ACCOUNTS = defineListFlag(
            "cloud-accounts", List.of(), String.class,
            "A list of cloud accounts (e.g. AWS account or GCP project IDs) that are valid for the given tenant",
            "Takes effect on next deployment through controller",
            TENANT_ID);

    public static final UnboundJacksonFlag<EnclaveAccountProfiles> ENCLAVE_ACCOUNT_PROFILES = defineJacksonFlag(
            "enclave-account-profiles", EnclaveAccountProfiles.EMPTY, EnclaveAccountProfiles.class,
            "A list of enclave account profiles that are valid for the given tenant. Includes cloud account and misc. cloud metadata",
            "Takes effect immediately", __ -> true,
            TENANT_ID);

    public static final UnboundBooleanFlag REQUIRE_ENCLAVE = defineFeatureFlag(
            "require-enclave", false,
            "Whether the given tenant should only be allowed to deploy to enclave",
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
            TENANT_ID, APPLICATION, INSTANCE_ID
    );

    public static final UnboundBooleanFlag ALLOW_USER_FILTERS = defineFeatureFlag(
            "allow-user-filters", true,
            "Allow user filter (chains) in application",
            "Takes effect on next redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag ALLOW_STATUS_PAGE = defineFeatureFlag(
            "allow-status-page", false,
            "Shows link to status page for nodes of a specific tenant",
            "Takes effect on browser reload of /user/v1/user",
            CONSOLE_USER_EMAIL, TENANT_ID);

    public static final UnboundIntFlag PRE_PROVISIONED_LB_COUNT = defineIntFlag(
            "pre-provisioned-lb-count", 0,
            "Number of application load balancers to have pre-provisioned at any time",
            "Takes immediate effect");

    public static final UnboundLongFlag CONFIG_SERVER_SESSION_LIFETIME = defineLongFlag(
            "config-server-session-lifetime", 3600,
            "Lifetime / expiry time in seconds for config sessions. " +
            "This can be lowered if there are incidents/bugs where one needs to delete sessions quickly",
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

    public static final UnboundBooleanFlag AUTOSCALING_DETAILED_LOGGING = defineFeatureFlag(
            "autoscaling-detailed-logging", false,
            "Whether to log autoscaling decision data",
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
            // The tenant, application, and instance ID dimensions are set from the exclusive ApplicationId
            // associated with the host, if any, or otherwise hosted-vespa:tenant-host:default.
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag DROP_DENTRIES = defineIntFlag(
            "drop-dentries", -1,
            "Drop dentries and inodes every N minutes.  0 means every tick. -1 means disabled. " +
            "This is combined with the drop-caches flag for a single write to /proc/sys/vm/drop_caches.",
            "Takes effect on next tick",
            // The tenant, application, and instance ID dimensions are set from the exclusive ApplicationId
            // associated with the host, if any, or otherwise hosted-vespa:tenant-host:default.
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundIntFlag CERT_POOL_SIZE = defineIntFlag(
            "cert-pool-size", 0,
            "Target number of preprovisioned endpoints certificates to maintain",
            "Takes effect on next run of CertificatePoolMaintainer",
            CERTIFICATE_PROVIDER
    );

    public static final UnboundStringFlag REFRESH_IDENTITY_BOUNDARY = defineStringFlag(
            "refresh-identity-after", "",
            "Refresh the identity document and certificates issued before this timestamp. Timestamp in ISO8601 format",
            "Takes effect on next host admin tick",
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID, HOSTNAME
    );

    public static final UnboundListFlag<String> LOG_REQUEST_CONTENT = defineListFlag(
            "log-request-content", List.of(), String.class,
            "Include request content in access log for paths starting with any of these prefixes",
            "Takes effect on next redeployment",
            list -> list.stream().allMatch(s -> s.matches("^[a-zA-Z/.0-9-]+:\\d+(?:\\.\\d+)?:\\d+(?:B|kB|MB|GB)?$")),
            INSTANCE_ID);

    public static final UnboundIntFlag ZOOKEEPER_JUTE_MAX_BUFFER = defineIntFlag(
            "zookeeper-jute-max-buffer", 104857600,
            "Jute maxbuffer. Used by zookeeper to determine max buffer when serializing/deserializing." +
                    "Values used in server and client must correspond (so if decreasing this one must be sure" +
                    "that no node has stored more bytes than this)",
            "Takes effect on next reboot of config server");

    public static UnboundJacksonFlag<RoleList> ROLE_DEFINITIONS = defineJacksonFlag(
            "role-definitions", RoleList.empty(), RoleList.class,
            "Role definitions for the system",
            "Takes effect on next iteration of UserManagementMaintainer",
            __ -> true);

    public static final UnboundBooleanFlag FORWARD_ALL_LOG_LEVELS = defineFeatureFlag(
            "forward-all-log-levels", true,
            "Forward all log levels from nodes to logserver (debug and spam levels will be forwarded only if this flag is enabled)",
            "Takes effect at redeployment");

    public static final UnboundStringFlag UNKNOWN_CONFIG_DEFINITION = defineStringFlag(
            "unknown-config-definition", "warn",
            "How to handle user config referencing unknown config definitions. Valid values are 'warn' and 'fail'",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> ALLOWED_ATHENZ_PROXY_IDENTITIES = defineListFlag(
            "allowed-athenz-proxy-identities", List.of(), String.class,
            "Allowed Athenz proxy identities",
            "takes effect at redeployment");

    public static final UnboundBooleanFlag HUBSPOT_SYNC_TENANTS = defineFeatureFlag(
            "hubspot-sync-tenants", true,
            "Whether to sync tenants to HubSpot. Use this to block sync for specific tenants",
            "Takes effect immediately");

    public static final UnboundBooleanFlag HUBSPOT_SYNC_CONTACTS = defineFeatureFlag(
            "hubspot-sync-contacts", true,
            "Whether to sync contacts to HubSpot. Use this to block sync for specific users",
            "Takes effect immediately");

    public static final UnboundBooleanFlag HUBSPOT_SYNC_COMPANIES = defineFeatureFlag(
            "hubspot-sync-companies", true,
            "Whether to sync companies to HubSpot. Use this to block sync for specific companies",
            "Takes effect immediately");

    public static final UnboundStringFlag TLS_CAPABILITIES_ENFORCEMENT_MODE = defineStringFlag(
            "tls-capabilities-enforcement-mode", "disable",
            "Configure Vespa TLS capability enforcement mode",
            "Takes effect on restart of Docker container",
            INSTANCE_ID,HOSTNAME,NODE_TYPE,TENANT_ID,VESPA_VERSION
    );

    public static final UnboundDoubleFlag FEED_CONCURRENCY = defineDoubleFlag(
            "feed-concurrency", 0.5,
            "How much concurrency should be allowed for feed",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundDoubleFlag FEED_NICENESS = defineDoubleFlag(
            "feed-niceness", 0.0,
            "How nice feeding shall be",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT = defineFeatureFlag(
            "container-dump-heap-on-shutdown-timeout", false,
            "Will trigger a heap dump during if container shutdown times out",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundStringFlag SYSTEM_MEMORY_MAX = defineStringFlag(
            "system-memory-max", "",
            "The value to write to /sys/fs/cgroup/system.slice/memory.max, if non-empty. " +
                    "You may want lower memory.high before lowering memory.max, " +
                    "and raise memory.high after raising memory.max.",
            "Takes effect on next tick.",
            NODE_TYPE);

    public static final UnboundIntFlag MAX_UNCOMMITTED_MEMORY = defineIntFlag(
            "max-uncommitted-memory", 130000,
            "Max amount of memory holding updates to an attribute before we do a commit.",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag SORT_BLUEPRINTS_BY_COST = defineFeatureFlag(
            "sort-blueprints-by-cost", false,
            "If true blueprints are sorted based on cost estimate, rather than absolute estimated hits",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> JDISC_HTTP_COMPLIANCE_VIOLATIONS = defineListFlag(
            "jdisc-http-compliance-violations", List.of(), String.class,
            "List of HTTP compliance violation that are allowed (compared to Jetty's RFC7230)",
            "Takes effect at redeployment",
            VESPA_VERSION, INSTANCE_ID);

    public static final UnboundIntFlag SEARCHNODE_INITIALIZER_THREADS = defineIntFlag(
            "searchnode-initializer-threads", 0,
            "Number of initializer threads used for loading structures from disk at proton startup." +
                    "The threads are shared between document databases when value is larger than 0." +
                    "When set to 0 (default) we use 1 separate thread per document database.",
            "Takes effect at startup of search node",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundListFlag<String> IGNORE_CORE_DUMP_TYPE_WHEN_REPORTING = defineListFlag(
            "ignore-core-dump-type-when-reporting", List.of(), String.class,
            "Whether to ignore core dump reporting (creating or updating a Jira ticket) for the specified core dump types." +
                    "Typically set to JVM_HEAP and/or OOM for apps that have issues in application code " +
                    "that the customer needs to fix and we don't want to create or update a Jira issue for.",
            "Takes effect immediately",
            list -> Set.of("CORE_DUMP", "JVM_HEAP", "OOM").containsAll(list),
            TENANT_ID, APPLICATION, INSTANCE_ID, ZONE_ID, ENVIRONMENT
    );

    public static final UnboundBooleanFlag LOCK_APPLICATION_PACKAGE_ACCESS = defineFeatureFlag(
            "lock-application-package-access", false,
            "Whether application package access should be locked down",
            "Takes effect immediately",
            TENANT_ID, APPLICATION
    );

    public static final UnboundIntFlag BACKUP_INTERVAL = defineIntFlag(
            "backup-interval", 0,
            "The interval in hours between automatic backup snapshots. " +
                    "Value 0 disables automatic backups.",
            "Takes effect on next maintainer run",
            CLUSTER_ID, APPLICATION, TENANT_ID, ZONE_ID);

    public static final UnboundBooleanFlag BACKUP_SINGLE_GROUP = defineFeatureFlag(
            "backup-single-group", false,
            "Whether to limit back up to a single group during automatic backup snapshots. " +
            "Recommended only when node bucket distribution is near equivalent between groups.",
            "Takes effect on next maintainer run",
            CLUSTER_ID, APPLICATION, TENANT_ID, ZONE_ID);

    public static final UnboundBooleanFlag IGNORE_CONNECTIVITY_CHECKS_AT_STARTUP = defineFeatureFlag(
            "ignore-connectivity-checks-at-startup", false,
            "Ignore connectivity checks in config-sentinel at startup. " +
                    "Normally the sentinel checks that a sufficient fraction of cluster nodes can reach each other before starting services. " +
                    "When this flag is set, services are started immediately regardless of connectivity check results.",
            "Takes effect on next host restart",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundListFlag<String> ALLOW_FLAVORS = defineListFlag(
            "allow-flavors", List.of(), String.class,
            "Flavors that that we will allow provisioning (flavors with lifecycle 'active' are allowed by default)" +
                    ". Each string in the list is a regexp, e.g. 'c4d-.*' or 'c4d-high.*'.",
            "Takes effect immediately",
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_ID, CLUSTER_TYPE
    );

    public static final UnboundListFlag<String> DENY_FLAVORS = defineListFlag(
            "deny-flavors", List.of(), String.class,
            "Flavors that that we will deny provisioning (flavors with lifecycle 'new' and 'retired' are disallowed by default)" +
                    ". Each string in the list is a regexp, e.g. 'c4d-.*' or 'c4d-high.*'.",
            "Takes effect immediately",
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_ID, CLUSTER_TYPE
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
            String flagId, T defaultValue, Class<T> jacksonClass,  String description, String modificationEffect, Predicate<T> validator, Dimension... dimensions) {
        return Flags.defineJacksonFlag(flagId, defaultValue, jacksonClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    private static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    private static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, Predicate<List<T>> validator, Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    private static String toString(Instant instant) { return DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC).format(instant); }
}
