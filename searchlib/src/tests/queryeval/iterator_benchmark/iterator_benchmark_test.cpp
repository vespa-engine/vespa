// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprint_factory.h"
#include "benchmark_blueprint_factory.h"
#include "common.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
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

using vespalib::make_string_short::fmt;

const vespalib::string field_name = "myfield";
double budget_sec = 1.0;

enum class PlanningAlgo {
    Order,
    Estimate,
    Cost,
    CostForceStrict
};

vespalib::string
to_string(PlanningAlgo algo)
{
    switch (algo) {
        case PlanningAlgo::Order: return "ordr";
        case PlanningAlgo::Estimate: return "esti";
        case PlanningAlgo::Cost: return "cost";
        case PlanningAlgo::CostForceStrict: return "forc";
    }
    return "unknown";
}

struct BenchmarkResult {
    double time_ms;
    uint32_t seeks;
    uint32_t hits;
    FlowStats flow;
    double actual_cost;
    vespalib::string iterator_name;
    vespalib::string blueprint_name;
    BenchmarkResult() : BenchmarkResult(0, 0, 0, {0, 0, 0}, 0, "", "") {}
    BenchmarkResult(double time_ms_in, uint32_t seeks_in, uint32_t hits_in, FlowStats flow_in, double actual_cost_in,
                    const vespalib::string& iterator_name_in, const vespalib::string& blueprint_name_in)
        : time_ms(time_ms_in),
          seeks(seeks_in),
          hits(hits_in),
          flow(flow_in),
          actual_cost(actual_cost_in),
          iterator_name(iterator_name_in),
          blueprint_name(blueprint_name_in)
    {}
    ~BenchmarkResult();
    double ns_per_seek() const { return (time_ms / seeks) * 1000.0 * 1000.0; }
    double ms_per_actual_cost() const { return (time_ms / actual_cost); }
};
BenchmarkResult::~BenchmarkResult() = default;

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
};

struct MatchLoopContext {
    Blueprint::UP blueprint;
    MatchData::UP match_data;
    SearchIterator::UP iterator;
    MatchLoopContext() : blueprint(), match_data(), iterator() {}
    MatchLoopContext(Blueprint::UP blueprint_in,
                     MatchData::UP match_data_in,
                     SearchIterator::UP iterator_in)
        : blueprint(std::move(blueprint_in)),
          match_data(std::move(match_data_in)),
          iterator(std::move(iterator_in))
    {}
    void operator=(MatchLoopContext&& rhs) {
        blueprint = std::move(rhs.blueprint);
        match_data = std::move(rhs.match_data);
        iterator = std::move(rhs.iterator);
    }
    ~MatchLoopContext();
};

MatchLoopContext::~MatchLoopContext() = default;

Blueprint::Options
to_sort_options(PlanningAlgo algo)
{
    Blueprint::Options opts;
    if (algo == PlanningAlgo::Order) {
        opts.keep_order(true);
    } else if (algo == PlanningAlgo::Cost) {
        opts.sort_by_cost(true);
    } else if (algo == PlanningAlgo::CostForceStrict) {
        opts.sort_by_cost(true).allow_force_strict(true);
    }
    return opts;
}

void
sort_blueprint(Blueprint& blueprint, InFlow in_flow, uint32_t docid_limit, Blueprint::Options opts)
{
    auto opts_guard = blueprint.bind_opts(opts);
    blueprint.setDocIdLimit(docid_limit);
    blueprint.each_node_post_order([docid_limit](Blueprint &bp){
        bp.update_flow_stats(docid_limit);
    });
    blueprint.sort(in_flow);
}

MatchLoopContext
make_match_loop_context(BenchmarkBlueprintFactory& factory, InFlow in_flow, uint32_t docid_limit, PlanningAlgo algo)
{
    auto blueprint = factory.make_blueprint();
    assert(blueprint);
    sort_blueprint(*blueprint, in_flow, docid_limit, to_sort_options(algo));
    blueprint->fetchPostings(ExecuteInfo::FULL);
    // Note: All blueprints get the same TermFieldMatchData instance.
    //       This is OK as long as we don't do unpacking and only use 1 thread.
    auto md = MatchData::makeTestInstance(1, 1);
    auto itr = blueprint->createSearch(*md);
    assert(itr);
    return {std::move(blueprint), std::move(md), std::move(itr)};
}

