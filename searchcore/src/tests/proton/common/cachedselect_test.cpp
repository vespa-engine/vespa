// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/select/parser.h>
#include <vespa/searchcore/proton/common/cachedselect.h>
#include <vespa/searchcore/proton/common/selectcontext.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/postinglistattribute.h>
#include <vespa/searchlib/attribute/singleenumattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericenumattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericpostattribute.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP(".cachedselect_test");

using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::IntFieldValue;
using document::StringFieldValue;
using document::config_builder::Array;
using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Map;
using document::config_builder::Struct;
using document::config_builder::Wset;
using document::select::CloningVisitor;
using document::select::Context;
using document::select::Node;
using document::select::Result;
using document::select::ResultSet;
using proton::CachedSelect;
using proton::SelectContext;
using search::AttributeContext;
using search::AttributeFactory;
using search::AttributeGuard;
using search::AttributePosting;
using search::AttributeVector;
using search::EnumAttribute;
using search::IntegerAttribute;
using search::IntegerAttributeTemplate;
using search::SingleValueNumericPostingAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::IAttributeContext;
using search::attribute::test::MockAttributeManager;
using std::string;

using IATint32 = IntegerAttributeTemplate<int32_t>;
using IntEnumAttribute = EnumAttribute<IATint32>;
using NodeUP = Node::UP;
using SessionUP = std::unique_ptr<CachedSelect::Session>;

#if 0
extern template class SingleValueNumericPostingAttribute<IntPostingAttribute>;
#endif

using SvIntAttr = SingleValueNumericPostingAttribute<IntEnumAttribute>;

namespace {

const int32_t doc_type_id = 787121340;
const string type_name = "test";
const string header_name = type_name + ".header";
const string body_name = type_name + ".body";
const string type_name_2 = "test_2";
const string header_name_2 = type_name_2 + ".header";
const string body_name_2 = type_name_2 + ".body";

const int32_t noIntVal = std::numeric_limits<int32_t>::min();


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
                     addField("ab", DataType::T_INT)).
                     imported_field("my_imported_field");
    builder.document(doc_type_id + 1, type_name_2,
                     Struct(header_name_2), Struct(body_name_2).
                     addField("ic", DataType::T_STRING).
                     addField("id", DataType::T_STRING).
                     addField("ac", DataType::T_INT).
                     addField("ad", DataType::T_INT));
    return std::unique_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(builder.config()));
}


Document::UP
makeDoc(const DocumentTypeRepo &repo,
        const string &docId,
        const string &ia,
        const string &ib,
        int32_t aa,
        int32_t ab)
{
    const DocumentType *docType = repo.getDocumentType("test");
    auto doc = std::make_unique<Document>(repo, *docType, DocumentId(docId));
    if (ia != "null")
        doc->setValue("ia", StringFieldValue(ia));
    if (ib != "null")
        doc->setValue("ib", StringFieldValue(ib));
    if (aa != noIntVal)
        doc->setValue("aa", IntFieldValue(aa));
    if (ab != noIntVal)
        doc->setValue("ab", IntFieldValue(ab));
    return doc;
}

bool
checkSelect(const NodeUP &sel,
            const Context &ctx,
            const Result &exp)
{
    bool failed = false;
    EXPECT_TRUE(sel->contains(ctx) == exp) << (failed = true, "");
    if (!failed) {
        return true;
    }
    std::ostringstream os;
    EXPECT_TRUE(sel->trace(ctx, os) == exp);
    LOG(info, "trace output: '%s'", os.str().c_str());
    return false;
}

void
checkSelect(const CachedSelect::SP &cs,
            uint32_t docId,
            const Document &doc,
            const Result &exp)
{
    SCOPED_TRACE("docId=" + std::to_string(docId));
    SelectContext ctx(*cs);
    ctx._docId = docId;
    ctx._doc = &doc;
    ctx.getAttributeGuards();
    bool expSessionContains = (cs->preDocOnlySelect() || (exp == Result::True));
    EXPECT_TRUE(checkSelect(cs->docSelect(), ctx, exp));
    EXPECT_EQ(expSessionContains, cs->createSession()->contains_doc(ctx));
}

void
checkSelect(const CachedSelect::SP &cs,
            uint32_t docId,
            const Result &exp,
            bool expSessionContains)
{
    SCOPED_TRACE("docId=" + std::to_string(docId));
    SelectContext ctx(*cs);
    ctx._docId = docId;
    ctx.getAttributeGuards();
    EXPECT_TRUE(checkSelect((cs->preDocOnlySelect() ? cs->preDocOnlySelect() : cs->preDocSelect()), ctx, exp));
    EXPECT_EQ(expSessionContains, cs->createSession()->contains_pre_doc(ctx));
}

