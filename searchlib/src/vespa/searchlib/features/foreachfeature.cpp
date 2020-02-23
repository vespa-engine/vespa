// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "foreachfeature.h"
#include "valuefeature.h"
#include "utils.h"

#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>
#include <boost/algorithm/string/replace.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".features.foreachfeature");

using namespace search::fef;

namespace search::features {

template <typename CO, typename OP>
ForeachExecutor<CO, OP>::ForeachExecutor(const CO & condition, uint32_t numInputs) :
    FeatureExecutor(),
    _condition(condition),
    _operation(),
    _numInputs(numInputs)
{
}

template <typename CO, typename OP>
void
ForeachExecutor<CO, OP>::execute(uint32_t)
{
    _operation.reset();
    for (uint32_t i = 0; i < inputs().size(); ++i) {
        feature_t val = inputs().get_number(i);
        if (_condition.useValue(val)) {
            _operation.onValue(val);
        }
    }
    outputs().set_number(0, _operation.getResult());
}


bool
ForeachBlueprint::decideDimension(const vespalib::string & param)
{
    if (param == "terms") {
        _dimension = TERMS;
    } else if (param == "fields") {
        _dimension = FIELDS;
    } else if (param == "attributes") {
        _dimension = ATTRIBUTES;
    } else {
        LOG(error, "Expected dimension parameter to be 'terms', 'fields', or 'attributes', but was '%s'",
            param.c_str());
        return false;
    }
    return true;
}

bool
ForeachBlueprint::decideCondition(const vespalib::string & condition, const vespalib::string & operation)
{
    if (condition == "true") {
        return decideOperation(TrueCondition(), operation);
    } else if (condition.size() >= 2 && condition[0] == '<') {
        return decideOperation(LessThanCondition(util::strToNum<feature_t>(condition.substr(1))), operation);
    } else if (condition.size() >= 2 && condition[0] == '>') {
        return decideOperation(GreaterThanCondition(util::strToNum<feature_t>(condition.substr(1))), operation);
    } else {
        LOG(error, "Expected condition parameter to be 'true', '<a', or '>a', but was '%s'",
            condition.c_str());
        return false;
    }
}

template <typename CO>
bool
ForeachBlueprint::decideOperation(CO condition, const vespalib::string & operation)
{
    if (operation == "sum") {
        setExecutorCreator<CO, SumOperation>(condition);
    } else if (operation == "product") {
        setExecutorCreator<CO, ProductOperation>(condition);
    } else if (operation == "average") {
        setExecutorCreator<CO, AverageOperation>(condition);
    } else if (operation == "max") {
        setExecutorCreator<CO, MaxOperation>(condition);
    } else if (operation == "min") {
        setExecutorCreator<CO, MinOperation>(condition);
    } else if (operation == "count") {
        setExecutorCreator<CO, CountOperation>(condition);
    } else {
        LOG(error, "Expected operation parameter to be 'sum', 'product', 'average', 'max', 'min', or 'count', but was '%s'",
            operation.c_str());
        return false;
    }
    return true;
}

template <typename CO, typename OP>
void
ForeachBlueprint::setExecutorCreator(CO condition)
{
    class ExecutorCreator : public ExecutorCreatorBase {
    private:
        CO _condition;
    public:
        ExecutorCreator(CO cond) : _condition(cond) {}
        search::fef::FeatureExecutor &create(uint32_t numInputs, vespalib::Stash &stash) const override {
            return stash.create<ForeachExecutor<CO, OP>>(_condition,  numInputs);
        }
    };
    _executorCreator.reset(new ExecutorCreator(condition));
}

ForeachBlueprint::ForeachBlueprint() :
    Blueprint("foreach"),
    _dimension(ILLEGAL),
    _executorCreator(),
    _num_inputs(0)
{
}

ForeachBlueprint::~ForeachBlueprint() = default;

void
ForeachBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                    IDumpFeatureVisitor &) const
{
}

bool
ForeachBlueprint::setup(const IIndexEnvironment & env,
                        const ParameterList & params)
{
    if (!decideDimension(params[0].getValue())) {
        return false;
    }
    if (!decideCondition(params[3].getValue(), params[4].getValue())) {
        return false;
    }

    const vespalib::string & variable = params[1].getValue();
    const vespalib::string & feature = params[2].getValue();

    if (_dimension == TERMS) {
        uint32_t maxTerms = util::strToNum<uint32_t>(env.getProperties().lookup(getBaseName(), "maxTerms").get("16"));
        for (uint32_t i = 0; i < maxTerms; ++i) {
            defineInput(boost::algorithm::replace_all_copy(feature, variable, vespalib::make_string("%u", i)));
            ++_num_inputs;
        }
    } else {
        for (uint32_t i = 0; i < env.getNumFields(); ++i) {
            const FieldInfo * info = env.getField(i);
            if (info->type() == FieldType::INDEX && _dimension == FIELDS) {
                defineInput(boost::algorithm::replace_all_copy(feature, variable, info->name()));
                ++_num_inputs;
            } else if (info->type() == FieldType::ATTRIBUTE && _dimension == ATTRIBUTES) {
                defineInput(boost::algorithm::replace_all_copy(feature, variable, info->name()));
                ++_num_inputs;
            }
        }
    }

    describeOutput("value", "The result after iterating over the input feature values using the specified operation");

    return true;
}

Blueprint::UP
ForeachBlueprint::createInstance() const
{
    return std::make_unique<ForeachBlueprint>();
}

FeatureExecutor &
ForeachBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    if (_executorCreator) {
        return _executorCreator->create(_num_inputs, stash);
    }
    return stash.create<SingleZeroValueExecutor>();
}


}
