// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/countaggregationresult.h>
#include <vespa/searchlib/expression/and_predicate_node.h>
#include <vespa/searchlib/expression/not_predicate_node.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/constantnode.h>
#include <vespa/searchlib/expression/correlated_predicate_node.h>
#include <vespa/searchlib/expression/once_per_document_predicate_node.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <map>

using namespace search;
using namespace search::expression;

namespace {

struct Rows {
    std::shared_ptr<AttributeVector> units;
    std::shared_ptr<AttributeVector> zones;
    CurrentIndex index;

    ~Rows();

    Rows()
        : units(AttributeFactory::createAttribute("signals.unit", attribute::Config(attribute::BasicType::INT64, attribute::CollectionType::ARRAY))),
          zones(AttributeFactory::createAttribute("signals.zone", attribute::Config(attribute::BasicType::STRING, attribute::CollectionType::ARRAY))) {
        units->addReservedDoc();
        zones->addReservedDoc();
        for (uint32_t expected = 1; expected <= 2; ++expected) {
            uint32_t docid = 0;
            EXPECT_TRUE(units->addDoc(docid));
            EXPECT_EQ(expected, docid);
            EXPECT_TRUE(zones->addDoc(docid));
            for (auto unit : {7, 7, 7, 8}) dynamic_cast<IntegerAttribute&>(*units).append(docid, unit, 1);
            for (auto zone : {"A", "A", "a", "B"}) dynamic_cast<StringAttribute&>(*zones).append(docid, zone, 1);
        }
        units->commit();
        zones->commit();
    }

