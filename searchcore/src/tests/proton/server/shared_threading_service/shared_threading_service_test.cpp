// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-proton.h>
#include <vespa/persistence/dummyimpl/dummy_bucket_executor.h>
#include <vespa/searchcore/proton/server/shared_threading_service.h>
#include <vespa/searchcore/proton/server/shared_threading_service_config.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;
using namespace proton;
using storage::spi::dummy::DummyBucketExecutor;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;

ProtonConfig
make_proton_config(double concurrency, uint32_t indexing_threads = 1)
{
    ProtonConfigBuilder builder;
    builder.documentdb.push_back(ProtonConfig::Documentdb());
    builder.documentdb.push_back(ProtonConfig::Documentdb());
    builder.flush.maxconcurrent = 1;

    builder.feeding.concurrency = concurrency;
    builder.indexing.tasklimit = 255;
    builder.indexing.threads = indexing_threads;
    return builder;
}

void
expect_shared_threads(uint32_t exp_threads, uint32_t cpu_cores)
{
    auto cfg = SharedThreadingServiceConfig::make(make_proton_config(0.5), HwInfo::Cpu(cpu_cores));
    EXPECT_EQ(exp_threads, cfg.shared_threads());
    EXPECT_EQ(exp_threads * 16, cfg.shared_task_limit());
}

void
expect_field_writer_threads(uint32_t exp_threads, uint32_t cpu_cores, uint32_t indexing_threads = 1)
{
    auto cfg = SharedThreadingServiceConfig::make(make_proton_config(0.5, indexing_threads), HwInfo::Cpu(cpu_cores));
    EXPECT_EQ(exp_threads, cfg.field_writer_threads());
}

TEST(SharedThreadingServiceConfigTest, shared_threads_are_derived_from_cpu_cores_and_feeding_concurrency)
{
    expect_shared_threads(2, 1);
    expect_shared_threads(2, 4);
    expect_shared_threads(3, 5);
    expect_shared_threads(3, 6);
    expect_shared_threads(4, 8);
    expect_shared_threads(5, 9);
    expect_shared_threads(5, 10);
}

TEST(SharedThreadingServiceConfigTest, field_writer_threads_are_derived_from_cpu_cores_and_feeding_concurrency)
{
    expect_field_writer_threads(3, 1);
    expect_field_writer_threads(3, 4);
    expect_field_writer_threads(3, 6);
    expect_field_writer_threads(4, 7);
    expect_field_writer_threads(4, 8);
    expect_field_writer_threads(5, 9);
}

TEST(SharedThreadingServiceConfigTest, field_writer_threads_can_be_overridden_in_proton_config)
{
    expect_field_writer_threads(4, 1, 4);
}

class SharedThreadingServiceTest : public ::testing::Test {
public:
    Transport transport;
    storage::spi::dummy::DummyBucketExecutor bucket_executor;
    std::unique_ptr<SharedThreadingService> service;
    SharedThreadingServiceTest()
        : transport(),
          bucket_executor(2),
          service()
    { }
    ~SharedThreadingServiceTest() = default;
    void setup(double concurrency, uint32_t cpu_cores) {
        service = std::make_unique<SharedThreadingService>(
                SharedThreadingServiceConfig::make(make_proton_config(concurrency), HwInfo::Cpu(cpu_cores)),
                transport.transport(), bucket_executor);
    }
    SequencedTaskExecutor* field_writer() {
        return dynamic_cast<SequencedTaskExecutor*>(&service->field_writer());
    }
};

void
assert_executor(SequencedTaskExecutor* exec, uint32_t exp_executors, uint32_t exp_task_limit)
{
    EXPECT_EQ(exp_executors, exec->getNumExecutors());
    EXPECT_EQ(exp_task_limit, exec->first_executor()->getTaskLimit());
}

TEST_F(SharedThreadingServiceTest, field_writer_can_be_shared_across_all_document_dbs)
{
    setup(0.75, 8);
    EXPECT_TRUE(field_writer());
    EXPECT_EQ(6, field_writer()->getNumExecutors());
    // This is rounded to the nearest power of 2 when using THROUGHPUT feed executor.
    EXPECT_EQ(256, field_writer()->first_executor()->getTaskLimit());
}

GTEST_MAIN_RUN_ALL_TESTS()
