// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/forcelink.h>
#include <cppunit/extensions/HelperMacros.h>

namespace document {

struct ForceLinkTest : public CppUnit::TestFixture {
  void testUsage();

  CPPUNIT_TEST_SUITE(ForceLinkTest);
  CPPUNIT_TEST(testUsage);
  CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(ForceLinkTest);

void ForceLinkTest::testUsage()
{
    ForceLink link;
}

} // document
