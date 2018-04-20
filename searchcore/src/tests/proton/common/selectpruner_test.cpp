// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchcore/proton/common/selectpruner.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>

#include <vespa/log/log.h>
LOG_SETUP("selectpruner_test");

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
using vespalib::string;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::test::MockAttributeManager;
using search::AttributeFactory;

typedef Node::UP NodeUP;

namespace
{

const int32_t doc_type_id = 787121340;
const string type_name = "test";
const string header_name = type_name + ".header";
const string body_name = type_name + ".body";
const string type_name_2 = "test_2";
const string header_name_2 = type_name_2 + ".header";
const string body_name_2 = type_name_2 + ".body";
const string false_name("false");
const string true_name("true");
const string not_name("not");
const string valid_name("test.aa > 3999");
const string valid2_name("test.ab > 4999");
const string rvalid_name("test.aa <= 3999");
const string rvalid2_name("test.ab <= 4999");
const string invalid_name("test_2.ac > 3999");
const string invalid2_name("test_2.ac > 4999");
const string empty("");

const document::DocumentId docId("doc:test:1");


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
                     addField("ae", DataType::T_INT));
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


class TestFixture
{
public:
    MockAttributeManager _amgr;
    std::unique_ptr<const DocumentTypeRepo> _repoUP;
    bool _hasFields;
    bool _hasDocuments;

    TestFixture();

    ~TestFixture();

    void
    testParse(const string &selection);

    void
    testParseFail(const string &selection);

    void
    testPrune(const string &selection,
              const string &exp);

    void
    testPrune(const string &selection,
              const string &exp,
              const string &docTypeName);
};


TestFixture::TestFixture()
    : _amgr(),
      _repoUP(),
      _hasFields(true),
      _hasDocuments(true)
{
    _amgr.addAttribute("aa", AttributeFactory::createAttribute("aa", { BasicType::INT32 }));
    _amgr.addAttribute("aaa", AttributeFactory::createAttribute("aaa", { BasicType::INT32 , CollectionType::ARRAY}));
    _amgr.addAttribute("aaw", AttributeFactory::createAttribute("aaw", { BasicType::INT32 , CollectionType::WSET}));
    _amgr.addAttribute("ae", AttributeFactory::createAttribute("ae", { BasicType::INT32 }));
    _repoUP = makeDocTypeRepo();
}


TestFixture::~TestFixture()
{
}


void
TestFixture::testParse(const string &selection)
{
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,
                                    document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info,
            "Trying to parse '%s'",
            selection.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info,
            "Parse failed: %s", e.what());
        select.reset(0);
    }
    ASSERT_TRUE(select.get() != NULL);
}


void
TestFixture::testParseFail(const string &selection)
{
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,
                                    document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info,
            "Trying to parse '%s'",
            selection.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info,
            "Parse failed: %s",
            e.getMessage().c_str());
        select.reset(0);
    }
    ASSERT_TRUE(select.get() == NULL);
}


