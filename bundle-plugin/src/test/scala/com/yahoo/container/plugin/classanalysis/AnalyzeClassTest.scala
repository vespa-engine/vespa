// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.junit.Test
import org.scalatest.junit.{ShouldMatchersForJUnit, JUnitSuite}
import java.awt.Image
import java.awt.image.{ImagingOpException, Kernel}
import sampleclasses._
import TestUtilities._
import com.yahoo.osgi.annotation.{ExportPackage, Version}
import javax.security.auth.login.LoginException

/**
 * Tests that analysis of class files works.
 * @author  tonytv
 */
class AnalyzeClassTest extends JUnitSuite with ShouldMatchersForJUnit {
  @Test def require_that_full_class_name_is_returned() {
    analyzeClass[Base].name should be(name[Base])
  }

  @Test def require_that_base_class_is_included() {
    analyzeClass[Derived].referencedClasses should contain (name[Base])
  }

  @Test def require_that_implemented_interfaces_are_included() {
    analyzeClass[Base].referencedClasses should (contain (name[Interface1]) and contain (name[Interface2]))
  }

  @Test def require_that_interface_can_be_analyzed() {
    val classMetaData = analyzeClass[Interface1]

    classMetaData.name should be(name[Interface1])
    classMetaData.referencedClasses should contain(name[Interface2])
  }

  @Test def require_that_return_type_is_included() {
    analyzeClass[Interface1].referencedClasses should contain (name[Image])
  }

  @Test def require_that_parameters_are_included() {
    analyzeClass[Interface1].referencedClasses should contain (name[Kernel])
  }

  @Test def require_that_exceptions_are_included() {
    analyzeClass[Interface1].referencedClasses should contain (name[ImagingOpException])
  }

  @Test def require_that_basic_types_ignored() {
    analyzeClass[Interface1].referencedClasses should not (contain ("int") or contain ("float"))
  }

  @Test def require_that_arrays_of_basic_types_ignored() {
    analyzeClass[Interface1].referencedClasses should not (contain ("int[]") or contain ("int[][]"))
  }

  @Test def require_that_instance_field_types_are_included() {
    analyzeClass[Fields].referencedClasses should contain (name[String])
  }

  @Test def require_that_static_field_types_are_included() {
    analyzeClass[Fields].referencedClasses should contain (name[java.util.List[_]])
  }

  @Test def require_that_field_annotation_is_included() {
    analyzeClass[Fields].referencedClasses should contain (name[DummyAnnotation])
  }

  @Test def require_that_class_annotation_is_included() {
    analyzeClass[ClassAnnotation].referencedClasses should contain(name[DummyAnnotation])
  }

  @Test def require_that_method_annotation_is_included() {
    analyzeClass[MethodAnnotation].referencedClasses should contain(name[DummyAnnotation])
  }

  @Test def require_that_export_package_annotations_are_ignored() {
    Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info")).
      referencedClasses should not (contain (name[ExportPackage]) or contain (name[Version]))
  }

  @Test def require_that_export_annotations_are_processed() {
    Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.package-info")).
      exportPackage should be (Some(ExportPackageAnnotation(3, 1, 4, "TEST_QUALIFIER-2")))
  }

  @Test def require_that_export_annotations_are_validated() {
    val exception = intercept[RuntimeException] {
      Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.invalid.package-info"))
    }

    exception.getMessage           should include ("invalid/package-info")
    exception.getCause.getMessage  should include ("qualifier must follow the format")
    exception.getCause.getMessage  should include ("'EXAMPLE INVALID QUALIFIER'")
  }

  @Test def require_that_catch_clauses_are_included() {
    Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.CatchException")).
      referencedClasses should contain(name[LoginException])
  }

  @Test def require_that_class_references_are_included() {
    Analyze.analyzeClass(classFile("com.yahoo.container.plugin.classanalysis.sampleclasses.ClassReference")).
      referencedClasses should contain(name[Interface1])
  }

  @Test def require_that_return_type_of_method_invocations_are_included() {
    analyzeClass[MethodInvocation].referencedClasses should contain(name[Image])
  }

  @Test def require_that_attributes_are_included() {
    //Uses com/coremedia/iso/Utf8.class from com.googlecode.mp4parser:isoparser:1.0-RC-1
    Analyze.analyzeClass(classFile("class/Utf8")).referencedClasses should contain("org.aspectj.weaver.MethodDeclarationLineNumber")
  }
}
