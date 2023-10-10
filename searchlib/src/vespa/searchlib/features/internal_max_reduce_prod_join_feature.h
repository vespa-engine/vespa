// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Feature for the specific replacement of the expression:
 *
 *      reduce(
 *          join(
 *              tensorFromLabels(attribute(A)),
 *              tensorFromWeightedset(query(Q)),
 *              f(x,y)(x*y)
 *          ),
 *          max
 *      )
 *
 * where A is an array attribute of int or long type and Q is a query that parses as
 * a weighted set. This expression is replaced with this feature to avoid incurring
 * the cost of creating temporary tensors.
 */
class InternalMaxReduceProdJoinBlueprint : public fef::Blueprint {
private:
    vespalib::string _attribute;
    vespalib::string _queryVector;
    vespalib::string _attrKey;
    vespalib::string _queryVectorKey;

public:
    InternalMaxReduceProdJoinBlueprint();
    ~InternalMaxReduceProdJoinBlueprint() override;

    fef::ParameterDescriptions getDescriptions() const override;
    fef::Blueprint::UP createInstance() const override;
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    void prepareSharedState(const fef::IQueryEnvironment & queryEnv, fef::IObjectStore & objectStore) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;

};

}
