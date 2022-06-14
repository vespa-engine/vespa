// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/attribute/multi_value_traits.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>

using document::GlobalId;
using document::DocumentId;

namespace search::attribute {

using test::MockGidToLidMapperFactory;

namespace {

GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:music::1");
vespalib::string doc2("id:test:music::2");

struct MyGidToLidMapperFactory : public MockGidToLidMapperFactory
{
    MyGidToLidMapperFactory()
        : MockGidToLidMapperFactory()
    {
        _map.insert({toGid(doc1), 1});
        _map.insert({toGid(doc2), 2});
    }
};

struct MockReadGuard : public IDocumentMetaStoreContext::IReadGuard {
    virtual const search::IDocumentMetaStore &get() const override {
        search::IDocumentMetaStore *nullStore = nullptr;
        return static_cast<search::IDocumentMetaStore &>(*nullStore);
    }
};

struct MockDocumentMetaStoreContext : public IDocumentMetaStoreContext
{
    std::unique_ptr<IReadGuard> getReadGuard() const override;
};

std::unique_ptr<IDocumentMetaStoreContext::IReadGuard>
MockDocumentMetaStoreContext::getReadGuard() const
{
    return std::make_unique<MockReadGuard>();
}


std::shared_ptr<ReferenceAttribute>
create_reference_attribute(const vespalib::string &name, const std::shared_ptr<IGidToLidMapperFactory> gid_to_lid_mapper_factory)
{
    auto attr = std::make_shared<ReferenceAttribute>(name, Config(BasicType::REFERENCE));
    attr->addReservedDoc();
    while (attr->getNumDocs() < 20u) {
        uint32_t doc_id = 0;
        attr->addDoc(doc_id);
        EXPECT_NE(0u, doc_id);
    }
    attr->update(4, toGid(doc1));
    attr->update(11, toGid(doc2));
    attr->setGidToLidMapperFactory(gid_to_lid_mapper_factory);
    attr->populateTargetLids({});
    return attr;
}

}

class TestParam {
    BasicType _basic_type;

public:
    TestParam(BasicType basic_type_in)
        : _basic_type(basic_type_in)
    {
    }

    BasicType basic_type() const noexcept { return _basic_type; }
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param.basic_type().asString();
    return os;
}

class MultiValueReadViewTest : public ::testing::TestWithParam<TestParam>
{
protected:
    std::shared_ptr<IGidToLidMapperFactory> _gid_to_lid_mapper_factory;
    std::shared_ptr<ReferenceAttribute> _reference_attribute;
    MultiValueReadViewTest()
        : ::testing::TestWithParam<TestParam>(),
          _gid_to_lid_mapper_factory(std::make_shared<MyGidToLidMapperFactory>()),
          _reference_attribute(create_reference_attribute("ref", _gid_to_lid_mapper_factory))
    {
    }
    ~MultiValueReadViewTest() override = default;

