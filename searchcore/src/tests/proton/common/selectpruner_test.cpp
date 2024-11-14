// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/selectpruner.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP(".selectpruner_test");

using document::DataType;
using document::Document;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::config_builder::Array;
using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Map;
using document::config_builder::Struct;
using document::config_builder::Wset;
using document::select::CloningVisitor;
using document::select::Node;
using document::select::Result;
using document::select::ResultSet;
using proton::SelectPruner;
using std::string;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::test::MockAttributeManager;
using search::AttributeFactory;

using NodeUP = Node::UP;

namespace {

const int32_t doc_type_id = 787121340;
const string type_name = "test";
const string header_name = type_name + ".header";
const string body_name = type_name + ".body";
const string type_name_2 = "test_2";
const string header_name_2 = type_name_2 + ".header";
const string body_name_2 = type_name_2 + ".body";
const string false_name("false");
const string true_name("true");
const string valid_name("test.aa > 3999");
const string valid2_name("test.ab > 4999");
const string rvalid_name("test.aa <= 3999");
const string rvalid2_name("test.ab <= 4999");
const string invalid_name("test_2.ac > 3999");
const string invalid2_name("test_2.ac > 4999");
const string empty("");

std::unique_ptr<const DocumentTypeRepo>
makeDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name).
                     addField("ia", DataType::T_STRING).
                     addField("ib", DataType::T_STRING).
                     addField("ibs", Struct("pair").
                              addField("x", DataType::T_STRING).
                              addField("y", DataType::T_STRING)).
                     addField("iba", Array(DataType::T_STRING)).
                     addField("ibw", Wset(DataType::T_STRING)).
                     addField("ibm", Map(DataType::T_STRING,
                                         DataType::T_STRING)).
                     addField("aa", DataType::T_INT).
                     addField("aaa", Array(DataType::T_INT)).
                     addField("aaw", Wset(DataType::T_INT)).
                     addField("ab", DataType::T_INT).
                     addField("ae", DataType::T_INT)).
                     imported_field("my_imported_field").
                     imported_field("my_missing_imported_field");
    builder.document(doc_type_id + 1, type_name_2,
                     Struct(header_name_2), Struct(body_name_2).
                     addField("ic", DataType::T_STRING).
                     addField("id", DataType::T_STRING).
                     addField("ac", DataType::T_INT).
                     addField("ad", DataType::T_INT));
    return std::unique_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(builder.config()));
}


std::string
rsString(const ResultSet &s)
{
    std::ostringstream os;
    bool first = true;
    uint32_t erange = Result::enumRange;
    for (uint32_t e = 0; e < erange; ++e) {
        if (s.hasEnum(e)) {
            if (!first)
                os << ",";
            first = false;
            Result::fromEnum(e).print(os, false, "");
        }
    }
    if (first) {
        os << "empty";
    }
    return os.str();
}


const char *
csString(const SelectPruner &pruner)
{
    if (!pruner.isConst())
        return "not const";
    if (pruner.isFalse())
        return "const false";
    if (pruner.isTrue())
        return "const true";
    if (pruner.isInvalid())
        return "const invalid";
    return "const something";
}

}

class SelectPrunerTest : public ::testing::Test
{
public:
    MockAttributeManager _amgr;
    std::unique_ptr<const DocumentTypeRepo> _repoUP;
    bool _hasFields;
    bool _hasDocuments;

    SelectPrunerTest();
    ~SelectPrunerTest() override;

    void testParse(const string &selection);
    void testParseFail(const string &selection);
    void testPrune(const string &selection, const string &exp);
    void testPrune(const string &selection, const string &exp, const string &docTypeName);
};