    std::vector<ExpressionNode::UP> fields(bool bound = true) {
        std::vector<ExpressionNode::UP> result;
        for (auto* attribute : {units.get(), zones.get()}) {
            auto field = std::make_unique<AttributeNode>(*attribute);
            if (bound) field->setCurrentIndex(&index);
            field->prepare(true);
            result.push_back(std::move(field));
        }
        return result;
    }
};

Rows::~Rows() = default;

struct Issues : vespalib::Issue::Handler {
    std::vector<std::string> list;
    vespalib::Issue::Binding capture;
    Issues() : list(), capture(vespalib::Issue::listen(*this)) {}
    ~Issues() override;
    void handle(const vespalib::Issue& issue) override { list.push_back(issue.message()); }
};

Issues::~Issues() = default;

TEST(CorrelatedGroupingPredicateTest, passes_exact_typed_row_bindings_to_provider) {
    Rows rows;
    CorrelatedPredicateNode filter("selection", {"unit", "zone"}, rows.fields());
    uint32_t calls = 0;
    filter.set_matcher([&](uint32_t docid, std::span<const CorrelationBinding> bindings) {
        ++calls;
        EXPECT_EQ(1u, docid);
        EXPECT_EQ("unit", bindings[0].first);
        EXPECT_EQ("zone", bindings[1].first);
        return bindings[0].second == CorrelationValue(int64_t(7)) && bindings[1].second == CorrelationValue(std::string_view("A"));
    });
    EXPECT_TRUE(filter.allow(1, 0));
    rows.index.set(2);
    EXPECT_FALSE(filter.allow(1, 0));
    rows.index.set(3);
    EXPECT_FALSE(filter.allow(1, 0));
    EXPECT_EQ(3u, calls);
}

TEST(CorrelatedGroupingPredicateTest, rejects_rows_and_reports_once_without_a_provider) {
    Rows rows;
    Issues issues;
    CorrelatedPredicateNode filter("selection", {"unit", "zone"}, rows.fields());
    EXPECT_FALSE(filter.allow(1, 0));
    EXPECT_FALSE(filter.allow(2, 0));
    ASSERT_EQ(1u, issues.list.size());
    EXPECT_NE(std::string::npos, issues.list[0].find("selection"));
}

TEST(CorrelatedGroupingPredicateTest, rejects_rows_and_reports_once_for_attributes_not_bound_to_a_row) {
    Rows rows;
    Issues issues;
    CorrelatedPredicateNode unbound("selection", {"unit", "zone"}, rows.fields(false));
    unbound.set_matcher([](uint32_t, auto) noexcept { return true; });
    EXPECT_FALSE(unbound.allow(1, 0));
    EXPECT_FALSE(unbound.allow(2, 0));
    EXPECT_EQ(1u, issues.list.size());
}

TEST(CorrelatedGroupingPredicateTest, rejects_rows_for_streaming_search) {
    Issues issues;
    document::Document document;
    CorrelatedPredicateNode filter;
    EXPECT_FALSE(filter.allow(document, 0));
    EXPECT_EQ(1u, issues.list.size());
}

TEST(CorrelatedGroupingPredicateTest, rejects_bad_arguments_when_created) {
    Rows rows;
    EXPECT_THROW((CorrelatedPredicateNode("selection", {"unit", "unit"}, rows.fields())), vespalib::IllegalArgumentException);
    EXPECT_THROW((CorrelatedPredicateNode("", {"unit", "zone"}, rows.fields())), vespalib::IllegalArgumentException);
}

TEST(CorrelatedGroupingPredicateTest, clone_does_not_retain_query_provider) {
    Rows rows;
    Issues issues;
    CorrelatedPredicateNode filter("selection", {"unit", "zone"}, rows.fields());
    filter.set_matcher([](uint32_t, auto) noexcept { return true; });
    std::unique_ptr<CorrelatedPredicateNode> copy(filter.clone());
    EXPECT_FALSE(copy->allow(1, 0));
    EXPECT_EQ(1u, issues.list.size());
    EXPECT_EQ("selection", copy->label());
    EXPECT_EQ((std::vector<std::string>{"unit", "zone"}), copy->variables());
}

TEST(OncePerDocumentPredicateTest, keeps_each_tuple_once_and_resets_for_next_document) {
    Rows rows;
    OncePerDocumentPredicateNode filter(rows.fields());
    for (uint32_t docid : {1, 2, 1}) {
        rows.index.set(0);
        EXPECT_TRUE(filter.allow(docid, 0));
        rows.index.set(1);
        EXPECT_FALSE(filter.allow(docid, 0));
        rows.index.set(2);
        EXPECT_TRUE(filter.allow(docid, 0));
        rows.index.set(3);
        EXPECT_TRUE(filter.allow(docid, 0));
    }
}

TEST(OncePerDocumentPredicateTest, clone_and_serialization_do_not_retain_seen_tuples) {
    std::vector<ExpressionNode::UP> keys;
    keys.push_back(std::make_unique<ConstantNode>(std::make_unique<StringResultNode>("M")));
    OncePerDocumentPredicateNode filter(std::move(keys));
    EXPECT_TRUE(filter.allow(1, 0));
    EXPECT_FALSE(filter.allow(1, 0));
    std::unique_ptr<OncePerDocumentPredicateNode> copy(filter.clone());
    EXPECT_TRUE(copy->allow(1, 0));
    vespalib::nbostream buffer;
    vespalib::NBOSerializer serializer(buffer);
    filter.onSerialize(serializer);
    copy->onDeserialize(serializer);
    EXPECT_TRUE(copy->allow(1, 0));
    EXPECT_FALSE(copy->allow(1, 0));
}

TEST(OncePerDocumentPredicateTest, rejects_empty_keys_and_unbound_multivalue_attributes) {
    EXPECT_THROW((OncePerDocumentPredicateNode(std::vector<ExpressionNode::UP>{})), vespalib::IllegalArgumentException);
    Rows rows;
    Issues issues;
    OncePerDocumentPredicateNode filter(rows.fields(false));
    EXPECT_FALSE(filter.allow(1, 0));
    EXPECT_FALSE(filter.allow(2, 0));
    EXPECT_EQ(1u, issues.list.size());
    vespalib::nbostream buffer;
    vespalib::NBOSerializer serializer(buffer);
    serializer << uint32_t(0);
    EXPECT_THROW(filter.onDeserialize(serializer), vespalib::IllegalArgumentException);
}

struct ZoneCounts {
    std::map<std::string, uint64_t> counts;
};

// Groups both documents by zone with filter correlated(...) and onceperdoc(unit, zone), in the given order.
// The query accepts the rows with unit 7 and zone A.
ZoneCounts group_accepted_rows_by_zone(bool once_per_document_last) {
    Rows rows;
    AttributeManager manager;
    EXPECT_TRUE(manager.add(rows.units));
    EXPECT_TRUE(manager.add(rows.zones));
    AttributeContext context(manager);
    std::vector<ExpressionNode::UP> fields;
    fields.push_back(std::make_unique<AttributeNode>("signals.unit"));
    fields.push_back(std::make_unique<AttributeNode>("signals.zone"));
    auto correlated = std::make_unique<CorrelatedPredicateNode>("selection", std::vector<std::string>{"unit", "zone"}, std::move(fields));
    auto* filter = correlated.get();
    std::vector<ExpressionNode::UP> keys;
    keys.push_back(std::make_unique<AttributeNode>("signals.unit"));
    keys.push_back(std::make_unique<AttributeNode>("signals.zone"));
    auto once_per_document = std::make_unique<OncePerDocumentPredicateNode>(std::move(keys));
    auto predicates = std::make_unique<AndPredicateNode>();
    if (once_per_document_last) {
        predicates->args().emplace_back(std::move(correlated));
        predicates->args().emplace_back(std::move(once_per_document));
    } else {
        predicates->args().emplace_back(std::move(once_per_document));
        predicates->args().emplace_back(std::move(correlated));
    }
    aggregation::Grouping grouping;
    aggregation::GroupingLevel level;
    level.setExpression(std::make_unique<AttributeNode>("signals.zone"));
    level.setFilter(std::move(predicates));
    level.addResult(aggregation::CountAggregationResult().setExpression(std::make_unique<ConstantNode>(std::make_unique<Int64ResultNode>(0))));
    grouping.addLevel(std::move(level)).setLastLevel(1);
    grouping.configureStaticStuff(ConfigureStaticParams(&context, nullptr));
    filter->set_matcher([](uint32_t, std::span<const CorrelationBinding> bindings) {
        return bindings[0].second == CorrelationValue(int64_t(7)) &&
               bindings[1].second == CorrelationValue(std::string_view("A"));
    });
    grouping.preAggregate(false);
    grouping.aggregate(DocId(2), HitRank(0));
    grouping.aggregate(DocId(1), HitRank(0));
    grouping.postAggregate();
    ZoneCounts result;
    for (uint32_t i = 0; i < grouping.getRoot().getChildrenSize(); ++i) {
        const auto& bucket = grouping.getRoot().getChild(i);
        char buffer[64];
        auto id = bucket.getId().getString(vespalib::BufferRef(buffer, sizeof(buffer)));
        const auto* count = dynamic_cast<const aggregation::CountAggregationResult*>(&bucket.getAggregationResult(0));
        result.counts[std::string(id.c_str(), id.size())] = count != nullptr ? count->getCount() : 0;
    }
    return result;
}

TEST(CorrelatedGroupingPredicateTest, grouping_counts_matching_units_without_duplicate_rows) {
    Issues issues;
    auto result = group_accepted_rows_by_zone(true);
    EXPECT_EQ((std::map<std::string, uint64_t>{{"A", 2}}), result.counts);
    EXPECT_TRUE(issues.list.empty());
}

TEST(OncePerDocumentPredicateTest, grouping_rejects_every_row_and_reports_once_when_onceperdoc_is_not_last) {
    Issues issues;
    auto result = group_accepted_rows_by_zone(false);
    EXPECT_TRUE(result.counts.empty());
    ASSERT_EQ(1u, issues.list.size());
    EXPECT_NE(std::string::npos, issues.list[0].find("onceperdoc"));
}

std::unique_ptr<OncePerDocumentPredicateNode> make_once_per_document() {
    std::vector<ExpressionNode::UP> keys;
    keys.push_back(std::make_unique<ConstantNode>(std::make_unique<StringResultNode>("M")));
    return std::make_unique<OncePerDocumentPredicateNode>(std::move(keys));
}

TEST(OncePerDocumentPredicateTest, only_the_whole_filter_or_the_last_and_condition_may_be_onceperdoc) {
    Issues issues;
    auto alone = make_once_per_document();
    EXPECT_EQ(alone.get(), OncePerDocumentPredicateNode::find_last_condition(*alone));
    EXPECT_TRUE(alone->allow(1, 0));

    AndPredicateNode nested;
    auto inner = std::make_unique<AndPredicateNode>();
    auto first = make_once_per_document();
    auto last = make_once_per_document();
    auto* first_ptr = first.get();
    auto* last_ptr = last.get();
    inner->args().emplace_back(std::move(last));
    nested.args().emplace_back(std::move(first));
    nested.args().emplace_back(std::move(inner));
    EXPECT_EQ(last_ptr, OncePerDocumentPredicateNode::find_last_condition(nested));
    EXPECT_TRUE(last_ptr->allow(1, 0));
    EXPECT_FALSE(first_ptr->allow(1, 0));

    auto negated_child = make_once_per_document();
    auto* negated_ptr = negated_child.get();
    NotPredicateNode negated(std::move(negated_child));
    EXPECT_EQ(nullptr, OncePerDocumentPredicateNode::find_last_condition(negated));
    EXPECT_FALSE(negated_ptr->allow(1, 0));
    EXPECT_EQ(2u, issues.list.size());
}

} // namespace
