// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <numeric>
#include <random>
#include <vector>

using namespace search::attribute::test;
using namespace search::attribute;
using namespace search::fef;
using namespace search::query;
using namespace search::queryeval;
using namespace search;
using namespace vespalib;

// TODO: Re-seed for each benchmark setup
constexpr uint32_t default_seed = 1234;
std::mt19937 gen(default_seed);
const vespalib::string field = "myfield";
double budget_sec = 1.0;
using DocidVector = std::vector<uint32_t>;

BitVector::UP
random_docids(uint32_t docid_limit, uint32_t count)
{
    auto res = BitVector::create(docid_limit);
    uint32_t docids_left = count;
    // Bit 0 is never set since it is reserved as docid 0.
    // All other docids have equal probability to be set.
    for (uint32_t docid = 1; docid < docid_limit; ++docid) {
        std::uniform_int_distribution<uint32_t> distr(0, docid_limit - docid - 1);
        if (distr(gen) < docids_left) {
            res->setBit(docid);
            --docids_left;
        }
    }
    res->invalidateCachedCount();
    assert(res->countTrueBits() == count);
    return res;
}

struct HitSpec {
    uint32_t term_value;
    uint32_t num_hits;
    HitSpec(uint32_t term_value_in, uint32_t num_hits_in) : term_value(term_value_in), num_hits(num_hits_in) {}
};

using HitSpecs = std::vector<HitSpec>;

HitSpecs
make_hit_specs(uint32_t num_terms, uint32_t hits_per_term, uint32_t first_term_value)
{
    HitSpecs res;
    for (uint32_t i = 0; i < num_terms; ++i) {
        res.push_back({first_term_value + i, hits_per_term});
    }
    return res;
}

template <typename AttributeType, bool is_string, bool is_multivalue>
void
populate_attribute(AttributeType& attr, uint32_t docid_limit, const HitSpecs& hit_specs)
{
    for (auto spec : hit_specs) {
        auto docids = random_docids(docid_limit, spec.num_hits);
        docids->foreach_truebit([&](uint32_t docid) {
            if constexpr (is_string) {
                if constexpr (is_multivalue) {
                    attr.append(docid, std::to_string(spec.term_value), 1);
                } else {
                    attr.update(docid, std::to_string(spec.term_value));
                }
            } else {
                if constexpr (is_multivalue) {
                    attr.append(docid, spec.term_value, 1);
                } else {
                    attr.update(docid, spec.term_value);
                }
            }
        });
        attr.commit(true);
    }
}

AttributeVector::SP
make_attribute(const Config& cfg, uint32_t num_docs, const HitSpecs& hit_specs)
{
    auto attr = AttributeFactory::createAttribute(field, cfg);
    attr->addReservedDoc();
    attr->addDocs(num_docs);
    uint32_t docid_limit = attr->getNumDocs();
    assert(docid_limit == (num_docs + 1));
    bool is_multivalue = cfg.collectionType() != CollectionType::SINGLE;
    if (attr->isStringType()) {
        auto& real = dynamic_cast<StringAttribute&>(*attr);
        if (is_multivalue) {
            populate_attribute<StringAttribute, true, true>(real, docid_limit, hit_specs);
        } else {
            populate_attribute<StringAttribute, true, false>(real, docid_limit, hit_specs);
        }
    } else {
        auto& real = dynamic_cast<IntegerAttribute&>(*attr);
        if (is_multivalue) {
            populate_attribute<IntegerAttribute, false, true>(real, docid_limit, hit_specs);
        } else {
            populate_attribute<IntegerAttribute, false, false>(real, docid_limit, hit_specs);
        }
    }
    return attr;
}

std::unique_ptr<IAttributeContext>
make_attribute_context(const Config& cfg, uint32_t num_docs, const HitSpecs& hit_specs)
{
    auto attr = make_attribute(cfg, num_docs, hit_specs);
    auto res = std::make_unique<MockAttributeContext>();
    res->add(std::move(attr));
    return res;
}

