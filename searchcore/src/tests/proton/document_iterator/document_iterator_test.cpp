// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/searchcore/proton/common/attrupdate.h>
#include <vespa/searchcore/proton/persistenceengine/document_iterator.h>
#include <vespa/searchcore/proton/server/commit_and_wait_document_retriever.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/test_kit.h>

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DoubleFieldValue;
using document::Field;
using document::IntFieldValue;
using document::StringFieldValue;
using search::AttributeContext;
using search::AttributeEnumGuard;
using search::AttributeGuard;
using search::AttributeVector;
using search::DocumentIdT;
using search::DocumentMetaData;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeContext;
using search::attribute::test::MockAttributeManager;
using storage::spi::Bucket;
using storage::spi::DocEntry;
using storage::spi::DocumentSelection;
using storage::spi::IncludedVersions;
using storage::spi::IterateResult;
using storage::spi::PartitionId;
using storage::spi::Selection;
using storage::spi::Timestamp;
using storage::spi::test::makeSpiBucket;

using namespace proton;
using namespace search::index;

const uint64_t largeNum = 10000000;

Bucket bucket(size_t x) {
    return makeSpiBucket(BucketId(x));
}

Selection selectAll() {
    return Selection(DocumentSelection(""));
}

Selection selectTimestampRange(uint64_t min, uint64_t max) {
    Selection sel(DocumentSelection(""));
    sel.setFromTimestamp(Timestamp(min));
    sel.setToTimestamp(Timestamp(max));
    return sel;
}

Selection selectTimestampSet(uint64_t a, uint64_t b, uint64_t c) {
    Selection sel(DocumentSelection(""));
    Selection::TimestampSubset subset;
    subset.push_back(Timestamp(a));
    subset.push_back(Timestamp(b));
    subset.push_back(Timestamp(c));
    sel.setTimestampSubset(subset);
    return sel;
}

Selection selectDocs(const std::string &docSel) {
    return Selection(DocumentSelection(docSel));
}

Selection selectDocsWithinRange(const std::string &docSel, uint64_t min, uint64_t max) {
    Selection sel((DocumentSelection(docSel)));
    sel.setFromTimestamp(Timestamp(min));
    sel.setToTimestamp(Timestamp(max));
    return sel;
}

IncludedVersions docV() {
    return storage::spi::NEWEST_DOCUMENT_ONLY;
}

IncludedVersions newestV() {
    return storage::spi::NEWEST_DOCUMENT_OR_REMOVE;
}

IncludedVersions allV() {
    return storage::spi::ALL_VERSIONS;
}

struct UnitDR : DocumentRetrieverBaseForTest {
    static DocumentIdT _docidCnt;

    document::DocumentTypeRepo repo;
    document::Document::UP     document;
    Timestamp                  timestamp;
    Bucket                     bucket;
    bool                       removed;
    DocumentIdT                docid;
    DocumentIdT                docIdLimit;

    UnitDR();
    UnitDR(document::Document::UP d, Timestamp t, Bucket b, bool r);
    UnitDR(const document::DocumentType &dt, document::Document::UP d, Timestamp t, Bucket b, bool r);
    ~UnitDR();

    const document::DocumentTypeRepo &getDocumentTypeRepo() const override {
        return repo;
    }
    void getBucketMetaData(const Bucket &b, DocumentMetaData::Vector &result) const override
    {
        if (b == bucket) {
            result.push_back(DocumentMetaData(docid, timestamp, bucket, document->getId().getGlobalId(), removed));
        }
    }
    DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const override {
        if (document->getId() == id) {
            return DocumentMetaData(docid, timestamp, bucket, document->getId().getGlobalId(), removed);
        }
        return DocumentMetaData();
    }
    document::Document::UP getDocument(DocumentIdT lid) const override {
        return Document::UP((lid == docid) ? document->clone() : 0);
    }

    uint32_t getDocIdLimit() const override {
        return docIdLimit;
    }
    void setDocIdLimit(DocumentIdT limit) {
        docIdLimit = limit;
    }

    CachedSelect::SP parseSelect(const vespalib::string &selection) const override {
        CachedSelect::SP res(new CachedSelect);
        res->set(selection, repo);
        return res;
    }

    static void reset() { _docidCnt = 2; }
};

