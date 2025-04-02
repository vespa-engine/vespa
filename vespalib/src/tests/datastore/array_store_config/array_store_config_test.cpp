// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/datastore/array_store_config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace vespalib::datastore;
using AllocSpec = ArrayStoreConfig::AllocSpec;

constexpr float ALLOC_GROW_FACTOR = 0.2;

std::function<size_t(uint32_t)> default_type_id_to_entry_size = [](size_t type_id) noexcept { return type_id * sizeof(int); };
std::function<size_t(uint32_t)> large_entry_type_id_to_entry_size = [](size_t type_id) noexcept { return type_id * 16_Ki; };

struct Fixture
{
    using EntryRefType = EntryRefT<18>;
    ArrayStoreConfig cfg;

    Fixture(uint32_t max_type_id,
            const AllocSpec &defaultSpec)
        : cfg(max_type_id, defaultSpec) {}

    Fixture(uint32_t max_type_id,
            size_t hugePageSize,
            size_t smallPageSize,
            size_t max_buffer_size,
            size_t min_num_entries_for_new_buffer,
            std::function<size_t(uint32_t)> type_id_to_entry_size = default_type_id_to_entry_size)
        : cfg(ArrayStoreConfig::optimizeForHugePage(max_type_id,
                                                    type_id_to_entry_size,
                                                    hugePageSize, smallPageSize,
                                                    EntryRefType::offsetSize(),
                                                    max_buffer_size,
                                                    min_num_entries_for_new_buffer,
                                                    ALLOC_GROW_FACTOR)) { }
    void assertSpec(uint32_t type_id, uint32_t num_entries_for_new_buffer) {
        assertSpec(type_id, EntryRefType::offsetSize(), num_entries_for_new_buffer);
    }
    void assertSpec(uint32_t type_id, uint32_t max_entries, uint32_t num_entries_for_new_buffer) {
        assertSpec(type_id, AllocSpec(0, max_entries,
                                      num_entries_for_new_buffer, ALLOC_GROW_FACTOR));
    }
    void assertSpec(uint32_t type_id, const AllocSpec &expSpec) {
        SCOPED_TRACE(std::to_string(type_id));
        const auto& actSpec = cfg.spec_for_type_id(type_id);
        EXPECT_EQ(expSpec.min_entries_in_buffer, actSpec.min_entries_in_buffer);
        EXPECT_EQ(expSpec.max_entries_in_buffer, actSpec.max_entries_in_buffer);
        EXPECT_EQ(expSpec.num_entries_for_new_buffer, actSpec.num_entries_for_new_buffer);
        EXPECT_EQ(expSpec.allocGrowFactor, actSpec.allocGrowFactor);
    }
};

AllocSpec
makeSpec(size_t min_entries_in_buffer,
         size_t max_entries_in_buffer,
         size_t num_entries_for_new_buffer)
{
    return AllocSpec(min_entries_in_buffer, max_entries_in_buffer, num_entries_for_new_buffer, ALLOC_GROW_FACTOR);
}

TEST(ArrayStoreConfigTest, require_that_default_allocation_spec_is_given_for_all_array_sizes)
{
    Fixture f(3, makeSpec(4, 32, 8));
    EXPECT_EQ(3u, f.cfg.max_type_id());
    f.assertSpec(0, makeSpec(4, 32, 8));
    f.assertSpec(1, makeSpec(4, 32, 8));
    f.assertSpec(2, makeSpec(4, 32, 8));
    f.assertSpec(3, makeSpec(4, 32, 8));
}

struct BigBuffersFixture : public Fixture {
    BigBuffersFixture() : Fixture(1023, 2_Mi, 4_Ki, 1024_Gi, 8_Ki) { }
};

TEST(ArrayStoreConfigTest, require_that_we_can_generate_config_optimized_for_a_given_huge_page_without_capped_buffer_sizes)
{
    BigBuffersFixture f;
    EXPECT_EQ(1023u, f.cfg.max_type_id());
    f.assertSpec(0, 8_Ki); // large arrays
    f.assertSpec(1, 256_Ki);
    f.assertSpec(2, 256_Ki);
    f.assertSpec(3, 168_Ki);
    f.assertSpec(4, 128_Ki);
    f.assertSpec(5, 100_Ki);
    f.assertSpec(6, 84_Ki);

    f.assertSpec(32, 16_Ki);
    f.assertSpec(33, 12_Ki);
    f.assertSpec(42, 12_Ki);
    f.assertSpec(43, 8_Ki);
    f.assertSpec(1022, 8_Ki);
    f.assertSpec(1023, 8_Ki);
}

struct CappedBuffersFixture : public Fixture {
    CappedBuffersFixture() : Fixture(1023, 2_Mi, 4_Ki, 256_Mi, 8_Ki) { }
    size_t max_entries(size_t array_size) {
        auto entry_size = array_size * sizeof(int);
        return (256_Mi + entry_size - 1) / entry_size;
    }
};

TEST(ArrayStoreConfigTest, require_that_we_can_generate_config_optimized_for_a_given_huge_page_with_capped_buffer_sizes)
{
    CappedBuffersFixture f;
    EXPECT_EQ(1023u, f.cfg.max_type_id());
    f.assertSpec(0, f.max_entries(1023), 8_Ki); // large arrays
    f.assertSpec(1, 256_Ki);
    f.assertSpec(2, 256_Ki);
    f.assertSpec(3, 168_Ki);
    f.assertSpec(4, 128_Ki);
    f.assertSpec(5, 100_Ki);
    f.assertSpec(6, 84_Ki);

    f.assertSpec(32, 16_Ki);
    f.assertSpec(33, 12_Ki);
    f.assertSpec(42, 12_Ki);
    f.assertSpec(43, 8_Ki);
    f.assertSpec(1022, f.max_entries(1022), 8_Ki);
    f.assertSpec(1023, f.max_entries(1023), 8_Ki);
}

struct CappedBuffersWithLargeEntriesFixture : public Fixture {
    CappedBuffersWithLargeEntriesFixture() : Fixture(3, 2_Mi, 4_Ki, 256_Mi, 8_Ki,
        large_entry_type_id_to_entry_size)
    { }
    size_t max_entries(uint32_t type_id) {
        auto entry_size = large_entry_type_id_to_entry_size(type_id);
        return (256_Mi + entry_size - 1) / entry_size;
    }
};

TEST(ArrayStoreConfigTest, require_that_min_entries_for_new_buffer_is_calculated_correctly_for_large_entries)
{
    CappedBuffersWithLargeEntriesFixture f;
    EXPECT_EQ(3u, f.cfg.max_type_id());
    EXPECT_GT(8_Ki, f.max_entries(3));
    f.assertSpec(0, f.max_entries(3), f.max_entries(3)); // large arrays
    f.assertSpec(1, f.max_entries(1), 8_Ki);
    f.assertSpec(2, f.max_entries(2), 8_Ki);
    f.assertSpec(3, f.max_entries(3), f.max_entries(3));
}

GTEST_MAIN_RUN_ALL_TESTS()
