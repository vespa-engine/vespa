// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP("word_store_test");

using namespace vespalib::datastore;

namespace search::memoryindex {

TEST(WordStoreTest, words_can_be_added_and_retrieved)
{
    std::string w1 = "require";
    std::string w2 = "that";
    std::string w3 = "words";
    WordStore ws;
    static constexpr auto buffer_array_size = WordStore::buffer_array_size;
    using Aligner = WordStore::Aligner;
    EntryRef r1 = ws.addWord(w1);
    EntryRef r2 = ws.addWord(w2);
    EntryRef r3 = ws.addWord(w3);
    uint32_t invp = WordStore::buffer_array_size;   // Reserved as invalid
    uint32_t w1s = w1.size() + 1;
    uint32_t w1p = Aligner::pad(w1s);
    uint32_t w2s = w2.size() + 1;
    uint32_t w2p = Aligner::pad(w2s);
    EXPECT_EQ(invp, WordStore::RefType(r1).offset() * buffer_array_size);
    EXPECT_EQ(invp + w1s + w1p, WordStore::RefType(r2).offset() * buffer_array_size);
    EXPECT_EQ(invp + w1s + w1p + w2s + w2p, WordStore::RefType(r3).offset() * buffer_array_size);
    EXPECT_EQ(0u, WordStore::RefType(r1).bufferId());
    EXPECT_EQ(0u, WordStore::RefType(r2).bufferId());
    EXPECT_EQ(0u, WordStore::RefType(r3).bufferId());
    EXPECT_EQ(std::string("require"), ws.getWord(r1));
    EXPECT_EQ(std::string("that"), ws.getWord(r2));
    EXPECT_EQ(std::string("words"), ws.getWord(r3));
}

TEST(WordStoreTest, add_word_triggers_change_of_buffer)
{
    WordStore ws;
    size_t word = 0;
    uint32_t lastId = 0;
    char wordStr[10];
    for (;;++word) {
        snprintf(wordStr, sizeof(wordStr), "%6zu", word);
        // all words uses 12 bytes (include padding)
        EntryRef r = ws.addWord(std::string(wordStr));
        EXPECT_EQ(std::string(wordStr), ws.getWord(r));
        uint32_t bufferId = WordStore::RefType(r).bufferId();
        if (bufferId > lastId) {
            LOG(info,
                "Changed to bufferId %u after %zu words",
                bufferId, word);
            lastId = bufferId;
        }
        if (bufferId == 4) {
            lastId = bufferId;
            break;
        }
    }
    LOG(info, "Added %zu words in 4 buffers", word);
    EXPECT_EQ(2047u, word);
    EXPECT_EQ(4u, lastId);
}

TEST(WordStoreTest, long_word_triggers_exception)
{
    WordStore ws;
    vespalib::string word(16_Mi + 1_Ki, 'z');
    EXPECT_THROW(ws.addWord(word), vespalib::OverflowException);
}

}

GTEST_MAIN_RUN_ALL_TESTS()

