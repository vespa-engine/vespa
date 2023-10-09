// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ComponentAdaptor;
import com.yahoo.component.provider.ComponentRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Owns all the source groups in the search chains model.
 * @author Tony Vaagenes
 */
class SourceGroupRegistry {
    private final ComponentRegistry<ComponentAdaptor<SourceGroup>> sourceGroups
            = new ComponentRegistry<>();

    private void add(Source source) {
        getGroup(source.getComponentId()).add(source);
    }

    private SourceGroup getGroup(ComponentId sourceId) {
        ComponentAdaptor<SourceGroup> group =
                sourceGroups.getComponent(sourceId);
        if (group == null) {
            group = new ComponentAdaptor<>(sourceId,
                    new SourceGroup(sourceId));
            sourceGroups.register(group.getId(), group);
        }
        return group.model;
    }

    void addSources(Provider provider) {
        for (Source source : provider.getSources()) {
            add(source);
        }
    }

    public Collection<SourceGroup> groups() {
        List<SourceGroup> result = new ArrayList<>();
        for (ComponentAdaptor<SourceGroup> group :
                sourceGroups.allComponents()) {
            result.add(group.model);
        }
        return result;
    }

    public SourceGroup getComponent(ComponentSpecification spec) {
        ComponentAdaptor<SourceGroup> result = sourceGroups.getComponent(spec);
        return (result != null)?
                result.model :
                null;
    }
}
