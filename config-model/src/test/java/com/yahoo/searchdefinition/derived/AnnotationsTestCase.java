// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Einar M R Rosenvinge
 */
public class AnnotationsTestCase extends AbstractExportingTestCase {

    @Test
    public void requireThatStructRegistersIfOnlyUsedByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstruct",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void requireThatStructRegistersIfOnlyUsedAsArrayByAnnotation() throws IOException, ParseException {
        assertCorrectDeriving("annotationsstructarray",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testSimpleAnnotationDeriving() throws IOException, ParseException {
        assertCorrectDeriving("annotationssimple",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testAnnotationDerivingWithImplicitStruct() throws IOException, ParseException {
        assertCorrectDeriving("annotationsimplicitstruct",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testAnnotationDerivingInheritance() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testAnnotationDerivingInheritance2() throws IOException, ParseException {
        assertCorrectDeriving("annotationsinheritance2",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testSimpleReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testAdvancedReference() throws IOException, ParseException {
        assertCorrectDeriving("annotationsreference2",
                              new TestProperties().setExperimentalSdParsing(true));
    }

    @Test
    public void testAnnotationsPolymorphy() throws IOException, ParseException {
        assertCorrectDeriving("annotationspolymorphy",
                              new TestProperties().setExperimentalSdParsing(true));
    }
    
    /**
     * An annotation declared before document {} won't work, no doc type to add it to.
     */
    @Test(expected = IllegalArgumentException.class)    
    public void testAnnotationOutsideOfDocumentOld() throws IOException, ParseException {
        assertCorrectDeriving("annotationsoutsideofdocument",
                              new TestProperties().setExperimentalSdParsing(false));
    }

    /**
     * An annotation declared before document {} should work.
     */
    @Test
    public void testAnnotationOutsideOfDocumentNew() throws IOException, ParseException {
        assertCorrectDeriving("annotationsoutsideofdocument",
                              new TestProperties().setExperimentalSdParsing(true));
    }
    
}
