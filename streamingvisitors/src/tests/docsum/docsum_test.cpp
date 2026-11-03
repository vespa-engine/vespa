// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/i_docsum_store_document.h>
#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/vsm/docsumfilter.h>
#include <vespa/vsm/vsm/flattendocsumwriter.h>
#include <vespa/vsm/vsm/vsm-adapter.h>

using namespace document;

namespace vsm {

template <typename T> class Vector : public std::vector<T> {
public:
    Vector<T>& add(T v) {
        this->push_back(v);
        return *this;
    }
};

using StringList = Vector<std::string>;
using WeightedStringList = Vector<std::pair<std::string, int32_t>>;

class TestDocument : public vsm::Document {
private:
    std::vector<FieldValueContainer> _fields;

public:
    TestDocument(const search::DocumentIdT& docId, size_t numFields)
        : vsm::Document(docId, numFields), _fields(numFields) {}
    bool setField(FieldIdT fId, document::FieldValue::UP fv) override {
        if (fId < _fields.size()) {
            _fields[fId].reset(fv.release());
            return true;
        }
        return false;
    }
    const document::FieldValue* getField(FieldIdT fId) const override {
        if (fId < _fields.size()) {
            return _fields[fId].get();
        }
        return nullptr;
    }
};

class DocsumTest : public ::testing::Test {
protected:
    ArrayFieldValue createFieldValue(const StringList& fv);
    WeightedSetFieldValue createFieldValue(const WeightedStringList& fv);

    void assertFlattenDocsumWriter(const FieldValue& fv, const std::string& exp, const std::string& label) {
        FlattenDocsumWriter fdw;
        assertFlattenDocsumWriter(fdw, fv, exp, label);
    }
    void assertFlattenDocsumWriter(FlattenDocsumWriter& fdw, const FieldValue& fv, const std::string& exp,
                                   const std::string& label);

    DocsumTest();
    ~DocsumTest() override;
};

DocsumTest::DocsumTest() : ::testing::Test() {
}

DocsumTest::~DocsumTest() = default;

ArrayFieldValue DocsumTest::createFieldValue(const StringList& fv) {

    static ArrayDataType type(*DataType::STRING);
    ArrayFieldValue      afv(type);
    for (size_t i = 0; i < fv.size(); ++i) {
        afv.add(StringFieldValue(fv[i]));
    }
    return afv;
}

WeightedSetFieldValue DocsumTest::createFieldValue(const WeightedStringList& fv) {
    static WeightedSetDataType type(*DataType::STRING, false, false);
    WeightedSetFieldValue      wsfv(type);
    for (size_t i = 0; i < fv.size(); ++i) {
        wsfv.add(StringFieldValue(fv[i].first), fv[i].second);
    }
    return wsfv;
}

void DocsumTest::assertFlattenDocsumWriter(FlattenDocsumWriter& fdw, const FieldValue& fv, const std::string& exp,
                                           const std::string& label) {
    SCOPED_TRACE(label);
    FieldPath empty;
    fv.iterateNested(empty.getFullRange(), fdw);
    std::string actual(fdw.getResult().getBuffer(), fdw.getResult().getPos());
    EXPECT_EQ(exp, actual);
}

TEST_F(DocsumTest, flatten_docsum_writer_basic) {
    assertFlattenDocsumWriter(StringFieldValue("foo bar"), "foo bar", "string foo bar");
    assertFlattenDocsumWriter(RawFieldValue("foo bar"), "foo bar", "raw foo bar");
    assertFlattenDocsumWriter(BoolFieldValue(true), "true", "bool true");
    assertFlattenDocsumWriter(BoolFieldValue(false), "false", "bool false");
    assertFlattenDocsumWriter(LongFieldValue(123456789), "123456789", "long");
    assertFlattenDocsumWriter(createFieldValue(StringList().add("foo bar").add("baz").add(" qux ")),
                              "foo bar baz  qux ", "wset");
}

TEST_F(DocsumTest, flatten_docsum_writer_multiple_invocations) {
    FlattenDocsumWriter fdw("#");
    assertFlattenDocsumWriter(fdw, StringFieldValue("foo"), "foo", "string foo");
    assertFlattenDocsumWriter(fdw, StringFieldValue("bar"), "foo#bar", "string bar");
    fdw.clear();
    assertFlattenDocsumWriter(fdw, StringFieldValue("baz"), "baz", "string baz");
    assertFlattenDocsumWriter(fdw, StringFieldValue("qux"), "baz qux", "string qux");
}

TEST_F(DocsumTest, flatten_docsum_writer_resizing) {
    FlattenDocsumWriter fdw("#");
    EXPECT_EQ(fdw.getResult().getPos(), 0u);
    EXPECT_EQ(fdw.getResult().getLength(), 32u);
    assertFlattenDocsumWriter(fdw, StringFieldValue("aaaabbbbccccddddeeeeffffgggghhhh"),
                              "aaaabbbbccccddddeeeeffffgggghhhh", "string long");
    EXPECT_EQ(fdw.getResult().getPos(), 32u);
    EXPECT_EQ(fdw.getResult().getLength(), 32u);
    assertFlattenDocsumWriter(fdw, StringFieldValue("aaaa"), "aaaabbbbccccddddeeeeffffgggghhhh#aaaa",
                              "string second long");
    EXPECT_EQ(fdw.getResult().getPos(), 37u);
    EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
    fdw.clear();
    EXPECT_EQ(fdw.getResult().getPos(), 0u);
    EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
}

namespace {

using search::common::ElementIds;
using search::docsummary::DynamicDocsumWriter;
using search::docsummary::GetDocsumsState;
using search::docsummary::ResultConfig;
using search::docsummary::SummaryElementsSelector;
using search::docsummary::test::SlimeValue;
using vespa::config::search::vsm::VsmsummaryConfig;
using vespa::config::search::vsm::VsmsummaryConfigBuilder;

StructDataType make_elem_type(const Field& name_field, const Field& weight_field) {
    StructDataType elem_type("elem");
    elem_type.addField(name_field);
    elem_type.addField(weight_field);
    return elem_type;
}

/*
 * A document type with an array of struct field, a map of struct field and a map of int field, and the
 * field id map and field path map needed by DocsumFilter for those three fields.
 */
struct MyDocType {
    const Field          _name_field;
    const Field          _weight_field;
    const StructDataType _elem_type;
    const ArrayDataType  _elem_array_type;
    const MapDataType    _elem_map_type;
    const MapDataType    _int_map_type;
    const Field          _elem_array_field;
    const Field          _elem_map_field;
    const Field          _int_map_field;
    DocumentType         _document_type;

