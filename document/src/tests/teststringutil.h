// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

class StringUtil_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE( StringUtil_Test);
  CPPUNIT_TEST(test_escape);
  CPPUNIT_TEST(test_unescape);
  CPPUNIT_TEST(test_printAsHex);
  CPPUNIT_TEST_SUITE_END();

public:
  void setUp() override;

protected:
  void test_escape();
  void test_unescape();
  void test_printAsHex();

};