    template <typename AttributeBaseType, typename BaseType>
    void populate_helper(AttributeVector& attr, const std::vector<BaseType>& values);
    void populate(AttributeVector& attr);
    template <typename MultiValueType>
    void check_values_helper(const IAttributeVector &attr, const std::vector<multivalue::ValueType_t<MultiValueType>>& exp_values);
    template <typename BasicType>
    void check_integer_values(const IAttributeVector &attr);
    template <typename BasicType>
    void check_floating_point_values(const IAttributeVector &attr);
    void check_string_values(const IAttributeVector &attr);
    void check_values(const IAttributeVector& attr);
    std::shared_ptr<AttributeVector> make_attribute(CollectionType collection_type, bool fast_search);
    std::shared_ptr<ReadableAttributeVector> make_imported_attribute(std::shared_ptr<AttributeVector> target);
    std::shared_ptr<AttributeVector> make_extendable_attribute(CollectionType collection_type);
    void test_normal_attribute_vector(CollectionType collection_type, bool fast_search);
    void test_imported_attribute_vector(CollectionType collection_type, bool fast_search);
    void test_extendable_attribute_vector(CollectionType collection_type);
};

template <typename AttributeBaseType, typename BaseType>
void
MultiValueReadViewTest::populate_helper(AttributeVector& attr, const std::vector<BaseType>& values)
{
    auto extend_interface = attr.getExtendInterface();
    if (extend_interface == nullptr) {
        attr.addReservedDoc();
    } else {
        uint32_t doc_id = 0;
        EXPECT_TRUE(attr.addDoc(doc_id));
        EXPECT_EQ(0u, doc_id);
    }
    uint32_t doc_id = 0;
    attr.addDoc(doc_id);
    EXPECT_EQ(1u, doc_id);
    if (extend_interface == nullptr) {
        attr.clearDoc(doc_id);
    }
    attr.addDoc(doc_id);
    EXPECT_EQ(2u, doc_id);
    if (extend_interface == nullptr) {
        attr.clearDoc(doc_id);
        auto& spec_attr = dynamic_cast<AttributeBaseType&>(attr);
        EXPECT_TRUE(spec_attr.append(doc_id, values[0], 2));
        EXPECT_TRUE(spec_attr.append(doc_id, values[1], 7));
        attr.commit();
    } else {
        EXPECT_TRUE(extend_interface->add(values[0], 2));
        EXPECT_TRUE(extend_interface->add(values[1], 7));
    }
}

void
MultiValueReadViewTest::populate(AttributeVector& attr)
{
    switch (attr.getConfig().basicType().type()) {
    case BasicType::Type::INT8:
    case BasicType::Type::INT16:
    case BasicType::Type::INT32:
    case BasicType::Type::INT64:
        populate_helper<IntegerAttribute, int64_t>(attr, {42, 44});
        break;
    case BasicType::Type::FLOAT:
    case BasicType::Type::DOUBLE:
        populate_helper<FloatingPointAttribute, double>(attr, {42.0, 44.0});
        break;
    case BasicType::Type::STRING:
        populate_helper<StringAttribute, const char*>(attr, {"42", "44"});
        break;
    default:
        FAIL() << "Cannot populate attribute vector";
    }
}

namespace {

template <typename BasicType>
struct CompareValues
{
    bool operator()(const BasicType &lhs, const BasicType &rhs) const { return lhs < rhs; }
    bool operator()(const multivalue::WeightedValue<BasicType>& lhs, const multivalue::WeightedValue<BasicType>& rhs) const { return lhs.value() < rhs.value(); }
    bool equal(const BasicType &lhs, const BasicType &rhs) const { return lhs == rhs; }
    bool equal(const multivalue::WeightedValue<BasicType>& lhs, const multivalue::WeightedValue<BasicType>& rhs) const { return lhs.value() == rhs.value(); }
};

template <>
struct CompareValues<const char *>
{
    bool operator()(const char *lhs, const char *rhs) const { return strcmp(lhs, rhs) < 0; }
    bool operator()(const multivalue::WeightedValue<const char *>& lhs, const multivalue::WeightedValue<const char *>& rhs) const { return strcmp(lhs.value(), rhs.value()) < 0; }
    bool equal(const char *lhs, const char *rhs) const { return strcmp(lhs, rhs) == 0; }
    bool equal(const multivalue::WeightedValue<const char *>& lhs, const multivalue::WeightedValue<const char *>& rhs) const { return strcmp(lhs.value(), rhs.value()) == 0; }
};

}

template <typename MultiValueType>
void
MultiValueReadViewTest::check_values_helper(const IAttributeVector &attr, const std::vector<multivalue::ValueType_t<MultiValueType>>& exp_values)
{
    vespalib::Stash stash;
    auto mv_attr = attr.as_multi_value_attribute();
    EXPECT_NE(nullptr, mv_attr);
    auto read_view = mv_attr->make_read_view(IMultiValueAttribute::MultiValueTag<MultiValueType>(), stash);
    EXPECT_NE(nullptr, read_view);
    bool is_imported = attr.isImported();
    auto values = read_view->get_values(is_imported ? 4 : 1);
    EXPECT_TRUE(values.empty());
    values = read_view->get_values(is_imported ? 11 : 2);
    std::vector<MultiValueType> values_copy(values.begin(), values.end());
    bool was_array = true;
    CompareValues<multivalue::ValueType_t<MultiValueType>> compare_values;
    if (attr.getCollectionType() == CollectionType::Type::WSET) {
        std::sort(values_copy.begin(), values_copy.end(), compare_values);
        was_array = false;
    }
    EXPECT_EQ(2u, values_copy.size());
    if constexpr (multivalue::is_WeightedValue_v<MultiValueType>) {
        EXPECT_TRUE(compare_values.equal(exp_values[0], values_copy[0].value()));
        EXPECT_EQ(was_array ? 1 : 2, values_copy[0].weight());
        EXPECT_TRUE(compare_values.equal(exp_values[1], values_copy[1].value()));
        EXPECT_EQ(was_array ? 1 : 7, values_copy[1].weight());
    } else {
        EXPECT_TRUE(compare_values.equal(exp_values[0], values_copy[0]));
        EXPECT_TRUE(compare_values.equal(exp_values[1], values_copy[1]));
    }
}

template <typename BasicType>
void
MultiValueReadViewTest::check_integer_values(const IAttributeVector &attr)
{
    std::vector<BasicType> exp_values{42, 44};
    check_values_helper<BasicType>(attr, exp_values);
    check_values_helper<multivalue::WeightedValue<BasicType>>(attr, exp_values);
}

template <typename BasicType>
void
MultiValueReadViewTest::check_floating_point_values(const IAttributeVector &attr)
{
    std::vector<BasicType> exp_values{42.0, 44.0};
    check_values_helper<BasicType>(attr, exp_values);
    check_values_helper<multivalue::WeightedValue<BasicType>>(attr, exp_values);
}

void
MultiValueReadViewTest::check_string_values(const IAttributeVector &attr)
{
    std::vector<const char *> exp_values{"42", "44"};
    check_values_helper<const char *>(attr, exp_values);
    check_values_helper<multivalue::WeightedValue<const char *>>(attr, exp_values);
}

void
MultiValueReadViewTest::check_values(const IAttributeVector& attr)
{
    switch (attr.getBasicType()) {
    case BasicType::Type::INT8:
        check_integer_values<int8_t>(attr);
        break;
    case BasicType::Type::INT16:
        check_integer_values<int16_t>(attr);
        break;
    case BasicType::Type::INT32:
        check_integer_values<int32_t>(attr);
        break;
    case BasicType::Type::INT64:
        check_integer_values<int64_t>(attr);
        break;
    case BasicType::Type::FLOAT:
        check_floating_point_values<float>(attr);
        break;
    case BasicType::Type::DOUBLE:
        check_floating_point_values<double>(attr);
        break;
    case BasicType::Type::STRING:
        check_string_values(attr);
        break;
    default:
        FAIL() << "Cannot check values in attribute vector";
    }
}

std::shared_ptr<AttributeVector>
MultiValueReadViewTest::make_attribute(CollectionType collection_type, bool fast_search)
{
    auto param = GetParam();
    Config config(param.basic_type(), collection_type);
    config.setFastSearch(fast_search);
    auto attr = AttributeFactory::createAttribute("attr", config);
    return attr;
}

std::shared_ptr<ReadableAttributeVector>
MultiValueReadViewTest::make_imported_attribute(std::shared_ptr<AttributeVector> target)
{
    return ImportedAttributeVectorFactory::create("imported",
                                                  _reference_attribute,
                                                  std::make_shared<MockDocumentMetaStoreContext>(),
                                                  target,
                                                  std::make_shared<MockDocumentMetaStoreContext>(),
                                                  false);
}

std::shared_ptr<AttributeVector>
MultiValueReadViewTest::make_extendable_attribute(CollectionType collection_type)
{
    vespalib::string name("attr");
    // Match strategy in streaming visitor
    switch (collection_type.type()) {
    case CollectionType::Type::ARRAY:
        switch (GetParam().basic_type().type()) {
        case BasicType::Type::INT8:
        case BasicType::Type::INT16:
        case BasicType::Type::INT32:
        case BasicType::Type::INT64:
            return std::make_shared<MultiIntegerExtAttribute>(name);
        case BasicType::Type::FLOAT:
        case BasicType::Type::DOUBLE:
            return std::make_shared<MultiFloatExtAttribute>(name);
        case BasicType::Type::STRING:
            return std::make_shared<MultiStringExtAttribute>(name);
        default:
            ;
        }
        break;
    case CollectionType::Type::WSET:
        switch (GetParam().basic_type().type()) {
        case BasicType::Type::INT8:
        case BasicType::Type::INT16:
        case BasicType::Type::INT32:
        case BasicType::Type::INT64:
            return std::make_shared<WeightedSetIntegerExtAttribute>(name);
        case BasicType::Type::FLOAT:
        case BasicType::Type::DOUBLE:
            return std::make_shared<WeightedSetFloatExtAttribute>(name);
        case BasicType::Type::STRING:
            return std::make_shared<WeightedSetStringExtAttribute>(name);
        default:
            ;
        }
        break;
    default:
        ;
    }
    return {};
}

void
MultiValueReadViewTest::test_normal_attribute_vector(CollectionType collection_type, bool fast_search)
{
    auto attr = make_attribute(collection_type, fast_search);
    populate(*attr);
    check_values(*attr);
}

void
MultiValueReadViewTest::test_imported_attribute_vector(CollectionType collection_type, bool fast_search)
{
    auto attr = make_attribute(collection_type, fast_search);
    populate(*attr);
    auto imported_attr = make_imported_attribute(attr);
    auto guard = imported_attr->makeReadGuard(false);
    check_values(**guard);
}

void
MultiValueReadViewTest::test_extendable_attribute_vector(CollectionType collection_type)
{
    auto attr = make_extendable_attribute(collection_type);
    if (attr == nullptr) {
        FAIL() << "Cannot create extend attribute";
    }
    populate(*attr);
    check_values(*attr);
}

TEST_P(MultiValueReadViewTest, test_array)
{
    test_normal_attribute_vector(CollectionType::Type::ARRAY, false);
};

TEST_P(MultiValueReadViewTest, test_enumerated_array)
{
    test_normal_attribute_vector(CollectionType::Type::ARRAY, true);
};

TEST_P(MultiValueReadViewTest, test_weighted_set)
{
    test_normal_attribute_vector(CollectionType::Type::WSET, false);
};

TEST_P(MultiValueReadViewTest, test_enumerated_weighted_set)
{
    test_normal_attribute_vector(CollectionType::Type::WSET, true);
};

TEST_P(MultiValueReadViewTest, test_imported_array)
{
    test_imported_attribute_vector(CollectionType::Type::ARRAY, false);
};

TEST_P(MultiValueReadViewTest, test_imported_enumerated_array)
{
    test_imported_attribute_vector(CollectionType::Type::ARRAY, true);
};

TEST_P(MultiValueReadViewTest, test_importe_weighted_set)
{
    test_imported_attribute_vector(CollectionType::Type::WSET, false);
};

TEST_P(MultiValueReadViewTest, test_imported_enumerated_weighted_set)
{
    test_imported_attribute_vector(CollectionType::Type::WSET, true);
};

TEST_P(MultiValueReadViewTest, test_extendable_array)
{
    test_extendable_attribute_vector(CollectionType::Type::ARRAY);
}

TEST_P(MultiValueReadViewTest, test_extendable_weighted_set)
{
    test_extendable_attribute_vector(CollectionType::Type::WSET);
}

auto test_values = ::testing::Values(TestParam(BasicType::Type::INT8),
                                     TestParam(BasicType::Type::INT16),
                                     TestParam(BasicType::Type::INT32),
                                     TestParam(BasicType::Type::INT64),
                                     TestParam(BasicType::Type::FLOAT),
                                     TestParam(BasicType::Type::DOUBLE),
                                     TestParam(BasicType::Type::STRING));

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(ReadView, MultiValueReadViewTest, test_values,testing::PrintToStringParamName());

}

GTEST_MAIN_RUN_ALL_TESTS()
