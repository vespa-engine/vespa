package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class NGramTestCase extends AbstractExportingTestCase {

    @Test
    void testNGram() throws IOException, ParseException {
        assertCorrectDeriving("ngram");
    }

}