// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/correlate_by_blueprint.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/mock_attribute_context.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/issue.h>

#include <algorithm>
#include <optional>

using namespace search;
using namespace search::fef;
using namespace search::queryeval;

namespace {

constexpr uint32_t docid_limit = 20;
using Rows = std::vector<std::pair<uint32_t, BindingRow>>;
using Matches = std::vector<std::pair<uint32_t, std::vector<uint32_t>>>;

using AttributeMatch = attribute::Config::Match;

struct Fixture {
    MatchDataLayout layout;
    uint32_t next_field = 0;
    std::vector<std::shared_ptr<AttributeVector>> attributes;
    attribute::test::MockAttributeContext attribute_context;
    FakeRequestContext request_context{&attribute_context};
    std::vector<TermFieldHandle> clause_handles;
    std::unique_ptr<CorrelateByBlueprint> blueprint;
    Blueprint::UP finalized;
    MatchData::UP match_data;

    explicit Fixture(uint32_t columns, std::optional<std::vector<std::string>> zone_domain = std::nullopt)
        : blueprint() {
        std::vector<std::optional<std::vector<std::string>>> domains(columns);
        if (zone_domain) domains.back() = std::move(zone_domain);
        blueprint = std::make_unique<CorrelateByBlueprint>(std::move(domains));
    }

    // String attributes match without case by default, like in a schema.
    const attribute::IAttributeVector* attribute(const std::string& name, const Rows& rows, bool string,
                                                attribute::BasicType::Type integer_type = attribute::BasicType::INT64,
                                                AttributeMatch match = AttributeMatch::UNCASED) {
        attribute::Config config(string ? attribute::BasicType::STRING : integer_type,
                                 attribute::CollectionType::ARRAY);
        config.set_match(match);
        auto attr = AttributeFactory::createAttribute(name, config);
        uint32_t doc = 0;
        do { attr->addDoc(doc); } while (doc + 1 < docid_limit);
        for (const auto& [docid, values] : rows) {
            for (const auto& value : values) {
                if (string) dynamic_cast<StringAttribute&>(*attr).append(docid, std::string(std::get<std::string_view>(value)), 1);
                else dynamic_cast<IntegerAttribute&>(*attr).append(docid, std::get<int64_t>(value), 1);
            }
        }
        attr->commit();
        auto* result = attr.get();
        attributes.push_back(std::move(attr));
        return result;
    }

    void clause(const std::string& field, const Matches& matches, std::vector<BoundAttribute> bindings,
                std::vector<ExactAttributeValues> exact_values = {}) {
        uint32_t id = next_field++;
        FieldSpec term_field(field + ".key", id, layout.allocTermField(id), false);
        FakeResult result;
        for (const auto& [doc, elements] : matches) {
            result.doc(doc);
            for (uint32_t element : elements) result.elem(element).pos(1);
        }
        uint32_t root_id = next_field++;
        FieldSpec root(field, root_id, layout.allocTermField(root_id), false);
        clause_handles.push_back(root.getHandle());
        auto same_element = std::make_unique<SameElementBlueprint>(root,
                std::vector<TermFieldHandle>{term_field.getHandle()}, false, true);
        same_element->addChild(std::make_unique<FakeBlueprint>(term_field, result));
        blueprint->add_clause(std::move(same_element), std::move(bindings), std::move(exact_values));
    }

    const attribute::IAttributeVector* bool_attribute(const std::string& name,
                                                     const std::vector<std::pair<uint32_t, std::vector<int8_t>>>& rows) {
        attribute::Config config(attribute::BasicType::BOOL, attribute::CollectionType::ARRAY);
        auto attr = AttributeFactory::createAttribute(name, config);
        uint32_t doc = 0;
        do { attr->addDoc(doc); } while (doc + 1 < docid_limit);
        auto& bools = dynamic_cast<attribute::ArrayBoolAttribute&>(*attr);
        for (const auto& [docid, values] : rows) bools.set_bools(docid, values);
        attr->commit();
        attribute_context.add(attr);
        auto* result = attr.get();
        attributes.push_back(std::move(attr));
        return result;
    }

