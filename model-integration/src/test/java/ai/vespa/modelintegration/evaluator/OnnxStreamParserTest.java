// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
class OnnxStreamParserTest {

    @Test
    void extracts_external_data_locations_from_model_file() throws IOException {
        assertEquals(
                Set.of(Paths.get("external_data.bin")),
                OnnxStreamParser.getExternalDataLocations(
                        Path.of("src/test/models/onnx/external_data/add_with_external_data.onnx")));
    }

}
