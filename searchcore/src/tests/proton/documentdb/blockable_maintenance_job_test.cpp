// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/blockable_maintenance_job.h>
#include <vespa/searchcore/proton/server/maintenance_job_token_source.h>
#include <vespa/searchcore/proton/server/maintenancejobrunner.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <condition_variable>
#include <memory>

using proton::BlockableMaintenanceJob;
using proton::IBlockableMaintenanceJob;
using proton::IMaintenanceJob;
using proton::IMaintenanceJobRunner;
using proton::MaintenanceJobRunner;
using proton::MaintenanceJobTokenSource;
using vespalib::ThreadStackExecutor;

namespace {

constexpr uint32_t work_log_size = 20;

class JobProxy : public IMaintenanceJob {
    std::shared_ptr<IMaintenanceJob> _job;
public:
    JobProxy(std::shared_ptr<IMaintenanceJob> job);
    ~JobProxy() override;
    bool isBlocked() const override { return _job->isBlocked(); }
    IBlockableMaintenanceJob *asBlockable() override { return _job->asBlockable(); }
    void registerRunner(IMaintenanceJobRunner *runner) override {
        _job->registerRunner(runner);
    }
    bool run() override { return _job->run(); }
    void onStop() override { _job->stop(); }
};

JobProxy::JobProxy(std::shared_ptr<IMaintenanceJob> job)
    : IMaintenanceJob(job->getName(), job->getDelay(), job->getInterval()),
      _job(std::move(job))
{

}

JobProxy::~JobProxy() = default;

class JobResult {
    std::mutex              _mutex;
    std::condition_variable _cv;
    bool                    _ready;
    bool                    _done;
    std::vector<uint32_t>   _work_log;
public:
    JobResult() noexcept;
    ~JobResult();
    void set_ready() { std::lock_guard guard(_mutex); _ready = true; _cv.notify_all(); }
    void set_done() { std::lock_guard guard(_mutex); _done = true; _cv.notify_all(); }
    void wait_ready() { std::unique_lock guard(_mutex); _cv.wait(guard, [this]() { return _ready; }); }
    void wait_done() { std::unique_lock guard(_mutex); _cv.wait(guard, [this]() { return _done; }); }
    bool is_done() noexcept { std::unique_lock guard(_mutex); return _done; }
    void add(uint32_t id) { _work_log.push_back(id); if (_work_log.size() >= work_log_size) { set_done(); }}
    const std::vector<uint32_t>& get_work_log() const noexcept { return _work_log; }
};

JobResult::JobResult() noexcept
    : _mutex(),
      _cv(),
      _ready(false),
      _done(false),
      _work_log()
{
}

JobResult::~JobResult() = default;

std::vector<uint32_t> expected_result(uint32_t num_jobs, uint32_t chunk_size, uint32_t max_size)
{
    std::vector<uint32_t> result;
    while (true) {
        for (uint32_t job = 0; job < num_jobs; ++job) {
            for (uint32_t elem = 0; elem < chunk_size; ++elem) {
                result.push_back(job);
                if (result.size() >= max_size) {
                    return result;
                }
            }
        }
    }
}

class MyJob : public BlockableMaintenanceJob,
              public std::enable_shared_from_this<MyJob> {
    uint32_t                   _id;
    std::shared_ptr<JobResult> _job_result;
    uint32_t                   _remaining;
public:
    MyJob(const std::string& name, std::shared_ptr<MaintenanceJobTokenSource> token_source,
          uint32_t id, std::shared_ptr<JobResult> job_result);
    ~MyJob() override;
    bool run() override;
};

MyJob::MyJob(const std::string& name, std::shared_ptr<MaintenanceJobTokenSource> token_source,
             uint32_t id, std::shared_ptr<JobResult> job_result)
  : BlockableMaintenanceJob(name, 1min, 1min),
    enable_shared_from_this<MyJob>(),
    _id(id),
    _job_result(std::move(job_result)),
    _remaining(3)
{
    _token_source = std::move(token_source);
}

MyJob::~MyJob() = default;

bool
MyJob::run()
{
    _job_result->wait_ready();
    if (isBlocked()) {
        return true;
    }
    if (_token_source && !_token && !_token_source->get_token(shared_from_this())) {
        return true; // Blocked on job token
    }
    if (!_job_result->is_done()) {
        _job_result->add(_id);
        if (--_remaining == 0) {
            _token.reset();
            _remaining = 3;
        }
        return false;
    }
    return true;
}

struct MyJobRunner : public IMaintenanceJobRunner {
    bool                             _pending;
    std::shared_ptr<IMaintenanceJob> _job;
    explicit MyJobRunner(std::shared_ptr<IMaintenanceJob> job)
        : _pending(false),
          _job(std::move(job))
    {
        _job->registerRunner(this);
    }
    ~MyJobRunner() override;
    void run() override { _pending = true; }
    bool is_pending() const noexcept { return _pending; }
    void run_once() {
        _pending = false;
        auto finished = _job->run();
        if (!finished) {
            _pending = true;
        }
    }
    IMaintenanceJob &get_job() { return *_job; }
};

MyJobRunner::~MyJobRunner() = default;

}

