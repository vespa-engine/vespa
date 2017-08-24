// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchcommon/attribute/search_context_params.h>

namespace search {
namespace attribute {


template <bool useReadGuard = false>
struct FixtureBase : public ImportedAttributeFixture
{
    FixtureBase()
        : ImportedAttributeFixture()
    {
    }

    std::shared_ptr<ImportedAttributeVector> get_imported_attr() {
        if (useReadGuard) {
            return imported_attr->makeReadGuard(false);
        } else {
            return imported_attr;
        }
    }
};

using Fixture = FixtureBase<false>;
using ReadGuardFixture = FixtureBase<true>;

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
    EXPECT_EQUAL(42u, f.imported_attr->getNumDocs());
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

TEST_F("makeReadGuard(false) acquires guards on both target and reference attributes", Fixture) {
    add_n_docs_with_undefined_values(*f.reference_attr, 2);
    add_n_docs_with_undefined_values(*f.target_attr, 2);
    // Now at generation 1 in both attributes.
    {
        auto guard = f.imported_attr->makeReadGuard(false);
        add_n_docs_with_undefined_values(*f.reference_attr, 1);
        add_n_docs_with_undefined_values(*f.target_attr, 1);
        
        EXPECT_EQUAL(2u, f.target_attr->getCurrentGeneration());
        EXPECT_EQUAL(2u, f.reference_attr->getCurrentGeneration());
        // Should still be holding guard for first generation of writes for both attributes
        EXPECT_EQUAL(1u, f.target_attr->getFirstUsedGeneration());
        EXPECT_EQUAL(1u, f.reference_attr->getFirstUsedGeneration());
    }
    // Force a generation handler update
    add_n_docs_with_undefined_values(*f.reference_attr, 1);
    add_n_docs_with_undefined_values(*f.target_attr, 1);
    EXPECT_EQUAL(3u, f.target_attr->getFirstUsedGeneration());
    EXPECT_EQUAL(3u, f.reference_attr->getFirstUsedGeneration());
}

TEST_F("makeReadGuard(true) acquires enum guard on target and regular guard on reference attribute", Fixture) {
    f.reset_with_new_target_attr(create_single_attribute<StringAttribute>(BasicType::STRING));
    add_n_docs_with_undefined_values(*f.reference_attr, 2);
    add_n_docs_with_undefined_values(*f.target_attr, 2);
    {
        auto guard = f.imported_attr->makeReadGuard(true);
        add_n_docs_with_undefined_values(*f.target_attr, 1);
        add_n_docs_with_undefined_values(*f.reference_attr, 1);

        EXPECT_EQUAL(5u, f.target_attr->getCurrentGeneration());
        EXPECT_EQUAL(2u, f.reference_attr->getCurrentGeneration());

        EXPECT_EQUAL(3u, f.target_attr->getFirstUsedGeneration());
        EXPECT_EQUAL(1u, f.reference_attr->getFirstUsedGeneration());
        EXPECT_TRUE(has_active_enum_guards(*f.target_attr));
    }
    // Force a generation handler update
    add_n_docs_with_undefined_values(*f.reference_attr, 1);
    add_n_docs_with_undefined_values(*f.target_attr, 1);
    EXPECT_EQUAL(7u, f.target_attr->getFirstUsedGeneration());
    EXPECT_EQUAL(3u, f.reference_attr->getFirstUsedGeneration());
    EXPECT_FALSE(has_active_enum_guards(*f.target_attr));
}

template <typename Fixture>
void
checkSingleInt()
{
    Fixture f;
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(3), dummy_gid(7), DocId(7), 5678}});

    EXPECT_EQUAL(1234, f.get_imported_attr()->getInt(DocId(1)));
    EXPECT_EQUAL(5678, f.get_imported_attr()->getInt(DocId(3)));
}

TEST("Single-valued integer attribute values can be retrieved via reference") {
    TEST_DO(checkSingleInt<Fixture>());
    TEST_DO(checkSingleInt<ReadGuardFixture>());
}

template <typename Fixture>
void
checkSingleMappedValueCount()
{
    Fixture f;
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32, {{DocId(1), dummy_gid(3), DocId(3), 1234}});
    EXPECT_EQUAL(1u, f.get_imported_attr()->getValueCount(DocId(1)));
}

TEST("getValueCount() is 1 for mapped single value attribute") {
    TEST_DO(checkSingleMappedValueCount<Fixture>());
    TEST_DO(checkSingleMappedValueCount<ReadGuardFixture>());
}