    // A clause with an element filter and one bool term, which optimize would
    // normally replace with an ArrayBoolBlueprint.
    void bool_clause(const std::string& field, const std::string& subfield, std::vector<uint32_t> element_filter,
                     std::vector<BoundAttribute> bindings) {
        uint32_t id = next_field++;
        FieldSpec term_field(field + "." + subfield, id, layout.allocTermField(id), false);
        search::query::SimpleStringTerm term("true", term_field.getName(), id, search::query::Weight(0));
        AttributeBlueprintFactory factory;
        auto child = factory.createBlueprint(request_context, term_field, term, layout);
        uint32_t root_id = next_field++;
        FieldSpec root(field, root_id, layout.allocTermField(root_id), false);
        clause_handles.push_back(root.getHandle());
        auto same_element = std::make_unique<SameElementBlueprint>(root,
                std::vector<TermFieldHandle>{term_field.getHandle()}, false, true, std::move(element_filter));
        same_element->addChild(std::move(child));
        blueprint->add_clause(std::move(same_element), std::move(bindings));
    }

    // Element ids that the clause exposed to ranking on unpack, or nothing if it was not unpacked.
    std::optional<std::vector<uint32_t>> unpacked_elements(size_t clause, uint32_t docid) const {
        const auto* tfmd = match_data->resolveTermField(clause_handles[clause]);
        if (tfmd->getDocId() != docid) return std::nullopt;
        std::vector<uint32_t> elements;
        for (const auto& position : *tfmd) elements.push_back(position.getElementId());
        return elements;
    }

    SearchIterator::UP search(bool strict) {
        blueprint->setDocIdLimit(docid_limit);
        finalized = Blueprint::optimize_and_sort(std::move(blueprint), strict);
        finalized->fetchPostings(ExecuteInfo::FULL);
        finalized->freeze();
        match_data = layout.createMatchData();
        return finalized->createSearch(*match_data);
    }
};

// The correlated elements of every clause, sorted so the result does not depend on the clause order.
std::vector<std::vector<uint32_t>> correlated_elements(SearchIterator& search, uint32_t docid, size_t clauses) {
    auto& correlated = dynamic_cast<CorrelateBySearch&>(search);
    std::vector<std::vector<uint32_t>> result(clauses);
    for (size_t i = 0; i < clauses; ++i) correlated.find_correlated_elements(docid, i, result[i]);
    std::sort(result.begin(), result.end());
    return result;
}

} // namespace

TEST(CorrelateBySearchTest, strict_and_nonstrict_search_reject_split_units_and_split_zones) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(2, std::vector<std::string>{"A", "B"});
        auto* price_unit = f.attribute("price.unit", {{2, {"u1"sv}}, {3, {"u1"sv}}, {4, {"u1"sv}}, {5, {"u1"sv}}}, true);
        auto* size_unit = f.attribute("size.unit", {{2, {"u1"sv}}, {3, {"u2"sv}}, {4, {"u1"sv}}, {5, {"u1"sv}}}, true);
        auto* flat_unit = f.attribute("flat.unit", {{2, {"u1"sv}}, {3, {"u1"sv}}, {4, {"u1"sv}}, {5, {"u1"sv}}}, true);
        auto* price_zone = f.attribute("price.zone", {{2, {"B"sv}}, {3, {"A"sv}}, {4, {"A"sv}}, {5, {"Z"sv}}}, true);
        auto* size_zone = f.attribute("size.zone", {{2, {"B"sv}}, {3, {"A"sv}}, {4, {"B"sv}}, {5, {"Z"sv}}}, true);
        Matches matches{{2, {0}}, {3, {0}}, {4, {0}}, {5, {0}}};
        f.clause("price", matches, {{0, price_unit}, {1, price_zone}});
        f.clause("size", matches, {{0, size_unit}, {1, size_zone}});
        f.clause("flat", matches, {{0, flat_unit}});
        auto search = f.search(strict);
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
    }
}

