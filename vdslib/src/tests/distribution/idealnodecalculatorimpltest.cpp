// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace lib {

struct IdealNodeCalculatorImplTest : public CppUnit::TestFixture {

    void testNormalUsage();

    CPPUNIT_TEST_SUITE(IdealNodeCalculatorImplTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(IdealNodeCalculatorImplTest);

/**
 * Class is just a wrapper for distribution, so little needs to be tested. Just
 * that:
 *
 *   - get ideal nodes calls gets propagated correctly.
 *   - Changes in distribution/cluster state is picked up.
 */

void
IdealNodeCalculatorImplTest::testNormalUsage()
{
    ClusterState state("storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl impl;
    IdealNodeCalculatorConfigurable& configurable(impl);
    IdealNodeCalculator& calc(impl);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::string expected("[storage.8, storage.9, storage.6]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());
}

} // lib
} // storage
