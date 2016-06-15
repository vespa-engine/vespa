// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("data_directory_upgrader_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/server/data_directory_upgrader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <iostream>

using namespace proton;
using namespace vespalib;

typedef DataDirectoryUpgrader::RowColDir RowColDir;
typedef DataDirectoryUpgrader::ScanResult ScanResult;
typedef DataDirectoryUpgrader::UpgradeResult UpgradeResult;

const string SCAN_DIR = "mytest";
const string DEST_DIR = SCAN_DIR + "/n1";

void
assertDirs(const DirectoryList &exp, const DirectoryList &act)
{
    ASSERT_EQUAL(exp.size(), act.size());
    for (size_t i = 0; i < exp.size(); ++i) {
        EXPECT_EQUAL(exp[i], act[i]);
    }
}

void
assertDirs(const DirectoryList &rowColDirs, bool destDirExisting, const ScanResult &act)
{
    ASSERT_EQUAL(rowColDirs.size(), act.getRowColDirs().size());
    for (size_t i = 0; i < rowColDirs.size(); ++i) {
        EXPECT_EQUAL(rowColDirs[i], act.getRowColDirs()[i].dir());
    }
    EXPECT_EQUAL(destDirExisting, act.isDestDirExisting());
}

void
assertDataFile(const vespalib::string &dir)
{
    FileInfo::UP file = stat(dir + "/data.txt");
    ASSERT_TRUE(file.get() != NULL);
    EXPECT_TRUE(file->_plainfile);
}

vespalib::string
readFile(const vespalib::string &fileName)
{
    File file(fileName);
    file.open(File::READONLY);
    FileInfo info = file.stat();
    char buf[512];
    size_t bytesRead = file.read(&buf, info._size, 0);
    return vespalib::string(buf, bytesRead);
}

void
assertUpgradeFile(const vespalib::string &exp, const vespalib::string &dir)
{
    EXPECT_EQUAL(exp, readFile(dir + "/data-directory-upgrade-source.txt"));
}

void
assertDowngradeScript(const vespalib::string &exp, const vespalib::string &dir)
{
    EXPECT_EQUAL(exp, readFile(dir + "/data-directory-downgrade.sh"));
}

struct BaseFixture
{
    DataDirectoryUpgrader _upg;
    BaseFixture(const DirectoryList &dirs, bool createDestDir = false) : _upg(SCAN_DIR, DEST_DIR) {
        mkdir(SCAN_DIR);
        if (createDestDir) {
            mkdir(DEST_DIR);
        }
        for (const string &dir : dirs) {
            mkdir(SCAN_DIR + "/" + dir);
            File f(SCAN_DIR + "/" + dir + "/data.txt");
            f.open(File::CREATE);
            f.close();
        }
    }
    virtual ~BaseFixture() {
        rmdir(SCAN_DIR, true);
    }
    DirectoryList getDirs(const vespalib::string &subDir = "") const {
        DirectoryList l = listDirectory(SCAN_DIR + "/" + subDir);
        std::sort(l.begin(), l.end());
        return l;
    }  
};

struct EmptyFixture : public BaseFixture
{
    EmptyFixture() : BaseFixture({}) {}
};

struct SingleFixture : public BaseFixture
{
    SingleFixture() : BaseFixture({"r0/c0"}) {}
};

struct DoubleFixture : public BaseFixture
{
    DoubleFixture() : BaseFixture({"r0/c0", "r1/c1"}) {}
};

struct UnrelatedFixture : public BaseFixture
{
    UnrelatedFixture() : BaseFixture({"r0/cY", "rX/c1", "r0"}) {}
};

struct ExistingDestinationFixture : public BaseFixture
{
    ExistingDestinationFixture() : BaseFixture({"r0/c0"}, true) {}
};

TEST_F("require that single row/column directory is discovered", SingleFixture)
{
    ScanResult res = f._upg.scan();
    assertDirs({"r0/c0"}, false, res);
}

TEST_F("require that multiple row/column directories are discovered", DoubleFixture)
{
    ScanResult res = f._upg.scan();
    assertDirs({"r0/c0", "r1/c1"}, false, res);
}

TEST_F("require that unrelated directories are not discovered", UnrelatedFixture)
{
    ScanResult res = f._upg.scan();
    assertDirs({}, false, res);
}

TEST_F("require that existing destination directory is discovered", ExistingDestinationFixture)
{
    ScanResult res = f._upg.scan();
    assertDirs({"r0/c0"}, true, res);
}

TEST("require that no-existing scan directory is handled")
{
    DataDirectoryUpgrader upg(SCAN_DIR, DEST_DIR);
    ScanResult res = upg.scan();
    assertDirs({}, false, res);
}

TEST_F("require that empty directory is left untouched", EmptyFixture)
{
    UpgradeResult res = f._upg.upgrade(f._upg.scan());
    EXPECT_EQUAL(DataDirectoryUpgrader::IGNORE, res.getStatus());
    EXPECT_EQUAL("No directory to upgrade", res.getDesc());
    DirectoryList dirs = f.getDirs();
    assertDirs({}, dirs);
}

TEST_F("require that existing destination directory is left untouched", ExistingDestinationFixture)
{
    UpgradeResult res = f._upg.upgrade(f._upg.scan());
    EXPECT_EQUAL(DataDirectoryUpgrader::IGNORE, res.getStatus());
    EXPECT_EQUAL("Destination directory 'mytest/n1' is already existing", res.getDesc());
    DirectoryList dirs = f.getDirs();
    assertDirs({"n1", "r0"}, dirs);
}

TEST_F("require that single directory is upgraded", SingleFixture)
{
    UpgradeResult res = f._upg.upgrade(f._upg.scan());
    EXPECT_EQUAL(DataDirectoryUpgrader::COMPLETE, res.getStatus());
    EXPECT_EQUAL("Moved data from 'mytest/r0/c0' to 'mytest/n1'", res.getDesc());
    DirectoryList dirs = f.getDirs();
    std::sort(dirs.begin(), dirs.end());
    assertDirs({"n1"}, dirs);
    assertDataFile(DEST_DIR);
    assertUpgradeFile("mytest/r0/c0", DEST_DIR);
    assertDowngradeScript("#!/bin/sh\n\n"
                          "mkdir mytest/r0 || exit 1\n"
                          "chown yahoo mytest/r0\n"
                          "mv mytest/n1 mytest/r0/c0\n"
                          "rm mytest/r0/c0/data-directory-upgrade-source.txt\n"
                          "rm mytest/r0/c0/data-directory-downgrade.sh\n", DEST_DIR);
}

TEST_F("require that multiple directories are left untouched", DoubleFixture)
{
    UpgradeResult res = f._upg.upgrade(f._upg.scan());
    EXPECT_EQUAL(DataDirectoryUpgrader::ERROR, res.getStatus());
    EXPECT_EQUAL("Can only upgrade a single directory, was asked to upgrade 2 ('r0/c0', 'r1/c1')", res.getDesc());
    DirectoryList dirs = f.getDirs();
    std::sort(dirs.begin(), dirs.end());
    assertDirs({"r0", "r1"}, dirs);
    assertDataFile(SCAN_DIR + "/r0/c0");
}

TEST_MAIN() { TEST_RUN_ALL(); }
