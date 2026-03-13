// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "element_similarity_feature.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.elementsimilarity");

namespace search::features {

using CollectionType = fef::FieldInfo::CollectionType;

namespace {

//-----------------------------------------------------------------------------

struct Aggregator {
    using UP = std::unique_ptr<Aggregator>;
    virtual UP create() const = 0;
    virtual void clear() = 0;
    virtual void add(double) = 0;
    virtual double get() const = 0;
    virtual ~Aggregator() = default;
};

struct MaxAggregator : Aggregator {
    size_t count;
    double value;

    MaxAggregator() : count(0), value(0.0) {}
    UP create() const override { return UP(new MaxAggregator()); }
    void clear() override { count = 0; value = 0.0; }
    void add(double v) override { value = ((++count == 1) || (v > value)) ? v : value; }
    double get() const override { return value; }
};

struct AvgAggregator : Aggregator {
    size_t count;
    double value;

    AvgAggregator() : count(0), value(0.0) {}

    UP create() const override { return UP(new AvgAggregator()); }
    void clear() override { count = 0; value = 0.0; }
    void add(double v) override { ++count;value += v; }
    double get() const override { return (count == 0) ? 0.0 : (value / count); }
};

struct SumAggregator : Aggregator {
    double value;

    SumAggregator() : value(0.0) {}
    UP create() const override { return UP(new SumAggregator()); }
    void clear() override { value = 0.0; }
    void add(double v) override { value += v; }
    double get() const override { return value; }
};

Aggregator::UP
create_aggregator(const std::string &name) {
    if (name == "max") {
        return Aggregator::UP(new MaxAggregator());
    }
    if (name == "avg") {
        return Aggregator::UP(new AvgAggregator());
    }
    if (name == "sum") {
        return Aggregator::UP(new SumAggregator());
    }
    return Aggregator::UP(nullptr);
}

//-----------------------------------------------------------------------------

typedef double (*function_5)(double, double, double, double, double);

using OutputSpec = std::pair<function_5, Aggregator::UP>;

//-----------------------------------------------------------------------------

struct VectorizedQueryTerms {
    struct Term {
        fef::TermFieldHandle handle;
        int weight;

        Term(fef::TermFieldHandle handle_in, int weight_in)
            : handle(handle_in), weight(weight_in)
        {}
    };

    std::vector<fef::TermFieldHandle> handles;
    std::vector<int> weights;
    int              total_weight;

    VectorizedQueryTerms(const VectorizedQueryTerms &) = delete;

    VectorizedQueryTerms(VectorizedQueryTerms &&rhs)
        : handles(std::move(rhs.handles)), weights(std::move(rhs.weights)),
          total_weight(rhs.total_weight)
    {}

    VectorizedQueryTerms(const fef::IQueryEnvironment &env, uint32_t field_id)
        : handles(), weights(), total_weight(0)
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
                        total_weight += term_weight;
                        terms.push_back(Term(tfd.getHandle(), term_weight));
                    }
                }
            }
        }
        handles.reserve(terms.size());
        weights.reserve(terms.size());
        for (size_t i = 0; i < terms.size(); ++i) {
            handles.push_back(terms[i].handle);
            weights.push_back(terms[i].weight);
        }
    }

    ~VectorizedQueryTerms();
};

VectorizedQueryTerms::~VectorizedQueryTerms() = default;

//-----------------------------------------------------------------------------

struct State {
    uint32_t element_length;
    uint32_t matched_terms;
    int      sum_term_weight;
    uint32_t last_pos;
    double   sum_proximity_score;
    uint32_t last_idx;
    uint32_t num_in_order;

    double proximity;
    double order;
    double query_coverage;
    double field_coverage;
    double element_weight;

    State(uint32_t element_length_in, int32_t element_weight_in,
          uint32_t first_pos, int32_t first_weight, uint32_t first_idx)
        : element_length(element_length_in),
          matched_terms(1), sum_term_weight(first_weight),
          last_pos(first_pos), sum_proximity_score(0.0),
          last_idx(first_idx), num_in_order(0),
          proximity(0.0), order(0.0),
          query_coverage(0.0), field_coverage(0.0),
          element_weight(element_weight_in)
    {}

    double proximity_score(uint32_t dist) {
        return (dist > 8) ? 0 : (1.0 - (((dist - 1) / 8.0) * ((dist - 1) / 8.0)));
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

    void calculate_scores(int total_term_weight) {
        element_length = std::max(element_length, matched_terms);
        double matches = matched_terms;
        if (matches < 2) {
            proximity = proximity_score(element_length);
            order = matches;
        } else {
            proximity = sum_proximity_score / (matches - 1);
            order = num_in_order / (double) (matches - 1);
        }
        query_coverage = sum_term_weight / (double) total_term_weight;
        field_coverage = matches / (double) element_length;
    }
};

//-----------------------------------------------------------------------------

class ElementSimilarityExecutor : public fef::FeatureExecutor
{
private:
    using ITR = fef::TermFieldMatchData::PositionsIterator;

