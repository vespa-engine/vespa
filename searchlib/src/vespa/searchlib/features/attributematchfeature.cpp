// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributematchfeature.h"
#include "utils.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/parameterdescriptions.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/vespalib/util/issue.h>
using vespalib::Issue;

#include <vespa/log/log.h>
LOG_SETUP(".features.attributematchfeature");

using namespace search::attribute;
using namespace search::fef;
using search::feature_t;

namespace {
feature_t adjustToOne(feature_t value) {
    if (value > 1.0f) {
        return 1.0f;
    }
    return value;
}

bool hasAttribute(const IQueryEnvironment &env, const ITermData &term_data)
{
    using FRA = ITermFieldRangeAdapter;

    for (FRA iter(term_data); iter.valid(); iter.next()) {
        const FieldInfo *info = env.getIndexEnvironment().getField(iter.get().getFieldId());
        if (info != nullptr && info->type() == FieldType::ATTRIBUTE) {
            return true;
        }
    }
    return false;
}
}  // namespace

namespace search::features {

template <typename T>
AttributeMatchExecutor<T>::Computer::Computer(const IQueryEnvironment & env, AttributeMatchParams params) :
    _params(params),
    _buffer(),
    _numAttrTerms(0),
    _totalTermWeight(0),
    _totalTermSignificance(0),
    _totalAttrTermWeight(0),
    _queryTerms(),
    _matches(0),
    _matchedTermWeight(0),
    _matchedTermSignificance(0),
    _totalWeight(0),
    _maxWeight(0),
    _normalizedWeightedWeight(0),
    _weightSum(0),
    _valueCount(0),
    _md(nullptr)
{
    QueryTermHelper queryTerms(env);
    for (const QueryTerm & qt : queryTerms.terms()) {
        _totalTermWeight += qt.termData()->getWeight().percent();
        _totalTermSignificance += qt.significance();

        if (hasAttribute(env, *qt.termData())) {
            _numAttrTerms++;
            _totalAttrTermWeight += qt.termData()->getWeight().percent();
            const ITermFieldData *field = qt.termData()->lookupField(_params.attrInfo->id());
            if (field != nullptr) {
                QueryTerm myQt(qt);
                myQt.fieldHandle(field->getHandle());
                _queryTerms.push_back(myQt);
            }
        }
    }
    LOG(debug, "attributeMatch(%s): weightedSet(%s), numAttributeTerms(%u), totalAttrTermWeight(%u), numTerms(%u), "
        "totalTermWeight(%u), totalTermSignificance(%f)",
        _params.attrInfo->name().c_str(), _params.weightedSet ? "true" : "false",
        _numAttrTerms, _totalAttrTermWeight, getNumTerms(), _totalTermWeight, _totalTermSignificance);
}

template <typename T>
void
AttributeMatchExecutor<T>::Computer::reset()
{
    _matches = 0;
    _matchedTermWeight = 0,
    _matchedTermSignificance = 0,
    _totalWeight = 0;
    _maxWeight = 0;
    _normalizedWeightedWeight = 0;
    _weightSum = 0;
    _valueCount = 0;
}

template <typename T>
void
AttributeMatchExecutor<T>::Computer::run(uint32_t docId)
{
    for (size_t i = 0; i < _queryTerms.size(); ++i) {
        const ITermData * td = _queryTerms[i].termData();
        feature_t significance = _queryTerms[i].significance();
        const TermFieldMatchData *tfmd = _md->resolveTermField(_queryTerms[i].fieldHandle());
        if (tfmd->has_ranking_data(docId)) { // hit on this document
            _matches++;
            _matchedTermWeight += td->getWeight().percent();
            _matchedTermSignificance += significance;
            if (_params.weightedSet) {
                int32_t weight = tfmd->getWeight();
                _totalWeight += weight;
                _maxWeight = (_matches == 1) ? weight : std::max(_maxWeight, weight);
                // attribute weight * query term weight
                _normalizedWeightedWeight += weight * static_cast<int32_t>(td->getWeight().percent());
            }
        }
    }
    if (_params.weightedSet) {
        _buffer.fill(*_params.attribute, docId);
        for (uint32_t i = 0; i < _buffer.size(); ++i) {
            _weightSum += _buffer[i].getWeight();
        }
    } else {
        _valueCount = _params.attribute->getValueCount(docId);
    }

    LOG(debug, "attributeMatch(%s)::Computer::run(): matches(%u), totalWeight(%d), maxWeight(%d), normalizedWeightedWeight(%f), "
        "weightSum(%d), valueCount(%u), matchedTermWeight(%u), matchedTermSignificance(%f)",
        _params.attrInfo->name().c_str(), _matches, _totalWeight, _maxWeight, _normalizedWeightedWeight,
        _weightSum, _valueCount, _matchedTermWeight, _matchedTermSignificance);
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getAverageWeight() const
{
    if (_matches != 0) {
        return (_totalWeight / static_cast<feature_t>(_matches));
    }
    return 0;
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getQueryCompleteness() const
{
    if (getNumTerms() != 0) {
        return (_matches / static_cast<feature_t>(getNumTerms()));
    }
    return 0;
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getNormalizedWeight() const
{
    if (_params.weightedSet) {
        feature_t normalizedWeight = _totalWeight > 0 ? _totalWeight / ((feature_t)_params.maxWeight * _numAttrTerms) : 0.0f;
        return adjustToOne(normalizedWeight);
    }
    return 0;
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getNormalizedWeightedWeight() const
{
    if (_params.weightedSet) {
        feature_t divider = _totalAttrTermWeight > 0 ? ((feature_t)_params.maxWeight * _totalAttrTermWeight) : _params.maxWeight;
        feature_t normalized = _normalizedWeightedWeight > 0 ? _normalizedWeightedWeight / divider : 0.0f;
        return adjustToOne(normalized);
    }
    return 0;
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getFieldCompleteness() const
{
    if (_params.weightedSet) {
       if (_totalWeight <= 0) {
            return 0;
        } else if (_weightSum <= 0) {
            return 1;
        } else {
            feature_t fieldCompleteness = (_totalWeight / static_cast<feature_t>(_weightSum));
            return adjustToOne(fieldCompleteness);
        }
    } else {
        if (_valueCount > 0) {
            feature_t fieldCompleteness = _matches / static_cast<feature_t>(_valueCount);
            return adjustToOne(fieldCompleteness);
        } else {
            return 0;
        }
    }
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getCompleteness() const
{
    return (getQueryCompleteness() * ( 1.0f - _params.fieldCompletenessImportance +
                                       (_params.fieldCompletenessImportance * getFieldCompleteness()) ));
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getWeight() const
{
    if (_totalTermWeight > 0) {
        return (feature_t)_matchedTermWeight / _totalTermWeight;
    }
    return 0;
}

template <typename T>
feature_t
AttributeMatchExecutor<T>::Computer::getSignificance() const
{
    if (_totalTermSignificance > 0) {
        return (feature_t)_matchedTermSignificance / _totalTermSignificance;
    }
    return 0;
}

template <typename T>
AttributeMatchExecutor<T>::AttributeMatchExecutor(const IQueryEnvironment & env, AttributeMatchParams params) :
    FeatureExecutor(),
    _cmp(env, params)
{
}


template <typename T>
void
AttributeMatchExecutor<T>::execute(uint32_t docId)
{
    //LOG(debug, "Execute for field '%s':", _params.attrInfo->name().c_str());
    _cmp.reset();
    _cmp.run(docId);

    outputs().set_number(0, _cmp.getCompleteness());
    outputs().set_number(1, _cmp.getQueryCompleteness());
    outputs().set_number(2, _cmp.getFieldCompleteness());
    outputs().set_number(3, _cmp.getNormalizedWeight());
    outputs().set_number(4, _cmp.getNormalizedWeightedWeight());
    outputs().set_number(5, _cmp.getWeight());
    outputs().set_number(6, _cmp.getSignificance());
    outputs().set_number(7, _cmp.getImportance());
    outputs().set_number(8, static_cast<feature_t>(_cmp.getMatches()));
    outputs().set_number(9, static_cast<feature_t>(_cmp.getTotalWeight()));
    outputs().set_number(10, _cmp.getAverageWeight());
    outputs().set_number(11, static_cast<feature_t>(_cmp.getMaxWeight()));
}

template <typename T>
void
AttributeMatchExecutor<T>::handle_bind_match_data(const MatchData &md)
{
    _cmp.bind_match_data(md);
}

AttributeMatchBlueprint::AttributeMatchBlueprint() :
    Blueprint("attributeMatch"),
    _params()
{
    // empty
}

AttributeMatchBlueprint::~AttributeMatchBlueprint() = default;

void
AttributeMatchBlueprint::visitDumpFeatures(const IIndexEnvironment &env,
                                           IDumpFeatureVisitor &visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const FieldInfo * field = env.getField(i);
        if (field->type() == FieldType::ATTRIBUTE &&
            ParameterDataTypeSet::primitiveTypeSet().allowedType(field->get_data_type())) {
            FeatureNameBuilder fnb;
            fnb.baseName(getBaseName()).parameter(field->name());
            visitor.visitDumpFeature(fnb.buildName());
            visitor.visitDumpFeature(fnb.output("completeness").buildName());
            visitor.visitDumpFeature(fnb.output("queryCompleteness").buildName());
            visitor.visitDumpFeature(fnb.output("fieldCompleteness").buildName());
            visitor.visitDumpFeature(fnb.output("normalizedWeight").buildName());
            visitor.visitDumpFeature(fnb.output("normalizedWeightedWeight").buildName());
            visitor.visitDumpFeature(fnb.output("weight").buildName());
            visitor.visitDumpFeature(fnb.output("significance").buildName());
            visitor.visitDumpFeature(fnb.output("importance").buildName());
            visitor.visitDumpFeature(fnb.output("matches").buildName());
            visitor.visitDumpFeature(fnb.output("totalWeight").buildName());
            visitor.visitDumpFeature(fnb.output("averageWeight").buildName());
            visitor.visitDumpFeature(fnb.output("maxWeight").buildName());
        }
    }
}

Blueprint::UP
AttributeMatchBlueprint::createInstance() const
{
    return std::make_unique<AttributeMatchBlueprint>();
}

fef::ParameterDescriptions
AttributeMatchBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().attributeField(fef::ParameterDataTypeSet::primitiveTypeSet(),
                                                              fef::ParameterCollection::ANY);
}


bool
AttributeMatchBlueprint::setup(const IIndexEnvironment & env,
                               const ParameterList & params)
{
    // params[0] = attribute name
    _params.attrInfo = params[0].asField();
    _params.maxWeight = util::strToNum<int32_t>(env.getProperties().lookup(getName(), "maxWeight").get("256"));
    _params.fieldCompletenessImportance =
        util::strToNum<feature_t>(env.getProperties().lookup(getName(), "fieldCompletenessImportance").get("0.05"));

    // normalized
    describeOutput("completeness",      "The normalized total completeness, where field completeness is more important");
    describeOutput("queryCompleteness", "The query completeness for this attribute: matches/the number of query terms searching this attribute");
    describeOutput("fieldCompleteness", "The normalized ratio of query tokens which was matched in the field");
    describeOutput("normalizedWeight",  "A number which is close to 1 if the attribute weights of most matches in a weighted set are high (relative to the maxWeight configuration value), 0 otherwise");
    describeOutput("normalizedWeightedWeight", "A number which is close to 1 if the attribute weights of most matches in a weighted set are high (relative to the maxWeight configuration value), and where highly weighted query terms has more impact, 0 otherwise");
    // normalized and relative to the whole query
    describeOutput("weight",       "The normalized weight of this match relative to the whole query");
    describeOutput("significance", "Returns the normalized term significance of the terms of this match relative to the whole query");
    describeOutput("importance",   "Returns the average of significance and weight");

    // not normalized
    describeOutput("matches",       "The number of query terms which was matched in this attribute");
    describeOutput("totalWeight",   "The sum of the weights of the attribute keys matched in a weighted set attribute");
    describeOutput("averageWeight", "totalWeight/matches");
    describeOutput("maxWeight",     "The max weight of the attribute keys matched in a weighted set attribute");

    return true;
}

FeatureExecutor &
AttributeMatchBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    const IAttributeVector * attribute = env.getAttributeContext().getAttribute(_params.attrInfo->name());
    if (attribute == nullptr) {
        Issue::report("attribute_match feature: The attribute vector '%s' was not found.", _params.attrInfo->name().c_str());
        std::vector<feature_t> values;
        values.push_back(0.0); // completeness
        values.push_back(0.0); // queryCompleteness
        values.push_back(0.0); // fieldCompleteness
        values.push_back(0.0); // normalizedWeight
        values.push_back(0.0); // normalizedWeightedWeight
        values.push_back(0.0); // weight
        values.push_back(0.0); // significance
        values.push_back(0.0); // importance
        values.push_back(0.0); // matches
        values.push_back(0.0); // totalWeight
        values.push_back(0.0); // averageWeight
        return stash.create<ValueExecutor>(values);
    }

    AttributeMatchParams amp = _params;
    amp.attribute = attribute;
    amp.weightedSet = attribute->getCollectionType() == attribute::CollectionType::WSET;

    if (attribute->isStringType()) {
        return stash.create<AttributeMatchExecutor<WeightedConstCharContent>>(env, amp);
    } else if (attribute->isIntegerType()) {
        return stash.create<AttributeMatchExecutor<WeightedIntegerContent>>(env, amp);
    } else { // FLOAT
        return stash.create<AttributeMatchExecutor<WeightedFloatContent>>(env, amp);
    }
}

void
AttributeMatchBlueprint::prepareSharedState(const IQueryEnvironment &queryEnv, IObjectStore &objectStore) const {
    QueryTermHelper::lookupAndStoreQueryTerms(queryEnv, objectStore);
}

}
