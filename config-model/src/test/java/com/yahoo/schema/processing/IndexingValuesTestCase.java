// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValuesTestCase {

    @Test
    void requireThatModifyFieldNoOutputDoesNotThrow() throws ParseException {
        var schema = """
                    search indexing_modify_field_no_output {
                        document indexing_modify_field_no_output {
                            field foo type string {
                                indexing: lowercase | echo
                            }
                        }
                    }
                    """;
        ApplicationBuilder.createFromString(schema);
    }

    @Test
    void requireThatInputOtherFieldThrows() throws IOException, ParseException {
        try {
            var schema = """
                        search indexing_input_other_field {
                           document indexing_input_other_field {
                               field foo type string {
                       
                               }
                               field bar type string {
                                   indexing: input foo | attribute | index | summary
                               }
                           }
                       }
                       """;
            ApplicationBuilder.createFromString(schema);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For schema 'indexing_input_other_field', field 'bar': Indexing expression 'input foo' " +
                         "attempts to modify the value of the document field 'bar'. " +
                         "Use a field outside the document block instead.",
                         Exceptions.toMessageString(e));
        }
    }

}
