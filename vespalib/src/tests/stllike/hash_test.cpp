// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <vespa/vespalib/stllike/allocator.h>
#include <cstddef>
#include <algorithm>
#include <atomic>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using std::make_pair;

namespace {
    struct Foo {
        int i;

        Foo() noexcept : i(0) {}
        Foo(int i_) noexcept : i(i_) {}

        bool operator==(const Foo& f) const noexcept
            { return (i == f.i); }

        struct hash {
            size_t operator() (const Foo& f) const noexcept {
                return (f.i % 16);
            }
        };
        friend std::ostream & operator << (std::ostream & os, const Foo & f) { return os << f.i; }
    };
}

TEST(HashTest, test_that_hashValue_gives_expected_response)
{
    const char * s("abcdefghi");
    EXPECT_EQ(16203358805722239136ul, vespalib::hashValue(s));
    EXPECT_EQ(vespalib::hashValue(s), vespalib::hashValue(s, strlen(s)));
    EXPECT_NE(vespalib::hashValue(s), vespalib::hashValue(s, strlen(s)-1));
}

TEST(HashTest, test_hash_set_with_custom_type_and_hash_function)
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
    EXPECT_EQ(*it, Foo((testSize/2)-1));
    for (size_t i(0); i < testSize/2; i++) {
        set.erase(Foo(i*2));
    }
    ASSERT_TRUE(it != set.end());
    EXPECT_EQ(*it, Foo((testSize/2)-1));
    EXPECT_TRUE(set.find(Foo(testSize/2)) == set.end());
    EXPECT_TRUE(set.size() == testSize/2);
    for (size_t i(0); i < testSize; i++) {
        set.insert(Foo(i));
    }
    EXPECT_EQ(set.size(), testSize);
    EXPECT_TRUE(*set.find(Foo(7)) == Foo(7));
    EXPECT_TRUE(*set.find(Foo(0)) == Foo(0));
    EXPECT_TRUE(*set.find(Foo(1)) == Foo(1));
    EXPECT_TRUE(*set.find(Foo(testSize-1)) == Foo(testSize-1));
    EXPECT_TRUE(set.find(Foo(testSize)) == set.end());

    set.clear();

    EXPECT_EQ(set.size(), 0u);
    EXPECT_TRUE(set.find(Foo(7)) == set.end());
}

TEST(HashTest, test_hash_set_with_simple_type)
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
    EXPECT_EQ(set.size(), 10000u);
    EXPECT_TRUE(*set.find(7) == 7);
    EXPECT_TRUE(*set.find(0) == 0);
    EXPECT_TRUE(*set.find(1) == 1);
    EXPECT_TRUE(*set.find(9999) == 9999);
    EXPECT_TRUE(set.find(10000) == set.end());

    set.clear();

    EXPECT_EQ(set.size(), 0u);
    EXPECT_TRUE(set.find(7) == set.end());
}

TEST(HashTest, test_hash_map_iterator_stability)
{
    hash_map<uint32_t, uint32_t> h;
    EXPECT_EQ(1ul, h.capacity());
    for (size_t i(0); i < 100; i++) {
        EXPECT_TRUE(h.find(i) == h.end());
        h[i] = i;
        EXPECT_TRUE(h.find(i) != h.end());
        uint32_t * p1 = & h.find(i)->second;
        uint32_t * p2 = & h[i];
        EXPECT_EQ(p1, p2);
    }
    EXPECT_EQ(128ul, h.capacity());
}


class Clever {
public:
    Clever() : _counter(&_global) { (*_counter)++; }
    Clever(std::atomic<size_t> * counter) :
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
    std::atomic<size_t> * _counter;
    static std::atomic<size_t> _global;
};

std::atomic<size_t> Clever::_global = 0;

TEST(HashTest, test_hash_map_resizing)
{
    std::atomic<size_t> counter(0);
    {
        EXPECT_EQ(0ul, Clever::getGlobal());
        Clever c(&counter);
        EXPECT_EQ(1ul, counter);
        EXPECT_EQ(0ul, Clever::getGlobal());
        {
            hash_map<int, Clever> h;
            h[0] = c;
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQ(2+i, counter);
            }
            EXPECT_EQ(10001ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQ(10001ul, counter);
            }
            EXPECT_EQ(10001ul, counter);
            h.clear();
            EXPECT_EQ(1ul, counter);
            for (size_t i(0); i < 10000; i++) {
                h[i] = c;
                EXPECT_EQ(2+i, counter);
            }
            EXPECT_EQ(10001ul, counter);
        }
        EXPECT_EQ(0ul, Clever::getGlobal());
        EXPECT_EQ(1ul, counter);
    }
    EXPECT_EQ(0ul, Clever::getGlobal());
    EXPECT_EQ(0ul, counter);
}

