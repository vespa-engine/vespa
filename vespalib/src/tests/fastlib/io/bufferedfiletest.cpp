// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

using namespace ::testing;

namespace fs = std::filesystem;

struct BufferedFileTest : Test {
    void SetUp() override {
        remove_testfiles();
    }
    void TearDown() override {
        remove_testfiles();
    }
    static void remove_testfiles() {
        fs::remove(fs::path("testfile1"));
        fs::remove(fs::path("testfile2"));
        fs::remove(fs::path("testfile3"));
        fs::remove(fs::path("testfile4"));
        fs::remove(fs::path("testfile5"));
    }
};

TEST_F(BufferedFileTest, main) {
    int value = 0;

    Fast_BufferedFile bufFile(4096);

    // test 1
    printf ("testing 11 byte long file\n");
    bufFile.WriteOpen("testfile1");
    bufFile.addNum(1,10,' ');
    ASSERT_TRUE(bufFile.CheckedWrite("\n",1));
    ASSERT_TRUE(bufFile.Close());
    ASSERT_EQ(11u, fs::file_size(fs::path("testfile1")));
    printf (" -- SUCCESS\n\n");

    // test 2
    printf ("testing 4095 byte long file\n");
    bufFile.WriteOpen("testfile2");
    char buf[8192]; // allocate 8K buffer
    memset(buf,0xff,8192);
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4095)); // write almost 4K
    ASSERT_TRUE(bufFile.Close());
    ASSERT_EQ(4095u, fs::file_size(fs::path("testfile2")));
    printf (" -- SUCCESS\n\n");

    // test 3
    printf ("testing 4096 byte long file\n");
    bufFile.WriteOpen("testfile3");
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4096));  // write exactly 4K
    ASSERT_TRUE(bufFile.Close());
    ASSERT_EQ(4096u, fs::file_size(fs::path("testfile3")));
    printf (" -- SUCCESS\n\n");

    // test 4
    printf ("testing 4097 byte long file\n");
    bufFile.WriteOpen("testfile4");
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4097));   // write a bit over 4K
    ASSERT_TRUE(bufFile.Close());
    ASSERT_EQ(4097u, fs::file_size(fs::path("testfile4")));
    printf (" -- SUCCESS\n\n");

    // test 5
    printf ("testing 610000 byte long file with repeated addNum\n");
    bufFile.WriteOpen("testfile5");
    for (int i = 0; i < 10000; i++) {
        for (int j = 0; j < 10; j++) {
            bufFile.addNum(value,6,' ');
            value++;
        }
        ASSERT_TRUE(bufFile.CheckedWrite("\n",1));
    }
    ASSERT_TRUE(bufFile.Close());
    ASSERT_EQ(610000u, fs::file_size(fs::path("testfile5")));
    printf (" -- SUCCESS\n\n");
}

TEST_F(BufferedFileTest, can_access_underlying_buffer_via_input_api) {
    {
        Fast_BufferedFile out_file;
        out_file.WriteOpen("testfile1");
        // The internal Fast_BufferedFile read buffer has a minimum size of 4Ki, so to
        // test boundary cases we need to write at least 2 such blocks. Also throw in a
        // partial block at the end of the file.
        char buf[8192 + 100];
        memset(buf, 'A', 4096);
        memset(buf + 4096, 'B', 4096);
        memset(buf + 8192, 'C', 100);
        ASSERT_TRUE(out_file.CheckedWrite(buf, sizeof(buf)));
        ASSERT_TRUE(out_file.Close());
    }
    Fast_BufferedFile f(4096);
    f.ReadOpenExisting("testfile1");
    ASSERT_TRUE(f.IsOpened());
    auto mem = f.obtain();
    ASSERT_TRUE(mem.data);
    ASSERT_EQ(mem.size, 4096);
    ASSERT_EQ(mem.make_string(), std::string(4096, 'A'));
    // obtain() should be idempotent until evict() is called
    auto mem2 = f.obtain();
    ASSERT_EQ(mem2.data, mem.data);
    ASSERT_EQ(mem2.size, mem.size);
    f.evict(1);
    mem = f.obtain();
    ASSERT_TRUE(mem.data);
    ASSERT_EQ(mem.size, 4095);
    ASSERT_EQ(mem.make_string(), std::string(4095, 'A'));
    f.evict(4094);
    mem = f.obtain();
    ASSERT_TRUE(mem.data);
    ASSERT_EQ(mem.size, 1);
    ASSERT_EQ(mem.make_string(), "A");
    f.evict(1);
    mem = f.obtain(); // --> read new block
    ASSERT_TRUE(mem.data);
    ASSERT_EQ(mem.size, 4096);
    ASSERT_EQ(mem.make_string(), std::string(4096, 'B'));
    f.evict(4096);
    mem = f.obtain(); // --> read new block (partial this time)
    ASSERT_TRUE(mem.data);
    ASSERT_EQ(mem.size, 100);
    ASSERT_EQ(mem.make_string(), std::string(100, 'C'));
    f.evict(100);
    // EOF
    mem = f.obtain();
    ASSERT_EQ(mem.size, 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
