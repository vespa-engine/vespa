// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

struct PersistenceMetricsSetTest : Test {
    void assert_failure_is_counted(PersistenceOperationMetricSet& metrics,
                                   api::ReturnCode::Result failure_code,
                                   const metrics::LongCountMetric& checked)
    {
        metrics.updateFromResult(api::ReturnCode(failure_code));
        EXPECT_EQ(1, checked.getLongValue("count"));
        EXPECT_EQ(0, metrics.ok.getLongValue("count"));
    }
};

TEST_F(PersistenceMetricsSetTest, successful_return_codes_are_counted_as_ok) {
    PersistenceOperationMetricSet metrics("foo");
    metrics.updateFromResult(api::ReturnCode());
    EXPECT_EQ(1, metrics.ok.getLongValue("count"));
}

TEST_F(PersistenceMetricsSetTest, wrong_distribution_failure_is_counted) {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::WRONG_DISTRIBUTION,
                              metrics.failures.wrongdistributor);
}

TEST_F(PersistenceMetricsSetTest, timeout_failure_is_counted) {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::TIMEOUT,
                              metrics.failures.timeout);
}

// Note for these tests: busy, connection failures et al are sets of
// failure codes and not just a single code. We only test certain members
// of these sets here. See api::ReturnCode implementation for an exhaustive
// list.
TEST_F(PersistenceMetricsSetTest, busy_failure_is_counted) {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::BUSY,
                              metrics.failures.busy);
}

TEST_F(PersistenceMetricsSetTest, connection_failure_is_counted) {
    PersistenceOperationMetricSet metrics("foo");
    // This is dirty enum value coercion, but this is how "parent protocol"
    // error codes are handled already.
    api::ReturnCode::Result error_code(static_cast<api::ReturnCode::Result>(
            mbus::ErrorCode::CONNECTION_ERROR));
    assert_failure_is_counted(metrics, error_code,
                              metrics.failures.notconnected);
}

TEST_F(PersistenceMetricsSetTest, inconsistent_bucket_is_counted) {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::BUCKET_NOT_FOUND,
                              metrics.failures.inconsistent_bucket);
}

TEST_F(PersistenceMetricsSetTest, non_special_cased_failure_codes_are_catchall_counted) {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::REJECTED,
                              metrics.failures.storagefailure);
}

}
