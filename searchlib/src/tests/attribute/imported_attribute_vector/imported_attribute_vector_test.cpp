// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <memory>
#include <map>
#include <vector>
#include <algorithm>

namespace search {
namespace attribute {

using DocumentId        = document::DocumentId;
using GlobalId          = document::GlobalId;
using DocId             = IAttributeVector::DocId;
using WeightedInt       = IAttributeVector::WeightedInt;
using WeightedFloat     = IAttributeVector::WeightedFloat;
using WeightedString    = IAttributeVector::WeightedString;
using WeightedConstChar = IAttributeVector::WeightedConstChar;

// FIXME (mostly) dupe with reference_attribute_test, pull out to shared mock
// Begin dupe-ey stuff:

using MockGidToLidMap = std::map<GlobalId, uint32_t>;

struct MockGidToLidMapper : public search::IGidToLidMapper
{
    const MockGidToLidMap &_map;
    MockGidToLidMapper(const MockGidToLidMap &map)
            : _map(map)
    {
    }
    uint32_t mapGidToLid(const document::GlobalId &gid) const override {
        auto itr = _map.find(gid);
        if (itr != _map.end()) {
            return itr->second;
        } else {
            return 0u;
        }
    }
};

struct MockGidToLidMapperFactory : public search::IGidToLidMapperFactory
{
    MockGidToLidMap _map;

    std::unique_ptr<search::IGidToLidMapper> getMapper() const override {
        return std::make_unique<MockGidToLidMapper>(_map);
    }
};
// FIXME Okay dupe stuff ended!

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
    std::shared_ptr<AttributeVector> target_vector;
    std::shared_ptr<ReferenceAttribute> ref_attr;
    std::shared_ptr<ImportedAttributeVector> imported_attribute;
    std::shared_ptr<MockGidToLidMapperFactory> mapper_factory;

    Fixture()
        : target_vector(create_single_attribute<IntegerAttribute>(BasicType::INT32)),
          ref_attr(create_reference_attribute()),
          imported_attribute(create_attribute_vector_from_members()),
          mapper_factory(std::make_shared<MockGidToLidMapperFactory>())
    {
        ref_attr->setGidToLidMapperFactory(mapper_factory);
    }

    void map_reference(DocId from_lid, GlobalId via_gid, DocId to_lid) {
        assert(from_lid < ref_attr->getNumDocs());
        ref_attr->update(from_lid, via_gid);
        ref_attr->commit();
        mapper_factory->_map[via_gid] = to_lid;
    }

    std::shared_ptr<ImportedAttributeVector> create_attribute_vector_from_members(vespalib::stringref name = "imported") {
        return std::make_shared<ImportedAttributeVector>(name, ref_attr, target_vector);
    }

    template <typename AttrVecType>
    std::shared_ptr<AttrVecType> target_vector_as() {
        auto ptr = std::dynamic_pointer_cast<AttrVecType>(target_vector);
        assert(ptr.get() != nullptr);
        return ptr;
    }

    void reset_with_new_target_vector(std::shared_ptr<AttributeVector> new_target) {
        target_vector = std::move(new_target);
        imported_attribute = create_attribute_vector_from_members();
    }

    template <typename ValueType>
    struct LidToLidMapping {
        DocId     _from_lid;
        GlobalId  _via_gid;
        DocId     _to_lid;
        ValueType _value_in_target_vector;

        LidToLidMapping(DocId from_lid,
                        GlobalId via_gid,
                        DocId to_lid,
                        ValueType value_in_target_vector)
            : _from_lid(from_lid),
              _via_gid(via_gid),
              _to_lid(to_lid),
              _value_in_target_vector(std::move(value_in_target_vector))
        {}
    };

    void set_up_attribute_vectors_before_adding_mappings() {
        // Make a sneaky assumption that no tests try to use a lid > 9
        add_n_docs_with_undefined_values(*ref_attr, 10);
        add_n_docs_with_undefined_values(*target_vector, 10);
    }

    // TODO de-dupe! only target type and update logic differs!
    template <typename AttrVecType, typename ValueType>
    void reset_with_single_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<ValueType>>& mappings) {
        reset_with_new_target_vector(create_single_attribute<AttrVecType>(type));
        set_up_attribute_vectors_before_adding_mappings();
        auto subtyped_target = target_vector_as<AttrVecType>();
        for (auto& m : mappings) {
            map_reference(m._from_lid, m._via_gid, m._to_lid);
            ASSERT_TRUE(subtyped_target->update(m._to_lid, m._value_in_target_vector));
        }
        subtyped_target->commit();
    }

