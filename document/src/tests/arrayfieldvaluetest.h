// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

class ArrayFieldValue_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE( ArrayFieldValue_Test);
    CPPUNIT_TEST(testArray);
    CPPUNIT_TEST(testArray2);
    CPPUNIT_TEST(testArrayRaw);
    CPPUNIT_TEST(testTextSerialize);
    CPPUNIT_TEST(testArrayRemove);
    CPPUNIT_TEST_SUITE_END();

public:
    void setUp();

    void tearDown();
protected:
    void testArray();
    void testArray2();
    void testArrayRaw();
    void testTextSerialize();
    void testArrayRemove();
};


