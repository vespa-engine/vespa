// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("multivaluemapping2_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/multi_value_mapping2.h>
#include <vespa/searchlib/attribute/multi_value_mapping2.hpp>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/test/insertion_operators.h>

template <typename EntryT>
void
assertArray(std::vector<EntryT> exp, vespalib::ConstArrayRef<EntryT> values)
{
    EXPECT_EQUAL(exp, std::vector<EntryT>(values.cbegin(), values.cend()));
}

template <typename EntryT>
class Fixture
{
    search::attribute::MultiValueMapping2<EntryT> _mvMapping;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;
    Fixture(uint32_t maxSmallArraySize)
        : _mvMapping(maxSmallArraySize)
    {
    }
    ~Fixture() { }

    void set(uint32_t docId, std::vector<EntryT> values) { _mvMapping.set(docId, values); }
    ConstArrayRef get(uint32_t docId) { return _mvMapping.get(docId); }
    void assertGet(uint32_t docId, std::vector<EntryT> exp)
    {
        ConstArrayRef act = get(docId);
        EXPECT_EQUAL(exp, std::vector<EntryT>(act.cbegin(), act.cend()));
    }
    void transferHoldLists(generation_t generation) { _mvMapping.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _mvMapping.trimHoldLists(firstUsed); }
};

TEST_F("Test that set and get works", Fixture<int>(3))
{
    f.set(1, {});
    f.set(2, {4, 7});
    f.set(3, {5});
    f.set(4, {10, 14, 17, 16});
    f.set(5, {3});
    TEST_DO(f.assertGet(1, {}));
    TEST_DO(f.assertGet(2, {4, 7}));
    TEST_DO(f.assertGet(3, {5}));
    TEST_DO(f.assertGet(4, {10, 14, 17, 16}));
    TEST_DO(f.assertGet(5, {3}));
}

TEST_F("Test that old value is not overwritten while held", Fixture<int>(3))
{
    f.set(3, {5});
    typename F1::ConstArrayRef old3 = f.get(3);
    TEST_DO(assertArray({5}, old3));
    f.set(3, {7});
    f.transferHoldLists(10);
    TEST_DO(assertArray({5}, old3));
    TEST_DO(f.assertGet(3, {7}));
    f.trimHoldLists(10);
    TEST_DO(assertArray({5}, old3));
    f.trimHoldLists(11);
    TEST_DO(assertArray({0}, old3));
}

TEST_MAIN() { TEST_RUN_ALL(); }
