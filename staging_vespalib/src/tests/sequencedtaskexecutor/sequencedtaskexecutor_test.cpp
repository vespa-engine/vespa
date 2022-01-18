// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>

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

vespalib::stringref ZERO("0");

TEST_F("testExecute", Fixture) {
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads->execute(1, [=]() { tv->modify(0, 42); });
    tv->wait(1);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}


TEST_F("require that task with same component id are serialized", Fixture)
{
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    f._threads->execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads->execute(0, [=]() { tv->modify(14, 42); });
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

TEST_F("require that task with same component id are serialized when executed with list", Fixture)
{
    std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
    EXPECT_EQUAL(0, tv->_val);
    ISequencedTaskExecutor::ExecutorId executorId = f._threads->getExecutorId(0);
    ISequencedTaskExecutor::TaskList list;
    list.template emplace_back(executorId, makeLambdaTask([=]() { usleep(2000); tv->modify(0, 14); }));
    list.template emplace_back(executorId, makeLambdaTask([=]() { tv->modify(14, 42); }));
    f._threads->executeTasks(std::move(list));
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

TEST_F("require that task with different component ids are not serialized", Fixture)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < 100; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQUAL(0, tv->_val);
        f._threads->execute(0, [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads->execute(2, [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        f._threads->sync_all();
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
    auto test2 = [=]() { tv->modify(14, 42); };
    f._threads->execute(f._threads->getExecutorIdFromName(ZERO), [=]() { usleep(2000); tv->modify(0, 14); });
    f._threads->execute(f._threads->getExecutorIdFromName(ZERO), test2);
    tv->wait(2);
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
    f._threads->sync_all();
    EXPECT_EQUAL(0,  tv->_fail);
    EXPECT_EQUAL(42, tv->_val);
}

namespace {

int
detectSerializeFailure(Fixture &f, vespalib::stringref altComponentId, int tryLimit)
{
    int tryCnt = 0;
    for (tryCnt = 0; tryCnt < tryLimit; ++tryCnt) {
        std::shared_ptr<TestObj> tv(std::make_shared<TestObj>());
        EXPECT_EQUAL(0, tv->_val);
        f._threads->execute(f._threads->getExecutorIdFromName(ZERO), [=]() { usleep(2000); tv->modify(0, 14); });
        f._threads->execute(f._threads->getExecutorIdFromName(altComponentId), [=]() { tv->modify(14, 42); });
        tv->wait(2);
        if (tv->_fail != 1) {
             continue;
        }
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        f._threads->sync_all();
        EXPECT_EQUAL(1,  tv->_fail);
        EXPECT_EQUAL(14, tv->_val);
        break;
    }
    return tryCnt;
}

vespalib::string
makeAltComponentId(Fixture &f)
{
    int tryCnt = 0;
    char altComponentId[20];
    ISequencedTaskExecutor::ExecutorId executorId0 = f._threads->getExecutorIdFromName(ZERO);
    for (tryCnt = 1; tryCnt < 100; ++tryCnt) {
        sprintf(altComponentId, "%d", tryCnt);
        if (f._threads->getExecutorIdFromName(altComponentId) == executorId0) {
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
    f._threads->execute(0, lambda);
    f._threads->execute(0, lambda);
    f._threads->sync_all();
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
    f._threads->execute(0, lambdaref);
    f._threads->execute(0, lambdaref);
    f._threads->sync_all();
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
    f._threads->executeLambda(ISequencedTaskExecutor::ExecutorId(0), lambda);
    f._threads->sync_all();
    std::vector<int> exp({5, 4});
    EXPECT_EQUAL(exp, res);
    EXPECT_EQUAL(5, i);
}

TEST("require that you get correct number of executors") {
    auto seven = SequencedTaskExecutor::create(sequenced_executor, 7);
    EXPECT_EQUAL(7u, seven->getNumExecutors());
}

void verifyHardLimitForLatency(bool expect_hard) {
    auto sequenced = SequencedTaskExecutor::create(sequenced_executor, 1, 100, expect_hard, Executor::OptimizeFor::LATENCY);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*sequenced);
    EXPECT_EQUAL(expect_hard,nullptr != dynamic_cast<const BlockingThreadStackExecutor *>(seq.first_executor()));
}

void verifyHardLimitForThroughput(bool expect_hard) {
    auto sequenced = SequencedTaskExecutor::create(sequenced_executor, 1, 100, expect_hard, Executor::OptimizeFor::THROUGHPUT);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*sequenced);
    const SingleExecutor * first = dynamic_cast<const SingleExecutor *>(seq.first_executor());
    EXPECT_TRUE(first != nullptr);
    EXPECT_EQUAL(expect_hard, first->isBlocking());
}

TEST("require that you can get executor with both hard and soft limit") {
    verifyHardLimitForLatency(true);
    verifyHardLimitForLatency(false);
    verifyHardLimitForThroughput(true);
    verifyHardLimitForThroughput(false);
}


TEST("require that you distribute well") {
    auto seven = SequencedTaskExecutor::create(sequenced_executor, 7);
    const SequencedTaskExecutor & seq = dynamic_cast<const SequencedTaskExecutor &>(*seven);
    const uint32_t NUM_EXACT = 8 * seven->getNumExecutors();
    EXPECT_EQUAL(7u, seven->getNumExecutors());
    EXPECT_EQUAL(97u, seq.getComponentHashSize());
    EXPECT_EQUAL(0u, seq.getComponentEffectiveHashSize());
    for (uint32_t id=0; id < 1000; id++) {
        if (id < NUM_EXACT) {
            EXPECT_EQUAL(id % seven->getNumExecutors(), seven->getExecutorId(id).getId());
        } else {
            EXPECT_EQUAL(((id - NUM_EXACT) % 97) % seven->getNumExecutors(), seven->getExecutorId(id).getId());
        }
    }
    EXPECT_EQUAL(97u, seq.getComponentHashSize());
    EXPECT_EQUAL(97u, seq.getComponentEffectiveHashSize());
}

TEST("require that similar names get perfect distribution with 4 executors") {
    auto four = SequencedTaskExecutor::create(sequenced_executor, 4);
    EXPECT_EQUAL(0u, four->getExecutorIdFromName("f1").getId());
    EXPECT_EQUAL(1u, four->getExecutorIdFromName("f2").getId());
    EXPECT_EQUAL(2u, four->getExecutorIdFromName("f3").getId());
    EXPECT_EQUAL(3u, four->getExecutorIdFromName("f4").getId());
    EXPECT_EQUAL(0u, four->getExecutorIdFromName("f5").getId());
    EXPECT_EQUAL(1u, four->getExecutorIdFromName("f6").getId());
    EXPECT_EQUAL(2u, four->getExecutorIdFromName("f7").getId());
    EXPECT_EQUAL(3u, four->getExecutorIdFromName("f8").getId());
}

TEST("require that similar names get perfect distribution with 8 executors") {
    auto four = SequencedTaskExecutor::create(sequenced_executor, 8);
    EXPECT_EQUAL(0u, four->getExecutorIdFromName("f1").getId());
    EXPECT_EQUAL(1u, four->getExecutorIdFromName("f2").getId());
    EXPECT_EQUAL(2u, four->getExecutorIdFromName("f3").getId());
    EXPECT_EQUAL(3u, four->getExecutorIdFromName("f4").getId());
    EXPECT_EQUAL(4u, four->getExecutorIdFromName("f5").getId());
    EXPECT_EQUAL(5u, four->getExecutorIdFromName("f6").getId());
    EXPECT_EQUAL(6u, four->getExecutorIdFromName("f7").getId());
    EXPECT_EQUAL(7u, four->getExecutorIdFromName("f8").getId());
}

TEST("Test creation of different types") {
    auto iseq = SequencedTaskExecutor::create(sequenced_executor, 1);

    EXPECT_EQUAL(1u, iseq->getNumExecutors());
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

TEST_MAIN() { TEST_RUN_ALL(); }
