// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "benchmark_blueprint_factory.h"
#include "blueprint_factory_builder.h"
#include "common.h"
#include "data_pond.h"
#include "data_pond_utils.h"
#include "intermediate_blueprint_factory.h"

#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stride.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <cmath>
#include <concepts>
#include <format>
#include <functional>
#include <iomanip>
#include <numeric>
#include <optional>
#include <print>
#include <vector>

using namespace search::attribute;
using namespace search::queryeval::test;
using namespace search::queryeval;
using namespace search;
using namespace vespalib;

using search::fef::MatchData;
using search::index::Schema;

using vespalib::make_string_short::fmt;

const std::string     field_name = "myfield";
double                budget_sec = 1.0;
std::optional<double> in_flow_filter = std::nullopt;

double estimate_actual_cost(Blueprint& bp, InFlow in_flow) {
    if (in_flow.strict()) {
        assert(bp.strict());
        return bp.strict_cost();
    } else if (bp.strict()) {
        auto stats = FlowStats::from(flow::DefaultAdapter(), &bp);
        return flow::forced_strict_cost(stats, in_flow.rate());
    } else {
        return bp.cost() * in_flow.rate();
    }
}

enum class PlanningAlgo { Order, Estimate, Cost, CostForceStrict };

std::string to_string(PlanningAlgo algo) {
    switch (algo) {
    case PlanningAlgo::Order:
        return "ordr";
    case PlanningAlgo::Estimate:
        return "esti";
    case PlanningAlgo::Cost:
        return "cost";
    case PlanningAlgo::CostForceStrict:
        return "forc";
    }
    return "unknown";
}

/**
 * Predefined field names to use when accessing the global pond.
 */
struct {
    using F = std::string;
    F actual_cost = "actual_cost";
    F algo = "algo";
    F blueprint_name = "blueprint_name";
    F calibration_constant = "calibration_constant";
    F children = "children";
    F class_ = "class";
    F dim = "dim";
    F error = "error";
    F error_label = "error_label";
    F fetch_postings_time_ms = "fetch_postings_time_ms";
    F field_cfg = "field_cfg";
    F filter_hit_ratio = "filter_hit_ratio";
    F force_strict = "force_strict";
    F gf_ratio = "gf_ratio";
    F group = "group";
    F hits = "hits";
    F in_flow = "in_flow";
    F iterator_name = "iterator_name";
    F ms_per_cost = "ms_per_cost";
    F num_docs = "num_docs";
    F op_hit_ratio = "op_hit_ratio";
    F pred_ms = "pred_ms";
    F query_op = "query_op";
    F range_high = "range_high";
    F range_low = "range_low";
    F range_size = "range_size";
    F seeks = "seeks";
    F strict_context = "strict_context";
    F target_hits = "target_hits";
    F time_error_abs = "time_error_abs";
    F time_ms = "time_ms";
    F unpack = "unpack";
    struct {
        F cost = "flow.cost";
        F estimate = "flow.estimate";
        F strict_cost = "flow.strict_cost";
    } flow;
} f;

struct BenchmarkResult {
    double      time_ms;
    uint32_t    seeks;
    uint32_t    hits;
    FlowStats   flow;
    double      actual_cost;
    std::string iterator_name;
    std::string blueprint_name;
    double      fetch_postings_time_ms;
    BenchmarkResult() : BenchmarkResult(0, 0, 0, {0, 0, 0}, 0, "", "", 0) {}
    BenchmarkResult(double time_ms_in, uint32_t seeks_in, uint32_t hits_in, FlowStats flow_in, double actual_cost_in,
                    const std::string& iterator_name_in, const std::string& blueprint_name_in,
                    double fetch_postings_time_ms_in)
        : time_ms(time_ms_in),
          seeks(seeks_in),
          hits(hits_in),
          flow(flow_in),
          actual_cost(actual_cost_in),
          iterator_name(iterator_name_in),
          blueprint_name(blueprint_name_in),
          fetch_postings_time_ms(fetch_postings_time_ms_in) {}
    BenchmarkResult(const BenchmarkResult&);
    BenchmarkResult(BenchmarkResult&&) noexcept = default;
    ~BenchmarkResult();
    BenchmarkResult& operator=(const BenchmarkResult&);
    BenchmarkResult& operator=(BenchmarkResult&&) noexcept = default;
    double ns_per_seek() const { return (time_ms / seeks) * 1000.0 * 1000.0; }
    double ms_per_actual_cost() const { return (time_ms / actual_cost); }
};

BenchmarkResult::BenchmarkResult(const BenchmarkResult&) = default;
BenchmarkResult::~BenchmarkResult() = default;
BenchmarkResult& BenchmarkResult::operator=(const BenchmarkResult&) = default;

struct Stats {
    double average;
    double median;
    double std_dev;
    Stats() : average(0.0), median(0.0), std_dev(0.0) {}
    Stats(double average_in, double median_in, double std_dev_in)
        : average(average_in), median(median_in), std_dev(std_dev_in) {}
    std::string to_string() const {
        std::ostringstream oss;
        oss << std::fixed << std::setprecision(3);
        oss << "{average=" << average << ", median=" << median << ", std_dev=" << std_dev << "}";
        return oss.str();
    }
};

double calc_median(const std::vector<double>& sorted_values) {
    size_t middle = sorted_values.size() / 2;
    if (sorted_values.size() % 2 == 0) {
        return (sorted_values[middle - 1] + sorted_values[middle]) / 2;
    } else {
        return sorted_values[middle];
    }
}

double calc_standard_deviation(const std::vector<double>& values, double average) {
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
        for (const auto& res : _results) {
            values.push_back(func(res));
        }
        std::sort(values.begin(), values.end());
        return values;
    }

    Stats calc_stats(auto func) const {
        auto   values = extract_sorted_values(func);
        double average = std::accumulate(values.begin(), values.end(), 0.0) / values.size();
        double median = calc_median(values);
        double std_dev = calc_standard_deviation(values, average);
        return {average, median, std_dev};
    }

public:
    BenchmarkCaseResult() : _results() {}
    void add(const BenchmarkResult& res) { _results.push_back(res); }
    Stats time_ms_stats() const {
        return calc_stats([](const auto& res) { return res.time_ms; });
    }
    Stats ns_per_seek_stats() const {
        return calc_stats([](const auto& res) { return res.ns_per_seek(); });
    }
    Stats ms_per_actual_cost_stats() const {
        return calc_stats([](const auto& res) { return res.ms_per_actual_cost(); });
    }
    Stats fetch_postings_time_ms_stats() const {
        return calc_stats([](const auto& res) { return res.fetch_postings_time_ms; });
    }
};

struct MatchLoopContext {
    Blueprint::UP      blueprint;
    MatchData::UP      match_data;
    SearchIterator::UP iterator;
    MatchLoopContext() : blueprint(), match_data(), iterator() {}
    MatchLoopContext(Blueprint::UP blueprint_in, MatchData::UP match_data_in, SearchIterator::UP iterator_in)
        : blueprint(std::move(blueprint_in)),
          match_data(std::move(match_data_in)),
          iterator(std::move(iterator_in)) {}
    void operator=(MatchLoopContext&& rhs) {
        blueprint = std::move(rhs.blueprint);
        match_data = std::move(rhs.match_data);
        iterator = std::move(rhs.iterator);
    }
    ~MatchLoopContext();
};

MatchLoopContext::~MatchLoopContext() = default;

