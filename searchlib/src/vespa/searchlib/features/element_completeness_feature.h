// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::features {

//-----------------------------------------------------------------------------

struct ElementCompletenessParams {
    uint32_t  fieldId;
    feature_t fieldCompletenessImportance;
    ElementCompletenessParams()
        : fieldId(fef::IllegalFieldId),
          fieldCompletenessImportance(0.5) {}
};

//-----------------------------------------------------------------------------

class ElementCompletenessExecutor : public fef::FeatureExecutor
{
private:
    struct Term {
        fef::TermFieldHandle termHandle;
        int                          termWeight;
        Term(fef::TermFieldHandle handle, int weight)
            : termHandle(handle), termWeight(weight) {}
    };

    struct Item {
        uint32_t termIdx;
        fef::TermFieldMatchData::PositionsIterator pos;
        fef::TermFieldMatchData::PositionsIterator end;
        Item(uint32_t idx,
             fef::TermFieldMatchData::PositionsIterator p,
             fef::TermFieldMatchData::PositionsIterator e)
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

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    ElementCompletenessExecutor(const fef::IQueryEnvironment &env,
                                const ElementCompletenessParams &params);
    bool isPure() override { return _terms.empty(); }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class ElementCompletenessBlueprint : public fef::Blueprint
{
private:
    std::vector<vespalib::string>  _output;
    ElementCompletenessParams _params;

public:
    ElementCompletenessBlueprint();
    ~ElementCompletenessBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;

    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }

    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;

    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    // for testing
    const ElementCompletenessParams &getParams() const { return _params; }
};

}
