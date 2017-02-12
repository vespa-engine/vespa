// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/fef.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

struct ElementCompletenessParams {
    uint32_t  fieldId;
    feature_t fieldCompletenessImportance;
    ElementCompletenessParams()
        : fieldId(search::fef::IllegalFieldId),
          fieldCompletenessImportance(0.5) {}
};

//-----------------------------------------------------------------------------

class ElementCompletenessExecutor : public search::fef::FeatureExecutor
{
private:
    struct Term {
        search::fef::TermFieldHandle termHandle;
        int                          termWeight;
        Term(search::fef::TermFieldHandle handle, int weight)
            : termHandle(handle), termWeight(weight) {}
    };

    struct Item {
        uint32_t termIdx;
        search::fef::TermFieldMatchData::PositionsIterator pos;
        search::fef::TermFieldMatchData::PositionsIterator end;
        Item(uint32_t idx,
             search::fef::TermFieldMatchData::PositionsIterator p,
             search::fef::TermFieldMatchData::PositionsIterator e)
            : termIdx(idx), pos(p), end(e) {}
        bool operator<(const Item &other) const {
            return (pos->getElementId() < other.pos->getElementId());
        }
    };

    struct State {
        int       elementWeight;
        uint32_t  elementLength;
        uint32_t  matchedTerms;
        int       sumTermWeight;
        double    score;
        feature_t completeness;
        feature_t fieldCompleteness;
        feature_t queryCompleteness;

        State(int weight, uint32_t length)
            : elementWeight(weight), elementLength(length),
              matchedTerms(0), sumTermWeight(0),
              score(0.0),
              completeness(0.0), fieldCompleteness(0.0), queryCompleteness(0.0) {}

        void addMatch(int termWeight) {
            ++matchedTerms;
            sumTermWeight += termWeight;
        }

        void calculateScore(int totalTermWeight, double factor) {
            double matches = std::min(elementLength, matchedTerms);
            queryCompleteness = ((double)sumTermWeight / (double)totalTermWeight);
            fieldCompleteness = (matches / (double)elementLength);
            completeness = (fieldCompleteness * factor) +
                           (queryCompleteness * (1 - factor));
            score = completeness * (double)elementWeight;
        }
    };

    ElementCompletenessParams     _params;
    std::vector<Term>             _terms;
    vespalib::PriorityQueue<Item> _queue;
    int                           _sumTermWeight;
    const fef::MatchData         *_md;

    static bool nextElement(Item &item);

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    ElementCompletenessExecutor(const search::fef::IQueryEnvironment &env,
                                const ElementCompletenessParams &params);
    virtual bool isPure() { return _terms.empty(); }
    virtual void execute(uint32_t docId);
};

//-----------------------------------------------------------------------------

class ElementCompletenessBlueprint : public search::fef::Blueprint
{
private:
    std::vector<vespalib::string>  _output;
    ElementCompletenessParams _params;

public:
    ElementCompletenessBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::ANY);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    // for testing
    const ElementCompletenessParams &getParams() const { return _params; }
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

