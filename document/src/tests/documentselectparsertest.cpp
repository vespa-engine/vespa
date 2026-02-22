// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/visitor.h>
#include <vespa/document/select/bodyfielddetector.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/valuenodes.h>
#include <vespa/document/select/branch.h>
#include <vespa/document/select/simpleparser.h>
#include <vespa/document/select/constant.h>
#include <vespa/document/select/invalidconstant.h>
#include <vespa/document/select/doctype.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/operator.h>
#include <vespa/document/select/parse_utils.h>
#include <vespa/document/select/parser_limits.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/casts.h>
#include <vespa/vespalib/util/exceptions.h>
#include <limits>

using namespace document::new_config_builder;

namespace document {

class DocumentSelectParserTest : public ::testing::Test {
protected:
    BucketIdFactory _bucketIdFactory;
    std::unique_ptr<select::Parser> _parser;
    std::vector<Document::SP > _doc;
    std::vector<DocumentUpdate::SP > _update;

    ~DocumentSelectParserTest() override;

    static Document::SP createDoc(
            std::string_view doctype, std::string_view id, uint32_t hint,
            double hfloat, std::string_view hstr, std::string_view cstr,
            uint64_t hlong = 0);

    static DocumentUpdate::SP createUpdate(
            const std::string& doctype, const std::string& id, uint32_t hint,
            const std::string& hstr);

    std::unique_ptr<select::FieldValueNode>
    parseFieldValue(const std::string& expression);

    template <typename ContainsType>
    select::ResultList doParse(std::string_view expr, const ContainsType& t);

    std::string parse_to_tree(const std::string& str);

    DocumentSelectParserTest()
        : _bucketIdFactory() {}

    void SetUp() override;
    void createDocs();

    void testDocumentUpdates0();
    void testDocumentUpdates1();
    void testDocumentUpdates2();
    void testDocumentUpdates3();
    void testDocumentUpdates4();
};

DocumentSelectParserTest::~DocumentSelectParserTest() = default;

namespace {
    std::shared_ptr<const DocumentTypeRepo> _repo;
}

void DocumentSelectParserTest::SetUp()
{
    // Create config using NewConfigBuilder with all TestDocRepo types plus additional ones
    NewConfigBuilder builder;

    // Recreate full TestDocRepo configuration
    const int type1_id = 238423572;
    const int type2_id = 238424533;
    const int type3_id = 1088783091;
    const int mystruct_id = -2092985851;

    auto& doc1 = builder.document("testdoctype1", type1_id);

    // Create mystruct
    auto mystruct_ref = doc1.createStruct("mystruct")
            .setId(mystruct_id)
            .addField("key", builder.intTypeRef())
            .addField("value", builder.stringTypeRef()).ref();

    // Create structarray (array of mystruct)
    auto structarray_ref = doc1.createArray(mystruct_ref).ref();

    // Add all fields from TestDocRepo
    doc1.addField("headerval", builder.intTypeRef())
        .addField("headerlongval", builder.longTypeRef())
        .addField("hfloatval", builder.floatTypeRef())
        .addField("hstringval", builder.stringTypeRef())
        .addField("mystruct", mystruct_ref)
        .addField("tags", doc1.createArray(builder.stringTypeRef()).ref())
        .addField("boolfield", builder.boolTypeRef())
        .addField("stringweightedset", doc1.createWset(builder.stringTypeRef()).ref())
        .addField("stringweightedset2", builder.tagTypeRef())
        .addField("byteweightedset", doc1.createWset(builder.byteTypeRef()).ref())
        .addField("mymap", doc1.createMap(builder.intTypeRef(),
                                         builder.stringTypeRef()).ref())
        .addField("structarrmap", doc1.createMap(builder.stringTypeRef(),
                                                 structarray_ref).ref())
        .addField("title", builder.stringTypeRef())
        .addField("byteval", builder.byteTypeRef())
        .addField("content", builder.stringTypeRef())
        .addField("rawarray", doc1.createArray(builder.rawTypeRef()).ref())
        .addField("structarray", structarray_ref)
        .addTensorField("sparse_tensor", "tensor(x{})")
        .addTensorField("sparse_xy_tensor", "tensor(x{},y{})")
        .addTensorField("sparse_float_tensor", "tensor<float>(x{})")
        .addTensorField("dense_tensor", "tensor(x[2])");

    doc1.imported_field("my_imported_field");
    doc1.fieldSet("[document]", {"headerval", "hstringval", "title"});

    // testdoctype2 inherits from testdoctype1
    auto& doc2 = builder.document("testdoctype2", type2_id);
    doc2.addField("onlyinchild", builder.intTypeRef())
        .inherit(doc1.idx());

    // _test_doctype3_ inherits from testdoctype1
    auto& doc3 = builder.document("_test_doctype3_", type3_id);
    doc3.addField("_only_in_child_", builder.intTypeRef())
        .inherit(doc1.idx());

    // Add additional document types for this test
    auto& with_imported = builder.document("with_imported", 1234567);
    with_imported.imported_field("my_imported_field");

    // Document types with names that are (or include) identifiers that lex to specific tokens
    builder.document("notandor", 535424777);
    builder.document("ornotand", 1348665801);
    builder.document("andornot", -1848670693);
    builder.document("idid", -1193328712);
    builder.document("usergroup", -1673092522);

    auto& user_doc = builder.document("user", 875463456);
    user_doc.addField("id", builder.intTypeRef());

    auto& group_doc = builder.document("group", 567463442);
    group_doc.addField("iD", builder.intTypeRef());

    _repo = std::make_unique<DocumentTypeRepo>(builder.config());

    _parser = std::make_unique<select::Parser>(*_repo, _bucketIdFactory);
}

Document::SP
DocumentSelectParserTest::createDoc(std::string_view doctype, std::string_view id, uint32_t hint, double hfloat,
                                    std::string_view hstr, std::string_view cstr, uint64_t hlong)
{
    const DocumentType* type = _repo->getDocumentType(doctype);
    auto doc = std::make_shared<Document>(*_repo, *type, DocumentId(id));
    doc->setValue(doc->getField("headerval"), IntFieldValue(hint));

    if (hlong != 0) {
        doc->setValue(doc->getField("headerlongval"), LongFieldValue(hlong));
    }
    doc->setValue(doc->getField("hfloatval"), FloatFieldValue(hfloat));
    doc->setValue(doc->getField("hstringval"), StringFieldValue(hstr));
    doc->setValue(doc->getField("content"), StringFieldValue(cstr));
    return doc;
}

DocumentUpdate::SP
DocumentSelectParserTest::createUpdate(const std::string& doctype, const std::string& id, uint32_t hint, const std::string& hstr)
{
    const DocumentType* type = _repo->getDocumentType(doctype);
    auto doc = std::make_shared<DocumentUpdate>(*_repo, *type, DocumentId(id));
    doc->addUpdate(FieldUpdate(doc->getType().getField("headerval"))
                      .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(hint))));
    doc->addUpdate(FieldUpdate(doc->getType().getField("hstringval"))
                      .addUpdate(std::make_unique<AssignValueUpdate>(StringFieldValue::make(hstr))));
    return doc;
}

void
DocumentSelectParserTest::createDocs()
{
    _doc.clear();
    _doc.push_back(createDoc("testdoctype1", "id:myspace:testdoctype1::anything", 24, 2.0, "foo", "bar", 0));  // DOC 0
    _doc.push_back(createDoc("testdoctype1", "id:anotherspace:testdoctype1::foo", 13, 4.1, "bar", "foo", 0));  // DOC 1
        // Add some arrays and structs to doc 1
    {
        StructFieldValue sval(_doc.back()->getField("mystruct").getDataType());
        sval.setValue("key", IntFieldValue::make(14));
        sval.setValue("value", StringFieldValue::make("structval"));
        _doc.back()->setValue("mystruct", sval);
        ArrayFieldValue
            aval(_doc.back()->getField("structarray").getDataType());
        {
            StructFieldValue sval1(aval.getNestedType());
            sval1.setValue("key", IntFieldValue::make(15));
            sval1.setValue("value", StringFieldValue::make("structval1"));
            StructFieldValue sval2(aval.getNestedType());
            sval2.setValue("key", IntFieldValue::make(16));
            sval2.setValue("value", StringFieldValue::make("structval2"));
            aval.add(sval1);
            aval.add(sval2);
        }
        _doc.back()->setValue("structarray", aval);

        MapFieldValue mval(_doc.back()->getField("mymap").getDataType());
        mval.put(document::IntFieldValue(3), document::StringFieldValue("a"));
        mval.put(document::IntFieldValue(5), document::StringFieldValue("b"));
        mval.put(document::IntFieldValue(7), document::StringFieldValue("c"));
        _doc.back()->setValue("mymap", mval);

        MapFieldValue amval(_doc.back()->getField("structarrmap").getDataType());
        amval.put(StringFieldValue("foo"), aval);
        amval.put(StringFieldValue("a key needing escaping"), aval);

        ArrayFieldValue abval(_doc.back()->getField("structarray").getDataType());
        {
            StructFieldValue sval1(aval.getNestedType());
            sval1.setValue("key", IntFieldValue::make(17));
            sval1.setValue("value", StringFieldValue::make("structval3"));
            StructFieldValue sval2(aval.getNestedType());
            sval2.setValue("key", IntFieldValue::make(18));
            sval2.setValue("value", StringFieldValue::make("structval4"));
            abval.add(sval1);
            abval.add(sval2);
        }

        amval.put(StringFieldValue("bar"), abval);
        _doc.back()->setValue("structarrmap", amval);

        WeightedSetFieldValue wsval(_doc.back()->getField("stringweightedset").getDataType());
        WSetHelper(wsval).add("foo");
        WSetHelper(wsval).add("val1");
        WSetHelper(wsval).add("val2");
        WSetHelper(wsval).add("val3");
        WSetHelper(wsval).add("val4");
        _doc.back()->setValue("stringweightedset", wsval);

        WeightedSetFieldValue wsbytes(_doc.back()->getField("byteweightedset").getDataType());
        wsbytes.add(ByteFieldValue(5));
        wsbytes.add(ByteFieldValue(75));
        wsbytes.add(ByteFieldValue(static_cast<int8_t>(255)));
        wsbytes.add(ByteFieldValue(0));
        _doc.back()->setValue("byteweightedset", wsbytes);
        // doc 1 also has a populated tensor field
        const auto& tensor_type = dynamic_cast<const TensorDataType&>(_doc.back()->getField("dense_tensor").getDataType());
        TensorFieldValue tfv(tensor_type);
        tfv.make_empty_if_not_existing();
        _doc.back()->setValue("dense_tensor", tfv);
    }

    _doc.push_back(createDoc("testdoctype1", "id:myspace:testdoctype1:n=1234:footype1", 15, 1.0, "some", "some", 0));  // DOC 2
        // Add empty struct and array
    {
        StructFieldValue sval(_doc.back()->getField("mystruct").getDataType());
        _doc.back()->setValue("mystruct", sval);
        ArrayFieldValue aval(_doc.back()->getField("structarray").getDataType());
        _doc.back()->setValue("structarray", aval);
    }
    _doc.push_back(createDoc("testdoctype1", "id:myspace:testdoctype1:g=yahoo:bar", 14, 2.4, "Yet", "\xE4\xB8\xBA\xE4\xBB\x80", 0)); // DOC 3
    _doc.push_back(createDoc("testdoctype2", "id:myspace:testdoctype2::inheriteddoc", 10, 1.4, "inherited", "")); // DOC 4
    _doc.push_back(createDoc(
        "testdoctype1", "id:footype:testdoctype1:n=123456789:aardvark",
        10, 1.4, "inherited", "", 0));  // DOC 5
    _doc.push_back(createDoc(
        "testdoctype1", "id:footype:testdoctype1:n=1234:highlong",
        10, 1.4, "inherited", "", 2651257743)); // DOC 6
    _doc.push_back(createDoc(
        "testdoctype1", "id:footype:testdoctype1:n=1234:highlong",
        10, 1.4, "inherited", "", -2651257743)); // DOC 7
    _doc.push_back(createDoc( // DOC 8. As DOC 0 but with version 2.
        "testdoctype1", "id:myspace:testdoctype1::anything", 24, 2.0, "foo", "bar", 0));
    _doc.push_back(createDoc(
        "testdoctype1", "id:footype:testdoctype1:n=12345:foo",
        10, 1.4, "inherited", "", 42)); // DOC 9
    _doc.push_back(createDoc(
        "testdoctype1", "id:myspace:testdoctype1:g=xyzzy:foo",
        10, 1.4, "inherited", "", 42)); // DOC 10
    _doc.push_back(createDoc(
            "testdoctype1", "id:myspace:testdoctype1::withtruebool",
            10, 1.4, "inherited", "", 42)); // DOC 11
    _doc.back()->setValue("boolfield", BoolFieldValue(true));
    _doc.push_back(createDoc(
            "testdoctype1", "id:myspace:testdoctype1::withfalsebool",
            10, 1.4, "inherited", "", 42)); // DOC 12
    _doc.back()->setValue("boolfield", BoolFieldValue(false));

    _update.clear();
    _update.push_back(createUpdate("testdoctype1", "id:myspace:testdoctype1::anything", 20, "hmm"));
    _update.push_back(createUpdate("testdoctype1", "id:anotherspace:testdoctype1::foo", 10, "foo"));
    _update.push_back(createUpdate("testdoctype1", "id:myspace:testdoctype1:n=1234:footype1", 0, "foo"));
    _update.push_back(createUpdate("testdoctype1", "id:myspace:testdoctype1:g=yahoo:bar", 3, "\xE4\xBA\xB8\xE4\xBB\x80"));
    _update.push_back(createUpdate("testdoctype2", "id:myspace:testdoctype2::inheriteddoc", 10, "bar"));
}

