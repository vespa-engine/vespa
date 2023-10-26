// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/indexmetainfo.h>

using search::IndexMetaInfo;

using Snap = IndexMetaInfo::Snapshot;

TEST_SETUP(Test)

int
Test::Main()
{
    TEST_INIT("indexmetainfo_test");
    { // load pregenerated file
        IndexMetaInfo info(TEST_PATH(""));
        EXPECT_TRUE(info.load());
        ASSERT_TRUE(info.snapshots().size() == 4);
        EXPECT_TRUE(info.snapshots()[0].valid);
        EXPECT_TRUE(info.snapshots()[0].syncToken == 50);
        EXPECT_TRUE(info.snapshots()[0].dirName == "foo");
        EXPECT_TRUE(!info.snapshots()[1].valid);
        EXPECT_TRUE(info.snapshots()[1].syncToken == 100);
        EXPECT_TRUE(info.snapshots()[1].dirName == "bar");
        EXPECT_TRUE(info.snapshots()[2].valid);
        EXPECT_TRUE(info.snapshots()[2].syncToken == 200);
        EXPECT_TRUE(info.snapshots()[2].dirName == "baz");
        EXPECT_TRUE(!info.snapshots()[3].valid);
        EXPECT_TRUE(info.snapshots()[3].syncToken == 500);
        EXPECT_TRUE(info.snapshots()[3].dirName == "last");
        {
            Snap s = info.getBestSnapshot();
            EXPECT_TRUE(s.valid);
            EXPECT_TRUE(s.syncToken == 200);
            EXPECT_TRUE(s.dirName == "baz");
        }
        {
            Snap s = info.getSnapshot(100);
            EXPECT_TRUE(!s.valid);
            EXPECT_TRUE(s.syncToken == 100);
            EXPECT_TRUE(s.dirName == "bar");
        }
        {
            Snap s = info.getSnapshot(666);
            EXPECT_TRUE(!s.valid);
            EXPECT_TRUE(s.syncToken == 0);
            EXPECT_TRUE(s.dirName == "");
        }
        {
            EXPECT_TRUE(info.invalidateSnapshot(200));
            Snap s = info.getBestSnapshot();
            EXPECT_TRUE(s.valid);
            EXPECT_TRUE(s.syncToken == 50);
            EXPECT_TRUE(s.dirName == "foo");
        }
        {
            EXPECT_TRUE(info.invalidateSnapshot(50));
            Snap s = info.getBestSnapshot();
            EXPECT_TRUE(!s.valid);
            EXPECT_TRUE(s.syncToken == 0);
            EXPECT_TRUE(s.dirName == "");
        }
        {
            EXPECT_TRUE(info.validateSnapshot(500));
            Snap s = info.getBestSnapshot();
            EXPECT_TRUE(s.valid);
            EXPECT_TRUE(s.syncToken == 500);
            EXPECT_TRUE(s.dirName == "last");
        }
        {
            EXPECT_TRUE(!info.invalidateSnapshot(666));
            EXPECT_TRUE(!info.validateSnapshot(666));
        }
        {
            info.clear();
            EXPECT_TRUE(info.snapshots().size() == 0);
            Snap s = info.getBestSnapshot();
            EXPECT_TRUE(!s.valid);
            EXPECT_TRUE(s.syncToken == 0);
            EXPECT_TRUE(s.dirName == "");
        }
    }
    { // load file that does not exist
        IndexMetaInfo info(".");
        EXPECT_TRUE(!info.load("file-not-present.txt"));
    }
    { // load files with errors should fail
        IndexMetaInfo info(TEST_PATH(""));
        EXPECT_TRUE(!info.load("bogus1.txt"));
        EXPECT_TRUE(!info.load("bogus2.txt"));
        EXPECT_TRUE(!info.load("bogus3.txt"));
        EXPECT_TRUE(!info.load("bogus4.txt"));
        EXPECT_TRUE(!info.load("bogus5.txt"));
        EXPECT_TRUE(!info.load("bogus6.txt"));
        EXPECT_TRUE(!info.load("bogus7.txt"));
        EXPECT_TRUE(!info.load("bogus8.txt"));
        EXPECT_TRUE(!info.load("bogus9.txt"));
        EXPECT_TRUE(!info.load("bogus10.txt"));
    }
    { // save/load/save/load/save/load test
        std::string file("test-save.txt");
        IndexMetaInfo a(".");
        IndexMetaInfo b(".");
        EXPECT_TRUE(a.addSnapshot(Snap(true, 50, "foo")));
        EXPECT_TRUE(a.addSnapshot(Snap(false, 100, "bar")));
        EXPECT_TRUE(!a.addSnapshot(Snap(false, 100, "bar")));
        EXPECT_TRUE(a.save(file));
        EXPECT_TRUE(b.load(file));
        ASSERT_TRUE(b.snapshots().size() == 2);
        EXPECT_TRUE(b.snapshots()[0] == Snap(true, 50, "foo"));
        EXPECT_TRUE(b.snapshots()[1] == Snap(false, 100, "bar"));
        EXPECT_TRUE(a.save(file));
        EXPECT_TRUE(b.load(file));
        ASSERT_TRUE(b.snapshots().size() == 2);
        EXPECT_TRUE(b.snapshots()[0] == Snap(true, 50, "foo"));
        EXPECT_TRUE(b.snapshots()[1] == Snap(false, 100, "bar"));
        a.removeSnapshot(100);
        EXPECT_TRUE(a.save(file));
        EXPECT_TRUE(b.load(file));
        ASSERT_TRUE(b.snapshots().size() == 1);
        EXPECT_TRUE(b.snapshots()[0] == Snap(true, 50, "foo"));
    }
    TEST_DONE();
}
