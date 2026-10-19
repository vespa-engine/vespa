// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/aggregation/grouping.h>
#include <vespa/searchlib/aggregation/countaggregationresult.h>
#include <vespa/searchlib/expression/and_predicate_node.h>
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

TEST(CorrelatedGroupingPredicateTest, requires_a_provider_and_scalar_attribute_arguments) {
    Rows rows;
    CorrelatedPredicateNode filter("selection", {"unit", "zone"}, rows.fields());
    EXPECT_THROW(filter.allow(1, 0), vespalib::IllegalArgumentException);
    CorrelatedPredicateNode unbound("selection", {"unit", "zone"}, rows.fields(false));
    unbound.set_matcher([](uint32_t, auto) noexcept { return true; });
    EXPECT_THROW(unbound.allow(1, 0), vespalib::IllegalArgumentException);
    EXPECT_THROW((CorrelatedPredicateNode("selection", {"unit", "unit"}, rows.fields())), vespalib::IllegalArgumentException);
    EXPECT_THROW((CorrelatedPredicateNode("", {"unit", "zone"}, rows.fields())), vespalib::IllegalArgumentException);
}

TEST(CorrelatedGroupingPredicateTest, clone_does_not_retain_query_provider) {
    Rows rows;
    CorrelatedPredicateNode filter("selection", {"unit", "zone"}, rows.fields());
    filter.set_matcher([](uint32_t, auto) noexcept { return true; });
    std::unique_ptr<CorrelatedPredicateNode> copy(filter.clone());
    EXPECT_THROW(copy->allow(1, 0), vespalib::IllegalArgumentException);
    EXPECT_EQ("selection", copy->label());
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
    OncePerDocumentPredicateNode filter(rows.fields(false));
    EXPECT_THROW(filter.allow(1, 0), vespalib::IllegalArgumentException);
    vespalib::nbostream buffer;
    vespalib::NBOSerializer serializer(buffer);
    serializer << uint32_t(0);
    EXPECT_THROW(filter.onDeserialize(serializer), vespalib::IllegalArgumentException);
}

TEST(CorrelatedGroupingPredicateTest, grouping_counts_matching_units_without_duplicate_rows) {
    Rows rows;
    AttributeManager manager;
    ASSERT_TRUE(manager.add(rows.units));
    ASSERT_TRUE(manager.add(rows.zones));
    AttributeContext context(manager);
    std::vector<ExpressionNode::UP> fields;
    fields.push_back(std::make_unique<AttributeNode>("signals.unit"));
    fields.push_back(std::make_unique<AttributeNode>("signals.zone"));
    auto correlated = std::make_unique<CorrelatedPredicateNode>("selection", std::vector<std::string>{"unit", "zone"}, std::move(fields));
    auto* filter = correlated.get();
    std::vector<ExpressionNode::UP> keys;
    keys.push_back(std::make_unique<AttributeNode>("signals.unit"));
    keys.push_back(std::make_unique<AttributeNode>("signals.zone"));
    auto predicates = std::make_unique<AndPredicateNode>();
    predicates->args().emplace_back(std::move(correlated));
    predicates->args().emplace_back(std::make_unique<OncePerDocumentPredicateNode>(std::move(keys)));
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
    grouping.sortById();
    ASSERT_EQ(1u, grouping.getRoot().getChildrenSize());
    const auto& bucket = grouping.getRoot().getChild(0);
    EXPECT_EQ(0, bucket.getId().cmp(StringResultNode("A")));
    ASSERT_EQ(1u, bucket.getAggrSize());
    const auto* count = dynamic_cast<const aggregation::CountAggregationResult*>(&bucket.getAggregationResult(0));
    ASSERT_NE(nullptr, count);
    EXPECT_EQ(2u, count->getCount());
}

} // namespace
