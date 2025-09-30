// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.exportsignificance;

import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Generates significance-model.json.
 *
 * @author johsol
 */
public class SignificanceExporter {

    private final IndexImporter indexImporter;

    private final String VESPA_HOME = Defaults.getDefaults().vespaHome();

    public SignificanceExporter() {
        System.out.println("Vespa home: " + VESPA_HOME);
        indexImporter = new IndexImporter();
    }

    public void export(String field, String language, String output) {
        for (var dir : new FlushedIndexPathIterator(Path.of(VESPA_HOME))) {
            var terms = indexImporter
                    .importFromIndex(field, dir)
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue() > 1)
                    .collect(Collectors.toSet());
            for (var entry : terms) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
            /*ObjectMapper mapper = new ObjectMapper();
            try {
                System.out.println(mapper.writeValueAsString(terms));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }*/
        }
    }
}
