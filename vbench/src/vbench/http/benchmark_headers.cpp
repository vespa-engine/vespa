// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "benchmark_headers.h"
#include <map>

namespace vbench {

namespace benchmark_headers {
const std::string NUM_HITS        = "X-Yahoo-Vespa-NumHits";
const std::string NUM_FASTHITS    = "X-Yahoo-Vespa-NumFastHits";
const std::string NUM_GROUPHITS   = "X-Yahoo-Vespa-NumGroupHits";
const std::string NUM_ERRORS      = "X-Yahoo-Vespa-NumErrors";
const std::string TOTAL_HIT_COUNT = "X-Yahoo-Vespa-TotalHitCount";
const std::string NUM_DOCSUMS     = "X-Yahoo-Vespa-NumDocsums";
const std::string QUERY_HITS      = "X-Yahoo-Vespa-QueryHits";
const std::string QUERY_OFFSET    = "X-Yahoo-Vespa-QueryOffset";
const std::string SEARCH_TIME     = "X-Yahoo-Vespa-SearchTime";
const std::string ATTR_TIME       = "X-Yahoo-Vespa-AttributeFetchTime";
const std::string FILL_TIME       = "X-Yahoo-Vespa-FillTime";
const std::string DOCS_SEARCHED   = "X-Yahoo-Vespa-DocsSearched";
const std::string NODES_SEARCHED  = "X-Yahoo-Vespa-NodesSearched";
const std::string FULL_COVERAGE   = "X-Yahoo-Vespa-FullCoverage";
struct HeaderTraverser {
    virtual ~HeaderTraverser() { }
    virtual void header(const string &name, double value) = 0;
};
struct HeaderMapper {
    typedef BenchmarkHeaders::Value BenchmarkHeaders::*ValueRef;
    using HeaderMap = std::map<string,ValueRef>;
    using HeaderEntry = std::map<string,ValueRef>::iterator;
    HeaderMap map;
    HeaderMapper() : map() {
        map[NUM_HITS]        = &BenchmarkHeaders::num_hits;
        map[NUM_FASTHITS]    = &BenchmarkHeaders::num_fasthits;
        map[NUM_GROUPHITS]   = &BenchmarkHeaders::num_grouphits;
        map[NUM_ERRORS]      = &BenchmarkHeaders::num_errors;
        map[TOTAL_HIT_COUNT] = &BenchmarkHeaders::total_hit_count;
        map[NUM_DOCSUMS]     = &BenchmarkHeaders::num_docsums;
        map[QUERY_HITS]      = &BenchmarkHeaders::query_hits;
        map[QUERY_OFFSET]    = &BenchmarkHeaders::query_offset;
        map[SEARCH_TIME]     = &BenchmarkHeaders::search_time;
        map[ATTR_TIME]       = &BenchmarkHeaders::attr_time;
        map[FILL_TIME]       = &BenchmarkHeaders::fill_time;
        map[DOCS_SEARCHED]   = &BenchmarkHeaders::docs_searched;
        map[NODES_SEARCHED]  = &BenchmarkHeaders::nodes_searched;
        map[FULL_COVERAGE]   = &BenchmarkHeaders::full_coverage;
    }
    void apply(BenchmarkHeaders &headers, const string &name, const string &string_value) {
        HeaderEntry entry = map.find(name);
        if (entry != map.end()) {
            (headers.*(entry->second)).set(string_value);
        }
    }
    void traverse(const BenchmarkHeaders &headers, HeaderTraverser &traverser) {
        for (HeaderEntry entry = map.begin(); entry != map.end(); ++entry) {
            if ((headers.*(entry->second)).is_set) {
                traverser.header(entry->first, (headers.*(entry->second)).value);
            }
        }
    }
};
HeaderMapper header_mapper;
} // namespace vbench::benchmark_headers

void
BenchmarkHeaders::handleHeader(const string &name, const string &string_value)
{
    benchmark_headers::header_mapper.apply(*this, name, string_value);
}

string
BenchmarkHeaders::toString() const
{
    string str = "";
    struct HeaderToString : benchmark_headers::HeaderTraverser {
        string &str;
        HeaderToString(string &s) : str(s) {}
        void header(const string &name, double value) override {
            str += strfmt("  %s: %g\n", name.c_str(), value);
        }
    } headerToString(str);
    benchmark_headers::header_mapper.traverse(*this, headerToString);
    return str;
}

} // namespace vbench
