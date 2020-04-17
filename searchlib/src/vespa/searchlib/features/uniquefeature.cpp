// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "uniquefeature.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

namespace {

/**
 * Implements the executor for combining lid and distribution key to form a globally unique value.
 */
class UniqueLidAndDistributionKeyExecutor : public fef::FeatureExecutor {
private:
    uint32_t _distributionKey;

public:
    UniqueLidAndDistributionKeyExecutor(uint32_t distributionKey)
        : _distributionKey(distributionKey)
    {
        assert( _distributionKey < 0x10000);
    }

    void execute(uint32_t docId) override {
        outputs().set_number(0, (uint64_t(docId) << 16u) | _distributionKey);
    }
};

}

UniqueBlueprint::UniqueBlueprint() :
    Blueprint("unique"),
    _distributionKey(0)
{
}

void
UniqueBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
UniqueBlueprint::setup(const IIndexEnvironment & env,
                        const ParameterList & )
{
    _distributionKey = env.getDistributionKey();
    describeOutput("out", "Returns (lid << 16) | distributionKey");
    return true;
}

Blueprint::UP
UniqueBlueprint::createInstance() const
{
    return std::make_unique<UniqueBlueprint>();
}

FeatureExecutor &
UniqueBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<UniqueLidAndDistributionKeyExecutor>(_distributionKey);
}

}
