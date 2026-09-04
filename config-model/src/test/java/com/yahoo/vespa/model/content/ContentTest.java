// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentTest {

    private static final String HOSTS = "<hosts><host name='mockhost'><alias>node1</alias></host></hosts>";

    private static final String SERVICES = """
            <services version='1.0'>
              <container id='default' version='1.0'>
                <document-processing>
                  <chain id='indexing-child' inherits='indexing'/>
                  <chain id='unrelated'/>
                </document-processing>
                <nodes><node hostalias='node1'/></nodes>
              </container>
              <container id='other' version='1.0'>
                <http><server id='other' port='8081'/></http>
                <document-processing>
                  <chain id='elsewhere' inherits='indexing'/>
                </document-processing>
                <nodes><node hostalias='node1'/></nodes>
              </container>
              <content id='content' version='1.0'>
                <redundancy>1</redundancy>
                <documents><document type='product' mode='index'/></documents>
                <nodes><node hostalias='node1' distribution-key='0'/></nodes>
              </content>
            </services>
            """;

    private final VespaModel model = new VespaModelCreatorWithMockPkg(HOSTS, SERVICES,
            List.of("schema product { document product { field f type string { indexing: summary } } }")).create();
    private final ContentCluster content = model.getContentClusters().get("content");
    private final ApplicationContainerCluster indexer = model.getContainerClusters().get("default");

    @Test
    void chainInheritingIndexingInTheIndexingClusterIsBound() {
        Content.bindIndexingChain(content, indexer, chain(indexer, "indexing-child"));
        assertEquals("indexing-child", content.getSearch().getIndexingDocproc().getChainName());
    }

    @Test
    void builtInIndexingChainIsBound() {
        Content.bindIndexingChain(content, indexer, chain(indexer, "indexing"));
        assertEquals("indexing", content.getSearch().getIndexingDocproc().getChainName());
    }

    @Test
    void clusterBindsOneChainOnly() {
        Content.bindIndexingChain(content, indexer, chain(indexer, "indexing-child"));
        Content.bindIndexingChain(content, indexer, chain(indexer, "indexing-child")); // the same chain again is fine
        Throwable thrown = assertThrows(IllegalArgumentException.class,
                                        () -> Content.bindIndexingChain(content, indexer, chain(indexer, "indexing")));
        assertEquals("content cluster 'content' already binds indexing chain 'indexing-child', and cannot also bind 'indexing'",
                     thrown.getMessage());
    }

    @Test
    void chainNotInheritingIndexingIsRejected() {
        Throwable thrown = assertThrows(IllegalArgumentException.class,
                                        () -> Content.bindIndexingChain(content, indexer, chain(indexer, "unrelated")));
        assertEquals("Docproc chain 'unrelated' must inherit from the 'indexing' chain", thrown.getMessage());
    }

    @Test
    void chainInAnotherContainerClusterIsRejected() {
        ApplicationContainerCluster other = model.getContainerClusters().get("other");
        Throwable thrown = assertThrows(IllegalArgumentException.class,
                                        () -> Content.bindIndexingChain(content, other, chain(other, "elsewhere")));
        assertEquals("content cluster 'content' indexes through container cluster 'default', but indexing chain " +
                     "'elsewhere' is in container cluster 'other'", thrown.getMessage());
    }


    private static DocprocChain chain(ApplicationContainerCluster cluster, String id) {
        DocprocChain chain = cluster.getDocprocChains().allChains().getComponent(id);
        assertTrue(chain != null, "test setup: chain '" + id + "' must exist in '" + cluster.getName() + "'");
        return chain;
    }

}