SelectPrunerTest::SelectPrunerTest()
    : ::testing::Test(),
      _amgr(),
      _repoUP(),
      _hasFields(true),
      _hasDocuments(true)
{
    _amgr.addAttribute("aa", AttributeFactory::createAttribute("aa", { BasicType::INT32 }));
    _amgr.addAttribute("aaa", AttributeFactory::createAttribute("aaa", { BasicType::INT32 , CollectionType::ARRAY}));
    _amgr.addAttribute("aaw", AttributeFactory::createAttribute("aaw", { BasicType::INT32 , CollectionType::WSET}));
    _amgr.addAttribute("ae", AttributeFactory::createAttribute("ae", { BasicType::INT32 }));
    // We "fake" having an imported attribute to avoid having to set up reference attributes, mappings etc.
    // This is fine since the attribute manager already abstracts away if an attribute is imported or not.
    _amgr.addAttribute("my_imported_field", AttributeFactory::createAttribute("my_imported_field", { BasicType::INT32 }));
    _repoUP = makeDocTypeRepo();
}


SelectPrunerTest::~SelectPrunerTest() = default;


void
SelectPrunerTest::testParse(const string &selection)
{
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info, "Trying to parse '%s'", selection.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info, "Parse failed: %s", e.what());
        select.reset();
    }
    ASSERT_TRUE(select.get() != nullptr);
}


void
SelectPrunerTest::testParseFail(const string &selection)
{
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info, "Trying to parse '%s'", selection.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info, "Parse failed: %s", e.getMessage().c_str());
        select.reset();
    }
    ASSERT_TRUE(select.get() == nullptr);
}


void
SelectPrunerTest::testPrune(const string &selection, const string &exp, const string &docTypeName)
{
    SCOPED_TRACE(selection);
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info, "Trying to parse '%s' with docType=%s", selection.c_str(), docTypeName.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info, "Parse failed: %s", e.what());
        select.reset();
    }
    ASSERT_TRUE(select.get() != nullptr);
    std::ostringstream os;
    select->print(os, true, "");
    LOG(info, "ParseTree: '%s'", os.str().c_str());
    const DocumentType *docType = repo.getDocumentType(docTypeName);
    ASSERT_TRUE(docType != nullptr);
    auto emptyDoc = std::make_unique<Document>(repo, *docType, document::DocumentId("id:ns:" + docTypeName + "::1"));
    SelectPruner pruner(docTypeName, &_amgr, *emptyDoc, repo, _hasFields, _hasDocuments);
    pruner.process(*select);
    std::ostringstream pos;
    pruner.getNode()->print(pos, true, "");
    EXPECT_EQ(exp, pos.str());
    LOG(info,
        "Pruned ParseTree: '%s', fieldNodes=%u, attrFieldNodes=%u, cs=%s, rs=%s",
        pos.str().c_str(),
        pruner.getFieldNodes(),
        pruner.getAttrFieldNodes(),
        csString(pruner),
        rsString(pruner.getResultSet()).c_str());
    if (pruner.isConst()) {
        ResultSet t;
        if (pruner.isFalse())
            t.add(Result::False);
        if (pruner.isTrue())
            t.add(Result::True);
        if (pruner.isInvalid())
            t.add(Result::Invalid);
        ASSERT_TRUE(t == pruner.getResultSet());
    }
    CloningVisitor cv;
    pruner.getNode()->visit(cv);
    std::ostringstream cvpos;
    cv.getNode()->print(cvpos, true, "");
    EXPECT_EQ(exp, cvpos.str());
#if 0
    std::ostringstream os2;
    pruner.trace(os2);
    LOG(info, "trace pruned: %s", os2.str().c_str());
#endif
}


void
SelectPrunerTest::testPrune(const string &selection,
                       const string &exp)
{
    testPrune(selection, exp, "test");
}


TEST_F(SelectPrunerTest, Test_that_test_setup_is_OK)
{
    const DocumentTypeRepo &repo = *_repoUP;
    const DocumentType *docType = repo.getDocumentType("test");
    ASSERT_TRUE(docType);
    EXPECT_EQ(11u, docType->getFieldCount());
    EXPECT_EQ("String", docType->getField("ia").getDataType().getName());
    EXPECT_EQ("String", docType->getField("ib").getDataType().getName());
    EXPECT_EQ("Int", docType->getField("aa").getDataType().getName());
    EXPECT_EQ("Int", docType->getField("ab").getDataType().getName());
}


