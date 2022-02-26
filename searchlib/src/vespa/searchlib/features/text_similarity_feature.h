// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::features {

//-----------------------------------------------------------------------------

class TextSimilarityExecutor : public fef::FeatureExecutor
{
private:
    std::vector<fef::TermFieldHandle> _handles;
    std::vector<int>                  _weights;
    int                               _total_term_weight;

    struct Item {
        uint32_t idx;
        fef::TermFieldMatchData::PositionsIterator pos;
        fef::TermFieldMatchData::PositionsIterator end;
        Item(uint32_t idx_in,
             fef::TermFieldMatchData::PositionsIterator pos_in,
             fef::TermFieldMatchData::PositionsIterator end_in)
            : idx(idx_in), pos(pos_in), end(end_in) {}
        bool operator<(const Item &other) const {
            return (pos->getPosition() == other.pos->getPosition())
                ? (idx < other.idx)
                : (pos->getPosition() < other.pos->getPosition());
        }
    };

    vespalib::PriorityQueue<Item> _queue;
    const fef::MatchData         *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    TextSimilarityExecutor(const fef::IQueryEnvironment &env, uint32_t field_id);
    bool isPure() override { return _handles.empty(); }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class TextSimilarityBlueprint : public fef::Blueprint
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
    ~TextSimilarityBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::SINGLE);
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
