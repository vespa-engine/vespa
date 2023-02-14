// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.admin.AdminModel;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.content.Content;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void testModelAmending() {
        ConfigModelRegistry amendingModelRepo = MapConfigModelRegistry.createFromList(new AdminModelAmenderBuilder(),
                new ContainerModelAmenderBuilder(),
                new ContentModelAmenderBuilder());
        String services =
                "<services version='1.0'>" +
                        "    <admin version='4.0'/>" +
                        "    <container id='test1' version='1.0'>" +
                        "        <search/>" +
                        "        <nodes count='2'/>" +
                        "    </container>" +
                        "    <container id='test2' version='1.0'>" +
                        "        <http><server id='server1' port='" + Defaults.getDefaults().vespaWebServicePort() + "'/></http>" +
                        "        <document-api/>" +
                        "        <nodes count='2'/>" +
                        "    </container>" +
                        "    <content id='test3' version='1.0'>" +
                        "        <redundancy>1</redundancy>" +
                        "        <documents>" +
                        "            <document mode='index' type='type1'/>" +
                        "        </documents>" +
                        "        <nodes count='2'/>" +
                        "    </content>" +
                        "    <content id='test4' version='1.0'>" +
                        "        <redundancy>1</redundancy>" +
                        "        <documents>" +
                        "            <document mode='index' type='type1'/>" +
                        "        </documents>" +
                        "        <nodes count='3'/>" +
                        "    </content>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester(amendingModelRepo);
        tester.addHosts(12);
        VespaModel model = tester.createModel(services);

        // Check that all hosts are amended
        for (HostResource host : model.getAdmin().hostSystem().getHosts()) {
            assertFalse(host.getHost().getChildrenByTypeRecursive(AmendedService.class).isEmpty(), host + " is amended");
        }

        // Check that container clusters are amended
        assertEquals(2, model.getContainerClusters().size());
        assertNotNull(model.getContainerClusters().get("test1").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
        assertNotNull(model.getContainerClusters().get("test2").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
    }

    @Test
    void testModelAmendingWithDedicatedCC() {
        ConfigModelRegistry amendingModelRepo = MapConfigModelRegistry.createFromList(new AdminModelAmenderBuilder(),
                new ContainerModelAmenderBuilder(),
                new ContentModelAmenderBuilder());
        String services =
                "<services version='1.0'>" +
                        "    <admin version='4.0'/>" +
                        "    <container id='test1' version='1.0'>" +
                        "        <search/>" +
                        "        <nodes count='2'/>" +
                        "    </container>" +
                        "    <container id='test2' version='1.0'>" +
                        "        <http><server id='server1' port='" + Defaults.getDefaults().vespaWebServicePort() + "'/></http>" +
                        "        <document-api/>" +
                        "        <nodes count='2'/>" +
                        "    </container>" +
                        "    <content id='test3' version='1.0'>" +
                        "        <redundancy>1</redundancy>" +
                        "        <documents>" +
                        "            <document mode='index' type='type1'/>" +
                        "        </documents>" +
                        "        <nodes count='2'/>" +
                        "    </content>" +
                        "    <content id='test4' version='1.0'>" +
                        "        <redundancy>1</redundancy>" +
                        "        <documents>" +
                        "            <document mode='index' type='type1'/>" +
                        "        </documents>" +
                        "        <nodes count='3'/>" +
                        "    </content>" +
                        "</services>";
        VespaModelTester tester = new VespaModelTester(amendingModelRepo);
        tester.addHosts(12);
        VespaModel model = tester.createModel(services);

        // Check that all hosts are amended
        for (HostResource host : model.getAdmin().hostSystem().getHosts()) {
            assertFalse(host.getHost().getChildrenByTypeRecursive(AmendedService.class).isEmpty(), host + " is amended");
        }

        // Check that container clusters are amended
        assertEquals(2, model.getContainerClusters().size());
        assertNotNull(model.getContainerClusters().get("test1").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
        assertNotNull(model.getContainerClusters().get("test2").getComponentsMap().get(new ComponentId("com.yahoo.MyAmendedComponent")));
    }

    public static class AdminModelAmenderBuilder extends ConfigModelBuilder<AdminModelAmender> {

        public AdminModelAmenderBuilder() {
            super(AdminModelAmender.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            List<ConfigModelId> adminElements = new ArrayList<>();
            adminElements.addAll(AdminModel.BuilderV2.configModelIds);
            adminElements.addAll(AdminModel.BuilderV4.configModelIds);
            return adminElements;
        }

        @Override
        public void doBuild(AdminModelAmender model, Element spec, ConfigModelContext modelContext) {
            for (AdminModel adminModel : model.adminModels)
                amend(modelContext.getDeployState(), adminModel);
        }

        private void amend(DeployState deployState, AdminModel adminModel) {
            for (HostResource host : adminModel.getAdmin().hostSystem().getHosts()) {
                if ( ! host.getHost().getChildrenByTypeRecursive(AmendedService.class).isEmpty()) continue; // already amended
                adminModel.getAdmin().addAndInitializeService(deployState, host, new AmendedService(host.getHost()));
            }
        }

    }

    /** To test that we can amend hosts with an additional service */
    private static class AmendedService extends AbstractService {

        public AmendedService(TreeConfigProducer parent) {
            super(parent, "testservice");
        }

        @Override
        public int getPortCount() {
            return 0;
        }

        @Override public void allocatePorts(int start, PortAllocBridge from) { }
    }

    public static class AdminModelAmender extends ConfigModel {

        /** The admin models this builder amends */
        private final Collection<AdminModel> adminModels;

        /** Depend on all models adding hosts to the system as this this should amend services on all hosts */
        public AdminModelAmender(ConfigModelContext modelContext, Collection<AdminModel> adminModels, 
                                 Collection<ContainerModel> containerModels, Collection<Content> contentModels) {
            super(modelContext);
            this.adminModels = adminModels;
        }

        @Override
        public boolean isServing() { return false; }

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
            if (built) return; // the same instance will be called once per container cluster
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