template <bool do_unpack>
BenchmarkResult
strict_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, PlanningAlgo algo)
{
    BenchmarkTimer timer(budget_sec);
    uint32_t hits = 0;
    MatchLoopContext ctx;
    while (timer.has_budget()) {
        ctx = make_match_loop_context(factory, true, docid_limit, algo);
        auto* itr = ctx.iterator.get();
        timer.before();
        hits = 0;
        itr->initRange(1, docid_limit);
        uint32_t docid = itr->seekFirst(1);
        if constexpr (do_unpack) {
            itr->unpack(docid);
        }
        while (docid < docid_limit) {
            ++hits;
            docid = itr->seekNext(docid + 1);
            if constexpr (do_unpack) {
                itr->unpack(docid);
            }
        }
        timer.after();
    }
    FlowStats flow(ctx.blueprint->estimate(), ctx.blueprint->cost(), ctx.blueprint->strict_cost());
    return {timer.min_time() * 1000.0, hits + 1, hits, flow, flow.strict_cost, get_class_name(*ctx.iterator), factory.get_name(*ctx.blueprint)};
}

template <bool do_unpack>
BenchmarkResult
non_strict_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, double filter_hit_ratio, bool force_strict, PlanningAlgo algo)
{
    BenchmarkTimer timer(budget_sec);
    uint32_t seeks = 0;
    uint32_t hits = 0;
    // This simulates a filter that is evaluated before this iterator.
    // The filter returns 'filter_hit_ratio' amount of the document corpus.
    uint32_t docid_skip = 1.0 / filter_hit_ratio;
    MatchLoopContext ctx;
    while (timer.has_budget()) {
        ctx = make_match_loop_context(factory, InFlow(force_strict, filter_hit_ratio), docid_limit, algo);
        auto* itr = ctx.iterator.get();
        timer.before();
        seeks = 0;
        hits = 0;
        itr->initRange(1, docid_limit);
        for (uint32_t docid = 1; !itr->isAtEnd(docid); docid += docid_skip) {
            ++seeks;
            if (itr->seek(docid)) {
                ++hits;
                if constexpr (do_unpack) {
                    itr->unpack(docid);
                }
            }
        }
        timer.after();
    }
    FlowStats flow(ctx.blueprint->estimate(), ctx.blueprint->cost(), ctx.blueprint->strict_cost());
    double actual_cost = flow.cost * filter_hit_ratio;
    return {timer.min_time() * 1000.0, seeks, hits, flow, actual_cost, get_class_name(*ctx.iterator), factory.get_name(*ctx.blueprint)};
}

BenchmarkResult
benchmark_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, bool strict_context, bool force_strict, bool unpack_iterator, double filter_hit_ratio, PlanningAlgo algo)
{
    if (strict_context) {
        if (unpack_iterator) {
            return strict_search<true>(factory, docid_limit, algo);
        } else {
            return strict_search<false>(factory, docid_limit, algo);
        }
    } else {
        if (unpack_iterator) {
            return non_strict_search<true>(factory, docid_limit, filter_hit_ratio, force_strict, algo);
        } else {
            return non_strict_search<false>(factory, docid_limit, filter_hit_ratio, force_strict, algo);
        }
    }
}





//-----------------------------------------------------------------------------

double est_forced_strict_cost(double estimate, double strict_cost, double rate) {
    return (rate - estimate) * 0.2 + strict_cost;
}

struct Sample {
    double value;
    double other;
    bool forced_strict;
    Sample(double v) noexcept : value(v), other(v), forced_strict(false) {}
    Sample(double v, double o, bool fs) noexcept : value(v), other(o), forced_strict(fs) {}
    bool operator<(const Sample &rhs) const noexcept { return value < rhs.value; }
    vespalib::string str() const noexcept {
        if (other == value) {
            return fmt("%g", value);
        }
        auto fs = [](bool forced){ return forced ? "_FS" : ""; };
        return fmt("%g%s(%g%s)", value, fs(forced_strict), other, fs(!forced_strict));
    }
};

