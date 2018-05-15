// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
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
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/enumcomparator.h>
#include <vespa/searchlib/attribute/postinglistattribute.h>
#include <vespa/searchlib/attribute/singleenumattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericenumattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericpostattribute.h>
#include <vespa/searchlib/attribute/singlenumericpostattribute.hpp>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("cachedselect_test");

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
using vespalib::string;

using namespace search::index;

using IATint32 = IntegerAttributeTemplate<int32_t>;
using IntEnumAttribute = EnumAttribute<IATint32>;
using NodeUP = Node::UP;
using SessionUP = std::unique_ptr<CachedSelect::Session>;

#if 0
extern template class SingleValueNumericPostingAttribute<IntPostingAttribute>;
#endif

typedef SingleValueNumericPostingAttribute<IntEnumAttribute> SvIntAttr;

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
                     addField("ab", DataType::T_INT));
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
    Document::UP doc(new Document(*docType, DocumentId(docId)));
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
    if (EXPECT_TRUE(sel->contains(ctx) == exp)) {
        return true;
    }
    std::ostringstream os;
    EXPECT_TRUE(sel->trace(ctx, os) == exp);
    LOG(info,
        "trace output: '%s'",
        os.str().c_str());
    return false;
}

void
checkSelect(const CachedSelect::SP &cs,
            const Document &doc,
            const Result &exp)
{
    bool expSessionContains = (cs->preDocOnlySelect() || (exp == Result::True));
    EXPECT_TRUE(checkSelect(cs->docSelect(), Context(doc), exp));
    EXPECT_EQUAL(expSessionContains, cs->createSession()->contains(doc));
}

void
checkSelect(const CachedSelect::SP &cs,
            uint32_t docId,
            const Result &exp,
            bool expSessionContains)
{
    SelectContext ctx(*cs);
    ctx._docId = docId;
    ctx.getAttributeGuards();
    EXPECT_TRUE(checkSelect((cs->preDocOnlySelect() ? cs->preDocOnlySelect() : cs->preDocSelect()), ctx, exp));
    EXPECT_EQUAL(expSessionContains, cs->createSession()->contains(ctx));
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
                                 true, false)),
          _gets(0)
    {
    }

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


class MyAttributeManager : public MockAttributeManager
{
public:
    using MockAttributeManager::addAttribute;
    void addAttribute(const vespalib::string &name) {
        if (findAttribute(name).get() != nullptr) {
            return;
        }
        AttributeVector::SP av(new MyIntAv(name));
        MockAttributeManager::addAttribute(name, av);
    }
    MyIntAv *getAsMyIntAttribute(const vespalib::string &name) const {
        return (dynamic_cast<MyIntAv *>(findAttribute(name).get()));
    }
};


class MyDB
{
public:
    typedef std::unique_ptr<MyDB> UP;

    const DocumentTypeRepo &_repo;
    MyAttributeManager &_amgr;
    typedef std::map<string, uint32_t> DocIdToLid;
    typedef std::map<uint32_t, Document::SP> LidToDocSP;
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
    _lidToDocSP[lid] = Document::SP(doc.release());
    AttributeGuard::UP guard = _amgr.getAttribute("aa");
    AttributeVector &av = *guard->get();
    if (lid >= av.getNumDocs()) {
        AttributeVector::DocId checkDocId(0u);
        ASSERT_TRUE(av.addDoc(checkDocId));
        ASSERT_EQUAL(lid, checkDocId);
    }
    IntegerAttribute &iav(static_cast<IntegerAttribute &>(av));
    AttributeVector::largeint_t laa(aa);
    EXPECT_TRUE(iav.update(lid, laa));
    av.commit();
}


const Document &
MyDB::getDoc(uint32_t lid) const
{
    LidToDocSP::const_iterator it(_lidToDocSP.find(lid));
    ASSERT_TRUE(it != _lidToDocSP.end());
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

    _db.reset(new MyDB(*_repoUP, _amgr));
}


TestFixture::~TestFixture()
{
}


CachedSelect::SP
TestFixture::testParse(const string &selection,
                       const string &docTypeName)
{
    const DocumentTypeRepo &repo(*_repoUP);

    CachedSelect::SP res(new CachedSelect);

    const DocumentType *docType = repo.getDocumentType(docTypeName);
    ASSERT_TRUE(docType != nullptr);
    Document::UP emptyDoc(new Document(*docType, DocumentId()));

    res->set(selection,
             docTypeName,
             *emptyDoc,
             repo,
             &_amgr,
             _hasFields);
    
    ASSERT_TRUE(res->docSelect());
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
        EXPECT_EQUAL(_preDocOnlySelect, (bool)select.preDocOnlySelect());
        EXPECT_EQUAL(_preDocSelect, (bool)select.preDocSelect());
        EXPECT_EQUAL(_allFalse, select.allFalse());
        EXPECT_EQUAL(_allTrue, select.allTrue());
        EXPECT_EQUAL(_allInvalid, select.allInvalid());
        EXPECT_EQUAL(_fieldNodes, select.fieldNodes());
        EXPECT_EQUAL(_attrFieldNodes, select.attrFieldNodes());
        EXPECT_EQUAL(_svAttrFieldNodes, select.svAttrFieldNodes());
    }
};

