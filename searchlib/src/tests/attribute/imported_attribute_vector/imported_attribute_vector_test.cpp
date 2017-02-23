// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <memory>
#include <map>
#include <vector>
#include <algorithm>

namespace search {
namespace attribute {

using document::DocumentId;
using document::GlobalId;
using DocId             = IAttributeVector::DocId;
using WeightedInt       = IAttributeVector::WeightedInt;
using WeightedFloat     = IAttributeVector::WeightedFloat;
using WeightedString    = IAttributeVector::WeightedString;
using WeightedConstChar = IAttributeVector::WeightedConstChar;
using WeightedEnum      = IAttributeVector::WeightedEnum;

std::shared_ptr<ReferenceAttribute> create_reference_attribute(vespalib::stringref name = "ref") {
    return std::make_shared<ReferenceAttribute>(name, Config(BasicType::REFERENCE));
}

template <typename AttrVecType>
std::shared_ptr<AttrVecType> create_typed_attribute(BasicType basic_type,
                                                    CollectionType collection_type,
                                                    vespalib::stringref name = "parent") {
    return std::dynamic_pointer_cast<AttrVecType>(
            AttributeFactory::createAttribute(name, Config(basic_type, collection_type)));
}

template <typename AttrVecType>
std::shared_ptr<AttrVecType> create_single_attribute(BasicType type, vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::SINGLE, name);
}

template <typename AttrVecType>
std::shared_ptr<AttrVecType> create_array_attribute(BasicType type, vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::ARRAY, name);
}

template <typename AttrVecType>
std::shared_ptr<AttrVecType> create_wset_attribute(BasicType type, vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::WSET, name);
}

template <typename VectorType>
void add_n_docs_with_undefined_values(VectorType& vec, size_t n) {
    vec.addDocs(n);
    vec.commit();
}

GlobalId dummy_gid(uint32_t doc_index) {
    return DocumentId(vespalib::make_string("id:foo:bar::%u", doc_index)).getGlobalId();
}

struct Fixture {
    std::shared_ptr<AttributeVector>           target_attr;
    std::shared_ptr<ReferenceAttribute>        reference_attr;
    std::shared_ptr<ImportedAttributeVector>   imported_attr;
    std::shared_ptr<MockGidToLidMapperFactory> mapper_factory;

    Fixture()
        : target_attr(create_single_attribute<IntegerAttribute>(BasicType::INT32)),
          reference_attr(create_reference_attribute()),
          imported_attr(create_attribute_vector_from_members()),
          mapper_factory(std::make_shared<MockGidToLidMapperFactory>())
    {
        reference_attr->setGidToLidMapperFactory(mapper_factory);
    }

    void map_reference(DocId from_lid, GlobalId via_gid, DocId to_lid) {
        assert(from_lid < reference_attr->getNumDocs());
        reference_attr->update(from_lid, via_gid);
        reference_attr->commit();
        mapper_factory->_map[via_gid] = to_lid;
    }

    std::shared_ptr<ImportedAttributeVector> create_attribute_vector_from_members(vespalib::stringref name = "imported") {
        return std::make_shared<ImportedAttributeVector>(name, reference_attr, target_attr);
    }

    template <typename AttrVecType>
    std::shared_ptr<AttrVecType> target_attr_as() {
        auto ptr = std::dynamic_pointer_cast<AttrVecType>(target_attr);
        assert(ptr.get() != nullptr);
        return ptr;
    }

    void reset_with_new_target_attr(std::shared_ptr<AttributeVector> new_target) {
        target_attr = std::move(new_target);
        imported_attr = create_attribute_vector_from_members();
    }

    template <typename ValueType>
    struct LidToLidMapping {
        DocId     _from_lid;
        GlobalId  _via_gid;
        DocId     _to_lid;
        ValueType _value_in_target_attr;

        LidToLidMapping(DocId from_lid,
                        GlobalId via_gid,
                        DocId to_lid,
                        ValueType value_in_target_attr)
            : _from_lid(from_lid),
              _via_gid(via_gid),
              _to_lid(to_lid),
              _value_in_target_attr(std::move(value_in_target_attr))
        {}
    };

