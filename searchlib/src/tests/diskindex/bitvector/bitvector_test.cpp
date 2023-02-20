// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/diskindex/bitvectordictionary.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchcommon/common/schema.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("bitvector_test");

using namespace search::index;
using search::index::schema::DataType;

namespace search::diskindex {

struct FieldWriterWrapper
{
    FieldWriter _writer;

    FieldWriterWrapper(uint32_t docIdLimit, uint64_t numWordIds, vespalib::stringref path);
    FieldWriterWrapper & newWord(vespalib::stringref word);
    FieldWriterWrapper & add(uint32_t docId);

    bool open(const Schema &schema, const uint32_t indexId,
              const TuneFileSeqWrite &tuneFileWrite, const common::FileHeaderContext &fileHeaderContext);
};


FieldWriterWrapper::FieldWriterWrapper(uint32_t docIdLimit, uint64_t numWordIds, vespalib::stringref path)
    : _writer(docIdLimit, numWordIds, path)
{
    std::filesystem::create_directory(std::filesystem::path(path));
}

bool
FieldWriterWrapper::open(const Schema &schema, const uint32_t indexId,
                         const TuneFileSeqWrite &tuneFileWrite, const common::FileHeaderContext &fileHeaderContext)
{
    return _writer.open(64, 10000, false, false, schema, indexId, FieldLengthInfo(), tuneFileWrite, fileHeaderContext);
}

FieldWriterWrapper &
FieldWriterWrapper::newWord(vespalib::stringref word)
{
    _writer.newWord(word);
    return *this;
}


FieldWriterWrapper &
FieldWriterWrapper::add(uint32_t docId)
{
    DocIdAndFeatures daf;
    daf.set_doc_id(docId);
    daf.elements().emplace_back(0);
    daf.elements().back().setNumOccs(1);
    daf.word_positions().emplace_back(0);
    //LOG(info, "add(%" PRIu64 ", %u)", wordNum, docId);
    _writer.add(daf);
    return *this;
}

class Test : public vespalib::TestApp
{
private:
    Schema _schema;
    uint32_t _indexId;
public:
    void requireThatDictionaryHandlesNoEntries(bool directio, bool readmmap);
    void requireThatDictionaryHandlesMultipleEntries(bool directio, bool readmmap);

    Test();
    ~Test() override;
    int Main() override;
};

void
Test::requireThatDictionaryHandlesNoEntries(bool directio, bool readmmap)
{
    TuneFileSeqWrite tuneFileWrite;
    TuneFileRandRead tuneFileRead;
    DummyFileHeaderContext fileHeaderContext;

    if (directio) {
        tuneFileWrite.setWantDirectIO();
        tuneFileRead.setWantDirectIO();
    }
    if (readmmap)
        tuneFileRead.setWantMemoryMap();
    std::filesystem::create_directory(std::filesystem::path("dump"));
    FieldWriterWrapper fww(5, 2, "dump/1/");
    EXPECT_TRUE(fww.open(_schema, _indexId, tuneFileWrite, fileHeaderContext));
    fww.newWord("1").add(1);
    fww.newWord("2").add(2).add(3);
    EXPECT_TRUE(fww._writer.close());

    BitVectorDictionary dict;
    BitVectorKeyScope bvScope(BitVectorKeyScope::PERFIELD_WORDS);
    EXPECT_TRUE(dict.open("dump/1/", tuneFileRead, bvScope));
    EXPECT_EQUAL(5u, dict.getDocIdLimit());
    EXPECT_EQUAL(0u, dict.getEntries().size());
    EXPECT_FALSE(dict.lookup(1));
    EXPECT_FALSE(dict.lookup(2));
}

void
Test::requireThatDictionaryHandlesMultipleEntries(bool directio, bool readmmap)
{
    TuneFileSeqWrite tuneFileWrite;
    TuneFileRandRead tuneFileRead;
    DummyFileHeaderContext fileHeaderContext;

    if (directio) {
        tuneFileWrite.setWantDirectIO();
        tuneFileRead.setWantDirectIO();
    }
    if (readmmap)
        tuneFileRead.setWantMemoryMap();
    FieldWriterWrapper fww(64, 6, "dump/2/");
    EXPECT_TRUE(fww.open(_schema, _indexId, tuneFileWrite, fileHeaderContext));
    // must have >16 docs in order to create bitvector for a word
    // 17 docs for word 1
    BitVector::UP bv1exp(BitVector::create(64));
    fww.newWord("1");
    for (uint32_t docId = 1; docId < 18; ++docId) {
        fww.add(docId);
        bv1exp->setBit(docId);
    }
    fww.newWord("2").add(1);
    // 16 docs for word 3
    fww.newWord("3");
    for (uint32_t docId = 1; docId < 17; ++docId) {
        fww.add(docId);
    }
    fww.newWord("4").add(1);
    // 23 docs for word 5
    BitVector::UP bv5exp(BitVector::create(64));
    fww.newWord("5");
    for (uint32_t docId = 1; docId < 24; ++docId) {
        fww.add(docId * 2);
        bv5exp->setBit(docId * 2);
    }
    fww.newWord("6").add(1);
    EXPECT_TRUE(fww._writer.close());

    BitVectorDictionary dict;
    BitVectorKeyScope bvScope(BitVectorKeyScope::PERFIELD_WORDS);
    EXPECT_TRUE(dict.open("dump/2/", tuneFileRead, bvScope));
    EXPECT_EQUAL(64u, dict.getDocIdLimit());
    EXPECT_EQUAL(2u, dict.getEntries().size());

    BitVectorWordSingleKey e;
    e = dict.getEntries()[0];
    EXPECT_EQUAL(1u, e._wordNum);
    EXPECT_EQUAL(17u, e._numDocs);
    e = dict.getEntries()[1];
    EXPECT_EQUAL(5u, e._wordNum);
    EXPECT_EQUAL(23u, e._numDocs);

    EXPECT_FALSE(dict.lookup(2));
    EXPECT_FALSE(dict.lookup(3));
    EXPECT_FALSE(dict.lookup(4));
    EXPECT_FALSE(dict.lookup(6));

    BitVector::UP bv1act = dict.lookup(1);
    EXPECT_TRUE(bv1act);
    EXPECT_TRUE(*bv1exp == *bv1act);

    BitVector::UP bv5act = dict.lookup(5);
    EXPECT_TRUE(bv5act);
    EXPECT_TRUE(*bv5exp == *bv5act);
}

Test::Test()
    : _schema(),
      _indexId(0)
{
    _schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
}

Test::~Test() = default;

int
Test::Main()
{
    TEST_INIT("bitvector_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    TEST_DO(requireThatDictionaryHandlesNoEntries(false, false));
    TEST_DO(requireThatDictionaryHandlesMultipleEntries(false, false));
    TEST_DO(requireThatDictionaryHandlesNoEntries(true, false));
    TEST_DO(requireThatDictionaryHandlesMultipleEntries(true, false));
    TEST_DO(requireThatDictionaryHandlesNoEntries(false, true));
    TEST_DO(requireThatDictionaryHandlesMultipleEntries(false, true));

    TEST_DONE();
}

}

TEST_APPHOOK(search::diskindex::Test);
