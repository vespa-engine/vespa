// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.HostPorts;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 * @author gjoranv
 */
public class DocprocBuilderTest extends DomBuilderTest {

    private ApplicationContainerCluster cluster;
    private ContainerMbusConfig containerMbusConfig;
    private ComponentsConfig componentsConfig;
    private ChainsConfig chainsConfig;
    private SchemamappingConfig schemamappingConfig;
    private QrStartConfig qrStartConfig;

    @BeforeEach
    public void setupCluster() {
        ContainerModel model = new ContainerModelBuilder(false, Networking.disable).build(DeployState.createTestState(), null, null, root, servicesXml());
        cluster = (ApplicationContainerCluster) model.getCluster();
        cluster.getDocproc().getChains().addServersAndClientsForChains();
        root.freezeModelTopology();

        containerMbusConfig = root.getConfig(ContainerMbusConfig.class, cluster.getContainers().get(0).getConfigId());
        componentsConfig = root.getConfig(ComponentsConfig.class, cluster.getConfigId());
        chainsConfig = root.getConfig(ChainsConfig.class,
                cluster.getConfigId() + "/component/com.yahoo.docproc.jdisc.DocumentProcessingHandler");

        schemamappingConfig = root.getConfig(SchemamappingConfig.class, cluster.getContainers().get(0).getConfigId());
        qrStartConfig = root.getConfig(QrStartConfig.class, cluster.getConfigId());
    }

    private Element servicesXml() {
        return parse(
                "<container id='banan' version='1.0'>",
                "  <nodes>",
                "    <node hostalias='mockhost' baseport='1500' />",
                "  </nodes>",
                "  <document-processing maxmessagesinqueue='300' maxqueuewait='200'>",
                "    <documentprocessor id='docproc1' class='com.yahoo.Docproc1' bundle='docproc1bundle'/>",
                "    <chain id='chein'>",
                "      <documentprocessor id='docproc2'/>",
                "    </chain>",
                "  </document-processing>",
                "</container>");
    }

    @Test
    void testDocprocCluster() {
        assertEquals("banan", cluster.getName());
        List<ApplicationContainer> services = cluster.getContainers();
        assertEquals(1, services.size());
        ApplicationContainer service = services.get(0);
        assertNotNull(service);

        Map<String, DocprocChain> chains = new HashMap<>();
        for (DocprocChain chain : cluster.getDocprocChains().allChains().allComponents()) {
            chains.put(chain.getId().stringValue(), chain);
        }
        assertEquals(1, chains.size());

        DocprocChain chain = chains.get("chein");
        assertEquals("chein", chain.getId().stringValue());
        assertEquals(1, chain.getInnerComponents().size());
        DocumentProcessor processor = chain.getInnerComponents().iterator().next();
        assertEquals("docproc2", processor.getComponentId().stringValue());
    }

    @Test
    void testContainerMbusConfig() {
        assertTrue(containerMbusConfig.port() >= HostPorts.BASE_PORT);
        assertEquals(300, containerMbusConfig.maxpendingcount());
    }