struct BenchmarkResult {
    double time_ms;
    uint32_t seeks;
    uint32_t hits;
    double estimate;
    double cost;
    vespalib::string iterator_name;
    BenchmarkResult() : BenchmarkResult(0, 0, 0, 0, 0, "") {}
    BenchmarkResult(double time_ms_in, uint32_t seeks_in, uint32_t hits_in, double estimate_in, double cost_in, const vespalib::string& iterator_name_in)
        : time_ms(time_ms_in), seeks(seeks_in), hits(hits_in), estimate(estimate_in), cost(cost_in), iterator_name(iterator_name_in) {}
    double time_per_seek_ns() const { return (time_ms / seeks) * 1000.0 * 1000.0; }
    double time_per_cost_ms() const { return (time_ms / cost); }
};

BenchmarkResult
strict_search(SearchIterator& itr, uint32_t docid_limit, double estimate, double strict_cost)
{
    BenchmarkTimer timer(budget_sec);
    uint32_t hits = 0;
    while (timer.has_budget()) {
        timer.before();
        hits = 0;
        itr.initRange(1, docid_limit);
        uint32_t docid = itr.seekFirst(1);
        while (docid < docid_limit) {
            ++hits;
            docid = itr.seekNext(docid + 1);
        }
        timer.after();
    }
    return {timer.min_time() * 1000.0, hits + 1, hits, estimate, strict_cost, itr.getClassName()};
}

BenchmarkResult
non_strict_search(SearchIterator& itr, uint32_t docid_limit, double estimate, double non_strict_cost)
{
    BenchmarkTimer timer(budget_sec);
    uint32_t seeks = 0;
    uint32_t hits = 0;
    while (timer.has_budget()) {
        timer.before();
        seeks = 0;
        hits = 0;
        itr.initRange(1, docid_limit);
        for (uint32_t docid = 1; !itr.isAtEnd(docid); ++docid) {
            ++seeks;
            if (itr.seek(docid)) {
                ++hits;
            }
        }
        timer.after();
    }
    return {timer.min_time() * 1000.0, seeks, hits, estimate, non_strict_cost, itr.getClassName()};
}

BenchmarkResult
benchmark_search(Blueprint::UP blueprint, uint32_t docid_limit, bool strict)
{
    blueprint->sort(strict, true);
    blueprint->fetchPostings(ExecuteInfo::createForTest(strict));
    // Note: All blueprints get the same TermFieldMatchData instance.
    //       This is OK as long as we don't do unpacking and only use 1 thread.
    auto md = MatchData::makeTestInstance(1, 1);
    if (strict) {
        auto iterator = blueprint->createSearch(*md, true);
        assert(iterator.get());
        return strict_search(*iterator, docid_limit, blueprint->estimate(), blueprint->strict_cost());
    } else {
        auto iterator = blueprint->createSearch(*md, false);
        assert(iterator.get());
        return non_strict_search(*iterator, docid_limit, blueprint->estimate(), blueprint->cost());
    }
}

Blueprint::UP
make_leaf_blueprint(const Node& node, IAttributeContext& attr_ctx, uint32_t docid_limit)
{
    FakeRequestContext request_ctx(&attr_ctx);
    AttributeBlueprintFactory source;
    auto blueprint = source.createBlueprint(request_ctx, FieldSpec(field, 0, 0), node);
    assert(blueprint.get());
    blueprint->setDocIdLimit(docid_limit);
    blueprint->update_flow_stats(docid_limit);
    return blueprint;
}

enum class QueryOperator {
    Term,
    In,
    WeightedSet,
    DotProduct,
    And,
    Or
};