    void set_up_attribute_vectors_before_adding_mappings() {
        // Make a sneaky assumption that no tests try to use a lid > 9
        add_n_docs_with_undefined_values(*reference_attr, 10);
        add_n_docs_with_undefined_values(*target_attr, 10);
    }

    template <typename AttrVecType, typename MappingsType, typename ValueAssigner>
    void set_up_and_map(const MappingsType& mappings, ValueAssigner assigner) {
        set_up_attribute_vectors_before_adding_mappings();
        auto subtyped_target = target_attr_as<AttrVecType>();
        for (auto& m : mappings) {
            map_reference(m._from_lid, m._via_gid, m._to_lid);
            assigner(*subtyped_target, m);
        }
        subtyped_target->commit();
    }

    template <typename AttrVecType, typename ValueType>
    void reset_with_single_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<ValueType>>& mappings) {
        reset_with_new_target_attr(create_single_attribute<AttrVecType>(type));
        // Fun experiment: rename `auto& mapping` to `auto& m` and watch GCC howl about
        // shadowing a variable... that exists in the set_up_and_map function!
        set_up_and_map<AttrVecType>(mappings, [this](auto& target_vec, auto& mapping) {
            ASSERT_TRUE(target_vec.update(mapping._to_lid, mapping._value_in_target_attr));
        });
    }

    template <typename AttrVecType, typename ValueType>
    void reset_with_array_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<ValueType>>> &mappings) {
        reset_with_new_target_attr(create_array_attribute<AttrVecType>(type));
        set_up_and_map<AttrVecType>(mappings, [this](auto& target_vec, auto& mapping) {
            constexpr uint32_t weight = 1;
            for (const auto& v : mapping._value_in_target_attr) {
                ASSERT_TRUE(target_vec.append(mapping._to_lid, v, weight));
            }
        });
    }

    template <typename AttrVecType, typename WeightedValueType>
    void reset_with_wset_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<WeightedValueType>>> &mappings) {
        reset_with_new_target_attr(create_wset_attribute<AttrVecType>(type));
        set_up_and_map<AttrVecType>(mappings, [this](auto& target_vec, auto& mapping) {
            for (const auto& v : mapping._value_in_target_attr) {
                ASSERT_TRUE(target_vec.append(mapping._to_lid, v.value(), v.weight()));
            }
        });
    }
};

template <typename AttrValueType, typename PredicateType>
void assert_multi_value_matches(const Fixture& f,
                                DocId lid,
                                const std::vector<AttrValueType>& expected,
                                PredicateType predicate) {
    AttributeContent<AttrValueType> content;
    content.fill(*f.imported_attr, lid);
    EXPECT_EQUAL(expected.size(), content.size());
    std::vector<AttrValueType> actual(content.begin(), content.end());
    EXPECT_TRUE(std::equal(expected.begin(), expected.end(),
                           actual.begin(), actual.end(), predicate));
}

template <typename AttrValueType>
void assert_multi_value_matches(const Fixture& f,
                                DocId lid,
                                const std::vector<AttrValueType>& expected) {
    assert_multi_value_matches(f, lid, expected, std::equal_to<AttrValueType>());
}

// Simple wrappers to avoid ugly "f.template reset..." syntax.
template <typename AttrVecType, typename ValueType>
void reset_with_single_value_reference_mappings(
        Fixture& f,
        BasicType type,
        const std::vector<Fixture::LidToLidMapping<ValueType>>& mappings) {
    f.reset_with_single_value_reference_mappings<AttrVecType, ValueType>(type, mappings);
}

template <typename AttrVecType, typename ValueType>
void reset_with_array_value_reference_mappings(
        Fixture& f,
        BasicType type,
        const std::vector<Fixture::LidToLidMapping<std::vector<ValueType>>> &mappings) {
    f.reset_with_array_value_reference_mappings<AttrVecType, ValueType>(type, mappings);
}