    struct CmpPosition {
        ITR *pos;

        CmpPosition(ITR *pos_in) : pos(pos_in) {}

        bool operator()(uint16_t a, uint16_t b) {
            return (pos[a]->getPosition() == pos[b]->getPosition())
                   ? (a < b)
                   : (pos[a]->getPosition() < pos[b]->getPosition());
        }
    };

    struct CmpElement {
        ITR *pos;

        CmpElement(ITR *pos_in) : pos(pos_in) {}

        bool operator()(uint16_t a, uint16_t b) {
            return pos[a]->getElementId() < pos[b]->getElementId();
        }
    };

    using PositionQueue = vespalib::PriorityQueue<uint16_t, CmpPosition>;
    using ElementQueue = vespalib::PriorityQueue<uint16_t, CmpElement>;

    VectorizedQueryTerms    _terms;
    std::vector<ITR>        _pos;
    std::vector<ITR>        _end;
    PositionQueue           _position_queue;
    ElementQueue            _element_queue;
    std::vector<OutputSpec> _outputs;
    const fef::MatchData   *_md;

public:
    ElementSimilarityExecutor(VectorizedQueryTerms &&terms, std::vector<OutputSpec> &&outputs_in);
    ~ElementSimilarityExecutor() override;

    bool isPure() override { return _terms.handles.empty(); }

    void handle_bind_match_data(const fef::MatchData &md) override {
        _md = &md;
    }

    // take the front term in the position queue,
    // iterate to its next element (or end), and
    // put it back into the element queue if possible
    void requeue_pos_front(uint32_t element) {
        uint16_t term = _position_queue.front();
        _position_queue.pop_front();
        while (_pos[term] != _end[term] && (_pos[term]->getElementId() == element)) {
            ++_pos[term];
        }
        if (_pos[term] != _end[term]) {
            _element_queue.push(term);
        }
    }

    void execute(uint32_t docId) override {
        for (auto &output: _outputs) {
            output.second->clear();
        }
        for (size_t i = 0; i < _terms.handles.size(); ++i) {
            const fef::TermFieldMatchData *tfmd = _md->resolveTermField(_terms.handles[i]);
            if (tfmd->has_ranking_data(docId)) {
                _pos[i] = tfmd->begin();
                _end[i] = tfmd->end();
                if (_pos[i] != _end[i]) {
                    _element_queue.push(i);
                }
            }
        }
        while (!_element_queue.empty()) {
            uint32_t elementId = _pos[_element_queue.front()]->getElementId();
            while (!_element_queue.empty() && _pos[_element_queue.front()]->getElementId() == elementId) {
                _position_queue.push(_element_queue.front());
                _element_queue.pop_front();
            }
            uint16_t first = _position_queue.front();
            State state(_pos[first]->getElementLen(),
                        _pos[first]->getElementWeight(),
                        _pos[first]->getPosition(),
                        _terms.weights[first],
                        first);
            requeue_pos_front(elementId);
            while (!_position_queue.empty()) {
                uint16_t item = _position_queue.front();
                if (state.want_match(_pos[item]->getPosition())) {
                    state.addMatch(_pos[item]->getPosition(), _terms.weights[item], item);
                    requeue_pos_front(elementId);
                } else {
                    ++_pos[item];
                    if (_pos[item] == _end[item] || _pos[item]->getElementId() != elementId) {
                        requeue_pos_front(elementId);
                    } else {
                        _position_queue.adjust();
                    }
                }
            }
            state.calculate_scores(_terms.total_weight);
            for (auto &output: _outputs) {
                output.second->add(output.first(state.proximity, state.order,
                                                state.query_coverage, state.field_coverage,
                                                state.element_weight));
            }
        }
        for (size_t i = 0; i < _outputs.size(); ++i) {
            outputs().set_number(i, _outputs[i].second->get());
        }
    }
};

ElementSimilarityExecutor::ElementSimilarityExecutor(VectorizedQueryTerms &&terms, std::vector<OutputSpec> &&outputs_in)
    : _terms(std::move(terms)),
      _pos(_terms.handles.size(), nullptr),
      _end(_terms.handles.size(), nullptr),
      _position_queue(CmpPosition(_pos.data())),
      _element_queue(CmpElement(_pos.data())),
      _outputs(std::move(outputs_in)),
      _md(nullptr)
{ }
ElementSimilarityExecutor::~ElementSimilarityExecutor() = default;

//-----------------------------------------------------------------------------

std::vector<std::pair<std::string, std::string> >
extract_properties(const fef::Properties &props, const std::string &ns,
                   const std::string &first_name, const std::string &first_default)
{
    struct MyVisitor : fef::IPropertiesVisitor {
        const std::string &first_name;
        std::vector<std::pair<std::string, std::string> > &result;

        MyVisitor(const std::string &first_name_in,
                  std::vector<std::pair<std::string, std::string> > &result_in)
            : first_name(first_name_in), result(result_in)
        {}

        void visitProperty(const fef::Property::Value &key, const fef::Property &values) override {
            if (key != first_name) {
                result.emplace_back(key, values.get());
            }
        }
    };
    std::vector<std::pair<std::string, std::string> > result;
    result.emplace_back(first_name, props.lookup(ns, first_name).get(first_default));
    MyVisitor my_visitor(first_name, result);
    props.visitNamespace(ns, my_visitor);
    return result;
}

std::vector<std::pair<std::string, std::string> >
get_outputs(const fef::Properties &props, const std::string &feature) {
    return extract_properties(props, feature + ".output", "default", "max((0.35*p+0.15*o+0.30*q+0.20*f)*w)");
}

} // namespace features::<unnamed>

