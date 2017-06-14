// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/storageframework/defaultimplementation/memory/simplememorylogic.h>
#include <tests/spi/memfiletestutils.h>


namespace storage {
namespace memfile {

class MemCacheTest : public SingleDiskMemFileTestUtils
{
    CPPUNIT_TEST_SUITE(MemCacheTest);
    CPPUNIT_TEST(testSimpleLRU);
    CPPUNIT_TEST(testCacheSize);
    CPPUNIT_TEST(testEvictBody);
    CPPUNIT_TEST(testEvictHeader);
    CPPUNIT_TEST(testKeepBodyWhenLessThanOneFourth);
    CPPUNIT_TEST(testComplexEviction);
    CPPUNIT_TEST(testEraseEmptyOnReturn);
    CPPUNIT_TEST(testDeleteDoesNotReAddMemoryUsage);
    CPPUNIT_TEST(testEraseDoesNotReAddMemoryUsage);
    CPPUNIT_TEST(testGetWithNoCreation);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimpleLRU();
    void testCacheSize();
    void testReduceCacheSizeCallback();
    void testReduceCacheSizeCallbackWhileActive();
    void testEvictBody();
    void testEvictHeader();
    void testKeepBodyWhenLessThanOneFourth();
    void testComplexEviction();
    void testEraseEmptyOnReturn();
    void testDeleteDoesNotReAddMemoryUsage();
    void testEraseDoesNotReAddMemoryUsage();
    void testGetWithNoCreation();

private:
    framework::defaultimplementation::ComponentRegisterImpl::UP _register;
    framework::Component::UP _component;
    FakeClock::UP _clock;
    framework::defaultimplementation::MemoryManager::UP _memoryManager;
    std::unique_ptr<MemFilePersistenceMetrics> _metrics;

    std::unique_ptr<MemFileCache> _cache;

    void setSize(const document::BucketId& id,
                 uint64_t metaSize,
                 uint64_t headerSz = 0,
                 uint64_t bodySz = 0,
                 bool createIfNotInCache = true)
    {
        MemFilePtr file(_cache->get(id, env(), env().getDirectory(),
                                    createIfNotInCache));
        CPPUNIT_ASSERT(file.get());

        file->_cacheSizeOverride.metaSize = metaSize;
        file->_cacheSizeOverride.headerSize = headerSz;
        file->_cacheSizeOverride.bodySize = bodySz;
    }

    std::string
    getBucketStatus(uint32_t buckets)
    {
        std::ostringstream ost;
        for (uint32_t i = 1; i < buckets + 1; i++) {
            document::BucketId id(16, i);
            ost << id << " ";
            if (!_cache->contains(id)) {
                ost << "<nil>\n";
            } else {
                MemFilePtr file(_cache->get(id, env(), env().getDirectory()));
                if (file->_cacheSizeOverride.bodySize > 0) {
                    ost << "body,";
                }
                if (file->_cacheSizeOverride.headerSize > 0) {
                    ost << "header\n";
                } else {
                    ost << "meta only\n";
                }
            }
        }

        return ost.str();
    }

    uint64_t cacheSize() {
        return _cache->size();
    }

    document::BucketId getLRU() {
        return _cache->getLeastRecentlyUsedBucket()->_bid;
    }

    void setCacheSize(uint64_t sz) {
        MemFileCache::MemoryUsage usage;
        usage.metaSize = sz / 3;
        usage.headerSize = sz / 3;
        usage.bodySize = sz - usage.metaSize - usage.headerSize;

        _cache->setCacheSize(usage);
    }

    void stealMemory(uint64_t memToSteal) {
        setCacheSize(_cache->getCacheSize() - memToSteal);
    }

    void setup(uint64_t maxMemory) {
        tearDown();
        _register.reset(
                new framework::defaultimplementation::ComponentRegisterImpl);
        _clock.reset(new FakeClock);
        _register->setClock(*_clock);
        _memoryManager.reset(
                new framework::defaultimplementation::MemoryManager(
                    framework::defaultimplementation::AllocationLogic::UP(
                        new framework::defaultimplementation::SimpleMemoryLogic(
                            *_clock, maxMemory * 2))));
        _register->setMemoryManager(*_memoryManager);
        _component.reset(new framework::Component(*_register, "testcomponent"));
        _metrics.reset(new MemFilePersistenceMetrics(*_component));
        _cache.reset(new MemFileCache(*_register, _metrics->_cache));
        setCacheSize(maxMemory);
        _memoryManager->registerAllocationType(framework::MemoryAllocationType(
                "steal", framework::MemoryAllocationType::FORCE_ALLOCATE));
    }

public:
    void tearDown() override {
        _cache.reset(0);
        _metrics.reset(0);
        _component.reset(0);
        _register.reset(0);
        _memoryManager.reset(0);
        _clock.reset(0);
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemCacheTest);

namespace {
    FakeClock clock;
}

void
MemCacheTest::testSimpleLRU()
{
    setup(2000);

    for (uint32_t i = 1; i < 4; i++) {
        setSize(document::BucketId(16, i), 100);
    }

    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1), getLRU());

