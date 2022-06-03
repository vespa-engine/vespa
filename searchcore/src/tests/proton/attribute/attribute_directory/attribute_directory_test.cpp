// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("attribute_directory_test");

using search::IndexMetaInfo;
using search::SerialNum;
using search::test::DirectoryHandler;

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

bool hasAttributeDir(const std::shared_ptr<AttributeDirectory> &dir) {
    return static_cast<bool>(dir);
}

bool hasWriter(const std::unique_ptr<AttributeDirectory::Writer> &writer) {
    return static_cast<bool>(writer);
}

}

struct Fixture : public DirectoryHandler
{

    std::shared_ptr<AttributeDiskLayout> _diskLayout;

    Fixture()
        : DirectoryHandler("attributes"),
          _diskLayout(AttributeDiskLayout::create("attributes"))
    {
    }

    ~Fixture() { }

    vespalib::string getDir() { return _diskLayout->getBaseDir(); }

    vespalib::string getAttrDir(const vespalib::string &name) { return getDir() + "/" + name; }

    void assertDiskDir(const vespalib::string &name) {
        EXPECT_TRUE(std::filesystem::is_directory(std::filesystem::path(name)));
    }

    void assertAttributeDiskDir(const vespalib::string &name) {
        TEST_DO(assertDiskDir(getAttrDir(name)));
    }

    void assertNotDiskDir(const vespalib::string &name) {
        EXPECT_FALSE(std::filesystem::exists(std::filesystem::path(name)));
    }

    void assertNotAttributeDiskDir(const vespalib::string &name) {
        TEST_DO(assertNotDiskDir(getAttrDir(name)));
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
        TEST_DO(assertDiskDir(getSnapshotDir(name, serialNum)));
    }

    void assertNotSnapshotDir(const vespalib::string &name, SerialNum serialNum) {
        TEST_DO(assertNotDiskDir(getSnapshotDir(name, serialNum)));
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
    void removeFooAttrDir(SerialNum serialNum) { removeAttributeDir("foo", serialNum); }
    void assertNotGetAttributeDir(const vespalib::string &name) {
        auto dir = getAttributeDir(name);
        EXPECT_FALSE(static_cast<bool>(dir));
        TEST_DO(assertNotAttributeDiskDir(name));
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
        EXPECT_TRUE(hasAttributeDir(dir));
        auto writer = dir->getWriter();
        writer->createInvalidSnapshot(serialNum);
        std::filesystem::create_directory(std::filesystem::path(writer->getSnapshotDir(serialNum)));
        writer->markValidSnapshot(serialNum);
        TEST_DO(assertAttributeDiskDir("foo"));
    }

    void invalidateFooSnapshots(bool removeDir) {
        auto dir = createFooAttrDir();
        auto writer = dir->getWriter();
        writer->invalidateOldSnapshots(10);
        writer->removeInvalidSnapshots();
        if (removeDir) {
            writer->removeDiskDir();
        }
        TEST_DO(assertGetAttributeDir("foo", dir));
    }

    void makeInvalidSnapshot(SerialNum serialNum) {
        auto dir = createFooAttrDir();
        EXPECT_TRUE(hasAttributeDir(dir));
        dir->getWriter()->createInvalidSnapshot(serialNum);
    }

    void makeValidSnapshot(SerialNum serialNum) {
        auto dir = createFooAttrDir();
        auto writer = dir->getWriter();
        writer->createInvalidSnapshot(serialNum);
        std::filesystem::create_directory(std::filesystem::path(writer->getSnapshotDir(serialNum)));
        writer->markValidSnapshot(serialNum);
    }

};

TEST_F("Test that we can create attribute directory", Fixture)
{
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
}


TEST_F("Test that attribute directory is persistent", Fixture)
{
    TEST_DO(f.assertNotGetAttributeDir("foo"));
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
    TEST_DO(f.assertGetAttributeDir("foo", dir));
}

TEST_F("Test that we can remove attribute directory", Fixture)
{
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
    TEST_DO(f.assertGetAttributeDir("foo", dir));
    f.removeFooAttrDir(10);
    TEST_DO(f.assertNotGetAttributeDir("foo"));
}

TEST_F("Test that we can create attribute directory with one snapshot", Fixture)
{
    TEST_DO(f.assertNotGetAttributeDir("foo"));
    auto dir = f.createFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
    TEST_DO(f.assertNotAttributeDiskDir("foo"));
    dir->getWriter()->createInvalidSnapshot(1);
    TEST_DO(f.assertAttributeDiskDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "i1"));
}

