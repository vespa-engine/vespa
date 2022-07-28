// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.13
 */
public class ContainerIncludeTest {

    @Test
    void include() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude/");
        VespaModel model = creator.create();

        assertEquals(1, model.getContainerClusters().size());
        ContainerCluster cluster = model.getContainerClusters().values().iterator().next();

        assertNotNull(cluster.getSearchChains());

        Map<String, SearchChain> searchChainMap = new HashMap<>();
        for (SearchChain searchChain : cluster.getSearchChains().allChains().allComponents()) {
            searchChainMap.put(searchChain.getId().stringValue(), searchChain);
        }
        assertNotNull(searchChainMap.get("searchchain1"));
        assertEquals(1, searchChainMap.get("searchchain1").getInnerComponents().size());
        assertEquals("com.yahoo.Searcher1", searchChainMap.get("searchchain1").getInnerComponents().iterator().next().getComponentId().stringValue());

        assertNotNull(searchChainMap.get("searchchain2"));
        assertEquals(1, searchChainMap.get("searchchain2").getInnerComponents().size());
        assertEquals("com.yahoo.Searcher2", searchChainMap.get("searchchain2").getInnerComponents().iterator().next().getComponentId().stringValue());

        assertNotNull(searchChainMap.get("searchchain3"));
        assertEquals(1, searchChainMap.get("searchchain3").getInnerComponents().size());
        assertEquals("com.yahoo.Searcher3", searchChainMap.get("searchchain3").getInnerComponents().iterator().next().getComponentId().stringValue());

        assertNotNull(searchChainMap.get("searchchain4"));
        assertEquals(1, searchChainMap.get("searchchain4").getInnerComponents().size());
        assertEquals("com.yahoo.Searcher4", searchChainMap.get("searchchain4").getInnerComponents().iterator().next().getComponentId().stringValue());


        assertNotNull(cluster.getDocprocChains());

        Map<String, DocprocChain> docprocChainMap = new HashMap<>();
        for (DocprocChain docprocChain : cluster.getDocprocChains().allChains().allComponents()) {
            docprocChainMap.put(docprocChain.getId().stringValue(), docprocChain);
        }

        assertNotNull(docprocChainMap.get("docprocchain1"));
        assertEquals(1, docprocChainMap.get("docprocchain1").getInnerComponents().size());
        assertEquals("com.yahoo.DocumentProcessor1", docprocChainMap.get("docprocchain1").getInnerComponents().iterator().next().getComponentId().stringValue());

        assertNotNull(docprocChainMap.get("docprocchain2"));
        assertEquals(1, docprocChainMap.get("docprocchain2").getInnerComponents().size());
        assertEquals("com.yahoo.DocumentProcessor2", docprocChainMap.get("docprocchain2").getInnerComponents().iterator().next().getComponentId().stringValue());


        assertNotNull(cluster.getProcessingChains());

        Map<String, ProcessingChain> processingChainMap = new HashMap<>();
        for (ProcessingChain processingChain : cluster.getProcessingChains().allChains().allComponents()) {
            processingChainMap.put(processingChain.getId().stringValue(), processingChain);
        }

        assertNotNull(processingChainMap.get("processingchain1"));
        assertEquals(1, processingChainMap.get("processingchain1").getInnerComponents().size());
        assertEquals("com.yahoo.Processor1", processingChainMap.get("processingchain1").getInnerComponents().iterator().next().getComponentId().stringValue());

        assertNotNull(processingChainMap.get("processingchain2"));
        assertEquals(1, processingChainMap.get("processingchain2").getInnerComponents().size());
        assertEquals("com.yahoo.Processor2", processingChainMap.get("processingchain2").getInnerComponents().iterator().next().getComponentId().stringValue());
    }

    @Test
    void includeNonExistent() {
        assertThrows(IllegalArgumentException.class, () -> {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude2/");
            creator.create();
        });
    }

    @Test
    void includeAbsolutePath() {
        assertThrows(IllegalArgumentException.class, () -> {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude3/");
            creator.create();
        });
    }

    @Test
    void includeNonDirectory() {
        assertThrows(IllegalArgumentException.class, () -> {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude4/");
            creator.create();
        });
    }

    @Test
    void include_file_with_wrong_root_element_name() {
        assertThrows(IllegalArgumentException.class, () -> {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude5/");
            creator.create();
        });
    }

    @Test
    void include_empty_directory() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude6/");
        creator.create();
    }

    @Test
    void included_file_with_xml_schema_violation() {
        try {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/include_xml_error/");
            creator.create(true);
            fail("Expected exception due to xml schema violation ('zearcer')");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid XML according to XML schema"));
            assertTrue(e.getMessage().contains("zearcer"));
        }
    }

}
