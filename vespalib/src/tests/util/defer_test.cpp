// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/defer.h>

using vespalib::defer;

TEST(DeferTest, defer_will_defer) {
    std::vector<int> seq;
    {
        auto d1 = defer([&](){ seq.push_back(1); });
        auto d2 = defer([&](){ seq.push_back(2); });
        {
            auto d3 = defer([&](){ seq.push_back(3); });
            auto d4 = defer([&](){ seq.push_back(4); });
        }
        {
            auto d5 = defer([&](){ seq.push_back(5); });
            auto d6 = defer([&](){ seq.push_back(6); });
        }
    }
    EXPECT_EQ(seq, std::vector<int>({4, 3, 6, 5, 2, 1}));
}