void
checkSelect(const CachedSelect::SP &cs,
            uint32_t docId,
            const Result &exp)
{
    checkSelect(cs, docId, exp, (exp == Result::True));
}


class MyIntAv : public SvIntAttr
{
    mutable uint32_t _gets;
public:
    MyIntAv(const string &name)
        : SvIntAttr(name, Config(BasicType::INT32,
                                 CollectionType::SINGLE,
                                 true)),
          _gets(0)
    {
    }
    ~MyIntAv() override;

    virtual uint32_t
    get(AttributeVector::DocId doc, largeint_t *v, uint32_t sz) const override
    {
        ++_gets;
        return SvIntAttr::get(doc, v, sz);
    }

    uint32_t
    getGets() const
    {
        return _gets;
    }
};

MyIntAv::~MyIntAv() = default;

class MyAttributeManager : public MockAttributeManager
{
public:
    using MockAttributeManager::addAttribute;
    void addAttribute(const std::string &name) {
        if (findAttribute(name).get() != nullptr) {
            return;
        }
        AttributeVector::SP av(new MyIntAv(name));
        MockAttributeManager::addAttribute(name, av);
    }
    MyIntAv *getAsMyIntAttribute(const std::string &name) const {
        return (dynamic_cast<MyIntAv *>(findAttribute(name).get()));
    }
};


class MyDB
{
public:
    using UP = std::unique_ptr<MyDB>;

    const DocumentTypeRepo &_repo;
    MyAttributeManager &_amgr;
    using DocIdToLid = std::map<string, uint32_t>;
    using LidToDocSP = std::map<uint32_t, Document::SP>;
    DocIdToLid _docIdToLid;
    LidToDocSP _lidToDocSP;

    MyDB(const DocumentTypeRepo &repo,
         MyAttributeManager &amgr)
        : _repo(repo),
          _amgr(amgr)
    {
    }

    void
    addDoc(uint32_t lid,
           const string &docId,
           const string &ia,
           const string &ib,
           int32_t aa,
           int32_t ab);

    const Document &
    getDoc(uint32_t lid) const;
};


void
MyDB::addDoc(uint32_t lid,
             const string &docId,
             const string &ia,
             const string &ib,
             int32_t aa,
             int32_t ab)
{
    Document::UP doc(makeDoc(_repo, docId, ia, ib, aa, ab));

    _docIdToLid[docId] = lid;
    _lidToDocSP[lid] = std::move(doc);
    auto add_attr_value = [lid, aa](auto guard) {
        AttributeVector &av = *guard->get();
        if (lid >= av.getNumDocs()) {
            AttributeVector::DocId checkDocId(0u);
            ASSERT_TRUE(av.addDoc(checkDocId));
            ASSERT_EQ(lid, checkDocId);
        }
        auto &iav(dynamic_cast<IntegerAttribute &>(av));
        AttributeVector::largeint_t laa(aa);
        EXPECT_TRUE(iav.update(lid, laa));
        av.commit();
    };

    add_attr_value(_amgr.getAttribute("aa"));
    add_attr_value(_amgr.getAttribute("my_imported_field"));
}


const Document &
MyDB::getDoc(uint32_t lid) const
{
    auto it = _lidToDocSP.find(lid);
    assert(it != _lidToDocSP.end());
    return *it->second;
}


class TestFixture
{
public:
    std::unique_ptr<const DocumentTypeRepo> _repoUP;
    bool _hasFields;
    MyAttributeManager _amgr;
    MyDB::UP _db;

    TestFixture();

    ~TestFixture();

    CachedSelect::SP
    testParse(const string &selection,
              const string &docTypeName);

    MyDB &db() { return *_db; }

};


TestFixture::TestFixture()
    : _repoUP(),
      _hasFields(true),
      _amgr(),
      _db()
{
    _repoUP = makeDocTypeRepo();

    _amgr.addAttribute("aa");
    _amgr.addAttribute("aaa", AttributeFactory::createAttribute("aaa", {BasicType::INT32, CollectionType::ARRAY}));
    _amgr.addAttribute("aaw", AttributeFactory::createAttribute("aaw", {BasicType::INT32, CollectionType::WSET}));
    // "Faked" imported attribute, as in `selectpruner_test.cpp`
    _amgr.addAttribute("my_imported_field", AttributeFactory::createAttribute("my_imported_field", { BasicType::INT32 }));

    _db = std::make_unique<MyDB>(*_repoUP, _amgr);
}