double find_crossover(const char *type, const auto &calculate_at, double delta) {
    double min = delta;
    double max = 1.0;
    fprintf(stderr, "looking for %s crossover in the range [%g, %g]...\n", type, min, max);
    auto at_min = calculate_at(min);
    auto at_max = calculate_at(max);
    fprintf(stderr, "  before: [%s, %s], after: [%s, %s]\n",
            at_min.first.str().c_str(), at_max.first.str().c_str(),
            at_min.second.str().c_str(), at_max.second.str().c_str());
    auto best_before = [](auto values) { return (values.first < values.second); };
    if (best_before(at_min) == best_before(at_max)) {
        fprintf(stderr, "  NO %s CROSSOVER FOUND\n", type);
        return 0.0;
    }
    while (max > (min + delta)) {
        double x = (min + max) / 2.0;
        auto at_x = calculate_at(x);
        fprintf(stderr, "  best@%g: %s (%s vs %s)\n", x, best_before(at_x) ? "before" : "after",
                at_x.first.str().c_str(), at_x.second.str().c_str());
        if (best_before(at_min) == best_before(at_x)) {
            min = x;
            at_min = at_x;
        } else {
            max = x;
            at_max = at_x;
        }
    }
    double result = (min + max) / 2.0;
    fprintf(stderr, "  %s CROSSOVER AT %g\n", type, result);
    return result;
}

void sample_at(const char *type, const auto &calculate_at, const std::vector<double> &values, const std::vector<const char *> &names) {
    assert(values.size() == names.size());
    for (size_t i = 0; i < values.size(); ++i) {
        double x = values[i];
        const char *x_name = names[i];
        auto at_x = calculate_at(x);
        fprintf(stderr, "%s@%s(%g): before: %s, after: %s\n", type, x_name, x,
                at_x.first.str().c_str(), at_x.second.str().c_str());
    }
}

void analyze_crossover(BenchmarkBlueprintFactory &fixed, std::function<std::unique_ptr<BenchmarkBlueprintFactory>(double arg)> variable,
                       uint32_t docid_limit, bool allow_force_strict, double delta)
{
    auto estimate_AND_time_ms = [&](auto &first, auto &last) {
                                    auto a = first.make_blueprint();
                                    a->basic_plan(true, docid_limit);
                                    double est_a = a->estimate();
                                    double a_ms = benchmark_search(first, docid_limit, true, false, false, 1.0, PlanningAlgo::Cost).time_ms;
                                    double b_ms = benchmark_search(last, docid_limit, false, false, false, est_a, PlanningAlgo::Cost).time_ms;
                                    if (!allow_force_strict) {
                                        return Sample(a_ms + b_ms);
                                    }
                                    double c_ms = benchmark_search(last, docid_limit, false, true, false, est_a, PlanningAlgo::Cost).time_ms;
                                    if (c_ms < b_ms) {
                                        return Sample(a_ms + c_ms, a_ms + b_ms, true);
                                    }
                                    return Sample(a_ms + b_ms, a_ms + c_ms, false);
                                };
    auto calculate_AND_cost = [&](auto &first, auto &last) {
                                  auto a = first.make_blueprint();
                                  auto b = last.make_blueprint();
                                  a->basic_plan(true, docid_limit);
                                  double a_cost = a->strict_cost();
                                  b->basic_plan(a->estimate(), docid_limit);
                                  double b_cost = b->cost() * a->estimate();
                                  if (!allow_force_strict) {
                                      return Sample(a_cost + b_cost);
                                  }
                                  auto c = last.make_blueprint();
                                  c->basic_plan(true, docid_limit);
                                  double c_cost = est_forced_strict_cost(c->estimate(), c->strict_cost(), a->estimate());
                                  if (c_cost < b_cost) {
                                      return Sample(a_cost + c_cost, a_cost + b_cost, true);
                                  }
                                  return Sample(a_cost + b_cost, a_cost + c_cost, false);
                              };
    auto first_abs_est = [&](auto &first, auto &) {
                             auto a = first.make_blueprint();
                             return Sample(a->getState().estimate().estHits);
                         };
    auto combine = [&](auto &fun) {
                       return [&](double rate) {
                                  auto variable_at_rate = variable(rate);
                                  return std::make_pair(fun(*variable_at_rate, fixed), fun(fixed, *variable_at_rate));
                              };
                   };
    std::vector<double> results;
    std::vector<const char *> names;
    names.push_back("time crossover");
    results.push_back(find_crossover("TIME", combine(estimate_AND_time_ms), delta));
    names.push_back("cost crossover");
    results.push_back(find_crossover("COST", combine(calculate_AND_cost), delta));
    names.push_back("abs_est crossover");
    results.push_back(find_crossover("ABS_EST", combine(first_abs_est), delta));
    sample_at("COST", combine(calculate_AND_cost), results, names);
    sample_at("TIME", combine(estimate_AND_time_ms), results, names);
}

