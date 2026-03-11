// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/i_attribute_functor.h>
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/readable_attribute_vector.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/array_bool_blueprint.h>
#include <vespa/searchlib/queryeval/array_bool_search.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>
#include <cassert>
#include <list>
#include <memory>

using search::AttributeBlueprintFactory;
using search::AttributeContext;
using search::AttributeFactory;
using search::AttributeGuard;
using search::AttributeVector;
using search::IAttributeManager;
using search::QueryTermSimple;
using search::attribute::ArrayBoolAttribute;
using search::attribute::AttributeReadGuard;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeFunctor;
using search::attribute::ReadableAttributeVector;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::queryeval::ArrayBoolBlueprint;
using search::queryeval::ArrayBoolSearch;
using search::queryeval::Blueprint;
using search::queryeval::FakeRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::SameElementBlueprint;
using search::queryeval::SameElementSearch;
using search::queryeval::SearchIterator;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;

namespace {
/***********************************************************************************************************************
 * Helper functions
 **********************************************************************************************************************/
// Generate all subsets of a given vector
// Used to generate element filters
std::list<std::vector<uint32_t>> all_subsets(const std::vector<uint32_t>& nums) {
    std::list<std::vector<uint32_t>> subsets { std::vector<uint32_t>() };

    for (uint32_t n : nums) {
        size_t num_elements = subsets.size();
        auto it = subsets.begin();
        for (size_t i = 0; i < num_elements; ++i) {
            std::vector<uint32_t> subset(*it);
            subset.push_back(n);
            subsets.emplace_back(std::move(subset));
            ++it;
        }
    }

    return subsets;
}

std::list<std::vector<uint32_t>> all_non_empty_subsets(const std::vector<uint32_t>& nums) {
    auto subsets = all_subsets(nums);
    subsets.pop_front();
    return subsets;
}

/***********************************************************************************************************************
 * Helper classes
 **********************************************************************************************************************/

/**
 * TestAttribute is a convenience class to get an ArrayBoolAttribute
 **/
struct TestAttribute {
    std::shared_ptr<AttributeVector> attr;
    ArrayBoolAttribute*              bool_attr;

    TestAttribute();
    ~TestAttribute();
    void reset(bool add_reserved);
};

TestAttribute::TestAttribute()
    : attr(),
      bool_attr(nullptr) {
    reset(true);
}

TestAttribute::~TestAttribute() = default;

void TestAttribute::reset(bool add_reserved) {
    Config cfg(BasicType::BOOL, CollectionType::ARRAY);
    attr = AttributeFactory::createAttribute("array_bool", cfg);
    bool_attr = &dynamic_cast<ArrayBoolAttribute&>(*attr);
    if (add_reserved) {
        attr->addReservedDoc();
    }
}

/**
 * TestMatchData is a convenience class to get TermFieldMatchData
 **/
struct TestMatchData {
    std::unique_ptr<MatchDataLayout> mdl;
    FieldSpec                        field_spec;
    FieldSpec                        field_spec2;
    FieldSpec                        field_spec3;
    TermFieldHandle                  handle;
    TermFieldHandle                  handle2;
    TermFieldHandle                  handle3;
    std::unique_ptr<MatchData>       md;
    TermFieldMatchData*              tfmd;
    TermFieldMatchData*              tfmd2;
    TermFieldMatchData*              tfmd3;

