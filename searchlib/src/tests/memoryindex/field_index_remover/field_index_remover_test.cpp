// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/i_field_index_remove_listener.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("document_remover_test");

using namespace search;
using namespace search::memoryindex;

struct WordFieldPair
{
    vespalib::string _word;
    uint32_t _fieldId;
    WordFieldPair(vespalib::stringref word, uint32_t fieldId)
        : _word(word), _fieldId(fieldId)
    {}
    bool operator<(const WordFieldPair &rhs) {
        if (_word != rhs._word) {
            return _word < rhs._word;
        }
        return _fieldId < rhs._fieldId;
    }
};

typedef std::vector<WordFieldPair> WordFieldVector;

std::ostream &
operator<<(std::ostream &os, const WordFieldPair &val)
{
    os << "{" << val._word << "," << val._fieldId << "}";
    return os;
}

struct MockRemoveListener : public IFieldIndexRemoveListener
{
    WordFieldVector _words;
    uint32_t _expDocId;
    uint32_t _fieldId;
    virtual void remove(const vespalib::stringref word, uint32_t docId) override {
        EXPECT_EQUAL(_expDocId, docId);
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

struct Fixture
{
    MockRemoveListener _listener;
    std::vector<std::unique_ptr<WordStore>> _wordStores;
    std::vector<std::map<vespalib::string, datastore::EntryRef>> _wordToRefMaps;
    std::vector<std::unique_ptr<FieldIndexRemover>> _removers;
    Fixture()
        : _listener(),
          _wordStores(),
          _wordToRefMaps(),
          _removers()
    {
        uint32_t numFields = 4;
        for (uint32_t fieldId = 0; fieldId < numFields; ++fieldId) {
            _wordStores.push_back(std::make_unique<WordStore>());
            _removers.push_back(std::make_unique<FieldIndexRemover>
                                (*_wordStores.back()));
        }
        _wordToRefMaps.resize(numFields);
    }
    datastore::EntryRef getWordRef(const vespalib::string &word, uint32_t fieldId) {
        auto &wordToRefMap = _wordToRefMaps[fieldId];
        WordStore &wordStore = *_wordStores[fieldId];
        auto itr = wordToRefMap.find(word);
        if (itr == wordToRefMap.end()) {
            datastore::EntryRef ref = wordStore.addWord(word);
            wordToRefMap[word] = ref;
            return ref;
        }
        return itr->second;
    }
    Fixture &insert(const vespalib::string &word, uint32_t fieldId, uint32_t docId) {
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

TEST_F("require that {word,fieldId} pairs for multiple doc ids can be inserted", Fixture)
{
    f.insert("a", 1, 10).insert("a", 1, 20).insert("a", 1, 30);
    f.insert("a", 2, 10).insert("a", 2, 20);
    f.insert("b", 1, 20).insert("b", 1, 30);
    f.insert("b", 2, 10).insert("b", 2, 30);
    f.insert("c", 1, 10);
    f.insert("c", 2, 20);
    f.insert("c", 3, 30);
    f.flush();

    EXPECT_EQUAL("[{a,1},{a,2},{b,2},{c,1}]", f.remove(10));
    EXPECT_EQUAL("[{a,1},{a,2},{b,1},{c,2}]", f.remove(20));
    EXPECT_EQUAL("[{a,1},{b,1},{b,2},{c,3}]", f.remove(30));
}

TEST_F("require that we can insert after flush", Fixture)
{
    f.insert("a", 1, 10).insert("b", 1, 10);
    f.flush();
    f.insert("b", 1, 20).insert("b", 2, 20);
    f.flush();

    EXPECT_EQUAL("[{a,1},{b,1}]", f.remove(10));
    EXPECT_EQUAL("[{b,1},{b,2}]", f.remove(20));
}


TEST_MAIN() { TEST_RUN_ALL(); }
