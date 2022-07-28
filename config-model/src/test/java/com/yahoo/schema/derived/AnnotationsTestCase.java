// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author Einar M R Rosenvinge
 */
public class AnnotationsTestCase extends AbstractExportingTestCase {

    @Test
    void requireThatStructRegistersIfOnlyUsedByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstruct");
    }

    @Test
    void requireThatStructRegistersIfOnlyUsedAsArrayByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstructarray");
    }

    @Test
    void testSimpleAnnotationDeriving() throws IOException, ParseException {
        assertCorrectDeriving("annotationssimple");
    }

    @Test
    void testAnnotationDerivingWithImplicitStruct() throws IOException, ParseException {
        assertCorrectDeriving("annotationsimplicitstruct");
    }

    @Test
    void testAnnotationDerivingInheritance() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance");
    }

    @Test
    void testAnnotationDerivingInheritance2() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance2");
    }

    @Test
    void testSimpleReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference");
    }

    @Test
    void testAdvancedReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference2");
    }

    @Test
    void testAnnotationsPolymorphy() throws IOException, ParseException {
        assertCorrectDeriving("annotationspolymorphy");
    }

    /**
     * An annotation declared before document {} should work.
     */
    @Test
    void testAnnotationOutsideOfDocumentNew() throws IOException, ParseException {
        assertCorrectDeriving("annotationsoutsideofdocument");
    }
}
