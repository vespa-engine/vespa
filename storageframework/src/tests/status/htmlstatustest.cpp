// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>

namespace storage {
namespace framework {

struct HtmlStatusTest : public CppUnit::TestFixture {

    void testHtmlStatus();

    CPPUNIT_TEST_SUITE(HtmlStatusTest);
    CPPUNIT_TEST(testHtmlStatus);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(HtmlStatusTest);

void
HtmlStatusTest::testHtmlStatus()
{
}

} // framework
} // storage