UnitDR::UnitDR()
    : repo(), document(new Document(*DataType::DOCUMENT, DocumentId())), timestamp(0),
      bucket(), removed(false), docid(0), docIdLimit(std::numeric_limits<uint32_t>::max())
{}
UnitDR::UnitDR(document::Document::UP d, Timestamp t, Bucket b, bool r)
    : repo(), document(std::move(d)), timestamp(t), bucket(b), removed(r), docid(++_docidCnt),
      docIdLimit(std::numeric_limits<uint32_t>::max())
{}
UnitDR::UnitDR(const document::DocumentType &dt, document::Document::UP d, Timestamp t, Bucket b, bool r)
    : repo(dt), document(std::move(d)), timestamp(t), bucket(b), removed(r), docid(++_docidCnt),
      docIdLimit(std::numeric_limits<uint32_t>::max())
{}
UnitDR::~UnitDR() {}


struct VisitRecordingUnitDR : UnitDR {
    using VisitedLIDs = std::unordered_set<DocumentIdT>;
    VisitedLIDs& visited_lids;

    VisitRecordingUnitDR(VisitedLIDs& visited, document::Document::UP d,
                        Timestamp t, Bucket b, bool r)
        : UnitDR(std::move(d), t, b, r),
          visited_lids(visited)
    {
    }

    document::Document::UP getDocument(DocumentIdT lid) const override {
        if (lid == docid) {
            visited_lids.insert(lid);
        }
        return UnitDR::getDocument(lid);
    }
};

struct AttrUnitDR : public UnitDR
{
    MockAttributeManager _amgr;
    AttributeVector::SP _aa;
    AttributeVector::SP _dd;
    AttributeVector::SP _ss;

    AttrUnitDR(document::Document::UP d, Timestamp t, Bucket b, bool r)
        : UnitDR(d->getType(), document::Document::UP(d->clone()), t, b, r),
          _amgr(), _aa(), _dd(), _ss()
    {
        createAttribute(_aa, BasicType::INT32, "aa");
        createAttribute(_dd, BasicType::DOUBLE, "dd");
        createAttribute(_ss, BasicType::STRING, "ss");
    }

    AttrUnitDR(document::Document::UP d, Timestamp t, Bucket b, bool r,
               int32_t aa, double dd, const vespalib::string &ss)
        : UnitDR(d->getType(), document::Document::UP(d->clone()), t, b, r),
          _amgr(), _aa(), _dd(), _ss()
    {
        createAttribute(_aa, BasicType::INT32, "aa");
        addAttribute<IntFieldValue, int32_t>(*_aa, aa);
        createAttribute(_dd, BasicType::DOUBLE, "dd");
        addAttribute<DoubleFieldValue, double>(*_dd, dd);
        createAttribute(_ss, BasicType::STRING, "ss");
        addAttribute<StringFieldValue, vespalib::string>(*_ss, ss);
    }

    void createAttribute(AttributeVector::SP &av, BasicType basicType,
                         const vespalib::string &fieldName)
    {
        Config cfg(basicType, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        av = search::AttributeFactory::createAttribute(fieldName, cfg);
        _amgr.addAttribute(fieldName, av);
        while (docid >= av->getNumDocs()) {
            AttributeVector::DocId checkDocId(0u);
            ASSERT_TRUE(av->addDoc(checkDocId));
            av->clearDoc(docid);
        }
        av->commit();
    }

    template <class FieldValType, typename FieldValArg>
    void addAttribute(AttributeVector &av, const FieldValArg &val) {
        search::AttrUpdate::handleValue(av, docid, FieldValType(val));
        av.commit();
    }

    CachedSelect::SP parseSelect(const vespalib::string &selection) const override {
        CachedSelect::SP res(new CachedSelect);
        res->set(selection, "foo", Document(document->getType(), DocumentId()), repo, &_amgr, true);
        return res;
    }
};

DocumentIdT UnitDR::_docidCnt(2);

struct PairDR : DocumentRetrieverBaseForTest {
    IDocumentRetriever::SP first;
    IDocumentRetriever::SP second;
    PairDR(IDocumentRetriever::SP f, IDocumentRetriever::SP s)
        : first(f), second(s) {}
    const document::DocumentTypeRepo &getDocumentTypeRepo() const override {
        return first->getDocumentTypeRepo();
    }
    void getBucketMetaData(const Bucket &b, DocumentMetaData::Vector &result) const override {
        first->getBucketMetaData(b, result);
        second->getBucketMetaData(b, result);
    }
    DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const override {
        DocumentMetaData ret = first->getDocumentMetaData(id);
        return (ret.valid()) ? ret : second->getDocumentMetaData(id);
    }
    document::Document::UP getDocument(DocumentIdT lid) const override {
        Document::UP ret = first->getDocument(lid);
        return (ret.get() != 0) ? std::move(ret) : second->getDocument(lid);
    }