TEST_F(SelectPrunerTest, Test_that_simple_parsing_works)
{
    testParse("not ((test))");
    testParse("not ((test and (test.aa > 3999)))");
    testParse("not ((test and (test.ab > 3999)))");
    testParse("not ((test and (test.af > 3999)))");
    testParse("not ((test_2 and (test_2.af > 3999)))");
}


TEST_F(SelectPrunerTest, Test_that_wrong_doctype_causes_parse_error)
{
    testParseFail("not ((test_3 and (test_3.af > 3999)))");
}


TEST_F(SelectPrunerTest, Test_that_boolean_const_shortcuts_are_OK)
{
    testPrune("false and false",
              "false");
    testPrune(false_name + " and " + invalid2_name,
              "false");
    testPrune(false_name + " and " + valid2_name,
              "false");
    testPrune("false and true",
              "false");

    testPrune(invalid_name + " and false",
              "false");
    testPrune(invalid_name + " and " + invalid2_name,
              "invalid");
    testPrune(invalid_name + " and " + valid2_name,
              empty + "invalid and " + valid2_name);
    testPrune(invalid_name + " and true",
              "invalid");

    testPrune(valid_name + " and false",
              "false");
    testPrune(valid_name + " and " + invalid2_name,
              empty + valid_name + " and invalid");
    testPrune(valid_name + " and " + valid2_name,
              valid_name + " and " + valid2_name);
    testPrune(valid_name + " and true",
              valid_name);

    testPrune("true and false",
              "false");
    testPrune(true_name + " and " + invalid2_name,
              "invalid");
    testPrune(true_name + " and " + valid2_name,
              valid2_name);
    testPrune("true and true",
              "true");

    testPrune("false or false",
              "false");
    testPrune(false_name + " or " + invalid2_name,
              "invalid");
    testPrune(false_name + " or " + valid2_name,
              valid2_name);
    testPrune("false or true",
              "true");

    testPrune(invalid_name + " or false",
              "invalid");
    testPrune(invalid_name + " or " + invalid2_name,
              "invalid");
    testPrune(invalid_name + " or " + valid2_name,
              empty + "invalid or " + valid2_name);
    testPrune(invalid_name + " or true",
              "true");

    testPrune(valid_name + " or false",
              valid_name);
    testPrune(valid_name + " or " + invalid2_name,
              valid_name + " or invalid");
    testPrune(valid_name + " or " + valid2_name,
              valid_name + " or " + valid2_name);
    testPrune(valid_name + " or true",
              "true");

    testPrune("true or false",
              "true");
    testPrune(true_name + " or " + invalid2_name,
              "true");
    testPrune(true_name + " or " + valid2_name,
              "true");
    testPrune("true or true",
              "true");
}


TEST_F(SelectPrunerTest, Test_that_selection_expressions_are_pruned)
{
    testPrune("not ((test))",
              "false");
    testPrune("not ((test and (test.aa > 3999)))",
              "test.aa <= 3999");
    testPrune("not ((test and (test.ab > 3999)))",
              "test.ab <= 3999");
    testPrune("not ((test and (test.af > 3999)))",
              "invalid");
    testPrune("not ((test and (test_2.ac > 3999)))",
              "invalid");
    testPrune("not ((test and (test.af > 3999)))",
              "true",
              "test_2");
    const char *combined =
        "not ((test and (test.aa > 3999)) or (test_2 and (test_2.ac > 4999)))";
    testPrune(combined,
              "test.aa <= 3999");
    testPrune(combined,
              "test_2.ac <= 4999",
              "test_2");
}


