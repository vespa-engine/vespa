// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/hitcollector.h>
#include <vespa/searchvisitor/matching_elements_filler.h>
#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/searcher/fieldsearcher.h>
#include <vespa/vsm/searcher/intfieldsearcher.h>
#include <vespa/vsm/searcher/utf8strchrfieldsearcher.h>
#include <iostream>

using document::ArrayDataType;
using document::ArrayFieldValue;
using document::DataType;
using document::DocumentId;
using document::DocumentType;
using document::Field;
using document::FieldPath;
using document::IntFieldValue;
using document::MapDataType;
using document::MapFieldValue;
using document::StringFieldValue;
using document::StructDataType;
using document::StructFieldValue;
using search::MatchingElements;
using search::MatchingElementsFields;
using search::fef::MatchData;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using streaming::HitCollector;
using streaming::MatchingElementsFiller;
using vdslib::SearchResult;
using vsm::FieldIdTList;
using vsm::IntFieldSearcher;
using vsm::StorageDocument;
using vsm::UTF8StrChrFieldSearcher;

using ElementVector = std::vector<uint32_t>;

namespace {

StructDataType make_elem_type(const Field& name_field, const Field& weight_field) {
    StructDataType elem_type("elem");
    elem_type.addField(name_field);
    elem_type.addField(weight_field);
    return elem_type;
}

struct BoundTerm {
    vespalib::string _bound_term;
    BoundTerm(vespalib::string bound_term)
        : _bound_term(std::move(bound_term))
    {
    }
    BoundTerm(const char* bound_term)
        : BoundTerm(vespalib::string(bound_term))
    {
    }
    vespalib::string index() const {
        auto pos = _bound_term.find(':');
        return _bound_term.substr(0, pos != vespalib::string::npos ? pos : 0);
    }
    vespalib::string term() const {
        auto pos = _bound_term.find(':');
        return _bound_term.substr(pos != vespalib::string::npos ? (pos + 1) : 0);
    }
};

Query make_query(std::unique_ptr<search::query::Node> root) {
    vespalib::string stack_dump = StackDumpCreator::create(*root);
    QueryNodeResultFactory empty;
    Query query(empty, stack_dump);
    return query;
}

struct MyQueryBuilder : public search::query::QueryBuilder<search::query::SimpleQueryNodeTypes>
{
    using search::query::QueryBuilder<search::query::SimpleQueryNodeTypes>::QueryBuilder;

    void add_term(BoundTerm term, int32_t id) {
        if (!term.term().empty() && std::isdigit(term.term()[0], std::locale::classic())) {
            addNumberTerm(term.term(), term.index(), id, Weight(0));
        } else {
            addStringTerm(term.term(), term.index(), id, Weight(0));
        }
    }
    void make_same_element(vespalib::string field, BoundTerm term1, int32_t id1, BoundTerm term2, int32_t id2) {
        addSameElement(2, field, 0, Weight(0));
        add_term(term1, id1);
        add_term(term2, id2);
    }
};

Query make_same_element(vespalib::string field, BoundTerm term1, BoundTerm term2) {
    MyQueryBuilder builder;
    builder.make_same_element(field, term1, 0, term2, 1);
    return make_query(builder.build());
}

Query make_same_element_single(BoundTerm term) {
    MyQueryBuilder builder;
    builder.add_term(term, 0);
    return make_query(builder.build());
}

struct MyDocType {
    const Field    _name_field;
    const Field    _weight_field;
    const StructDataType _elem_type;
    const ArrayDataType  _elem_array_type;
    const MapDataType _elem_map_type;
    const MapDataType _str_int_map_type;
    const Field _elem_array_field;
    const Field _elem_map_field;
    const Field _str_int_map_field;
    DocumentType   _document_type;

