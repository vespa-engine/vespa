// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("retrypolicy_test");

    RetryTransientErrorsPolicy policy;
    for (uint32_t i = 0; i < 5; ++i) {
        double delay = i / 3.0;
        policy.setBaseDelay(delay);
        for (uint32_t j = 0; j < 5; ++j) {
            EXPECT_EQUAL((int)(j * delay), (int)policy.getRetryDelay(j));
        }
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

    TEST_DONE();
}