TEST(CorrelateBySearchTest, repeated_fields_can_match_different_rows_with_the_same_unit) {
    for (bool strict : {false, true}) {
        Fixture f(1);
        auto* units = f.attribute("signals.unit", {{2, {int64_t(7), int64_t(7)}}, {3, {int64_t(7), int64_t(9)}}}, false);
        f.clause("signals", {{2, {0}}, {3, {0}}}, {{0, units}});
        f.clause("signals", {{2, {1}}, {3, {1}}}, {{0, units}});
        auto search = f.search(strict);
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
    }
}

TEST(CorrelateBySearchTest, missing_attribute_rows_do_not_bind_default_or_out_of_range_values) {
    using namespace std::literals;
    Fixture f(1);
    auto* a = f.attribute("a.unit", {{2, {""sv}}, {3, {"u"sv}}, {4, {"u"sv}}}, true);
    auto* b = f.attribute("b.unit", {{2, {""sv}}, {3, {}}, {4, {"u"sv}}}, true);
    f.clause("a", {{2, {0}}, {3, {0}}, {4, {1}}}, {{0, a}});
    f.clause("b", {{2, {0}}, {3, {0}}, {4, {0}}}, {{0, b}});
    auto search = f.search(true);
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult());
}

TEST(CorrelateBySearchTest, undefined_integer_bindings_are_rejected_for_every_signed_width) {
    using attribute::BasicType;
    const std::vector<std::pair<BasicType::Type, int64_t>> undefined_values{
        {BasicType::INT8, attribute::getUndefined<int8_t>()},
        {BasicType::INT16, attribute::getUndefined<int16_t>()},
        {BasicType::INT32, attribute::getUndefined<int32_t>()},
        {BasicType::INT64, attribute::getUndefined<int64_t>()}
    };
    for (const auto& [type, undefined] : undefined_values) {
        SCOPED_TRACE(static_cast<int>(type));
        for (bool strict : {false, true}) {
            Fixture f(1);
            Rows rows{{2, {undefined}}, {3, {undefined + 1}}, {4, {int64_t(0)}}, {5, {int64_t(7)}}};
            auto* a = f.attribute("a.unit", rows, false, type);
            auto* b = f.attribute("b.unit", rows, false, type);
            Matches matches{{2, {0}}, {3, {0}}, {4, {0}}, {5, {0}}};
            f.clause("a", matches, {{0, a}});
            f.clause("b", matches, {{0, b}});
            auto search = f.search(strict);
            EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({3, 4, 5}));
        }
    }
}

TEST(CorrelateBySearchTest, collected_bindings_are_distinct_and_clear_when_a_clause_does_not_match) {
    using namespace std::literals;
    Fixture f(2);
    auto* unit = f.attribute("zoned.unit", {{2, {int64_t(7), int64_t(7), int64_t(9)}}, {3, {int64_t(7)}}}, false);
    auto* zone = f.attribute("zoned.zone", {{2, {"A"sv, "A"sv, "B"sv}}, {3, {"A"sv}}}, true);
    auto* flat = f.attribute("flat.unit", {{2, {int64_t(7), int64_t(9)}}, {3, {int64_t(7)}}}, false);
    f.clause("zoned", {{2, {0, 1, 2}}, {3, {0}}}, {{0, unit}, {1, zone}});
    f.clause("flat", {{2, {0, 1}}}, {{0, flat}});
    auto search = f.search(false);
    auto* correlated = dynamic_cast<CorrelateBySearch*>(search.get());
    ASSERT_NE(correlated, nullptr);
    correlated->initRange(1, docid_limit);
    // The zone attribute matches without case, so its values are reported in lowercase.
    EXPECT_EQ(correlated->find_bindings(2), (std::vector<BindingRow>{{int64_t(7), "a"sv}, {int64_t(9), "b"sv}}));
    std::vector<uint32_t> projection{0};
    EXPECT_EQ(correlated->find_bindings(2, projection), (std::vector<BindingRow>{{int64_t(7)}, {int64_t(9)}}));
    EXPECT_TRUE(correlated->find_bindings(3).empty());
    correlated->initRange(1, docid_limit);
    EXPECT_EQ(correlated->find_bindings(2).size(), 2u);
}

