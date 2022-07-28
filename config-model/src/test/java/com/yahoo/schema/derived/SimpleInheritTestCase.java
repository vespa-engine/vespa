// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * Tests really simple inheriting
 */
public class SimpleInheritTestCase extends AbstractExportingTestCase {

    @Test
    void testEmptyChild() throws IOException, ParseException {
        String name = "emptychild";
        final String expectedResultsDirName = "src/test/derived/" + name + "/";

        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(expectedResultsDirName + "parent.sd");
        builder.addSchemaFile(expectedResultsDirName + "child.sd");
        builder.build(true);

        Schema schema = builder.getSchema("child");

        String toDirName = "temp/" + name;
        File toDir = new File(toDirName);
        toDir.mkdirs();
        deleteContent(toDir);

        DerivedConfiguration config = new DerivedConfiguration(schema, builder.getRankProfileRegistry());
        config.export(toDirName);

        checkDir(toDirName, expectedResultsDirName);
    }

    private void checkDir(String toDirName, String expectedResultsDirName) throws IOException {
        File[] files = new File(expectedResultsDirName).listFiles();
        for (File file : files) {
            if ( ! file.getName().endsWith(".cfg")) continue;
            assertEqualFiles(file.getPath(), toDirName + "/" + file.getName(), false);
        }
    }
}
