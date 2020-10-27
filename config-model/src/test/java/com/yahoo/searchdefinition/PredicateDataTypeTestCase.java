// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Lester Solbakken
 */

public class PredicateDataTypeTestCase {

    private String searchSd(String field) {
        return "search p {\n document p {\n" + field + "}\n}\n";
    }

    private String predicateFieldSd(String index) {
        return "field pf type predicate {\n" + index + "}\n";
    }

    private String arrayPredicateFieldSd(String index) {
        return "field apf type array<predicate> {\n" + index + "}\n";
    }

    private String stringFieldSd(String index) {
        return "field sf type string {\n" + index + "}\n";
    }

    private String attributeFieldSd(String terms) {
        return "indexing: attribute\n index {\n" + terms + "}\n";
    }

    private String arityParameter(int arity) {
        return "arity: " + arity + "\n";
    }

    private String lowerBoundParameter(long bound) {
        return "lower-bound: " + bound + "\n";
    }

    private String upperBoundParameter(long bound) {
        return "upper-bound: " + bound + "\n";
    }

    @Test
    public void requireThatBuilderSetsIndexParametersCorrectly() throws ParseException {
        int arity = 2;
        long lowerBound = -100;
        long upperBound = 100;
        String sd = searchSd(
                        predicateFieldSd(
                            attributeFieldSd(
                                    arityParameter(arity) +
                                            lowerBoundParameter(lowerBound) +
                                            upperBoundParameter(upperBound))));

        SearchBuilder sb = SearchBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSearch().allConcreteFields()) {
              if (field.getDataType() == DataType.PREDICATE) {
                for (Index index : field.getIndices().values()) {
                    assertTrue(index.getBooleanIndexDefiniton().hasArity());
                    assertEquals(arity, index.getBooleanIndexDefiniton().getArity());
                    assertTrue(index.getBooleanIndexDefiniton().hasLowerBound());
                    assertEquals(lowerBound, index.getBooleanIndexDefiniton().getLowerBound());
                    assertTrue(index.getBooleanIndexDefiniton().hasUpperBound());
                    assertEquals(upperBound, index.getBooleanIndexDefiniton().getUpperBound());
                }
            }
        }
    }

    @Test
    public void requireThatBuilderHandlesLongValues() throws ParseException {
        int arity = 2;
        long lowerBound = -100000000000000000L;
        long upperBound = 1000000000000000000L;
        String sd = searchSd(
                        predicateFieldSd(
                            attributeFieldSd(
                                    arityParameter(arity) +
                                            "lower-bound: -100000000000000000L\n" + // +'L'
                                            upperBoundParameter(upperBound))));

        SearchBuilder sb = SearchBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSearch().allConcreteFields()) {
              if (field.getDataType() == DataType.PREDICATE) {
                for (Index index : field.getIndices().values()) {
                    assertEquals(arity, index.getBooleanIndexDefiniton().getArity());
                    assertEquals(lowerBound, index.getBooleanIndexDefiniton().getLowerBound());
                    assertEquals(upperBound, index.getBooleanIndexDefiniton().getUpperBound());
                }
            }
        }
    }

    @Test
    public void requireThatBuilderHandlesMissingParameters() throws ParseException {
        String sd = searchSd(
                        predicateFieldSd(
                            attributeFieldSd(
                                    arityParameter(2))));
        SearchBuilder sb = SearchBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSearch().allConcreteFields()) {
            if (field.getDataType() == DataType.PREDICATE) {
                for (Index index : field.getIndices().values()) {
                    assertTrue(index.getBooleanIndexDefiniton().hasArity());
                    assertFalse(index.getBooleanIndexDefiniton().hasLowerBound());
                    assertFalse(index.getBooleanIndexDefiniton().hasUpperBound());
                }
            }
        }
    }

    @Test
    public void requireThatBuilderFailsIfNoArityValue() throws ParseException {
        String sd = searchSd(predicateFieldSd(attributeFieldSd("")));

        assertCreateSearchBuilderThrows(sd, "Missing arity value in predicate field.");
    }

    @Test
    public void requireThatBuilderFailsIfBothIndexAndAttribute() {
        String sd = searchSd(predicateFieldSd("indexing: summary | index | attribute\nindex { arity: 2 }"));

        assertCreateSearchBuilderThrows(sd, "For search 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
    }

    @Test
    public void requireThatBuilderFailsIfIndex() {
        String sd = searchSd(predicateFieldSd("indexing: summary | index \nindex { arity: 2 }"));

        assertCreateSearchBuilderThrows(sd, "For search 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
    }


    @Test
    public void requireThatBuilderFailsIfIllegalArityValue() {
        String sd = searchSd(predicateFieldSd(attributeFieldSd(arityParameter(0))));

        assertCreateSearchBuilderThrows(sd, "Invalid arity value in predicate field, must be greater than 1.");
    }

    @Test
    public void requireThatBuilderFailsIfArityParameterExistButNotPredicateField() throws ParseException {
        String sd = searchSd(stringFieldSd(attributeFieldSd(arityParameter(2))));

        assertCreateSearchBuilderThrows(sd, "Arity parameter is used only for predicate type fields.");
    }

    @Test
    public void requireThatBuilderFailsIfBoundParametersExistButNotPredicateField() {
        String sd = searchSd(
                        stringFieldSd(
                            attributeFieldSd(
                                    lowerBoundParameter(100) + upperBoundParameter(1000))));

        assertCreateSearchBuilderThrows(sd, "Parameters lower-bound and upper-bound are used only for predicate type fields.");
    }

    @Test
    public void requireThatArrayOfPredicateFails() {
        String sd = searchSd(
                        arrayPredicateFieldSd(
                                attributeFieldSd(
                                        arityParameter(1))));

        assertCreateSearchBuilderThrows(sd, "Collections of predicates are not allowed.");
    }

    private void assertCreateSearchBuilderThrows(String schema, String expectedErrorMessage) {
        Exception e = assertThrows(IllegalArgumentException.class, () -> SearchBuilder.createFromString(schema));
        assertThat(e.getMessage(), containsString(expectedErrorMessage));
    }

}
