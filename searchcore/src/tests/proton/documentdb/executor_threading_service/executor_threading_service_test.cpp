// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

using namespace proton;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using SharedFieldWriterExecutor = ThreadingServiceConfig::SharedFieldWriterExecutor;


SequencedTaskExecutor*
to_concrete_type(ISequencedTaskExecutor& exec)
{
    return dynamic_cast<SequencedTaskExecutor*>(&exec);
}

class ExecutorThreadingServiceTest : public ::testing::Test {
public:
    vespalib::ThreadStackExecutor shared_executor;
    std::unique_ptr<ExecutorThreadingService> service;
    ExecutorThreadingServiceTest()
        : shared_executor(1, 1000),
          service()
    {
    }
    void setup(uint32_t indexing_threads, SharedFieldWriterExecutor shared_field_writer) {
        service = std::make_unique<ExecutorThreadingService>(shared_executor,
                                                             ThreadingServiceConfig::make(indexing_threads, shared_field_writer));
    }
    SequencedTaskExecutor* index_inverter() {
        return to_concrete_type(service->indexFieldInverter());
    }
    SequencedTaskExecutor* index_writer() {
        return to_concrete_type(service->indexFieldWriter());
    }
    SequencedTaskExecutor* attribute_writer() {
        return to_concrete_type(service->attributeFieldWriter());
    }
};

void
assert_executor(SequencedTaskExecutor* exec, uint32_t exp_executors, uint32_t exp_task_limit)
{
    EXPECT_EQ(exp_executors, exec->getNumExecutors());
    EXPECT_EQ(exp_task_limit, exec->first_executor()->getTaskLimit());
}

TEST_F(ExecutorThreadingServiceTest, no_shared_field_writer_executor)
{
    setup(4, SharedFieldWriterExecutor::NONE);
    EXPECT_NE(index_inverter(), index_writer());
    EXPECT_NE(index_writer(), attribute_writer());
    assert_executor(index_inverter(), 4, 100);
    assert_executor(index_writer(), 4, 100);
    assert_executor(attribute_writer(), 4, 100);
}

TEST_F(ExecutorThreadingServiceTest, shared_executor_for_index_field_writers)
{
    setup(4, SharedFieldWriterExecutor::INDEX);
    EXPECT_EQ(index_inverter(), index_writer());
    EXPECT_NE(index_inverter(), attribute_writer());
    assert_executor(index_inverter(), 8, 100);
    assert_executor(attribute_writer(), 4, 100);
}

TEST_F(ExecutorThreadingServiceTest, shared_executor_for_index_and_attribute_field_writers)
{
    setup(4, SharedFieldWriterExecutor::INDEX_AND_ATTRIBUTE);
    EXPECT_EQ(index_inverter(), index_writer());
    EXPECT_EQ(index_inverter(), attribute_writer());
    assert_executor(index_inverter(), 12, 100);
}

GTEST_MAIN_RUN_ALL_TESTS()

