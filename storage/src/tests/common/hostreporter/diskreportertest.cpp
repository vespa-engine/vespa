// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/storage/common/hostreporter/diskreporter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include "util.h"

LOG_SETUP(".test.diskreporter");

namespace storage {

struct DiskReporterTest : public CppUnit::TestFixture
{
    void testDiskReporter();

    CPPUNIT_TEST_SUITE(DiskReporterTest);
    CPPUNIT_TEST(testDiskReporter);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DiskReporterTest);

void
DiskReporterTest::testDiskReporter()
{
    DiskReporter diskReporter;
    vespalib::Slime slime;
    util::reporterToSlime(diskReporter,  slime);
    CPPUNIT_ASSERT(0 < slime.get()["disk"].toString().size());
}
} // storage
