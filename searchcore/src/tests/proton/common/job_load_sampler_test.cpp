// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/metrics/job_load_sampler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <chrono>

using namespace proton;

inline namespace job_load_sampler_test {
constexpr double EPS = 0.000001;

using time_point = std::chrono::time_point<std::chrono::steady_clock>;
using std::chrono::duration;
using std::chrono::duration_cast;
using std::chrono::steady_clock;

time_point fakeTime(double now)
{
    return time_point(duration_cast<steady_clock::duration>(duration<double>(now)));
}

}

class JobLoadSamplerTest : public ::testing::Test
{
    JobLoadSampler _sampler;
protected:
    JobLoadSamplerTest();
    ~JobLoadSamplerTest() override;
public:
    JobLoadSamplerTest& start(double now) {
        _sampler.startJob(fakeTime(now));
        return *this;
    }
    JobLoadSamplerTest& end(double now) {
        _sampler.endJob(fakeTime(now));
        return *this;
    }
    double sample(double now) {
        return _sampler.sampleLoad(fakeTime(now));
    }
};

JobLoadSamplerTest::JobLoadSamplerTest()
    : ::testing::Test(),
      _sampler(fakeTime(10))
{
}

JobLoadSamplerTest::~JobLoadSamplerTest() = default;

TEST_F(JobLoadSamplerTest, require_that_empty_sampler_gives_0_load)
{
    EXPECT_NEAR(0.0, sample(11), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_empty_time_interval_gives_0_load)
{
    EXPECT_NEAR(0.0, sample(10), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_job_that_starts_and_ends_in_interval_gets_correct_load)
{
    start(12).end(17);
    EXPECT_NEAR(0.5, sample(20), EPS);
    EXPECT_NEAR(0.0, sample(21), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_job_that_starts_in_interval_gets_correct_load)
{
    start(12);
    EXPECT_NEAR(0.8, sample(20), EPS);
    EXPECT_NEAR(1.0, sample(21), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_job_that_ends_in_interval_gets_correct_load)
{
    start(12).sample(20);
    end(27);
    EXPECT_NEAR(0.7, sample(30), EPS);
    EXPECT_NEAR(0.0, sample(31), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_job_that_runs_in_complete_interval_gets_correct_load)
{
    start(12).sample(20);
    EXPECT_NEAR(1.0, sample(30), EPS);
    EXPECT_NEAR(1.0, sample(31), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_multiple_jobs_that_starts_and_ends_in_interval_gets_correct_load)
{
    // job1: 12->17: 0.5
    // job2: 14->16: 0.2
    start(12).start(14).end(16).end(17);
    EXPECT_NEAR(0.7, sample(20), EPS);
}

TEST_F(JobLoadSamplerTest, require_that_multiple_jobs_that_starts_and_ends_in_several_intervals_gets_correct_load)
{
    // job1: 12->22
    // job2: 14->34
    // job3: 25->45
    start(12).start(14);
    EXPECT_NEAR(1.4, sample(20), EPS);
    end(22).start(25);
    EXPECT_NEAR(1.7, sample(30), EPS);
    end(34);
    EXPECT_NEAR(1.4, sample(40), EPS);
    end(45);
    EXPECT_NEAR(0.5, sample(50), EPS);
}
