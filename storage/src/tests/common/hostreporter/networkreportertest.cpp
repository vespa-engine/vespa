// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/storage/common/hostreporter/networkreporter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include "util.h"

LOG_SETUP(".test.networkreporter");

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
}

struct NetworkReporterTest : public CppUnit::TestFixture
{
    void testNetworkReporter();

    CPPUNIT_TEST_SUITE(NetworkReporterTest);
    CPPUNIT_TEST(testNetworkReporter);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(NetworkReporterTest);

void
NetworkReporterTest::testNetworkReporter()
{
    NetworkReporter networkReporter;
    vespalib::Slime slime;
    util::reporterToSlime(networkReporter,  slime);
    CPPUNIT_ASSERT(0 < slime.get()["network"]["lo"]["input"]["bytes"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["network"]["lo"]["input"]["packets"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["network"]["lo"]["output"]["bytes"].asLong());
    CPPUNIT_ASSERT(0 < slime.get()["network"]["lo"]["output"]["packets"].asLong());
}
} // storage
