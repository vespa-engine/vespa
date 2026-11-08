// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/decode_fast_map_lookup_node.h>
#include <vespa/searchlib/expression/expressiontree.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>

using search::AttributeContext;
using search::AttributeFactory;
using search::AttributeManager;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::expression::AttributeNode;
using search::expression::DecodeFastMapLookupNode;
using search::expression::ExpressionTree;
using search::expression::ResultNodeVector;
using search::expression::StringResultNodeVector;
using ValueType = DecodeFastMapLookupNode::ValueType;
using FastMapLookup = DecodeFastMapLookupNode::FastMapLookup;
using vespalib::BufferRef;

namespace {

std::string entry(std::string_view key, std::string_view encoded_value) {
    std::string result(key);
    result.push_back(DecodeFastMapLookupNode::encoded_separator);
    result.append(encoded_value);
    return result;
}

FastMapLookup make_lookup(const std::string& field_name, const std::string& lookup_name,
                          const std::string& value_type) {
    return {.field_name = field_name,
            .lookup_name = lookup_name,
            .key_field = "key",
            .key_type = "string",
            .value_field = "value",
            .value_type = value_type};
}

std::string decode(std::string_view key, std::string_view encoded_value, ValueType value_type) {
    return DecodeFastMapLookupNode::decode(entry(key, encoded_value), value_type);
}

} // namespace

TEST(DecodeFastMapLookupNodeTest, int_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k=0", decode("k", "80000000", ValueType::INT));
    EXPECT_EQ("k=7", decode("k", "80000007", ValueType::INT));
    EXPECT_EQ("k=-1", decode("k", "7fffffff", ValueType::INT));
    EXPECT_EQ("k=-2147483648", decode("k", "00000000", ValueType::INT));
    EXPECT_EQ("k=2147483647", decode("k", "ffffffff", ValueType::INT));
    EXPECT_EQ("k=2147483647", decode("k", "FFFFFFFF", ValueType::INT));
}

TEST(DecodeFastMapLookupNodeTest, long_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k=5", decode("k", "8000000000000005", ValueType::LONG));
    EXPECT_EQ("k=-2", decode("k", "7ffffffffffffffe", ValueType::LONG));
    EXPECT_EQ("k=-9223372036854775808", decode("k", "0000000000000000", ValueType::LONG));
    EXPECT_EQ("k=9223372036854775807", decode("k", "ffffffffffffffff", ValueType::LONG));
}

TEST(DecodeFastMapLookupNodeTest, float_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k=0", decode("k", "80000000", ValueType::FLOAT));
    EXPECT_EQ("k=-0", decode("k", "7fffffff", ValueType::FLOAT));
    EXPECT_EQ("k=1", decode("k", "bf800000", ValueType::FLOAT));
    EXPECT_EQ("k=-1.5", decode("k", "403fffff", ValueType::FLOAT));
    EXPECT_EQ("k=inf", decode("k", "ff800000", ValueType::FLOAT));
    EXPECT_EQ("k=-inf", decode("k", "007fffff", ValueType::FLOAT));
}

TEST(DecodeFastMapLookupNodeTest, double_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k=2.5", decode("k", "c004000000000000", ValueType::DOUBLE));
    EXPECT_EQ("k=-0.25", decode("k", "402fffffffffffff", ValueType::DOUBLE));
    EXPECT_EQ("k=0.1", decode("k", "bfb999999999999a", ValueType::DOUBLE));
}

TEST(DecodeFastMapLookupNodeTest, string_values_and_numeric_keys_are_kept_as_is) {
    EXPECT_EQ("k=some value", decode("k", "some value", ValueType::STRING));
    EXPECT_EQ("k=", decode("k", "", ValueType::STRING));
    EXPECT_EQ("42=7", decode("42", "80000007", ValueType::INT));
    EXPECT_EQ("=7", decode("", "80000007", ValueType::INT));
}

TEST(DecodeFastMapLookupNodeTest, malformed_entries_are_kept_undecoded) {
    EXPECT_EQ("no separator", DecodeFastMapLookupNode::decode("no separator", ValueType::INT));
    EXPECT_EQ("k=8000007", decode("k", "8000007", ValueType::INT));
    EXPECT_EQ("k=8000000x", decode("k", "8000000x", ValueType::INT));
    EXPECT_EQ("k=80000000", decode("k", "80000000", ValueType::LONG));
    EXPECT_EQ("k=80000000", decode("k", "80000000", ValueType::UNKNOWN));
}