void
assertEquals(const Stats &stats, const CachedSelect &select)
{
    stats.assertEquals(select);
}


TEST_F("Test that test setup is OK", TestFixture)
{
    const DocumentTypeRepo &repo = *f._repoUP;
    const DocumentType *docType = repo.getDocumentType("test");
    ASSERT_TRUE(docType);
    EXPECT_EQUAL(10u, docType->getFieldCount());
    EXPECT_EQUAL("String", docType->getField("ia").getDataType().getName());
    EXPECT_EQUAL("String", docType->getField("ib").getDataType().getName());
    EXPECT_EQUAL("Int", docType->getField("aa").getDataType().getName());
    EXPECT_EQUAL("Int", docType->getField("ab").getDataType().getName());
}


TEST_F("Test that simple parsing works", TestFixture)
{
    f.testParse("not ((test))", "test");
    f.testParse("not ((test and (test.aa > 3999)))", "test");
    f.testParse("not ((test and (test.ab > 3999)))", "test");
    f.testParse("not ((test and (test.af > 3999)))", "test");
    f.testParse("not ((test_2 and (test_2.af > 3999)))", "test");
}


TEST_F("Test that const is flagged", TestFixture)
{
    CachedSelect::SP cs;

    cs = f.testParse("false", "test");
    EXPECT_TRUE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQUAL(0u, cs->fieldNodes());
    cs = f.testParse("true", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_TRUE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQUAL(0u, cs->fieldNodes());
    cs = f.testParse("test_2.ac > 4999", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_TRUE(cs->allInvalid());
    EXPECT_EQUAL(0u, cs->fieldNodes());
    cs = f.testParse("test.aa > 4999", "test");
    EXPECT_FALSE(cs->allFalse());
    EXPECT_FALSE(cs->allTrue());
    EXPECT_FALSE(cs->allInvalid());
    EXPECT_EQUAL(1u, cs->fieldNodes());
    EXPECT_EQUAL(1u, cs->attrFieldNodes());
    EXPECT_EQUAL(1u, cs->svAttrFieldNodes());
}


TEST_F("Test that basic select works", TestFixture)
{
    MyDB &db(*f._db);
    
    db.addDoc(1u, "doc:test:1", "hello", "null", 45, 37);
    db.addDoc(2u, "doc:test:2", "gotcha", "foo", 3, 25);
    db.addDoc(3u, "doc:test:3", "gotcha", "foo", noIntVal, noIntVal);
    db.addDoc(4u, "doc:test:4", "null", "foo", noIntVal, noIntVal);
    
    CachedSelect::SP cs;

    cs = f.testParse("test.ia == \"hello\"", "test");
    TEST_DO(assertEquals(Stats().fieldNodes(1).attrFieldNodes(0).svAttrFieldNodes(0), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::True));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::False));
    
    cs = f.testParse("test.ia.foo == \"hello\"", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));
    
    cs = f.testParse("test.ia[2] == \"hello\"", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));
    
    cs = f.testParse("test.ia{foo} == \"hello\"", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));
    
    cs = f.testParse("test.ia < \"hello\"", "test");
    TEST_DO(assertEquals(Stats().fieldNodes(1).attrFieldNodes(0).svAttrFieldNodes(0), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::True));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::True));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));

    cs = f.testParse("test.aa == 3", "test");
    TEST_DO(assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::True));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::False));
    TEST_DO(checkSelect(cs, 1u, Result::False));
    TEST_DO(checkSelect(cs, 2u, Result::True));
    TEST_DO(checkSelect(cs, 3u, Result::False));
    TEST_DO(checkSelect(cs, 4u, Result::False));

    cs = f.testParse("test.aa.foo == 3", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));

    cs = f.testParse("test.aa[2] == 3", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));

    cs = f.testParse("test.aa{4} > 3", "test");
    TEST_DO(assertEquals(Stats().allInvalid(), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));

    cs = f.testParse("test.aaa[2] == 3", "test");
    TEST_DO(assertEquals(Stats().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(0), *cs));

    cs = f.testParse("test.aaw{4} > 3", "test");
    TEST_DO(assertEquals(Stats().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(0), *cs));

    cs = f.testParse("test.aa < 45", "test");
    TEST_DO(assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs));
    TEST_DO(checkSelect(cs, db.getDoc(1u), Result::False));
    TEST_DO(checkSelect(cs, db.getDoc(2u), Result::True));
    TEST_DO(checkSelect(cs, db.getDoc(3u), Result::Invalid));
    TEST_DO(checkSelect(cs, db.getDoc(4u), Result::Invalid));
    TEST_DO(checkSelect(cs, 1u, Result::False,   false));
    TEST_DO(checkSelect(cs, 2u, Result::True,    true));
    TEST_DO(checkSelect(cs, 3u, Result::Invalid, false));
    TEST_DO(checkSelect(cs, 4u, Result::Invalid, false));

    MyIntAv *v = f._amgr.getAsMyIntAttribute("aa");
    EXPECT_TRUE(v != nullptr);
    EXPECT_EQUAL(8u, v->getGets());
}