class BlockableMaintenanceJobTest : public ::testing::Test {
protected:
    ThreadStackExecutor                        _executor;
    std::shared_ptr<MaintenanceJobTokenSource> _token_source;
    std::shared_ptr<JobResult>                 _job_result;
    std::vector<std::unique_ptr<MaintenanceJobRunner>> _runners;

    BlockableMaintenanceJobTest();
    ~BlockableMaintenanceJobTest() override;
    void TearDown() override;
    void add_job(std::shared_ptr<IMaintenanceJob> job);
    void stop_jobs();
    void start_jobs(bool with_token);
};

BlockableMaintenanceJobTest::BlockableMaintenanceJobTest()
    : ::testing::Test(),
      _executor(1),
      _token_source(std::make_shared<MaintenanceJobTokenSource>()),
      _job_result(std::make_shared<JobResult>()),
      _runners()
{

}

BlockableMaintenanceJobTest::~BlockableMaintenanceJobTest() = default;

void
BlockableMaintenanceJobTest::TearDown()
{
    stop_jobs();
}

void
BlockableMaintenanceJobTest::add_job(std::shared_ptr<IMaintenanceJob> job)
{
    _runners.emplace_back(std::make_unique<MaintenanceJobRunner>(_executor, std::make_unique<JobProxy>(std::move(job))));
    _runners.back()->run();
}

void
BlockableMaintenanceJobTest::stop_jobs()
{
    for (auto& runner : _runners) {
        runner->stop();
    }
    _executor.sync();
}

void
BlockableMaintenanceJobTest::start_jobs(bool with_token)
{
    auto token_source = _token_source;
    if (!with_token) {
        token_source.reset();
    }
    add_job(std::make_shared<MyJob>("job1", token_source, 0, _job_result));
    add_job(std::make_shared<MyJob>("job2", token_source, 1, _job_result));
    add_job(std::make_shared<MyJob>("job3", token_source, 2, _job_result));
    _job_result->set_ready();
    _job_result->wait_done();
}

TEST_F(BlockableMaintenanceJobTest, token_released_when_job_is_destroyed)
{
    auto job1 = std::make_shared<MyJob>("job1", _token_source, 0, _job_result);
    auto runner1 = std::make_unique<MyJobRunner>(std::move(job1));
    EXPECT_FALSE(runner1->get_job().isBlocked());
    auto job2 = std::make_shared<MyJob>("job2", _token_source, 1, _job_result);
    auto runner2 = std::make_unique<MyJobRunner>(std::move(job2));
    EXPECT_FALSE(runner2->get_job().isBlocked());
    _job_result->set_ready();
    runner1->run_once(); // job1 gets token
    EXPECT_FALSE(runner1->get_job().isBlocked());
    EXPECT_TRUE(runner1->is_pending());
    runner2->run_once(); // job2 fails to get token
    EXPECT_TRUE(runner2->get_job().isBlocked());
    EXPECT_FALSE(runner2->is_pending());
    runner1.reset(); // destroy job1, job2 gets token
    EXPECT_FALSE(runner2->get_job().isBlocked());
    EXPECT_TRUE(runner2->is_pending());
}

TEST_F(BlockableMaintenanceJobTest, round_robin_without_token)
{
    start_jobs(false);
    EXPECT_EQ(expected_result(3, 1, work_log_size), _job_result->get_work_log());
}

TEST_F(BlockableMaintenanceJobTest, sticky_with_token)
{
    start_jobs(true);
    EXPECT_EQ(expected_result(3, 3, work_log_size), _job_result->get_work_log());
}
