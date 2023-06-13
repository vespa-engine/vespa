// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.ranking.RankProfilesEvaluator;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.search.DocumentDatabase;

public class RankProfilesEvaluatorComponent
    extends Component<AnyConfigProducer, ComponentModel>
    implements
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        RankingExpressionsConfig.Producer,
        OnnxModelsConfig.Producer
{
    private final DocumentDatabase ddb;

    public RankProfilesEvaluatorComponent(DocumentDatabase db) {
        super(toComponentModel(db.getSchemaName()));
        ddb = db;
    }

    private static ComponentModel toComponentModel(String p) {
        String myComponentId = "ranking-expression-evaluator." + p;
        return new ComponentModel(myComponentId,
                                  RankProfilesEvaluator.class.getName(),
                                  PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) { ddb.getConfig(builder); }

    @Override
    public void getConfig(RankingExpressionsConfig.Builder builder) { ddb.getConfig(builder); }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) { ddb.getConfig(builder); }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) { ddb.getConfig(builder); }
}
