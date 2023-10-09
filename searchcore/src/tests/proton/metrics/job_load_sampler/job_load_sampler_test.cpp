// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("job_load_sampler_test");

#include <vespa/searchcore/proton/metrics/job_load_sampler.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <chrono>

using namespace proton;

constexpr double EPS = 0.000001;

using time_point = std::chrono::time_point<std::chrono::steady_clock>;
using std::chrono::duration;
using std::chrono::duration_cast;
using std::chrono::steady_clock;

time_point fakeTime(double now)
{
    return time_point(duration_cast<steady_clock::duration>(duration<double>(now)));
}

struct Fixture
{
    JobLoadSampler _sampler;
    Fixture()
        : _sampler(fakeTime(10))
    {
    }
    Fixture &start(double now) {
        _sampler.startJob(fakeTime(now));
        return *this;
    }
    Fixture &end(double now) {
        _sampler.endJob(fakeTime(now));
        return *this;
    }
    double sample(double now) {
        return _sampler.sampleLoad(fakeTime(now));
    }
};

TEST_F("require that empty sampler gives 0 load", Fixture)
{
    EXPECT_APPROX(0.0, f.sample(11), EPS);
}

TEST_F("require that empty time interval gives 0 load", Fixture)
{
    EXPECT_APPROX(0.0, f.sample(10), EPS);
}

TEST_F("require that job that starts and ends in interval gets correct load", Fixture)
{
    f.start(12).end(17);
    EXPECT_APPROX(0.5, f.sample(20), EPS);
    EXPECT_APPROX(0.0, f.sample(21), EPS);
}

TEST_F("require that job that starts in interval gets correct load", Fixture)
{
    f.start(12);
    EXPECT_APPROX(0.8, f.sample(20), EPS);
    EXPECT_APPROX(1.0, f.sample(21), EPS);
}

TEST_F("require that job that ends in interval gets correct load", Fixture)
{
    f.start(12).sample(20);
    f.end(27);
    EXPECT_APPROX(0.7, f.sample(30), EPS);
    EXPECT_APPROX(0.0, f.sample(31), EPS);
}

TEST_F("require that job that runs in complete interval gets correct load", Fixture)
{
    f.start(12).sample(20);
    EXPECT_APPROX(1.0, f.sample(30), EPS);
    EXPECT_APPROX(1.0, f.sample(31), EPS);
}

TEST_F("require that multiple jobs that starts and ends in interval gets correct load", Fixture)
{
    // job1: 12->17: 0.5
    // job2: 14->16: 0.2
    f.start(12).start(14).end(16).end(17);
    EXPECT_APPROX(0.7, f.sample(20), EPS);
}

TEST_F("require that multiple jobs that starts and ends in several intervals gets correct load", Fixture)
{
    // job1: 12->22
    // job2: 14->34
    // job3: 25->45
    f.start(12).start(14);
    EXPECT_APPROX(1.4, f.sample(20), EPS);
    f.end(22).start(25);
    EXPECT_APPROX(1.7, f.sample(30), EPS);
    f.end(34);
    EXPECT_APPROX(1.4, f.sample(40), EPS);
    f.end(45);
    EXPECT_APPROX(0.5, f.sample(50), EPS);
}

TEST_MAIN() { TEST_RUN_ALL(); }
