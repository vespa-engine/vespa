// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/allocinarray.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <deque>

using namespace vespalib;

class Test : public TestApp
{
public:
    int Main() override;
private:
    template <typename T, typename V>
    void testAllocInArray();
};

int
Test::Main()
{
    TEST_INIT("allocinarray_test");

    testAllocInArray<int64_t, vespalib::Array<int64_t> >();
    testAllocInArray<int64_t, vespalib::Array<int64_t> >();
    testAllocInArray<int64_t, std::vector<int64_t> >();
    testAllocInArray<int64_t, std::deque<int64_t> >();

    TEST_DONE();
}

template <typename T, typename V>
void Test::testAllocInArray()
{
    typedef AllocInArray<T, V> AA;
    AA alloc;
    EXPECT_EQUAL(0ul, alloc.size());
    EXPECT_EQUAL(0ul, alloc.alloc(1));
    EXPECT_EQUAL(1ul, alloc.size());
    EXPECT_EQUAL(1, alloc[0]);
    alloc.free(0);
    EXPECT_EQUAL(0ul, alloc.size());
    alloc.free(0);
    EXPECT_EQUAL(0ul, alloc.size());
    alloc.free(1);
    EXPECT_EQUAL(0ul, alloc.size());

    alloc.alloc(7);
    alloc.alloc(17);
    alloc.alloc(-17);
    EXPECT_EQUAL(3ul, alloc.size());
    EXPECT_EQUAL(7, alloc[0]);
    EXPECT_EQUAL(17, alloc[1]);
    EXPECT_EQUAL(-17, alloc[2]);
    alloc[1] = 99;
    EXPECT_EQUAL(99, alloc[1]);
    alloc.free(1);
    EXPECT_EQUAL(2ul, alloc.size());
    EXPECT_EQUAL(7, alloc[0]);
    EXPECT_EQUAL(-17, alloc[2]);
    EXPECT_EQUAL(1ul, alloc.alloc(103));
    EXPECT_EQUAL(3ul, alloc.size());
    EXPECT_EQUAL(7, alloc[0]);
    EXPECT_EQUAL(103, alloc[1]);
    EXPECT_EQUAL(-17, alloc[2]);

    alloc.clear();
    EXPECT_EQUAL(0ul, alloc.size());
}

TEST_APPHOOK(Test)
