// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen

import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.hamcrest.CoreMatchers.is
import java.io.StringReader
import ConfigGenerator.createClassName
import JavaClassBuilder.createUniqueSymbol
import org.junit.{Ignore, Test}

/**
 * @author gjoranv
 */
class JavaClassBuilderTest {

  @Ignore
  @Test
  def visual_inspection_of_generated_class() {
    val testDefinition =
      """version=1
        |namespace=test
        |p path
        |pathArr[] path
        |f file
        |fileArr[] file
        |i int default=0
        |# A long value
        |l long default=0
        |s string default=""
        |b bool
        |# An enum value
        |e enum {A, B, C}
        |intArr[] int
        |boolArr[] bool
        |enumArr[] enum {FOO, BAR}
        |intMap{} int
        |# A struct
        |# with multi-line
        |# comment and "quotes".
        |myStruct.i int
        |myStruct.s string
        |# An inner array
        |myArr[].i int
        |myArr[].newStruct.s string
        |myArr[].newStruct.b bool
        |myArr[].intArr[] int
        |# An inner map
        |myMap{}.i int
        |myMap{}.newStruct.s string
        |myMap{}.newStruct.b bool
        |myMap{}.intArr[] int
        |intMap{} int
        |""".stripMargin

    val parser = new DefParser("test", new StringReader(testDefinition))
    val root = parser.getTree
    val builder = new JavaClassBuilder(root, parser.getNormalizedDefinition, null)
    val configClass = builder.getConfigClass("TestConfig")
    print(configClass)
  }

  @Test
  def testCreateUniqueSymbol() {
    val testDefinition =
    """version=1
      |namespace=test
      |m int
      |n int
    """.stripMargin
    val root = new DefParser("test", new StringReader(testDefinition)).getTree

    assertThat(createUniqueSymbol(root, "foo"), is("f"))
    assertThat(createUniqueSymbol(root, "name"), is("na"))
    assertTrue(createUniqueSymbol(root, "m").startsWith(ReservedWords.INTERNAL_PREFIX + "m"))

    // The basis string is not a legal return value, even if unique, to avoid multiple symbols
    // with the same name if the same basis string is given twice.
    assertTrue(createUniqueSymbol(root, "my").startsWith(ReservedWords.INTERNAL_PREFIX + "my"))
  }

  @Test
  def testCreateClassName() {
    assertThat(createClassName("simple"), is("SimpleConfig"))
    assertThat(createClassName("a"), is("AConfig"))
    assertThat(createClassName("a-b-c"), is("ABCConfig"))
    assertThat(createClassName("a-1-2b"), is("A12bConfig"))
    assertThat(createClassName("my-app"), is("MyAppConfig"))
    assertThat(createClassName("MyApp"), is("MyAppConfig"))
  }

  @Test(expected=classOf[CodegenRuntimeException])
  def testIllegalClassName() {
    createClassName("+illegal")
  }

}
