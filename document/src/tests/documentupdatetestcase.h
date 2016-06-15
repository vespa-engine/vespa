// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

class DocumentUpdate_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE(DocumentUpdate_Test);
  CPPUNIT_TEST(testUpdateApplySingleValue);
  CPPUNIT_TEST(testUpdateArray);
  CPPUNIT_TEST(testUpdateWeightedSet);
  CPPUNIT_TEST(testReadSerializedFile);
  CPPUNIT_TEST(testGenerateSerializedFile);
  CPPUNIT_TEST(testSetBadFieldTypes);
  CPPUNIT_TEST(testUpdateApplyNoParams);
  CPPUNIT_TEST(testUpdateApplyNoArrayValues);
  CPPUNIT_TEST(testUpdateArrayEmptyParamValue);
  CPPUNIT_TEST(testUpdateWeightedSetEmptyParamValue);
  CPPUNIT_TEST(testUpdateArrayWrongSubtype);
  CPPUNIT_TEST(testUpdateWeightedSetWrongSubtype);
  CPPUNIT_TEST(testMapValueUpdate);
  CPPUNIT_TEST(testThatDocumentUpdateFlagsIsWorking);
  CPPUNIT_TEST(testThatCreateIfNonExistentFlagIsSerialized50AndDeserialized50);
  CPPUNIT_TEST(testThatCreateIfNonExistentFlagIsSerializedAndDeserialized);
  CPPUNIT_TEST_SUITE_END();

public:
  void setUp();
  void tearDown();

protected:
  void testUpdateApplySingleValue();
  void testUpdateArray();
  void testUpdateWeightedSet();
  void testReadSerializedFile();
  void testGenerateSerializedFile();
  void testSetBadFieldTypes();
  void testUpdateApplyNoParams();
  void testUpdateApplyNoArrayValues();
  void testUpdateArrayEmptyParamValue();
  void testUpdateWeightedSetEmptyParamValue();
  void testUpdateArrayWrongSubtype();
  void testUpdateWeightedSetWrongSubtype();
  void testMapValueUpdate();
  void testThatDocumentUpdateFlagsIsWorking();
  void testThatCreateIfNonExistentFlagIsSerialized50AndDeserialized50();
  void testThatCreateIfNonExistentFlagIsSerializedAndDeserialized();
};

