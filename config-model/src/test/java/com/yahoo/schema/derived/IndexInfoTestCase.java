package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.DeployState;
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

    private static VespaModel createModel(String schemaName, String sdContent) {
        var builder = new DeployState.Builder();
        return new ApplicationPackageBuilder()
                .addCluster(new ContentClusterBuilder().name("content").docTypes(List.of(DocType.index(schemaName))))
                .addSchemas(new SchemaBuilder().name(schemaName).content(sdContent).build())
                .buildCreator().create(builder);
    }
}
