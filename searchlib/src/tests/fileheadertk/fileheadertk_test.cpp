// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP("fileheadertk_test");

using namespace search;

TEST("testVersionTags")
{
    vespalib::FileHeader header;
    FileHeaderTk::addVersionTags(header);

    FastOS_File file;
    ASSERT_TRUE(file.OpenWriteOnlyTruncate("versiontags.dat"));
    EXPECT_EQUAL(header.getSize(), header.writeFile(file));

    EXPECT_EQUAL(8u, header.getNumTags());
    EXPECT_TRUE(header.hasTag("version-arch"));
    EXPECT_TRUE(header.hasTag("version-builder"));
    EXPECT_TRUE(header.hasTag("version-component"));
    EXPECT_TRUE(header.hasTag("version-date"));
    EXPECT_TRUE(header.hasTag("version-system"));
    EXPECT_TRUE(header.hasTag("version-system-rev"));
    EXPECT_TRUE(header.hasTag("version-tag"));
    EXPECT_TRUE(header.hasTag("version-pkg"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
