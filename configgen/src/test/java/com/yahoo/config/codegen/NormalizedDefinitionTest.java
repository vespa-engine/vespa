// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import static org.junit.Assert.*;

import java.io.*;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;


/**
 * @author hmusum
 */
public class NormalizedDefinitionTest {

    @Test
    public void testNormalizingFromReader() {
        String def =
                "version=1\n" +
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
        assertThat(out.size(), is(6));
        assertThat(out.get(0), is ("version=1\n"));
        assertThat(out.get(1), is ("aString string\n"));
        assertThat(out.get(2), is ("anInt int\n"));
        assertThat(out.get(3), is ("aStringCommentCharacterAfter string default=\"ab\"\n"));
        assertThat(out.get(4), is ("aStringWithCommentCharacter string default=\"a#b\"\n"));
        assertThat(out.get(5), is ("aStringWithEscapedQuote string default=\"a\"b\"\n"));

        reader.close();
    }

    @Test
    public void testNormalizingFromFile() throws IOException {
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
        assertThat(out.size(), is(72));

        assertNotNull(fileReader);
        fileReader.close();
    }

}
