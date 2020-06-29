// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_sequence_feature.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

namespace {

/**
 * Implements the executor for combining lid and distribution key to form a globally unique value.
 */
class GlobalSequenceExecutor : public fef::FeatureExecutor {
private:
    uint32_t _distributionKey;

public:
    GlobalSequenceExecutor(uint32_t distributionKey)
        : _distributionKey(distributionKey)
    {
    }

    void execute(uint32_t docId) override {
        outputs().set_number(0, ((1ul << 48u) - ((uint64_t(docId) << 16u) | _distributionKey)));
    }
};

}

GlobalSequenceBlueprint::GlobalSequenceBlueprint() :
    Blueprint("globalsequence"),
    _distributionKey(0)
{
}

void
GlobalSequenceBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
GlobalSequenceBlueprint::setup(const IIndexEnvironment & env, const ParameterList & )
{
    _distributionKey = env.getDistributionKey();
    assert( _distributionKey < 0x80000);
    describeOutput("out", "Returns (1 << 48) - ((lid << 16) | distributionKey)");
    return true;
}

Blueprint::UP
GlobalSequenceBlueprint::createInstance() const
{
    return std::make_unique<GlobalSequenceBlueprint>();
}

FeatureExecutor &
GlobalSequenceBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<GlobalSequenceExecutor>(_distributionKey);
}

}
