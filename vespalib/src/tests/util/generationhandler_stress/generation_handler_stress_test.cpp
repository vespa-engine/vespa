// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("generation_handler_stress_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>

using vespalib::Executor;
using vespalib::GenerationHandler;
using vespalib::ThreadStackExecutor;

namespace {

bool smoke_test = false;
const vespalib::string smoke_test_option = "--smoke-test";

}

struct WorkContext
{
    std::atomic<uint64_t> _generation;

    WorkContext() noexcept
        : _generation(0)
    {
    }
};

class Fixture : public ::testing::Test {
protected:
    GenerationHandler _generationHandler;
    uint32_t _readThreads;
    ThreadStackExecutor _writer; // 1 write thread
    std::unique_ptr<ThreadStackExecutor> _readers; // multiple reader threads
    std::atomic<long> _readSeed;
    std::atomic<long> _doneWriteWork;
    std::atomic<long> _doneReadWork;
    std::atomic<int> _stopRead;
    bool _reportWork;

    Fixture();
    ~Fixture();

    void set_read_threads(uint32_t read_threads);

    uint32_t getReadThreads() const { return _readThreads; }
    void stressTest(uint32_t writeCnt);
public:
    void readWork(const WorkContext &context);
    void writeWork(uint32_t cnt, WorkContext &context);
private:
    Fixture(const Fixture &index) = delete;
    Fixture(Fixture &&index) = delete;
    Fixture &operator=(const Fixture &index) = delete;
    Fixture &operator=(Fixture &&index) = delete;
};


Fixture::Fixture()
    : ::testing::Test(),
      _generationHandler(),
      _readThreads(1),
      _writer(1, 128_Ki),
      _readers(),
      _doneWriteWork(0),
      _doneReadWork(0),
      _stopRead(0),
      _reportWork(false)
{
    set_read_threads(1);
}


Fixture::~Fixture()
{
    if (_readers) {
        _readers->sync();
        _readers->shutdown();
    }
    _writer.sync();
    _writer.shutdown();
    if (_reportWork) {
        LOG(info,
            "readWork=%ld, writeWork=%ld",
            _doneReadWork.load(), _doneWriteWork.load());
    }
}

void
Fixture::set_read_threads(uint32_t read_threads)
{
    if (_readers) {
        _readers->sync();
        _readers->shutdown();
    }
    _readThreads = read_threads;
    _readers = std::make_unique<ThreadStackExecutor>(read_threads, 128_Ki);
}

void
Fixture::readWork(const WorkContext &context)
{
    uint32_t i;
    uint32_t cnt = std::numeric_limits<uint32_t>::max();

    for (i = 0; i < cnt && _stopRead.load() == 0; ++i) {
        auto guard = _generationHandler.takeGuard();
        auto generation = context._generation.load(std::memory_order_relaxed);
        EXPECT_GE(generation, guard.getGeneration());
    }
    _doneReadWork += i;
    LOG(info, "done %u read work", i);
}


void
Fixture::writeWork(uint32_t cnt, WorkContext &context)
{
    for (uint32_t i = 0; i < cnt; ++i) {
        context._generation.store(_generationHandler.getNextGeneration(), std::memory_order_relaxed);
        _generationHandler.incGeneration();
    }
    _doneWriteWork += cnt;
    _stopRead = 1;
    LOG(info, "done %u write work", cnt);
}

namespace
{

class ReadWorkTask : public vespalib::Executor::Task
{
    Fixture &_f;
    std::shared_ptr<WorkContext> _context;
public:
    ReadWorkTask(Fixture &f, std::shared_ptr<WorkContext> context)
        : _f(f),
          _context(context)
    {
    }
    virtual void run() override { _f.readWork(*_context); }
};

class WriteWorkTask : public vespalib::Executor::Task
{
    Fixture &_f;
    uint32_t _cnt;
    std::shared_ptr<WorkContext> _context;
public:
    WriteWorkTask(Fixture &f, uint32_t cnt,
                  std::shared_ptr<WorkContext> context)
        : _f(f),
          _cnt(cnt),
          _context(context)
    {
    }
    virtual void run() override { _f.writeWork(_cnt, *_context); }
};

}


void
Fixture::stressTest(uint32_t writeCnt)
{
    _reportWork = true;
    uint32_t readThreads = getReadThreads();
    LOG(info,
        "starting stress test, 1 write thread, %u read threads, %u writes",
        readThreads, writeCnt);
    auto context = std::make_shared<WorkContext>();
    _writer.execute(std::make_unique<WriteWorkTask>(*this, writeCnt, context));
    for (uint32_t i = 0; i < readThreads; ++i) {
        _readers->execute(std::make_unique<ReadWorkTask>(*this, context));
    }
}

using GenerationHandlerStressTest = Fixture;

TEST_F(GenerationHandlerStressTest, stress_test_2_readers)
{
    set_read_threads(2);
    stressTest(smoke_test ? 10000 : 1000000);
}

TEST_F(GenerationHandlerStressTest, stress_test_4_readers)
{
    set_read_threads(4);
    stressTest(smoke_test ? 10000 : 1000000);
}

int main(int argc, char **argv) {
    if (argc > 1 && argv[1] == smoke_test_option) {
        smoke_test = true;
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
