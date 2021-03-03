// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gjoranv
 */
public class SecretStore {
    private final List<Group> groups = new ArrayList<>();

    public void addGroup(String name, String environment) {
        groups.add(new Group(name, environment));
    }

    public List<Group> getGroups() {
        return ImmutableList.copyOf(groups);
    }

    public static class Group {
        public final String name;
        public final String environment;

        Group(String name, String environment) {
            this.name = name;
            this.environment = environment;
        }
    }
}
