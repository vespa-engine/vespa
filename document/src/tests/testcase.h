// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

class Document_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE( Document_Test);
  CPPUNIT_TEST(testShit);
  CPPUNIT_TEST_SUITE_END();

protected:
  void testShit();
};


