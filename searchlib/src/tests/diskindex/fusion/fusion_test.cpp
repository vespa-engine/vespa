// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/fusion.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/fef/fieldpositionsiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/posting_iterator.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("fusion_test");

namespace search {

using document::ArrayFieldValue;
using document::DataType;
using document::Document;
using document::StringFieldValue;
using document::WeightedSetFieldValue;
using fef::FieldPositionsIterator;
using fef::TermFieldMatchData;
using fef::TermFieldMatchDataArray;
using memoryindex::DocumentInverter;
using memoryindex::DocumentInverterContext;
using memoryindex::FieldIndexCollection;
using queryeval::SearchIterator;
using search::common::FileHeaderContext;
using search::index::schema::CollectionType;
using search::index::test::MockFieldLengthInspector;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
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
    bool   _force_small_merge_chunk;
    const Schema & getSchema() const { return _schema; }

    void requireThatFusionIsWorking(const vespalib::string &prefix, bool directio, bool readmmap, bool force_short_merge_chunk);
    void make_simple_index(const vespalib::string &dump_dir, const IFieldLengthInspector &field_length_inspector);
    bool try_merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources, std::shared_ptr<IFlushToken> flush_token);
    void merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources);
    void reconstruct_interleaved_features();
public:
    FusionTest();
};

namespace {

void
myPushDocument(DocumentInverter &inv)
{
    vespalib::Gate gate;
    inv.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
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
    auto doc = b.make_document("id:ns:searchdocument::10");
    StringFieldBuilder sfb(b);
    doc->setValue("f0", sfb.tokenize("a b c d e f z").build());
    doc->setValue("f1", sfb.tokenize("w x y z").build());
    auto string_array = b.make_array("f2");
    string_array.add(sfb.tokenize("ax ay z").build());
    string_array.add(sfb.tokenize("ax").build());
    doc->setValue("f2", string_array);
    auto string_wset = b.make_wset("f3");
    string_wset.add(sfb.tokenize("wx z").build(), 4);
    doc->setValue("f3", string_wset);
    return doc;
}

DocBuilder::AddFieldsType
make_add_fields()
{
    return [](auto& header) { using namespace document::config_builder;
        header.addField("f0", DataType::T_STRING)
            .addField("f1", DataType::T_STRING)
            .addField("f2", Array(DataType::T_STRING))
            .addField("f3", Wset(DataType::T_STRING));
            };
}

Schema
make_schema(bool interleaved_features)
{
    DocBuilder db(make_add_fields());
    return SchemaBuilder(db).add_all_indexes(interleaved_features).build();
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
    using LR = DiskIndex::LookupResult;
    using PH = index::PostingListHandle;
    using SB = search::queryeval::SearchIterator;

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
FusionTest::requireThatFusionIsWorking(const vespalib::string &prefix, bool directio, bool readmmap, bool force_small_merge_chunk)
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
    schema3.addIndexField(Schema::IndexField("f4", search::index::schema::DataType::STRING));
    schema.addFieldSet(Schema::FieldSet("nc0").
                              addField("f0").addField("f1"));
    schema2.addFieldSet(Schema::FieldSet("nc0").
                               addField("f1").addField("f0"));
    schema3.addFieldSet(Schema::FieldSet("nc2").
                               addField("f0").addField("f1").
                               addField("f2").addField("f3").
                               addField("f4"));
    FieldIndexCollection fic(schema, MockFieldLengthInspector());
    DocBuilder b(make_add_fields());
    StringFieldBuilder sfb(b);
    auto invertThreads = SequencedTaskExecutor::create(invert_executor, 2);
    auto pushThreads = SequencedTaskExecutor::create(push_executor, 2);
    DocumentInverterContext inv_context(schema, *invertThreads, *pushThreads, fic);
    DocumentInverter inv(inv_context);
    Document::UP doc;

    doc = make_doc10(b);
    inv.invertDocument(10, *doc, {});
    myPushDocument(inv);

    doc = b.make_document("id:ns:searchdocument::11");
    {
        auto string_wset = b.make_wset("f3");
        string_wset.add(sfb.word("zz").build(), -27);
        doc->setValue("f3", string_wset);
    }
    inv.invertDocument(11, *doc, {});
    myPushDocument(inv);

    doc = b.make_document("id:ns:searchdocument::12");
    {
        auto string_wset = b.make_wset("f3");
        string_wset.add(sfb.word("zz0").build(), 0);
        doc->setValue("f3", string_wset);
    }
    inv.invertDocument(12, *doc, {});
    myPushDocument(inv);


    const vespalib::string dump2dir = prefix + "dump2";
    constexpr uint32_t numDocs = 12 + 1;
    IndexBuilder ib(schema, dump2dir, numDocs);
    const uint32_t numWords = fic.getNumUniqueWords();
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
    ib.open(numWords, mock_field_length_inspector, tuneFileIndexing, fileHeaderContext);
    fic.dump(ib);
    ib.close();

    vespalib::string tsName = dump2dir + "/.teststamp";
    using FileKit = search::FileKit;
    ASSERT_TRUE(FileKit::createStamp(tsName));
    ASSERT_TRUE(FileKit::hasStamp(tsName));
    ASSERT_TRUE(FileKit::removeStamp(tsName));
    ASSERT_FALSE(FileKit::hasStamp(tsName));
    vespalib::ThreadStackExecutor executor(4);

    do {
        DiskIndex dw2(prefix + "dump2");
        ASSERT_TRUE(dw2.setup(tuneFileSearch));
        validateDiskIndex(dw2, true, true);
    } while (0);

    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump2");
        Fusion fusion(schema, prefix + "dump3", sources, selector,
                      tuneFileIndexing,fileHeaderContext);
        fusion.set_force_small_merge_chunk(force_small_merge_chunk);
        ASSERT_TRUE(fusion.merge(executor, std::make_shared<FlushToken>()));
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
        Fusion fusion(schema2, prefix + "dump4", sources, selector,
                      tuneFileIndexing, fileHeaderContext);
        fusion.set_force_small_merge_chunk(force_small_merge_chunk);
        ASSERT_TRUE(fusion.merge(executor, std::make_shared<FlushToken>()));
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
        Fusion fusion(schema3, prefix + "dump5", sources, selector,
                      tuneFileIndexing, fileHeaderContext);
        fusion.set_force_small_merge_chunk(force_small_merge_chunk);
        ASSERT_TRUE(fusion.merge(executor, std::make_shared<FlushToken>()));
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
        Fusion fusion(schema, prefix + "dump6", sources, selector,
                      tuneFileIndexing, fileHeaderContext);
        fusion.set_dynamic_k_pos_index_format(true);
        fusion.set_force_small_merge_chunk(force_small_merge_chunk);
        ASSERT_TRUE(fusion.merge(executor, std::make_shared<FlushToken>()));
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
        Fusion fusion(schema, prefix + "dump3", sources, selector,
                      tuneFileIndexing, fileHeaderContext);
        fusion.set_force_small_merge_chunk(force_small_merge_chunk);
        ASSERT_TRUE(fusion.merge(executor, std::make_shared<FlushToken>()));
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
    constexpr  uint32_t numDocs = 20;
    constexpr uint32_t numWords = 1000;
    DocBuilder b(make_add_fields());
    auto invertThreads = SequencedTaskExecutor::create(invert_executor, 2);
    auto pushThreads = SequencedTaskExecutor::create(push_executor, 2);
    DocumentInverterContext inv_context(_schema, *invertThreads, *pushThreads, fic);
    DocumentInverter inv(inv_context);

    auto doc10 = make_doc10(b);
    inv.invertDocument(10, *doc10, {});
    myPushDocument(inv);

    IndexBuilder ib(_schema, dump_dir, numDocs);
    TuneFileIndexing tuneFileIndexing;
    DummyFileHeaderContext fileHeaderContext;
    ib.open(numWords, field_length_inspector, tuneFileIndexing, fileHeaderContext);
    fic.dump(ib);
    ib.close();
}

bool
FusionTest::try_merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources, std::shared_ptr<IFlushToken> flush_token)
{
    vespalib::ThreadStackExecutor executor(4);
    TuneFileIndexing tuneFileIndexing;
    DummyFileHeaderContext fileHeaderContext;
    SelectorArray selector(20, 0);
    Fusion fusion(_schema, dump_dir, sources, selector, tuneFileIndexing, fileHeaderContext);
    fusion.set_force_small_merge_chunk(_force_small_merge_chunk);
    return fusion.merge(executor, flush_token);
}

