// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "util.h"
#include <vespa/storage/common/hostreporter/versionreporter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using namespace ::testing;

namespace storage {
namespace {

using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;

}

TEST(VersionReporterTest, version_reporter) {
    VersionReporter versionReporter;
    vespalib::Slime slime;
    util::reporterToSlime(versionReporter,  slime);
    std::string version = slime.get()["vtag"]["version"].asString().make_string().c_str();
    EXPECT_GT(version.size(), 2);
    EXPECT_THAT(version, HasSubstr("."));
}

} // storage