namespace {
void doVerifyParse(select::Node *node, const std::string &query, const char *expected) {
    std::string message("Query "+query+" failed to parse.");
    ASSERT_TRUE(node != nullptr) << message;
    std::ostringstream actual;
    actual << *node;

    std::string exp(expected != nullptr ? std::string(expected) : query);
    EXPECT_EQ(exp, actual.str());
    // Test that cloning gives the same result
    std::unique_ptr<select::Node> clonedNode(node->clone());
    std::ostringstream clonedStr;
    clonedStr << *clonedNode;
    EXPECT_EQ(exp, clonedStr.str());
}

void verifySimpleParse(const std::string& query, const char* expected = nullptr) {
    BucketIdFactory factory;
    select::simple::SelectionParser parser(factory);
    std::string message("Query "+query+" failed to parse.");
    EXPECT_TRUE(parser.parse(query)) << message;
    std::unique_ptr<select::Node> node(parser.getNode());
    doVerifyParse(node.get(), query, expected);
}

void verifyParse(const std::string& query, const char* expected = nullptr) {
    BucketIdFactory factory;
    select::Parser parser(*_repo, factory);
    std::unique_ptr<select::Node> node(parser.parse(query));
    doVerifyParse(node.get(), query, expected);
}

void verifyFailedParse(const std::string& query, const std::string& error) {
    try {
        BucketIdFactory factory;
        TestDocRepo test_repo;
        select::Parser parser(test_repo.getTypeRepo(), factory);
        std::unique_ptr<select::Node> node(parser.parse(query));
        FAIL() << "Expected exception parsing query '" << query << "'";
    } catch (select::ParsingFailedException& e) {
        std::string message(e.what());
        if (message.size() > error.size())
            message = message.substr(0, error.size());
        std::string failure("Expected: " + error + "\n- Actual  : "
                            + std::string(e.what()));
        EXPECT_EQ(error, message) << failure;
    }
}

}

TEST_F(DocumentSelectParserTest, test_syntax_error_reporting)
{
    createDocs();

    verifyFailedParse("testdoctype1.headerval == aaa", "ParsingFailedException: "
                      "syntax error, unexpected end of input, expecting . at column 30 "
                      "when parsing selection 'testdoctype1.headerval == aaa'");
    // TODO improve error reporting of broken escape sequences. Current error messages
    // are not too helpful since we simply fail to parse the string token altogether.
    verifyFailedParse("testdoctype1.headerval == \"tab\\x0notcomplete\"",
                      "ParsingFailedException: Unexpected character: '\\\"' at column 27 "
                      "when parsing selection 'testdoctype1.headerval == \"tab\\x0notcomplete\"'");
    verifyFailedParse("testdoctype1.headerval == \"tab\\ysf\"",
                      "ParsingFailedException: Unexpected character: '\\\"' at column 27 "
                      "when parsing selection 'testdoctype1.headerval == \"tab\\ysf\"'");
    // Test illegal operator
    verifyFailedParse("testdoctype1.headerval <> 12", "ParsingFailedException: syntax error, "
                      "unexpected > at column 25 when parsing selection 'testdoctype1.headerval <> 12'");

    // This will trigger a missing doctype error instead of syntax error, as "fal"
    // will be reduced into a doctype rule.
    verifyFailedParse("fal se", "ParsingFailedException: Document type 'fal' "
                       "not found at column 1 when parsing selection 'fal se'");

    verifyFailedParse("mytype", "ParsingFailedException: Document type 'mytype' not found");

    verifyFailedParse("mytype.foo.bar", "ParsingFailedException: Document type 'mytype' not found");

    verifyFailedParse("testdoctype1 == 8", "ParsingFailedException: syntax error, unexpected ==, "
                      "expecting end of input at column 14 when parsing selection 'testdoctype1 == 8'");

    verifyFailedParse("(1 + 2)", "ParsingFailedException: expected field spec, "
                      "doctype, bool or comparison at column 1 when parsing selection '(1 + 2)'");
}

TEST_F(DocumentSelectParserTest, testParseTerminals)
{
    createDocs();

    // Test number value
    verifyParse("", "true");
    verifyParse("testdoctype1.headerval == 123");
    verifyParse("testdoctype1.headerval == +123.53", "testdoctype1.headerval == 123.53");
    verifyParse("testdoctype1.headerval == -123.5");
    verifyParse("testdoctype1.headerval == 234123.523e3",
                "testdoctype1.headerval == 2.34124e+08");
    verifyParse("testdoctype1.headerval == -234123.523E-3",
                "testdoctype1.headerval == -234.124");

    // Test string value
    verifyParse("testdoctype1.headerval == \"test\"");
    std::unique_ptr<select::Node> node = _parser->parse("testdoctype1.headerval == \"test\"");
    const auto & compnode = dynamic_cast<const select::Compare&>(*node);
    const auto & fnode = dynamic_cast<const select::FieldValueNode&>(compnode.getLeft());
    const auto & vnode = dynamic_cast<const select::StringValueNode&>(compnode.getRight());

    EXPECT_EQ(std::string("headerval"), fnode.getFieldName());
    EXPECT_EQ(std::string("test"), vnode.getValue());
    // Test whitespace
    verifyParse("testdoctype1.headerval == \"te st \"");
    verifyParse(" \t testdoctype1.headerval\t==  \t \"test\"\t",
                "testdoctype1.headerval == \"test\"");

    // Test escaping
    verifyParse("testdoctype1.headerval == \"tab\\ttest\"");
    verifyParse("testdoctype1.headerval == \"tab\\x09test\"",
                "testdoctype1.headerval == \"tab\\ttest\"");
    verifyParse("testdoctype1.headerval == \"tab\\x055test\"");
    node = _parser->parse("testdoctype1.headerval == \"\\tt\\x48 \\n\"");
    select::Compare& escapednode(dynamic_cast<select::Compare&>(*node));
    const auto & escval = dynamic_cast<const select::StringValueNode&>(escapednode.getRight());
    EXPECT_EQ(std::string("\ttH \n"), escval.getValue());
    // Test <= <, > >=
    verifyParse("testdoctype1.headerval >= 123");
    verifyParse("testdoctype1.headerval > 123");
    verifyParse("testdoctype1.headerval <= 123");
    verifyParse("testdoctype1.headerval < 123");
    verifyParse("testdoctype1.headerval != 123");

    // Test defined
    verifyParse("testdoctype1.headerval", "testdoctype1.headerval != null");

    // Test bools
    verifyParse("TRUE", "true");
    verifyParse("FALSE", "false");
    verifyParse("true");
    verifyParse("false");
    verifyParse("faLSe", "false");

    // Test document types
    verifyParse("testdoctype1");
    verifyParse("_test_doctype3_");
    verifyParse("_test_doctype3_._only_in_child_ == 0");

    // Test document id with simple parser.
    verifySimpleParse("id == \"id:ns:mytest\"");
    verifySimpleParse("id.namespace == \"myspace\"");
    verifySimpleParse("id.scheme == \"id\"");
    verifySimpleParse("id.type == \"testdoctype1\"");
    verifySimpleParse("id.group == \"yahoo.com\"");
    verifySimpleParse("id.user == 1234");
    verifySimpleParse("id.user == 0x12456ab", "id.user == 19158699");

    // Test document id
    verifyParse("id == \"id:ns:mytest\"");
    verifyParse("id.namespace == \"myspace\"");
    verifyParse("id.scheme == \"id\"");
    verifyParse("id.type == \"testdoctype1\"");
    verifyParse("id.user == 1234");
    verifyParse("id.user == 0x12456ab", "id.user == 19158699");
    verifyParse("id.group == \"yahoo.com\"");

    verifyParse("id.specific == \"mypart\"");
    verifyParse("id.bucket == 1234");
    verifyParse("id.bucket == 0x800000", "id.bucket == 8388608");
    verifyParse("id.bucket == 0x80a000", "id.bucket == 8429568");
    verifyParse("id.bucket == 0x80000000000000f2",
                "id.bucket == -9223372036854775566");
    verifyParse("id.gid == \"gid(0xd755743aea262650274d70f0)\"");

    // Test other operators
    verifyParse("id.scheme = \"*doc\"");
    verifyParse("testdoctype1.hstringval =~ \"(john|barry|shrek)\"");

    // Verify functions
    verifyParse("id.hash() == 124");
    verifyParse("id.specific.hash() == 124");
    verifyParse("testdoctype1.hstringval.lowercase() == \"chang\"");
    verifyParse("testdoctype1.hstringval.lowercase().hash() == 124");
    verifyParse("testdoctype1.hintval > now()");
    verifyParse("testdoctype1.hintval > now().abs()");

    // Value grouping
    verifyParse("(123) < (200)");
    verifyParse("(\"hmm\") < (id.scheme)");

    // Arithmetics
    verifyParse("1 + 2 > 1");
    verifyParse("1 - 2 > 1");
    verifyParse("1 * 2 > 1");
    verifyParse("1 / 2 > 1");
    verifyParse("1 % 2 > 1");
    verifyParse("(1 + 2) * (4 - 2) == 1");
    verifyParse("23 + 643 / 34 % 10 > 34");

    // CJK stuff
    verifyParse("testdoctype1.hstringval = \"\xE4\xB8\xBA\xE4\xBB\x80\"",
                "testdoctype1.hstringval = \"\\xe4\\xb8\\xba\\xe4\\xbb\\x80\"");

    // Strange doctype names
    verifyParse("notandor");
    verifyParse("ornotand");
    verifyParse("andornot");
    verifyParse("idid");
    verifyParse("usergroup");
    verifyParse("user");
    verifyParse("group");
}

