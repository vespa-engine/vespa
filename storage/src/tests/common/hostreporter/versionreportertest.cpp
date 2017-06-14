// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
#include <vespa/storage/common/hostreporter/versionreporter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include "util.h"

LOG_SETUP(".test.versionreporter");

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
}

struct VersionReporterTest : public CppUnit::TestFixture
{
    void testVersionReporter();

    CPPUNIT_TEST_SUITE(VersionReporterTest);
    CPPUNIT_TEST(testVersionReporter);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(VersionReporterTest);

void
VersionReporterTest::testVersionReporter()
{
    VersionReporter versionReporter;
    vespalib::Slime slime;
    util::reporterToSlime(versionReporter,  slime);
    std::string version = slime.get()["vtag"]["version"].asString().make_string().c_str();
    CPPUNIT_ASSERT(version.length() > 2);
    CPPUNIT_ASSERT(version.find(".") > 0);
}
} // storage
