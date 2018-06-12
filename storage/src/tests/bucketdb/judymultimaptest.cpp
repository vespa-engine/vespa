// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".judy_multi_map_test");
#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/judymultimap.hpp>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <boost/assign.hpp>
#include <boost/random.hpp>
#include <cppunit/extensions/HelperMacros.h>
#include <map>
#include <ostream>
#include <vector>

namespace storage {

struct JudyMultiMapTest : public CppUnit::TestFixture {
    void testSimpleUsage();
    void testIterator();

    CPPUNIT_TEST_SUITE(JudyMultiMapTest);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST(testIterator);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(JudyMultiMapTest);

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

void
JudyMultiMapTest::testSimpleUsage() {
    typedef JudyMultiMap<C, B, A> MultiMap;
    MultiMap multiMap;
        // Do some insertions
    bool preExisted;
    CPPUNIT_ASSERT(multiMap.empty());
    multiMap.insert(16, A(1, 2, 3), preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    multiMap.insert(11, A(4, 6, 0), preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    multiMap.insert(14, A(42, 0, 0), preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    CPPUNIT_ASSERT_EQUAL_MSG(multiMap.toString(),
                             (MultiMap::size_type) 3, multiMap.size());

    multiMap.insert(11, A(4, 7, 0), preExisted);
    CPPUNIT_ASSERT_EQUAL(true, preExisted);
    CPPUNIT_ASSERT_EQUAL((MultiMap::size_type) 3, multiMap.size());
    CPPUNIT_ASSERT(!multiMap.empty());

        // Access some elements
    CPPUNIT_ASSERT_EQUAL(A(4, 7, 0), multiMap[11]);
    CPPUNIT_ASSERT_EQUAL(A(1, 2, 3), multiMap[16]);
    CPPUNIT_ASSERT_EQUAL(A(42,0, 0), multiMap[14]);

        // Do removes
    CPPUNIT_ASSERT(multiMap.erase(12) == 0);
    CPPUNIT_ASSERT_EQUAL((MultiMap::size_type) 3, multiMap.size());

    CPPUNIT_ASSERT(multiMap.erase(14) == 1);
    CPPUNIT_ASSERT_EQUAL((MultiMap::size_type) 2, multiMap.size());

    CPPUNIT_ASSERT(multiMap.erase(11) == 1);
    CPPUNIT_ASSERT(multiMap.erase(16) == 1);
    CPPUNIT_ASSERT_EQUAL((MultiMap::size_type) 0, multiMap.size());
    CPPUNIT_ASSERT(multiMap.empty());
}

void
JudyMultiMapTest::testIterator()
{
    typedef JudyMultiMap<C, B, A> MultiMap;
    MultiMap multiMap;
    bool preExisted;
    // Do some insertions
    multiMap.insert(16, A(1, 2, 3), preExisted);
    multiMap.insert(11, A(4, 6, 0), preExisted);
    multiMap.insert(14, A(42, 0, 0), preExisted);

    MultiMap::Iterator iter = multiMap.begin();
    CPPUNIT_ASSERT_EQUAL((uint64_t)11, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(4, 6, 0), iter.value());
    ++iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)14, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(42, 0, 0), iter.value());
    ++iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)16, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(1, 2, 3), iter.value());
    --iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)14, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(42, 0, 0), iter.value());
    ++iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)16, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(1, 2, 3), iter.value());
    --iter;
    --iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)11,(uint64_t) iter.key());
    CPPUNIT_ASSERT_EQUAL(A(4, 6, 0), iter.value());
    ++iter;
    ++iter;
    ++iter;
    CPPUNIT_ASSERT_EQUAL(multiMap.end(), iter);
    --iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)16, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(1, 2, 3), iter.value());
    --iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)14, (uint64_t)iter.key());
    CPPUNIT_ASSERT_EQUAL(A(42, 0, 0), iter.value());
    --iter;
    CPPUNIT_ASSERT_EQUAL((uint64_t)11,(uint64_t) iter.key());
    CPPUNIT_ASSERT_EQUAL(A(4, 6, 0), iter.value());
}

} // storage