TestFixture::~TestFixture() = default;

CachedSelect::SP
TestFixture::testParse(const string &selection,
                       const string &docTypeName)
{
    const DocumentTypeRepo &repo(*_repoUP);

    CachedSelect::SP res(new CachedSelect);

    const DocumentType *docType = repo.getDocumentType(docTypeName);
    assert(docType != nullptr);
    auto emptyDoc = std::make_unique<Document>(repo, *docType, DocumentId());

    res->set(selection,
             docTypeName,
             *emptyDoc,
             repo,
             &_amgr,
             _hasFields);
    
    EXPECT_TRUE(res->docSelect());
    return res;
}

class Stats {
private:
    bool _preDocOnlySelect;
    bool _preDocSelect;
    bool _allFalse;
    bool _allTrue;
    bool _allInvalid;
    uint32_t _fieldNodes;
    uint32_t _attrFieldNodes;
    uint32_t _svAttrFieldNodes;

public:
    Stats()
        : _preDocOnlySelect(false),
          _preDocSelect(false),
          _allFalse(false),
          _allTrue(false),
          _allInvalid(false),
          _fieldNodes(0),
          _attrFieldNodes(0),
          _svAttrFieldNodes(0)
    {}
    Stats &preDocOnlySelect() { _preDocOnlySelect = true; return *this; }
    Stats &preDocSelect() { _preDocSelect = true; return *this; }
    Stats &allFalse() { _allFalse = true; return *this; }
    Stats &allTrue() { _allTrue = true; return *this; }
    Stats &allInvalid() { _allInvalid = true; return *this; };
    Stats &fieldNodes(uint32_t value) { _fieldNodes = value; return *this; }
    Stats &attrFieldNodes(uint32_t value) { _attrFieldNodes = value; return *this; }
    Stats &svAttrFieldNodes(uint32_t value) { _svAttrFieldNodes = value; return *this; }

    void assertEquals(const CachedSelect &select) const {
        EXPECT_EQ(_preDocOnlySelect, (bool)select.preDocOnlySelect());
        EXPECT_EQ(_preDocSelect, (bool)select.preDocSelect());
        EXPECT_EQ(_allFalse, select.allFalse());
        EXPECT_EQ(_allTrue, select.allTrue());
        EXPECT_EQ(_allInvalid, select.allInvalid());
        EXPECT_EQ(_fieldNodes, select.fieldNodes());
        EXPECT_EQ(_attrFieldNodes, select.attrFieldNodes());
        EXPECT_EQ(_svAttrFieldNodes, select.svAttrFieldNodes());
    }
};

void
assertEquals(const Stats &stats, const CachedSelect &select)
{
    stats.assertEquals(select);
}


TEST(CachedSelectTest, Test_that_test_setup_is_OK)
{
    TestFixture f;
    const DocumentTypeRepo &repo = *f._repoUP;
    const DocumentType *docType = repo.getDocumentType("test");
    ASSERT_TRUE(docType);
    EXPECT_EQ(10u, docType->getFieldCount());
    EXPECT_EQ("String", docType->getField("ia").getDataType().getName());
    EXPECT_EQ("String", docType->getField("ib").getDataType().getName());
    EXPECT_EQ("Int", docType->getField("aa").getDataType().getName());
    EXPECT_EQ("Int", docType->getField("ab").getDataType().getName());
}


TEST(CachedSelectTest, Test_that_simple_parsing_works)
{
    TestFixture f;
    f.testParse("not ((test))", "test");
    f.testParse("not ((test and (test.aa > 3999)))", "test");
    f.testParse("not ((test and (test.ab > 3999)))", "test");
    f.testParse("not ((test and (test.af > 3999)))", "test");
    f.testParse("not ((test_2 and (test_2.af > 3999)))", "test");
}