TEST_F(DocumentSelectParserTest, testParseBranches)
{
    createDocs();

    verifyParse("TRUE or FALSE aNd FALSE oR TRUE", "true or false and false or true");
    verifyParse("TRUE and FALSE or FALSE and TRUE", "true and false or false and true");
    verifyParse("TRUE or FALSE and FALSE or TRUE", "true or false and false or true");
    verifyParse("(TRUE or FALSE) and (FALSE or TRUE)", "(true or false) and (false or true)");
    verifyParse("true or (not false) and not true");

    // Test number branching with node branches
    verifyParse("((243) < 300 and (\"FOO\").lowercase() == (\"foo\"))");

    // Strange doctype names
    verifyParse("notandor and ornotand");
    verifyParse("ornotand or andornot");
    verifyParse("not andornot");
    verifyParse("idid or not usergroup");
    verifyParse("not(andornot or idid)", "not (andornot or idid)");
    verifyParse("not user or not group");
}

template <typename ContainsType>
select::ResultList
DocumentSelectParserTest::doParse(std::string_view expr,
                                  const ContainsType& t)
{
    std::unique_ptr<select::Node> root(_parser->parse(std::string(expr)));
    select::ResultList result(root->contains(t));

    std::unique_ptr<select::Node> cloned(root->clone());
    select::ResultList clonedResult(cloned->contains(t));

    std::unique_ptr<select::Node> traced(_parser->parse(std::string(expr)));
    std::ostringstream oss;
    oss << "for expr: " << expr << "\n";
    select::ResultList tracedResult(root->trace(t, oss));

    EXPECT_EQ(result, clonedResult) << expr;
    EXPECT_EQ(result, tracedResult) << oss.str();

    return result;
}

#define PARSE(expr, doc, result) \
    EXPECT_EQ(select::ResultList(select::Result::result),       \
              doParse(expr, (doc))) << expr;

#define PARSEI(expr, doc, result) \
    EXPECT_EQ(select::ResultList(select::Result::result), \
              doParse(expr, (doc))) << (std::string("Doc: ") + expr);  \
    EXPECT_EQ(select::ResultList(select::Result::result), \
              doParse(expr, (doc).getId())) << (std::string("Doc id: ") + expr);

TEST_F(DocumentSelectParserTest, operators_0)
{
    createDocs();

    /* Code for tracing result to see what went wrong
    {
        std::ostringstream ost;
        _parser->parse("id.specific.hash() % 10 = 8")->trace(*_doc[0], ost);
        ost << "\n\n";
        _parser->parse("id.specific.hash() % 10 = 8")
                ->trace(_doc[0]->getId(), ost);
        std::cerr << ost.str() << "\n";
    } // */

    // Check that comparison operators work.
    PARSE("", *_doc[0], True);
    PARSE("30 < 10", *_doc[0], False);
    PARSE("10 < 30", *_doc[0], True);
    PARSE("30 < 10", *_doc[0], False);
    PARSE("10 < 30", *_doc[0], True);
    PARSE("30 <= 10", *_doc[0], False);
    PARSE("10 <= 30", *_doc[0], True);
    PARSE("30 <= 30", *_doc[0], True);
    PARSE("10 >= 30", *_doc[0], False);
    PARSE("30 >= 10", *_doc[0], True);
    PARSE("30 >= 30", *_doc[0], True);

    PARSE("10 > 30", *_doc[0], False);
    PARSE("30 > 10", *_doc[0], True);
    PARSE("30 == 10", *_doc[0], False);
    PARSE("30 == 30", *_doc[0], True);
    PARSE("30 != 10", *_doc[0], True);
    PARSE("30 != 30", *_doc[0], False);
    PARSE("\"foo\" != \"bar\"", *_doc[0], True);
    PARSE("\"foo\" != \"foo\"", *_doc[0], False);
    PARSE("\"foo\" == 'bar'", *_doc[0], False);
    PARSE("\"foo\" == 'foo'", *_doc[0], True);
    PARSE("\"bar\" = \"a\"", *_doc[0], False);
    PARSE("\"bar\" = \"*a*\"", *_doc[0], True);
    PARSE("\"bar\" = \"*x*\"", *_doc[0], False);
    PARSE("\"bar\" = \"ba*\"", *_doc[0], True);
    PARSE("\"bar\" = \"a*\"", *_doc[0], False)
    PARSE("\"bar\" = \"*ar\"", *_doc[0], True);
    PARSE("\"bar\" = \"*a\"", *_doc[0], False);
    PARSE("\"bar\" = \"\"", *_doc[0], False);
    PARSE("\"\" = \"\"", *_doc[0], True);
    PARSE("\"\" = \"*\"", *_doc[0], True);
    PARSE("\"\" = \"****\"", *_doc[0], True);
    PARSE("\"a\" = \"*?*\"", *_doc[0], True);
    PARSE("\"a\" = \"*??*\"", *_doc[0], False);
    PARSE("\"bar\" =~ \"^a$\"", *_doc[0], False);
    PARSE("\"bar\" =~ \"a\"", *_doc[0], True);
    PARSE("\"bar\" =~ \"\"", *_doc[0], True);
    PARSE("\"\" =~ \"\"", *_doc[0], True);
    PARSE("30 = 10", *_doc[0], False);
    PARSE("30 = 30", *_doc[0], True);
}

TEST_F(DocumentSelectParserTest, using_non_commutative_comparison_operator_with_field_value_is_well_defined) {
    auto doc = createDoc("testdoctype1", "id:foo:testdoctype1::bar", 24, 0.0, "foo", "bar", 0);
    // Document's `headerval` field has value of 24.
    PARSE("25 <= testdoctype1.headerval", *doc, False);
    PARSE("24 <= testdoctype1.headerval", *doc, True);
    PARSE("25 > testdoctype1.headerval", *doc, True);
    PARSE("24 > testdoctype1.headerval", *doc, False);
    PARSE("24 >= testdoctype1.headerval", *doc, True);

    PARSE("testdoctype1.headerval <= 23", *doc, False);
    PARSE("testdoctype1.headerval <= 24", *doc, True);
    PARSE("testdoctype1.headerval > 23", *doc, True);
    PARSE("testdoctype1.headerval > 24", *doc, False);
    PARSE("testdoctype1.headerval >= 24", *doc, True);
}

TEST_F(DocumentSelectParserTest, regex_matching_does_not_bind_anchors_to_newlines) {
    createDocs();

    PARSE("\"a\\nb\\nc\" =~ \"^b$\"", *_doc[0], False);
    PARSE("\"a\\r\\nb\\r\\nc\" =~ \"^b$\"", *_doc[0], False);
    // Same applies to implicit regex created from glob expression
    PARSE("\"a\\nb\\nc\" = \"b\"", *_doc[0], False);
}

// With a recursive backtracking regex implementation like that found in (at the time of
// writing) GCC's std::regex implementation, certain expressions on a sufficiently large
// input will cause a stack overflow and send the whole thing spiraling into a flaming
// vortex of doom. See https://gcc.gnu.org/bugzilla/show_bug.cgi?id=86164 for context.
//
// Since crashing the process based on user input is considered bad karma for all the
// obvious reasons, test that the underlying regex engine is not susceptible to such
// crashes.
TEST_F(DocumentSelectParserTest, regex_matching_is_not_susceptible_to_catastrophic_backtracking) {
    std::string long_string(1024*50, 'A'); // -> hstringval field
    auto doc = createDoc("testdoctype1", "id:foo:testdoctype1::bar", 24, 0.0, long_string, "bar", 0);
    // This _will_ crash std::regex on GCC 8.3. Don't try this at home. Unless you want to.
    PARSE(R"(testdoctype1.hstringval =~ ".*")", *doc, True);
}

TEST_F(DocumentSelectParserTest, operators_1)
{
    createDocs();

    // Mix of types should within numbers, but otherwise not match
    PARSE("30 < 10.2", *_doc[0], False);
    PARSE("10.2 < 30", *_doc[0], True);
    PARSE("30 < \"foo\"", *_doc[0], Invalid);
    PARSE("30 > \"foo\"", *_doc[0], Invalid);
    PARSE("30 != \"foo\"", *_doc[0], Invalid);
    PARSE("14.2 <= \"foo\"", *_doc[0], Invalid);
    PARSE("null == null", *_doc[0], True);
    PARSE("null = null", *_doc[0], True);
    PARSE("\"bar\" == null", *_doc[0], False);
    PARSE("14.3 == null", *_doc[0], False);
    PARSE("null = 0", *_doc[0], False);

    // Boolean literals in comparisons
    PARSE("true = true", *_doc[0], True);
    PARSE("true == true", *_doc[0], True);
    PARSE("true == false", *_doc[0], False);
    PARSE("false == false", *_doc[0], True);
    PARSE("true == 1", *_doc[0], True);
    PARSE("true == 0", *_doc[0], False);
    PARSE("false == 1", *_doc[0], False);
    PARSE("false == 0", *_doc[0], True);

    // Field values
    PARSE("testdoctype1.headerval = 24", *_doc[0], True);
    PARSE("testdoctype1.headerval = 24", *_doc[1], False);
    PARSE("testdoctype1.headerval = 13", *_doc[0], False);
    PARSE("testdoctype1.headerval = 13", *_doc[1], True);
    PARSE("testdoctype1.hfloatval = 2.0", *_doc[0], True);
    PARSE("testdoctype1.hfloatval = 1.0", *_doc[1], False);
    PARSE("testdoctype1.hfloatval = 4.1", *_doc[0], False);
    PARSE("testdoctype1.hfloatval > 4.09 and testdoctype1.hfloatval < 4.11",
            *_doc[1], True);
    PARSE("testdoctype1.content = \"bar\"", *_doc[0], True);
    PARSE("testdoctype1.content = \"bar\"", *_doc[1], False);
    PARSE("testdoctype1.content = \"foo\"", *_doc[0], False);
    PARSE("testdoctype1.content = \"foo\"", *_doc[1], True);
    PARSE("testdoctype1.hstringval == testdoctype1.content", *_doc[0], False);
    PARSE("testdoctype1.hstringval == testdoctype1.content", *_doc[2], True);
    PARSE("testdoctype1.byteweightedset == 7", *_doc[1], False);
    PARSE("testdoctype1.byteweightedset == 5", *_doc[1], True);

    // Document types
    PARSE("testdoctype1", *_doc[0], True);
    PARSE("testdoctype2", *_doc[0], False);

    // Inherited doctypes
    PARSE("testdoctype2", *_doc[4], True);
    PARSE("testdoctype2", *_doc[3], False);
    PARSE("testdoctype1", *_doc[4], False); // testdoctype2 inherits testdoctype1, but we use exact matching for "standalone" doctype matches.
    PARSE("testdoctype1.headerval = 10", *_doc[4], True); // But _field lookups_ use is-a type matching semantics.
    PARSE("testdoctype2.headerval = 10", *_doc[4], True); // Exact type match with parent field also works transparently
}

