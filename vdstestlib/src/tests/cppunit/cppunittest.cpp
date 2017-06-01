// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>

namespace vespalib {

struct CppunitTest : public CppUnit::TestFixture {

    void testSomething();

    CPPUNIT_TEST_SUITE(CppunitTest);
    CPPUNIT_TEST(testSomething);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(CppunitTest);

void
CppunitTest::testSomething()
{
    CPPUNIT_ASSERT_EQUAL_MESSAGE("hmm", "foo", "foo");
}

} // vespalib