    TestMatchData();
    ~TestMatchData();
};

TestMatchData::TestMatchData()
    : mdl(std::make_unique<MatchDataLayout>()),
      field_spec("foo", 0, mdl->allocTermField(0)),
      field_spec2("bar", 1, mdl->allocTermField(1)),
      field_spec3("baz", 2, mdl->allocTermField(2)),
      handle(field_spec.getHandle()),
      handle2(field_spec2.getHandle()),
      handle3(field_spec3.getHandle()),
      md(mdl->createMatchData()),
      tfmd(md->resolveTermField(handle)),
      tfmd2(md->resolveTermField(handle2)),
      tfmd3(md->resolveTermField(handle3)) {
}

TestMatchData::~TestMatchData() = default;

/***********************************************************************************************************************
 * Builder classes
 **********************************************************************************************************************/

/**
 * Base class for convenience classes to get iterators
 */
class SearchBuilder {
protected:
    TestMatchData                    _tmd;
    ArrayBoolAttribute*              _bool_attr;
    std::shared_ptr<AttributeVector> _attribute_vector;
    std::vector<uint32_t>            _element_filter;
    bool                             _want_true;

public:
    SearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    virtual ~SearchBuilder();
    virtual TermFieldMatchData* tfmd() const;
    virtual std::unique_ptr<SearchIterator> create_search(bool strict) const = 0;
};

SearchBuilder::SearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : _tmd(),
      _bool_attr(bool_attr),
      _attribute_vector(std::move(attribute_vector)),
      _element_filter(element_filter),
      _want_true(want_true) {
}

SearchBuilder::~SearchBuilder() = default;

TermFieldMatchData* SearchBuilder::tfmd() const {
    return _tmd.tfmd;
}

/**
 * ArrayBoolSearchBuilder is a convenience class to get an ArrayBoolSearch
 */
class ArrayBoolSearchBuilder : public SearchBuilder {
public:
    ArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~ArrayBoolSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

ArrayBoolSearchBuilder::ArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true) {
}

ArrayBoolSearchBuilder::~ArrayBoolSearchBuilder() = default;

std::unique_ptr<SearchIterator> ArrayBoolSearchBuilder::create_search(bool strict) const {
    return ArrayBoolSearch::create(*_bool_attr, _element_filter, _want_true, strict, _tmd.tfmd);
}

/**
 * SameElementArrayBoolSearchBuilder is a convenience class to get a SameElementSearch with an ArrayBoolSearch
 */
class SameElementArrayBoolSearchBuilder : public  SearchBuilder {
public:
    SameElementArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~SameElementArrayBoolSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

SameElementArrayBoolSearchBuilder::SameElementArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true) {
}

SameElementArrayBoolSearchBuilder::~SameElementArrayBoolSearchBuilder() = default;

std::unique_ptr<SearchIterator> SameElementArrayBoolSearchBuilder::create_search(bool strict) const {
    std::vector<std::unique_ptr<SearchIterator>> ab_search;
    ab_search.emplace_back(ArrayBoolSearch::create(*_bool_attr, _element_filter, _want_true, strict, _tmd.tfmd2));
    std::vector<TermFieldMatchData*> children_tfmd;
    children_tfmd.push_back(_tmd.tfmd2);
    return std::make_unique<SameElementSearch>(*(_tmd.tfmd), children_tfmd, std::move(ab_search), strict, _element_filter);
}

/**
 * SameElementMultiArrayBoolSearchBuilder is a convenience class to get a SameElementSearch with two ArrayBoolSearches
 */
class SameElementMultiArrayBoolSearchBuilder : public  SearchBuilder {
public:
    SameElementMultiArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~SameElementMultiArrayBoolSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

SameElementMultiArrayBoolSearchBuilder::SameElementMultiArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true) {
}

SameElementMultiArrayBoolSearchBuilder::~SameElementMultiArrayBoolSearchBuilder() = default;

std::unique_ptr<SearchIterator> SameElementMultiArrayBoolSearchBuilder::create_search(bool strict) const {
    std::vector<std::unique_ptr<SearchIterator>> ab_search;
    ab_search.emplace_back(ArrayBoolSearch::create(*_bool_attr, _element_filter, _want_true, strict, _tmd.tfmd2));
    ab_search.emplace_back(ArrayBoolSearch::create(*_bool_attr, _element_filter, _want_true, strict, _tmd.tfmd3));
    std::vector<TermFieldMatchData*> children_tfmd;
    children_tfmd.push_back(_tmd.tfmd2);
    children_tfmd.push_back(_tmd.tfmd3);
    return std::make_unique<SameElementSearch>(*(_tmd.tfmd), children_tfmd, std::move(ab_search), strict, _element_filter);
}

/**
 * SameElementGenericSearchBuilder is a convenience class to get a SameElementSearch with a generic search iterator
 */
class SameElementGenericSearchBuilder : public SearchBuilder {
    std::unique_ptr<SearchContext> _ctx;
public:
    SameElementGenericSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~SameElementGenericSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

SameElementGenericSearchBuilder::SameElementGenericSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true),
      _ctx(_bool_attr->getSearch(std::make_unique<QueryTermSimple>(want_true ? "true" : "false", QueryTermSimple::Type::WORD), SearchContextParams())) {
}

SameElementGenericSearchBuilder::~SameElementGenericSearchBuilder() = default;

std::unique_ptr<SearchIterator> SameElementGenericSearchBuilder::create_search(bool strict) const {
    std::vector<std::unique_ptr<SearchIterator>> ctx_search;
    ctx_search.emplace_back(_ctx->createIterator(_tmd.tfmd2, strict));
    std::vector<TermFieldMatchData*> children_tfmd;
    children_tfmd.push_back(_tmd.tfmd2);
    return std::make_unique<SameElementSearch>(*(_tmd.tfmd), children_tfmd, std::move(ctx_search), strict, _element_filter);
}

/**
 * SameElementBlueprintSearchBuilder is a convenience class to get a SameElementSearch with whatever a SameElementBlueprint constructs
 * But first, we need an Attribute Manager
 */
class MyAttributeManager : public IAttributeManager {
    AttributeVector::SP _attribute_vector;

public:
    MyAttributeManager(MyAttributeManager && rhs) :
        IAttributeManager(),
        _attribute_vector(std::move(rhs._attribute_vector))
    {
    }

    MyAttributeManager(AttributeVector::SP attr)
        : _attribute_vector(std::move(attr))
    {
    }

    AttributeGuard::UP getAttribute(std::string_view) const override {
        return std::make_unique<AttributeGuard>(_attribute_vector);
    }

