// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/searchcore/proton/persistenceengine/document_iterator.h>
#include <vespa/searchcore/proton/persistenceengine/commit_and_wait_document_retriever.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <unordered_set>

#include <vespa/log/log.h>
LOG_SETUP("document_iterator_test");

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DoubleFieldValue;
using document::Field;
using document::GlobalId;
using document::IntFieldValue;
using document::StringFieldValue;
using search::AttributeContext;
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
using storage::spi::Selection;
using storage::spi::Timestamp;
using storage::spi::DocumentMetaEnum;
using storage::spi::test::makeSpiBucket;
using storage::spi::test::equal;

using namespace proton;

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
    ~UnitDR() override;

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
    document::Document::UP getFullDocument(DocumentIdT lid) const override {
        return Document::UP((lid == docid) ? document->clone() : nullptr);
    }

    uint32_t getDocIdLimit() const override {
        return docIdLimit;
    }
    void setDocIdLimit(DocumentIdT limit) {
        docIdLimit = limit;
    }

    CachedSelect::SP parseSelect(const std::string &selection) const override {
        auto res = std::make_shared<CachedSelect>();
        res->set(selection, repo);
        return res;
    }

    static void reset() { _docidCnt = 2; }
};

Document::UP make_doc(DocumentId docid) {
    return Document::make_without_repo(*DataType::DOCUMENT, docid);
}

UnitDR::UnitDR()
    : repo(), document(make_doc(DocumentId())), timestamp(0),
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
UnitDR::~UnitDR() = default;

struct VisitRecordingUnitDR : UnitDR {
    using VisitedLIDs = std::unordered_set<DocumentIdT>;
    VisitedLIDs& visited_lids;

    VisitRecordingUnitDR(VisitedLIDs& visited, document::Document::UP d,
                        Timestamp t, Bucket b, bool r)
        : UnitDR(std::move(d), t, b, r),
          visited_lids(visited)
    {
    }

    document::Document::UP getFullDocument(DocumentIdT lid) const override {
        if (lid == docid) {
            visited_lids.insert(lid);
        }
        return UnitDR::getFullDocument(lid);
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
               int32_t aa, double dd, const std::string &ss)
        : UnitDR(d->getType(), document::Document::UP(d->clone()), t, b, r),
          _amgr(), _aa(), _dd(), _ss()
    {
        createAttribute(_aa, BasicType::INT32, "aa");
        addAttribute<IntFieldValue, int32_t>(*_aa, aa);
        createAttribute(_dd, BasicType::DOUBLE, "dd");
        addAttribute<DoubleFieldValue, double>(*_dd, dd);
        createAttribute(_ss, BasicType::STRING, "ss");
        addAttribute<StringFieldValue, std::string>(*_ss, ss);
    }

    void createAttribute(AttributeVector::SP &av, BasicType basicType,
                         const std::string &fieldName)
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
        search::AttributeUpdater::handleValue(av, docid, FieldValType(val));
        av.commit();
    }

    CachedSelect::SP parseSelect(const std::string &selection) const override {
        auto res = std::make_shared<CachedSelect>();
        res->set(selection, "foo", Document(repo, document->getType(), DocumentId()), repo, &_amgr, true);
        return res;
    }
};

DocumentIdT UnitDR::_docidCnt(2);

struct PairDR : DocumentRetrieverBaseForTest {
    IDocumentRetriever::SP first;
    IDocumentRetriever::SP second;
    PairDR(IDocumentRetriever::SP f, IDocumentRetriever::SP s)
        : first(std::move(f)), second(std::move(s)) {}
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
    document::Document::UP getFullDocument(DocumentIdT lid) const override {
        Document::UP ret = first->getFullDocument(lid);
        return ret ? std::move(ret) : second->getFullDocument(lid);
    }

    CachedSelect::SP parseSelect(const std::string &selection) const override {
        auto res = std::make_shared<CachedSelect>();
        res->set(selection, getDocumentTypeRepo());
        return res;
    }
};

size_t getSize(const document::Document &doc) {
    vespalib::nbostream tmp;
    doc.serialize(tmp);
    return tmp.size();
}

size_t getSize(const document::DocumentId &id) {
    return id.getSerializedSize();
}

IDocumentRetriever::SP nil() { return std::make_unique<UnitDR>(); }

