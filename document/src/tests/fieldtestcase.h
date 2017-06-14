// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

class Field_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE( Field_Test);
  CPPUNIT_TEST(testserialize);
  CPPUNIT_TEST_SUITE_END();

public:
  void setUp();

protected:
  void testserialize();
};


