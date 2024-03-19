// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "benchmark_blueprint_factory.h"
#include "common.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <cmath>
#include <numeric>
#include <vector>

using namespace search::attribute;
using namespace search::fef;
using namespace search::queryeval::test;
using namespace search::queryeval;
using namespace search;
using namespace vespalib;

using search::index::Schema;

const vespalib::string field_name = "myfield";
double budget_sec = 1.0;

struct BenchmarkResult {
    double time_ms;
    uint32_t seeks;
    uint32_t hits;
    FlowStats flow;
    double actual_cost;
    double alt_cost;
    vespalib::string iterator_name;
    vespalib::string blueprint_name;
    BenchmarkResult() : BenchmarkResult(0, 0, 0, {0, 0, 0}, 0, 0, "", "") {}
    BenchmarkResult(double time_ms_in, uint32_t seeks_in, uint32_t hits_in, FlowStats flow_in, double actual_cost_in, double alt_cost_in,
                    const vespalib::string& iterator_name_in, const vespalib::string& blueprint_name_in)
        : time_ms(time_ms_in),
          seeks(seeks_in),
          hits(hits_in),
          flow(flow_in),
          actual_cost(actual_cost_in),
          alt_cost(alt_cost_in),
          iterator_name(iterator_name_in),
          blueprint_name(blueprint_name_in)
    {}
    double ns_per_seek() const { return (time_ms / seeks) * 1000.0 * 1000.0; }
    double ms_per_actual_cost() const { return (time_ms / actual_cost); }
    double ms_per_alt_cost() const { return (time_ms / alt_cost); }
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

double
calc_median(const std::vector<double>& sorted_values)
{
    size_t middle = sorted_values.size() / 2;
    if (sorted_values.size() % 2 == 0) {
        return (sorted_values[middle - 1] + sorted_values[middle]) / 2;
    } else {
        return sorted_values[middle];
    }
}

double
calc_standard_deviation(const std::vector<double>& values, double average)
{
    double deviations = 0.0;
    for (double val : values) {
        double diff = val - average;
        deviations += (diff * diff);
    }
    // Bessel's correction (dividing by N-1, instead of N).
    double variance = deviations / (values.size() - 1);
    return std::sqrt(variance);
}

class BenchmarkCaseResult {
private:
    std::vector<BenchmarkResult> _results;

    std::vector<double> extract_sorted_values(auto func) const {
        std::vector<double> values;
        for (const auto& res: _results) {
            values.push_back(func(res));
        }
        std::sort(values.begin(), values.end());
        return values;
    }

