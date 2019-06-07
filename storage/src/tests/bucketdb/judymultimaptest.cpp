// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/judymultimap.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <map>
#include <ostream>
#include <vector>

using namespace ::testing;

namespace storage {

namespace {
    struct B;
    struct C;

    struct A {
        int _val1;
        int _val2;
        int _val3;

        A() = default;
        A(const B &);
        A(const C &);
        A(int val1, int val2, int val3)
            : _val1(val1), _val2(val2), _val3(val3) {}

        static bool mayContain(const A&) { return true; }

        bool operator==(const A& a) const {
            return (_val1 == a._val1 && _val2 == a._val2 && _val3 == a._val3);
        }
    };

    struct B {
        int _val1;
        int _val2;

        B() = default;
        B(const A& a) : _val1(a._val1), _val2(a._val2) {}
        B(int val1, int val2) : _val1(val1), _val2(val2) {}

        static bool mayContain(const A& a) { return (a._val3 == 0); }
    };

    struct C {
        int _val1;

        C() = default;
        C(const A& a) : _val1(a._val1) {}
        C(int val1) : _val1(val1) {}

        static bool mayContain(const A& a) { return (a._val2 == 0 && a._val3 == 0); }
    };

    A::A(const B& b) : _val1(b._val1), _val2(b._val2), _val3(0) {}
    A::A(const C& c) : _val1(c._val1), _val2(0), _val3(0) {}

    std::ostream& operator<<(std::ostream& out, const A& a) {
        return out << "A(" << a._val1 << ", " << a._val2 << ", " << a._val3 << ")";
    }
    std::ostream& operator<<(std::ostream& out, const B& b) {
        return out << "B(" << b._val1 << ", " << b._val2 << ")";
    }
    std::ostream& operator<<(std::ostream& out, const C& c) {
        return out << "C(" << c._val1 << ")";
    }
}

TEST(JudyMultiMapTest, simple_usage) {
    typedef JudyMultiMap<C, B, A> MultiMap;
    MultiMap multiMap;
    // Do some insertions
    bool preExisted;
    EXPECT_TRUE(multiMap.empty());
    multiMap.insert(16, A(1, 2, 3), preExisted);
    EXPECT_EQ(false, preExisted);
    multiMap.insert(11, A(4, 6, 0), preExisted);
    EXPECT_EQ(false, preExisted);
    multiMap.insert(14, A(42, 0, 0), preExisted);
    EXPECT_EQ(false, preExisted);
    EXPECT_EQ((MultiMap::size_type) 3, multiMap.size()) << multiMap.toString();

    multiMap.insert(11, A(4, 7, 0), preExisted);
    EXPECT_EQ(true, preExisted);
    EXPECT_EQ((MultiMap::size_type) 3, multiMap.size());
    EXPECT_FALSE(multiMap.empty());

    // Access some elements
    EXPECT_EQ(A(4, 7, 0), multiMap[11]);
    EXPECT_EQ(A(1, 2, 3), multiMap[16]);
    EXPECT_EQ(A(42,0, 0), multiMap[14]);

    // Do removes
    EXPECT_EQ(multiMap.erase(12), 0);
    EXPECT_EQ((MultiMap::size_type) 3, multiMap.size());

    EXPECT_EQ(multiMap.erase(14), 1);
    EXPECT_EQ((MultiMap::size_type) 2, multiMap.size());

    EXPECT_EQ(multiMap.erase(11), 1);
    EXPECT_EQ(multiMap.erase(16), 1);
    EXPECT_EQ((MultiMap::size_type) 0, multiMap.size());
    EXPECT_TRUE(multiMap.empty());
}

TEST(JudyMultiMapTest, iterator) {
    typedef JudyMultiMap<C, B, A> MultiMap;
    MultiMap multiMap;
    bool preExisted;
    // Do some insertions
    multiMap.insert(16, A(1, 2, 3), preExisted);
    multiMap.insert(11, A(4, 6, 0), preExisted);
    multiMap.insert(14, A(42, 0, 0), preExisted);

    MultiMap::Iterator iter = multiMap.begin();
    EXPECT_EQ((uint64_t)11, (uint64_t)iter.key());
    EXPECT_EQ(A(4, 6, 0), iter.value());
    ++iter;
    EXPECT_EQ((uint64_t)14, (uint64_t)iter.key());
    EXPECT_EQ(A(42, 0, 0), iter.value());
    ++iter;
    EXPECT_EQ((uint64_t)16, (uint64_t)iter.key());
    EXPECT_EQ(A(1, 2, 3), iter.value());
    --iter;
    EXPECT_EQ((uint64_t)14, (uint64_t)iter.key());
    EXPECT_EQ(A(42, 0, 0), iter.value());
    ++iter;
    EXPECT_EQ((uint64_t)16, (uint64_t)iter.key());
    EXPECT_EQ(A(1, 2, 3), iter.value());
    --iter;
    --iter;
    EXPECT_EQ((uint64_t)11,(uint64_t) iter.key());
    EXPECT_EQ(A(4, 6, 0), iter.value());
    ++iter;
    ++iter;
    ++iter;
    EXPECT_EQ(multiMap.end(), iter);
    --iter;
    EXPECT_EQ((uint64_t)16, (uint64_t)iter.key());
    EXPECT_EQ(A(1, 2, 3), iter.value());
    --iter;
    EXPECT_EQ((uint64_t)14, (uint64_t)iter.key());
    EXPECT_EQ(A(42, 0, 0), iter.value());
    --iter;
    EXPECT_EQ((uint64_t)11,(uint64_t) iter.key());
    EXPECT_EQ(A(4, 6, 0), iter.value());
}

} // storage

