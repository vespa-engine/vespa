// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class IdentifierTestCase {

    @Test
    public void requireThatThereAreNoReservedWords() throws ParseException {
        List<String> tokens = Arrays.asList("attribute",
                                            "base64decode",
                                            "base64encode",
                                            "clear_state",
                                            "create_if_non_existent",
                                            "echo",
                                            "exact",
                                            "flatten",
                                            "for_each",
                                            "get_field",
                                            "get_var",
                                            "guard",
                                            "hex_decode",
                                            "hex_encode",
                                            "host_name",
                                            "if",
                                            "index",
                                            "join",
                                            "linguistics",
                                            "lowercase",
                                            "ngram",
                                            "normalize",
                                            "now",
                                            "optimize_predicate",
                                            "predicate_to_raw",
                                            "put_symbol",
                                            "random",
                                            "raw_to_predicate",
                                            "remove_ctrl_chars",
                                            "remove_if_zero",
                                            "remove_so_si",
                                            "select_input",
                                            "set_language",
                                            "set_var",
                                            "split",
                                            "substring",
                                            "summary",
                                            "switch",
                                            "this",
                                            "tokenize",
                                            "to_array",
                                            "to_double",
                                            "to_float",
                                            "to_int",
                                            "to_long",
                                            "to_pos",
                                            "to_string",
                                            "to_wset",
                                            "to_bool",
                                            "trim",
                                            "true",
                                            "false",
                                            "zcurve");
        for (String str : tokens) {
            IndexingParser parser = new IndexingParser(new IndexingInput(str));
            assertEquals(str, parser.identifier());
        }
    }

}