IDocumentRetriever::SP
doc(const DocumentId &id, Timestamp t, Bucket b) {
    return std::make_shared<UnitDR>(make_doc(id), t, b, false);
}

IDocumentRetriever::SP
doc(const std::string &id, Timestamp t, Bucket b) {
    return doc(DocumentId(id), t, b);
}

IDocumentRetriever::SP
rem(const DocumentId &id, Timestamp t, Bucket b) {
    return std::make_shared<UnitDR>(make_doc(id), t, b, true);
}

IDocumentRetriever::SP
rem(const std::string &id, Timestamp t, Bucket b) {
    return rem(DocumentId(id), t, b);
}

IDocumentRetriever::SP cat(IDocumentRetriever::SP first, IDocumentRetriever::SP second) {
    return std::make_unique<PairDR>(std::move(first), std::move(second));
}

const DocumentType &getDocType() {
    static DocumentType::UP doc_type;
    if (!doc_type) {
        doc_type = std::make_unique<DocumentType>("foo", 42);
        doc_type->addField(Field("header", 43, *DataType::STRING));
        doc_type->addField(Field("body", 44, *DataType::STRING));
    }
    return *doc_type;
}

const DocumentType &getAttrDocType() {
    static DocumentType::UP doc_type;
    if (!doc_type) {
        doc_type = std::make_unique<DocumentType>("foo", 42);
        doc_type->addField(Field("header", 43, *DataType::STRING));
        doc_type->addField(Field("body", 44, *DataType::STRING));
        doc_type->addField(Field("aa", 45, *DataType::INT));
        doc_type->addField(Field("ab", 46, *DataType::INT));
        doc_type->addField(Field("dd", 47, *DataType::DOUBLE));
        doc_type->addField(Field("ss", 48, *DataType::STRING));
    }
    return *doc_type;
}

IDocumentRetriever::SP doc_with_fields(const std::string &id, Timestamp t, Bucket b) {
    auto d = Document::make_without_repo(getDocType(), DocumentId(id));
    d->setValue("header", StringFieldValue::make("foo"));
    d->setValue("body", StringFieldValue::make("bar"));
    return std::make_shared<UnitDR>(getDocType(), std::move(d), t, b, false);
}

IDocumentRetriever::SP doc_with_null_fields(const std::string &id, Timestamp t, Bucket b) {
    return std::make_unique<AttrUnitDR>(Document::make_without_repo(getAttrDocType(), DocumentId(id)), t, b, false);
}

IDocumentRetriever::SP doc_with_attr_fields(const std::string &id,
                                            Timestamp t, Bucket b,
                                            int32_t aa, int32_t ab, int32_t attr_aa,
                                            double dd, double attr_dd,
                                            const std::string &ss,
                                            const std::string &attr_ss)
{
    auto d = Document::make_without_repo(getAttrDocType(), DocumentId(id));
    d->setValue("header", StringFieldValue::make("foo"));
    d->setValue("body", StringFieldValue::make("bar"));
    d->setValue("aa", IntFieldValue::make(aa));
    d->setValue("ab", IntFieldValue::make(ab));
    d->setValue("dd", DoubleFieldValue::make(dd));
    d->setValue("ss", StringFieldValue::make(ss));
    return std::make_shared<AttrUnitDR>(std::move(d), t, b, false, attr_aa, attr_dd, attr_ss);
}

auto doc_rec(VisitRecordingUnitDR::VisitedLIDs& visited_lids, const std::string &id, Timestamp t, Bucket b)
{
    return std::make_shared<VisitRecordingUnitDR>(visited_lids, Document::make_without_repo(getAttrDocType(), DocumentId(id)), t, b, false);
}

void checkDoc(const IDocumentRetriever &dr, const std::string &id,
              size_t timestamp, size_t bucket, bool removed)
{
    SCOPED_TRACE(id);
    DocumentId documentId(id);
    DocumentMetaData dmd = dr.getDocumentMetaData(documentId);
    EXPECT_TRUE(dmd.valid());
    EXPECT_EQ(timestamp, dmd.timestamp);
    EXPECT_EQ(bucket, dmd.bucketId.getId());
    EXPECT_EQ(DocumentId(id).getGlobalId(), dmd.gid);
    EXPECT_EQ(removed, dmd.removed);
    Document::UP doc = dr.getDocument(dmd.lid, documentId);
    ASSERT_TRUE(doc);
    EXPECT_TRUE(DocumentId(id) == doc->getId());
}

