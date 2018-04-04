// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/TestFixture.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
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
#include <vespa/document/select/parse_utils.h>
#include <vespa/vespalib/util/exceptions.h>
#include <limits>

using namespace document::config_builder;

namespace document {

class DocumentSelectParserTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(DocumentSelectParserTest);
    CPPUNIT_TEST(testParseTerminals);
    CPPUNIT_TEST(testParseBranches);
    CPPUNIT_TEST(testOperators);
    CPPUNIT_TEST(testVisitor);
    CPPUNIT_TEST(testUtf8);
    CPPUNIT_TEST(testThatSimpleFieldValuesHaveCorrectFieldName);
    CPPUNIT_TEST(testThatComplexFieldValuesHaveCorrectFieldNames);
    CPPUNIT_TEST(testBodyFieldDetection);
    CPPUNIT_TEST(testDocumentUpdates);
    CPPUNIT_TEST(testDocumentIdsInRemoves);
    CPPUNIT_TEST(test_syntax_error_reporting);
    CPPUNIT_TEST(test_operator_precedence);
    CPPUNIT_TEST(test_token_used_as_ident_preserves_casing);
    CPPUNIT_TEST(test_ambiguous_field_spec_expression_is_handled_correctly);
    CPPUNIT_TEST(test_can_build_field_value_from_field_expr_node);
    CPPUNIT_TEST(test_can_build_function_call_from_field_expr_node);
    CPPUNIT_TEST(test_function_call_on_doctype_throws_exception);
    CPPUNIT_TEST(test_parse_utilities_handle_well_formed_input);
    CPPUNIT_TEST(test_parse_utilities_handle_malformed_input);
    CPPUNIT_TEST_SUITE_END();

    BucketIdFactory _bucketIdFactory;
    std::unique_ptr<select::Parser> _parser;
    std::vector<Document::SP > _doc;
    std::vector<DocumentUpdate::SP > _update;

    Document::SP createDoc(
            const std::string& doctype, const std::string& id, uint32_t hint,
            double hfloat, const std::string& hstr, const std::string& cstr,
            uint64_t hlong = 0);

    DocumentUpdate::SP createUpdate(
            const std::string& doctype, const std::string& id, uint32_t hint,
            const std::string& hstr);

    std::unique_ptr<select::FieldValueNode>
    parseFieldValue(const std::string& expression);

    template <typename ContainsType>
    select::ResultList doParse(const vespalib::stringref& expr,
                               const ContainsType& t);

    std::string parse_to_tree(const std::string& str);
public:

    DocumentSelectParserTest()
        : _bucketIdFactory() {}

    void setUp() override;
    void createDocs();

    void testParseTerminals();
    void testParseBranches();
    void testOperators();
    void testOperators0();
    void testOperators1();
    void testOperators2();
    void testOperators3();
    void testOperators4();
    void testOperators5();
    void testOperators6();
    void testOperators7();
    void testOperators8();
    void testOperators9();
    void testVisitor();
    void testUtf8();
    void testThatSimpleFieldValuesHaveCorrectFieldName();
    void testThatComplexFieldValuesHaveCorrectFieldNames();
    void testBodyFieldDetection();
    void testDocumentUpdates();
    void testDocumentUpdates0();
    void testDocumentUpdates1();
    void testDocumentUpdates2();
    void testDocumentUpdates3();
    void testDocumentUpdates4();
    void testDocumentIdsInRemoves();
    void test_syntax_error_reporting();
    void test_operator_precedence();
    void test_token_used_as_ident_preserves_casing();
    void test_ambiguous_field_spec_expression_is_handled_correctly();
    void test_can_build_field_value_from_field_expr_node();
    void test_can_build_function_call_from_field_expr_node();
    void test_function_call_on_doctype_throws_exception();
    void test_parse_utilities_handle_well_formed_input();
    void test_parse_utilities_handle_malformed_input();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentSelectParserTest);

namespace {
    std::shared_ptr<const DocumentTypeRepo> _repo;
}

void DocumentSelectParserTest::setUp()
{
    DocumenttypesConfigBuilderHelper builder(TestDocRepo::getDefaultConfig());
    builder.document(535424777, "notandor",
                     Struct("notandor.header"), Struct("notandor.body"));
    builder.document(1348665801, "ornotand",
                     Struct("ornotand.header"), Struct("ornotand.body"));
    builder.document(-1848670693, "andornot",
                     Struct("andornot.header"), Struct("andornot.body"));
    builder.document(-1193328712, "idid",
                     Struct("idid.header"), Struct("idid.body"));
    builder.document(-1673092522, "usergroup",
                     Struct("usergroup.header"),
                     Struct("usergroup.body"));
    _repo = std::make_unique<DocumentTypeRepo>(builder.config());

    _parser = std::make_unique<select::Parser>(*_repo, _bucketIdFactory);
}

