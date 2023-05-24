// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.container.plugin.classanalysis.sampleclasses.Base;
import com.yahoo.container.plugin.classanalysis.sampleclasses.ClassAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Derived;
import com.yahoo.container.plugin.classanalysis.sampleclasses.DummyAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Fields;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface1;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface2;
import com.yahoo.container.plugin.classanalysis.sampleclasses.InvisibleAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.InvisibleDummyAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.MethodAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.MethodInvocation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.RecordWithOverride;
import com.yahoo.container.plugin.classanalysis.sampleclasses.SwitchStatement;
import com.yahoo.osgi.annotation.ExportPackage;
import com.yahoo.osgi.annotation.Version;
import org.junit.jupiter.api.Test;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.awt.image.ImagingOpException;
import java.awt.image.Kernel;
import java.util.List;
import java.util.Optional;

import static com.yahoo.container.plugin.classanalysis.TestUtilities.analyzeClass;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.classFile;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.name;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that analysis of class files works.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class AnalyzeClassTest {

    @Test
    void full_class_name_is_returned() {
        assertEquals(name(Base.class), analyzeClass(Base.class).getName());
    }

    @Test
    void base_class_is_included() {
        assertTrue(analyzeClass(Derived.class).getReferencedClasses().contains(name(Base.class)));
    }

    @Test
    void implemented_interfaces_are_included() {
        assertTrue(analyzeClass(Base.class).getReferencedClasses().containsAll(
                List.of(name(Interface1.class), name(Interface2.class))));
    }

    @Test
    void interface_can_be_analyzed() {
        ClassFileMetaData classMetaData = analyzeClass(Interface1.class);

        assertEquals(name(Interface1.class), classMetaData.getName());
        assertTrue(classMetaData.getReferencedClasses().contains(name(Interface2.class)));
    }

    @Test
    void return_type_is_included() {
        assertTrue(analyzeClass(Interface1.class).getReferencedClasses().contains(name(Image.class)));
    }

    @Test
    void parameters_are_included() {
        assertTrue(analyzeClass(Interface1.class).getReferencedClasses().contains(name(Kernel.class)));
    }

    @Test
    void exceptions_are_included() {
        assertTrue(analyzeClass(Interface1.class).getReferencedClasses().contains(name(ImagingOpException.class)));
    }

    @Test
    void basic_types_ignored() {
        List.of("int", "float").forEach(type ->
                assertFalse(analyzeClass(Interface1.class).getReferencedClasses().contains(type)));
    }

    @Test
    void arrays_of_basic_types_ignored() {
        List.of("int[]", "int[][]").forEach(type ->
                assertFalse(analyzeClass(Interface1.class).getReferencedClasses().contains(type)));
    }

    @Test
    void instance_field_types_are_included() {
        assertTrue(analyzeClass(Fields.class).getReferencedClasses().contains(name(String.class)));
    }

    @Test
    void static_field_types_are_included() {
        assertTrue(analyzeClass(Fields.class).getReferencedClasses().contains(name(java.util.List.class)));
    }

    @Test
    void field_annotation_is_included() {
        assertTrue(analyzeClass(Fields.class).getReferencedClasses().contains(name(DummyAnnotation.class)));
    }

    @Test
    void class_annotation_is_included() {
        assertTrue(analyzeClass(ClassAnnotation.class).getReferencedClasses().contains(name(DummyAnnotation.class)));
    }

    @Test
    void invisible_annotation_not_included() {
        assertFalse(analyzeClass(InvisibleAnnotation.class).getReferencedClasses().contains(name(InvisibleDummyAnnotation.class)));
    }

    @Test
    void method_annotation_is_included() {
        assertTrue(analyzeClass(MethodAnnotation.class).getReferencedClasses().contains(name(DummyAnnotation.class)));
    }

    @Test
    void export_package_annotations_are_ignored() {
        List.of(ExportPackage.class, Version.class).forEach(type ->
                assertFalse(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info"))
                        .getReferencedClasses().contains(type)));
    }

    @Test
    void export_annotations_are_processed() {
        assertEquals(Optional.of(new ExportPackageAnnotation(3, 1, 4, "TEST_QUALIFIER-2")),
                     Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info")).getExportPackage());
    }

    @Test
    void publicApi_annotations_are_processed() {
        assertTrue(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info")).isPublicApi());
    }

    @Test
    void export_annotations_are_validated() {

        try {
            Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.invalid.package-info"));
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("invalid/package-info"));
            assertTrue(e.getCause().getMessage().contains("qualifier must follow the format"));
            assertTrue(e.getCause().getMessage().contains("'EXAMPLE INVALID QUALIFIER'"));
        }
    }

    @Test
    void catch_clauses_are_included() {
        assertTrue(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.CatchException"))
                .getReferencedClasses().contains(name(LoginException.class)));
    }

    @Test
    void class_references_are_included() {
        assertTrue(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.ClassReference"))
                .getReferencedClasses().contains(name(Interface1.class)));
    }

    @Test
    void return_type_of_method_invocations_are_included() {
        assertTrue(analyzeClass(MethodInvocation.class).getReferencedClasses().contains(name(Image.class)));
    }

    @Test
    void attributes_are_included() {
        //Uses com/coremedia/iso/Utf8.class from com.googlecode.mp4parser:isoparser:1.0-RC-1
        assertTrue(Analyze.analyzeClass(classFile("class/Utf8")).getReferencedClasses()
                .contains("org.aspectj.weaver.MethodDeclarationLineNumber"));
    }

    @Test
    void switch_statements_are_analyzed() {
        var referencedClasses = analyzeClass(SwitchStatement.class).getReferencedClasses();
        assertTrue(referencedClasses.contains(name(List.class)));
        assertTrue(referencedClasses.contains(name(IllegalArgumentException.class)));
    }

    @Test
    void records_are_analyzed() {
        var referencedClasses = analyzeClass(RecordWithOverride.class).getReferencedClasses();
        assertTrue(referencedClasses.containsAll(List.of(
                name(List.class),
                name(Byte.class),
                name(String.class),
                name(IllegalArgumentException.class)
        )));

    }
}
