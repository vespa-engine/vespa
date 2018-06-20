// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/storage/bucketdb/judymultimap.h>
#include <vespa/storage/bucketdb/judymultimap.hpp>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <cppunit/extensions/HelperMacros.h>
#include <boost/operators.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".lockable_map_test");

namespace storage {

struct LockableMapTest : public CppUnit::TestFixture {
    void testSimpleUsage();
    void testComparison();
    void testIterating();
    void testChunkedIterationIsTransparentAcrossChunkSizes();
    void testCanAbortDuringChunkedIteration();
    void testThreadSafetyStress();
    void testFindBuckets();
    void testFindBuckets2();
    void testFindBuckets3();
    void testFindBuckets4();
    void testFindBuckets5();
    void testFindBucketsSimple();
    void testFindNoBuckets();
    void testFindAll();
    void testFindAll2();
    void testFindAllUnusedBitIsSet();
    void testFindAllInconsistentlySplit();
    void testFindAllInconsistentlySplit2();
    void testFindAllInconsistentlySplit3();
    void testFindAllInconsistentlySplit4();
    void testFindAllInconsistentlySplit5();
    void testFindAllInconsistentlySplit6();
    void testFindAllInconsistentBelow16Bits();
    void testCreate();
    void testCreate2();
    void testCreate3();
    void testCreate4();
    void testCreate5();
    void testCreate6();
    void testCreateEmpty();
    void testIsConsistent();

    CPPUNIT_TEST_SUITE(LockableMapTest);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST(testComparison);
    CPPUNIT_TEST(testIterating);
    CPPUNIT_TEST(testChunkedIterationIsTransparentAcrossChunkSizes);
    CPPUNIT_TEST(testCanAbortDuringChunkedIteration);
    CPPUNIT_TEST(testThreadSafetyStress);
    CPPUNIT_TEST(testFindBuckets);
    CPPUNIT_TEST(testFindBuckets2);
    CPPUNIT_TEST(testFindBuckets3);
    CPPUNIT_TEST(testFindBuckets4);
    CPPUNIT_TEST(testFindBuckets5);
    CPPUNIT_TEST(testFindBucketsSimple);
    CPPUNIT_TEST(testFindNoBuckets);
    CPPUNIT_TEST(testFindAll);
    CPPUNIT_TEST(testFindAll2);
    CPPUNIT_TEST(testFindAllUnusedBitIsSet);
    CPPUNIT_TEST(testFindAllInconsistentlySplit);
    CPPUNIT_TEST(testFindAllInconsistentlySplit2);
    CPPUNIT_TEST(testFindAllInconsistentlySplit3);
    CPPUNIT_TEST(testFindAllInconsistentlySplit4);
    CPPUNIT_TEST(testFindAllInconsistentlySplit5);
    CPPUNIT_TEST(testFindAllInconsistentlySplit6);
    CPPUNIT_TEST(testFindAllInconsistentBelow16Bits);
    CPPUNIT_TEST(testCreate);
    CPPUNIT_TEST(testCreate2);
    CPPUNIT_TEST(testCreate3);
    CPPUNIT_TEST(testCreate4);
    CPPUNIT_TEST(testCreate5);
    CPPUNIT_TEST(testCreate6);
    CPPUNIT_TEST(testCreateEmpty);
    CPPUNIT_TEST(testIsConsistent);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(LockableMapTest);

namespace {
    struct A : public boost::operators<A> {
        int _val1;
        int _val2;
        int _val3;

        A() : _val1(0), _val2(0), _val3(0) {}
        A(int val1, int val2, int val3)
            : _val1(val1), _val2(val2), _val3(val3) {}

        static bool mayContain(const A&) { return true; }

        bool operator==(const A& a) const {
            return (_val1 == a._val1 && _val2 == a._val2 && _val3 == a._val3);
        }
        bool operator<(const A& a) const {
            if (_val1 != a._val1) return (_val1 < a._val1);
            if (_val2 != a._val2) return (_val2 < a._val2);
            return (_val3 < a._val3);
        }
    };

    std::ostream& operator<<(std::ostream& out, const A& a) {
        return out << "A(" << a._val1 << ", " << a._val2 << ", " << a._val3 << ")";
    }

