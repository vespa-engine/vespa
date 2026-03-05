// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/array_bool_search.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>
#include <list>
#include <memory>

using search::AttributeFactory;
using search::AttributeVector;
using search::QueryTermSimple;
using search::attribute::ArrayBoolAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::queryeval::ArrayBoolSearch;
using search::queryeval::FieldSpec;
using search::queryeval::SameElementSearch;
using search::queryeval::SearchIterator;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;

namespace {
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
    TermFieldHandle                  handle;
    std::unique_ptr<MatchData>       md;
    TermFieldMatchData*              tfmd;

    TestMatchData();
    ~TestMatchData();
};

TestMatchData::TestMatchData()
    : mdl(std::make_unique<MatchDataLayout>()),
      field_spec("foo", 0, mdl->allocTermField(0)),
      handle(field_spec.getHandle()),
      md(mdl->createMatchData()),
      tfmd(md->resolveTermField(handle)) {
}

TestMatchData::~TestMatchData() = default;

/**
 * Base class for convenience classes to get iterators
 */
class SearchBuilder {
protected:
    ArrayBoolAttribute* _bool_attr;
    TestMatchData       _tmd;

public:
    SearchBuilder(ArrayBoolAttribute* bool_attr);
    virtual ~SearchBuilder();
    TermFieldMatchData* tfmd() const;
    virtual std::unique_ptr<SearchIterator> create_search(const std::vector<uint32_t>& element_filter, bool want_true, bool strict) const = 0;
};

SearchBuilder::SearchBuilder(ArrayBoolAttribute* bool_attr)
    : _bool_attr(bool_attr) {
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
    ArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr);
    ~ArrayBoolSearchBuilder() override;
    std::unique_ptr<SearchIterator> create_search(const std::vector<uint32_t>& element_filter, bool want_true, bool strict) const override;
};

ArrayBoolSearchBuilder::ArrayBoolSearchBuilder(ArrayBoolAttribute* bool_attr)
    : SearchBuilder(bool_attr) {
}

ArrayBoolSearchBuilder::~ArrayBoolSearchBuilder() = default;

std::unique_ptr<SearchIterator> ArrayBoolSearchBuilder::create_search(const std::vector<uint32_t>& element_filter, bool want_true, bool strict) const {
    return ArrayBoolSearch::create(*_bool_attr, element_filter, want_true, strict, _tmd.tfmd);
}

}

/**
 * Test fixture
 **/
template<typename B>
class ArrayBoolSearchTest : public ::testing::Test {
protected:
    TestAttribute _test_attribute;
    B             _builder;

    ArrayBoolSearchTest();
    ~ArrayBoolSearchTest() override;
    void add_docs();
};

template<typename B>
ArrayBoolSearchTest<B>::ArrayBoolSearchTest()
    : _test_attribute(),
      _builder(this->_test_attribute.bool_attr) {
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

// Only the ArrayBoolSearchBuilder for now
using Builders = ::testing::Types<ArrayBoolSearchBuilder>;
TYPED_TEST_SUITE(ArrayBoolSearchTest, Builders);

/***********************************************************************************************************************
 * Full iterator tests
 **********************************************************************************************************************/

TYPED_TEST(ArrayBoolSearchTest, require_that_non_strict_iterator_can_seek_and_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam& builder = this->_builder;
    auto search = builder.create_search(element_filter, true, false);

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

    // An iterator must not necessarily update the docid if we do not have a match
    //EXPECT_TRUE(search->isAtEnd());
}

TYPED_TEST(ArrayBoolSearchTest, require_that_non_strict_iterator_can_seek_and_unpack_matching_docid_searching_for_false) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam& builder = this->_builder;
    auto search = builder.create_search(element_filter, false, false);

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

    // An iterator must not necessarily update the docid if we do not have a match
    //EXPECT_TRUE(search->isAtEnd());
}

