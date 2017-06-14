// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "element_completeness_feature.h"

namespace search {
namespace features {

//-----------------------------------------------------------------------------

ElementCompletenessExecutor::ElementCompletenessExecutor(const search::fef::IQueryEnvironment &env,
                                                         const ElementCompletenessParams &params)
    : _params(params),
      _terms(),
      _queue(),
      _sumTermWeight(0),
      _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const search::fef::ITermData *termData = env.getTerm(i);
        if (termData->getWeight().percent() != 0) { // only consider query terms with contribution
            typedef search::fef::ITermFieldRangeAdapter FRA;
            for (FRA iter(*termData); iter.valid(); iter.next()) {
                const search::fef::ITermFieldData &tfd = iter.get();
                if (tfd.getFieldId() == _params.fieldId) {
                    int termWeight = termData->getWeight().percent();
                    _sumTermWeight += termWeight;
                    _terms.push_back(Term(tfd.getHandle(), termWeight));
                }
            }
        }
    }
}

void
ElementCompletenessExecutor::execute(uint32_t docId)
{
    assert(_queue.empty());
    for (size_t i = 0; i < _terms.size(); ++i) {
        const search::fef::TermFieldMatchData *tfmd = _md->resolveTermField(_terms[i].termHandle);
        if (tfmd->getDocId() == docId) {
            Item item(i, tfmd->begin(), tfmd->end());
            if (item.pos != item.end) {
                _queue.push(item);
            }
        }
    }
    State best(0, 0);
    while (!_queue.empty()) {
        uint32_t elementId = _queue.front().pos->getElementId();
        State state(_queue.front().pos->getElementWeight(),
                    _queue.front().pos->getElementLen());
        while (!_queue.empty() && _queue.front().pos->getElementId() == elementId) {
            state.addMatch(_terms[_queue.front().termIdx].termWeight);
            Item &item = _queue.front();
            while (item.pos != item.end && item.pos->getElementId() == elementId) {
                ++item.pos;
            }
            if (item.pos == item.end) {
                _queue.pop_front();
            } else {
                _queue.adjust();
            }
        }
        state.calculateScore(_sumTermWeight, _params.fieldCompletenessImportance);
        if (state.score > best.score) {
            best = state;
        }
    }
    outputs().set_number(0, best.completeness);
    outputs().set_number(1, best.fieldCompleteness);
    outputs().set_number(2, best.queryCompleteness);
    outputs().set_number(3, best.elementWeight);
}

void
ElementCompletenessExecutor::handle_bind_match_data(fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

ElementCompletenessBlueprint::ElementCompletenessBlueprint()
    : Blueprint("elementCompleteness"),
      _output(),
      _params()
{
    _output.push_back("completeness");
    _output.push_back("fieldCompleteness");
    _output.push_back("queryCompleteness");
    _output.push_back("elementWeight");
}

void
ElementCompletenessBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                                search::fef::IDumpFeatureVisitor &visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const search::fef::FieldInfo &field = *env.getField(i);
        if (field.type() == search::fef::FieldType::INDEX) {
            if (!field.isFilter()) {
                search::fef::FeatureNameBuilder fnb;
                fnb.baseName(getBaseName()).parameter(field.name());
                for (size_t out = 0; out < _output.size(); ++out) {
                    visitor.visitDumpFeature(fnb.output(_output[out]).buildName());
                }
            }
        }
    }
}

search::fef::Blueprint::UP
ElementCompletenessBlueprint::createInstance() const
{
    return Blueprint::UP(new ElementCompletenessBlueprint());
}

bool
ElementCompletenessBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                    const search::fef::ParameterList &params)
{
    const search::fef::FieldInfo *field = params[0].asField();

    _params.fieldId = field->id();
    const search::fef::Properties &lst = env.getProperties();
    search::fef::Property obj = lst.lookup(getName(), "fieldCompletenessImportance");
    if (obj.found()) {
        _params.fieldCompletenessImportance = atof(obj.get().c_str());
    }
    describeOutput(_output[0], "combined completeness for best scored element");
    describeOutput(_output[1], "best scored element completeness");
    describeOutput(_output[2], "query completeness for best scored element");
    describeOutput(_output[3], "element weight of best scored element");
    env.hintFieldAccess(field->id());
    return true;
}

search::fef::FeatureExecutor &
ElementCompletenessBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<ElementCompletenessExecutor>(env, _params);
}

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search