TEST(CachedSelectTest, Test_that_const_is_flagged)
{
    TestFixture f;
    CachedSelect::SP cs;

    cs = f.testParse("false", "test");
    EXPECT_TRUE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQ(0u, cs->fieldNodes());
    cs = f.testParse("true", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_TRUE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQ(0u, cs->fieldNodes());
    cs = f.testParse("test_2.ac > 4999", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_TRUE(cs->allInvalid());
    EXPECT_EQ(0u, cs->fieldNodes());
    cs = f.testParse("test.aa > 4999", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQ(1u, cs->fieldNodes());
    EXPECT_EQ(1u, cs->attrFieldNodes());
    EXPECT_EQ(1u, cs->svAttrFieldNodes());
}


TEST(CachedSelectTest, Test_that_basic_select_works)
{
    TestFixture f;
    MyDB &db(*f._db);
    
    db.addDoc(1u, "id:ns:test::1", "hello", "null", 45, 37);
    db.addDoc(2u, "id:ns:test::2", "gotcha", "foo", 3, 25);
    db.addDoc(3u, "id:ns:test::3", "gotcha", "foo", noIntVal, noIntVal);
    db.addDoc(4u, "id:ns:test::4", "null", "foo", noIntVal, noIntVal);
    
    CachedSelect::SP cs;

    {
        std::string selection("test.ia == \"hello\"");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().fieldNodes(1).attrFieldNodes(0).svAttrFieldNodes(0), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::True);
        checkSelect(cs, 2u, db.getDoc(2u), Result::False);
        checkSelect(cs, 3u, db.getDoc(3u), Result::False);
        checkSelect(cs, 4u, db.getDoc(4u), Result::False);
    }

    {
        std::string selection("test.ia.foo == \"hello\"");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.ia[2] == \"hello\"");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.ia{foo} == \"hello\"");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.ia < \"hello\"");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().fieldNodes(1).attrFieldNodes(0).svAttrFieldNodes(0), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::False);
        checkSelect(cs, 2u, db.getDoc(2u), Result::True);
        checkSelect(cs, 3u, db.getDoc(3u), Result::True);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.aa == 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::False);
        checkSelect(cs, 2u, db.getDoc(2u), Result::True);
        checkSelect(cs, 3u, db.getDoc(3u), Result::False);
        checkSelect(cs, 4u, db.getDoc(4u), Result::False);
        checkSelect(cs, 1u, Result::False);
        checkSelect(cs, 2u, Result::True);
        checkSelect(cs, 3u, Result::False);
        checkSelect(cs, 4u, Result::False);
    }

    {
        std::string selection("test.aa.foo == 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.aa[2] == 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.aa{4} > 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().allInvalid(), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::Invalid);
        checkSelect(cs, 2u, db.getDoc(2u), Result::Invalid);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
    }

    {
        std::string selection("test.aaa[2] == 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(0), *cs);
    }

    {
        std::string selection("test.aaw{4} > 3");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(0), *cs);
    }

    {
        std::string selection("test.aa < 45");
        SCOPED_TRACE(selection);
        cs = f.testParse(selection, "test");
        assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs);
        checkSelect(cs, 1u, db.getDoc(1u), Result::False);
        checkSelect(cs, 2u, db.getDoc(2u), Result::True);
        checkSelect(cs, 3u, db.getDoc(3u), Result::Invalid);
        checkSelect(cs, 4u, db.getDoc(4u), Result::Invalid);
        checkSelect(cs, 1u, Result::False, false);
        checkSelect(cs, 2u, Result::True, true);
        checkSelect(cs, 3u, Result::Invalid, false);
        checkSelect(cs, 4u, Result::Invalid, false);
    }

    MyIntAv *v = f._amgr.getAsMyIntAttribute("aa");
    EXPECT_TRUE(v != nullptr);
    EXPECT_EQ(8u, v->getGets());
}

struct PreDocSelectFixture : public TestFixture {
    PreDocSelectFixture()
        : TestFixture()
    {
        db().addDoc(1u, "id:ns:test::1", "foo", "null", 3, 5);
        db().addDoc(2u, "id:ns:test::1", "bar", "null", 3, 5);
        db().addDoc(3u, "id:ns:test::2", "foo", "null", 7, 5);
    }
};

TEST(CachedSelectTest, Test_that_single_value_attribute_combined_with_non_attribute_field_results_in_pre_document_select_pruner)
{
    PreDocSelectFixture f;
    CachedSelect::SP cs = f.testParse("test.aa == 3 AND test.ia == \"foo\"", "test");
    assertEquals(Stats().preDocSelect().fieldNodes(2).attrFieldNodes(1).svAttrFieldNodes(1), *cs);

    checkSelect(cs, 1u, Result::Invalid, true);
    checkSelect(cs, 2u, Result::Invalid, true);
    checkSelect(cs, 3u, Result::False, false);
    checkSelect(cs, 1u, f.db().getDoc(1u), Result::True);
    checkSelect(cs, 2u, f.db().getDoc(2u), Result::False);
    checkSelect(cs, 3u, f.db().getDoc(3u), Result::False);
}

