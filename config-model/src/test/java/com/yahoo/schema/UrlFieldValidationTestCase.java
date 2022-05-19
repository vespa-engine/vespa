// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class UrlFieldValidationTestCase {

    @Test
    public void requireThatInheritedRiseFieldsStillCanBeInConflictButDontThrowException() throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchema("search test {" +
                          "    document test { " +
                          "        field a type uri { indexing: attribute | summary }" +
                          "    }" +
                          "}");
        try {
            builder.build(true);
            fail("Should have caused an exception");
            // success
        } catch (IllegalArgumentException e) {
            assertEquals("Error in field 'a' in schema 'test': uri type fields cannot be attributes",
                         Exceptions.toMessageString(e));
        }
    }

}