    std::unique_ptr<AttributeReadGuard>
    getAttributeReadGuard(std::string_view, bool stableEnumGuard) const override {
        if (_attribute_vector) {
            return _attribute_vector->makeReadGuard(stableEnumGuard);
        } else {
            return std::unique_ptr<AttributeReadGuard>();
        }
    }
    void getAttributeList(std::vector<AttributeGuard> &) const override {
        assert(!"Not implemented");
    }
    IAttributeContext::UP createContext() const override {
        assert(!"Not implemented");
        return IAttributeContext::UP();
    }

    void asyncForAttribute(std::string_view, std::unique_ptr<IAttributeFunctor>) const override {
        assert(!"Not implemented");
    }

    std::shared_ptr<ReadableAttributeVector> readable_attribute_vector(std::string_view) const override {
        return _attribute_vector;
    }
};

class SameElementBlueprintSearchBuilder : public SearchBuilder {
protected:
    MyAttributeManager                    _manager;
    AttributeContext                      _attribute_context;
    FakeRequestContext                    _request_context;
    AttributeBlueprintFactory             _factory;
    std::unique_ptr<SameElementBlueprint> _blueprint;

public:
    SameElementBlueprintSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true, bool expose_match_data_for_same_element = true);
    ~SameElementBlueprintSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

SameElementBlueprintSearchBuilder::SameElementBlueprintSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true, bool expose_match_data_for_same_element)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true),
        _manager(_attribute_vector),
        _attribute_context(_manager),
        _request_context(&_attribute_context) {
    search::query::SimpleStringTerm term(want_true ? "true" : "false", "bar", 1, search::query::Weight(0));
    std::unique_ptr<Blueprint> child_blueprint = _factory.createBlueprint(_request_context, _tmd.field_spec2, term, *_tmd.mdl);

    std::vector<TermFieldHandle> descendant_handles;
    descendant_handles.push_back(_tmd.handle2);

    _blueprint = std::make_unique<SameElementBlueprint>(_tmd.field_spec, descendant_handles, false, expose_match_data_for_same_element, element_filter);
    _blueprint->addChild(std::move(child_blueprint));
}

SameElementBlueprintSearchBuilder::~SameElementBlueprintSearchBuilder() = default;

std::unique_ptr<SearchIterator> SameElementBlueprintSearchBuilder::create_search(bool strict) const {
    search::queryeval::InFlow flow(strict);
    _blueprint->basic_plan(flow, _attribute_vector->getCommittedDocIdLimit());
    return _blueprint->createSearchImpl(*_tmd.md);
}

/**
 * SameElementBlueprintReplacementSearchBuilder is a convenience class to get whatever the replacement for SameElementBlueprint constructs
 */
class SameElementBlueprintReplacementSearchBuilder : public SameElementBlueprintSearchBuilder {
    std::unique_ptr<Blueprint> _replacement;

public:
    SameElementBlueprintReplacementSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true, bool expose_match_data_for_same_element = true);
    ~SameElementBlueprintReplacementSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

SameElementBlueprintReplacementSearchBuilder::SameElementBlueprintReplacementSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true, bool expose_match_data_for_same_element)
    : SameElementBlueprintSearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true, expose_match_data_for_same_element),
      _replacement(_blueprint->get_replacement()) {
    assert(_replacement);
}

SameElementBlueprintReplacementSearchBuilder::~SameElementBlueprintReplacementSearchBuilder() = default;

std::unique_ptr<SearchIterator> SameElementBlueprintReplacementSearchBuilder::create_search(bool strict) const {
    search::queryeval::InFlow flow(strict);
    _replacement->basic_plan(flow, _attribute_vector->getCommittedDocIdLimit());
    return _replacement->createSearch(*_tmd.md);
}

/**
 * UnexposingSameElementBlueprintReplacementSearchBuilder is a convenience class to get whatever the replacement for SameElementBlueprint constructs
 * when the SameElementBlueprint is instructed to not expose its match data
 */
class UnexposingSameElementBlueprintReplacementSearchBuilder : public SameElementBlueprintReplacementSearchBuilder {
public:
    UnexposingSameElementBlueprintReplacementSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~UnexposingSameElementBlueprintReplacementSearchBuilder() override;
    TermFieldMatchData* tfmd() const override;
};

UnexposingSameElementBlueprintReplacementSearchBuilder::UnexposingSameElementBlueprintReplacementSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SameElementBlueprintReplacementSearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true, false) { // Do not expose match data of SameElementBlueprint but of children
}

UnexposingSameElementBlueprintReplacementSearchBuilder::~UnexposingSameElementBlueprintReplacementSearchBuilder() = default;

TermFieldMatchData* UnexposingSameElementBlueprintReplacementSearchBuilder::tfmd() const {
    return _tmd.tfmd2; // This is the match data that should be exposed by replacement
}

/**
 * ArrayBoolBlueprintSearchBuilder is a convenience class to get whatever ArrayBoolBlueprint constructs
 */
class ArrayBoolBlueprintSearchBuilder : public SearchBuilder {
protected:
    std::unique_ptr<ArrayBoolBlueprint>   _blueprint;

public:
    ArrayBoolBlueprintSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~ArrayBoolBlueprintSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
    const ArrayBoolBlueprint& get_blueprint() const;
};

