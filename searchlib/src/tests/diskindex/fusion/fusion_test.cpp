// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/diskindex/fusion.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/fef/fieldpositionsiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/posting_iterator.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("fusion_test");

namespace search {

using document::Document;
using fef::FieldPositionsIterator;
using fef::TermFieldMatchData;
using fef::TermFieldMatchDataArray;
using memoryindex::DocumentInverter;
using memoryindex::FieldIndexCollection;
using queryeval::SearchIterator;
using search::common::FileHeaderContext;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::index::test::MockFieldLengthInspector;
using vespalib::SequencedTaskExecutor;

using namespace index;

namespace diskindex {

class MyMockFieldLengthInspector : public IFieldLengthInspector {
    FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        if (field_name == "f0") {
            return FieldLengthInfo(3.5, 21);
        } else {
            return FieldLengthInfo();
        }
    }
};

class FusionTest : public ::testing::Test
{
protected:
    Schema _schema;
    const Schema & getSchema() const { return _schema; }

    void requireThatFusionIsWorking(const vespalib::string &prefix, bool directio, bool readmmap);
    void make_simple_index(const vespalib::string &dump_dir, const IFieldLengthInspector &field_length_inspector);
    bool try_merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources, std::shared_ptr<IFlushToken> flush_token);
    void merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources);
public:
    FusionTest();
};

namespace {

void
myPushDocument(DocumentInverter &inv)
{
    inv.pushDocuments(std::shared_ptr<vespalib::IDestructorCallback>());
}

}

vespalib::string
toString(FieldPositionsIterator posItr, bool hasElements = false, bool hasWeights = false)
{
    vespalib::asciistream ss;
    ss << "{";
    ss << posItr.getFieldLength() << ":";
    bool first = true;
    for (; posItr.valid(); posItr.next()) {
        if (!first) ss << ",";
        ss << posItr.getPosition();
        first = false;
        if (hasElements) {
            ss << "[e=" << posItr.getElementId();
            if (hasWeights)
                ss << ",w=" << posItr.getElementWeight();
            ss << ",l=" << posItr.getElementLen() << "]";
        }
    }
    ss << "}";
    return ss.str();
}

std::unique_ptr<Document>
make_doc10(DocBuilder &b)
{
    b.startDocument("id:ns:searchdocument::10");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        addStr("e").addStr("f").addStr("z").
        endField();
    b.startIndexField("f1").
        addStr("w").addStr("x").
        addStr("y").addStr("z").
        endField();
    b.startIndexField("f2").
        startElement(4).addStr("ax").addStr("ay").addStr("z").endElement().
        startElement(5).addStr("ax").endElement().
        endField();
    b.startIndexField("f3").
        startElement(4).addStr("wx").addStr("z").endElement().
        endField();

    return b.endDocument();
}

Schema::IndexField
make_index_field(vespalib::stringref name, CollectionType collection_type, bool interleaved_features)
{
    Schema::IndexField index_field(name, DataType::STRING, collection_type);
    index_field.set_interleaved_features(interleaved_features);
    return index_field;
}

Schema
make_schema(bool interleaved_features)
{
    Schema schema;
    schema.addIndexField(make_index_field("f0", CollectionType::SINGLE, interleaved_features));
    schema.addIndexField(make_index_field("f1", CollectionType::SINGLE, interleaved_features));
    schema.addIndexField(make_index_field("f2", CollectionType::ARRAY, interleaved_features));
    schema.addIndexField(make_index_field("f3", CollectionType::WEIGHTEDSET, interleaved_features));
    return schema;
}

