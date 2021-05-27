// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.vespa.model.AbstractService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Constant values for ranking/model execution tied to a search definition, or globally to an application
 * package
 *
 * @author bratseth
 */
public class RankingConstants {

    private final Map<String, RankingConstant> constants = new HashMap<>();

    public void add(RankingConstant constant) {
        constant.validate();
        String name = constant.getName();
        if (constants.containsKey(name))
            throw new IllegalArgumentException("Ranking constant '" + name + "' defined twice");
        constants.put(name, constant);
    }

    /** Returns the ranking constant with the given name, or null if not present */
    public RankingConstant get(String name) {
        return constants.get(name);
    }

    /** Returns a read-only map of the ranking constants in this indexed by name */
    public Map<String, RankingConstant> asMap() {
        return Collections.unmodifiableMap(constants);
    }

    /** Initiate sending of these constants to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        constants.values().forEach(constant -> constant.sendTo(services));
    }
}
