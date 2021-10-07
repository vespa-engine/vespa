// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Einar M R Rosenvinge
 */
public class AnnotationsTestCase extends AbstractExportingTestCase {

    @Test
    public void requireThatStructRegistersIfOnlyUsedByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstruct");
    }

    @Test
    public void requireThatStructRegistersIfOnlyUsedAsArrayByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstructarray");
    }

    @Test
    public void testSimpleAnnotationDeriving() throws IOException, ParseException {
        assertCorrectDeriving("annotationssimple");
    }

    @Test
    public void testAnnotationDerivingWithImplicitStruct() throws IOException, ParseException {
        assertCorrectDeriving("annotationsimplicitstruct");
    }

    @Test
    public void testAnnotationDerivingInheritance() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance");
    }

    @Test
    public void testAnnotationDerivingInheritance2() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance2");
    }

    @Test
    public void testSimpleReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference");
    }

    @Test
    public void testAdvancedReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference2");
    }

    @Test
    public void testAnnotationsPolymorphy() throws IOException, ParseException {
        assertCorrectDeriving("annotationspolymorphy");
    }
    
    /**
     * An annotation declared before document {} won't work, no doc type to add it to.
     */
    @Test(expected = IllegalArgumentException.class)    
    public void testAnnotationOutsideOfDocumment() throws IOException, ParseException {
        assertCorrectDeriving("annotationsoutsideofdocument");
    }
    
}