void checkEntry(const IterateResult &res, size_t idx, const Timestamp &timestamp, DocumentMetaEnum flags)
{
    SCOPED_TRACE("idx=" + std::to_string(idx));
    ASSERT_LT(idx, res.getEntries().size());
    auto expect = DocEntry::create(timestamp, flags);
    EXPECT_TRUE(equal(*expect, *res.getEntries()[idx]));
    EXPECT_EQ(sizeof(DocEntry), res.getEntries()[idx]->getSize());
}

void checkEntry(const IterateResult &res, size_t idx, const Timestamp &timestamp, DocumentMetaEnum flags,
                const GlobalId &gid, std::string_view doc_type_name)
{
    SCOPED_TRACE("idx=" + std::to_string(idx));
    ASSERT_LT(idx, res.getEntries().size());
    auto expect = DocEntry::create(timestamp, flags, doc_type_name, gid);
    EXPECT_TRUE(equal(*expect, *res.getEntries()[idx]));
    EXPECT_EQ(sizeof(DocEntry) + sizeof(GlobalId) + doc_type_name.size(), res.getEntries()[idx]->getSize());
}

void checkEntry(const IterateResult &res, size_t idx, const DocumentId &id, const Timestamp &timestamp)
{
    SCOPED_TRACE("idx=" + std::to_string(idx));
    ASSERT_LT(idx, res.getEntries().size());
    auto expect = DocEntry::create(timestamp, DocumentMetaEnum::REMOVE_ENTRY, id);
    EXPECT_TRUE(equal(*expect, *res.getEntries()[idx]));
    EXPECT_EQ(getSize(id), res.getEntries()[idx]->getSize());
    EXPECT_GT(getSize(id), 0u);
}

void checkEntry(const IterateResult &res, size_t idx, const Document &doc, const Timestamp &timestamp)
{
    SCOPED_TRACE("idx=" + std::to_string(idx));
    ASSERT_LT(idx, res.getEntries().size());
    auto expect = DocEntry::create(timestamp, Document::UP(doc.clone()));
    EXPECT_TRUE(equal(*expect, *res.getEntries()[idx]));
    EXPECT_EQ(getSize(doc), res.getEntries()[idx]->getSize());
    EXPECT_GT(getSize(doc), 0u);
}

GlobalId gid_of(std::string_view id_str) {
    return DocumentId(id_str).getGlobalId();
}

TEST(DocumentIteratorTest, require_that_custom_retrievers_work_as_expected)
{
    DocumentId id1("id:ns:document::1");
    DocumentId id2("id:ns:document::2");
    DocumentId id3("id:ns:document::3");
    IDocumentRetriever::SP dr =
        cat(cat(doc(id1, Timestamp(2), bucket(5)),
                rem(id2, Timestamp(3), bucket(5))),
            cat(doc(id3, Timestamp(7), bucket(6)),
                nil()));
    EXPECT_FALSE(dr->getDocumentMetaData(DocumentId("id:ns:document::bogus")).valid());
    EXPECT_FALSE(dr->getDocument(1, id1));
    EXPECT_FALSE(dr->getDocument(2, id2));
    EXPECT_TRUE(dr->getDocument(3, id3));
    checkDoc(*dr, "id:ns:document::1", 2, 5, false);
    checkDoc(*dr, "id:ns:document::2", 3, 5, true);
    checkDoc(*dr, "id:ns:document::3", 7, 6, false);
    DocumentMetaData::Vector b5;
    DocumentMetaData::Vector b6;
    dr->getBucketMetaData(bucket(5), b5);
    dr->getBucketMetaData(bucket(6), b6);
    ASSERT_EQ(2u, b5.size());
    ASSERT_EQ(1u, b6.size());
    EXPECT_EQ(5u, b5[0].timestamp + b5[1].timestamp);
    EXPECT_EQ(7u, b6[0].timestamp);
}

TEST(DocumentIteratorTest, require_that_an_empty_list_of_retrievers_can_be_iterated)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    IterateResult res = itr.iterate(largeNum);
    EXPECT_EQ(0u, res.getEntries().size());
    EXPECT_TRUE(res.isCompleted());
}

