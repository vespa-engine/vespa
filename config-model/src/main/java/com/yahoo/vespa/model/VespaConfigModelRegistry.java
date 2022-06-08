// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.admin.AdminModel;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.builder.xml.dom.DomRoutingBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import com.yahoo.vespa.model.content.Content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the Vespa config model types
 *
 * @author bratseth
 */
public class VespaConfigModelRegistry extends ConfigModelRegistry {

    private final List<ConfigModelBuilder> builderList = new ArrayList<>();

    /** Creates a bundled model class registry which forwards unresolved requests to the argument instance */
    public VespaConfigModelRegistry(ConfigModelRegistry chained) {
        super(chained);
        builderList.add(new AdminModel.BuilderV2());
        builderList.add(new AdminModel.BuilderV4());
        builderList.add(new DomRoutingBuilder());
        builderList.add(new Content.Builder());
        builderList.add(new ContainerModelBuilder(false, Networking.enable));
    }

    @Override
    public Collection<ConfigModelBuilder> resolve(ConfigModelId id) {
        Set<ConfigModelBuilder> builders = new HashSet<>(chained().resolve(id));
        for (ConfigModelBuilder builder : builderList) {
            if (builder.handlesElements().contains(id))
                builders.add(builder);
        }
        return builders;
    }

}
