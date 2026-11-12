// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/aggregation/grouping.h>
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
using search::aggregation::Grouping;
using search::aggregation::GroupingLevel;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::expression::AttributeNode;
using search::expression::ConfigureStaticParams;
using search::expression::DecodeFastMapLookupNode;
using search::expression::ExpressionTree;
using search::expression::ResultNodeVector;
using search::expression::StringResultNode;
using search::expression::StringResultNodeVector;
using ValueType = DecodeFastMapLookupNode::ValueType;
using vespalib::BufferRef;

namespace {

std::string entry(std::string_view key, std::string_view encoded_value) {
    std::string result(key);
    result.push_back(DecodeFastMapLookupNode::encoded_separator);
    result.append(encoded_value);
    return result;
}

std::string decode(std::string_view key, std::string_view encoded_value, ValueType value_type) {
    return DecodeFastMapLookupNode::decode(entry(key, encoded_value), value_type);
}

} // namespace

TEST(DecodeFastMapLookupNodeTest, int_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k:0", decode("k", "80000000", ValueType::INT));
    EXPECT_EQ("k:7", decode("k", "80000007", ValueType::INT));
    EXPECT_EQ("k:-1", decode("k", "7fffffff", ValueType::INT));
    EXPECT_EQ("k:-2147483648", decode("k", "00000000", ValueType::INT));
    EXPECT_EQ("k:2147483647", decode("k", "ffffffff", ValueType::INT));
    EXPECT_EQ("k:2147483647", decode("k", "FFFFFFFF", ValueType::INT));
}

TEST(DecodeFastMapLookupNodeTest, long_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k:5", decode("k", "8000000000000005", ValueType::LONG));
    EXPECT_EQ("k:-2", decode("k", "7ffffffffffffffe", ValueType::LONG));
    EXPECT_EQ("k:-9223372036854775808", decode("k", "0000000000000000", ValueType::LONG));
    EXPECT_EQ("k:9223372036854775807", decode("k", "ffffffffffffffff", ValueType::LONG));
}

TEST(DecodeFastMapLookupNodeTest, float_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k:0", decode("k", "80000000", ValueType::FLOAT));
    EXPECT_EQ("k:-0", decode("k", "7fffffff", ValueType::FLOAT));
    EXPECT_EQ("k:1", decode("k", "bf800000", ValueType::FLOAT));
    EXPECT_EQ("k:-1.5", decode("k", "403fffff", ValueType::FLOAT));
    EXPECT_EQ("k:inf", decode("k", "ff800000", ValueType::FLOAT));
    EXPECT_EQ("k:-inf", decode("k", "007fffff", ValueType::FLOAT));
}

TEST(DecodeFastMapLookupNodeTest, double_values_are_decoded_from_excess_hex) {
    EXPECT_EQ("k:2.5", decode("k", "c004000000000000", ValueType::DOUBLE));
    EXPECT_EQ("k:-0.25", decode("k", "402fffffffffffff", ValueType::DOUBLE));
    EXPECT_EQ("k:0.1", decode("k", "bfb999999999999a", ValueType::DOUBLE));
}

TEST(DecodeFastMapLookupNodeTest, nan_and_large_exponents_are_decoded) {
    EXPECT_EQ("k:nan", decode("k", "ffc00000", ValueType::FLOAT));
    EXPECT_EQ("k:nan", decode("k", "fff8000000000000", ValueType::DOUBLE));
    EXPECT_EQ("k:1e+30", decode("k", "f149f2ca", ValueType::FLOAT));
    EXPECT_EQ("k:1e+300", decode("k", "fe37e43c8800759c", ValueType::DOUBLE));
    EXPECT_EQ("k:1e-300", decode("k", "81a56e1fc2f8f359", ValueType::DOUBLE));
}