    typedef LockableMap<JudyMultiMap<A> > Map;
}

void
LockableMapTest::testSimpleUsage() {
        // Tests insert, erase, size, empty, operator[]
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
        // Do some insertions
    CPPUNIT_ASSERT(map.empty());
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    CPPUNIT_ASSERT_EQUAL_MSG(map.toString(),
                             (Map::size_type) 3, map.size());

    map.insert(11, A(4, 7, 0), "foo", preExisted);
    CPPUNIT_ASSERT_EQUAL(true, preExisted);
    CPPUNIT_ASSERT_EQUAL((Map::size_type) 3, map.size());
    CPPUNIT_ASSERT(!map.empty());

        // Access some elements
    CPPUNIT_ASSERT_EQUAL(A(4, 7, 0), *map.get(11, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(1, 2, 3), *map.get(16, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(42,0, 0), *map.get(14, "foo"));

        // Do removes
    CPPUNIT_ASSERT(map.erase(12, "foo") == 0);
    CPPUNIT_ASSERT_EQUAL((Map::size_type) 3, map.size());

    CPPUNIT_ASSERT(map.erase(14, "foo") == 1);
    CPPUNIT_ASSERT_EQUAL((Map::size_type) 2, map.size());

    CPPUNIT_ASSERT(map.erase(11, "foo") == 1);
    CPPUNIT_ASSERT(map.erase(16, "foo") == 1);
    CPPUNIT_ASSERT_EQUAL((Map::size_type) 0, map.size());
    CPPUNIT_ASSERT(map.empty());
}

void
LockableMapTest::testComparison() {
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map1;
    Map map2;
    bool preExisted;

        // Check empty state is correct
    CPPUNIT_ASSERT_EQUAL(map1, map2);
    CPPUNIT_ASSERT(!(map1 < map2));
    CPPUNIT_ASSERT(!(map1 != map2));

        // Check that different lengths are oki
    map1.insert(4, A(1, 2, 3), "foo", preExisted);
    CPPUNIT_ASSERT(!(map1 == map2));
    CPPUNIT_ASSERT(!(map1 < map2));
    CPPUNIT_ASSERT(map2 < map1);
    CPPUNIT_ASSERT(map1 != map2);

        // Check that equal elements are oki
    map2.insert(4, A(1, 2, 3), "foo", preExisted);
    CPPUNIT_ASSERT_EQUAL(map1, map2);
    CPPUNIT_ASSERT(!(map1 < map2));
    CPPUNIT_ASSERT(!(map1 != map2));

        // Check that non-equal values are oki
    map1.insert(6, A(1, 2, 6), "foo", preExisted);
    map2.insert(6, A(1, 2, 3), "foo", preExisted);
    CPPUNIT_ASSERT(!(map1 == map2));
    CPPUNIT_ASSERT(!(map1 < map2));
    CPPUNIT_ASSERT(map2 < map1);
    CPPUNIT_ASSERT(map1 != map2);

        // Check that non-equal keys are oki
    map1.erase(6, "foo");
    map1.insert(7, A(1, 2, 3), "foo", preExisted);
    CPPUNIT_ASSERT(!(map1 == map2));
    CPPUNIT_ASSERT(!(map1 < map2));
    CPPUNIT_ASSERT(map2 < map1);
    CPPUNIT_ASSERT(map1 != map2);
}

namespace {
    struct NonConstProcessor {
        Map::Decision operator()(int key, A& a) {
            (void) key;
            ++a._val2;
            return Map::UPDATE;
        }
    };
    struct EntryProcessor {
        mutable uint32_t count;
        mutable std::vector<std::string> log;
        mutable std::vector<Map::Decision> behaviour;

        EntryProcessor();
        EntryProcessor(const std::vector<Map::Decision>& decisions);
        ~EntryProcessor();

        Map::Decision operator()(uint64_t key, A& a) const {
            std::ostringstream ost;
            ost << key << " - " << a;
            log.push_back(ost.str());
            Map::Decision d = Map::CONTINUE;
            if (behaviour.size() > count) {
                d = behaviour[count++];
            }
            if (d == Map::UPDATE) {
                ++a._val3;
            }
            return d;
        }

        std::string toString() {
            std::ostringstream ost;
            for (uint32_t i=0; i<log.size(); ++i) ost << log[i] << "\n";
            return ost.str();
        }
    };
}

EntryProcessor::EntryProcessor() : count(0), log(), behaviour() {}
EntryProcessor::EntryProcessor(const std::vector<Map::Decision>& decisions)
        : count(0), log(), behaviour(decisions) {}
EntryProcessor::~EntryProcessor() {}

void
LockableMapTest::testIterating() {
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
        // Test that we can use functor with non-const function
    {
        NonConstProcessor ncproc;
        map.each(ncproc, "foo"); // Locking both for each element
        CPPUNIT_ASSERT_EQUAL(A(4, 7, 0), *map.get(11, "foo"));
        CPPUNIT_ASSERT_EQUAL(A(42,1, 0), *map.get(14, "foo"));
        CPPUNIT_ASSERT_EQUAL(A(1, 3, 3), *map.get(16, "foo"));
        map.all(ncproc, "foo"); // And for all
        CPPUNIT_ASSERT_EQUAL(A(4, 8, 0), *map.get(11, "foo"));
        CPPUNIT_ASSERT_EQUAL(A(42,2, 0), *map.get(14, "foo"));
        CPPUNIT_ASSERT_EQUAL(A(1, 4, 3), *map.get(16, "foo"));
    }
        // Test that we can use const functors directly..
    map.each(EntryProcessor(), "foo");

        // Test iterator bounds
    {
        EntryProcessor proc;
        map.each(proc, "foo", 11, 16);
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        CPPUNIT_ASSERT_EQUAL(expected, proc.toString());

        EntryProcessor proc2;
        map.each(proc2, "foo", 12, 15);
        expected = "14 - A(42, 2, 0)\n";
        CPPUNIT_ASSERT_EQUAL(expected, proc2.toString());
    }
        // Test that we can abort iterating
    {
        std::vector<Map::Decision> decisions;
        decisions.push_back(Map::CONTINUE);
        decisions.push_back(Map::ABORT);
        EntryProcessor proc(decisions);
        map.each(proc, "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n");
        CPPUNIT_ASSERT_EQUAL(expected, proc.toString());
    }
        // Test that we can remove during iteration
    {
        std::vector<Map::Decision> decisions;
        decisions.push_back(Map::CONTINUE);
        decisions.push_back(Map::REMOVE);
        EntryProcessor proc(decisions);
        map.each(proc, "foo");
        std::string expected("11 - A(4, 8, 0)\n"
                             "14 - A(42, 2, 0)\n"
                             "16 - A(1, 4, 3)\n");
        CPPUNIT_ASSERT_EQUAL(expected, proc.toString());
        CPPUNIT_ASSERT_EQUAL_MSG(map.toString(),
                                 (Map::size_type) 2, map.size());
        CPPUNIT_ASSERT_EQUAL(A(4, 8, 0), *map.get(11, "foo"));
        CPPUNIT_ASSERT_EQUAL(A(1, 4, 3), *map.get(16, "foo"));
        Map::WrappedEntry entry = map.get(14, "foo");
        CPPUNIT_ASSERT(!entry.exist());
    }
}

void
LockableMapTest::testChunkedIterationIsTransparentAcrossChunkSizes()
{
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);
    NonConstProcessor ncproc; // Increments 2nd value in all entries.
    // chunkedAll with chunk size of 1
    map.chunkedAll(ncproc, "foo", 1);
    CPPUNIT_ASSERT_EQUAL(A(4, 7, 0), *map.get(11, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(42, 1, 0), *map.get(14, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(1, 3, 3), *map.get(16, "foo"));
    // chunkedAll with chunk size larger than db size
    map.chunkedAll(ncproc, "foo", 100);
    CPPUNIT_ASSERT_EQUAL(A(4, 8, 0), *map.get(11, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(42, 2, 0), *map.get(14, "foo"));
    CPPUNIT_ASSERT_EQUAL(A(1, 4, 3), *map.get(16, "foo"));
}

void
LockableMapTest::testCanAbortDuringChunkedIteration()
{
    Map map;
    bool preExisted;
    map.insert(16, A(1, 2, 3), "foo", preExisted);
    map.insert(11, A(4, 6, 0), "foo", preExisted);
    map.insert(14, A(42, 0, 0), "foo", preExisted);

    std::vector<Map::Decision> decisions;
    decisions.push_back(Map::CONTINUE);
    decisions.push_back(Map::ABORT);
    EntryProcessor proc(decisions);
    map.chunkedAll(proc, "foo", 100);
    std::string expected("11 - A(4, 6, 0)\n"
            "14 - A(42, 0, 0)\n");
    CPPUNIT_ASSERT_EQUAL(expected, proc.toString());
}

namespace {
    struct LoadGiver : public document::Runnable {
        typedef std::shared_ptr<LoadGiver> SP;
        Map& _map;
        uint32_t _counter;

        LoadGiver(Map& map) : _map(map), _counter(0) {}
        ~LoadGiver() __attribute__((noinline));
    };

    LoadGiver::~LoadGiver() { }

    struct InsertEraseLoadGiver : public LoadGiver {
        InsertEraseLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
                // Screws up order of buckets by xor'ing with 12345.
                // Only operate on last 32k super buckets.
            while (running()) {
                uint32_t bucket = ((_counter ^ 12345) % 0x8000) + 0x8000;
                if (bucket % 7 < 3) {
                    bool preExisted;
                    _map.insert(bucket, A(bucket, 0, _counter), "foo",
                                preExisted);
                }
                if (bucket % 5 < 2) {
                    _map.erase(bucket, "foo");
                }
                ++_counter;
            }
        }
    };

    struct GetLoadGiver : public LoadGiver {
        GetLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
                // It's legal to keep entries as long as you only request higher
                // buckets. So, to test this, keep entries until you request one
                // that is smaller than those stored.
            std::vector<std::pair<uint32_t, Map::WrappedEntry> > stored;
            while (running()) {
                uint32_t bucket = (_counter ^ 52721) % 0x10000;
                if (!stored.empty() && stored.back().first > bucket) {
                    stored.clear();
                }
                stored.push_back(std::pair<uint32_t, Map::WrappedEntry>(
                            bucket, _map.get(bucket, "foo", _counter % 3 == 0)));
                ++_counter;
            }
        }
    };

    struct AllLoadGiver : public LoadGiver {
        AllLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
            while (running()) {
                _map.all(*this, "foo");
                ++_counter;
            }
        }

        Map::Decision operator()(int key, A& a) {
            //std::cerr << (void*) this << " - " << key << "\n";
            (void) key;
            ++a._val2;
            return Map::CONTINUE;
        }
    };

    struct EachLoadGiver : public LoadGiver {
        EachLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
            while (running()) {
                _map.each(*this, "foo");
                ++_counter;
            }
        }

        Map::Decision operator()(int key, A& a) {
            //std::cerr << (void*) this << " - " << key << "\n";
            (void) key;
            ++a._val2;
            return Map::CONTINUE;
        }
    };

    struct RandomRangeLoadGiver : public LoadGiver {
        RandomRangeLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
            while (running()) {
                uint32_t min = (_counter ^ 23426) % 0x10000;
                uint32_t max = (_counter ^ 40612) % 0x10000;
                if (min > max) {
                    uint32_t tmp = min;
                    min = max;
                    max = tmp;
                }
                if (_counter % 7 < 5) {
                    _map.each(*this, "foo", min, max);
                } else {
                    _map.all(*this, "foo", min, max);
                }
                ++_counter;
            }
        }

        Map::Decision operator()(int key, A& a) {
            //std::cerr << ".";
            (void) key;
            ++a._val2;
            return Map::CONTINUE;
        }
    };

