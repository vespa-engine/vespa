// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import static org.junit.Assert.*;

import com.yahoo.searchdefinition.document.ImmutableSDField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.parser.ParseException;

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

    @Rule
    public ExpectedException exception = ExpectedException.none();

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

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Missing arity value in predicate field.");
        SearchBuilder.createFromString(sd);
        fail();
    }

    @Test
    public void requireThatBuilderFailsIfBothIndexAndAttribute() throws ParseException {
        String sd = searchSd(predicateFieldSd("indexing: summary | index | attribute\nindex { arity: 2 }"));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
        SearchBuilder.createFromString(sd);
    }

    @Test
    public void requireThatBuilderFailsIfIndex() throws ParseException {
        String sd = searchSd(predicateFieldSd("indexing: summary | index \nindex { arity: 2 }"));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
        SearchBuilder.createFromString(sd);
    }


    @Test
    public void requireThatBuilderFailsIfIllegalArityValue() throws ParseException {
        String sd = searchSd(predicateFieldSd(attributeFieldSd(arityParameter(0))));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Invalid arity value in predicate field, must be greater than 1.");
        SearchBuilder.createFromString(sd);
    }

    @Test
    public void requireThatBuilderFailsIfArityParameterExistButNotPredicateField() throws ParseException {
        String sd = searchSd(stringFieldSd(attributeFieldSd(arityParameter(2))));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Arity parameter is used only for predicate type fields.");
        SearchBuilder.createFromString(sd);
    }

    @Test
    public void requireThatBuilderFailsIfBoundParametersExistButNotPredicateField() throws ParseException {
        String sd = searchSd(
                        stringFieldSd(
                            attributeFieldSd(
                                    lowerBoundParameter(100) + upperBoundParameter(1000))));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Parameters lower-bound and upper-bound are used only for predicate type fields.");
        SearchBuilder.createFromString(sd);
    }

    @Test
    public void requireThatArrayOfPredicateFails() throws ParseException {
        String sd = searchSd(
                        arrayPredicateFieldSd(
                                attributeFieldSd(
                                        arityParameter(1))));

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Collections of predicates are not allowed.");
        SearchBuilder.createFromString(sd);
    }

}
