// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression;

import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class FeatureListTestCase {

    @Test
    public void requireThatFeatureListFromStringWorks() throws ParseException {
        assertFromString("attribute(foo).out",
                         Arrays.asList("attribute(foo).out"));
        assertFromString("attribute(foo).out attribute ( bar ) . out",
                         Arrays.asList("attribute(foo).out", "attribute(bar).out"));
        assertFromString("foo\n bar\n \t \t \n baz \n",
                         Arrays.asList("foo", "bar", "baz"));
        assertFromString("attribute attribute(foo) attribute(foo).out attribute(bar).out.out",
                         Arrays.asList("attribute", "attribute(foo)", "attribute(foo).out", "attribute(bar).out.out"));
    }

    @Test
    public void requireThatFeatureListFromReaderWorks() throws ParseException {
        assertFromReader(new StringReader("attribute(foo).out"),
                         Arrays.asList("attribute(foo).out"));
        assertFromReader(new StringReader("attribute(foo).out attribute ( bar ) . out"),
                         Arrays.asList("attribute(foo).out", "attribute(bar).out"));
        assertFromReader(new StringReader("foo\n bar\n \t \t \n baz \n"),
                         Arrays.asList("foo", "bar", "baz"));
        assertFromReader(new StringReader("attribute attribute(foo) attribute(foo).out attribute(bar).out.out"),
                         Arrays.asList("attribute", "attribute(foo)", "attribute(foo).out", "attribute(bar).out.out"));
    }

    @Test
    public void requireThatFeatureListFromFileWorks() throws ParseException, FileNotFoundException {
        assertFromFile(new File("src/test/files/features01.expression"),
                       Arrays.asList("attribute(foo).out"));
        assertFromFile(new File("src/test/files/features02.expression"),
                       Arrays.asList("attribute(foo).out", "attribute(bar).out"));
        assertFromFile(new File("src/test/files/features03.expression"),
                       Arrays.asList("foo", "bar", "baz"));
        assertFromFile(new File("src/test/files/features04.expression"),
                       Arrays.asList("attribute", "attribute(foo)", "attribute(foo).out", "attribute(bar).out.out"));
    }

    public void assertFromString(String input, List<String> expected) throws ParseException {
        assertFeatureList(new FeatureList(input), expected);
    }

    public void assertFromReader(Reader input, List<String> expected) throws ParseException {
        assertFeatureList(new FeatureList(input), expected);
    }

    public void assertFromFile(File input, List<String> expected) throws ParseException, FileNotFoundException {
        assertFeatureList(new FeatureList(input), expected);
    }

    public void assertFeatureList(FeatureList features, List<String> expected) throws ParseException {
        assertEquals(expected.size(), features.size());
        for (int i = 0; i < features.size(); ++i) {
            assertTrue(features.get(i) != null);
            assertEquals(expected.get(i), features.get(i).toString());
        }
    }
}