Document::SP DocumentSelectParserTest::createDoc(
        const std::string& doctype, const std::string& id, uint32_t hint,
        double hfloat, const std::string& hstr, const std::string& cstr,
        uint64_t hlong)
{
    const DocumentType* type = _repo->getDocumentType(doctype);
    Document::SP doc(new Document(*type, DocumentId(id)));
    doc->setValue(doc->getField("headerval"), IntFieldValue(hint));

    if (hlong != 0) {
        doc->setValue(doc->getField("headerlongval"), LongFieldValue(hlong));
    }
    doc->setValue(doc->getField("hfloatval"), FloatFieldValue(hfloat));
    doc->setValue(doc->getField("hstringval"), StringFieldValue(hstr.c_str()));
    doc->setValue(doc->getField("content"), StringFieldValue(cstr.c_str()));
    return doc;
}

DocumentUpdate::SP DocumentSelectParserTest::createUpdate(
        const std::string& doctype, const std::string& id, uint32_t hint,
        const std::string& hstr)
{
    const DocumentType* type = _repo->getDocumentType(doctype);
    DocumentUpdate::SP doc(
            new DocumentUpdate(*type, DocumentId(id)));
    doc->addUpdate(FieldUpdate(doc->getType().getField("headerval"))
                      .addUpdate(AssignValueUpdate(IntFieldValue(hint))));
    doc->addUpdate(FieldUpdate(doc->getType().getField("hstringval"))
                      .addUpdate(AssignValueUpdate(StringFieldValue(hstr))));
    return doc;
}