ArrayBoolBlueprintSearchBuilder::ArrayBoolBlueprintSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : SearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true) {
    _blueprint = std::make_unique<ArrayBoolBlueprint>(_tmd.field_spec, *_bool_attr, _element_filter, _want_true);
}

ArrayBoolBlueprintSearchBuilder::~ArrayBoolBlueprintSearchBuilder() = default;

const ArrayBoolBlueprint& ArrayBoolBlueprintSearchBuilder::get_blueprint() const {
    return *_blueprint;
}

std::unique_ptr<SearchIterator> ArrayBoolBlueprintSearchBuilder::create_search(bool strict) const {
    search::queryeval::InFlow flow(strict);
    _blueprint->basic_plan(flow, _attribute_vector->getCommittedDocIdLimit());
    return _blueprint->createSearchImpl(*_tmd.md);
}

/**
 * ArrayBoolBlueprintFilterSearchBuilder is a convenience class to get whatever filter iterator ArrayBoolBlueprint constructs
 */
class ArrayBoolBlueprintFilterSearchBuilder : public ArrayBoolBlueprintSearchBuilder {

public:
    ArrayBoolBlueprintFilterSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true);
    ~ArrayBoolBlueprintFilterSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(bool strict) const override;
};

ArrayBoolBlueprintFilterSearchBuilder::ArrayBoolBlueprintFilterSearchBuilder(ArrayBoolAttribute* bool_attr, std::shared_ptr<AttributeVector> attribute_vector, const std::vector<uint32_t>& element_filter, bool want_true)
    : ArrayBoolBlueprintSearchBuilder(bool_attr, std::move(attribute_vector), element_filter, want_true) {
}

ArrayBoolBlueprintFilterSearchBuilder::~ArrayBoolBlueprintFilterSearchBuilder() = default;

std::unique_ptr<SearchIterator> ArrayBoolBlueprintFilterSearchBuilder::create_search(bool strict) const {
    search::queryeval::InFlow flow(strict);
    _blueprint->basic_plan(flow, _attribute_vector->getCommittedDocIdLimit());
    // Should yield an exact iterator in this case, not just an upper bound
    return _blueprint->createFilterSearchImpl(Blueprint::FilterConstraint::UPPER_BOUND);
}

}

/***********************************************************************************************************************
 * Blueprint tests
 **********************************************************************************************************************/

TEST(ArrayBoolSearchTest, require_that_same_element_blueprint_creates_array_bool_search) {
    TestAttribute test_attribute;
    test_attribute.attr->addDocs(5);
    test_attribute.attr->commit();

    for (bool want_true : {false, true}) {
        for (bool strict : {false, true}) {
            std::vector<uint32_t> element_filter({1, 2, 3});
            SameElementBlueprintSearchBuilder builder(test_attribute.bool_attr, test_attribute.attr, element_filter, want_true);
            auto search = builder.create_search(strict);

            auto se = dynamic_cast<SameElementSearch*>(search.get());
            ASSERT_TRUE(se);
            const auto& children = se->children();
            ASSERT_EQ(children.size(), 1);
            auto only_child = children[0].get();
            auto abs = dynamic_cast<ArrayBoolSearch*>(only_child);
            ASSERT_TRUE(abs);
            EXPECT_EQ(abs->get_want_true(), want_true);
            EXPECT_TRUE(abs->is_strict() == (strict ? vespalib::Trinary::True : vespalib::Trinary::False));
            EXPECT_TRUE(std::equal(element_filter.begin(), element_filter.begin() + element_filter.size(), abs->get_element_filter().begin()));
            EXPECT_EQ(test_attribute.bool_attr, &abs->get_attribute());
        }
    }
}

TEST(ArrayBoolSearchTest, require_that_same_element_blueprint_replacement_creates_array_bool_search) {
    TestAttribute test_attribute;
    test_attribute.attr->addDocs(5);
    test_attribute.attr->commit();

    for (bool want_true : {false, true}) {
        for (bool strict : {false, true}) {
            std::vector<uint32_t> element_filter({1, 2, 3});
            SameElementBlueprintReplacementSearchBuilder builder(test_attribute.bool_attr, test_attribute.attr, element_filter, want_true);
            auto search = builder.create_search(strict);

            auto abs = dynamic_cast<ArrayBoolSearch*>(search.get());
            ASSERT_TRUE(abs);
            EXPECT_EQ(abs->get_want_true(), want_true);
            EXPECT_TRUE(abs->is_strict() == (strict ? vespalib::Trinary::True : vespalib::Trinary::False));
            EXPECT_TRUE(std::equal(element_filter.begin(), element_filter.begin() + element_filter.size(), abs->get_element_filter().begin()));
            EXPECT_EQ(test_attribute.bool_attr, &abs->get_attribute());
        }
    }
}

