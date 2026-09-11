// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.DistributableResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.schema.DistributableResource.PathType.BLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that we handle special characters in resource names correctly
 *
 * @author eirik
 */
public class RankSetupValidatorTest {

    @Test
    void escape_special_characters_in_resource_names() {
        String nameWithSpecialCharacters = """
                aaa"
                file[9].ref "foo"
                file[9].path "\\x2fmy\\x2ffile"
                #.lz4""";
        var resource = new DistributableResource("model", nameWithSpecialCharacters, BLOB);

        List<String> config = new ArrayList<>();
        new RankSetupValidator(false).writeExtraVerifyRankSetupConfig(config, List.of(resource));

        // Exactly one ref line and one path line
        assertEquals(2, config.size());

        // No config line may contain a raw newline
        for (String line : config)
            assertFalse(line.contains("\n"), "Config line must not contain raw newlines: " + line);

        String pathLine = config.get(1);
        assertTrue(pathLine.startsWith("file[0].path \""), pathLine);
        assertTrue(pathLine.endsWith("\""), pathLine);
        // quote must be escaped rather than terminating the value early.
        assertTrue(pathLine.contains("\\\""), "quote must be escaped: " + pathLine);
        assertFalse(pathLine.contains("\nfile[9]"), pathLine);
    }

}