// The encoded values are those the Java encoders produce in vespajlib TextTestCase (testToExcessHex8,
// testToExcessHex16, testFloatToExcessHex8 and testDoubleToExcessHex16); keep the two in sync.
TEST(DecodeFastMapLookupNodeTest, values_encoded_by_java_text_are_decoded) {
    struct Vector {
        ValueType   type;
        const char* encoded;
        const char* decoded;
    };
    const std::vector<Vector> vectors{
        {ValueType::INT, "00000000", "-2147483648"},
        {ValueType::INT, "00000001", "-2147483647"},
        {ValueType::INT, "7fffffff", "-1"},
        {ValueType::INT, "80000000", "0"},
        {ValueType::INT, "80000001", "1"},
        {ValueType::INT, "8000000a", "10"},
        {ValueType::INT, "fffffffe", "2147483646"},
        {ValueType::INT, "ffffffff", "2147483647"},
        {ValueType::LONG, "0000000000000000", "-9223372036854775808"},
        {ValueType::LONG, "0000000000000001", "-9223372036854775807"},
        {ValueType::LONG, "7fffffffffffffff", "-1"},
        {ValueType::LONG, "8000000000000000", "0"},
        {ValueType::LONG, "8000000000000001", "1"},
        {ValueType::LONG, "800000000000000a", "10"},
        {ValueType::LONG, "fffffffffffffffe", "9223372036854775806"},
        {ValueType::LONG, "ffffffffffffffff", "9223372036854775807"},
        {ValueType::LONG, "7fffffff80000000", "-2147483648"},
        {ValueType::LONG, "800000007fffffff", "2147483647"},
        {ValueType::FLOAT, "007fffff", "-inf"},
        {ValueType::FLOAT, "00800000", "-3.4028235e+38"},
        {ValueType::FLOAT, "407fffff", "-1"},
        {ValueType::FLOAT, "7ffffffe", "-1e-45"},
        {ValueType::FLOAT, "7fffffff", "-0"},
        {ValueType::FLOAT, "80000000", "0"},
        {ValueType::FLOAT, "80000001", "1e-45"},
        {ValueType::FLOAT, "bf800000", "1"},
        {ValueType::FLOAT, "ff7fffff", "3.4028235e+38"},
        {ValueType::FLOAT, "ff800000", "inf"},
        {ValueType::FLOAT, "ffc00000", "nan"},
        {ValueType::FLOAT, "003fffff", "-nan"},
        {ValueType::DOUBLE, "000fffffffffffff", "-inf"},
        {ValueType::DOUBLE, "0010000000000000", "-1.7976931348623157e+308"},
        {ValueType::DOUBLE, "400fffffffffffff", "-1"},
        {ValueType::DOUBLE, "7ffffffffffffffe", "-5e-324"},
        {ValueType::DOUBLE, "7fffffffffffffff", "-0"},
        {ValueType::DOUBLE, "8000000000000000", "0"},
        {ValueType::DOUBLE, "8000000000000001", "5e-324"},
        {ValueType::DOUBLE, "bff0000000000000", "1"},
        {ValueType::DOUBLE, "ffefffffffffffff", "1.7976931348623157e+308"},
        {ValueType::DOUBLE, "fff0000000000000", "inf"},
        {ValueType::DOUBLE, "fff8000000000000", "nan"},
        {ValueType::DOUBLE, "0007ffffffffffff", "-nan"},
    };
    for (const auto& v : vectors) {
        SCOPED_TRACE(v.encoded);
        EXPECT_EQ(std::string("k:") + v.decoded, decode("k", v.encoded, v.type));
    }
}

TEST(DecodeFastMapLookupNodeTest, string_values_and_numeric_keys_are_kept_as_is) {
    EXPECT_EQ("k:some value", decode("k", "some value", ValueType::STRING));
    EXPECT_EQ("k:", decode("k", "", ValueType::STRING));
    EXPECT_EQ("42:7", decode("42", "80000007", ValueType::INT));
    EXPECT_EQ(":7", decode("", "80000007", ValueType::INT));
}

TEST(DecodeFastMapLookupNodeTest, separator_between_key_and_value_can_be_given) {
    EXPECT_EQ("k: 7", DecodeFastMapLookupNode::decode(entry("k", "80000007"), ValueType::INT, ": "));
    EXPECT_EQ("k7", DecodeFastMapLookupNode::decode(entry("k", "80000007"), ValueType::INT, ""));
    EXPECT_EQ("no separator", DecodeFastMapLookupNode::decode("no separator", ValueType::INT, ": "));
}

TEST(DecodeFastMapLookupNodeTest, separator_defaults_to_colon_and_can_be_set) {
    EXPECT_EQ(":", DecodeFastMapLookupNode("m$lookup", ValueType::INT).separator());
    EXPECT_EQ(":", DecodeFastMapLookupNode().separator());
    EXPECT_EQ("=", DecodeFastMapLookupNode("m$lookup", ValueType::INT, "=").separator());
    DecodeFastMapLookupNode node("m$lookup", ValueType::INT);
    EXPECT_EQ("->", node.separator("->").separator());
}

TEST(DecodeFastMapLookupNodeTest, malformed_entries_are_kept_undecoded) {
    EXPECT_EQ("no separator", DecodeFastMapLookupNode::decode("no separator", ValueType::INT));
    EXPECT_EQ("k:8000007", decode("k", "8000007", ValueType::INT));
    EXPECT_EQ("k:8000000x", decode("k", "8000000x", ValueType::INT));
    EXPECT_EQ("k:80000000", decode("k", "80000000", ValueType::LONG));
    EXPECT_EQ("k:80000000", decode("k", "80000000", ValueType::UNKNOWN));
}

