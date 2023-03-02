// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/file.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>
#include <cassert>
#include <sys/mman.h>
#include <filesystem>

const std::string srcDir = getenv("SOURCE_DIRECTORY") ? getenv("SOURCE_DIRECTORY") : ".";
const std::string roFilename = srcDir + "/hello.txt";
const std::string woFilename = "generated/writeonlytest.txt";
const std::string rwFilename = "generated/readwritetest.txt";

// create and remove 'generated' sub-directory
struct Generated {
    Generated() { std::filesystem::create_directory(std::filesystem::path("generated")); }
    ~Generated() { std::filesystem::remove_all(std::filesystem::path("generated")); }
};

TEST(FileTest, GetCurrentDirTest) {
    std::string currentDir = FastOS_File::getCurrentDirectory();
    EXPECT_FALSE(currentDir.empty());
    EXPECT_TRUE(FastOS_File::SetCurrentDirectory(".."));
    std::string parentDir = FastOS_File::getCurrentDirectory();
    EXPECT_FALSE(parentDir.empty());
    EXPECT_NE(currentDir, parentDir);
    EXPECT_TRUE(FastOS_File::SetCurrentDirectory(currentDir.c_str()));
}

void MemoryMapTestImpl(int mmap_flags) {
    Generated guard;
    const int bufSize = 1000;
    FastOS_File file("generated/memorymaptest");
    ASSERT_TRUE(file.OpenReadWrite());
    std::vector<char> space(bufSize);
    char *buffer = space.data();
    for (int i = 0; i < bufSize; i++) {
        buffer[i] = i % 256;
    }
    EXPECT_EQ(file.Write2(buffer, bufSize), bufSize);
    bool close_ok = file.Close();
    assert(close_ok);
    file.enableMemoryMap(mmap_flags);
    ASSERT_TRUE(file.OpenReadOnly());
    bool mmapEnabled = file.IsMemoryMapped();
    char *mmapBuffer = static_cast<char *>(file.MemoryMapPtr(0));
    fprintf(stderr, "Memory mapping %s\n", mmapEnabled ? "enabled" : "disabled");
    fprintf(stderr, "Map address: 0x%p\n", mmapBuffer);
    if (mmapEnabled) {
        for (int i = 0; i < bufSize; i++) {
            EXPECT_EQ(mmapBuffer[i], char(i % 256));
        }
    }
}

TEST(FileTest, MemoryMapTest) { MemoryMapTestImpl(0); }

#ifdef __linux__
TEST(FileTest, MemoryMapTestHuge) { MemoryMapTestImpl(MAP_HUGETLB); }
#endif

TEST(FileTest, DirectIOTest) {
    Generated guard;
    const int bufSize = 40000;
    FastOS_File file("generated/diotest");
    ASSERT_TRUE(file.OpenWriteOnly());
    std::vector<char> space(bufSize);
    char *buffer = space.data();
    for (int i = 0; i < bufSize; i++) {
        buffer[i] = 'A' + (i % 17);
    }
    EXPECT_EQ(file.Write2(buffer, bufSize), bufSize);
    bool close_ok = file.Close();
    assert(close_ok);
    file.EnableDirectIO();
    ASSERT_TRUE(file.OpenReadOnly());
    size_t memoryAlignment = 0;
    size_t transferGranularity = 0;
    size_t transferMaximum = 0;
    bool dioEnabled = file.GetDirectIORestrictions(memoryAlignment,
                                                   transferGranularity,
                                                   transferMaximum);
    fprintf(stderr, "DirectIO %s\n", dioEnabled ? "enabled" : "disabled");
    fprintf(stderr, "Memory alignment: %zu bytes\n", memoryAlignment);
    fprintf(stderr, "Transfer granularity: %zu bytes\n", transferGranularity);
    fprintf(stderr, "Transfer maximum: %zu bytes\n", transferMaximum);
    if (dioEnabled) {
        int eachRead = (8192 + transferGranularity - 1) / transferGranularity;
        std::vector<char> space2(eachRead * transferGranularity + memoryAlignment - 1);
        char *buffer2 = space2.data();
        char *alignPtr = buffer2;
        unsigned int align =
            static_cast<unsigned int>
            (reinterpret_cast<unsigned long>(alignPtr) &
             (memoryAlignment - 1));
        if (align != 0) {
            alignPtr = &alignPtr[memoryAlignment - align];
        }
        int residue = bufSize;
        int pos = 0;
        while (residue > 0) {
            int readThisTime = eachRead * transferGranularity;
            if (readThisTime > residue) {
                readThisTime = residue;
            }
            file.ReadBuf(alignPtr, readThisTime, pos);
            for (int i = 0; i < readThisTime; i++) {
                ASSERT_EQ(alignPtr[i], char('A' + ((i+pos) % 17)));
            }
            residue -= readThisTime;
            pos += readThisTime;
        }
        ASSERT_TRUE(file.SetPosition(1));
        try {
            const int attemptReadBytes = 173;
            [[maybe_unused]] auto res = file.Read(buffer, attemptReadBytes);
            EXPECT_TRUE(false);
        } catch (const DirectIOException &) {
            fprintf(stderr, "got DirectIOException as expected\n");
        } catch (...) {
            EXPECT_TRUE(false);
        }
        ASSERT_TRUE(file.SetPosition(1));
        try {
            const int attemptReadBytes = 4096;
            [[maybe_unused]] auto res = file.Read(buffer, attemptReadBytes);
            EXPECT_TRUE(false);
        } catch (const DirectIOException &) {
            fprintf(stderr, "got DirectIOException as expected\n");
        } catch (...) {
            EXPECT_TRUE(false);
        }
    } else {
        memset(buffer, 0, bufSize);
        ssize_t readBytes = file.Read(buffer, bufSize);
        ASSERT_EQ(readBytes, bufSize);
        for (int i = 0; i < bufSize; i++) {
            ASSERT_EQ(buffer[i], char('A' + (i % 17)));
        }
    }
}

