// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.component.Vtag;
import com.yahoo.vespa.defaults.Defaults;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.FetchVector.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.FetchVector.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.FetchVector.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.VESPA_VERSION;
import static com.yahoo.vespa.flags.FetchVector.Dimension.ZONE_ID;

/**
 * Definitions of feature flags.
 *
 * <p>To use feature flags, define the flag in this class as an "unbound" flag, e.g. {@link UnboundBooleanFlag}
 * or {@link UnboundStringFlag}. At the location you want to get the value of the flag, you need the following:</p>
 *
 * <ol>
 *     <li>The unbound flag</li>
 *     <li>A {@link FlagSource}. The flag source is typically available as an injectable component. Binding
 *     an unbound flag to a flag source produces a (bound) flag, e.g. {@link BooleanFlag} and {@link StringFlag}.</li>
 *     <li>If you would like your flag value to be dependent on e.g. the application ID, then 1. you should
 *     declare this in the unbound flag definition in this file (referring to
 *     {@link FetchVector.Dimension#APPLICATION_ID}), and 2. specify the application ID when retrieving the value, e.g.
 *     {@link BooleanFlag#with(FetchVector.Dimension, String)}. See {@link FetchVector} for more info.</li>
 * </ol>
 *
 * <p>Once the code is in place, you can override the flag value. This depends on the flag source, but typically
 * there is a REST API for updating the flags in the config server, which is the root of all flag sources in the zone.</p>
 *
 * @author hakonhall
 */
public class Flags {

    private static volatile TreeMap<FlagId, FlagDefinition> flags = new TreeMap<>();

    public static final UnboundBooleanFlag IPV6_IN_GCP = defineFeatureFlag(
            "ipv6-in-gcp", false,
            List.of("hakonhall"), "2023-05-15", "2023-06-15",
            "Provision GCP hosts with external IPv6 addresses",
            "Takes effect on the next host provisioning");

    public static final UnboundBooleanFlag DROP_CACHES = defineFeatureFlag(
            "drop-caches", false,
            List.of("hakonhall", "baldersheim"), "2023-03-06", "2023-06-05",
            "Drop caches on tenant hosts",
            "Takes effect on next tick",
            ZONE_ID,
            // The application ID is the exclusive application ID associated with the host,
            // if any, or otherwise hosted-vespa:tenant-host:default.
            APPLICATION_ID, TENANT_ID, CLUSTER_ID, CLUSTER_TYPE);

