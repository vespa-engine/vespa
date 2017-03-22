// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/test/directory_handler.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_directory_test");

using search::IndexMetaInfo;
using search::SerialNum;

namespace proton {

namespace {

vespalib::string toString(IndexMetaInfo &info) {
    vespalib::asciistream os;
    bool first = true;
    for (auto &snap : info.snapshots()) {
        if (!first) {
            os << ",";
        }
        first = false;
        if (snap.valid) {
            os << "v";
        } else {
            os << "i";
        }
        os << snap.syncToken;
    }
    return os.str();
}

bool gotAttributeDir(const std::shared_ptr<AttributeDirectory> &dir) {
    return static_cast<bool>(dir);
}

bool gotWriter(const std::unique_ptr<AttributeDirectory::Writer> &writer) {
    return static_cast<bool>(writer);
}

}

struct Fixture : public test::DirectoryHandler
{

    std::shared_ptr<AttributeDiskLayout> _diskLayout;

    Fixture()
        : test::DirectoryHandler("attributes"),
          _diskLayout(AttributeDiskLayout::create("attributes"))
    {
    }

    ~Fixture() { }

    vespalib::string getDir() { return _diskLayout->getBaseDir(); }

    vespalib::string getAttrDir(const vespalib::string &name) { return getDir() + "/" + name; }

    void assertAttributeDir(const vespalib::string &name) {
        auto fileinfo = vespalib::stat(getAttrDir(name));
        EXPECT_TRUE(static_cast<bool>(fileinfo));
        EXPECT_TRUE(fileinfo->_directory);
    }

    void assertNotAttributeDir(const vespalib::string &name) {
        auto fileinfo = vespalib::stat(getAttrDir(name));
        EXPECT_FALSE(static_cast<bool>(fileinfo));
    }

    vespalib::string getSnapshotDirComponent(SerialNum serialNum) {
        vespalib::asciistream os;
        os << "snapshot-";
        os << serialNum;
        return os.str();
    }

    vespalib::string getSnapshotDir(const vespalib::string &name, SerialNum serialNum) {
        return getAttrDir(name) + "/" + getSnapshotDirComponent(serialNum);
    }

    void assertSnapshotDir(const vespalib::string &name, SerialNum serialNum) {
        vespalib::string snapDir = getSnapshotDir(name, serialNum);
        auto fileinfo = vespalib::stat(snapDir);
        EXPECT_TRUE(static_cast<bool>(fileinfo));
        EXPECT_TRUE(fileinfo->_directory);
    }

    void assertNotSnapshotDir(const vespalib::string &name, SerialNum serialNum) {
        vespalib::string snapDir = getSnapshotDir(name, serialNum);
        auto fileinfo = vespalib::stat(snapDir);
        EXPECT_FALSE(static_cast<bool>(fileinfo));
    }

    void assertSnapshots(const vespalib::string &name, const vespalib::string &exp) {
        vespalib::string attrDir(getAttrDir(name));
        IndexMetaInfo info(attrDir);
        info.load();
        vespalib::string act = toString(info);
        EXPECT_EQUAL(exp, act);
    }

    auto createAttributeDir(const vespalib::string &name) { return _diskLayout->createAttributeDir(name); }
    auto getAttributeDir(const vespalib::string &name) { return _diskLayout->getAttributeDir(name); }
    void removeAttributeDir(const vespalib::string &name, SerialNum serialNum) { return _diskLayout->removeAttributeDir(name, serialNum); }
    auto createFooAttrDir() { return createAttributeDir("foo"); }
    auto getFooAttrDir() { return getAttributeDir("foo"); }
    void removeFooAttrDir() { removeAttributeDir("foo", 10); }
    void assertNotGetAttributeDir(const vespalib::string &name) {
        auto dir = getAttributeDir(name);
        EXPECT_FALSE(static_cast<bool>(dir));
    }
    void assertGetAttributeDir(const vespalib::string &name, std::shared_ptr<AttributeDirectory> expDir) {
        auto dir = getAttributeDir(name);
        EXPECT_TRUE(static_cast<bool>(dir));
        EXPECT_EQUAL(expDir, dir);
    }
    void assertCreateAttributeDir(const vespalib::string &name, std::shared_ptr<AttributeDirectory> expDir) {
        auto dir = getAttributeDir(name);
        EXPECT_TRUE(static_cast<bool>(dir));
        EXPECT_EQUAL(expDir, dir);
    }