void
TestFixture::testPrune(const string &selection,
                       const string &exp,
                       const string &docTypeName)
{
    const DocumentTypeRepo &repo(*_repoUP);
    document::select::Parser parser(repo,
                                    document::BucketIdFactory());

    NodeUP select;

    try {
        LOG(info,
            "Trying to parse '%s' with docType=%s",
            selection.c_str(),
            docTypeName.c_str());
        select = parser.parse(selection);
    } catch (document::select::ParsingFailedException &e) {
        LOG(info,
            "Parse failed: %s", e.what());
        select.reset(0);
    }
    ASSERT_TRUE(select.get() != NULL);
    std::ostringstream os;
    select->print(os, true, "");
    LOG(info, "ParseTree: '%s'", os.str().c_str());
    const DocumentType *docType = repo.getDocumentType(docTypeName);
    ASSERT_TRUE(docType != NULL);
    Document::UP emptyDoc(new Document(*docType, docId));
    emptyDoc->setRepo(repo);
    SelectPruner pruner(docTypeName, &_amgr, *emptyDoc, repo, _hasFields, _hasDocuments);
    pruner.process(*select);
    std::ostringstream pos;
    pruner.getNode()->print(pos, true, "");
    EXPECT_EQUAL(exp, pos.str());
    LOG(info,
        "Pruned ParseTree: '%s', fieldNodes=%u,%u, %s, rs=%s",
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
    EXPECT_EQUAL(exp, cvpos.str());
#if 0
    std::ostringstream os2;
    pruner.trace(os2);
    LOG(info, "trace pruned: %s", os2.str().c_str());
#endif
}


void
TestFixture::testPrune(const string &selection,
                       const string &exp)
{
    testPrune(selection, exp, "test");
}


TEST_F("Test that test setup is OK", TestFixture)
{
    const DocumentTypeRepo &repo = *f._repoUP;
    const DocumentType *docType = repo.getDocumentType("test");
    ASSERT_TRUE(docType);
    EXPECT_EQUAL(11u, docType->getFieldCount());
    EXPECT_EQUAL("String", docType->getField("ia").getDataType().getName());
    EXPECT_EQUAL("String", docType->getField("ib").getDataType().getName());
    EXPECT_EQUAL("Int", docType->getField("aa").getDataType().getName());
    EXPECT_EQUAL("Int", docType->getField("ab").getDataType().getName());
}


TEST_F("Test that simple parsing works", TestFixture)
{
    f.testParse("not ((test))");
    f.testParse("not ((test and (test.aa > 3999)))");
    f.testParse("not ((test and (test.ab > 3999)))");
    f.testParse("not ((test and (test.af > 3999)))");
    f.testParse("not ((test_2 and (test_2.af > 3999)))");
}


TEST_F("Test that wrong doctype causes parse error", TestFixture)
{
    f.testParseFail("not ((test_3 and (test_3.af > 3999)))");
}


TEST_F("Test that boolean const shortcuts are OK", TestFixture)
{
    f.testPrune("false and false",
                "false");
    f.testPrune(false_name + " and " + invalid2_name,
                "false");
    f.testPrune(false_name + " and " + valid2_name,
                "false");
    f.testPrune("false and true",
                "false");

    f.testPrune(invalid_name + " and false",
                "false");
    f.testPrune(invalid_name + " and " + invalid2_name,
                "invalid");
    f.testPrune(invalid_name + " and " + valid2_name,
                empty + "invalid and " + valid2_name);
    f.testPrune(invalid_name + " and true",
                "invalid");

    f.testPrune(valid_name + " and false",
                "false");
    f.testPrune(valid_name + " and " + invalid2_name,
                empty + valid_name + " and invalid");
    f.testPrune(valid_name + " and " + valid2_name,
                valid_name + " and " + valid2_name);
    f.testPrune(valid_name + " and true",
                valid_name);

    f.testPrune("true and false",
                "false");
    f.testPrune(true_name + " and " + invalid2_name,
                "invalid");
    f.testPrune(true_name + " and " + valid2_name,
                valid2_name);
    f.testPrune("true and true",
                "true");

    f.testPrune("false or false",
                "false");
    f.testPrune(false_name + " or " + invalid2_name,
                "invalid");
    f.testPrune(false_name + " or " + valid2_name,
                valid2_name);
    f.testPrune("false or true",
                "true");

    f.testPrune(invalid_name + " or false",
                "invalid");
    f.testPrune(invalid_name + " or " + invalid2_name,
                "invalid");
    f.testPrune(invalid_name + " or " + valid2_name,
                empty + "invalid or " + valid2_name);
    f.testPrune(invalid_name + " or true",
                "true");

    f.testPrune(valid_name + " or false",
                valid_name);
    f.testPrune(valid_name + " or " + invalid2_name,
                valid_name + " or invalid");
    f.testPrune(valid_name + " or " + valid2_name,
                valid_name + " or " + valid2_name);
    f.testPrune(valid_name + " or true",
                "true");

    f.testPrune("true or false",
                "true");
    f.testPrune(true_name + " or " + invalid2_name,
                "true");
    f.testPrune(true_name + " or " + valid2_name,
                "true");
    f.testPrune("true or true",
                "true");
}


TEST_F("Test that selection expressions are pruned", TestFixture)
{
    f.testPrune("not ((test))",
                "false");
    f.testPrune("not ((test and (test.aa > 3999)))",
                "test.aa <= 3999");
    f.testPrune("not ((test and (test.ab > 3999)))",
                     "test.ab <= 3999");
    f.testPrune("not ((test and (test.af > 3999)))",
                "invalid");
    f.testPrune("not ((test and (test_2.ac > 3999)))",
                "invalid");
    f.testPrune("not ((test and (test.af > 3999)))",
                "true",
                "test_2");
    const char *combined =
        "not ((test and (test.aa > 3999)) or (test_2 and (test_2.ac > 4999)))";
    f.testPrune(combined,
                "test.aa <= 3999");
    f.testPrune(combined,
                "test_2.ac <= 4999",
                "test_2");
}


TEST_F("Test that De Morgan's laws are applied", TestFixture)
{
    f.testPrune("not (test.aa < 3901 and test.ab < 3902)",
                "test.aa >= 3901 or test.ab >= 3902");
    f.testPrune("not (test.aa < 3903 or test.ab < 3904)",
                "test.aa >= 3903 and test.ab >= 3904");
    f.testPrune("not (not (test.aa < 3903 or test.ab < 3904))",
                "test.aa < 3903 or test.ab < 3904");

    f.testPrune("not (false and false)",
                "true");
    f.testPrune(empty + "not (false and " + invalid2_name + ")",
                "true");
    f.testPrune(empty + "not (false and " + valid2_name + ")",
                "true");
    f.testPrune("not (false and true)",
                "true");

    f.testPrune(empty + "not (" + invalid_name + " and false)",
                "true");
    f.testPrune(empty + "not (" + invalid_name + " and " + invalid2_name + ")",
                "invalid");
    f.testPrune(empty + "not (" + invalid_name + " and " + valid2_name + ")",
                empty + "invalid or " + rvalid2_name);
    f.testPrune(empty + "not (" + invalid_name + " and true)",
                "invalid");

    f.testPrune(empty + "not (" + valid_name + " and false)",
                "true");
    f.testPrune(empty + "not (" + valid_name + " and " + invalid2_name + ")",
                empty + rvalid_name + " or invalid");
    f.testPrune(empty + "not (" + valid_name + " and " + valid2_name + ")",
                rvalid_name + " or " + rvalid2_name);
    f.testPrune(empty + "not (" + valid_name + " and true)",
                rvalid_name);

    f.testPrune("not (true and false)",
                "true");
    f.testPrune(empty + "not (true and " + invalid2_name + ")",
                "invalid");
    f.testPrune(empty + "not (true and " + valid2_name + ")",
                rvalid2_name);
    f.testPrune("not (true and true)",
                "false");

    f.testPrune("not (false or false)",
                "true");
    f.testPrune(empty + "not (false or " + invalid2_name + ")",
                "invalid");
    f.testPrune(empty + "not (false or " + valid2_name + ")",
                rvalid2_name);
    f.testPrune("not (false or true)",
                "false");

    f.testPrune(empty + "not (" + invalid_name + " or false)",
                "invalid");
    f.testPrune(empty + "not (" + invalid_name + " or " + invalid2_name + ")",
                "invalid");
    f.testPrune(empty + "not (" + invalid_name + " or " + valid2_name + ")",
                empty + "invalid and " + rvalid2_name);
    f.testPrune(empty + "not (" + invalid_name + " or true)",
                "false");

    f.testPrune(empty + "not (" + valid_name + " or false)",
                rvalid_name);
    f.testPrune(empty + "not (" + valid_name + " or " + invalid2_name + ")",
                rvalid_name + " and invalid");
    f.testPrune(empty + "not (" + valid_name + " or " + valid2_name + ")",
                rvalid_name + " and " + rvalid2_name);
    f.testPrune(empty + "not (" + valid_name + " or true)",
                "false");

    f.testPrune("not (true or false)",
                "false");
    f.testPrune(empty + "not (true or " + invalid2_name + ")",
                "false");
    f.testPrune(empty + "not (true or " + valid2_name + ")",
                "false");
    f.testPrune("not (true or true)",
                "false");

}


TEST_F("Test that attribute fields and constants are evaluated"
       " before other fields",
       TestFixture)
{
    f.testPrune("test.ia == \"hello\" and test.aa > 5",
                "test.aa > 5 and test.ia == \"hello\"");
}


TEST_F("Test that functions are visited", TestFixture)
{
    f.testPrune("test.ia.lowercase() == \"hello\"",
                "test.ia.lowercase() == \"hello\"");
    f.testPrune("test_2.ac.lowercase() == \"hello\"",
                "invalid");
    f.testPrune("test.ia.hash() == 45",
                "test.ia.hash() == 45");
    f.testPrune("test_2.ic.hash() == 45",
                "invalid");
    f.testPrune("test.aa.abs() == 45",
                "test.aa.abs() == 45");
    f.testPrune("test_2.ac.abs() == 45",
                "invalid");
}


TEST_F("Test that arithmethic values are visited", TestFixture)
{
    f.testPrune("test.aa + 4 < 3999",
                "test.aa + 4 < 3999");
    f.testPrune("test_2.ac + 4 < 3999",
                "invalid");
    f.testPrune("test.aa + 4.2 < 3999",
                "test.aa + 4.2 < 3999");
    f.testPrune("test_2.ac + 5.2 < 3999",
                "invalid");
}


TEST_F("Test that addition is associative", TestFixture)
{
    f.testPrune("test.aa + 4 + 5 < 3999",
                "test.aa + 4 + 5 < 3999");
    f.testPrune("(test.aa + 6) + 7 < 3999",
                "test.aa + 6 + 7 < 3999");
    f.testPrune("test.aa + (8 + 9) < 3999",
                "test.aa + 8 + 9 < 3999");
}


TEST_F("Test that subtraction is left associative", TestFixture)
{
    f.testPrune("test.aa - 4 - 5 < 3999",
                "test.aa - 4 - 5 < 3999");
    f.testPrune("(test.aa - 6) - 7 < 3999",
                "test.aa - 6 - 7 < 3999");
    f.testPrune("test.aa - (8 - 9) < 3999",
                "test.aa - (8 - 9) < 3999");
}


TEST_F("Test that multiplication is associative", TestFixture)
{
    f.testPrune("test.aa * 4 * 5 < 3999",
                "test.aa * 4 * 5 < 3999");
    f.testPrune("(test.aa * 6) * 7 < 3999",
                "test.aa * 6 * 7 < 3999");
    f.testPrune("test.aa * (8 * 9) < 3999",
                "test.aa * 8 * 9 < 3999");
}


TEST_F("Test that division is left associative", TestFixture)
{
    f.testPrune("test.aa / 4 / 5 < 3999",
                "test.aa / 4 / 5 < 3999");
    f.testPrune("(test.aa / 6) / 7 < 3999",
                "test.aa / 6 / 7 < 3999");
    f.testPrune("test.aa / (8 / 9) < 3999",
                "test.aa / (8 / 9) < 3999");
}


TEST_F("Test that mod is left associative", TestFixture)
{
    f.testPrune("test.aa % 4 % 5 < 3999",
                "test.aa % 4 % 5 < 3999");
    f.testPrune("(test.aa % 6) % 7 < 3999",
                "test.aa % 6 % 7 < 3999");
    f.testPrune("test.aa % (8 % 9) < 3999",
                "test.aa % (8 % 9) < 3999");
}


TEST_F("Test that multiplication has higher priority than addition",
       TestFixture)
{
    f.testPrune("test.aa + 4 * 5 < 3999",
                "test.aa + 4 * 5 < 3999");
    f.testPrune("(test.aa + 6) * 7 < 3999",
                "(test.aa + 6) * 7 < 3999");
    f.testPrune("test.aa + (8 * 9) < 3999",
                "test.aa + 8 * 9 < 3999");
    f.testPrune("test.aa * 4 + 5 < 3999",
                "test.aa * 4 + 5 < 3999");
    f.testPrune("(test.aa * 6) + 7 < 3999",
                "test.aa * 6 + 7 < 3999");
    f.testPrune("test.aa * (8 + 9) < 3999",
                "test.aa * (8 + 9) < 3999");
}


TEST_F("Test that toplevel functions are visited", TestFixture)
{
    f.testPrune("id.scheme == \"doc\"",
                "id.scheme == \"doc\"");
    f.testPrune("test.aa < now() - 7200",
                "test.aa < now() - 7200");
}


TEST_F("Test that variables are visited", TestFixture)
{
    f.testPrune("$foovar == 4.3",
                "$foovar == 4.3");
}


TEST_F("Test that null is visited", TestFixture)
{
    f.testPrune("test.aa",
                "test.aa != null");
    f.testPrune("test.aa == null",
                "test.aa == null");
    f.testPrune("not test.aa",
                "test.aa == null");
}


TEST_F("Test that operator inversion works", TestFixture)
{
    f.testPrune("not test.aa < 3999",
                "test.aa >= 3999");
    f.testPrune("not test.aa <= 3999",
                "test.aa > 3999");
    f.testPrune("not test.aa > 3999",
                "test.aa <= 3999");
    f.testPrune("not test.aa >= 3999",
                "test.aa < 3999");
    f.testPrune("not test.aa == 3999",
                "test.aa != 3999");
    f.testPrune("not test.aa != 3999",
                "test.aa == 3999");
}


TEST_F("Test that fields are not present in removed sub db", TestFixture)
{
    f._hasFields = true;
    f.testPrune("test.aa > 5",
                "test.aa > 5");
    f.testPrune("test.aa == test.ab",
                "test.aa == test.ab");
    f.testPrune("test.aa != test.ab",
                "test.aa != test.ab");
    f.testPrune("not test.aa == test.ab",
                "test.aa != test.ab");
    f.testPrune("not test.aa != test.ab",
                "test.aa == test.ab");
    f.testPrune("test.ia == \"hello\"",
                "test.ia == \"hello\"");
    f._hasFields = false;
    f.testPrune("test.aa > 5",
                "invalid");
    f.testPrune("test.aa == test.ab",
                "true");
    f.testPrune("test.aa != test.ab",
                "false");
    f.testPrune("test.aa < test.ab",
                "invalid");
    f.testPrune("test.aa > test.ab",
                "invalid");
    f.testPrune("test.aa <= test.ab",
                "invalid");
    f.testPrune("test.aa >= test.ab",
                "invalid");
    f.testPrune("not test.aa == test.ab",
                "false");
    f.testPrune("not test.aa != test.ab",
                "true");
    f.testPrune("test.ia == \"hello\"",
                "invalid");
    f.testPrune("not test.aa < test.ab",
                "invalid");
    f.testPrune("not test.aa > test.ab",
                "invalid");
    f.testPrune("not test.aa <= test.ab",
                "invalid");
    f.testPrune("not test.aa >= test.ab",
                "invalid");
}


TEST_F("Test that some operators cannot be inverted", TestFixture)
{
    f.testPrune("test.ia == \"hello\"",
                "test.ia == \"hello\"");
    f.testPrune("not test.ia == \"hello\"",
                "test.ia != \"hello\"");
    f.testPrune("test.ia = \"hello\"",
                "test.ia = \"hello\"");
    f.testPrune("not test.ia = \"hello\"",
                "not test.ia = \"hello\"");
    f.testPrune("not (test.ia == \"hello\" or test.ia == \"hi\")",
                "test.ia != \"hello\" and test.ia != \"hi\"");
    f.testPrune("not (test.ia == \"hello\" or test.ia = \"hi\")",
                "not (not test.ia != \"hello\" or test.ia = \"hi\")");
    f.testPrune("not (test.ia = \"hello\" or test.ia == \"hi\")",
                "not (test.ia = \"hello\" or not test.ia != \"hi\")");
    f.testPrune("not (test.ia = \"hello\" or test.ia = \"hi\")",
                "not (test.ia = \"hello\" or test.ia = \"hi\")");
}


TEST_F("Test that complex field refs are handled", TestFixture)
{
    f.testPrune("test.ia",
                "test.ia != null");
    f.testPrune("test.ia != null",
                "test.ia != null");
    f.testPrune("test.ia == \"hello\"",
                "test.ia == \"hello\"");
    f.testPrune("test.ia.foo == \"hello\"",
                "invalid");
    f.testPrune("test.ibs.foo == \"hello\"",
                "invalid");
    f.testPrune("test.ibs.x == \"hello\"",
                "test.ibs.x == \"hello\"");
    f.testPrune("test.ia[2] == \"hello\"",
                "invalid");
    f.testPrune("test.iba[2] == \"hello\"",
                "test.iba[2] == \"hello\"");
    f.testPrune("test.ia{foo} == \"hello\"",
                "invalid");
    f.testPrune("test.ibw{foo} == 4",
                "test.ibw{foo} == 4");
    f.testPrune("test.ibw{foo} == \"hello\"",
                "test.ibw{foo} == \"hello\"");
    f.testPrune("test.ibm{foo} == \"hello\"",
                "test.ibm{foo} == \"hello\"");
    f.testPrune("test.aa == 4",
                "test.aa == 4");
    f.testPrune("test.aa[4] == 4",
                "invalid");
    f.testPrune("test.aaa[4] == 4",
                "test.aaa[4] == 4");
    f.testPrune("test.aa{4} == 4",
                "invalid");
    f.testPrune("test.aaw{4} == 4",
                "test.aaw{4} == 4");
    f.testPrune("id.namespace == \"hello\"",
                "id.namespace == \"hello\"");
    f.testPrune("test.aa == 4 and id.namespace == \"hello\"",
                "test.aa == 4 and id.namespace == \"hello\"");
    f.testPrune("test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"",
                "test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"");
}

TEST_F("Test that field values are invalid when disabling document access", TestFixture)
{
    f._hasDocuments = false;
    f.testPrune("test.ia",
                "invalid");
    f.testPrune("test.ia != null",
                "invalid");
    f.testPrune("test.ia == \"hello\"",
                "invalid");
    f.testPrune("test.ia.foo == \"hello\"",
                "invalid");
    f.testPrune("test.ibs.foo == \"hello\"",
                "invalid");
    f.testPrune("test.ibs.x == \"hello\"",
                "invalid");
    f.testPrune("test.ia[2] == \"hello\"",
                "invalid");
    f.testPrune("test.iba[2] == \"hello\"",
                "invalid");
    f.testPrune("test.ia{foo} == \"hello\"",
                "invalid");
    f.testPrune("test.ibw{foo} == 4",
                "invalid");
    f.testPrune("test.ibw{foo} == \"hello\"",
                "invalid");
    f.testPrune("test.ibm{foo} == \"hello\"",
                "invalid");
    f.testPrune("test.aa == 4",
                "test.aa == 4");
    f.testPrune("test.aa[4] == 4",
                "invalid");
    f.testPrune("test.aaa[4] == 4",
                "invalid");
    f.testPrune("test.aa{4} == 4",
                "invalid");
    f.testPrune("test.aaw{4} == 4",
                "invalid");
    f.testPrune("id.namespace == \"hello\"",
                "invalid");
    f.testPrune("test.aa == 4 and id.namespace == \"hello\"",
                "test.aa == 4 and invalid");
    f.testPrune("test.aa == 4 and test.ae == 5 and id.namespace == \"hello\"",
                "test.aa == 4 and test.ae == 5 and invalid");
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