TEST_F(DocumentSelectParserTest, operators_2)
{
    createDocs();

    // Id values
    PARSEI("id == \"id:myspace:testdoctype1::anything\"", *_doc[0], True);
    PARSEI(" iD==  \"id:myspace:testdoctype1::anything\"  ", *_doc[0], True);
    PARSEI("id == \"id:myspa:testdoctype1::nything\"", *_doc[0], False);
    PARSEI("Id.scHeme == \"doc\"", *_doc[0], False);
    PARSEI("id.scheme == \"id\"", *_doc[0], True);
    PARSEI("id.type == \"testdoctype1\"", *_doc[9], True);
    PARSEI("id.type == \"wrong_type\"", *_doc[9], False);
    PARSEI("id.type == \"unknown\"", *_doc[0], False);
    PARSEI("Id.namespaCe == \"myspace\"", *_doc[0], True);
    PARSEI("id.NaMespace == \"pace\"", *_doc[0], False);
    PARSEI("id.specific == \"anything\"", *_doc[0], True);
    PARSEI("id.user=1234", *_doc[2], True);
    PARSEI("id.user == 1234", *_doc[0], Invalid);
    PARSEI("id.group == 1234", *_doc[3], Invalid);
    PARSEI("id.group == \"yahoo\"", *_doc[3], True);
    PARSEI("id.bucket == 1234", *_doc[0], False);
    PARSEI("id.user=12345", *_doc[9], True);
    PARSEI("id.group == \"xyzzy\"", *_doc[10], True);
}

TEST_F(DocumentSelectParserTest, operators_3)
{
    createDocs();
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(16, 0xe1f0).getId() ;
        PARSEI(ost.str(), *_doc[0], True);
    }
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(18, 0xe1f0).getId() ;
        PARSEI(ost.str(), *_doc[0], False);
    }
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(18, 0x2e1f0).getId() ;
        PARSEI(ost.str(), *_doc[0], True);
    }
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(16, 1234).getId() ;
        PARSEI(ost.str(), *_doc[0], False);
    }

    PARSEI("id.bucket == \"foo\"", *_doc[0], Invalid);

    std::string gidmatcher = "id.gid == \"" + _doc[0]->getId().getGlobalId().toString() + "\"";
    PARSEI(gidmatcher, *_doc[0], True);

    PARSEI("id.user=123456789 and id = \"id:footype:testdoctype1:n=123456789:aardvark\"", *_doc[5], True);
    PARSEI("id == \"id:footype:testdoctype1:n=123456789:badger\"", *_doc[5], False);
}

TEST_F(DocumentSelectParserTest, operators_4)
{
    createDocs();

    // Branch operators
    PARSEI("true and false", *_doc[0], False);
    PARSEI("true and true", *_doc[0], True);
    PARSEI("true or false", *_doc[0], True);
    PARSEI("false or false", *_doc[0], False);
    PARSEI("false and true or true and true", *_doc[0], True);
    PARSEI("false or true and true or false", *_doc[0], True);
    PARSEI("not false", *_doc[0], True);
    PARSEI("not true", *_doc[0], False);
    PARSEI("true and not false or false", *_doc[0], True);
    PARSEI("((243 < 300) and (\"FOO\".lowercase() == \"foo\"))", *_doc[0], True);

    // Invalid branching. testdoctype1.content = 1 is invalid
    PARSE("testdoctype1.content = 1 and true", *_doc[0], Invalid);
    PARSE("testdoctype1.content = 1 or true", *_doc[0], True);
    PARSE("testdoctype1.content = 1 and false", *_doc[0], False);
    PARSE("testdoctype1.content = 1 or false", *_doc[0], Invalid);
    PARSE("true and testdoctype1.content = 1", *_doc[0], Invalid);
    PARSE("true or testdoctype1.content = 1", *_doc[0], True);
    PARSE("false and testdoctype1.content = 1", *_doc[0], False);
    PARSE("false or testdoctype1.content = 1", *_doc[0], Invalid);
}

TEST_F(DocumentSelectParserTest, operators_5)
{
    createDocs();

    // Functions
    PARSE("testdoctype1.hstringval.lowercase() == \"Yet\"", *_doc[3], False);
    PARSE("testdoctype1.hstringval.lowercase() == \"yet\"", *_doc[3], True);
    PARSE("testdoctype1.hfloatval.lowercase() == \"yet\"", *_doc[3], Invalid);
    PARSEI("\"bar\".hash() == -2012135647395072713", *_doc[0], True);
    PARSEI("\"bar\".hash().abs() == 2012135647395072713", *_doc[0], True);
    PARSEI("null.hash() == 123", *_doc[0], Invalid);
    PARSEI("(0.234).hash() == 123", *_doc[0], False);
    PARSEI("(0.234).lowercase() == 123", *_doc[0], Invalid);
    PARSE("\"foo\".hash() == 123", *_doc[0], False);
    PARSEI("(234).hash() == 123", *_doc[0], False);
    PARSE("now() > 1311862500", *_doc[8], True);
    PARSE("now() < 1911862500", *_doc[8], True);
    PARSE("now() < 1311862500", *_doc[8], False);
    PARSE("now() > 1911862500", *_doc[8], False);

    // Arithmetics
    PARSEI("id.specific.hash() % 10 = 8", *_doc[0], True);
    PARSEI("id.specific.hash() % 10 = 2", *_doc[0], False);
    PARSEI("\"foo\" + \"bar\" = \"foobar\"", *_doc[0], True);
    PARSEI("\"foo\" + 4 = 25", *_doc[0], Invalid);
    PARSEI("34.0 % 4 = 4", *_doc[0], Invalid);
    PARSEI("-6 % 10 = -6", *_doc[0], True);
}

TEST_F(DocumentSelectParserTest, operators_6)
{
    createDocs();

    // CJK
    // Assuming the characters " \ ? * is not used as part of CJK tokens
    PARSE("testdoctype1.content=\"\xE4\xB8\xBA\xE4\xBB\x80\"", *_doc[3], True);
    PARSE("testdoctype1.content=\"\xE4\xB7\xBA\xE4\xBB\x80\"", *_doc[3], False);

    // Structs and arrays
    PARSE("testdoctype1.mystruct", *_doc[0], False);
    PARSE("testdoctype1.mystruct", *_doc[1], True);
    PARSE("(testdoctype1.mystruct)", *_doc[0], False);
    PARSE("(testdoctype1.mystruct)", *_doc[1], True);
    PARSE("(((testdoctype1.mystruct)))", *_doc[0], False);
    PARSE("(((testdoctype1.mystruct)))", *_doc[1], True);
    PARSE("testdoctype1.mystruct", *_doc[2], False);
    PARSE("testdoctype1.mystruct == testdoctype1.mystruct", *_doc[0], True);
    PARSE("testdoctype1.mystruct == testdoctype1.mystruct", *_doc[1], True);
    PARSE("testdoctype1.mystruct != testdoctype1.mystruct", *_doc[0], False);
    PARSE("testdoctype1.mystruct != testdoctype1.mystruct", *_doc[1], False);
    PARSE("testdoctype1.mystruct < testdoctype1.mystruct", *_doc[0], Invalid);
    PARSE("testdoctype1.mystruct < testdoctype1.mystruct", *_doc[1], False);
    PARSE("testdoctype1.mystruct < 5", *_doc[1], False);
    //  PARSE("testdoctype1.mystruct == \"foo\"", *_doc[1], Invalid);
    PARSE("testdoctype1.mystruct.key == 14", *_doc[0], False);
    PARSE("testdoctype1.mystruct.value == \"structval\"", *_doc[0], False);
    PARSE("testdoctype1.mystruct.key == 14", *_doc[1], True);
    PARSE("testdoctype1.mystruct.value == \"structval\"", *_doc[1], True);
    PARSE("testdoctype1.structarray", *_doc[0], False);
    PARSE("testdoctype1.structarray", *_doc[1], True);
    PARSE("testdoctype1.structarray", *_doc[2], False);
    PARSE("testdoctype1.structarray == testdoctype1.structarray",
          *_doc[0], True);
    PARSE("testdoctype1.structarray < testdoctype1.structarray",
          *_doc[0], Invalid);
    PARSE("testdoctype1.structarray == testdoctype1.structarray",
          *_doc[1], True);
    PARSE("testdoctype1.structarray < testdoctype1.structarray",
          *_doc[1], False);
    PARSE("testdoctype1.headerlongval<0", *_doc[6], False);
    PARSE("testdoctype1.headerlongval<0", *_doc[7], True);
}

