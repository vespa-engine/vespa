// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/cache.hpp>
#include <map>

using namespace vespalib;

template<typename K, typename V>
class Map : public std::map<K, V> {
    typedef typename std::map<K, V>::const_iterator const_iterator;
    typedef std::map<K, V> M;
public:
    bool read(const K & k, V & v) const {
        const_iterator found = M::find(k);
        bool ok(found != this->end());
        if (ok) {
            v = found->second;
        }
        return ok;
    }
    void write(const K & k, const V & v) {
        (*this)[k] = v;
    }
    void erase(const K & k) {
        M::erase(k);
    }
};

using P = LruParam<uint32_t, string>;
using B = Map<uint32_t, string>;

TEST("testCache") {
    B m;
    cache< CacheParam<P, B> > cache(m, -1);
    // Verfify start conditions.
    EXPECT_TRUE(cache.size() == 0);
    EXPECT_TRUE( ! cache.hasKey(1) );
    cache.write(1, "First inserted string");
    EXPECT_TRUE( cache.hasKey(1) );
    m[2] = "String inserted beneath";
    EXPECT_TRUE( ! cache.hasKey(2) );
    EXPECT_EQUAL( cache.read(2), "String inserted beneath");
    EXPECT_TRUE( cache.hasKey(2) );
    cache.erase(1);
    EXPECT_TRUE( ! cache.hasKey(1) );
    EXPECT_TRUE(cache.size() == 1);
}

TEST("testCacheSize")
{
    B m;
    cache< CacheParam<P, B> > cache(m, -1);
    cache.write(1, "10 bytes string");
    EXPECT_EQUAL(80u, cache.sizeBytes());
    cache.write(1, "10 bytes string"); // Still the same size
    EXPECT_EQUAL(80u, cache.sizeBytes());
}

TEST("testCacheSizeDeep")
{
    B m;
    cache< CacheParam<P, B, zero<uint32_t>, size<string> > > cache(m, -1);
    cache.write(1, "15 bytes string");
    EXPECT_EQUAL(95u, cache.sizeBytes());
    cache.write(1, "10 bytes s");
    EXPECT_EQUAL(90u, cache.sizeBytes());
    cache.write(1, "20 bytes string ssss");
    EXPECT_EQUAL(100u, cache.sizeBytes());
}

TEST("testCacheEntriesHonoured") {
    B m;
    cache< CacheParam<P, B, zero<uint32_t>, size<string> > > cache(m, -1);
    cache.maxElements(1);
    cache.write(1, "15 bytes string");
    EXPECT_EQUAL(1u, cache.size());
    EXPECT_EQUAL(95u, cache.sizeBytes());
    cache.write(2, "16 bytes stringg");
    EXPECT_EQUAL(1u, cache.size());
    EXPECT_TRUE( cache.hasKey(2) );
    EXPECT_FALSE( cache.hasKey(1) );
    EXPECT_EQUAL(96u, cache.sizeBytes());
}

TEST("testCacheMaxSizeHonoured") {
    B m;
    cache< CacheParam<P, B, zero<uint32_t>, size<string> > > cache(m, 200);
    cache.write(1, "15 bytes string");
    EXPECT_EQUAL(1u, cache.size());
    EXPECT_EQUAL(95u, cache.sizeBytes());
    cache.write(2, "16 bytes stringg");
    EXPECT_EQUAL(2u, cache.size());
    EXPECT_EQUAL(191u, cache.sizeBytes());
    cache.write(3, "17 bytes stringgg");
    EXPECT_EQUAL(3u, cache.size());
    EXPECT_EQUAL(288u, cache.sizeBytes());
    cache.write(4, "18 bytes stringggg");
    EXPECT_EQUAL(3u, cache.size());
    EXPECT_EQUAL(291u, cache.sizeBytes());
}

TEST("testThatMultipleRemoveOnOverflowIsFine") {
    B m;
    cache< CacheParam<P, B, zero<uint32_t>, size<string> > > cache(m, 2000);
    
    for (size_t j(0); j < 5; j++) {
        for (size_t i(0); cache.size() == i; i++) {
            cache.write(j*53+i, "a");
        }
    }
    EXPECT_EQUAL(25u, cache.size());
    EXPECT_EQUAL(2025u, cache.sizeBytes());
    EXPECT_FALSE( cache.hasKey(0) );
    string ls("long string aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    string vls=ls+ls+ls+ls+ls+ls;
    cache.write(53+5, ls);
    EXPECT_EQUAL(25u, cache.size());
    EXPECT_EQUAL(2498u, cache.sizeBytes());
    EXPECT_FALSE( cache.hasKey(1) );
    cache.write(53*7+5, ls);
    EXPECT_EQUAL(19u, cache.size());
    EXPECT_EQUAL(2485u, cache.sizeBytes());
    EXPECT_FALSE( cache.hasKey(2) );
    cache.write(53*8+5, vls);
    EXPECT_EQUAL(14u, cache.size());
    EXPECT_EQUAL(4923u, cache.sizeBytes());
    cache.write(53*9+6, vls);
    EXPECT_EQUAL(1u, cache.size());
    EXPECT_EQUAL(2924u, cache.sizeBytes());
}

TEST_MAIN() { TEST_RUN_ALL(); }