    template <typename AttrVecType, typename ValueType>
    void reset_with_array_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<ValueType>>> &mappings) {
        reset_with_new_target_vector(create_array_attribute<AttrVecType>(type));
        set_up_attribute_vectors_before_adding_mappings();
        auto subtyped_target = target_vector_as<AttrVecType>();
        constexpr uint32_t weight = 1;
        for (auto& m : mappings) {
            map_reference(m._from_lid, m._via_gid, m._to_lid);
            for (const auto& v : m._value_in_target_vector) {
                ASSERT_TRUE(subtyped_target->append(m._to_lid, v, weight));
            }
        }
        subtyped_target->commit();
    }

    template <typename AttrVecType, typename WeightedValueType>
    void reset_with_wset_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<WeightedValueType>>> &mappings) {
        reset_with_new_target_vector(create_wset_attribute<AttrVecType>(type));
        set_up_attribute_vectors_before_adding_mappings();
        auto subtyped_target = target_vector_as<AttrVecType>();
        for (auto& m : mappings) {
            map_reference(m._from_lid, m._via_gid, m._to_lid);
            for (const auto& v : m._value_in_target_vector) {
                ASSERT_TRUE(subtyped_target->append(m._to_lid, v.value(), v.weight()));
            }
        }
        subtyped_target->commit();
    }
};

template <typename AttrValueType, typename PredicateType>
void assert_multi_value_matches(const Fixture& f,
                                DocId lid,
                                const std::vector<AttrValueType>& expected,
                                PredicateType predicate) {
    AttributeContent<AttrValueType> content;
    content.fill(*f.imported_attribute, lid);
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
        Fixture &f,
        BasicType type,
        const std::vector<Fixture::LidToLidMapping<std::vector<ValueType>>> &mappings) {
    f.reset_with_array_value_reference_mappings<AttrVecType, ValueType>(type, mappings);
}

template <typename AttrVecType, typename WeightedValueType>
void reset_with_wset_value_reference_mappings(
        Fixture &f,
        BasicType type,
        const std::vector<Fixture::LidToLidMapping<std::vector<WeightedValueType>>> &mappings) {
    f.reset_with_wset_value_reference_mappings<AttrVecType, WeightedValueType>(type, mappings);
}

// TODO or is it?
TEST_F("getName() is equal to name given during construction", Fixture) {
    auto attr = f.create_attribute_vector_from_members("coolvector");
    EXPECT_EQUAL("coolvector", attr->getName());
}

TEST_F("getNumDocs() returns number of documents in reference attribute vector", Fixture) {
    add_n_docs_with_undefined_values(*f.ref_attr, 42);
    EXPECT_EQUAL(42, f.imported_attribute->getNumDocs());
}

TEST_F("Collection type is inherited from target attribute", Fixture) {
    EXPECT_EQUAL(CollectionType::SINGLE, f.imported_attribute->getCollectionType());
    f.reset_with_new_target_vector(create_array_attribute<IntegerAttribute>(BasicType::INT32));
    EXPECT_EQUAL(CollectionType::ARRAY, f.imported_attribute->getCollectionType());
}

TEST_F("getBasicType() returns target vector basic type", Fixture) {
    f.reset_with_new_target_vector(create_single_attribute<IntegerAttribute>(BasicType::INT64));
    EXPECT_EQUAL(BasicType::INT64, f.imported_attribute->getBasicType());
    f.reset_with_new_target_vector(create_single_attribute<FloatingPointAttribute>(BasicType::DOUBLE));
    EXPECT_EQUAL(BasicType::DOUBLE, f.imported_attribute->getBasicType());
}

TEST_F("Single-valued integer attribute values can be retrieved via reference", Fixture) {
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(3), dummy_gid(7), DocId(7), 5678}});

    EXPECT_EQUAL(1234, f.imported_attribute->getInt(DocId(1)));
    EXPECT_EQUAL(5678, f.imported_attribute->getInt(DocId(3)));
}

TEST_F("Multi-valued integer attribute values can be retrieved via reference", Fixture) {
    reset_with_array_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), {{1234}}},
             {DocId(3), dummy_gid(7), DocId(7), {{5678, 9876, 555, 777}}},
             {DocId(5), dummy_gid(8), DocId(8), {{}}}});
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(1), {{1234}});
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(3), {{5678, 9876, 555, 777}});
    assert_multi_value_matches<IAttributeVector::largeint_t>(f, DocId(5), {{}});
}