TEST(DocumentIteratorTest, require_that_a_list_of_empty_retrievers_can_be_iterated)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(nil());
    itr.add(nil());
    itr.add(nil());
    IterateResult res = itr.iterate(largeNum);
    EXPECT_EQ(0u, res.getEntries().size());
    EXPECT_TRUE(res.isCompleted());
}

TEST(DocumentIteratorTest, require_that_normal_documents_can_be_iterated)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("id:ns:document::2", Timestamp(3), bucket(5)),
                doc("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(3u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::1")), Timestamp(2));
    checkEntry(res, 1, *make_doc(DocumentId("id:ns:document::2")), Timestamp(3));
    checkEntry(res, 2, *make_doc(DocumentId("id:ns:document::3")), Timestamp(4));
}

void verifyIterateIgnoringStopSignal(DocumentIterator & itr) {
    itr.add(doc("id:ns:document::1", Timestamp(2), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(0u, res.getEntries().size());
}

TEST(DocumentIteratorTest, require_that_iterator_stops_at_the_end_and_does_not_auto_rewind)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    verifyIterateIgnoringStopSignal(itr);
}

TEST(DocumentIteratorTest, require_that_iterator_ignoring_maxbytes_stops_at_the_end_and_does_not_auto_rewind) {
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, true);
    verifyIterateIgnoringStopSignal(itr);
}

void verifyReadConsistency(DocumentIterator & itr, ILidCommitState & lidCommitState) {
    IDocumentRetriever::SP retriever = doc("id:ns:document::1", Timestamp(2), bucket(5));
    auto commitAndWaitRetriever = std::make_shared<CommitAndWaitDocumentRetriever>(retriever, lidCommitState);
    itr.add(commitAndWaitRetriever);

    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::1")), Timestamp(2));
}

class ILidCommitStateProxy : public ILidCommitState {
public:
    explicit ILidCommitStateProxy(ILidCommitState & lidState)
        : _waitCompleteCount(0),
          _lidState(lidState)
    {}
private:
    State waitState(State state, uint32_t lid) const override {
        assert(state == State::COMPLETED);
        _lidState.waitComplete(lid);
        _waitCompleteCount++;
        return state;
    }

    State waitState(State state, const LidList &lids) const override {
        assert(state == State::COMPLETED);
        _lidState.waitComplete(lids);
        _waitCompleteCount++;
        return state;
    }

public:
    mutable size_t _waitCompleteCount;
private:
    ILidCommitState & _lidState;
};

void verifyStrongReadConsistency(DocumentIterator & itr) {
    PendingLidTracker lidTracker;

    ILidCommitStateProxy lidCommitState(lidTracker);
    verifyReadConsistency(itr, lidCommitState);
    EXPECT_EQ(1u, lidCommitState._waitCompleteCount);
}

TEST(DocumentIteratorTest, require_that_default_readconsistency_does_commit)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    verifyStrongReadConsistency(itr);
}

TEST(DocumentIteratorTest, require_that_readconsistency_strong_does_commit)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false, storage::spi::ReadConsistency::STRONG);
    verifyStrongReadConsistency(itr);
}

TEST(DocumentIteratorTest, require_that_docid_limit_is_honoured)
{
    IDocumentRetriever::SP retriever = doc("id:ns:document::1", Timestamp(2), bucket(5));
    auto & udr = dynamic_cast<UnitDR &>(*retriever);
    udr.docid = 7;
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(retriever);
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::1")), Timestamp(2));

    udr.setDocIdLimit(7);
    DocumentIterator limited(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    limited.add(retriever);
    res = limited.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(0u, res.getEntries().size());
}

TEST(DocumentIteratorTest, require_that_remove_entries_can_be_iterated)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(rem("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("id:ns:document::2", Timestamp(3), bucket(5)),
                rem("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(3u, res.getEntries().size());
    checkEntry(res, 0, DocumentId("id:ns:document::1"), Timestamp(2));
    checkEntry(res, 1, DocumentId("id:ns:document::2"), Timestamp(3));
    checkEntry(res, 2, DocumentId("id:ns:document::3"), Timestamp(4));
}

TEST(DocumentIteratorTest, require_that_remove_entries_can_be_ignored)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), docV(), -1, false);
    itr.add(rem("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("id:ns:document::2", Timestamp(3), bucket(5)),
                rem("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::2")), Timestamp(3));
}

