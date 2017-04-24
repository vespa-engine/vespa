// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

class TextSimilarityExecutor : public search::fef::FeatureExecutor
{
private:
    std::vector<fef::TermFieldHandle> _handles;
    std::vector<int>                  _weights;
    int                               _total_term_weight;

    struct Item {
        uint32_t idx;
        search::fef::TermFieldMatchData::PositionsIterator pos;
        search::fef::TermFieldMatchData::PositionsIterator end;
        Item(uint32_t idx_in,
             search::fef::TermFieldMatchData::PositionsIterator pos_in,
             search::fef::TermFieldMatchData::PositionsIterator end_in)
            : idx(idx_in), pos(pos_in), end(end_in) {}
        bool operator<(const Item &other) const {
            return (pos->getPosition() == other.pos->getPosition())
                ? (idx < other.idx)
                : (pos->getPosition() < other.pos->getPosition());
        }
    };

    vespalib::PriorityQueue<Item> _queue;
    const fef::MatchData         *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    TextSimilarityExecutor(const search::fef::IQueryEnvironment &env, uint32_t field_id);
    virtual bool isPure() override { return _handles.empty(); }
    virtual void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class TextSimilarityBlueprint : public search::fef::Blueprint
{
private:
    static const vespalib::string score_output;
    static const vespalib::string proximity_output;
    static const vespalib::string order_output;
    static const vespalib::string query_coverage_output;
    static const vespalib::string field_coverage_output;

    uint32_t _field_id;

public:
    TextSimilarityBlueprint();
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const override;
    virtual search::fef::Blueprint::UP createInstance() const override;
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::SINGLE);
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params) override;
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