TEST(ArrayBoolSearchTest, require_that_blueprint_hit_estimate_yields_correct_number_of_documents) {
    TestAttribute test_attribute;
    test_attribute.attr->addDocs(5);
    test_attribute.attr->commit();
    {
        ArrayBoolBlueprintSearchBuilder builder(test_attribute.bool_attr, test_attribute.attr, {0}, true);
        auto hit_estimate = builder.get_blueprint().getState().estimate();
        EXPECT_EQ(hit_estimate.estHits, 5 + 1);
        EXPECT_EQ(hit_estimate.empty, false);
    }

    // Empty with reserved document id
    test_attribute.reset(true);
    {
        ArrayBoolBlueprintSearchBuilder builder(test_attribute.bool_attr, test_attribute.attr, {0}, true);
        auto hit_estimate = builder.get_blueprint().getState().estimate();
        EXPECT_EQ(hit_estimate.estHits, 0 + 1);
        EXPECT_EQ(hit_estimate.empty, false);
    }

    // Empty without reserved document id
    test_attribute.reset(false);
    {
        ArrayBoolBlueprintSearchBuilder builder(test_attribute.bool_attr, test_attribute.attr, {0}, true);
        auto hit_estimate = builder.get_blueprint().getState().estimate();
        EXPECT_EQ(hit_estimate.estHits, 0);
        EXPECT_EQ(hit_estimate.empty, true);
    }
}

/***********************************************************************************************************************
 * Setup for typed test
 **********************************************************************************************************************/

/**
 * Test fixture
 **/
template<typename B>
class ArrayBoolSearchTest : public ::testing::Test {
protected:
    TestAttribute _test_attribute;

    ArrayBoolSearchTest();
    ~ArrayBoolSearchTest() override;
    void add_docs();
};

template<typename B>
ArrayBoolSearchTest<B>::ArrayBoolSearchTest()
    : _test_attribute() {
    add_docs();
}

template<typename B>
ArrayBoolSearchTest<B>::~ArrayBoolSearchTest() = default;

template<typename B>
void ArrayBoolSearchTest<B>::add_docs() {
    _test_attribute.attr->addDocs(5);
    std::vector<int8_t> vals1 = {0, 1, 0};
    _test_attribute.bool_attr->set_bools(1, vals1);
    std::vector<int8_t> vals2 = {0, 0, 0};
    _test_attribute.bool_attr->set_bools(2, vals2);
    std::vector<int8_t> vals3 = {1};
    _test_attribute.bool_attr->set_bools(3, vals3);
    std::vector<int8_t> vals4 = {1, 1, 1};
    _test_attribute.bool_attr->set_bools(4, vals4);
    _test_attribute.attr->commit();
}

using Builders = ::testing::Types<ArrayBoolSearchBuilder,
                                  ArrayBoolBlueprintSearchBuilder,
                                  SameElementArrayBoolSearchBuilder,
                                  SameElementMultiArrayBoolSearchBuilder,
                                  SameElementGenericSearchBuilder,
                                  SameElementBlueprintSearchBuilder,
                                  SameElementBlueprintReplacementSearchBuilder,
                                  UnexposingSameElementBlueprintReplacementSearchBuilder>;
TYPED_TEST_SUITE(ArrayBoolSearchTest, Builders);

/***********************************************************************************************************************
 * Full iterator tests
 **********************************************************************************************************************/