TEST_F(DocumentSelectParserTest, operators_7)
{
    createDocs();

    PARSE("testdoctype1.structarray.key == 15", *_doc[0], False);
    PARSE("testdoctype1.structarray[4].key == 15", *_doc[0], False);
    PARSE("testdoctype1.structarray", *_doc[1], True);
    PARSE("testdoctype1.structarray.key == 15", *_doc[1], True);
    PARSE("testdoctype1.structarray[1].key == 16", *_doc[1], True);
    PARSE("testdoctype1.structarray[1].key", *_doc[1], True); // "key is set?" expr
    PARSE("testdoctype1.structarray[1].key = 16", *_doc[1], True);
    PARSE("testdoctype1.structarray.value == \"structval1\"", *_doc[0], False);
    PARSE("testdoctype1.structarray[4].value == \"structval1\"", *_doc[0], False);
    PARSE("testdoctype1.structarray.value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarray[0].value == \"structval1\"", *_doc[1], True);
    // Globbing of array-of-struct fields
    PARSE("testdoctype1.structarray.key = 15", *_doc[0], False);
    PARSE("testdoctype1.structarray.key = 15", *_doc[2], False);
    PARSE("testdoctype1.structarray.key = 15", *_doc[1], True);
    PARSE("testdoctype1.structarray.value = \"structval2\"", *_doc[2], Invalid); // Invalid due to lhs being NullValue
    PARSE("testdoctype1.structarray.value = \"*ctval*\"", *_doc[1], True);
    PARSE("testdoctype1.structarray[1].value = \"structval2\"", *_doc[1], True);
    PARSE("testdoctype1.structarray[1].value = \"batman\"", *_doc[1], False);
    // Regexp of array-of-struct fields
    PARSE("testdoctype1.structarray.value =~ \"structval[1-9]\"", *_doc[1], True);
    PARSE("testdoctype1.structarray.value =~ \"structval[a-z]\"", *_doc[1], False);
    // Globbing/regexp of struct fields
    PARSE("testdoctype1.mystruct.value = \"struc?val\"", *_doc[0], Invalid); // Invalid due to lhs being NullValue
    PARSE("testdoctype1.mystruct.value = \"struc?val\"", *_doc[1], True);
    PARSE("testdoctype1.mystruct.value =~ \"struct.*\"", *_doc[0], Invalid); // Ditto here
    PARSE("testdoctype1.mystruct.value =~ \"struct.*\"", *_doc[1], True);

    PARSE("testdoctype1.structarray[$x].key == 15 AND testdoctype1.structarray[$x].value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarray[$x].key == 15 AND testdoctype1.structarray[$x].value == \"structval2\"", *_doc[1], False);
    PARSE("testdoctype1.structarray[$x].key == 15 AND testdoctype1.structarray[$y].value == \"structval2\"", *_doc[1], True);
}

TEST_F(DocumentSelectParserTest, operators_8)
{
    createDocs();

    PARSE("testdoctype1.mymap", *_doc[0], False);
    PARSE("testdoctype1.mymap", *_doc[1], True);
    PARSE("testdoctype1.mymap{3}", *_doc[1], True);
    PARSE("testdoctype1.mymap{9}", *_doc[1], False);
    PARSE("testdoctype1.mymap{3} == \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap{3} == \"b\"", *_doc[1], False);
    PARSE("testdoctype1.mymap{9} == \"b\"", *_doc[1], False);
    PARSE("testdoctype1.mymap.value == \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap.value == \"d\"", *_doc[1], False);
    PARSE("testdoctype1.mymap{3} = \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap{3} = \"b\"", *_doc[1], False);
    PARSE("testdoctype1.mymap{3} =~ \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap{3} =~ \"b\"", *_doc[1], False);
    PARSE("testdoctype1.mymap.value = \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap.value = \"d\"", *_doc[1], False);
    PARSE("testdoctype1.mymap.value =~ \"a\"", *_doc[1], True);
    PARSE("testdoctype1.mymap.value =~ \"d\"", *_doc[1], False);
    PARSE("testdoctype1.mymap == 3", *_doc[1], True);
    PARSE("testdoctype1.mymap == 4", *_doc[1], False);
    PARSE("testdoctype1.mymap = 3", *_doc[1], True); // Fallback to ==
    PARSE("testdoctype1.mymap = 4", *_doc[1], False); // Fallback to ==

    PARSE("testdoctype1.structarrmap{\"a key needing escaping\"}", *_doc[1], True);
    PARSE("testdoctype1.structarrmap{\"a key needing escaping\"}", *_doc[0], False);

    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$x}[$y].value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarrmap.value[$y].key == 15 AND testdoctype1.structarrmap.value[$y].value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$x}[$y].value == \"structval2\"", *_doc[1], False);
    PARSE("testdoctype1.structarrmap.value[$y].key == 15 AND testdoctype1.structarrmap.value[$y].value == \"structval2\"", *_doc[1], False);
    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$y}[$x].value == \"structval2\"", *_doc[1], False);
}

TEST_F(DocumentSelectParserTest, operators_9)
{
    createDocs();

    PARSE("testdoctype1.stringweightedset", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset{val1}", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset{val1} == 1", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset{val1} == 2", *_doc[1], False);
    PARSE("testdoctype1.stringweightedset == \"val1\"", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset = \"val*\"", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset =~ \"val[0-9]\"", *_doc[1], True);
    PARSE("testdoctype1.stringweightedset == \"val5\"", *_doc[1], False);
    PARSE("testdoctype1.stringweightedset = \"val5\"", *_doc[1], False);
    PARSE("testdoctype1.stringweightedset =~ \"val5\"", *_doc[1], False);

    PARSE("testdoctype1.structarrmap{$x}.key == 15 AND testdoctype1.stringweightedset{$x}", *_doc[1], True);
    PARSE("testdoctype1.structarrmap{$x}.key == 17 AND testdoctype1.stringweightedset{$x}", *_doc[1], False);

    PARSE("testdoctype1.structarray.key < 16", *_doc[1], True);
    PARSE("testdoctype1.structarray.key < 15", *_doc[1], False);
    PARSE("testdoctype1.structarray.key > 15", *_doc[1], True);
    PARSE("testdoctype1.structarray.key > 16", *_doc[1], False);
    PARSE("testdoctype1.structarray.key <= 15", *_doc[1], True);
    PARSE("testdoctype1.structarray.key <= 14", *_doc[1], False);
    PARSE("testdoctype1.structarray.key >= 16", *_doc[1], True);
    PARSE("testdoctype1.structarray.key >= 17", *_doc[1], False);
}

TEST_F(DocumentSelectParserTest, can_use_boolean_fields_in_expressions) {
    createDocs();
    // Doc 11 has bool field set explicitly to true, doc 12 has field explicitly set to false
    PARSE("testdoctype1.boolfield == 1", *_doc[11], True);
    PARSE("testdoctype1.boolfield == true", *_doc[11], True);
    PARSE("testdoctype1.boolfield == 1", *_doc[12], False);
    PARSE("testdoctype1.boolfield == true", *_doc[12], False);
    PARSE("testdoctype1.boolfield == 0", *_doc[12], True);
    PARSE("testdoctype1.boolfield == false", *_doc[12], True);
    // FIXME very un-intuitive behavior when nulls are implicitly returned:
    // Doc 1 does not have the bool field set, but the implicit null value is neither true nor false
    PARSE("testdoctype1.boolfield == 1", *_doc[1], False);
    PARSE("testdoctype1.boolfield == true", *_doc[1], False);
    PARSE("testdoctype1.boolfield == 0", *_doc[1], False);
    PARSE("testdoctype1.boolfield == false", *_doc[1], False);
}

// Note: no support for checking tensor field _contents_, only their presence
TEST_F(DocumentSelectParserTest, tensor_fields_can_be_null_checked_in_expressions) {
    createDocs();
    // Doc 1 has `dense_tensor` field set, the rest have no tensor fields set
    PARSE("testdoctype1.dense_tensor != null", *_doc[1], True);
    PARSE("null != testdoctype1.dense_tensor", *_doc[1], True);
    PARSE("testdoctype1.dense_tensor", *_doc[1], True);
    PARSE("testdoctype1.dense_tensor == null", *_doc[1], False);
    PARSE("null == testdoctype1.dense_tensor", *_doc[1], False);
    // No tensor fields set in doc 0
    PARSE("testdoctype1.dense_tensor != null", *_doc[0], False);
    PARSE("testdoctype1.dense_tensor == null", *_doc[0], True);
    PARSE("testdoctype1.dense_tensor", *_doc[0], False);
    PARSE("not testdoctype1.dense_tensor", *_doc[0], True);
    PARSE("testdoctype1.sparse_tensor == null", *_doc[0], True);
    PARSE("testdoctype1.sparse_tensor != null", *_doc[0], False);

    // Tensors are not defined for any other operations than presence checks
    PARSE("testdoctype1.dense_tensor == 1234", *_doc[1], Invalid);
    PARSE("testdoctype1.dense_tensor != false", *_doc[1], Invalid);
    // ... not even identity checks
    PARSE("testdoctype1.dense_tensor == testdoctype1.dense_tensor", *_doc[1], Invalid);
    PARSE("testdoctype1.dense_tensor != testdoctype1.dense_tensor", *_doc[1], Invalid);
    // ... unless the fields are not set, in which case identity checks will succeed
    // since the expression degenerates to comparing null values.
    PARSE("testdoctype1.dense_tensor == testdoctype1.dense_tensor", *_doc[0], True);
    PARSE("testdoctype1.dense_tensor != testdoctype1.dense_tensor", *_doc[0], False);
}

namespace {

    class TestVisitor : public select::Visitor {
    private:
        std::ostringstream data;

    public:
        ~TestVisitor() override {}

        void visitConstant(const select::Constant& node) override {
            data << "CONSTANT(" << node << ")";
        }

        void
        visitInvalidConstant(const select::InvalidConstant& node) override {
            data << "INVALIDCONSTANT(" << node << ")";
        }

        void visitDocumentType(const select::DocType& node) override {
            data << "DOCTYPE(" << node << ")";
        }

        void visitComparison(const select::Compare& node) override {
            data << "COMPARE(" << node.getLeft() << " "
                 << node.getOperator() << " " << node.getRight() << ")";
        }

        void visitAndBranch(const select::And& node) override {
            data << "AND(";
            node.getLeft().visit(*this);
            data << ", ";
            node.getRight().visit(*this);
            data << ")";
        }

        void visitOrBranch(const select::Or& node) override {
            data << "OR(";
            node.getLeft().visit(*this);
            data << ", ";
            node.getRight().visit(*this);
            data << ")";
        }

        void visitNotBranch(const select::Not& node) override {
            data << "NOT(";
            node.getChild().visit(*this);
            data << ")";
        }

        void visitArithmeticValueNode(const select::ArithmeticValueNode &) override {}
        void visitFunctionValueNode(const select::FunctionValueNode &) override {}
        void visitIdValueNode(const select::IdValueNode &) override {}
        void visitFieldValueNode(const select::FieldValueNode &) override {}
        void visitFloatValueNode(const select::FloatValueNode &) override {}
        void visitVariableValueNode(const select::VariableValueNode &) override {}
        void visitIntegerValueNode(const select::IntegerValueNode &) override {}
        void visitBoolValueNode(const select::BoolValueNode &) override {}
        void visitCurrentTimeValueNode(const select::CurrentTimeValueNode &) override {}
        void visitStringValueNode(const select::StringValueNode &) override {}
        void visitNullValueNode(const select::NullValueNode &) override {}
        void visitInvalidValueNode(const select::InvalidValueNode &) override {}

        std::string getVisitString() { return data.str(); }
    };

}

TEST_F(DocumentSelectParserTest, testVisitor)
{
    createDocs();

    std::unique_ptr<select::Node> root(_parser->parse(
        "true or testdoctype1 and (not id.user = 12 or testdoctype1.hstringval = \"ola\") and "
        "testdoctype1.headerval"));

    TestVisitor v;
    root->visit(v);

    std::string expected =
            "OR(CONSTANT(true), "
               "AND(AND(DOCTYPE(testdoctype1), "
                       "OR(NOT(COMPARE(id.user = 12)), "
                          "COMPARE(testdoctype1.hstringval = \"ola\"))), "
                   "COMPARE(testdoctype1.headerval != null)))";

    EXPECT_EQ(expected, v.getVisitString());
}

TEST_F(DocumentSelectParserTest, testBodyFieldDetection)
{

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        EXPECT_FALSE(detector.foundBodyField);
        EXPECT_TRUE(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1 AND id.user=1234"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        EXPECT_FALSE(detector.foundBodyField);
        EXPECT_TRUE(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1.headerval=123"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        EXPECT_FALSE(detector.foundBodyField);
        EXPECT_TRUE(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1.content"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        EXPECT_FALSE(detector.foundBodyField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse(
                                                 "true or testdoctype1 and (not id.user = 12 or testdoctype1.hstringval = \"ola\") and "
                                                 "testdoctype1.headerval"));

        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        EXPECT_FALSE(detector.foundBodyField);
    }

}

TEST_F(DocumentSelectParserTest, testDocumentUpdates0)
{
    createDocs();

    /* Code for tracing result to see what went wrong
    {
        std::ostringstream ost;
        _parser->parse("id.bucket == 4006")->trace(*_update[0], ost);
        ost << "\n\n";
        _parser->parse("id.bucket == 4006")
                ->trace(_update[0]->getId(), ost);
        std::cerr << ost.str() << "\n";
    } // */
    PARSEI("", *_update[0], True);
    PARSEI("30 < 10", *_update[0], False);
    PARSEI("10 < 30", *_update[0], True);
    PARSEI("30 < 10", *_update[0], False);
    PARSEI("10 < 30", *_update[0], True);
    PARSEI("30 <= 10", *_update[0], False);
    PARSEI("10 <= 30", *_update[0], True);
    PARSEI("30 <= 30", *_update[0], True);
    PARSEI("10 >= 30", *_update[0], False);
    PARSEI("30 >= 10", *_update[0], True);
    PARSEI("30 >= 30", *_update[0], True);
    PARSEI("10 > 30", *_update[0], False);
    PARSEI("30 > 10", *_update[0], True);
    PARSEI("30 == 10", *_update[0], False);
    PARSEI("30 == 30", *_update[0], True);
    PARSEI("30 != 10", *_update[0], True);
    PARSEI("30 != 30", *_update[0], False);
    PARSEI("\"foo\" != \"bar\"", *_update[0], True);
    PARSEI("\"foo\" != \"foo\"", *_update[0], False);
    PARSEI("\"foo\" == \"bar\"", *_update[0], False);
    PARSEI("\"foo\" == \"foo\"", *_update[0], True);
    PARSEI("\"bar\" = \"a\"", *_update[0], False);
    PARSEI("\"bar\" = \"*a*\"", *_update[0], True);
    PARSEI("\"bar\" = \"**\"", *_update[0], True);
    PARSEI("\"bar\" = \"***\"", *_update[0], True);
    PARSEI("\"bar\" = \"****\"", *_update[0], True);
    PARSEI("\"bar\" = \"???\"", *_update[0], True);
    PARSEI("\"bar\" = \"????\"", *_update[0], False);
    PARSEI("\"bar\" = \"\"", *_update[0], False);
    PARSEI("\"\" = \"\"", *_update[0], True);
    PARSEI("\"bar\" =~ \"^a$\"", *_update[0], False);
    PARSEI("\"bar\" =~ \"a\"", *_update[0], True);
    PARSEI("\"bar\" =~ \"\"", *_update[0], True);
    PARSEI("\"\" =~ \"\"", *_update[0], True);
    PARSEI("30 = 10", *_update[0], False);
    PARSEI("30 = 30", *_update[0], True);
    PARSEI("(30 = 10)", *_update[0], False);
    PARSEI("(30 = 30)", *_update[0], True);
}

TEST_F(DocumentSelectParserTest, testDocumentUpdates1)
{
    createDocs();

    // Mix of types should within numbers, but otherwise not match
    PARSEI("30 < 10.2", *_update[0], False);
    PARSEI("10.2 < 30", *_update[0], True);
    PARSEI("30 < \"foo\"", *_update[0], Invalid);
    PARSEI("30 > \"foo\"", *_update[0], Invalid);
    PARSEI("30 != \"foo\"", *_update[0], Invalid);
    PARSEI("14.2 <= \"foo\"", *_update[0], Invalid);
    PARSEI("null == null", *_update[0], True);
    PARSEI("null = null", *_update[0], True);
    PARSEI("\"bar\" == null", *_update[0], False);
    PARSEI("14.3 == null", *_update[0], False);
    PARSEI("null = 0", *_update[0], False);

    // Field values
    PARSE("testdoctype1.headerval = 24", *_update[0], Invalid);
    PARSE("testdoctype1.hfloatval = 2.0", *_update[0], Invalid);
    PARSE("testdoctype1.content = \"bar\"", *_update[0], Invalid);
    PARSE("testdoctype1.hstringval == testdoctype1.content", *_update[0], Invalid);

    // Document types
    PARSE("testdoctype1", *_update[0], True);
    PARSE("(testdoctype1)", *_update[0], True);
    PARSE("testdoctype2", *_update[0], False);

    // Inherited doctypes
    PARSE("testdoctype2", *_update[4], True);
    PARSE("testdoctype2", *_update[3], False);
    PARSE("testdoctype1", *_update[4], False); // testdoctype2 inherits testdoctype1, but we use exact matching for types
    PARSE("testdoctype1.headerval = 10", *_update[4], Invalid);
}

TEST_F(DocumentSelectParserTest, testDocumentUpdates2)
{
    createDocs();

    // Id values
    PARSEI("id == \"id:myspace:testdoctype1::anything\"", *_update[0], True);
    PARSEI(" iD==  \"id:myspace:testdoctype1::anything\"  ", *_update[0], True);
    PARSEI("id == \"id:myspa:testdoctype1::nything\"", *_update[0], False);
    PARSEI("Id.scHeme == \"doc\"", *_update[0], False);
    PARSEI("id.scheme == \"id\"", *_update[0], True);
    PARSEI("Id.namespaCe == \"myspace\"", *_update[0], True);
    PARSEI("id.NaMespace == \"pace\"", *_update[0], False);
    PARSEI("id.specific == \"anything\"", *_update[0], True);
    PARSEI("id.user=1234", *_update[2], True);
    PARSEI("id.user == 1234", *_update[0], Invalid);
    PARSEI("id.group == 1234", *_update[3], Invalid);
    PARSEI("id.group == \"yahoo\"", *_update[3], True);
    PARSEI("id.bucket == 1234", *_update[0], False);
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(16, 0xe1f0).getId();
        PARSEI(ost.str(), *_update[0], True);
    }
    PARSEI("id.bucket == \"foo\"", *_update[0], Invalid);
}

