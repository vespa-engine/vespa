// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.config.search.SummaryConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of derived summaries
 *
 * @author  bratseth
 */
public class Summaries extends Derived implements SummaryConfig.Producer {

    private final boolean useV8GeoPositions;
    private final List<SummaryClass> summaries;

    public Summaries(Schema schema, DeployLogger deployLogger, ModelContext.FeatureFlags featureFlags) {
        super();
        this.useV8GeoPositions = featureFlags.useV8GeoPositions();

        // Make sure the default is first
        List<SummaryClass> summaries = new ArrayList<>();
        summaries.add(new SummaryClass(schema, schema.getSummary("default"), deployLogger));
        for (DocumentSummary summary : schema.getSummaries().values()) {
            if (!summary.getName().equals("default"))
                summaries.add(new SummaryClass(schema, summary, deployLogger));
        }
        this.summaries = List.copyOf(summaries);
    }

    public List<SummaryClass> asList() { return summaries; }

    @Override
    protected String getDerivedName() { return "summary"; }

    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        builder.defaultsummaryid(summaries.isEmpty() ? -1 : summaries.get(0).hashCode());
        builder.usev8geopositions(useV8GeoPositions);
        for (SummaryClass summaryClass : summaries) {
            builder.classes(summaryClass.getSummaryClassConfig());
        }
    }    

}
