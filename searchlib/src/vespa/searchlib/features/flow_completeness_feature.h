// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

struct FlowCompletenessParams {
    uint32_t  fieldId;
    feature_t fieldWeight;
    feature_t fieldCompletenessImportance;
    FlowCompletenessParams()
        : fieldId(search::fef::IllegalFieldId),
          fieldWeight(0),
          fieldCompletenessImportance(0.5) {}
};

//-----------------------------------------------------------------------------

const uint32_t IllegalElementId = 0xffffffff;
const uint32_t IllegalTermId    = 0xffffffff;
const uint32_t IllegalPosId     = 0xffffffff;

class FlowCompletenessExecutor : public search::fef::FeatureExecutor
{
private:
    struct Term {
        search::fef::TermFieldHandle termHandle;
        int                          termWeight;
        Term(search::fef::TermFieldHandle handle, int weight)
            : termHandle(handle), termWeight(weight) {}
    };

    struct Item {
        uint32_t elemId;
        uint32_t termIdx;
        search::fef::TermFieldMatchData::PositionsIterator pos;
        search::fef::TermFieldMatchData::PositionsIterator end;

        Item(uint32_t idx,
             search::fef::TermFieldMatchData::PositionsIterator p,
             search::fef::TermFieldMatchData::PositionsIterator e)
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

    static bool nextElement(Item &item);

public:
    FlowCompletenessExecutor(const search::fef::IQueryEnvironment &env,
                             const FlowCompletenessParams &params);
    virtual bool isPure() { return _terms.empty(); }
    virtual void execute(search::fef::MatchData & data);
};

//-----------------------------------------------------------------------------

class FlowCompletenessBlueprint : public search::fef::Blueprint
{
private:
    std::vector<vespalib::string>  _output;
    FlowCompletenessParams _params;

public:
    FlowCompletenessBlueprint();

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
    virtual search::fef::FeatureExecutor::LP
    createExecutor(const search::fef::IQueryEnvironment & env) const override;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