TEST(DecodeFastMapLookupNodeTest, value_type_names_are_parsed) {
    EXPECT_EQ(ValueType::INT, DecodeFastMapLookupNode::toValueType("int"));
    EXPECT_EQ(ValueType::LONG, DecodeFastMapLookupNode::toValueType("long"));
    EXPECT_EQ(ValueType::FLOAT, DecodeFastMapLookupNode::toValueType("float"));
    EXPECT_EQ(ValueType::DOUBLE, DecodeFastMapLookupNode::toValueType("double"));
    EXPECT_EQ(ValueType::STRING, DecodeFastMapLookupNode::toValueType("string"));
    EXPECT_EQ(ValueType::UNKNOWN, DecodeFastMapLookupNode::toValueType("byte"));
}

TEST(DecodeFastMapLookupNodeTest, lookup_attribute_name_replaces_last_dot_with_dollar) {
    EXPECT_EQ("m$lookup", DecodeFastMapLookupNode("m.lookup").lookupAttributeName());
    EXPECT_EQ("a.b$c", DecodeFastMapLookupNode("a.b.c").lookupAttributeName());
    EXPECT_THROW((void)DecodeFastMapLookupNode("m").lookupAttributeName(), std::runtime_error);
    EXPECT_THROW((void)DecodeFastMapLookupNode(".lookup").lookupAttributeName(), std::runtime_error);
    EXPECT_THROW((void)DecodeFastMapLookupNode("m.").lookupAttributeName(), std::runtime_error);
}

TEST(DecodeFastMapLookupNodeTest, configure_requires_matching_name_and_known_value_type) {
    DecodeFastMapLookupNode node("m.lookup");
    EXPECT_EQ(ValueType::UNKNOWN, node.getValueType());
    EXPECT_THROW(node.configure(make_lookup("m", "other", "int")), std::runtime_error);
    EXPECT_THROW(node.configure(make_lookup("m", "lookup", "byte")), std::runtime_error);
    EXPECT_EQ(ValueType::UNKNOWN, node.getValueType());
    node.configure(make_lookup("m", "lookup", "long"));
    EXPECT_EQ(ValueType::LONG, node.getValueType());
    DecodeFastMapLookupNode malformed("m");
    EXPECT_THROW(malformed.configure(make_lookup("m", "lookup", "int")), std::runtime_error);
}

TEST(DecodeFastMapLookupNodeTest, only_name_is_serialized_but_copies_keep_value_type) {
    DecodeFastMapLookupNode node("m.lookup");
    node.configure(make_lookup("m", "lookup", "int"));
    DecodeFastMapLookupNode copy(node);
    EXPECT_EQ("m.lookup", copy.getName());
    EXPECT_EQ(ValueType::INT, copy.getValueType());

    vespalib::nbostream     os;
    vespalib::NBOSerializer serializer(os);
    serializer << node;
    // class id, no result yet, then the name as length and bytes: nothing about the hidden input
    EXPECT_EQ(4u + 1u + 4u + 8u, os.size());
    auto deserialized = vespalib::Identifiable::create(serializer);
    ASSERT_TRUE(deserialized);
    auto* result = dynamic_cast<DecodeFastMapLookupNode*>(deserialized.get());
    ASSERT_NE(nullptr, result);
    EXPECT_EQ("m.lookup", result->getName());
    EXPECT_EQ(ValueType::UNKNOWN, result->getValueType());
}

class DecodeFastMapLookupNodeAttributeTest : public ::testing::Test {
protected:
    AttributeManager mgr;
    AttributeContext context;

    DecodeFastMapLookupNodeAttributeTest() : mgr(), context(mgr) {
        add_string_array("m$lookup", {{entry("a", "80000001"), entry("b", "7fffffff")}, {}, {entry("c", "80000000")}});
        add_string_array("s$lookup", {{entry("x", "hello")}});
    }
    ~DecodeFastMapLookupNodeAttributeTest() override;

