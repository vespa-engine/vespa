// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/diskindex/pagedict4file.h>
#include <vespa/searchlib/diskindex/pagedict4randread.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <filesystem>

using search::diskindex::PageDict4FileSeqRead;
using search::diskindex::PageDict4FileSeqWrite;
using search::diskindex::PageDict4RandRead;
using search::index::DummyFileHeaderContext;
using search::index::PostingListCounts;
using search::index::PostingListOffsetAndCounts;
using search::index::PostingListParams;


namespace {

vespalib::string test_dir("long_words_dir");
vespalib::string dict(test_dir + "/dict");

PostingListCounts make_counts()
{
    PostingListCounts counts;
    counts._bitLength = 100;
    counts._numDocs = 1;
    counts._segments.clear();
    return counts;
}

vespalib::string
make_word(int i)
{
    vespalib::asciistream os;
    vespalib::string word(5_Ki, 'a');
    os << vespalib::setfill('0') << vespalib::setw(8) << i;
    word.append(os.str());
    return word;
}

}

/*
 * A long word that don't fit into a 4 KiB 'page' causes a fallback to
 * overflow handling where the word is put in the .ssdat file.
 *
 * Many long words causes excessive growth of the .ssdat file, with
 * overflow potentials when the whole file is read into a buffer.
 *
 *  4 GiB size: Overflow in ComprFileReadBase::ReadComprBuffer for expression
 *              readUnits * cbuf.getUnitSize() when both are 32-bits.
 *              Testable by setting num_words to 900_Ki
 *
 * 16 GiB size: Overflow in ComprFileReadBase::ReadComprBuffer when
 *              readUnits is 32-bit signed.
 *              Some overflows in ComprFileDecodeContext API.
 *              Overflow in DecodeContext64Base::getBitPos
 *              Testable by setting num_words to 4_Mi
 *
 * 32 GiB size: Overflow when calling ComprFileReadContext::allocComprBuf when
 *              comprBufSize is 32-bit unsigned.
 *              Overflow in DecodeContext64Base::setEnd.
 *              Testable by setting num_words to 9_Mi
 *
 * These overflows are fixed.
 */
TEST(PageDict4LongWordsTest, test_many_long_words)
{
    int num_words = 9_Mi;
    auto counts = make_counts();
    std::filesystem::remove_all(std::filesystem::path(test_dir));
    std::filesystem::create_directories(std::filesystem::path(test_dir));

    auto dw = std::make_unique<PageDict4FileSeqWrite>();
    DummyFileHeaderContext file_header_context;
    PostingListParams params;
    search::TuneFileSeqWrite tune_file_write;
    params.set("numWordIds", num_words);
    params.set("minChunkDocs", 256_Ki);
    dw->setParams(params);
    EXPECT_TRUE(dw->open(dict, tune_file_write, file_header_context));
    for (int i = 0; i < num_words; ++i) {
        auto word = make_word(i);
        dw->writeWord(word, counts);
    }
    EXPECT_TRUE(dw->close());
    dw.reset();

    auto drr = std::make_unique<PageDict4RandRead>();
    search::TuneFileRandRead tune_file_rand_read;
    EXPECT_TRUE(drr->open(dict, tune_file_rand_read));
    PostingListOffsetAndCounts offset_and_counts;
    uint64_t exp_offset = 0;
    uint64_t exp_acc_num_docs = 0;
    for (int i = 0; i < num_words; ++i) {
        auto word = make_word(i);
        uint64_t check_word_num = 0;
        EXPECT_TRUE(drr->lookup(word, check_word_num, offset_and_counts));
        EXPECT_EQ(i + 1, (int) check_word_num);
        EXPECT_EQ(exp_offset, offset_and_counts._offset);
        EXPECT_EQ(exp_acc_num_docs, offset_and_counts._accNumDocs);
        EXPECT_EQ(counts, offset_and_counts._counts);
        exp_offset += offset_and_counts._counts._bitLength;
        exp_acc_num_docs += offset_and_counts._counts._numDocs;
    }
    EXPECT_TRUE(drr->close());
    drr.reset();

    auto dr = std::make_unique<PageDict4FileSeqRead>();
    search::TuneFileSeqRead tune_file_read;
    EXPECT_TRUE(dr->open(dict, tune_file_read));
    vespalib::string check_word;
    PostingListCounts check_counts;
    for (int i = 0; i < num_words; ++i) {
        uint64_t check_word_num = 0;
        check_word.clear();
        dr->readWord(check_word, check_word_num, check_counts);
        EXPECT_EQ(i + 1, (int) check_word_num);
        EXPECT_EQ(make_word(i), check_word);
        EXPECT_EQ(counts, check_counts);
    }
    EXPECT_TRUE(dr->close());
    dr.reset();

    std::filesystem::remove_all(std::filesystem::path(test_dir));
}

GTEST_MAIN_RUN_ALL_TESTS()