struct PreDocSelectFixture : public TestFixture {
    PreDocSelectFixture()
        : TestFixture()
    {
        db().addDoc(1u, "doc:test:1", "foo", "null", 3, 5);
        db().addDoc(2u, "doc:test:1", "bar", "null", 3, 5);
        db().addDoc(3u, "doc:test:2", "foo", "null", 7, 5);
    }
};

TEST_F("Test that single value attribute combined with non-attribute field results in pre-document select pruner", PreDocSelectFixture)
{
    CachedSelect::SP cs = f.testParse("test.aa == 3 AND test.ia == \"foo\"", "test");
    TEST_DO(assertEquals(Stats().preDocSelect().fieldNodes(2).attrFieldNodes(1).svAttrFieldNodes(1), *cs));

    TEST_DO(checkSelect(cs, 1u, Result::Invalid, true));
    TEST_DO(checkSelect(cs, 2u, Result::Invalid, true));
    TEST_DO(checkSelect(cs, 3u, Result::False, false));
    TEST_DO(checkSelect(cs, f.db().getDoc(1u), Result::True));
    TEST_DO(checkSelect(cs, f.db().getDoc(2u), Result::False));
    TEST_DO(checkSelect(cs, f.db().getDoc(3u), Result::False));
}

TEST_F("Test that single value attribute with complex attribute field results in pre-document select pruner", PreDocSelectFixture)
{
    CachedSelect::SP cs = f.testParse("test.aa == 3 AND test.aaa[0] == 5", "test");
    TEST_DO(assertEquals(Stats().preDocSelect().fieldNodes(2).attrFieldNodes(2).svAttrFieldNodes(1), *cs));

    TEST_DO(checkSelect(cs, 1u, Result::Invalid, true));
    TEST_DO(checkSelect(cs, 2u, Result::Invalid, true));
    TEST_DO(checkSelect(cs, 3u, Result::False, false));
    TEST_DO(checkSelect(cs, f.db().getDoc(1u), Result::False));
    TEST_DO(checkSelect(cs, f.db().getDoc(2u), Result::False));
    TEST_DO(checkSelect(cs, f.db().getDoc(3u), Result::False));
}

TEST_F("Test performance when using attributes", TestFixture)
{
    MyDB &db(*f._db);
    
    db.addDoc(1u, "doc:test:1", "hello", "null", 45, 37);
    db.addDoc(2u, "doc:test:2", "gotcha", "foo", 3, 25);
    db.addDoc(3u, "doc:test:3", "gotcha", "foo", noIntVal, noIntVal);
    db.addDoc(4u, "doc:test:4", "null", "foo", noIntVal, noIntVal);
    
    CachedSelect::SP cs;
    cs = f.testParse("test.aa < 45", "test");
    TEST_DO(assertEquals(Stats().preDocOnlySelect().fieldNodes(1).attrFieldNodes(1).svAttrFieldNodes(1), *cs));

    SelectContext ctx(*cs);
    ctx.getAttributeGuards();
    const NodeUP &sel(cs->preDocOnlySelect());
    uint32_t i;
    const uint32_t loopcnt = 30000;
    LOG(info, "Starting minibm loop, %u ierations of 4 docs each", loopcnt);
    fastos::StopWatchT<fastos::ClockSystem> sw;
    sw.start();
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
    sw.stop();
    EXPECT_EQUAL(loopcnt, i);
    LOG(info,
        "Elapsed time for %u iterations of 4 docs each: %" PRId64 " ns, "
        "%8.4f ns/doc",
        i,
        sw.elapsed().ns(),
        static_cast<double>(sw.elapsed().ns()) / ( 4 * i));
    
}


}

TEST_MAIN() { TEST_RUN_ALL(); }
