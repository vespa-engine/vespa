// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchcommon/attribute/attributecontent.h>

namespace search::features {

/**
 * Implements the executor for the documentage feature outputting the
 * difference between document time (stored in an attribute) and current
 * system time
 **/
class AgeExecutor : public fef::FeatureExecutor {
private:
    const attribute::IAttributeVector *_attribute;
    attribute::IntegerContent _buf;
public:
    AgeExecutor(const attribute::IAttributeVector *attribute);
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for 'documentage' feature. It uses the 'now' feature
 * to get current time and reads document time from a specified attribute
 */
class AgeBlueprint : public fef::Blueprint {
private:
    vespalib::string _attribute;

public:
    AgeBlueprint() : fef::Blueprint("age") { }
    ~AgeBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
};

}
