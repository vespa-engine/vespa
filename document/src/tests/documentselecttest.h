// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/
#pragma once

#include <cppunit/extensions/HelperMacros.h>

class DocumentSelect_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE( DocumentSelect_Test);
  CPPUNIT_TEST(testEquals);
  CPPUNIT_TEST(testLt);
  CPPUNIT_TEST(testGt);
  CPPUNIT_TEST(testAnd);
  CPPUNIT_TEST(testOr);
  CPPUNIT_TEST(testNot);
  CPPUNIT_TEST(testConfig1);
  CPPUNIT_TEST(testConfig2);
  CPPUNIT_TEST_SUITE_END();

public:
  void setUp();

  void tearDown();
protected:
  void testEquals();
  void testLt();
  void testGt();
  void testAnd();
  void testOr();
  void testNot();
  void testConfig1();
  void testConfig2();
};


