// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SchemaTester {

    private static final String vespaHosts =
            "<?xml version='1.0' encoding='utf-8' ?>" +
            "<hosts>  " +
            "  <host name='foo'>" +
            "    <alias>node0</alias>" +
            "  </host>" +
            "</hosts>";

    private String createVespaServices(List<String> sds, String mode) {
        List<DocType> nameAndModes = new ArrayList<>(sds.size());
        for (String sd : sds) {
            nameAndModes.add(DocType.create(sd, mode));
        }
        return createVespaServicesXml(nameAndModes, "");
    }
    private String createVespaServicesXml(List<DocType> nameAndModes, String xmlTuning) {
        StringBuilder retval = new StringBuilder();
        retval.append("" +
                      "<?xml version='1.0' encoding='utf-8' ?>\n" +
                      "<services version='1.0'>\n" +
                      "<admin version='2.0'>\n" +
                      "   <adminserver hostalias='node0' />\n" +
                      "</admin>\n" +
                      "<container version='1.0'>\n" +
                      "   <nodes>\n" +
                      "      <node hostalias='node0'/>\n" +
                      "   </nodes>\n" +
                      "   <search/>\n" +
                      "</container>\n" +
                      "<content version='1.0' id='test'>\n" +
                      "   <redundancy>1</redundancy>\n");
        retval.append(DocType.listToXml(nameAndModes));
        retval.append(
                "    <engine>\n" +
                "      <proton>\n" +
                "        <tuning>\n" +
                "          <searchnode>\n" +
                xmlTuning +
                "          </searchnode>\n" +
                "        </tuning\n>" +
                "      </proton\n>" +
                "    </engine\n>" +
                "    <nodes>\n" +
                "      <node hostalias='node0' distribution-key='0'/>\n" +
                "    </nodes>\n" +
                "  </content>\n" +
                "</services>\n");
        return retval.toString();
    }

    ProtonConfig getProtonCfg(ContentSearchCluster cluster) {
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        cluster.getConfig(pb);
        return new ProtonConfig(pb);
    }

    void assertSingleSD(String mode) {
        List<String> sds = List.of("type1");
        VespaModel model = new VespaModelCreatorWithMockPkg(vespaHosts, createVespaServices(sds, mode),
                                                            generateSchemas("", sds)).create();
        IndexedSearchCluster indexedSearchCluster = (IndexedSearchCluster)model.getSearchClusters().get(0);
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        assertEquals(1, indexedSearchCluster.getDocumentDbs().size());
        String type1Id = "test/search/cluster.test/type1";
        ProtonConfig proton = getProtonCfg(contentSearchCluster);
        assertEquals(1, proton.documentdb().size());
        assertEquals("type1", proton.documentdb(0).inputdoctypename());
        assertEquals(type1Id, proton.documentdb(0).configid());
    }

    VespaModel createModel(List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas("", schemas)).create();
    }

    VespaModel createModelWithRankProfile(String rankProfile, List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas(rankProfile, schemas)).create();
    }

    VespaModel createModel(List<DocType> nameAndModes, String xmlTuning) {
        return createModel(nameAndModes, xmlTuning, null);
    }

    VespaModel createModelWithMode(String mode, List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, mode),
                                                generateSchemas("", schemas)).create();

    }

    VespaModel createModelWithMode(String mode, List<String> schemas, DeployState.Builder builder) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, mode),
                                                generateSchemas("", schemas)).create(builder);
    }

    VespaModel createModel(List<DocType> nameAndModes, String xmlTuning, DeployState.Builder builder) {
        List<String> sds = new ArrayList<>(nameAndModes.size());
        for (DocType nameAndMode : nameAndModes) {
            sds.add(nameAndMode.getType());
        }
        var creator = new VespaModelCreatorWithMockPkg(vespaHosts, createVespaServicesXml(nameAndModes, xmlTuning),
                                                       generateSchemas("", sds));
        return builder != null ? creator.create(builder) : creator.create();
    }

    public static String generateSchema(String name, String field1, String field2, String rankProfile) {
        return "schema " + name + " {" +
               "  document " + name + " {" +
               "    field " + field1 + " type string {\n" +
               "      indexing: index | summary\n" +
               "      summary: dynamic\n" +
               "    }\n" +
               "    field " + field2 + " type int {\n" +
               "      indexing: attribute | summary\n" +
               "      attribute: fast-access\n" +
               "    }\n" +
               "    field " + field2 + "_nfa type int {\n" +
               "      indexing: attribute \n" +
               "    }\n" +
               "  }\n" +
               "  rank-profile staticrank inherits default {" +
               "    first-phase { expression: attribute(" + field2 + ") }" +
               "  }" +
               "  rank-profile summaryfeatures inherits default {" +
               "    first-phase { expression: attribute(" + field2 + ") }\n" +
               "    summary-features: attribute(" + field2 + ")" +
               "  }" +
               "  rank-profile inheritedsummaryfeatures inherits summaryfeatures {" +
               "  }" +
               "  rank-profile rankfeatures {" +
               "    first-phase { expression: attribute(" + field2 + ") }\n" +
               "    rank-features: attribute(" + field2 + ")" +
               "  }" +
               rankProfile +
               "}";
    }

    public static List<String> generateSchemas(String rankProfile, String ... sdNames) {
        return generateSchemas(rankProfile, Arrays.asList(sdNames));
    }

    public static List<String> generateSchemas(String rankProfile, List<String> sdNames) {
        List<String> sds = new ArrayList<>();
        int i = 0;
        for (String sdName : sdNames) {
            sds.add(generateSchema(sdName, "f" + (i + 1), "f" + (i + 2), rankProfile));
            i = i + 2;
        }
        return sds;
    }

    DocumentdbInfoConfig.Documentdb.Rankprofile assertRankProfile(DocumentdbInfoConfig.Documentdb db,
                                                                  int index,
                                                                  String name,
                                                                  boolean hasSummaryFeatures,
                                                                  boolean hasRankFeatures) {
        DocumentdbInfoConfig.Documentdb.Rankprofile rankProfile = db.rankprofile(index);
        assertEquals(name, rankProfile.name());
        assertEquals(hasSummaryFeatures, rankProfile.hasSummaryFeatures());
        assertEquals(hasRankFeatures, rankProfile.hasRankFeatures());
        return rankProfile;
    }

    SchemaInfoConfig.Schema.Rankprofile assertRankProfile(SchemaInfoConfig.Schema schema,
                                                          int index,
                                                          String name,
                                                          boolean hasSummaryFeatures,
                                                          boolean hasRankFeatures) {
        SchemaInfoConfig.Schema.Rankprofile rankProfile = schema.rankprofile(index);
        assertEquals(name, rankProfile.name());
        assertEquals(hasSummaryFeatures, rankProfile.hasSummaryFeatures());
        assertEquals(hasRankFeatures, rankProfile.hasRankFeatures());
        return rankProfile;
    }

    void assertSummaryField(DocumentdbInfoConfig.Documentdb db, int summaryClassIndex, int fieldIndex,
                            String name, String type, boolean dynamic) {
        DocumentdbInfoConfig.Documentdb.Summaryclass.Fields field = db.summaryclass(summaryClassIndex).fields(fieldIndex);
        assertEquals(name, field.name());
        assertEquals(type, field.type());
        assertEquals(dynamic, field.dynamic());
    }

    void assertSummaryField(SchemaInfoConfig.Schema schema, int summaryClassIndex, int fieldIndex,
                            String name, String type, boolean dynamic) {
        SchemaInfoConfig.Schema.Summaryclass.Fields field = schema.summaryclass(summaryClassIndex).fields(fieldIndex);
        assertEquals(name, field.name());
        assertEquals(type, field.type());
        assertEquals(dynamic, field.dynamic());
    }

}