//-----------------------------------------------------------------------------

vespalib::string
to_string(bool val)
{
    return val ? "true" : "false";
}

void
print_result_header()
{
    std::cout << "|   chn | f_ratio | o_ratio | a_ratio |   f.est |    f.cost | f.scost |     hits |    seeks |  time_ms |  act_cost | ns_per_seek | ms_per_act_cost | iterator | blueprint |" << std::endl;
}

void
print_result(const BenchmarkResult& res, uint32_t children, double op_hit_ratio, double filter_hit_ratio, uint32_t num_docs)
{
    std::cout << std::fixed << std::setprecision(5)
              << "| " << std::setw(5) << children
              << " | " << std::setw(7) << filter_hit_ratio
              << " | " << std::setw(7) << op_hit_ratio
              << " | " << std::setw(7) << ((double) res.hits / (double) num_docs)
              << " | " << std::setw(6) << res.flow.estimate
              << std::setprecision(4)
              << " | " << std::setw(9) << res.flow.cost
              << " | " << std::setw(7) << res.flow.strict_cost
              << " | " << std::setw(8) << res.hits
              << " | " << std::setw(8) << res.seeks
              << std::setprecision(3)
              << " | " << std::setw(8) << res.time_ms
              << std::setprecision(4)
              << " | " << std::setw(9) << res.actual_cost
              << std::setprecision(2)
              << " | " << std::setw(11) << res.ns_per_seek()
              << " | " << std::setw(15) << res.ms_per_actual_cost()
              << " | " << res.iterator_name
              << " | " << res.blueprint_name << " |" << std::endl;
}

void
print_result(const BenchmarkCaseResult& result)
{
    std::cout << std::fixed << std::setprecision(3)
              << "summary: time_ms=" << result.time_ms_stats().to_string() << std::endl
              << "         ns_per_seek=" << result.ns_per_seek_stats().to_string() << std::endl
              << "         ms_per_act_cost=" << result.ms_per_actual_cost_stats().to_string() << std::endl << std::endl;
}

