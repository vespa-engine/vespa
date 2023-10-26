// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the blueprint for the unique rank feature.
 *
 * This will compute a globally unique id based on lid and distribution key.
 * Cheap way to get deterministic ordering
 * It will change if documents change lid.
 */

class GlobalSequenceBlueprint : public fef::Blueprint
{
private:
    uint32_t  _distributionKey;
public:
    GlobalSequenceBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    static uint64_t globalSequence(uint32_t docId, uint32_t distrKey) {
        return (1ul << 48) - ((uint64_t(docId) << 16)| distrKey);
    }
};

}
