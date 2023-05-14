// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "agefeature.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/util/stash.h>

using search::attribute::IAttributeVector;

namespace search {

using FNB = fef::FeatureNameBuilder;

namespace features {

AgeExecutor::AgeExecutor(const IAttributeVector *attribute) :
    search::fef::FeatureExecutor(),
    _attribute(attribute),
    _buf()
{
    if (_attribute != nullptr) {
        _buf.allocate(attribute->getMaxValueCount());
    }
}

AgeBlueprint::~AgeBlueprint() = default;

void
AgeExecutor::execute(uint32_t docId)
{
    feature_t age = 10000000000.0;
    if (_attribute != nullptr) {
        _buf.fill(*_attribute, docId);
        int64_t docTime = _buf[0];
        feature_t currTime = inputs().get_number(0);
        age = currTime - docTime;
        if (age < 0) {
            age = 0;
        }
    }
    outputs().set_number(0, age);
}

void
AgeBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
AgeBlueprint::setup(const search::fef::IIndexEnvironment&,
                    const search::fef::ParameterList &params)
{
    _attribute = params[0].getValue();
    defineInput("now");

    describeOutput("out", "The age of the document, in seconds.");
    return true;
}

search::fef::Blueprint::UP
AgeBlueprint::createInstance() const
{
    return std::make_unique<AgeBlueprint>();
}

search::fef::FeatureExecutor &
AgeBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    // Get docdate attribute vector
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(_attribute);
    return stash.create<AgeExecutor>(attribute);
}

fef::ParameterDescriptions
AgeBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().attribute(fef::ParameterDataTypeSet::normalTypeSet(), fef::ParameterCollection::ANY);
}

}
}