    struct GetNextLoadGiver : public LoadGiver {
        GetNextLoadGiver(Map& map) : LoadGiver(map) {}

        void run() override {
            while (running()) {
                uint32_t bucket = (_counter ^ 60417) % 0xffff;
                if (_counter % 7 < 5) {
                    _map.each(*this, "foo", bucket + 1, 0xffff);
                } else {
                    _map.all(*this, "foo", bucket + 1, 0xffff);
                }
                ++_counter;
            }
        }

        Map::Decision operator()(int key, A& a) {
            //std::cerr << ".";
            (void) key;
            ++a._val2;
            return Map::ABORT;
        }
    };
}

void
LockableMapTest::testThreadSafetyStress() {
    uint32_t duration = 2 * 1000;
    std::cerr << "\nRunning LockableMap threadsafety test for "
              << (duration / 1000) << " seconds.\n";
    // Set up multiple threads going through the bucket database at the same
    // time. Ensuring all works and there are no deadlocks.

    // Initial database of 32k elements which should always be present.
    // Next 32k elements may exist (loadgivers may erase and create them, "foo")
    Map map;
    for (uint32_t i=0; i<65536; ++i) {
        bool preExisted;
        map.insert(i, A(i, 0, i ^ 12345), "foo", preExisted);
    }
    std::vector<LoadGiver::SP> loadgivers;
    for (uint32_t i=0; i<8; ++i) {
        loadgivers.push_back(LoadGiver::SP(new InsertEraseLoadGiver(map)));
    }
    for (uint32_t i=0; i<2; ++i) {
        loadgivers.push_back(LoadGiver::SP(new GetLoadGiver(map)));
    }
    for (uint32_t i=0; i<2; ++i) {
        loadgivers.push_back(LoadGiver::SP(new AllLoadGiver(map)));
    }
    for (uint32_t i=0; i<2; ++i) {
        loadgivers.push_back(LoadGiver::SP(new EachLoadGiver(map)));
    }
    for (uint32_t i=0; i<2; ++i) {
        loadgivers.push_back(LoadGiver::SP(new RandomRangeLoadGiver(map)));
    }
    for (uint32_t i=0; i<2; ++i) {
        loadgivers.push_back(LoadGiver::SP(new GetNextLoadGiver(map)));
    }

    FastOS_ThreadPool pool(128 * 1024);
    for (uint32_t i=0; i<loadgivers.size(); ++i) {
        CPPUNIT_ASSERT(loadgivers[i]->start(pool));
    }
    FastOS_Thread::Sleep(duration);
    std::cerr << "Closing down test\n";
    for (uint32_t i=0; i<loadgivers.size(); ++i) {
        CPPUNIT_ASSERT(loadgivers[i]->stop());
    }
//    FastOS_Thread::Sleep(duration);
//    std::cerr << "Didn't manage to shut down\n";
//    map._lockedKeys.print(std::cerr, true, "");

    for (uint32_t i=0; i<loadgivers.size(); ++i) {
        CPPUNIT_ASSERT(loadgivers[i]->join());
    }
    std::cerr << "Loadgiver counts:";
    for (uint32_t i=0; i<loadgivers.size(); ++i) {
        std::cerr << " " << loadgivers[i]->_counter;
    }
    std::cerr << "\nTest completed\n";
}

#if 0
namespace {
struct Hex {
    document::BucketId::Type val;

