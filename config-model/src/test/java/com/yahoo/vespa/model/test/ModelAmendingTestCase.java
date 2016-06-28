// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.content.Content;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Demonstrates how a model can be added at build time to amend another model.
 * This is useful is situations where the core Vespa config models needs to be
 * modified by third party code which follows the installed environment rather than
 * the application.
 *
 * @author bratseth
 */
public class ModelAmendingTestCase {

    @Test
    public void testModelAmending() throws IOException, SAXException {
        ConfigModelRegistry amendingModelRepo = MapConfigModelRegistry.createFromList(new ContainerModelAmenderBuilder(),
                                                                                      new ContentModelAmenderBuilder());
        VespaModel model = new VespaModel(new MockApplicationPackage.Builder()
                                                  .withServices(
                                     "<services version='1.0'>" +
                                             "    <jdisc id='test1' version='1.0'>" +
                                             "        <search />" +
                                             "    </jdisc>" +
                                             "    <jdisc id='test2' version='1.0'>" +
                                             "        <http><server id='server1' port='19107'/></http>" +
                                             "        <document-api/>" +
                                             "    </jdisc>" +
                                             "    <content id='test3' version='1.0'>" +
                                             "        <redundancy>1</redundancy>" +
                                             "        <documents>" +
                                             "            <document mode='index' type='testtype1'/>" +
                                             "        </documents>" +
                                             "    </content>" +
                                             "    <content id='test4' version='1.0'>" +
                                             "        <redundancy>1</redundancy>" +
                                             "        <documents>" +
                                             "            <document mode='index' type='testtype1'/>" +
                                             "        </documents>" +
                                             "    </content>" +
                                             "</services>")
                                          .withSearchDefinitions(
                                                  searchDefinition("testtype1"))
                                                  .build(),
                                          amendingModelRepo);
        assertEquals(1, model.getHostSystem().getHosts().size());

        // Check that explicit jdisc clusters are amended
        assertEquals(4, model.getContainerClusters().size());
        assertNotNull(model.getContainerClusters().get("test1").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
        assertNotNull(model.getContainerClusters().get("test2").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
        assertNotNull(model.getContainerClusters().get("cluster.test3.indexing").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
        assertNotNull(model.getContainerClusters().get("cluster.test4.indexing").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
    }

    private List<String> searchDefinition(String name) {
        return Collections.singletonList(
                "search " + name + " {" +
                "  document " + name + " {" +
                "    field testfield type string {}" +
                "  }" +
                "}");
    }

    public static class ContainerModelAmenderBuilder extends ConfigModelBuilder<ContainerModelAmender> {

        private boolean built = false;

        public ContainerModelAmenderBuilder() {
            super(ContainerModelAmender.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            return ContainerModelBuilder.configModelIds;
        }

        @Override
        public void doBuild(ContainerModelAmender model, Element spec, ConfigModelContext modelContext) {
            if (built) return; // the same instance will be called once per jdisc cluster
            for (ContainerModel containerModel : model.containerModels)
                amend(containerModel.getCluster());
            built = true;
        }

        static void amend(ContainerCluster cluster) {
            cluster.addSimpleComponent("com.yahoo.MyAmendedComponent", null, "my-amendment-bundle");
        }

    }

    public static class ContainerModelAmender extends ConfigModel {

        /** The container models this builder amends */
        private final Collection<ContainerModel> containerModels;

        public ContainerModelAmender(ConfigModelContext modelContext, Collection<ContainerModel> containerModels) {
            super(modelContext);
            this.containerModels = containerModels;
        }

        @Override
        public boolean isServing() { return false; }

    }

    public static class ContentModelAmenderBuilder extends ConfigModelBuilder<ContentModelAmender> {

        private boolean built = false;

        public ContentModelAmenderBuilder() {
            super(ContentModelAmender.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            return Content.Builder.configModelIds;
        }

        @Override
        public void doBuild(ContentModelAmender model, Element spec, ConfigModelContext modelContext) {
            if (built) return; // the same instance will be called once per content cluster
            for (Content contentModel : model.contentModels)
                contentModel.ownedIndexingCluster().ifPresent(ContainerModelAmenderBuilder::amend);
            built = true;
        }
    }

    public static class ContentModelAmender extends ConfigModel {

        private final Collection<Content> contentModels;

        public ContentModelAmender(ConfigModelContext modelContext, Collection<Content> contentModels) {
            super(modelContext);
            this.contentModels = contentModels;
        }

        @Override
        public boolean isServing() { return false; }

    }

}
