// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.yahoo.component.Vtag;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.custom.Sidecars;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import static com.yahoo.vespa.flags.Dimension.APPLICATION;
import static com.yahoo.vespa.flags.Dimension.ARCHITECTURE;
import static com.yahoo.vespa.flags.Dimension.CLOUD_ACCOUNT;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_ID;
import static com.yahoo.vespa.flags.Dimension.CLUSTER_TYPE;
import static com.yahoo.vespa.flags.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;
import static com.yahoo.vespa.flags.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.Dimension.TENANT_ID;
import static com.yahoo.vespa.flags.Dimension.VESPA_VERSION;

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
 *     {@link Dimension#INSTANCE_ID}), and 2. specify the application ID when retrieving the value, e.g.
 *     {@link BooleanFlag#with(Dimension, String)}. See {@link FetchVector} for more info.</li>
 * </ol>
 *
 * <p>Once the code is in place, you can override the flag value. This depends on the flag source, but typically
 * there is a REST API for updating the flags in the config server, which is the root of all flag sources in the zone.</p>
 *
 * @author hakonhall
 */
public class Flags {

    private static volatile TreeMap<FlagId, FlagDefinition> flags = new TreeMap<>();

    public static final UnboundBooleanFlag USE_NON_PUBLIC_ENDPOINT_FOR_TEST = defineFeatureFlag(
            "use-non-public-endpoint-for-test", false,
            List.of("hakonhall"), "2025-03-19", "2026-04-10",
            "Whether to use non-public endpoint in test and staging environments (except Azure since it's not supported yet)",
            "Takes effect on next deployment of the application",
            INSTANCE_ID, VESPA_VERSION);

    public static final UnboundLongFlag DELETE_IDLE_TENANT_SECONDS = defineLongFlag(
            "delete-idle-tenant-seconds", -1,
            List.of("hakonhall"), "2026-02-03", "2026-04-03",
            "If >=0, then (A) the last deploy time is not updated on config server bootstrap, " +
            "and (B) an idle tenant will be deleted after this many seconds (default 604800 = 1 week).",
            "(A) takes effect on cfg bootstrap, (B) on next tick of TenantsMaintainer",
            TENANT_ID);

    public static final UnboundBooleanFlag SOFT_DELETE_TENANT = defineFeatureFlag(
            "soft-delete-tenant", false,
            List.of("hakonhall"), "2026-01-20", "2026-04-20",
            "When deleting /config/v2/tenants/TENANT recursively - whether to give up (true) or retry (false) on NotEmptyException",
            "Takes effect immediately",
            TENANT_ID);

    public static final UnboundBooleanFlag LOCKED_GCP_PROVISION = defineFeatureFlag(
            "locked-gcp-provision", true,
            List.of("hakonhall"), "2025-08-05", "2026-04-15",
            "Whether to provision GCP hosts under the application- and unallocated- locks, even though it takes ~1m.",
            "Takes effect on next host being provisioned");

    public static final UnboundStringFlag RESPONSE_SEQUENCER_TYPE = defineStringFlag(
            "response-sequencer-type", "ADAPTIVE",
            List.of("hmusum"), "2020-12-02", "2026-06-01",
            "Selects type of sequenced executor used for mbus responses, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag RESPONSE_NUM_THREADS = defineIntFlag(
            "response-num-threads", 2,
            List.of("hmusum"), "2020-12-02", "2026-06-01",
            "Number of threads used for mbus responses, default is 2, negative number = numcores/4",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE = defineFeatureFlag(
            "async-message-handling-on-schedule", true,
            List.of("hmusum"), "2020-12-02", "2026-06-01",
            "Optionally deliver async messages in own thread",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MBUS_JAVA_NUM_TARGETS = defineIntFlag(
            "mbus-java-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_CPP_NUM_TARGETS = defineIntFlag(
            "mbus-cpp-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag RPC_NUM_TARGETS = defineIntFlag(
            "rpc-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of rpc targets per content node",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_JAVA_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-java-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_CPP_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-cpp-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag RPC_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "rpc-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2026-06-01",
            "Number of write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MBUS_NUM_NETWORK_THREADS = defineIntFlag(
            "mbus-num-network-threads", 1,
            List.of("hmusum"), "2022-07-01", "2026-06-01",
            "Number of threads used for mbus network",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            List.of("vekterli"), "2021-02-19", "2026-03-01",
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            INSTANCE_ID);

    // Do not use. Removing
    public static final UnboundBooleanFlag ENABLE_OTELCOL = defineFeatureFlag(
            "enable-otel-collector", false,
            List.of("olaa"), "2022-09-23", "2026-03-01",
            "Whether an OpenTelemetry collector should be enabled",
            "Takes effect at next tick",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundStringFlag CORE_ENCRYPTION_PUBLIC_KEY_ID = defineStringFlag(
            "core-encryption-public-key-id", "",
            List.of("vekterli"), "2022-11-03", "2026-05-01",
            "Specifies which public key to use for core dump encryption.",
            "Takes effect on the next tick.",
            NODE_TYPE, HOSTNAME);

    public static final UnboundListFlag<String> ZONAL_WEIGHTED_ENDPOINT_RECORDS = defineListFlag(
            "zonal-weighted-endpoint-records", List.of(), String.class,
            List.of("hmusum"), "2023-12-15", "2026-06-01",
            "A list of weighted (application) endpoint fqdns for which we should use zonal endpoints as targets, not LBs.",
            "Takes effect at redeployment from controller");

    public static final UnboundListFlag<String> WEIGHTED_ENDPOINT_RECORD_TTL = defineListFlag(
            "weighted-endpoint-record-ttl", List.of(), String.class,
            List.of("hmusum"), "2023-05-16", "2026-06-01",
            "A list of endpoints and custom TTLs, on the form \"endpoint-fqdn:TTL-seconds\". " +
            "Where specified, CNAME records are used instead of the default ALIAS records, which have a default 60s TTL.",
            "Takes effect at redeployment from controller");

    public static final UnboundBooleanFlag WRITE_CONFIG_SERVER_SESSION_DATA_AS_ONE_BLOB = defineFeatureFlag(
            "write-config-server-session-data-as-blob", false,
            List.of("hmusum"), "2023-07-19", "2026-06-01",
            "Whether to write config server session data in one blob or as individual paths",
            "Takes effect immediately");

    public static final UnboundBooleanFlag READ_CONFIG_SERVER_SESSION_DATA_AS_ONE_BLOB = defineFeatureFlag(
            "read-config-server-session-data-as-blob", false,
            List.of("hmusum"), "2023-07-19", "2026-06-01",
            "Whether to read config server session data from session data blob or from individual paths",
            "Takes effect immediately");

    public static final UnboundBooleanFlag MORE_WIREGUARD = defineFeatureFlag(
            "more-wireguard", false,
            List.of("andreer"), "2023-08-21", "2026-03-01",
            "Use wireguard in INternal enCLAVES",
            "Takes effect on next host-admin run",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundBooleanFlag IPV6_AWS_TARGET_GROUPS = defineFeatureFlag(
            "ipv6-aws-target-groups", false,
            List.of("andreer"), "2023-08-28", "2026-03-01",
            "Always use IPv6 target groups for load balancers in aws",
            "Takes effect on next load-balancer provisioning",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundBooleanFlag PROVISION_IPV6_ONLY_AWS = defineFeatureFlag(
            "provision-ipv6-only", false,
            List.of("andreer"), "2023-08-28", "2026-03-01",
            "Provision without private IPv4 addresses in INternal enCLAVES in AWS",
            "Takes effect on next host provisioning / run of host-admin",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundIntFlag CONTENT_LAYER_METADATA_FEATURE_LEVEL = defineIntFlag(
            "content-layer-metadata-feature-level", 1,
            List.of("vekterli"), "2022-09-12", "2026-04-01",
            "Value semantics: 0) legacy behavior, 1) operation cancellation, 2) operation " +
            "cancellation and ephemeral content node sequence numbers for bucket replicas",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag SEARCH_HANDLER_THREADPOOL = defineIntFlag(
            "search-handler-threadpool", 10,
            List.of("bjorncs"), "2023-10-01", "2026-03-01",
            "Adjust search handler threadpool size",
            "Takes effect at redeployment",
            APPLICATION);

    public static final UnboundDoubleFlag DOCPROC_HANDLER_THREADPOOL = defineDoubleFlag(
            "docproc-handler-threadpool", 1.0,
            List.of("johsol"), "2025-10-17", "2026-03-01",
            "Adjust document processor handler threadpool size (scale the number of threads with cpu cores, 1 means same number of threads as cpu cores))",
            "Takes effect at redeployment",
            APPLICATION);

    public static final UnboundStringFlag ENDPOINT_CONFIG = defineStringFlag(
            "endpoint-config", "legacy",
            List.of("andreer", "olaa"), "2023-10-06", "2026-05-01",
            "Set the endpoint config to use for an application. Must be 'legacy', 'combined' or 'generated'. See EndpointConfig for further details",
            "Takes effect on next deployment through controller",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static UnboundBooleanFlag LOGSERVER_OTELCOL_AGENT = defineFeatureFlag(
            "logserver-otelcol-agent", false,
            List.of("olaa"), "2024-04-03", "2026-05-01",
            "Whether logserver container should run otel agent",
            "Takes effect at redeployment",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundBooleanFlag USE_LEGACY_WAND_QUERY_PARSING = defineFeatureFlag(
            "use-legacy-wand-query-parsing", true,
            List.of("arnej"), "2023-07-26", "2027-01-01",
            "If true, force legacy mode for weakAnd query parsing",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_SIMPLE_ANNOTATIONS = defineFeatureFlag(
            "use-simple-annotations", true,
            List.of("arnej"), "2025-11-13", "2026-12-31",
            "Enable lightweight annotation representation for StringFieldValue",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag SEND_PROTOBUF_QUERYTREE = defineFeatureFlag(
            "send-protobuf-querytree", true,
            List.of("arnej"), "2025-10-06", "2026-03-31",
            "If true, send query tree as protobuf in addition to legacy format",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag MONITORING_JWT = defineFeatureFlag(
            "monitoring-jwt", true,
            List.of("olaa"), "2024-07-05", "2026-05-01",
            "Whether a monitoring JWT should be issued by the controller",
            "Takes effect immediately",
            TENANT_ID, CONSOLE_USER_EMAIL);

    public static final UnboundBooleanFlag SNAPSHOTS_ENABLED = defineFeatureFlag(
            "snapshots-enabled", false,
            List.of("olaa"), "2024-10-22", "2026-05-01",
            "Whether node snapshots should be created when host storage is discarded",
            "Takes effect immediately");

    public static final UnboundLongFlag ZOOKEEPER_PRE_ALLOC_SIZE_KIB = defineLongFlag(
            "zookeeper-pre-alloc-size", 65536,
            List.of("hmusum"), "2024-11-11", "2026-03-01",
            "Setting for zookeeper.preAllocSize flag in KiB, can be reduced from default value "
            + "e.g. when running tests to avoid writing a large, sparse, mostly unused file",
            "Takes effect on restart of Docker container");

    public static final UnboundBooleanFlag ENFORCE_EMAIL_DOMAIN_SSO = defineFeatureFlag(
            "enforce-email-domain-sso", false,
            List.of("eirik"), "2024-11-07", "2026-05-01",
            "Enforce SSO login for an email domain",
            "Takes effect immediately",
            CONSOLE_USER_EMAIL);

    public static final UnboundListFlag<String> RESTRICT_USERS_TO_DOMAIN = defineListFlag(
            "restrict-users-to-domain", List.of(), String.class,
            List.of("eirik"), "2024-11-07", "2026-05-01",
            "Only allow adding specific email domains as user to tenant",
            "Takes effect immediately",
            TENANT_ID);

    public static final UnboundIntFlag DOCUMENT_V1_QUEUE_SIZE = defineIntFlag(
            "document-v1-queue-size", -1,
            List.of("bjorncs"), "2025-01-14", "2026-03-01",
            "Size of the document v1 queue. Use -1 for default as determined by 'document-operation-executor.def'",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_CONTENT_NODE_MAINTENANCE_OP_CONCURRENCY = defineIntFlag(
            "max-content-node-maintenance-op-concurrency", -1,
            List.of("vekterli"), "2025-03-07", "2026-04-01",
            "Sets the maximum concurrency for maintenance-related operations on content nodes. " +
            "Only intended as a manual emergency brake feature if a system is suddenly incapable of handling " +
            "regular maintenance pressure.",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_DOCUMENT_OPERATION_REQUEST_SIZE_MIB = defineIntFlag(
            "max-document-operation-request-size-mib", 2048,
            List.of("glebashnik"), "2025-09-04", "2026-06-01",
            "Sets the maximum size in MiB of a document operation request (POST or PUT). " +
            "This is the size of a serialized request, which can be several times larger than " +
            "the content of the document, especially for tensors in JSON." +
            "POST and PUT requests larger than this will return HTTP 413 Content Too Large response " +
            "and will not be added to the message bus queue.",
            "Takes effect immediately",
            INSTANCE_ID
    );

    public static final UnboundJacksonFlag<Sidecars> SIDECARS_FOR_TEST = defineJacksonFlag(
            "sidecars-for-test", Sidecars.DEFAULT, Sidecars.class,
            List.of("glebashnik"), "2025-04-25", "2026-03-01",
            "Specifies configuration for sidecars to testing provisioning",
            "Takes effect at redeployment",
            __ -> true,
            APPLICATION
    );

    public static final UnboundBooleanFlag CREATE_TENANT_ROLES = defineFeatureFlag(
            "create-tenant-roles", true,
            List.of("andreer"), "2025-04-28", "2026-03-01",
            "Whether to create tenant specific roles",
            "Takes effect immediately",
            TENANT_ID
    );

    public static final UnboundBooleanFlag USE_TRITON = defineFeatureFlag(
            "use-triton", false,
            List.of("bjorncs", "glebashnik"), "2025-04-30", "2026-06-01",
            "Whether to use Triton as ONNX runtime",
            "Takes effect at redeployment"
    );

    public static final UnboundBooleanFlag DELETE_TENANT_ROLES = defineFeatureFlag(
            "delete-tenant-roles", false,
            List.of("andreer"), "2025-05-05", "2026-03-01",
            "Whether to delete tenant specific roles",
            "Role deletion happens when tenant is next processed by TenantRoleMaintainer",
            TENANT_ID
    );

    public static final UnboundBooleanFlag USE_NEW_PREPARE_FOR_RESTART_METHOD = defineFeatureFlag(
            "use-new-prepare-for-restart-method", true,
            List.of("hmusum"), "2025-06-17", "2026-03-01",
            "Whether to use new logic and new RPC method to do prepareForRestart for content nodes",
            "Takes effect at next tick",
            HOSTNAME
    );

    public static final UnboundIntFlag SEARCH_CORE_MAX_OUTSTANDING_MOVE_OPS = defineIntFlag(
            "search-core-max-outstanding-move-ops", 100,
            List.of("hmusum"), "2025-07-09", "2026-06-01",
            "The max outstanding move operations a maintenance job can have before being blocked.",
            "Takes effect at next deployment of the application",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_VESPA_NODE_CTL = defineFeatureFlag(
            "use-vespa-node-ctl", true,
            List.of("hmusum"), "2025-08-12", "2026-06-01",
            "Whether to use vespa-node-ctl to start, stop, restart, suspend and resume services " +
            "or do this directly from host-admin.",
            "Takes effect at next tick",
            HOSTNAME
    );

    public static final UnboundStringFlag VESPA_USE_MALLOC_IMPL = defineStringFlag(
            "vespa-use-malloc-impl", "",
            List.of("hmusum", "johsol"), "2025-09-10", "2026-03-01",
            "Which malloc implementation to use  " +
                    "Valid values: 'vespamalloc', 'mimalloc', '' (empty string, meaning default malloc implementation).",
            "Takes effect at next reboot of the node",
            TENANT_ID, APPLICATION, INSTANCE_ID, HOSTNAME, CLUSTER_TYPE
    );

    public static final UnboundBooleanFlag WAIT_FOR_APPLY_ON_RESTART = defineFeatureFlag(
            "wait-for-apply-on-restart", false,
            List.of("glebashnik"), "2026-02-01", "2026-08-01",
            "Determines whether triggering of a pending restart waits for `applyOnRestart` to be set to `true` " +
                    "in the observed config state. In addition, changes service convergence condition bounded to a " +
                    "current node instead of across multiple nodes, see `RestartOnDeployMaintainer`.",
            "Takes effect at next run of RestartOnDeployMaintainer.",
            INSTANCE_ID
    );

    public static final UnboundDoubleFlag HOST_MEMORY_SERVICES_MIXING_FACTOR = defineDoubleFlag(
            "host-memory-services-mixing-factor", 0.0,
            List.of("boeker"), "2026-01-16", "2026-04-16",
            "How much of the sum of the memory limits specified for the customer rpm services should be added to " +
            "the memory reserved for host's management processes. " +
            "0.0 means none at all, 1.0 means the sum of the memory limits.",
            "Affects future deployments, JVM settings for new config server Podman containers, auto scaling modelling.",
            TENANT_ID, APPLICATION, INSTANCE_ID, ARCHITECTURE, CLUSTER_ID, CLUSTER_TYPE
    );

    public static final UnboundBooleanFlag USE_EXPERIMENTAL_DELETE_SESSIONS_CODE = defineFeatureFlag(
            "use-experimental-delete-sessions-code", false,
            List.of("hmusum"), "2026-02-11", "2026-03-01",
            "Whether to use new code for deleting unused sessions on config server",
            "Takes effect at next run of config server maintainer SessionsMaintainer",
            HOSTNAME
    );

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundBooleanFlag defineFeatureFlag(String flagId, boolean defaultValue, List<String> owners,
                                                       String createdAt, String expiresAt, String description,
                                                       String modificationEffect, Dimension... dimensions) {
        return define(UnboundBooleanFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, Dimension... dimensions) {
        return defineStringFlag(flagId, defaultValue, owners,
                                createdAt, expiresAt, description,
                                modificationEffect, value -> true,
                                dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundStringFlag defineStringFlag(String flagId, String defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, Predicate<String> validator,
                                                     Dimension... dimensions) {
        return define((i, d, v) -> new UnboundStringFlag(i, d, v, validator),
                      flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundIntFlag defineIntFlag(String flagId, int defaultValue, List<String> owners,
                                               String createdAt, String expiresAt, String description,
                                               String modificationEffect, Dimension... dimensions) {
        return define(UnboundIntFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundLongFlag defineLongFlag(String flagId, long defaultValue, List<String> owners,
                                                 String createdAt, String expiresAt, String description,
                                                 String modificationEffect, Dimension... dimensions) {
        return define(UnboundLongFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static UnboundDoubleFlag defineDoubleFlag(String flagId, double defaultValue, List<String> owners,
                                                     String createdAt, String expiresAt, String description,
                                                     String modificationEffect, Dimension... dimensions) {
        return define(UnboundDoubleFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundJacksonFlag<T> defineJacksonFlag(String flagId, T defaultValue, Class<T> jacksonClass, List<String> owners,
                                                              String createdAt, String expiresAt, String description,
                                                              String modificationEffect, Predicate<T> validator, Dimension... dimensions) {
        return define((id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass, validator),
                      flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundListFlag<T> defineListFlag(String flagId, List<T> defaultValue, Class<T> elementClass,
                                                        List<String> owners, String createdAt, String expiresAt,
                                                        String description, String modificationEffect, Dimension... dimensions) {
        return define((fid, dval, fvec) -> new UnboundListFlag<>(fid, dval, elementClass, fvec),
                flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
    }

    /** WARNING: public for testing: All flags should be defined in {@link Flags}. */
    public static <T> UnboundListFlag<T> defineListFlag(String flagId, List<T> defaultValue, Class<T> elementClass,
                                                        List<String> owners, String createdAt, String expiresAt,
                                                        String description, String modificationEffect,
                                                        Predicate<List<T>> validator, Dimension... dimensions) {
        return define((fid, dval, fvec) -> new UnboundListFlag<>(fid, dval, elementClass, fvec, validator),
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
     *                           SYSTEM, CLOUD, ENVIRONMENT, and ZONE_ID are special:  These dimensions are resolved just
     *                           before the flag data is published to a zone.  This means there is never any need to set
     *                           these dimensions when resolving a flag, and setting these dimensions just before resolving
     *                           the flag will have no effect.
     *                           There is one exception.  If any of these dimensions are declared when defining a flag,
     *                           then those dimensions are NOT resolved when published to the controllers.  This allows
     *                           the controller to resolve the flag to different values based on which cloud or zone
     *                           it is operating on.  Flags should NOT declare these dimensions unless they intend to
     *                           use them in the controller in this way.
     * @param <T>                The boxed type of the flag value, e.g. Boolean for flags guarding features.
     * @param <U>                The type of the unbound flag, e.g. UnboundBooleanFlag.
     * @return An unbound flag with {@link Dimension#HOSTNAME HOSTNAME} and
     *         {@link Dimension#VESPA_VERSION VESPA_VERSION} already set. The ZONE environment
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
                                                                Dimension[] dimensions) {
        FlagId id = new FlagId(flagId);
        FetchVector vector = new FetchVector()
                .with(HOSTNAME, Defaults.getDefaults().vespaHostname())
                // Warning: In unit tests and outside official Vespa releases, the currentVersion is e.g. 7.0.0
                // (determined by the current major version). Consider not setting VESPA_VERSION if minor = micro = 0.
                .with(VESPA_VERSION, Vtag.currentVersion.toFullString());
        U unboundFlag = factory.create(id, defaultValue, vector);
        FlagDefinition definition = new FlagDefinition(
                unboundFlag, owners, parseDate(createdAt), parseDate(expiresAt), description, modificationEffect, dimensions);
        if (flags.put(id, definition) != null)
            throw new IllegalStateException("There are multiple definitions of the " + id + " flag");
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
