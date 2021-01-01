// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for hashtable.

#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/identity.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/hash_map.h>

using vespalib::hashtable;
using std::vector;

using namespace vespalib;

namespace {

template<typename T>
struct Dereference : std::unary_function<T, std::unique_ptr<T>> {
    T &operator()(std::unique_ptr<T>& p) const { return *p; }
    const T& operator()(const std::unique_ptr<T>& p) const { return *p; }
};

template<typename K> using up_hashtable =
    hashtable<K, std::unique_ptr<K>,
              vespalib::hash<K>, std::equal_to<K>, Dereference<K>>;

TEST("require that hashtable can store unique_ptrs") {
    up_hashtable<int> table(100);
    typedef std::unique_ptr<int> UP;
    table.insert(UP(new int(42)));
    auto it = table.find(42);
    EXPECT_EQUAL(42, **it);

    UP u = std::move(*it);  // This changes the key. Don't do this.
    EXPECT_EQUAL(42, *u);

    // it = table.find(42);  // This will crash, since the key is removed.
}


template <typename K, typename V> using Entry =
    std::pair<K, std::unique_ptr<V>>;
typedef hashtable<int, Entry<int, int>,
                  vespalib::hash<int>, std::equal_to<int>,
                  Select1st<Entry<int, int>>> PairHashtable;

TEST("require that hashtable can store pairs of <key, unique_ptr to value>") {
    PairHashtable table(100);
    table.insert(make_pair(42, std::unique_ptr<int>(new int(84))));
    PairHashtable::iterator it = table.find(42);
    EXPECT_EQUAL(84, *it->second);
    auto it2 = table.find(42);
    EXPECT_EQUAL(84, *it2->second);  // find is not destructive.

    std::unique_ptr<int> up = std::move(it->second);
    it2 = table.find(42);
    EXPECT_FALSE(it2->second.get());  // value has been moved out.
}

template<typename K> using set_hashtable =
    hashtable<K, K, vespalib::hash<K>, std::equal_to<K>, Identity>;

TEST("require that hashtable<int> can be copied") {
    set_hashtable<int> table(100);
    table.insert(42);
    set_hashtable<int> table2(table);
    EXPECT_EQUAL(42, *table2.find(42));
}

TEST("require that you can insert duplicates") {
    using Pair = std::pair<int, vespalib::string>;
    using Map = hashtable<int, Pair, vespalib::hash<int>, std::equal_to<int>, Select1st<Pair>>;

    Map m(1);
    EXPECT_EQUAL(0u, m.size());
    EXPECT_EQUAL(8u, m.capacity());
    auto res = m.insert(Pair(1, "1"));
    EXPECT_TRUE(res.second);
    EXPECT_EQUAL(1u, m.size());
    EXPECT_EQUAL(8u, m.capacity());
    res = m.insert(Pair(1, "1.2"));
    EXPECT_FALSE(res.second);
    auto found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQUAL(found->second, "1");

    m.force_insert(Pair(1, "1.2"));
    EXPECT_EQUAL(2u, m.size());
    EXPECT_EQUAL(8u, m.capacity());
    m.force_insert(Pair(1, "1.3"));
    EXPECT_EQUAL(3u, m.size());
    EXPECT_EQUAL(16u, m.capacity()); // Resize has been conducted
    Pair expected[3] = {{1,"1"},{1,"1.2"},{1,"1.3"}};
    size_t index(0);
    for (const auto & e : m) {
        EXPECT_EQUAL(expected[index].first, e.first);
        EXPECT_EQUAL(expected[index].second, e.second);
        index++;
    }
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQUAL(found->second, "1");

    m.erase(1);
    EXPECT_EQUAL(2u, m.size());
    EXPECT_EQUAL(16u, m.capacity());
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQUAL(found->second, "1.3");

    m.erase(1);
    EXPECT_EQUAL(1u, m.size());
    EXPECT_EQUAL(16u, m.capacity());
    found = m.find(1);
    ASSERT_TRUE(found != m.end());
    EXPECT_EQUAL(found->second, "1.2");
}

template<typename To, typename Vector>
struct FirstInVector : std::unary_function<To, Vector> {
    To &operator()(Vector& v) const { return v[0]; }
    const To& operator()(const Vector& v) const { return v[0]; }
};

TEST("require that hashtable<vector<int>> can be copied") {
    typedef hashtable<int, vector<int>, vespalib::hash<int>,
        std::equal_to<int>, FirstInVector<int, vector<int>>> VectorHashtable;
    VectorHashtable table(100);
    table.insert(std::vector<int>{2, 4, 6});
    VectorHashtable table2(table);
    EXPECT_EQUAL(6, (*table2.find(2))[2]);
    EXPECT_EQUAL(6, (*table.find(2))[2]);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