TEST(HashTest, test_hash_map_with_simple_key_and_value_type)
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
    EXPECT_EQ(set.size(), 10000u);
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
    EXPECT_EQ(set2.size(), 10000u);
    EXPECT_TRUE(set2.find(7)->first == 7);
    EXPECT_TRUE(set2.find(7)->second == 70);

    EXPECT_EQ(set.size(), 0u);
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
        EXPECT_EQ(i*10, set.find(i)->second);
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
    explicit S(uint64_t l=0) noexcept : _a(l&0xfffffffful), _b(l>>32) { }
    uint32_t hash() const { return _a; }
    uint32_t a() const { return _a; }
    friend bool operator == (const S & a, const S & b) noexcept { return a._a == b._a && a._b == b._b; }
private:
    uint32_t _a, _b;
};

struct myhash {
    size_t operator() (const S & arg) const { return arg.hash(); }
    size_t operator() (uint32_t arg) const { return arg; }
};

bool operator == (uint32_t a, const S & b) { return a == b.a(); }
bool operator == (const S & a, uint32_t b) noexcept { return a.a() == b; }

TEST(HashTest, test_hash_set_find)
{
    hash_set<S, myhash> set(1000);
    for (size_t i(0); i < 10000; i++) {
        set.insert(S(i));
    }
    EXPECT_TRUE(*set.find(S(1)) == S(1));
    auto cit = set.find<uint32_t>(7);
    EXPECT_TRUE(*cit == S(7));

    EXPECT_EQ(1u, set.count(S(7)));
    EXPECT_EQ(0u, set.count(S(10007)));
}

TEST(HashTest, test_hash_set_range_constructor)
{
    // std::string satisfies iterable char range concept
    std::string chars("abcd");
    hash_set<char> set(chars.begin(), chars.end());
    EXPECT_EQ(4u, set.size());
    for (size_t i = 0; i < chars.size(); ++i) {
        EXPECT_TRUE(set.find(chars[i]) != set.end());
    }
}

namespace {

template <typename T0, typename T1>
struct equal_types {
    static constexpr bool value = false;
};

template <typename T0>
struct equal_types<T0, T0> {
    static constexpr bool value = true;
};

}

TEST(HashTest, test_hash_set_iterators_stl_compatible)
{
    using set_type = vespalib::hash_set<int>;
    using iter_type = set_type::iterator;
    using iter_traits = std::iterator_traits<iter_type>;

    set_type set;
    set.insert(123);
    set.insert(456);
    set.insert(789);

    std::vector<int> vec(set.begin(), set.end());
    std::sort(vec.begin(), vec.end());
    ASSERT_EQ(size_t(3), vec.size());
    EXPECT_EQ(123, vec[0]);
    EXPECT_EQ(456, vec[1]);
    EXPECT_EQ(789, vec[2]);

    // Meta-testing
    ASSERT_TRUE((equal_types<int, int>::value));
    ASSERT_FALSE((equal_types<int, char>::value));

    // These could be compile-time assertions...
    EXPECT_TRUE((equal_types<iter_traits::difference_type, ptrdiff_t>::value));
    EXPECT_TRUE((equal_types<iter_traits::value_type, int>::value));
    EXPECT_TRUE((equal_types<iter_traits::reference, int&>::value));
    EXPECT_TRUE((equal_types<iter_traits::pointer, int*>::value));
    EXPECT_TRUE((equal_types<iter_traits::iterator_category, std::forward_iterator_tag>::value));

    using const_iter_type = set_type::const_iterator;
    using const_iter_traits = std::iterator_traits<const_iter_type>;
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
    EXPECT_EQ(expexted_sum, computed_sum);
    computed_sum = 0;
    m.for_each([&computed_sum](const auto & v) { computed_sum += v.second; });
    EXPECT_EQ(expexted_sum, computed_sum);
}

TEST(HashTest, test_that_for_each_member_works_as_std__for_each) {
    hash_map<size_t, size_t> m;
    size_t expected_sum(0);
    for (size_t i(0); i < 1000; i++) {
        GTEST_DO(verify_sum(m, expected_sum));
        m[i] = i;
        expected_sum += i;
    }
    GTEST_DO(verify_sum(m, expected_sum));
}

namespace {

class WrappedKey
{
    std::unique_ptr<const int> _key;
public:
    WrappedKey() : _key() { }
    WrappedKey(int key) : _key(std::make_unique<const int>(key)) { }
    size_t hash() const noexcept { return vespalib::hash<int>()(*_key); }
    bool operator==(const WrappedKey &rhs) const noexcept { return *_key == *rhs._key; }
};

}

