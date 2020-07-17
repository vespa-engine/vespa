// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.HostPorts;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


/**
 * @author einarmr
 * @author gjoranv
 * @since 5.1.9
 */
public class DocprocBuilderTest extends DomBuilderTest {

    private ApplicationContainerCluster cluster;
    private DocumentmanagerConfig documentmanagerConfig;
    private ContainerMbusConfig containerMbusConfig;
    private ComponentsConfig componentsConfig;
    private ChainsConfig chainsConfig;
    private SchemamappingConfig schemamappingConfig;
    private DocprocConfig docprocConfig;
    private QrStartConfig qrStartConfig;

    @Before
    public void setupCluster() {
        ContainerModel model = new ContainerModelBuilder(false, Networking.disable).build(DeployState.createTestState(), null, null, root, servicesXml());
        cluster = (ApplicationContainerCluster) model.getCluster();
        cluster.getDocproc().getChains().addServersAndClientsForChains();
        root.freezeModelTopology();

        containerMbusConfig = root.getConfig(ContainerMbusConfig.class, cluster.getContainers().get(0).getConfigId());
        componentsConfig = root.getConfig(ComponentsConfig.class, cluster.getConfigId());
        chainsConfig = root.getConfig(ChainsConfig.class,
                cluster.getConfigId() + "/component/com.yahoo.docproc.jdisc.DocumentProcessingHandler");

        documentmanagerConfig = root.getConfig(DocumentmanagerConfig.class, cluster.getConfigId());
        schemamappingConfig = root.getConfig(SchemamappingConfig.class, cluster.getContainers().get(0).getConfigId());
        qrStartConfig = root.getConfig(QrStartConfig.class, cluster.getConfigId());
        docprocConfig = root.getConfig(DocprocConfig.class, cluster.getConfigId());
    }

    private Element servicesXml() {
        return parse(
                "<container id='banan' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='mockhost' baseport='1500' />",
                "  </nodes>",
                "  <document-processing compressdocuments='true' preferlocalnode='true' numnodesperclient='2' maxqueuebytesize='100m' maxmessagesinqueue='300' maxqueuewait='200'>",
                "    <documentprocessor id='docproc1' class='com.yahoo.Docproc1' bundle='docproc1bundle'/>",
                "    <chain id='chein'>",
                "      <documentprocessor id='docproc2'/>",
                "    </chain>",
                "  </document-processing>",
                "</container>");
    }

    // TODO: re-enable assertions when the appropriate attributes are handled by the builder
    @Test
    public void testDocprocCluster() {
        assertThat(cluster.getName(), is("banan"));
        assertThat(cluster.getDocproc().isCompressDocuments(), is(true));
        //assertThat(cluster.getContainerDocproc().isPreferLocalNode(), is(true));
        //assertThat(cluster.getContainerDocproc().getNumNodesPerClient(), is(2));
        List<ApplicationContainer> services = cluster.getContainers();
        assertThat(services.size(), is(1));
        ApplicationContainer service = services.get(0);
        assertThat(service, notNullValue());

        Map<String, DocprocChain> chains = new HashMap<>();
        for (DocprocChain chain : cluster.getDocprocChains().allChains().allComponents()) {
            chains.put(chain.getId().stringValue(), chain);
        }
        assertThat(chains.size(), is(1));

        DocprocChain chain = chains.get("chein");
        assertThat(chain.getId().stringValue(), is("chein"));
        assertThat(chain.getInnerComponents().size(), is(1));
        DocumentProcessor processor = chain.getInnerComponents().iterator().next();
        assertThat(processor.getComponentId().stringValue(), is("docproc2"));
    }

    @Test
    public void testDocumentManagerConfig() {
        assertThat(documentmanagerConfig.enablecompression(), is(true));
    }

    @Test
    public void testDocprocConfig() {
        assertThat(docprocConfig.maxqueuetimems(), is(200000));

    }

    @Test
    public void testContainerMbusConfig() {
        assertThat(containerMbusConfig.enabled(), is(true));
        assertTrue(containerMbusConfig.port() >= HostPorts.BASE_PORT);
        assertThat(containerMbusConfig.maxpendingcount(), is(300));
        assertThat(containerMbusConfig.maxpendingsize(), is(100));
    }

