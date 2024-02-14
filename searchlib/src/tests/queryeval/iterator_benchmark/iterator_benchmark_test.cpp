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
#include <vespa/vespalib/util/benchmark_timer.h>
#include <cmath>
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
    if ((count + 1) == docid_limit) {
        res->notSelf();
        res->clearBit(0);
        return res;
    }
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

namespace benchmark {
using TermVector = std::vector<uint32_t>;
}

class HitSpecs {
private:
    std::vector<HitSpec> _specs;
    uint32_t _next_term_value;

public:
    HitSpecs(uint32_t first_term_value)
        : _specs(), _next_term_value(first_term_value)
    {
    }
    benchmark::TermVector add(uint32_t num_terms, uint32_t hits_per_term) {
        benchmark::TermVector res;
        for (uint32_t i = 0; i < num_terms; ++i) {
            uint32_t term_value = _next_term_value++;
            _specs.push_back({term_value, hits_per_term});
            res.push_back(term_value);
        }
        return res;
    }
    auto begin() const { return _specs.begin(); }
    auto end() const { return _specs.end(); }
};

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
    attr->commit(true);
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
    double ns_per_seek() const { return (time_ms / seeks) * 1000.0 * 1000.0; }
    double ms_per_cost() const { return (time_ms / cost); }
};

struct Stats {
    double average;
    double median;
    double std_dev;
    Stats() : average(0.0), median(0.0), std_dev(0.0) {}
    Stats(double average_in, double median_in, double std_dev_in)
        : average(average_in), median(median_in), std_dev(std_dev_in)
    {}
    vespalib::string to_string() const {
        std::ostringstream oss;
        oss << std::fixed << std::setprecision(3);
        oss << "{average=" << average << ", median=" << median << ", std_dev=" << std_dev << "}";
        return oss.str();
    }
};

class BenchmarkResults {
private:
    std::vector<BenchmarkResult> _results;

    template <typename F>
    std::vector<double> extract_sorted_values(F func) const {
        std::vector<double> values;
        for (const auto& res: _results) {
            values.push_back(func(res));
        }
        std::sort(values.begin(), values.end());
        return values;
    }

    double calc_median(const std::vector<double>& values) const {
        size_t middle = values.size() / 2;
        if (values.size() % 2 == 0) {
            return (values[middle - 1] + values[middle]) / 2;
        } else {
            return values[middle];
        }
    }

    double calc_standard_deviation(const std::vector<double>& values, double average) const {
        double deviations = 0.0;
        for (double val : values) {
            double diff = val - average;
            deviations += (diff * diff);
        }
        double variance = deviations / values.size();
        return std::sqrt(variance);
    }

