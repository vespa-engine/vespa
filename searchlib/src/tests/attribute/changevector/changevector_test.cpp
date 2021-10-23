// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/attribute/changevector.hpp>
#include <vespa/vespalib/stllike/hash_set.h>
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
            EXPECT_GREATER(c._data, prev_value);
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

TEST("require insert ordering is preserved for same doc")
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQUAL(1u, a.size());
    a.push_back(Change(Change::NOOP, 7, 2));
    EXPECT_EQUAL(2u, a.size());
    verifyStrictOrdering(a);
}

TEST("require insert ordering is preserved ")
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQUAL(1u, a.size());
    a.push_back(Change(Change::NOOP, 5, 2));
    EXPECT_EQUAL(2u, a.size());
    a.push_back(Change(Change::NOOP, 6, 3));
    EXPECT_EQUAL(3u, a.size());
    verifyStrictOrdering(a);
}

TEST("require insert ordering is preserved with mix")
{
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQUAL(1u, a.size());
    a.push_back(Change(Change::NOOP, 5, 2));
    EXPECT_EQUAL(2u, a.size());
    a.push_back(Change(Change::NOOP, 5, 3));
    EXPECT_EQUAL(3u, a.size());
    a.push_back(Change(Change::NOOP, 6, 10));
    EXPECT_EQUAL(4u, a.size());
    std::vector<long> v({4,5,6,7,8});
    Accessor ac(v);
    a.push_back(5, ac);
    EXPECT_EQUAL(9u, a.size());
    a.push_back(Change(Change::NOOP, 5, 9));
    EXPECT_EQUAL(10u, a.size());
    verifyStrictOrdering(a);
}

TEST("require that inserting empty vector does not affect the vector.") {
    CV a;
    std::vector<long> v;
    Accessor ac(v);
    a.push_back(1, ac);
    EXPECT_EQUAL(0u, a.size());
}

TEST("require that we have control over buffer construction size") {
    CV a;
    EXPECT_EQUAL(0u, a.size());
    EXPECT_EQUAL(256u, a.capacity());
    a.clear();
    EXPECT_EQUAL(0u, a.size());
    EXPECT_EQUAL(256u, a.capacity());
}

TEST("require that buffer can grow some") {
    CV a;
    for (size_t i(0); i < 1024; i++) {
        a.push_back(Change(Change::NOOP, i, i));
    }
    EXPECT_EQUAL(1024u, a.size());
    EXPECT_EQUAL(1024u, a.capacity());
    a.clear();
    EXPECT_EQUAL(0u, a.size());
    EXPECT_EQUAL(1024u, a.capacity());
}

TEST("require that buffer can grow some, but not unbound") {
    CV a;
    for (size_t i(0); i < 1025; i++) {
        a.push_back(Change(Change::NOOP, i, i));
    }
    EXPECT_EQUAL(1025u, a.size());
    EXPECT_EQUAL(2048u, a.capacity());
    a.clear();
    EXPECT_EQUAL(0u, a.size());
    EXPECT_EQUAL(256u, a.capacity());
}

TEST("Control Change size") {
    EXPECT_EQUAL(32u, sizeof(ChangeTemplate<NumericChangeData<long>>));
    EXPECT_EQUAL(80u, sizeof(ChangeTemplate<StringChangeData>));
}

TEST_MAIN() { TEST_RUN_ALL(); }