    Hex(document::BucketId::Type v) : val(v) {}
    bool operator==(const Hex& h) const { return val == h.val; }
};

std::ostream& operator<<(std::ostream& out, const Hex& h) {
    out << std::hex << h.val << std::dec;
    return out;
}

void
printBucket(const std::string s, const document::BucketId& b) {
    std::cerr << s << "bucket=" << b << ", reversed=" << b.stripUnused().toKey() << ", hex=" << Hex(b.stripUnused().toKey()) << "\n";
}

void
printBuckets(const std::map<document::BucketId, Map::WrappedEntry>& results) {
    for (std::map<document::BucketId, Map::WrappedEntry>::const_iterator iter = results.begin();
         iter != results.end();
         iter++) {
        printBucket("Returned ", iter->first);
    }
}

}
#endif

void
LockableMapTest::testFindBucketsSimple() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(17, 0x0ffff);
    id1 = id1.stripUnused();

    document::BucketId id2(18, 0x1ffff);
    id2 = id2.stripUnused();

    document::BucketId id3(18, 0x3ffff);
    id3 = id3.stripUnused();

    bool preExisted;
    map.insert(id1.toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(22, 0xfffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3]);
#endif
}

void
LockableMapTest::testFindBuckets() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(17, 0x1ffff);
    document::BucketId id4(19, 0xfffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(22, 0xfffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)3, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]);
    CPPUNIT_ASSERT_EQUAL(A(4,5,6), *results[id4.stripUnused()]);
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]);
#endif
}