void DocumentSelectParserTest::testDocumentUpdates3()
{
    createDocs();

    // Branch operators
    PARSEI("true and false", *_update[0], False);
    PARSEI("true and true", *_update[0], True);
    PARSEI("true or false", *_update[0], True);
    PARSEI("false or false", *_update[0], False);
    PARSEI("false and true or true and true", *_update[0], True);
    PARSEI("false or true and true or false", *_update[0], True);
    PARSEI("not false", *_update[0], True);
    PARSEI("not true", *_update[0], False);
    PARSEI("true and not false or false", *_update[0], True);
    PARSEI("((243 < 300) and (\"FOO\".lowercase() == \"foo\"))", *_update[0], True);

    // Invalid branching. testdoctype1.content = 1 is invalid
    PARSE("testdoctype1.content = 1 and true", *_update[0], Invalid);
    PARSE("testdoctype1.content = 1 or true", *_update[0], True);
    PARSE("testdoctype1.content = 1 and false", *_update[0], False);
    PARSE("testdoctype1.content = 1 or false", *_update[0], Invalid);
    PARSE("true and testdoctype1.content = 1", *_update[0], Invalid);
    PARSE("true or testdoctype1.content = 1", *_update[0], True);
    PARSE("false and testdoctype1.content = 1", *_update[0], False);
    PARSE("false or testdoctype1.content = 1", *_update[0], Invalid);
}

TEST_F(DocumentSelectParserTest, testDocumentUpdates4)
{
    createDocs();

    // Functions
    PARSEI("\"bar\".hash() == -2012135647395072713", *_update[0], True);
    PARSEI("\"bar\".hash().abs() == 2012135647395072713", *_update[0], True);
    PARSEI("null.hash() == 123", *_update[0], Invalid);
    PARSEI("(0.234).hash() == 123", *_update[0], False);
    PARSEI("(0.234).lowercase() == 123", *_update[0], Invalid);
    PARSEI("\"foo\".hash() == 123", *_update[0], False);
    PARSEI("(234).hash() == 123", *_update[0], False);

    // Arithmetics
    PARSEI("id.specific.hash() % 10 = 8", *_update[0], True);
    PARSEI("id.specific.hash() % 10 = 2", *_update[0], False);
    PARSEI("\"foo\" + \"bar\" = \"foobar\"", *_update[0], True);
    PARSEI("\"foo\" + 4 = 25", *_update[0], Invalid);
    PARSEI("34.0 % 4 = 4", *_update[0], Invalid);
    PARSEI("-6 % 10 = -6", *_update[0], True);
}

TEST_F(DocumentSelectParserTest, testDocumentIdsInRemoves)
{
    PARSE("testdoctype1", DocumentId("id:ns:testdoctype1::1"), True);
    PARSE("testdoctype1", DocumentId("id:ns:null::1"), False);
    PARSE("testdoctype1", DocumentId("id::testdoctype2:n=1234:1"), False);
    PARSE("testdoctype1.headerval", DocumentId("id:ns:testdoctype1::1"), Invalid);
    // FIXME: Should ideally be False. As long as there always is an AND node with doctype in a selection expression
    // we won't end up sending removes using the wrong route.
    PARSE("testdoctype1.headerval", DocumentId("id:ns:null::1"), Invalid);
    PARSE("testdoctype1.headerval == 0", DocumentId("id:ns:testdoctype1::1"), Invalid);
    PARSE("testdoctype1 and testdoctype1.headerval == 0", DocumentId("id:ns:testdoctype1::1"), Invalid);
}

TEST_F(DocumentSelectParserTest, testUtf8)
{
    createDocs();
    std::string utf8name = u8"Håkon"_C;
    EXPECT_EQ(size_t(6), utf8name.size());

    /// \todo TODO (was warning):  UTF8 test for glob/regex support in selection language disabled. Known not to work
//    boost::u32regex rx = boost::make_u32regex("H.kon");
//    EXPECT_EQ(true, boost::u32regex_match(utf8name, rx));

    _doc.push_back(createDoc("testdoctype1", "id:myspace:testdoctype1::utf8doc", 24, 2.0, utf8name, "bar"));
//    PARSE("testdoctype1.hstringval = \"H?kon\"", *_doc[_doc.size()-1], True);
//    PARSE("testdoctype1.hstringval =~ \"H.kon\"", *_doc[_doc.size()-1], True);
}