    CachedSelect::SP parseSelect(const vespalib::string &selection) const override {
        CachedSelect::SP res(new CachedSelect);
        res->set(selection, getDocumentTypeRepo());
        return res;
    }
};

struct Committer : public ICommitable {
    size_t _commitCount;
    size_t _commitAndWaitCount;
    Committer() : _commitCount(0), _commitAndWaitCount(0) { }
    void commit() override { _commitCount++; }
    void commitAndWait() override { _commitAndWaitCount++; }
};

size_t getSize() {
    return sizeof(DocEntry);
}

size_t getSize(const document::Document &doc) {
    vespalib::nbostream tmp;
    doc.serialize(tmp);
    return tmp.size() + getSize();
}

size_t getSize(const document::DocumentId &id) {
    return id.getSerializedSize() + getSize();
}

IDocumentRetriever::SP nil() { return IDocumentRetriever::SP(new UnitDR()); }

IDocumentRetriever::SP doc(const std::string &id, Timestamp t, Bucket b) {
    Document::UP d(new Document(*DataType::DOCUMENT, DocumentId(id)));
    return IDocumentRetriever::SP(new UnitDR(std::move(d), t, b, false));
}

IDocumentRetriever::SP rem(const std::string &id, Timestamp t, Bucket b) {
    Document::UP d(new Document(*DataType::DOCUMENT, DocumentId(id)));
    return IDocumentRetriever::SP(new UnitDR(std::move(d), t, b, true));
}

IDocumentRetriever::SP cat(IDocumentRetriever::SP first, IDocumentRetriever::SP second) {
    return IDocumentRetriever::SP(new PairDR(first, second));
}

const DocumentType &getDocType() {
    static DocumentType::UP doc_type;
    if (!doc_type.get()) {
        doc_type.reset(new DocumentType("foo", 42));
        doc_type->addField(Field("header", 43, *DataType::STRING, true));
        doc_type->addField(Field("body", 44, *DataType::STRING, false));
    }
    return *doc_type;
}

const DocumentType &getAttrDocType() {
    static DocumentType::UP doc_type;
    if (!doc_type.get()) {
        doc_type.reset(new DocumentType("foo", 42));
        doc_type->addField(Field("header", 43, *DataType::STRING, true));
        doc_type->addField(Field("body", 44, *DataType::STRING, false));
        doc_type->addField(Field("aa", 45, *DataType::INT, false));
        doc_type->addField(Field("ab", 46, *DataType::INT, false));
        doc_type->addField(Field("dd", 47, *DataType::DOUBLE, false));
        doc_type->addField(Field("ss", 48, *DataType::STRING, false));
    }
    return *doc_type;
}

IDocumentRetriever::SP doc_with_fields(const std::string &id, Timestamp t, Bucket b) {
    Document::UP d(new Document(getDocType(), DocumentId(id)));
    d->set("header", "foo");
    d->set("body", "bar");
    return IDocumentRetriever::SP(new UnitDR(getDocType(), std::move(d), t, b, false));
}

IDocumentRetriever::SP doc_with_null_fields(const std::string &id, Timestamp t, Bucket b) {
    Document::UP d(new Document(getAttrDocType(), DocumentId(id)));
    return IDocumentRetriever::SP(new AttrUnitDR(std::move(d), t, b, false));
}

IDocumentRetriever::SP doc_with_attr_fields(const vespalib::string &id,
                                            Timestamp t, Bucket b,
                                            int32_t aa, int32_t ab, int32_t attr_aa,
                                            double dd, double attr_dd,
                                            const vespalib::string &ss,
                                            const vespalib::string &attr_ss)
{
    Document::UP d(new Document(getAttrDocType(), DocumentId(id)));
    d->set("header", "foo");
    d->set("body", "bar");
    d->set("aa", aa);
    d->set("ab", ab);
    d->set("dd", dd);
    d->set("ss", ss);
    return IDocumentRetriever::SP(new AttrUnitDR(std::move(d), t, b, false,
                                                 attr_aa, attr_dd, attr_ss));
}

auto doc_rec(VisitRecordingUnitDR::VisitedLIDs& visited_lids,
             const std::string &id, Timestamp t, Bucket b)
{
    Document::UP d(new Document(getDocType(), DocumentId(id)));
    return std::make_shared<VisitRecordingUnitDR>(
            visited_lids, std::move(d), t, b, false);
}

void checkDoc(const IDocumentRetriever &dr, const std::string &id,
              size_t timestamp, size_t bucket, bool removed)
{
    DocumentMetaData dmd = dr.getDocumentMetaData(DocumentId(id));
    EXPECT_TRUE(dmd.valid());
    EXPECT_EQUAL(timestamp, dmd.timestamp);
    EXPECT_EQUAL(bucket, dmd.bucketId.getId());
    EXPECT_EQUAL(DocumentId(id).getGlobalId(), dmd.gid);
    EXPECT_EQUAL(removed, dmd.removed);
    Document::UP doc = dr.getDocument(dmd.lid);
    ASSERT_TRUE(doc.get() != 0);
    EXPECT_TRUE(DocumentId(id) == doc->getId());
}

void checkEntry(const IterateResult &res, size_t idx, const Timestamp &timestamp, int flags)
{
    ASSERT_LESS(idx, res.getEntries().size());
    DocEntry expect(timestamp, flags);
    EXPECT_EQUAL(expect, *res.getEntries()[idx]);
    EXPECT_EQUAL(getSize(), res.getEntries()[idx]->getSize());
}

void checkEntry(const IterateResult &res, size_t idx, const DocumentId &id, const Timestamp &timestamp)
{
    ASSERT_LESS(idx, res.getEntries().size());
    DocEntry expect(timestamp, storage::spi::REMOVE_ENTRY, id);
    EXPECT_EQUAL(expect, *res.getEntries()[idx]);
    EXPECT_EQUAL(getSize(id), res.getEntries()[idx]->getSize());
    EXPECT_GREATER(getSize(id), 0u);
}

void checkEntry(const IterateResult &res, size_t idx, const Document &doc, const Timestamp &timestamp)
{
    ASSERT_LESS(idx, res.getEntries().size());
    DocEntry expect(timestamp, storage::spi::NONE, Document::UP(doc.clone()));
    EXPECT_EQUAL(expect, *res.getEntries()[idx]);
    EXPECT_EQUAL(getSize(doc), res.getEntries()[idx]->getSize());
    EXPECT_GREATER(getSize(doc), 0u);
}

TEST("require that custom retrievers work as expected") {
    IDocumentRetriever::SP dr =
        cat(cat(doc("doc:foo:1", Timestamp(2), bucket(5)),
                rem("doc:foo:2", Timestamp(3), bucket(5))),
            cat(doc("doc:foo:3", Timestamp(7), bucket(6)),
                nil()));
    EXPECT_FALSE(dr->getDocumentMetaData(DocumentId("doc:foo:bogus")).valid());
    EXPECT_TRUE(dr->getDocument(1).get() == 0);
    EXPECT_TRUE(dr->getDocument(2).get() == 0);
    EXPECT_TRUE(dr->getDocument(3).get() != 0);
    TEST_DO(checkDoc(*dr, "doc:foo:1", 2, 5, false));
    TEST_DO(checkDoc(*dr, "doc:foo:2", 3, 5, true));
    TEST_DO(checkDoc(*dr, "doc:foo:3", 7, 6, false));
    DocumentMetaData::Vector b5;
    DocumentMetaData::Vector b6;
    dr->getBucketMetaData(bucket(5), b5);
    dr->getBucketMetaData(bucket(6), b6);
    ASSERT_EQUAL(2u, b5.size());
    ASSERT_EQUAL(1u, b6.size());
    EXPECT_EQUAL(5u, b5[0].timestamp + b5[1].timestamp);
    EXPECT_EQUAL(7u, b6[0].timestamp);
}

TEST("require that an empty list of retrievers can be iterated") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    IterateResult res = itr.iterate(largeNum);
    EXPECT_EQUAL(0u, res.getEntries().size());
    EXPECT_TRUE(res.isCompleted());
}