void
DocumentSelectParserTest::createDocs()
{
    _doc.clear();
    _doc.push_back(createDoc(
                           "testdoctype1", "doc:myspace:anything", 24, 2.0, "foo", "bar", 0));  // DOC 0
    _doc.push_back(createDoc(
                           "testdoctype1", "doc:anotherspace:foo", 13, 4.1, "bar", "foo", 0));  // DOC 1
        // Add some arrays and structs to doc 1
    {
        StructFieldValue sval(_doc.back()->getField("mystruct").getDataType());
        sval.set("key", 14);
        sval.set("value", "structval");
        _doc.back()->setValue("mystruct", sval);
        ArrayFieldValue
            aval(_doc.back()->getField("structarray").getDataType());
        {
            StructFieldValue sval1(aval.getNestedType());
            sval1.set("key", 15);
            sval1.set("value", "structval1");
            StructFieldValue sval2(aval.getNestedType());
            sval2.set("key", 16);
            sval2.set("value", "structval2");
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

        ArrayFieldValue abval(_doc.back()->getField("structarray").getDataType());
        {
            StructFieldValue sval1(aval.getNestedType());
            sval1.set("key", 17);
            sval1.set("value", "structval3");
            StructFieldValue sval2(aval.getNestedType());
            sval2.set("key", 18);
            sval2.set("value", "structval4");
            abval.add(sval1);
            abval.add(sval2);
        }

        amval.put(StringFieldValue("bar"), abval);
        _doc.back()->setValue("structarrmap", amval);

        WeightedSetFieldValue wsval(
                _doc.back()->getField("stringweightedset").getDataType());
        wsval.add("foo");
        wsval.add("val1");
        wsval.add("val2");
        wsval.add("val3");
        wsval.add("val4");
        _doc.back()->setValue("stringweightedset", wsval);

        WeightedSetFieldValue wsbytes(
                _doc.back()->getField("byteweightedset").getDataType());
        wsbytes.add(ByteFieldValue(5));
        wsbytes.add(ByteFieldValue(75));
        wsbytes.add(ByteFieldValue(255));
        wsbytes.add(ByteFieldValue(0));
        _doc.back()->setValue("byteweightedset", wsbytes);
    }

    _doc.push_back(createDoc(
                           "testdoctype1", "userdoc:myspace:1234:footype1", 15, 1.0, "some", "some", 0));  // DOC 2
        // Add empty struct and array
    {
        StructFieldValue sval(_doc.back()->getField("mystruct").getDataType());
        _doc.back()->setValue("mystruct", sval);
        ArrayFieldValue aval(
                _doc.back()->getField("structarray").getDataType());
        _doc.back()->setValue("structarray", aval);
    }
    _doc.push_back(createDoc(
                           "testdoctype1", "groupdoc:myspace:yahoo:bar", 14, 2.4, "Yet", "\xE4\xB8\xBA\xE4\xBB\x80", 0)); // DOC 3
    _doc.push_back(createDoc(
                           "testdoctype2", "doc:myspace:inheriteddoc", 10, 1.4, "inherited", "")); // DOC 4
    _doc.push_back(createDoc(
        "testdoctype1", "userdoc:footype:123456789:aardvark",
        10, 1.4, "inherited", "", 0));  // DOC 5
    _doc.push_back(createDoc(
        "testdoctype1", "userdoc:footype:1234:highlong",
        10, 1.4, "inherited", "", 2651257743)); // DOC 6
    _doc.push_back(createDoc(
        "testdoctype1", "userdoc:footype:1234:highlong",
        10, 1.4, "inherited", "", -2651257743)); // DOC 7
    _doc.push_back(createDoc(  // DOC 8
        "testdoctype1", "orderdoc(4,4):footype:1234:12:highlong",
        10, 1.4, "inherited", "", -2651257743));
    _doc.push_back(createDoc(  // DOC 9
        "testdoctype1", "orderdoc(4,4):footype:mygroup:12:highlong",
        10, 1.4, "inherited", "", -2651257743));
    _doc.push_back(createDoc( // DOC 10. As DOC 0 but with version 2.
        "testdoctype1", "doc:myspace:anything", 24, 2.0, "foo", "bar", 0));
    _doc.push_back(createDoc(
        "testdoctype1", "id:footype:testdoctype1:n=12345:foo",
        10, 1.4, "inherited", "", 42)); // DOC 11
    _doc.push_back(createDoc(
        "testdoctype1", "id:myspace:testdoctype1:g=xyzzy:foo",
        10, 1.4, "inherited", "", 42)); // DOC 12

    _update.clear();
    _update.push_back(createUpdate(
        "testdoctype1", "doc:myspace:anything", 20, "hmm"));
    _update.push_back(createUpdate(
        "testdoctype1", "doc:anotherspace:foo", 10, "foo"));
    _update.push_back(createUpdate(
        "testdoctype1", "userdoc:myspace:1234:footype1", 0, "foo"));
    _update.push_back(createUpdate(
        "testdoctype1", "groupdoc:myspace:yahoo:bar", 3, "\xE4\xBA\xB8\xE4\xBB\x80"));
    _update.push_back(createUpdate(
        "testdoctype2", "doc:myspace:inheriteddoc", 10, "bar"));
}

namespace {
void doVerifyParse(select::Node *node, const std::string &query, const char *expected) {
    std::string message("Query "+query+" failed to parse.");
    CPPUNIT_ASSERT_MESSAGE(message, node != 0);
    std::ostringstream actual;
    actual << *node;

    std::string exp(expected != 0 ? std::string(expected) : query);
    CPPUNIT_ASSERT_EQUAL(exp, actual.str());
    // Test that cloning gives the same result
    std::unique_ptr<select::Node> clonedNode(node->clone());
    std::ostringstream clonedStr;
    clonedStr << *clonedNode;
    CPPUNIT_ASSERT_EQUAL(exp, clonedStr.str());
}

void verifySimpleParse(const std::string& query, const char* expected = 0) {
    BucketIdFactory factory;
    select::simple::SelectionParser parser(factory);
    std::string message("Query "+query+" failed to parse.");
    CPPUNIT_ASSERT_MESSAGE(message, parser.parse(query));
    std::unique_ptr<select::Node> node(parser.getNode());
    doVerifyParse(node.get(), query, expected);
}

void verifyParse(const std::string& query, const char* expected = 0) {
    BucketIdFactory factory;
    select::Parser parser(*_repo, factory);
    std::unique_ptr<select::Node> node(parser.parse(query));
    doVerifyParse(node.get(), query, expected);
}

    void verifyFailedParse(const std::string& query, const std::string& error) {
        try{
            BucketIdFactory factory;
            TestDocRepo test_repo;
            select::Parser parser(test_repo.getTypeRepo(), factory);
            std::unique_ptr<select::Node> node(parser.parse(query));
            CPPUNIT_FAIL("Expected exception parsing query '"+query+"'");
        } catch (select::ParsingFailedException& e) {
            std::string message(e.what());
            if (message.size() > error.size())
                message = message.substr(0, error.size());
            std::string failure("Expected: " + error + "\n- Actual  : "
                                + std::string(e.what()));
            CPPUNIT_ASSERT_MESSAGE(failure, error == message);
        }
    }
}

void DocumentSelectParserTest::test_syntax_error_reporting() {
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

void DocumentSelectParserTest::testParseTerminals()
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
    std::unique_ptr<select::Node> node(
            _parser->parse("testdoctype1.headerval == \"test\""));
    const select::Compare& compnode(
            dynamic_cast<const select::Compare&>(*node));
    const select::FieldValueNode& fnode(
            dynamic_cast<const select::FieldValueNode&>(compnode.getLeft()));
    const select::StringValueNode& vnode(
            dynamic_cast<const select::StringValueNode&>(compnode.getRight()));

    CPPUNIT_ASSERT_EQUAL(vespalib::string("headerval"), fnode.getFieldName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("test"), vnode.getValue());
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
    const select::StringValueNode& escval(
        dynamic_cast<const select::StringValueNode&>(escapednode.getRight()));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("\ttH \n"), escval.getValue());
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
    verifySimpleParse("id == \"userdoc:ns:mytest\"");
    verifySimpleParse("id.namespace == \"myspace\"");
    verifySimpleParse("id.scheme == \"userdoc\"");
    verifySimpleParse("id.type == \"testdoctype1\"");
    verifySimpleParse("id.group == \"yahoo.com\"");
    verifySimpleParse("id.user == 1234");
    verifySimpleParse("id.user == 0x12456ab", "id.user == 19158699");

    // Test document id
    verifyParse("id == \"userdoc:ns:mytest\"");
    verifyParse("id.namespace == \"myspace\"");
    verifyParse("id.scheme == \"userdoc\"");
    verifyParse("id.type == \"testdoctype1\"");
    verifyParse("id.user == 1234");
    verifyParse("id.user == 0x12456ab", "id.user == 19158699");
    verifyParse("id.group == \"yahoo.com\"");
    verifyParse("id.order(10,5) < 100");

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
}