TEST_F(SelectPrunerTest, Test_that_De_Morgans_laws_are_applied)
{
    testPrune("not (test.aa < 3901 and test.ab < 3902)",
              "test.aa >= 3901 or test.ab >= 3902");
    testPrune("not (test.aa < 3903 or test.ab < 3904)",
              "test.aa >= 3903 and test.ab >= 3904");
    testPrune("not (not (test.aa < 3903 or test.ab < 3904))",
              "test.aa < 3903 or test.ab < 3904");

    testPrune("not (false and false)",
              "true");
    testPrune(empty + "not (false and " + invalid2_name + ")",
              "true");
    testPrune(empty + "not (false and " + valid2_name + ")",
              "true");
    testPrune("not (false and true)",
              "true");

    testPrune(empty + "not (" + invalid_name + " and false)",
              "true");
    testPrune(empty + "not (" + invalid_name + " and " + invalid2_name + ")",
              "invalid");
    testPrune(empty + "not (" + invalid_name + " and " + valid2_name + ")",
              empty + "invalid or " + rvalid2_name);
    testPrune(empty + "not (" + invalid_name + " and true)",
              "invalid");

    testPrune(empty + "not (" + valid_name + " and false)",
              "true");
    testPrune(empty + "not (" + valid_name + " and " + invalid2_name + ")",
              empty + rvalid_name + " or invalid");
    testPrune(empty + "not (" + valid_name + " and " + valid2_name + ")",
              rvalid_name + " or " + rvalid2_name);
    testPrune(empty + "not (" + valid_name + " and true)",
              rvalid_name);

    testPrune("not (true and false)",
              "true");
    testPrune(empty + "not (true and " + invalid2_name + ")",
              "invalid");
    testPrune(empty + "not (true and " + valid2_name + ")",
              rvalid2_name);
    testPrune("not (true and true)",
              "false");

    testPrune("not (false or false)",
              "true");
    testPrune(empty + "not (false or " + invalid2_name + ")",
              "invalid");
    testPrune(empty + "not (false or " + valid2_name + ")",
              rvalid2_name);
    testPrune("not (false or true)",
              "false");

    testPrune(empty + "not (" + invalid_name + " or false)",
              "invalid");
    testPrune(empty + "not (" + invalid_name + " or " + invalid2_name + ")",
              "invalid");
    testPrune(empty + "not (" + invalid_name + " or " + valid2_name + ")",
              empty + "invalid and " + rvalid2_name);
    testPrune(empty + "not (" + invalid_name + " or true)",
              "false");

    testPrune(empty + "not (" + valid_name + " or false)",
              rvalid_name);
    testPrune(empty + "not (" + valid_name + " or " + invalid2_name + ")",
              rvalid_name + " and invalid");
    testPrune(empty + "not (" + valid_name + " or " + valid2_name + ")",
              rvalid_name + " and " + rvalid2_name);
    testPrune(empty + "not (" + valid_name + " or true)",
              "false");

    testPrune("not (true or false)",
              "false");
    testPrune(empty + "not (true or " + invalid2_name + ")",
              "false");
    testPrune(empty + "not (true or " + valid2_name + ")",
              "false");
    testPrune("not (true or true)",
              "false");

}


TEST_F(SelectPrunerTest, Test_that_attribute_fields_and_constants_are_evaluated_before_other_fields)
{
    testPrune("test.ia == \"hello\" and test.aa > 5",
              "test.aa > 5 and test.ia == \"hello\"");
}


TEST_F(SelectPrunerTest, Test_that_functions_are_visited)
{
    testPrune("test.ia.lowercase() == \"hello\"",
              "test.ia.lowercase() == \"hello\"");
    testPrune("test_2.ac.lowercase() == \"hello\"",
              "invalid");
    testPrune("test.ia.hash() == 45",
              "test.ia.hash() == 45");
    testPrune("test_2.ic.hash() == 45",
              "invalid");
    testPrune("test.aa.abs() == 45",
              "test.aa.abs() == 45");
    testPrune("test_2.ac.abs() == 45",
              "invalid");
}


