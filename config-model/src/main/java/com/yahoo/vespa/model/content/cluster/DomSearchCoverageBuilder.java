// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.content.SearchCoverage;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * @author Simon Thoresen Hult
 */
public class DomSearchCoverageBuilder {

    public static SearchCoverage build(ModelElement contentXml) {
        SearchCoverage.Builder builder = new SearchCoverage.Builder();
        ModelElement searchElement = contentXml.getChild("search");
        if (searchElement == null) {
            return builder.build();
        }
        ModelElement coverageElement = searchElement.getChild("coverage");
        if (coverageElement == null) {
            return builder.build();
        }
        builder.setMinimum(coverageElement.childAsDouble("minimum"));
        builder.setMinWaitAfterCoverageFactor(coverageElement.childAsDouble("min-wait-after-coverage-factor"));
        builder.setMaxWaitAfterCoverageFactor(coverageElement.childAsDouble("max-wait-after-coverage-factor"));
        return builder.build();
    }
}