vespalib::string
to_string(QueryOperator query_op)
{
    switch (query_op) {
        case QueryOperator::Term: return "Term";
        case QueryOperator::In: return "In";
        case QueryOperator::WeightedSet: return "WeightedSet";
        case QueryOperator::DotProduct: return "DotProduct";
        case QueryOperator::And: return "And";
        case QueryOperator::Or: return "Or";
    }
    return "unknown";
}

std::unique_ptr<Node>
make_query_node(QueryOperator query_op, const HitSpecs& hit_specs)
{
    if (query_op == QueryOperator::Term) {
        assert(hit_specs.size() == 1);
        return std::make_unique<SimpleNumberTerm>(std::to_string(hit_specs[0].term_value), field, 0, Weight(1));
    } else if (query_op == QueryOperator::In) {
        auto terms = std::make_unique<IntegerTermVector>(hit_specs.size());
        for (auto spec : hit_specs) {
            terms->addTerm(spec.term_value);
        }
        return std::make_unique<SimpleInTerm>(std::move(terms), MultiTerm::Type::INTEGER, field, 0, Weight(1));
    } else if (query_op == QueryOperator::WeightedSet) {
        auto res = std::make_unique<SimpleWeightedSetTerm>(hit_specs.size(), field, 0, Weight(1));
        for (auto spec : hit_specs) {
            res->addTerm(spec.term_value, Weight(1));
        }
        return res;
    } else if (query_op == QueryOperator::DotProduct) {
        auto res = std::make_unique<SimpleDotProduct>(hit_specs.size(), field, 0, Weight(1));
        for (auto spec : hit_specs) {
            res->addTerm(spec.term_value, Weight(1));
        }
        return res;
    }
    return {};
}

template <typename BlueprintType>
Blueprint::UP
make_intermediate_blueprint(IAttributeContext& attr_ctx, const HitSpecs& hit_specs, uint32_t docid_limit)
{
    auto blueprint = std::make_unique<BlueprintType>();
    for (auto spec : hit_specs) {
        SimpleNumberTerm term(std::to_string(spec.term_value), field, 0, Weight(1));
        auto child = make_leaf_blueprint(term, attr_ctx, docid_limit);
        blueprint->addChild(std::move(child));
    }
    blueprint->setDocIdLimit(docid_limit);
    blueprint->update_flow_stats(docid_limit);
    return blueprint;
}

BenchmarkResult
run_benchmark(IAttributeContext& attr_ctx, QueryOperator query_op, const HitSpecs& hit_specs, uint32_t docid_limit, bool strict)
{
    if (query_op == QueryOperator::And) {
        return benchmark_search(make_intermediate_blueprint<AndBlueprint>(attr_ctx, hit_specs, docid_limit), docid_limit, strict);
    } else if (query_op == QueryOperator::Or) {
        return benchmark_search(make_intermediate_blueprint<OrBlueprint>(attr_ctx, hit_specs, docid_limit), docid_limit, strict);
    } else {
        auto query_node = make_query_node(query_op, hit_specs);
        auto blueprint = make_leaf_blueprint(*query_node, attr_ctx, docid_limit);
        return benchmark_search(std::move(blueprint), docid_limit, strict);
    }
}

void
run_benchmark(const Config& cfg, uint32_t num_docs, const HitSpecs& hit_specs, double hit_ratio, QueryOperator query_op, bool strict)
{
    auto attr_ctx = make_attribute_context(cfg, num_docs, hit_specs);
    BenchmarkResult res = run_benchmark(*attr_ctx, query_op, hit_specs, num_docs + 1, strict);

    std::cout << std::fixed << std::setprecision(3)
        << "children=" << std::setw(4) << hit_specs.size()
        << ", strict=" << std::setw(5) << (strict ? "true" : "false")
        << ", t_ratio=" << std::setw(5) << hit_ratio
        << ", a_ratio=" << std::setw(5) << ((double)res.hits / (double)num_docs)
        << ", est=" << std::setw(5) << res.estimate
        << ", hits=" << std::setw(7) << res.hits
        << ", seeks=" << std::setw(8) << res.seeks
        << std::setprecision(2)
        << ", time_ms:" << std::setw(8) << res.time_ms
        << std::setprecision(3)
        << ", cost=" << std::setw(8) << res.cost
        << std::setprecision(2)
        << ", ns_per_seek=" << std::setw(8) << res.time_per_seek_ns()
        << ", ms_per_cost=" << std::setw(7) << res.time_per_cost_ms()
        << ", itr=" << res.iterator_name << std::endl;
}