namespace {

std::unique_ptr<DecodeFastMapLookupNode> serialize_and_deserialize(const DecodeFastMapLookupNode& node,
                                                                   size_t*                        size = nullptr) {
    vespalib::nbostream     os;
    vespalib::NBOSerializer serializer(os);
    serializer << node;
    if (size != nullptr) {
        *size = os.size();
    }
    auto deserialized = vespalib::Identifiable::create(serializer);
    return std::unique_ptr<DecodeFastMapLookupNode>(dynamic_cast<DecodeFastMapLookupNode*>(deserialized.release()));
}

} // namespace

TEST(DecodeFastMapLookupNodeTest, attribute_name_value_type_and_separator_are_copied_and_serialized) {
    DecodeFastMapLookupNode node("m$lookup", ValueType::LONG, ": ");
    DecodeFastMapLookupNode copy(node);
    EXPECT_EQ("m$lookup", copy.attribute_name());
    EXPECT_EQ(ValueType::LONG, copy.value_type());
    EXPECT_EQ(": ", copy.separator());

    size_t size = 0;
    auto   result = serialize_and_deserialize(node, &size);
    // class id, no result yet, the attribute name, one byte value type and the separator: nothing about the input
    EXPECT_EQ(4u + 1u + 4u + 8u + 1u + 4u + 2u, size);
    ASSERT_TRUE(result);
    EXPECT_EQ("m$lookup", result->attribute_name());
    EXPECT_EQ(ValueType::LONG, result->value_type());
    EXPECT_EQ(": ", result->separator());
}

TEST(DecodeFastMapLookupNodeTest, all_value_types_are_serialized) {
    for (auto type : {ValueType::UNKNOWN, ValueType::INT, ValueType::LONG, ValueType::FLOAT, ValueType::DOUBLE,
                      ValueType::STRING})
    {
        auto result = serialize_and_deserialize(DecodeFastMapLookupNode("m$lookup", type));
        ASSERT_TRUE(result);
        EXPECT_EQ(type, result->value_type());
    }
}

TEST(DecodeFastMapLookupNodeTest, unknown_serialized_value_type_is_deserialized_as_unknown) {
    vespalib::nbostream     os;
    vespalib::NBOSerializer serializer(os);
    serializer << DecodeFastMapLookupNode("m$lookup", ValueType::INT, "=");
    // The value type is the byte just before the separator (length 4 + 1 byte).
    ASSERT_EQ(uint8_t(ValueType::INT), uint8_t(os.data()[os.size() - 6]));
    const_cast<char*>(os.data())[os.size() - 6] = 42;
    auto  deserialized = vespalib::Identifiable::create(serializer);
    auto* result = dynamic_cast<DecodeFastMapLookupNode*>(deserialized.get());
    ASSERT_NE(nullptr, result);
    EXPECT_EQ("m$lookup", result->attribute_name());
    EXPECT_EQ(ValueType::UNKNOWN, result->value_type());
    EXPECT_EQ("=", result->separator());
}

class DecodeFastMapLookupNodeAttributeTest : public ::testing::Test {
protected:
    AttributeManager mgr;
    AttributeContext context;

    DecodeFastMapLookupNodeAttributeTest() : mgr(), context(mgr) {
        add_string_array("m$lookup",
                         {{entry("a", "80000001"), entry("b", "7fffffff")}, {}, {entry("c", "80000000")}});
        add_string_array("s$lookup", {{entry("x", "hello")}});
        add_string_array("a.b$lookup", {{entry("d", "80000002")}});
        add_string_single("single$lookup", {entry("a", "80000001"), entry("b", "7fffffff")});
    }
    ~DecodeFastMapLookupNodeAttributeTest() override;