    public static final UnboundDoubleFlag DEFAULT_TERM_WISE_LIMIT = defineDoubleFlag(
            "default-term-wise-limit", 1.0,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Default limit for when to apply termwise query evaluation",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag QUERY_DISPATCH_POLICY = defineStringFlag(
            "query-dispatch-policy", "adaptive",
            List.of("baldersheim"), "2022-08-20", "2023-12-31",
            "Select query dispatch policy, valid values are adaptive, round-robin, best-of-random-2," +
                    " latency-amortized-over-requests, latency-amortized-over-time",
            "Takes effect at redeployment (requires restart)",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundStringFlag SUMMARY_DECODE_POLICY = defineStringFlag(
            "summary-decode-policy", "eager",
            List.of("baldersheim"), "2023-03-30", "2023-12-31",
            "Select summary decoding policy, valid values are eager and on-demand/ondemand.",
            "Takes effect at redeployment (requires restart)",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag FEED_SEQUENCER_TYPE = defineStringFlag(
            "feed-sequencer-type", "THROUGHPUT",
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Selects type of sequenced executor used for feeding in proton, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment (requires restart)",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_UNCOMMITTED_MEMORY = defineIntFlag(
            "max-uncommitted-memory", 130000,
            List.of("geirst, baldersheim"), "2021-10-21", "2023-12-31",
            "Max amount of memory holding updates to an attribute before we do a commit.",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag RESPONSE_SEQUENCER_TYPE = defineStringFlag(
            "response-sequencer-type", "ADAPTIVE",
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Selects type of sequenced executor used for mbus responses, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag RESPONSE_NUM_THREADS = defineIntFlag(
            "response-num-threads", 2,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Number of threads used for mbus responses, default is 2, negative number = numcores/4",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_COMMUNICATIONMANAGER_THREAD = defineFeatureFlag(
            "skip-communicationmanager-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Should we skip the communicationmanager thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REQUEST_THREAD = defineFeatureFlag(
            "skip-mbus-request-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Should we skip the mbus request thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REPLY_THREAD = defineFeatureFlag(
            "skip-mbus-reply-thread", false,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Should we skip the mbus reply thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE = defineFeatureFlag(
            "async-message-handling-on-schedule", false,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "Optionally deliver async messages in own thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag FEED_CONCURRENCY = defineDoubleFlag(
            "feed-concurrency", 0.5,
            List.of("baldersheim"), "2020-12-02", "2023-12-31",
            "How much concurrency should be allowed for feed",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag FEED_NICENESS = defineDoubleFlag(
            "feed-niceness", 0.0,
            List.of("baldersheim"), "2022-06-24", "2023-12-31",
            "How nice feeding shall be",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);


    public static final UnboundIntFlag MBUS_JAVA_NUM_TARGETS = defineIntFlag(
            "mbus-java-num-targets", 2,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_CPP_NUM_TARGETS = defineIntFlag(
            "mbus-cpp-num-targets", 2,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag RPC_NUM_TARGETS = defineIntFlag(
            "rpc-num-targets", 2,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number of rpc targets per content node",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_JAVA_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-java-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag MBUS_CPP_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-cpp-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundIntFlag RPC_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "rpc-events-before-wakeup", 1,
            List.of("baldersheim"), "2022-07-05", "2023-12-31",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MBUS_NUM_NETWORK_THREADS = defineIntFlag(
            "mbus-num-network-threads", 1,
            List.of("baldersheim"), "2022-07-01", "2023-12-31",
            "Number of threads used for mbus network",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SHARED_STRING_REPO_NO_RECLAIM = defineFeatureFlag(
            "shared-string-repo-no-reclaim", false,
            List.of("baldersheim"), "2022-06-14", "2023-12-31",
            "Controls whether we do track usage and reclaim unused enum values in shared string repo",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT = defineFeatureFlag(
            "container-dump-heap-on-shutdown-timeout", false,
            List.of("baldersheim"), "2021-09-25", "2023-12-31",
            "Will trigger a heap dump during if container shutdown times out",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);
    public static final UnboundBooleanFlag LOAD_CODE_AS_HUGEPAGES = defineFeatureFlag(
            "load-code-as-hugepages", false,
            List.of("baldersheim"), "2022-05-13", "2023-12-31",
            "Will try to map the code segment with huge (2M) pages",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag CONTAINER_SHUTDOWN_TIMEOUT = defineDoubleFlag(
            "container-shutdown-timeout", 50.0,
            List.of("baldersheim"), "2021-09-25", "2023-12-31",
            "Timeout for shutdown of a jdisc container",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    // TODO: Move to a permanent flag
    public static final UnboundListFlag<String> ALLOWED_ATHENZ_PROXY_IDENTITIES = defineListFlag(
            "allowed-athenz-proxy-identities", List.of(), String.class,
            List.of("bjorncs", "tokle"), "2021-02-10", "2023-08-01",
            "Allowed Athenz proxy identities",
            "takes effect at redeployment");

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            List.of("vekterli"), "2021-02-19", "2023-09-01",
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag MIN_NODE_RATIO_PER_GROUP = defineDoubleFlag(
            "min-node-ratio-per-group", 0.0,
            List.of("geirst", "vekterli"), "2021-07-16", "2023-09-01",
            "Minimum ratio of nodes that have to be available (i.e. not Down) in any hierarchic content cluster group for the group to be Up",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag SYSTEM_MEMORY_HIGH = defineStringFlag(
            "system-memory-high", "",
            List.of("baldersheim"), "2023-02-14", "2023-06-13",
            "The value to write to /sys/fs/cgroup/system.slice/memory.high, if non-empty.",
            "Takes effect on next tick.",
            ZONE_ID, NODE_TYPE);

    public static final UnboundStringFlag SYSTEM_MEMORY_MAX = defineStringFlag(
            "system-memory-max", "",
            List.of("baldersheim"), "2023-02-14", "2023-06-13",
            "The value to write to /sys/fs/cgroup/system.slice/memory.max, if non-empty.",
            "Takes effect on next tick.",
            ZONE_ID, NODE_TYPE);

    public static final UnboundBooleanFlag ENABLED_HORIZON_DASHBOARD = defineFeatureFlag(
            "enabled-horizon-dashboard", false,
            List.of("olaa"), "2021-09-13", "2023-06-01",
            "Enable Horizon dashboard",
            "Takes effect immediately",
            TENANT_ID, CONSOLE_USER_EMAIL
    );

    public static final UnboundBooleanFlag IGNORE_THREAD_STACK_SIZES = defineFeatureFlag(
            "ignore-thread-stack-sizes", false,
            List.of("arnej"), "2021-11-12", "2023-12-31",
            "Whether C++ thread creation should ignore any requested stack size",
            "Triggers restart, takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_V8_GEO_POSITIONS = defineFeatureFlag(
            "use-v8-geo-positions", true,
            List.of("arnej"), "2021-11-15", "2023-12-31",
            "Use Vespa 8 types and formats for geographical positions",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_COMPACT_BUFFERS = defineIntFlag(
                "max-compact-buffers", 1,
                List.of("baldersheim", "geirst", "toregge"), "2021-12-15", "2023-12-31",
                "Upper limit of buffers to compact in a data store at the same time for each reason (memory usage, address space usage)",
                "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_QRSERVER_SERVICE_NAME = defineFeatureFlag(
            "use-qrserver-service-name", false,
            List.of("arnej"), "2022-01-18", "2023-12-31",
            "Use backwards-compatible 'qrserver' service name for containers with only 'search' API",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag AVOID_RENAMING_SUMMARY_FEATURES = defineFeatureFlag(
            "avoid-renaming-summary-features", true,
            List.of("arnej"), "2022-01-15", "2023-12-31",
            "Tell backend about the original name of summary-features that were wrapped in a rankingExpression feature",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_PROXY_PROTOCOL_MIXED_MODE = defineFeatureFlag(
            "enable-proxy-protocol-mixed-mode", true,
            List.of("tokle"), "2022-05-09", "2023-08-01",
            "Enable or disable proxy protocol mixed mode",
            "Takes effect on redeployment",
            APPLICATION_ID);

    public static final UnboundStringFlag LOG_FILE_COMPRESSION_ALGORITHM = defineStringFlag(
            "log-file-compression-algorithm", "",
            List.of("arnej"), "2022-06-14", "2024-12-31",
            "Which algorithm to use for compressing log files. Valid values: empty string (default), gzip, zstd",
            "Takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SEPARATE_METRIC_CHECK_CONFIG = defineFeatureFlag(
            "separate-metric-check-config", false,
            List.of("olaa"), "2022-07-04", "2023-06-01",
            "Determines whether one metrics config check should be written per Vespa node",
            "Takes effect on next tick",
            HOSTNAME);

    public static final UnboundStringFlag TLS_CAPABILITIES_ENFORCEMENT_MODE = defineStringFlag(
            "tls-capabilities-enforcement-mode", "disable",
            List.of("bjorncs", "vekterli"), "2022-07-21", "2024-01-01",
            "Configure Vespa TLS capability enforcement mode",
            "Takes effect on restart of Docker container",
            APPLICATION_ID,HOSTNAME,NODE_TYPE,TENANT_ID,VESPA_VERSION
    );

    public static final UnboundBooleanFlag RESTRICT_DATA_PLANE_BINDINGS = defineFeatureFlag(
            "restrict-data-plane-bindings", false,
            List.of("mortent"), "2022-09-08", "2023-08-01",
            "Use restricted data plane bindings",
            "Takes effect at redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_OTELCOL = defineFeatureFlag(
            "enable-otel-collector", false,
            List.of("olaa"), "2022-09-23", "2023-06-01",
            "Whether an OpenTelemetry collector should be enabled",
            "Takes effect at next tick",
            APPLICATION_ID);

    public static final UnboundBooleanFlag CONSOLE_CSRF = defineFeatureFlag(
            "console-csrf", false,
            List.of("bjorncs", "tokle"), "2022-09-26", "2023-06-01",
            "Enable CSRF token in console",
            "Takes effect immediately",
            CONSOLE_USER_EMAIL);

    public static final UnboundStringFlag CORE_ENCRYPTION_PUBLIC_KEY_ID = defineStringFlag(
            "core-encryption-public-key-id", "",
            List.of("vekterli"), "2022-11-03", "2023-10-01",
            "Specifies which public key to use for core dump encryption.",
            "Takes effect on the next tick.",
            ZONE_ID, NODE_TYPE, HOSTNAME);

    public static final UnboundBooleanFlag ENABLE_GLOBAL_PHASE = defineFeatureFlag(
            "enable-global-phase", true,
            List.of("arnej", "bjorncs"), "2023-02-28", "2024-01-10",
            "Enable global phase ranking",
            "Takes effect at redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag VESPA_ATHENZ_PROVIDER = defineFeatureFlag(
            "vespa-athenz-provider", false,
            List.of("mortent"), "2023-02-22", "2023-08-01",
            "Enable athenz provider in public systems",
            "Takes effect on next config server container start",
            ZONE_ID);

    public static final UnboundBooleanFlag NODE_ADMIN_TENANT_SERVICE_REGISTRY = defineFeatureFlag(
            "node-admin-tenant-service-registry", false,
            List.of("olaa"), "2023-04-12", "2023-06-12",
            "Whether AthenzCredentialsMaintainer in node-admin should create tenant service identity certificate",
            "Takes effect on next tick",
            ZONE_ID, HOSTNAME, VESPA_VERSION, APPLICATION_ID
    );

    public static final UnboundBooleanFlag ENABLE_CROWDSTRIKE = defineFeatureFlag(
            "enable-crowdstrike", true, List.of("andreer"), "2023-04-13", "2023-06-13",
            "Whether to enable CrowdStrike.", "Takes effect on next host admin tick",
            HOSTNAME);

    public static final UnboundBooleanFlag ALLOW_MORE_THAN_ONE_CONTENT_GROUP_DOWN = defineFeatureFlag(
            "allow-more-than-one-content-group-down", false, List.of("hmusum"), "2023-04-14", "2023-07-01",
            "Whether to enable possible configuration of letting more than one content group down",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag NEW_IDDOC_LAYOUT = defineFeatureFlag(
            "new_iddoc_layout", false, List.of("tokle", "bjorncs", "olaa"), "2023-04-24", "2023-05-31",
            "Whether to use new identity document layout",
            "Takes effect on node reboot",
            HOSTNAME, APPLICATION_ID, VESPA_VERSION);

    public static final UnboundBooleanFlag RANDOMIZED_ENDPOINT_NAMES = defineFeatureFlag(
            "randomized-endpoint-names", false, List.of("andreer"), "2023-04-26", "2023-06-30",
            "Whether to use randomized endpoint names",
            "Takes effect on application deployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag USE_VESPA_ALMA_LINUX_X86_64_AMI = defineFeatureFlag(
            "use-vespa-alma-linux-x86_64-ami", true, List.of("hmusum"), "2023-05-04", "2023-07-01",
            "Whether to use VESPA-ALMALINUX-8-* AMI for x86_64 architecture",
            "Takes effect when provisioning new AWS hosts",
            APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_THE_ONE_THAT_SHOULD_NOT_BE_NAMED = defineFeatureFlag(
            "enable-the-one-that-should-not-be-named", false, List.of("hmusum"), "2023-05-08", "2023-07-01",
            "Whether to enable the one program that should not be named",
            "Takes effect at next host-admin tick",
            ZONE_ID);

    public static final UnboundListFlag<String> WEIGHTED_ENDPOINT_RECORD_TTL = defineListFlag(
            "weighted-endpoint-record-ttl", List.of(), String.class, List.of("jonmv"), "2023-05-16", "2023-06-16",
            "A list of endpoints and custom TTLs, on the form \"endpoint-fqdn:TTL-seconds\". " +
            "Where specified, CNAME records are used instead of the default ALIAS records, which have a default 60s TTL.",
            "Takes effect at redeployment from controller");

    public static final UnboundBooleanFlag ENABLE_CONDITIONAL_PUT_REMOVE_WRITE_REPAIR = defineFeatureFlag(
            "enable-conditional-put-remove-write-repair", false,
            List.of("vekterli", "havardpe"), "2023-05-10", "2023-07-01",
            "If set, a conditional Put or Remove operation for a document in an inconsistent bucket " +
            "will initiate a write-repair that evaluates the condition across all mutually inconsistent " +
            "replicas, with the newest document version (if any) being authoritative",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_DATAPLANE_PROXY = defineFeatureFlag(
            "enable-dataplane-proxy", false,
            List.of("mortent", "olaa"), "2023-05-15", "2023-08-01",
            "Whether to enable dataplane proxy",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID
    );

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundBooleanFlag defineFeatureFlag(String flagId, boolean defaultValue, List<String> owners,
                                                       String createdAt, String expiresAt, String description,
                                                       String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundBooleanFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, FetchVector.Dimension... dimensions) {
        return defineStringFlag(flagId, defaultValue, owners,
                                createdAt, expiresAt, description,
                                modificationEffect, value -> true,
                                dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, Predicate<String> validator,
                                                     FetchVector.Dimension... dimensions) {
        return define((i, d, v) -> new UnboundStringFlag(i, d, v, validator),
                      flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundIntFlag defineIntFlag(String flagId, int defaultValue, List<String> owners,
                                               String createdAt, String expiresAt, String description,
                                               String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundIntFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundLongFlag defineLongFlag(String flagId, long defaultValue, List<String> owners,
                                                 String createdAt, String expiresAt, String description,
                                                 String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundLongFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundDoubleFlag defineDoubleFlag(String flagId, double defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(UnboundDoubleFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundJacksonFlag<T> defineJacksonFlag(String flagId, T defaultValue, Class<T> jacksonClass, List<String> owners,
                                                              String createdAt, String expiresAt, String description,
                                                              String modificationEffect, FetchVector.Dimension... dimensions) {
        return define((id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass),
                flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundListFlag<T> defineListFlag(String flagId, List<T> defaultValue, Class<T> elementClass,
                                                        List<String> owners, String createdAt, String expiresAt,
                                                        String description, String modificationEffect, FetchVector.Dimension... dimensions) {
        return define((fid, dval, fvec) -> new UnboundListFlag<>(fid, dval, elementClass, fvec),
                flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    @FunctionalInterface
    private interface TypedUnboundFlagFactory<T, U extends UnboundFlag<?, ?, ?>> {
        U create(FlagId id, T defaultValue, FetchVector defaultFetchVector);
    }

    /**
     * Defines a Flag.
     *
     * @param factory            Factory for creating unbound flag of type U
     * @param flagId             The globally unique FlagId.
     * @param defaultValue       The default value if none is present after resolution.
     * @param description        Description of how the flag is used.
     * @param modificationEffect What is required for the flag to take effect? A restart of process? immediately? etc.
     * @param dimensions         What dimensions will be set in the {@link FetchVector} when fetching
     *                           the flag value in
     *                           {@link FlagSource#fetch(FlagId, FetchVector) FlagSource::fetch}.
     *                           For instance, if APPLICATION is one of the dimensions here, you should make sure
     *                           APPLICATION is set to the ApplicationId in the fetch vector when fetching the RawFlag
     *                           from the FlagSource.
     * @param <T>                The boxed type of the flag value, e.g. Boolean for flags guarding features.
     * @param <U>                The type of the unbound flag, e.g. UnboundBooleanFlag.
     * @return An unbound flag with {@link FetchVector.Dimension#HOSTNAME HOSTNAME} and
     *         {@link FetchVector.Dimension#VESPA_VERSION VESPA_VERSION} already set. The ZONE environment
     *         is typically implicit.
     */
    private static <T, U extends UnboundFlag<?, ?, ?>> U define(TypedUnboundFlagFactory<T, U> factory,
                                                                String flagId,
                                                                T defaultValue,
                                                                List<String> owners,
                                                                String createdAt,
                                                                String expiresAt,
                                                                String description,
                                                                String modificationEffect,
                                                                FetchVector.Dimension[] dimensions) {
        FlagId id = new FlagId(flagId);
        FetchVector vector = new FetchVector()
                .with(HOSTNAME, Defaults.getDefaults().vespaHostname())
                // Warning: In unit tests and outside official Vespa releases, the currentVersion is e.g. 7.0.0
                // (determined by the current major version). Consider not setting VESPA_VERSION if minor = micro = 0.
                .with(VESPA_VERSION, Vtag.currentVersion.toFullString());
        U unboundFlag = factory.create(id, defaultValue, vector);
        FlagDefinition definition = new FlagDefinition(
                unboundFlag, owners, parseDate(createdAt), parseDate(expiresAt), description, modificationEffect, dimensions);
        flags.put(id, definition);
        return unboundFlag;
    }

    private static Instant parseDate(String rawDate) {
        return DateTimeFormatter.ISO_DATE.parse(rawDate, LocalDate::from).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static List<FlagDefinition> getAllFlags() {
        return List.copyOf(flags.values());
    }

    public static Optional<FlagDefinition> getFlag(FlagId flagId) {
        return Optional.ofNullable(flags.get(flagId));
    }

    /**
     * Allows the statically defined flags to be controlled in a test.
     *
     * <p>Returns a Replacer instance to be used with e.g. a try-with-resources block. Within the block,
     * the flags starts out as cleared. Flags can be defined, etc. When leaving the block, the flags from
     * before the block is reinserted.
     *
     * <p>NOT thread-safe. Tests using this cannot run in parallel.
     */
    public static Replacer clearFlagsForTesting(FlagId... flagsToKeep) {
        return new Replacer(flagsToKeep);
    }

    public static class Replacer implements AutoCloseable {
        private static volatile boolean flagsCleared = false;

        private final TreeMap<FlagId, FlagDefinition> savedFlags;

        private Replacer(FlagId... flagsToKeep) {
            verifyAndSetFlagsCleared(true);
            this.savedFlags = Flags.flags;
            Flags.flags = new TreeMap<>();
            List.of(flagsToKeep).forEach(id -> Flags.flags.put(id, savedFlags.get(id)));
        }

        @Override
        public void close() {
            verifyAndSetFlagsCleared(false);
            Flags.flags = savedFlags;
        }

        /**
         * Used to implement a simple verification that Replacer is not used by multiple threads.
         * For instance two different tests running in parallel cannot both use Replacer.
         */
        private static void verifyAndSetFlagsCleared(boolean newValue) {
            if (flagsCleared == newValue) {
                throw new IllegalStateException("clearFlagsForTesting called while already cleared - running tests in parallell!?");
            }
            flagsCleared = newValue;
        }
    }
}