TEST(CorrelateBySearchTest, exact_string_filters_on_cased_attributes_reject_case_variants_and_partial_labels) {
    using namespace std::literals;
    using attribute::BasicType;
    for (bool strict : {false, true}) {
        Fixture f(1);
        auto* units = f.attribute("signals.unit", {{2, {int64_t(7)}}, {3, {int64_t(7)}}, {4, {int64_t(7)}}, {5, {int64_t(7)}}}, false);
        auto* keys = f.attribute("signals.key", {{2, {"Eco Friendly"sv}}, {3, {"eco friendly"sv}}, {4, {"Eco Friendly"sv}},
                {5, {"Eco Friendly Extra"sv}}}, true, BasicType::INT64, AttributeMatch::CASED);
        auto* values = f.attribute("signals.value", {{2, {"True"sv}}, {3, {"True"sv}}, {4, {"true"sv}}, {5, {"True"sv}}},
                true, BasicType::INT64, AttributeMatch::CASED);
        Matches candidates{{2, {0}}, {3, {0}}, {4, {0}}, {5, {0}}};
        f.clause("signals", candidates, {{0, units}}, {{keys, {"Eco Friendly"}}, {values, {"True"}}});
        f.clause("other", candidates, {{0, units}});
        auto search = f.search(strict);
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
    }
}

TEST(CorrelateBySearchTest, exact_namespace_filters_accept_bare_rows_without_binding_empty_values) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(1);
        auto* units = f.attribute("signals.unit", {{2, {"u1"sv}}, {3, {"u1"sv}}, {4, {"u1"sv}},
                                                   {5, {"u1"sv}}, {6, {""sv}}, {7, {"u1"sv}}, {8, {"u1"sv, "u2"sv}}}, true);
        auto* namespaces = f.attribute("signals.namespace", {{2, {""sv}}, {3, {"apparel"sv}},
                {4, {"Apparel"sv}}, {5, {"delivery"sv}}, {6, {"apparel"sv}}, {8, {"delivery"sv}}}, true,
                attribute::BasicType::INT64, AttributeMatch::CASED);
        f.clause("signals", {{2, {0}}, {3, {0}}, {4, {0}}, {5, {0}}, {6, {0}}, {7, {0}}, {8, {1}}}, {{0, units}},
                 {{namespaces, {"", "apparel"}}});
        auto search = f.search(strict);
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2, 3, 7, 8}));
    }
}

TEST(CorrelateBySearchTest, fallback_uses_exact_key_presence_before_value_filtering_per_unit) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(2);
        auto* units = f.attribute("price.unit", {{2, {int64_t(7), int64_t(7)}}, {3, {int64_t(7), int64_t(7)}},
                {4, {int64_t(9), int64_t(7)}}, {5, {int64_t(7), int64_t(7)}}, {6, {int64_t(7), int64_t(7)}}}, false);
        auto* zones = f.attribute("price.zone", {{2, {"A"sv, "B"sv}}, {3, {"A"sv, "B"sv}}, {4, {"A"sv, "B"sv}},
                {5, {"A"sv, "B"sv}}, {6, {"Z"sv, "B"sv}}}, true);
        auto* keys = f.attribute("price.key", {{2, {"price"sv, "price"sv}}, {3, {"Price"sv, "price"sv}},
                {4, {"price"sv, "price"sv}}, {5, {"price"sv, "price"sv}}, {6, {"price"sv, "price"sv}}}, true,
                attribute::BasicType::INT64, AttributeMatch::CASED);
        auto* flat = f.attribute("flat.unit", {{2, {int64_t(7)}}, {3, {int64_t(7)}}, {4, {int64_t(7)}},
                {5, {int64_t(7)}}, {6, {int64_t(7)}}}, false);
        f.clause("price", {{2, {1}}, {3, {1}}, {4, {1}}, {5, {0, 1}}, {6, {1}}}, {{0, units}, {1, zones}}, {{keys, {"price"}}});
        f.clause("price", {{2, {0, 1}}, {3, {0, 1}}, {4, {0, 1}}, {5, {0, 1}}, {6, {0, 1}}}, {{0, units}, {1, zones}}, {{keys, {"price"}}});
        f.clause("flat", {{2, {0}}, {3, {0}}, {4, {0}}, {5, {0}}, {6, {0}}}, {{0, flat}});
        f.blueprint->add_first_present(0, 1, 1, {"A", "B"});
        auto search = f.search(strict);
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({3, 4, 5, 6}));
        auto* correlated = dynamic_cast<CorrelateBySearch*>(search.get());
        ASSERT_NE(nullptr, correlated);
        correlated->initRange(5, 6);
        EXPECT_EQ(correlated->find_bindings(5), (std::vector<BindingRow>{{int64_t(7), "a"sv}}));
    }
}

