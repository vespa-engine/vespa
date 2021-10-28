// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/test/fakedata/fake_match_loop.h>
#include <vespa/searchlib/test/fakedata/fakeposting.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cinttypes>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::queryeval::SearchIterator;

using namespace search::index;
using namespace search::fakedata;

using FakeWordUP = std::unique_ptr<FakeWord>;

void
validate_posting_list_for_word(const FakePosting& posting, const FakeWord& word)
{
    TermFieldMatchData md;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&md);

    md.setNeedNormalFeatures(posting.enable_unpack_normal_features());
    md.setNeedInterleavedFeatures(posting.enable_unpack_interleaved_features());
    std::unique_ptr<SearchIterator> iterator(posting.createIterator(tfmda));
    if (posting.hasWordPositions()) {
        word.validate(iterator.get(), tfmda, posting.enable_unpack_normal_features(), posting.has_interleaved_features() && posting.enable_unpack_interleaved_features(), false);
    } else {
        word.validate(iterator.get(), false);
    }
}

void
test_fake(const std::string& posting_type,
          const Schema& schema,
          const FakeWord& word)
{
    std::unique_ptr<FPFactory> factory(getFPFactory(posting_type, schema));
    std::vector<const FakeWord *> words;
    words.push_back(&word);
    factory->setup(words);
    auto posting = factory->make(word);

    printf("%s.bitsize=%d+%d+%d+%d+%d\n",
           posting->getName().c_str(),
           static_cast<int>(posting->bitSize()),
           static_cast<int>(posting->l1SkipBitSize()),
           static_cast<int>(posting->l2SkipBitSize()),
           static_cast<int>(posting->l3SkipBitSize()),
           static_cast<int>(posting->l4SkipBitSize()));

    validate_posting_list_for_word(*posting, word);
}

struct PostingListTest : public ::testing::Test {
    uint32_t num_docs;
    std::vector<std::string> posting_types;
    FakeWordSet word_set;
    FakeWordUP word1;
    FakeWordUP word2;
    FakeWordUP word3;
    FakeWordUP word4;
    FakeWordUP word5;
    vespalib::Rand48 rnd;

    PostingListTest()
        : num_docs(36000),
          posting_types(getPostingTypes()),
          word_set(),
          word1(),
          word2(),
          word3(),
          word4(),
          word5(),
          rnd()
    {
        rnd.srand48(32);
    }

    void setup(bool has_elements, bool has_element_weights) {
        word_set.setupParams(has_elements, has_element_weights);

        uint32_t w1_freq = 2;
        uint32_t w2_freq = 1000;
        uint32_t w3_freq = 10000;
        uint32_t w4_freq = 19000;
        uint32_t w5_freq = 5000;
        uint32_t w4w5od = 1000;

        word1 = std::make_unique<FakeWord>(num_docs, w1_freq, w1_freq / 2, "word1", rnd,
                                           word_set.getFieldsParams(), word_set.getPackedIndex());

        word2 = std::make_unique<FakeWord>(num_docs, w2_freq, w2_freq / 2, "word2", *word1, 4, rnd,
                                           word_set.getFieldsParams(), word_set.getPackedIndex());

        word3 = std::make_unique<FakeWord>(num_docs, w3_freq, w3_freq / 2, "word3", *word1, 10, rnd,
                                           word_set.getFieldsParams(), word_set.getPackedIndex());

        word4 = std::make_unique<FakeWord>(num_docs, w4_freq, w4_freq / 2, "word4", rnd,
                                           word_set.getFieldsParams(), word_set.getPackedIndex());

        word5 = std::make_unique<FakeWord>(num_docs, w5_freq, w5_freq / 2, "word5", *word4, w4w5od, rnd,
                                           word_set.getFieldsParams(), word_set.getPackedIndex());

    }

    void run() {
        for (const auto& type : posting_types) {
            test_fake(type, word_set.getSchema(), *word1);
            test_fake(type, word_set.getSchema(), *word2);
            test_fake(type, word_set.getSchema(), *word3);
            test_fake(type, word_set.getSchema(), *word4);
            test_fake(type, word_set.getSchema(), *word5);
        }
    }

};

TEST_F(PostingListTest, verify_posting_list_iterators_over_single_value_field)
{
    setup(false, false);
    run();
}

TEST_F(PostingListTest, verify_posting_list_iterators_over_array_field)
{
    setup(true, false);
    run();
}

TEST_F(PostingListTest, verify_posting_list_iterators_over_weighted_set_field)
{
    setup(true, true);
    run();
}

GTEST_MAIN_RUN_ALL_TESTS()
