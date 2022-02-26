// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/lrucache_map.hpp>

using namespace vespalib;

TEST("testCache") {
    lrucache_map< LruParam<int, string> > cache(7);
    // Verfify start conditions.
    EXPECT_TRUE(cache.size() == 0);
    cache.insert(1, "First inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 1);
    EXPECT_TRUE(cache.hasKey(1));
    cache.insert(2, "Second inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 2);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    cache.insert(3, "Third inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 3);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    cache.insert(4, "Fourth inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 4);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    cache.insert(5, "Fifth inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 5);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    cache.insert(6, "Sixt inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(cache.size() == 6);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    cache.insert(7, "Seventh inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_EQUAL(cache.size(), 7u);
    EXPECT_TRUE(cache.hasKey(1));
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    cache.insert(8, "Eighth inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_EQUAL(cache.size(), 7u);
    EXPECT_TRUE(cache.hasKey(2));
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    EXPECT_TRUE(cache.hasKey(8));
    cache.insert(15, "Eighth inserted string");
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_EQUAL(cache.size(), 7u);
    EXPECT_TRUE(cache.hasKey(3));
    EXPECT_TRUE(cache.hasKey(4));
    EXPECT_TRUE(cache.hasKey(5));
    EXPECT_TRUE(cache.hasKey(6));
    EXPECT_TRUE(cache.hasKey(7));
    EXPECT_TRUE(cache.hasKey(8));
    EXPECT_TRUE(cache.hasKey(15));
    // Test get and erase
    cache.get(3);
    EXPECT_TRUE(cache.verifyInternals());
    cache.erase(3);
    EXPECT_TRUE(cache.verifyInternals());
    EXPECT_TRUE(!cache.hasKey(3));
}

typedef std::shared_ptr<std::string> MyKey;
typedef std::shared_ptr<std::string> MyData;

struct SharedEqual {
    bool operator()(const MyKey & a, const MyKey & b) {
        return ((*a) == (*b));
    }
};

struct SharedHash {
    size_t operator() (const MyKey & arg) const { return arg->size(); }
};


TEST("testCacheInsertOverResize") {
    using LS = std::shared_ptr<std::string>;
    using Cache = lrucache_map< LruParam<int, LS> >;

    Cache cache(100);
    size_t sum(0);
    for (size_t i(0); i < cache.capacity()*10; i++) {
        LS s(new std::string("abc"));
        cache[random()] = s;
        sum += strlen(s->c_str());
        EXPECT_EQUAL(strlen(s->c_str()), s->size());
    }
    EXPECT_EQUAL(sum, cache.capacity()*10*3);
}

TEST("testCacheErase") {
    lrucache_map< LruParam<MyKey, MyData, SharedHash, SharedEqual> > cache(4);

    MyData d(new std::string("foo"));
    MyKey k(new std::string("barlol"));
    // Verfify start conditions.
    EXPECT_TRUE(cache.size() == 0);
    EXPECT_TRUE(d.use_count() == 1);
    EXPECT_TRUE(k.use_count() == 1);
    cache.insert(k, d);
    EXPECT_TRUE(d.use_count() == 2);
    EXPECT_TRUE(k.use_count() == 2);
    cache.erase(k);
    EXPECT_TRUE(d.use_count() == 1);
    EXPECT_TRUE(k.use_count() == 1);
}

TEST("testCacheIterator") {
    typedef lrucache_map< LruParam<int, string> > Cache;
    Cache cache(3);
    cache.insert(1, "first");
    cache.insert(2, "second");
    cache.insert(3, "third");
    Cache::iterator it(cache.begin());
    Cache::iterator mt(cache.end());
    ASSERT_TRUE(it != mt);
    ASSERT_EQUAL("third", *it);
    ASSERT_TRUE(it != mt);
    ASSERT_EQUAL("second", *(++it));
    ASSERT_TRUE(it != mt);
    ASSERT_EQUAL("second", *it++);
    ASSERT_TRUE(it != mt);
    ASSERT_EQUAL("first", *it);
    ASSERT_TRUE(it != mt);
    it++;
    ASSERT_TRUE(it == mt);
    cache.insert(4, "fourth");
    Cache::iterator it2(cache.begin());
    Cache::iterator it3(cache.begin());
    ASSERT_EQUAL("fourth", *it2);
    ASSERT_TRUE(it2 == it3);
    it2++;
    ASSERT_TRUE(it2 != it3);
    it2++;
    it2++;
    ASSERT_TRUE(it2 == mt);
    Cache::iterator it4 = cache.erase(it3);
    ASSERT_EQUAL("third", *it4);
    ASSERT_EQUAL("third", *cache.begin());
    Cache::iterator it5(cache.erase(cache.end()));
    ASSERT_TRUE(it5 == cache.end());
}

TEST("testCacheIteratorErase") {
    typedef lrucache_map< LruParam<int, string> > Cache;
    Cache cache(3);
    cache.insert(1, "first");
    cache.insert(8, "second");
    cache.insert(15, "third");
    cache.insert(15, "third");
    cache.insert(8, "second");
    cache.insert(1, "first");
    Cache::iterator it(cache.begin());
    ASSERT_EQUAL("first", *it);
    it++;
    ASSERT_EQUAL("second", *it);
    it = cache.erase(it);
    ASSERT_EQUAL("third", *it);
    cache.erase(it);
}

TEST_MAIN() { TEST_RUN_ALL(); }