TYPED_TEST(ArrayBoolSearchTest, require_that_non_strict_iterator_can_seek_and_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, true);
    auto search = builder.create_search(false);

    search->initRange(1, 1000);
    EXPECT_LT(search->getDocId(), 1u);

    // Doc 1: has true at element 1
    EXPECT_TRUE(search->seek(1u));
    EXPECT_EQ(search->getDocId(), 1u);
    search->unpack(1u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 1u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 2: all false, no match
    EXPECT_FALSE(search->seek(2u));

    // Doc 3: has true at element 0, no match
    EXPECT_FALSE(search->seek(3u));

    // Doc 4: has true at element 1 and 2
    EXPECT_TRUE(search->seek(4u));
    EXPECT_EQ(search->getDocId(), 4u);
    search->unpack(4u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 4u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 5: has no values
    EXPECT_FALSE(search->seek(5));

    EXPECT_FALSE(search->seek(6));
    EXPECT_FALSE(search->seek(1000));

    // A non-strict iterator must not necessarily update the docid if we do not have a match
    //EXPECT_TRUE(search->isAtEnd());
}

TYPED_TEST(ArrayBoolSearchTest, require_that_non_strict_iterator_can_seek_and_unpack_matching_docid_searching_for_false) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, false);
    auto search = builder.create_search(false);

    search->initRange(1, 1000);
    EXPECT_LT(search->getDocId(), 1u);

    // Doc 1: has false at element 2
    EXPECT_TRUE(search->seek(1u));
    EXPECT_EQ(search->getDocId(), 1u);
    search->unpack(1u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 1u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 2: all false
    EXPECT_TRUE(search->seek(2u));
    EXPECT_EQ(search->getDocId(), 2u);
    search->unpack(2u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 2u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 3: has no false, no match
    EXPECT_FALSE(search->seek(3u));

    // Doc 4: has no false, no match
    EXPECT_FALSE(search->seek(4u));

    // Doc 5: has no values
    EXPECT_FALSE(search->seek(5));

    EXPECT_FALSE(search->seek(6));
    EXPECT_FALSE(search->seek(1000));

    // A non-strict iterator must not necessarily update the docid if we do not have a match
    //EXPECT_TRUE(search->isAtEnd());
}

TYPED_TEST(ArrayBoolSearchTest, require_that_strict_iterator_seeks_to_next_hit_and_can_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, true);
    auto search = builder.create_search(true);

    search->initRange(1, 1000);
    EXPECT_LT(search->getDocId(), 1u);

    // Doc 1: has true at element 1
    EXPECT_TRUE(search->seek(1u));
    EXPECT_EQ(search->getDocId(), 1u);
    search->unpack(1u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 1u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 2: all false, no match
    // Doc 3: has true at element 0, no match
    // Doc 4: has true at element 1 and 2
    EXPECT_FALSE(search->seek(2u));
    EXPECT_EQ(search->getDocId(), 4u);
    search->unpack(4u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 4u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 5: has no values
    EXPECT_FALSE(search->seek(5));

    EXPECT_TRUE(search->isAtEnd());
}

TYPED_TEST(ArrayBoolSearchTest, require_that_strict_iterator_seeks_to_next_hit_and_can_unpack_matching_docid_searching_for_false) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, false);
    auto search = builder.create_search(true);

    search->initRange(1, 1000);
    EXPECT_LT(search->getDocId(), 1u);

    // Doc 1: has false at element 2
    EXPECT_TRUE(search->seek(1u));
    EXPECT_EQ(search->getDocId(), 1u);
    search->unpack(1u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 1u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 2: all false
    EXPECT_TRUE(search->seek(2u));
    EXPECT_EQ(search->getDocId(), 2u);
    search->unpack(2u);
    EXPECT_EQ(builder.tfmd()->getDocId(), 2u);
    EXPECT_EQ(builder.tfmd()->getWeight(), 1u);

    // Doc 3: has no false, no match
    // Doc 4: has no false, no match
    // Doc 5: has no values
    EXPECT_FALSE(search->seek(3u));

    EXPECT_TRUE(search->isAtEnd());
}

/***********************************************************************************************************************
 * Unpacking tests
 **********************************************************************************************************************/

void verify_unpacking(SearchIterator& search, uint32_t docid, uint32_t /*matches*/, TermFieldMatchData* tfmd) {
    EXPECT_TRUE(search.seek(docid));
    EXPECT_EQ(search.getDocId(), docid);
    search.unpack(docid);
    EXPECT_EQ(tfmd->getDocId(), docid);
    EXPECT_EQ(tfmd->getWeight(), 1); // Weight is not the number of matches for now
}

TYPED_TEST(ArrayBoolSearchTest, require_that_single_element_iterator_can_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, true);
        auto search = builder.create_search(strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 4u, 1u, builder.tfmd());
    }

    std::vector<uint32_t> element_filter2({2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter2, false);
        auto search = builder.create_search(strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 2u, 1u, builder.tfmd());
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_multi_element_iterator_can_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, true);
        auto search = builder.create_search(strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 4u, 2u, builder.tfmd());
    }

    std::vector<uint32_t> element_filter2({0, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter2, false);
        auto search = builder.create_search(strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_unpacking(*search, 1u, 2u, builder.tfmd());
        verify_unpacking(*search, 2u, 2u, builder.tfmd());
    }
}


/***********************************************************************************************************************
 * Element id tests
 * Check that get_element_ids() and and_element_ids_into() work correctly
 **********************************************************************************************************************/

std::vector<uint32_t> get_element_ids(SearchIterator& search, uint32_t docid) {
    std::vector<uint32_t> element_ids;
    // Special treatment of SameElementSearch as it does not implement get_element_ids
    if (auto se = dynamic_cast<SameElementSearch*>(&search)) {
        se->find_matching_elements(docid, element_ids);
    } else {
        search.get_element_ids(docid, element_ids);
    }
    return element_ids;
}

void and_element_ids_into(SearchIterator& search, uint32_t docid, std::vector<uint32_t>& element_ids) {
    // Special treatment of SameElementSearch as it does not implement and_element_ids_into
    if (auto se = dynamic_cast<SameElementSearch*>(&search)) {
        std::vector<uint32_t> temp;
        se->find_matching_elements(docid, temp);
        std::vector<uint32_t> intersection;
        std::set_intersection(element_ids.begin(), element_ids.end(), temp.begin(), temp.end(),
                              std::back_inserter(intersection));
        std::swap(intersection, element_ids);
    } else {
        search.and_element_ids_into(docid, element_ids);
    }
}