TEST("require that a list of empty retrievers can be iterated") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(nil());
    itr.add(nil());
    itr.add(nil());
    IterateResult res = itr.iterate(largeNum);
    EXPECT_EQUAL(0u, res.getEntries().size());
    EXPECT_TRUE(res.isCompleted());
}

TEST("require that normal documents can be iterated") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("doc:foo:2", Timestamp(3), bucket(5)),
                doc("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:1")), Timestamp(2)));
    TEST_DO(checkEntry(res, 1, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(3)));
    TEST_DO(checkEntry(res, 2, Document(*DataType::DOCUMENT, DocumentId("doc:foo:3")), Timestamp(4)));
}

void verifyIterateIgnoringStopSignal(DocumentIterator & itr) {
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(0u, res.getEntries().size());
}

TEST("require that iterator stops at the end, and does not auto rewind") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    TEST_DO(verifyIterateIgnoringStopSignal(itr));
}

TEST("require that iterator ignoring maxbytes stops at the end, and does not auto rewind") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, true);
    TEST_DO(verifyIterateIgnoringStopSignal(itr));
}

void verifyReadConsistency(DocumentIterator & itr, Committer & committer) {
    IDocumentRetriever::SP retriever = doc("doc:foo:1", Timestamp(2), bucket(5));
    IDocumentRetriever::SP commitAndWaitRetriever(new CommitAndWaitDocumentRetriever(retriever, committer));
    itr.add(commitAndWaitRetriever);

    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:1")), Timestamp(2)));
    EXPECT_EQUAL(0u, committer._commitCount);
}

