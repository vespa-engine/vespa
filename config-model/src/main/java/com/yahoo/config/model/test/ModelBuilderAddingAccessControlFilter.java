// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.List;

/**
 * A {@link ConfigModelBuilder} that configures a dummy filter component to the {@link AccessControl#ACCESS_CONTROL_CHAIN_ID} filter chain.
 *
 * @author bjorncs
 */
public class ModelBuilderAddingAccessControlFilter
        extends ConfigModelBuilder<ModelBuilderAddingAccessControlFilter.ModelPlaceholder> {

    public ModelBuilderAddingAccessControlFilter() {
        super(ModelPlaceholder.class);
    }

    @Override
    public List<ConfigModelId> handlesElements() { return ContainerModelBuilder.configModelIds; }

    @Override
    public void doBuild(ModelPlaceholder model, Element spec, ConfigModelContext modelContext) {
        for (ContainerModel containerModel : model.containers) {
            addFilterToContainerCluster(containerModel);
        }
    }

    private static void addFilterToContainerCluster(ContainerModel containerModel) {
        if (!(containerModel.getCluster() instanceof ApplicationContainerCluster)) return;
        ApplicationContainerCluster cluster = (ApplicationContainerCluster) containerModel.getCluster();
        Http http = cluster.getHttp();
        if (http.getAccessControl().isPresent()) {
            Chain<Filter> chain = http.getFilterChains()
                    .allChains()
                    .getComponent(AccessControl.ACCESS_CONTROL_CHAIN_ID);
            if (chain == null) return;
            if (!chain.getInnerComponents().isEmpty()) return;
            chain.addInnerComponent(new DummyAccessControlFilterModel());
        }
    }

    public static class ModelPlaceholder extends ConfigModel {
        final Collection<ContainerModel> containers;

        public ModelPlaceholder(ConfigModelContext modelContext, Collection<ContainerModel> containers) {
            super(modelContext);
            this.containers = containers;
        }

        @Override
        public boolean isServing() { return false; }
    }

    private static class DummyAccessControlFilterModel extends Filter {

        DummyAccessControlFilterModel() { super(createDummyComponentModel()); }

        static ChainedComponentModel createDummyComponentModel() {
            return new ChainedComponentModel(
                    new BundleInstantiationSpecification(
                            new ComponentId("dummy-filter"),
                            new ComponentSpecification("com.test.DummyAccessControlFilter"),
                            new ComponentSpecification("dummy-bundle")),
                    Dependencies.emptyDependencies());
        }
    }
}