TEST(FileTest, ReadOnlyTest) {
    auto myFile = std::make_unique<FastOS_File>(roFilename.c_str());
    ASSERT_TRUE(myFile->OpenReadOnly());
    EXPECT_EQ(myFile->GetSize(), 27);
    char dummyData[6] = "Dummy";
    ASSERT_FALSE(myFile->CheckedWrite(dummyData, 6));
    char dummyData2[28];
    ASSERT_TRUE(myFile->SetPosition(1));
    EXPECT_EQ(myFile->Read(dummyData2, 28), 26);
    EXPECT_EQ(myFile->GetPosition(), 27);
}

TEST(FileTest, WriteOnlyTest) {
    Generated guard;
    auto myFile = std::make_unique<FastOS_File>(woFilename.c_str());
    ASSERT_TRUE(myFile->OpenWriteOnly());
    EXPECT_EQ(myFile->GetSize(), 0);
    char dummyData[6] = "Dummy";
    ASSERT_TRUE(myFile->CheckedWrite(dummyData, 6));
    ASSERT_EQ(myFile->GetPosition(), 6);
    ASSERT_TRUE(myFile->SetPosition(0));
    ASSERT_EQ(myFile->GetPosition(), 0);
    EXPECT_LT(myFile->Read(dummyData, 6), 0);
    EXPECT_TRUE(myFile->Close());
    EXPECT_TRUE(myFile->Delete());
}

TEST(FileTest, ReadWriteTest) {
    Generated guard;
    auto myFile = std::make_unique<FastOS_File>(rwFilename.c_str());
    ASSERT_FALSE(myFile->OpenExisting());
    ASSERT_TRUE(myFile->OpenReadWrite());
    ASSERT_EQ(myFile->GetSize(), 0);
    char dummyData[6] = "Dummy";
    ASSERT_TRUE(myFile->CheckedWrite(dummyData, 6));
    ASSERT_EQ(myFile->GetPosition(), 6);
    ASSERT_TRUE(myFile->SetPosition(0));
    ASSERT_EQ(myFile->GetPosition(), 0);
    char dummyData2[7];
    ASSERT_EQ(myFile->Read(dummyData2, 6), 6);
    EXPECT_EQ(memcmp(dummyData, dummyData2, 6), 0);
    ASSERT_TRUE(myFile->SetPosition(1));
    EXPECT_EQ(myFile->Read(dummyData2, 7), 5);
    EXPECT_EQ(myFile->GetPosition(), 6);
    EXPECT_EQ(myFile->Read(dummyData2, 6), 0);
    EXPECT_EQ(myFile->GetPosition(), 6);
    EXPECT_TRUE(myFile->Close());
    EXPECT_TRUE(myFile->Delete());
}

TEST(FileTest, ScanDirectoryTest) {
    auto scanDir = std::make_unique<FastOS_DirectoryScan>(".");
    while (scanDir->ReadNext()) {
        const char *name = scanDir->GetName();
        bool isDirectory = scanDir->IsDirectory();
        bool isRegular   = scanDir->IsRegular();
        fprintf(stderr, "%-30s %s\n", name, isDirectory ? "DIR" : (isRegular ? "FILE" : "UNKN"));
    }
}

TEST(FileTest, ReadBufTest) {
    FastOS_File file(roFilename.c_str());
    char buffer[20];
    ASSERT_TRUE(file.OpenReadOnly());
    EXPECT_EQ(file.GetPosition(), 0);
    EXPECT_EQ(file.Read(buffer, 4), 4);
    buffer[4] = '\0';
    EXPECT_EQ(file.GetPosition(), 4);
    EXPECT_EQ(strcmp(buffer, "This"), 0);
    file.ReadBuf(buffer, 6, 8);
    buffer[6] = '\0';
    EXPECT_EQ(file.GetPosition(), 4);
    EXPECT_EQ(strcmp(buffer, "a test"), 0);
}

TEST(FileTest, DiskFreeSpaceTest) {
    EXPECT_NE(FastOS_File::GetFreeDiskSpace(roFilename.c_str()), int64_t(-1));
    EXPECT_NE(FastOS_File::GetFreeDiskSpace("."), int64_t(-1));
}

TEST(FileTest, MaxLengthTest) {
    int maxval = FastOS_File::GetMaximumFilenameLength(".");
    EXPECT_GT(maxval, 5);
    EXPECT_LT(maxval, (512*1024));
    maxval = FastOS_File::GetMaximumPathLength(".");
    EXPECT_GT(maxval, 5);
    EXPECT_LT(maxval, (512*1024));
}

GTEST_MAIN_RUN_ALL_TESTS()
