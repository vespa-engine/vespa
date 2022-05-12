// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.FileRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Constant values for ranking/model execution tied to a schema, or globally to an application package
 *
 * @author bratseth
 */
public class RankingConstants {

    private final FileRegistry fileRegistry;

    /** The schema this belongs to, or empty if it is global */
    private final Optional<Schema> owner;

    private final Map<String, RankingConstant> constants = new LinkedHashMap<>();

    private final Object mutex = new Object();

    public RankingConstants(FileRegistry fileRegistry, Optional<Schema> owner) {
        this.fileRegistry = fileRegistry;
        this.owner = owner;
    }

    public void add(RankingConstant constant) {
        synchronized (mutex) {
            constant.validate();
            constant.register(fileRegistry);
            String name = constant.getName();
            RankingConstant prev = constants.putIfAbsent(name, constant);
            if (prev != null)
                throw new IllegalArgumentException("Constant '" + name + "' defined twice");
        }
    }

    public void putIfAbsent(RankingConstant constant) {
        synchronized (mutex) {
            constant.validate();
            constant.register(fileRegistry);
            String name = constant.getName();
            constants.putIfAbsent(name, constant);
        }
    }

    public void computeIfAbsent(String name, Function<? super String, ? extends RankingConstant> createConstant) {
        synchronized (mutex) {
            constants.computeIfAbsent(name, key -> {
                RankingConstant constant = createConstant.apply(key);
                constant.validate();
                constant.register(fileRegistry);
                return constant;
            });
        }
    }

    /** Returns the ranking constant with the given name, or null if not present */
    public RankingConstant get(String name) {
        synchronized (mutex) {
            var constant = constants.get(name);
            if (constant != null) return constant;
            if (owner.isPresent() && owner.get().inherited().isPresent())
                return owner.get().inherited().get().rankingConstants().get(name);
            return null;
        }
    }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankingConstant> asMap() {
        synchronized (mutex) {
            // Shortcuts
            if (owner.isEmpty() || owner.get().inherited().isEmpty()) return Collections.unmodifiableMap(constants);
            if (constants.isEmpty()) return owner.get().inherited().get().rankingConstants().asMap();

            var allConstants = new LinkedHashMap<>(owner.get().inherited().get().rankingConstants().asMap());
            allConstants.putAll(constants);
            return Collections.unmodifiableMap(allConstants);
        }
    }

}
