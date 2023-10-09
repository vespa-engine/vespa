// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to add builders and elements in addBuilder, and then build a dependency graph based on the
 * constructor arguments.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ModelGraphBuilder {

    private final List<ConfigModelBuilder<? extends ConfigModel>> builders = new ArrayList<>();

    /**
     * Add a {@link com.yahoo.config.model.builder.xml.ConfigModelBuilder} to this graph.
     *
     * @param builder The {@link com.yahoo.config.model.builder.xml.ConfigModelBuilder} to add.
     * @return this for convenience
     */
    public ModelGraphBuilder addBuilder(ConfigModelBuilder<? extends ConfigModel> builder) {
        builders.add(builder);
        return this;
    }

    /**
     * Build a {@link com.yahoo.config.model.graph.ModelGraph} based on the {@link com.yahoo.config.model.builder.xml.ConfigModelBuilder}s
     * added to this.
     *
     * @return A {@link com.yahoo.config.model.graph.ModelGraph} representing the dependency graph.
     */
    public ModelGraph build() {
        List<ModelNode> modelNodes = new ArrayList<>();
        for (ConfigModelBuilder<? extends ConfigModel> builder : builders) {
            modelNodes.add(new ModelNode(builder));
        }
        List<ModelNode> roots = new ArrayList<>();
        for (ModelNode modelNode : modelNodes) {
            int numDependencies = modelNode.addDependenciesFrom(modelNodes);
            if (numDependencies == 0) {
                roots.add(modelNode);
            }
        }
        return new ModelGraph(modelNodes, roots);
    }
}
