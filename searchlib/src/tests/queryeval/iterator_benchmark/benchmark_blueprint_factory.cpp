// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_ctx_builder.h"
#include "benchmark_blueprint_factory.h"
#include "benchmark_searchable.h"
#include "disk_index_builder.h"
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <cmath>

using search::query::IntegerTermVector;
using search::query::MultiTerm;
using search::query::Node;
using search::query::SimpleDotProduct;
using search::query::SimpleInTerm;
using search::query::SimpleStringTerm;
using search::query::SimpleWandTerm;
using search::query::SimpleWeightedSetTerm;
using search::query::Weight;

namespace search::queryeval::test {

namespace {

const vespalib::string field_name = "myfield";
const vespalib::string index_dir = "indexdir";

uint32_t
calc_hits_per_term(uint32_t num_docs, double op_hit_ratio, uint32_t children, QueryOperator query_op)
{
    if (query_op == QueryOperator::And) {
        double child_hit_ratio = std::pow(op_hit_ratio, (1.0/(double)children));
        return num_docs * child_hit_ratio;
    } else {
        uint32_t op_num_hits = num_docs * op_hit_ratio;
        return op_num_hits / children;
    }
}

std::unique_ptr<BenchmarkSearchable>
make_searchable(const FieldConfig& cfg, uint32_t num_docs, const HitSpecs& hit_specs, bool disjunct_terms)
{
    if (cfg.is_attr()) {
        AttributeContextBuilder builder;
        builder.add(cfg.attr_cfg(), field_name, num_docs, hit_specs, disjunct_terms);
        return builder.build();
    } else {
        uint32_t docid_limit = num_docs + 1;
        DiskIndexBuilder builder(cfg.index_cfg(), index_dir, docid_limit, hit_specs.size());
        for (auto spec : hit_specs) {
            // TODO: make number of occurrences configurable.
            uint32_t num_occs = 1;
            builder.add_word(std::to_string(spec.term_value), *random_docids(docid_limit, spec.num_hits), num_occs);
        }
        return builder.build();
    }
}

std::unique_ptr<Node>
make_query_node(QueryOperator query_op, const TermVector& terms)
{
    if (query_op == QueryOperator::Term) {
        assert(terms.size() == 1);
        return std::make_unique<SimpleStringTerm>(std::to_string(terms[0]), field_name, 0, Weight(1));
    } else if (query_op == QueryOperator::In) {
        auto termv = std::make_unique<IntegerTermVector>(terms.size());
        for (auto term : terms) {
            termv->addTerm(term);
        }
        return std::make_unique<SimpleInTerm>(std::move(termv), MultiTerm::Type::INTEGER, field_name, 0, Weight(1));
    } else if (query_op == QueryOperator::WeightedSet) {
        auto res = std::make_unique<SimpleWeightedSetTerm>(terms.size(), field_name, 0, Weight(1));
        for (auto term : terms) {
            res->addTerm(term, Weight(1));
        }
        return res;
    } else if (query_op == QueryOperator::DotProduct) {
        auto res = std::make_unique<SimpleDotProduct>(terms.size(), field_name, 0, Weight(1));
        for (auto term : terms) {
            res->addTerm(term, Weight(1));
        }
        return res;
    } else if (query_op == QueryOperator::ParallelWeakAnd) {
        // These config values match the defaults (see WandItem.java):
        uint32_t target_hits = 100;
        int64_t score_threshold = 0;
        double threshold_boost_factor = 1.0;
        auto res = std::make_unique<SimpleWandTerm>(terms.size(), field_name, 0, Weight(1),
                                                    target_hits, score_threshold, threshold_boost_factor);
        for (auto term : terms) {
            res->addTerm(term, Weight(random_int(1, 100)));
        }
        return res;
    }
    return {};
}

Blueprint::UP
make_leaf_blueprint(const Node& node, BenchmarkSearchable& searchable, uint32_t docid_limit)
{
    auto blueprint = searchable.create_blueprint(FieldSpec(field_name, 0, 0), node);
    assert(blueprint.get());
    blueprint->setDocIdLimit(docid_limit);
    blueprint->update_flow_stats(docid_limit);
    return blueprint;
}

Blueprint::UP
make_intermediate_blueprint(std::unique_ptr<IntermediateBlueprint> blueprint, BenchmarkSearchable& searchable, const TermVector& terms, uint32_t docid_limit)
{
    auto* weak_and = blueprint->asWeakAnd();
    for (auto term : terms) {
        SimpleStringTerm sterm(std::to_string(term), field_name, 0, Weight(1));
        auto child = make_leaf_blueprint(sterm, searchable, docid_limit);
        if (weak_and != nullptr) {
            weak_and->addTerm(std::move(child), random_int(1, 100));
        } else {
            blueprint->addChild(std::move(child));
        }
    }
    blueprint->setDocIdLimit(docid_limit);
    blueprint->update_flow_stats(docid_limit);
    return blueprint;
}

Blueprint::UP
make_blueprint_helper(BenchmarkSearchable& searchable, QueryOperator query_op, const TermVector& terms, uint32_t docid_limit)
{
    if (query_op == QueryOperator::And) {
        return make_intermediate_blueprint(std::make_unique<AndBlueprint>(), searchable, terms, docid_limit);
    } else if (query_op == QueryOperator::Or) {
        return make_intermediate_blueprint(std::make_unique<OrBlueprint>(), searchable, terms, docid_limit);
    } else if (query_op == QueryOperator::WeakAnd) {
        uint32_t target_hits = 100;
        return make_intermediate_blueprint(std::make_unique<WeakAndBlueprint>(target_hits), searchable, terms, docid_limit);
    } else {
        auto query_node = make_query_node(query_op, terms);
        return make_leaf_blueprint(*query_node, searchable, docid_limit);
    }
}

/**
 * Factory for creating a Blueprint for a given benchmark setup.
 *
 * This populates an attribute or disk index field such that the query operator hits
 * the given ratio of the total document corpus.
 */
class MyFactory : public BenchmarkBlueprintFactory {
private:
    QueryOperator _query_op;
    uint32_t _docid_limit;
    TermVector _terms;
    std::unique_ptr<BenchmarkSearchable> _searchable;

public:
    MyFactory(const FieldConfig& field_cfg, QueryOperator query_op,
              uint32_t num_docs, uint32_t default_values_per_document,
              double op_hit_ratio, uint32_t children, bool disjunct_children);

