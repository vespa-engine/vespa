// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/eval/eval/value_type.h>

namespace search::features {

/**
 * Implements the blueprint for the attribute executor.
 *
 * An executor of this outputs number(s) if used with regular attributes
 * or a tensor value if used with tensor attributes.
 */
class AttributeBlueprint : public fef::Blueprint {
private:
    vespalib::string _attrName; // the name of the attribute vector
    vespalib::string _extra;    // the index or key
    vespalib::eval::ValueType _tensorType;

public:
    AttributeBlueprint();
    ~AttributeBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;

    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    fef::ParameterDescriptions getDescriptions() const  override;
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
};

}
