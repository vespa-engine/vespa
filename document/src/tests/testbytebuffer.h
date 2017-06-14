// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$*/

#pragma once

#include <cppunit/extensions/HelperMacros.h>

/**
   CPPUnit test case for ByteBuffer class.
*/
class ByteBuffer_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE( ByteBuffer_Test);
    CPPUNIT_TEST(test_constructors);
    CPPUNIT_TEST(test_copy_constructor);
    CPPUNIT_TEST(test_assignment_operator);
    CPPUNIT_TEST(test_slice);
    CPPUNIT_TEST(test_slice2);
    CPPUNIT_TEST(test_putGetFlip);
    CPPUNIT_TEST(test_NumberEncodings);
    CPPUNIT_TEST(test_NumberLengths);
    CPPUNIT_TEST(test_SerializableArray);
    CPPUNIT_TEST_SUITE_END();

public:
  /**
     Initialization.
  */
  void setUp() override;

protected:
    /**
       Test construction and deletion.
    */
    void test_constructors();
    void test_SerializableArray();

    /**
       Test copy constructor
    */
    void test_copy_constructor();
    /**
       Test construction and deletion.
    */
    void test_assignment_operator();

    /**
       Test the slice() method
    */
    void test_slice();

    /**
       Test the slice2() method
    */
    void test_slice2();

    /**
       Test put(), get() and flip() methods.
    */
    void test_putGetFlip();

    /**
       Test writing integers with funny encodings.
    */
    void test_NumberEncodings();

    /**
       Tests lengths of those encodings.
    */
    void test_NumberLengths();

};


