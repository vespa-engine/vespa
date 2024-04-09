// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.yahoo.language.significance.impl.DefaultSignificanceModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author MariusArhaug

 */
public class DefaultSignificanceModelTest {

    @Test
    public void testDocumentFrequency() {
        DefaultSignificanceModel significanceModel = new DefaultSignificanceModel(Path.of("src/test/models/en.json"));

        assertEquals(2, significanceModel.documentFrequency("test").frequency());
        assertEquals(10, significanceModel.documentFrequency("test").corpusSize());

        assertEquals(3, significanceModel.documentFrequency("hello").frequency());
        assertEquals(10, significanceModel.documentFrequency("hello").corpusSize());

        assertEquals(1, significanceModel.documentFrequency("non-existent-word").frequency());
        assertEquals(10, significanceModel.documentFrequency("hello").corpusSize());
    }
}