struct BenchmarkCase {
    FieldConfig field_cfg;
    QueryOperator query_op;
    bool strict_context;
    bool force_strict;
    bool unpack_iterator;
    BenchmarkCase(const FieldConfig& field_cfg_in, QueryOperator query_op_in, bool strict_context_in)
        : field_cfg(field_cfg_in),
          query_op(query_op_in),
          strict_context(strict_context_in),
          force_strict(false),
          unpack_iterator(false)
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
    bool disjunct_children;
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
          disjunct_children(false),
          filter_crossover_factor(0.0)
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
    bool unpack_iterator;
    uint32_t default_values_per_document;
    bool disjunct_children;
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
          unpack_iterator(false),
          default_values_per_document(0),
          disjunct_children(false),
          filter_crossover_factor(0.0)
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
        res.bcase.unpack_iterator = unpack_iterator;
        res.default_values_per_document = default_values_per_document;
        res.disjunct_children = disjunct_children;
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
                                                  op_hit_ratio, children, setup.disjunct_children);
            for (double filter_hit_ratio : setup.filter_hit_ratios) {
                if (filter_hit_ratio * setup.filter_crossover_factor <= op_hit_ratio) {
                    auto res = benchmark_search(*factory, setup.num_docs + 1,
                                                setup.bcase.strict_context, setup.bcase.force_strict, setup.bcase.unpack_iterator, filter_hit_ratio, PlanningAlgo::Cost);
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

//---------------------------------------------------------------------------------------
// Tools for benchmarking root intermediate blueprints with configurable children setups.
//---------------------------------------------------------------------------------------

void
print_intermediate_blueprint_result_header(size_t children)
{
    // This matches the naming scheme in IntermediateBlueprintFactory.
    char name = 'A';
    for (size_t i = 0; i < children; ++i) {
        std::cout << "| " << name++ << ".ratio ";
    }
    std::cout << "|  flow.cost | flow.scost | flow.est |   ratio |     hits |    seeks | ms_per_cost |  time_ms | algo | blueprint |" << std::endl;
}

void
print_intermediate_blueprint_result(const BenchmarkResult& res, const std::vector<double>& children_ratios, PlanningAlgo algo, uint32_t num_docs)
{
    std::cout << std::fixed << std::setprecision(5);
    for (auto ratio : children_ratios) {
        std::cout << "| " << std::setw(7) << ratio << " ";
    }
    std::cout << std::setprecision(5)
              << "| " << std::setw(10) << res.flow.cost
              << " | " << std::setw(10) << res.flow.strict_cost
              << " | " << std::setw(8) << res.flow.estimate
              << " | " << std::setw(7) << ((double) res.hits / (double) num_docs)
              << std::setprecision(4)
              << " | " << std::setw(8) << res.hits
              << " | " << std::setw(8) << res.seeks
              << std::setprecision(3)
              << " | " << std::setw(11) << res.ms_per_actual_cost()
              << " | " << std::setw(8) << res.time_ms
              << " | " << to_string(algo)
              << " | " << res.blueprint_name << " |" << std::endl;
}

struct BlueprintFactorySetup {
    FieldConfig field_cfg;
    QueryOperator query_op;
    std::vector<double> op_hit_ratios;
    uint32_t children;
    bool disjunct_children;
    uint32_t default_values_per_document;

    BlueprintFactorySetup(const FieldConfig& field_cfg_in, QueryOperator query_op_in, const std::vector<double>& op_hit_ratios_in)
            : BlueprintFactorySetup(field_cfg_in, query_op_in, op_hit_ratios_in, 1, false)
    {}
    BlueprintFactorySetup(const FieldConfig& field_cfg_in, QueryOperator query_op_in, const std::vector<double>& op_hit_ratios_in,
                          uint32_t children_in, bool disjunct_children_in)
            : field_cfg(field_cfg_in),
              query_op(query_op_in),
              op_hit_ratios(op_hit_ratios_in),
              children(children_in),
              disjunct_children(disjunct_children_in),
              default_values_per_document(0)
    {}
    ~BlueprintFactorySetup();
    std::unique_ptr<BenchmarkBlueprintFactory> make_factory(size_t num_docs, double op_hit_ratio) const {
        return make_blueprint_factory(field_cfg, query_op, num_docs, default_values_per_document, op_hit_ratio, children, disjunct_children);
    }
    std::shared_ptr<BenchmarkBlueprintFactory> make_factory_shared(size_t num_docs, double op_hit_ratio) const {
        return std::shared_ptr<BenchmarkBlueprintFactory>(make_factory(num_docs, op_hit_ratio));
    }
    vespalib::string to_string() const {
        return "field=" + field_cfg.to_string() + ", query=" + test::to_string(query_op) + ", children=" + std::to_string(children);
    }
};

BlueprintFactorySetup::~BlueprintFactorySetup() = default;

template <typename IntermediateBlueprintFactoryType>
void
run_intermediate_blueprint_benchmark(const BlueprintFactorySetup& a, const BlueprintFactorySetup& b, size_t num_docs)
{
    print_intermediate_blueprint_result_header(2);
    for (double b_hit_ratio: b.op_hit_ratios) {
        auto b_factory = b.make_factory_shared(num_docs, b_hit_ratio);
        for (double a_hit_ratio : a.op_hit_ratios) {
            IntermediateBlueprintFactoryType factory;
            factory.add_child(a.make_factory(num_docs, a_hit_ratio));
            factory.add_child(b_factory);
            for (auto algo: {PlanningAlgo::Order, PlanningAlgo::Estimate, PlanningAlgo::Cost, PlanningAlgo::CostForceStrict}) {
                auto res = benchmark_search(factory, num_docs + 1, true, false, false, 1.0, algo);
                print_intermediate_blueprint_result(res, {a_hit_ratio, b_hit_ratio}, algo, num_docs);
            }
            std::cout << std::endl;
        }
    }
}

void
run_and_benchmark(const BlueprintFactorySetup& a, const BlueprintFactorySetup& b, size_t num_docs)
{
    std::cout << "AND[A={" << a.to_string() << "},B={" << b.to_string() << "}]" << std::endl;
    run_intermediate_blueprint_benchmark<AndBlueprintFactory>(a, b, num_docs);
}

//-------------------------------------------------------------------------------------

std::vector<double>
gen_ratios(double middle, double range_multiplier, size_t num_samples)
{
    double lower = middle / range_multiplier;
    double upper = middle * range_multiplier;
    // Solve the following equation:
    // lower * (factor ^ (num_samples - 1)) = upper;
    double factor = std::pow(upper / lower, 1.0 / (num_samples - 1));
    std::vector<double> res;
    double ratio = lower;
    for (size_t i = 0; i < num_samples; ++i) {
        res.push_back(ratio);
        ratio *= factor;
    }
    return res;
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

TEST(IteratorBenchmark, analyze_in_operator_non_strict)
{
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8};
    BenchmarkSetup setup(num_docs, {int32_fs}, {QueryOperator::In}, {false}, hit_ratios, {5, 9, 10, 100, 1000, 10000});
    setup.disjunct_children = true;
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, analyze_in_operator_strict)
{
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8};
    BenchmarkSetup setup(num_docs, {int32_fs}, {QueryOperator::In}, {true}, hit_ratios, {5, 9, 10, 100, 1000, 10000});
    setup.disjunct_children = true;
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, analyze_complex_leaf_operators)
{
    std::vector<FieldConfig> field_cfgs = {int32_array_fs};
    std::vector<QueryOperator> query_ops = {QueryOperator::In, QueryOperator::DotProduct};
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8};
    BenchmarkSetup setup(num_docs, field_cfgs, query_ops, {true, false}, hit_ratios, {1, 2, 10, 100});
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, analyze_weak_and_operators)
{
    std::vector<FieldConfig> field_cfgs = {int32_wset_fs};
    std::vector<QueryOperator> query_ops = {QueryOperator::WeakAnd, QueryOperator::ParallelWeakAnd};
    BenchmarkSetup setup(num_docs, field_cfgs, query_ops, {true, false}, base_hit_ratios, {1, 2, 10, 100});
    setup.unpack_iterator = true;
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

TEST(IteratorBenchmark, or_vs_filter_crossover)
{
    auto fixed_or = make_blueprint_factory(int32_array_fs, QueryOperator::Or, num_docs, 0, 0.1, 100, false);
    auto variable_term = [](double rate) {
                             return make_blueprint_factory(int32_array_fs, QueryOperator::Term, num_docs, 0, rate, 1, false);
                         };
    analyze_crossover(*fixed_or, variable_term, num_docs + 1, false, 0.0001);
}

TEST(IteratorBenchmark, or_vs_filter_crossover_with_allow_force_strict)
{
    auto fixed_or = make_blueprint_factory(int32_array_fs, QueryOperator::Or, num_docs, 0, 0.1, 100, false);
    auto variable_term = [](double rate) {
                             return make_blueprint_factory(int32_array_fs, QueryOperator::Term, num_docs, 0, rate, 1, false);
                         };
    analyze_crossover(*fixed_or, variable_term, num_docs + 1, true, 0.0001);
}

TEST(IteratorBenchmark, analyze_and_with_filter_vs_in)
{
    for (uint32_t children: {10, 100, 1000}) {
        run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(0.1, 8.0, 15)},
                          {int32_fs, QueryOperator::In, {0.1}, children, false},
                          num_docs);
    }
}

TEST(IteratorBenchmark, analyze_and_with_filter_vs_in_array)
{
    for (uint32_t children: {10, 100, 1000}) {
        run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(0.1, 8.0, 15)},
                          {int32_array_fs, QueryOperator::In, {0.1}, children, false},
                          num_docs);
    }
}

TEST(IteratorBenchmark, analyze_and_with_filter_vs_or)
{
    for (uint32_t children: {10, 100, 1000}) {
        run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(0.1, 8.0, 15)},
                          {int32_fs, QueryOperator::Or, {0.1}, children, false},
                          num_docs);
    }
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
