// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Tests really simple inheriting
 */
public class SimpleInheritTestCase extends AbstractExportingTestCase {

    @Test
    public void testEmptyChild() throws IOException, ParseException {
        String name = "emptychild";
        final String expectedResultsDirName = "src/test/derived/" + name + "/";

        SearchBuilder builder = new SearchBuilder();
        builder.importFile(expectedResultsDirName + "parent.sd");
        builder.importFile(expectedResultsDirName + "child.sd");
        builder.build();

        Search search = builder.getSearch("child");

        String toDirName = "temp/" + name;
        File toDir = new File(toDirName);
        toDir.mkdirs();
        deleteContent(toDir);

        DerivedConfiguration config = new DerivedConfiguration(search,
                                                               builder.getRankProfileRegistry(),
                                                               new QueryProfileRegistry());
        config.export(toDirName);

        checkDir(toDirName, expectedResultsDirName);
    }

    private void checkDir(String toDirName, String expectedResultsDirName) throws IOException {
        File[] files = new File(expectedResultsDirName).listFiles();
        for (File file : files) {
            if (!file.getName().endsWith(".cfg")) {
                continue;
            }
            assertEqualFiles(file.getPath(), toDirName + "/" + file.getName());
        }
    }
}
