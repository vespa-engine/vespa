// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const override;
public:
    EmptyBlueprint(FieldSpecBaseList fields);
    EmptyBlueprint(FieldSpecBase field) : SimpleLeafBlueprint(field) {}
    EmptyBlueprint() = default;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    SearchIterator::UP createFilterSearch(FilterConstraint constraint) const override;
    EmptyBlueprint *as_empty() noexcept final override { return this; }
};

class AlwaysTrueBlueprint : public SimpleLeafBlueprint
{
protected:
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const override;
public:
    AlwaysTrueBlueprint();
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    SearchIterator::UP createFilterSearch(FilterConstraint constraint) const override;
    const AlwaysTrueBlueprint *asAlwaysTrue() const noexcept override { return this; }
};

//-----------------------------------------------------------------------------

class SimpleBlueprint : public SimpleLeafBlueprint
{
private:
    std::string  _tag;
    SimpleResult _result;

protected:
    SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda) const override;
public:
    SimpleBlueprint(const SimpleResult &result);
    ~SimpleBlueprint() override;
    SimpleBlueprint &tag(const std::string &tag);
    const std::string &tag() const { return _tag; }
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    SearchIterator::UP createFilterSearch(FilterConstraint constraint) const override;
};

//-----------------------------------------------------------------------------

class FakeBlueprint : public SimpleLeafBlueprint
{
private:
    std::string _tag;
    std::string _term;
    FieldSpec   _field;
    FakeResult  _result;
    std::unique_ptr<attribute::ISearchContext> _ctx;

protected:
    SearchIterator::UP
    createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const override;

public:
    FakeBlueprint(const FieldSpec &field, const FakeResult &result);
    ~FakeBlueprint() override;

    FakeBlueprint &tag(const std::string &t) {
        _tag = t;
        return *this;
    }
    const std::string &tag() const { return _tag; }

    FakeBlueprint &is_attr(bool value);
    bool is_attr() const { return bool(_ctx); }

    FakeBlueprint &term(const std::string &t) {
        _term = t;
        return *this;
    }

    const attribute::ISearchContext *get_attribute_search_context() const noexcept final {
        return _ctx.get();
    }

    FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        return default_flow_stats(docid_limit, _result.inspect().size(), 0);
    }

    SearchIteratorUP createFilterSearch(FilterConstraint constraint) const override {
        return create_default_filter(constraint);
    }
};

//-----------------------------------------------------------------------------

}