void DocumentSelectParserTest::testParseBranches()
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
}

template <typename ContainsType>
select::ResultList
DocumentSelectParserTest::doParse(const vespalib::stringref& expr,
                                  const ContainsType& t)
{
    std::unique_ptr<select::Node> root(_parser->parse(expr));
    select::ResultList result(root->contains(t));

    std::unique_ptr<select::Node> cloned(root->clone());
    select::ResultList clonedResult(cloned->contains(t));

    std::unique_ptr<select::Node> traced(_parser->parse(expr));
    std::ostringstream oss;
    oss << "for expr: " << expr << "\n";
    select::ResultList tracedResult(root->trace(t, oss));

    CPPUNIT_ASSERT_EQUAL_MESSAGE(expr, result, clonedResult);
    CPPUNIT_ASSERT_EQUAL_MESSAGE(oss.str(), result, tracedResult);

    return result;
}

#define PARSE(expr, doc, result) \
    CPPUNIT_ASSERT_EQUAL_MESSAGE(expr, select::ResultList(select::Result::result), \
                                 doParse(expr, (doc)));

#define PARSEI(expr, doc, result) \
    CPPUNIT_ASSERT_EQUAL_MESSAGE(std::string("Doc: ") + expr,          \
                                 select::ResultList(select::Result::result), \
                                 doParse(expr, (doc)));                 \
    CPPUNIT_ASSERT_EQUAL_MESSAGE(std::string("Doc id: ") + expr,       \
                                 select::ResultList(select::Result::result), \
                                 doParse(expr, (doc).getId()));

void DocumentSelectParserTest::testOperators()
{
    testOperators0();
    testOperators1();
    testOperators2();
    testOperators3();
    testOperators4();
    testOperators5();
    testOperators6();
    testOperators7();
    testOperators8();
    testOperators9();
}

void DocumentSelectParserTest::testOperators0()
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
    PARSE("\"bar\" = \"\"", *_doc[0], False);
    PARSE("\"\" = \"\"", *_doc[0], True);
    PARSE("\"bar\" =~ \"^a$\"", *_doc[0], False);
    PARSE("\"bar\" =~ \"a\"", *_doc[0], True);
    PARSE("\"bar\" =~ \"\"", *_doc[0], True);
    PARSE("\"\" =~ \"\"", *_doc[0], True);
    PARSE("30 = 10", *_doc[0], False);
    PARSE("30 = 30", *_doc[0], True);
}

void DocumentSelectParserTest::testOperators1()
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
    PARSE("testdoctype1", *_doc[4], True);
    PARSE("testdoctype1.headerval = 10", *_doc[4], True);
}

void DocumentSelectParserTest::testOperators2()
{
    createDocs();

    // Id values
    PARSEI("id == \"doc:myspace:anything\"", *_doc[0], True);
    PARSEI(" iD==  \"doc:myspace:anything\"  ", *_doc[0], True);
    PARSEI("id == \"doc:myspa:nything\"", *_doc[0], False);
    PARSEI("Id.scHeme == \"doc\"", *_doc[0], True);
    PARSEI("id.scheme == \"userdoc\"", *_doc[0], False);
    PARSEI("id.type == \"testdoctype1\"", *_doc[11], True);
    PARSEI("id.type == \"wrong_type\"", *_doc[11], False);
    PARSEI("id.type == \"unknown\"", *_doc[0], Invalid);
    PARSEI("Id.namespaCe == \"myspace\"", *_doc[0], True);
    PARSEI("id.NaMespace == \"pace\"", *_doc[0], False);
    PARSEI("id.specific == \"anything\"", *_doc[0], True);
    PARSEI("id.user=1234", *_doc[2], True);
    PARSEI("id.user == 1234", *_doc[0], Invalid);
    PARSEI("id.group == 1234", *_doc[3], Invalid);
    PARSEI("id.group == \"yahoo\"", *_doc[3], True);
    PARSEI("id.bucket == 1234", *_doc[0], False);
    PARSEI("id.order(4,4) == 12", *_doc[8], True);
    PARSEI("id.order(4,4) < 20", *_doc[8], True);
    PARSEI("id.order(4,4) > 12", *_doc[8], False);
    PARSEI("id.order(5,5) <= 12", *_doc[8], Invalid);
    PARSEI("id.order(4,4) <= 12", *_doc[8], True);
    PARSEI("id.order(4,4) == 12", *_doc[0], Invalid);
    PARSEI("id.user=12345", *_doc[11], True);
    PARSEI("id.group == \"xyzzy\"", *_doc[12], True);
}

