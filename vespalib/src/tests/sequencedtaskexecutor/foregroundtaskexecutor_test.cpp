// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/foregroundtaskexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <condition_variable>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("foregroundtaskexecutor_test");

namespace vespalib {


class Fixture
{
public:
    ForegroundTaskExecutor _threads;

    Fixture()
        : _threads()
    {
    }
};


class TestObj
{
public:
    std::mutex _m;
    std::condition_variable _cv;
    int _done;
    int _fail;
    int _val;

    TestObj() noexcept
        : _m(),
          _cv(),
          _done(0),
          _fail(0),
          _val(0)
    {
    }

    void
    modify(int oldValue, int newValue)
    {
        {
            std::lock_guard<std::mutex> guard(_m);
            if (_val == oldValue) {
                _val = newValue;
            } else {
                ++_fail;
            }
            ++_done;
        }
        _cv.notify_all();
    }

    void
    wait(int wantDone)
    {
        std::unique_lock<std::mutex> guard(_m);
        _cv.wait(guard, [this, wantDone] { return this->_done >= wantDone; });
    }
};

TEST(ForegroundTaskExecutorTest, testExecute) {
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads.execute(1, [=]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}


TEST(ForegroundTaskExecutorTest, require_that_task_with_same_id_are_serialized)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads.execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(0, [=]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

TEST(ForegroundTaskExecutorTest, require_that_task_with_different_ids_are_serialized)
{
    Fixture f;
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQ(0, tv->_val);
        f._threads.execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(1, [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        f._threads.sync_all();
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        break;
    }
    EXPECT_TRUE(tryCnt >= 100);
}


}

GTEST_MAIN_RUN_ALL_TESTS()