    @Test
    public void testComponentsConfig() {
        Map<String, ComponentsConfig.Components> components = new HashMap<>();
        for (ComponentsConfig.Components component : componentsConfig.components()) {
            System.err.println(component.id());
            components.put(component.id(), component);
        }

        ComponentsConfig.Components docprocHandler = components.get("com.yahoo.docproc.jdisc.DocumentProcessingHandler");
        assertThat(docprocHandler.id(), is("com.yahoo.docproc.jdisc.DocumentProcessingHandler"));
        assertThat(docprocHandler.configId(), is("banan/component/com.yahoo.docproc.jdisc.DocumentProcessingHandler"));
        assertThat(docprocHandler.classId(), is("com.yahoo.docproc.jdisc.DocumentProcessingHandler"));
        assertThat(docprocHandler.bundle(), is("container-search-and-docproc"));

        ComponentsConfig.Components docproc1 = components.get("docproc1");
        assertThat(docproc1.id(), is("docproc1"));
        assertThat(docproc1.configId(), is("banan/docprocchains/component/docproc1"));
        assertThat(docproc1.classId(), is("com.yahoo.Docproc1"));
        assertThat(docproc1.bundle(), is("docproc1bundle"));

        ComponentsConfig.Components docproc2 = components.get("docproc2@chein");
        assertThat(docproc2.id(), is("docproc2@chein"));
        assertThat(docproc2.configId(), is("banan/docprocchains/chain/chein/component/docproc2"));
        assertThat(docproc2.classId(), is("docproc2"));
        assertThat(docproc2.bundle(), is("docproc2"));
/*
        ComponentsConfig.Components health = components.get("com.yahoo.container.jdisc.state.StateHandler");
        assertThat(health.id(), is("com.yahoo.container.jdisc.state.StateHandler"));
        assertThat(health.classId(), is("com.yahoo.container.jdisc.state.StateHandler"));
        assertThat(health.bundle(), is("com.yahoo.container.jdisc.state.StateHandler"));
*/
        ComponentsConfig.Components sourceClient = components.get("source@MbusClient");
        assertNotNull(sourceClient);
        assertThat(sourceClient.classId(), is("com.yahoo.container.jdisc.messagebus.MbusClientProvider"));
        assertThat(sourceClient.bundle(),  is("com.yahoo.container.jdisc.messagebus.MbusClientProvider"));

        ComponentsConfig.Components intermediateClient = components.get("chain.chein@MbusClient");
        assertNotNull(intermediateClient);
        assertThat(intermediateClient.classId(), is("com.yahoo.container.jdisc.messagebus.MbusClientProvider"));
        assertThat(intermediateClient.bundle(),  is("com.yahoo.container.jdisc.messagebus.MbusClientProvider"));
    }

    @Test
    public void testChainsConfig() {
        Map<String, ChainsConfig.Components> components = new HashMap<>();
        for (ChainsConfig.Components component : chainsConfig.components()) {
            components.put(component.id(), component);
        }

        assertThat(components.size(), is(2));

        ChainsConfig.Components docproc1 = components.get("docproc1");
        assertThat(docproc1.id(), is("docproc1"));
        assertThat(docproc1.dependencies().provides().size(), is(0));
        assertThat(docproc1.dependencies().before().size(), is(0));
        assertThat(docproc1.dependencies().after().size(), is(0));

        ChainsConfig.Components docproc2 = components.get("docproc2@chein");
        assertThat(docproc2.id(), is("docproc2@chein"));
        assertThat(docproc2.dependencies().provides().size(), is(0));
        assertThat(docproc2.dependencies().before().size(), is(0));
        assertThat(docproc2.dependencies().after().size(), is(0));

        Map<String, ChainsConfig.Chains> chainsMap = new HashMap<>();
        for (ChainsConfig.Chains chain : chainsConfig.chains()) {
            chainsMap.put(chain.id(), chain);
        }

        assertThat(chainsMap.size(), is(1));
        assertThat(chainsMap.get("chein").id(), is("chein"));
        assertThat(chainsMap.get("chein").components().size(), is(1));
        assertThat(chainsMap.get("chein").components(0), is("docproc2@chein"));
        assertThat(chainsMap.get("chein").inherits().size(), is(0));
        assertThat(chainsMap.get("chein").excludes().size(), is(0));
        assertThat(chainsMap.get("chein").phases().size(), is(0));
    }

    @Test
    public void testSchemaMappingConfig() {
        assertTrue(schemamappingConfig.fieldmapping().isEmpty());
    }

    @Test
    public void testQrStartConfig() {
        QrStartConfig.Jvm jvm = qrStartConfig.jvm();
        assertTrue(jvm.server());
        assertTrue(jvm.verbosegc());
        assertEquals("-XX:+UseG1GC -XX:MaxTenuringThreshold=15", jvm.gcopts());
        assertEquals(1536, jvm.minHeapsize());
        assertEquals(1536, jvm.heapsize());
        assertEquals(512, jvm.stacksize());
        assertTrue(qrStartConfig.ulimitv().isEmpty());
        assertEquals(0, jvm.compressedClassSpaceSize());
    }

}
