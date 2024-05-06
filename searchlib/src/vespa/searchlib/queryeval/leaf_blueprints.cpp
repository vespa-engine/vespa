// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "leaf_blueprints.h"
#include "emptysearch.h"
#include "full_search.h"
#include "simplesearch.h"
#include "fake_search.h"

namespace search::queryeval {

//-----------------------------------------------------------------------------

FlowStats
EmptyBlueprint::calculate_flow_stats(uint32_t) const
{
    return {0.0, 0.2, 0.0};
}

SearchIterator::UP
EmptyBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &) const
{
    return std::make_unique<EmptySearch>();
}

SearchIterator::UP
EmptyBlueprint::createFilterSearch(FilterConstraint /* constraint */) const
{
    return std::make_unique<EmptySearch>();
}

EmptyBlueprint::EmptyBlueprint(FieldSpecBaseList fields)
    : SimpleLeafBlueprint(std::move(fields))
{
}

FlowStats
AlwaysTrueBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    return default_flow_stats(docid_limit, docid_limit, 0);
}

SearchIterator::UP
AlwaysTrueBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &) const
{
    return std::make_unique<FullSearch>();
}

SearchIterator::UP
AlwaysTrueBlueprint::createFilterSearch(FilterConstraint /* constraint */) const
{
    return std::make_unique<FullSearch>();
}

AlwaysTrueBlueprint::AlwaysTrueBlueprint() : SimpleLeafBlueprint()
{
    setEstimate(HitEstimate(search::endDocId, false));
}

//-----------------------------------------------------------------------------

FlowStats
SimpleBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    return default_flow_stats(docid_limit, _result.getHitCount(), 0);
}

SearchIterator::UP
SimpleBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray &) const
{
    auto search = std::make_unique<SimpleSearch>(_result, strict());
    search->tag(_tag);
    return search;
}

SearchIterator::UP
SimpleBlueprint::createFilterSearch(FilterConstraint constraint) const
{
    auto search = std::make_unique<SimpleSearch>(_result, strict());
    search->tag(_tag +
               (strict() ? "<strict," : "<nostrict,") +
               (constraint == FilterConstraint::UPPER_BOUND ? "upper>" : "lower>"));
    return search;
}

SimpleBlueprint::SimpleBlueprint(const SimpleResult &result)
    : SimpleLeafBlueprint(),
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

namespace {

struct FakeContext : attribute::ISearchContext {
    const vespalib::string &name;
    const FakeResult &result;
    FakeContext(const vespalib::string &name_in, const FakeResult &result_in)
        : name(name_in), result(result_in) {}
    int32_t onFind(DocId docid, int32_t elemid, int32_t &weight) const override {
        for (const auto &doc: result.inspect()) {
            if (doc.docId == docid) {
                for (const auto &elem: doc.elements) {
                    if (elem.id >= uint32_t(elemid)) {
                        weight = elem.weight;
                        return elem.id;
                    }
                }
            }
        }
        return -1;
    }
    int32_t onFind(DocId docid, int32_t elem) const override {
        int32_t ignore_weight;
        return onFind(docid, elem, ignore_weight);
    }
    attribute::HitEstimate calc_hit_estimate() const override { return attribute::HitEstimate(0); }
    std::unique_ptr<SearchIterator> createIterator(fef::TermFieldMatchData *, bool) override { abort(); }
    void fetchPostings(const ExecuteInfo &, bool) override { }
    bool valid() const override { return true; }
    Int64Range getAsIntegerTerm() const override { abort(); }
    DoubleRange getAsDoubleTerm() const override { abort(); }
    const QueryTermUCS4 * queryTerm() const override { abort(); }
    const vespalib::string &attributeName() const override { return name; }
    uint32_t get_committed_docid_limit() const noexcept override;
};

uint32_t
FakeContext::get_committed_docid_limit() const noexcept
{
    auto& documents = result.inspect();
    return documents.empty() ? 0 : (documents.back().docId + 1);
}

}

SearchIterator::UP
FakeBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const
{
    auto result = std::make_unique<FakeSearch>(_tag, _field.getName(), _term, _result, tfmda);
    result->attr_ctx(_ctx.get());
    return result;
}

FakeBlueprint::FakeBlueprint(const FieldSpec &field, const FakeResult &result)
    : SimpleLeafBlueprint(field),
      _tag("<tag>"),
      _term("<term>"),
      _field(field),
      _result(result),
      _ctx()
{
    setEstimate(HitEstimate(result.inspect().size(), result.inspect().empty()));
}

FakeBlueprint::~FakeBlueprint() = default;

FakeBlueprint &
FakeBlueprint::is_attr(bool value) {
    if (value) {
        _ctx = std::make_unique<FakeContext>(_field.getName(), _result);
    } else {
        _ctx.reset();
    }
    return *this;
}

}
