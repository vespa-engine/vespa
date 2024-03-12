// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/diskindex/bitvectordictionary.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

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
    _writer.add(daf);
    return *this;
}

struct TestParam {
    bool directio;
    bool readmmap;
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << (param.directio ? "directio" : "normal") << (param.readmmap ? "mmap" : "read");
    return os;
}

class BitVectorTest : public ::testing::TestWithParam<TestParam>
{
protected:
    Schema _schema;
    uint32_t _indexId;
    BitVectorTest();
    ~BitVectorTest() override;
};

BitVectorTest::BitVectorTest()
    : ::testing::TestWithParam<TestParam>(),
      _schema(),
      _indexId(0)
{
    _schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
}

BitVectorTest::~BitVectorTest() = default;

INSTANTIATE_TEST_SUITE_P(BitVectorMultiTest, BitVectorTest,
                         ::testing::Values(TestParam{false, false}, TestParam{true, false}, TestParam{false, true}),
                         ::testing::PrintToStringParamName());

TEST_P(BitVectorTest, require_that_dictionary_handles_no_entries)
{
    TuneFileSeqWrite tuneFileWrite;
    TuneFileRandRead tuneFileRead;
    DummyFileHeaderContext fileHeaderContext;

    if (GetParam().directio) {
        tuneFileWrite.setWantDirectIO();
        tuneFileRead.setWantDirectIO();
    }
    if (GetParam().readmmap) {
        tuneFileRead.setWantMemoryMap();
    }
    std::filesystem::create_directory(std::filesystem::path("dump"));
    FieldWriterWrapper fww(5, 2, "dump/1/");
    EXPECT_TRUE(fww.open(_schema, _indexId, tuneFileWrite, fileHeaderContext));
    fww.newWord("1").add(1);
    fww.newWord("2").add(2).add(3);
    EXPECT_TRUE(fww._writer.close());

    BitVectorDictionary dict;
    BitVectorKeyScope bvScope(BitVectorKeyScope::PERFIELD_WORDS);
    EXPECT_TRUE(dict.open("dump/1/", tuneFileRead, bvScope));
    EXPECT_EQ(5u, dict.getDocIdLimit());
    EXPECT_EQ(0u, dict.getEntries().size());
    EXPECT_FALSE(dict.lookup(1));
    EXPECT_FALSE(dict.lookup(2));
}

TEST_P(BitVectorTest, require_that_dictionary_handles_multiple_entries)
{
    TuneFileSeqWrite tuneFileWrite;
    TuneFileRandRead tuneFileRead;
    DummyFileHeaderContext fileHeaderContext;

    if (GetParam().directio) {
        tuneFileWrite.setWantDirectIO();
        tuneFileRead.setWantDirectIO();
    }
    if (GetParam().readmmap) {
        tuneFileRead.setWantMemoryMap();
    }
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
    EXPECT_EQ(64u, dict.getDocIdLimit());
    EXPECT_EQ(2u, dict.getEntries().size());

    BitVectorWordSingleKey e;
    e = dict.getEntries()[0];
    EXPECT_EQ(1u, e._wordNum);
    EXPECT_EQ(17u, e._numDocs);
    e = dict.getEntries()[1];
    EXPECT_EQ(5u, e._wordNum);
    EXPECT_EQ(23u, e._numDocs);

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

}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 0) {
        search::index::DummyFileHeaderContext::setCreator(argv[0]);
    }
    return RUN_ALL_TESTS();
}