    MyDocType();
    ~MyDocType();
    std::unique_ptr<StructFieldValue> make_elem(const vespalib::string& name, int weight) const;
    std::unique_ptr<ArrayFieldValue> make_elem_array(std::vector<std::pair<std::string, int>> values) const;
    std::unique_ptr<MapFieldValue> make_elem_map(std::map<std::string, std::pair<std::string, int>> values) const;
    std::unique_ptr<MapFieldValue> make_str_int_map(std::map<std::string, int> values) const;
    FieldPath make_field_path(vespalib::string path) const;
    std::unique_ptr<document::Document> make_test_doc() const;
};

MyDocType::MyDocType()
    : _name_field("name", 1, *DataType::STRING),
      _weight_field("weight", 2, *DataType::INT),
      _elem_type(make_elem_type(_name_field, _weight_field)),
      _elem_array_type(_elem_type),
      _elem_map_type(*DataType::STRING, _elem_type),
      _str_int_map_type(*DataType::STRING, *DataType::INT),
      _elem_array_field("elem_array", 3, _elem_array_type),
      _elem_map_field("elem_map", 4, _elem_map_type),
      _str_int_map_field("str_int_map", _str_int_map_type),
      _document_type("test")
{
    _document_type.addField(_elem_array_field);
    _document_type.addField(_elem_map_field);
    _document_type.addField(_str_int_map_field);
}

MyDocType::~MyDocType() = default;

std::unique_ptr<StructFieldValue>
MyDocType::make_elem(const vespalib::string& name, int weight) const
{
    auto ret = std::make_unique<StructFieldValue>(_elem_type);
    ret->setValue(_name_field, StringFieldValue(name));
    ret->setValue(_weight_field, IntFieldValue(weight));
    return ret;
}

std::unique_ptr<ArrayFieldValue>
MyDocType::make_elem_array(std::vector<std::pair<std::string, int>> values) const
{
    auto ret = std::make_unique<ArrayFieldValue>(_elem_array_type);
    for (auto& name_weight : values) {
        ret->add(*make_elem(name_weight.first, name_weight.second));
    }
    return ret;
}

std::unique_ptr<MapFieldValue>
MyDocType::make_elem_map(std::map<std::string, std::pair<std::string, int>> values) const
{
    auto ret = std::make_unique<MapFieldValue>(_elem_map_type);
    for (auto& kv : values) {
        ret->put(StringFieldValue(kv.first), *make_elem(kv.second.first, kv.second.second));
    }
    return ret;
}

std::unique_ptr<MapFieldValue>
MyDocType::make_str_int_map(std::map<std::string, int> values) const
{
    auto ret = std::make_unique<MapFieldValue>(_str_int_map_type);
    for (auto& kv : values) {
        ret->put(StringFieldValue(kv.first), IntFieldValue(kv.second));
    }
    return ret;
}

FieldPath
MyDocType::make_field_path(vespalib::string path) const
{
    FieldPath ret;
    _document_type.buildFieldPath(ret, path);
    return ret;
}

std::unique_ptr<document::Document>
MyDocType::make_test_doc() const
{
    auto doc = std::make_unique<document::Document>(_document_type, DocumentId("id::test::1"));
    doc->setValue("elem_array", *make_elem_array({{"foo", 10},{"bar", 20},{"baz", 30},{"foo", 40}, {"zap", 20}, {"zap", 20}}));
    // the elements in maps are ordered on the key
    doc->setValue("elem_map", *make_elem_map({{"@foo", {"foo", 10}}, {"@bar", {"bar", 20}},{"@baz", {"baz", 30}},{"@foo@", {"foo", 40}},{"@zap", {"zap", 20}}, {"@zap@", {"zap", 20}}}));
    doc->setValue("str_int_map", *make_str_int_map({{"@foo", 10}, {"@bar", 20}, {"@baz", 30}, {"@foo@", 40}, {"@zap", 20}, {"@zap@", 20}}));
    return doc;
}

vsm::SharedFieldPathMap make_field_path_map(const MyDocType& doc_type) {
    auto ret = std::make_shared<std::vector<FieldPath>>();
    ret->emplace_back(doc_type.make_field_path("elem_array.name"));
    ret->emplace_back(doc_type.make_field_path("elem_array.weight"));
    ret->emplace_back(doc_type.make_field_path("elem_map.key"));
    ret->emplace_back(doc_type.make_field_path("elem_map.value.name"));
    ret->emplace_back(doc_type.make_field_path("elem_map.value.weight"));
    ret->emplace_back(doc_type.make_field_path("str_int_map.key"));
    ret->emplace_back(doc_type.make_field_path("str_int_map.value"));
    return ret;
}

vsm::FieldIdTSearcherMap make_field_searcher_map() {
    vsm::FieldIdTSearcherMap ret;
    ret.emplace_back(std::make_unique<UTF8StrChrFieldSearcher>(0));
    ret.emplace_back(std::make_unique<IntFieldSearcher>(1));
    ret.emplace_back(std::make_unique<UTF8StrChrFieldSearcher>(2));
    ret.emplace_back(std::make_unique<UTF8StrChrFieldSearcher>(3));
    ret.emplace_back(std::make_unique<IntFieldSearcher>(4));
    ret.emplace_back(std::make_unique<UTF8StrChrFieldSearcher>(5));
    ret.emplace_back(std::make_unique<IntFieldSearcher>(6));
    return ret;
}

vsm::DocumentTypeIndexFieldMapT make_index_to_field_ids() {
    vsm::DocumentTypeIndexFieldMapT ret;
    auto& index_map = ret["test"];
    index_map["elem_array.name"] = FieldIdTList{0};
    index_map["elem_array.weight"] = FieldIdTList{1};
    index_map["elem_map.key"] = FieldIdTList{2};
    index_map["elem_map.value.name"] = FieldIdTList{3};
    index_map["elem_map.value.weight"] = FieldIdTList{4};
    index_map["str_int_map.key"] = FieldIdTList{5};
    index_map["str_int_map.value"] = FieldIdTList{6};
    return ret;
}

MatchingElementsFields make_matching_elements_fields() {
    MatchingElementsFields fields;
    fields.add_mapping("elem_array", "elem_array.name");
    fields.add_mapping("elem_array", "elem_array.weight");
    fields.add_mapping("elem_map", "elem_map.key");
    fields.add_mapping("elem_map", "elem_map.value.name");
    fields.add_mapping("elem_map", "elem_map.value.weight");
    fields.add_mapping("str_int_map", "str_int_map.key");
    fields.add_mapping("str_int_map", "str_int_map.value");
    return fields;
}

}

