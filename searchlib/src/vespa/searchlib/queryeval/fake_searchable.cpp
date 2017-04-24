// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_searchable.h"
#include "leaf_blueprints.h"
#include "termasstring.h"
#include "create_blueprint_visitor_helper.h"
#include <vespa/vespalib/objects/visit.h>

using search::query::NumberTerm;
using search::query::LocationTerm;
using search::query::Node;
using search::query::PredicateQuery;
using search::query::PrefixTerm;
using search::query::RangeTerm;
using search::query::RegExpTerm;
using search::query::StringTerm;
using search::query::SubstringTerm;
using search::query::SuffixTerm;

namespace search {
namespace queryeval {

FakeSearchable::FakeSearchable()
    : _tag("<undef>"),
      _map()
{
}

FakeSearchable &
FakeSearchable::addResult(const vespalib::string &field,
                          const vespalib::string &term,
                          const FakeResult &result)
{
    _map[Key(field, term)] = result;
    return *this;
}

namespace {

/**
 * Determines the correct LookupResult to use.
 **/
template <class Map>
class LookupVisitor : public CreateBlueprintVisitorHelper
{
    const Map &_map;
    const vespalib::string _tag;

public:
    LookupVisitor(Searchable &searchable, const IRequestContext & requestContext,
                  const Map &map, const vespalib::string &tag, const FieldSpec &field);

    ~LookupVisitor();
    template <class TermNode>
    void visitTerm(TermNode &n);

    virtual void visit(NumberTerm &n) override { visitTerm(n); }
    virtual void visit(LocationTerm &n) override { visitTerm(n); }
    virtual void visit(PrefixTerm &n) override { visitTerm(n); }
    virtual void visit(RangeTerm &n) override { visitTerm(n); }
    virtual void visit(StringTerm &n) override { visitTerm(n); }
    virtual void visit(SubstringTerm &n) override { visitTerm(n); }
    virtual void visit(SuffixTerm &n) override { visitTerm(n); }
    virtual void visit(PredicateQuery &n) override { visitTerm(n); }
    virtual void visit(RegExpTerm &n) override { visitTerm(n); }
};

template <class Map>
LookupVisitor<Map>::LookupVisitor(Searchable &searchable, const IRequestContext & requestContext,
                                  const Map &map, const vespalib::string &tag, const FieldSpec &field)
    : CreateBlueprintVisitorHelper(searchable, field, requestContext),
      _map(map),
      _tag(tag)
{}

template <class Map>
LookupVisitor<Map>::~LookupVisitor() { }

template <class Map>
template <class TermNode>
void
LookupVisitor<Map>::visitTerm(TermNode &n) {
    const vespalib::string term_string = termAsString(n);

    FakeResult result;
    typename Map::const_iterator pos =
            _map.find(typename Map::key_type(getField().getName(), term_string));
    if (pos != _map.end()) {
        result = pos->second;
    }
    FakeBlueprint *fake = new FakeBlueprint(getField(), result);
    Blueprint::UP b(fake);
    fake->tag(_tag).term(term_string);
    setResult(std::move(b));
}

} // namespace search::queryeval::<unnamed>

Blueprint::UP
FakeSearchable::createBlueprint(const IRequestContext & requestContext,
                                const FieldSpec &field,
                                const search::query::Node &term)
{
    LookupVisitor<Map> visitor(*this, requestContext, _map, _tag, field);
    const_cast<Node &>(term).accept(visitor);
    return visitor.getResult();
}

FakeSearchable::~FakeSearchable()
{
}

} // namespace search::queryeval
} // namespace search