TYPED_TEST(ArrayBoolSearchTest, require_that_strict_iterator_seeks_to_next_hit_and_can_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    TypeParam& builder = this->_builder;
    auto search = builder.create_search(element_filter, true, true);

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
    TypeParam& builder = this->_builder;
    auto search = builder.create_search(element_filter, false, true);

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
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter, true, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 4u, 1u, builder.tfmd());
    }

    std::vector<uint32_t> element_filter2({2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter2, false, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 2u, 1u, builder.tfmd());
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_multi_element_iterator_can_unpack_matching_docid) {
    std::vector<uint32_t> element_filter({1, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter, true, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_unpacking(*search, 1u, 1u, builder.tfmd());
        verify_unpacking(*search, 4u, 2u, builder.tfmd());
    }

    std::vector<uint32_t> element_filter2({0, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter2, false, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_unpacking(*search, 1u, 2u, builder.tfmd());
        verify_unpacking(*search, 2u, 2u, builder.tfmd());
    }
}


/***********************************************************************************************************************
 * Element id tests
 **********************************************************************************************************************/

void verify_element_ids(SearchIterator& search, uint32_t docid, const std::vector<uint32_t>& element_ids) {
    std::vector<uint32_t> temp_element_ids;
    search.get_element_ids(docid, temp_element_ids);

    ASSERT_EQ(element_ids.size(), temp_element_ids.size());
    EXPECT_TRUE(std::equal(element_ids.begin(), element_ids.begin() + element_ids.size(), temp_element_ids.begin()));

    for (const auto& subset : all_subsets({0, 1, 2, 3, 4, 5})) {
        // Compute intersection of element_id and subset as reference
        std::vector<uint32_t> intersection;
        std::set_intersection(element_ids.begin(), element_ids.end(), subset.begin(), subset.end(),
                              std::back_inserter(intersection));

        // Let searcher compute the intersection
        temp_element_ids.clear();
        temp_element_ids.insert(temp_element_ids.end(), subset.begin(), subset.end());
        search.and_element_ids_into(docid, temp_element_ids);

        ASSERT_EQ(intersection.size(), temp_element_ids.size());
        EXPECT_TRUE(std::equal(intersection.begin(), intersection.begin() + intersection.size(), temp_element_ids.begin()));
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_single_element_iterator_can_get_element_ids) {
    std::vector<uint32_t> element_filter({1});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter, true, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_element_ids(*search, 1u, {1});
        verify_element_ids(*search, 2u, {});
        verify_element_ids(*search, 3u, {});
        verify_element_ids(*search, 4u, {1});
        verify_element_ids(*search, 5u, {});
        verify_element_ids(*search, 6u, {});
    }

    std::vector<uint32_t> element_filter2({2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter2, false, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_element_ids(*search, 1u, {2});
        verify_element_ids(*search, 2u, {2});
        verify_element_ids(*search, 3u, {});
        verify_element_ids(*search, 4u, {});
        verify_element_ids(*search, 5u, {});
        verify_element_ids(*search, 6u, {});
    }
}

TYPED_TEST(ArrayBoolSearchTest, require_that_multi_element_iterator_can_get_element_ids) {
    std::vector<uint32_t> element_filter({1, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter, true, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 4
        verify_element_ids(*search, 1u, {1});
        verify_element_ids(*search, 2u, {});
        verify_element_ids(*search, 3u, {});
        verify_element_ids(*search, 4u, {1, 2});
        verify_element_ids(*search, 5u, {});
        verify_element_ids(*search, 6u, {});
    }

    std::vector<uint32_t> element_filter2({0, 2});
    for (bool strict : {false, true}) {
        TypeParam builder(this->_test_attribute.bool_attr);
        auto search = builder.create_search(element_filter2, false, strict);

        search->initRange(1, 1000);
        // Matches doc 1 and 2
        verify_element_ids(*search, 1u, {0, 2});
        verify_element_ids(*search, 2u, {0, 2});
        verify_element_ids(*search, 3u, {});
        verify_element_ids(*search, 4u, {});
        verify_element_ids(*search, 5u, {});
        verify_element_ids(*search, 6u, {});
    }
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
    // An iterator must not necessarily update the docid if we do not have a match
    //EXPECT_EQ(it1.isAtEnd(), it2.isAtEnd());
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
                ArrayBoolSearchBuilder builder(this->_test_attribute.bool_attr);
                auto search1 = builder.create_search(element_filter, want_true, strict);

                TypeParam builder2(this->_test_attribute.bool_attr);
                auto search2 = builder2.create_search(element_filter, want_true, strict);

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
    std::vector<uint32_t> _element_filter;
    bool                  _want_true;

public:
    Verifier(size_t array_length, const std::vector<uint32_t>& element_filter, bool want_true);
    ~Verifier() override;
    SearchIterator::UP create(bool strict) const override;
};

template<typename B>
Verifier<B>::Verifier(size_t array_length, const std::vector<uint32_t>& element_filter, bool want_true)
    : _test_attribute(),
      _builder(_test_attribute.bool_attr),
      _element_filter(element_filter),
      _want_true(want_true) {
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
            uint32_t true_element = element_filter_index < _element_filter.size()
                                    ? _element_filter[element_filter_index]
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
    return _builder.create_search(_element_filter, _want_true, strict);
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


TYPED_TEST_SUITE(VerifierTest, Builders);

TYPED_TEST(VerifierTest, verify_iterator_multiple_elements) {
    for (const auto& element_filter: all_non_empty_subsets({0, 1, 2, 3, 4})) {
        for (bool want_true : {false, true}) {
            Verifier<TypeParam> verifier(5, element_filter, want_true);
            verifier.verify();
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