TEST_F(SelectPrunerTest, Test_that_arithmethic_values_are_visited)
{
    testPrune("test.aa + 4 < 3999",
              "test.aa + 4 < 3999");
    testPrune("test_2.ac + 4 < 3999",
              "invalid");
    testPrune("test.aa + 4.2 < 3999",
              "test.aa + 4.2 < 3999");
    testPrune("test_2.ac + 5.2 < 3999",
              "invalid");
}


TEST_F(SelectPrunerTest, Test_that_addition_is_associative)
{
    testPrune("test.aa + 4 + 5 < 3999",
              "test.aa + 4 + 5 < 3999");
    testPrune("(test.aa + 6) + 7 < 3999",
              "test.aa + 6 + 7 < 3999");
    testPrune("test.aa + (8 + 9) < 3999",
              "test.aa + 8 + 9 < 3999");
}


TEST_F(SelectPrunerTest, Test_that_subtraction_is_left_associative)
{
    testPrune("test.aa - 4 - 5 < 3999",
              "test.aa - 4 - 5 < 3999");
    testPrune("(test.aa - 6) - 7 < 3999",
              "test.aa - 6 - 7 < 3999");
    testPrune("test.aa - (8 - 9) < 3999",
              "test.aa - (8 - 9) < 3999");
}


TEST_F(SelectPrunerTest, Test_that_multiplication_is_associative)
{
    testPrune("test.aa * 4 * 5 < 3999",
              "test.aa * 4 * 5 < 3999");
    testPrune("(test.aa * 6) * 7 < 3999",
              "test.aa * 6 * 7 < 3999");
    testPrune("test.aa * (8 * 9) < 3999",
              "test.aa * 8 * 9 < 3999");
}


TEST_F(SelectPrunerTest, Test_that_division_is_left_associative)
{
    testPrune("test.aa / 4 / 5 < 3999",
              "test.aa / 4 / 5 < 3999");
    testPrune("(test.aa / 6) / 7 < 3999",
              "test.aa / 6 / 7 < 3999");
    testPrune("test.aa / (8 / 9) < 3999",
              "test.aa / (8 / 9) < 3999");
}


TEST_F(SelectPrunerTest, Test_that_mod_is_left_associative)
{
    testPrune("test.aa % 4 % 5 < 3999",
              "test.aa % 4 % 5 < 3999");
    testPrune("(test.aa % 6) % 7 < 3999",
              "test.aa % 6 % 7 < 3999");
    testPrune("test.aa % (8 % 9) < 3999",
              "test.aa % (8 % 9) < 3999");
}


TEST_F(SelectPrunerTest, Test_that_multiplication_has_higher_priority_than_addition)
{
    testPrune("test.aa + 4 * 5 < 3999",
              "test.aa + 4 * 5 < 3999");
    testPrune("(test.aa + 6) * 7 < 3999",
              "(test.aa + 6) * 7 < 3999");
    testPrune("test.aa + (8 * 9) < 3999",
              "test.aa + 8 * 9 < 3999");
    testPrune("test.aa * 4 + 5 < 3999",
              "test.aa * 4 + 5 < 3999");
    testPrune("(test.aa * 6) + 7 < 3999",
              "test.aa * 6 + 7 < 3999");
    testPrune("test.aa * (8 + 9) < 3999",
              "test.aa * (8 + 9) < 3999");
}


TEST_F(SelectPrunerTest, Test_that_toplevel_functions_are_visited)
{
    testPrune("id.scheme == \"doc\"",
              "id.scheme == \"doc\"");
    testPrune("test.aa < now() - 7200",
              "test.aa < now() - 7200");
}


TEST_F(SelectPrunerTest, Test_that_variables_are_visited)
{
    testPrune("$foovar == 4.3",
              "$foovar == 4.3");
}


TEST_F(SelectPrunerTest, Test_that_null_is_visited)
{
    testPrune("test.aa",
              "test.aa != null");
    testPrune("test.aa == null",
              "test.aa == null");
    testPrune("not test.aa",
              "test.aa == null");
}


