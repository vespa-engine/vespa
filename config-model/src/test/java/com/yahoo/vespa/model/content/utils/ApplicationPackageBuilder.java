// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for building an application package with content clusters (used for testing only).
 *
 * @author geirst
 */
public class ApplicationPackageBuilder {

    private List<ContentClusterBuilder> contentClusters = new ArrayList<>();
    private List<String> searchDefinitions = new ArrayList<>();

    public ApplicationPackageBuilder() {
    }

    public ApplicationPackageBuilder addCluster(ContentClusterBuilder contentCluster) {
        contentClusters.add(contentCluster);
        return this;
    }

    public ApplicationPackageBuilder addSearchDefinition(String searchDefinition) {
        searchDefinitions.add(searchDefinition);
        return this;
    }

    public VespaModelCreatorWithMockPkg buildCreator() {
        return new VespaModelCreatorWithMockPkg(null, getServices(), searchDefinitions);
    }

    private String getServices() {
        return "<services version='1.0'>\n" +
                contentClusters.stream().map(cluster -> cluster.getXml() + "\n").reduce("", (acc, element) -> acc + element) +
                "</services>";
    }

}

