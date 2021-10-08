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

import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static com.yahoo.vespa.flags.FetchVector.Dimension.CONSOLE_USER_EMAIL;
import static com.yahoo.vespa.flags.FetchVector.Dimension.HOSTNAME;
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

    public static final UnboundBooleanFlag FORCE_DISK_ENCRYPTION = defineFeatureFlag(
            "force-disk-encryption", true,
            List.of("hakonhall"), "2021-10-01", "2021-11-01",
            "Enable new conditions for when to encrypt disk.",
            "Takes effect on next host admin tick.");

    public static final UnboundDoubleFlag DEFAULT_TERM_WISE_LIMIT = defineDoubleFlag(
            "default-term-wise-limit", 1.0,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Default limit for when to apply termwise query evaluation",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag FEED_SEQUENCER_TYPE = defineStringFlag(
            "feed-sequencer-type", "LATENCY",
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Selects type of sequenced executor used for feeding, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag RESPONSE_SEQUENCER_TYPE = defineStringFlag(
            "response-sequencer-type", "ADAPTIVE",
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Selects type of sequenced executor used for mbus responses, valid values are LATENCY, ADAPTIVE, THROUGHPUT",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag RESPONSE_NUM_THREADS = defineIntFlag(
            "response-num-threads", 2,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Number of threads used for mbus responses, default is 2, negative number = numcores/4",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_COMMUNICATIONMANAGER_THREAD = defineFeatureFlag(
            "skip-communicationmanager-thread", false,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Should we skip the communicationmanager thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REQUEST_THREAD = defineFeatureFlag(
            "skip-mbus-request-thread", false,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Should we skip the mbus request thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag SKIP_MBUS_REPLY_THREAD = defineFeatureFlag(
            "skip-mbus-reply-thread", false,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Should we skip the mbus reply thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag USE_THREE_PHASE_UPDATES = defineFeatureFlag(
            "use-three-phase-updates", false,
            List.of("vekterli"), "2020-12-02", "2021-11-01",
            "Whether to enable the use of three-phase updates when bucket replicas are out of sync.",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag HIDE_SHARED_ROUTING_ENDPOINT = defineFeatureFlag(
            "hide-shared-routing-endpoint", false,
            List.of("tokle", "bjormel"), "2020-12-02", "2021-11-01",
            "Whether the controller should hide shared routing layer endpoint",
            "Takes effect immediately",
            APPLICATION_ID
    );

    public static final UnboundBooleanFlag USE_ASYNC_MESSAGE_HANDLING_ON_SCHEDULE = defineFeatureFlag(
            "async-message-handling-on-schedule", false,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "Optionally deliver async messages in own thread",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag FEED_CONCURRENCY = defineDoubleFlag(
            "feed-concurrency", 0.5,
            List.of("baldersheim"), "2020-12-02", "2022-01-01",
            "How much concurrency should be allowed for feed",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag DISK_BLOAT_FACTOR = defineDoubleFlag(
            "disk-bloat-factor", 0.2,
            List.of("baldersheim"), "2021-10-08", "2022-01-01",
            "Amount of bloat allowed before compacting file",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag DOCSTORE_COMPRESSION_LEVEL = defineIntFlag(
            "docstore-compression-level", 9,
            List.of("baldersheim"), "2021-10-08", "2022-01-01",
            "Default compression level used for document store",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag NUM_DEPLOY_HELPER_THREADS = defineIntFlag(
            "num-model-builder-threads", -1,
            List.of("balder"), "2021-09-09", "2021-11-01",
            "Number of threads used for speeding up building of models.",
            "Takes effect on first (re)start of config server");

    public static final UnboundBooleanFlag ENABLE_FEED_BLOCK_IN_DISTRIBUTOR = defineFeatureFlag(
            "enable-feed-block-in-distributor", true,
            List.of("geirst"), "2021-01-27", "2021-11-01",
            "Enables blocking of feed in the distributor if resource usage is above limit on at least one content node",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag CONTAINER_DUMP_HEAP_ON_SHUTDOWN_TIMEOUT = defineFeatureFlag(
            "container-dump-heap-on-shutdown-timeout", false,
            List.of("baldersheim"), "2021-09-25", "2021-11-01",
            "Will trigger a heap dump during if container shutdown times out",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag CONTAINER_SHUTDOWN_TIMEOUT = defineDoubleFlag(
            "container-shutdown-timeout", 50.0,
            List.of("baldersheim"), "2021-09-25", "2021-11-01",
            "Timeout for shutdown of a jdisc container",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundListFlag<String> ALLOWED_ATHENZ_PROXY_IDENTITIES = defineListFlag(
            "allowed-athenz-proxy-identities", List.of(), String.class,
            List.of("bjorncs", "tokle"), "2021-02-10", "2021-12-01",
            "Allowed Athenz proxy identities",
            "takes effect at redeployment");

    public static final UnboundBooleanFlag GENERATE_NON_MTLS_ENDPOINT = defineFeatureFlag(
            "generate-non-mtls-endpoint", true,
            List.of("tokle"), "2021-02-18", "2021-12-01",
            "Whether to generate the non-mtls endpoint",
            "Takes effect on next internal redeployment",
            APPLICATION_ID);

    public static final UnboundIntFlag MAX_ACTIVATION_INHIBITED_OUT_OF_SYNC_GROUPS = defineIntFlag(
            "max-activation-inhibited-out-of-sync-groups", 0,
            List.of("vekterli"), "2021-02-19", "2021-11-01",
            "Allows replicas in up to N content groups to not be activated " +
            "for query visibility if they are out of sync with a majority of other replicas",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_CONCURRENT_MERGES_PER_NODE = defineIntFlag(
            "max-concurrent-merges-per-node", 128,
            List.of("balder", "vekterli"), "2021-06-06", "2021-11-01",
            "Specifies max concurrent merges per content node.",
            "Takes effect at redeploy",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag MAX_MERGE_QUEUE_SIZE = defineIntFlag(
            "max-merge-queue-size", 1024,
            List.of("balder", "vekterli"), "2021-06-06", "2021-11-01",
            "Specifies max size of merge queue.",
            "Takes effect at redeploy",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag IGNORE_MERGE_QUEUE_LIMIT = defineFeatureFlag(
            "ignore-merge-queue-limit", false,
            List.of("vekterli", "geirst"), "2021-10-06", "2021-12-01",
            "Specifies if merges that are forwarded (chained) from another content node are always " +
                    "allowed to be enqueued even if the queue is otherwise full.",
            "Takes effect at redeploy",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundIntFlag LARGE_RANK_EXPRESSION_LIMIT = defineIntFlag(
            "large-rank-expression-limit", 8192,
            List.of("baldersheim"), "2021-06-09", "2021-11-01",
            "Limit for size of rank expressions distributed by filedistribution",
            "Takes effect on next internal redeployment",
            APPLICATION_ID);

    public static final UnboundIntFlag MAX_ENCRYPTING_HOSTS = defineIntFlag(
            "max-encrypting-hosts", 0,
            List.of("mpolden", "hakonhall"), "2021-05-27", "2021-11-01",
            "The maximum number of hosts allowed to encrypt their disk concurrently",
            "Takes effect on next run of HostEncrypter, but any currently encrypting hosts will not be cancelled when reducing the limit");

    public static final UnboundBooleanFlag REQUIRE_CONNECTIVITY_CHECK = defineFeatureFlag(
            "require-connectivity-check", true,
            List.of("arnej"), "2021-06-03", "2021-12-01",
            "Require that config-sentinel connectivity check passes with good quality before starting services",
            "Takes effect on next restart",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundListFlag<String> DEFER_APPLICATION_ENCRYPTION = defineListFlag(
            "defer-application-encryption", List.of(), String.class,
            List.of("mpolden", "hakonhall"), "2021-06-23", "2021-11-01",
            "List of applications where encryption of their host should be deferred",
            "Takes effect on next run of HostEncrypter");

    public static final UnboundDoubleFlag MIN_NODE_RATIO_PER_GROUP = defineDoubleFlag(
            "min-node-ratio-per-group", 0.0,
            List.of("geirst", "vekterli"), "2021-07-16", "2021-12-01",
            "Minimum ratio of nodes that have to be available (i.e. not Down) in any hierarchic content cluster group for the group to be Up",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundListFlag<String> ALLOWED_SERVICE_VIEW_APIS = defineListFlag(
            "allowed-service-view-apis", List.of("state/v1/"), String.class,
            List.of("mortent"), "2021-08-05", "2021-11-01",
            "Apis allowed to proxy through the service view api",
            "Takes effect immediately");

    public static final UnboundBooleanFlag SEPARATE_TENANT_IAM_ROLES = defineFeatureFlag(
            "separate-tenant-iam-roles", false,
            List.of("mortent"), "2021-08-12", "2021-11-01",
            "Create separate iam roles for tenant",
            "Takes effect on redeploy",
            TENANT_ID);

    public static final UnboundIntFlag METRICSPROXY_NUM_THREADS = defineIntFlag(
            "metricsproxy-num-threads", 2,
            List.of("balder"), "2021-09-01", "2021-11-01",
            "Number of threads for metrics proxy",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag NEW_LOCATION_BROKER_LOGIC = defineFeatureFlag(
            "new-location-broker-logic", true,
            List.of("arnej"), "2021-09-07", "2021-12-31",
            "Use new implementation of internal logic in service location broker",
            "Takes effect immediately",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLED_HORIZON_DASHBOARD = defineFeatureFlag(
            "enabled-horizon-dashboard", false,
            List.of("olaa"), "2021-09-13", "2021-12-31",
            "Enable Horizon dashboard",
            "Takes effect immediately",
            TENANT_ID, CONSOLE_USER_EMAIL
    );

    public static final UnboundBooleanFlag ENABLE_ONPREM_TENANT_S3_ARCHIVE = defineFeatureFlag(
            "enable-onprem-tenant-s3-archive", false,
            List.of("bjorncs"), "2021-09-14", "2021-12-31",
            "Enable tenant S3 buckets in cd/main. Must be set on controller cluster only.",
            "Takes effect immediately",
            ZONE_ID, TENANT_ID
    );

    public static final UnboundBooleanFlag USE_APPLICATION_LOCK_IN_MAINTENANCE_DEPLOYMENT = defineFeatureFlag(
            "use-application-lock-in-maintenance-deployment", true,
            List.of("hmusum"), "2021-09-16", "2021-10-16",
            "Whether to use application node repository lock when doing maintenance deployment.",
            "Takes effect immediately",
            APPLICATION_ID
    );

    public static final UnboundBooleanFlag ENABLE_TENANT_DEVELOPER_ROLE = defineFeatureFlag(
            "enable-tenant-developer-role", false,
            List.of("bjorncs"), "2021-09-23", "2021-12-31",
            "Enable tenant developer Athenz role in cd/main. Must be set on controller cluster only.",
            "Takes effect immediately",
            TENANT_ID
    );

    public static final UnboundIntFlag MAX_CONNECTION_LIFE_IN_HOSTED = defineIntFlag(
            "max-connection-life-in-hosted", 45,
            List.of("bjorncs"), "2021-09-30", "2021-12-31",
            "Max connection life for connections to jdisc endpoints in hosted",
            "Takes effect at redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_ROUTING_REUSE_PORT = defineFeatureFlag(
            "enable-routing-reuse-port", false,
            List.of("mortent"), "2021-09-29", "2021-12-31",
            "Enable reuse port in routing configuration",
            "Takes effect on container restart",
            HOSTNAME
    );

    public static final UnboundBooleanFlag ENABLE_TENANT_OPERATOR_ROLE = defineFeatureFlag(
            "enable-tenant-operator-role", false,
            List.of("bjorncs"), "2021-09-29", "2021-12-31",
            "Enable tenant specific operator roles in public systems. For controllers only.",
            "Takes effect on subsequent maintainer invocation",
            TENANT_ID
    );

    public static final UnboundIntFlag DISTRIBUTOR_MERGE_BUSY_WAIT = defineIntFlag(
            "distributor-merge-busy-wait", 10,
            List.of("geirst", "vekterli"), "2021-10-04", "2021-12-31",
            "Number of seconds that scheduling of new merge operations in the distributor should be inhibited " +
                    "towards a content node that has indicated merge busy",
            "Takes effect at redeploy",
            ZONE_ID, APPLICATION_ID);

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
        return define(UnboundStringFlag::new, flagId, defaultValue, owners, createdAt, expiresAt, description, modificationEffect, dimensions);
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
        U create(FlagId id, T defaultVale, FetchVector defaultFetchVector);
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
