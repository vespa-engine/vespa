// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package helpers;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.yahoo.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vegard Sjonfjell
 */
public class CompareConfigTestHelper {

    public static void assertSerializedConfigFileEquals(String filename, String actual) throws IOException {
        IOUtils.writeFile(filename + ".actual", actual, false);
        if (! actual.endsWith("\n")) {
            IOUtils.writeFile(filename + ".actual", "\n", true);
        }
        assertSerializedConfigEquals(IOUtils.readFile(new File(filename)), actual, false);
    }

    // Written this way to compare order independently but output error with order preserved
    // Note that this means that if a test fails you'll also see spurious differences in the comparison
    // from lines which are present in both but at different locations.
    public static void assertSerializedConfigEquals(String expected, String actual, boolean orderMatters) {
        if (orderMatters) {
            assertEquals(expected.trim(), actual.trim());
        }
        else {
            if (!sortLines(expected.trim()).equals(sortLines(actual.trim())))
                assertEquals(expected, actual);
        }
    }

    private static String sortLines(String fileData) {
        final List<String> lines = Lists.newArrayList(Splitter.on('\n').split(fileData));
        Collections.sort(lines);
        return Joiner.on('\n').join(lines);
    }

}
