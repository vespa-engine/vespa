// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/devicemanager.h>
#include <vespa/memfilepersistence/init/filescanner.h>
#include <vespa/memfilepersistence/mapper/bucketdirectorymapper.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/random.h>
#include <iomanip>
#include <sys/errno.h>

namespace storage {
namespace memfile {

struct FileScannerTest : public CppUnit::TestFixture {
    struct TestParameters {
        uint32_t filesPerDisk;
        uint32_t diskCount;
        uint32_t bucketSplitBits;
        uint32_t dirLevels;
        uint32_t dirSpread;
        uint32_t parts;
        std::set<uint32_t> disksDown;
        bool diskDownWithBrokenSymlink;
        bool bucketWrongDir;
        bool bucketMultipleDirs;
        bool bucketMultipleDisks;
        bool addTemporaryFiles;
        bool addAlienFiles;
        bool dirWithNoListPermission;
        bool dirWithNoWritePermission;
        bool dirWithNoExecutePermission;
        bool fileWithNoReadPermission;
        bool fileWithNoWritePermission;

        TestParameters()
            : filesPerDisk(10), diskCount(5), bucketSplitBits(20),
              dirLevels(1), dirSpread(16), parts(1), disksDown(),
              diskDownWithBrokenSymlink(false),
              bucketWrongDir(false), bucketMultipleDirs(false),
              bucketMultipleDisks(false),
              addTemporaryFiles(false), addAlienFiles(false),
              dirWithNoListPermission(false),
              dirWithNoWritePermission(false),
              dirWithNoExecutePermission(false),
              fileWithNoReadPermission(false),
              fileWithNoWritePermission(false) {}
        void addAllComplexities() {
            disksDown.insert(0);
            disksDown.insert(2);
            disksDown.insert(4);
            bucketWrongDir = true;
            bucketMultipleDirs = true;
            bucketMultipleDisks = true;
            parts = 7;
            addTemporaryFiles = true;
            addAlienFiles = true;
            dirWithNoWritePermission = true;
            fileWithNoWritePermission = true;
            fileWithNoReadPermission = true;
        }
    };

    void testNormalUsage() {
        TestParameters params;
        runTest(params);
    }
    void testMultipleParts() {
        TestParameters params;
        params.parts = 3;
        runTest(params);
    }
    void testBucketInWrongDirectory() {
        TestParameters params;
        params.bucketWrongDir = true;
        runTest(params);
    }
    void testBucketInMultipleDirectories() {
        TestParameters params;
        params.bucketMultipleDirs = true;
        runTest(params);
    }
    void testZeroDirLevel() {
        TestParameters params;
        params.dirLevels = 0;
        runTest(params);
    }
    void testSeveralDirLevels() {
        TestParameters params;
        params.dirLevels = 3;
        runTest(params);
    }
    void testNonStandardDirSpread() {
        TestParameters params;
        params.dirSpread = 63;
        runTest(params);
    }
    void testDiskDown() {
        TestParameters params;
        params.disksDown.insert(1);
        runTest(params);
    }
    void testDiskDownBrokenSymlink() {
        TestParameters params;
        params.disksDown.insert(1);
        params.disksDown.insert(3);
        params.diskDownWithBrokenSymlink = true;
        runTest(params);
    }
    void testRemoveTemporaryFile() {
        TestParameters params;
        params.addTemporaryFiles = true;
        runTest(params);
    }
    void testAlienFile() {
        TestParameters params;
        params.addAlienFiles = true;
        runTest(params);
    }
    void testUnlistableDirectory() {
        TestParameters params;
        params.dirWithNoListPermission = true;
        runTest(params);
    }
    void testDirWithNoWritePermission() {
        TestParameters params;
        params.dirWithNoWritePermission = true;
        runTest(params);
    }
    void testDirWithNoExecutePermission() {
        TestParameters params;
        params.dirWithNoWritePermission = true;
        runTest(params);
    }
    void testFileWithNoReadPermission() {
        TestParameters params;
        params.bucketWrongDir = true;
        params.fileWithNoReadPermission = true;
        runTest(params);
    }
    void testFileWithNoWritePermission() {
        TestParameters params;
        params.bucketWrongDir = true;
        params.fileWithNoWritePermission = true;
        runTest(params);
    }
    void testAllFailuresCombined() {
        TestParameters params;
        params.addAllComplexities();
        runTest(params);
    }