TEST_F("Weighted integer attribute values can be retrieved via reference", Fixture) {
    reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), {{WeightedInt(1234, 5)}}},
             {DocId(3), dummy_gid(7), DocId(7), {{WeightedInt(5678, 10), WeightedInt(9876, 20)}}}});
    assert_multi_value_matches<WeightedInt>(f, DocId(1), {{WeightedInt(1234, 5)}});
    assert_multi_value_matches<WeightedInt>(f, DocId(3), {{WeightedInt(5678, 10), WeightedInt(9876, 20)}});
}

TEST_F("LID with not present GID reference mapping returns default value", Fixture) {
    add_n_docs_with_undefined_values(*f.ref_attr, 2);
    EXPECT_EQUAL(f.target_vector->getInt(DocId(0)), // Implicit default undefined value
                 f.imported_attribute->getInt(DocId(1)));
}

TEST_F("Singled-valued floating point attribute values can be retrieved via reference", Fixture) {
    reset_with_single_value_reference_mappings<FloatingPointAttribute, float>(
            f, BasicType::FLOAT,
            {{DocId(2), dummy_gid(3), DocId(3), 10.5f},
             {DocId(4), dummy_gid(8), DocId(8), 3.14f}});

    EXPECT_EQUAL(10.5f, f.imported_attribute->getFloat(DocId(2)));
    EXPECT_EQUAL(3.14f, f.imported_attribute->getFloat(DocId(4)));
}

TEST_F("Multi-valued floating point attribute values can be retrieved via reference", Fixture) {
    reset_with_array_value_reference_mappings<FloatingPointAttribute, double>(
            f, BasicType::DOUBLE,
            {{DocId(2), dummy_gid(3), DocId(3), {{3.14, 133.7}}},
             {DocId(4), dummy_gid(7), DocId(7), {{5.5,  6.5, 10.5}}}});
    assert_multi_value_matches<double>(f, DocId(2), {{3.14, 133.7}});
    assert_multi_value_matches<double>(f, DocId(4), {{5.5, 6.5, 10.5}});
}

TEST_F("Weighted floating point attribute values can be retrieved via reference", Fixture) {
    reset_with_wset_value_reference_mappings<FloatingPointAttribute, WeightedFloat>(
            f, BasicType::DOUBLE,
            {{DocId(1), dummy_gid(3), DocId(3), {{WeightedFloat(3.14, 5)}}},
             {DocId(3), dummy_gid(7), DocId(7), {{WeightedFloat(5.5, 7), WeightedFloat(10.25, 42)}}}});
    assert_multi_value_matches<WeightedFloat>(f, DocId(1), {{WeightedFloat(3.14, 5)}});
    assert_multi_value_matches<WeightedFloat>(f, DocId(3), {{WeightedFloat(5.5, 7), WeightedFloat(10.25, 42)}});
}

// TODO use string attr for enum test; getEnum, findEnum
TEST_F("Single-valued string attribute values can be retrieved via reference", Fixture) {
     reset_with_single_value_reference_mappings<StringAttribute, const char*>(
             f, BasicType::STRING,
             {{DocId(2), dummy_gid(3), DocId(3), "foo"},
              {DocId(4), dummy_gid(7), DocId(7), "bar"}});

    char buf[64];
    EXPECT_EQUAL(vespalib::string("foo"), f.imported_attribute->getString(DocId(2), buf, sizeof(buf)));
    //EXPECT_EQUAL(vespalib::string("foo"), buf); // TODO borked?
    EXPECT_EQUAL(vespalib::string("bar"), f.imported_attribute->getString(DocId(4), buf, sizeof(buf)));
    //EXPECT_EQUAL(vespalib::string("bar"), buf); // TODO borked?
}

TEST_F("Multi-valued string attribute values can be retrieved via reference", Fixture) {
    reset_with_array_value_reference_mappings<StringAttribute, const char *>(
            f, BasicType::STRING,
            {{DocId(2), dummy_gid(3), DocId(3), {{"foo", "bar"}}},
             {DocId(4), dummy_gid(7), DocId(7), {{"baz", "bjarne", "betjent"}}}});
    const auto string_eq = [](const char* lhs, const char* rhs) noexcept {
        return strcmp(lhs, rhs) == 0;
    };
    assert_multi_value_matches<const char*>(f, DocId(2), {{"foo", "bar"}}, string_eq);
    assert_multi_value_matches<const char*>(f, DocId(4), {{"baz", "bjarne", "betjent"}}, string_eq);
}

/*
 * Test list:
 *  - multi-get() calls
 *  - test serializeFor<X>Sort using NotImplementedAttribute subclass
 */

}
}

TEST_MAIN() { TEST_RUN_ALL(); }