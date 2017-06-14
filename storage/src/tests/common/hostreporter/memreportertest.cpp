// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/storage/common/hostreporter/memreporter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include "util.h"

LOG_SETUP(".test.memreporter");

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
}

struct MemReporterTest : public CppUnit::TestFixture
{
    void testMemReporter();

    CPPUNIT_TEST_SUITE(MemReporterTest);
    CPPUNIT_TEST(testMemReporter);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemReporterTest);

void
MemReporterTest::testMemReporter()
{
    MemReporter  memReporter;
    vespalib::Slime slime;
    util::reporterToSlime(memReporter,  slime);
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["total memory"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["free memory"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["disk cache"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["active memory"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["inactive memory"].asLong());
    CPPUNIT_ASSERT(0 <= slime.get()["memory"]["swap total"].asLong());
    CPPUNIT_ASSERT(0 <= slime.get()["memory"]["swap free"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["memory"]["dirty"].asLong());
}
} // storage
