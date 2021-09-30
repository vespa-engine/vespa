// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.processing.ProcessingChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 * @since 5.1.13
 */
public class ContainerIncludeTest {

    @Test
    public void include() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude/");
        VespaModel model = creator.create();

        assertThat(model.getContainerClusters().size(), is(1));
        ContainerCluster cluster = model.getContainerClusters().values().iterator().next();

        assertThat(cluster.getSearchChains(), notNullValue());

        Map<String, SearchChain> searchChainMap = new HashMap<>();
        for (SearchChain searchChain : cluster.getSearchChains().allChains().allComponents()) {
            searchChainMap.put(searchChain.getId().stringValue(), searchChain);
        }
        assertThat(searchChainMap.get("searchchain1"), notNullValue());
        assertThat(searchChainMap.get("searchchain1").getInnerComponents().size(), is(1));
        assertThat(searchChainMap.get("searchchain1").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Searcher1"));

        assertThat(searchChainMap.get("searchchain2"), notNullValue());
        assertThat(searchChainMap.get("searchchain2").getInnerComponents().size(), is(1));
        assertThat(searchChainMap.get("searchchain2").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Searcher2"));

        assertThat(searchChainMap.get("searchchain3"), notNullValue());
        assertThat(searchChainMap.get("searchchain3").getInnerComponents().size(), is(1));
        assertThat(searchChainMap.get("searchchain3").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Searcher3"));

        assertThat(searchChainMap.get("searchchain4"), notNullValue());
        assertThat(searchChainMap.get("searchchain4").getInnerComponents().size(), is(1));
        assertThat(searchChainMap.get("searchchain4").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Searcher4"));


        assertThat(cluster.getDocprocChains(), notNullValue());

        Map<String, DocprocChain> docprocChainMap = new HashMap<>();
        for (DocprocChain docprocChain : cluster.getDocprocChains().allChains().allComponents()) {
            docprocChainMap.put(docprocChain.getId().stringValue(), docprocChain);
        }

        assertThat(docprocChainMap.get("docprocchain1"), notNullValue());
        assertThat(docprocChainMap.get("docprocchain1").getInnerComponents().size(), is(1));
        assertThat(docprocChainMap.get("docprocchain1").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.DocumentProcessor1"));

        assertThat(docprocChainMap.get("docprocchain2"), notNullValue());
        assertThat(docprocChainMap.get("docprocchain2").getInnerComponents().size(), is(1));
        assertThat(docprocChainMap.get("docprocchain2").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.DocumentProcessor2"));


        assertThat(cluster.getProcessingChains(), notNullValue());

        Map<String, ProcessingChain> processingChainMap = new HashMap<>();
        for (ProcessingChain processingChain : cluster.getProcessingChains().allChains().allComponents()) {
            processingChainMap.put(processingChain.getId().stringValue(), processingChain);
        }

        assertThat(processingChainMap.get("processingchain1"), notNullValue());
        assertThat(processingChainMap.get("processingchain1").getInnerComponents().size(), is(1));
        assertThat(processingChainMap.get("processingchain1").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Processor1"));

        assertThat(processingChainMap.get("processingchain2"), notNullValue());
        assertThat(processingChainMap.get("processingchain2").getInnerComponents().size(), is(1));
        assertThat(processingChainMap.get("processingchain2").getInnerComponents().iterator().next().getComponentId().stringValue(), is("com.yahoo.Processor2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void includeNonExistent() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude2/");
        creator.create();
    }

    @Test(expected = IllegalArgumentException.class)
    public void includeAbsolutePath() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude3/");
        creator.create();
    }

    @Test(expected = IllegalArgumentException.class)
    public void includeNonDirectory() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude4/");
        creator.create();
    }

    @Test(expected = IllegalArgumentException.class)
    public void include_file_with_wrong_root_element_name() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude5/");
        creator.create();
    }

    @Test
    public void include_empty_directory() {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/containerinclude6/");
        creator.create();
    }

    @Test
    public void included_file_with_xml_schema_violation() {
        try {
            VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg("src/test/cfg/container/data/include_xml_error/");
            creator.create(true);
            fail("Expected exception due to xml schema violation ('zearcer')");
         } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Invalid XML according to XML schema"));
            assertThat(e.getMessage(), containsString("zearcer"));
        }
    }

}
