// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>

#include <condition_variable>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("sequencedtaskexecutor_test");

VESPA_THREAD_STACK_TAG(sequenced_executor)

namespace vespalib {


class Fixture
{
public:
    std::unique_ptr<ISequencedTaskExecutor> _threads;

    Fixture(bool is_task_limit_hard = true) :
        _threads(SequencedTaskExecutor::create(sequenced_executor, 2, 1000, is_task_limit_hard,
                                               Executor::OptimizeFor::LATENCY))
    { }
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

std::string_view ZERO("0");

TEST(SequencedTaskExecutorTest, testExecute)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads->execute(1, [=]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}


TEST(SequencedTaskExecutorTest, require_that_task_with_same_component_id_are_serialized)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    f._threads->execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads->execute(0, [=]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

TEST(SequencedTaskExecutorTest, require_that_task_with_same_component_id_are_serialized_when_executed_with_list)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    ISequencedTaskExecutor::ExecutorId executorId = f._threads->getExecutorId(0);
    ISequencedTaskExecutor::TaskList list;
    list.emplace_back(executorId, makeLambdaTask([=]() { usleep(2000); tv->modify(0, 14); }));
    list.emplace_back(executorId, makeLambdaTask([=]() { tv->modify(14, 42); }));
    f._threads->executeTasks(std::move(list));
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

TEST(SequencedTaskExecutorTest, require_that_task_with_different_component_ids_are_not_serialized)
{
    Fixture f;
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQ(0, tv->_val);
        f._threads->execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads->execute(2, [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        f._threads->sync_all();
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        break;
    }
    EXPECT_TRUE(tryCnt < 100);
}


TEST(SequencedTaskExecutorTest, require_that_task_with_same_string_component_id_are_serialized)
{
    Fixture f;
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQ(0, tv->_val);
    auto test2 = [=]() { tv->modify(14, 42); };
    f._threads->execute(f._threads->getExecutorIdFromName(ZERO), [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads->execute(f._threads->getExecutorIdFromName(ZERO), test2);
    tv->wait(2);
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQ(0,  tv->_fail);
    EXPECT_EQ(42, tv->_val);
}

namespace {

int
detectSerializeFailure(Fixture &f, std::string_view altComponentId, int tryLimit)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < tryLimit; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQ(0, tv->_val);
        f._threads->execute(f._threads->getExecutorIdFromName(ZERO), [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads->execute(f._threads->getExecutorIdFromName(altComponentId), [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        f._threads->sync_all();
        EXPECT_EQ(1,  tv->_fail);
        EXPECT_EQ(14, tv->_val);
        break;
    }
    return tryCnt;
}

std::string
makeAltComponentId(Fixture &f)
{
    int tryCnt = 0;
    char altComponentId[20];
    ISequencedTaskExecutor::ExecutorId executorId0 = f._threads->getExecutorIdFromName(ZERO);
    for (tryCnt = 1; tryCnt < 100; ++tryCnt) {
        snprintf(altComponentId, sizeof(altComponentId), "%d", tryCnt);
        if (f._threads->getExecutorIdFromName(altComponentId) == executorId0) {
            break;
        }
    }
    EXPECT_TRUE(tryCnt < 100);
    return altComponentId;
}

}

TEST(SequencedTaskExecutorTest, require_that_task_with_different_string_component_ids_are_not_serialized)
{
    Fixture f;
    int tryCnt = detectSerializeFailure(f, "2", 100);
    EXPECT_TRUE(tryCnt < 100);
}


TEST(SequencedTaskExecutorTest, require_that_task_with_different_string_component_ids_mapping_to_the_same_executor_id_are_serialized)
{
    Fixture f;
    std::string altComponentId = makeAltComponentId(f);
    LOG(info, "second string component id is \"%s\"", altComponentId.c_str());
    int tryCnt = detectSerializeFailure(f, altComponentId, 100);
    EXPECT_TRUE(tryCnt == 100);
}


TEST(SequencedTaskExecutorTest, require_that_execute_works_with_const_lambda)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads->execute(0, lambda);
    f._threads->execute(0, lambda);
    f._threads->sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(SequencedTaskExecutorTest, require_that_execute_works_with_reference_to_lambda)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    auto lambda = [i, &res]() mutable
                  { res.push_back(i--); res.push_back(i--); };
    auto &lambdaref = lambda;
    f._threads->execute(0, lambdaref);
    f._threads->execute(0, lambdaref);
    f._threads->sync_all();
    std::vector<int> exp({5, 4, 5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(SequencedTaskExecutorTest, require_that_executeLambda_works)
{
    Fixture f;
    int i = 5;
    std::vector<int> res;
    const auto lambda = [i, &res]() mutable
                        { res.push_back(i--); res.push_back(i--); };
    f._threads->executeLambda(ISequencedTaskExecutor::ExecutorId(0), lambda);
    f._threads->sync_all();
    std::vector<int> exp({5, 4});
    EXPECT_EQ(exp, res);
    EXPECT_EQ(5, i);
}

TEST(SequencedTaskExecutorTest, require_that_you_get_correct_number_of_executors) {
    auto seven = SequencedTaskExecutor::create(sequenced_executor, 7);
    EXPECT_EQ(7u, seven->getNumExecutors());
}

void verifyHardLimitForLatency(bool expect_hard) {
    auto sequenced = SequencedTaskExecutor::create(sequenced_executor, 1, 100, expect_hard, Executor::OptimizeFor::LATENCY);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*sequenced);
    EXPECT_EQ(expect_hard,nullptr != dynamic_cast<const BlockingThreadStackExecutor *>(seq.first_executor()));
}

void verifyHardLimitForThroughput(bool expect_hard) {
    auto sequenced = SequencedTaskExecutor::create(sequenced_executor, 1, 100, expect_hard, Executor::OptimizeFor::THROUGHPUT);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*sequenced);
    const SingleExecutor * first = dynamic_cast<const SingleExecutor *>(seq.first_executor());
    EXPECT_TRUE(first != nullptr);
    EXPECT_EQ(expect_hard, first->isBlocking());
}

TEST(SequencedTaskExecutorTest, require_that_you_can_get_executor_with_both_hard_and_soft_limit) {
    verifyHardLimitForLatency(true);
    verifyHardLimitForLatency(false);
    verifyHardLimitForThroughput(true);
    verifyHardLimitForThroughput(false);
}


TEST(SequencedTaskExecutorTest, require_that_you_distribute_well) {
    auto seven = SequencedTaskExecutor::create(sequenced_executor, 7);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*seven);
    const uint32_t NUM_EXACT = 8 * seven->getNumExecutors();
    EXPECT_EQ(7u, seven->getNumExecutors());
    EXPECT_EQ(97u, seq.getComponentHashSize());
    EXPECT_EQ(0u, seq.getComponentEffectiveHashSize());
    for (uint32_t id=0; id < 1000; id++) {
        if (id < NUM_EXACT) {
            EXPECT_EQ(id % seven->getNumExecutors(), seven->getExecutorId(id).getId());
        } else {
            EXPECT_EQ(((id - NUM_EXACT) % 97) % seven->getNumExecutors(), seven->getExecutorId(id).getId());
        }
    }
    EXPECT_EQ(97u, seq.getComponentHashSize());
    EXPECT_EQ(97u, seq.getComponentEffectiveHashSize());
}

TEST(SequencedTaskExecutorTest, require_that_similar_names_get_perfect_distribution_with_4_executors) {
    auto four = SequencedTaskExecutor::create(sequenced_executor, 4);
    EXPECT_EQ(0u, four->getExecutorIdFromName("f1").getId());
    EXPECT_EQ(1u, four->getExecutorIdFromName("f2").getId());
    EXPECT_EQ(2u, four->getExecutorIdFromName("f3").getId());
    EXPECT_EQ(3u, four->getExecutorIdFromName("f4").getId());
    EXPECT_EQ(0u, four->getExecutorIdFromName("f5").getId());
    EXPECT_EQ(1u, four->getExecutorIdFromName("f6").getId());
    EXPECT_EQ(2u, four->getExecutorIdFromName("f7").getId());
    EXPECT_EQ(3u, four->getExecutorIdFromName("f8").getId());
}

TEST(SequencedTaskExecutorTest, require_that_similar_names_get_perfect_distribution_with_8_executors) {
    auto four = SequencedTaskExecutor::create(sequenced_executor, 8);
    EXPECT_EQ(0u, four->getExecutorIdFromName("f1").getId());
    EXPECT_EQ(1u, four->getExecutorIdFromName("f2").getId());
    EXPECT_EQ(2u, four->getExecutorIdFromName("f3").getId());
    EXPECT_EQ(3u, four->getExecutorIdFromName("f4").getId());
    EXPECT_EQ(4u, four->getExecutorIdFromName("f5").getId());
    EXPECT_EQ(5u, four->getExecutorIdFromName("f6").getId());
    EXPECT_EQ(6u, four->getExecutorIdFromName("f7").getId());
    EXPECT_EQ(7u, four->getExecutorIdFromName("f8").getId());
}

TEST(SequencedTaskExecutorTest, Test_creation_of_different_types) {
    auto iseq = SequencedTaskExecutor::create(sequenced_executor, 1);

    EXPECT_EQ(1u, iseq->getNumExecutors());
    auto * seq = dynamic_cast<SequencedTaskExecutor *>(iseq.get());
    ASSERT_TRUE(seq != nullptr);

    iseq = SequencedTaskExecutor::create(sequenced_executor, 1, 1000, true, Executor::OptimizeFor::LATENCY);
    seq = dynamic_cast<SequencedTaskExecutor *>(iseq.get());
    ASSERT_TRUE(seq != nullptr);

    iseq = SequencedTaskExecutor::create(sequenced_executor, 1, 1000, true, Executor::OptimizeFor::THROUGHPUT);
    seq = dynamic_cast<SequencedTaskExecutor *>(iseq.get());
    ASSERT_TRUE(seq != nullptr);

    iseq = SequencedTaskExecutor::create(sequenced_executor, 1, 1000, true, Executor::OptimizeFor::ADAPTIVE, 17);
    auto aseq = dynamic_cast<AdaptiveSequencedExecutor *>(iseq.get());
    ASSERT_TRUE(aseq != nullptr);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
