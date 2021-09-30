// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("task_runner_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <mutex>

using proton::initializer::InitializerTask;
using proton::initializer::TaskRunner;

struct TestLog
{
    std::mutex _lock;
    vespalib::string _log;
    using UP = std::unique_ptr<TestLog>;

    TestLog()
        : _lock(),
          _log()
    {
    }

    void append(vespalib::string str) {
        std::lock_guard<std::mutex> guard(_lock);
        _log += str;
    }

    vespalib::string result() const { return _log; }
};

class NamedTask : public InitializerTask
{
protected:
    vespalib::string  _name;
    TestLog          &_log;
    size_t            _transient_memory_usage;
public:
    NamedTask(const vespalib::string &name, TestLog &log, size_t transient_memory_usage = 0)
        : _name(name),
          _log(log),
          _transient_memory_usage(transient_memory_usage)
    {
    }

    virtual void run() override { _log.append(_name); }
    size_t get_transient_memory_usage() const override { return _transient_memory_usage; }
};


struct TestJob {
    TestLog::UP _log;
    InitializerTask::SP _root;

    TestJob(TestLog::UP log, InitializerTask::SP root);
    TestJob(TestJob &&) = default;
    ~TestJob();

    static TestJob setupCDependsOnAandB()
    {
        TestLog::UP log = std::make_unique<TestLog>();
        InitializerTask::SP A(std::make_shared<NamedTask>("A", *log));
        InitializerTask::SP B(std::make_shared<NamedTask>("B", *log));
        InitializerTask::SP C(std::make_shared<NamedTask>("C", *log));
        C->addDependency(A);
        C->addDependency(B);
        return TestJob(std::move(log), std::move(C));
    }

    static TestJob setupDiamond()
    {
        TestLog::UP log = std::make_unique<TestLog>();
        InitializerTask::SP A(std::make_shared<NamedTask>("A", *log));
        InitializerTask::SP B(std::make_shared<NamedTask>("B", *log));
        InitializerTask::SP C(std::make_shared<NamedTask>("C", *log));
        InitializerTask::SP D(std::make_shared<NamedTask>("D", *log));
        C->addDependency(A);
        C->addDependency(B);
        A->addDependency(D);
        B->addDependency(D);
        return TestJob(std::move(log), std::move(C));
    }

    static TestJob setupResourceUsingTasks()
    {
        auto log = std::make_unique<TestLog>();
        auto task_a = std::make_shared<NamedTask>("A", *log, 0);
        auto task_b = std::make_shared<NamedTask>("B", *log, 10);
        auto task_c = std::make_shared<NamedTask>("C", *log, 2);
        auto task_d = std::make_shared<NamedTask>("D", *log, 5);
        auto task_e = std::make_shared<NamedTask>("E", *log, 0);
        task_e->addDependency(task_a);
        task_e->addDependency(task_b);
        task_e->addDependency(task_c);
        task_e->addDependency(task_d);
        return TestJob(std::move(log), std::move(task_e));
    }

};

TestJob::TestJob(TestLog::UP log, InitializerTask::SP root)
    : _log(std::move(log)),
      _root(std::move(root))
{ }
TestJob::~TestJob() {}


struct Fixture
{
    vespalib::ThreadStackExecutor _executor;
    TaskRunner _taskRunner;

    Fixture(uint32_t numThreads = 1)
        : _executor(numThreads, 128_Ki),
          _taskRunner(_executor)
    {
    }

    void run(const InitializerTask::SP &task) { _taskRunner.runTask(task); }
};


TEST_F("1 thread, 2 dependees, 1 depender", Fixture(1))
{
    TestJob job = TestJob::setupCDependsOnAandB();
    f.run(job._root);
    EXPECT_EQUAL("ABC", job._log->result());
}

TEST_F("1 thread, dag graph", Fixture(1))
{
    for (int iter = 0; iter < 1000; ++iter) {
        TestJob job = TestJob::setupDiamond();
        f.run(job._root);
        EXPECT_EQUAL("DABC", job._log->result());
    }
}

TEST_F("multiple threads, dag graph", Fixture(10))
{
    int dabc_count = 0;
    int dbac_count = 0;
    for (int iter = 0; iter < 1000; ++iter) {
        TestJob job = TestJob::setupDiamond();
        f.run(job._root);
        vespalib::string result = job._log->result();
        EXPECT_TRUE("DABC" == result || "DBAC" == result);
        if ("DABC" == result) {
            ++dabc_count;
        }
        if ("DBAC" == result) {
            ++dbac_count;
        }
    }
    LOG(info, "dabc=%d, dbac=%d", dabc_count, dbac_count);
}

TEST_F("single thread with resource using tasks", Fixture(1))
{
    auto job = TestJob::setupResourceUsingTasks();
    f.run(job._root);
    EXPECT_EQUAL("BDCAE", job._log->result());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
