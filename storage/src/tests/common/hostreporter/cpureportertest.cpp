// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/storage/common/hostreporter/cpureporter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include "util.h"

LOG_SETUP(".test.cpureporter");

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
}

struct CpuReporterTest : public CppUnit::TestFixture
{
    void testCpuReporter();

    CPPUNIT_TEST_SUITE(CpuReporterTest);
    CPPUNIT_TEST(testCpuReporter);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(CpuReporterTest);

void
CpuReporterTest::testCpuReporter()
{
    CpuReporter cpuReporter;
    vespalib::Slime slime;
    util::reporterToSlime(cpuReporter,  slime);
    CPPUNIT_ASSERT(1.0 <= slime.get()["cpu"]["context switches"].asDouble());
    CPPUNIT_ASSERT(1.0 <= slime.get()["cpu"]["cputotal"]["user"].asDouble());
    CPPUNIT_ASSERT(1.0 <= slime.get()["cpu"]["cputotal"]["user"].asDouble());
    CPPUNIT_ASSERT(1.0 <= slime.get()["cpu"]["cputotal"]["user"].asDouble());
}
} // storage
