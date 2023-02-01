// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/i_field_index_remove_listener.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("document_remover_test");

using namespace search;
using namespace search::memoryindex;

struct WordFieldPair {
    vespalib::string _word;
    uint32_t _fieldId;
    WordFieldPair(vespalib::stringref word, uint32_t fieldId) noexcept
        : _word(word), _fieldId(fieldId)
    {}
    bool operator<(const WordFieldPair &rhs) const noexcept {
        if (_word != rhs._word) {
            return _word < rhs._word;
        }
        return _fieldId < rhs._fieldId;
    }
};

using WordFieldVector = std::vector<WordFieldPair>;

std::ostream &
operator<<(std::ostream &os, const WordFieldPair &val)
{
    os << "{" << val._word << "," << val._fieldId << "}";
    return os;
}

struct MockRemoveListener : public IFieldIndexRemoveListener {
    WordFieldVector _words;
    uint32_t _expDocId;
    uint32_t _fieldId;
    virtual void remove(const vespalib::stringref word, uint32_t docId) override {
        EXPECT_EQ(_expDocId, docId);
        _words.emplace_back(word, _fieldId);
    }
    void reset(uint32_t expDocId) {
        _words.clear();
        _expDocId = expDocId;
    }
    vespalib::string getWords() {
        std::sort(_words.begin(), _words.end());
        std::ostringstream oss;
        oss << _words;
        return oss.str();
    }
    void setFieldId(uint32_t fieldId) { _fieldId = fieldId; }
};

struct FieldIndexRemoverTest : public ::testing::Test {
    MockRemoveListener _listener;
    std::vector<std::unique_ptr<WordStore>> _wordStores;
    std::vector<std::map<vespalib::string, vespalib::datastore::EntryRef>> _wordToRefMaps;
    std::vector<std::unique_ptr<FieldIndexRemover>> _removers;

    FieldIndexRemoverTest();
    ~FieldIndexRemoverTest() override;
    vespalib::datastore::EntryRef getWordRef(const vespalib::string &word, uint32_t fieldId) {
        auto &wordToRefMap = _wordToRefMaps[fieldId];
        WordStore &wordStore = *_wordStores[fieldId];
        auto itr = wordToRefMap.find(word);
        if (itr == wordToRefMap.end()) {
            vespalib::datastore::EntryRef ref = wordStore.addWord(word);
            wordToRefMap[word] = ref;
            return ref;
        }
        return itr->second;
    }
    FieldIndexRemoverTest &insert(const vespalib::string &word, uint32_t fieldId, uint32_t docId) {
        assert(fieldId < _wordStores.size());
        _removers[fieldId]->insert(getWordRef(word, fieldId), docId);
        return *this;
    }
    void flush() {
        for (auto &remover : _removers) {
            remover->flush();
        }
    }
    vespalib::string remove(uint32_t docId) {
        _listener.reset(docId);
        uint32_t fieldId = 0;
        for (auto &remover : _removers) {
            _listener.setFieldId(fieldId);
            remover->remove(docId, _listener);
            ++fieldId;
        }
        return _listener.getWords();
    }
};

FieldIndexRemoverTest::FieldIndexRemoverTest()
    : _listener(),
      _wordStores(),
      _wordToRefMaps(),
      _removers()
{
    uint32_t numFields = 4;
    for (uint32_t fieldId = 0; fieldId < numFields; ++fieldId) {
        _wordStores.push_back(std::make_unique<WordStore>());
        _removers.push_back(std::make_unique<FieldIndexRemover>(*_wordStores.back()));
    }
    _wordToRefMaps.resize(numFields);
}
FieldIndexRemoverTest::~FieldIndexRemoverTest() = default;

TEST_F(FieldIndexRemoverTest, word_field_id_pairs_for_multiple_doc_ids_can_be_inserted)
{
    insert("a", 1, 10).insert("a", 1, 20).insert("a", 1, 30);
    insert("a", 2, 10).insert("a", 2, 20);
    insert("b", 1, 20).insert("b", 1, 30);
    insert("b", 2, 10).insert("b", 2, 30);
    insert("c", 1, 10);
    insert("c", 2, 20);
    insert("c", 3, 30);
    flush();

    EXPECT_EQ("[{a,1},{a,2},{b,2},{c,1}]", remove(10));
    EXPECT_EQ("[{a,1},{a,2},{b,1},{c,2}]", remove(20));
    EXPECT_EQ("[{a,1},{b,1},{b,2},{c,3}]", remove(30));
}

TEST_F(FieldIndexRemoverTest, we_can_insert_after_flush)
{
    insert("a", 1, 10).insert("b", 1, 10);
    flush();
    insert("b", 1, 20).insert("b", 2, 20);
    flush();

    EXPECT_EQ("[{a,1},{b,1}]", remove(10));
    EXPECT_EQ("[{b,1},{b,2}]", remove(20));
}

GTEST_MAIN_RUN_ALL_TESTS()