    MyDocType()
        : _name_field("name", 1, *DataType::STRING),
          _weight_field("weight", 2, *DataType::INT),
          _elem_type(make_elem_type(_name_field, _weight_field)),
          _elem_array_type(_elem_type),
          _elem_map_type(*DataType::STRING, _elem_type),
          _int_map_type(*DataType::STRING, *DataType::INT),
          _elem_array_field("elem_array", 3, _elem_array_type),
          _elem_map_field("elem_map", 4, _elem_map_type),
          _int_map_field("int_map", 5, _int_map_type),
          _document_type("test") {
        _document_type.addField(_elem_array_field);
        _document_type.addField(_elem_map_field);
        _document_type.addField(_int_map_field);
    }
    ~MyDocType() = default;

    std::unique_ptr<StructFieldValue> make_elem(const std::string& name, int weight) const {
        auto ret = std::make_unique<StructFieldValue>(_elem_type);
        ret->setValue(_name_field, StringFieldValue(name));
        ret->setValue(_weight_field, IntFieldValue(weight));
        return ret;
    }
    std::unique_ptr<ArrayFieldValue> make_elem_array() const {
        auto ret = std::make_unique<ArrayFieldValue>(_elem_array_type);
        ret->add(*make_elem("foo", 10));
        ret->add(*make_elem("bar", 20));
        return ret;
    }
    std::unique_ptr<MapFieldValue> make_elem_map() const {
        auto ret = std::make_unique<MapFieldValue>(_elem_map_type);
        ret->put(StringFieldValue("@foo"), *make_elem("foo", 10));
        ret->put(StringFieldValue("@bar"), *make_elem("bar", 20));
        return ret;
    }
    std::unique_ptr<MapFieldValue> make_int_map() const {
        auto ret = std::make_unique<MapFieldValue>(_int_map_type);
        ret->put(StringFieldValue("@foo"), IntFieldValue(10));
        ret->put(StringFieldValue("@bar"), IntFieldValue(20));
        return ret;
    }
    std::unique_ptr<document::Document> make_test_doc() const {
        auto ret = document::Document::make_without_repo(_document_type, DocumentId("id::test::1"));
        ret->setValue("elem_array", *make_elem_array());
        ret->setValue("elem_map", *make_elem_map());
        ret->setValue("int_map", *make_int_map());
        return ret;
    }
    FieldPath make_field_path(const std::string& path) const {
        FieldPath ret;
        _document_type.buildFieldPath(ret, path);
        return ret;
    }
};

class MyDocSumCache : public IDocSumCache {
    const Document& _doc;

public:
    explicit MyDocSumCache(const Document& doc) : _doc(doc) {}
    const Document& getDocSum(const search::DocumentIdT&) const override { return _doc; }
};

class StructFieldsDocsumTest : public ::testing::Test {
protected:
    MyDocType          _doc_type;
    StringFieldIdTMap  _field_map;
    SharedFieldPathMap _field_path_map;
    StorageDocument    _doc;

    StructFieldsDocsumTest();
    ~StructFieldsDocsumTest() override;

