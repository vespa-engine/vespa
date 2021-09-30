// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-stor-distribution.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::lib {

/**
 * Class is just a wrapper for distribution, so little needs to be tested. Just
 * that:
 *
 *   - get ideal nodes calls gets propagated correctly.
 *   - Changes in distribution/cluster state is picked up.
 */

TEST(IdealNodeCalculatorImplTest, test_normal_usage)
{
    ClusterState state("storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl impl;
    IdealNodeCalculatorConfigurable& configurable(impl);
    IdealNodeCalculator& calc(impl);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::string expected("[storage.8, storage.9, storage.6]");
    EXPECT_EQ(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());
}

}