void DocumentSelectParserTest::testOperators3()
{
    createDocs();
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(16, 4006).getId() ;
        PARSEI(ost.str(), *_doc[0], True);
    }
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(17, 4006).getId() ;
        PARSEI(ost.str(), *_doc[0], False);
    }
    {
        std::ostringstream ost;
        ost << "id.bucket == " << BucketId(17, 69542).getId() ;
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

    PARSEI("id.user=123456789 and id = \"userdoc:footype:123456789:aardvark\"", *_doc[5], True);
    PARSEI("id == \"userdoc:footype:123456789:badger\"", *_doc[5], False);

    PARSEI("id.user = 1234", *_doc[8], True);
    PARSEI("id.group == \"1234\"", *_doc[8], True);
    PARSEI("id.group == \"mygroup\"", *_doc[9], True);
}

void DocumentSelectParserTest::testOperators4()
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

void DocumentSelectParserTest::testOperators5()
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
    PARSE("now() > 1311862500", *_doc[10], True);
    PARSE("now() < 1611862500", *_doc[10], True);
    PARSE("now() < 1311862500", *_doc[10], False);
    PARSE("now() > 1611862500", *_doc[10], False);

    // Arithmetics
    PARSEI("id.specific.hash() % 10 = 8", *_doc[0], True);
    PARSEI("id.specific.hash() % 10 = 2", *_doc[0], False);
    PARSEI("\"foo\" + \"bar\" = \"foobar\"", *_doc[0], True);
    PARSEI("\"foo\" + 4 = 25", *_doc[0], Invalid);
    PARSEI("34.0 % 4 = 4", *_doc[0], Invalid);
    PARSEI("-6 % 10 = -6", *_doc[0], True);
}

void DocumentSelectParserTest::testOperators6()
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

void DocumentSelectParserTest::testOperators7()
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

void DocumentSelectParserTest::testOperators8()
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

    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$x}[$y].value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarrmap.value[$y].key == 15 AND testdoctype1.structarrmap.value[$y].value == \"structval1\"", *_doc[1], True);
    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$x}[$y].value == \"structval2\"", *_doc[1], False);
    PARSE("testdoctype1.structarrmap.value[$y].key == 15 AND testdoctype1.structarrmap.value[$y].value == \"structval2\"", *_doc[1], False);
    PARSE("testdoctype1.structarrmap{$x}[$y].key == 15 AND testdoctype1.structarrmap{$y}[$x].value == \"structval2\"", *_doc[1], False);
}

void DocumentSelectParserTest::testOperators9()
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

namespace {

    class TestVisitor : public select::Visitor {
    private:
        std::ostringstream data;

    public:
        ~TestVisitor() {}

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
        void visitCurrentTimeValueNode(const select::CurrentTimeValueNode &) override {}
        void visitStringValueNode(const select::StringValueNode &) override {}
        void visitNullValueNode(const select::NullValueNode &) override {}
        void visitInvalidValueNode(const select::InvalidValueNode &) override {}

        std::string getVisitString() { return data.str(); }
    };

}

void DocumentSelectParserTest::testVisitor()
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

    CPPUNIT_ASSERT_EQUAL(expected, v.getVisitString());
}

void DocumentSelectParserTest::testBodyFieldDetection()
{

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        CPPUNIT_ASSERT(!detector.foundBodyField);
        CPPUNIT_ASSERT(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1 AND id.user=1234"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        CPPUNIT_ASSERT(!detector.foundBodyField);
        CPPUNIT_ASSERT(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1.headerval=123"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        CPPUNIT_ASSERT(!detector.foundBodyField);
        CPPUNIT_ASSERT(detector.foundHeaderField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse("testdoctype1.content"));
        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        CPPUNIT_ASSERT(detector.foundBodyField);
    }

    {
        std::unique_ptr<select::Node> root(_parser->parse(
                                                 "true or testdoctype1 and (not id.user = 12 or testdoctype1.hstringval = \"ola\") and "
                                                 "testdoctype1.headerval"));

        select::BodyFieldDetector detector(*_repo);

        root->visit(detector);
        CPPUNIT_ASSERT(!detector.foundBodyField);
    }

}

void DocumentSelectParserTest::testDocumentUpdates()
{
    testDocumentUpdates0();
    testDocumentUpdates1();
    testDocumentUpdates2();
    testDocumentUpdates3();
    testDocumentUpdates4();
}

void DocumentSelectParserTest::testDocumentUpdates0()
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

void DocumentSelectParserTest::testDocumentUpdates1()
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
    PARSE("testdoctype1", *_update[4], True);
    PARSE("testdoctype1.headerval = 10", *_update[4], Invalid);
}

