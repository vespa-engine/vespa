// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "simpleresult.h"
#include "fake_result.h"
#include "searchable.h"

namespace search {

namespace queryeval {

//-----------------------------------------------------------------------------

class EmptyBlueprint : public SimpleLeafBlueprint
{
protected:
    SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;

public:
    EmptyBlueprint(const FieldSpecBaseList &fields);
    EmptyBlueprint(const FieldSpecBase &field);
    EmptyBlueprint();
};

//-----------------------------------------------------------------------------

class SimpleBlueprint : public SimpleLeafBlueprint
{
private:
    vespalib::string  _tag;
    SimpleResult _result;

protected:
    SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;

public:
    SimpleBlueprint(const SimpleResult &result);
    SimpleBlueprint &tag(const vespalib::string &tag);
    const vespalib::string &tag() const { return _tag; }
};

//-----------------------------------------------------------------------------

class FakeBlueprint : public SimpleLeafBlueprint
{
private:
    vespalib::string _tag;
    vespalib::string _term;
    FieldSpec   _field;
    FakeResult  _result;

protected:
    SearchIterator::UP
    createLeafSearch(const fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;

public:
    FakeBlueprint(const FieldSpec &field,
                  const FakeResult &result);

    FakeBlueprint &tag(const vespalib::string &t) {
        _tag = t;
        return *this;
    }

    FakeBlueprint &term(const vespalib::string &t) {
        _term = t;
        return *this;
    }
};

//-----------------------------------------------------------------------------

} // namespace queryeval
} // namespace search

