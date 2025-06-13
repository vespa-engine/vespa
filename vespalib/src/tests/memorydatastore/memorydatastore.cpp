// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace vespalib;

TEST(MemoryDataStoreTest, testMemoryDataStore)
{
    MemoryDataStore s(alloc::Alloc::alloc(256), nullptr);
    std::vector<MemoryDataStore::Reference> v;
    v.push_back(s.push_back("mumbo", 5));
    for (size_t i(0); i < 50; i++) {
        v.push_back(s.push_back("mumbo", 5));
        EXPECT_EQ(static_cast<const char *>(v[i].data()) + 5, v[i+1].data());
    }
    v.push_back(s.push_back("mumbo", 5));
    EXPECT_EQ(52ul, v.size());
    EXPECT_NE(static_cast<const char *>(v[50].data()) + 5, v[51].data());
    for (auto & i : v) {
        EXPECT_EQ(0, memcmp("mumbo", i.data(), 5));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
