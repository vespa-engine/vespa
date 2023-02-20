// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>

#include <condition_variable>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("adaptive_sequenced_executor_test");

namespace vespalib {


class Fixture
{
public:
    AdaptiveSequencedExecutor _threads;

    Fixture(bool is_max_pending_hard=true) : _threads(2, 2, 0, 1000, is_max_pending_hard) { }
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
            _cv.notify_all();
        }
    }

    void
    wait(int wantDone)
    {
        std::unique_lock<std::mutex> guard(_m);
        _cv.wait(guard, [&] { return this->_done >= wantDone; });
    }
};

vespalib::stringref ZERO("0");

TEST_F("testExecute", Fixture) {
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads.execute(1, [&]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}


TEST_F("require that task with same component id are serialized", Fixture)
{
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads.execute(0, [&]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(0, [&]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

TEST_F("require that task with different component ids are not serialized", Fixture)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQUAL(0, tv->_val);
        f._threads.execute(0, [&]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(1, [&]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        f._threads.sync_all();
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        break;
    }
    EXPECT_TRUE(tryCnt < 100);
}


TEST_F("require that task with same string component id are serialized", Fixture)
{
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    auto test2 = [&]() { tv->modify(14, 42); };
    f._threads.execute(f._threads.getExecutorIdFromName(ZERO), [&]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(f._threads.getExecutorIdFromName(ZERO), test2);
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

namespace {

int detectSerializeFailure(Fixture &f, vespalib::stringref altComponentId, int tryLimit)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < tryLimit; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQUAL(0, tv->_val);
        f._threads.execute(f._threads.getExecutorIdFromName(ZERO), [&]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(f._threads.getExecutorIdFromName(altComponentId), [&]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        f._threads.sync_all();
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        break;
    }
    return tryCnt;
}

vespalib::string makeAltComponentId(Fixture &f)
{
    int tryCnt = 0;
    char altComponentId[20];
    ISequencedTaskExecutor::ExecutorId executorId0 = f._threads.getExecutorIdFromName(ZERO);
    for (tryCnt = 1; tryCnt < 100; ++tryCnt) {
        snprintf(altComponentId, sizeof(altComponentId), "%d", tryCnt);
        if (f._threads.getExecutorIdFromName(altComponentId) == executorId0) {
            break;
        }
    }
    EXPECT_TRUE(tryCnt < 100);
    return altComponentId;
}

}

TEST_F("require that task with different string component ids are not serialized", Fixture)
{
    int tryCnt = detectSerializeFailure(f, "2", 100);
    EXPECT_TRUE(tryCnt < 100);
}


TEST_F("require that task with different string component ids mapping to the same executor id are serialized",
       Fixture)
{
    vespalib::string altComponentId = makeAltComponentId(f);
    LOG(info, "second string component id is \"%s\"", altComponentId.c_str());
    int tryCnt = detectSerializeFailure(f, altComponentId, 100);
    EXPECT_TRUE(tryCnt == 100);
}


TEST_F("require that execute works with const lambda", Fixture)
{
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads.execute(0, lambda);
    f._threads.execute(0, lambda);
    f._threads.sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQUAL(exp, res);
    EXPECT_EQUAL(5, i);
}

TEST_F("require that execute works with reference to lambda", Fixture)
{
    int i = 5;
    std::vector<int> res;
    auto lambda = [i, &res]() mutable
                  { res.push_back(i--); res.push_back(i--); };
    auto &lambdaref = lambda;
    f._threads.execute(0, lambdaref);
    f._threads.execute(0, lambdaref);
    f._threads.sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQUAL(exp, res);
    EXPECT_EQUAL(5, i);
}

TEST_F("require that executeLambda works", Fixture)
{
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads.executeLambda(ISequencedTaskExecutor::ExecutorId(0), lambda);
    f._threads.sync_all();
    std::vector<int> exp({5, 4});
    EXPECT_EQUAL(exp, res);
    EXPECT_EQUAL(5, i);
}

TEST("require that you get correct number of executors") {
    AdaptiveSequencedExecutor seven(7, 1, 0, 10, true);
    EXPECT_EQUAL(7u, seven.getNumExecutors());
}

TEST("require that you distribute well") {
    AdaptiveSequencedExecutor seven(7, 1, 0, 10, true);
    EXPECT_EQUAL(7u, seven.getNumExecutors());
    for (uint32_t id=0; id < 1000; id++) {
        EXPECT_EQUAL(id%7, seven.getExecutorId(id).getId());
    }
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
