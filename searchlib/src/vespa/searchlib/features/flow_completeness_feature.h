// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::features {

//-----------------------------------------------------------------------------

struct FlowCompletenessParams {
    uint32_t  fieldId;
    feature_t fieldWeight;
    feature_t fieldCompletenessImportance;
    FlowCompletenessParams()
        : fieldId(fef::IllegalFieldId),
          fieldWeight(0),
          fieldCompletenessImportance(0.5) {}
};

//-----------------------------------------------------------------------------

const uint32_t IllegalElementId = 0xffffffff;
const uint32_t IllegalTermId    = 0xffffffff;
const uint32_t IllegalPosId     = 0xffffffff;

class FlowCompletenessExecutor : public fef::FeatureExecutor
{
private:
    struct Term {
        fef::TermFieldHandle termHandle;
        int                          termWeight;
        Term(fef::TermFieldHandle handle, int weight)
            : termHandle(handle), termWeight(weight) {}
    };

    struct Item {
        uint32_t elemId;
        uint32_t termIdx;
        fef::TermFieldMatchData::PositionsIterator pos;
        fef::TermFieldMatchData::PositionsIterator end;

        Item(uint32_t idx,
             fef::TermFieldMatchData::PositionsIterator p,
             fef::TermFieldMatchData::PositionsIterator e)
            : elemId(IllegalElementId), termIdx(idx), pos(p), end(e)
        {
            if (p != e) elemId = p->getElementId();
        }

        bool operator< (const Item &other) const {
            return (elemId < other.elemId);
        }
    };

    FlowCompletenessParams        _params;
    std::vector<Term>             _terms;
    vespalib::PriorityQueue<Item> _queue;
    int                           _sumTermWeight;
    const fef::MatchData         *_md;

    static bool nextElement(Item &item);

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    FlowCompletenessExecutor(const fef::IQueryEnvironment &env,
                             const FlowCompletenessParams &params);
    bool isPure() override { return _terms.empty(); }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class FlowCompletenessBlueprint : public fef::Blueprint
{
private:
    std::vector<vespalib::string>  _output;
    FlowCompletenessParams _params;

public:
    FlowCompletenessBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

}
