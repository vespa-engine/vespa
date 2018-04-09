// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include "blueprintbuilder.h"
#include "matchdatareservevisitor.h"
#include "resolveviewvisitor.h"
#include "termdataextractor.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/rectangle.h>
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
using search::fef::Location;
using search::query::Node;
using search::query::QueryTreeCreator;
using search::query::Weight;
using search::queryeval::AndBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::IRequestContext;
using search::queryeval::SearchIterator;
using vespalib::string;
using std::vector;

namespace proton::matching {

namespace {
void AddLocationNode(const string &location_str, Node::UP &query_tree, Location &fef_location) {
    if (location_str.empty()) {
        return;
    }
    string::size_type pos = location_str.find(':');
    if (pos == string::npos) {
        LOG(warning, "Location string lacks attribute vector specification. loc='%s'",
            location_str.c_str());
        return;
    }
    const string view = PositionDataType::getZCurveFieldName(
            location_str.substr(0, pos));
    const string loc = location_str.substr(pos + 1);

    search::common::Location locationSpec;
    if (!locationSpec.parse(loc)) {
        LOG(warning, "Location parse error (location: '%s'): %s",
            location_str.c_str(),
            locationSpec.getParseError());
        return;
    }

    int32_t id = -1;
    Weight weight(100);

    ProtonAnd::UP new_base(new ProtonAnd);
    new_base->append(std::move(query_tree));

    if (locationSpec.getRankOnDistance()) {
        new_base->append(Node::UP(new ProtonLocationTerm(loc, view, id, weight)));
        fef_location.setAttribute(view);
        fef_location.setXPosition(locationSpec.getX());
        fef_location.setYPosition(locationSpec.getY());
        fef_location.setXAspect(locationSpec.getXAspect());
        fef_location.setValid(true);
    } else if (locationSpec.getPruneOnDistance()) {
        new_base->append(Node::UP(new ProtonLocationTerm(loc, view, id, weight)));
    }
    query_tree = std::move(new_base);
}
}  // namespace

Query::Query() {}
Query::~Query() {}

bool
Query::buildTree(const vespalib::stringref &stack, const string &location,
                 const ViewResolver &resolver, const IIndexEnvironment &indexEnv)
{
    SimpleQueryStackDumpIterator stack_dump_iterator(stack);
    _query_tree = QueryTreeCreator<ProtonNodeTypes>::create(stack_dump_iterator);
    if (_query_tree.get()) {
        AddLocationNode(location, _query_tree, _location);
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
Query::extractLocations(vector<const Location *> &locations)
{
    locations.clear();
    locations.push_back(&_location);
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
        std::unique_ptr<AndBlueprint> andBlueprint(new AndBlueprint());
        (*andBlueprint)
            .addChild(std::move(_blueprint))
            .addChild(std::move(_whiteListBlueprint));
        _blueprint = std::move(andBlueprint);
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
    _blueprint->fetchPostings(true);
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