    /*
     * Renders the given summary field the way streaming search does, when the requested document-summary
     * selects the given subset of the struct fields.
     */
    std::string render(const std::string& field_name, const std::vector<std::string>& struct_fields);
    void assertRendered(const std::string& exp, const std::string& field_name,
                        const std::vector<std::string>& struct_fields);
};

SharedFieldPathMap make_field_path_map(const MyDocType& doc_type) {
    auto ret = std::make_shared<FieldPathMapT>();
    ret->emplace_back(); // field id 0 is the "no field"
    ret->emplace_back(doc_type.make_field_path("elem_array"));
    ret->emplace_back(doc_type.make_field_path("elem_map"));
    ret->emplace_back(doc_type.make_field_path("int_map"));
    return ret;
}

StructFieldsDocsumTest::StructFieldsDocsumTest()
    : _doc_type(),
      _field_map(),
      _field_path_map(make_field_path_map(_doc_type)),
      _doc(_doc_type.make_test_doc(), _field_path_map, _field_path_map->size()) {
    _field_map.add("no_field", 0);
    _field_map.add("elem_array", 1);
    _field_map.add("elem_map", 2);
    _field_map.add("int_map", 3);
}

StructFieldsDocsumTest::~StructFieldsDocsumTest() = default;

std::string StructFieldsDocsumTest::render(const std::string&              field_name,
                                           const std::vector<std::string>& struct_fields) {
    // Two document-summaries, as in a real streaming schema: the default one, which vsmsummary.cfg is
    // derived from and which is the only one DocsumFilter knows about, and the requested one, which is
    // where the selection of struct fields is made.
    auto  result_config = std::make_unique<ResultConfig>();
    auto* default_class = result_config->addResultClass("default", 0);
    default_class->addConfigEntry(field_name, SummaryElementsSelector::select_all(), {}, {});
    auto* requested_class = result_config->addResultClass("requested", 1);
    requested_class->addConfigEntry(field_name, SummaryElementsSelector::select_all(), {}, struct_fields);
    result_config->set_default_result_class_id(0);
    auto  writer = std::make_unique<DynamicDocsumWriter>(std::move(result_config));
    auto& writer_ref = *writer;
    auto  tools = std::make_shared<DocsumTools>();
    tools->set_writer(std::move(writer));
    VsmsummaryConfigBuilder vsm_summary;
    vsm_summary.outputclass = "default";
    tools->obtainFieldNames(std::make_shared<VsmsummaryConfig>(vsm_summary));

    MyDocSumCache cache(_doc);
    DocsumFilter  filter(tools, cache);
    filter.init(_field_map, *_field_path_map);
    GetDocsumsStateCallback        callback;
    GetDocsumsState                state(callback);
    auto                           rci = writer_ref.resolveClassInfo("requested", {});
    vespalib::Slime                slime;
    vespalib::slime::SlimeInserter inserter(slime);
    writer_ref.insertDocsum(rci, 0, state, filter, inserter);
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

void StructFieldsDocsumTest::assertRendered(const std::string& exp, const std::string& field_name,
                                            const std::vector<std::string>& struct_fields) {
    SlimeValue      exp_slime(exp);
    vespalib::Slime act_slime;
    auto            rendered = render(field_name, struct_fields);
    EXPECT_GT(vespalib::slime::JsonFormat::decode(rendered, act_slime), 0u);
    EXPECT_EQ(exp_slime.slime, act_slime) << rendered;
}

TEST_F(StructFieldsDocsumTest, all_struct_fields_are_rendered_without_selection) {
    assertRendered("{elem_array:[{name:'foo',weight:10},{name:'bar',weight:20}]}", "elem_array", {});
    assertRendered("{elem_map:[{key:'@foo',value:{name:'foo',weight:10}},{key:'@bar',value:{name:'bar',weight:20}}]}",
                   "elem_map", {});
}

TEST_F(StructFieldsDocsumTest, selected_struct_fields_of_array_of_struct_are_rendered) {
    assertRendered("{elem_array:[{name:'foo'},{name:'bar'}]}", "elem_array", {"name"});
    assertRendered("{elem_array:[{weight:10},{weight:20}]}", "elem_array", {"weight"});
}

TEST_F(StructFieldsDocsumTest, selected_struct_fields_of_map_of_struct_are_rendered) {
    assertRendered("{elem_map:[{key:'@foo',value:{name:'foo'}},{key:'@bar',value:{name:'bar'}}]}", "elem_map",
                   {"value.name"});
}

TEST_F(StructFieldsDocsumTest, only_the_key_of_a_map_can_be_selected) {
    assertRendered("{elem_map:[{key:'@foo'},{key:'@bar'}]}", "elem_map", {"key"});
}

TEST_F(StructFieldsDocsumTest, key_and_value_of_a_map_of_scalar_are_selected_on_their_own) {
    assertRendered("{int_map:[{key:'@foo',value:10},{key:'@bar',value:20}]}", "int_map", {});
    assertRendered("{int_map:[{key:'@foo'},{key:'@bar'}]}", "int_map", {"key"});
    // Unlike the key of a map of struct, this one is left out when it is not selected.
    assertRendered("{int_map:[{value:10},{value:20}]}", "int_map", {"value"});
}

} // namespace

} // namespace vsm

GTEST_MAIN_RUN_ALL_TESTS()
