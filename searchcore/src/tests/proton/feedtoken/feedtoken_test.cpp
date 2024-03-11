// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace proton;

class LocalTransport : public feedtoken::ITransport {
private:
    size_t _receivedCount;

public:
    LocalTransport()
        : _receivedCount(0)
    { }

    void send(ResultUP, bool) override {
        _receivedCount++;
    }

    size_t getReceivedCount() const { return _receivedCount; }
};

TEST(FeedTokenTest, test_ack)
{
    LocalTransport transport;
    {
        FeedToken token = feedtoken::make(transport);
    }
    EXPECT_EQ(1u, transport.getReceivedCount());
}

TEST(FeedTokenTest, test_fail)
{
    LocalTransport transport;
    FeedToken token = feedtoken::make(transport);
    token->fail();
    EXPECT_EQ(1u, transport.getReceivedCount());
}

TEST(FeedTokenTest, test_handover)
{
    struct MyHandover {
        static FeedToken handover(FeedToken token) {
            return token;
        }
    };

    LocalTransport transport;

    {
        FeedToken token = feedtoken::make(transport);
        token = MyHandover::handover(token);
    }
    EXPECT_EQ(1u, transport.getReceivedCount());
}

GTEST_MAIN_RUN_ALL_TESTS()
