// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.ImmutableSDField;
import org.junit.jupiter.api.Test;

import com.yahoo.document.DataType;
import com.yahoo.schema.parser.ParseException;

import static org.junit.jupiter.api.Assertions.*;

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
    void requireThatBuilderSetsIndexParametersCorrectly() throws ParseException {
        int arity = 2;
        long lowerBound = -100;
        long upperBound = 100;
        String sd = searchSd(
                predicateFieldSd(
                        attributeFieldSd(
                                arityParameter(arity) +
                                        lowerBoundParameter(lowerBound) +
                                        upperBoundParameter(upperBound))));

        ApplicationBuilder sb = ApplicationBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSchema().allConcreteFields()) {
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
    void requireThatBuilderHandlesLongValues() throws ParseException {
        int arity = 2;
        long lowerBound = -100000000000000000L;
        long upperBound = 1000000000000000000L;
        String sd = searchSd(
                predicateFieldSd(
                        attributeFieldSd(
                                arityParameter(arity) +
                                        "lower-bound: -100000000000000000L\n" + // +'L'
                                        upperBoundParameter(upperBound))));

        ApplicationBuilder sb = ApplicationBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSchema().allConcreteFields()) {
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
    void requireThatBuilderHandlesMissingParameters() throws ParseException {
        String sd = searchSd(
                predicateFieldSd(
                        attributeFieldSd(
                                arityParameter(2))));
        ApplicationBuilder sb = ApplicationBuilder.createFromString(sd);
        for (ImmutableSDField field : sb.getSchema().allConcreteFields()) {
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
    void requireThatBuilderFailsIfNoArityValue() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(predicateFieldSd(attributeFieldSd("")));
            ApplicationBuilder.createFromString(sd);
            fail();
        });
        assertTrue(exception.getMessage().contains("Missing arity value in predicate field."));
    }

    @Test
    void requireThatBuilderFailsIfBothIndexAndAttribute() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(predicateFieldSd("indexing: summary | index | attribute\nindex { arity: 2 }"));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("For schema 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded."));
    }

    @Test
    void requireThatBuilderFailsIfIndex() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(predicateFieldSd("indexing: summary | index \nindex { arity: 2 }"));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("For schema 'p', field 'pf': Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded."));
    }


    @Test
    void requireThatBuilderFailsIfIllegalArityValue() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(predicateFieldSd(attributeFieldSd(arityParameter(0))));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("Invalid arity value in predicate field, must be greater than 1."));
    }

    @Test
    void requireThatBuilderFailsIfArityParameterExistButNotPredicateField() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(stringFieldSd(attributeFieldSd(arityParameter(2))));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("Arity parameter is used only for predicate type fields."));
    }

    @Test
    void requireThatBuilderFailsIfBoundParametersExistButNotPredicateField() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(
                    stringFieldSd(
                            attributeFieldSd(
                                    lowerBoundParameter(100) + upperBoundParameter(1000))));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("Parameters lower-bound and upper-bound are used only for predicate type fields."));
    }

    @Test
    void requireThatArrayOfPredicateFails() throws ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String sd = searchSd(
                    arrayPredicateFieldSd(
                            attributeFieldSd(
                                    arityParameter(1))));
            ApplicationBuilder.createFromString(sd);
        });
        assertTrue(exception.getMessage().contains("Collections of predicates are not allowed."));
    }

}
