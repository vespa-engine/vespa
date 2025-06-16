// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for hashtable.

#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/identity.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::hashtable;
using std::vector;

using namespace vespalib;

namespace {

template<typename T>
struct Dereference {
    T &operator()(std::unique_ptr<T>& p) const { return *p; }
    const T& operator()(const std::unique_ptr<T>& p) const { return *p; }
};

template<typename K> using up_hashtable =
    hashtable<K, std::unique_ptr<K>,
              vespalib::hash<K>, std::equal_to<K>, Dereference<K>>;

TEST(HashtableTest, require_that_hashtable_can_store_unique_ptrs) {
    up_hashtable<int> table(100);
    using UP = std::unique_ptr<int>;
    table.insert(std::make_unique<int>(42));
    auto it = table.find(42);
    EXPECT_EQ(42, **it);

    UP u = std::move(*it);  // This changes the key. Don't do this.
    EXPECT_EQ(42, *u);

    // it = table.find(42);  // This will crash, since the key is removed.
}


template <typename K, typename V> using Entry =
    std::pair<K, std::unique_ptr<V>>;
typedef hashtable<int, Entry<int, int>,
                  vespalib::hash<int>, std::equal_to<>,
                  Select1st<Entry<int, int>>> PairHashtable;

TEST(HashtableTest, require_that_hashtable_can_store_pairs_of__key__unique_ptr_to_value) {
    PairHashtable table(100);
    table.insert(make_pair(42, std::make_unique<int>(84)));
    PairHashtable::iterator it = table.find(42);
    EXPECT_EQ(84, *it->second);
    auto it2 = table.find(42);
    EXPECT_EQ(84, *it2->second);  // find is not destructive.

    std::unique_ptr<int> up = std::move(it->second);
    it2 = table.find(42);
    EXPECT_FALSE(it2->second.get());  // value has been moved out.
}

template<typename K> using set_hashtable =
    hashtable<K, K, vespalib::hash<K>, std::equal_to<K>, Identity>;

TEST(HashtableTest, require_that_hashtable_int__can_be_copied) {
    set_hashtable<int> table(100);
    table.insert(42);
    set_hashtable<int> table2(table);
    EXPECT_EQ(42, *table2.find(42));
}

TEST(HashtableTest, require_that_getModuloStl_always_return_a_larger_number_in_32_bit_integer_range) {
    for (size_t i=0; i < 32; i++) {
        size_t num = 1ul << i;
        size_t prime = hashtable_base::getModuloStl(num);
        EXPECT_GE(prime, num);
        EXPECT_EQ(prime, hashtable_base::getModuloStl(prime));
        EXPECT_GT(hashtable_base::getModuloStl(prime+1), prime + 1);
        printf("%lu <= %lu\n", num, prime);
    }
    for (size_t i=0; i < 32; i++) {
        size_t num = (1ul << i) - 1;
        size_t prime = hashtable_base::getModuloStl(num);
        EXPECT_GE(prime, num);
        printf("%lu <= %lu\n", num, prime);
    }
}

TEST(HashtableTest, require_that_you_can_insert_duplicates) {
    using Pair = std::pair<int, std::string>;
    using Map = hashtable<int, Pair, vespalib::hash<int>, std::equal_to<>, Select1st<Pair>>;

    Map m(1);
    EXPECT_EQ(0u, m.size());
    EXPECT_EQ(8u, m.capacity());
    auto res = m.insert(Pair(1, "1"));
    EXPECT_TRUE(res.second);
    EXPECT_EQ(1u, m.size());
    EXPECT_EQ(8u, m.capacity());
    res = m.insert(Pair(1, "1.2"));
    EXPECT_FALSE(res.second);
    auto found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQ(found->second, "1");

    m.force_insert(Pair(1, "1.2"));
    EXPECT_EQ(2u, m.size());
    EXPECT_EQ(8u, m.capacity());
    m.force_insert(Pair(1, "1.3"));
    EXPECT_EQ(3u, m.size());
    EXPECT_EQ(16u, m.capacity()); // Resize has been conducted
    Pair expected[3] = {{1,"1"},{1,"1.2"},{1,"1.3"}};
    size_t index(0);
    for (const auto & e : m) {
        EXPECT_EQ(expected[index].first, e.first);
        EXPECT_EQ(expected[index].second, e.second);
        index++;
    }
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQ(found->second, "1");

    m.erase(1);
    EXPECT_EQ(2u, m.size());
    EXPECT_EQ(16u, m.capacity());
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQ(found->second, "1.3");

    m.erase(1);
    EXPECT_EQ(1u, m.size());
    EXPECT_EQ(16u, m.capacity());
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQ(found->second, "1.2");
}

template<typename To, typename Vector>
struct FirstInVector {
    To &operator()(Vector& v) const { return v[0]; }
    const To& operator()(const Vector& v) const { return v[0]; }
};

TEST(HashtableTest, require_that_hashtable_vector_int___can_be_copied) {
    typedef hashtable<int, vector<int>, vespalib::hash<int>,
        std::equal_to<>, FirstInVector<int, vector<int>>> VectorHashtable;
    VectorHashtable table(100);
    table.insert(std::vector<int>{2, 4, 6});
    VectorHashtable table2(table);
    EXPECT_EQ(6, (*table2.find(2))[2]);
    EXPECT_EQ(6, (*table.find(2))[2]);
}

/**
 * Test to profile destruction and recreation of hash map.
 * It revealed some unexpected behaviour. Results with 10k iterations on 2018 macbook pro 2.6 Ghz i7
 * 1 - previous - 14.7s hash_node() : _node(), _next(invalid) {}
 * 2 - test     -  6.6s hash_node() : _next(invalid) { memset(_node, 0, sizeof(node)); }
 * 3 - current  -  2.3s hash_node() : _next(invalid) {}
 */
TEST(HashtableTest, benchmark_hash_table_reconstruction_with_POD_objects) {
    vespalib::hash_map<uint32_t, uint32_t> m(1000000);
    constexpr size_t NUM_ITER = 10; // Set to 1k-10k to get measurable numbers 10k ~= 2.3s
    for (size_t i(0); i < NUM_ITER; i++) {
        m[46] = 17;
        EXPECT_FALSE(m.empty());
        EXPECT_EQ(1u, m.size());
        EXPECT_EQ(1048576u, m.capacity());
        m.clear();
        EXPECT_TRUE(m.empty());
        EXPECT_EQ(1048576u, m.capacity());
    }
}

class NonPOD {
public:
    NonPOD() noexcept
        : _v(rand())
    {
        construction_count++;
    }
    NonPOD(NonPOD && rhs) noexcept { _v = rhs._v; rhs._v = -1; }
    NonPOD & operator =(NonPOD && rhs) noexcept { _v = rhs._v; rhs._v = -1; return *this; }
    NonPOD(const NonPOD &) = delete;
    NonPOD & operator =(const NonPOD &) = delete;
    ~NonPOD() {
        if (_v != -1) {
            destruction_count++;
        }
    }
    int32_t _v;
    static size_t construction_count;
    static size_t destruction_count;
};

size_t NonPOD::construction_count = 0;
size_t NonPOD::destruction_count = 0;

/**
 * Performance is identical for NonPOD objects as with POD object.
 * Object are are only constructed on insert, and destructed on erase/clear.
 */
TEST(HashtableTest, benchmark_hash_table_reconstruction_with_non_POD_objects) {
    vespalib::hash_map<uint32_t, NonPOD> m(1000000);
    constexpr size_t NUM_ITER = 10; // Set to 1k-10k to get measurable numbers 10k ~= 2.3s
    NonPOD::construction_count = 0;
    NonPOD::destruction_count = 0;
    for (size_t i(0); i < NUM_ITER; i++) {
        EXPECT_EQ(i, NonPOD::construction_count);
        EXPECT_EQ(i, NonPOD::destruction_count);
        m.insert(std::make_pair(46, NonPOD()));
        EXPECT_EQ(i+1, NonPOD::construction_count);
        EXPECT_EQ(i, NonPOD::destruction_count);
        EXPECT_FALSE(m.empty());
        EXPECT_EQ(1u, m.size());
        EXPECT_EQ(1048576u, m.capacity());
        m.clear();
        EXPECT_EQ(i+1, NonPOD::construction_count);
        EXPECT_EQ(i+1, NonPOD::destruction_count);
        EXPECT_TRUE(m.empty());
        EXPECT_EQ(1048576u, m.capacity());
    }
    EXPECT_EQ(NUM_ITER, NonPOD::construction_count);
    EXPECT_EQ(NUM_ITER, NonPOD::destruction_count);
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
