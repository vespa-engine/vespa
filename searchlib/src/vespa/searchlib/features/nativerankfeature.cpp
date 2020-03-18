// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nativerankfeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".features.nativerankfeature");

using namespace search::fef;

namespace {

vespalib::string
buildFeatureName(const vespalib::string & baseName, const search::features::FieldWrapper & fields)
{
    std::ostringstream oss;
    oss << baseName << "(";
    for (size_t i = 0; i < fields.getNumFields(); ++i) {
        if (i > 0) {
            oss << ",";
        }
        oss << fields.getField(i)->name();
    }
    oss << ")";
    return oss.str();
}

}

namespace search::features {

FieldWrapper::FieldWrapper(const IIndexEnvironment & env,
                           const ParameterList & fields,
                           const FieldType filter) :
    _fields()
{
    if (!fields.empty()) {
        for (size_t i = 0; i < fields.size(); ++i) {
            const search::fef::FieldInfo * info = fields[i].asField();
            if (info->type() == filter) {
                _fields.push_back(info);
            }
        }
    } else {
        for (size_t i = 0; i < env.getNumFields(); ++i) {
            const search::fef::FieldInfo * info = env.getField(i);
            LOG_ASSERT(info->id() == i && "The field ids must be the same in FieldInfo as in IIndexEnvironment");
            if (info->type() == filter) {
                _fields.push_back(info);
            }
        }
    }
}


NativeRankExecutor::NativeRankExecutor(const NativeRankParams & params) :
    FeatureExecutor(),
    _params(params),
    _divisor(0)
{
    _divisor += _params.fieldMatchWeight;
    _divisor += _params.attributeMatchWeight;
    _divisor += _params.proximityWeight;
}

void
NativeRankExecutor::execute(uint32_t)
{
    outputs().set_number(0, (inputs().get_number(0) * _params.fieldMatchWeight
                             + inputs().get_number(1) * _params.proximityWeight
                             + inputs().get_number(2) * _params.attributeMatchWeight) / _divisor);
}


NativeRankBlueprint::NativeRankBlueprint() :
    Blueprint("nativeRank"),
    _params()
{
}

void
NativeRankBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                       IDumpFeatureVisitor & visitor) const
{
    (void) env;
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
NativeRankBlueprint::createInstance() const
{
    return std::make_unique<NativeRankBlueprint>();
}

bool
NativeRankBlueprint::setup(const IIndexEnvironment & env,
                           const ParameterList & params)
{
    _params.fieldMatchWeight = util::strToNum<feature_t>
        (env.getProperties().lookup(getBaseName(), "fieldMatchWeight").get("100"));
    _params.attributeMatchWeight = util::strToNum<feature_t>
        (env.getProperties().lookup(getBaseName(), "attributeMatchWeight").get("100"));
    vespalib::string defProxWeight = "25";
    if (!useTableNormalization(env)) {
        defProxWeight = "100"; // must use another weight to match the default boost tables
    }
    _params.proximityWeight = util::strToNum<feature_t>
        (env.getProperties().lookup(getBaseName(), "proximityWeight").get(defProxWeight));

    vespalib::string nfm = "nativeFieldMatch";
    vespalib::string np = "nativeProximity";
    vespalib::string nam = "nativeAttributeMatch";
    vespalib::string zero = "value(0)";

    // handle parameter list
    if (!params.empty()) {
        FieldWrapper indexFields(env, params, FieldType::INDEX);
        FieldWrapper attrFields(env, params, FieldType::ATTRIBUTE);
        if (indexFields.getNumFields() > 0) {
            nfm = buildFeatureName("nativeFieldMatch", indexFields);
            np = buildFeatureName("nativeProximity", indexFields);
        } else {
            nfm = zero;
            np = zero;
        }
        if (attrFields.getNumFields() > 0) {
            nam = buildFeatureName("nativeAttributeMatch", attrFields);
        } else {
            nam = zero;
        }
    }
    // optimizations when weight == 0
    if (_params.fieldMatchWeight == 0) {
        nfm = zero;
    }
    if (_params.proximityWeight == 0) {
        np = zero;
    }
    if (_params.attributeMatchWeight == 0) {
        nam = zero;
    }

    defineInput(nfm);
    defineInput(np);
    defineInput(nam);
    describeOutput("score", "The native rank score");
    return true;
}

FeatureExecutor &
NativeRankBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    if (_params.proximityWeight + _params.fieldMatchWeight + _params.attributeMatchWeight > 0) {
        return stash.create<NativeRankExecutor>(_params);
    } else {
        return stash.create<SingleZeroValueExecutor>();
    }
}

bool
NativeRankBlueprint::useTableNormalization(const search::fef::IIndexEnvironment & env)
{
    Property norm = env.getProperties().lookup("nativeRank", "useTableNormalization");
    return (!(norm.found() && (norm.get() == vespalib::string("false"))));
}

}