template <typename AttrVecType, typename WeightedValueType>
void reset_with_wset_value_reference_mappings(
        Fixture& f,
        BasicType type,
        const std::vector<Fixture::LidToLidMapping<std::vector<WeightedValueType>>> &mappings) {
    f.reset_with_wset_value_reference_mappings<AttrVecType, WeightedValueType>(type, mappings);
}

TEST_F("Accessors return expected attributes", Fixture) {
    EXPECT_EQUAL(f.imported_attr->getReferenceAttribute().get(),
                 f.reference_attr.get());
    EXPECT_EQUAL(f.imported_attr->getTargetAttribute().get(),
                 f.target_attr.get());
}

TEST_F("getName() is equal to name given during construction", Fixture) {
    auto attr = f.create_attribute_vector_from_members("coolvector");
    EXPECT_EQUAL("coolvector", attr->getName());
}

TEST_F("getNumDocs() returns number of documents in reference attribute vector", Fixture) {
    add_n_docs_with_undefined_values(*f.reference_attr, 42);
    EXPECT_EQUAL(42, f.imported_attr->getNumDocs());
}

TEST_F("hasEnum() is false for non-enum target attribute vector", Fixture) {
    EXPECT_FALSE(f.imported_attr->hasEnum());
}

TEST_F("Collection type is inherited from target attribute", Fixture) {
    EXPECT_EQUAL(CollectionType::SINGLE, f.imported_attr->getCollectionType());
    f.reset_with_new_target_attr(create_array_attribute<IntegerAttribute>(BasicType::INT32));
    EXPECT_EQUAL(CollectionType::ARRAY, f.imported_attr->getCollectionType());
}

TEST_F("getBasicType() returns target vector basic type", Fixture) {
    f.reset_with_new_target_attr(create_single_attribute<IntegerAttribute>(BasicType::INT64));
    EXPECT_EQUAL(BasicType::INT64, f.imported_attr->getBasicType());
    f.reset_with_new_target_attr(create_single_attribute<FloatingPointAttribute>(BasicType::DOUBLE));
    EXPECT_EQUAL(BasicType::DOUBLE, f.imported_attr->getBasicType());
}

TEST_F("Single-valued integer attribute values can be retrieved via reference", Fixture) {
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(3), dummy_gid(7), DocId(7), 5678}});

    EXPECT_EQUAL(1234, f.imported_attr->getInt(DocId(1)));
    EXPECT_EQUAL(5678, f.imported_attr->getInt(DocId(3)));
}

TEST_F("getValueCount() is 1 for mapped single value attribute", Fixture) {
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32, {{DocId(1), dummy_gid(3), DocId(3), 1234}});
    EXPECT_EQUAL(1u, f.imported_attr->getValueCount(DocId(1)));
}

TEST_F("getValueCount() is 0 for non-mapped single value attribute", Fixture) {
    add_n_docs_with_undefined_values(*f.reference_attr, 3);
    EXPECT_EQUAL(0u, f.imported_attr->getValueCount(DocId(2)));
}

TEST_F("getMaxValueCount() is 1 for single value attribute vectors", Fixture) {
    EXPECT_EQUAL(1u, f.imported_attr->getMaxValueCount());
}

TEST_F("getFixedWidth() is inherited from target attribute vector", Fixture) {
    EXPECT_EQUAL(f.target_attr->getFixedWidth(),
                 f.imported_attr->getFixedWidth());
}

TEST_F("Multi-valued integer attribute values can be retrieved via reference", Fixture) {
    const std::vector<int64_t> doc3_values({1234});
    const std::vector<int64_t> doc7_values({5678, 9876, 555, 777});
    const std::vector<int64_t> doc8_values({});
    reset_with_array_value_reference_mappings<IntegerAttribute, int64_t>(
            f, BasicType::INT64,
            {{DocId(1), dummy_gid(3), DocId(3), doc3_values},
             {DocId(3), dummy_gid(7), DocId(7), doc7_values},
             {DocId(5), dummy_gid(8), DocId(8), doc8_values}});
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(1), doc3_values);
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(3), doc7_values);
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(5), doc8_values);
}