struct BenchmarkSetup {
    uint32_t num_docs;
    Config attr_cfg;
    QueryOperator query_op;
    std::vector<double> hit_ratios;
    std::vector<uint32_t> child_counts;
    std::vector<bool> strictness;
    BenchmarkSetup(uint32_t num_docs_in,
                   Config attr_cfg_in,
                   QueryOperator query_op_in,
                   const std::vector<double>& hit_ratios_in,
                   const std::vector<uint32_t>& child_counts_in,
                   const std::vector<bool>& strictness_in)
        : num_docs(num_docs_in),
          attr_cfg(attr_cfg_in),
          query_op(query_op_in),
          hit_ratios(hit_ratios_in),
          child_counts(child_counts_in),
          strictness(strictness_in)
    {}
    ~BenchmarkSetup() {}
};

uint32_t
calc_hits_per_term(uint32_t num_docs, double target_hit_ratio, uint32_t children, QueryOperator query_op)
{
    if (query_op == QueryOperator::And) {
        double child_hit_ratio = std::pow(target_hit_ratio, (1.0/(double)children));
        return num_docs * child_hit_ratio;
    } else {
        uint32_t target_num_hits = num_docs * target_hit_ratio;
        return target_num_hits / children;
    }
}

void
run_benchmarks(const BenchmarkSetup& setup)
{
    std::cout << "-------- run_benchmarks: " << to_string(setup.query_op) << " --------" << std::endl;
    for (bool strict : setup.strictness) {
        for (double hit_ratio : setup.hit_ratios) {
            for (uint32_t children : setup.child_counts) {
                uint32_t hits_per_term = calc_hits_per_term(setup.num_docs, hit_ratio, children, setup.query_op);
                run_benchmark(setup.attr_cfg, setup.num_docs, make_hit_specs(children, hits_per_term, 55555), hit_ratio, setup.query_op, strict);
            }
        }
    }
}

Config
make_config(BasicType basic_type, CollectionType col_type, bool fast_search)
{
    Config res(basic_type, col_type);
    res.setFastSearch(fast_search);
    return res;
}

constexpr uint32_t num_docs = 10'000'000;
const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.5};
const std::vector<uint32_t> child_counts = {1, 10, 100, 1000};
const Config int32_fs = make_config(BasicType::INT32, CollectionType::SINGLE, true);
const Config int32_array_fs = make_config(BasicType::INT32, CollectionType::ARRAY, true);
const Config int32_wset_fs = make_config(BasicType::INT32, CollectionType::WSET, true);

TEST(IteratorBenchmark, term_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_fs, QueryOperator::Term, hit_ratios, {1}, {true, false});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, in_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_array_fs, QueryOperator::In, hit_ratios, child_counts, {true, false});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, weighted_set_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_array_fs, QueryOperator::WeightedSet, hit_ratios, child_counts, {true, false});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, dot_product_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_wset_fs, QueryOperator::DotProduct, hit_ratios, child_counts, {true, false});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, and_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_array_fs, QueryOperator::And, hit_ratios, {1, 2, 4, 8}, {true, false});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, or_benchmark)
{
    BenchmarkSetup setup(num_docs, int32_array_fs, QueryOperator::Or, hit_ratios, child_counts, {true, false});
    run_benchmarks(setup);
}

GTEST_MAIN_RUN_ALL_TESTS()