    template <typename F>
    Stats calc_stats(F func) const {
        auto values = extract_sorted_values(func);
        double average = std::accumulate(values.begin(), values.end(), 0.0) / values.size();
        double median = calc_median(values);
        double std_dev = calc_standard_deviation(values, average);
        return {average, median, std_dev};
    }

public:
    BenchmarkResults(): _results() {}
    void add(const BenchmarkResult& res) {
        _results.push_back(res);
    }
    Stats time_ms_stats() const {
        return calc_stats([](const auto& res){ return res.time_ms; });
    }
    Stats ns_per_seek_stats() const {
        return calc_stats([](const auto& res){ return res.ns_per_seek(); });
    }
    Stats ms_per_cost_stats() const {
        return calc_stats([](const auto& res){ return res.ms_per_cost(); });
    }
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

vespalib::string
to_string(const Config& attr_config)
{
    auto col_type = attr_config.collectionType();
    auto basic_type = attr_config.basicType();
    if (col_type == CollectionType::SINGLE) {
        return basic_type.asString();
    }
    return vespalib::string(col_type.asString()) + "<" + vespalib::string(basic_type.asString()) + ">";
}

std::unique_ptr<Node>
make_query_node(QueryOperator query_op, const benchmark::TermVector& terms)
{
    if (query_op == QueryOperator::Term) {
        assert(terms.size() == 1);
        return std::make_unique<SimpleNumberTerm>(std::to_string(terms[0]), field, 0, Weight(1));
    } else if (query_op == QueryOperator::In) {
        auto termv = std::make_unique<IntegerTermVector>(terms.size());
        for (auto term : terms) {
            termv->addTerm(term);
        }
        return std::make_unique<SimpleInTerm>(std::move(termv), MultiTerm::Type::INTEGER, field, 0, Weight(1));
    } else if (query_op == QueryOperator::WeightedSet) {
        auto res = std::make_unique<SimpleWeightedSetTerm>(terms.size(), field, 0, Weight(1));
        for (auto term : terms) {
            res->addTerm(term, Weight(1));
        }
        return res;
    } else if (query_op == QueryOperator::DotProduct) {
        auto res = std::make_unique<SimpleDotProduct>(terms.size(), field, 0, Weight(1));
        for (auto term : terms) {
            res->addTerm(term, Weight(1));
        }
        return res;
    }
    return {};
}

template <typename BlueprintType>
Blueprint::UP
make_intermediate_blueprint(IAttributeContext& attr_ctx, const benchmark::TermVector& terms, uint32_t docid_limit)
{
    auto blueprint = std::make_unique<BlueprintType>();
    for (auto term : terms) {
        SimpleNumberTerm sterm(std::to_string(term), field, 0, Weight(1));
        auto child = make_leaf_blueprint(sterm, attr_ctx, docid_limit);
        blueprint->addChild(std::move(child));
    }
    blueprint->setDocIdLimit(docid_limit);
    blueprint->update_flow_stats(docid_limit);
    return blueprint;
}

BenchmarkResult
run_benchmark(IAttributeContext& attr_ctx, QueryOperator query_op, const benchmark::TermVector& terms, uint32_t docid_limit, bool strict)
{
    if (query_op == QueryOperator::And) {
        return benchmark_search(make_intermediate_blueprint<AndBlueprint>(attr_ctx, terms, docid_limit), docid_limit, strict);
    } else if (query_op == QueryOperator::Or) {
        return benchmark_search(make_intermediate_blueprint<OrBlueprint>(attr_ctx, terms, docid_limit), docid_limit, strict);
    } else {
        auto query_node = make_query_node(query_op, terms);
        auto blueprint = make_leaf_blueprint(*query_node, attr_ctx, docid_limit);
        return benchmark_search(std::move(blueprint), docid_limit, strict);
    }
}

void
print_result(const BenchmarkResult& res, const benchmark::TermVector& terms, double hit_ratio, uint32_t num_docs)
{
    std::cout << std::fixed << std::setprecision(3)
              << "children=" << std::setw(4) << terms.size()
              << ", t_ratio=" << std::setw(5) << hit_ratio
              << ", a_ratio=" << std::setw(5) << ((double) res.hits / (double) num_docs)
              << ", est=" << std::setw(5) << res.estimate
              << ", hits=" << std::setw(7) << res.hits
              << ", seeks=" << std::setw(8) << res.seeks
              << std::setprecision(2)
              << ", time_ms=" << std::setw(8) << res.time_ms
              << std::setprecision(3)
              << ", cost=" << std::setw(8) << res.cost
              << std::setprecision(2)
              << ", ns_per_seek=" << std::setw(8) << res.ns_per_seek()
              << ", ms_per_cost=" << std::setw(7) << res.ms_per_cost()
              << ", itr=" << res.iterator_name << std::endl;
}

void
print_results(const BenchmarkResults& results)
{
    std::cout << std::fixed << std::setprecision(3)
              << "statistics summary: time_ms=" << results.time_ms_stats().to_string()
              << ", ns_per_seek=" << results.ns_per_seek_stats().to_string()
              << ", ms_per_cost=" << results.ms_per_cost_stats().to_string() << std::endl << std::endl;
}

struct BenchmarkCase {
    Config attr_cfg;
    QueryOperator query_op;
    bool strict;
    BenchmarkResults results;
    double scaled_cost;
    BenchmarkCase(const Config& attr_cfg_in, QueryOperator query_op_in, bool strict_in, const BenchmarkResults& results_in)
        : attr_cfg(attr_cfg_in),
          query_op(query_op_in),
          strict(strict_in),
          results(results_in),
          scaled_cost(1.0)
    {}
    BenchmarkCase(const BenchmarkCase&);
    BenchmarkCase& operator=(const BenchmarkCase&);
    ~BenchmarkCase();
};

BenchmarkCase::BenchmarkCase(const BenchmarkCase&) = default;
BenchmarkCase& BenchmarkCase::operator=(const BenchmarkCase&) = default;
BenchmarkCase::~BenchmarkCase() = default;

class BenchmarkSummary {
private:
    std::vector<BenchmarkCase> _cases;
    double _baseline_ms_per_cost;

public:
    BenchmarkSummary(double baseline_ms_per_cost_in)
        : _cases(),
          _baseline_ms_per_cost(baseline_ms_per_cost_in)
    {}
    double baseline_ms_per_cost() const { return _baseline_ms_per_cost; }
    void add(const Config& attr_cfg, QueryOperator query_op, bool strict, const BenchmarkResults& results) {
        _cases.emplace_back(attr_cfg, query_op, strict, results);
    }
    void calc_scaled_costs() {
        std::sort(_cases.begin(), _cases.end(), [](const auto& lhs, const auto& rhs) {
            return lhs.results.ms_per_cost_stats().average < rhs.results.ms_per_cost_stats().average;
        });
        for (auto& c : _cases) {
            c.scaled_cost = c.results.ms_per_cost_stats().average / _baseline_ms_per_cost;
        }
    }
    const std::vector<BenchmarkCase>& cases() const { return _cases; }
};

vespalib::string
to_string(QueryOperator query_op, const Config& attr_cfg, bool strict)
{
    return "op=" + to_string(query_op) + ", cfg=" + to_string(attr_cfg) + ", strict=" + (strict ? "true" : "false");
}

void
print_summary(const BenchmarkSummary& summary)
{
    std::cout << "-------- benchmark summary (baseline_ms_per_cost=" << summary.baseline_ms_per_cost() << ") --------" << std::endl;
    for (const auto& c : summary.cases()) {
        std::cout << std::fixed << std::setprecision(3) << ""
                  << std::setw(40) << std::left << to_string(c.query_op, c.attr_cfg, c.strict) << ": "
                  << "ms_per_cost=" << std::setw(7) << std::right << c.results.ms_per_cost_stats().to_string()
                  << ", scaled_cost=" << std::setw(7) << c.scaled_cost << std::endl;
    }
}

struct BenchmarkSetup {
    uint32_t num_docs;
    Config attr_cfg;
    QueryOperator query_op;
    std::vector<double> hit_ratios;
    std::vector<uint32_t> child_counts;
    std::vector<bool> strictness;
    uint32_t default_values_per_document;
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
          strictness(strictness_in),
          default_values_per_document(0)
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

BenchmarkResults
run_benchmarks(const BenchmarkSetup& setup)
{
    BenchmarkResults results;
    for (bool strict : setup.strictness) {
        std::cout << "-------- run_benchmarks: " << to_string(setup.query_op, setup.attr_cfg, strict) << " --------" << std::endl;
        for (double hit_ratio : setup.hit_ratios) {
            for (uint32_t children : setup.child_counts) {
                uint32_t hits_per_term = calc_hits_per_term(setup.num_docs, hit_ratio, children, setup.query_op);
                HitSpecs hit_specs(55555);
                hit_specs.add(setup.default_values_per_document, setup.num_docs);
                auto terms = hit_specs.add(children, hits_per_term);
                auto attr_ctx = make_attribute_context(setup.attr_cfg, setup.num_docs, hit_specs);
                auto res = run_benchmark(*attr_ctx, setup.query_op, terms, setup.num_docs + 1, strict);
                print_result(res, terms, hit_ratio, setup.num_docs);
                results.add(res);
            }
        }
    }
    print_results(results);
    return results;
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
const Config int32 = make_config(BasicType::INT32, CollectionType::SINGLE, false);
const Config int32_fs = make_config(BasicType::INT32, CollectionType::SINGLE, true);
const Config int32_array = make_config(BasicType::INT32, CollectionType::ARRAY, false);
const Config int32_array_fs = make_config(BasicType::INT32, CollectionType::ARRAY, true);
const Config int32_wset_fs = make_config(BasicType::INT32, CollectionType::WSET, true);
const Config str = make_config(BasicType::STRING, CollectionType::SINGLE, false);
const Config str_array = make_config(BasicType::STRING, CollectionType::ARRAY, false);


TEST(IteratorBenchmark, analyze_term_search_in_attributes_without_fast_search)
{
    std::vector<Config> attr_cfgs = {int32, int32_array, str, str_array};
    const std::vector<double> my_hit_ratios = {0.001, 0.005, 0.01, 0.05, 0.1, 0.3, 0.5, 0.7, 0.9};
    BenchmarkSummary summary(25.0);
    for (const auto& attr_cfg : attr_cfgs) {
        for (bool strict : {true, false}) {
            BenchmarkSetup setup(num_docs, attr_cfg, QueryOperator::Term, my_hit_ratios, {1}, {strict});
            setup.default_values_per_document = 1;
            auto results = run_benchmarks(setup);
            summary.add(attr_cfg, QueryOperator::Term, strict, results);
        }
    }
    summary.calc_scaled_costs();
    print_summary(summary);
}

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