TEST_F("Weighted integer attribute values can be retrieved via reference", Fixture) {
    const std::vector<WeightedInt> doc3_values({WeightedInt(1234, 5)});
    const std::vector<WeightedInt> doc7_values({WeightedInt(5678, 10), WeightedInt(9876, 20)});
    reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), doc3_values},
             {DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    assert_multi_value_matches<WeightedInt>(f, DocId(1), doc3_values);
    assert_multi_value_matches<WeightedInt>(f, DocId(3), doc7_values);
}

TEST_F("LID with not present GID reference mapping returns default value", Fixture) {
    add_n_docs_with_undefined_values(*f.reference_attr, 2);
    EXPECT_EQUAL(f.target_attr->getInt(DocId(0)), // Implicit default undefined value
                 f.imported_attr->getInt(DocId(1)));
}

TEST_F("Singled-valued floating point attribute values can be retrieved via reference", Fixture) {
    reset_with_single_value_reference_mappings<FloatingPointAttribute, float>(
            f, BasicType::FLOAT,
            {{DocId(2), dummy_gid(3), DocId(3), 10.5f},
             {DocId(4), dummy_gid(8), DocId(8), 3.14f}});

    EXPECT_EQUAL(10.5f, f.imported_attr->getFloat(DocId(2)));
    EXPECT_EQUAL(3.14f, f.imported_attr->getFloat(DocId(4)));
}

TEST_F("Multi-valued floating point attribute values can be retrieved via reference", Fixture) {
    const std::vector<double> doc3_values({3.14, 133.7});
    const std::vector<double> doc7_values({5.5,  6.5, 10.5});
    reset_with_array_value_reference_mappings<FloatingPointAttribute, double>(
            f, BasicType::DOUBLE,
            {{DocId(2), dummy_gid(3), DocId(3), doc3_values},
             {DocId(4), dummy_gid(7), DocId(7), doc7_values}});
    assert_multi_value_matches<double>(f, DocId(2), doc3_values);
    assert_multi_value_matches<double>(f, DocId(4), doc7_values);
}