TEST_F("Test that we can prune attribute snapshots", Fixture)
{
    auto dir = f.createFooAttrDir();
    TEST_DO(f.assertNotAttributeDiskDir("foo"));
    auto writer = dir->getWriter();
    writer->createInvalidSnapshot(2);
    std::filesystem::create_directory(std::filesystem::path(writer->getSnapshotDir(2)));
    writer->markValidSnapshot(2);
    writer->createInvalidSnapshot(4);
    std::filesystem::create_directory(std::filesystem::path(writer->getSnapshotDir(4)));
    writer->markValidSnapshot(4);
    writer.reset();
    TEST_DO(f.assertAttributeDiskDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "v2,v4"));
    dir->getWriter()->invalidateOldSnapshots();
    TEST_DO(f.assertSnapshots("foo", "i2,v4"));
    dir->getWriter()->removeInvalidSnapshots();
    TEST_DO(f.assertSnapshots("foo", "v4"));
}

TEST_F("Test that attribute directory is not removed if valid snapshots remain", Fixture)
{
    TEST_DO(f.setupFooSnapshots(20));
    auto dir = f.getFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
    dir->getWriter()->createInvalidSnapshot(30);
    TEST_DO(f.assertSnapshots("foo", "v20,i30"));
    TEST_DO(f.removeFooAttrDir(10));
    TEST_DO(f.assertGetAttributeDir("foo", dir));
    TEST_DO(f.assertAttributeDiskDir("foo"));
    TEST_DO(f.assertSnapshots("foo", "v20"));
}

TEST_F("Test that attribute directory is removed if no valid snapshots remain", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    auto dir = f.getFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(dir));
    dir->getWriter()->createInvalidSnapshot(30);
    TEST_DO(f.assertSnapshots("foo", "v5,i30"));
    TEST_DO(f.removeFooAttrDir(10));
    TEST_DO(f.assertNotGetAttributeDir("foo"));
}

TEST_F("Test that attribute directory is not removed due to pruning and disk dir is kept", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    TEST_DO(f.invalidateFooSnapshots(false));
    TEST_DO(f.assertAttributeDiskDir("foo"));
}

TEST_F("Test that attribute directory is not removed due to pruning but disk dir is removed", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    TEST_DO(f.invalidateFooSnapshots(true));
    TEST_DO(f.assertNotAttributeDiskDir("foo"));
}

TEST("Test that initial state tracks disk layout")
{
    std::filesystem::create_directory(std::filesystem::path("attributes"));
    std::filesystem::create_directory(std::filesystem::path("attributes/foo"));
    std::filesystem::create_directory(std::filesystem::path("attributes/bar"));
    IndexMetaInfo fooInfo("attributes/foo");
    IndexMetaInfo barInfo("attributes/bar");
    fooInfo.addSnapshot({true, 4, "snapshot-4"});
    fooInfo.addSnapshot({false, 8, "snapshot-8"});
    fooInfo.save();
    barInfo.addSnapshot({false, 5, "snapshot-5"});
    barInfo.save();
    Fixture f;
    TEST_DO(f.assertAttributeDiskDir("foo"));
    TEST_DO(f.assertAttributeDiskDir("bar"));
    auto foodir = f.getFooAttrDir();
    EXPECT_TRUE(hasAttributeDir(foodir));
    auto bardir = f.getAttributeDir("bar");
    EXPECT_TRUE(hasAttributeDir(bardir));
    TEST_DO(f.assertNotGetAttributeDir("baz"));
    TEST_DO(f.assertSnapshots("foo", "v4,i8"));
    TEST_DO(f.assertSnapshots("bar", "i5"));
    f.makeInvalidSnapshot(12);
    f.makeValidSnapshot(16);
    TEST_DO(f.assertSnapshots("foo", "v4,i8,i12,v16"));
}

TEST_F("Test that snapshot removal removes correct snapshot directory", Fixture)
{
    TEST_DO(f.setupFooSnapshots(5));
    std::filesystem::create_directory(std::filesystem::path(f.getSnapshotDir("foo", 5)));
    std::filesystem::create_directory(std::filesystem::path(f.getSnapshotDir("foo", 6)));
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
    EXPECT_TRUE(hasWriter(writer));
    auto writer2 = dir->tryGetWriter();
    EXPECT_FALSE(hasWriter(writer2));
    writer.reset();
    writer2 = dir->tryGetWriter();
    EXPECT_TRUE(hasWriter(writer2));
    writer = dir->tryGetWriter();
    EXPECT_FALSE(hasWriter(writer));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