void DocumentSelectParserTest::testDocumentUpdates2()
{
    createDocs();

    // Id values
    PARSEI("id == \"doc:myspace:anything\"", *_update[0], True);
    PARSEI(" iD==  \"doc:myspace:anything\"  ", *_update[0], True);
    PARSEI("id == \"doc:myspa:nything\"", *_update[0], False);
    PARSEI("Id.scHeme == \"doc\"", *_update[0], True);
    PARSEI("id.scheme == \"userdoc\"", *_update[0], False);
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
        ost << "id.bucket == " << BucketId(16, 4006).getId();
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

void DocumentSelectParserTest::testDocumentUpdates4()
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

void DocumentSelectParserTest::testDocumentIdsInRemoves()
{
    PARSE("testdoctype1", DocumentId("id:ns:testdoctype1::1"), True);
    PARSE("testdoctype1", DocumentId("id:ns:null::1"), False);
    PARSE("testdoctype1", DocumentId("userdoc:testdoctype1:1234:1"), False);
    PARSE("testdoctype1.headerval", DocumentId("id:ns:testdoctype1::1"), Invalid);
    // FIXME: Should ideally be False. As long as there always is an AND node with doctype in a selection expression
    // we won't end up sending removes using the wrong route.
    PARSE("testdoctype1.headerval", DocumentId("id:ns:null::1"), Invalid);
    PARSE("testdoctype1.headerval == 0", DocumentId("id:ns:testdoctype1::1"), Invalid);
    PARSE("testdoctype1 and testdoctype1.headerval == 0", DocumentId("id:ns:testdoctype1::1"), Invalid);
}

void DocumentSelectParserTest::testUtf8()
{
    createDocs();
    std::string utf8name(u8"H\u00e5kon");
    CPPUNIT_ASSERT_EQUAL(size_t(6), utf8name.size());

    /// \todo TODO (was warning):  UTF8 test for glob/regex support in selection language disabled. Known not to work
//    boost::u32regex rx = boost::make_u32regex("H.kon");
//    CPPUNIT_ASSERT_EQUAL(true, boost::u32regex_match(utf8name, rx));

    _doc.push_back(createDoc(
        "testdoctype1", "doc:myspace:utf8doc", 24, 2.0, utf8name, "bar"));
//    PARSE("testdoctype1.hstringval = \"H?kon\"", *_doc[_doc.size()-1], True);
//    PARSE("testdoctype1.hstringval =~ \"H.kon\"", *_doc[_doc.size()-1], True);
}

std::unique_ptr<select::FieldValueNode>
DocumentSelectParserTest::parseFieldValue(const std::string& expression) {
    return std::unique_ptr<select::FieldValueNode>(dynamic_cast<select::FieldValueNode *>(
        dynamic_cast<const select::Compare &>(*_parser->parse(expression)).getLeft().clone().release()));
}

void DocumentSelectParserTest::testThatSimpleFieldValuesHaveCorrectFieldName() {
    CPPUNIT_ASSERT_EQUAL(
        vespalib::string("headerval"),
        parseFieldValue("testdoctype1.headerval")->getRealFieldName());
}

void DocumentSelectParserTest::testThatComplexFieldValuesHaveCorrectFieldNames() {
    CPPUNIT_ASSERT_EQUAL(
        vespalib::string("headerval"),
        parseFieldValue("testdoctype1.headerval{test}")->getRealFieldName());

    CPPUNIT_ASSERT_EQUAL(
        vespalib::string("headerval"),
        parseFieldValue("testdoctype1.headerval[42]")->getRealFieldName());

    CPPUNIT_ASSERT_EQUAL(
        vespalib::string("headerval"),
        parseFieldValue("testdoctype1.headerval.meow.meow{test}")->getRealFieldName());
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

void DocumentSelectParserTest::test_operator_precedence() {
    createDocs();
    using namespace std::string_literals;

    CPPUNIT_ASSERT_EQUAL("(AND true false)"s, parse_to_tree("true and false"));
    CPPUNIT_ASSERT_EQUAL("(AND (NOT false) true)"s, parse_to_tree("not false and true"));
    CPPUNIT_ASSERT_EQUAL("(NOT (AND false true))"s, parse_to_tree("not (false and true)"));
    CPPUNIT_ASSERT_EQUAL("(NOT (DOCTYPE testdoctype1))"s, parse_to_tree("not testdoctype1"));
    CPPUNIT_ASSERT_EQUAL("(NOT (DOCTYPE (testdoctype1)))"s, parse_to_tree("not (testdoctype1)"));
    CPPUNIT_ASSERT_EQUAL("(NOT (DOCTYPE (testdoctype1)))"s, parse_to_tree("(not (testdoctype1))"));
    CPPUNIT_ASSERT_EQUAL("(OR (== 1 2) (== 3 4))"s, parse_to_tree("1==2 or 3==4"));
    CPPUNIT_ASSERT_EQUAL("(!= (+ (+ 1 2) 3) 0)"s, parse_to_tree("1+2+3 != 0"));
    CPPUNIT_ASSERT_EQUAL("(!= (+ (+ 1.1 2.2) 3.3) 4.4)"s, parse_to_tree("1.1+2.2+3.3 != 4.4"));
    CPPUNIT_ASSERT_EQUAL("(!= (- (- 1 2) 3) 0)"s, parse_to_tree("1-2-3 != 0"));
    CPPUNIT_ASSERT_EQUAL("(!= (+ (+ 1 2) 3) 0)"s, parse_to_tree("1 + 2 + 3 != 0"));
    CPPUNIT_ASSERT_EQUAL("(!= (+ 1 (* 2 3)) 0)"s, parse_to_tree("1 + 2 * 3 != 0"));
    CPPUNIT_ASSERT_EQUAL("(!= (- (/ (* 1 2) 3) 4) 0)"s, parse_to_tree("1 * 2 / 3 - 4 != 0"));
    CPPUNIT_ASSERT_EQUAL("(!= (/ (* 1 2) (- 3 4)) 0)"s, parse_to_tree("1 * 2 / (3 - 4) != 0"));
    CPPUNIT_ASSERT_EQUAL("(OR (AND true (NOT (== 1 2))) false)"s,
                         parse_to_tree("true and not 1 == 2 or false"));
    CPPUNIT_ASSERT_EQUAL("(AND (AND (AND (< 1 2) (> 3 4)) (<= 5 6)) (>= 7 8))"s,
                         parse_to_tree("1 < 2 and 3 > 4 and 5 <= 6 and 7 >= 8"));
    CPPUNIT_ASSERT_EQUAL("(OR (AND (AND (< 1 2) (> 3 4)) (<= 5 6)) (>= 7 8))"s,
                         parse_to_tree("1 < 2 and 3 > 4 and 5 <= 6 or 7 >= 8"));
    CPPUNIT_ASSERT_EQUAL("(OR (AND (< 1 2) (> 3 4)) (AND (<= 5 6) (>= 7 8)))"s,
                         parse_to_tree("1 < 2 and 3 > 4 or 5 <= 6 and 7 >= 8"));
    // Unary plus is simply ignored by the parser.
    CPPUNIT_ASSERT_EQUAL("(== 1 -2)"s, parse_to_tree("+1==-2"));
    CPPUNIT_ASSERT_EQUAL("(== 1.23 -2.56)"s, parse_to_tree("+1.23==-2.56"));
    CPPUNIT_ASSERT_EQUAL("(== (+ 1 2) (- 3 -4))"s, parse_to_tree("1 + +2==3 - -4"));
    CPPUNIT_ASSERT_EQUAL("(== (+ 1 2) (- 3 -4))"s, parse_to_tree("1++2==3--4"));

    // Due to the way parentheses are handled by the AST, ((foo)) always gets
    // reduced down to (foo).
    CPPUNIT_ASSERT_EQUAL("(DOCTYPE (testdoctype1))"s, parse_to_tree("(((testdoctype1)))"));
    CPPUNIT_ASSERT_EQUAL("(AND (DOCTYPE (testdoctype1)) (DOCTYPE (testdoctype2)))"s,
                         parse_to_tree("((((testdoctype1))) and ((testdoctype2)))"));

    CPPUNIT_ASSERT_EQUAL("(== (ID id) \"foo\")"s, parse_to_tree("id == 'foo'"));
    CPPUNIT_ASSERT_EQUAL("(== (ID id.group) \"foo\")"s, parse_to_tree("id.group == 'foo'"));
    // id_spec function apply
    CPPUNIT_ASSERT_EQUAL("(== (hash (ID id)) 12345)"s, parse_to_tree("id.hash() == 12345"));
    // Combination of id_spec function apply and arith_expr function apply
    CPPUNIT_ASSERT_EQUAL("(== (abs (hash (ID id))) 12345)"s, parse_to_tree("id.hash().abs() == 12345"));
}

void DocumentSelectParserTest::test_token_used_as_ident_preserves_casing() {
    createDocs();
    using namespace std::string_literals;

    // TYPE, SCHEME, ORDER etc are tokens that may also be used as identifiers
    // without introducing parsing ambiguities. In this context their original
    // casing should be preserved.
    CPPUNIT_ASSERT_EQUAL("(== (VAR Type) 123)"s, parse_to_tree("$Type == 123"));
    CPPUNIT_ASSERT_EQUAL("(== (VAR giD) 123)"s, parse_to_tree("$giD == 123"));
    CPPUNIT_ASSERT_EQUAL("(== (VAR ORDER) 123)"s, parse_to_tree("$ORDER == 123"));
}

void DocumentSelectParserTest::test_ambiguous_field_spec_expression_is_handled_correctly() {
    createDocs();
    using namespace std::string_literals;
    // In earlier revisions of LR(1)-grammar, this triggered a reduce/reduce conflict between
    // logical_expr and arith_expr for the sequence '(' field_spec ')', which failed to
    // parse in an expected manner. Test that we don't get regressions here.
    CPPUNIT_ASSERT_EQUAL("(!= (FIELD testdoctype1 foo) null)"s, parse_to_tree("(testdoctype1.foo)"));
    CPPUNIT_ASSERT_EQUAL("(AND (!= (FIELD testdoctype1 foo) null) (!= (FIELD testdoctype1 bar) null))"s,
                         parse_to_tree("(testdoctype1.foo) AND (testdoctype1.bar)"));
}

void DocumentSelectParserTest::test_can_build_field_value_from_field_expr_node() {
    using select::FieldExprNode;
    {
        // Simple field expression
        auto lhs = std::make_unique<FieldExprNode>("mydoctype");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs), "foo");
        auto fv = root->convert_to_field_value();
        CPPUNIT_ASSERT_EQUAL(vespalib::string("mydoctype"), fv->getDocType());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("foo"), fv->getFieldName());
    }
    {
        // Nested field expression
        auto lhs1 = std::make_unique<FieldExprNode>("mydoctype");
        auto lhs2 = std::make_unique<FieldExprNode>(std::move(lhs1), "foo");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs2), "bar");
        auto fv = root->convert_to_field_value();
        CPPUNIT_ASSERT_EQUAL(vespalib::string("mydoctype"), fv->getDocType());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("foo.bar"), fv->getFieldName());
    }
}