    CPPUNIT_TEST_SUITE(FileScannerTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testMultipleParts);
    CPPUNIT_TEST(testBucketInWrongDirectory);
    CPPUNIT_TEST(testBucketInMultipleDirectories);
    CPPUNIT_TEST(testZeroDirLevel);
    CPPUNIT_TEST(testSeveralDirLevels);
    CPPUNIT_TEST(testNonStandardDirSpread);
    CPPUNIT_TEST(testDiskDown);
    CPPUNIT_TEST(testDiskDownBrokenSymlink);
    CPPUNIT_TEST(testRemoveTemporaryFile);
    CPPUNIT_TEST(testAlienFile);
    CPPUNIT_TEST(testUnlistableDirectory);
    CPPUNIT_TEST(testDirWithNoWritePermission);
    CPPUNIT_TEST(testDirWithNoExecutePermission);
    CPPUNIT_TEST(testFileWithNoReadPermission);
    CPPUNIT_TEST(testFileWithNoWritePermission);
    CPPUNIT_TEST(testAllFailuresCombined);
    CPPUNIT_TEST_SUITE_END();

    // Actual implementation of the tests.

    /** Run a console command and fail test if it fails. */
    void run(std::string cmd);

    /** Struct containing metadata for a single bucket. */
    struct BucketData {
        document::BucketId bucket;
        uint32_t disk;
        std::vector<uint32_t> directory;
        bool shouldExist; // Set to false for buckets that won't exist due to
                          // some failure.

        BucketData() : shouldExist(true) {}

        bool sameDir(BucketData& other) const {
            return (disk == other.disk && directory == other.directory);
        }
    };

    /**
     * Create an overview of the buckets we're gonna use in the test.
     * (Without any failures introduced)
     */
    std::vector<BucketData> createBuckets(const TestParameters& params);

    /**
     * Create the data in the bucket map and introduce the failures specified
     * in the test. Mark buckets in bucket list that won't exist due to the
     * failures so we know how to verify result of test.
     */
    void createData(const TestParameters&, std::vector<BucketData>& buckets,
                    std::vector<std::string>& tempFiles,
                    std::vector<std::string>& alienFiles);

