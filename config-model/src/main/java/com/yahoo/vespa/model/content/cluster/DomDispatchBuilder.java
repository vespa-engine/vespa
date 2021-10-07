// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;


import com.yahoo.vespa.model.content.DispatchSpec;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for the dispatch setup for a content cluster.
 *
 * @author geirst
 */
public class DomDispatchBuilder {

    public static DispatchSpec build(ModelElement contentXml) {
        DispatchSpec.Builder builder = new DispatchSpec.Builder();
        ModelElement dispatchElement = contentXml.child("dispatch");
        if (dispatchElement == null) {
            return builder.build();
        }
        builder.setNumDispatchGroups(dispatchElement.childAsInteger("num-dispatch-groups"));

        List<ModelElement> groupsElement = dispatchElement.subElements("group");
        if (groupsElement != null) {
            builder.setGroups(buildGroups(groupsElement));
        }
        return builder.build();
    }

    private static List<DispatchSpec.Group> buildGroups(List<ModelElement> groupsElement) {
        List<DispatchSpec.Group> groups = new ArrayList<>();
        for (ModelElement groupElement : groupsElement) {
            groups.add(buildGroup(groupElement));
        }
        return groups;
    }

    private static DispatchSpec.Group buildGroup(ModelElement groupElement) {
        List<ModelElement> nodes = groupElement.subElements("node");
        DispatchSpec.Group group = new DispatchSpec.Group();
        for (ModelElement nodeElement : nodes) {
            group.addNode(buildNode(nodeElement));
        }
        return group;
    }

    private static DispatchSpec.Node buildNode(ModelElement nodeElement) {
        return new DispatchSpec.Node(nodeElement.integerAttribute("distribution-key"));
    }
}
