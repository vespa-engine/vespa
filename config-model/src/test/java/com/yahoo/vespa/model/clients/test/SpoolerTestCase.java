// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients.test;

import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.CommonVespaModelSetup;
import com.yahoo.vespaclient.config.FeederConfig;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Thomas Gundersen
 */
public class SpoolerTestCase {

    @Test
    public void testSimple() throws Exception {
        VespaModel model = createModel("src/test/cfg/clients/simpleconfig.v2.docprocv3");

        SpoolerConfig.Builder builder = new SpoolerConfig.Builder();
        SpoolerConfig.Parsers.Builder parserBuilder1 = createParserBuilder("com.yahoo.vespaspooler.XMLFileParser");
        SpoolerConfig.Parsers.Builder parserBuilder2 = createParserBuilder("com.yahoo.vespaspooler.MusicFileParser");
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("route", "default");
        parameters.put("foo", "bar");
        SpoolerConfig.Parsers.Builder parserBuilder3 = createParserBuilder("com.yahoo.vespaspooler.MusicParser",
                parameters);
        builder.maxfailuresize(100000).
                maxfatalfailuresize(1000000).
                threads(5).
                parsers(Arrays.asList(parserBuilder1, parserBuilder2, parserBuilder3));
        final int spoolerIndex = 0;
        testSpoolerConfigBuilder(model, spoolerIndex, builder);

        FeederConfig.Builder feederBuilder = new FeederConfig.Builder().
                abortondocumenterror(false).
                maxpendingbytes(8000).
                tracelevel(7);
        testFeederConfigBuilder(model, spoolerIndex, feederBuilder);
    }

    @Test
    public void testAdvanced() throws Exception {
        VespaModel model = createModel("src/test/cfg/clients/advancedconfig.v2");

        SpoolerConfig.Builder builder = new SpoolerConfig.Builder();
        SpoolerConfig.Parsers.Builder parserBuilder1 = createParserBuilder("com.yahoo.vespaspooler.XMLFileParser");
        SpoolerConfig.Parsers.Builder parserBuilder2 = createParserBuilder("com.yahoo.vespaspooler.MusicFileParser");
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("route", "default");
        SpoolerConfig.Parsers.Builder parserBuilder3 = createParserBuilder("com.yahoo.vespaspooler.MusicParser",
                parameters);
        builder.keepsuccess(true).
               parsers(Arrays.asList(parserBuilder1, parserBuilder2, parserBuilder3));
        int spoolerIndex = 0;
        testSpoolerConfigBuilder(model, spoolerIndex, builder);

        FeederConfig.Builder feederBuilder = new FeederConfig.Builder().
                abortondocumenterror(false).
                maxpendingbytes(8000).
                timeout(90.0);
        testFeederConfigBuilder(model, spoolerIndex, feederBuilder);

        builder = new SpoolerConfig.Builder();
        parameters = new LinkedHashMap<>();
        parameters.put("route", "othercluster");

        parserBuilder1 = createParserBuilder("com.yahoo.vespaspooler.MusicParser",
                parameters);
        builder.keepsuccess(false).
                parsers(parserBuilder1);
        spoolerIndex = 1;
        testSpoolerConfigBuilder(model, spoolerIndex, builder);

        feederBuilder = new FeederConfig.Builder().
                abortondocumenterror(false).
                maxpendingbytes(4000).
                timeout(50.0);
        testFeederConfigBuilder(model, spoolerIndex, feederBuilder);

        builder = new SpoolerConfig.Builder();
        parserBuilder1 = new SpoolerConfig.Parsers.Builder();
        parserBuilder1.classname("com.yahoo.vespaspooler.MusicFileParser");
        builder.parsers(parserBuilder1);
        String id = "plan9";
        testSpoolerConfigBuilder(model, "clients/spoolers/" + id, builder);

        feederBuilder = new FeederConfig.Builder().
                route("myroute").
                mbusport(14064).
                timeout(90.0);
        testFeederConfigBuilder(model, "clients/spoolers/" + id, feederBuilder);
    }

    SpoolerConfig.Parsers.Builder createParserBuilder(String className) {
        return createParserBuilder(className, new HashMap<String, String>());
    }

    SpoolerConfig.Parsers.Builder createParserBuilder(String className, Map<String, String> parameters) {
        SpoolerConfig.Parsers.Builder builder = new SpoolerConfig.Parsers.Builder();
        builder.classname(className);
        if (!parameters.isEmpty()) {
            List<SpoolerConfig.Parsers.Parameters.Builder> parametersBuilders = new ArrayList<>();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                final SpoolerConfig.Parsers.Parameters.Builder parametersBuilder = new SpoolerConfig.Parsers.Parameters.Builder();
                parametersBuilder.key(entry.getKey()).value(entry.getValue());
                parametersBuilders.add(parametersBuilder);
            }
            builder.parameters(parametersBuilders);
        }
        return builder;
    }

    private void testSpoolerConfigBuilder(VespaModel model, int index, SpoolerConfig.Builder expected) throws Exception {
        testSpoolerConfigBuilder(model, "clients/spoolers/spooler." + index, expected);
    }

    private void testSpoolerConfigBuilder(VespaModel model, String id, SpoolerConfig.Builder expected) throws Exception {
        SpoolerConfig.Builder b = new SpoolerConfig.Builder();
        model.getConfig(b, id);
        SpoolerConfig config = new SpoolerConfig(b);
        SpoolerConfig expectedConfig = new SpoolerConfig(expected);
        assertEquals(expectedConfig, config);
    }

    private void testFeederConfigBuilder(VespaModel model, int index, FeederConfig.Builder expected) throws Exception {
        testFeederConfigBuilder(model, "clients/spoolers/spooler." + index, expected);
    }

    private void testFeederConfigBuilder(VespaModel model, String id, FeederConfig.Builder expected) throws Exception {
        FeederConfig.Builder b = new FeederConfig.Builder();
        model.getConfig(b, id);
        FeederConfig config = new FeederConfig(b);
        FeederConfig expectedConfig = new FeederConfig(expected);
        assertEquals(expectedConfig, config);
    }

    private VespaModel createModel(String configFile) throws Exception {
        return CommonVespaModelSetup.createVespaModelWithMusic(configFile);
    }

}