    std::unique_ptr<Blueprint> make_blueprint() override;
    vespalib::string get_name(Blueprint& blueprint) const override {
        return get_class_name(blueprint);
    }
};

MyFactory::MyFactory(const FieldConfig& field_cfg, QueryOperator query_op,
                     uint32_t num_docs, uint32_t default_values_per_document,
                     double op_hit_ratio, uint32_t children, bool disjunct_children)
    : _query_op(query_op),
      _docid_limit(num_docs + 1),
      _terms(),
      _searchable()
{
    uint32_t hits_per_term = calc_hits_per_term(num_docs, op_hit_ratio, children, query_op);
    HitSpecs hit_specs(55555);
    if (!disjunct_children) {
        hit_specs.add(default_values_per_document, num_docs);
    }
    _terms = hit_specs.add(children, hits_per_term);
    if (disjunct_children && default_values_per_document != 0) {
        // This ensures that the remaining docids are populated with a "default value".
        // Only a single default value is supported.
        uint32_t op_num_hits = num_docs * op_hit_ratio;
        hit_specs.add(1, num_docs - op_num_hits);
    }
    _searchable = make_searchable(field_cfg, num_docs, hit_specs, disjunct_children);
}

std::unique_ptr<Blueprint>
MyFactory::make_blueprint()
{
    return make_blueprint_helper(*_searchable, _query_op, _terms, _docid_limit);
}

}

std::unique_ptr<BenchmarkBlueprintFactory>
make_blueprint_factory(const FieldConfig& field_cfg, QueryOperator query_op,
                       uint32_t num_docs, uint32_t default_values_per_document,
                       double op_hit_ratio, uint32_t children, bool disjunct_children)
{
    return std::make_unique<MyFactory>(field_cfg, query_op, num_docs, default_values_per_document, op_hit_ratio, children, disjunct_children);
}

}

