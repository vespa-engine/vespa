// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "simpleresult.h"
#include "fake_result.h"
#include "searchable.h"

namespace search::queryeval {

//-----------------------------------------------------------------------------

class EmptyBlueprint : public SimpleLeafBlueprint
{
protected:
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
public:
    EmptyBlueprint(FieldSpecBaseList fields);
    EmptyBlueprint(FieldSpecBase field) : SimpleLeafBlueprint(field) {}
    EmptyBlueprint() = default;
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;
};

class AlwaysTrueBlueprint : public SimpleLeafBlueprint
{
protected:
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
public:
    AlwaysTrueBlueprint();
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;
};

//-----------------------------------------------------------------------------

class SimpleBlueprint : public SimpleLeafBlueprint
{
private:
    vespalib::string  _tag;
    SimpleResult _result;

protected:
    SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
public:
    SimpleBlueprint(const SimpleResult &result);
    ~SimpleBlueprint() override;
    SimpleBlueprint &tag(const vespalib::string &tag);
    const vespalib::string &tag() const { return _tag; }
    SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;
};

//-----------------------------------------------------------------------------

class FakeBlueprint : public SimpleLeafBlueprint
{
private:
    vespalib::string _tag;
    vespalib::string _term;
    FieldSpec   _field;
    FakeResult  _result;
    std::unique_ptr<attribute::ISearchContext> _ctx;

protected:
    SearchIterator::UP
    createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;

public:
    FakeBlueprint(const FieldSpec &field, const FakeResult &result);
    ~FakeBlueprint() override;

    FakeBlueprint &tag(const vespalib::string &t) {
        _tag = t;
        return *this;
    }
    const vespalib::string &tag() const { return _tag; }

    FakeBlueprint &is_attr(bool value);
    bool is_attr() const { return bool(_ctx); }

    FakeBlueprint &term(const vespalib::string &t) {
        _term = t;
        return *this;
    }

    const attribute::ISearchContext *get_attribute_search_context() const override {
        return _ctx.get();
    }

    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }
};

//-----------------------------------------------------------------------------

}