std::unique_ptr<select::FieldValueNode>
DocumentSelectParserTest::parseFieldValue(const std::string& expression) {
    return std::unique_ptr<select::FieldValueNode>(dynamic_cast<select::FieldValueNode *>(
        dynamic_cast<const select::Compare &>(*_parser->parse(expression)).getLeft().clone().release()));
}

TEST_F(DocumentSelectParserTest, testThatSimpleFieldValuesHaveCorrectFieldName)
{
    EXPECT_EQ(
        std::string("headerval"),
        parseFieldValue("testdoctype1.headerval")->getRealFieldName());
}

TEST_F(DocumentSelectParserTest, testThatComplexFieldValuesHaveCorrectFieldNames)
{
    EXPECT_EQ(std::string("headerval"),
              parseFieldValue("testdoctype1.headerval{test}")->getRealFieldName());

    EXPECT_EQ(std::string("headerval"),
              parseFieldValue("testdoctype1.headerval[42]")->getRealFieldName());

    EXPECT_EQ(std::string("headerval"),
              parseFieldValue("testdoctype1.headerval.meow.meow{test}")->getRealFieldName());

    EXPECT_EQ(std::string("headerval"),
              parseFieldValue("testdoctype1.headerval .meow.meow{test}")->getRealFieldName());
}

namespace {

class OperatorVisitor : public select::Visitor {
private:
    std::ostringstream data;
public:
    void visitConstant(const select::Constant& node) override {
        data << node;
    }

    void
    visitInvalidConstant(const select::InvalidConstant& node) override {
        (void) node;
        assert(false);
    }

    void visitDocumentType(const select::DocType& node) override {
        data << "(DOCTYPE " << node << ")";
    }

    void visitComparison(const select::Compare& node) override {
        data << '(' << node.getOperator() << ' ';
        node.getLeft().visit(*this);
        data << ' ';
        node.getRight().visit(*this);
        data << ')';
    }

    void visitAndBranch(const select::And& node) override {
        data << "(AND ";
        node.getLeft().visit(*this);
        data << " ";
        node.getRight().visit(*this);
        data << ")";
    }

    void visitOrBranch(const select::Or& node) override {
        data << "(OR ";
        node.getLeft().visit(*this);
        data << " ";
        node.getRight().visit(*this);
        data << ")";
    }

    void visitNotBranch(const select::Not& node) override {
        data << "(NOT ";
        node.getChild().visit(*this);
        data << ")";
    }

    void visitArithmeticValueNode(const select::ArithmeticValueNode& node) override {
        data << '(' << node.getOperatorName() << ' ';
        node.getLeft().visit(*this);
        data << ' ';
        node.getRight().visit(*this);
        data << ')';
    }
    void visitFunctionValueNode(const select::FunctionValueNode& node) override {
        data << '(' << node.getFunctionName() << ' ';
        node.getChild().visit(*this);
        data << ')';
    }
    void visitIdValueNode(const select::IdValueNode& node) override {
        data << "(ID " << node.toString() << ')';
    }
    void visitFieldValueNode(const select::FieldValueNode& node) override {
        data << "(FIELD " << node.getDocType() << ' ' << node.getFieldName() << ')';
    }
    void visitFloatValueNode(const select::FloatValueNode& node) override {
        data << node.getValue();
    }
    void visitVariableValueNode(const select::VariableValueNode& node) override {
        data << "(VAR " << node.getVariableName() << ')';
    }
    void visitIntegerValueNode(const select::IntegerValueNode& node) override {
        data << node.getValue();
    }
    void visitBoolValueNode(const select::BoolValueNode& node) override {
        data << node.bool_value_str();
    }
    void visitCurrentTimeValueNode(const select::CurrentTimeValueNode&) override {}
    void visitStringValueNode(const select::StringValueNode& str) override {
        data << '"' << str.getValue() << '"';
    }
    void visitNullValueNode(const select::NullValueNode&) override {
        data << "null";
    }
    void visitInvalidValueNode(const select::InvalidValueNode&) override {
        data << "INVALID!";
    }

    std::string visit_string() { return data.str(); }
};

template <typename NodeType>
std::string node_to_string(const NodeType& node) {
    OperatorVisitor v;
    node.visit(v);
    return v.visit_string();
}

}

std::string DocumentSelectParserTest::parse_to_tree(const std::string& str) {
    std::unique_ptr<select::Node> root(_parser->parse(str));
    return node_to_string(*root);
}

TEST_F(DocumentSelectParserTest, test_operator_precedence)
{
    createDocs();
    using namespace std::string_literals;

    EXPECT_EQ("(AND true false)"s, parse_to_tree("true and false"));
    EXPECT_EQ("(AND (NOT false) true)"s, parse_to_tree("not false and true"));
    EXPECT_EQ("(NOT (AND false true))"s, parse_to_tree("not (false and true)"));
    EXPECT_EQ("(NOT (DOCTYPE testdoctype1))"s, parse_to_tree("not testdoctype1"));
    EXPECT_EQ("(NOT (DOCTYPE (testdoctype1)))"s, parse_to_tree("not (testdoctype1)"));
    EXPECT_EQ("(NOT (DOCTYPE (testdoctype1)))"s, parse_to_tree("(not (testdoctype1))"));
    EXPECT_EQ("(OR (== 1 2) (== 3 4))"s, parse_to_tree("1==2 or 3==4"));
    EXPECT_EQ("(!= (+ (+ 1 2) 3) 0)"s, parse_to_tree("1+2+3 != 0"));
    EXPECT_EQ("(!= (+ (+ 1.1 2.2) 3.3) 4.4)"s, parse_to_tree("1.1+2.2+3.3 != 4.4"));
    EXPECT_EQ("(!= (- (- 1 2) 3) 0)"s, parse_to_tree("1-2-3 != 0"));
    EXPECT_EQ("(!= (+ (+ 1 2) 3) 0)"s, parse_to_tree("1 + 2 + 3 != 0"));
    EXPECT_EQ("(!= (+ 1 (* 2 3)) 0)"s, parse_to_tree("1 + 2 * 3 != 0"));
    EXPECT_EQ("(!= (- (/ (* 1 2) 3) 4) 0)"s, parse_to_tree("1 * 2 / 3 - 4 != 0"));
    EXPECT_EQ("(!= (/ (* 1 2) (- 3 4)) 0)"s, parse_to_tree("1 * 2 / (3 - 4) != 0"));
    EXPECT_EQ("(OR (AND true (NOT (== 1 2))) false)"s,
                         parse_to_tree("true and not 1 == 2 or false"));
    EXPECT_EQ("(AND (AND (AND (< 1 2) (> 3 4)) (<= 5 6)) (>= 7 8))"s,
                         parse_to_tree("1 < 2 and 3 > 4 and 5 <= 6 and 7 >= 8"));
    EXPECT_EQ("(OR (AND (AND (< 1 2) (> 3 4)) (<= 5 6)) (>= 7 8))"s,
                         parse_to_tree("1 < 2 and 3 > 4 and 5 <= 6 or 7 >= 8"));
    EXPECT_EQ("(OR (AND (< 1 2) (> 3 4)) (AND (<= 5 6) (>= 7 8)))"s,
                         parse_to_tree("1 < 2 and 3 > 4 or 5 <= 6 and 7 >= 8"));
    // Unary plus is simply ignored by the parser.
    EXPECT_EQ("(== 1 -2)"s, parse_to_tree("+1==-2"));
    EXPECT_EQ("(== 1.23 -2.56)"s, parse_to_tree("+1.23==-2.56"));
    EXPECT_EQ("(== (+ 1 2) (- 3 -4))"s, parse_to_tree("1 + +2==3 - -4"));
    EXPECT_EQ("(== (+ 1 2) (- 3 -4))"s, parse_to_tree("1++2==3--4"));

    // Due to the way parentheses are handled by the AST, ((foo)) always gets
    // reduced down to (foo).
    EXPECT_EQ("(DOCTYPE (testdoctype1))"s, parse_to_tree("(((testdoctype1)))"));
    EXPECT_EQ("(AND (DOCTYPE (testdoctype1)) (DOCTYPE (testdoctype2)))"s,
                         parse_to_tree("((((testdoctype1))) and ((testdoctype2)))"));

    EXPECT_EQ("(== (ID id) \"foo\")"s, parse_to_tree("id == 'foo'"));
    EXPECT_EQ("(== (ID id.group) \"foo\")"s, parse_to_tree("id.group == 'foo'"));
    // id_spec function apply
    EXPECT_EQ("(== (hash (ID id)) 12345)"s, parse_to_tree("id.hash() == 12345"));
    // Combination of id_spec function apply and arith_expr function apply
    EXPECT_EQ("(== (abs (hash (ID id))) 12345)"s, parse_to_tree("id.hash().abs() == 12345"));
}

TEST_F(DocumentSelectParserTest, test_token_used_as_ident_preserves_casing)
{
    createDocs();
    using namespace std::string_literals;

    // TYPE, SCHEME etc are tokens that may also be used as identifiers
    // without introducing parsing ambiguities. In this context their original
    // casing should be preserved.
    EXPECT_EQ("(== (VAR Type) 123)"s, parse_to_tree("$Type == 123"));
    EXPECT_EQ("(== (VAR giD) 123)"s, parse_to_tree("$giD == 123"));
}

TEST_F(DocumentSelectParserTest, test_ambiguous_field_spec_expression_is_handled_correctly)
{
    createDocs();
    using namespace std::string_literals;
    // In earlier revisions of LR(1)-grammar, this triggered a reduce/reduce conflict between
    // logical_expr and arith_expr for the sequence '(' field_spec ')', which failed to
    // parse in an expected manner. Test that we don't get regressions here.
    EXPECT_EQ("(!= (FIELD testdoctype1 foo) null)"s, parse_to_tree("(testdoctype1.foo)"));
    EXPECT_EQ("(AND (!= (FIELD testdoctype1 foo) null) (!= (FIELD testdoctype1 bar) null))"s,
                         parse_to_tree("(testdoctype1.foo) AND (testdoctype1.bar)"));
}

TEST_F(DocumentSelectParserTest, test_ambiguous_bool_expression_is_handled_correctly)
{
    createDocs();
    using namespace std::string_literals;
    // Bools both as high level Nodes and low level ValueNodes
    EXPECT_EQ("(OR (AND true false) (== (FIELD testdoctype1 myfield) true))"s,
              parse_to_tree("true and false or testdoctype1.myfield == true"));
    EXPECT_EQ("(!= true false)"s, parse_to_tree("true != false"));
    EXPECT_EQ("(!= true false)"s, parse_to_tree("(true) != (false)"));
}

TEST_F(DocumentSelectParserTest, special_tokens_are_allowed_as_freestanding_identifier_names) {
    createDocs();
    EXPECT_EQ("(NOT (DOCTYPE user))", parse_to_tree("not user"));
    EXPECT_EQ("(== (ID id.user) (FIELD user user))", parse_to_tree("id.user == user.user"));
    EXPECT_EQ("(NOT (DOCTYPE group))", parse_to_tree("not group"));
    EXPECT_EQ("(== (ID id.group) (FIELD group group))", parse_to_tree("id.group == group.group"));
    EXPECT_EQ("(== (FIELD user id) (ID id.user))", parse_to_tree("user.id == id.user"));
    // Case is preserved for special ID field
    EXPECT_EQ("(== (FIELD group iD) (ID id.user))", parse_to_tree("group.iD == id.user"));
}

