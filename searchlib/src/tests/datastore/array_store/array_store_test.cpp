// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("array_store_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchlib/datastore/array_store.hpp>
#include <vector>

using namespace search::datastore;

template <typename EntryT>
struct Fixture
{
    using ArrayStoreType = ArrayStore<EntryT>;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using EntryVector = std::vector<EntryT>;

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

using SimpleTypeFixture = Fixture<uint32_t>;

TEST_F("require that we can add and get small arrays of simple type", SimpleTypeFixture(5))
{
    TEST_DO(f.assertAdd({}));
    TEST_DO(f.assertAdd({1}));
    TEST_DO(f.assertAdd({2,3}));
    TEST_DO(f.assertAdd({3,4,5}));
    TEST_DO(f.assertAdd({4,5,6,7}));
    TEST_DO(f.assertAdd({5,6,7,8,9}));
}

TEST_F("require that we can add and get large arrays of simple type", SimpleTypeFixture(3))
{
    TEST_DO(f.assertAdd({1,2,3,4}));
    TEST_DO(f.assertAdd({2,3,4,5,6}));
    TEST_DO(f.assertAdd({3,4,5,6,7,8}));

}

TEST_MAIN() { TEST_RUN_ALL(); }