void DocumentSelectParserTest::test_can_build_function_call_from_field_expr_node() {
    using select::FieldExprNode;
    {
        // doctype.foo.lowercase()
        // Note that calling lowercase() directly on the doctype is not supported
        // (see test_function_call_on_doctype_throws_exception)
        auto lhs1 = std::make_unique<FieldExprNode>("mydoctype");
        auto lhs2 = std::make_unique<FieldExprNode>(std::move(lhs1), "foo");
        auto root = std::make_unique<FieldExprNode>(std::move(lhs2), "lowercase");
        auto func = root->convert_to_function_call();
        CPPUNIT_ASSERT_EQUAL(vespalib::string("lowercase"), func->getFunctionName());
        // TODO vespalib::string?
        CPPUNIT_ASSERT_EQUAL(std::string("(FIELD mydoctype foo)"), node_to_string(func->getChild()));
    }
}

void DocumentSelectParserTest::test_function_call_on_doctype_throws_exception() {
    using select::FieldExprNode;
    auto lhs = std::make_unique<FieldExprNode>("mydoctype");
    auto root = std::make_unique<FieldExprNode>(std::move(lhs), "lowercase");
    try {
        root->convert_to_function_call();
    } catch (const vespalib::IllegalArgumentException& e) {
        CPPUNIT_ASSERT_EQUAL(vespalib::string("Cannot call function 'lowercase' directly on document type"),
                             e.getMessage());
    }
}

