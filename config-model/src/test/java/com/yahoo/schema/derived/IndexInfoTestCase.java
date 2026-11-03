package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestDeployState;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.utils.ApplicationPackageBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.content.utils.SchemaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexInfoTestCase {
    private static final String F = "f";
    @Test
    void testThatIndexingEnablesNormalizing() {
        var cmds = createIndexCmds(false);
        assertEquals(8, cmds.size());
        assertEquals(1, cmds.stream().filter(c -> c.indexname().equals(F) && c.command().equals("normalize")).count());
    }
    @Test
    void testThatStreamingDisablesNormalizing() {
        var cmds = createIndexCmds(true);
        assertEquals(7, cmds.size());
        assertEquals(0, cmds.stream().filter(c -> c.indexname().equals(F) && c.command().equals("normalize")).count());
    }

    @Test
    void testThatStructFieldsInMapValueAreMultivalue() {
        String documentContent = String.join("\n",
                "struct elem {",
                "  field name type string {}",
                "  field weight type int {}",
                "}",
                "field elem_map type map<string, elem> {",
                "  indexing: summary",
                "  struct-field key { indexing: attribute }",
                "  struct-field value.name { indexing: attribute }",
                "  struct-field value.weight { indexing: attribute }",
                "}");
        var cmds = createIndexInfo("sda", documentContent, false).indexinfo(0).command();
        assertMultivalue(cmds, "elem_map");
        assertMultivalue(cmds, "elem_map.key");
        assertMultivalue(cmds, "elem_map.value");
        assertMultivalue(cmds, "elem_map.value.name");
        assertMultivalue(cmds, "elem_map.value.weight");
    }

    @Test
    void testThatNestedStructFieldsInArrayOfStructAreMultivalue() {
        String documentContent = String.join("\n",
                "struct inner {",
                "  field name type string {}",
                "}",
                "struct outer {",
                "  field in type inner {}",
                "}",
                "field outer_array type array<outer> {",
                "  indexing: summary",
                "}");
        var cmds = createIndexInfo("sda", documentContent, false).indexinfo(0).command();
        assertMultivalue(cmds, "outer_array");
        assertMultivalue(cmds, "outer_array.in");
        assertMultivalue(cmds, "outer_array.in.name");
    }

    private static void assertMultivalue(List<IndexInfoConfig.Indexinfo.Command> cmds, String indexName) {
        assertEquals(1, cmds.stream().filter(c -> c.indexname().equals(indexName) && c.command().equals("multivalue")).count(),
                     "Expected multivalue for " + indexName);
    }

    private static List<IndexInfoConfig.Indexinfo.Command> createIndexCmds(boolean isStreaming) {
        final String SD = "sda";
        String documentContent = "field " + F + " type string {indexing:index | summary}";
        var cfg = createIndexInfo(SD, documentContent, isStreaming);
        assertEquals(SD, cfg.indexinfo(0).name());
        return cfg.indexinfo(0).command();
    }

    private static IndexInfoConfig createIndexInfo(String schemaName, String sdContent, boolean isStreaming) {
        var model = createModel(schemaName, sdContent);
        var schema = model.getSearchClusters().get(0).schemas().get(schemaName);
        var indexInfo = new IndexInfo(schema.fullSchema(), isStreaming);
        IndexInfoConfig.Builder builder = new IndexInfoConfig.Builder();
        indexInfo.getConfig(builder);
        return builder.build();
    }

    static VespaModel createModel(String schemaName, String sdContent) {
        return new ApplicationPackageBuilder()
                .addCluster(new ContentClusterBuilder().name("content").docTypes(List.of(DocType.index(schemaName))))
                .addSchemas(new SchemaBuilder().name(schemaName).content(sdContent).build())
                .buildCreator().create();
    }
}
