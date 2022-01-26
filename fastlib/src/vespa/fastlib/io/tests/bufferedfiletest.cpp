// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/testkit/test_kit.h>


TEST("main") {
    int value = 0;
    FastOS_StatInfo statInfo;

    FastOS_File::Delete("testfile1");
    FastOS_File::Delete("testfile2");
    FastOS_File::Delete("testfile3");
    FastOS_File::Delete("testfile4");
    FastOS_File::Delete("testfile5");

    Fast_BufferedFile bufFile(4096);

    // test 1
    printf ("testing 11 byte long file\n");
    bufFile.WriteOpen("testfile1");
    bufFile.addNum(1,10,' ');
    ASSERT_TRUE(bufFile.CheckedWrite("\n",1));
    ASSERT_TRUE(bufFile.Close());
    FastOS_File::Stat("testfile1", &statInfo);
    if (statInfo._size != 11) {
        printf (" -- FAILURE\n\n");
        TEST_FATAL("exit 1");
    }
    printf (" -- SUCCESS\n\n");

    // test 2
    printf ("testing 4095 byte long file\n");
    bufFile.WriteOpen("testfile2");
    char buf[8192]; // allocate 8K buffer
    memset(buf,0xff,8192);
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4095)); // write almost 4K
    ASSERT_TRUE(bufFile.Close());
    FastOS_File::Stat("testfile2", &statInfo);
    if (statInfo._size != 4095) {
        printf (" -- FAILURE\n\n");
        TEST_FATAL("exit 1");
    }
    printf (" -- SUCCESS\n\n");

    // test 3
    printf ("testing 4096 byte long file\n");
    bufFile.WriteOpen("testfile3");
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4096));  // write exactly 4K
    ASSERT_TRUE(bufFile.Close());
    FastOS_File::Stat("testfile3", &statInfo);
    if (statInfo._size != 4096) {
        printf (" -- FAILURE\n\n");
        TEST_FATAL("exit 1");
    }
    printf (" -- SUCCESS\n\n");

    // test 4
    printf ("testing 4097 byte long file\n");
    bufFile.WriteOpen("testfile4");
    ASSERT_TRUE(bufFile.CheckedWrite(buf,4097));   // write a bit over 4K
    ASSERT_TRUE(bufFile.Close());
    FastOS_File::Stat("testfile4", &statInfo);
    if (statInfo._size != 4097) {
        printf (" -- FAILURE\n\n");
        TEST_FATAL("exit 1");
    }
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
    FastOS_File::Stat("testfile5", &statInfo);
    if (statInfo._size != 610000) {
        printf (" -- FAILURE\n\n");
        TEST_FATAL("exit 1");
    }
    printf (" -- SUCCESS\n\n");

    FastOS_File::Delete("testfile1");
    FastOS_File::Delete("testfile2");
    FastOS_File::Delete("testfile3");
    FastOS_File::Delete("testfile4");
    FastOS_File::Delete("testfile5");

    printf ("All tests OK for bufferedfiletest\n");
    printf (" -- SUCCESS\n\n");
}

TEST_MAIN() { TEST_RUN_ALL(); }
