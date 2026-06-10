// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace vespalib;

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
        EXPECT_EQ(0, memcmp(mumbo.data(), i.data(), 5));
        EXPECT_EQ(5, i.size());
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