TEST_F(DocumentSelectParserTest, test_can_build_field_value_from_field_expr_node)
{
    using select::FieldExprNode;
    {
        // Simple field expression
        auto lhs = std::make_unique<FieldExprNode>("mydoctype");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs), "foo");
        auto fv = root->convert_to_field_value();
        EXPECT_EQ(std::string("mydoctype"), fv->getDocType());
        EXPECT_EQ(std::string("foo"), fv->getFieldName());
    }
    {
        // Nested field expression
        auto lhs1 = std::make_unique<FieldExprNode>("mydoctype");
        auto lhs2 = std::make_unique<FieldExprNode>(std::move(lhs1), "foo");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs2), "bar");
        auto fv = root->convert_to_field_value();
        EXPECT_EQ(std::string("mydoctype"), fv->getDocType());
        EXPECT_EQ(std::string("foo.bar"), fv->getFieldName());
    }
}

TEST_F(DocumentSelectParserTest, test_can_build_function_call_from_field_expr_node)
{
    using select::FieldExprNode;
    {
        // doctype.foo.lowercase()
        // Note that calling lowercase() directly on the doctype is not supported
        // (see test_function_call_on_doctype_throws_exception)
        auto lhs1 = std::make_unique<FieldExprNode>("mydoctype");
        auto lhs2 = std::make_unique<FieldExprNode>(std::move(lhs1), "foo");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs2), "lowercase");
        auto func = root->convert_to_function_call();
        EXPECT_EQ(std::string("lowercase"), func->getFunctionName());
        // TODO std::string?
        EXPECT_EQ(std::string("(FIELD mydoctype foo)"), node_to_string(func->getChild()));
    }
}

TEST_F(DocumentSelectParserTest, test_function_call_on_doctype_throws_exception)
{
    using select::FieldExprNode;
    auto lhs = std::make_unique<FieldExprNode>("mydoctype");
    auto root = std::make_unique<FieldExprNode>(std::move(lhs), "lowercase");
    try {
        root->convert_to_function_call();
    } catch (const vespalib::IllegalArgumentException& e) {
        EXPECT_EQ(std::string("Cannot call function 'lowercase' directly on document type"),
                             e.getMessage());
    }
}

namespace {

void check_parse_i64(std::string_view str, bool expect_ok, int64_t expected_output) {
    int64_t out = 0;
    bool ok = select::util::parse_i64(str.data(), str.size(), out);
    EXPECT_EQ(expect_ok, ok) << "Parsing did not returned expected success status for i64 input " << str;
    if (expect_ok) {
        EXPECT_EQ(expected_output, out) << "Parse output not as expected for i64 input " << str;
    }
}

void check_parse_hex_i64(std::string_view str, bool expect_ok, int64_t expected_output) {
    int64_t out = 0;
    bool ok = select::util::parse_hex_i64(str.data(), str.size(), out);
    EXPECT_EQ(expect_ok, ok) << "Parsing did not returned expected success status for hex i64 input " << str;
    if (expect_ok) {
        EXPECT_EQ(expected_output, out) << "Parse output not as expected for hex i64 input " << str;
    }
}

void check_parse_double(std::string_view str, bool expect_ok, double expected_output) {
    double out = 0;
    bool ok = select::util::parse_double(str.data(), str.size(), out);
    EXPECT_EQ(expect_ok, ok) << "Parsing did not returned expected success status for hex i64 input " << str;
    if (expect_ok) {
        EXPECT_EQ(expected_output, out) << "Parse output not as expected for double input " << str;
    }
}

}

TEST_F(DocumentSelectParserTest, test_parse_utilities_handle_well_formed_input)
{
    check_parse_i64("0", true, 0);
    check_parse_i64("1", true, 1);
    check_parse_i64("9223372036854775807", true, INT64_MAX);

    // Note: 0x prefix is _not_ included
    check_parse_hex_i64("0", true, 0);
    check_parse_hex_i64("1", true, 1);
    check_parse_hex_i64("f", true, 15);
    check_parse_hex_i64("F", true, 15);
    check_parse_hex_i64("ffffffff", true, UINT32_MAX);
    check_parse_hex_i64("7FFFFFFFFFFFFFFF", true, INT64_MAX);
    // We actually parse as u64 internally, then convert
    check_parse_hex_i64("ffffffffffffffff", true, -1);

    check_parse_double("1.0", true, 1.0);
    check_parse_double("1.", true, 1.0);
    check_parse_double("1.79769e+308", true, 1.79769e+308); // DBL_MAX
}

TEST_F(DocumentSelectParserTest, test_parse_utilities_handle_malformed_input)
{
    check_parse_i64("9223372036854775808", false, 0); // INT64_MAX + 1
    check_parse_i64("18446744073709551615", false, 0); // UINT64_MAX
    check_parse_i64("", false, 0);
    check_parse_i64("bjarne", false, 0);
    check_parse_i64("1x", false, 0);

    check_parse_hex_i64("", false, 0);
    check_parse_hex_i64("g", false, 0);
    check_parse_hex_i64("0x1", false, 0);
    check_parse_hex_i64("ffffffffffffffff1", false, 0);

    check_parse_double("1.x", false, 0.0);
    // TODO double outside representable range returns Inf, but we probably would
    // like this to trigger a parse failure?
    check_parse_double("1.79769e+309", true, std::numeric_limits<double>::infinity());
    check_parse_double("-1.79769e+309", true, -std::numeric_limits<double>::infinity());
}

TEST_F(DocumentSelectParserTest, imported_field_references_are_treated_as_valid_field_with_missing_value) {
    const DocumentType* type = _repo->getDocumentType("with_imported");
    ASSERT_TRUE(type != nullptr);
    Document doc(*_repo, *type, DocumentId("id::with_imported::foo"));

    PARSE("with_imported.my_imported_field == null", doc, True);
    PARSE("with_imported.my_imported_field != null", doc, False);
    PARSE("with_imported.my_imported_field", doc, False);
    // Only (in)equality operators are well defined for null values; everything else becomes Invalid.
    PARSE("with_imported.my_imported_field > 0", doc, Invalid);
}

TEST_F(DocumentSelectParserTest, imported_field_references_only_support_for_simple_expressions) {
    const DocumentType* type = _repo->getDocumentType("with_imported");
    ASSERT_TRUE(type != nullptr);
    Document doc(*_repo, *type, DocumentId("id::with_imported::foo"));

    PARSE("with_imported.my_imported_field.foo", doc, Invalid);
    PARSE("with_imported.my_imported_field[0]", doc, Invalid);
    PARSE("with_imported.my_imported_field{foo}", doc, Invalid);
}

TEST_F(DocumentSelectParserTest, prefix_and_suffix_wildcard_globs_are_rewritten_to_optimized_form) {
    using select::GlobOperator;
    EXPECT_EQ(GlobOperator::convertToRegex("*foo"), "foo$");
    EXPECT_EQ(GlobOperator::convertToRegex("foo*"), "^foo");
    EXPECT_EQ(GlobOperator::convertToRegex("*foo*"), "foo");
    EXPECT_EQ(GlobOperator::convertToRegex("*"), ""); // Matches any string.
    EXPECT_EQ(GlobOperator::convertToRegex("**"), ""); // Still matches any string.
}

TEST_F(DocumentSelectParserTest, redundant_glob_wildcards_are_collapsed_into_minimal_form) {
    using select::GlobOperator;
    EXPECT_EQ(GlobOperator::convertToRegex("***"), ""); // Even still matches any string.
    EXPECT_EQ(GlobOperator::convertToRegex("**foo**"), "foo");
    EXPECT_EQ(GlobOperator::convertToRegex("foo***"), "^foo");
    EXPECT_EQ(GlobOperator::convertToRegex("***foo"), "foo$");
    EXPECT_EQ(GlobOperator::convertToRegex("foo**bar"), "^foo.*bar$");
    EXPECT_EQ(GlobOperator::convertToRegex("**foo*bar**"), "foo.*bar");
    EXPECT_EQ(GlobOperator::convertToRegex("**foo***bar**"), "foo.*bar");
    EXPECT_EQ(GlobOperator::convertToRegex("*?*"), ".");
    EXPECT_EQ(GlobOperator::convertToRegex("*?*?*?*"), "..*..*."); // Don't try this at home, kids!
}

TEST_F(DocumentSelectParserTest, recursion_depth_is_bounded_for_field_exprs) {
    createDocs();
    std::string expr = "testdoctype1";
    for (size_t i = 0; i < 50000; ++i) {
        expr += ".foo";
    }
    expr += ".hash() != 0";
    verifyFailedParse(expr, "ParsingFailedException: expression is too deeply nested (max 1024 levels)");
}

TEST_F(DocumentSelectParserTest, recursion_depth_is_bounded_for_arithmetic_exprs) {
    createDocs();
    std::string expr = "1";
    for (size_t i = 0; i < 50000; ++i) {
        expr += "+1";
    }
    expr += " != 0";
    verifyFailedParse(expr, "ParsingFailedException: expression is too deeply nested (max 1024 levels)");
}

TEST_F(DocumentSelectParserTest, recursion_depth_is_bounded_for_binary_logical_exprs) {
    createDocs();
    // Also throw in some comparisons to ensure they carry over the max depth.
    std::string expr = "1 == 2";
    std::string cmp_subexpr = "3 != 4";
    for (size_t i = 0; i < 10000; ++i) {
        expr += (i % 2 == 0 ? " and " : " or ") + cmp_subexpr;
    }
    verifyFailedParse(expr, "ParsingFailedException: expression is too deeply nested (max 1024 levels)");
}

TEST_F(DocumentSelectParserTest, recursion_depth_is_bounded_for_unary_logical_exprs) {
    createDocs();
    std::string expr;
    for (size_t i = 0; i < 10000; ++i) {
        expr += "not ";
    }
    expr += "true";
    verifyFailedParse(expr, "ParsingFailedException: expression is too deeply nested (max 1024 levels)");
}

TEST_F(DocumentSelectParserTest, selection_has_upper_limit_on_input_size) {
    createDocs();
    std::string expr = ("testdoctype1.a_biii"
                        + std::string(select::ParserLimits::MaxSelectionByteSize, 'i')
                        + "iiig_identifier");
    verifyFailedParse(expr, "ParsingFailedException: expression is too large to be "
                            "parsed (max 1048576 bytes, got 1048610)");
}

TEST_F(DocumentSelectParserTest, lexing_does_not_have_superlinear_time_complexity) {
    createDocs();
    std::string expr = ("testdoctype1.hstringval == 'a_biii"
                        + std::string(select::ParserLimits::MaxSelectionByteSize - 100, 'i')
                        + "iiig string'");
    // If the lexer is not compiled with the appropriate options, this will take a long time.
    // A really, really long time.
    PARSE(expr, *_doc[0], False);
}

} // document
