// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.ml.ImportedModelTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests adding Vespa ranking expression based models in the models/ dir
 *
 * @author bratseth
 */
public class VespaMlModelTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/vespa/");

    private final String expectedRankConfig =
            "constant(constant1).type : tensor(x[3])\n" +
            "constant(constant1).value : tensor(x[3]):[0.5, 1.5, 2.5]\n" +
            "rankingExpression(foo1).rankingScript : reduce(reduce(input1 * input2, sum, name) * constant(constant1), max, x) * 3.0\n" +
            "rankingExpression(foo1).input2.type : tensor(x[3])\n" +
            "rankingExpression(foo1).input1.type : tensor(name{},x[3])\n" +
            "rankingExpression(foo2).rankingScript : reduce(reduce(input1 * input2, sum, name) * constant(constant1asLarge), max, x) * 3.0\n" +
            "rankingExpression(foo2).input2.type : tensor(x[3])\n" +
            "rankingExpression(foo2).input1.type : tensor(name{},x[3])\n";

    /** The model name */
    private final String name = "example";

    @AfterEach
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    void testGlobalVespaModel() throws IOException {
        ImportedModelTester tester = new ImportedModelTester(name, applicationDir);
        VespaModel model = tester.createVespaModel();
        tester.assertLargeConstant("constant1asLarge", model, Optional.of(3L));
        assertEquals(expectedRankConfig, rankConfigOf("example", model));

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedAppDir = applicationDir.append("copy");
        try {
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(applicationDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ImportedModelTester storedTester = new ImportedModelTester(name, storedAppDir);
            VespaModel storedModel = storedTester.createVespaModel();
            storedTester.assertLargeConstant("constant1asLarge", model, Optional.of(3L));
            assertEquals(expectedRankConfig, rankConfigOf("example", storedModel));
        }
        finally {
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    private String rankConfigOf(String rankProfileName, VespaModel model) {
        StringBuilder b = new StringBuilder();
        RawRankProfile profile = model.rankProfileList().getRankProfiles().get(rankProfileName);
        for (var property : profile.configProperties())
            b.append(property.getFirst()).append(" : ").append(property.getSecond()).append("\n");
        return b.toString();
    }

}