namespace {

void check_parse_i64(vespalib::stringref str, bool expect_ok, int64_t expected_output) {
    int64_t out = 0;
    bool ok = select::util::parse_i64(str.data(), str.size(), out);
    CPPUNIT_ASSERT_EQUAL_MESSAGE("Parsing did not returned expected success status for i64 input " + str, expect_ok, ok);
    if (expect_ok) {
        CPPUNIT_ASSERT_EQUAL_MESSAGE("Parse output not as expected for i64 input " + str, expected_output, out);
    }
}

void check_parse_hex_i64(vespalib::stringref str, bool expect_ok, int64_t expected_output) {
    int64_t out = 0;
    bool ok = select::util::parse_hex_i64(str.data(), str.size(), out);
    CPPUNIT_ASSERT_EQUAL_MESSAGE("Parsing did not returned expected success status for hex i64 input " + str, expect_ok, ok);
    if (expect_ok) {
        CPPUNIT_ASSERT_EQUAL_MESSAGE("Parse output not as expected for hex i64 input " + str, expected_output, out);
    }
}

void check_parse_double(vespalib::stringref str, bool expect_ok, double expected_output) {
    double out = 0;
    bool ok = select::util::parse_double(str.data(), str.size(), out);
    CPPUNIT_ASSERT_EQUAL_MESSAGE("Parsing did not returned expected success status for hex i64 input " + str, expect_ok, ok);
    if (expect_ok) {
        CPPUNIT_ASSERT_EQUAL_MESSAGE("Parse output not as expected for double input " + str, expected_output, out);
    }
}

}

void DocumentSelectParserTest::test_parse_utilities_handle_well_formed_input() {
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

void DocumentSelectParserTest::test_parse_utilities_handle_malformed_input() {
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
}

} // document