TEST_F(SelectPrunerTest, Test_that_operator_inversion_works)
{
    testPrune("not test.aa < 3999",
              "test.aa >= 3999");
    testPrune("not test.aa <= 3999",
              "test.aa > 3999");
    testPrune("not test.aa > 3999",
              "test.aa <= 3999");
    testPrune("not test.aa >= 3999",
              "test.aa < 3999");
    testPrune("not test.aa == 3999",
              "test.aa != 3999");
    testPrune("not test.aa != 3999",
              "test.aa == 3999");
}


TEST_F(SelectPrunerTest, Test_that_fields_are_not_present_in_removed_sub_db)
{
    _hasFields = true;
    testPrune("test.aa > 5",
              "test.aa > 5");
    testPrune("test.aa == test.ab",
              "test.aa == test.ab");
    testPrune("test.aa != test.ab",
              "test.aa != test.ab");
    testPrune("not test.aa == test.ab",
              "test.aa != test.ab");
    testPrune("not test.aa != test.ab",
              "test.aa == test.ab");
    testPrune("test.ia == \"hello\"",
              "test.ia == \"hello\"");
    _hasFields = false;
    testPrune("test.aa > 5",
              "invalid");
    testPrune("test.aa == test.ab",
              "true");
    testPrune("test.aa != test.ab",
              "false");
    testPrune("test.aa < test.ab",
              "invalid");
    testPrune("test.aa > test.ab",
              "invalid");
    testPrune("test.aa <= test.ab",
              "invalid");
    testPrune("test.aa >= test.ab",
              "invalid");
    testPrune("not test.aa == test.ab",
              "false");
    testPrune("not test.aa != test.ab",
              "true");
    testPrune("test.ia == \"hello\"",
              "invalid");
    testPrune("not test.aa < test.ab",
              "invalid");
    testPrune("not test.aa > test.ab",
              "invalid");
    testPrune("not test.aa <= test.ab",
              "invalid");
    testPrune("not test.aa >= test.ab",
              "invalid");
}


TEST_F(SelectPrunerTest, Test_that_some_operators_cannot_be_inverted)
{
    testPrune("test.ia == \"hello\"",
              "test.ia == \"hello\"");
    testPrune("not test.ia == \"hello\"",
              "test.ia != \"hello\"");
    testPrune("test.ia = \"hello\"",
              "test.ia = \"hello\"");
    testPrune("not test.ia = \"hello\"",
              "not test.ia = \"hello\"");
    testPrune("not (test.ia == \"hello\" or test.ia == \"hi\")",
              "test.ia != \"hello\" and test.ia != \"hi\"");
    testPrune("not (test.ia == \"hello\" or test.ia = \"hi\")",
              "not (not test.ia != \"hello\" or test.ia = \"hi\")");
    testPrune("not (test.ia = \"hello\" or test.ia == \"hi\")",
              "not (test.ia = \"hello\" or not test.ia != \"hi\")");
    testPrune("not (test.ia = \"hello\" or test.ia = \"hi\")",
              "not (test.ia = \"hello\" or test.ia = \"hi\")");
}


