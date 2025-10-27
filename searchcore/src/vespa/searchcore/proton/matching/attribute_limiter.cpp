// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_limiter.h"
#include "rangequerylocator.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchlib/query/tree/range.h>
#include <vespa/searchlib/query/tree/simplequery.h>

using namespace search::queryeval;
using namespace search::query;
using vespalib::make_string;
using std::string;
using vespalib::make_string_short::fmt;

namespace proton::matching {

AttributeLimiter::AttributeLimiter(const RangeQueryLocator & rangeQueryLocator,
                                   Searchable &searchable_attributes,
                                   const IRequestContext & requestContext,
                                   const string &attribute_name,
                                   bool descending,
                                   const string &diversity_attribute,
                                   double diversityCutoffFactor,
                                   DiversityCutoffStrategy diversityCutoffStrategy)
    : _searchable_attributes(searchable_attributes),
      _requestContext(requestContext),
      _rangeQueryLocator(rangeQueryLocator),
      _attribute_name(attribute_name),
      _descending(descending),
      _diversity_attribute(diversity_attribute),
      _lock(),
      _match_datas(),
      _blueprint(),
      _estimatedHits(-1),
      _diversityCutoffFactor(diversityCutoffFactor),
      _diversityCutoffStrategy(diversityCutoffStrategy)
{
}

bool
AttributeLimiter::was_used() const
{
    return (_estimatedHits.load(std::memory_order_relaxed) >= 0);
}

ssize_t
AttributeLimiter::getEstimatedHits() const
{
    return _estimatedHits.load(std::memory_order_relaxed);
}

AttributeLimiter::~AttributeLimiter() = default;

namespace {

std::string STRICT_STR("strict");
std::string LOOSE_STR("loose");

}

AttributeLimiter::DiversityCutoffStrategy
AttributeLimiter::toDiversityCutoffStrategy(std::string_view strategy)
{
    return (strategy == STRICT_STR)
        ? DiversityCutoffStrategy::STRICT
        : DiversityCutoffStrategy::LOOSE;
}

const std::string &
toString(AttributeLimiter::DiversityCutoffStrategy strategy)
{
    return (strategy == AttributeLimiter::DiversityCutoffStrategy::STRICT) ? STRICT_STR : LOOSE_STR;
}

AttributeLimiter::BlueprintAndMatchData
AttributeLimiter::create_match_data(size_t want_hits, size_t max_group_size, double hit_rate, bool strictSearch) {
    std::lock_guard<std::mutex> guard(_lock);
    const uint32_t my_field_id = 0;
    search::fef::MatchDataLayout layout;
    auto my_handle = layout.allocTermField(my_field_id);
    if (!_blueprint) {
        RangeLimitMetaInfo rangeInfo = _rangeQueryLocator.locate();
        const uint32_t no_unique_id = 0;
        string range_spec = fmt("[%s;%s;%s%zu", rangeInfo.low().c_str(), rangeInfo.high().c_str(),
                                _descending ? "-" : "", want_hits);
        if (max_group_size < want_hits) {
            size_t cutoffGroups = (_diversityCutoffFactor * want_hits) / max_group_size;
            range_spec.append(fmt(";%s;%zu;%zu;%s]", _diversity_attribute.c_str(), max_group_size,
                                  cutoffGroups, toString(_diversityCutoffStrategy).c_str()));
        } else {
            range_spec.push_back(']');
        }
        Range range(range_spec);
        SimpleRangeTerm node(range, _attribute_name, no_unique_id, Weight(0));
        FieldSpecList field; // single field API is protected
        field.add(FieldSpec(_attribute_name, my_field_id, my_handle));
        _blueprint = _searchable_attributes.createBlueprint(_requestContext, field, node, layout);
        uint32_t dummy_docid_limit = 1337;
        _blueprint->basic_plan(strictSearch, dummy_docid_limit);
        //TODO use_estimate must be switched to true quite soon
        //TODO Use thread_bundle once verified(soon), _requestContext.thread_bundle()
        auto execInfo = ExecuteInfo::create(strictSearch ? 1.0 : hit_rate, _requestContext.getDoom(),
                                            vespalib::ThreadBundle::trivial());
        _blueprint->fetchPostings(execInfo);
        _estimatedHits.store(_blueprint->getState().estimate().estHits, std::memory_order_relaxed);
        _blueprint->freeze();
    }
    _match_datas.push_back(layout.createMatchData());
    return {*_blueprint, *_match_datas.back()};
}

std::unique_ptr<SearchIterator>
AttributeLimiter::create_search(size_t want_hits, size_t max_group_size, double hit_rate, bool strictSearch) {
    auto [blueprint, match_data] = create_match_data(want_hits, max_group_size, hit_rate, strictSearch);
    return blueprint.createSearch(match_data);
}

}
