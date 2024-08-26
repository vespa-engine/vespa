// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>

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

std::string_view ZERO("0");

TEST(AdaptiveSequencedExecutorTest, testExecute)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads.execute(1, [&]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}


TEST(AdaptiveSequencedExecutorTest, require_that_task_with_same_component_id_are_serialized)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads.execute(0, [&]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(0, [&]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

TEST(AdaptiveSequencedExecutorTest, require_that_task_with_different_component_ids_are_not_serialized)
{
    Fixture f;
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQ(0, tv->_val);
        f._threads.execute(0, [&]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(1, [&]() { tv->modify(14, 42); });
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
    EXPECT_TRUE(tryCnt < 100);
}


TEST(AdaptiveSequencedExecutorTest, require_that_task_with_same_string_component_id_are_serialized)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    auto test2 = [&]() { tv->modify(14, 42); };
    f._threads.execute(f._threads.getExecutorIdFromName(ZERO), [&]() { usleep(2000); tv->modify(0, 14); });
    f._threads.execute(f._threads.getExecutorIdFromName(ZERO), test2);
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads.sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

namespace {

int detectSerializeFailure(Fixture &f, std::string_view altComponentId, int tryLimit)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < tryLimit; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQ(0, tv->_val);
        f._threads.execute(f._threads.getExecutorIdFromName(ZERO), [&]() { usleep(2000); tv->modify(0, 14); });
        f._threads.execute(f._threads.getExecutorIdFromName(altComponentId), [&]() { tv->modify(14, 42); });
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
    return tryCnt;
}

std::string makeAltComponentId(Fixture &f)
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

TEST(AdaptiveSequencedExecutorTest, require_that_task_with_different_string_component_ids_are_not_serialized)
{
    Fixture f;
    int tryCnt = detectSerializeFailure(f, "2", 100);
    EXPECT_TRUE(tryCnt < 100);
}


TEST(AdaptiveSequencedExecutorTest, require_that_task_with_different_string_component_ids_mapping_to_the_same_executor_id_are_serialized)
{
    Fixture f;
    std::string altComponentId = makeAltComponentId(f);
    LOG(info, "second string component id is \"%s\"", altComponentId.c_str());
    int tryCnt = detectSerializeFailure(f, altComponentId, 100);
    EXPECT_TRUE(tryCnt == 100);
}


TEST(AdaptiveSequencedExecutorTest, require_that_execute_works_with_const_lambda)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads.execute(0, lambda);
    f._threads.execute(0, lambda);
    f._threads.sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(AdaptiveSequencedExecutorTest, require_that_execute_works_with_reference_to_lambda)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    auto lambda = [i, &res]() mutable
                  { res.push_back(i--); res.push_back(i--); };
    auto &lambdaref = lambda;
    f._threads.execute(0, lambdaref);
    f._threads.execute(0, lambdaref);
    f._threads.sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(AdaptiveSequencedExecutorTest, require_that_executeLambda_works)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads.executeLambda(ISequencedTaskExecutor::ExecutorId(0), lambda);
    f._threads.sync_all();
    std::vector<int> exp({5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(AdaptiveSequencedExecutorTest, require_that_you_get_correct_number_of_executors) {
    AdaptiveSequencedExecutor seven(7, 1, 0, 10, true);
    EXPECT_EQ(7u, seven.getNumExecutors());
}

TEST(AdaptiveSequencedExecutorTest, require_that_you_distribute_well) {
    AdaptiveSequencedExecutor seven(7, 1, 0, 10, true);
    EXPECT_EQ(7u, seven.getNumExecutors());
    for (uint32_t id=0; id < 1000; id++) {
        EXPECT_EQ(id%7, seven.getExecutorId(id).getId());
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