Blueprint::Options to_sort_options(PlanningAlgo algo) {
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

void sort_blueprint(Blueprint& blueprint, InFlow in_flow, uint32_t docid_limit, Blueprint::Options opts) {
    auto opts_guard = blueprint.bind_opts(opts);
    blueprint.setDocIdLimit(docid_limit);
    blueprint.each_node_post_order([docid_limit](Blueprint& bp) { bp.update_flow_stats(docid_limit); });
    blueprint.sort(in_flow);
}

MatchLoopContext make_match_loop_context(BenchmarkBlueprintFactory& factory, InFlow in_flow, uint32_t docid_limit,
                                         PlanningAlgo algo, BenchmarkTimer& fetch_postings_timer) {
    auto blueprint = factory.make_blueprint();
    assert(blueprint);
    sort_blueprint(*blueprint, in_flow, docid_limit, to_sort_options(algo));
    fetch_postings_timer.before();
    blueprint->fetchPostings(ExecuteInfo::create(in_flow.rate(), ExecuteInfo::FULL));
    fetch_postings_timer.after();
    // Note: All blueprints get the same TermFieldMatchData instance.
    //       This is OK as long as we don't do unpacking and only use 1 thread.
    auto md = MatchData::makeTestInstance(1, 1);
    auto itr = blueprint->createSearch(*md);
    assert(itr);
    return {std::move(blueprint), std::move(md), std::move(itr)};
}

template <bool do_unpack>
BenchmarkResult strict_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, PlanningAlgo algo) {
    BenchmarkTimer   timer(budget_sec);
    BenchmarkTimer   fetch_postings_timer(0);
    uint32_t         hits = 0;
    MatchLoopContext ctx;
    while (timer.has_budget()) {
        ctx = make_match_loop_context(factory, true, docid_limit, algo, fetch_postings_timer);
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
    double    actual_cost = estimate_actual_cost(*ctx.blueprint, InFlow(true));
    return {timer.min_time() * 1000.0,
            hits + 1,
            hits,
            flow,
            actual_cost,
            get_class_name(*ctx.iterator),
            factory.get_name(*ctx.blueprint),
            fetch_postings_timer.min_time() * 1000.0};
}

template <bool do_unpack>
BenchmarkResult non_strict_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, double filter_hit_ratio,
                                  bool force_strict, PlanningAlgo algo) {
    BenchmarkTimer timer(budget_sec);
    BenchmarkTimer fetch_postings_timer(0);
    uint32_t       seeks = 0;
    uint32_t       hits = 0;
    // The following loop simulates a filter that is evaluated before the iterator.
    // The filter returns 'filter_hit_ratio' amount of the document corpus.
    const int32_t num_docs = docid_limit - 1;
    assert(num_docs > 0);
    auto num_matches = static_cast<uint32_t>(num_docs * filter_hit_ratio);
    assert(num_matches > 0 && "Trying to run non-strict search over 0 matches. "
                              "Probably misconfigured benchmark setup.");
    MatchLoopContext ctx;
    while (timer.has_budget()) {
        ctx = make_match_loop_context(factory, InFlow(force_strict, filter_hit_ratio), docid_limit, algo,
                                      fetch_postings_timer);
        auto* itr = ctx.iterator.get();
        timer.before();
        seeks = 0;
        hits = 0;
        itr->initRange(1, docid_limit);
        Stride stride(num_docs, num_matches);
        for (uint32_t docid = 1; !itr->isAtEnd(docid); docid += stride.next()) {
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
    assert(seeks == num_matches);
    FlowStats flow(ctx.blueprint->estimate(), ctx.blueprint->cost(), ctx.blueprint->strict_cost());
    double    actual_cost = estimate_actual_cost(*ctx.blueprint, InFlow(filter_hit_ratio));
    return {timer.min_time() * 1000.0,
            seeks,
            hits,
            flow,
            actual_cost,
            get_class_name(*ctx.iterator),
            factory.get_name(*ctx.blueprint),
            fetch_postings_timer.min_time() * 1000.0};
}

BenchmarkResult benchmark_search(BenchmarkBlueprintFactory& factory, uint32_t docid_limit, bool strict_context,
                                 bool force_strict, bool unpack_iterator, double filter_hit_ratio,
                                 PlanningAlgo algo) {
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
// Crossover utils.
//-----------------------------------------------------------------------------

double est_forced_strict_cost(double estimate, double strict_cost, double rate) {
    return (rate - estimate) * 0.2 + strict_cost;
}

struct Sample {
    double value;
    double other;
    bool   forced_strict;
    Sample(double v) noexcept : value(v), other(v), forced_strict(false) {}
    Sample(double v, double o, bool fs) noexcept : value(v), other(o), forced_strict(fs) {}
    bool operator<(const Sample& rhs) const noexcept { return value < rhs.value; }
    std::string str() const noexcept {
        if (other == value) {
            return fmt("%g", value);
        }
        auto fs = [](bool forced) { return forced ? "_FS" : ""; };
        return fmt("%g%s(%g%s)", value, fs(forced_strict), other, fs(!forced_strict));
    }
};

double find_crossover(const char* type, const char* a, const char* b, const auto& calculate_at, double delta) {
    double min = delta;
    double max = 1.0;
    fprintf(stderr, "looking for %s crossover in the range [%g, %g]...\n", type, min, max);
    auto at_min = calculate_at(min);
    auto at_max = calculate_at(max);
    fprintf(stderr, "  %s: [%s, %s], %s: [%s, %s]\n", a, at_min.first.str().c_str(), at_max.first.str().c_str(), b,
            at_min.second.str().c_str(), at_max.second.str().c_str());
    auto a_best = [](auto values) { return (values.first < values.second); };
    if (a_best(at_min) == a_best(at_max)) {
        fprintf(stderr, "  NO %s CROSSOVER FOUND\n", type);
        return 0.0;
    }
    while (max > (min + delta)) {
        double x = (min + max) / 2.0;
        auto   at_x = calculate_at(x);
        fprintf(stderr, "  best@%g: %s (%s vs %s)\n", x, a_best(at_x) ? a : b, at_x.first.str().c_str(),
                at_x.second.str().c_str());
        if (a_best(at_min) == a_best(at_x)) {
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

void sample_at(const char* type, const auto& calculate_at, const std::vector<double>& values,
               const std::vector<const char*>& names) {
    assert(values.size() == names.size());
    for (size_t i = 0; i < values.size(); ++i) {
        double      x = values[i];
        const char* x_name = names[i];
        auto        at_x = calculate_at(x);
        fprintf(stderr, "%s@%s(%g): before: %s, after: %s\n", type, x_name, x, at_x.first.str().c_str(),
                at_x.second.str().c_str());
    }
}

void analyze_crossover(BenchmarkBlueprintFactory&                                            fixed,
                       std::function<std::unique_ptr<BenchmarkBlueprintFactory>(double arg)> variable,
                       uint32_t docid_limit, bool allow_force_strict, double delta) {
    auto estimate_AND_time_ms = [&](auto& first, auto& last) {
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
    auto calculate_AND_cost = [&](auto& first, auto& last) {
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
    auto first_abs_est = [&](auto& first, auto&) {
        auto a = first.make_blueprint();
        return Sample(a->getState().estimate().estHits);
    };
    auto combine = [&](auto& fun) {
        return [&](double rate) {
            auto variable_at_rate = variable(rate);
            return std::make_pair(fun(*variable_at_rate, fixed), fun(fixed, *variable_at_rate));
        };
    };
    std::vector<double>      results;
    std::vector<const char*> names;
    names.push_back("time crossover");
    results.push_back(find_crossover("TIME", "before", "after", combine(estimate_AND_time_ms), delta));
    names.push_back("cost crossover");
    results.push_back(find_crossover("COST", "before", "after", combine(calculate_AND_cost), delta));
    names.push_back("abs_est crossover");
    results.push_back(find_crossover("ABS_EST", "before", "after", combine(first_abs_est), delta));
    sample_at("COST", combine(calculate_AND_cost), results, names);
    sample_at("TIME", combine(estimate_AND_time_ms), results, names);
}

//-----------------------------------------------------------------------------
// Print utils.
//-----------------------------------------------------------------------------

void print_result_header() {
    std::cout << "| in_flow |   chn | o_ratio | a_ratio |   f.est |    f.cost | f.act_cost | f.scost | f.act_scost | "
                 "    hits |    seeks |  time_ms | fetch_ms |  act_cost | ns_per_seek | ms_per_act_cost | iterator | "
                 "blueprint |"
              << std::endl;
}

std::ostream& operator<<(std::ostream& dst, InFlow in_flow) {
    auto old_w = dst.width();
    auto old_p = dst.precision();
    dst << std::setw(7) << std::setprecision(5);
    if (in_flow.strict()) {
        dst << " STRICT";
    } else {
        dst << in_flow.rate();
    }
    dst << std::setw(old_w);
    dst << std::setprecision(old_p);
    return dst;
}

void print_result(const BenchmarkResult& res, uint32_t children, double op_hit_ratio, InFlow in_flow,
                  uint32_t num_docs) {
    std::cout << std::fixed << std::setprecision(5) << "| " << in_flow << " | " << std::setw(5) << children << " | "
              << std::setw(7) << op_hit_ratio << " | " << std::setw(7) << ((double)res.hits / (double)num_docs)
              << " | " << std::setw(6) << res.flow.estimate << std::setprecision(4) << " | " << std::setw(9)
              << res.flow.cost << " | " << std::setw(10) << (res.flow.cost * in_flow.rate()) << " | " << std::setw(7)
              << res.flow.strict_cost << " | " << std::setw(11)
              << (in_flow.strict() ? res.flow.strict_cost : flow::forced_strict_cost(res.flow, in_flow.rate()))
              << " | " << std::setw(8) << res.hits << " | " << std::setw(8) << res.seeks << std::setprecision(3)
              << " | " << std::setw(8) << res.time_ms << " | " << std::setw(8) << res.fetch_postings_time_ms
              << std::setprecision(4) << " | " << std::setw(9) << res.actual_cost << std::setprecision(2) << " | "
              << std::setw(11) << res.ns_per_seek() << " | " << std::setw(15) << res.ms_per_actual_cost() << " | "
              << res.iterator_name << " | " << res.blueprint_name << " |" << std::endl;
}

void print_result(const BenchmarkCaseResult& result) {
    std::cout << std::fixed << std::setprecision(3) << "summary: time_ms=" << result.time_ms_stats().to_string()
              << std::endl
              << "         ns_per_seek=" << result.ns_per_seek_stats().to_string() << std::endl
              << "         ms_per_act_cost=" << result.ms_per_actual_cost_stats().to_string() << std::endl
              << "         fetch_postings_time_ms=" << result.fetch_postings_time_ms_stats().to_string() << std::endl
              << std::endl;
}

//-----------------------------------------------------------------------------
// Pond postprocess and print utils.
//-----------------------------------------------------------------------------

/**
 * Calibration constant = sum(time_ms) / sum(actual_cost) over all samples.
 */
void postprocess_calculate_calibration_constant(DataPond& pond) {
    std::vector<RecordRef> records;
    double                 total_time_ms = 0.0;
    double                 total_actual_cost = 0.0;
    for (auto& record : pond.records()) {
        if (record.has_field<double>(f.time_ms) && record.has_field<double>(f.actual_cost)) {
            records.emplace_back(record);

            double time_ms = record.get<double>(f.time_ms);
            double actual_cost = record.get<double>(f.actual_cost);
            double ms_per_cost = time_ms / actual_cost;
            record.set(f.ms_per_cost, ms_per_cost);
            total_time_ms += time_ms;
            total_actual_cost += actual_cost;
        }
    }

    double global_average = total_time_ms / total_actual_cost;
    for (auto record : records) {
        record.get().set(f.calibration_constant, global_average);
    }
}

/**
 * Calculate pred_ms.
 */
void postprocess_calculate_pred_ms(DataPond& pond) {
    for (auto& record : pond.records()) {
        if (record.has_field<double>(f.calibration_constant) && record.has_field<double>(f.actual_cost)) {
            double actual_cost = record.get<double>(f.actual_cost);
            double calibration_constant = record.get<double>(f.calibration_constant);
            double pred_ms = actual_cost * calibration_constant;
            record.set(f.pred_ms, pred_ms);

            double time_ms = record.get<double>(f.time_ms);
            double time_difference = std::abs(pred_ms - time_ms);
            record.set(f.time_error_abs, time_difference);
        }
    }
}

/**
 * Calculates averages, error and classifies per sample.
 */
void postprocess_calculate_error(DataPond& pond) {
    auto classify = [](double error_ratio) -> std::string {
        constexpr double ok_band = 1.4;
        constexpr double under_threshold = ok_band;
        constexpr double over_threshold = 1.0 / ok_band;
        if (error_ratio > under_threshold)
            return "UNDER";
        if (error_ratio < over_threshold)
            return "OVER";
        return "OK";
    };

    for (auto& record : pond.records()) {
        double calibration_constant = record.get<double>(f.calibration_constant);
        double ms_per_cost = record.get<double>(f.ms_per_cost);

        double error = ms_per_cost / calibration_constant;
        record.set(f.class_, classify(error));
        record.set(f.error, error);
    }
}

/**
 * Make in_flow label.
 */
void postprocess_make_display_fields(DataPond& pond) {
    for (auto& record : pond.records()) {
        if (record.has_field<bool>(f.strict_context) && record.has_field<double>(f.filter_hit_ratio)) {
            bool   strict = record.get<bool>(f.strict_context);
            double rate = record.get<double>(f.filter_hit_ratio);
            record.set(f.in_flow, strict ? std::string("STRICT") : std::format("{:.5f}", rate));
        }

        if (record.has_field<double>(f.error)) {
            record.set(f.error_label, std::format("{:.3f}x", record.get<double>(f.error)));
        }
    }
}

/**
 * Preprocess the raw data samples before summary.
 */
void postprocess_pond(DataPond& pond) {
    postprocess_calculate_calibration_constant(pond);
    postprocess_calculate_pred_ms(pond);
    postprocess_calculate_error(pond);
    postprocess_make_display_fields(pond);
}

/**
 * Render a data pond record's field.
 */
std::string render_field(const Record& rec, const std::string& field) {
    if (rec.has_field<bool>(field)) {
        return rec.get<bool>(field) ? "true" : "false";
    } else if (rec.has_field<int64_t>(field)) {
        return std::format("{}", rec.get<int64_t>(field));
    } else if (rec.has_field<double>(field)) {
        return std::format("{:.3f}", rec.get<double>(field));
    } else if (rec.has_field<std::string>(field)) {
        return rec.get<std::string>(field);
    } else {
        assert(false && "render_field: Unhandled data type.");
        return "Unhandled data type";
    }
}

void print_cell(const std::string& value, size_t width, bool left_align) {
    if (left_align) {
        std::print("{:<{}}", value, width);
    } else {
        std::print("{:>{}}", value, width);
    }
}

struct Column {
    uint64_t width = 4;
};

/**
 * Dynamically renders a data pond with only the column_keys.
 */
void print_pond_summary(const DataPond& pond, size_t column_padding = 2) {
    std::vector<std::string> column_keys = {f.group,   f.in_flow,        f.time_ms,     f.actual_cost, f.ms_per_cost,
                                            f.pred_ms, f.time_error_abs, f.error_label, f.class_};

    std::map<std::string, Column> columns;

    // column key width
    for (const auto& key : column_keys) {
        columns[key].width = std::max(columns[key].width, key.size());
    }

    // longest column data width
    for (const auto& record : pond.records()) {
        for (const auto& key : column_keys) {
            std::string rendered = render_field(record, key);
            columns[key].width = std::max(columns[key].width, rendered.size());
        }
    }

    // left align group label column. right align the others.
    std::string separator(column_padding, ' ');
    auto        print_row = [&](auto render_cell) {
        bool first = true;
        for (const auto& key : column_keys) {
            if (!first) {
                std::print("{}", ' ');
            }
            first = false;
            print_cell(render_cell(key), columns[key].width, key == f.group);
        }
        std::println("");
    };

    // calibration score header
    std::println("calibration score: ms_per_cost={:.3f} ({} cases)\n",
                 pond.records().front().get<double>(f.calibration_constant), pond.records().size());

    // print the rows
    print_row([&](const std::string& key) { return key; });
    for (const auto& record : pond.records()) {
        print_row([&](const std::string& key) { return render_field(record, key); });
    }
}

void dump_pond(const DataPond& pond) {
    for (const auto& rec : pond.records()) {
        std::println(stderr, "{}", rec.to_string());
    }
}

DataPond global_pond;

//-----------------------------------------------------------------------------
// Drive an arbitrary BenchmarkCase across a list of InFlow values.
//-----------------------------------------------------------------------------

struct BenchmarkOptions {
    bool         unpack = false;
    bool         force_strict = false;
    PlanningAlgo algo = PlanningAlgo::Cost;
};

struct BenchmarkCaseSetup {
    std::string                  group;
    FactoryPtr                   factory;
    uint32_t                     docid_limit;
    std::vector<InFlow>          in_flows;
    BenchmarkOptions             options;
    std::function<void(Record&)> decorate;

    ~BenchmarkCaseSetup();
};

BenchmarkCaseSetup::~BenchmarkCaseSetup() = default;

void add_factory_run_to_pond(DataPond& pond, const BenchmarkCaseSetup& setup, const BenchmarkResult& res,
                             InFlow in_flow) {
    Record record;
    record.set(f.actual_cost, res.actual_cost);
    record.set(f.algo, to_string(setup.options.algo));
    record.set(f.blueprint_name, res.blueprint_name);
    record.set(f.children, static_cast<int64_t>(0));
    record.set(f.fetch_postings_time_ms, res.fetch_postings_time_ms);
    record.set(f.field_cfg, std::string{});
    record.set(f.filter_hit_ratio, in_flow.rate());
    record.set(f.flow.cost, res.flow.cost);
    record.set(f.flow.estimate, res.flow.estimate);
    record.set(f.flow.strict_cost, res.flow.strict_cost);
    record.set(f.force_strict, setup.options.force_strict);
    record.set(f.group, setup.group);
    record.set(f.hits, static_cast<int64_t>(res.hits));
    record.set(f.iterator_name, res.iterator_name);
    record.set(f.op_hit_ratio, 0.0);
    record.set(f.query_op, std::string{});
    record.set(f.seeks, static_cast<int64_t>(res.seeks));
    record.set(f.strict_context, in_flow.strict());
    record.set(f.time_ms, res.time_ms);
    record.set(f.unpack, setup.options.unpack);
    if (setup.decorate) {
        setup.decorate(record);
    }
    pond.add(record);
}

BenchmarkCaseResult run_benchmark(const BenchmarkCaseSetup& setup) {
    assert(setup.factory);
    assert(setup.docid_limit > 0);
    BenchmarkCaseResult result;
    std::cout << "-------- run_benchmark: " << setup.group << " --------" << std::endl;
    print_result_header();
    uint32_t num_docs_for_print = setup.docid_limit - 1;
    for (InFlow in_flow : setup.in_flows) {
        auto res = benchmark_search(*setup.factory, setup.docid_limit, in_flow.strict(), setup.options.force_strict,
                                    setup.options.unpack, in_flow.rate(), setup.options.algo);
        print_result(res, /*children*/ 0, /*op_hit_ratio*/ 0.0, in_flow, num_docs_for_print);
        result.add(res);
        add_factory_run_to_pond(global_pond, setup, res, in_flow);
    }
    print_result(result);
    return result;
}

//---------------------------------------------------------------------------------------
// Drives a set of benchmark cases.
//---------------------------------------------------------------------------------------

std::vector<InFlow> make_in_flows(const std::vector<double>& rates, bool include_strict) {
    std::vector<InFlow> result;
    if (include_strict) {
        result.emplace_back(true);
    }
    for (double rate : rates) {
        result.emplace_back(false, rate);
    }
    return result;
}

/**
 * Make blueprint factory from EnnConfig.
 */
FactoryPtr make_factory(const EnnConfig& cfg) {
    return enn(cfg);
}

/**
 * Select fields used to describe an EnnConfig.
 */
void describe(const EnnConfig& cfg, Record& r) {
    r.set(f.num_docs, static_cast<int64_t>(cfg.num_docs));
    r.set(f.dim, static_cast<int64_t>(cfg.dim));
    r.set(f.target_hits, static_cast<int64_t>(cfg.target_hits));
    if (cfg.global_filter_hit_ratio.has_value()) {
        r.set(f.gf_ratio, cfg.global_filter_hit_ratio.value());
    }
}

/**
 * Make blueprint factory from RangeConfig.
 */
FactoryPtr make_factory(const RangeConfig& cfg) {
    return attr_range(cfg);
}

/**
 * Select fields used to describe a RangeConfig.
 */
void describe(const RangeConfig& cfg, Record& r) {
    r.set(f.range_low, cfg.range_low);
    r.set(f.range_high, cfg.range_high());
    r.set(f.range_size, cfg.range_size);
    r.set(f.target_hits, cfg.target_hits);
}

/**
 * A config adapts the "old approach" into this driver system.
 */
struct OpConfig {
    FieldConfig   field_cfg;
    QueryOperator query_op;
    uint32_t      num_docs;
    double        op_hit_ratio;
    uint32_t      children = 1;
    uint32_t      default_values_per_document = 0;
    bool          disjunct_children = false;
};

/**
 * Make blueprint factory for "old approach".
 */
FactoryPtr make_factory(const OpConfig& cfg) {
    return make_blueprint_factory(cfg.field_cfg, cfg.query_op, cfg.num_docs, cfg.default_values_per_document,
                                  cfg.op_hit_ratio, cfg.children, cfg.disjunct_children);
}

/**
 * Select fields used to describe OpConfig.
 */
void describe(const OpConfig& cfg, Record& r) {
    r.set(f.field_cfg, cfg.field_cfg.to_string());
    r.set(f.query_op, to_string(cfg.query_op));
    r.set(f.num_docs, static_cast<int64_t>(cfg.num_docs));
    r.set(f.op_hit_ratio, cfg.op_hit_ratio);
    r.set(f.children, static_cast<int64_t>(cfg.children));
}

/**
 * Use `describe()` function to generate a group name for a config.
 */
template <typename Config>
std::string group_name(const std::string& name, const Config& cfg) {
    Record scratch;
    describe(cfg, scratch);
    std::string result = name + "[";
    int         i = 0;
    for (const auto& [field, value] : scratch.data()) {
        if (i++) {
            result += ",";
        }
        result += field + "=" + value.to_string();
    }
    result += "]";
    return result;
}

/**
 * Run each configuration. Takes make_flows.
 */
template <typename Config, typename InFlowsFn>
    requires std::invocable<InFlowsFn, const Config&>
void run_benchmarks(const std::string& name, const std::vector<Config>& cases, InFlowsFn&& make_flows,
                    const BenchmarkOptions& options) {
    for (const auto& cfg : cases) {
        std::vector<InFlow> in_flows = make_flows(cfg);
        if (in_flows.empty()) {
            continue;
        }
        run_benchmark({.group = group_name(name, cfg),
                       .factory = make_factory(cfg),
                       .docid_limit = cfg.num_docs + 1,
                       .in_flows = std::move(in_flows),
                       .options = options,
                       .decorate = [&cfg](Record& r) { describe(cfg, r); }});
    }
}

/**
 * Runs each configuration. Takes in_flows as vector.
 */
template <typename Config>
void run_benchmarks(const std::string& name, const std::vector<Config>& cases, const std::vector<InFlow>& in_flows,
                    const BenchmarkOptions& options) {
    run_benchmarks(name, cases, [&in_flows](const Config&) { return in_flows; }, options);
}

//---------------------------------------------------------------------------------------
// Tools for benchmarking root intermediate blueprints with configurable children setups.
//---------------------------------------------------------------------------------------

void print_intermediate_blueprint_result_header(size_t children) {
    std::cout << "| in_flow";
    // This matches the naming scheme in IntermediateBlueprintFactory.
    char name = 'A';
    for (size_t i = 0; i < children; ++i) {
        std::cout << " | " << name++ << ".ratio";
    }
    std::cout << " |  flow.cost | flow.scost | flow.est |   ratio |     hits |    seeks | ms_per_cost |  time_ms | "
                 "algo | blueprint |"
              << std::endl;
}

void print_intermediate_blueprint_result(const BenchmarkResult& res, const std::vector<double>& children_ratios,
                                         PlanningAlgo algo, InFlow in_flow, uint32_t num_docs) {
    std::cout << std::fixed << std::setprecision(5) << "| " << in_flow;
    for (auto ratio : children_ratios) {
        std::cout << " | " << std::setw(7) << ratio;
    }
    std::cout << std::setprecision(5) << " | " << std::setw(10) << res.flow.cost << " | " << std::setw(10)
              << res.flow.strict_cost << " | " << std::setw(8) << res.flow.estimate << " | " << std::setw(7)
              << ((double)res.hits / (double)num_docs) << std::setprecision(4) << " | " << std::setw(8) << res.hits
              << " | " << std::setw(8) << res.seeks << std::setprecision(3) << " | " << std::setw(11)
              << res.ms_per_actual_cost() << " | " << std::setw(8) << res.time_ms << " | " << to_string(algo) << " | "
              << res.blueprint_name << " |" << std::endl;
}

struct BlueprintFactorySetup {
    FieldConfig         field_cfg;
    QueryOperator       query_op;
    std::vector<double> op_hit_ratios;
    uint32_t            children;
    bool                disjunct_children;
    uint32_t            default_values_per_document;

    BlueprintFactorySetup(const FieldConfig& field_cfg_in, QueryOperator query_op_in,
                          const std::vector<double>& op_hit_ratios_in)
        : BlueprintFactorySetup(field_cfg_in, query_op_in, op_hit_ratios_in, 1, false) {}
    BlueprintFactorySetup(const FieldConfig& field_cfg_in, QueryOperator query_op_in,
                          const std::vector<double>& op_hit_ratios_in, uint32_t children_in,
                          bool disjunct_children_in)
        : field_cfg(field_cfg_in),
          query_op(query_op_in),
          op_hit_ratios(op_hit_ratios_in),
          children(children_in),
          disjunct_children(disjunct_children_in),
          default_values_per_document(0) {}
    ~BlueprintFactorySetup();
    std::unique_ptr<BenchmarkBlueprintFactory> make_factory(size_t num_docs, double op_hit_ratio) const {
        return make_blueprint_factory(field_cfg, query_op, num_docs, default_values_per_document, op_hit_ratio,
                                      children, disjunct_children);
    }
    std::shared_ptr<BenchmarkBlueprintFactory> make_factory_shared(size_t num_docs, double op_hit_ratio) const {
        return std::shared_ptr<BenchmarkBlueprintFactory>(make_factory(num_docs, op_hit_ratio));
    }
    std::string to_string() const {
        return "field=" + field_cfg.to_string() + ", query=" + queryeval::test::to_string(query_op) +
               ", children=" + std::to_string(children);
    }
};

BlueprintFactorySetup::~BlueprintFactorySetup() = default;

void run_intermediate_blueprint_benchmark(auto factory_factory, std::vector<InFlow> in_flows,
                                          const BlueprintFactorySetup& a, const BlueprintFactorySetup& b,
                                          size_t num_docs) {
    print_intermediate_blueprint_result_header(2);
    double max_speedup = 0.0;
    double min_speedup = std::numeric_limits<double>::max();
    for (double b_hit_ratio : b.op_hit_ratios) {
        auto b_factory = b.make_factory_shared(num_docs, b_hit_ratio);
        for (double a_hit_ratio : a.op_hit_ratios) {
            auto factory = factory_factory();
            factory->add_child(a.make_factory(num_docs, a_hit_ratio));
            factory->add_child(b_factory);
            double time_ms_esti = 0.0;
            for (InFlow in_flow : in_flows) {
                for (auto algo :
                     {PlanningAlgo::Order, PlanningAlgo::Estimate, PlanningAlgo::Cost, PlanningAlgo::CostForceStrict})
                {
                    auto res = benchmark_search(*factory, num_docs + 1, in_flow.strict(), false, false,
                                                in_flow.rate(), algo);
                    print_intermediate_blueprint_result(res, {a_hit_ratio, b_hit_ratio}, algo, in_flow, num_docs);
                    if (algo == PlanningAlgo::Estimate) {
                        time_ms_esti = res.time_ms;
                    }
                    if (algo == PlanningAlgo::CostForceStrict) {
                        double speedup = time_ms_esti / res.time_ms;
                        if (speedup > max_speedup) {
                            max_speedup = speedup;
                        }
                        if (speedup < min_speedup) {
                            min_speedup = speedup;
                        }
                        std::cout << "speedup (esti/forc)=" << std::setprecision(4) << speedup << std::endl;
                    }
                }
            }
        }
    }
    std::cout << "max_speedup=" << max_speedup << ", min_speedup=" << min_speedup << std::endl << std::endl;
}

void run_and_benchmark(const BlueprintFactorySetup& a, const BlueprintFactorySetup& b, size_t num_docs) {
    std::cout << "AND[A={" << a.to_string() << "},B={" << b.to_string() << "}]" << std::endl;
    run_intermediate_blueprint_benchmark([]() { return std::make_unique<AndBlueprintFactory>(); }, {true}, a, b,
                                         num_docs);
}

void run_source_blender_benchmark(const BlueprintFactorySetup& a, const BlueprintFactorySetup& b, size_t num_docs) {
    std::cout << "SB[A={" << a.to_string() << "},B={" << b.to_string() << "}]" << std::endl;
    auto factory_factory = [&]() {
        auto factory = std::make_unique<SourceBlenderBlueprintFactory>();
        factory->init_selector([](uint32_t i) { return (i % 10 == 0) ? 1 : 2; }, num_docs + 1);
        return factory;
    };
    run_intermediate_blueprint_benchmark(factory_factory, {true, 0.75, 0.5, 0.25, 0.1, 0.01, 0.001}, a, b, num_docs);
}

//-------------------------------------------------------------------------------------

std::vector<double> gen_ratios(double middle, double range_multiplier, size_t num_samples) {
    double lower = middle / range_multiplier;
    double upper = middle * range_multiplier;
    // Solve the following equation:
    // lower * (factor ^ (num_samples - 1)) = upper;
    double              factor = std::pow(upper / lower, 1.0 / (num_samples - 1));
    std::vector<double> res;
    double              ratio = lower;
    for (size_t i = 0; i < num_samples; ++i) {
        res.push_back(ratio);
        ratio *= factor;
        if (ratio > 1.0) {
            if (res.size() < num_samples) {
                res.push_back(1.0);
            }
            break;
        }
    }
    return res;
}

/**
 * In-flow rates centered on each case's op_hit_ratio,
 * replacing the old 'filter_hit_ratios = gen_ratios(...)' pattern.
 */
auto in_flows_around_op_ratio(double range_multiplier = 10.0, size_t num_samples = 13, bool include_strict = false) {
    return [=](const auto& cfg) {
        return make_in_flows(gen_ratios(cfg.op_hit_ratio, range_multiplier, num_samples), include_strict);
    };
}

/**
 * Fixed in-flow rates pruned per case, replacing the old filter_crossover_factor:
 * a rate is kept when rate * crossover_factor <= op_hit_ratio.
 */
auto in_flows_pruned(const std::vector<double>& rates, double crossover_factor, bool include_strict) {
    return [=](const auto& cfg) {
        std::vector<double> kept;
        for (double rate : rates) {
            if (rate * crossover_factor <= cfg.op_hit_ratio) {
                kept.push_back(rate);
            }
        }
        return make_in_flows(kept, include_strict);
    };
}

FieldConfig make_attr_config(BasicType basic_type, CollectionType col_type, bool fast_search,
                             bool rank_filter = false) {
    Config cfg(basic_type, col_type);
    cfg.setFastSearch(fast_search);
    cfg.setIsFilter(rank_filter);
    return FieldConfig(cfg);
}

FieldConfig make_index_config() {
    Schema::IndexField field(field_name, search::index::schema::DataType::STRING,
                             search::index::schema::CollectionType::SINGLE);
    field.set_interleaved_features(true);
    return FieldConfig(field);
}

constexpr uint32_t          num_docs = 10'000'000;
const std::vector<double>   base_hit_ratios = {0.0001, 0.001, 0.01, 0.1, 0.5, 1.0};
const std::vector<double>   filter_hit_ratios = {0.00001, 0.00005, 0.0001, 0.0005, 0.001, 0.005,
                                                 0.01,    0.05,    0.1,    0.2,    0.5,   1.0};
const std::vector<double>   mid_hit_ratios = {0.01, 0.1, 0.5};
const std::vector<uint32_t> in_children_counts = {2, 5, 9, 10, 100, 1000, 10000};
const std::vector<uint32_t> or_children_counts = {2, 4, 6, 8, 10, 100, 1000};
const std::vector<double>   enn_in_flow_rates = {0.001, 0.005, 0.01, 0.05, 0.1, 0.2, 0.3,
                                                 0.4,   0.5,   0.6,  0.7,  0.8, 0.9, 1.0};
const auto                  int32 = make_attr_config(BasicType::INT32, CollectionType::SINGLE, false);
const auto                  int32_fs = make_attr_config(BasicType::INT32, CollectionType::SINGLE, true);
const auto                  int32_fs_rf = make_attr_config(BasicType::INT32, CollectionType::SINGLE, true, true);
const auto                  int32_array = make_attr_config(BasicType::INT32, CollectionType::ARRAY, false);
const auto                  int32_array_fs = make_attr_config(BasicType::INT32, CollectionType::ARRAY, true);
const auto                  int32_wset = make_attr_config(BasicType::INT32, CollectionType::WSET, false);
const auto                  int32_wset_fs = make_attr_config(BasicType::INT32, CollectionType::WSET, true);
const auto                  str = make_attr_config(BasicType::STRING, CollectionType::SINGLE, false);
const auto                  str_fs = make_attr_config(BasicType::STRING, CollectionType::SINGLE, true);
const auto                  str_array = make_attr_config(BasicType::STRING, CollectionType::ARRAY, false);
const auto                  str_array_fs = make_attr_config(BasicType::STRING, CollectionType::ARRAY, true);
const auto                  str_wset = make_attr_config(BasicType::STRING, CollectionType::WSET, false);
const auto                  str_index = make_index_config();

TEST(IteratorBenchmark, analyze_term_search_in_disk_index) {
    std::vector<OpConfig> cases;
    for (double ratio : base_hit_ratios) {
        cases.push_back(
            {.field_cfg = str_index, .query_op = QueryOperator::Term, .num_docs = num_docs, .op_hit_ratio = ratio});
    }
    run_benchmarks("TERM", cases, make_in_flows(filter_hit_ratios, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, analyze_term_search_in_attributes_non_strict) {
    std::vector<FieldConfig> field_cfgs = {int32, int32_array, int32_wset, str, str_array, str_wset};
    std::vector<OpConfig>    cases;
    for (const auto& field_cfg : field_cfgs) {
        for (double ratio : base_hit_ratios) {
            cases.push_back({.field_cfg = field_cfg,
                             .query_op = QueryOperator::Term,
                             .num_docs = num_docs,
                             .op_hit_ratio = ratio,
                             .default_values_per_document = 1});
        }
    }
    run_benchmarks("TERM", cases, in_flows_pruned(filter_hit_ratios, 1.0, /*include_strict=*/false), {});
}

TEST(IteratorBenchmark, analyze_term_search_in_attributes_strict) {
    std::vector<FieldConfig> field_cfgs = {int32, int32_array, int32_wset, str, str_array, str_wset};
    std::vector<OpConfig>    cases;
    for (const auto& field_cfg : field_cfgs) {
        // Note: This hit ratio matches the estimate of such attributes (0.5).
        cases.push_back({.field_cfg = field_cfg,
                         .query_op = QueryOperator::Term,
                         .num_docs = num_docs,
                         .op_hit_ratio = 0.5,
                         .default_values_per_document = 1});
    }
    run_benchmarks("TERM", cases, make_in_flows({}, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, analyze_term_search_in_fast_search_attributes) {
    std::vector<FieldConfig> field_cfgs = {int32_fs, int32_array_fs, str_fs, str_array_fs};
    std::vector<OpConfig>    cases;
    for (const auto& field_cfg : field_cfgs) {
        for (double ratio : base_hit_ratios) {
            cases.push_back({.field_cfg = field_cfg,
                             .query_op = QueryOperator::Term,
                             .num_docs = num_docs,
                             .op_hit_ratio = ratio});
        }
    }
    run_benchmarks("TERM", cases, in_flows_pruned(filter_hit_ratios, 1.0, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, analyze_IN_non_strict) {
    std::vector<OpConfig> cases;
    for (double in_hit_ratio : mid_hit_ratios) {
        for (uint32_t children : in_children_counts) {
            cases.push_back({.field_cfg = int32_fs,
                             .query_op = QueryOperator::In,
                             .num_docs = num_docs,
                             .op_hit_ratio = in_hit_ratio,
                             .children = children,
                             .disjunct_children = true});
        }
    }
    run_benchmarks("IN", cases, in_flows_around_op_ratio(), {});
}

TEST(IteratorBenchmark, analyze_IN_strict) {
    std::vector<OpConfig> cases;
    for (double ratio : {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8}) {
        for (uint32_t children : in_children_counts) {
            cases.push_back({.field_cfg = int32_fs,
                             .query_op = QueryOperator::In,
                             .num_docs = num_docs,
                             .op_hit_ratio = ratio,
                             .children = children,
                             .disjunct_children = true});
        }
    }
    run_benchmarks("IN", cases, make_in_flows({}, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, analyze_weak_and_operators) {
    std::vector<OpConfig> cases;
    for (auto query_op : {QueryOperator::WeakAnd, QueryOperator::ParallelWeakAnd}) {
        for (double ratio : base_hit_ratios) {
            for (uint32_t children : {1, 2, 10, 100}) {
                cases.push_back({.field_cfg = int32_wset_fs,
                                 .query_op = query_op,
                                 .num_docs = num_docs,
                                 .op_hit_ratio = ratio,
                                 .children = children});
            }
        }
    }
    run_benchmarks("WAND", cases, make_in_flows({1.0}, /*include_strict=*/true), {.unpack = true});
}

TEST(IteratorBenchmark, or_vs_filter_crossover) {
    auto fixed_or = make_blueprint_factory(int32_array_fs, QueryOperator::Or, num_docs, 0, 0.1, 100, false);
    auto variable_term = [](double rate) {
        return make_blueprint_factory(int32_array_fs, QueryOperator::Term, num_docs, 0, rate, 1, false);
    };
    analyze_crossover(*fixed_or, variable_term, num_docs + 1, false, 0.0001);
}

TEST(IteratorBenchmark, or_vs_filter_crossover_with_allow_force_strict) {
    auto fixed_or = make_blueprint_factory(int32_array_fs, QueryOperator::Or, num_docs, 0, 0.1, 100, false);
    auto variable_term = [](double rate) {
        return make_blueprint_factory(int32_array_fs, QueryOperator::Term, num_docs, 0, rate, 1, false);
    };
    analyze_crossover(*fixed_or, variable_term, num_docs + 1, true, 0.0001);
}

TEST(IteratorBenchmark, analyze_AND_filter_vs_IN) {
    for (auto in_filter_ratio : {0.01, 0.1, 0.5}) {
        for (uint32_t children : {2, 10, 100, 1000}) {
            run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(in_filter_ratio, 10.0, 13)},
                              {int32_fs, QueryOperator::In, {in_filter_ratio}, children, false}, num_docs);
        }
    }
}

TEST(IteratorBenchmark, analyze_AND_filter_vs_OR) {
    for (auto or_filter_ratio : {0.01, 0.1, 0.5}) {
        for (uint32_t children : {2, 10, 100, 1000}) {
            run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(or_filter_ratio, 10, 13)},
                              {int32_fs, QueryOperator::Or, {or_filter_ratio}, children, false}, num_docs);
        }
    }
}

TEST(IteratorBenchmark, analyze_AND_filter_vs_IN_array) {
    for (uint32_t children : {2, 10, 100, 1000}) {
        run_and_benchmark({int32_fs, QueryOperator::Term, gen_ratios(0.1, 10.0, 13)},
                          {int32_array_fs, QueryOperator::In, {0.1}, children, false}, num_docs);
    }
}

TEST(IteratorBenchmark, analyze_AND_bitvector_vs_IN) {
    for (uint32_t children : {10, 100, 1000, 10000}) {
        run_and_benchmark({int32_fs,
                           QueryOperator::In,
                           {0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60},
                           children,
                           true},
                          {int32_fs_rf,
                           QueryOperator::Term,
                           {1.0},
                           1,
                           true}, // this setup returns a bitvector matching all documents.
                          num_docs);
    }
}

TEST(IteratorBenchmark, analyze_strict_SOURCEBLENDER_memory_and_disk) {
    for (double small_ratio : {0.001, 0.005, 0.01, 0.05}) {
        run_source_blender_benchmark({str_fs, QueryOperator::Term, {small_ratio}},
                                     {str_index, QueryOperator::Term, {small_ratio * 10}}, num_docs);
    }
}

TEST(IteratorBenchmark, analyze_OR_non_strict_fs) {
    std::vector<OpConfig> cases;
    for (double or_hit_ratio : mid_hit_ratios) {
        for (uint32_t children : or_children_counts) {
            cases.push_back({.field_cfg = int32_fs,
                             .query_op = QueryOperator::Or,
                             .num_docs = num_docs,
                             .op_hit_ratio = or_hit_ratio,
                             .children = children});
        }
    }
    // Use {.force_strict = true} to benchmark the forced strict variants.
    run_benchmarks("OR", cases, in_flows_around_op_ratio(), {});
}

TEST(IteratorBenchmark, analyze_OR_non_strict_fs_child_est_adjust) {
    std::vector<OpConfig> cases;
    for (double or_hit_ratio : mid_hit_ratios) {
        for (uint32_t children : or_children_counts) {
            cases.push_back({.field_cfg = int32_fs,
                             .query_op = QueryOperator::Or,
                             .num_docs = num_docs,
                             .op_hit_ratio = or_hit_ratio,
                             .children = children});
        }
    }
    // In-flow rates centered on the estimate of a single OR child.
    auto child_est_flows = [](const OpConfig& cfg) {
        return make_in_flows(gen_ratios(cfg.op_hit_ratio / cfg.children, 10.0, 13), false);
    };
    run_benchmarks("OR", cases, child_est_flows, {});
}

TEST(IteratorBenchmark, analyze_OR_non_strict_non_fs) {
    std::vector<OpConfig> cases;
    for (uint32_t children : {2, 4, 6, 8, 10}) {
        cases.push_back({.field_cfg = int32,
                         .query_op = QueryOperator::Or,
                         .num_docs = num_docs,
                         .op_hit_ratio = 0.1,
                         .children = children});
    }
    run_benchmarks("OR", cases, in_flows_around_op_ratio(), {});
}

TEST(IteratorBenchmark, analyze_OR_strict) {
    std::vector<OpConfig> cases;
    for (double or_hit_ratio : mid_hit_ratios) {
        for (uint32_t children : or_children_counts) {
            cases.push_back({.field_cfg = int32_fs,
                             .query_op = QueryOperator::Or,
                             .num_docs = num_docs,
                             .op_hit_ratio = or_hit_ratio,
                             .children = children});
        }
    }
    run_benchmarks("OR", cases, make_in_flows({}, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, analyze_btree_iterator_non_strict) {
    std::vector<OpConfig> cases;
    for (auto term_ratio : {0.01, 0.1, 0.5, 1.0}) {
        cases.push_back({.field_cfg = int32_fs,
                         .query_op = QueryOperator::Term,
                         .num_docs = num_docs,
                         .op_hit_ratio = term_ratio});
    }
    run_benchmarks("TERM", cases, in_flows_around_op_ratio(10.0, 15), {});
}

TEST(IteratorBenchmark, analyze_btree_vs_bitvector_iterators_strict) {
    std::vector<OpConfig> cases;
    for (const auto& field_cfg : {int32_fs, int32_fs_rf}) {
        for (double ratio : {0.1, 0.2, 0.4, 0.5, 0.6, 0.8, 1.0}) {
            cases.push_back({.field_cfg = field_cfg,
                             .query_op = QueryOperator::Term,
                             .num_docs = num_docs,
                             .op_hit_ratio = ratio});
        }
    }
    run_benchmarks("TERM", cases, make_in_flows({}, /*include_strict=*/true), {});
}

TEST(IteratorBenchmark, btree_vs_array_nonstrict_crossover) {
    for (double hit_ratio : {0.001, 0.002, 0.003, 0.004, 0.005, 0.006, 0.007, 0.008, 0.009, 0.01, 0.02, 0.03, 0.04,
                             0.05,  0.06,  0.07,  0.08,  0.09,  0.1,   0.2,   0.3,   0.4,   0.5,  0.6,  0.7,  0.8,
                             0.9,   0.91,  0.92,  0.93,  0.94,  0.95,  0.96,  0.97,  0.98,  0.99, 1.0})
    {
        auto btree = make_blueprint_factory(int32_array_fs, QueryOperator::Term, num_docs, 0, hit_ratio, 1, false);
        auto array = make_blueprint_factory(int32_array, QueryOperator::Term, num_docs, 0, hit_ratio, 1, false);
        auto time_ms = [&](auto& bpf, double in_flow) {
            return Sample(
                benchmark_search(bpf, num_docs + 1, false, false, false, in_flow, PlanningAlgo::Cost).time_ms);
        };
        auto calculate_at = [&](double in_flow) {
            return std::make_pair(time_ms(*btree, in_flow), time_ms(*array, in_flow));
        };
        fprintf(stderr, "btree/array crossover@%5.3f: %8.6f\n", hit_ratio,
                find_crossover("TIME", "btree", "array", calculate_at, 0.0001));
    }
}

TEST(IteratorBenchmark, analyze_ENN) {
    std::vector<EnnConfig> cases;
    for (uint32_t target_hits : {10u, 100u, 1000u}) {
        cases.push_back({.num_docs = num_docs, .target_hits = target_hits});
    }
    run_benchmarks("ENN", cases, make_in_flows(enn_in_flow_rates, /*include_strict=*/true), {.unpack = true});
}

TEST(IteratorBenchmark, analyze_ENN_with_GF) {
    std::vector<EnnConfig> cases;
    // The gf_ratio values overlap enn_in_flow_rates so that strict ENN_GF at ratio r
    // can be compared directly against non-strict ENN driven at rate r.
    for (double gf_ratio : {0.9, 0.5, 0.1, 0.05, 0.01}) {
        cases.push_back({.num_docs = num_docs, .target_hits = 100, .global_filter_hit_ratio = gf_ratio});
    }
    run_benchmarks("ENN_GF", cases, make_in_flows(enn_in_flow_rates, /*include_strict=*/true), {.unpack = true});
}

// num_docs_scaled = document count.
// hit_ratio       = how many documents are hits.
// distinct_ratio  = how big is the range in terms of the hit ratio.
TEST(IteratorBenchmark, analyze_attr_range) {
    std::vector<RangeConfig> cases;
    for (double hit_ratio : {0.001, 0.01, 0.1, 0.5}) {
        int64_t target_hits = static_cast<int64_t>(hit_ratio * num_docs);
        for (double distinct_ratio : {0.01, 0.1, 1.0}) {
            int64_t range_size = static_cast<int64_t>(distinct_ratio * target_hits);
            cases.push_back(
                {.field_cfg = int32_fs, .target_hits = target_hits, .range_size = range_size, .num_docs = num_docs});
        }
    }
    run_benchmarks("ATTR_RANGE", cases, make_in_flows(enn_in_flow_rates, /*include_strict=*/true), {.unpack = true});
}

/**
 * Handles iteration of arguments. Use flag() when checking only for presence of flag,
 * use arg_string() or arg_double() when arguments should be provided.
 */
struct Args {
    int                        argc;
    char**                     argv;
    int                        i{};
    double                     double_value{};
    std::string                string_value{};
    std::optional<std::string> error{};

    ~Args();

    bool next() {
        if (error) {
            return false;
        }
        i++;
        return i < argc;
    }

    bool flag(const std::string& name) {
        assert(i < argc);
        return name == argv[i];
    }

    bool expect_arg(const std::string& name) {
        if (!next()) {
            error = std::format("Option {} expected an argument, but none were provided.", name);
            return false;
        }
        return true;
    }

    bool arg_double(const std::string& name) {
        assert(i < argc);
        if (!flag(name)) {
            return false;
        }
        if (!expect_arg(name)) {
            return false;
        }
        try {
            std::string value = argv[i];
            size_t      pos = 0;
            double_value = std::stod(value, &pos);
            // In case stod matches "0.5hello" as 0.5.
            if (pos != value.size()) {
                error = std::format("Option {} expected a double, but got '{}'", name, argv[i]);
                return false;
            }
        } catch (const std::exception&) {
            error = std::format("Option {} expected a double, but got '{}'", name, argv[i]);
            return false;
        }
        return true;
    }

    bool arg_string(const std::string& name) {
        assert(i < argc);
        if (!flag(name)) {
            return false;
        }
        if (!expect_arg(name)) {
            return false;
        }
        string_value = argv[i];
        return true;
    }
};

Args::~Args() = default;

static std::string smoke_test_filter = "--gtest_filter="
                                       "IteratorBenchmark.analyze_ENN";

int main(int argc, char** argv) {
    bool                       opt_dump_pond = false;
    std::optional<std::string> opt_save_pond = std::nullopt;
    std::optional<std::string> opt_load_pond = std::nullopt;

    Args args = {argc, argv};
    while (args.next()) {
        if (args.flag("--smoke-test")) {
            std::println(stderr, "Adding --smoke-test filter");
            argv[args.i] = smoke_test_filter.data();
        } else if (args.flag("--dump-pond")) {
            opt_dump_pond = true;
        } else if (args.arg_string("--save-pond")) {
            opt_save_pond = args.string_value;
        } else if (args.arg_string("--load-pond")) {
            opt_load_pond = args.string_value;
        } else if (args.arg_double("--filter-in-flow")) {
            in_flow_filter = args.double_value;
        }
    }

    if (args.error) {
        std::println(stderr, "error: {}", *args.error);
        return 1;
    }

    if (opt_load_pond) {
        try {
            read_file_into_data_pond(*opt_load_pond, global_pond);
        } catch (const vespalib::Exception& e) {
            std::println(stderr, "error: failed to load pond from '{}': {}", *opt_load_pond, e.getMessage());
            return 1;
        }
    } else {
        ::testing::InitGoogleTest(&argc, argv);
        int res = RUN_ALL_TESTS();
        if (res != 0) {
            return res;
        }
        if (opt_save_pond) {
            try {
                write_data_pond_to_file(*opt_save_pond, global_pond);
            } catch (const vespalib::Exception& e) {
                std::println(stderr, "error: failed to save pond to '{}': {}", *opt_save_pond, e.getMessage());
                return 1;
            }
        }
    }
    if (!global_pond.records().empty()) {
        postprocess_pond(global_pond);
        print_pond_summary(global_pond);
    }
    if (opt_dump_pond) {
        dump_pond(global_pond);
    }
}
