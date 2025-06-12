// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

TEST(RetryPolicyTest, retrypolicy_test) {
    constexpr double DELAY(0.001);
    RetryTransientErrorsPolicy policy;
    policy.setBaseDelay(DELAY);
    EXPECT_EQ(0.0, policy.getRetryDelay(0));
    EXPECT_EQ(0.0, policy.getRetryDelay(1));
    for (uint32_t j = 2; j < 15; ++j) {
        EXPECT_EQ(DELAY*(1 << (j-1)), policy.getRetryDelay(j));
    }
    EXPECT_EQ(10.0, policy.getRetryDelay(15));
    EXPECT_EQ(10.0, policy.getRetryDelay(20));
    for (uint32_t i = 0; i < 5; ++i) {
        for (uint32_t j = ErrorCode::NONE; j < ErrorCode::ERROR_LIMIT; ++j) {
            policy.setEnabled(true);
            if (j < ErrorCode::FATAL_ERROR) {
                EXPECT_TRUE(policy.canRetry(j));
            } else {
                EXPECT_TRUE(!policy.canRetry(j));
            }
            policy.setEnabled(false);
            EXPECT_TRUE(!policy.canRetry(j));
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
