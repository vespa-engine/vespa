// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * @author hmusum
 */
public class NormalizedDefinitionTest {

    @Test
    void testNormalizingFromReader() {
        String def =
                "aString string \n" +
                        "anInt int #comment \n" +
                        "aStringCommentCharacterAfter string default=\"ab\" #foo\n" +
                        "aStringWithCommentCharacter string default=\"a#b\"\n" +
                        "aStringWithEscapedQuote string default=\"a\"b\"\n";

        StringReader reader = new StringReader(def);

        NormalizedDefinition nd = new NormalizedDefinition();
        List<String> out = null;
        try {
            nd.normalize(new BufferedReader(reader));
            out = nd.getNormalizedContent();
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertNotNull(out);
        assertEquals(5, out.size());
        assertEquals("aString string\n", out.get(0));
        assertEquals("anInt int\n", out.get(1));
        assertEquals("aStringCommentCharacterAfter string default=\"ab\"\n", out.get(2));
        assertEquals("aStringWithCommentCharacter string default=\"a#b\"\n", out.get(3));
        assertEquals("aStringWithEscapedQuote string default=\"a\"b\"\n", out.get(4));

        reader.close();
    }

    @Test
    void testNormalizingFromFile() throws IOException {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader("src/test/resources/configgen.allfeatures.def");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        NormalizedDefinition nd = new NormalizedDefinition();
        List<String> out = null;
        try {
            nd.normalize(new BufferedReader(fileReader));
            out = nd.getNormalizedContent();
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertNotNull(out);
        assertEquals(75, out.size());

        assertNotNull(fileReader);
        fileReader.close();
    }

}
