// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/changevector.hpp>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;

using Change = ChangeTemplate<NumericChangeData<long>>;
using CV = ChangeVectorT<Change>;

template <typename T>
void verifyStrictOrdering(const T & v) {
    vespalib::hash_set<uint32_t> complete;
    uint32_t prev_doc(0);
    uint32_t prev_value(0);
    for (const auto & c : v.getDocIdInsertOrder()) {
        if (prev_doc != c._doc) {
            complete.insert(prev_doc);
            EXPECT_FALSE(complete.contains(c._doc));
            prev_doc = c._doc;
        } else {
            EXPECT_GT(c._data, prev_value);
        }
        prev_value = c._data;
    }
}

class Accessor {
public:
    Accessor(const std::vector<long> & v) : _size(v.size()), _current(v.begin()), _end(v.end()) { }
    size_t size() const { return _size; }
    void next() { _current++; }
    long value() const { return *_current; }
    int weight() const { return *_current; }
private:
    size_t _size;
    std::vector<long>::const_iterator _current;
    std::vector<long>::const_iterator _end;
};

TEST(ChangeVectorTest, require_insert_ordering_is_preserved_for_same_doc)
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQ(1u, a.size());
    a.push_back(Change(Change::NOOP, 7, 2));
    EXPECT_EQ(2u, a.size());
    verifyStrictOrdering(a);
}

TEST(ChangeVectorTest, require_insert_ordering_is_preserved_)
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQ(1u, a.size());
    a.push_back(Change(Change::NOOP, 5, 2));
    EXPECT_EQ(2u, a.size());
    a.push_back(Change(Change::NOOP, 6, 3));
    EXPECT_EQ(3u, a.size());
    verifyStrictOrdering(a);
}

TEST(ChangeVectorTest, require_insert_ordering_is_preserved_with_mix)
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQ(1u, a.size());
    a.push_back(Change(Change::NOOP, 5, 2));
    EXPECT_EQ(2u, a.size());
    a.push_back(Change(Change::NOOP, 5, 3));
    EXPECT_EQ(3u, a.size());
    a.push_back(Change(Change::NOOP, 6, 10));
    EXPECT_EQ(4u, a.size());
    std::vector<long> v({4,5,6,7,8});
    Accessor ac(v);
    a.push_back(5, ac);
    EXPECT_EQ(9u, a.size());
    a.push_back(Change(Change::NOOP, 5, 9));
    EXPECT_EQ(10u, a.size());
    verifyStrictOrdering(a);
}

TEST(ChangeVectorTest, require_that_inserting_empty_vector_does_not_affect_the_vector) {
    CV a;
    std::vector<long> v;
    Accessor ac(v);
    a.push_back(1, ac);
    EXPECT_EQ(0u, a.size());
}

TEST(ChangeVectorTest, require_that_we_have_control_over_buffer_construction_size) {
    CV a;
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(4u, a.capacity());
    a.clear();
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(4u, a.capacity());
}

TEST(ChangeVectorTest, require_that_buffer_can_grow_some) {
    CV a;
    for (size_t i(0); i < 1024; i++) {
        a.push_back(Change(Change::NOOP, i, i));
    }
    EXPECT_EQ(1024u, a.size());
    EXPECT_EQ(1024u, a.capacity());
    a.clear();
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(1024u, a.capacity());
}

TEST(ChangeVectorTest, require_that_buffer_can_grow_some_but_not_unbound) {
    CV a;
    for (size_t i(0); i < 1025; i++) {
        a.push_back(Change(Change::NOOP, i, i));
    }
    EXPECT_EQ(1025u, a.size());
    EXPECT_EQ(2048u, a.capacity());
    a.clear();
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(256u, a.capacity());
}

TEST(ChangeVectorTest, Control_Change_size) {
    EXPECT_EQ(32u, sizeof(ChangeTemplate<NumericChangeData<long>>));
    EXPECT_EQ(16u + sizeof(std::string), sizeof(ChangeTemplate<StringChangeData>));
}

GTEST_MAIN_RUN_ALL_TESTS()
