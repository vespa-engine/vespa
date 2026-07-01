// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace vespalib;

namespace {

[[nodiscard]] size_t get_total_transient_memory() noexcept {
    auto lock = TransientMemoryTracker::acquire_lock();
    return TransientMemoryTracker::get_total_transient_memory(std::move(lock));
}

[[nodiscard]] std::span<const std::byte> as_bytes(const std::vector<char>& v) noexcept {
    std::span<const char> span = v;
    return std::as_bytes(span);
}

} // namespace

TEST(MemoryDataStoreTest, testMemoryDataStore) {
    MemoryDataStore                         s(alloc::Alloc::alloc(256));
    std::vector<std::span<const std::byte>> v;
    auto                                    mumbo = as_bytes(std::span<const char>("mumbo", 5));
    v.push_back(s.push_back(mumbo));
    EXPECT_EQ(5, v[0].size());
    for (size_t i(0); i < 50; i++) {
        v.push_back(s.push_back(mumbo));
        EXPECT_EQ(static_cast<const std::byte*>(v[i].data()) + 5, v[i + 1].data());
        EXPECT_EQ(5, v[i + 1].size());
    }
    v.push_back(s.push_back(mumbo));
    EXPECT_EQ(52ul, v.size());
    EXPECT_NE(static_cast<const std::byte*>(v[50].data()) + 5, v[51].data());
    for (auto& i : v) {
        ASSERT_EQ(5, i.size());
        EXPECT_EQ(0, memcmp(mumbo.data(), i.data(), 5));
    }
}

TEST(MemoryDataStoreTest, test_transient_memory_with_2mib_slack) {
    MemoryDataStore m(alloc::Alloc::alloc(4_Mi));
    EXPECT_EQ(0, get_total_transient_memory());
    std::vector<char> bytes;
    bytes.resize(512_Ki);
    uint32_t i = 0;
    while (i < 6) {
        auto ref = m.push_back(as_bytes(bytes));
        EXPECT_EQ(512_Ki, ref.size());
        auto total_transient_memory = get_total_transient_memory();
        EXPECT_EQ((i < 4) ? 0_Ki : 2560_Ki, total_transient_memory);
        if (total_transient_memory != 0) {
            break;
        }
        ++i;
    }
    EXPECT_EQ(4, i);
    EXPECT_EQ(2560_Ki, get_total_transient_memory());
    m.clear();
    EXPECT_EQ(0, get_total_transient_memory());
}

GTEST_MAIN_RUN_ALL_TESTS()