void
FusionTest::merge_simple_indexes(const vespalib::string &dump_dir, const std::vector<vespalib::string> &sources)
{
    ASSERT_TRUE(try_merge_simple_indexes(dump_dir, sources, std::make_shared<FlushToken>()));
}

FusionTest::FusionTest()
    : ::testing::Test(),
      _schema(make_schema(false)),
      _force_small_merge_chunk(false)
{
}

TEST_F(FusionTest, require_that_normal_fusion_is_working)
{
    requireThatFusionIsWorking("", false, false, false);
}

TEST_F(FusionTest, require_that_directio_fusion_is_working)
{
    requireThatFusionIsWorking("d", true, false, false);
}

TEST_F(FusionTest, require_that_mmap_fusion_is_working)
{
    requireThatFusionIsWorking("m", false, true, false);
}

TEST_F(FusionTest, require_that_directiommap_fusion_is_working)
{
    requireThatFusionIsWorking("dm", true, true, false);
}

TEST_F(FusionTest, require_that_small_merge_chunk_fusion_is_working)
{
    requireThatFusionIsWorking("s", false, false, true);
}

namespace {

void clean_field_length_testdirs()
{
    std::filesystem::remove_all(std::filesystem::path("fldump2"));
    std::filesystem::remove_all(std::filesystem::path("fldump3"));
    std::filesystem::remove_all(std::filesystem::path("fldump4"));
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

void
FusionTest::reconstruct_interleaved_features()
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

TEST_F(FusionTest, require_that_interleaved_features_can_be_reconstructed)
{
    reconstruct_interleaved_features();
}

TEST_F(FusionTest, require_that_interleaved_features_can_be_reconstructed_with_small_merge_chunk)
{
    _force_small_merge_chunk = true;
    reconstruct_interleaved_features();
}

namespace {

void clean_stopped_fusion_testdirs()
{
    std::filesystem::remove_all(std::filesystem::path("stopdump2"));
    std::filesystem::remove_all(std::filesystem::path("stopdump3"));
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
    std::filesystem::remove_all(std::filesystem::path("stopdump3"));
    flush_token = std::make_shared<MyFlushToken>(1);
    ASSERT_FALSE(try_merge_simple_indexes("stopdump3", {"stopdump2"}, flush_token));
    EXPECT_EQ(8, flush_token->get_checks());
    std::filesystem::remove_all(std::filesystem::path("stopdump3"));
    flush_token = std::make_shared<MyFlushToken>(47);
    ASSERT_FALSE(try_merge_simple_indexes("stopdump3", {"stopdump2"}, flush_token));
    EXPECT_LE(48, flush_token->get_checks());
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
