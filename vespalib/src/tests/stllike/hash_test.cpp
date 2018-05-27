// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <cstddef>
#include <algorithm>

using namespace vespalib;
using std::make_pair;

namespace {
    struct Foo {
        int i;

        Foo() : i(0) {}
        Foo(int i_) : i(i_) {}

        bool operator==(const Foo& f) const
            { return (i == f.i); }

        struct hash {
            size_t operator() (const Foo& f) const {
                return (f.i % 16);
            }
        };
        friend std::ostream & operator << (std::ostream & os, const Foo & f) { return os << f.i; }
    };
}

TEST("test hash set with custom type and hash function")
{
    const size_t testSize(2000);
    hash_set<Foo, Foo::hash> set(100);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(Foo(7));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(Foo(7)) != set.end());
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(set.find(Foo(8)) == set.end());
    // erase non existing
    set.erase(Foo(8));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(Foo(7)) != set.end());
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(set.find(Foo(8)) == set.end());
    // erase existing
    set.erase(Foo(7));
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(Foo(7)) == set.end());
    for (size_t i(0); i < testSize; i++) {
        set.insert(Foo(i));
        hash_set<Foo, Foo::hash>::iterator it = set.find(Foo(i));
        ASSERT_TRUE(it != set.end());
        for (size_t j=0; j < i; j++) {
            it = set.find(Foo(j));
            ASSERT_TRUE(it != set.end());
        }
    }
    EXPECT_TRUE(set.size() == testSize);
    hash_set<Foo, Foo::hash>::iterator it = set.find(Foo((testSize/2)-1));
    ASSERT_TRUE(it != set.end());
    EXPECT_EQUAL(*it, Foo((testSize/2)-1));
    for (size_t i(0); i < testSize/2; i++) {
        set.erase(Foo(i*2));
    }
    ASSERT_TRUE(it != set.end());
    EXPECT_EQUAL(*it, Foo((testSize/2)-1));
    EXPECT_TRUE(set.find(Foo(testSize/2)) == set.end());
    EXPECT_TRUE(set.size() == testSize/2);
    for (size_t i(0); i < testSize; i++) {
        set.insert(Foo(i));
    }
    EXPECT_EQUAL(set.size(), testSize);
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(*set.find(Foo(0)) == Foo(0));
    EXPECT_TRUE(*set.find(Foo(1)) == Foo(1));
    EXPECT_TRUE(*set.find(Foo(testSize-1)) == Foo(testSize-1));
    EXPECT_TRUE(set.find(Foo(testSize)) == set.end());

    set.clear();

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(Foo(7)) == set.end());
}

TEST("test hash set with simple type")
{
    hash_set<int> set(1000);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(7);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase non existing
    set.erase(8);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase existing
    set.erase(7);
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    for (size_t i(0); i < 10000; i++) {
        set.insert(i);
    }
    EXPECT_TRUE(set.size() == 10000);
    for (size_t i(0); i < 5000; i++) {
        set.erase(i*2);
    }
    EXPECT_TRUE(*set.find(4999) == 4999);
    EXPECT_TRUE(set.find(5000) == set.end());
    EXPECT_TRUE(set.size() == 5000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(i);
    }
    EXPECT_EQUAL(set.size(), 10000u);
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(*set.find(0) == 0);
    EXPECT_TRUE(*set.find(1) == 1);
    EXPECT_TRUE(*set.find(9999) == 9999);
    EXPECT_TRUE(set.find(10000) == set.end());

    set.clear();

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(7) == set.end());
}

TEST("test hash map iterator stability")
{
    hash_map<int, int> h;
    EXPECT_EQUAL(1ul, h.capacity());
    for (size_t i(0); i < 100; i++) {
        EXPECT_TRUE(h.find(i) == h.end());
        h[i] = i;
        EXPECT_TRUE(h.find(i) != h.end());
        int * p1 = & h.find(i)->second;
        int * p2 = & h[i];
        EXPECT_EQUAL(p1, p2);
    }
    EXPECT_EQUAL(128ul, h.capacity());
}