void
LockableMapTest::testFindBuckets2() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(17, 0x1ffff);
    document::BucketId id4(18, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)3, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]);
    CPPUNIT_ASSERT_EQUAL(A(4,5,6), *results[id4.stripUnused()]);
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]);
#endif
}

void
LockableMapTest::testFindBuckets3() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]);
#endif
}

void
LockableMapTest::testFindBuckets4() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]);
#endif
}

void
LockableMapTest::testFindBuckets5() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff);
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x5ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getContained(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]);
#endif
}

void
LockableMapTest::testFindNoBuckets() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id(16, 0x0ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)0, results.size());
#endif
}

void
LockableMapTest::testFindAll() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0aaaa); // contains id2-id7
    document::BucketId id2(17, 0x0aaaa); // contains id3-id4
    document::BucketId id3(20, 0xcaaaa);
    document::BucketId id4(20, 0xeaaaa);
    document::BucketId id5(17, 0x1aaaa); // contains id6-id7
    document::BucketId id6(20, 0xdaaaa);
    document::BucketId id7(20, 0xfaaaa);
    document::BucketId id8(20, 0xceaaa);
    document::BucketId id9(17, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);
    map.insert(id5.stripUnused().toKey(), A(5,6,7), "foo", preExisted);
    map.insert(id6.stripUnused().toKey(), A(6,7,8), "foo", preExisted);
    map.insert(id7.stripUnused().toKey(), A(7,8,9), "foo", preExisted);
    map.insert(id8.stripUnused().toKey(), A(8,9,10), "foo", preExisted);
    map.insert(id9.stripUnused().toKey(), A(9,10,11), "foo", preExisted);
    //printBucket("Inserted ", id1);
    //printBucket("Inserted ", id2);
    //printBucket("Inserted ", id3);
    //printBucket("Inserted ", id4);
    //printBucket("Inserted ", id5);
    //printBucket("Inserted ", id6);
    //printBucket("Inserted ", id7);
    //printBucket("Inserted ", id8);
    //printBucket("Inserted ", id9);

    document::BucketId id(17, 0x1aaaa);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    //std::cerr << "Done: getAll() for bucket " << id << "\n";
    //printBuckets(results);

    CPPUNIT_ASSERT_EQUAL((size_t)4, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(5,6,7), *results[id5.stripUnused()]); // most specific match (exact match)
    CPPUNIT_ASSERT_EQUAL(A(6,7,8), *results[id6.stripUnused()]); // sub bucket
    CPPUNIT_ASSERT_EQUAL(A(7,8,9), *results[id7.stripUnused()]); // sub bucket

    id = document::BucketId(16, 0xffff);
    results = map.getAll(id, "foo");

    //std::cerr << "Done: getAll() for bucket " << id << "\n";
    //printBuckets(results);

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());

    CPPUNIT_ASSERT_EQUAL(A(9,10,11), *results[id9.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAll2() { // Ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(17, 0x00001);
    document::BucketId id2(17, 0x10001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(16, 0x00001);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // sub bucket
    CPPUNIT_ASSERT_EQUAL(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAllUnusedBitIsSet() { // ticket 2938896
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(24, 0x000dc7089);
    document::BucketId id2(33, 0x0053c7089);
    document::BucketId id3(33, 0x1053c7089);
    document::BucketId id4(24, 0x000bc7089);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(33, 0x1053c7089);
    id.setUsedBits(32); // Bit 33 is set, but unused
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit() { // Ticket 2938896
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x00001); // contains id2-id3
    document::BucketId id2(17, 0x00001);
    document::BucketId id3(17, 0x10001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(16, 0x00001);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)3, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // most specific match (exact match)
    CPPUNIT_ASSERT_EQUAL(A(2,3,4), *results[id2.stripUnused()]); // sub bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit2() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(17, 0x10000);
    document::BucketId id2(27, 0x007228034); // contains id3
    document::BucketId id3(29, 0x007228034);
    document::BucketId id4(17, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);
    map.insert(id4.stripUnused().toKey(), A(4,5,6), "foo", preExisted);

    document::BucketId id(32, 0x027228034);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(2,3,4), *results[id2.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // most specific match (super bucket)
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit3() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff); // contains id2
    document::BucketId id2(17, 0x0ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);

    document::BucketId id(22, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)1, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit4() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x1ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit5() { // ticket 3121525
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(17, 0x0ffff);
    document::BucketId id3(19, 0x5ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x1ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
#endif
}

void
LockableMapTest::testFindAllInconsistentlySplit6() {
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(16, 0x0ffff); // contains id2-id3
    document::BucketId id2(18, 0x1ffff);
    document::BucketId id3(19, 0x7ffff);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(18, 0x3ffff);
    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL((size_t)2, results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

void
LockableMapTest::testFindAllInconsistentBelow16Bits()
{
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;

    document::BucketId id1(1, 0x1); // contains id2-id3
    document::BucketId id2(3, 0x1);
    document::BucketId id3(4, 0xD);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    map.insert(id2.stripUnused().toKey(), A(2,3,4), "foo", preExisted);
    map.insert(id3.stripUnused().toKey(), A(3,4,5), "foo", preExisted);

    document::BucketId id(3, 0x5);

    std::map<document::BucketId, Map::WrappedEntry> results =
        map.getAll(id, "foo");

    CPPUNIT_ASSERT_EQUAL(size_t(2), results.size());

    CPPUNIT_ASSERT_EQUAL(A(1,2,3), *results[id1.stripUnused()]); // super bucket
    CPPUNIT_ASSERT_EQUAL(A(3,4,5), *results[id3.stripUnused()]); // sub bucket
}

void
LockableMapTest::testCreate() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(58, 0x43d6c878000004d2ull);

        std::map<document::BucketId, Map::WrappedEntry> entries(
                map.getContained(id1, "foo"));

        CPPUNIT_ASSERT_EQUAL((size_t)0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(36, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(36,0x8000004d2ull),
                             entry.getBucketId());
    }
    {
        document::BucketId id1(58, 0x423bf1e0000004d2ull);

        std::map<document::BucketId, Map::WrappedEntry> entries(
                map.getContained(id1, "foo"));
        CPPUNIT_ASSERT_EQUAL((size_t)0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(36, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(36,0x0000004d2ull),
                             entry.getBucketId());
    }

    CPPUNIT_ASSERT_EQUAL((size_t)2, map.size());
#endif
}

void
LockableMapTest::testCreate2() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(58, 0xeaf77782000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000000004d2);
        std::map<document::BucketId, Map::WrappedEntry> entries(
                map.getContained(id1, "foo"));

        CPPUNIT_ASSERT_EQUAL((size_t)0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);

        CPPUNIT_ASSERT_EQUAL(document::BucketId(34, 0x0000004d2ull),
                             entry.getBucketId());
    }

    CPPUNIT_ASSERT_EQUAL((size_t)2, map.size());
#endif
}

void
LockableMapTest::testCreate3() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(58, 0xeaf77780000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0xeaf77782000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000000004d2);
        std::map<document::BucketId, Map::WrappedEntry> entries(
                map.getContained(id1, "foo"));

        CPPUNIT_ASSERT_EQUAL((size_t)0, entries.size());

        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(40, 0x0000004d2ull),
                             entry.getBucketId());
    }
#endif
}

void
LockableMapTest::testCreate4() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(16, 0x00000000000004d1);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(40, 0x00000000000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);

        CPPUNIT_ASSERT_EQUAL(document::BucketId(25, 0x0010004d2ull),
                             entry.getBucketId());
    }
#endif
}

void
LockableMapTest::testCreate6() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(0x8c000000000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }

    {
        document::BucketId id1(0xeb54b3ac000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }

    {
        document::BucketId id1(0x88000002000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(0x84000001000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(0xe9944a44000004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(0x90000004000004d2),
                             entry.getBucketId());
    }
#endif
}


void
LockableMapTest::testCreate5() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(58, 0xeaf77780000004d2);
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(40, 0x00000000000004d1);

        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
    }
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(25, 0x0010004d2ull),
                             entry.getBucketId());
    }
#endif
}

void
LockableMapTest::testCreateEmpty() {
#if __WORDSIZE == 64
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    {
        document::BucketId id1(58, 0x00000000010004d2);
        Map::WrappedEntry entry = map.createAppropriateBucket(16, "", id1);
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x0000004d2ull),
                             entry.getBucketId());
    }
#endif
}

void
LockableMapTest::testIsConsistent()
{
    typedef LockableMap<JudyMultiMap<A> > Map;
    Map map;
    document::BucketId id1(16, 0x00001); // contains id2-id3
    document::BucketId id2(17, 0x00001);

    bool preExisted;
    map.insert(id1.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
        CPPUNIT_ASSERT(map.isConsistent(entry));
    }
    map.insert(id2.stripUnused().toKey(), A(1,2,3), "foo", preExisted);
    {
        Map::WrappedEntry entry(
                map.get(id1.stripUnused().toKey(), "foo", true));
        CPPUNIT_ASSERT(!map.isConsistent(entry));
    }
}

} // storage
