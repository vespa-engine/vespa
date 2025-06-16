// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for hashtable.

#include <vespa/log/log.h>
LOG_SETUP("vector_map_test");

#include <vespa/vespalib/stllike/vector_map.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::vector_map;

using namespace vespalib;

namespace {

TEST(VectorMapTest, verify_size_of_underlying_storage) {
    EXPECT_EQ(8u, sizeof(vector_map<uint32_t, uint32_t>::value_type));
    EXPECT_EQ(16u, sizeof(vector_map<uint32_t, void *>::value_type));
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