TEST(CorrelateBySearchTest, a_domain_does_not_change_which_present_zone_is_selected) {
    using namespace std::literals;
    Fixture f(2, std::vector<std::string>{"B"});
    auto* units = f.attribute("price.unit", {{2, {int64_t(7), int64_t(7)}}}, false);
    auto* zones = f.attribute("price.zone", {{2, {"A"sv, "B"sv}}}, true);
    f.clause("price", {{2, {1}}}, {{0, units}, {1, zones}});
    f.clause("price", {{2, {0, 1}}}, {{0, units}, {1, zones}});
    f.clause("flat", {{2, {0}}}, {{0, units}});
    f.blueprint->add_first_present(0, 1, 1, {"A", "B"});
    auto search = f.search(false);
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult());
}

TEST(CorrelateBySearchTest, uncased_attributes_compare_values_and_literals_without_case) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(2, std::vector<std::string>{"a", "B"});
        auto* price_unit = f.attribute("price.unit", {{2, {"U1"sv}}, {3, {"U1"sv}}, {4, {"U1"sv}}}, true);
        auto* price_zone = f.attribute("price.zone", {{2, {"A"sv}}, {3, {"b"sv}}, {4, {"C"sv}}}, true);
        auto* price_key = f.attribute("price.key", {{2, {"Price"sv}}, {3, {"PRICE"sv}}, {4, {"price"sv}}}, true);
        auto* flat_unit = f.attribute("flat.unit", {{2, {"u1"sv}}, {3, {"u1"sv}}, {4, {"u1"sv}}}, true);
        Matches matches{{2, {0}}, {3, {0}}, {4, {0}}};
        f.clause("price", matches, {{0, price_unit}, {1, price_zone}}, {{price_key, {"pRiCe"}}});
        f.clause("flat", matches, {{0, flat_unit}});
        auto search = f.search(strict);
        // Doc 4 has zone C, which is outside the domain.
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2, 3}));
        auto* correlated = dynamic_cast<CorrelateBySearch*>(search.get());
        ASSERT_NE(nullptr, correlated);
        EXPECT_EQ((std::vector<bool>{true, true}), correlated->uncased_columns());
        correlated->initRange(1, docid_limit);
        // Values of uncased variables are reported in lowercase.
        EXPECT_EQ(correlated->find_bindings(2), (std::vector<BindingRow>{{"u1"sv, "a"sv}}));
    }
}

TEST(CorrelateBySearchTest, first_present_priority_on_an_uncased_attribute_ignores_case) {
    using namespace std::literals;
    Fixture f(2);
    auto* units = f.attribute("price.unit", {{2, {int64_t(7), int64_t(7)}}, {3, {int64_t(7), int64_t(7)}}}, false);
    auto* zones = f.attribute("price.zone", {{2, {"A"sv, "B"sv}}, {3, {"A"sv, "B"sv}}}, true);
    auto* flat = f.attribute("flat.unit", {{2, {int64_t(7)}}, {3, {int64_t(7)}}}, false);
    // Doc 2 offers a price in zone A, doc 3 only in zone B, and both have zones A and B present.
    f.clause("price", {{2, {0}}, {3, {1}}}, {{0, units}, {1, zones}});
    f.clause("price", {{2, {0, 1}}, {3, {0, 1}}}, {{0, units}, {1, zones}});
    f.clause("flat", {{2, {0}}, {3, {0}}}, {{0, flat}});
    f.blueprint->add_first_present(0, 1, 1, {"a", "b"});
    auto search = f.search(false);
    // Zone A is present in both documents, so only a price in zone A counts.
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
}

