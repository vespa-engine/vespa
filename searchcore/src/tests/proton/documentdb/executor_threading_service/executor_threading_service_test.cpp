// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

using namespace proton;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using SharedFieldWriterExecutor = ThreadingServiceConfig::SharedFieldWriterExecutor;

VESPA_THREAD_STACK_TAG(my_field_writer_executor)

SequencedTaskExecutor*
to_concrete_type(ISequencedTaskExecutor& exec)
{
    return dynamic_cast<SequencedTaskExecutor*>(&exec);
}

class ExecutorThreadingServiceTest : public ::testing::Test {
public:
    TransportAndExecutor _transport;
    std::unique_ptr<ISequencedTaskExecutor> field_writer_executor;
    std::unique_ptr<ExecutorThreadingService> service;
    ExecutorThreadingServiceTest()
        : _transport(1),
          field_writer_executor(SequencedTaskExecutor::create(my_field_writer_executor, 3, 200)),
          service()
    {
    }
    void setup(uint32_t indexing_threads, SharedFieldWriterExecutor shared_field_writer) {
        service = std::make_unique<ExecutorThreadingService>(_transport.shared(),
                                                             _transport.transport(),
                                                             _transport.clock(),
                                                             field_writer_executor.get(),
                                                             nullptr,
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
    SequencedTaskExecutor* field_writer() {
        return to_concrete_type(*field_writer_executor);
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

TEST_F(ExecutorThreadingServiceTest, shared_field_writer_specified_from_the_outside)
{
    setup(4, SharedFieldWriterExecutor::DOCUMENT_DB);
    EXPECT_EQ(field_writer(), index_inverter());
    EXPECT_EQ(field_writer(), index_writer());
    EXPECT_EQ(field_writer(), attribute_writer());
    assert_executor(field_writer(), 3, 200);
}

TEST_F(ExecutorThreadingServiceTest, tasks_limits_can_be_updated)
{
    setup(4, SharedFieldWriterExecutor::NONE);
    service->set_task_limits(5, 7, 11);
    EXPECT_EQ(5, service->master_task_limit());
    EXPECT_EQ(7, service->index().getTaskLimit());
    EXPECT_EQ(11, service->summary().getTaskLimit());
    EXPECT_EQ(7, index_inverter()->first_executor()->getTaskLimit());
    EXPECT_EQ(7, index_writer()->first_executor()->getTaskLimit());
    EXPECT_EQ(7, attribute_writer()->first_executor()->getTaskLimit());
}

GTEST_MAIN_RUN_ALL_TESTS()