TEST_F(SelectPrunerTest, Test_that_complex_field_refs_are_handled)
{
    testPrune("test.ia",
              "test.ia != null");
    testPrune("test.ia != null",
              "test.ia != null");
    testPrune("test.ia == \"hello\"",
              "test.ia == \"hello\"");
    testPrune("test.ia.foo == \"hello\"",
              "invalid");
    testPrune("test.ibs.foo == \"hello\"",
              "invalid");
    testPrune("test.ibs.x == \"hello\"",
              "test.ibs.x == \"hello\"");
    testPrune("test.ia[2] == \"hello\"",
              "invalid");
    testPrune("test.iba[2] == \"hello\"",
              "test.iba[2] == \"hello\"");
    testPrune("test.ia{foo} == \"hello\"",
              "invalid");
    testPrune("test.ibw{foo} == 4",
              "test.ibw{foo} == 4");
    testPrune("test.ibw{foo} == \"hello\"",
              "test.ibw{foo} == \"hello\"");
    testPrune("test.ibm{foo} == \"hello\"",
              "test.ibm{foo} == \"hello\"");
    testPrune("test.aa == 4",
              "test.aa == 4");
    testPrune("test.aa[4] == 4",
              "invalid");
    testPrune("test.aaa[4] == 4",
              "test.aaa[4] == 4");
    testPrune("test.aa{4} == 4",
              "invalid");
    testPrune("test.aaw{4} == 4",
              "test.aaw{4} == 4");
    testPrune("id.namespace == \"hello\"",
              "id.namespace == \"hello\"");
    testPrune("test.aa == 4 and id.namespace == \"hello\"",
              "test.aa == 4 and id.namespace == \"hello\"");
    testPrune("test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"",
              "test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"");
}

TEST_F(SelectPrunerTest, Test_that_field_values_are_invalid_when_disabling_document_access)
{
    _hasDocuments = false;
    testPrune("test.ia",
              "invalid");
    testPrune("test.ia != null",
              "invalid");
    testPrune("test.ia == \"hello\"",
              "invalid");
    testPrune("test.ia.foo == \"hello\"",
              "invalid");
    testPrune("test.ibs.foo == \"hello\"",
              "invalid");
    testPrune("test.ibs.x == \"hello\"",
              "invalid");
    testPrune("test.ia[2] == \"hello\"",
              "invalid");
    testPrune("test.iba[2] == \"hello\"",
              "invalid");
    testPrune("test.ia{foo} == \"hello\"",
              "invalid");
    testPrune("test.ibw{foo} == 4",
              "invalid");
    testPrune("test.ibw{foo} == \"hello\"",
              "invalid");
    testPrune("test.ibm{foo} == \"hello\"",
              "invalid");
    testPrune("test.aa == 4",
              "test.aa == 4");
    testPrune("test.aa[4] == 4",
              "invalid");
    testPrune("test.aaa[4] == 4",
              "invalid");
    testPrune("test.aa{4} == 4",
              "invalid");
    testPrune("test.aaw{4} == 4",
              "invalid");
    testPrune("id.namespace == \"hello\"",
              "invalid");
    testPrune("test.aa == 4 and id.namespace == \"hello\"",
              "test.aa == 4 and invalid");
    testPrune("test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"",
              "test.aa == 4 and test.ae == 5 and invalid");
}

TEST_F(SelectPrunerTest, Imported_fields_with_matching_attribute_names_are_supported)
{
    testPrune("test.my_imported_field > 0",
              "test.my_imported_field > 0");
}

TEST_F(SelectPrunerTest, Imported_fields_can_be_used_alongside_non_attribute_fields)
{
    testPrune("test.my_imported_field > 0 and id.namespace != \"foo\"",
              "test.my_imported_field > 0 and id.namespace != \"foo\"");
}

// Edge case: document type reconfigured but attribute not yet visible in Proton
TEST_F(SelectPrunerTest, Imported_fields_without_matching_attribute_are_mapped_to_constant_NullValue)
{
    testPrune("test.my_missing_imported_field != test.aa", "null != test.aa");
    // Simplified to -> "null != null" -> "false"
    testPrune("test.my_missing_imported_field != null", "false");
    // Simplified to -> "null > 0" -> "invalid", as null is not well-defined
    // for operators other than (in-)equality.
    testPrune("test.my_missing_imported_field > 0", "invalid");
}

TEST_F(SelectPrunerTest, Complex_imported_field_references_return_Invalid)
{
    testPrune("test.my_imported_field.foo", "invalid");
    testPrune("test.my_imported_field[123]", "invalid");
    testPrune("test.my_imported_field{foo}", "invalid");
}
