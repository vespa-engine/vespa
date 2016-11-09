// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("array_store_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchlib/datastore/array_store.hpp>
#include <vector>
#include <tr1/type_traits>

using namespace search::datastore;

template <typename EntryT>
struct Fixture
{
    using ArrayStoreType = ArrayStore<EntryT>;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using EntryVector = std::vector<EntryT>;
    using value_type = EntryT;

    ArrayStoreType store;
    Fixture(uint32_t maxSmallArraySize)
        : store(maxSmallArraySize)
    {}
    void assertAdd(const EntryVector &input) {
        EntryRef ref = store.add(ConstArrayRef(input));
        ConstArrayRef output = store.get(ref);
        EXPECT_EQUAL(input, EntryVector(output.begin(), output.end()));
    }
};

using NumberFixture = Fixture<uint32_t>;
using StringFixture = Fixture<std::string>;

TEST("require that we test with trivial and non-trivial types")
{
    EXPECT_TRUE(std::tr1::has_trivial_destructor<NumberFixture::value_type>::value);
    EXPECT_FALSE(std::tr1::has_trivial_destructor<StringFixture::value_type>::value);
}

TEST_F("require that we can add and get small arrays of trivial type", NumberFixture(3))
{
    TEST_DO(f.assertAdd({}));
    TEST_DO(f.assertAdd({1}));
    TEST_DO(f.assertAdd({2,3}));
    TEST_DO(f.assertAdd({3,4,5}));
}

TEST_F("require that we can add and get small arrays of non-trivial type", StringFixture(3))
{
    TEST_DO(f.assertAdd({}));
    TEST_DO(f.assertAdd({"aa"}));
    TEST_DO(f.assertAdd({"bbb", "ccc"}));
    TEST_DO(f.assertAdd({"ddd", "eeee", "fffff"}));
}

TEST_F("require that we can add and get large arrays of simple type", NumberFixture(3))
{
    TEST_DO(f.assertAdd({1,2,3,4}));
    TEST_DO(f.assertAdd({2,3,4,5,6}));
}

TEST_F("require that we can add and get large arrays of non-trivial type", StringFixture(3))
{
    TEST_DO(f.assertAdd({"aa", "bb", "cc", "dd"}));
    TEST_DO(f.assertAdd({"ddd", "eee", "ffff", "gggg", "hhhh"}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