TEST_F("Weighted floating point attribute values can be retrieved via reference", Fixture) {
    const std::vector<WeightedFloat> doc3_values({WeightedFloat(3.14, 5)});
    const std::vector<WeightedFloat> doc7_values({WeightedFloat(5.5, 7), WeightedFloat(10.25, 42)});
    reset_with_wset_value_reference_mappings<FloatingPointAttribute, WeightedFloat>(
            f, BasicType::DOUBLE,
            {{DocId(1), dummy_gid(3), DocId(3), doc3_values},
             {DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    assert_multi_value_matches<WeightedFloat>(f, DocId(1), doc3_values);
    assert_multi_value_matches<WeightedFloat>(f, DocId(3), doc7_values);
}

struct SingleStringAttrFixture : Fixture {
    DocId from_lid1{2};
    DocId to_lid1{3};
    DocId from_lid2{4};
    DocId to_lid2{7};

    SingleStringAttrFixture() : Fixture() {
        setup();
    }

    void setup() {
        reset_with_single_value_reference_mappings<StringAttribute, const char*>(
                BasicType::STRING,
                {{from_lid1, dummy_gid(3), to_lid1, "foo"},
                 {from_lid2, dummy_gid(7), to_lid2, "bar"}});
    }
};

TEST_F("Single-valued string attribute values can be retrieved via reference", SingleStringAttrFixture) {
    char buf[64];
    EXPECT_EQUAL(vespalib::string("foo"), f.imported_attr->getString(f.from_lid1, buf, sizeof(buf)));
    EXPECT_EQUAL(vespalib::string("bar"), f.imported_attr->getString(f.from_lid2, buf, sizeof(buf)));
}

TEST_F("getEnum() returns target vector enum via reference", SingleStringAttrFixture) {
    EXPECT_EQUAL(f.target_attr->getEnum(f.to_lid1),
                 f.imported_attr->getEnum(f.from_lid1));
    EXPECT_EQUAL(f.target_attr->getEnum(f.to_lid2),
                 f.imported_attr->getEnum(f.from_lid2));
}

TEST_F("findEnum() returns target vector enum via reference", SingleStringAttrFixture) {
    EnumHandle expected_handle{};
    ASSERT_TRUE(f.target_attr->findEnum("foo", expected_handle));
    EnumHandle actual_handle{};
    ASSERT_TRUE(f.imported_attr->findEnum("foo", actual_handle));
    EXPECT_EQUAL(expected_handle, actual_handle);
}

TEST_F("hasEnum() is true for enum target attribute vector", SingleStringAttrFixture) {
    EXPECT_TRUE(f.imported_attr->hasEnum());
}

bool string_eq(const char* lhs, const char* rhs) noexcept {
    return strcmp(lhs, rhs) == 0;
};

template <typename T>
std::vector<T> as_vector(const AttributeContent<T>& content) {
    return {content.begin(), content.end()};
}

struct MultiStringAttrFixture : Fixture {
    std::vector<const char*> doc3_values{{"foo", "bar"}};
    std::vector<const char*> doc7_values{{"baz", "bjarne", "betjent"}};

    MultiStringAttrFixture() : Fixture() {
        setup();
    }

    void setup() {
        reset_with_array_value_reference_mappings<StringAttribute, const char *>(
                BasicType::STRING,
                {{DocId(2), dummy_gid(3), DocId(3), doc3_values},
                 {DocId(4), dummy_gid(7), DocId(7), doc7_values}});
    }
};

TEST_F("Multi-valued string attribute values can be retrieved via reference", MultiStringAttrFixture) {
    assert_multi_value_matches<const char*>(f, DocId(2), f.doc3_values, string_eq);
    assert_multi_value_matches<const char*>(f, DocId(4), f.doc7_values, string_eq);
}

TEST_F("Multi-valued enum attribute values can be retrieved via reference", MultiStringAttrFixture) {
    AttributeContent<EnumHandle> expected;
    expected.fill(*f.target_attr, DocId(3));
    assert_multi_value_matches<EnumHandle>(f, DocId(2), as_vector(expected));
}

TEST_F("getValueCount() is equal to stored values for mapped multi value attribute", MultiStringAttrFixture) {
    EXPECT_EQUAL(f.doc7_values.size(), f.imported_attr->getValueCount(DocId(4)));
}

TEST_F("getMaxValueCount() is greater than 1 for multi value attribute vectors", MultiStringAttrFixture) {
    EXPECT_GREATER(f.imported_attr->getMaxValueCount(), 1u);
}

struct WeightedMultiStringAttrFixture : Fixture {
    std::vector<WeightedString> doc3_values{{WeightedString("foo", 5)}};
    std::vector<WeightedString> doc7_values{{WeightedString("bar", 7), WeightedString("baz", 42)}};

    WeightedMultiStringAttrFixture() : Fixture() {
        setup();
    }

    void setup() {
        reset_with_wset_value_reference_mappings<StringAttribute, WeightedString>(
                BasicType::STRING,
                {{DocId(1), dummy_gid(3), DocId(3), doc3_values},
                 {DocId(3), dummy_gid(7), DocId(7), doc7_values}});
    }
};

TEST_F("Weighted string attribute values can be retrieved via reference", WeightedMultiStringAttrFixture) {
    assert_multi_value_matches<WeightedString>(f, DocId(1), f.doc3_values);
    assert_multi_value_matches<WeightedString>(f, DocId(3), f.doc7_values);
}

TEST_F("Weighted enum attribute values can be retrieved via reference", WeightedMultiStringAttrFixture) {
    AttributeContent<WeightedEnum> expected;
    expected.fill(*f.target_attr, DocId(7));
    assert_multi_value_matches<WeightedEnum>(f, DocId(3), as_vector(expected));
}

bool weighted_string_eq(const WeightedConstChar& lhs, const WeightedConstChar& rhs) noexcept {
    if (lhs.weight() != rhs.weight()) {
        return false;
    }
    return (strcmp(lhs.value(), rhs.value()) == 0);
};

TEST_F("Weighted const char attribute values can be retrieved via reference", WeightedMultiStringAttrFixture) {
    AttributeContent<WeightedConstChar> expected;
    expected.fill(*f.target_attr, DocId(7));

    assert_multi_value_matches<WeightedConstChar>(f, DocId(3), as_vector(expected), weighted_string_eq);
}

// Poor man's function call mock matching
struct MockAttributeVector : NotImplementedAttribute {
    // Mutable is dirty, but funcs are called in a const context and we know
    // there won't be multiple threads touching anything.
    mutable DocId _doc_id{0};
    mutable void* _ser_to{nullptr};
    mutable long  _available{0};
    mutable const common::BlobConverter* _bc{nullptr};
    mutable bool _ascending_called{false};
    mutable bool _descending_called{false};

    long _return_value{1234};

    MockAttributeVector()
            : NotImplementedAttribute("mock", Config(BasicType::INT32)) {
    }

    void set_received_args(DocId doc_id, void* ser_to,
                           long available, const common::BlobConverter* bc) const {
        _doc_id = doc_id;
        _ser_to = ser_to;
        _available = available;
        _bc = bc;
    }

    long onSerializeForAscendingSort(
            DocId doc_id, void* ser_to,
            long available, const common::BlobConverter* bc) const override {
        set_received_args(doc_id, ser_to, available, bc);
        _ascending_called = true;
        return _return_value;
    }
    long onSerializeForDescendingSort(
            DocId doc_id, void* ser_to,
            long available, const common::BlobConverter* bc) const override {
        set_received_args(doc_id, ser_to, available, bc);
        _descending_called = true;
        return _return_value;
    }

    // Not covered by NotImplementedAttribute
    void onCommit() override {}
    void onUpdateStat() override {}
};

struct MockBlobConverter : common::BlobConverter {
    ConstBufferRef onConvert(const ConstBufferRef&) const override {
        return ConstBufferRef();
    }
};

struct SerializeFixture : Fixture {
    std::shared_ptr<MockAttributeVector> mock_target;
    MockBlobConverter mock_converter;

    SerializeFixture()
            : Fixture(),
              mock_target(std::make_shared<MockAttributeVector>())
    {
        reset_with_new_target_attr(mock_target);
    }
};

TEST_F("onSerializeForAscendingSort() is forwarded to target vector", SerializeFixture) {
    int dummy_tag;
    void* ser_to = &dummy_tag;
    EXPECT_EQUAL(f.mock_target->_return_value,
                 f.imported_attr->serializeForAscendingSort(
                         DocId(10), ser_to, 777, &f.mock_converter));
    EXPECT_TRUE(f.mock_target->_ascending_called);
    EXPECT_EQUAL(DocId(10), f.mock_target->_doc_id);
    EXPECT_EQUAL(ser_to, f.mock_target->_ser_to);
    EXPECT_EQUAL(777, f.mock_target->_available);
    EXPECT_EQUAL(&f.mock_converter, f.mock_target->_bc);
}

TEST_F("onSerializeForDescendingSort() is forwarded to target vector", SerializeFixture) {
    int dummy_tag;
    void* ser_to = &dummy_tag;
    EXPECT_EQUAL(f.mock_target->_return_value,
                 f.imported_attr->serializeForDescendingSort(
                         DocId(20), ser_to, 555, &f.mock_converter));
    EXPECT_TRUE(f.mock_target->_descending_called);
    EXPECT_EQUAL(DocId(20), f.mock_target->_doc_id);
    EXPECT_EQUAL(ser_to, f.mock_target->_ser_to);
    EXPECT_EQUAL(555, f.mock_target->_available);
    EXPECT_EQUAL(&f.mock_converter, f.mock_target->_bc);
}

} // attribute
} // search

TEST_MAIN() { TEST_RUN_ALL(); }