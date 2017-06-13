// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vdslib/container/parameters.h>
#include <cppunit/extensions/HelperMacros.h>

using document::DocumentTypeRepo;
using namespace vdslib;

class Parameters_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(Parameters_Test);
    CPPUNIT_TEST(testParameters);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testParameters();
};

#ifndef FASTOS_NO_THREADS
CPPUNIT_TEST_SUITE_REGISTRATION(Parameters_Test);
#endif

void
Parameters_Test::testParameters()
{
    Parameters par;
    par.set("fast", "overture");
    par.set("overture", "yahoo");
    par.set("number", 6);
    par.set("long", 8589934590L);
    par.set("double", 0.25);
    std::unique_ptr<document::ByteBuffer> buffer(par.serialize());

    buffer->flip();
    DocumentTypeRepo repo;
    Parameters par2(repo, *buffer);

    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("overture"), par2.get("fast"));
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("yahoo"), par2.get("overture"));
    std::string stringDefault = "wayne corp";
    int numberDefault = 123;
    long longDefault = 456;
    double doubleDefault = 0.5;
    CPPUNIT_ASSERT_EQUAL(6, par2.get("number", numberDefault));
    CPPUNIT_ASSERT_EQUAL(8589934590L, par2.get("long", longDefault));
    CPPUNIT_ASSERT_DOUBLES_EQUAL(0.25, par2.get("double", doubleDefault), 0.0001);

    CPPUNIT_ASSERT_EQUAL(stringDefault, par2.get("nonexistingstring", stringDefault));
    CPPUNIT_ASSERT_EQUAL(numberDefault, par2.get("nonexistingnumber", numberDefault));
    CPPUNIT_ASSERT_EQUAL(longDefault,   par2.get("nonexistinglong", longDefault));
    CPPUNIT_ASSERT_EQUAL(doubleDefault, par2.get("nonexistingdouble", doubleDefault));
}