    /**
     * Run a test with a given set of parameters, calling createData to set up
     * the data, and then using a file scanner to actually list the files.
     */
    void runTest(const TestParameters&);

};

CPPUNIT_TEST_SUITE_REGISTRATION(FileScannerTest);

void
FileScannerTest::run(std::string cmd)
{
    int result = system(cmd.c_str());
    if (result != 0) {
        CPPUNIT_FAIL("Failed to run command '" + cmd + "'.");
    }
}

std::vector<FileScannerTest::BucketData>
FileScannerTest::createBuckets(const TestParameters& params)
{
    std::vector<BucketData> buckets;
    BucketDirectoryMapper dirMapper(params.dirLevels, params.dirSpread);
    for (uint32_t i=0; i<params.diskCount; ++i) {
        if (params.disksDown.find(i) != params.disksDown.end()) {
            continue;
        }
        for (uint32_t j=0; j<params.filesPerDisk; ++j) {
            BucketData data;
            data.bucket = document::BucketId(params.bucketSplitBits,
                                             params.filesPerDisk * i + j);
            data.disk = i;
            data.directory = dirMapper.getPath(data.bucket);
            buckets.push_back(data);
        }
    }
    return buckets;
}

void
FileScannerTest::createData(const TestParameters& params,
                            std::vector<BucketData>& buckets,
                            std::vector<std::string>& tempFiles,
                            std::vector<std::string>& alienFiles)
{
    if (params.bucketWrongDir) {
        CPPUNIT_ASSERT(params.dirLevels > 0);
        buckets[0].directory[0] = (buckets[0].directory[0] + 1)
                                % params.dirSpread;
    }
    if (params.bucketMultipleDirs) {
        CPPUNIT_ASSERT(params.dirLevels > 0);
        BucketData copy(buckets[1]);
        copy.directory[0] = (buckets[1].directory[0] + 1) % params.dirSpread;
        buckets.push_back(copy);
    }
    if (params.bucketMultipleDisks && params.dirLevels > 0) {
        BucketData copy(buckets[2]);
        uint32_t disk = 0;
        for (; disk<params.diskCount; ++disk) {
            if (disk == copy.disk) continue;
            if (params.disksDown.find(disk) == params.disksDown.end()) break;
        }
        CPPUNIT_ASSERT(disk < params.diskCount);
        copy.disk = disk;
        buckets.push_back(copy);
    }

    run("mkdir -p vdsroot");
    run("chmod -R a+rwx vdsroot");
    run("rm -rf vdsroot");
    run("mkdir -p vdsroot/disks");
    vespalib::RandomGen randomizer;
    uint32_t diskToHaveBrokenSymlink = (params.disksDown.empty()
            ? 0 : randomizer.nextUint32(0, params.disksDown.size()));
    uint32_t downIndex = 0;
    for (uint32_t i=0; i<params.diskCount; ++i) {
        if (params.disksDown.find(i) != params.disksDown.end()) {
            if (downIndex++ == diskToHaveBrokenSymlink
                && params.diskDownWithBrokenSymlink)
            {
                std::ostringstream path;
                path << "vdsroot/disks/d" << i;
                run("ln -s /non-existing-dir " + path.str());
            }
        } else {
            std::ostringstream path;
            path << "vdsroot/disks/d" << i;
            run("mkdir -p " + path.str());
            std::ofstream of((path.str() + "/chunkinfo").c_str());
            of << "#chunkinfo\n" << i << "\n" << params.diskCount << "\n";
        }
    }
    for (uint32_t i=0; i<buckets.size(); ++i) {
        if (!buckets[i].shouldExist) continue;
        std::ostringstream path;
        path << "vdsroot/disks/d" << buckets[i].disk << std::hex;
        for (uint32_t j=0; j<buckets[i].directory.size(); ++j) {
            path << '/' << std::setw(4) << std::setfill('0')
                 << buckets[i].directory[j];
        }
        run("mkdir -p " + path.str());
        if (params.dirWithNoListPermission && i == 8) {
            run("chmod a-r " + path.str());
                // Scanner will abort with exception, so we don't really know
                // how many docs will not be found due to this.
            continue;
        }
        if (params.dirWithNoExecutePermission && i == 9) {
            run("chmod a-x " + path.str());
                // Scanner will abort with exception, so we don't really know
                // how many docs will not be found due to this.
            continue;
        }
        path << '/' << std::setw(16) << std::setfill('0')
             << buckets[i].bucket.getId() << ".0";
        run("touch " + path.str());
        if (params.addTemporaryFiles && i == 4) {
            run("touch " + path.str() + ".tmp");
            tempFiles.push_back(path.str() + ".tmp");
        }
        if (params.addAlienFiles && i == 6) {
            run("touch " + path.str() + ".alien");
            alienFiles.push_back(path.str() + ".alien");
        }
        if (params.fileWithNoWritePermission && i == 0) {
            // Overlapping with wrong dir so it would want to move file
            run("chmod a-w " + path.str());
        }
        if (params.fileWithNoReadPermission && i == 0) {
            // Overlapping with wrong dir so it would want to move file
            run("chmod a-r " + path.str());
        }
        if (params.dirWithNoWritePermission && i == 9) {
            run("chmod a-w " + path.str());
        }
    }
}

namespace {
    struct BucketDataFound {
        uint16_t _disk;
        bool _checked;

