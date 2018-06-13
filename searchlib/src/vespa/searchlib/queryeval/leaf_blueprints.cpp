// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprints.h"
#include "emptysearch.h"
#include "simplesearch.h"
#include "fake_search.h"

namespace search::queryeval {

//-----------------------------------------------------------------------------

SearchIterator::UP
EmptyBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &,
                                 bool) const
{
    return std::make_unique<EmptySearch>();
}

EmptyBlueprint::EmptyBlueprint(const FieldSpecBase &field)
    : SimpleLeafBlueprint(field)
{
}

EmptyBlueprint::EmptyBlueprint(const FieldSpecBaseList &fields)
    : SimpleLeafBlueprint(fields)
{
}

EmptyBlueprint::EmptyBlueprint()
    : SimpleLeafBlueprint(FieldSpecBaseList())
{
}

//-----------------------------------------------------------------------------

SearchIterator::UP
SimpleBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &, bool) const
{
    SimpleSearch *ss = new SimpleSearch(_result);
    SearchIterator::UP search(ss);
    ss->tag(_tag);
    return search;
}

SimpleBlueprint::SimpleBlueprint(const SimpleResult &result)
    : SimpleLeafBlueprint(FieldSpecBaseList()),
      _tag(),
      _result(result)
{
    setEstimate(HitEstimate(result.getHitCount(), (result.getHitCount() == 0)));
}

SimpleBlueprint::~SimpleBlueprint() = default;

SimpleBlueprint &
SimpleBlueprint::tag(const vespalib::string &t)
{
    _tag = t;
    return *this;
}

//-----------------------------------------------------------------------------

SearchIterator::UP
FakeBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const
{
    auto result = std::make_unique<FakeSearch>(_tag, _field.getName(), _term, _result, tfmda);
    result->is_attr(_is_attr);
    return result;
}

FakeBlueprint::FakeBlueprint(const FieldSpec &field, const FakeResult &result)
    : SimpleLeafBlueprint(field),
      _tag("<tag>"),
      _term("<term>"),
      _field(field),
      _result(result),
      _is_attr(false)
{
    setEstimate(HitEstimate(result.inspect().size(), result.inspect().empty()));
}

FakeBlueprint::~FakeBlueprint() = default;

}