    setSize(document::BucketId(16, 1), 100);

    CPPUNIT_ASSERT_EQUAL(1UL, _cache->getMetrics().hits.getValue());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 2), getLRU());
}

void
MemCacheTest::testCacheSize()
{
    setup(400);

    setSize(document::BucketId(16, 2), 100);
    setSize(document::BucketId(16, 1), 150);

    CPPUNIT_ASSERT_EQUAL(0UL, _cache->getMetrics().hits.getValue());
    CPPUNIT_ASSERT_EQUAL(2UL, _cache->getMetrics().misses.getValue());

    CPPUNIT_ASSERT_EQUAL(250ul, cacheSize());

    setSize(document::BucketId(16, 1), 200);

    CPPUNIT_ASSERT_EQUAL(1UL, _cache->getMetrics().hits.getValue());
    CPPUNIT_ASSERT_EQUAL(2UL, _cache->getMetrics().misses.getValue());

    CPPUNIT_ASSERT_EQUAL(300ul, cacheSize());

    CPPUNIT_ASSERT(_cache->contains(document::BucketId(16, 2)));
    CPPUNIT_ASSERT(_cache->contains(document::BucketId(16, 1)));

    setSize(document::BucketId(16, 1), 301);

    CPPUNIT_ASSERT_EQUAL(2UL, _cache->getMetrics().hits.getValue());
    CPPUNIT_ASSERT_EQUAL(2UL, _cache->getMetrics().misses.getValue());

    CPPUNIT_ASSERT(!_cache->contains(document::BucketId(16, 2)));
    CPPUNIT_ASSERT(_cache->contains(document::BucketId(16, 1)));

    _cache->clear();
    CPPUNIT_ASSERT_EQUAL(0ul, cacheSize());
}

void
MemCacheTest::testEvictBody()
{
    setup(1400);

    CPPUNIT_ASSERT_EQUAL(0UL, _cache->getMetrics().body_evictions.getValue());

    setSize(BucketId(16, 1), 150, 100, 0);
    setSize(BucketId(16, 2), 100, 100, 900);

    CPPUNIT_ASSERT_EQUAL(1350ul, cacheSize());

    stealMemory(150);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) header\n"
                    "BucketId(0x4000000000000002) header\n"),
            getBucketStatus(2));
    CPPUNIT_ASSERT_EQUAL(1UL, _cache->getMetrics().body_evictions.getValue());
}

void
MemCacheTest::testKeepBodyWhenLessThanOneFourth()
{
    setup(450);

    setSize(BucketId(16, 1), 150, 0, 0);
    setSize(BucketId(16, 2), 100, 50, 50);

    stealMemory(150);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) <nil>\n"
                    "BucketId(0x4000000000000002) body,header\n"),
            getBucketStatus(2));
}

void
MemCacheTest::testEvictHeader()
{
    setup(550);

    CPPUNIT_ASSERT_EQUAL(0UL, _cache->getMetrics().header_evictions.getValue());

    setSize(BucketId(16, 1), 150, 0, 0);
    setSize(BucketId(16, 2), 100, 200, 100);

    stealMemory(150);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) meta only\n"
                    "BucketId(0x4000000000000002) meta only\n"),
            getBucketStatus(2));
    CPPUNIT_ASSERT_EQUAL(1UL, _cache->getMetrics().header_evictions.getValue());
}

#define ASSERT_CACHE_EVICTIONS(meta, header, body) \
    CPPUNIT_ASSERT_EQUAL(size_t(meta), _cache->getMetrics().body_evictions.getValue()); \
    CPPUNIT_ASSERT_EQUAL(size_t(header), _cache->getMetrics().header_evictions.getValue()); \
    CPPUNIT_ASSERT_EQUAL(size_t(body), _cache->getMetrics().meta_evictions.getValue());