TEST(DocumentIteratorTest, require_that_iterating_all_versions_returns_both_documents_and_removes)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), allV(), -1, false);
    itr.add(rem("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(doc("id:ns:document::2", Timestamp(3), bucket(5)),
                rem("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(3u, res.getEntries().size());
    checkEntry(res, 0, DocumentId("id:ns:document::1"), Timestamp(2));
    checkEntry(res, 1, *make_doc(DocumentId("id:ns:document::2")), Timestamp(3));
    checkEntry(res, 2, DocumentId("id:ns:document::3"), Timestamp(4));
}

TEST(DocumentIteratorTest, require_that_using_an_empty_field_set_returns_meta_data_only)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::NoFields>(), selectAll(), newestV(), -1, false);
    itr.add(DocTypeName("foo"), doc_with_fields("id:ns:foo::1", Timestamp(2), bucket(5)));
    itr.add(DocTypeName("document"), cat(doc("id:ns:document::2", Timestamp(3), bucket(5)),
                                         rem("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(3u, res.getEntries().size());
    checkEntry(res, 0, Timestamp(2), DocumentMetaEnum::NONE, gid_of("id:ns:foo::1"), "foo");
    checkEntry(res, 1, Timestamp(3), DocumentMetaEnum::NONE, gid_of("id:ns:document::2"), "document");
    checkEntry(res, 2, Timestamp(4), DocumentMetaEnum::REMOVE_ENTRY, gid_of("id:ns:document::3"), "document");
}

TEST(DocumentIteratorTest, require_that_entries_in_other_buckets_are_skipped)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(rem("id:ns:document::1", Timestamp(2), bucket(6)));
    itr.add(cat(doc("id:ns:document::2", Timestamp(3), bucket(5)),
                doc("id:ns:document::3", Timestamp(4), bucket(6))));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::2")), Timestamp(3));
}

TEST(DocumentIteratorTest, require_that_maxBytes_splits_iteration_results)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("id:ns:document::2", Timestamp(3), bucket(5)),
                doc("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(getSize(*make_doc(DocumentId("id:ns:document::1"))) +
                                     getSize(DocumentId("id:ns:document::2")));
    EXPECT_TRUE(!res1.isCompleted());
    EXPECT_EQ(2u, res1.getEntries().size());
    {
        SCOPED_TRACE("first part");
        checkEntry(res1, 0, *make_doc(DocumentId("id:ns:document::1")), Timestamp(2));
        checkEntry(res1, 1, DocumentId("id:ns:document::2"), Timestamp(3));
    }

    IterateResult res2 = itr.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    {
        SCOPED_TRACE("second part");
        checkEntry(res2, 0, *make_doc(DocumentId("id:ns:document::3")), Timestamp(4));
    }

    IterateResult res3 = itr.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQ(0u, res3.getEntries().size());
}

TEST(DocumentIteratorTest, require_that_maxBytes_splits_iteration_results_for_meta_data_only_iteration)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::NoFields>(), selectAll(), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("id:ns:document::2", Timestamp(3), bucket(5)),
                doc("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(2 * sizeof(DocEntry));
    EXPECT_TRUE(!res1.isCompleted());
    EXPECT_EQ(2u, res1.getEntries().size());
    // Note: empty doc types since we did not pass in an explicit doc type alongside the retrievers
    {
        SCOPED_TRACE("first part");
        checkEntry(res1, 0, Timestamp(2), DocumentMetaEnum::NONE, gid_of("id:ns:document::1"), "");
        checkEntry(res1, 1, Timestamp(3), DocumentMetaEnum::REMOVE_ENTRY, gid_of("id:ns:document::2"), "");
    }

    IterateResult res2 = itr.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    {
        SCOPED_TRACE("second part");
        checkEntry(res2, 0, Timestamp(4), DocumentMetaEnum::NONE, gid_of("id:ns:document::3"), "");
    }

    IterateResult res3 = itr.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQ(0u, res3.getEntries().size());
}

TEST(DocumentIteratorTest, require_that_at_least_one_document_is_returned_by_visit)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectAll(), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(2), bucket(5)));
    itr.add(cat(rem("id:ns:document::2", Timestamp(3), bucket(5)),
                doc("id:ns:document::3", Timestamp(4), bucket(5))));
    IterateResult res1 = itr.iterate(0);
    EXPECT_TRUE( ! res1.getEntries().empty());
    checkEntry(res1, 0, *make_doc(DocumentId("id:ns:document::1")), Timestamp(2));
}

