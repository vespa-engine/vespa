// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

using namespace vespalib;

void check_ptr_real(void *ptr)
{
    (void) ptr;
}

void (*check_ptr)(void *ptr) = check_ptr_real;

class Test : public TestApp
{
public:
    int Main() override;
    ~Test();
private:
    void testFillValue(char *a);
    void verifyPreWriteDetection(); // Should abort
    void verifyPostWriteDetection(); // Should abort
    void verifyWriteAfterFreeDetection(); // Should abort
};

Test::~Test()
{
}

void Test::testFillValue(char *a)
{
    // Verify fillvalue
    EXPECT_EQUAL((int)a[0], 0x66);
    EXPECT_EQUAL((int)a[1], 0x66);
    EXPECT_EQUAL((int)a[255], 0x66);

    // Make sure that enough blocks of memory is allocated and freed.
    for (size_t i(0); i < 100; i++) {
        char *d = new char[256];
        memset(d, 0x77, 256);
        check_ptr(d);
        delete [] d;
        EXPECT_EQUAL((int)d[0], 0x66);
        EXPECT_EQUAL((int)d[1], 0x66);
        EXPECT_EQUAL((int)d[255], 0x66);
    }

    // Make sure we trigger vespamallocd detection of memory written after delete.
    char *aa[1024];
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        aa[i] = new char[256];
    }

    // Verify overwrite detection in place after cleaning up.
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        check_ptr(aa[i]);
        delete [] aa[i];
        EXPECT_EQUAL((int)a[0], 0x66);
        EXPECT_EQUAL((int)a[1], 0x66);
        EXPECT_EQUAL((int)a[255], 0x66);
    }
}

void Test::verifyPreWriteDetection()
{
    char * a = new char[8];
    *(a-1) = 0;
    check_ptr(a);
    delete [] a;
}

void Test::verifyPostWriteDetection()
{
    char * a = new char[8];
    a[8] = 0;
    check_ptr(a);
    delete [] a;
}

void Test::verifyWriteAfterFreeDetection()
{
    // Make sure that enough blocks of memory is allocated and freed.
    char * a = new char[256];
    check_ptr(a);
    delete [] a;
    for (size_t i(0); i < 100; i++) {
        char *d = new char[256];
        check_ptr(d);
        delete [] d;
    }
    // Write freed memory.
    a[0] = 0;

    // Make sure we trigger vespamallocd detection of memory written after delete.
    char *aa[1024];
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        aa[i] = new char[256];
    }

    // Clean up.
    for (size_t i(0); i < sizeof(aa)/sizeof(aa[0]); i++) {
        check_ptr(aa[i]);
        delete [] aa[i];
    }
}

int Test::Main()
{
    TEST_INIT("overwrite_test");

    char * a = new char[256];
    memset(a, 0x77, 256);
    a[0] = 0;
    EXPECT_EQUAL((int)a[0], 0);
    EXPECT_EQUAL((int)a[1], 0x77);
    EXPECT_EQUAL((int)a[255], 0x77);
    char * b = a;
    EXPECT_EQUAL(a, b);
    check_ptr(a);
    delete [] a;
    EXPECT_EQUAL(a, b);

    if (_argc > 1) {
        testFillValue(a);
        if (strcmp(_argv[1], "prewrite") == 0) {
            verifyPreWriteDetection();
            return 0;
        } else if (strcmp(_argv[1], "postwrite") == 0) {
            verifyPostWriteDetection();
            return 0;
        } else if (strcmp(_argv[1], "writeafterfree") == 0) {
            verifyWriteAfterFreeDetection();
            return 0;
        }

    } else {
        // Verify that nothing is done when not expected too.
        EXPECT_EQUAL((int)a[0], 0);
        EXPECT_EQUAL((int)a[1], 0x77);
        EXPECT_EQUAL((int)a[255], 0x77);
    }

    TEST_DONE();
    return 42;
}

TEST_APPHOOK(Test)