TEST(HashTest, test_that_hash_map_can_have_non_copyable_key)
{
    hash_map<WrappedKey, int> m;
    EXPECT_TRUE(m.insert(std::make_pair(WrappedKey(4), 5)).second);
    WrappedKey testKey(4);
    ASSERT_TRUE(m.find(testKey) != m.end());
    EXPECT_EQ(5, m.find(testKey)->second);
}

TEST(HashTest, test_that_hash_map_can_have_non_copyable_value)
{
    hash_map<int, std::unique_ptr<int>> m;
    EXPECT_TRUE(m.insert(std::make_pair(4, std::make_unique<int>(5))).second);
    EXPECT_TRUE(m[4]);
    EXPECT_EQ(5, *m[4]);
}

TEST(HashTest, test_that_hash_set_can_have_non_copyable_key)
{
    hash_set<WrappedKey> m;
    EXPECT_TRUE(m.insert(WrappedKey(4)).second);
    WrappedKey testKey(4);
    ASSERT_TRUE(m.find(testKey) != m.end());
}

using IntHashSet = hash_set<int>;

TEST(HashTest, test_hash_set_initializer_list__empty)
{
    IntHashSet s = {};
    EXPECT_EQ(0u, s.size());
}

TEST(HashTest, empty_hash_set_can_be_looked_up)
{
    IntHashSet s;
    EXPECT_EQ(0u, s.size());
    EXPECT_EQ(1u, s.capacity());
    EXPECT_TRUE(s.find(1) == s.end());
}

TEST(HashTest, test_hash_set_initializer_list__1_element)
{
    IntHashSet s = {1};
    EXPECT_EQ(1u, s.size());
    EXPECT_TRUE(s.find(1) != s.end());
}

TEST(HashTest, test_hash_set_initializer_list__many_elements)
{
    IntHashSet s = {1,2,3};
    EXPECT_EQ(3u, s.size());
    EXPECT_TRUE(s.find(1) != s.end());
    EXPECT_TRUE(s.find(2) != s.end());
    EXPECT_TRUE(s.find(3) != s.end());
}

bool
checkEquals(const IntHashSet &lhs, const IntHashSet &rhs)
{
    return lhs == rhs;
}

TEST(HashTest, test_hash_set_operator_eq)
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

TEST(HashTest, test_hash_table_capacity_and_size) {
    hash_set<int> empty;
    EXPECT_EQ(0u, empty.size());
    EXPECT_EQ(1u, empty.capacity());

    hash_set<int> one(1);
    EXPECT_EQ(0u, one.size());
    EXPECT_EQ(8u, one.capacity());

    hash_set<int> three(3);
    EXPECT_EQ(0u, three.size());
    EXPECT_EQ(8u, three.capacity());

    hash_set<int> many(1894);
    EXPECT_EQ(0u, many.size());
    EXPECT_EQ(2048u, many.capacity());
}

TEST(HashTest, test_that_begin_and_end_are_identical_with_empty_hashtables) {
    hash_set<int> empty;
    EXPECT_TRUE(empty.begin() == empty.end());
    hash_set<int> empty_but_reserved(10);
    EXPECT_TRUE(empty_but_reserved.begin() == empty_but_reserved.end());
}

TEST(HashTest, test_that_large_allocator_works_fine_with_std__vector) {
    using V = std::vector<uint64_t, allocator_large<uint64_t>>;
    V a;
    a.push_back(1);
    a.reserve(14);
    for (size_t i(0); i < 400000; i++) {
        a.push_back(i);
    }
    V b = std::move(a);
    V c = b;
    ASSERT_EQ(b.size(), c.size());
}

TEST(HashTest, test_that_hash_table_clear_does_not_resize_hashtable) {
    hash_set<int> a(100);
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(128u, a.capacity());
    for (size_t i(0); i < 100; i++) {
        a.insert(i);
    }
    EXPECT_EQ(100u, a.size());
    EXPECT_EQ(128u, a.capacity());
    a.clear();
    EXPECT_EQ(0u, a.size());
    EXPECT_EQ(128u, a.capacity());
}

TEST(HashTest, test_that_hash_nodes_have_expected_sizes)
{
    EXPECT_EQ(8u, sizeof(hash_node<int8_t>));
    EXPECT_EQ(8u, sizeof(hash_node<int32_t>));
    EXPECT_EQ(16u, sizeof(hash_node<int64_t>));
}

GTEST_MAIN_RUN_ALL_TESTS()