    void add_string_array(const std::string& name, const std::vector<std::vector<std::string>>& values) {
        auto attr = AttributeFactory::createAttribute(name, Config(BasicType::STRING, CollectionType::ARRAY));
        auto& sattr = dynamic_cast<StringAttribute&>(*attr);
        attr->addReservedDoc();
        for (const auto& docValues : values) {
            uint32_t docId = 0;
            ASSERT_TRUE(attr->addDoc(docId));
            for (const auto& value : docValues) {
                sattr.append(docId, value, 1);
            }
            attr->commit();
        }
        ASSERT_TRUE(mgr.add(attr));
    }

    static std::unique_ptr<DecodeFastMapLookupNode> make_configured_node(const std::string& field_name,
                                                                         const std::string& value_type) {
        auto node = std::make_unique<DecodeFastMapLookupNode>(field_name + ".lookup");
        node->configure(make_lookup(field_name, "lookup", value_type));
        return node;
    }

    // Wires and prepares the node as grouping does, then wraps it in a tree, which sets the docid on the input.
    ExpressionTree make_tree(std::unique_ptr<DecodeFastMapLookupNode> node) {
        AttributeNode::Configure configure(context);
        node->select(configure, configure);
        node->prepare(false);
        return ExpressionTree(std::move(node));
    }

    static std::vector<std::string> values_of(const ExpressionTree& tree, uint32_t docId) {
        tree.execute(docId, 0.0);
        const auto& result = *tree.getResult();
        EXPECT_TRUE(result.inherits(StringResultNodeVector::classId));
        const auto&              vector = static_cast<const ResultNodeVector&>(result);
        std::vector<std::string> values;
        for (size_t i = 0; i < vector.size(); ++i) {
            char buf[100];
            auto sbuf = vector.get(i).getString(BufferRef(buf, sizeof(buf)));
            values.emplace_back(sbuf.c_str(), sbuf.size());
        }
        return values;
    }
};

DecodeFastMapLookupNodeAttributeTest::~DecodeFastMapLookupNodeAttributeTest() = default;

TEST_F(DecodeFastMapLookupNodeAttributeTest, entries_of_hidden_attribute_are_decoded_per_document) {
    auto tree = make_tree(make_configured_node("m", "int"));
    EXPECT_EQ((std::vector<std::string>{"a=1", "b=-1"}), values_of(tree, 1));
    EXPECT_EQ((std::vector<std::string>{}), values_of(tree, 2));
    EXPECT_EQ((std::vector<std::string>{"c=0"}), values_of(tree, 3));
    EXPECT_EQ((std::vector<std::string>{"a=1", "b=-1"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, string_values_are_decoded) {
    auto tree = make_tree(make_configured_node("s", "string"));
    EXPECT_EQ((std::vector<std::string>{"x=hello"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, deserialized_node_decodes_when_configured_again) {
    vespalib::nbostream     os;
    vespalib::NBOSerializer serializer(os);
    serializer << *make_configured_node("m", "int");
    auto node = std::unique_ptr<DecodeFastMapLookupNode>(
        dynamic_cast<DecodeFastMapLookupNode*>(vespalib::Identifiable::create(serializer).release()));
    ASSERT_TRUE(node);
    EXPECT_THROW(make_tree(std::make_unique<DecodeFastMapLookupNode>(*node)), std::runtime_error);
    node->configure(make_lookup("m", "lookup", "int"));
    auto tree = make_tree(std::move(node));
    EXPECT_EQ((std::vector<std::string>{"a=1", "b=-1"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, copy_of_configured_node_has_its_own_input) {
    auto node = make_configured_node("m", "int");
    auto copy = std::make_unique<DecodeFastMapLookupNode>(*node);
    auto tree = make_tree(std::move(node));
    auto copy_tree = make_tree(std::move(copy));
    EXPECT_EQ((std::vector<std::string>{"c=0"}), values_of(copy_tree, 3));
    EXPECT_EQ((std::vector<std::string>{"a=1", "b=-1"}), values_of(tree, 1));
    EXPECT_EQ((std::vector<std::string>{"c=0"}), values_of(copy_tree, 3));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, prepare_fails_without_configuration) {
    EXPECT_THROW(make_tree(std::make_unique<DecodeFastMapLookupNode>("m.lookup")), std::runtime_error);
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, wiring_fails_without_attribute) {
    EXPECT_THROW(make_tree(make_configured_node("missing", "int")), std::runtime_error);
}

GTEST_MAIN_RUN_ALL_TESTS()
