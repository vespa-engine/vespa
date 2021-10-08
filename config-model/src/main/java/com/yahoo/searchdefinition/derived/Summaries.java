// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.config.search.SummaryConfig;
import java.util.List;

/**
 * A list of derived summaries
 *
 * @author  bratseth
 */
public class Summaries extends Derived implements SummaryConfig.Producer {

    private List<SummaryClass> summaries=new java.util.ArrayList<>(1);

    public Summaries(Search search, DeployLogger deployLogger) {
        // Make sure the default is first
        summaries.add(new SummaryClass(search,search.getSummary("default"), deployLogger));
        for (DocumentSummary summary : search.getSummaries().values()) {
            if (!summary.getName().equals("default"))
                summaries.add(new SummaryClass(search,summary, deployLogger));
        }
    }

    @Override
    protected String getDerivedName() { return "summary"; }

    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        builder.defaultsummaryid(summaries.isEmpty() ? -1 : summaries.get(0).hashCode());
        for (SummaryClass summaryClass : summaries) {
            builder.classes(summaryClass.getSummaryClassConfig());
        }
    }    
}