TEST(CorrelateBySearchTest, a_variable_with_one_uncased_attribute_compares_all_its_values_without_case) {
    using namespace std::literals;
    Fixture f(1);
    auto* cased = f.attribute("a.unit", {{2, {"U1"sv}}}, true, attribute::BasicType::INT64, AttributeMatch::CASED);
    auto* uncased = f.attribute("b.unit", {{2, {"u1"sv}}}, true);
    f.clause("a", {{2, {0}}}, {{0, cased}});
    f.clause("b", {{2, {0}}}, {{0, uncased}});
    auto search = f.search(true);
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
}

TEST(CorrelateBySearchTest, cased_attributes_keep_case_in_joins_and_domains) {
    using namespace std::literals;
    using attribute::BasicType;
    Fixture f(2, std::vector<std::string>{"a"});
    auto* units = f.attribute("a.unit", {{2, {"U1"sv}}, {3, {"u1"sv}}}, true, BasicType::INT64, AttributeMatch::CASED);
    auto* zones = f.attribute("a.zone", {{2, {"a"sv}}, {3, {"A"sv}}}, true, BasicType::INT64, AttributeMatch::CASED);
    auto* other = f.attribute("b.unit", {{2, {"u1"sv}}, {3, {"u1"sv}}}, true, BasicType::INT64, AttributeMatch::CASED);
    f.clause("a", {{2, {0}}, {3, {0}}}, {{0, units}, {1, zones}});
    f.clause("b", {{2, {0}}, {3, {0}}}, {{0, other}});
    auto search = f.search(true);
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult());
}

TEST(CorrelateBySearchTest, unpack_exposes_only_elements_in_a_correlated_match) {
    using namespace std::literals;
    // Elements: 0 = {u1, A, price}, 1 = {u2, C, price}, 2 = {u1, eco}, 3 = {u2, A, price}.
    Fixture f(2, std::vector<std::string>{"A", "B"});
    auto* units = f.attribute("offers.unit", {{2, {"u1"sv, "u2"sv, "u1"sv, "u2"sv}}}, true);
    auto* zones = f.attribute("offers.zone", {{2, {"A"sv, "C"sv, ""sv, "A"sv}}}, true);
    f.clause("offers", {{2, {0, 1, 3}}}, {{0, units}, {1, zones}});
    f.clause("offers", {{2, {2}}}, {{0, units}});
    auto search = f.search(true);
    search->initRange(1, docid_limit);
    ASSERT_TRUE(search->seek(2));
    search->unpack(2);
    // Element 1 is outside the zone domain, and unit u2 of element 3 has no eco element.
    EXPECT_EQ(f.unpacked_elements(0, 2), (std::vector<uint32_t>{0}));
    EXPECT_EQ(f.unpacked_elements(1, 2), (std::vector<uint32_t>{2}));
    EXPECT_EQ(correlated_elements(*search, 2, 2), (std::vector<std::vector<uint32_t>>{{0}, {2}}));
}

TEST(CorrelateBySearchTest, unpack_hides_presence_clauses_and_zones_that_first_present_skipped) {
    using namespace std::literals;
    Fixture f(2);
    auto* units = f.attribute("price.unit", {{2, {int64_t(7), int64_t(7)}}}, false);
    auto* zones = f.attribute("price.zone", {{2, {"A"sv, "B"sv}}}, true);
    auto* flat = f.attribute("flat.unit", {{2, {int64_t(7)}}}, false);
    f.clause("price", {{2, {0, 1}}}, {{0, units}, {1, zones}});
    f.clause("price", {{2, {0, 1}}}, {{0, units}, {1, zones}});
    f.clause("flat", {{2, {0}}}, {{0, flat}});
    f.blueprint->add_first_present(0, 1, 1, {"A", "B"});
    auto search = f.search(true);
    search->initRange(1, docid_limit);
    ASSERT_TRUE(search->seek(2));
    search->unpack(2);
    // Zone A is present, so the price in zone B is not part of the match.
    EXPECT_EQ(f.unpacked_elements(0, 2), (std::vector<uint32_t>{0}));
    EXPECT_FALSE(f.unpacked_elements(1, 2).has_value());
    EXPECT_EQ(f.unpacked_elements(2, 2), (std::vector<uint32_t>{0}));
    // The presence clause reports no elements.
    EXPECT_EQ(correlated_elements(*search, 2, 3), (std::vector<std::vector<uint32_t>>{{}, {0}, {0}}));
}

