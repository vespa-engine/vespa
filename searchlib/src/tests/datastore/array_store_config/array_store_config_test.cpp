// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/datastore/array_store_config.h>

using namespace search::datastore;
using AllocSpec = ArrayStoreConfig::AllocSpec;

constexpr float ALLOC_GROW_FACTOR = 0.2;

struct Fixture
{
    using EntryRefType = EntryRefT<18>;
    ArrayStoreConfig cfg;

    Fixture(uint32_t maxSmallArraySize,
            const AllocSpec &defaultSpec)
        : cfg(maxSmallArraySize, defaultSpec) {}

    Fixture(uint32_t maxSmallArraySize,
            size_t hugePageSize,
            size_t smallPageSize,
            size_t minNumArraysForNewBuffer)
        : cfg(ArrayStoreConfig::optimizeForHugePage(maxSmallArraySize, hugePageSize, smallPageSize,
                                                    sizeof(int), EntryRefType::offsetSize(),
                                                    minNumArraysForNewBuffer,
                                                    ALLOC_GROW_FACTOR)) { }
    void assertSpec(size_t arraySize, uint32_t numArraysForNewBuffer) {
        assertSpec(arraySize, AllocSpec(0, EntryRefType::offsetSize(),
                                        numArraysForNewBuffer, ALLOC_GROW_FACTOR));
    }
    void assertSpec(size_t arraySize, const AllocSpec &expSpec) {
        const ArrayStoreConfig::AllocSpec &actSpec = cfg.specForSize(arraySize);
        EXPECT_EQUAL(expSpec.minArraysInBuffer, actSpec.minArraysInBuffer);
        EXPECT_EQUAL(expSpec.maxArraysInBuffer, actSpec.maxArraysInBuffer);
        EXPECT_EQUAL(expSpec.numArraysForNewBuffer, actSpec.numArraysForNewBuffer);
        EXPECT_EQUAL(expSpec.allocGrowFactor, actSpec.allocGrowFactor);
    }
};

AllocSpec
makeSpec(size_t minArraysInBuffer,
         size_t maxArraysInBuffer,
         size_t numArraysForNewBuffer)
{
    return AllocSpec(minArraysInBuffer, maxArraysInBuffer, numArraysForNewBuffer, ALLOC_GROW_FACTOR);
}

constexpr size_t KB = 1024;
constexpr size_t MB = KB * KB;

TEST_F("require that default allocation spec is given for all array sizes", Fixture(3, makeSpec(4, 32, 8)))
{
    EXPECT_EQUAL(3u, f.cfg.maxSmallArraySize());
    TEST_DO(f.assertSpec(0, makeSpec(4, 32, 8)));
    TEST_DO(f.assertSpec(1, makeSpec(4, 32, 8)));
    TEST_DO(f.assertSpec(2, makeSpec(4, 32, 8)));
    TEST_DO(f.assertSpec(3, makeSpec(4, 32, 8)));
}

TEST_F("require that we can generate config optimized for a given huge page", Fixture(1024,
                                                                                      2 * MB,
                                                                                      4 * KB,
                                                                                      8 * KB))
{
    EXPECT_EQUAL(1024u, f.cfg.maxSmallArraySize());
    TEST_DO(f.assertSpec(0, 8 * KB)); // large arrays
    TEST_DO(f.assertSpec(1, 256 * KB));
    TEST_DO(f.assertSpec(2, 256 * KB));
    TEST_DO(f.assertSpec(3, 168 * KB));
    TEST_DO(f.assertSpec(4, 128 * KB));
    TEST_DO(f.assertSpec(5, 100 * KB));
    TEST_DO(f.assertSpec(6, 84 * KB));

    TEST_DO(f.assertSpec(32, 16 * KB));
    TEST_DO(f.assertSpec(33, 12 * KB));
    TEST_DO(f.assertSpec(42, 12 * KB));
    TEST_DO(f.assertSpec(43, 8 * KB));
    TEST_DO(f.assertSpec(1022, 8 * KB));
    TEST_DO(f.assertSpec(1023, 8 * KB));
}

TEST_MAIN() { TEST_RUN_ALL(); }
