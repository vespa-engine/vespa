// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey.classvisitor

import com.yahoo.container.servlet.jersey.{ResourceOrProviderClassVisitor, classvisitor}
import org.junit.{Assert, Test}
import org.objectweb.asm.ClassReader

import Assert.assertThat
import org.hamcrest.CoreMatchers.is

import scala.reflect.ClassTag

class ResourceOrProviderClassVisitorTest {
  @Test
  def resource_is_detected() {
    assert_is_accepted[classvisitor.Resource]
  }

  @Test
  def provider_is_detected() {
    assert_is_accepted[classvisitor.Provider]
  }

  @Test
  def inner_class_is_ignored() {
    assert_is_ignored[classvisitor.InnerClass#Inner]
  }

  @Test
  def nested_public_class_is_detected() {
    assert_is_accepted[classvisitor.NestedClass.Nested]
  }

  @Test
  def nested_non_public_class_is_ignored() {
    assert_is_ignored[classvisitor.NonPublicNestedClass.Nested]
  }

  @Test
  def resource_with_multiple_annotations_is_detected() {
    assert_is_accepted[classvisitor.ResourceWithMultipleAnnotations]
  }

  def interface_is_ignored() {
    assert_is_ignored[classvisitor.InterfaceResource]
  }

  @Test
  def abstract_class_is_ignored() {
    assert_is_ignored[classvisitor.AbstractResource]
  }

  @Test
  def className_is_equal_to_getName() {
    assertThat(analyzeClass[classvisitor.Resource].getClassName, is(classOf[classvisitor.Resource].getName))
  }

  def assert_is_accepted[T: ClassTag] {
    Assert.assertTrue(className[T] + " was not accepted",
      analyzeClass[T].isJerseyClass)
  }

  def assert_is_ignored[T: ClassTag] {
    Assert.assertFalse(className[T] + " was not ignored",
      analyzeClass[T].isJerseyClass)
  }

  def analyzeClass[T: ClassTag] = {
    ResourceOrProviderClassVisitor.visit(new ClassReader(className[T]))
  }

  def className[T: ClassTag] = implicitly[ClassTag[T]].runtimeClass.getName
}


