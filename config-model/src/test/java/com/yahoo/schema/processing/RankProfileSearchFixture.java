// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.application.api.ApplicationPackage;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import ai.vespa.rankingexpression.importer.lightgbm.LightGBMImporter;
import ai.vespa.rankingexpression.importer.xgboost.XGBoostImporter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Helper class for setting up and asserting over a Search instance with a rank profile given literally
 * in the search definition language.
 *
 * @author geirst
 */
class RankProfileSearchFixture {

    private final ImmutableList<MlModelImporter> importers = ImmutableList.of(new TensorFlowImporter(),
                                                                              new OnnxImporter(),
                                                                              new LightGBMImporter(),
                                                                              new XGBoostImporter());
    private final RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
    private final QueryProfileRegistry queryProfileRegistry;
    private final Schema schema;
    private final Map<String, RankProfile> compiledRankProfiles = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public RankProfileRegistry getRankProfileRegistry() {
        return rankProfileRegistry;
    }

    public QueryProfileRegistry getQueryProfileRegistry() {
        return queryProfileRegistry;
    }

    RankProfileSearchFixture(String rankProfiles) throws ParseException {
        this(MockApplicationPackage.createEmpty(), new QueryProfileRegistry(), rankProfiles);
    }

    RankProfileSearchFixture(ApplicationPackage applicationpackage, QueryProfileRegistry queryProfileRegistry,
                             String rankProfiles) throws ParseException {
        this(applicationpackage, queryProfileRegistry, rankProfiles, null, null);
    }

    RankProfileSearchFixture(ApplicationPackage applicationpackage, QueryProfileRegistry queryProfileRegistry,
                             String rankProfiles, String constant, String field)
            throws ParseException {
        this.queryProfileRegistry = queryProfileRegistry;
        ApplicationBuilder builder = new ApplicationBuilder(applicationpackage, new MockFileRegistry(), new BaseDeployLogger(), new TestProperties(), rankProfileRegistry, queryProfileRegistry);
        String sdContent = "search test {\n" +
                           "  " + (constant != null ? constant : "") + "\n" +
                           "  document test {\n" +
                           "    " + (field != null ? field : "") + "\n" +
                           "  }\n" +
                           rankProfiles +
                           "\n" +
                           "}";
        builder.addSchema(sdContent);
        builder.build(true);
        schema = builder.getSchema();
    }

    public void assertFirstPhaseExpression(String expExpression, String rankProfile) {
        assertEquals(expExpression, compiledRankProfile(rankProfile).getFirstPhaseRanking().getRoot().toString());
    }

    public void assertSecondPhaseExpression(String expExpression, String rankProfile) {
        assertEquals(expExpression, compiledRankProfile(rankProfile).getSecondPhaseRanking().getRoot().toString());
    }

    public void assertRankProperty(String expValue, String name, String rankProfile) {
        List<RankProfile.RankProperty> rankPropertyList = compiledRankProfile(rankProfile).getRankPropertyMap().get(name);
        assertEquals(1, rankPropertyList.size());
        assertEquals(expValue, rankPropertyList.get(0).getValue());
    }

    public void assertFunction(String expexctedExpression, String functionName, String rankProfile) {
        assertEquals(expexctedExpression,
                     compiledRankProfile(rankProfile).getFunctions().get(functionName).function().getBody().getRoot().toString());
    }

    public RankProfile compileRankProfile(String rankProfile) {
        return compileRankProfile(rankProfile, Path.fromString("nonexistinng"));
    }

    public RankProfile compileRankProfile(String rankProfile, Path applicationDir) {
        RankProfile compiled = rankProfileRegistry.get(schema, rankProfile)
                                                  .compile(queryProfileRegistry,
                                                           new ImportedMlModels(applicationDir.toFile(), executor, importers));
        compiledRankProfiles.put(rankProfile, compiled);
        return compiled;
    }

    /** Returns the given uncompiled profile */
    public RankProfile rankProfile(String rankProfile) {
        return rankProfileRegistry.get(schema, rankProfile);
    }

    /** Returns the given compiled profile, or null if not compiled yet or not present at all */
    public RankProfile compiledRankProfile(String rankProfile) {
        return compiledRankProfiles.get(rankProfile);
    }

    public Schema search() { return schema; }

}