// Check that the elements obtained by get_element_ids and and and_element_ids_into are these in element_ids
void verify_element_ids_for_docid(SearchIterator& search, uint32_t docid, const std::vector<uint32_t>& expected_ids) {
    // Start with get_element_ids of iterator
    std::vector<uint32_t> element_ids = get_element_ids(search, docid);

    ASSERT_EQ(element_ids.size(), expected_ids.size());
    EXPECT_TRUE(std::equal(element_ids.begin(), element_ids.begin() + element_ids.size(), expected_ids.begin()));

    for (const auto& subset : all_subsets({0, 1, 2, 3, 4, 5})) {
        // Compute intersection of element_id and subset as reference
        std::vector<uint32_t> intersection;
        std::set_intersection(expected_ids.begin(), expected_ids.end(), subset.begin(), subset.end(),
                              std::back_inserter(intersection));

        // Use and_element_ids_into of search iterator
        element_ids.clear();
        element_ids.insert(element_ids.end(), subset.begin(), subset.end());
        and_element_ids_into(search, docid, element_ids);

        ASSERT_EQ(element_ids.size(), intersection.size());
        EXPECT_TRUE(std::equal(element_ids.begin(), element_ids.begin() + element_ids.size(), intersection.begin()));
    }
}

template<typename B>
void verify_element_ids(TestAttribute &test_attr, const std::vector<uint32_t>& element_filter, bool want_true, const std::vector<std::vector<uint32_t>>& element_ids) {
    for (bool strict : {false, true}) {
        B builder(test_attr.bool_attr, test_attr.attr, element_filter, want_true);
        auto search = builder.create_search(strict);

        search->initRange(1, test_attr.attr->getCommittedDocIdLimit());
        for (uint32_t docid = 1; docid <= element_ids.size(); ++docid) {
            verify_element_ids_for_docid(*search, docid, element_ids[docid-1]);
        }
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_single_element_iterator_can_get_element_ids) {
    // True at position 1 matches doc 1 and 4
    verify_element_ids<TypeParam>(this->_test_attribute, {1}, true, {{1}, {}, {}, {1}, {}, {}});
    // False at position 2 matches doc 1 and 2
    verify_element_ids<TypeParam>(this->_test_attribute, {2}, false, {{2}, {2}, {}, {}, {}, {}});
}

TYPED_TEST(ArrayBoolSearchTest, require_that_multi_element_iterator_can_get_element_ids) {
    // True at position 1 or 2 matches doc 1 and 4
    verify_element_ids<TypeParam>(this->_test_attribute, {1, 2}, true, {{1}, {}, {}, {1, 2}, {}, {}});
    // False at position 0 or 2 matches doc 1 and 2
    verify_element_ids<TypeParam>(this->_test_attribute, {0, 2}, false, {{0, 2}, {0, 2}, {}, {}, {}, {}});
}

/***********************************************************************************************************************
 * Comparison of ArrayBoolSearch to generic iterator obtained from search context
 **********************************************************************************************************************/

void verify_same_relaxed_iterator_behavior(SearchIterator& it1, TermFieldMatchData* tfmd1, SearchIterator& it2, TermFieldMatchData* tfmd2, uint32_t begin_id, uint32_t end_id) {
    it1.initRange(begin_id, end_id);
    it2.initRange(begin_id, end_id);

    for (uint32_t docid = begin_id; docid < end_id; ++docid) {
        bool match = it1.seek(docid);
        EXPECT_EQ(match, it2.seek(docid));
        if (match) {
            // Verify that unpacking yields same id and weight
            it1.unpack(docid);
            it2.unpack(docid);
            EXPECT_EQ(tfmd1->getDocId(), tfmd2->getDocId());
            EXPECT_EQ(tfmd1->getWeight(), tfmd2->getWeight());
        }
    }
    EXPECT_EQ(it1.isAtEnd(), it2.isAtEnd());
}

void verify_same_strict_iterator_behavior(SearchIterator& it1, TermFieldMatchData* tfmd1, SearchIterator& it2, TermFieldMatchData* tfmd2, uint32_t begin_id, uint32_t end_id) {
    it1.initRange(begin_id, end_id);
    it2.initRange(begin_id, end_id);

    uint32_t docid = it1.seekFirst(begin_id);
    EXPECT_EQ(docid, it2.seekFirst(begin_id));
    while (docid < end_id && !it1.isAtEnd()) {
        // Verify that unpacking yields same id and weight
        it1.unpack(docid);
        it2.unpack(docid);
        EXPECT_EQ(tfmd1->getDocId(), tfmd2->getDocId());
        EXPECT_EQ(tfmd1->getWeight(), tfmd2->getWeight());

        // Seek
        EXPECT_FALSE(it2.isAtEnd());
        uint32_t old_docid = docid;
        docid = it1.seekNext(old_docid + 1);
        EXPECT_EQ(docid, it2.seekNext(old_docid + 1));

    }
    EXPECT_EQ(it1.isAtEnd(), it2.isAtEnd());
}

void verify_same_iterator_behavior(SearchIterator& it1, TermFieldMatchData* tfmd1, SearchIterator& it2, TermFieldMatchData* tfmd2, uint32_t begin_id, uint32_t end_id, bool strict) {
    if (strict) {
        verify_same_strict_iterator_behavior(it1, tfmd1, it2, tfmd2, begin_id, end_id);
    } else {
        verify_same_relaxed_iterator_behavior(it1, tfmd1, it2, tfmd2, begin_id, end_id);
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_iterator_behaves_the_same_as_other_iterators) {
    for (const auto& element_filter : all_non_empty_subsets({0, 1, 2, 3, 4, 5})) {
        for (bool strict : {false, true}) {
            for (bool want_true: {false, true}) {
                ArrayBoolSearchBuilder builder(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, want_true);
                auto search1 = builder.create_search(strict);

                TypeParam builder2(this->_test_attribute.bool_attr, this->_test_attribute.attr, element_filter, want_true);
                auto search2 = builder2.create_search(strict);

                for (uint32_t begin_id : {0, 1, 2, 3, 4, 5}) {
                    verify_same_iterator_behavior(*search1, builder.tfmd(), *search2, builder2.tfmd(), begin_id, 1000, strict);
                }
            }
        }
    }
}

/***********************************************************************************************************************
 * Tests using SearchIteratorVerifier
 **********************************************************************************************************************/

/**
 * Verifier creates the test attribute
 **/
template<typename B>
class Verifier : public search::test::SearchIteratorVerifier {
    TestAttribute         _test_attribute;
    B                     _builder;

public:
    Verifier(size_t array_length, const std::vector<uint32_t>& element_filter, bool want_true);
    ~Verifier() override;
    SearchIterator::UP create(bool strict) const override;
};

template<typename B>
Verifier<B>::Verifier(size_t array_length, const std::vector<uint32_t>& element_filter, bool want_true)
    : _test_attribute(),
      _builder(_test_attribute.bool_attr, _test_attribute.attr, element_filter, want_true) {
    _test_attribute.attr->addDocs(getDocIdLimit());

    size_t element_filter_index = 0;
    auto expected_docids = getExpectedDocIds();
    size_t index = 0;
    for (uint32_t docid = 0; docid < getDocIdLimit(); docid++) {
        if (index < expected_docids.size() && expected_docids[index] == docid) {
            ++index;
            // This docid must match
            // Add an array for this docid that produces a match
            std::vector<int8_t> array;
            array.reserve(array_length);

            // Set one element of the array to want_true, the other to !want_true
            // This element is determined by element_filter_index
            uint32_t true_element = element_filter_index < element_filter.size()
                                    ? element_filter[element_filter_index]
                                    : 0;
            element_filter_index = (element_filter_index + 1) % element_filter.size();
            for (size_t i = 0; i < array_length; i++) {
                if (i == true_element) {
                    array.push_back(want_true ? 1 : 0);

                    // Cut off some of the arrays
                    if (docid % 2 == 0) {
                        break;
                    }
                } else {
                    array.push_back(want_true ? 0 : 1);
                }
            }
            _test_attribute.bool_attr->set_bools(docid, array);

        } else {
            // This docid must not match
            // Add an array that does not match for some of these docids
            if (docid % 2 == 0) {
                std::vector<int8_t> array;
                array.reserve(array_length);

                // Set all but the elements in element_filter to want_true
                size_t another_element_filter_index = 0;
                for (size_t i = 0; i < array_length; i++) {
                    if (another_element_filter_index < element_filter.size() && element_filter[another_element_filter_index] == i) {
                        ++another_element_filter_index;
                        array.push_back(want_true ? 0 : 1);

                        // Cut off some of the arrays
                        if (docid % 8 == 0) {
                            break;
                        }
                    } else {
                        array.push_back(want_true ? 1 : 0);
                    }
                }
                _test_attribute.bool_attr->set_bools(docid, array);
            }
        }
    }

    _test_attribute.attr->commit();
}

template<typename B>
Verifier<B>::~Verifier() = default;


template<typename B>
SearchIterator::UP Verifier<B>::create(bool strict) const {
    return _builder.create_search(strict);
}


/**
 * Test fixture that does absolutely nothing
 **/
template<typename B>
class VerifierTest : public ::testing::Test {
protected:
    VerifierTest();
    ~VerifierTest() override;
};

template<typename B>
VerifierTest<B>::VerifierTest() = default;

template<typename B>
VerifierTest<B>::~VerifierTest() = default;


// All builders, including the one producing a filter iterator, which does not support unpacking
using AllBuilders = ::testing::Types<ArrayBoolSearchBuilder,
                                     ArrayBoolBlueprintSearchBuilder,
                                     ArrayBoolBlueprintFilterSearchBuilder,
                                     SameElementArrayBoolSearchBuilder,
                                     SameElementMultiArrayBoolSearchBuilder,
                                     SameElementGenericSearchBuilder,
                                     SameElementBlueprintSearchBuilder,
                                     SameElementBlueprintReplacementSearchBuilder,
                                     UnexposingSameElementBlueprintReplacementSearchBuilder>;
TYPED_TEST_SUITE(VerifierTest, AllBuilders);

TYPED_TEST(VerifierTest, verify_iterator_multiple_elements) {
    for (const auto& element_filter: all_non_empty_subsets({0, 1, 2, 3, 4})) {
        for (bool want_true : {false, true}) {
            Verifier<TypeParam> verifier(5, element_filter, want_true);
            verifier.verify();
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
