// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/i_document_weight_attribute.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::queryeval::MatchingElementsSearch;
using search::AttributeFactory;
using search::AttributeVector;
using search::IDocumentWeightAttribute;
using search::IntegerAttribute;
using search::MatchingElements;
using search::StringAttribute;

std::shared_ptr<AttributeVector> make_attribute(BasicType type) {
    Config cfg(type, CollectionType::WSET);
    cfg.setFastSearch(true);
    auto result = AttributeFactory::createAttribute("field", cfg);
    uint32_t docid = 0;
    for (size_t i = 0; i < 2; ++i) {
        result->addDoc(docid);
    }
    result->commit();
    return result;
}

std::unique_ptr<MatchingElementsSearch> make_search(AttributeVector &attr, const std::vector<vespalib::string> &terms)
{
    using LookupResult = IDocumentWeightAttribute::LookupResult;
    auto dwa = attr.asDocumentWeightAttribute();
    assert(dwa != nullptr);
    auto snapshot = dwa->get_dictionary_snapshot();
    std::vector<LookupResult> dict_entries;
    for (const auto &term : terms) {
        dict_entries.emplace_back(dwa->lookup(term, snapshot));
    }
    auto result = MatchingElementsSearch::create(attr, snapshot, dict_entries);
    result->initRange(1, attr.getCommittedDocIdLimit());
    return result;
}

template <typename KeyType>
class MatchingElementsSearchTest : public ::testing::Test {
public:
    static constexpr bool is_string = std::is_same_v<KeyType, const char *>;
    using Values = std::vector<std::pair<KeyType, int32_t>>;
    using MatchResult = std::map<std::conditional_t<is_string, vespalib::string, KeyType>, int32_t>;
    using LookupTest = std::pair<std::vector<vespalib::string>, MatchResult>;
    using LookupTests = std::vector<LookupTest>;
    using AttributeSubType = std::conditional_t<is_string, StringAttribute, IntegerAttribute>;
    static Values _values;
    static LookupTests _lookup_tests;
    std::shared_ptr<AttributeVector> _attr;
    std::conditional_t<is_string, search::attribute::WeightedStringContent, search::attribute::WeightedIntegerContent> _content;

    MatchingElementsSearchTest()
        : _attr(make_attribute(std::is_same_v<KeyType, int64_t> ? BasicType::INT64 : BasicType::STRING))
    {
        auto &attr = dynamic_cast<AttributeSubType &>(*_attr);
        uint32_t docid = 1;
        attr.clearDoc(docid);
        for (const auto &value : _values) {
            attr.append(docid, value.first, value.second);
        }
        attr.commit();
    }

    MatchResult
    get_matches(MatchingElementsSearch &matching_elements_search) {
        MatchingElements matching_elements_store;
        uint32_t docid = 1;
        matching_elements_search.find_matching_elements(docid, matching_elements_store);
        auto matching_elements = matching_elements_store.get_matching_elements(docid, "field");
        _content.fill(*_attr, docid);
        MatchResult result;
        for (auto &element_id : matching_elements) {
            if (element_id < _content.size()) {
                auto &element = _content[element_id];
                result.emplace(element.value(), element.weight());
            }
        }
        return result;
    }

    void verify_matching_elements() {
        for (const auto &lookup_test : _lookup_tests) {
            auto search = make_search(*_attr, lookup_test.first);
            auto matches = get_matches(*search);
            EXPECT_EQ(lookup_test.second, matches);
        }
    }
};

template <> MatchingElementsSearchTest<int64_t>::Values MatchingElementsSearchTest<int64_t>::_values{{10, 5}, {20, 7}};
template <> MatchingElementsSearchTest<const char *>::Values MatchingElementsSearchTest<const char *>::_values{{"FOO", 3}, {"bar", 7}, {"foo", 5}};
template <> MatchingElementsSearchTest<int64_t>::LookupTests MatchingElementsSearchTest<int64_t>::_lookup_tests{
    {{"10", "11"}, {{10, 5}}},
    {{"11", "20"}, {{20, 7}}},
    {{"10", "20"}, {{10, 5}, {20, 7}}}
};
template <> MatchingElementsSearchTest<const char *>::LookupTests MatchingElementsSearchTest<const char *>::_lookup_tests{
    {{"foo", "baz"}, {{"FOO", 3}, {"foo", 5}}},
    {{"baz", "bar"}, {{"bar", 7}}},
    {{"foo", "bar"}, {{"FOO", 3}, {"foo", 5}, {"bar", 7}}},
    {{"FOO"},        {{"FOO", 3}, {"foo", 5}}}
};

// Disable warnings emitted by gtest generated files when using typed tests     
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

using MatchingElementsSearchTestTypes = ::testing::Types<int64_t, const char *>;
VESPA_GTEST_TYPED_TEST_SUITE(MatchingElementsSearchTest, MatchingElementsSearchTestTypes);

TYPED_TEST(MatchingElementsSearchTest, verify_matching_elements)
{
    this->verify_matching_elements();
}

#pragma GCC diagnostic pop

GTEST_MAIN_RUN_ALL_TESTS()