    void setupFooSnapshots(SerialNum serialNum) {
        auto dir = createFooAttrDir();
        EXPECT_TRUE(gotAttributeDir(dir));
        auto writer = dir->getWriter();
        writer->createInvalidSnapshot(serialNum);
        writer->markValidSnapshot(serialNum);
        TEST_DO(assertAttributeDir("foo"));
    }

    void invalidateFooSnapshots(bool removeDir) {
        auto dir = createFooAttrDir();
        auto writer = dir->getWriter();
        writer->invalidateOldSnapshots(10);
        writer->removeInvalidSnapshots(removeDir);
        TEST_DO(assertGetAttributeDir("foo", dir));
    }


    void testRemoveSnapshots(bool removeDir) {
        TEST_DO(setupFooSnapshots(5));
        TEST_DO(invalidateFooSnapshots(removeDir));
        if (removeDir) {
            TEST_DO(assertNotAttributeDir("foo"));
        } else {
            TEST_DO(assertAttributeDir("foo"));
        }
    }

    void makeInvalidSnapshot(SerialNum serialNum) {
        auto dir = createFooAttrDir();
        EXPECT_TRUE(gotAttributeDir(dir));
        dir->getWriter()->createInvalidSnapshot(serialNum);
    }

    void makeValidSnapshot(SerialNum serialNum) {
        auto dir = createFooAttrDir();
        auto writer = dir->getWriter();
        writer->createInvalidSnapshot(serialNum);
        writer->markValidSnapshot(serialNum);
    }

};

TEST_F("Test that we can create attribute directory", Fixture)
{
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
}


TEST_F("Test that attribute directory is persistent", Fixture)
{
    TEST_DO(f.assertNotGetAttributeDir("foo"));
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
    TEST_DO(f.assertGetAttributeDir("foo", dir));
}

TEST_F("Test that we can remove attribute directory", Fixture)
{
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
    TEST_DO(f.assertGetAttributeDir("foo", dir));
    f.removeFooAttrDir();
    TEST_DO(f.assertNotGetAttributeDir("foo"));
}

TEST_F("Test that we can create attribute directory with one snapshot", Fixture)
{
    TEST_DO(f.assertNotGetAttributeDir("foo"));
    TEST_DO(f.assertNotAttributeDir("foo"));
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
    TEST_DO(f.assertNotAttributeDir("foo"));
    dir->getWriter()->createInvalidSnapshot(1);
    TEST_DO(f.assertAttributeDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "i1"));
}

TEST_F("Test that we can prune attribute snapshots", Fixture)
{
    auto dir = f.createFooAttrDir();
    TEST_DO(f.assertNotAttributeDir("foo"));
    auto writer = dir->getWriter();
    writer->createInvalidSnapshot(2);
    writer->markValidSnapshot(2);
    writer->createInvalidSnapshot(4);
    writer->markValidSnapshot(4);
    writer.reset();
    TEST_DO(f.assertAttributeDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "v2,v4"));
    dir->getWriter()->invalidateOldSnapshots();
    TEST_DO(f.assertSnapshots("foo", "i2,v4"));
    dir->getWriter()->removeInvalidSnapshots(false);
    TEST_DO(f.assertSnapshots("foo", "v4"));
}

