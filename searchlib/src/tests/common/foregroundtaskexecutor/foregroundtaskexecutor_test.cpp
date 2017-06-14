// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("foregroundtaskexecutor_test");
#include <vespa/searchlib/common/foregroundtaskexecutor.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <mutex>
#include <condition_variable>

namespace search
{

namespace common
{


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

    TestObj()
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
        _cv.wait(guard, [=] { return this->_done >= wantDone; });
    }
};

TEST_F("testExecute", Fixture) {
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads.execute(1, [=]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads.sync();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}


TEST_F("require that task with same id are serialized", Fixture)
{
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads.execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(0, [=]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads.sync();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

TEST_F("require that task with different ids are serialized", Fixture)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQUAL(0, tv->_val);
        f._threads.execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(1, [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        f._threads.sync();
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        break;
    }
    EXPECT_TRUE(tryCnt >= 100);
}


}  // namespace common
}  // namespace search

TEST_MAIN() { TEST_RUN_ALL(); }
