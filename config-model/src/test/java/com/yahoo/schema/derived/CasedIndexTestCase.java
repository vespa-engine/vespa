package com.yahoo.schema.derived;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.search.config.IndexInfoConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author bratseth
 */
public class CasedIndexTestCase {

    @Test
    public void testCasedIndexDeriving() throws Exception {
        var b = new ApplicationBuilder();
        b.addSchema("""
                    schema test {
                      document test {
                        field a type string {
                          indexing: summary | index
                          match: cased
                        }
                      }
                    }
                    """);
        var application = b.build(true);
        var config = new DerivedConfiguration(application.schemas().get("test"), b.getRankProfileRegistry());
        var indexInfo = config.getIndexInfo();
        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        indexInfo.getConfig(indexInfoConfigBuilder);
        assertFalse(commandsOf("test", "a", indexInfoConfigBuilder).contains("lowercase"));
    }

    private Set<String> commandsOf(String schema, String field, IndexInfoConfig.Builder indexInfoConfigBuilder) {
        var schemaIndexInfo = indexInfoConfigBuilder.build().indexinfo().stream()
                                                    .filter(c -> c.name().equals(schema))
                                                    .findAny().get();
        return schemaIndexInfo.command().stream()
                              .filter(c -> c.indexname().equals(field))
                              .map(c -> c.command())
                              .collect(Collectors.toSet());
    }

}
