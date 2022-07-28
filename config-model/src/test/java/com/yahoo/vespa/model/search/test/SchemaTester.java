// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    ProtonConfig getProtonConfig(ContentSearchCluster cluster) {
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        cluster.getConfig(pb);
        return new ProtonConfig(pb);
    }

    void assertSingleSD(String mode) {
        List<String> schemas = List.of("type1");
        VespaModel model = new VespaModelCreatorWithMockPkg(vespaHosts, createVespaServices(schemas, mode),
                                                            generateSchemas("", "", schemas),
                                                            Map.of()).create();
        IndexedSearchCluster indexedSearchCluster = (IndexedSearchCluster)model.getSearchClusters().get(0);
        ContentSearchCluster contentSearchCluster = model.getContentClusters().get("test").getSearch();
        assertEquals(1, indexedSearchCluster.getDocumentDbs().size());
        String type1Id = "test/search/cluster.test/type1";
        ProtonConfig proton = getProtonConfig(contentSearchCluster);
        assertEquals(1, proton.documentdb().size());
        assertEquals("type1", proton.documentdb(0).inputdoctypename());
        assertEquals(type1Id, proton.documentdb(0).configid());
    }

    VespaModel createModel(List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas("", "", schemas),
                                                Map.of()).create();
    }

    VespaModel createModelWithRankProfile(String rankProfile, List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas("", rankProfile, schemas),
                                                Map.of()).create();
    }

    VespaModel createModelWithSchemaContent(String schemaContent, List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas(schemaContent, "", schemas),
                                                Map.of()).create();
    }

    VespaModel createModel(String schemaContent, String rankProfile, List<String> schemas, Map<Path, String> files) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, "index"),
                                                generateSchemas(schemaContent, rankProfile, schemas),
                                                files).create();
    }

    VespaModel createModel(List<DocType> nameAndModes, String xmlTuning) {
        return createModel(nameAndModes, xmlTuning, null);
    }

    VespaModel createModelWithMode(String mode, List<String> schemas) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, mode),
                                                generateSchemas("", "", schemas),
                                                Map.of()).create();

    }

    VespaModel createModelWithMode(String mode, List<String> schemas, DeployState.Builder builder) {
        return new VespaModelCreatorWithMockPkg(vespaHosts,
                                                createVespaServices(schemas, mode),
                                                generateSchemas("", "", schemas),
                                                Map.of()).create(builder);
    }

    VespaModel createModel(List<DocType> nameAndModes, String xmlTuning, DeployState.Builder builder) {
        List<String> schemas = new ArrayList<>(nameAndModes.size());
        for (DocType nameAndMode : nameAndModes)
            schemas.add(nameAndMode.getType());
        var creator = new VespaModelCreatorWithMockPkg(vespaHosts, createVespaServicesXml(nameAndModes, xmlTuning),
                                                       generateSchemas("", "", schemas),
                                                       Map.of());
        return builder != null ? creator.create(builder) : creator.create();
    }

    public static String generateSchema(String name, String field1, String field2, String schemaContent, String rankProfile) {
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
               schemaContent +
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

    public static List<String> generateSchemas(String schemaContent, String rankProfile, String ... schemaNames) {
        return generateSchemas(schemaContent, rankProfile, Arrays.asList(schemaNames));
    }

    public static List<String> generateSchemas(String schemaContent, String rankProfile, List<String> schemaNames) {
        List<String> schemas = new ArrayList<>();
        int i = 0;
        for (String sdName : schemaNames) {
            schemas.add(generateSchema(sdName, "f" + (i + 1), "f" + (i + 2), schemaContent, rankProfile));
            i = i + 2;
        }
        return schemas;
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

    void assertSummaryField(SchemaInfoConfig.Schema schema, int summaryClassIndex, int fieldIndex,
                            String name, String type, boolean dynamic) {
        SchemaInfoConfig.Schema.Summaryclass.Fields field = schema.summaryclass(summaryClassIndex).fields(fieldIndex);
        assertEquals(name, field.name());
        assertEquals(type, field.type());
        assertEquals(dynamic, field.dynamic());
    }

}