void verifyStrongReadConsistency(DocumentIterator & itr) {
    Committer committer;
    TEST_DO(verifyReadConsistency(itr, committer));
    EXPECT_EQUAL(1u, committer._commitAndWaitCount);
}

TEST("require that default readconsistency does commit") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    TEST_DO(verifyStrongReadConsistency(itr));
}

TEST("require that readconsistency::strong does commit") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false, storage::spi::ReadConsistency::STRONG);
    TEST_DO(verifyStrongReadConsistency(itr));
}

TEST("require that docid limit is honoured") {
    IDocumentRetriever::SP retriever = doc("doc:foo:1", Timestamp(2), bucket(5));
    UnitDR & udr = dynamic_cast<UnitDR &>(*retriever);
    udr.docid = 7;
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(retriever);
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:1")), Timestamp(2)));

    udr.setDocIdLimit(7);
    DocumentIterator limited(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    limited.add(retriever);
    res = limited.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(0u, res.getEntries().size());
}

TEST("require that remove entries can be iterated") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(rem("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("doc:foo:2", Timestamp(3), bucket(5)),
                rem("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, DocumentId("doc:foo:1"), Timestamp(2)));
    TEST_DO(checkEntry(res, 1, DocumentId("doc:foo:2"), Timestamp(3)));
    TEST_DO(checkEntry(res, 2, DocumentId("doc:foo:3"), Timestamp(4)));
}

TEST("require that remove entries can be ignored") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), docV(), -1, false);
    itr.add(rem("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("doc:foo:2", Timestamp(3), bucket(5)),
                rem("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(3)));
}

TEST("require that iterating all versions returns both documents and removes") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), allV(), -1, false);
    itr.add(rem("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("doc:foo:2", Timestamp(3), bucket(5)),
                rem("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, DocumentId("doc:foo:1"), Timestamp(2)));
    TEST_DO(checkEntry(res, 1, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(3)));
    TEST_DO(checkEntry(res, 2, DocumentId("doc:foo:3"), Timestamp(4)));
}

TEST("require that using an empty field set returns meta-data only") {
    DocumentIterator itr(bucket(5), document::NoFields(), selectAll(), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("doc:foo:2", Timestamp(3), bucket(5)),
                rem("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Timestamp(2), storage::spi::NONE));
    TEST_DO(checkEntry(res, 1, Timestamp(3), storage::spi::NONE));
    TEST_DO(checkEntry(res, 2, Timestamp(4), storage::spi::REMOVE_ENTRY));
}

TEST("require that entries in other buckets are skipped") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(rem("doc:foo:1", Timestamp(2), bucket(6)));
    itr.add(cat(doc("doc:foo:2", Timestamp(3), bucket(5)),
                doc("doc:foo:3", Timestamp(4), bucket(6))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(3)));
}

TEST("require that maxBytes splits iteration results") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("doc:foo:2", Timestamp(3), bucket(5)),
                doc("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(getSize(Document(*DataType::DOCUMENT, DocumentId("doc:foo:1"))) +
                                     getSize(DocumentId("doc:foo:2")));
    EXPECT_TRUE(!res1.isCompleted());
    EXPECT_EQUAL(2u, res1.getEntries().size());
    TEST_DO(checkEntry(res1, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:1")), Timestamp(2)));
    TEST_DO(checkEntry(res1, 1, DocumentId("doc:foo:2"), Timestamp(3)));

    IterateResult res2 = itr.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    TEST_DO(checkEntry(res2, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:3")), Timestamp(4)));

    IterateResult res3 = itr.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQUAL(0u, res3.getEntries().size());
}

TEST("require that maxBytes splits iteration results for meta-data only iteration") {
    DocumentIterator itr(bucket(5), document::NoFields(), selectAll(), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("doc:foo:2", Timestamp(3), bucket(5)),
                doc("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(getSize() + getSize());
    EXPECT_TRUE(!res1.isCompleted());
    EXPECT_EQUAL(2u, res1.getEntries().size());
    TEST_DO(checkEntry(res1, 0, Timestamp(2), storage::spi::NONE));
    TEST_DO(checkEntry(res1, 1, Timestamp(3), storage::spi::REMOVE_ENTRY));

    IterateResult res2 = itr.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    TEST_DO(checkEntry(res2, 0, Timestamp(4), storage::spi::NONE));

    IterateResult res3 = itr.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQUAL(0u, res3.getEntries().size());
}

TEST("require that at least one document is returned by visit") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectAll(), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("doc:foo:2", Timestamp(3), bucket(5)),
                doc("doc:foo:3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(0);
    EXPECT_TRUE(1u <= res1.getEntries().size());
    TEST_DO(checkEntry(res1, 0, Document(*DataType::DOCUMENT,DocumentId("doc:foo:1")), Timestamp(2)));
}

TEST("require that documents outside the timestamp limits are ignored") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectTimestampRange(100, 200), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(99),  bucket(5)));
    itr.add(doc("doc:foo:2", Timestamp(100), bucket(5)));
    itr.add(doc("doc:foo:3", Timestamp(200), bucket(5)));
    itr.add(doc("doc:foo:4", Timestamp(201), bucket(5)));
    itr.add(rem("doc:foo:5", Timestamp(99),  bucket(5)));
    itr.add(rem("doc:foo:6", Timestamp(100), bucket(5)));
    itr.add(rem("doc:foo:7", Timestamp(200), bucket(5)));
    itr.add(rem("doc:foo:8", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(4u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(100)));
    TEST_DO(checkEntry(res, 1, Document(*DataType::DOCUMENT, DocumentId("doc:foo:3")), Timestamp(200)));
    TEST_DO(checkEntry(res, 2, DocumentId("doc:foo:6"), Timestamp(100)));
    TEST_DO(checkEntry(res, 3, DocumentId("doc:foo:7"), Timestamp(200)));
}

TEST("require that timestamp subset returns the appropriate documents") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectTimestampSet(200, 350, 400), newestV(), -1, false);
    itr.add(doc("doc:foo:1", Timestamp(500),  bucket(5)));
    itr.add(doc("doc:foo:2", Timestamp(400), bucket(5)));
    itr.add(doc("doc:foo:3", Timestamp(300), bucket(5)));
    itr.add(doc("doc:foo:4", Timestamp(200), bucket(5)));
    itr.add(rem("doc:foo:5", Timestamp(250),  bucket(5)));
    itr.add(rem("doc:foo:6", Timestamp(350), bucket(5)));
    itr.add(rem("doc:foo:7", Timestamp(450), bucket(5)));
    itr.add(rem("doc:foo:8", Timestamp(550), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:2")), Timestamp(400)));
    TEST_DO(checkEntry(res, 1, Document(*DataType::DOCUMENT, DocumentId("doc:foo:4")), Timestamp(200)));
    TEST_DO(checkEntry(res, 2, DocumentId("doc:foo:6"), Timestamp(350)));
}

TEST("require that document selection will filter results") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectDocs("id=\"doc:foo:xxx*\""), newestV(), -1, false);
    itr.add(doc("doc:foo:xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("doc:foo:yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("doc:foo:xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("doc:foo:yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("doc:foo:xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("doc:foo:yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("doc:foo:xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("doc:foo:yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(4u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:xxx1")), Timestamp(99)));
    TEST_DO(checkEntry(res, 1, Document(*DataType::DOCUMENT, DocumentId("doc:foo:xxx2")), Timestamp(200)));
    TEST_DO(checkEntry(res, 2, DocumentId("doc:foo:xxx3"), Timestamp(99)));
    TEST_DO(checkEntry(res, 3, DocumentId("doc:foo:xxx4"), Timestamp(200)));
}

TEST("require that document selection handles 'field == null'") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectDocs("foo.aa == null"), newestV(), -1, false);
    itr.add(doc_with_null_fields("doc:foo:xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc_with_null_fields("doc:foo:xxx2", Timestamp(100),  bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    ASSERT_EQUAL(2u, res.getEntries().size());
    Document expected1(getAttrDocType(), DocumentId("doc:foo:xxx1"));
    TEST_DO(checkEntry(res, 0, expected1, Timestamp(99)));
    Document expected2(getAttrDocType(), DocumentId("doc:foo:xxx2"));
    TEST_DO(checkEntry(res, 1, expected2, Timestamp(100)));
}

TEST("require that invalid document selection returns no documents") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectDocs("=="), newestV(), -1, false);
    itr.add(doc("doc:foo:xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("doc:foo:yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("doc:foo:xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("doc:foo:yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("doc:foo:xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("doc:foo:yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("doc:foo:xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("doc:foo:yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(0u, res.getEntries().size());
}

TEST("require that document selection and timestamp range works together") {
    DocumentIterator itr(bucket(5), document::AllFields(), selectDocsWithinRange("id=\"doc:foo:xxx*\"", 100, 200), newestV(), -1, false);
    itr.add(doc("doc:foo:xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("doc:foo:yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("doc:foo:xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("doc:foo:yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("doc:foo:xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("doc:foo:yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("doc:foo:xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("doc:foo:yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(2u, res.getEntries().size());
    TEST_DO(checkEntry(res, 0, Document(*DataType::DOCUMENT, DocumentId("doc:foo:xxx2")), Timestamp(200)));
    TEST_DO(checkEntry(res, 1, DocumentId("doc:foo:xxx4"), Timestamp(200)));
}

TEST("require that fieldset limits fields returned") {
    DocumentIterator itr(bucket(5), document::HeaderFields(), selectAll(), newestV(), -1, false);
    itr.add(doc_with_fields("doc:foo:xxx1", Timestamp(1),  bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(1u, res.getEntries().size());
    Document expected(getDocType(), DocumentId("doc:foo:xxx1"));
    expected.set("header", "foo");
    TEST_DO(checkEntry(res, 0, expected, Timestamp(1)));
}

namespace {
template <typename Container, typename T>
bool contains(const Container& c, const T& value) {
    return c.find(value) != c.end();
}
}

TEST("require that userdoc-constrained selections pre-filter on GIDs") {
    DocumentIterator itr(bucket(5), document::AllFields(),
                         selectDocs("id.user=1234"), newestV(), -1, false);
    VisitRecordingUnitDR::VisitedLIDs visited_lids;
    // Even though GID filtering is probabilistic when it comes to filtering
    // user IDs that cover the 64-bit range, it's fully deterministic when the
    // user IDs are all 32 bits or less, which is the case for the below IDs.
    auto wanted_dr_1   = doc_rec(visited_lids, "id::foo:n=1234:a",
                                 Timestamp(99), bucket(5));
    auto filtered_dr_1 = doc_rec(visited_lids, "id::foo:n=4321:b",
                                 Timestamp(200), bucket(5));
    auto filtered_dr_2 = doc_rec(visited_lids, "id::foo:n=5678:c",
                                 Timestamp(201), bucket(5));
    auto wanted_dr_2   = doc_rec(visited_lids, "id::foo:n=1234:d",
                                 Timestamp(300), bucket(5));
    auto wanted_dr_3   = doc_rec(visited_lids, "id::foo:n=1234:e",
                                 Timestamp(301), bucket(5));
    itr.add(wanted_dr_1);
    itr.add(filtered_dr_1);
    itr.add(cat(filtered_dr_2, wanted_dr_2));
    itr.add(wanted_dr_3);
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(3u, visited_lids.size());
    EXPECT_TRUE(contains(visited_lids, wanted_dr_1->docid));
    EXPECT_TRUE(contains(visited_lids, wanted_dr_2->docid));
    EXPECT_TRUE(contains(visited_lids, wanted_dr_3->docid));
}

TEST("require that attributes are used")
{
    UnitDR::reset();
    DocumentIterator itr(bucket(5), document::AllFields(), selectDocs("foo.aa == 45"), docV(), -1, false);
    itr.add(doc_with_attr_fields("doc:foo:xx1", Timestamp(1), bucket(5),
                                 27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr.add(doc_with_attr_fields("doc:foo:xx2", Timestamp(2), bucket(5),
                                 27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr.add(doc_with_attr_fields("doc:foo:xx3", Timestamp(3), bucket(5),
                                 45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr.add(doc_with_attr_fields("doc:foo:xx4", Timestamp(4), bucket(5),
                                 45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQUAL(2u, res.getEntries().size());
    Document expected1(getAttrDocType(), DocumentId("doc:foo:xx2"));
    expected1.set("header", "foo");
    expected1.set("body", "bar");
    expected1.set("aa", 27);
    expected1.set("ab", 28);
    expected1.set("dd", 2.7);
    expected1.set("ss", "x27");
    Document expected2(getAttrDocType(), DocumentId("doc:foo:xx4"));
    expected2.set("header", "foo");
    expected2.set("body", "bar");
    expected2.set("aa", 45);
    expected2.set("ab", 46);
    expected2.set("dd", 4.5);
    expected2.set("ss", "x45");
    TEST_DO(checkEntry(res, 0, expected1, Timestamp(2)));
    TEST_DO(checkEntry(res, 1, expected2, Timestamp(4)));

    DocumentIterator itr2(bucket(5), document::AllFields(), selectDocs("foo.dd == 4.5"), docV(), -1, false);
    itr2.add(doc_with_attr_fields("doc:foo:xx5", Timestamp(5), bucket(5),
                                  27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr2.add(doc_with_attr_fields("doc:foo:xx6", Timestamp(6), bucket(5),
                                  27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr2.add(doc_with_attr_fields("doc:foo:xx7", Timestamp(7), bucket(5),
                                  45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr2.add(doc_with_attr_fields("doc:foo:xx8", Timestamp(8), bucket(5),
                                  45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res2 = itr2.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    EXPECT_EQUAL(2u, res2.getEntries().size());
    Document expected3(getAttrDocType(), DocumentId("doc:foo:xx6"));
    expected3.set("header", "foo");
    expected3.set("body", "bar");
    expected3.set("aa", 27);
    expected3.set("ab", 28);
    expected3.set("dd", 2.7);
    expected3.set("ss", "x27");
    Document expected4(getAttrDocType(), DocumentId("doc:foo:xx8"));
    expected4.set("header", "foo");
    expected4.set("body", "bar");
    expected4.set("aa", 45);
    expected4.set("ab", 46);
    expected4.set("dd", 4.5);
    expected4.set("ss", "x45");
    TEST_DO(checkEntry(res2, 0, expected3, Timestamp(6)));
    TEST_DO(checkEntry(res2, 1, expected4, Timestamp(8)));

    DocumentIterator itr3(bucket(5), document::AllFields(), selectDocs("foo.ss == \"x45\""), docV(), -1, false);
    itr3.add(doc_with_attr_fields("doc:foo:xx9", Timestamp(9), bucket(5),
                                  27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr3.add(doc_with_attr_fields("doc:foo:xx10", Timestamp(10), bucket(5),
                                  27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr3.add(doc_with_attr_fields("doc:foo:xx11", Timestamp(11), bucket(5),
                                  45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr3.add(doc_with_attr_fields("doc:foo:xx12", Timestamp(12), bucket(5),
                                  45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res3 = itr3.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQUAL(2u, res3.getEntries().size());
    Document expected5(getAttrDocType(), DocumentId("doc:foo:xx10"));
    expected5.set("header", "foo");
    expected5.set("body", "bar");
    expected5.set("aa", 27);
    expected5.set("ab", 28);
    expected5.set("dd", 2.7);
    expected5.set("ss", "x27");
    Document expected6(getAttrDocType(), DocumentId("doc:foo:xx12"));
    expected6.set("header", "foo");
    expected6.set("body", "bar");
    expected6.set("aa", 45);
    expected6.set("ab", 46);
    expected6.set("dd", 4.5);
    expected6.set("ss", "x45");
    TEST_DO(checkEntry(res3, 0, expected5, Timestamp(10)));
    TEST_DO(checkEntry(res3, 1, expected6, Timestamp(12)));
} 

TEST_MAIN() { TEST_RUN_ALL(); }