TEST(DocumentIteratorTest, require_that_documents_outside_the_timestamp_limits_are_ignored)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectTimestampRange(100, 200), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(99),  bucket(5)));
    itr.add(doc("id:ns:document::2", Timestamp(100), bucket(5)));
    itr.add(doc("id:ns:document::3", Timestamp(200), bucket(5)));
    itr.add(doc("id:ns:document::4", Timestamp(201), bucket(5)));
    itr.add(rem("id:ns:document::5", Timestamp(99),  bucket(5)));
    itr.add(rem("id:ns:document::6", Timestamp(100), bucket(5)));
    itr.add(rem("id:ns:document::7", Timestamp(200), bucket(5)));
    itr.add(rem("id:ns:document::8", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(4u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::2")), Timestamp(100));
    checkEntry(res, 1, *make_doc(DocumentId("id:ns:document::3")), Timestamp(200));
    checkEntry(res, 2, DocumentId("id:ns:document::6"), Timestamp(100));
    checkEntry(res, 3, DocumentId("id:ns:document::7"), Timestamp(200));
}

TEST(DocumentIteratorTest, require_that_timestamp_subset_returns_the_appropriate_documents)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectTimestampSet(200, 350, 400), newestV(), -1, false);
    itr.add(doc("id:ns:document::1", Timestamp(500),  bucket(5)));
    itr.add(doc("id:ns:document::2", Timestamp(400), bucket(5)));
    itr.add(doc("id:ns:document::3", Timestamp(300), bucket(5)));
    itr.add(doc("id:ns:document::4", Timestamp(200), bucket(5)));
    itr.add(rem("id:ns:document::5", Timestamp(250),  bucket(5)));
    itr.add(rem("id:ns:document::6", Timestamp(350), bucket(5)));
    itr.add(rem("id:ns:document::7", Timestamp(450), bucket(5)));
    itr.add(rem("id:ns:document::8", Timestamp(550), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(3u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::2")), Timestamp(400));
    checkEntry(res, 1, *make_doc(DocumentId("id:ns:document::4")), Timestamp(200));
    checkEntry(res, 2, DocumentId("id:ns:document::6"), Timestamp(350));
}

TEST(DocumentIteratorTest, require_that_document_selection_will_filter_results)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocs("id=\"id:ns:document::xxx*\""), newestV(), -1, false);
    itr.add(doc("id:ns:document::xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("id:ns:document::yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("id:ns:document::xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("id:ns:document::yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("id:ns:document::xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("id:ns:document::yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("id:ns:document::xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("id:ns:document::yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(4u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::xxx1")), Timestamp(99));
    checkEntry(res, 1, *make_doc(DocumentId("id:ns:document::xxx2")), Timestamp(200));
    checkEntry(res, 2, DocumentId("id:ns:document::xxx3"), Timestamp(99));
    checkEntry(res, 3, DocumentId("id:ns:document::xxx4"), Timestamp(200));
}

TEST(DocumentIteratorTest, require_that_document_selection_handles_null_field)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocs("foo.aa == null"), newestV(), -1, false);
    itr.add(doc_with_null_fields("id:ns:foo::xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc_with_null_fields("id:ns:foo::xxx2", Timestamp(100),  bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    ASSERT_EQ(2u, res.getEntries().size());
    auto expected1 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xxx1"));
    checkEntry(res, 0, *expected1, Timestamp(99));
    auto expected2 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xxx2"));
    checkEntry(res, 1, *expected2, Timestamp(100));
}

TEST(DocumentIteratorTest, require_that_invalid_document_selection_returns_no_documents)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocs("=="), newestV(), -1, false);
    itr.add(doc("id:ns:document::xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("id:ns:document::yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("id:ns:document::xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("id:ns:document::yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("id:ns:document::xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("id:ns:document::yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("id:ns:document::xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("id:ns:document::yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(0u, res.getEntries().size());
}

TEST(DocumentIteratorTest, require_that_document_selection_and_timestamp_range_works_together)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocsWithinRange("id=\"id:ns:document::xxx*\"", 100, 200), newestV(), -1, false);
    itr.add(doc("id:ns:document::xxx1", Timestamp(99),  bucket(5)));
    itr.add(doc("id:ns:document::yyy1", Timestamp(100), bucket(5)));
    itr.add(doc("id:ns:document::xxx2", Timestamp(200), bucket(5)));
    itr.add(doc("id:ns:document::yyy2", Timestamp(201), bucket(5)));
    itr.add(rem("id:ns:document::xxx3", Timestamp(99),  bucket(5)));
    itr.add(rem("id:ns:document::yyy3", Timestamp(100), bucket(5)));
    itr.add(rem("id:ns:document::xxx4", Timestamp(200), bucket(5)));
    itr.add(rem("id:ns:document::yyy4", Timestamp(201), bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(2u, res.getEntries().size());
    checkEntry(res, 0, *make_doc(DocumentId("id:ns:document::xxx2")), Timestamp(200));
    checkEntry(res, 1, DocumentId("id:ns:document::xxx4"), Timestamp(200));
}

TEST(DocumentIteratorTest, require_that_fieldset_limits_fields_returned)
{
    auto limited = std::make_shared<document::FieldCollection>(getDocType(),document::Field::Set::Builder().add(&getDocType().getField("header")).build());
    DocumentIterator itr(bucket(5), std::move(limited), selectAll(), newestV(), -1, false);
    itr.add(doc_with_fields("id:ns:foo::xxx1", Timestamp(1),  bucket(5)));
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(1u, res.getEntries().size());
    auto expected = Document::make_without_repo(getDocType(), DocumentId("id:ns:foo::xxx1"));
    expected->setValue("header", StringFieldValue::make("foo"));
    checkEntry(res, 0, *expected, Timestamp(1));
}

namespace {
template <typename Container, typename T>
bool contains(const Container& c, const T& value) {
    return c.find(value) != c.end();
}
}

TEST(DocumentIteratorTest, require_that_userdoc_constrained_selections_pre_filter_on_GIDs)
{
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocs("id.user=1234"), newestV(), -1, false);
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
    EXPECT_EQ(3u, visited_lids.size());
    EXPECT_TRUE(contains(visited_lids, wanted_dr_1->docid));
    EXPECT_TRUE(contains(visited_lids, wanted_dr_2->docid));
    EXPECT_TRUE(contains(visited_lids, wanted_dr_3->docid));
}

TEST(DocumentIteratorTest, require_that_attributes_are_used)
{
    UnitDR::reset();
    DocumentIterator itr(bucket(5), std::make_shared<document::AllFields>(), selectDocs("foo.aa == 45"), docV(), -1, false);
    itr.add(doc_with_attr_fields("id:ns:foo::xx1", Timestamp(1), bucket(5),
                                 27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr.add(doc_with_attr_fields("id:ns:foo::xx2", Timestamp(2), bucket(5),
                                 27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr.add(doc_with_attr_fields("id:ns:foo::xx3", Timestamp(3), bucket(5),
                                 45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr.add(doc_with_attr_fields("id:ns:foo::xx4", Timestamp(4), bucket(5),
                                 45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res = itr.iterate(largeNum);
    EXPECT_TRUE(res.isCompleted());
    EXPECT_EQ(2u, res.getEntries().size());
    auto expected1 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx2"));
    expected1->setValue("header", StringFieldValue::make("foo"));
    expected1->setValue("body", StringFieldValue::make("bar"));
    expected1->setValue("aa", IntFieldValue::make(27));
    expected1->setValue("ab", IntFieldValue::make(28));
    expected1->setValue("dd", DoubleFieldValue::make(2.7));
    expected1->setValue("ss", StringFieldValue::make("x27"));
    auto expected2 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx4"));
    expected2->setValue("header", StringFieldValue::make("foo"));
    expected2->setValue("body", StringFieldValue::make("bar"));
    expected2->setValue("aa", IntFieldValue::make(45));
    expected2->setValue("ab", IntFieldValue::make(46));
    expected2->setValue("dd", DoubleFieldValue::make(4.5));
    expected2->setValue("ss", StringFieldValue::make("x45"));
    {
        SCOPED_TRACE("foo.aa == 45");
        checkEntry(res, 0, *expected1, Timestamp(2));
        checkEntry(res, 1, *expected2, Timestamp(4));
    }

    DocumentIterator itr2(bucket(5), std::make_shared<document::AllFields>(), selectDocs("foo.dd == 4.5"), docV(), -1, false);
    itr2.add(doc_with_attr_fields("id:ns:foo::xx5", Timestamp(5), bucket(5),
                                  27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr2.add(doc_with_attr_fields("id:ns:foo::xx6", Timestamp(6), bucket(5),
                                  27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr2.add(doc_with_attr_fields("id:ns:foo::xx7", Timestamp(7), bucket(5),
                                  45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr2.add(doc_with_attr_fields("id:ns:foo::xx8", Timestamp(8), bucket(5),
                                  45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res2 = itr2.iterate(largeNum);
    EXPECT_TRUE(res2.isCompleted());
    EXPECT_EQ(2u, res2.getEntries().size());
    auto expected3 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx6"));
    expected3->setValue("header", StringFieldValue::make("foo"));
    expected3->setValue("body", StringFieldValue::make("bar"));
    expected3->setValue("aa", IntFieldValue::make(27));
    expected3->setValue("ab", IntFieldValue::make(28));
    expected3->setValue("dd", DoubleFieldValue::make(2.7));
    expected3->setValue("ss", StringFieldValue::make("x27"));
    auto expected4 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx8"));
    expected4->setValue("header", StringFieldValue::make("foo"));
    expected4->setValue("body", StringFieldValue::make("bar"));
    expected4->setValue("aa", IntFieldValue::make(45));
    expected4->setValue("ab", IntFieldValue::make(46));
    expected4->setValue("dd", DoubleFieldValue::make(4.5));
    expected4->setValue("ss", StringFieldValue::make("x45"));
    {
        SCOPED_TRACE("foo.dd == 4.5");
        checkEntry(res2, 0, *expected3, Timestamp(6));
        checkEntry(res2, 1, *expected4, Timestamp(8));
    }

    DocumentIterator itr3(bucket(5), std::make_shared<document::AllFields>(), selectDocs("foo.ss == \"x45\""), docV(), -1, false);
    itr3.add(doc_with_attr_fields("id:ns:foo::xx9", Timestamp(9), bucket(5),
                                  27, 28, 27, 2.7, 2.8, "x27", "x28"));
    itr3.add(doc_with_attr_fields("id:ns:foo::xx10", Timestamp(10), bucket(5),
                                  27, 28, 45, 2.7, 4.5, "x27", "x45"));
    itr3.add(doc_with_attr_fields("id:ns:foo::xx11", Timestamp(11), bucket(5),
                                  45, 46, 27, 4.5, 2.7, "x45", "x27"));
    itr3.add(doc_with_attr_fields("id:ns:foo::xx12", Timestamp(12), bucket(5),
                                  45, 46, 45, 4.5, 4.5, "x45", "x45"));
    
    IterateResult res3 = itr3.iterate(largeNum);
    EXPECT_TRUE(res3.isCompleted());
    EXPECT_EQ(2u, res3.getEntries().size());
    auto expected5 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx10"));
    expected5->setValue("header", StringFieldValue::make("foo"));
    expected5->setValue("body", StringFieldValue::make("bar"));
    expected5->setValue("aa", IntFieldValue::make(27));
    expected5->setValue("ab", IntFieldValue::make(28));
    expected5->setValue("dd", DoubleFieldValue::make(2.7));
    expected5->setValue("ss", StringFieldValue::make("x27"));
    auto expected6 = Document::make_without_repo(getAttrDocType(), DocumentId("id:ns:foo::xx12"));
    expected6->setValue("header", StringFieldValue::make("foo"));
    expected6->setValue("body", StringFieldValue::make("bar"));
    expected6->setValue("aa", IntFieldValue::make(45));
    expected6->setValue("ab", IntFieldValue::make(46));
    expected6->setValue("dd", DoubleFieldValue::make(4.5));
    expected6->setValue("ss", StringFieldValue::make("x45"));
    {
        SCOPED_TRACE("foo.ss == \"x45\"");
        checkEntry(res3, 0, *expected5, Timestamp(10));
        checkEntry(res3, 1, *expected6, Timestamp(12));
    }
} 

GTEST_MAIN_RUN_ALL_TESTS()
