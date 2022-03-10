// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_searchable.h"
#include "leaf_blueprints.h"
#include "termasstring.h"
#include "create_blueprint_visitor_helper.h"
#include <vespa/vespalib/objects/visit.h>

using search::query::FuzzyTerm;
using search::query::LocationTerm;
using search::query::NearestNeighborTerm;
using search::query::Node;
using search::query::NumberTerm;
using search::query::PredicateQuery;
using search::query::PrefixTerm;
using search::query::RangeTerm;
using search::query::RegExpTerm;
using search::query::StringTerm;
using search::query::SubstringTerm;
using search::query::SuffixTerm;

namespace search::queryeval {

FakeSearchable::FakeSearchable()
    : _tag("<undef>"),
      _map(),
      _is_attr(false)
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
    bool _is_attr;

public:
    LookupVisitor(Searchable &searchable, const IRequestContext & requestContext,
                  const Map &map, const vespalib::string &tag, bool is_attr, const FieldSpec &field);

    ~LookupVisitor();
    template <class TermNode>
    void visitTerm(TermNode &n);

    void visit(NumberTerm &n) override { visitTerm(n); }
    void visit(LocationTerm &n) override { visitTerm(n); }
    void visit(PrefixTerm &n) override { visitTerm(n); }
    void visit(RangeTerm &n) override { visitTerm(n); }
    void visit(StringTerm &n) override { visitTerm(n); }
    void visit(SubstringTerm &n) override { visitTerm(n); }
    void visit(SuffixTerm &n) override { visitTerm(n); }
    void visit(PredicateQuery &n) override { visitTerm(n); }
    void visit(RegExpTerm &n) override { visitTerm(n); }
    void visit(NearestNeighborTerm &n) override { visitTerm(n); }
    void visit(FuzzyTerm &n) override { visitTerm(n); }
};

template <class Map>
LookupVisitor<Map>::LookupVisitor(Searchable &searchable, const IRequestContext & requestContext,
                                  const Map &map, const vespalib::string &tag, bool is_attr, const FieldSpec &field)
    : CreateBlueprintVisitorHelper(searchable, field, requestContext),
      _map(map),
      _tag(tag),
      _is_attr(is_attr)
{}

template <class Map>
LookupVisitor<Map>::~LookupVisitor() = default;

template <class Map>
template <class TermNode>
void
LookupVisitor<Map>::visitTerm(TermNode &n) {
    const vespalib::string term_string = termAsString(n);

    FakeResult result;
    auto pos = _map.find(typename Map::key_type(getField().getName(), term_string));
    if (pos != _map.end()) {
        result = pos->second;
    }
    auto fake = std::make_unique<FakeBlueprint>(getField(), result);
    fake->tag(_tag).is_attr(_is_attr).term(term_string);
    setResult(std::move(fake));
}

} // namespace search::queryeval::<unnamed>

Blueprint::UP
FakeSearchable::createBlueprint(const IRequestContext & requestContext,
                                const FieldSpec &field,
                                const search::query::Node &term)
{
    LookupVisitor<Map> visitor(*this, requestContext, _map, _tag, _is_attr, field);
    const_cast<Node &>(term).accept(visitor);
    return visitor.getResult();
}

FakeSearchable::~FakeSearchable() = default;

}