class MatchingElementsFillerTest : public ::testing::Test {
    const MyDocType                         _doc_type;
    MatchingElementsFields                  _matching_elems_fields;
    vsm::SharedFieldPathMap                 _field_path_map;
    vsm::FieldIdTSearcherMap                _field_searcher_map;
    vsm::DocumentTypeIndexFieldMapT         _index_to_field_ids;
    HitCollector                            _hit_collector;
    SearchResult                            _search_result;
    Query                                   _query;
    vsm::SharedSearcherBuf                  _shared_searcher_buf;
    std::unique_ptr<MatchingElementsFiller> _matching_elements_filler;
    std::unique_ptr<MatchingElements>       _matching_elements;
    std::unique_ptr<StorageDocument>        _sdoc;
public:
    MatchingElementsFillerTest();
    ~MatchingElementsFillerTest();
    void fill_matching_elements(Query &&query);
    void assert_elements(uint32_t doc_lid, const vespalib::string& field, const ElementVector& exp_elements);
    void assert_same_element(const vespalib::string& field, const vespalib::string& term1, const vespalib::string& term2, const ElementVector& exp_elements);
    void assert_same_element_single(const vespalib::string& field, const vespalib::string& term, const ElementVector& exp_elements);
};

MatchingElementsFillerTest::MatchingElementsFillerTest()
    : ::testing::Test(),
      _doc_type(),
      _matching_elems_fields(make_matching_elements_fields()),
      _field_path_map(make_field_path_map(_doc_type)),
      _field_searcher_map(make_field_searcher_map()),
      _index_to_field_ids(make_index_to_field_ids()),
      _hit_collector(10),
      _search_result(),
      _query(),
      _shared_searcher_buf(std::make_shared<vsm::SearcherBuf>()),
      _matching_elements_filler(),
      _matching_elements(),
      _sdoc()
{
    _search_result.addHit(1, "id::test::1", 0.0, nullptr, 0);
    _sdoc = std::make_unique<StorageDocument>(_doc_type.make_test_doc(), _field_path_map, _field_path_map->size());
    EXPECT_TRUE(_sdoc->valid());
    MatchData md(MatchData::params());
    _hit_collector.addHit(_sdoc.get(), 1, md, 0.0, nullptr, 0);
}

MatchingElementsFillerTest::~MatchingElementsFillerTest() = default;

void
MatchingElementsFillerTest::fill_matching_elements(Query &&query)
{
    _matching_elements_filler.reset();
    _matching_elements.reset();
    _query = std::move(query);
    _field_searcher_map.prepare(_index_to_field_ids, _shared_searcher_buf, _query);
    _matching_elements_filler = std::make_unique<MatchingElementsFiller>(_field_searcher_map, _query, _hit_collector, _search_result);
    _matching_elements = _matching_elements_filler->fill_matching_elements(_matching_elems_fields);
}

