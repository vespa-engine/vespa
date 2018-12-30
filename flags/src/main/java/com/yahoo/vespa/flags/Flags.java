// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yahoo.vespa.defaults.Defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Definitions of most/all flags.
 *
 * <p>The flags are centrally defined in this module to allow 1. all code to access flags that may be used in
 * quite different modules and processes, and 2. in particular allow the config server to access all flags
 * so operators have a nicer UI for setting, modifying, or removing flag values.
 *
 * <p>This class should have been an enum, but unfortunately enums cannot be generic, which will eventually be
 * fixed with <a href="https://openjdk.java.net/jeps/301">JEP 301: Enhanced Enums</a>.
 *
 * @author hakonhall
 */
public class Flags {
    public static final FlagSerializer<Boolean> BOOLEAN_SERIALIZER = new SimpleFlagSerializer<>(BooleanNode::valueOf, JsonNode::isBoolean, JsonNode::asBoolean);
    public static final FlagSerializer<String> STRING_SERIALIZER = new SimpleFlagSerializer<>(TextNode::new, JsonNode::isTextual, JsonNode::asText);
    public static final FlagSerializer<Integer> INT_SERIALIZER = new SimpleFlagSerializer<>(IntNode::new, JsonNode::isIntegralNumber, JsonNode::asInt);
    public static final FlagSerializer<Long> LONG_SERIALIZER = new SimpleFlagSerializer<>(LongNode::new, JsonNode::isIntegralNumber, JsonNode::asLong);

    private static final ConcurrentHashMap<FlagId, FlagDefinition<?>> flags = new ConcurrentHashMap<>();

    public static final UnboundFlag<Boolean> HEALTHMONITOR_MONITOR_INFRA = defineBoolean(
            "healthmonitor-monitorinfra", true,
            "Whether the health monitor in service monitor monitors the health of infrastructure applications.",
            "Affects all applications activated after the value is changed.",
            FetchVector.Dimension.HOSTNAME);

    public static final UnboundFlag<Boolean> DUPERMODEL_CONTAINS_INFRA = defineBoolean(
            "dupermodel-contains-infra", true,
            "Whether the DuperModel in config server/controller includes active infrastructure applications " +
                    "(except from controller/config apps).",
            "Requires restart of config server/controller to take effect.",
            FetchVector.Dimension.HOSTNAME);

    public static final UnboundFlag<Boolean> DUPERMODEL_USE_CONFIGSERVERCONFIG = defineBoolean(
            "dupermodel-use-configserverconfig", true,
            "For historical reasons, the ApplicationInfo in the DuperModel for controllers and config servers " +
                    "is based on the ConfigserverConfig (this flag is true). We want to transition to use the " +
                    "infrastructure application activated by the InfrastructureProvisioner once that supports health.",
            "Requires restart of config server/controller to take effect.",
            FetchVector.Dimension.HOSTNAME);

    public static final UnboundFlag<Boolean> USE_CONFIG_SERVER_CACHE = defineBoolean(
            "use-config-server-cache", true,
            "Whether config server will use cache to answer config requests.",
            "Takes effect immediately when changed.",
            FetchVector.Dimension.HOSTNAME, FetchVector.Dimension.APPLICATION_ID);

    public static final UnboundFlag<Boolean> CONFIG_SERVER_BOOTSTRAP_IN_SEPARATE_THREAD = defineBoolean(
            "config-server-bootstrap-in-separate-thread", true,
            "Whether to run config server/controller bootstrap in a separate thread.",
            "Takes effect only at bootstrap of config server/controller",
            FetchVector.Dimension.HOSTNAME);

    public static UnboundFlag<Boolean> defineBoolean(String flagId, boolean defaultValue, String description,
                                                     String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(flagId, defaultValue, BOOLEAN_SERIALIZER, description, modificationEffect, dimensions);
    }

    public static UnboundFlag<String> defineString(String flagId, String defaultValue, String description,
                                                   String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(flagId, defaultValue, STRING_SERIALIZER, description, modificationEffect, dimensions);
    }

    public static UnboundFlag<Integer> defineInt(String flagId, Integer defaultValue, String description,
                                                 String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(flagId, defaultValue, INT_SERIALIZER, description, modificationEffect, dimensions);
    }

    public static UnboundFlag<Long> defineLong(String flagId, Long defaultValue, String description,
                                               String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(flagId, defaultValue, LONG_SERIALIZER, description, modificationEffect, dimensions);
    }

    public static <T> UnboundFlag<T> defineJackson(String flagId, Class<T> jacksonClass, T defaultValue, String description,
                                                   String modificationEffect, FetchVector.Dimension... dimensions) {
        return define(flagId, defaultValue, new JacksonSerializer<>(jacksonClass), description, modificationEffect, dimensions);
    }

    /**
     * Defines a Flag.
     *
     * @param flagId             The globally unique FlagId.
     * @param defaultValue       The default value if none is present after resolution.
     * @param deserializer       Deserialize JSON to value type.
     * @param description        Description of how the flag is used.
     * @param modificationEffect What is required for the flag to take effect? A restart of process? immediately? etc.
     * @param dimensions         What dimensions will be set in the {@link FetchVector} when fetching
     *                           the flag value in
     *                           {@link FlagSource#fetch(FlagId, FetchVector) FlagSource::fetch}.
     *                           For instance, if APPLICATION is one of the dimensions here, you should make sure
     *                           APPLICATION is set to the ApplicationId in the fetch vector when fetching the RawFlag
     *                           from the FlagSource.
     * @param <T>                The type of the flag value, typically Boolean for flags guarding features.
     * @return An unbound flag with {@link FetchVector.Dimension#HOSTNAME HOSTNAME} environment. The ZONE environment
     *         is typically implicit.
     */
    private static <T> UnboundFlag<T> define(String flagId, T defaultValue, Deserializer<T> deserializer,
                                             String description, String modificationEffect,
                                             FetchVector.Dimension... dimensions) {
        UnboundFlag<T> flag = new UnboundFlag<>(flagId, defaultValue, deserializer)
                .with(FetchVector.Dimension.HOSTNAME, Defaults.getDefaults().vespaHostname());
        FlagDefinition<T> definition = new FlagDefinition<>(flag, description, modificationEffect, Arrays.asList(dimensions));
        flags.put(flag.id(), definition);
        return flag;
    }

    public static List<FlagDefinition<?>> getAllFlags() {
        return new ArrayList<>(flags.values());
    }

    public static Optional<FlagDefinition<?>> getFlag(FlagId flagId) {
        return Optional.ofNullable(flags.get(flagId));
    }
}
