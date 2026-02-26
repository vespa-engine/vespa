// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "text_similarity_feature.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

using CollectionType = fef::FieldInfo::CollectionType;

namespace {

struct Term {
    fef::TermFieldHandle handle;
    int                          weight;
    Term(fef::TermFieldHandle handle_in, int weight_in)
        : handle(handle_in), weight(weight_in) {}
};

struct State {
    uint32_t  field_length;
    uint32_t  matched_terms;
    int       sum_term_weight;
    uint32_t  last_pos;
    double    sum_proximity_score;
    uint32_t  last_idx;
    uint32_t  num_in_order;

    State(uint32_t length, uint32_t first_pos, int32_t first_weight, uint32_t first_idx)
        : field_length(length),
          matched_terms(1), sum_term_weight(first_weight),
          last_pos(first_pos), sum_proximity_score(0.0),
          last_idx(first_idx), num_in_order(0) {}

    double proximity_score(uint32_t dist) {
        return (dist > 8) ? 0 : (1.0 - (((dist-1)/8.0) * ((dist-1)/8.0)));
    }

    bool want_match(uint32_t pos) {
        return (pos > last_pos);
    }

    void addMatch(uint32_t pos, int32_t weight, uint32_t idx) {
        sum_proximity_score += proximity_score(pos - last_pos);
        num_in_order += (idx > last_idx) ? 1 : 0;
        last_pos = pos;
        last_idx = idx;        
        ++matched_terms;
        sum_term_weight += weight;
    }

    void calculateScore(int total_term_weight,
                        double &score_out,
                        double &proximity_out, double &order_out,
                        double &query_coverage_out, double &field_coverage_out)
    {
        double matches = std::min(field_length, matched_terms);
        if (matches < 2) {
            proximity_out = proximity_score(field_length);
            order_out = matches;
        } else {
            proximity_out = sum_proximity_score / (matches - 1);
            order_out = num_in_order / (double) (matches - 1);
        }
        query_coverage_out = sum_term_weight / (double) total_term_weight;
        field_coverage_out = matches / (double) field_length;
        score_out = (0.35 * proximity_out) + (0.15 * order_out)
                    + (0.30 * query_coverage_out) + (0.20 * field_coverage_out);
    }
};

} // namespace search::features::<unnamed>

//-----------------------------------------------------------------------------

TextSimilarityExecutor::TextSimilarityExecutor(const fef::IQueryEnvironment &env,
                                               uint32_t field_id)
    : _handles(),
      _weights(),
      _total_term_weight(0),
      _queue(),
      _md(nullptr)
{
    std::vector<Term> terms;
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const fef::ITermData *termData = env.getTerm(i);
        if (termData->getWeight().percent() != 0) { // only consider query terms with contribution
            using FRA = fef::ITermFieldRangeAdapter;
            for (FRA iter(*termData); iter.valid(); iter.next()) {
                const fef::ITermFieldData &tfd = iter.get();
                if (tfd.getFieldId() == field_id) {
                    int term_weight = termData->getWeight().percent();
                    _total_term_weight += term_weight;
                    terms.push_back(Term(tfd.getHandle(), term_weight));
                }
            }
        }
    }
    _handles.reserve(terms.size());
    _weights.reserve(terms.size());
    for (size_t i = 0; i < terms.size(); ++i) {
        _handles.push_back(terms[i].handle);
        _weights.push_back(terms[i].weight);
    }
}

void
TextSimilarityExecutor::execute(uint32_t docId)
{
    for (size_t i = 0; i < _handles.size(); ++i) {
        const fef::TermFieldMatchData *tfmd = _md->resolveTermField(_handles[i]);
        if (tfmd->has_ranking_data(docId)) {
            Item item(i, tfmd->begin(), tfmd->end());
            if (item.pos != item.end) {
                _queue.push(item);
            }
        }
    }
    if (_queue.empty()) {
        outputs().set_number(0, 0.0);
        outputs().set_number(1, 0.0);
        outputs().set_number(2, 0.0);
        outputs().set_number(3, 0.0);
        outputs().set_number(4, 0.0);
        return;
    }
    const Item &first = _queue.front();
    State state(first.pos->getElementLen(),
                first.pos->getPosition(),
                _weights[first.idx],
                first.idx);
    _queue.pop_front();
    while (!_queue.empty()) {
        Item &item = _queue.front();
        if (state.want_match(item.pos->getPosition())) {
            state.addMatch(item.pos->getPosition(),
                           _weights[item.idx],
                           item.idx);
            _queue.pop_front();
        } else {
            ++item.pos;
            if (item.pos == item.end) {
                _queue.pop_front();
            } else {
                _queue.adjust();
            }
        }
    }
    state.calculateScore(_total_term_weight,
                         *outputs().get_number_ptr(0),
                         *outputs().get_number_ptr(1),
                         *outputs().get_number_ptr(2),
                         *outputs().get_number_ptr(3),
                         *outputs().get_number_ptr(4));
}

void
TextSimilarityExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

const std::string TextSimilarityBlueprint::score_output("score");
const std::string TextSimilarityBlueprint::proximity_output("proximity");
const std::string TextSimilarityBlueprint::order_output("order");
const std::string TextSimilarityBlueprint::query_coverage_output("queryCoverage");
const std::string TextSimilarityBlueprint::field_coverage_output("fieldCoverage");

TextSimilarityBlueprint::TextSimilarityBlueprint()
    : Blueprint("textSimilarity"), _field_id(fef::IllegalHandle) {}

TextSimilarityBlueprint::~TextSimilarityBlueprint() = default;

void
TextSimilarityBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &env,
                                           fef::IDumpFeatureVisitor &visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const fef::FieldInfo &field = *env.getField(i);
        if (field.type() == fef::FieldType::INDEX) {
            if (!field.isFilter() && field.collection() == CollectionType::SINGLE) {
                fef::FeatureNameBuilder fnb;
                fnb.baseName(getBaseName()).parameter(field.name());
                visitor.visitDumpFeature(fnb.output(score_output).buildName());
                visitor.visitDumpFeature(fnb.output(proximity_output).buildName());
                visitor.visitDumpFeature(fnb.output(order_output).buildName());
                visitor.visitDumpFeature(fnb.output(query_coverage_output).buildName());
                visitor.visitDumpFeature(fnb.output(field_coverage_output).buildName());
            }
        }
    }
}

fef::Blueprint::UP
TextSimilarityBlueprint::createInstance() const
{
    return std::make_unique<TextSimilarityBlueprint>();
}

bool
TextSimilarityBlueprint::setup(const fef::IIndexEnvironment &,
                               const fef::ParameterList &params)
{
    const fef::FieldInfo *field = params[0].asField();
    _field_id = field->id();
    describeOutput(score_output, "default normalized combination of other outputs");
    describeOutput(proximity_output, "normalized match proximity score");
    describeOutput(order_output, "normalized match order score");
    describeOutput(query_coverage_output, "normalized query match coverage");
    describeOutput(field_coverage_output, "normalized field match coverage");
    return true;
}

fef::FeatureExecutor &
TextSimilarityBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<TextSimilarityExecutor>(env, _field_id);
}

}
