// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include "blueprintbuilder.h"
#include "matchdatareservevisitor.h"
#include "resolveviewvisitor.h"
#include "sameelementmodifier.h"
#include "termdataextractor.h"
#include "unpacking_iterators_optimizer.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/thread_bundle.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.query");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using document::PositionDataType;
using search::SimpleQueryStackDumpIterator;
using search::common::GeoLocation;
using search::common::GeoLocationParser;
using search::common::GeoLocationSpec;
using search::fef::IIndexEnvironment;
using search::fef::ITermData;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::query::LocationTerm;
using search::query::Node;
using search::query::QueryTreeCreator;
using search::query::Weight;
using search::queryeval::AndBlueprint;
using search::queryeval::AndNotBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::GlobalFilter;
using search::queryeval::IRequestContext;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::RankBlueprint;
using search::queryeval::SearchIterator;
using vespalib::Issue;
using vespalib::string;
using std::vector;

namespace proton::matching {

namespace {

Node::UP
inject(Node::UP query, Node::UP to_inject) {
    if (auto * my_and = dynamic_cast<search::query::And *>(query.get())) {
        my_and->append(std::move(to_inject));
    } else  if (dynamic_cast<search::query::Rank *>(query.get()) || dynamic_cast<search::query::AndNot *>(query.get())) {
        auto & root = static_cast<search::query::Intermediate &>(*query);
        root.prepend(inject(root.stealFirst(), std::move(to_inject)));
    } else {
        auto new_root = std::make_unique<ProtonAnd>();
        new_root->append(std::move(query));
        new_root->append(std::move(to_inject));
        query = std::move(new_root);
    }
    return query;
}

void
find_location_terms(Node *node, std::vector<LocationTerm *> & locations) {
    if (node->isLocationTerm() ) {
        locations.push_back(static_cast<LocationTerm *>(node));
    } else if (node->isIntermediate()) {
        auto parent = static_cast<const search::query::Intermediate *>(node);
        for (Node * child : parent->getChildren()) {
            find_location_terms(child, locations);
        }
    }
}

std::vector<LocationTerm *>
find_location_terms(Node *tree) {
    std::vector<LocationTerm *> locations;
    find_location_terms(tree, locations);
    return locations;
}

GeoLocationSpec parse_location_string(const string & str) {
    GeoLocationSpec empty;
    if (str.empty()) {
        return empty;
    }
    GeoLocationParser parser;
    if (parser.parseWithField(str)) {
        auto attr_name = PositionDataType::getZCurveFieldName(parser.getFieldName());
        return GeoLocationSpec{attr_name, parser.getGeoLocation()};
    } else {
        Issue::report("Location parse error (location: '%s'): %s", str.c_str(), parser.getParseError());
    }
    return empty;
}

GeoLocationSpec process_location_term(LocationTerm &pterm) {
    auto old_view = pterm.getView();
    auto new_view = PositionDataType::getZCurveFieldName(old_view);
    pterm.setView(new_view);
    const GeoLocation &loc = pterm.getTerm();
    return GeoLocationSpec{new_view, loc};
}

void exchange_location_nodes(const string &location_str,
                             Node::UP &query_tree,
                             std::vector<GeoLocationSpec> &fef_locations) __attribute__((noinline));

void exchange_location_nodes(const string &location_str,
                           Node::UP &query_tree,
                           std::vector<GeoLocationSpec> &fef_locations)
{
    std::vector<GeoLocationSpec> locationSpecs;

    auto parsed = parse_location_string(location_str);
    if (parsed.location.valid()) {
        locationSpecs.push_back(parsed);
    }
    for (LocationTerm * pterm : find_location_terms(query_tree.get())) {
        auto spec = process_location_term(*pterm);
        if (spec.location.valid()) {
            locationSpecs.push_back(spec);
        }
    }
    for (const GeoLocationSpec &spec : locationSpecs) {
        if (spec.location.has_point) {
            fef_locations.push_back(spec);
        }
    }
    if (parsed.location.can_limit()) {
        int32_t id = -1;
        Weight weight(100);
        query_tree = inject(std::move(query_tree),
                            std::make_unique<ProtonLocationTerm>(parsed.location, parsed.field_name, id, weight));
    }
}

IntermediateBlueprint *
asRankOrAndNot(Blueprint * blueprint) {
    return ((blueprint->isAndNot() || blueprint->isRank()))
        ? static_cast<IntermediateBlueprint *>(blueprint)
        : nullptr;
}

IntermediateBlueprint *
lastConsequtiveRankOrAndNot(Blueprint * blueprint) {
    IntermediateBlueprint * prev = nullptr;
    IntermediateBlueprint * curr = asRankOrAndNot(blueprint);
    while (curr != nullptr) {
        prev =  curr;
        curr = asRankOrAndNot(&curr->getChild(0));
    }
    return prev;
}

}  // namespace

Query::Query() = default;
Query::~Query() = default;

bool
Query::buildTree(vespalib::stringref stack, const string &location,
                 const ViewResolver &resolver, const IIndexEnvironment &indexEnv)
{
    return buildTree(stack, location, resolver, indexEnv, true);
}
bool
Query::buildTree(vespalib::stringref stack, const string &location,
                 const ViewResolver &resolver, const IIndexEnvironment &indexEnv,
                 bool split_unpacking_iterators)
{
    SimpleQueryStackDumpIterator stack_dump_iterator(stack);
    _query_tree = QueryTreeCreator<ProtonNodeTypes>::create(stack_dump_iterator);
    if (_query_tree) {
        SameElementModifier prefixSameElementSubIndexes;
        _query_tree->accept(prefixSameElementSubIndexes);
        exchange_location_nodes(location, _query_tree, _locations);
        _query_tree = UnpackingIteratorsOptimizer::optimize(std::move(_query_tree),
                bool(_whiteListBlueprint), split_unpacking_iterators);
        ResolveViewVisitor resolve_visitor(resolver, indexEnv);
        _query_tree->accept(resolve_visitor);
        return true;
    } else {
        Issue::report("invalid query");
        return false;
    }
}

void
Query::extractTerms(vector<const ITermData *> &terms)
{
    TermDataExtractor::extractTerms(*_query_tree, terms);
}

void
Query::extractLocations(vector<const GeoLocationSpec *> &locations)
{
    locations.clear();
    for (const auto & loc : _locations) {
        locations.push_back(&loc);
    }
}

void
Query::setWhiteListBlueprint(Blueprint::UP whiteListBlueprint)
{
    _whiteListBlueprint = std::move(whiteListBlueprint);
}

void
Query::reserveHandles(const IRequestContext & requestContext, ISearchContext &context, MatchDataLayout &mdl)
{
    MatchDataReserveVisitor reserve_visitor(mdl);
    _query_tree->accept(reserve_visitor);

    _blueprint = BlueprintBuilder::build(requestContext, *_query_tree, context);
    LOG(debug, "original blueprint:\n%s\n", _blueprint->asString().c_str());
    if (_whiteListBlueprint) {
        auto andBlueprint = std::make_unique<AndBlueprint>();
        IntermediateBlueprint * rankOrAndNot = lastConsequtiveRankOrAndNot(_blueprint.get());
        if (rankOrAndNot != nullptr) {
            (*andBlueprint)
                    .addChild(rankOrAndNot->removeChild(0))
                    .addChild(std::move(_whiteListBlueprint));
            rankOrAndNot->insertChild(0, std::move(andBlueprint));
        } else {
            (*andBlueprint)
                    .addChild(std::move(_blueprint))
                    .addChild(std::move(_whiteListBlueprint));
            _blueprint = std::move(andBlueprint);
        }
        _blueprint->setDocIdLimit(context.getDocIdLimit());
        LOG(debug, "blueprint after white listing:\n%s\n", _blueprint->asString().c_str());
    }
}

void
Query::optimize()
{
    _blueprint = Blueprint::optimize(std::move(_blueprint));
    LOG(debug, "optimized blueprint:\n%s\n", _blueprint->asString().c_str());
}

void
Query::fetchPostings()
{
    _blueprint->fetchPostings(search::queryeval::ExecuteInfo::create(true, 1.0));
}

void
Query::handle_global_filter(uint32_t docid_limit, double global_filter_lower_limit, double global_filter_upper_limit,
                            vespalib::ThreadBundle &thread_bundle, search::engine::Trace& trace)
{
    if (!handle_global_filter(*_blueprint, docid_limit, global_filter_lower_limit, global_filter_upper_limit, thread_bundle, &trace)) {
        return;
    }
    // optimized order may change after accounting for global filter:
    trace.addEvent(5, "Optimize query execution plan to account for global filter");
    _blueprint = Blueprint::optimize(std::move(_blueprint));
    LOG(debug, "blueprint after handle_global_filter:\n%s\n", _blueprint->asString().c_str());
    // strictness may change if optimized order changed:
    fetchPostings();
}

bool
Query::handle_global_filter(Blueprint& blueprint, uint32_t docid_limit,
                            double global_filter_lower_limit, double global_filter_upper_limit,
                            vespalib::ThreadBundle &thread_bundle, search::engine::Trace* trace)
{
    using search::queryeval::GlobalFilter;
    double estimated_hit_ratio = blueprint.getState().hit_ratio(docid_limit);
    if (!blueprint.getState().want_global_filter()) {
        return false;
    }

    if (estimated_hit_ratio < global_filter_lower_limit) {
        if (trace && trace->shouldTrace(5)) {
            trace->addEvent(5, vespalib::make_string("Skip calculate global filter (estimated_hit_ratio (%f) < lower_limit (%f))",
                                                     estimated_hit_ratio, global_filter_lower_limit));
        }
        return false;
    }

    std::shared_ptr<GlobalFilter> global_filter;
    if (estimated_hit_ratio <= global_filter_upper_limit) {
        if (trace && trace->shouldTrace(5)) {
            trace->addEvent(5, vespalib::make_string("Calculate global filter (estimated_hit_ratio (%f) <= upper_limit (%f))",
                                                     estimated_hit_ratio, global_filter_upper_limit));
        }
        global_filter = GlobalFilter::create(blueprint, docid_limit, thread_bundle);
        if (!global_filter->is_active() && trace && trace->shouldTrace(5)) {
            trace->addEvent(5, "Global filter matches everything");
        }
    } else {
        if (trace && trace->shouldTrace(5)) {
            trace->addEvent(5, vespalib::make_string("Create match everything global filter (estimated_hit_ratio (%f) > upper_limit (%f))",
                                                     estimated_hit_ratio, global_filter_upper_limit));
        }
        global_filter = GlobalFilter::create();
    }
    if (trace) {
        trace->addEvent(5, "Handle global filter in query execution plan");
    }
    blueprint.set_global_filter(*global_filter, estimated_hit_ratio);
    return true;
}

void
Query::freeze()
{
    _blueprint->freeze();
}

Blueprint::HitEstimate
Query::estimate() const
{
    return _blueprint->getState().estimate();
}

SearchIterator::UP
Query::createSearch(MatchData &md) const
{
    return _blueprint->createSearch(md, true);
}

}
