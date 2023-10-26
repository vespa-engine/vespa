// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/queue.h>

using namespace mbus;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("queue_test");
    Queue<int> q;
    EXPECT_TRUE(q.size() == 0);
    q.push(1);
    EXPECT_TRUE(q.size() == 1);
    EXPECT_TRUE(q.front() == 1);
    q.push(2);
    EXPECT_TRUE(q.size() == 2);
    EXPECT_TRUE(q.front() == 1);
    q.push(3);
    EXPECT_TRUE(q.size() == 3);
    EXPECT_TRUE(q.front() == 1);
    q.push(4);
    EXPECT_TRUE(q.size() == 4);
    EXPECT_TRUE(q.front() == 1);
    q.push(5);
    EXPECT_TRUE(q.size() == 5);
    EXPECT_TRUE(q.front() == 1);
    q.push(6);
    EXPECT_TRUE(q.size() == 6);
    EXPECT_TRUE(q.front() == 1);
    q.push(7);
    EXPECT_TRUE(q.size() == 7);
    EXPECT_TRUE(q.front() == 1);
    q.push(8);
    EXPECT_TRUE(q.size() == 8);
    EXPECT_TRUE(q.front() == 1);
    q.push(9);
    EXPECT_TRUE(q.size() == 9);
    EXPECT_TRUE(q.front() == 1);
    q.pop();
    EXPECT_TRUE(q.size() == 8);
    EXPECT_TRUE(q.front() == 2);
    q.pop();
    EXPECT_TRUE(q.size() == 7);
    EXPECT_TRUE(q.front() == 3);
    q.pop();
    EXPECT_TRUE(q.size() == 6);
    EXPECT_TRUE(q.front() == 4);
    q.push(1);
    EXPECT_TRUE(q.size() == 7);
    EXPECT_TRUE(q.front() == 4);
    q.push(2);
    EXPECT_TRUE(q.size() == 8);
    EXPECT_TRUE(q.front() == 4);
    q.push(3);
    EXPECT_TRUE(q.size() == 9);
    EXPECT_TRUE(q.front() == 4);
    q.pop();
    EXPECT_TRUE(q.size() == 8);
    EXPECT_TRUE(q.front() == 5);
    q.pop();
    EXPECT_TRUE(q.size() == 7);
    EXPECT_TRUE(q.front() == 6);
    q.pop();
    EXPECT_TRUE(q.size() == 6);
    EXPECT_TRUE(q.front() == 7);
    q.pop();
    EXPECT_TRUE(q.size() == 5);
    EXPECT_TRUE(q.front() == 8);
    q.pop();
    EXPECT_TRUE(q.size() == 4);
    EXPECT_TRUE(q.front() == 9);
    q.pop();
    EXPECT_TRUE(q.size() == 3);
    EXPECT_TRUE(q.front() == 1);
    q.pop();
    EXPECT_TRUE(q.size() == 2);
    EXPECT_TRUE(q.front() == 2);
    q.pop();
    EXPECT_TRUE(q.size() == 1);
    EXPECT_TRUE(q.front() == 3);
    q.pop();
    EXPECT_TRUE(q.size() == 0);
    TEST_DONE();
}
