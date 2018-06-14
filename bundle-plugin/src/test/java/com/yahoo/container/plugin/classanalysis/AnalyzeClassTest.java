// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.container.plugin.classanalysis.sampleclasses.Base;
import com.yahoo.container.plugin.classanalysis.sampleclasses.ClassAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Derived;
import com.yahoo.container.plugin.classanalysis.sampleclasses.DummyAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Fields;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface1;
import com.yahoo.container.plugin.classanalysis.sampleclasses.Interface2;
import com.yahoo.container.plugin.classanalysis.sampleclasses.MethodAnnotation;
import com.yahoo.container.plugin.classanalysis.sampleclasses.MethodInvocation;
import com.yahoo.osgi.annotation.ExportPackage;
import com.yahoo.osgi.annotation.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.security.auth.login.LoginException;
import java.awt.Image;
import java.awt.image.ImagingOpException;
import java.awt.image.Kernel;
import java.util.Optional;

import static com.yahoo.container.plugin.classanalysis.TestUtilities.analyzeClass;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.classFile;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.name;
import static com.yahoo.container.plugin.classanalysis.TestUtilities.throwableMessage;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * Tests that analysis of class files works.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class AnalyzeClassTest {
    @Test
    public void require_that_full_class_name_is_returned() {
        assertThat(analyzeClass(Base.class).getName(), is(name(Base.class)));
    }

    @Test
    public void require_that_base_class_is_included() {
        assertThat(analyzeClass(Derived.class).getReferencedClasses(), hasItem(name(Base.class)));
    }

    @Test
    public void require_that_implemented_interfaces_are_included() {
        assertThat(analyzeClass(Base.class).getReferencedClasses(),
                allOf(hasItem(name(Interface1.class)), hasItem(name(Interface2.class))));
    }

    @Test
    public void require_that_interface_can_be_analyzed() {
        ClassFileMetaData classMetaData = analyzeClass(Interface1.class);

        assertThat(classMetaData.getName(), is(name(Interface1.class)));
        assertThat(classMetaData.getReferencedClasses(), hasItem(name(Interface2.class)));
    }

    @Test
    public void require_that_return_type_is_included() {
        assertThat(analyzeClass(Interface1.class).getReferencedClasses(), hasItem(name(Image.class)));
    }

    @Test
    public void require_that_parameters_are_included() {
        assertThat(analyzeClass(Interface1.class).getReferencedClasses(), hasItem(name(Kernel.class)));
    }

    @Test
    public void require_that_exceptions_are_included() {
        assertThat(analyzeClass(Interface1.class).getReferencedClasses(), hasItem(name(ImagingOpException.class)));
    }

    @Test
    public void require_that_basic_types_ignored() {
        assertThat(analyzeClass(Interface1.class).getReferencedClasses(), not(anyOf(hasItem("int"), hasItem("float"))));
    }

    @Test
    public void require_that_arrays_of_basic_types_ignored() {
        assertThat(analyzeClass(Interface1.class).getReferencedClasses(), not(anyOf(hasItem("int[]"), hasItem("int[][]"))));
    }

    @Test
    public void require_that_instance_field_types_are_included() {
        assertThat(analyzeClass(Fields.class).getReferencedClasses(), hasItem(name(String.class)));
    }

    @Test
    public void require_that_static_field_types_are_included() {
        assertThat(analyzeClass(Fields.class).getReferencedClasses(), hasItem(name(java.util.List.class)));
    }

    @Test
    public void require_that_field_annotation_is_included() {
        assertThat(analyzeClass(Fields.class).getReferencedClasses(), hasItem(name(DummyAnnotation.class)));
    }

    @Test
    public void require_that_class_annotation_is_included() {
        assertThat(analyzeClass(ClassAnnotation.class).getReferencedClasses(), hasItem(name(DummyAnnotation.class)));
    }

    @Test
    public void require_that_method_annotation_is_included() {
        assertThat(analyzeClass(MethodAnnotation.class).getReferencedClasses(), hasItem(name(DummyAnnotation.class)));
    }

    @Test
    public void require_that_export_package_annotations_are_ignored() {
        assertThat(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info"))
                .getReferencedClasses(), not(anyOf(hasItem(name(ExportPackage.class)), hasItem(name(Version.class)))));
    }

    @Test
    public void require_that_export_annotations_are_processed() {
        assertThat(
                Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info")).getExportPackage(),
                is(Optional.of(new ExportPackageAnnotation(3, 1, 4, "TEST_QUALIFIER-2"))));
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void require_that_export_annotations_are_validated() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("invalid/package-info"));
        expectedException.expectCause(throwableMessage(containsString("qualifier must follow the format")));
        expectedException.expectCause(throwableMessage(containsString("'EXAMPLE INVALID QUALIFIER'")));

        Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.invalid.package-info"));
    }

    @Test
    public void require_that_catch_clauses_are_included() {
        assertThat(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.CatchException"))
                .getReferencedClasses(), hasItem(name(LoginException.class)));
    }

    @Test
    public void require_that_class_references_are_included() {
        assertThat(Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.ClassReference"))
                .getReferencedClasses(), hasItem(name(Interface1.class)));
    }

    @Test
    public void require_that_return_type_of_method_invocations_are_included() {
        assertThat(analyzeClass(MethodInvocation.class).getReferencedClasses(), hasItem(name(Image.class)));
    }

    @Test
    public void require_that_attributes_are_included() {
        //Uses com/coremedia/iso/Utf8.class from com.googlecode.mp4parser:isoparser:1.0-RC-1
        assertThat(Analyze.analyzeClass(classFile("class/Utf8")).getReferencedClasses(),
                hasItem("org.aspectj.weaver.MethodDeclarationLineNumber"));
    }
}