    void add_string_array(const std::string& name, const std::vector<std::vector<std::string>>& values) {
        auto  attr = AttributeFactory::createAttribute(name, Config(BasicType::STRING, CollectionType::ARRAY));
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

    void add_string_single(const std::string& name, const std::vector<std::string>& values) {
        auto  attr = AttributeFactory::createAttribute(name, Config(BasicType::STRING, CollectionType::SINGLE));
        auto& sattr = dynamic_cast<StringAttribute&>(*attr);
        attr->addReservedDoc();
        for (const auto& value : values) {
            uint32_t docId = 0;
            ASSERT_TRUE(attr->addDoc(docId));
            sattr.update(docId, value);
            attr->commit();
        }
        ASSERT_TRUE(mgr.add(attr));
    }

    static std::unique_ptr<DecodeFastMapLookupNode> make_node(const std::string& attribute_name,
                                                              ValueType          value_type) {
        return std::make_unique<DecodeFastMapLookupNode>(attribute_name, value_type);
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
    auto tree = make_tree(make_node("m$lookup", ValueType::INT));
    EXPECT_EQ((std::vector<std::string>{"a:1", "b:-1"}), values_of(tree, 1));
    EXPECT_EQ((std::vector<std::string>{}), values_of(tree, 2));
    EXPECT_EQ((std::vector<std::string>{"c:0"}), values_of(tree, 3));
    EXPECT_EQ((std::vector<std::string>{"a:1", "b:-1"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, entries_are_decoded_with_set_separator) {
    auto node = make_node("m$lookup", ValueType::INT);
    node->separator(" -> ");
    auto tree = make_tree(std::move(node));
    EXPECT_EQ((std::vector<std::string>{"a -> 1", "b -> -1"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, string_values_are_decoded) {
    auto tree = make_tree(make_node("s$lookup", ValueType::STRING));
    EXPECT_EQ((std::vector<std::string>{"x:hello"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, deserialized_node_decodes) {
    auto node = serialize_and_deserialize(*make_node("m$lookup", ValueType::INT));
    ASSERT_TRUE(node);
    auto tree = make_tree(std::move(node));
    EXPECT_EQ((std::vector<std::string>{"a:1", "b:-1"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, copy_of_node_has_its_own_input) {
    auto node = make_node("m$lookup", ValueType::INT);
    auto copy = std::make_unique<DecodeFastMapLookupNode>(*node);
    auto tree = make_tree(std::move(node));
    auto copy_tree = make_tree(std::move(copy));
    EXPECT_EQ((std::vector<std::string>{"c:0"}), values_of(copy_tree, 3));
    EXPECT_EQ((std::vector<std::string>{"a:1", "b:-1"}), values_of(tree, 1));
    EXPECT_EQ((std::vector<std::string>{"c:0"}), values_of(copy_tree, 3));
}

// Grouping on the same (or an associated) array expands it, so the input then yields a single string.
TEST_F(DecodeFastMapLookupNodeAttributeTest, single_string_input_is_decoded_to_single_string) {
    auto tree = make_tree(make_node("single$lookup", ValueType::INT));
    for (auto [docId, expected] : {std::pair<uint32_t, std::string>{1, "a:1"}, {2, "b:-1"}}) {
        tree.execute(docId, 0.0);
        const auto& result = *tree.getResult();
        ASSERT_TRUE(result.inherits(StringResultNode::classId));
        EXPECT_FALSE(result.inherits(StringResultNodeVector::classId));
        char buf[100];
        auto sbuf = result.getString(BufferRef(buf, sizeof(buf)));
        EXPECT_EQ(expected, std::string(sbuf.c_str(), sbuf.size()));
    }
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, attribute_of_dotted_field_name_is_read) {
    auto tree = make_tree(make_node("a.b$lookup", ValueType::INT));
    EXPECT_EQ((std::vector<std::string>{"d:2"}), values_of(tree, 1));
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, grouping_prepares_node_with_serialized_value_type) {
    auto make_grouping = [](ValueType value_type) {
        Grouping      grouping;
        GroupingLevel level;
        level.setExpression(std::make_unique<DecodeFastMapLookupNode>("m$lookup", value_type));
        grouping.addLevel(std::move(level));
        grouping.setFirstLevel(0).setLastLevel(1);
        return grouping;
    };
    Grouping grouping = make_grouping(ValueType::INT);
    grouping.configureStaticStuff(ConfigureStaticParams(&context, nullptr));
    const auto& tree = grouping.getLevels()[0].getExpression();
    EXPECT_EQ((std::vector<std::string>{"a:1", "b:-1"}), values_of(tree, 1));

    Grouping unknown = make_grouping(ValueType::UNKNOWN);
    EXPECT_THROW(unknown.configureStaticStuff(ConfigureStaticParams(&context, nullptr)), std::runtime_error);
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, prepare_fails_for_unknown_value_type) {
    EXPECT_THROW(make_tree(make_node("m$lookup", ValueType::UNKNOWN)), std::runtime_error);
    EXPECT_THROW(make_tree(std::make_unique<DecodeFastMapLookupNode>()), std::runtime_error);
}

TEST_F(DecodeFastMapLookupNodeAttributeTest, wiring_fails_without_attribute) {
    EXPECT_THROW(make_tree(make_node("missing$lookup", ValueType::INT)), std::runtime_error);
    EXPECT_THROW(make_tree(make_node("m.lookup", ValueType::INT)), std::runtime_error);
}

GTEST_MAIN_RUN_ALL_TESTS()