TEST(CorrelateBySearchTest, sorting_clauses_keeps_each_clause_with_its_bindings) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(2);
        Rows units_rows, zones_rows;
        Matches many;
        for (uint32_t doc = 1; doc < docid_limit; ++doc) {
            units_rows.push_back({doc, {int64_t(7)}});
            zones_rows.push_back({doc, {"A"sv}});
            many.push_back({doc, {0}});
        }
        auto* units = f.attribute("price.unit", units_rows, false);
        auto* zones = f.attribute("price.zone", zones_rows, true);
        auto* flat = f.attribute("flat.unit", {{3, {int64_t(7)}}, {5, {int64_t(9)}}}, false);
        f.clause("price", many, {{0, units}, {1, zones}});
        f.clause("flat", {{3, {0}}, {5, {0}}}, {{0, flat}});
        auto search = f.search(strict);
        // The clause with fewer hits is moved first.
        auto& sorted = *f.finalized->asIntermediate();
        ASSERT_EQ(2u, sorted.childCnt());
        EXPECT_EQ("flat", dynamic_cast<const SameElementBlueprint&>(sorted.getChild(0)).field_name());
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({3}));
        auto* correlated = dynamic_cast<CorrelateBySearch*>(search.get());
        ASSERT_NE(nullptr, correlated);
        correlated->initRange(1, docid_limit);
        EXPECT_EQ(correlated->find_bindings(3), (std::vector<BindingRow>{{int64_t(7), "a"sv}}));
    }
}

TEST(CorrelateBySearchTest, a_clause_with_an_element_filter_on_a_bool_array_is_kept_by_optimize) {
    using namespace std::literals;
    for (bool strict : {false, true}) {
        Fixture f(1);
        auto* units = f.attribute("offers.unit", {{2, {"u1"sv, "u2"sv}}, {3, {"u1"sv, "u2"sv}}, {4, {"u3"sv}}}, true);
        f.bool_attribute("offers.in_stock", {{2, {1, 0}}, {3, {0, 1}}, {4, {1}}});
        auto* flat = f.attribute("flat.unit", {{2, {"u1"sv}}, {3, {"u2"sv}}, {4, {"u1"sv}}}, true);
        // Only element 0 of offers is considered.
        f.bool_clause("offers", "in_stock", {0}, {{0, units}});
        f.clause("flat", {{2, {0}}, {3, {0}}, {4, {0}}}, {{0, flat}});
        auto search = f.search(strict);
        auto& optimized = *f.finalized->asIntermediate();
        for (size_t i = 0; i < optimized.childCnt(); ++i) {
            EXPECT_NE(nullptr, dynamic_cast<const SameElementBlueprint*>(&optimized.getChild(i)));
        }
        EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult({2}));
    }
}

TEST(CorrelateBySearchTest, a_clause_that_is_not_same_element_reports_an_issue_and_matches_nothing) {
    using namespace std::literals;
    Fixture f(1);
    auto* units = f.attribute("flat.unit", {{2, {"u1"sv}}}, true);
    f.clause("flat", {{2, {0}}}, {{0, units}});
    uint32_t id = f.next_field++;
    FieldSpec field("plain", id, f.layout.allocTermField(id), false);
    f.blueprint->add_clause(std::make_unique<FakeBlueprint>(field, FakeResult().doc(2)), {{0, units}});
    search::UniqueIssues issues;
    auto capture = vespalib::Issue::listen(issues);
    auto search = f.search(true);
    EXPECT_EQ(SimpleResult().search(*search, docid_limit), SimpleResult());
    EXPECT_EQ(1u, issues.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