class Clever {
public:
    Clever() : _counter(&_global) { (*_counter)++; }
    Clever(volatile size_t * counter) :
        _counter(counter)
    {
        (*_counter)++;
    }
    Clever(const Clever & rhs) :
        _counter(rhs._counter)
    {
        (*_counter)++;
    }
    Clever & operator = (const Clever & rhs)
    {
        if (&rhs != this) {
            Clever tmp(rhs);
            swap(tmp);
        }
        return *this;
    }
    void swap(Clever & rhs)
    {
        std::swap(_counter, rhs._counter);
    }
    ~Clever() { (*_counter)--; }
    static size_t getGlobal() { return _global; }
private:
    volatile size_t * _counter;
    static size_t _global;
};

size_t Clever::_global = 0;

TEST("test hash map resizing")
{
    volatile size_t counter(0);
    {
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        Clever c(&counter);
        EXPECT_EQUAL(1ul, counter);
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        {
            hash_map<int, Clever> h;
            h[0] = c;
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQUAL(2+i, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQUAL(10001ul, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
            h.clear();
            EXPECT_EQUAL(1ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQUAL(2+i, counter);
            }
            EXPECT_EQUAL(10001ul, counter);
        }
        EXPECT_EQUAL(0ul, Clever::getGlobal());
        EXPECT_EQUAL(1ul, counter);
    }
    EXPECT_EQUAL(0ul, Clever::getGlobal());
    EXPECT_EQUAL(0ul, counter);
}

TEST("test hash map with simple key and value type")
{
    hash_map<int, int> set(1000);
    // Verfify start conditions.
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    // Insert one element
    set.insert(make_pair(7, 70));
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase non existing
    set.erase(8);
    EXPECT_TRUE(set.size() == 1);
    EXPECT_TRUE(set.begin() != set.end());
    EXPECT_TRUE(set.find(7) != set.end());
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(8) == set.end());
    // erase existing
    set.erase(7);
    EXPECT_TRUE(set.size() == 0);
    EXPECT_TRUE(set.begin() == set.end());
    EXPECT_TRUE(set.find(7) == set.end());
    for (size_t i(0); i < 10000; i++) {
        set.insert(make_pair(i,i*10));
    }
    EXPECT_TRUE(set.size() == 10000);
    for (size_t i(0); i < 5000; i++) {
        set.erase(i*2);
    }
    EXPECT_TRUE(set.find(4999)->first == 4999);
    EXPECT_TRUE(set.find(4999)->second == 49990);
    EXPECT_TRUE(set.find(5000) == set.end());
    EXPECT_TRUE(set.size() == 5000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(make_pair(i,i*10));
    }
    EXPECT_EQUAL(set.size(), 10000u);
    EXPECT_TRUE(set.find(7)->first == 7);
    EXPECT_TRUE(set.find(7)->second == 70);
    EXPECT_TRUE(set.find(0)->first == 0);
    EXPECT_TRUE(set.find(0)->second == 0);
    EXPECT_TRUE(set.find(1)->first == 1);
    EXPECT_TRUE(set.find(1)->second == 10);
    EXPECT_TRUE(set.find(9999)->first == 9999);
    EXPECT_TRUE(set.find(9999)->second == 99990);
    EXPECT_TRUE(set.find(10000) == set.end());

    hash_map<int, int> set2(7);
    set.swap(set2);
    EXPECT_EQUAL(set2.size(), 10000u);
    EXPECT_TRUE(set2.find(7)->first == 7);
    EXPECT_TRUE(set2.find(7)->second == 70);

    EXPECT_EQUAL(set.size(), 0u);
    EXPECT_TRUE(set.find(7) == set.end());
    for (int i=0; i < 100; i++) {
        set.insert(make_pair(i,i*10));
    }
    for (int i=0; i < 100; i++) {
        EXPECT_TRUE(set.find(i)->second == i*10);
    }

    hash_map<int, int> set3;
    set3.insert(set.begin(), set.end());
    for (int i=0; i < 100; i++) {
        EXPECT_EQUAL(i*10, set.find(i)->second);
    }

    {
       hash_map<int, int> a, b;
       EXPECT_TRUE(a == b);
       a[1] = 2;
       EXPECT_FALSE(a == b);
       EXPECT_TRUE(a == a);
       b[1] = 3;
       EXPECT_FALSE(a == b);
       a[2] = 7;
       EXPECT_FALSE(a == b);
       b[1] = 2;
       EXPECT_FALSE(a == b);
       b[2] = 7;
       EXPECT_TRUE(a == b);
    }
}

class S {
public:
    explicit S(uint64_t l=0) : _a(l&0xfffffffful), _b(l>>32) { }
    uint32_t hash() const { return _a; }
    uint32_t a() const { return _a; }
    friend bool operator == (const S & a, const S & b) { return a._a == b._a && a._b == b._b; }
private:
    uint32_t _a, _b;
};

struct myhash {
    size_t operator() (const S & arg) const { return arg.hash(); }
};

struct myextract {
    uint32_t operator() (const S & arg) const { return arg.a(); }
};

TEST("test hash set find")
{
    hash_set<S, myhash> set(1000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(S(i));
    }
    EXPECT_TRUE(*set.find(S(1)) == S(1));
    hash_set<S, myhash>::iterator cit = set.find<uint32_t, myextract, vespalib::hash<uint32_t>, std::equal_to<uint32_t> >(7);
    EXPECT_TRUE(*cit == S(7));
}

TEST("test hash set range constructor")
{
    // std::string satisfies iterable char range concept
    std::string chars("abcd");
    hash_set<char> set(chars.begin(), chars.end());
    EXPECT_EQUAL(4u, set.size());
    for (size_t i = 0; i < chars.size(); ++i) {
        EXPECT_TRUE(set.find(chars[i]) != set.end());
    }
}

namespace {

template <typename T0, typename T1>
struct equal_types {
    static const bool value = false;
};

template <typename T0>
struct equal_types<T0, T0> {
    static const bool value = true;
};

}

TEST("test hash set iterators stl compatible")
{
    typedef vespalib::hash_set<int> set_type;
    typedef set_type::iterator iter_type;
    typedef std::iterator_traits<iter_type> iter_traits;

    set_type set;
    set.insert(123);
    set.insert(456);
    set.insert(789);

    std::vector<int> vec(set.begin(), set.end());
    std::sort(vec.begin(), vec.end());
    ASSERT_EQUAL(size_t(3), vec.size());
    EXPECT_EQUAL(123, vec[0]);
    EXPECT_EQUAL(456, vec[1]);
    EXPECT_EQUAL(789, vec[2]);

    // Meta-testing
    ASSERT_TRUE((equal_types<int, int>::value));
    ASSERT_FALSE((equal_types<int, char>::value));

    // These could be compile-time assertions...
    EXPECT_TRUE((equal_types<iter_traits::difference_type, ptrdiff_t>::value));
    EXPECT_TRUE((equal_types<iter_traits::value_type, int>::value));
    EXPECT_TRUE((equal_types<iter_traits::reference, int&>::value));
    EXPECT_TRUE((equal_types<iter_traits::pointer, int*>::value));
    EXPECT_TRUE((equal_types<iter_traits::iterator_category, std::forward_iterator_tag>::value));

    typedef set_type::const_iterator const_iter_type;
    typedef std::iterator_traits<const_iter_type> const_iter_traits;
    EXPECT_TRUE((equal_types<const_iter_traits::difference_type, ptrdiff_t>::value));
    EXPECT_TRUE((equal_types<const_iter_traits::value_type, const int>::value));
    EXPECT_TRUE((equal_types<const_iter_traits::reference, const int&>::value));
    EXPECT_TRUE((equal_types<const_iter_traits::pointer, const int*>::value));
    EXPECT_TRUE((equal_types<const_iter_traits::iterator_category, std::forward_iterator_tag>::value));
}

void
verify_sum(const hash_map<size_t, size_t> & m, size_t expexted_sum) {
    size_t computed_sum = 0;
    std::for_each(m.begin(), m.end(), [&computed_sum](const auto & v) { computed_sum += v.second; });
    EXPECT_EQUAL(expexted_sum, computed_sum);
    computed_sum = 0;
    m.for_each([&computed_sum](const auto & v) { computed_sum += v.second; });
    EXPECT_EQUAL(expexted_sum, computed_sum);
}

TEST("test that for_each member works as std::for_each") {
    hash_map<size_t, size_t> m;
    size_t expected_sum(0);
    for (size_t i(0); i < 1000; i++) {
        TEST_DO(verify_sum(m, expected_sum));
        m[i] = i;
        expected_sum += i;
    }
    TEST_DO(verify_sum(m, expected_sum));
}

namespace {

class WrappedKey
{
    std::unique_ptr<const int> _key;
public:
    WrappedKey() : _key() { }
    WrappedKey(int key) : _key(std::make_unique<const int>(key)) { }
    size_t hash() const { return vespalib::hash<int>()(*_key); }
    bool operator==(const WrappedKey &rhs) const { return *_key == *rhs._key; }
};

}

TEST("test that hash map can have non-copyable key")
{
    hash_map<WrappedKey, int> m;
    EXPECT_TRUE(m.insert(std::make_pair(WrappedKey(4), 5)).second);
    WrappedKey testKey(4);
    ASSERT_TRUE(m.find(testKey) != m.end());
    EXPECT_EQUAL(5, m.find(testKey)->second);
}

TEST("test that hash map can have non-copyable value")
{
    hash_map<int, std::unique_ptr<int>> m;
    EXPECT_TRUE(m.insert(std::make_pair(4, std::make_unique<int>(5))).second);
    EXPECT_TRUE(m[4]);
    EXPECT_EQUAL(5, *m[4]);
}

TEST("test that hash set can have non-copyable key")
{
    hash_set<WrappedKey> m;
    EXPECT_TRUE(m.insert(WrappedKey(4)).second);
    WrappedKey testKey(4);
    ASSERT_TRUE(m.find(testKey) != m.end());
}

using IntHashSet = hash_set<int>;

TEST("test hash set initializer list - empty")
{
    IntHashSet s = {};
    EXPECT_EQUAL(0u, s.size());
}

TEST("test hash set initializer list - 1 element")
{
    IntHashSet s = {1};
    EXPECT_EQUAL(1u, s.size());
    EXPECT_TRUE(s.find(1) != s.end());
}

TEST("test hash set initializer list - many elements")
{
    IntHashSet s = {1,2,3};
    EXPECT_EQUAL(3u, s.size());
    EXPECT_TRUE(s.find(1) != s.end());
    EXPECT_TRUE(s.find(2) != s.end());
    EXPECT_TRUE(s.find(3) != s.end());
}

bool
checkEquals(const IntHashSet &lhs, const IntHashSet &rhs)
{
    return lhs == rhs;
}

TEST("test hash set operator==")
{
    EXPECT_TRUE(checkEquals({}, {}));
    EXPECT_TRUE(checkEquals({1}, {1}));
    EXPECT_TRUE(checkEquals({1,2,3}, {1,2,3}));
    EXPECT_TRUE(checkEquals({1,2,3}, {3,2,1}));
    EXPECT_FALSE(checkEquals({1}, {}));
    EXPECT_FALSE(checkEquals({}, {1}));
    EXPECT_FALSE(checkEquals({1,2}, {1}));
    EXPECT_FALSE(checkEquals({1}, {1,2}));
    EXPECT_FALSE(checkEquals({1,2,3}, {2,3,4}));
    EXPECT_FALSE(checkEquals({2,3,4}, {1,2,3}));
}

TEST("test hash table capacity and size") {
    hash_set<int> empty;
    EXPECT_EQUAL(0u, empty.size());
    EXPECT_EQUAL(1u, empty.capacity());

    hash_set<int> one(1);
    EXPECT_EQUAL(0u, one.size());
    EXPECT_EQUAL(8u, one.capacity());

    hash_set<int> three(3);
    EXPECT_EQUAL(0u, three.size());
    EXPECT_EQUAL(8u, three.capacity());

    hash_set<int> many(1894);
    EXPECT_EQUAL(0u, many.size());
    EXPECT_EQUAL(2048u, many.capacity());
}

TEST("test that begin and end are identical with empty hashtables") {
    hash_set<int> empty;
    EXPECT_TRUE(empty.begin() == empty.end());
    hash_set<int> empty_but_reserved(10);
    EXPECT_TRUE(empty_but_reserved.begin() == empty_but_reserved.end());
}

TEST_MAIN() { TEST_RUN_ALL(); }