    @Test
    void testComponentsConfig() {
        Map<String, ComponentsConfig.Components> components = new HashMap<>();
        for (ComponentsConfig.Components component : componentsConfig.components()) {
            System.err.println(component.id());
            components.put(component.id(), component);
        }

        ComponentsConfig.Components docprocHandler = components.get("com.yahoo.docproc.jdisc.DocumentProcessingHandler");
        assertEquals("com.yahoo.docproc.jdisc.DocumentProcessingHandler", docprocHandler.id());
        assertEquals("banan/component/com.yahoo.docproc.jdisc.DocumentProcessingHandler", docprocHandler.configId());
        assertEquals("com.yahoo.docproc.jdisc.DocumentProcessingHandler", docprocHandler.classId());
        assertEquals("container-search-and-docproc", docprocHandler.bundle());

        ComponentsConfig.Components docproc1 = components.get("docproc1");
        assertEquals("docproc1", docproc1.id());
        assertEquals("banan/docprocchains/component/docproc1", docproc1.configId());
        assertEquals("com.yahoo.Docproc1", docproc1.classId());
        assertEquals("docproc1bundle", docproc1.bundle());

        ComponentsConfig.Components docproc2 = components.get("docproc2@chein");
        assertEquals("docproc2@chein", docproc2.id());
        assertEquals("banan/docprocchains/chain/chein/component/docproc2", docproc2.configId());
        assertEquals("docproc2", docproc2.classId());
        assertEquals("docproc2", docproc2.bundle());
        /*
                ComponentsConfig.Components health = components.get("com.yahoo.container.jdisc.state.StateHandler");
                assertEquals("com.yahoo.container.jdisc.state.StateHandler", health.id()));
                assertEquals("com.yahoo.container.jdisc.state.StateHandler", health.classId());
                assertEquals("com.yahoo.container.jdisc.state.StateHandler", health.bundle()));
        */
        ComponentsConfig.Components sourceClient = components.get("source@MbusClient");
        assertNotNull(sourceClient);
        assertEquals("com.yahoo.container.jdisc.messagebus.MbusClientProvider", sourceClient.classId());
        assertEquals("com.yahoo.container.jdisc.messagebus.MbusClientProvider", sourceClient.bundle());

        ComponentsConfig.Components intermediateClient = components.get("chain.chein@MbusClient");
        assertNotNull(intermediateClient);
        assertEquals("com.yahoo.container.jdisc.messagebus.MbusClientProvider", intermediateClient.classId());
        assertEquals("com.yahoo.container.jdisc.messagebus.MbusClientProvider", intermediateClient.bundle());
    }

    @Test
    void testChainsConfig() {
        Map<String, ChainsConfig.Components> components = new HashMap<>();
        for (ChainsConfig.Components component : chainsConfig.components()) {
            components.put(component.id(), component);
        }

        assertEquals(2, components.size());

        ChainsConfig.Components docproc1 = components.get("docproc1");
        assertEquals("docproc1", docproc1.id());
        assertTrue(docproc1.dependencies().provides().isEmpty());
        assertTrue(docproc1.dependencies().before().isEmpty());
        assertTrue(docproc1.dependencies().after().isEmpty());

        ChainsConfig.Components docproc2 = components.get("docproc2@chein");
        assertEquals("docproc2@chein", docproc2.id());
        assertTrue(docproc2.dependencies().provides().isEmpty());
        assertTrue(docproc2.dependencies().before().isEmpty());
        assertTrue(docproc2.dependencies().after().isEmpty());

        Map<String, ChainsConfig.Chains> chainsMap = new HashMap<>();
        for (ChainsConfig.Chains chain : chainsConfig.chains()) {
            chainsMap.put(chain.id(), chain);
        }

        assertEquals(1, chainsMap.size());
        assertEquals("chein", chainsMap.get("chein").id());
        assertEquals(1, chainsMap.get("chein").components().size());
        assertEquals("docproc2@chein", chainsMap.get("chein").components(0));
        assertTrue(chainsMap.get("chein").inherits().isEmpty());
        assertTrue(chainsMap.get("chein").excludes().isEmpty());
        assertTrue(chainsMap.get("chein").phases().isEmpty());
    }

    @Test
    void testSchemaMappingConfig() {
        assertTrue(schemamappingConfig.fieldmapping().isEmpty());
    }

    @Test
    void testQrStartConfig() {
        QrStartConfig.Jvm jvm = qrStartConfig.jvm();
        assertTrue(jvm.server());
        assertTrue(jvm.verbosegc());
        assertEquals("-XX:+UseG1GC -XX:MaxTenuringThreshold=15", jvm.gcopts());
        assertEquals(1536, jvm.minHeapsize());
        assertEquals(1536, jvm.heapsize());
        assertEquals(512, jvm.stacksize());
        assertEquals(0, jvm.compressedClassSpaceSize());
    }

}