//-----------------------------------------------------------------------------

struct ElementSimilarityBlueprint::OutputContext {
    vespalib::eval::CompileCache::Token::UP compile_token;
    Aggregator::UP aggregator_factory;

    OutputContext(const vespalib::eval::Function &function, Aggregator::UP aggregator)
        : compile_token(vespalib::eval::CompileCache::compile(function, vespalib::eval::PassParams::SEPARATE)),
          aggregator_factory(std::move(aggregator))
    {}
};

//-----------------------------------------------------------------------------

ElementSimilarityBlueprint::ElementSimilarityBlueprint()
    : Blueprint("elementSimilarity"), _field_id(fef::IllegalHandle), _outputs()
{}

ElementSimilarityBlueprint::~ElementSimilarityBlueprint() = default;

void
ElementSimilarityBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &env,
                                              fef::IDumpFeatureVisitor &visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const fef::FieldInfo &field = *env.getField(i);
        if ((field.type() == fef::FieldType::INDEX) &&
            (field.collection() != CollectionType::SINGLE) &&
            (!field.isFilter()))
        {
            fef::FeatureNameBuilder fnb;
            fnb.baseName(getBaseName()).parameter(field.name());
            auto outputs = get_outputs(env.getProperties(), fnb.buildName());
            visitor.visitDumpFeature(fnb.output("").buildName());
            for (size_t out_idx = 1; out_idx < outputs.size(); ++out_idx) {
                visitor.visitDumpFeature(fnb.output(outputs[out_idx].first).buildName());
            }
        }
    }
}

bool
ElementSimilarityBlueprint::setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params)
{
    const fef::FieldInfo *field = params[0].asField();
    _field_id = field->id();
    fef::FeatureNameBuilder fnb;
    fnb.baseName(getBaseName()).parameter(field->name());
    auto outputs = get_outputs(env.getProperties(), fnb.buildName());
    for (const auto &entry: outputs) {
        describeOutput(entry.first, entry.second);
        std::string aggr_name;
        std::string expr;
        std::string error;
        if (!vespalib::eval::Function::unwrap(entry.second, aggr_name, expr, error)) {
            LOG(warning,
                "'%s': could not extract aggregator and expression for output '%s' from config value '%s' (%s)",
                fnb.buildName().c_str(), entry.first.c_str(), entry.second.c_str(), error.c_str());
            return false;
        }
        Aggregator::UP aggr = create_aggregator(aggr_name);
        if (aggr.get() == nullptr) {
            LOG(warning, "'%s': unknown aggregator '%s'", fnb.buildName().c_str(), aggr_name.c_str());
            return false;
        }
        std::vector<std::string> args({"p", "o", "q", "f", "w"});
        auto function = vespalib::eval::Function::parse(args, expr);
        if (function->has_error()) {
            LOG(warning, "'%s': per-element expression parse error: %s",
                fnb.buildName().c_str(), function->get_error().c_str());
            return false;
        }
        _outputs.push_back(OutputContext_UP(new OutputContext(*function, std::move(aggr))));
    }
    return true;
}

fef::FeatureExecutor &
ElementSimilarityBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    std::vector<OutputSpec> output_specs;
    for (const auto &output: _outputs) {
        output_specs.emplace_back(output->compile_token->get().get_function<5>(),
                                  output->aggregator_factory->create());
    }
    return stash.create<ElementSimilarityExecutor>(VectorizedQueryTerms(env, _field_id), std::move(output_specs));
}

}
