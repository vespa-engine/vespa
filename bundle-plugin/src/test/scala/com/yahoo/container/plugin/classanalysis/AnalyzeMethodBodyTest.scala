// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis

import org.scalatest.junit.{JUnitSuite, ShouldMatchersForJUnit}
import sampleclasses._
import TestUtilities._
import org.junit.Test
import java.io.PrintStream

/**
 * Tests that classes used in method bodies are included in the imports list.
 * @author  tonytv
 */
class AnalyzeMethodBodyTest extends JUnitSuite with ShouldMatchersForJUnit {
  @Test def require_that_class_of_locals_are_included() {
    analyzeClass[Methods].referencedClasses should contain(name[Base])
  }

  @Test def require_that_class_of_locals_in_static_method_are_included() {
    analyzeClass[Methods].referencedClasses should contain(name[Derived])
  }

  @Test def require_that_field_references_are_included() {
    analyzeClass[Methods].referencedClasses should (contain (name[java.util.List[_]]) and contain (name[Fields]))
  }

  @Test def require_that_class_owning_field_is_included() {
    analyzeClass[Methods].referencedClasses should contain (name[System])
  }

  @Test def require_that_class_containing_method_is_included() {
    analyzeClass[Methods].referencedClasses should contain (name[PrintStream])
  }

  @Test def require_that_element_of_new_multidimensional_array_is_included() {
    analyzeClass[Methods].referencedClasses should contain (name[Interface1])
  }

  @Test def require_that_basic_arrays_are_not_included() {
    analyzeClass[Methods].referencedClasses should not (contain("int[]"))
  }

  @Test def require_that_container_generic_parameters_are_included() {
    analyzeClass[Methods].referencedClasses should contain(name[Dummy])
  }

  @Test def require_that_class_owning_method_handler_is_included() {
    analyzeClass[Methods].referencedClasses should contain(name[ClassWithMethod])
  }
}
