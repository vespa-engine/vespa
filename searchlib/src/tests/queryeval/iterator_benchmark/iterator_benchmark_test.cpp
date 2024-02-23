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
non_strict_search(Blueprint& blueprint, MatchData& md, uint32_t docid_limit, double filter_hit_ratio)
{
    auto itr = blueprint.createSearch(md, false);
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
benchmark_search(Blueprint::UP blueprint, uint32_t docid_limit, bool strict, double filter_hit_ratio)
{
    auto opts = Blueprint::Options::all();
    blueprint->sort(strict, opts);
    blueprint->fetchPostings(ExecuteInfo::createForTest(strict));
    // Note: All blueprints get the same TermFieldMatchData instance.
    //       This is OK as long as we don't do unpacking and only use 1 thread.
    auto md = MatchData::makeTestInstance(1, 1);
    if (strict) {
        return strict_search(*blueprint, *md, docid_limit);
    } else {
        return non_strict_search(*blueprint, *md, docid_limit, filter_hit_ratio);
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
    std::ostringstream oss;
    auto col_type = attr_config.collectionType();
    auto basic_type = attr_config.basicType();
    if (col_type == CollectionType::SINGLE) {
        oss << basic_type.asString();
    } else {
        oss << col_type.asString() << "<" << basic_type.asString() << ">";
    }
    if (attr_config.fastSearch()) {
        oss << "(fs)";
    }
    return oss.str();
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
run_benchmark(IAttributeContext& attr_ctx, QueryOperator query_op, const benchmark::TermVector& terms, uint32_t docid_limit, bool strict, double filter_hit_ratio)
{
    if (query_op == QueryOperator::And) {
        return benchmark_search(make_intermediate_blueprint<AndBlueprint>(attr_ctx, terms, docid_limit), docid_limit, strict, filter_hit_ratio);
    } else if (query_op == QueryOperator::Or) {
        return benchmark_search(make_intermediate_blueprint<OrBlueprint>(attr_ctx, terms, docid_limit), docid_limit, strict, filter_hit_ratio);
    } else {
        auto query_node = make_query_node(query_op, terms);
        auto blueprint = make_leaf_blueprint(*query_node, attr_ctx, docid_limit);
        return benchmark_search(std::move(blueprint), docid_limit, strict, filter_hit_ratio);
    }
}

void
print_result_header()
{
    std::cout << "|  chn | f_ratio | o_ratio | a_ratio |  f.est | f.cost | f.scost |     hits |    seeks |  time_ms | act_cost | alt_cost | ns_per_seek | ms_per_act_cost | ms_per_alt_cost | iterator | blueprint |" << std::endl;
}

void
print_result(const BenchmarkResult& res, const benchmark::TermVector& terms, double op_hit_ratio, double filter_hit_ratio, uint32_t num_docs)
{
    std::cout << std::fixed << std::setprecision(4)
              << "| " << std::setw(4) << terms.size()
              << " | " << std::setw(7) << filter_hit_ratio
              << " | " << std::setw(7) << op_hit_ratio
              << " | " << std::setw(7) << ((double) res.hits / (double) num_docs)
              << " | " << std::setw(6) << res.flow.estimate
              << " | " << std::setw(6) << res.flow.cost
              << " | " << std::setw(7) << res.flow.strict_cost
              << " | " << std::setw(8) << res.hits
              << " | " << std::setw(8) << res.seeks
              << std::setprecision(2)
              << " | " << std::setw(8) << res.time_ms
              << std::setprecision(3)
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
    Config attr_cfg;
    QueryOperator query_op;
    bool strict;
    BenchmarkCase(const Config& attr_cfg_in, QueryOperator query_op_in, bool strict_in)
        : attr_cfg(attr_cfg_in),
          query_op(query_op_in),
          strict(strict_in)
    {}
    vespalib::string to_string() const {
        return "op=" + ::to_string(query_op) + ", cfg=" + ::to_string(attr_cfg) + ", strict=" + (strict ? "true" : "false");
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
};

void
print_summary(const BenchmarkSummary& summary)
{
    std::cout << "-------- benchmark summary --------" << std::endl;
    for (const auto& c : summary.cases()) {
        std::cout << std::fixed << std::setprecision(3) << ""
                  << std::setw(50) << std::left << c.bcase.to_string() << ": "
                  << "ms_per_act_cost=" << std::setw(7) << std::right << c.result.ms_per_actual_cost_stats().to_string()
                  << ", ms_per_alt_cost=" << std::setw(7) << std::right << c.result.ms_per_alt_cost_stats().to_string()
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
    BenchmarkCaseSetup(uint32_t num_docs_in,
                       const BenchmarkCase& bcase_in,
                       const std::vector<double>& op_hit_ratios_in,
                       const std::vector<uint32_t>& child_counts_in)
        : num_docs(num_docs_in),
          bcase(bcase_in),
          op_hit_ratios(op_hit_ratios_in),
          child_counts(child_counts_in),
          filter_hit_ratios({1.0}),
          default_values_per_document(0)
    {}
    ~BenchmarkCaseSetup() {}
};

struct BenchmarkSetup {
    uint32_t num_docs;
    std::vector<Config> attr_cfgs;
    std::vector<QueryOperator> query_ops;
    std::vector<bool> strictness;
    std::vector<double> op_hit_ratios;
    std::vector<uint32_t> child_counts;
    std::vector<double> filter_hit_ratios;
    uint32_t default_values_per_document;
    BenchmarkSetup(uint32_t num_docs_in,
                   const std::vector<Config>& attr_cfgs_in,
                   const std::vector<QueryOperator>& query_ops_in,
                   const std::vector<bool>& strictness_in,
                   const std::vector<double>& op_hit_ratios_in,
                   const std::vector<uint32_t>& child_counts_in)
        : num_docs(num_docs_in),
          attr_cfgs(attr_cfgs_in),
          query_ops(query_ops_in),
          strictness(strictness_in),
          op_hit_ratios(op_hit_ratios_in),
          child_counts(child_counts_in),
          filter_hit_ratios({1.0}),
          default_values_per_document(0)
    {}
    BenchmarkSetup(uint32_t num_docs_in,
                   const std::vector<Config>& attr_cfgs_in,
                   const std::vector<QueryOperator>& query_ops_in,
                   const std::vector<bool>& strictness_in,
                   const std::vector<double>& op_hit_ratios_in)
        : BenchmarkSetup(num_docs_in, attr_cfgs_in, query_ops_in, strictness_in, op_hit_ratios_in, {1})
    {}
    BenchmarkCaseSetup make_case_setup(const BenchmarkCase& bcase) const {
        BenchmarkCaseSetup res(num_docs, bcase, op_hit_ratios, child_counts);
        res.default_values_per_document = default_values_per_document;
        if (!bcase.strict) {
            // Simulation of a filter is only relevant in a non-strict context.
            res.filter_hit_ratios = filter_hit_ratios;
        } else {
            res.filter_hit_ratios = {1.0};
        }
        return res;
    }
    ~BenchmarkSetup();
};

BenchmarkSetup::~BenchmarkSetup() = default;

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

BenchmarkCaseResult
run_benchmark_case(const BenchmarkCaseSetup& setup)
{
    BenchmarkCaseResult result;
    std::cout << "-------- run_benchmark_case: " << setup.bcase.to_string() << " --------" << std::endl;
    print_result_header();
    for (double op_hit_ratio : setup.op_hit_ratios) {
        for (uint32_t children : setup.child_counts) {
            uint32_t hits_per_term = calc_hits_per_term(setup.num_docs, op_hit_ratio, children, setup.bcase.query_op);
            HitSpecs hit_specs(55555);
            hit_specs.add(setup.default_values_per_document, setup.num_docs);
            auto terms = hit_specs.add(children, hits_per_term);
            auto attr_ctx = make_attribute_context(setup.bcase.attr_cfg, setup.num_docs, hit_specs);
            for (double filter_hit_ratio : setup.filter_hit_ratios) {
                auto res = run_benchmark(*attr_ctx, setup.bcase.query_op, terms, setup.num_docs + 1,
                                         setup.bcase.strict, filter_hit_ratio);
                print_result(res, terms, op_hit_ratio, filter_hit_ratio, setup.num_docs);
                result.add(res);
            }
        }
    }
    print_result(result);
    return result;
}

void
run_benchmarks(const BenchmarkSetup& setup)
{
    BenchmarkSummary summary;
    for (const auto& attr_cfg : setup.attr_cfgs) {
        for (auto query_op : setup.query_ops) {
            for (bool strict : setup.strictness) {
                BenchmarkCase bcase(attr_cfg, query_op, strict);
                auto case_setup = setup.make_case_setup(bcase);
                auto results = run_benchmark_case(case_setup);
                summary.add(bcase, results);
            }
        }
    }
    summary.calc_scaled_costs();
    print_summary(summary);
}

Config
make_config(BasicType basic_type, CollectionType col_type, bool fast_search)
{
    Config res(basic_type, col_type);
    res.setFastSearch(fast_search);
    return res;
}

constexpr uint32_t num_docs = 10'000'000;
const std::vector<double> base_hit_ratios = {0.001, 0.01, 0.1, 0.5};
const Config int32 = make_config(BasicType::INT32, CollectionType::SINGLE, false);
const Config int32_fs = make_config(BasicType::INT32, CollectionType::SINGLE, true);
const Config int32_array = make_config(BasicType::INT32, CollectionType::ARRAY, false);
const Config int32_array_fs = make_config(BasicType::INT32, CollectionType::ARRAY, true);
const Config int32_wset_fs = make_config(BasicType::INT32, CollectionType::WSET, true);
const Config str = make_config(BasicType::STRING, CollectionType::SINGLE, false);
const Config str_fs = make_config(BasicType::STRING, CollectionType::SINGLE, true);
const Config str_array = make_config(BasicType::STRING, CollectionType::ARRAY, false);
const Config str_array_fs = make_config(BasicType::STRING, CollectionType::ARRAY, true);


TEST(IteratorBenchmark, analyze_term_search_in_attributes_without_fast_search)
{
    std::vector<Config> attr_cfgs = {int32, int32_array, str, str_array};
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8, 1.0};
    BenchmarkSetup setup(num_docs, attr_cfgs, {QueryOperator::Term}, {false}, hit_ratios);
    setup.default_values_per_document = 1;
    setup.filter_hit_ratios = {0.01, 0.1, 0.5, 1.0};
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, analyze_term_search_in_attributes_with_fast_search)
{
    std::vector<Config> attr_cfgs = {int32_fs, int32_array_fs, str_fs, str_array_fs};
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8, 1.0};
    BenchmarkSetup setup(num_docs, attr_cfgs, {QueryOperator::Term}, {true, false}, hit_ratios);
    setup.filter_hit_ratios = {0.01, 0.1, 0.5, 1.0};
    run_benchmarks(setup);
}

TEST(IteratorBenchmark, analyze_complex_leaf_operators)
{
    std::vector<Config> attr_cfgs = {int32_array_fs};
    std::vector<QueryOperator> query_ops = {QueryOperator::In, QueryOperator::DotProduct};
    const std::vector<double> hit_ratios = {0.001, 0.01, 0.1, 0.2, 0.4, 0.6, 0.8};
    BenchmarkSetup setup(num_docs, attr_cfgs, query_ops, {true, false}, hit_ratios, {1, 2, 10, 100});
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

GTEST_MAIN_RUN_ALL_TESTS()