void
MemCacheTest::testComplexEviction()
{
    setup(4200);

    setSize(BucketId(16, 1), 150, 0, 0);
    setSize(BucketId(16, 2), 100, 200, 200);
    setSize(BucketId(16, 3), 100, 200, 0);
    setSize(BucketId(16, 4), 100, 400, 0);
    setSize(BucketId(16, 5), 100, 200, 400);
    setSize(BucketId(16, 6), 100, 200, 300);
    setSize(BucketId(16, 7), 100, 0, 0);
    setSize(BucketId(16, 8), 100, 200, 400);
    setSize(BucketId(16, 9), 100, 200, 250);

    CPPUNIT_ASSERT_EQUAL(4100ul, cacheSize());

    ASSERT_CACHE_EVICTIONS(0, 0, 0);

    stealMemory(600);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) meta only\n"
                    "BucketId(0x4000000000000002) header\n"
                    "BucketId(0x4000000000000003) header\n"
                    "BucketId(0x4000000000000004) header\n"
                    "BucketId(0x4000000000000005) header\n"
                    "BucketId(0x4000000000000006) body,header\n"
                    "BucketId(0x4000000000000007) meta only\n"
                    "BucketId(0x4000000000000008) body,header\n"
                    "BucketId(0x4000000000000009) body,header\n"),
            getBucketStatus(9));

    CPPUNIT_ASSERT_EQUAL(3500ul, cacheSize());

    ASSERT_CACHE_EVICTIONS(2, 0, 0);

    stealMemory(500);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) meta only\n"
                    "BucketId(0x4000000000000002) meta only\n"
                    "BucketId(0x4000000000000003) meta only\n"
                    "BucketId(0x4000000000000004) header\n"
                    "BucketId(0x4000000000000005) header\n"
                    "BucketId(0x4000000000000006) body,header\n"
                    "BucketId(0x4000000000000007) meta only\n"
                    "BucketId(0x4000000000000008) body,header\n"
                    "BucketId(0x4000000000000009) body,header\n"),
            getBucketStatus(9));

    CPPUNIT_ASSERT_EQUAL(3100ul, cacheSize());

    ASSERT_CACHE_EVICTIONS(2, 2, 0);

    stealMemory(1000);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) <nil>\n"
                    "BucketId(0x4000000000000002) meta only\n"
                    "BucketId(0x4000000000000003) meta only\n"
                    "BucketId(0x4000000000000004) meta only\n"
                    "BucketId(0x4000000000000005) meta only\n"
                    "BucketId(0x4000000000000006) header\n"
                    "BucketId(0x4000000000000007) meta only\n"
                    "BucketId(0x4000000000000008) body,header\n"
                    "BucketId(0x4000000000000009) body,header\n"),
            getBucketStatus(9));

    CPPUNIT_ASSERT_EQUAL(2050ul, cacheSize());

    ASSERT_CACHE_EVICTIONS(3, 4, 1);

    stealMemory(1100);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "BucketId(0x4000000000000001) <nil>\n"
                    "BucketId(0x4000000000000002) <nil>\n"
                    "BucketId(0x4000000000000003) <nil>\n"
                    "BucketId(0x4000000000000004) <nil>\n"
                    "BucketId(0x4000000000000005) <nil>\n"
                    "BucketId(0x4000000000000006) <nil>\n"
                    "BucketId(0x4000000000000007) meta only\n"
                    "BucketId(0x4000000000000008) header\n"
                    "BucketId(0x4000000000000009) body,header\n"),
            getBucketStatus(9));

    CPPUNIT_ASSERT_EQUAL(950ul, cacheSize());
}

#undef ASSERT_CACHE_EVICTIONS

void
MemCacheTest::testEraseEmptyOnReturn()
{
    setup(4200);
    setSize(BucketId(16, 1), 0, 0, 0);
    CPPUNIT_ASSERT(!_cache->contains(document::BucketId(16, 1)));
}

void
MemCacheTest::testDeleteDoesNotReAddMemoryUsage()
{
    BucketId id(16, 1);
    setup(1000);
    setSize(id, 100, 200, 300);
    CPPUNIT_ASSERT_EQUAL(600ul, cacheSize());
    {
        MemFilePtr file(_cache->get(id, env(), env().getDirectory()));
        file.deleteFile();
    }
    CPPUNIT_ASSERT_EQUAL(0ul, cacheSize());

}

void
MemCacheTest::testGetWithNoCreation()
{
    BucketId id(16, 1);
    setup(1000);
    setSize(id, 100, 200, 300, false);
    CPPUNIT_ASSERT_EQUAL(0ul, cacheSize());
}


void
MemCacheTest::testEraseDoesNotReAddMemoryUsage()
{
    BucketId id(16, 1);
    setup(1000);
    setSize(id, 100, 200, 300);
    CPPUNIT_ASSERT_EQUAL(600ul, cacheSize());
    {
        MemFilePtr file(_cache->get(id, env(), env().getDirectory()));
        file.eraseFromCache();
    }
    CPPUNIT_ASSERT_EQUAL(0ul, cacheSize());

}

} // memfile
} // storage
