// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include "blueprintbuilder.h"
#include "matchdatareservevisitor.h"
#include "resolveviewvisitor.h"
#include "termdataextractor.h"
#include "sameelementmodifier.h"
#include "unpacking_iterators_optimizer.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.query");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using document::PositionDataType;
using search::SimpleQueryStackDumpIterator;
using search::fef::IIndexEnvironment;
using search::fef::ITermData;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::query::Node;
using search::query::QueryTreeCreator;
using search::query::Weight;
using search::queryeval::AndBlueprint;
using search::queryeval::AndNotBlueprint;
using search::queryeval::RankBlueprint;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::IRequestContext;
using search::queryeval::SearchIterator;
using vespalib::string;
using std::vector;

namespace proton::matching {

namespace {

Node::UP
inject(Node::UP query, Node::UP to_inject) {
    if (auto * my_and = dynamic_cast<search::query::And *>(query.get())) {
        my_and->append(std::move(to_inject));
    } else  if (dynamic_cast<search::query::Rank *>(query.get()) || dynamic_cast<search::query::AndNot *>(query.get())) {
        search::query::Intermediate & root = static_cast<search::query::Intermediate &>(*query);
        root.prepend(inject(root.stealFirst(), std::move(to_inject)));
    } else {
        auto new_root = std::make_unique<ProtonAnd>();
        new_root->append(std::move(query));
        new_root->append(std::move(to_inject));
        query = std::move(new_root);
    }
    return query;
}

std::vector<const ProtonLocationTerm *>
fix_location_terms(Node *tree) {
    std::vector<const ProtonLocationTerm *> retval;
    std::vector<Node *> nodes;
    nodes.push_back(tree);
    for (size_t i = 0; i < nodes.size(); ++i) {
        if (auto loc = dynamic_cast<ProtonLocationTerm *>(nodes[i])) {
            const string old_view = loc->getView();
            loc->setView(PositionDataType::getZCurveFieldName(old_view));
            retval.push_back(loc);
        }
        if (auto parent = dynamic_cast<const search::query::Intermediate *>(nodes[i])) {
            for (Node * child : parent->getChildren()) {
                nodes.push_back(child);
            }
        }
    }
    return retval;
}

struct ParsedLocationString {
    bool valid;
    string view;
    search::common::GeoLocationSpec locationSpec;
    string loc_term;
    ParsedLocationString() : valid(false), view(), locationSpec(), loc_term() {}
    ~ParsedLocationString() {}
};
  
ParsedLocationString parseQueryLocationString(string str) {
    ParsedLocationString retval;
    if (str.empty()) {
        return retval;
    }
    search::common::GeoLocationParser locationParser;
    if (locationParser.parseOldFormatWithField(str)) {
        auto spec = locationParser.spec();
        retval.locationSpec = spec;
        retval.view = PositionDataType::getZCurveFieldName(spec.getFieldName());
        retval.loc_term = str.substr(str.find(':') + 1);
        retval.valid = true;
    } else {
        LOG(warning, "Location parse error (location: '%s'): %s", str.c_str(), locationParser.getParseError());
    }
    return retval;
}

void exchangeLocationNodes(const string &location_str,
                           Node::UP &query_tree,
                           std::vector<search::fef::Location> &fef_locations)
{
    using Spec = std::pair<string, search::common::GeoLocationSpec>;
    std::vector<Spec> locationSpecs;

    auto parsed = parseQueryLocationString(location_str);
    if (parsed.valid) {
        locationSpecs.emplace_back(parsed.view, parsed.locationSpec);
    }
    for (const ProtonLocationTerm * pterm : fix_location_terms(query_tree.get())) {
        const string view = pterm->getView();
        const string loc = pterm->getTerm().getLocationString();
        search::common::GeoLocationParser loc_parser;
        if (loc_parser.parseOldFormat(loc)) {
            locationSpecs.emplace_back(view, loc_parser.spec());
        } else {
            LOG(warning, "GeoLocationItem in query had invalid location string: %s", loc.c_str());
        }
    }
    for (const Spec &spec : locationSpecs) {
        if (spec.second.hasPoint()) {
            search::fef::Location fef_loc;
            fef_loc.setAttribute(spec.first);
            fef_loc.setXPosition(spec.second.getX());
            fef_loc.setYPosition(spec.second.getY());
            fef_loc.setXAspect(spec.second.getXAspect());
            fef_loc.setValid(true);
            fef_locations.push_back(fef_loc);
        }
    }
    if (parsed.valid) {
        int32_t id = -1;
        Weight weight(100);
        query_tree = inject(std::move(query_tree),
                            std::make_unique<ProtonLocationTerm>(parsed.locationSpec, parsed.view, id, weight));
    }
}

IntermediateBlueprint *
asRankOrAndNot(Blueprint * blueprint) {
    IntermediateBlueprint * rankOrAndNot = dynamic_cast<RankBlueprint*>(blueprint);
    if (rankOrAndNot == nullptr) {
        rankOrAndNot = dynamic_cast<AndNotBlueprint*>(blueprint);
    }
    return rankOrAndNot;
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
                 const ViewResolver &resolver, const IIndexEnvironment &indexEnv,
                 bool split_unpacking_iterators, bool delay_unpacking_iterators)
{
    SimpleQueryStackDumpIterator stack_dump_iterator(stack);
    _query_tree = QueryTreeCreator<ProtonNodeTypes>::create(stack_dump_iterator);
    if (_query_tree) {
        SameElementModifier prefixSameElementSubIndexes;
        _query_tree->accept(prefixSameElementSubIndexes);
        exchangeLocationNodes(location, _query_tree, _locations);
        _query_tree = UnpackingIteratorsOptimizer::optimize(std::move(_query_tree),
                bool(_whiteListBlueprint), split_unpacking_iterators, delay_unpacking_iterators);
        ResolveViewVisitor resolve_visitor(resolver, indexEnv);
        _query_tree->accept(resolve_visitor);
        return true;
    } else {
        // TODO(havardpe): log warning or pass message upwards
        return false;
    }
}

void
Query::extractTerms(vector<const ITermData *> &terms)
{
    TermDataExtractor::extractTerms(*_query_tree, terms);
}

void
Query::extractLocations(vector<const search::fef::Location *> &locations)
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
Query::handle_global_filters(uint32_t docid_limit, double global_filter_limit)
{
    using search::queryeval::GlobalFilter;
    double estimated_hit_ratio = _blueprint->getState().hit_ratio(docid_limit);
    if (_blueprint->getState().want_global_filter() && estimated_hit_ratio >= global_filter_limit) {
        auto constraint = Blueprint::FilterConstraint::UPPER_BOUND;
        bool strict = true;
        auto filter_iterator = _blueprint->createFilterSearch(strict, constraint);
        filter_iterator->initRange(1, docid_limit);
        auto white_list = filter_iterator->get_hits(1);
        auto global_filter = GlobalFilter::create(std::move(white_list));
        _blueprint->set_global_filter(*global_filter);
        // optimized order may change after accounting for global filter:
        _blueprint = Blueprint::optimize(std::move(_blueprint));
        LOG(debug, "blueprint after handle_global_filters:\n%s\n", _blueprint->asString().c_str());
        // strictness may change if optimized order changed:
        fetchPostings();
    }
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
