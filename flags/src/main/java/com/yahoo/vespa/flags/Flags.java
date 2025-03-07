// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static com.yahoo.vespa.flags.Dimension.APPLICATION;
import static com.yahoo.vespa.flags.Dimension.CLOUD_ACCOUNT;
import static com.yahoo.vespa.flags.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.Dimension.HOSTNAME;
import static com.yahoo.vespa.flags.Dimension.INSTANCE_ID;
import static com.yahoo.vespa.flags.Dimension.NODE_TYPE;
import static com.yahoo.vespa.flags.Dimension.SYSTEM;
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

    public static final UnboundBooleanFlag UPGRADE_WIREGUARD = defineFeatureFlag(
            "upgrade-wireguard", false,
            List.of("hakonhall"), "2025-02-04",
            "2025-04-04", // TODO: Remove flag once all machine images have been built after 2025-02-13
            "Whether to upgrade vespa-wireguard-go to latest",
            "Takes effect on start of host-admin.",
            HOSTNAME);

    public static final UnboundStringFlag SUMMARY_DECODE_POLICY = defineStringFlag(
            "summary-decode-policy", "eager",
            List.of("hmusum"), "2023-03-30", "2025-04-01",
            "Select summary decoding policy, valid values are eager and on-demand/ondemand.",
            "Takes effect at redeployment (requires restart)",
            INSTANCE_ID);

    public static final UnboundStringFlag SEARCH_MMAP_ADVISE = defineStringFlag(
            "search-mmap-advise", "NORMAL",
            List.of("vekterli"), "2025-02-14", "2025-06-01",
            "Sets the MMAP advise setting used for disk based posting lists on the content node. " +
            "Valid values are [NORMAL, RANDOM, SEQUENTIAL]",
            "Takes effect at redeployment (requires restart)",
            INSTANCE_ID);

    public static final UnboundStringFlag RESPONSE_SEQUENCER_TYPE = defineStringFlag(
            "response-sequencer-type", "ADAPTIVE",
            List.of("hmusum"), "2020-12-02", "2025-04-01",
            "Selects type of sequenced executor used for mbus responses, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag RESPONSE_NUM_THREADS = defineIntFlag(
            "response-num-threads", 2,
            List.of("hmusum"), "2020-12-02", "2025-04-01",
            "Number of threads used for mbus responses, default is 2, negative number = numcores/4",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE = defineFeatureFlag(
            "async-message-handling-on-schedule", true,
            List.of("hmusum"), "2020-12-02", "2025-04-01",
            "Optionally deliver async messages in own thread",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MBUS_JAVA_NUM_TARGETS = defineIntFlag(
            "mbus-java-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_CPP_NUM_TARGETS = defineIntFlag(
            "mbus-cpp-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number of rpc targets per service",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag RPC_NUM_TARGETS = defineIntFlag(
            "rpc-num-targets", 2,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number of rpc targets per content node",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_JAVA_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-java-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag MBUS_CPP_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "mbus-cpp-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);
    public static final UnboundIntFlag RPC_EVENTS_BEFORE_WAKEUP = defineIntFlag(
            "rpc-events-before-wakeup", 1,
            List.of("hmusum"), "2022-07-05", "2025-04-01",
            "Number write events before waking up transport thread",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MBUS_NUM_NETWORK_THREADS = defineIntFlag(
            "mbus-num-network-threads", 1,
            List.of("hmusum"), "2022-07-01", "2025-04-01",
            "Number of threads used for mbus network",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            List.of("vekterli"), "2021-02-19", "2025-06-01",
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundDoubleFlag MIN_NODE_RATIO_PER_GROUP = defineDoubleFlag(
            "min-node-ratio-per-group", 0.0,
            List.of("vekterli"), "2021-07-16", "2025-06-01",
            "Minimum ratio of nodes that have to be available (i.e. not Down) in any hierarchic content cluster group for the group to be Up",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_V8_GEO_POSITIONS = defineFeatureFlag(
            "use-v8-geo-positions", true,
            List.of("arnej"), "2021-11-15", "2025-04-01",
            "Use Vespa 8 types and formats for geographical positions",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundStringFlag LOG_FILE_COMPRESSION_ALGORITHM = defineStringFlag(
            "log-file-compression-algorithm", "",
            List.of("arnej"), "2022-06-14", "2025-12-01",
            "Which algorithm to use for compressing log files. Valid values: empty string (default), gzip, zstd",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundBooleanFlag ENABLE_OTELCOL = defineFeatureFlag(
            "enable-otel-collector", false,
            List.of("olaa"), "2022-09-23", "2025-06-01",
            "Whether an OpenTelemetry collector should be enabled",
            "Takes effect at next tick",
            INSTANCE_ID);

    public static final UnboundListFlag<String> OTELCOL_LOGS = defineListFlag(
            "otelcol-logs", List.of(), String.class,
            List.of("olaa"), "2024-01-15", "2025-06-01",
            "Determines log files handled by the OpenTelemetry collector",
            "Takes effect at next tick",
            INSTANCE_ID, HOSTNAME
    );

    public static final UnboundStringFlag CORE_ENCRYPTION_PUBLIC_KEY_ID = defineStringFlag(
            "core-encryption-public-key-id", "",
            List.of("vekterli"), "2022-11-03", "2025-06-01",
            "Specifies which public key to use for core dump encryption.",
            "Takes effect on the next tick.",
            NODE_TYPE, HOSTNAME);

    public static final UnboundListFlag<String> ZONAL_WEIGHTED_ENDPOINT_RECORDS = defineListFlag(
            "zonal-weighted-endpoint-records", List.of(), String.class,
            List.of("hmusum"), "2023-12-15", "2025-04-01",
            "A list of weighted (application) endpoint fqdns for which we should use zonal endpoints as targets, not LBs.",
            "Takes effect at redeployment from controller");

    public static final UnboundListFlag<String> WEIGHTED_ENDPOINT_RECORD_TTL = defineListFlag(
            "weighted-endpoint-record-ttl", List.of(), String.class,
            List.of("hmusum"), "2023-05-16", "2025-04-01",
            "A list of endpoints and custom TTLs, on the form \"endpoint-fqdn:TTL-seconds\". " +
            "Where specified, CNAME records are used instead of the default ALIAS records, which have a default 60s TTL.",
            "Takes effect at redeployment from controller");

    public static final UnboundBooleanFlag WRITE_CONFIG_SERVER_SESSION_DATA_AS_ONE_BLOB = defineFeatureFlag(
            "write-config-server-session-data-as-blob", false,
            List.of("hmusum"), "2023-07-19", "2025-04-01",
            "Whether to write config server session data in one blob or as individual paths",
            "Takes effect immediately");

    public static final UnboundBooleanFlag READ_CONFIG_SERVER_SESSION_DATA_AS_ONE_BLOB = defineFeatureFlag(
            "read-config-server-session-data-as-blob", false,
            List.of("hmusum"), "2023-07-19", "2025-04-01",
            "Whether to read config server session data from session data blob or from individual paths",
            "Takes effect immediately");

    public static final UnboundBooleanFlag MORE_WIREGUARD = defineFeatureFlag(
            "more-wireguard", false,
            List.of("andreer"), "2023-08-21", "2025-09-01",
            "Use wireguard in INternal enCLAVES",
            "Takes effect on next host-admin run",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundBooleanFlag IPV6_AWS_TARGET_GROUPS = defineFeatureFlag(
            "ipv6-aws-target-groups", false,
            List.of("andreer"), "2023-08-28", "2025-09-01",
            "Always use IPv6 target groups for load balancers in aws",
            "Takes effect on next load-balancer provisioning",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundBooleanFlag PROVISION_IPV6_ONLY_AWS = defineFeatureFlag(
            "provision-ipv6-only", false,
            List.of("andreer"), "2023-08-28", "2025-09-01",
            "Provision without private IPv4 addresses in INternal enCLAVES in AWS",
            "Takes effect on next host provisioning / run of host-admin",
            HOSTNAME, CLOUD_ACCOUNT);

    public static final UnboundIntFlag CONTENT_LAYER_METADATA_FEATURE_LEVEL = defineIntFlag(
            "content-layer-metadata-feature-level", 1,
            List.of("vekterli"), "2022-09-12", "2025-06-01",
            "Value semantics: 0) legacy behavior, 1) operation cancellation, 2) operation " +
            "cancellation and ephemeral content node sequence numbers for bucket replicas",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundIntFlag SEARCH_HANDLER_THREADPOOL = defineIntFlag(
            "search-handler-threadpool", 10,
            List.of("bjorncs"), "2023-10-01", "2025-12-01",
            "Adjust search handler threadpool size",
            "Takes effect at redeployment",
            APPLICATION);

    public static final UnboundStringFlag ENDPOINT_CONFIG = defineStringFlag(
            "endpoint-config", "legacy",
            List.of("mpolden", "tokle"), "2023-10-06", "2025-09-01",
            "Set the endpoint config to use for an application. Must be 'legacy', 'combined' or 'generated'. See EndpointConfig for further details",
            "Takes effect on next deployment through controller",
            TENANT_ID, APPLICATION, INSTANCE_ID);

    public static final UnboundIntFlag PERSISTENCE_THREAD_MAX_FEED_OP_BATCH_SIZE = defineIntFlag(
            "persistence-thread-max-feed-op-batch-size", 64,
            List.of("vekterli"), "2024-04-12", "2025-06-01",
            "Maximum number of enqueued feed operations (put/update/remove) bound "+
            "towards the same bucket that can be async dispatched as part of the " +
            "same write-locked batch by a persistence thread.",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static UnboundBooleanFlag LOGSERVER_OTELCOL_AGENT = defineFeatureFlag(
            "logserver-otelcol-agent", false,
            List.of("olaa"), "2024-04-03", "2025-06-01",
            "Whether logserver container should run otel agent",
            "Takes effect at redeployment", INSTANCE_ID);

    public static final UnboundBooleanFlag SYMMETRIC_PUT_AND_ACTIVATE_REPLICA_SELECTION = defineFeatureFlag(
            "symmetric-put-and-activate-replica-selection", true,
            List.of("vekterli"), "2024-05-23", "2025-06-01",
            "Iff true there will be an 1-1 symmetry between the replicas chosen as feed targets " +
            "for Put operations and the replica selection logic for bucket activation. If false, " +
            "legacy feed behavior is used.",
            "Takes effect immediately",
            INSTANCE_ID);

    public static final UnboundBooleanFlag USE_LEGACY_WAND_QUERY_PARSING = defineFeatureFlag(
            "use-legacy-wand-query-parsing", true,
            List.of("arnej"), "2023-07-26", "2025-12-31",
            "If true, force legacy mode for weakAnd query parsing",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundBooleanFlag MONITORING_JWT = defineFeatureFlag(
            "monitoring-jwt", false,
            List.of("olaa"), "2024-07-05", "2025-06-01",
            "Whether a monitoring JWT should be issued by the controller",
            "Takes effect immediately",
            TENANT_ID, CONSOLE_USER_EMAIL);

    public static final UnboundBooleanFlag SNAPSHOTS_ENABLED = defineFeatureFlag(
            "snapshots-enabled", false,
            List.of("mpolden"), "2024-10-22", "2025-06-01",
            "Whether node snapshots should be created when host storage is discarded",
            "Takes effect immediately");

    public static final UnboundLongFlag ZOOKEEPER_PRE_ALLOC_SIZE_KIB = defineLongFlag(
            "zookeeper-pre-alloc-size", 65536,
            List.of("hmusum"), "2024-11-11", "2025-04-01",
            "Setting for zookeeper.preAllocSize flag in KiB, can be reduced from default value "
            + "e.g. when running tests to avoid writing a large, sparse, mostly unused file",
            "Takes effect on restart of Docker container");

    public static final UnboundBooleanFlag ENFORCE_EMAIL_DOMAIN_SSO = defineFeatureFlag(
            "enforce-email-domain-sso", false,
            List.of("eirik"), "2024-11-07", "2025-05-07",
            "Enforce SSO login for an email domain",
            "Takes effect immediately",
            CONSOLE_USER_EMAIL);

    public static final UnboundListFlag<String> RESTRICT_USERS_TO_DOMAIN = defineListFlag(
            "restrict-users-to-domain", List.of(), String.class,
            List.of("eirik"), "2024-11-07", "2025-05-07",
            "Only allow adding specific email domains as user to tenant",
            "Takes effect immediately",
            TENANT_ID);

    public static final UnboundBooleanFlag USE_LEGACY_STORE = defineFeatureFlag(
            "use-legacy-trust-store", true,
            List.of("marlon"), "2024-12-05", "2025-04-01",
            "Use legacy trust store for CA, or new one",
            "Takes effect on restart of OCI containers");

    public static final UnboundBooleanFlag CONFIG_SERVER_TRIGGER_DOWNLOAD_WITH_SOURCE = defineFeatureFlag(
            "config-server-trigger-download-with-source", false,
            List.of("hmusum"), "2024-12-25", "2025-04-01",
            "Use new RPC method for triggering download of file reference",
            "Takes effect immediately");

    public static final UnboundIntFlag DOCUMENT_V1_QUEUE_SIZE = defineIntFlag(
            "document-v1-queue-size", -1,
            List.of("bjorncs"), "2025-01-14", "2025-12-01",
            "Size of the document v1 queue. Use -1 for default as determined by 'document-operation-executor.def'",
            "Takes effect at redeployment",
            INSTANCE_ID);

    public static final UnboundListFlag<String> FILE_DISTRIBUTION_COMPRESSION_TYPES_TO_SERVE = defineListFlag(
            "file-distribution-compression-types-to-serve", List.of("zstd", "lz4", "none", "gzip"), String.class,
            List.of("hmusum"), "2025-02-24", "2025-04-01",
            "List of compression type to use when distributing files, in preferred order, depends " +
                    "on what the client accepts (sent in request from client)",
            "Takes effect at restart of config server",
            INSTANCE_ID);

    public static final UnboundBooleanFlag INCREMENTAL_USAGE_CALCULATION = defineFeatureFlag(
            "incremental-usage-calculation", false,
            List.of("evgiz"), "2025-02-27", "2025-05-01",
            "Use new incremental usage calculation for node snapshots",
            "Takes effect at controller startup");

    public static final UnboundIntFlag MAX_CONTENT_NODE_MAINTENANCE_OP_CONCURRENCY = defineIntFlag(
            "max-content-node-maintenance-op-concurrency", -1,
            List.of("vekterli"), "2025-03-07", "2025-09-01",
            "Sets the maximum concurrency for maintenance-related operations on content nodes. " +
            "Only intended as a manual emergency brake feature if a system is suddenly incapable of handling " +
            "regular maintenance pressure.",
            "Takes effect immediately",
            INSTANCE_ID);

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
                                                              String modificationEffect, Dimension... dimensions) {
        return define((id2, defaultValue2, vector2) -> new UnboundJacksonFlag<>(id2, defaultValue2, vector2, jacksonClass),
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