    Stats calc_stats(auto func) const {
        auto values = extract_sorted_values(func);
        double average = std::accumulate(values.begin(), values.end(), 0.0) / values.size();
        double median = calc_median(values);
        double std_dev = calc_standard_deviation(values, average);
        return {average, median, std_dev};
    }

public:
    BenchmarkCaseResult(): _results() {}
    void add(const BenchmarkResult& res) {
        _results.push_back(res);
    }
    Stats time_ms_stats() const {
        return calc_stats([](const auto& res){ return res.time_ms; });
    }
    Stats ns_per_seek_stats() const {
        return calc_stats([](const auto& res){ return res.ns_per_seek(); });
    }
    Stats ms_per_actual_cost_stats() const {
        return calc_stats([](const auto& res){ return res.ms_per_actual_cost(); });
    }
    Stats ms_per_alt_cost_stats() const {
        return calc_stats([](const auto& res){ return res.ms_per_alt_cost(); });
    }
};

std::string
delete_substr_from(const std::string& source, const std::string& substr)
{
    std::string res = source;
    auto i = res.find(substr);
    while (i != std::string::npos) {
        res.erase(i, substr.length());
        i = res.find(substr, i);
    }
    return res;
}

vespalib::string
get_class_name(const auto& obj)
{
    auto res = obj.getClassName();
    res = delete_substr_from(res, "search::attribute::");
    res = delete_substr_from(res, "search::queryeval::");
    res = delete_substr_from(res, "vespalib::btree::");
    res = delete_substr_from(res, "search::");
    res = delete_substr_from(res, "vespalib::");
    res = delete_substr_from(res, "anonymous namespace");
    return res;
}

BenchmarkResult
strict_search(Blueprint& blueprint, MatchData& md, uint32_t docid_limit)
{
    auto itr = blueprint.createSearch(md, true);
    assert(itr.get());
    BenchmarkTimer timer(budget_sec);
    uint32_t hits = 0;
    while (timer.has_budget()) {
        timer.before();
        hits = 0;
        itr->initRange(1, docid_limit);
        uint32_t docid = itr->seekFirst(1);
        while (docid < docid_limit) {
            ++hits;
            docid = itr->seekNext(docid + 1);
        }
        timer.after();
    }
    FlowStats flow(blueprint.estimate(), blueprint.cost(), blueprint.strict_cost());
    return {timer.min_time() * 1000.0, hits + 1, hits, flow, flow.strict_cost, flow.strict_cost, get_class_name(*itr), get_class_name(blueprint)};
}

BenchmarkResult
non_strict_search(Blueprint& blueprint, MatchData& md, uint32_t docid_limit, double filter_hit_ratio, bool force_strict)
{
    auto itr = blueprint.createSearch(md, force_strict);
    assert(itr.get());
    BenchmarkTimer timer(budget_sec);
    uint32_t seeks = 0;
    uint32_t hits = 0;
    // This simulates a filter that is evaluated before this iterator.
    // The filter returns 'filter_hit_ratio' amount of the document corpus.
    uint32_t docid_skip = 1.0 / filter_hit_ratio;
    while (timer.has_budget()) {
        timer.before();
        seeks = 0;
        hits = 0;
        itr->initRange(1, docid_limit);
        for (uint32_t docid = 1; !itr->isAtEnd(docid); docid += docid_skip) {
            ++seeks;
            if (itr->seek(docid)) {
                ++hits;
            }
        }
        timer.after();
    }
    FlowStats flow(blueprint.estimate(), blueprint.cost(), blueprint.strict_cost());
    double actual_cost = flow.cost * filter_hit_ratio;
    // This is an attempt to calculate an alternative actual cost for strict / posting list iterators that are used in a non-strict context.
    double alt_cost = flow.strict_cost + 0.5 * filter_hit_ratio;
    return {timer.min_time() * 1000.0, seeks, hits, flow, actual_cost, alt_cost, get_class_name(*itr), get_class_name(blueprint)};
}

BenchmarkResult
benchmark_search(Blueprint::UP blueprint, uint32_t docid_limit, bool strict_context, bool force_strict, double filter_hit_ratio)
{
    auto opts = Blueprint::Options::all();
    blueprint->sort(strict_context || force_strict, opts);
    blueprint->fetchPostings(ExecuteInfo::createForTest(strict_context || force_strict));
    // Note: All blueprints get the same TermFieldMatchData instance.
    //       This is OK as long as we don't do unpacking and only use 1 thread.
    auto md = MatchData::makeTestInstance(1, 1);
    if (strict_context) {
        return strict_search(*blueprint, *md, docid_limit);
    } else {
        return non_strict_search(*blueprint, *md, docid_limit, filter_hit_ratio, force_strict);
    }
}

vespalib::string
to_string(bool val)
{
    return val ? "true" : "false";
}

void
print_result_header()
{
    std::cout << "|  chn | f_ratio | o_ratio | a_ratio |  f.est |  f.cost | f.scost |     hits |    seeks |  time_ms | act_cost | alt_cost | ns_per_seek | ms_per_act_cost | ms_per_alt_cost | iterator | blueprint |" << std::endl;
}

void
print_result(const BenchmarkResult& res, uint32_t children, double op_hit_ratio, double filter_hit_ratio, uint32_t num_docs)
{
    std::cout << std::fixed << std::setprecision(4)
              << "| " << std::setw(4) << children
              << " | " << std::setw(7) << filter_hit_ratio
              << " | " << std::setw(7) << op_hit_ratio
              << " | " << std::setw(7) << ((double) res.hits / (double) num_docs)
              << " | " << std::setw(6) << res.flow.estimate
              << " | " << std::setw(7) << res.flow.cost
              << " | " << std::setw(7) << res.flow.strict_cost
              << " | " << std::setw(8) << res.hits
              << " | " << std::setw(8) << res.seeks
              << std::setprecision(3)
              << " | " << std::setw(8) << res.time_ms
              << std::setprecision(4)
              << " | " << std::setw(8) << res.actual_cost
              << " | " << std::setw(8) << res.alt_cost
              << std::setprecision(2)
              << " | " << std::setw(11) << res.ns_per_seek()
              << " | " << std::setw(15) << res.ms_per_actual_cost()
              << " | " << std::setw(15) << res.ms_per_alt_cost()
              << " | " << res.iterator_name
              << " | " << res.blueprint_name << " |" << std::endl;
}

void
print_result(const BenchmarkCaseResult& result)
{
    std::cout << std::fixed << std::setprecision(3)
              << "summary: time_ms=" << result.time_ms_stats().to_string() << std::endl
              << "         ns_per_seek=" << result.ns_per_seek_stats().to_string() << std::endl
              << "         ms_per_act_cost=" << result.ms_per_actual_cost_stats().to_string() << std::endl
              << "         ms_per_alt_cost=" << result.ms_per_alt_cost_stats().to_string() << std::endl << std::endl;
}

struct BenchmarkCase {
    FieldConfig field_cfg;
    QueryOperator query_op;
    bool strict_context;
    bool force_strict;
    BenchmarkCase(const FieldConfig& field_cfg_in, QueryOperator query_op_in, bool strict_context_in)
        : field_cfg(field_cfg_in),
          query_op(query_op_in),
          strict_context(strict_context_in),
          force_strict(false)
    {}
    vespalib::string to_string() const {
        return "op=" + search::queryeval::test::to_string(query_op) + ", cfg=" + field_cfg.to_string() +
               ", strict_context=" + ::to_string(strict_context) + (force_strict ? (", force_strict=" + ::to_string(force_strict)) : "");
    }
};

struct BenchmarkCaseSummary {
    BenchmarkCase bcase;
    BenchmarkCaseResult result;
    double scaled_cost;
    BenchmarkCaseSummary(const BenchmarkCase& bcase_in, const BenchmarkCaseResult& result_in)
        : bcase(bcase_in),
          result(result_in),
          scaled_cost(1.0)
    {}
    BenchmarkCaseSummary(const BenchmarkCaseSummary&);
    BenchmarkCaseSummary& operator=(const BenchmarkCaseSummary&);
    ~BenchmarkCaseSummary();
};

BenchmarkCaseSummary::BenchmarkCaseSummary(const BenchmarkCaseSummary&) = default;
BenchmarkCaseSummary& BenchmarkCaseSummary::operator=(const BenchmarkCaseSummary&) = default;
BenchmarkCaseSummary::~BenchmarkCaseSummary() = default;

class BenchmarkSummary {
private:
    std::vector<BenchmarkCaseSummary> _cases;

public:
    BenchmarkSummary()
        : _cases()
    {}
    void add(const BenchmarkCase& bcase, const BenchmarkCaseResult& result) {
        _cases.emplace_back(bcase, result);
    }
    void calc_scaled_costs() {
        std::sort(_cases.begin(), _cases.end(), [](const auto& lhs, const auto& rhs) {
            return lhs.result.ms_per_actual_cost_stats().average < rhs.result.ms_per_actual_cost_stats().average;
        });
        double baseline_ms_per_cost = _cases[0].result.ms_per_actual_cost_stats().average;
        for (size_t i = 1; i < _cases.size(); ++i) {
            auto& c = _cases[i];
            c.scaled_cost = c.result.ms_per_actual_cost_stats().average / baseline_ms_per_cost;
        }
    }
    const std::vector<BenchmarkCaseSummary>& cases() const { return _cases; }
    bool empty() const { return _cases.empty(); }
};

void
print_summary(const BenchmarkSummary& summary)
{
    std::cout << "-------- benchmark summary --------" << std::endl;
    for (const auto& c : summary.cases()) {
        std::cout << std::fixed << std::setprecision(3) << ""
                  << std::setw(50) << std::left << c.bcase.to_string() << ": "
                  << "ms_per_act_cost=" << std::setw(7) << std::right << c.result.ms_per_actual_cost_stats().to_string()
                  << ", scaled_cost=" << std::setw(7) << c.scaled_cost << std::endl;
    }
}

struct BenchmarkCaseSetup {
    uint32_t num_docs;
    BenchmarkCase bcase;
    std::vector<double> op_hit_ratios;
    std::vector<uint32_t> child_counts;
    std::vector<double> filter_hit_ratios;
    uint32_t default_values_per_document;
    double filter_crossover_factor;
    BenchmarkCaseSetup(uint32_t num_docs_in,
                       const BenchmarkCase& bcase_in,
                       const std::vector<double>& op_hit_ratios_in,
                       const std::vector<uint32_t>& child_counts_in)
        : num_docs(num_docs_in),
          bcase(bcase_in),
          op_hit_ratios(op_hit_ratios_in),
          child_counts(child_counts_in),
          filter_hit_ratios({1.0}),
          default_values_per_document(0),
          filter_crossover_factor(1.0)
    {}
    ~BenchmarkCaseSetup() {}
};

struct BenchmarkSetup {
    uint32_t num_docs;
    std::vector<FieldConfig> field_cfgs;
    std::vector<QueryOperator> query_ops;
    std::vector<bool> strictness;
    std::vector<double> op_hit_ratios;
    std::vector<uint32_t> child_counts;
    std::vector<double> filter_hit_ratios;
    bool force_strict;
    uint32_t default_values_per_document;
    double filter_crossover_factor;
    BenchmarkSetup(uint32_t num_docs_in,
                   const std::vector<FieldConfig>& field_cfgs_in,
                   const std::vector<QueryOperator>& query_ops_in,
                   const std::vector<bool>& strictness_in,
                   const std::vector<double>& op_hit_ratios_in,
                   const std::vector<uint32_t>& child_counts_in)
        : num_docs(num_docs_in),
          field_cfgs(field_cfgs_in),
          query_ops(query_ops_in),
          strictness(strictness_in),
          op_hit_ratios(op_hit_ratios_in),
          child_counts(child_counts_in),
          filter_hit_ratios({1.0}),
          force_strict(false),
          default_values_per_document(0),
          filter_crossover_factor(1.0)
    {}
    BenchmarkSetup(uint32_t num_docs_in,
                   const std::vector<FieldConfig>& field_cfgs_in,
                   const std::vector<QueryOperator>& query_ops_in,
                   const std::vector<bool>& strictness_in,
                   const std::vector<double>& op_hit_ratios_in)
        : BenchmarkSetup(num_docs_in, field_cfgs_in, query_ops_in, strictness_in, op_hit_ratios_in, {1})
    {}
    BenchmarkCaseSetup make_case_setup(const BenchmarkCase& bcase) const {
        BenchmarkCaseSetup res(num_docs, bcase, op_hit_ratios, child_counts);
        res.bcase.force_strict = force_strict;
        res.default_values_per_document = default_values_per_document;
        if (!bcase.strict_context) {
            // Simulation of a filter is only relevant in a non-strict context.
            res.filter_hit_ratios = filter_hit_ratios;
            res.filter_crossover_factor = filter_crossover_factor;
        } else {
            res.filter_hit_ratios = {1.0};
            res.filter_crossover_factor = 0.0;
        }
        return res;
    }
    ~BenchmarkSetup();
};

BenchmarkSetup::~BenchmarkSetup() = default;

BenchmarkCaseResult
run_benchmark_case(const BenchmarkCaseSetup& setup)
{
    BenchmarkCaseResult result;
    std::cout << "-------- run_benchmark_case: " << setup.bcase.to_string() << " --------" << std::endl;
    print_result_header();
    for (double op_hit_ratio : setup.op_hit_ratios) {
        for (uint32_t children : setup.child_counts) {
            auto factory = make_blueprint_factory(setup.bcase.field_cfg, setup.bcase.query_op,
                                                  setup.num_docs, setup.default_values_per_document,
                                                  op_hit_ratio, children);
            for (double filter_hit_ratio : setup.filter_hit_ratios) {
                if (filter_hit_ratio * setup.filter_crossover_factor <= op_hit_ratio) {
                    auto res = benchmark_search(factory->make_blueprint(), setup.num_docs + 1, setup.bcase.strict_context, setup.bcase.force_strict, filter_hit_ratio);
                    print_result(res, children, op_hit_ratio, filter_hit_ratio, setup.num_docs);
                    result.add(res);
                }
            }
        }
    }
    print_result(result);
    return result;
}

void
run_benchmarks(const BenchmarkSetup& setup, BenchmarkSummary& summary)
{
    for (const auto& field_cfg : setup.field_cfgs) {
        for (auto query_op : setup.query_ops) {
            for (bool strict : setup.strictness) {
                BenchmarkCase bcase(field_cfg, query_op, strict);
                auto case_setup = setup.make_case_setup(bcase);
                auto results = run_benchmark_case(case_setup);
                summary.add(bcase, results);
            }
        }
    }
}

void
run_benchmarks(const BenchmarkSetup& setup)
{
    BenchmarkSummary summary;
    run_benchmarks(setup, summary);
    summary.calc_scaled_costs();
    print_summary(summary);
}

FieldConfig
make_attr_config(BasicType basic_type, CollectionType col_type, bool fast_search)
{
    Config cfg(basic_type, col_type);
    cfg.setFastSearch(fast_search);
    return FieldConfig(cfg);
}

FieldConfig
make_index_config()
{
    Schema::IndexField field(field_name, search::index::schema::DataType::STRING, search::index::schema::CollectionType::SINGLE);
    field.set_interleaved_features(true);
    return FieldConfig(field);
}

constexpr uint32_t num_docs = 10'000'000;
const std::vector<double> base_hit_ratios = {0.0001, 0.001, 0.01, 0.1, 0.5, 1.0};
const std::vector<double> filter_hit_ratios = {0.00001, 0.00005, 0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.2, 0.5, 1.0};
const auto int32 = make_attr_config(BasicType::INT32, CollectionType::SINGLE, false);
const auto int32_fs = make_attr_config(BasicType::INT32, CollectionType::SINGLE, true);
const auto int32_array = make_attr_config(BasicType::INT32, CollectionType::ARRAY, false);
const auto int32_array_fs = make_attr_config(BasicType::INT32, CollectionType::ARRAY, true);
const auto int32_wset = make_attr_config(BasicType::INT32, CollectionType::WSET, false);
const auto int32_wset_fs = make_attr_config(BasicType::INT32, CollectionType::WSET, true);
const auto str = make_attr_config(BasicType::STRING, CollectionType::SINGLE, false);
const auto str_fs = make_attr_config(BasicType::STRING, CollectionType::SINGLE, true);
const auto str_array = make_attr_config(BasicType::STRING, CollectionType::ARRAY, false);
const auto str_array_fs = make_attr_config(BasicType::STRING, CollectionType::ARRAY, true);
const auto str_wset = make_attr_config(BasicType::STRING, CollectionType::WSET, false);
const auto str_index = make_index_config();

BenchmarkSummary global_summary;

TEST(IteratorBenchmark, analyze_term_search_in_disk_index)
{
    BenchmarkSetup setup(num_docs, {str_index}, {QueryOperator::Term}, {true, false}, base_hit_ratios);
    setup.filter_hit_ratios = filter_hit_ratios;
    setup.filter_crossover_factor = 1.0;
    run_benchmarks(setup, global_summary);
}

TEST(IteratorBenchmark, analyze_term_search_in_attributes_non_strict)
{
    std::vector<FieldConfig> field_cfgs = {int32, int32_array, int32_wset, str, str_array, str_wset};
    BenchmarkSetup setup(num_docs, field_cfgs, {QueryOperator::Term}, {false}, base_hit_ratios);
    setup.default_values_per_document = 1;
    setup.filter_hit_ratios = filter_hit_ratios;
    setup.filter_crossover_factor = 1.0;
    run_benchmarks(setup, global_summary);
}

TEST(IteratorBenchmark, analyze_term_search_in_attributes_strict)
{
    std::vector<FieldConfig> field_cfgs = {int32, int32_array, int32_wset, str, str_array, str_wset};
    // Note: This hit ratio matches the estimate of such attributes (0.5).
    BenchmarkSetup setup(num_docs, field_cfgs, {QueryOperator::Term}, {true}, {0.5});
    setup.default_values_per_document = 1;
    run_benchmarks(setup, global_summary);
}

TEST(IteratorBenchmark, analyze_term_search_in_fast_search_attributes)
{
    std::vector<FieldConfig> field_cfgs = {int32_fs, int32_array_fs, str_fs, str_array_fs};
    BenchmarkSetup setup(num_docs, field_cfgs, {QueryOperator::Term}, {true, false}, base_hit_ratios);
    setup.filter_hit_ratios = filter_hit_ratios;
    setup.filter_crossover_factor = 1.0;
    run_benchmarks(setup, global_summary);
}

TEST(IteratorBenchmark, analyze_complex_leaf_operators)
{
    std::vector<FieldConfig> field_cfgs = {int32_array_fs};
    std::vector<QueryOperator> query_ops = {QueryOperator::In, QueryOperator::DotProduct};
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8};
    BenchmarkSetup setup(num_docs, field_cfgs, query_ops, {true, false}, hit_ratios, {1, 2, 10, 100});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, term_benchmark)
{
    BenchmarkSetup setup(num_docs, {int32_fs}, {QueryOperator::Term}, {true, false}, base_hit_ratios);
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, and_benchmark)
{
    BenchmarkSetup setup(num_docs, {int32_array_fs}, {QueryOperator::And}, {true, false}, base_hit_ratios, {1, 2, 4, 8});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, or_benchmark)
{
    BenchmarkSetup setup(num_docs, {int32_array_fs}, {QueryOperator::Or}, {true, false}, base_hit_ratios, {1, 10, 100, 1000});
    run_benchmarks(setup);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    int res = RUN_ALL_TESTS();
    if (!global_summary.empty()) {
        global_summary.calc_scaled_costs();
        print_summary(global_summary);
    }
    return res;
}
