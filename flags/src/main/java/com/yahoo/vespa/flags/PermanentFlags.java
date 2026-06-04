// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

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
import static com.yahoo.vespa.flags.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;
import static com.yahoo.vespa.flags.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.Dimension.VESPA_VERSION;

/**
 * Definition for permanent feature flags
 *
 * @author bjorncs
 */
public class PermanentFlags {

    static final List<String> OWNERS = List.of();
    static final Instant CREATED_AT = Instant.EPOCH;
    static final Instant EXPIRES_AT = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

    public static final UnboundStringFlag JVM_GC_OPTIONS = defineStringFlag(
            "jvm-gc-options", "",
            "Sets default jvm gc options",
            "Takes effect at redeployment",
            TENANT_ID, APPLICATION, INSTANCE_ID, CLUSTER_TYPE, CLUSTER_ID, HOSTNAME);

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

    public static final UnboundJacksonFlag<SharedHost> SHARED_HOST = defineJacksonFlag(
            "shared-host", SharedHost.createDisabled(), SharedHost.class,
            "Specifies whether shared hosts can be provisioned, and if so, the advertised " +
                    "node resources of the host, the maximum number of containers, etc.",
            "Takes effect on next iteration of HostCapacityMaintainer.",
            __ -> true);

    public static final UnboundListFlag<String> INACTIVE_MAINTENANCE_JOBS = defineListFlag(
            "inactive-maintenance-jobs", List.of(), String.class,
            "The list of maintenance jobs that are inactive.",
            "Takes effect immediately, but any currently running jobs will run until completion.");

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

    public static final UnboundBooleanFlag ALLOW_DISABLE_MTLS = defineFeatureFlag(
            "allow-disable-mtls", true,
            "Allow application to disable client authentication",
            "Takes effect on redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> TLS_CIPHERS_OVERRIDE = defineListFlag(
            "tls-ciphers-override", List.of(), String.class,
            "Override TLS ciphers enabled for port 4443 on hosted application containers",
            "Takes effect on redeployment",
            INSTANCE_ID
    );

    public static final UnboundDoubleFlag RESOURCE_LIMIT_DISK = defineDoubleFlag(
            "resource-limit-disk", 0.8,
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

    public static final UnboundListFlag<String> ENVIRONMENT_VARIABLES = defineListFlag(
            "environment-variables", List.of(), String.class,
            "A list of environment variables set for all services. " +
                    "Each item should be on the form <ENV_VAR>=<VALUE>",
            "Takes effect on service restart",
            INSTANCE_ID
    );

    public static final UnboundBooleanFlag FORWARD_ISSUES_AS_ERRORS = defineFeatureFlag(
            "forward-issues-as-errors", true,
            "When the backend detects a problematic issue with a query, it will by default send it as an error message to the QRS, which adds it in an ErrorHit in the result.  May be disabled using this flag.",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundListFlag<String> IGNORED_HTTP_USER_AGENTS = defineListFlag(
            "ignored-http-user-agents",
            List.of("GGG", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0)"),
            String.class,
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

    public static final UnboundStringFlag APPLICATION_FILES_WITH_UNKNOWN_EXTENSION = defineStringFlag(
            "fail-deployment-for-files-with-unknown-extension", "FAIL",
            "Whether to log or fail for deployments when app has a file with unknown extension (valid values: LOG, FAIL)",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag ALLOW_USER_FILTERS = defineFeatureFlag(
            "allow-user-filters", true,
            "Allow user filter (chains) in application",
            "Takes effect on next redeployment",
            INSTANCE_ID);

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

    public static final UnboundBooleanFlag IGNORE_CONNECTIVITY_CHECKS_AT_STARTUP = defineFeatureFlag(
            "ignore-connectivity-checks-at-startup", false,
            "Ignore connectivity checks in config-sentinel at startup. " +
                    "Normally the sentinel checks that a sufficient fraction of cluster nodes can reach each other before starting services. " +
                    "When this flag is set, services are started immediately regardless of connectivity check results.",
            "Takes effect on next host restart",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundLongFlag ZOOKEEPER_PRE_ALLOC_SIZE_KIB = defineLongFlag(
            "zookeeper-pre-alloc-size", 65536,
            "Setting for zookeeper.preAllocSize flag in KiB, can be reduced from default value "
                    + "e.g. when running tests to avoid writing a large, sparse, mostly unused file",
            "Takes effect on restart of Docker container");


    public static final UnboundStringFlag VESPA_USE_MALLOC_IMPL = defineStringFlag(
            "vespa-use-malloc-impl", "",
            "Which malloc implementation to use  " +
                    "Valid values: 'vespamalloc', 'mimalloc', '' (empty string, meaning default malloc implementation).",
            "Takes effect at next reboot of the node",
            TENANT_ID, APPLICATION, INSTANCE_ID, HOSTNAME, CLUSTER_TYPE
    );

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_CONTENT_NODE_MAINTENANCE_OP_CONCURRENCY = defineIntFlag(
            "max-content-node-maintenance-op-concurrency", -1,
            "Sets the maximum concurrency for maintenance-related operations on content nodes. " +
            "Only intended as a manual emergency brake feature if a system is suddenly incapable of handling " +
            "regular maintenance pressure.",
            "Takes effect immediately",
            INSTANCE_ID);

    private PermanentFlags() {}

    public static UnboundBooleanFlag defineFeatureFlag(
            String flagId, boolean defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineFeatureFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static UnboundStringFlag defineStringFlag(
            String flagId, String defaultValue, String description, String modificationEffect, Predicate<String> validator, Dimension... dimensions) {
        return Flags.defineStringFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    public static UnboundIntFlag defineIntFlag(
            String flagId, int defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineIntFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static UnboundLongFlag defineLongFlag(
            String flagId, long defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineLongFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static UnboundDoubleFlag defineDoubleFlag(
            String flagId, double defaultValue, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineDoubleFlag(flagId, defaultValue, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static <T> UnboundJacksonFlag<T> defineJacksonFlag(
            String flagId, T defaultValue, Class<T> jacksonClass,  String description, String modificationEffect, Predicate<T> validator, Dimension... dimensions) {
        return Flags.defineJacksonFlag(flagId, defaultValue, jacksonClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    public static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, dimensions);
    }

    public static <T> UnboundListFlag<T> defineListFlag(
            String flagId, List<T> defaultValue, Class<T> elementClass, String description, String modificationEffect, Predicate<List<T>> validator, Dimension... dimensions) {
        return Flags.defineListFlag(flagId, defaultValue, elementClass, OWNERS, toString(CREATED_AT), toString(EXPIRES_AT), description, modificationEffect, validator, dimensions);
    }

    private static String toString(Instant instant) { return DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC).format(instant); }
}