template <typename Fixture>
void
checkSingleNonMappedValueCount()
{
    Fixture f;
    add_n_docs_with_undefined_values(*f.reference_attr, 3);
    EXPECT_EQUAL(0u, f.get_imported_attr()->getValueCount(DocId(2)));
}

TEST("getValueCount() is 0 for non-mapped single value attribute") {
    TEST_DO(checkSingleNonMappedValueCount<Fixture>());
    TEST_DO(checkSingleNonMappedValueCount<ReadGuardFixture>());
}

TEST_F("getMaxValueCount() is 1 for single value attribute vectors", Fixture) {
    EXPECT_EQUAL(1u, f.imported_attr->getMaxValueCount());
}

TEST_F("getFixedWidth() is inherited from target attribute vector", Fixture) {
    EXPECT_EQUAL(f.target_attr->getFixedWidth(),
                 f.imported_attr->getFixedWidth());
}

TEST_F("asDocumentWeightAttribute() returns nullptr", Fixture) {
    EXPECT_TRUE(f.imported_attr->asDocumentWeightAttribute() == nullptr);
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

template <class Fixture>
void
checkLidWithNotPresentGid()
{
    Fixture f;
    f.target_attr->addReservedDoc();
    add_n_docs_with_undefined_values(*f.reference_attr, 2);
    EXPECT_EQUAL(f.target_attr->getInt(DocId(0)), // Implicit default undefined value
                 f.get_imported_attr()->getInt(DocId(1)));
}

TEST("LID with not present GID reference mapping returns default value") {
    TEST_DO(checkLidWithNotPresentGid<Fixture>());
    TEST_DO(checkLidWithNotPresentGid<ReadGuardFixture>());
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

template <bool useReadGuard = false>
struct SingleStringAttrFixtureBase : FixtureBase<useReadGuard> {
    SingleStringAttrFixtureBase() : FixtureBase<useReadGuard>() {
        setup();
    }

    void setup() {
        this->template reset_with_single_value_reference_mappings<StringAttribute, const char*>(
                BasicType::STRING,
                {{DocId(2), dummy_gid(3), DocId(3), "foo"},
                 {DocId(4), dummy_gid(7), DocId(7), "bar"}});
    }
};

using SingleStringAttrFixture = SingleStringAttrFixtureBase<false>;
using ReadGuardSingleStringAttrFixture = SingleStringAttrFixtureBase<true>;

template <class Fixture>
void
checkSingleString()
{
    Fixture f;
    char buf[64];
    EXPECT_EQUAL(vespalib::string("foo"), f.get_imported_attr()->getString(DocId(2), buf, sizeof(buf)));
    EXPECT_EQUAL(vespalib::string("bar"), f.get_imported_attr()->getString(DocId(4), buf, sizeof(buf)));
}


TEST("Single-valued string attribute values can be retrieved via reference") {
    TEST_DO(checkSingleString<SingleStringAttrFixture>());
    TEST_DO(checkSingleString<ReadGuardSingleStringAttrFixture>());
}

template <class Fixture>
void
checkSingleStringEnum()
{
    Fixture f;
    EXPECT_EQUAL(f.target_attr->getEnum(DocId(3)),
                 f.get_imported_attr()->getEnum(DocId(2)));
    EXPECT_EQUAL(f.target_attr->getEnum(DocId(7)),
                 f.get_imported_attr()->getEnum(DocId(4)));
}

TEST("getEnum() returns target vector enum via reference") {
    TEST_DO(checkSingleStringEnum<SingleStringAttrFixture>());
    TEST_DO(checkSingleStringEnum<ReadGuardSingleStringAttrFixture>());
}

TEST_F("findEnum() returns target vector enum via reference", SingleStringAttrFixture) {
    EnumHandle expected_handle{};
    ASSERT_TRUE(f.target_attr->findEnum("foo", expected_handle));
    EnumHandle actual_handle{};
    ASSERT_TRUE(f.imported_attr->findEnum("foo", actual_handle));
    EXPECT_EQUAL(expected_handle, actual_handle);
}

// Note: assumes that fixture has set up a string enum of value "foo" in target attribute
template <typename FixtureType>
void verify_get_string_from_enum_is_mapped(FixtureType& f) {
    EnumHandle handle{};
    ASSERT_TRUE(f.target_attr->findEnum("foo", handle));
    const char* from_enum = f.get_imported_attr()->getStringFromEnum(handle);
    ASSERT_TRUE(from_enum != nullptr);
    EXPECT_EQUAL(vespalib::string("foo"), vespalib::string(from_enum));
}

TEST_F("Single-value getStringFromEnum() returns string enum is mapped to", SingleStringAttrFixture) {
    verify_get_string_from_enum_is_mapped(f);
}

TEST_F("hasEnum() is true for enum target attribute vector", SingleStringAttrFixture) {
    EXPECT_TRUE(f.imported_attr->hasEnum());
}

TEST_F("createSearchContext() returns an imported search context", SingleStringAttrFixture) {
    auto ctx = f.imported_attr->createSearchContext(word_term("bar"), SearchContextParams());
    ASSERT_TRUE(ctx.get() != nullptr);
    fef::TermFieldMatchData match;
    // Iterator specifics are tested in imported_search_context_test, so just make sure
    // we get the expected iterator functionality. In this case, a non-strict iterator.
    auto iter = ctx->createIterator(&match, false);
    iter->initRange(1, f.imported_attr->getNumDocs());
    EXPECT_FALSE(iter->seek(DocId(1)));
    EXPECT_FALSE(iter->seek(DocId(2)));
    EXPECT_FALSE(iter->seek(DocId(3)));
    EXPECT_TRUE(iter->seek(DocId(4)));
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

TEST_F("Multi-value getStringFromEnum() returns string enum is mapped to", MultiStringAttrFixture) {
    verify_get_string_from_enum_is_mapped(f);
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

TEST_F("Weighted set getStringFromEnum() returns string enum is mapped to", WeightedMultiStringAttrFixture) {
    verify_get_string_from_enum_is_mapped(f);
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
            : NotImplementedAttribute("mock", Config(BasicType::STRING)) {
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

template <typename BaseFixture>
struct SerializeFixture : BaseFixture {
    std::shared_ptr<MockAttributeVector> mock_target;
    MockBlobConverter mock_converter;

    SerializeFixture() : mock_target(std::make_shared<MockAttributeVector>()) {
        this->reset_with_new_target_attr(mock_target);
        mock_target->setCommittedDocIdLimit(8); // Target LID of 7 is highest used by ref attribute. Limit is +1.
    }
    ~SerializeFixture() override;
};

template <typename BaseFixture>
SerializeFixture<BaseFixture>::~SerializeFixture() {}

template <typename FixtureT>
void check_onSerializeForAscendingSort_is_forwarded_with_remapped_lid() {
    FixtureT f;
    int dummy_tag;
    void* ser_to = &dummy_tag;
    EXPECT_EQUAL(f.mock_target->_return_value,
                 f.get_imported_attr()->serializeForAscendingSort(
                         DocId(4), ser_to, 777, &f.mock_converter)); // child lid 4 -> parent lid 7
    EXPECT_TRUE(f.mock_target->_ascending_called);
    EXPECT_EQUAL(DocId(7), f.mock_target->_doc_id);
    EXPECT_EQUAL(ser_to, f.mock_target->_ser_to);
    EXPECT_EQUAL(777, f.mock_target->_available);
    EXPECT_EQUAL(&f.mock_converter, f.mock_target->_bc);
}

TEST("onSerializeForAscendingSort() is forwarded with remapped LID to target vector") {
    TEST_DO(check_onSerializeForAscendingSort_is_forwarded_with_remapped_lid<
                    SerializeFixture<SingleStringAttrFixture>>());
    TEST_DO(check_onSerializeForAscendingSort_is_forwarded_with_remapped_lid<
                    SerializeFixture<ReadGuardSingleStringAttrFixture>>());
}

template <typename FixtureT>
void check_onSerializeForDescendingSort_is_forwarded_with_remapped_lid() {
    FixtureT f;
    int dummy_tag;
    void* ser_to = &dummy_tag;
    EXPECT_EQUAL(f.mock_target->_return_value,
                 f.get_imported_attr()->serializeForDescendingSort(
                         DocId(2), ser_to, 555, &f.mock_converter)); // child lid 2 -> parent lid 3
    EXPECT_TRUE(f.mock_target->_descending_called);
    EXPECT_EQUAL(DocId(3), f.mock_target->_doc_id);
    EXPECT_EQUAL(ser_to, f.mock_target->_ser_to);
    EXPECT_EQUAL(555, f.mock_target->_available);
    EXPECT_EQUAL(&f.mock_converter, f.mock_target->_bc);
}

TEST("onSerializeForDescendingSort() is forwarded with remapped LID to target vector") {
    TEST_DO(check_onSerializeForDescendingSort_is_forwarded_with_remapped_lid<
                    SerializeFixture<SingleStringAttrFixture>>());
    TEST_DO(check_onSerializeForDescendingSort_is_forwarded_with_remapped_lid<
                    SerializeFixture<ReadGuardSingleStringAttrFixture>>());
}

} // attribute
} // search

TEST_MAIN() { TEST_RUN_ALL(); }
