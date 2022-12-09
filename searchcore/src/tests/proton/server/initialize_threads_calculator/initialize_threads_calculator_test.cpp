// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/initialize_threads_calculator.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace proton;
using vespalib::ThreadStackExecutor;

class InitializeThreadsCalculatorTest : public search::test::DirectoryHandler, public testing::Test {
public:
    InitializeThreadsCalculatorTest() : DirectoryHandler("tmp") {}
};

void
expect_successful_init(uint32_t exp_threads)
{
    InitializeThreadsCalculator i("tmp", 9);
    EXPECT_EQ(exp_threads, i.num_threads());
    EXPECT_TRUE(i.threads().get() != nullptr);
    EXPECT_EQ(exp_threads, dynamic_cast<const ThreadStackExecutor&>(*i.threads()).getNumThreads());
    i.init_done();
    EXPECT_TRUE(i.threads().get() == nullptr);
}

void
expect_aborted_init(uint32_t exp_threads, uint32_t cfg_threads = 9)
{
    InitializeThreadsCalculator i("tmp", cfg_threads);
    EXPECT_EQ(exp_threads, i.num_threads());
    EXPECT_TRUE(i.threads().get() != nullptr);
    EXPECT_EQ(exp_threads, dynamic_cast<const ThreadStackExecutor&>(*i.threads()).getNumThreads());
}

TEST_F(InitializeThreadsCalculatorTest, initialize_threads_unchanged_when_init_is_successful)
{
    expect_successful_init(9);
    // The previous init was successful,
    // so we still use the configured number of initialize threads.
    expect_successful_init(9);
}

TEST_F(InitializeThreadsCalculatorTest, initialize_threads_cut_in_half_when_init_is_aborted)
{
    expect_aborted_init(9);
    expect_aborted_init(4);
    expect_aborted_init(2);
    expect_aborted_init(1);
    expect_aborted_init(1);
}

TEST_F(InitializeThreadsCalculatorTest, zero_initialize_threads_is_special)
{
    {
        InitializeThreadsCalculator i("tmp", 0);
        EXPECT_EQ(0, i.num_threads());
        EXPECT_TRUE(i.threads().get() == nullptr);
    }
    expect_aborted_init(1, 0);
    expect_aborted_init(1, 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
