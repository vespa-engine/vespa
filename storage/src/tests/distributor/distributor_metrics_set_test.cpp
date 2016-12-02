// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace distributor {

// TODO name this PersistenceMetricsSetTest instead?
struct DistributorMetricsSetTest : CppUnit::TestFixture {
    void successful_return_codes_are_counted_as_ok();
    void wrong_distribution_failure_is_counted();
    void timeout_failure_is_counted();
    // Note for these tests: busy, connection failures et al are sets of
    // failure codes and not just a single code. We only test certain members
    // of these sets here. See api::ReturnCode implementation for an exhaustive
    // list.
    void busy_failure_is_counted();
    void connection_failure_is_counted();
    void non_special_cased_failure_codes_are_catchall_counted();

    CPPUNIT_TEST_SUITE(DistributorMetricsSetTest);
    CPPUNIT_TEST(successful_return_codes_are_counted_as_ok);
    CPPUNIT_TEST(wrong_distribution_failure_is_counted);
    CPPUNIT_TEST(timeout_failure_is_counted);
    CPPUNIT_TEST(busy_failure_is_counted);
    CPPUNIT_TEST(connection_failure_is_counted);
    CPPUNIT_TEST(non_special_cased_failure_codes_are_catchall_counted);
    CPPUNIT_TEST_SUITE_END();

    void assert_failure_is_counted(PersistenceOperationMetricSet& metrics,
                                   api::ReturnCode::Result failure_code,
                                   const metrics::LongCountMetric& checked)
    {
        metrics.updateFromResult(api::ReturnCode(failure_code));
        CPPUNIT_ASSERT_EQUAL(int64_t(1), checked.getLongValue("count"));
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.ok.getLongValue("count"));
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(DistributorMetricsSetTest);

void DistributorMetricsSetTest::successful_return_codes_are_counted_as_ok() {
    PersistenceOperationMetricSet metrics("foo");
    metrics.updateFromResult(api::ReturnCode());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), metrics.ok.getLongValue("count"));
}

void DistributorMetricsSetTest::wrong_distribution_failure_is_counted() {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::WRONG_DISTRIBUTION,
                              metrics.failures.wrongdistributor);
}

void DistributorMetricsSetTest::timeout_failure_is_counted() {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::TIMEOUT,
                              metrics.failures.timeout);
}

void DistributorMetricsSetTest::busy_failure_is_counted() {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::BUSY,
                              metrics.failures.busy);
}

void DistributorMetricsSetTest::connection_failure_is_counted() {
    PersistenceOperationMetricSet metrics("foo");
    // This is dirty enum value coercion, but this is how "parent protocol"
    // error codes are handled already.
    api::ReturnCode::Result error_code(static_cast<api::ReturnCode::Result>(
            mbus::ErrorCode::CONNECTION_ERROR));
    assert_failure_is_counted(metrics, error_code,
                              metrics.failures.notconnected);
}

void DistributorMetricsSetTest::non_special_cased_failure_codes_are_catchall_counted() {
    PersistenceOperationMetricSet metrics("foo");
    assert_failure_is_counted(metrics, api::ReturnCode::REJECTED,
                              metrics.failures.storagefailure);
}

}
}
