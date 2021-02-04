// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static com.yahoo.vespa.flags.FetchVector.Dimension.*;

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

    public static final UnboundStringFlag ALLOCATE_OS_REQUIREMENT = defineStringFlag(
            "allocate-os-requirement", "rhel7",
            List.of("hakonhall"), "2021-01-26", "2021-07-26",
            "Allocations of new nodes are limited to the given host OS.  Must be one of 'rhel7', " +
            "'rhel8', or 'any'",
            "Takes effect on next (re)deployment.",
            APPLICATION_ID);

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
            "skip-communicatiomanager-thread", false,
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
            List.of("vekterli"), "2020-12-02", "2021-03-01",
            "Whether to enable the use of three-phase updates when bucket replicas are out of sync.",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundStringFlag TLS_FOR_ZOOKEEPER_CLIENT_SERVER_COMMUNICATION = defineStringFlag(
            "tls-for-zookeeper-client-server-communication", "OFF",
            List.of("hmusum"), "2020-12-02", "2021-04-01",
            "How to setup TLS for ZooKeeper client/server communication. Valid values are OFF, PORT_UNIFICATION, TLS_WITH_PORT_UNIFICATION, TLS_ONLY",
            "Takes effect on restart of config server",
            NODE_TYPE, HOSTNAME);

    public static final UnboundBooleanFlag USE_TLS_FOR_ZOOKEEPER_CLIENT = defineFeatureFlag(
            "use-tls-for-zookeeper-client", false,
            List.of("hmusum"), "2020-12-02", "2021-04-01",
            "Whether to use TLS for ZooKeeper clients",
            "Takes effect on restart of process",
            NODE_TYPE, HOSTNAME);

    public static final UnboundStringFlag YUM_DIST_HOST = defineStringFlag(
            "yum-dist-host", "",
            List.of("aressem"), "2020-12-02", "2021-03-01",
            "Override the default dist host for yum.",
            "Takes effect on next tick or on host-admin restart (may vary where used).");

    public static final UnboundBooleanFlag PROVISION_TENANT_ROLES = defineFeatureFlag(
            "provision-tenant-roles", false,
            List.of("tokle"), "2020-12-02", "2021-04-01",
            "Whether tenant roles should be provisioned",
            "Takes effect on next deployment (controller)",
            TENANT_ID);

    public static final UnboundBooleanFlag TENANT_IAM_ROLE = defineFeatureFlag(
            "application-iam-roles", false,
            List.of("tokle"), "2020-12-02", "2021-04-01",
            "Allow separate iam roles when provisioning/assigning hosts",
            "Takes effect immediately on new hosts, on next redeploy for applications",
            TENANT_ID);

    public static final UnboundIntFlag MAX_TRIAL_TENANTS = defineIntFlag(
            "max-trial-tenants", -1,
            List.of("ogronnesby"), "2020-12-03", "2021-04-01",
            "The maximum nr. of tenants with trial plan, -1 is unlimited",
            "Takes effect immediately"
    );

    public static final UnboundBooleanFlag ONLY_PUBLIC_ACCESS = defineFeatureFlag(
            "enable-public-only", false,
            List.of("ogronnesby"), "2020-12-02", "2021-02-01",
            "Only access public hosts from container",
            "Takes effect on next tick"
    );

    public static final UnboundBooleanFlag HIDE_SHARED_ROUTING_ENDPOINT = defineFeatureFlag(
            "hide-shared-routing-endpoint", false,
            List.of("tokle"), "2020-12-02", "2021-06-01",
            "Whether the controller should hide shared routing layer endpoint",
            "Takes effect immediately",
            APPLICATION_ID
    );

    public static final UnboundBooleanFlag USE_ACCESS_CONTROL_CLIENT_AUTHENTICATION = defineFeatureFlag(
            "use-access-control-client-authentication", false,
            List.of("tokle"), "2020-12-02", "2021-03-01",
            "Whether application container should set up client authentication on default port based on access control element",
            "Takes effect on next internal redeployment",
            APPLICATION_ID);

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

    public static final UnboundDoubleFlag REINDEXER_WINDOW_SIZE_INCREMENT = defineDoubleFlag(
            "reindexer-window-size-increment", 0.2,
            List.of("jonmv"), "2020-12-09", "2021-02-07",
            "Window size increment for dynamic throttle policy used by reindexer visitor session — more means more aggressive reindexing",
            "Takes effect on (re)deployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag USE_BUCKET_EXECUTOR_FOR_LID_SPACE_COMPACT = defineFeatureFlag(
            "use-bucket-executor-for-lid-space-compact", false,
            List.of("baldersheim"), "2021-01-24", "2021-03-01",
            "Wheter to use content-level bucket executor or legacy frozen buckets",
            "Takes effect on next internal redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag USE_POWER_OF_TWO_CHOICES_LOAD_BALANCING = defineFeatureFlag(
            "use-power-of-two-choices-load-balancing", false,
            List.of("tokle"), "2020-12-02", "2021-02-15",
            "Whether to use Power of two load balancing algorithm for application",
            "Takes effect on next internal redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag GROUP_SUSPENSION = defineFeatureFlag(
            "group-suspension", false,
            List.of("hakon"), "2021-01-22", "2021-03-22",
            "Allow all content nodes in a hierarchical group to suspend at the same time",
            "Takes effect on the next suspension request to the Orchestrator.",
            APPLICATION_ID);

    public static final UnboundBooleanFlag RECONFIGURABLE_ZOOKEEPER_SERVER_FOR_CLUSTER_CONTROLLER = defineFeatureFlag(
            "reconfigurable-zookeeper-server-for-cluster-controller", false,
            List.of("musum", "mpolden"), "2020-12-16", "2021-02-16",
            "Whether to use reconfigurable zookeeper server for cluster controller",
            "Takes effect on (re)redeployment",
            APPLICATION_ID);

    public static final UnboundBooleanFlag ENABLE_JDISC_CONNECTION_LOG = defineFeatureFlag(
            "enable-jdisc-connection-log", false,
            List.of("bjorncs", "tokle"), "2021-01-12", "2021-04-01",
            "Whether to enable jdisc connection log",
            "Takes effect on (re)deployment");

    public static final UnboundBooleanFlag ENABLE_ZSTD_COMPRESSION_ACCESS_LOG = defineFeatureFlag(
            "enable-zstd-compression-access-log", false,
            List.of("bjorncs", "tokle", "baldersheim"), "2021-01-19", "2021-04-01",
            "Whether to enable zstd compression of jdisc access logs",
            "Takes effect on (re)deployment");

    public static final UnboundBooleanFlag ENABLE_FEED_BLOCK_IN_DISTRIBUTOR = defineFeatureFlag(
            "enable-feed-block-in-distributor", false,
            List.of("geirst"), "2021-01-27", "2021-04-01",
            "Enables blocking of feed in the distributor if resource usage is above limit on at least one content node",
            "Takes effect at redeployment",
            ZONE_ID, APPLICATION_ID);

    public static final UnboundDoubleFlag MAX_DEAD_BYTES_RATIO = defineDoubleFlag(
            "max-dead-bytes-ratio", 0.2,
            List.of("baldersheim", "geirst","toregge"), "2021-02-03", "2021-04-01",
            "max ratio of dead to used memory bytes in large data structures before compaction is attempted",
            "Takes effect at redeployment",
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
    public static Replacer clearFlagsForTesting() {
        return new Replacer();
    }

    public static class Replacer implements AutoCloseable {
        private static volatile boolean flagsCleared = false;

        private final TreeMap<FlagId, FlagDefinition> savedFlags;

        private Replacer() {
            verifyAndSetFlagsCleared(true);
            this.savedFlags = Flags.flags;
            Flags.flags = new TreeMap<>();
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
