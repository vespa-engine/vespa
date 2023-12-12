// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_weighted_set_blueprint.h"
#include "multi_term_filter.hpp"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/orsearch.h>


namespace search {

namespace {

using attribute::ISearchContext;
using attribute::IAttributeVector;

class AttrWrapper
{
private:
    const attribute::IAttributeVector &_attr;

protected:
    const attribute::IAttributeVector &attribute() const { return _attr; }

public:
    explicit AttrWrapper(const attribute::IAttributeVector & attr)
        : _attr(attr) {}
};

class StringEnumWrapper : public AttrWrapper
{
public:
    using TokenT = uint32_t;
    explicit StringEnumWrapper(const IAttributeVector & attr)
        : AttrWrapper(attr) {}
    auto mapToken(const ISearchContext &context) const {
        return attribute().findFoldedEnums(context.queryTerm()->getTerm());
    }
    TokenT getToken(uint32_t docId) const {
        return attribute().getEnum(docId);
    }
};

class IntegerWrapper : public AttrWrapper
{
public:
    using TokenT = uint64_t;
    explicit IntegerWrapper(const IAttributeVector & attr) : AttrWrapper(attr) {}
    std::vector<int64_t> mapToken(const ISearchContext &context) const {
        std::vector<int64_t> result;
        Int64Range range(context.getAsIntegerTerm());
        if (range.isPoint()) {
            result.push_back(range.lower());
        }
        return result;
    }
    TokenT getToken(uint32_t docId) const {
        return attribute().getInt(docId);
    }
};

template <typename WrapperType>
std::unique_ptr<queryeval::SearchIterator>
make_multi_term_filter(fef::TermFieldMatchData& tfmd,
                       const IAttributeVector& attr,
                       const std::vector<int32_t>& weights,
                       const std::vector<ISearchContext*>& contexts)
{
    using FilterType = attribute::MultiTermFilter<WrapperType>;
    typename FilterType::TokenMap tokens;
    WrapperType wrapper(attr);
    for (size_t i = 0; i < contexts.size(); ++i) {
        for (auto token : wrapper.mapToken(*contexts[i])) {
            tokens[token] = weights[i];
        }
    }
    return std::make_unique<FilterType>(tfmd, wrapper, std::move(tokens));
}

}

AttributeWeightedSetBlueprint::AttributeWeightedSetBlueprint(const queryeval::FieldSpec &field, const IAttributeVector & attr)
    : queryeval::ComplexLeafBlueprint(field),
      _numDocs(attr.getNumDocs()),
      _estHits(0),
      _weights(),
      _attr(attr),
      _contexts()
{
    set_allow_termwise_eval(true);
}

AttributeWeightedSetBlueprint::~AttributeWeightedSetBlueprint()
{
    while (!_contexts.empty()) {
        delete _contexts.back();
        _contexts.pop_back();
    }
}

void
AttributeWeightedSetBlueprint::addToken(std::unique_ptr<ISearchContext> context, int32_t weight)
{
    _estHits = std::min(_estHits + context->approximateHits(), _numDocs);
    setEstimate(HitEstimate(_estHits, (_estHits == 0)));
    _weights.push_back(weight);
    _contexts.push_back(context.release());
}

queryeval::SearchIterator::UP
AttributeWeightedSetBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    fef::TermFieldMatchData &tfmd = *tfmda[0];
    bool field_is_filter = getState().fields()[0].isFilter();
    if ((tfmd.isNotNeeded() || field_is_filter) && (_contexts.size() == 1)) {
        return _contexts[0]->createIterator(&tfmd, strict);
    }
    if (strict) { // use generic weighted set search
        fef::MatchDataLayout layout;
        auto handle = layout.allocTermField(tfmd.getFieldId());
        auto match_data = layout.createMatchData();
        auto child_tfmd = match_data->resolveTermField(handle);
        std::vector<queryeval::SearchIterator*> children(_contexts.size());
        for (size_t i = 0; i < _contexts.size(); ++i) {
            // TODO: pass ownership with unique_ptr
            children[i] = _contexts[i]->createIterator(child_tfmd, true).release();
        }
        return queryeval::WeightedSetTermSearch::create(children, tfmd, field_is_filter, _weights, std::move(match_data));
    } else { // use attribute filter optimization
        bool isString = (_attr.isStringType() && _attr.hasEnum());
        assert(!_attr.hasMultiValue());
        if (isString) {
            return make_multi_term_filter<StringEnumWrapper>(tfmd, _attr, _weights, _contexts);
        } else {
            assert(_attr.isIntegerType());
            return make_multi_term_filter<IntegerWrapper>(tfmd, _attr, _weights, _contexts);
        }
    }
}

queryeval::SearchIterator::UP
AttributeWeightedSetBlueprint::createFilterSearch(bool strict, FilterConstraint) const
{
    std::vector<std::unique_ptr<queryeval::SearchIterator>> children;
    children.reserve(_contexts.size());
    for (auto& context : _contexts) {
        auto wrapper = std::make_unique<queryeval::FilterWrapper>(1);
        wrapper->wrap(context->createIterator(wrapper->tfmda()[0], strict));
        children.emplace_back(std::move(wrapper));
    }
    return queryeval::OrSearch::create(std::move(children), strict, queryeval::UnpackInfo());
}

void
AttributeWeightedSetBlueprint::fetchPostings(const queryeval::ExecuteInfo &execInfo)
{
    if (execInfo.is_strict()) {
        for (auto * context : _contexts) {
            context->fetchPostings(execInfo);
        }
    }
}

void
AttributeWeightedSetBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute", _attr.getName());
    visitor.openStruct("terms", "TermList");
    for (size_t i = 0; i < _contexts.size(); ++i) {
        const ISearchContext * context = _contexts[i];
        visitor.openStruct(vespalib::make_string("[%zu]", i), "Term");
        visitor.visitBool("valid", context->valid());
        if (context-> valid()) {
            bool isString = (_attr.isStringType() && _attr.hasEnum());
            if (isString) {
                visitor.visitString("term", context->queryTerm()->getTerm());
            } else {
                visitor.visitInt("term", context->getAsIntegerTerm().lower());
            }
            visitor.visitInt("weight", _weights[i]);
        }
        visitor.closeStruct();
    }
    visitor.closeStruct();
}

} // namespace search
