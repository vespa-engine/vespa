// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>

void check_ptr_real(void *ptr)
{
    (void) ptr;
}

void (*check_ptr)(void *ptr) = check_ptr_real;

void overwrite_memory_real(char *ptr, int offset)
{
    *(ptr + offset) = 0;
}

void (*overwrite_memory)(char *ptr, int offset) = overwrite_memory_real;

char *new_vec_real(size_t size)
{
    return new char[size];
}

char *(*new_vec)(size_t size) = new_vec_real;

void delete_vec_real(char *ptr)
{
    delete [] ptr;
}

void (*delete_vec)(char *ptr) = delete_vec_real;

void testFillValue(char *a)
{
    // Verify fillvalue
    EXPECT_EQ((int)a[0], 0x66);
    EXPECT_EQ((int)a[1], 0x66);
    EXPECT_EQ((int)a[255], 0x66);

    // Make sure that enough blocks of memory is allocated and freed.
    for (size_t i(0); i < 100; i++) {
        char *d = new_vec(256);
        memset(d, 0x77, 256);
        check_ptr(d);
        delete_vec(d);
        EXPECT_EQ((int)d[0], 0x66);
        EXPECT_EQ((int)d[1], 0x66);
        EXPECT_EQ((int)d[255], 0x66);
    }

    // Make sure we trigger vespamallocd detection of memory written after delete.
    char *aa[1024];
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        aa[i] = new_vec(256);
    }

    // Verify overwrite detection in place after cleaning up.
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        check_ptr(aa[i]);
        delete_vec(aa[i]);
        EXPECT_EQ((int)a[0], 0x66);
        EXPECT_EQ((int)a[1], 0x66);
        EXPECT_EQ((int)a[255], 0x66);
    }
}

void verifyPreWriteDetection()
{
    char * a = new_vec(8);
    overwrite_memory(a, -1);
    delete_vec(a);
}

void verifyPostWriteDetection()
{
    char * a = new_vec(8);
    overwrite_memory(a, 8);
    delete_vec(a);
}

void verifyWriteAfterFreeDetection()
{
    // Make sure that enough blocks of memory is allocated and freed.
    char * a = new_vec(256);
    check_ptr(a);
    delete_vec(a);
    for (size_t i(0); i < 100; i++) {
        char *d = new_vec(256);
        check_ptr(d);
        delete_vec(d);
    }
    // Write freed memory.
    a[0] = 0;

    // Make sure we trigger vespamallocd detection of memory written after delete.
    char *aa[1024];
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        aa[i] = new_vec(256);
    }

    // Clean up.
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        check_ptr(aa[i]);
        delete_vec(aa[i]);
    }
}

int my_argc = 0;
char **my_argv = nullptr;

TEST(OverwriteTest, main) {
    char * a = new_vec(256);
    memset(a, 0x77, 256);
    a[0] = 0;
    EXPECT_EQ((int)a[0], 0);
    EXPECT_EQ((int)a[1], 0x77);
    EXPECT_EQ((int)a[255], 0x77);
    char * b = a;
    EXPECT_EQ(a, b);
    check_ptr(a);
    delete_vec(a);
    EXPECT_EQ(a, b);

    if (my_argc > 1) {
        testFillValue(a);
        if (strcmp(my_argv[1], "prewrite") == 0) {
            verifyPreWriteDetection();
        } else if (strcmp(my_argv[1], "postwrite") == 0) {
            verifyPostWriteDetection();
        } else if (strcmp(my_argv[1], "writeafterfree") == 0) {
            verifyWriteAfterFreeDetection();
        }

    } else {
        // Verify that nothing is done when not expected too.
        EXPECT_EQ((int)a[0], 0);
        EXPECT_EQ((int)a[1], 0x77);
        EXPECT_EQ((int)a[255], 0x77);
    }
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    my_argc = argc;
    my_argv = argv;
    return RUN_ALL_TESTS();
}
