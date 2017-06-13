// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/attribute/changevector.hpp>

using namespace search;

template <typename T>
void verifyStrictOrdering(const T & v) {
    long count(0);
    for (const auto & c : v) {
        count++;
        EXPECT_EQUAL(count, c._data.get());
    }
    EXPECT_EQUAL(v.size(), size_t(count));
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
    typedef ChangeTemplate<NumericChangeData<long>> Change;
    typedef ChangeVectorT<Change> CV;
    CV a;
    a.push_back(Change(Change::NOOP, 7, 1));
    EXPECT_EQUAL(1u, a.size());
    a.push_back(Change(Change::NOOP, 7, 2));
    EXPECT_EQUAL(2u, a.size());
    verifyStrictOrdering(a);
}

TEST("require insert ordering is preserved ")
{
    typedef ChangeTemplate<NumericChangeData<long>> Change;
    typedef ChangeVectorT<Change> CV;
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
    typedef ChangeTemplate<NumericChangeData<long>> Change;
    typedef ChangeVectorT<Change> CV;
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
    typedef ChangeTemplate<NumericChangeData<long>> Change;
    typedef ChangeVectorT<Change> CV;
    CV a;
    std::vector<long> v;
    Accessor ac(v);
    a.push_back(1, ac);
    EXPECT_EQUAL(0u, a.size());
}

TEST_MAIN() { TEST_RUN_ALL(); }