void
MatchingElementsFillerTest::assert_elements(uint32_t doc_lid, const vespalib::string& field, const ElementVector& exp_elements)
{
    auto act_elements = _matching_elements->get_matching_elements(doc_lid, field);
    EXPECT_EQ(exp_elements, act_elements);
}

void
MatchingElementsFillerTest::assert_same_element(const vespalib::string& field, const vespalib::string& term1, const vespalib::string& term2, const ElementVector& exp_elements)
{
    fill_matching_elements(make_same_element(field, term1, term2));
    assert_elements(1, field, exp_elements);
}

void
MatchingElementsFillerTest::assert_same_element_single(const vespalib::string& field, const vespalib::string& term, const ElementVector& exp_elements)
{
    fill_matching_elements(make_same_element_single(field + "." + term));
    assert_elements(1, field, exp_elements);
}

TEST_F(MatchingElementsFillerTest, matching_elements_calculated_for_same_element_operator)
{
    assert_same_element("elem_array", "name:bar", "weight:20", { 1 });
    assert_same_element("elem_array", "name:zap", "weight:20", { 4, 5 });
    assert_same_element("elem_map", "value.name:bar", "value.weight:20", { 0 });
    assert_same_element("elem_map", "value.name:zap", "value.weight:20", { 4, 5 });
    assert_same_element("str_int_map", "key:bar", "value:20", { 0 });
    assert_same_element("str_int_map", "key:zap", "value:20", { 4, 5 });
}

TEST_F(MatchingElementsFillerTest, matching_elements_calculated_when_searching_on_nested_field)
{
    assert_same_element_single("elem_array", "name:bar", { 1 });
    assert_same_element_single("elem_array", "name:foo", { 0, 3 });
    assert_same_element_single("elem_array", "name:zap", { 4, 5 });
    assert_same_element_single("elem_array", "weight:20", { 1, 4, 5 });
    assert_same_element_single("elem_map", "key:foo", { 2, 3 });
    assert_same_element_single("elem_map", "key:zap", { 4, 5 });
    assert_same_element_single("elem_map", "value.name:bar", { 0 });
    assert_same_element_single("elem_map", "value.name:foo", { 2, 3 });
    assert_same_element_single("elem_map", "value.name:zap", { 4, 5 });
    assert_same_element_single("elem_map", "value.weight:20", { 0, 4, 5 });
    assert_same_element_single("str_int_map", "key:bar", { 0 });
    assert_same_element_single("str_int_map", "key:foo", { 2, 3 });
    assert_same_element_single("str_int_map", "key:zap", { 4, 5 });
    assert_same_element_single("str_int_map", "value:20", { 0, 4, 5 });
    assert_same_element_single("str_int_map", "value:10", { 2 });
}

TEST_F(MatchingElementsFillerTest, all_children_of_intermediate_query_nodes_are_traversed)
{
    MyQueryBuilder builder;
    builder.addAnd(2);
    builder.add_term("elem_array.name:bar", 0);
    builder.make_same_element("elem_map", "value.name:zap", 1, "value.weight:20", 2);
    fill_matching_elements(make_query(builder.build()));
    assert_elements(1, "elem_array", { 1 });
    assert_elements(1, "elem_map", { 4, 5 });
}

TEST_F(MatchingElementsFillerTest, and_not_query_node_ignores_all_but_first_child)
{
    MyQueryBuilder builder;
    builder.addAndNot(2);
    builder.add_term("elem_array.name:bar", 0);
    builder.make_same_element("elem_map", "value.name:zap", 1, "value.weight:20", 2);
    fill_matching_elements(make_query(builder.build()));
    assert_elements(1, "elem_array", { 1 });
    assert_elements(1, "elem_map", { });
}

TEST_F(MatchingElementsFillerTest, union_of_matching_elements)
{
    MyQueryBuilder builder;
    builder.addAnd(2);
    builder.add_term("elem_array.name:foo", 0);
    builder.add_term("elem_array.weight:20", 1);
    fill_matching_elements(make_query(builder.build()));
    assert_elements(1, "elem_array", { 0, 1, 3, 4, 5 });
}

GTEST_MAIN_RUN_ALL_TESTS()
