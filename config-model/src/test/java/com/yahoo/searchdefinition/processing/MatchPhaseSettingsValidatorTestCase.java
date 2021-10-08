// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import org.junit.Test;

import static com.yahoo.searchdefinition.processing.AssertSearchBuilder.assertBuildFails;

public class MatchPhaseSettingsValidatorTestCase {

    private static String getMessagePrefix() {
        return "In search definition 'test', rank-profile 'default': match-phase attribute 'foo' ";
    }

    @Test
    public void requireThatAttributeMustExists() throws Exception {
        assertBuildFails("src/test/examples/matchphase/non_existing_attribute.sd",
                getMessagePrefix() + "does not exists");
    }

    @Test
    public void requireThatAttributeMustBeNumeric() throws Exception {
        assertBuildFails("src/test/examples/matchphase/wrong_data_type_attribute.sd",
                getMessagePrefix() + "must be single value numeric, but it is 'string'");
    }

    @Test
    public void requireThatAttributeMustBeSingleValue() throws Exception {
        assertBuildFails("src/test/examples/matchphase/wrong_collection_type_attribute.sd",
                getMessagePrefix() + "must be single value numeric, but it is 'Array<int>'");
    }

    @Test
    public void requireThatAttributeMustHaveFastSearch() throws Exception {
        assertBuildFails("src/test/examples/matchphase/non_fast_search_attribute.sd",
                getMessagePrefix() + "must be fast-search, but it is not");
    }
}