TEST(CachedSelectTest, Test_that_single_value_attribute_with_complex_attribute_field_results_in_pre_document_select_pruner)
{
    PreDocSelectFixture f;
    CachedSelect::SP cs = f.testParse("test.aa == 3 AND test.aaa[0] == 5", "test");
    assertEquals(Stats().preDocSelect().fieldNodes(2).attrFieldNodes(2).svAttrFieldNodes(1), *cs);

    checkSelect(cs, 1u, Result::Invalid, true);
    checkSelect(cs, 2u, Result::Invalid, true);
    checkSelect(cs, 3u, Result::False, false);
    checkSelect(cs, 1u, f.db().getDoc(1u), Result::False);
    checkSelect(cs, 2u, f.db().getDoc(2u), Result::False);
    checkSelect(cs, 3u, f.db().getDoc(3u), Result::False);
}

TEST(CachedSelectTest, Imported_field_can_be_used_in_pre_doc_selections_with_only_attribute_fields)
{
    PreDocSelectFixture f;
    auto cs = f.testParse("test.my_imported_field == 3", "test");
    assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs);

    checkSelect(cs, 1u, Result::True,  true);
    checkSelect(cs, 2u, Result::True,  true);
    checkSelect(cs, 3u, Result::False, false);
    // Cannot match against document here since preDocOnly is set; will just return false.
    checkSelect(cs, 1u, f.db().getDoc(1u), Result::False);
    checkSelect(cs, 2u, f.db().getDoc(2u), Result::False);
    checkSelect(cs, 3u, f.db().getDoc(3u), Result::False);
}

TEST(CachedSelectTest, Imported_field_can_be_used_in_doc_selections_with_mixed_attribute_and_non_attribute_fields)
{
    PreDocSelectFixture f;
    // `id.namespace` requires a doc store fetch and cannot be satisfied by attributes alone
    auto cs = f.testParse("test.my_imported_field == 3 and id.namespace != 'foo'", "test");
    assertEquals(Stats().preDocSelect().fieldNodes(2).attrFieldNodes(1).svAttrFieldNodes(1), *cs);

    // 2 first checks cannot be completed in pre-doc stage alone
    checkSelect(cs, 1u, Result::Invalid, true);  // -> doc eval stage
    checkSelect(cs, 2u, Result::Invalid, true);  // -> doc eval stage
    checkSelect(cs, 3u, Result::False,   false); // short-circuited since attr value 7 != 3
    // When matching against a concrete document, it's crucial that the selection AST contains
    // attribute references for at least all imported fields, or we'll implicitly fall back to
    // returning null for all imported fields (as they do not exist in the document itself).
    checkSelect(cs, 1u, f.db().getDoc(1u), Result::True);
    checkSelect(cs, 2u, f.db().getDoc(2u), Result::True);
    checkSelect(cs, 3u, f.db().getDoc(3u), Result::False);
}

TEST(CachedSelectTest, Test_performance_when_using_attributes)
{
    TestFixture f;
    MyDB &db(*f._db);
    
    db.addDoc(1u, "id:ns:test::1", "hello", "null", 45, 37);
    db.addDoc(2u, "id:ns:test::2", "gotcha", "foo", 3, 25);
    db.addDoc(3u, "id:ns:test::3", "gotcha", "foo", noIntVal, noIntVal);
    db.addDoc(4u, "id:ns:test::4", "null", "foo", noIntVal, noIntVal);
    
    CachedSelect::SP cs;
    cs = f.testParse("test.aa < 45", "test");
    assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs);

    SelectContext ctx(*cs);
    ctx.getAttributeGuards();
    const NodeUP &sel(cs->preDocOnlySelect());
    uint32_t i;
    const uint32_t loopcnt = 30000;
    LOG(info, "Starting minibm loop, %u ierations of 4 docs each", loopcnt);
    vespalib::Timer sw;
    for (i = 0; i < loopcnt; ++i) {
        ctx._docId = 1u;
        if (sel->contains(ctx) != Result::False)
            break;
        ctx._docId = 2u;
        if (sel->contains(ctx) != Result::True)
            break;
        ctx._docId = 3u;
        if (sel->contains(ctx) != Result::Invalid)
            break;
        ctx._docId = 4u;
        if (sel->contains(ctx) != Result::Invalid)
            break;
    }
    vespalib::duration elapsed = sw.elapsed();
    EXPECT_EQ(loopcnt, i);
    LOG(info,
        "Elapsed time for %u iterations of 4 docs each: %" PRId64 " ns, %8.4f ns/doc",
        i, vespalib::count_ns(elapsed), static_cast<double>(vespalib::count_ns(elapsed)) / ( 4 * i));
    
}

}