TEST_F("Test that attribute directory is not removed if valid snapshots remain", Fixture)
{
    TEST_DO(f.setupFooSnapshots(20));
    auto dir = f.getFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
    dir->getWriter()->createInvalidSnapshot(30);
    TEST_DO(f.assertSnapshots("foo", "v20,i30"));
    TEST_DO(f.removeFooAttrDir());
    TEST_DO(f.assertGetAttributeDir("foo", dir));
    TEST_DO(f.assertAttributeDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "v20"));
}

TEST_F("Test that attribute directory is removed if no valid snapshots remain", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    auto dir = f.getFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(dir));
    dir->getWriter()->createInvalidSnapshot(30);
    TEST_DO(f.assertSnapshots("foo", "v5,i30"));
    TEST_DO(f.removeFooAttrDir());
    TEST_DO(f.assertNotGetAttributeDir("foo"));
    TEST_DO(f.assertNotAttributeDir("foo"));
}

TEST_F("Test that attribute directory is not removed due to pruning and disk dir is kept", Fixture)
{
    TEST_DO(f.testRemoveSnapshots(false));
}

TEST_F("Test that attribute directory is not removed due to pruning but disk dir is removed", Fixture)
{
    TEST_DO(f.testRemoveSnapshots(true));
}

TEST("Test that initial state tracks disk layout")
{
    vespalib::mkdir("attributes");
    vespalib::mkdir("attributes/foo");
    vespalib::mkdir("attributes/bar");
    IndexMetaInfo fooInfo("attributes/foo");
    IndexMetaInfo barInfo("attributes/bar");
    fooInfo.addSnapshot({true, 4, "snapshot-4"});
    fooInfo.addSnapshot({false, 8, "snapshot-8"});
    fooInfo.save();
    barInfo.addSnapshot({false, 5, "snapshot-5"});
    barInfo.save();
    Fixture f;
    TEST_DO(f.assertAttributeDir("foo"));
    TEST_DO(f.assertAttributeDir("bar"));
    auto foodir = f.getFooAttrDir();
    EXPECT_TRUE(gotAttributeDir(foodir));
    auto bardir = f.getAttributeDir("bar");
    EXPECT_TRUE(gotAttributeDir(bardir));
    TEST_DO(f.assertNotGetAttributeDir("baz"));
    TEST_DO(f.assertNotAttributeDir("baz"));
    TEST_DO(f.assertSnapshots("foo", "v4,i8"));
    TEST_DO(f.assertSnapshots("bar", "i5"));
    f.makeInvalidSnapshot(12);
    f.makeValidSnapshot(16);
    TEST_DO(f.assertSnapshots("foo", "v4,i8,i12,v16"));
}

TEST_F("Test that snapshot removal removes correct snapshot directory", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    vespalib::mkdir(f.getSnapshotDir("foo", 5));
    vespalib::mkdir(f.getSnapshotDir("foo", 6));
    TEST_DO(f.assertSnapshotDir("foo", 5));
    TEST_DO(f.assertSnapshotDir("foo", 6));
    TEST_DO(f.invalidateFooSnapshots(false));
    TEST_DO(f.assertNotSnapshotDir("foo", 5));
    TEST_DO(f.assertSnapshotDir("foo", 6));
    TEST_DO(f.invalidateFooSnapshots(true));
    TEST_DO(f.assertNotSnapshotDir("foo", 5));
    TEST_DO(f.assertNotSnapshotDir("foo", 6));
}

TEST_F("Test that we can get nonblocking writer", Fixture)
{
    auto dir = f.createFooAttrDir();
    auto writer = dir->getWriter();
    EXPECT_TRUE(gotWriter(writer));
    auto writer2 = dir->tryGetWriter();
    EXPECT_FALSE(gotWriter(writer2));
    writer.reset();
    writer2 = dir->tryGetWriter();
    EXPECT_TRUE(gotWriter(writer2));
    writer = dir->tryGetWriter();
    EXPECT_FALSE(gotWriter(writer));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