void
assert_interleaved_features(DiskIndex &d, const vespalib::string &field, const vespalib::string &term, uint32_t doc_id, uint32_t exp_num_occs, uint32_t exp_field_length)
{
    using LookupResult = DiskIndex::LookupResult;
    using PostingListHandle = index::PostingListHandle;
    using SearchIterator = search::queryeval::SearchIterator;

    const Schema &schema = d.getSchema();
    uint32_t field_id(schema.getIndexFieldId(field));
    std::unique_ptr<LookupResult> lookup_result(d.lookup(field_id, term));
    ASSERT_TRUE(lookup_result);
    std::unique_ptr<PostingListHandle> handle(d.readPostingList(*lookup_result));
    ASSERT_TRUE(handle);
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);
    std::unique_ptr<SearchIterator> sbap(handle->createIterator(lookup_result->counts, tfmda));
    sbap->initFullRange();
    EXPECT_TRUE(sbap->seek(doc_id));
    sbap->unpack(doc_id);
    EXPECT_EQ(exp_num_occs, tfmd.getNumOccs());
    EXPECT_EQ(exp_field_length, tfmd.getFieldLength());
}

void
validateDiskIndex(DiskIndex &dw, bool f2HasElements, bool f3HasWeights)
{
    typedef DiskIndex::LookupResult LR;
    typedef index::PostingListHandle PH;
    typedef search::queryeval::SearchIterator SB;

    const Schema &schema(dw.getSchema());

    {
        uint32_t id1(schema.getIndexFieldId("f0"));
        LR::UP lr1(dw.lookup(id1, "c"));
        ASSERT_TRUE(lr1);
        PH::UP wh1(dw.readPostingList(*lr1));
        ASSERT_TRUE(wh1);
        TermFieldMatchData f0;
        TermFieldMatchDataArray a;
        a.add(&f0);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQ(vespalib::string("{1000000:}"), toString(f0.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        EXPECT_EQ(vespalib::string("{7:2}"), toString(f0.getIterator()));
    }
    {
        uint32_t id1(schema.getIndexFieldId("f2"));
        LR::UP lr1(dw.lookup(id1, "ax"));
        ASSERT_TRUE(lr1);
        PH::UP wh1(dw.readPostingList(*lr1));
        ASSERT_TRUE(wh1);
        TermFieldMatchData f2;
        TermFieldMatchDataArray a;
        a.add(&f2);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQ(vespalib::string("{1000000:}"), toString(f2.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        if (f2HasElements) {
            EXPECT_EQ(vespalib::string("{3:0[e=0,l=3],0[e=1,l=1]}"),
                      toString(f2.getIterator(), true));
        } else {
            EXPECT_EQ(vespalib::string("{3:0[e=0,l=3]}"),
                      toString(f2.getIterator(), true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));
        LR::UP lr1(dw.lookup(id1, "wx"));
        ASSERT_TRUE(lr1);
        PH::UP wh1(dw.readPostingList(*lr1));
        ASSERT_TRUE(wh1);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQ(vespalib::string("{1000000:}"), toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        if (f3HasWeights) {
            EXPECT_EQ(vespalib::string("{2:0[e=0,w=4,l=2]}"),
                      toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQ(vespalib::string("{2:0[e=0,w=1,l=2]}"),
                      toString(f3.getIterator(), true, true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));;
        LR::UP lr1(dw.lookup(id1, "zz"));
        ASSERT_TRUE(lr1);
        PH::UP wh1(dw.readPostingList(*lr1));
        ASSERT_TRUE(wh1);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQ(vespalib::string("{1000000:}"), toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(11));
        sbap->unpack(11);
        if (f3HasWeights) {
            EXPECT_EQ(vespalib::string("{1:0[e=0,w=-27,l=1]}"),
                      toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQ(vespalib::string("{1:0[e=0,w=1,l=1]}"),
                      toString(f3.getIterator(), true, true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));;
        LR::UP lr1(dw.lookup(id1, "zz0"));
        ASSERT_TRUE(lr1);
        PH::UP wh1(dw.readPostingList(*lr1));
        ASSERT_TRUE(wh1);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQ(vespalib::string("{1000000:}"), toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(12));
        sbap->unpack(12);
        if (f3HasWeights) {
            EXPECT_EQ(vespalib::string("{1:0[e=0,w=0,l=1]}"),
                         toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQ(vespalib::string("{1:0[e=0,w=1,l=1]}"),
                      toString(f3.getIterator(), true, true));
        }
    }
}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

void
FusionTest::requireThatFusionIsWorking(const vespalib::string &prefix, bool directio, bool readmmap)
{
    Schema schema;
    Schema schema2;
    Schema schema3;
    for (SchemaUtil::IndexIterator it(getSchema()); it.isValid(); ++it) {
        const Schema::IndexField &iField = _schema.getIndexField(it.getIndex());
        schema.addIndexField(Schema::IndexField(iField.getName(),
                                     iField.getDataType(),
                                     iField.getCollectionType()));
        if (iField.getCollectionType() == CollectionType::WEIGHTEDSET) {
            schema2.addIndexField(Schema::IndexField(iField.getName(),
                                                     iField.getDataType(),
                                                     CollectionType::ARRAY));
        } else {
            schema2.addIndexField(Schema::IndexField(iField.getName(),
                                                     iField.getDataType(),
                                                     iField.getCollectionType()));
        }
        schema3.addIndexField(Schema::IndexField(iField.getName(),
                                      iField.getDataType(),
                                      CollectionType::SINGLE));
    }
    schema3.addIndexField(Schema::IndexField("f4", DataType::STRING));
    schema.addFieldSet(Schema::FieldSet("nc0").
                              addField("f0").addField("f1"));
    schema2.addFieldSet(Schema::FieldSet("nc0").
                               addField("f1").addField("f0"));
    schema3.addFieldSet(Schema::FieldSet("nc2").
                               addField("f0").addField("f1").
                               addField("f2").addField("f3").
                               addField("f4"));
    FieldIndexCollection fic(schema, MockFieldLengthInspector());
    DocBuilder b(schema);
    auto invertThreads = SequencedTaskExecutor::create(invert_executor, 2);
    auto pushThreads = SequencedTaskExecutor::create(push_executor, 2);
    DocumentInverter inv(schema, *invertThreads, *pushThreads, fic);
    Document::UP doc;

    doc = make_doc10(b);
    inv.invertDocument(10, *doc);
    invertThreads->sync();
    myPushDocument(inv);
    pushThreads->sync();

    b.startDocument("id:ns:searchdocument::11").
        startIndexField("f3").
        startElement(-27).addStr("zz").endElement().
        endField();
    doc = b.endDocument();
    inv.invertDocument(11, *doc);
    invertThreads->sync();
    myPushDocument(inv);
    pushThreads->sync();

    b.startDocument("id:ns:searchdocument::12").
        startIndexField("f3").
        startElement(0).addStr("zz0").endElement().
        endField();
    doc = b.endDocument();
    inv.invertDocument(12, *doc);
    invertThreads->sync();
    myPushDocument(inv);
    pushThreads->sync();

    IndexBuilder ib(schema);
    vespalib::string dump2dir = prefix + "dump2";
    ib.setPrefix(dump2dir);
    uint32_t numDocs = 12 + 1;
    uint32_t numWords = fic.getNumUniqueWords();
    bool dynamicKPosOcc = false;
    MockFieldLengthInspector mock_field_length_inspector;
    TuneFileIndexing tuneFileIndexing;
    TuneFileSearch tuneFileSearch;
    DummyFileHeaderContext fileHeaderContext;
    if (directio) {
        tuneFileIndexing._read.setWantDirectIO();
        tuneFileIndexing._write.setWantDirectIO();
        tuneFileSearch._read.setWantDirectIO();
    }
    if (readmmap) {
        tuneFileSearch._read.setWantMemoryMap();
    }
    ib.open(numDocs, numWords, mock_field_length_inspector, tuneFileIndexing, fileHeaderContext);
    fic.dump(ib);
    ib.close();

    vespalib::string tsName = dump2dir + "/.teststamp";
    typedef search::FileKit FileKit;
    ASSERT_TRUE(FileKit::createStamp(tsName));
    ASSERT_TRUE(FileKit::hasStamp(tsName));
    ASSERT_TRUE(FileKit::removeStamp(tsName));
    ASSERT_FALSE(FileKit::hasStamp(tsName));
    vespalib::ThreadStackExecutor executor(4, 0x10000);

    do {
        DiskIndex dw2(prefix + "dump2");
        ASSERT_TRUE(dw2.setup(tuneFileSearch));
        validateDiskIndex(dw2, true, true);
    } while (0);

    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump2");
        ASSERT_TRUE(Fusion::merge(schema, prefix + "dump3", sources, selector,
                                  dynamicKPosOcc,
                                  tuneFileIndexing,fileHeaderContext, executor, std::make_shared<FlushToken>()));
    } while (0);
    do {
        DiskIndex dw3(prefix + "dump3");
        ASSERT_TRUE(dw3.setup(tuneFileSearch));
        validateDiskIndex(dw3, true, true);
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        ASSERT_TRUE(Fusion::merge(schema2, prefix + "dump4", sources, selector,
                                  dynamicKPosOcc,
                                  tuneFileIndexing, fileHeaderContext, executor, std::make_shared<FlushToken>()));
    } while (0);
    do {
        DiskIndex dw4(prefix + "dump4");
        ASSERT_TRUE(dw4.setup(tuneFileSearch));
        validateDiskIndex(dw4, true, false);
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        ASSERT_TRUE(Fusion::merge(schema3, prefix + "dump5", sources, selector,
                                  dynamicKPosOcc,
                                  tuneFileIndexing, fileHeaderContext, executor, std::make_shared<FlushToken>()));
    } while (0);
    do {
        DiskIndex dw5(prefix + "dump5");
        ASSERT_TRUE(dw5.setup(tuneFileSearch));
        validateDiskIndex(dw5, false, false);
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        ASSERT_TRUE(Fusion::merge(schema, prefix + "dump6", sources, selector,
                                  !dynamicKPosOcc,
                                  tuneFileIndexing, fileHeaderContext, executor, std::make_shared<FlushToken>()));
    } while (0);
    do {
        DiskIndex dw6(prefix + "dump6");
        ASSERT_TRUE(dw6.setup(tuneFileSearch));
        validateDiskIndex(dw6, true, true);
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump2");
        ASSERT_TRUE(Fusion::merge(schema, prefix + "dump3", sources, selector,
                                  dynamicKPosOcc,
                                  tuneFileIndexing, fileHeaderContext, executor, std::make_shared<FlushToken>()));
    } while (0);
    do {
        DiskIndex dw3(prefix + "dump3");
        ASSERT_TRUE(dw3.setup(tuneFileSearch));
        validateDiskIndex(dw3, true, true);
    } while (0);
}

void
FusionTest::make_simple_index(const vespalib::string &dump_dir, const IFieldLengthInspector &field_length_inspector)
{
    FieldIndexCollection fic(_schema, field_length_inspector);
    uint32_t numDocs = 20;
    uint32_t numWords = 1000;
    DocBuilder b(_schema);
    auto invertThreads = SequencedTaskExecutor::create(invert_executor, 2);
    auto pushThreads = SequencedTaskExecutor::create(push_executor, 2);
    DocumentInverter inv(_schema, *invertThreads, *pushThreads, fic);

    inv.invertDocument(10, *make_doc10(b));
    invertThreads->sync();
    myPushDocument(inv);
    pushThreads->sync();

    IndexBuilder ib(_schema);
    TuneFileIndexing tuneFileIndexing;
    DummyFileHeaderContext fileHeaderContext;
    ib.setPrefix(dump_dir);
    ib.open(numDocs, numWords, field_length_inspector, tuneFileIndexing, fileHeaderContext);
    fic.dump(ib);
    ib.close();
}

bool
FusionTest::try_merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources, std::shared_ptr<IFlushToken> flush_token)
{
    vespalib::ThreadStackExecutor executor(4, 0x10000);
    TuneFileIndexing tuneFileIndexing;
    DummyFileHeaderContext fileHeaderContext;
    SelectorArray selector(20, 0);
    return Fusion::merge(_schema, dump_dir, sources, selector,
                         false,
                         tuneFileIndexing, fileHeaderContext, executor, flush_token);
}

void
FusionTest::merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources)
{
    ASSERT_TRUE(try_merge_simple_indexes(dump_dir, sources, std::make_shared<FlushToken>()));
}

FusionTest::FusionTest()
    : ::testing::Test(),
      _schema(make_schema(false))
{
}

TEST_F(FusionTest, require_that_normal_fusion_is_working)
{
    requireThatFusionIsWorking("", false, false);
}

TEST_F(FusionTest, require_that_directio_fusion_is_working)
{
    requireThatFusionIsWorking("d", true, false);
}

TEST_F(FusionTest, require_that_mmap_fusion_is_working)
{
    requireThatFusionIsWorking("m", false, true);
}

TEST_F(FusionTest, require_that_directiommap_fusion_is_working)
{
    requireThatFusionIsWorking("dm", true, true);
}

namespace {

void clean_field_length_testdirs()
{
    vespalib::rmdir("fldump2", true);
    vespalib::rmdir("fldump3", true);
    vespalib::rmdir("fldump4", true);
}

}

TEST_F(FusionTest, require_that_average_field_length_is_preserved)
{
    clean_field_length_testdirs();
    make_simple_index("fldump2", MockFieldLengthInspector());
    make_simple_index("fldump3", MyMockFieldLengthInspector());
    merge_simple_indexes("fldump4", {"fldump2", "fldump3"});
    DiskIndex disk_index("fldump4");
    ASSERT_TRUE(disk_index.setup(TuneFileSearch()));
    EXPECT_EQ(3.5, disk_index.get_field_length_info("f0").get_average_field_length());
    clean_field_length_testdirs();
}

TEST_F(FusionTest, require_that_interleaved_features_can_be_reconstructed)
{
    clean_field_length_testdirs();
    make_simple_index("fldump2", MockFieldLengthInspector());
    _schema = make_schema(true); // want interleaved features
    merge_simple_indexes("fldump4", {"fldump2"});
    DiskIndex disk_index("fldump4");
    ASSERT_TRUE(disk_index.setup(TuneFileSearch()));
    assert_interleaved_features(disk_index, "f0", "a", 10, 1, 7);
    assert_interleaved_features(disk_index, "f1", "w", 10, 1, 4);
    assert_interleaved_features(disk_index, "f2", "ax", 10, 2, 4);
    assert_interleaved_features(disk_index, "f2", "ay", 10, 1, 4);
    assert_interleaved_features(disk_index, "f3", "wx", 10, 1, 2);
    clean_field_length_testdirs();
}

namespace {

void clean_stopped_fusion_testdirs()
{
    vespalib::rmdir("stopdump2", true);
    vespalib::rmdir("stopdump3", true);
}

class MyFlushToken : public FlushToken
{
    mutable std::atomic<size_t> _checks;
    const size_t        _limit;
public:
    MyFlushToken(size_t limit)
        : FlushToken(),
          _checks(0u),
          _limit(limit)
    {
    }
    ~MyFlushToken() override = default;
    bool stop_requested() const noexcept override;
    size_t get_checks() const noexcept { return _checks; }
};

bool
MyFlushToken::stop_requested() const noexcept
{
    if (++_checks >= _limit) {
        const_cast<MyFlushToken *>(this)->request_stop();
    }
    return FlushToken::stop_requested();
}

}

TEST_F(FusionTest, require_that_fusion_can_be_stopped)
{
    clean_stopped_fusion_testdirs();
    auto flush_token = std::make_shared<MyFlushToken>(10000);
    make_simple_index("stopdump2", MockFieldLengthInspector());
    ASSERT_TRUE(try_merge_simple_indexes("stopdump3", {"stopdump2"}, flush_token));
    EXPECT_EQ(48, flush_token->get_checks());
    vespalib::rmdir("stopdump3", true);
    flush_token = std::make_shared<MyFlushToken>(1);
    ASSERT_FALSE(try_merge_simple_indexes("stopdump3", {"stopdump2"}, flush_token));
    EXPECT_EQ(12, flush_token->get_checks());
    vespalib::rmdir("stopdump3", true);
    flush_token = std::make_shared<MyFlushToken>(47);
    ASSERT_FALSE(try_merge_simple_indexes("stopdump3", {"stopdump2"}, flush_token));
    EXPECT_EQ(49, flush_token->get_checks());
    clean_stopped_fusion_testdirs();
}

}

}

int
main(int argc, char* argv[])
{
    if (argc > 0) {
        search::index::DummyFileHeaderContext::setCreator(argv[0]);
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
