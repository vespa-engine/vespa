// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP("generation_handler_stress_test");

using vespalib::Executor;
using vespalib::GenerationHandler;
using vespalib::makeLambdaTask;
using vespalib::ThreadStackExecutor;

namespace {

bool smoke_test = false;
const vespalib::string smoke_test_option = "--smoke-test";

}

class ReadStopper {
    std::atomic<bool> &_stop_read;
public:
    ReadStopper(std::atomic<bool>& stop_read)
        : _stop_read(stop_read)
    {
    }
    ~ReadStopper() {
        _stop_read = true;
    }
};

struct WorkContext
{
    std::atomic<uint64_t> _generation;

    WorkContext() noexcept
        : _generation(0)
    {
    }
};

struct IndirectContext {
    std::atomic<uint64_t *> _value_ptr;
    char _pad[256];
    static constexpr size_t values_size = 65536;
    uint64_t _values[values_size];

    IndirectContext() noexcept;
    uint64_t* calc_value_ptr(uint64_t idx) { return &_values[(idx & (values_size - 1))]; }
};

IndirectContext::IndirectContext() noexcept
    : _value_ptr(nullptr),
      _pad(),
      _values()
{
    _value_ptr = &_values[0];
}

class Fixture : public ::testing::Test {
protected:
    GenerationHandler _generationHandler;
    uint32_t _readThreads;
    ThreadStackExecutor _writer; // 1 write thread
    std::unique_ptr<ThreadStackExecutor> _readers; // multiple reader threads
    std::atomic<long> _readSeed;
    std::atomic<long> _doneWriteWork;
    std::atomic<long> _doneReadWork;
    std::atomic<bool> _stopRead;
    bool _reportWork;

    Fixture();
    ~Fixture();

    void set_read_threads(uint32_t read_threads);

    uint32_t getReadThreads() const { return _readThreads; }
    void stressTest(uint32_t writeCnt);
    void stress_test_indirect(uint64_t write_cnt);
public:
    void readWork(const WorkContext &context);
    void writeWork(uint32_t cnt, WorkContext &context);
    void read_indirect_work(const IndirectContext& context);
    void write_indirect_work(uint64_t cnt, IndirectContext& context);
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
      _writer(1),
      _readers(),
      _doneWriteWork(0),
      _doneReadWork(0),
      _stopRead(false),
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
    _readers = std::make_unique<ThreadStackExecutor>(read_threads);
}

void
Fixture::readWork(const WorkContext &context)
{
    uint32_t i;
    uint32_t cnt = std::numeric_limits<uint32_t>::max();

    for (i = 0; i < cnt && !_stopRead.load(); ++i) {
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
    ReadStopper read_stopper(_stopRead);
    for (uint32_t i = 0; i < cnt; ++i) {
        context._generation.store(_generationHandler.getNextGeneration(), std::memory_order_relaxed);
        _generationHandler.incGeneration();
    }
    _doneWriteWork += cnt;
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
    _writer.sync();
    _readers->sync();
}

void
Fixture::read_indirect_work(const IndirectContext& context)
{
    uint64_t i;
    uint64_t cnt = std::numeric_limits<uint32_t>::max();
    uint64_t old_value = 0;
    for (i = 0; i < cnt && !_stopRead.load(); ++i) {
        auto guard = _generationHandler.takeGuard();
        // Data referenced by pointer is protected by guard
        auto v_ptr = context._value_ptr.load(std::memory_order_acquire);
        EXPECT_GE(*v_ptr, old_value);
        old_value = *v_ptr;
    }
    _doneReadWork += i;
    LOG(info, "done %" PRIu64 " read work", i);
}


void
Fixture::write_indirect_work(uint64_t cnt, IndirectContext& context)
{
    ReadStopper read_stopper(_stopRead);
    uint32_t sleep_cnt = 0;
    ASSERT_EQ(0, _generationHandler.getCurrentGeneration());
    auto oldest_gen = _generationHandler.get_oldest_used_generation();
    for (uint64_t i = 0; i < cnt; ++i) {
        auto gen = _generationHandler.getCurrentGeneration();
        // Hold data for gen, write new data for next_gen
        auto next_gen = gen + 1;
        auto *v_ptr = context.calc_value_ptr(next_gen);
        ASSERT_EQ(0u, *v_ptr);
        *v_ptr = next_gen;
        context._value_ptr.store(v_ptr, std::memory_order_release);
        _generationHandler.incGeneration();
        auto first_used_gen = _generationHandler.get_oldest_used_generation();
        while (oldest_gen < first_used_gen) {
            // Clear data that readers should no longer have access to.
            *context.calc_value_ptr(oldest_gen) = 0;
            ++oldest_gen;
        }
        while ((next_gen - first_used_gen) >= context.values_size - 2) {
            // Sleep if writer gets too much ahead of readers.
            std::this_thread::sleep_for(1ms);
            ++sleep_cnt;
            _generationHandler.update_oldest_used_generation();
            first_used_gen = _generationHandler.get_oldest_used_generation();
        }
    }
    _doneWriteWork += cnt;
    LOG(info, "done %" PRIu64 " write work, %u sleeps", cnt, sleep_cnt);
}

void
Fixture::stress_test_indirect(uint64_t write_cnt)
{
    _reportWork = true;
    uint32_t read_threads = getReadThreads();
    LOG(info, "starting stress test indirect, 1 write thread, %u read threads, %" PRIu64 " writes", read_threads, write_cnt);
    auto context = std::make_shared<IndirectContext>();
    _writer.execute(makeLambdaTask([this, context, write_cnt]() { write_indirect_work(write_cnt, *context); }));
    for (uint32_t i = 0; i < read_threads; ++i) {
        _readers->execute(makeLambdaTask([this, context]() { read_indirect_work(*context); }));
    }
    _writer.sync();
    _readers->sync();
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

TEST_F(GenerationHandlerStressTest, stress_test_indirect_2_readers)
{
    set_read_threads(2);
    stress_test_indirect(smoke_test ? 10000 : 1000000);
}

TEST_F(GenerationHandlerStressTest, stress_test_indirect_4_readers)
{
    set_read_threads(4);
    stress_test_indirect(smoke_test ? 10000 : 1000000);
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