        BucketDataFound() : _disk(65535), _checked(false) {}
        BucketDataFound(uint32_t disk) : _disk(disk), _checked(false) {}
    };
}

void
FileScannerTest::runTest(const TestParameters& params)
{
    std::vector<BucketData> buckets(createBuckets(params));
    std::vector<std::string> tempFiles;
    std::vector<std::string> alienFiles;
    createData(params, buckets, tempFiles, alienFiles);

    framework::defaultimplementation::RealClock clock;
    framework::defaultimplementation::ComponentRegisterImpl compReg;
    compReg.setClock(clock);

    MountPointList mountPoints("./vdsroot",
                               std::vector<vespalib::string>(),
                               DeviceManager::UP(
                                       new DeviceManager(
                                               DeviceMapper::UP(new SimpleDeviceMapper),
                                               clock)));
    mountPoints.init(params.diskCount);

    FileScanner scanner(compReg, mountPoints,
                        params.dirLevels, params.dirSpread);
    std::map<document::BucketId, BucketDataFound> foundBuckets;
    uint32_t extraBucketsSameDisk = 0;
    uint32_t extraBucketsOtherDisk = 0;
    for (uint32_t j=0; j<params.diskCount; ++j) {
        // std::cerr << "Disk " << j << "\n";
        if (params.disksDown.find(j) != params.disksDown.end()) continue;
        for (uint32_t i=0; i<params.parts; ++i) {
           document::BucketId::List bucketList;
            try{
                scanner.buildBucketList(bucketList, j, i, params.parts);
                for (uint32_t k=0; k<bucketList.size(); ++k) {
                    if (foundBuckets.find(bucketList[k]) != foundBuckets.end())
                    {
                        if (j == foundBuckets[bucketList[k]]._disk) {
                            ++extraBucketsSameDisk;
                        } else {
                            ++extraBucketsOtherDisk;
                        }
//                        std::cerr << "Bucket " << bucketList[k]
//                                  << " on disk " << j << " is already found on disk "
//                                  << foundBuckets[bucketList[k]]._disk << ".\n";
                    }
                    foundBuckets[bucketList[k]] = BucketDataFound(j);
                }
            } catch (vespalib::IoException& e) {
                if (!(params.dirWithNoListPermission
                        && e.getType() == vespalib::IoException::NO_PERMISSION))
                {
                    throw;
                }
            }
        }
    }
    std::vector<BucketData> notFound;
    std::vector<BucketData> wasFound;
    std::vector<BucketDataFound> foundNonExisting;
        // Verify that found buckets match buckets expected.
    for (uint32_t i=0; i<buckets.size(); ++i) {
        std::map<document::BucketId, BucketDataFound>::iterator found(
                foundBuckets.find(buckets[i].bucket));
        if (buckets[i].shouldExist && found == foundBuckets.end()) {
            notFound.push_back(buckets[i]);
        } else if (!buckets[i].shouldExist && found != foundBuckets.end()) {
            wasFound.push_back(buckets[i]);
        }
        if (found != foundBuckets.end()) { found->second._checked = true; }
    }
    for (std::map<document::BucketId, BucketDataFound>::iterator it
            = foundBuckets.begin(); it != foundBuckets.end(); ++it)
    {
        if (!it->second._checked) {
            foundNonExisting.push_back(it->second);
        }
    }
    if (params.dirWithNoListPermission) {
        CPPUNIT_ASSERT(!notFound.empty());
    } else if (!notFound.empty()) {
        std::ostringstream ost;
        ost << "Failed to find " << notFound.size() << " of "
            << buckets.size() << " buckets. Including buckets:";
        for (uint32_t i=0; i<5 && i<notFound.size(); ++i) {
            ost << " " << notFound[i].bucket;
        }
        CPPUNIT_FAIL(ost.str());
    }
    CPPUNIT_ASSERT(wasFound.empty());
    CPPUNIT_ASSERT(foundNonExisting.empty());
    if (params.bucketMultipleDirs) {
        // TODO: Test something else here? This is not correct test, as when
        // there are two buckets on the same disk, one of them will be ignored by
        // the bucket lister.
        // CPPUNIT_ASSERT_EQUAL(1u, extraBucketsSameDisk);
    } else {
        CPPUNIT_ASSERT_EQUAL(0u, extraBucketsSameDisk);
    }
    if (params.bucketMultipleDisks) {
        CPPUNIT_ASSERT_EQUAL(1u, extraBucketsOtherDisk);
    } else {
        CPPUNIT_ASSERT_EQUAL(0u, extraBucketsOtherDisk);
    }
    if (params.addTemporaryFiles) {
        CPPUNIT_ASSERT_EQUAL(
                1, int(scanner.getMetrics()._temporaryFilesDeleted.getValue()));
    } else {
        CPPUNIT_ASSERT_EQUAL(
                0, int(scanner.getMetrics()._temporaryFilesDeleted.getValue()));
    }
    if (params.addAlienFiles) {
        CPPUNIT_ASSERT_EQUAL(
                1, int(scanner.getMetrics()._alienFileCounter.getValue()));
    } else {
        CPPUNIT_ASSERT_EQUAL(
                0, int(scanner.getMetrics()._alienFileCounter.getValue()));
    }
        // We automatically delete temporary files (created by VDS, indicating
        // an operation that only half finished.
    for (uint32_t i=0; i<tempFiles.size(); ++i) {
        CPPUNIT_ASSERT_MSG(tempFiles[i], !vespalib::fileExists(tempFiles[i]));
    }
        // We don't automatically delete alien files
    for (uint32_t i=0; i<alienFiles.size(); ++i) {
        CPPUNIT_ASSERT_MSG(alienFiles[i], vespalib::fileExists(alienFiles[i]));
    }
}

} // memfile
} // storage
