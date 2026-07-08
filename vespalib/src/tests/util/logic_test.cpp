// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/logic.h>

namespace vespalib {

TEST(LogicTest, material_implication_has_expected_truth_table) {
    EXPECT_TRUE(implies(true, true));
    EXPECT_FALSE(implies(true, false));
    EXPECT_TRUE(implies(false, true));
    EXPECT_TRUE(implies(false, false));
}

} // namespace vespalib
