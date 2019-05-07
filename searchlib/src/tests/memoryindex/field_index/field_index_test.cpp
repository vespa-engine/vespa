// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchlib/diskindex/fusion.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/fef/fieldpositionsiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/memoryindex/posting_iterator.h>
#include <vespa/searchlib/test/memoryindex/wrap_inserter.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("field_index_test");

namespace search {

using namespace btree;
using namespace datastore;
using namespace fef;
using namespace index;

using document::Document;
using queryeval::SearchIterator;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespalib::GenerationHandler;

namespace memoryindex {

using test::WrapInserter;
using PostingList = FieldIndex::PostingList;
using PostingConstItr = PostingList::ConstIterator;

class MyBuilder : public IndexBuilder {
private:
    std::stringstream _ss;
    bool              _insideWord;
    bool              _insideField;
    bool              _firstWord;
    bool              _firstField;
    bool              _firstDoc;

public:
    MyBuilder(const Schema &schema)
        : IndexBuilder(schema),
          _ss(),
          _insideWord(false),
          _insideField(false),
          _firstWord(true),
          _firstField(true),
          _firstDoc(true)
    {}

    virtual void startWord(vespalib::stringref word) override {
        assert(_insideField);
        assert(!_insideWord);
        if (!_firstWord)
            _ss << ",";
        _ss << "w=" << word << "[";
        _firstDoc = true;
        _insideWord = true;
    }

    virtual void endWord() override {
        assert(_insideWord);
        _ss << "]";
        _firstWord = false;
        _insideWord = false;
    }

    virtual void startField(uint32_t fieldId) override {
        assert(!_insideField);
        if (!_firstField) _ss << ",";
        _ss << "f=" << fieldId << "[";
        _firstWord = true;
        _insideField = true;
    }

    virtual void endField() override {
        assert(_insideField);
        assert(!_insideWord);
        _ss << "]";
        _firstField = false;
        _insideField = false;
    }

    virtual void add_document(const DocIdAndFeatures &features) override {
        assert(_insideWord);
        if (!_firstDoc) {
            _ss << ",";
        }
        _ss << "d=" << features.doc_id() << "[";
        bool first_elem = true;
        size_t word_pos_offset = 0;
        for (const auto& elem : features.elements()) {
            if (!first_elem) {
                _ss << ",";
            }
            _ss << "e=" << elem.getElementId() << ",w=" << elem.getWeight() << ",l=" << elem.getElementLen() << "[";
            bool first_pos = true;
            for (size_t i = 0; i < elem.getNumOccs(); ++i) {
                if (!first_pos) {
                    _ss << ",";
                }
                _ss << features.word_positions()[i + word_pos_offset].getWordPos();
                first_pos = false;
            }
            word_pos_offset += elem.getNumOccs();
            _ss << "]";
            first_elem = false;
        }
        _ss << "]";
        _firstDoc = false;
    }

    std::string toStr() const {
        return _ss.str();
    }
};

std::string
toString(FieldPositionsIterator posItr,
         bool hasElements = false,
         bool hasWeights = false)
{
    std::stringstream ss;
    ss << "{";
    ss << posItr.getFieldLength() << ":";
    bool first = true;
    for (; posItr.valid(); posItr.next()) {
        if (!first) ss << ",";
        ss << posItr.getPosition();
        first = false;
        if (hasElements) {
            ss << "[e=" << posItr.getElementId();
            if (hasWeights) {
                ss << ",w=" << posItr.getElementWeight();
            }
            ss << ",l=" << posItr.getElementLen() << "]";
        }
    }
    ss << "}";
    return ss.str();
}

bool
assertPostingList(const std::string &exp,
                  PostingConstItr itr,
                  const FeatureStore *store = nullptr)
{
    std::stringstream ss;
    FeatureStore::DecodeContextCooked decoder(nullptr);
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    ss << "[";
    for (size_t i = 0; itr.valid(); ++itr, ++i) {
        if (i > 0) ss << ",";
        uint32_t docId = itr.getKey();
        ss << docId;
        if (store != nullptr) { // consider features as well
            EntryRef ref(itr.getData());
            store->setupForField(0, decoder);
            store->setupForUnpackFeatures(ref, decoder);
            decoder.unpackFeatures(matchData, docId);
            ss << toString(tfmd.getIterator());
        }
    }
    ss << "]";
    bool result = (exp == ss.str());
    EXPECT_EQ(exp, ss.str());
    return result;
}

bool
assertPostingList(std::vector<uint32_t> &exp, PostingConstItr itr)
{
    std::stringstream ss;
    ss << "[";
    for (size_t i = 0; i < exp.size(); ++i) {
        if (i > 0) ss << ",";
        ss << exp[i];
    }
    ss << "]";
    return assertPostingList(ss.str(), itr);
}


namespace {

/**
 * A simple mockup of a memory field index, used to verify
 * that we get correct posting lists from real memory field index.
 */
class MockFieldIndex {
    std::map<std::pair<vespalib::string, uint32_t>, std::set<uint32_t>> _dict;
    vespalib::string _word;
    uint32_t _fieldId;

public:
    ~MockFieldIndex();
    void
    setNextWord(const vespalib::string &word) {
        _word = word;
    }

    void setNextField(uint32_t fieldId) {
        _fieldId = fieldId;
    }

    void add(uint32_t docId) {
        _dict[std::make_pair(_word, _fieldId)].insert(docId);
    }

    void remove(uint32_t docId) {
        _dict[std::make_pair(_word, _fieldId)].erase(docId);
    }

    std::vector<uint32_t> find(const vespalib::string &word, uint32_t fieldId) {
        std::vector<uint32_t> res;
        for (auto docId : _dict[std::make_pair(word, fieldId)] ) {
            res.push_back(docId);
        }
        return res;
    }

    auto begin() {
        return _dict.begin();
    }

    auto end() {
        return _dict.end();
    }
};

MockFieldIndex::~MockFieldIndex() = default;

/**
 * MockWordStoreScan is a helper class to ensure that previous word is
 * still stored safely in memory, to satisfy OrderedFieldIndexInserter
 * needs.
 */
class MockWordStoreScan {
    vespalib::string _word0;
    vespalib::string _word1;
    vespalib::string *_prevWord;
    vespalib::string *_word;

public:
    MockWordStoreScan()
        : _word0(),
          _word1(),
          _prevWord(&_word0),
          _word(&_word1)
    { }
    ~MockWordStoreScan();

    const vespalib::string &getWord() const {
        return *_word;
    }

    const vespalib::string &setWord(const vespalib::string &word) {
        std::swap(_prevWord, _word);
        *_word = word;
        return *_word;
    }
};

MockWordStoreScan::~MockWordStoreScan() = default;

/**
 * MyInserter performs insertions on both a mockup version of memory index
 * and a real memory index.  Mockup version is used to calculate expected
 * answers.
 */
class MyInserter {
    MockWordStoreScan _wordStoreScan;
    MockFieldIndex _mock;
    FieldIndexCollection _fieldIndexes;
    DocIdAndPosOccFeatures _features;
    IOrderedFieldIndexInserter *_inserter;

public:
    MyInserter(const Schema &schema)
        : _wordStoreScan(),
          _mock(),
          _fieldIndexes(schema),
          _features(),
          _inserter(nullptr)
    {
        _features.addNextOcc(0, 0, 1, 1);
    }
    ~MyInserter();

    void setNextWord(const vespalib::string &word) {
        const vespalib::string &w = _wordStoreScan.setWord(word);
        _inserter->setNextWord(w);
        _mock.setNextWord(w);
    }

    void setNextField(uint32_t fieldId) {
        if (_inserter != nullptr) {
            _inserter->flush();
        }
        _inserter = &_fieldIndexes.getFieldIndex(fieldId)->getInserter();
        _inserter->rewind();
        _mock.setNextField(fieldId);
    }

    void add(uint32_t docId) {
        _inserter->add(docId, _features);
        _mock.add(docId);
    }

    void remove(uint32_t docId) {
        _inserter->remove(docId);
        _mock.remove(docId);
    }

    bool assertPosting(const vespalib::string &word,
                       uint32_t fieldId) {
        std::vector<uint32_t> exp = _mock.find(word, fieldId);
        PostingConstItr itr = _fieldIndexes.find(word, fieldId);
        bool result = assertPostingList(exp, itr);
        EXPECT_TRUE(result);
        return result;
    }

    bool assertPostings() {
        if (_inserter != nullptr) {
            _inserter->flush();
        }
        for (auto wfp : _mock) {
            auto &wf = wfp.first;
            auto &word = wf.first;
            auto fieldId = wf.second;
            bool result = assertPosting(word, fieldId);
            EXPECT_TRUE(result);
            if (!result) {
                return false;
            }
        }
        return true;
    }

    void rewind() {
        if (_inserter != nullptr) {
            _inserter->flush();
            _inserter = nullptr;
        }
    }

    uint32_t getNumUniqueWords() {
        return _fieldIndexes.getNumUniqueWords();
    }

    FieldIndexCollection &getFieldIndexes() { return _fieldIndexes; }
};

MyInserter::~MyInserter() = default;

void
myremove(uint32_t docId, DocumentInverter &inv, FieldIndexCollection &fieldIndexes,
         ISequencedTaskExecutor &invertThreads)
{
    inv.removeDocument(docId);
    invertThreads.sync();
    inv.pushDocuments(fieldIndexes, std::shared_ptr<IDestructorCallback>());
}

class MyDrainRemoves : IFieldIndexRemoveListener {
    FieldIndexRemover &_remover;
public:
    virtual void remove(const vespalib::stringref, uint32_t) override { }

    MyDrainRemoves(FieldIndexCollection &fieldIndexes, uint32_t fieldId)
        : _remover(fieldIndexes.getFieldIndex(fieldId)->getDocumentRemover())
    {
    }

    MyDrainRemoves(FieldIndex& field_index)
            : _remover(field_index.getDocumentRemover())
    {
    }

    void drain(uint32_t docId) {
        _remover.remove(docId, *this);
    }
};

void
myPushDocument(DocumentInverter &inv, FieldIndexCollection &fieldIndexes)
{
    inv.pushDocuments(fieldIndexes, std::shared_ptr<IDestructorCallback>());
}

const FeatureStore *
featureStorePtr(const FieldIndexCollection &fieldIndexes, uint32_t fieldId)
{
    return &fieldIndexes.getFieldIndex(fieldId)->getFeatureStore();
}

const FeatureStore &
featureStoreRef(const FieldIndexCollection &fieldIndexes, uint32_t fieldId)
{
    return fieldIndexes.getFieldIndex(fieldId)->getFeatureStore();
}

DataStoreBase::MemStats
getFeatureStoreMemStats(const FieldIndexCollection &fieldIndexes)
{
    DataStoreBase::MemStats res;
    uint32_t numFields = fieldIndexes.getNumFields();
    for (uint32_t fieldId = 0; fieldId < numFields; ++fieldId) {
        DataStoreBase::MemStats stats =
            fieldIndexes.getFieldIndex(fieldId)->getFeatureStore().getMemStats();
        res += stats;
    }
    return res;
}

void
myCommit(FieldIndexCollection &fieldIndexes, ISequencedTaskExecutor &pushThreads)
{
    uint32_t fieldId = 0;
    for (auto &fieldIndex : fieldIndexes.getFieldIndexes()) {
        pushThreads.execute(fieldId,
                            [fieldIndex(fieldIndex.get())]()
                            { fieldIndex->commit(); });
        ++fieldId;
    }
    pushThreads.sync();
}

void
myCompactFeatures(FieldIndexCollection &fieldIndexes, ISequencedTaskExecutor &pushThreads)
{
    uint32_t fieldId = 0;
    for (auto &fieldIndex : fieldIndexes.getFieldIndexes()) {
        pushThreads.execute(fieldId,
                            [fieldIndex(fieldIndex.get())]()
                            { fieldIndex->compactFeatures(); });
        ++fieldId;
    }
}

}

Schema
make_single_field_schema()
{
    Schema result;
    result.addIndexField(Schema::IndexField("f0", DataType::STRING));
    return result;
}

struct FieldIndexTest : public ::testing::Test {
    Schema schema;
    FieldIndex idx;
    FieldIndexTest()
        : schema(make_single_field_schema()),
          idx(schema, 0)
    {
    }
};

Schema
make_multi_field_schema()
{
    Schema result;
    result.addIndexField(Schema::IndexField("f0", DataType::STRING));
    result.addIndexField(Schema::IndexField("f1", DataType::STRING));
    result.addIndexField(Schema::IndexField("f2", DataType::STRING, CollectionType::ARRAY));
    result.addIndexField(Schema::IndexField("f3", DataType::STRING, CollectionType::WEIGHTEDSET));
    return result;
}

struct FieldIndexCollectionTest : public ::testing::Test {
    Schema schema;
    FieldIndexCollection fic;
    FieldIndexCollectionTest()
        : schema(make_multi_field_schema()),
          fic(schema)
    {
    }
    ~FieldIndexCollectionTest() {}
};

TEST_F(FieldIndexTest, require_that_fresh_insert_works)
{
    EXPECT_TRUE(assertPostingList("[]", idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", idx.findFrozen("a")));
    EXPECT_EQ(0u, idx.getNumUniqueWords());
    WrapInserter(idx).word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", idx.findFrozen("a")));
    idx.commit();
    EXPECT_TRUE(assertPostingList("[10]", idx.findFrozen("a")));
    EXPECT_EQ(1u, idx.getNumUniqueWords());
}

TEST_F(FieldIndexTest, require_that_append_insert_works)
{
    WrapInserter(idx).word("a").add(10).flush().rewind().
            word("a").add(5).flush();
    EXPECT_TRUE(assertPostingList("[5,10]", idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", idx.findFrozen("a")));
    WrapInserter(idx).rewind().word("a").add(20).flush();
    EXPECT_TRUE(assertPostingList("[5,10,20]", idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", idx.findFrozen("a")));
    idx.commit();
    EXPECT_TRUE(assertPostingList("[5,10,20]", idx.findFrozen("a")));
}

TEST_F(FieldIndexCollectionTest, require_that_multiple_posting_lists_across_multiple_fields_can_exist)
{
    WrapInserter(fic, 0).word("a").add(10).word("b").add(11).add(15).flush();
    WrapInserter(fic, 1).word("a").add(5).word("b").add(12).flush();
    EXPECT_EQ(4u, fic.getNumUniqueWords());
    EXPECT_TRUE(assertPostingList("[10]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[5]", fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[11,15]", fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[12]", fic.find("b", 1)));
    EXPECT_TRUE(assertPostingList("[]", fic.find("a", 2)));
    EXPECT_TRUE(assertPostingList("[]", fic.find("c", 0)));
}

TEST_F(FieldIndexTest, require_that_remove_works)
{
    WrapInserter(idx).word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[]", idx.find("a")));
    WrapInserter(idx).add(10).add(20).add(30).flush();
    EXPECT_TRUE(assertPostingList("[10,20,30]", idx.find("a")));
    WrapInserter(idx).rewind().word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[20,30]", idx.find("a")));
    WrapInserter(idx).remove(20).flush();
    EXPECT_TRUE(assertPostingList("[30]", idx.find("a")));
    WrapInserter(idx).remove(30).flush();
    EXPECT_TRUE(assertPostingList("[]", idx.find("a")));
    EXPECT_EQ(1u, idx.getNumUniqueWords());
    MyDrainRemoves(idx).drain(10);
    WrapInserter(idx).rewind().word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", idx.find("a")));
}

TEST_F(FieldIndexCollectionTest, require_that_multiple_insert_and_remove_works)
{
    MyInserter inserter(schema);
    uint32_t numFields = 4;
    for (uint32_t fi = 0; fi < numFields; ++fi) {
        inserter.setNextField(fi);
        for (char w = 'a'; w <= 'z'; ++w) {
            std::string word(&w, 1);
            inserter.setNextWord(word);
            for (uint32_t di = 0; di < (uint32_t) w; ++di) { // insert
                inserter.add(di * 3);
            }
            EXPECT_EQ((w - 'a' + 1u) + ('z' - 'a' +1u) * fi,
                      inserter.getNumUniqueWords());
        }
    }
    EXPECT_TRUE(inserter.assertPostings());
    inserter.rewind();
    for (uint32_t fi = 0; fi < numFields; ++fi) {
        MyDrainRemoves drainRemoves(inserter.getFieldIndexes(), fi);
        for (uint32_t di = 0; di < 'z' * 2 + 1; ++di) {
            drainRemoves.drain(di);
        }
    }
    for (uint32_t fi = 0; fi < numFields; ++fi) {
        inserter.setNextField(fi);
        for (char w = 'a'; w <= 'z'; ++w) {
            std::string word(&w, 1);
            inserter.setNextWord(word);
            for (uint32_t di = 0; di < (uint32_t) w; ++di) {
                // remove half of the docs
                if ((di % 2) == 0) {
                    inserter.remove(di * 2);
                } else {
                    inserter.add(di * 2 + 1);
                }
            }
        }
    }
    EXPECT_TRUE(inserter.assertPostings());
}

void
addElement(DocIdAndFeatures &f,
           uint32_t elemLen,
           uint32_t numOccs,
           int32_t weight = 1)
{
    f.elements().emplace_back(f.elements().size(), weight, elemLen);
    f.elements().back().setNumOccs(numOccs);
    for (uint32_t i = 0; i < numOccs; ++i) {
        f.word_positions().emplace_back(i);
    }
}

DocIdAndFeatures
getFeatures(uint32_t elemLen, uint32_t numOccs, int32_t weight = 1)
{
    DocIdAndFeatures f;
    addElement(f, elemLen, numOccs, weight);
    return f;
}

TEST_F(FieldIndexCollectionTest, require_that_features_are_in_posting_lists)
{
    WrapInserter(fic, 0).word("a").add(1, getFeatures(4, 2)).flush();
    EXPECT_TRUE(assertPostingList("[1{4:0,1}]",
                                  fic.find("a", 0),
                                  featureStorePtr(fic, 0)));
    WrapInserter(fic, 0).word("b").add(2, getFeatures(5, 1)).
        add(3, getFeatures(6, 2)).flush();
    EXPECT_TRUE(assertPostingList("[2{5:0},3{6:0,1}]",
                                  fic.find("b", 0),
                                  featureStorePtr(fic, 0)));
    WrapInserter(fic, 1).word("c").add(4, getFeatures(7, 2)).flush();
    EXPECT_TRUE(assertPostingList("[4{7:0,1}]",
                                  fic.find("c", 1),
                                  featureStorePtr(fic, 1)));
}

TEST_F(FieldIndexTest, require_that_posting_iterator_is_working)
{
    WrapInserter(idx).word("a").add(10, getFeatures(4, 1)).
        add(20, getFeatures(5, 2)).
        add(30, getFeatures(6, 1)).
        add(40, getFeatures(7, 2)).flush();
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        PostingIterator itr(idx.find("not"),
                            idx.getFeatureStore(),
                            0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(idx.find("a"),
                            idx.getFeatureStore(),
                            0, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{4:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_EQ(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQ("{6:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(itr.seek(40));
        EXPECT_EQ(40u, itr.getDocId());
        itr.unpack(40);
        EXPECT_EQ("{7:0,1}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(41));
        EXPECT_TRUE(itr.isAtEnd());
    }
}

TEST_F(FieldIndexCollectionTest, require_that_basic_dumping_to_index_builder_is_working)
{
    MyBuilder b(schema);
    WordDocElementWordPosFeatures wpf;
    b.startField(4);
    b.startWord("a");
    DocIdAndFeatures features;
    features.set_doc_id(2);
    features.elements().emplace_back(0, 10, 20);
    features.elements().back().setNumOccs(2);
    features.word_positions().emplace_back(1);
    features.word_positions().emplace_back(3);
    b.add_document(features);
    b.endWord();
    b.endField();
    EXPECT_EQ("f=4[w=a[d=2[e=0,w=10,l=20[1,3]]]]", b.toStr());
}

TEST_F(FieldIndexCollectionTest, require_that_dumping_of_multiple_fields_to_index_builder_is_working)
{
    MyBuilder b(schema);
    DocIdAndFeatures df;
    WrapInserter(fic, 1).word("a").add(5, getFeatures(2, 1)).
            add(7, getFeatures(3, 2)).
            word("b").add(5, getFeatures(12, 2)).flush();

    df = getFeatures(4, 1);
    addElement(df, 5, 2);
    WrapInserter(fic, 2).word("a").add(5, df);
    df = getFeatures(6, 1);
    addElement(df, 7, 2);
    WrapInserter(fic, 2).add(7, df).flush();

    df = getFeatures(8, 1, 12);
    addElement(df, 9, 2, 13);
    WrapInserter(fic, 3).word("a").add(5, df);
    df = getFeatures(10, 1, 14);
    addElement(df, 11, 2, 15);
    WrapInserter(fic, 3).add(7, df).flush();

    fic.dump(b);

    EXPECT_EQ("f=0[],"
              "f=1[w=a[d=5[e=0,w=1,l=2[0]],d=7[e=0,w=1,l=3[0,1]]],"
              "w=b[d=5[e=0,w=1,l=12[0,1]]]],"
              "f=2[w=a[d=5[e=0,w=1,l=4[0],e=1,w=1,l=5[0,1]],"
              "d=7[e=0,w=1,l=6[0],e=1,w=1,l=7[0,1]]]],"
              "f=3[w=a[d=5[e=0,w=12,l=8[0],e=1,w=13,l=9[0,1]],"
              "d=7[e=0,w=14,l=10[0],e=1,w=15,l=11[0,1]]]]",
              b.toStr());
}

TEST_F(FieldIndexCollectionTest, require_that_dumping_words_with_no_docs_to_index_builder_is_working)
{
    WrapInserter(fic, 0).word("a").add(2, getFeatures(2, 1)).
            word("b").add(4, getFeatures(4, 1)).flush().rewind().
            word("a").remove(2).flush();
    {
        MyBuilder b(schema);
        fic.dump(b);
        EXPECT_EQ("f=0[w=b[d=4[e=0,w=1,l=4[0]]]],f=1[],f=2[],f=3[]",
                  b.toStr());
    }
    {
        search::diskindex::IndexBuilder b(schema);
        b.setPrefix("dump");
        TuneFileIndexing tuneFileIndexing;
        DummyFileHeaderContext fileHeaderContext;
        b.open(5, 2, tuneFileIndexing, fileHeaderContext);
        fic.dump(b);
        b.close();
    }
}

class InverterTest : public ::testing::Test {
public:
    Schema _schema;
    FieldIndexCollection _fic;
    DocBuilder _b;
    SequencedTaskExecutor _invertThreads;
    SequencedTaskExecutor _pushThreads;
    DocumentInverter _inv;

    InverterTest(const Schema& schema)
        : _schema(schema),
          _fic(_schema),
          _b(_schema),
          _invertThreads(2),
          _pushThreads(2),
          _inv(_schema, _invertThreads, _pushThreads)
    {
    }
};

class BasicInverterTest : public InverterTest {
public:
    BasicInverterTest() : InverterTest(make_multi_field_schema()) {}
};

TEST_F(BasicInverterTest, require_that_inversion_is_working)
{
    Document::UP doc;

    _b.startDocument("doc::10");
    _b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(10, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    _b.startDocument("doc::20");
    _b.startIndexField("f0").
        addStr("a").addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(20, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    _b.startDocument("doc::30");
    _b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        addStr("e").addStr("f").
        endField();
    _b.startIndexField("f1").
        addStr("\nw2").addStr("w").addStr("x").
        addStr("\nw3").addStr("y").addStr("z").
        endField();
    _b.startIndexField("f2").
        startElement(4).
        addStr("w").addStr("x").
        endElement().
        startElement(5).
        addStr("y").addStr("z").
        endElement().
        endField();
    _b.startIndexField("f3").
        startElement(6).
        addStr("w").addStr("x").
        endElement().
        startElement(7).
        addStr("y").addStr("z").
        endElement().
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(30, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    _b.startDocument("doc::40");
    _b.startIndexField("f0").
        addStr("a").addStr("a").addStr("b").addStr("c").addStr("a").
        addStr("e").addStr("f").
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(40, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    _b.startDocument("doc::999");
    _b.startIndexField("f0").
        addStr("this").addStr("is").addStr("_a_").addStr("test").
        addStr("for").addStr("insertion").addStr("speed").addStr("with").
        addStr("more").addStr("than").addStr("just").addStr("__a__").
        addStr("few").addStr("words").addStr("present").addStr("in").
        addStr("some").addStr("of").addStr("the").addStr("fields").
        endField();
    _b.startIndexField("f1").
        addStr("the").addStr("other").addStr("field").addStr("also").
        addStr("has").addStr("some").addStr("content").
        endField();
    _b.startIndexField("f2").
        startElement(1).
        addStr("strange").addStr("things").addStr("here").
        addStr("has").addStr("some").addStr("content").
        endElement().
        endField();
    _b.startIndexField("f3").
        startElement(3).
        addStr("not").addStr("a").addStr("weighty").addStr("argument").
        endElement().
        endField();
    doc = _b.endDocument();
    for (uint32_t docId = 10000; docId < 20000; ++docId) {
        _inv.invertDocument(docId, *doc);
        _invertThreads.sync();
        myPushDocument(_inv, _fic);
        _pushThreads.sync();
    }

    _pushThreads.sync();
    DataStoreBase::MemStats beforeStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "Before feature compaction: allocElems=%zu, usedElems=%zu"
        ", deadElems=%zu, holdElems=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        beforeStats._allocElems,
        beforeStats._usedElems,
        beforeStats._deadElems,
        beforeStats._holdElems,
        beforeStats._freeBuffers,
        beforeStats._activeBuffers,
        beforeStats._holdBuffers);
    myCompactFeatures(_fic, _pushThreads);
    std::vector<std::unique_ptr<GenerationHandler::Guard>> guards;
    for (auto &fieldIndex : _fic.getFieldIndexes()) {
        guards.push_back(std::make_unique<GenerationHandler::Guard>
                         (fieldIndex->takeGenerationGuard()));
    }
    myCommit(_fic, _pushThreads);
    DataStoreBase::MemStats duringStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "During feature compaction: allocElems=%zu, usedElems=%zu"
        ", deadElems=%zu, holdElems=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        duringStats._allocElems,
        duringStats._usedElems,
        duringStats._deadElems,
        duringStats._holdElems,
        duringStats._freeBuffers,
        duringStats._activeBuffers,
        duringStats._holdBuffers);
    guards.clear();
    myCommit(_fic, _pushThreads);
    DataStoreBase::MemStats afterStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "After feature compaction: allocElems=%zu, usedElems=%zu"
        ", deadElems=%zu, holdElems=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        afterStats._allocElems,
        afterStats._usedElems,
        afterStats._deadElems,
        afterStats._holdElems,
        afterStats._freeBuffers,
        afterStats._activeBuffers,
        afterStats._holdBuffers);

    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        PostingIterator itr(_fic.findFrozen("not", 0), featureStoreRef(_fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(_fic.findFrozen("a", 0), featureStoreRef(_fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{4:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_EQ(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQ("{6:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(itr.seek(40));
        EXPECT_EQ(40u, itr.getDocId());
        itr.unpack(40);
        EXPECT_EQ("{7:0,1,4}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(41));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(_fic.findFrozen("x", 0), featureStoreRef(_fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(_fic.findFrozen("x", 1), featureStoreRef(_fic, 1), 1, matchData);
        itr.initFullRange();
        EXPECT_EQ(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQ("{6:2[e=0,w=1,l=6]}", toString(tfmd.getIterator(), true, true));
    }
    {
        PostingIterator itr(_fic.findFrozen("x", 2), featureStoreRef(_fic, 2), 2, matchData);
        itr.initFullRange();
        EXPECT_EQ(30u, itr.getDocId());
        itr.unpack(30);
        // weight is hardcoded to 1 for new style il doc array field
        EXPECT_EQ("{2:1[e=0,w=1,l=2]}", toString(tfmd.getIterator(), true, true));
    }
    {
        PostingIterator itr(_fic.findFrozen("x", 3), featureStoreRef(_fic, 3), 3, matchData);
        itr.initFullRange();
        EXPECT_EQ(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQ("{2:1[e=0,w=6,l=2]}",
                  toString(tfmd.getIterator(), true, true));
    }
}

TEST_F(BasicInverterTest, require_that_inverter_handles_remove_via_document_remover)
{
    Document::UP doc;

    _b.startDocument("doc::1");
    _b.startIndexField("f0").addStr("a").addStr("b").endField();
    _b.startIndexField("f1").addStr("a").addStr("c").endField();
    Document::UP doc1 = _b.endDocument();
    _inv.invertDocument(1, *doc1.get());
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    _b.startDocument("doc::2");
    _b.startIndexField("f0").addStr("b").addStr("c").endField();
    Document::UP doc2 = _b.endDocument();
    _inv.invertDocument(2, *doc2.get());
    _invertThreads.sync();
    myPushDocument(_inv, _fic);
    _pushThreads.sync();

    EXPECT_TRUE(assertPostingList("[1]", _fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[1,2]", _fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", _fic.find("c", 0)));
    EXPECT_TRUE(assertPostingList("[1]", _fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[1]", _fic.find("c", 1)));

    myremove(1, _inv, _fic, _invertThreads);
    _pushThreads.sync();

    EXPECT_TRUE(assertPostingList("[]", _fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[2]", _fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", _fic.find("c", 0)));
    EXPECT_TRUE(assertPostingList("[]", _fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[]", _fic.find("c", 1)));
}

Schema
make_uri_schema()
{
    Schema result;
    result.addUriIndexFields(Schema::IndexField("iu", DataType::STRING));
    result.addUriIndexFields(Schema::IndexField("iau", DataType::STRING, CollectionType::ARRAY));
    result.addUriIndexFields(Schema::IndexField("iwu", DataType::STRING, CollectionType::WEIGHTEDSET));
    return result;
}

class UriInverterTest : public InverterTest {
public:
    UriInverterTest() : InverterTest(make_uri_schema()) {}
};

TEST_F(UriInverterTest, require_that_uri_indexing_is_working)
{
    Document::UP doc;

    _b.startDocument("doc::10");
    _b.startIndexField("iu").
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:81/fluke?ab=2#4").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("81").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("4").
        endSubField().
        endField();
    _b.startIndexField("iau").
        startElement(1).
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:82/fluke?ab=2#8").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("82").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("8").
        endSubField().
        endElement().
        startElement(1).
        startSubField("all").
        addUrlTokenizedString("http://www.flickr.com:82/fluke?ab=2#9").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.flickr.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("82").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("9").
        endSubField().
        endElement().
        endField();
    _b.startIndexField("iwu").
        startElement(4).
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:83/fluke?ab=2#12").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("83").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("12").
        endSubField().
        endElement().
        startElement(7).
        startSubField("all").
        addUrlTokenizedString("http://www.flickr.com:85/fluke?ab=2#13").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.flickr.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("85").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("13").
        endSubField().
        endElement().
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(10, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);

    _pushThreads.sync();

    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        uint32_t fieldId = _schema.getIndexFieldId("iu");
        PostingIterator itr(_fic.findFrozen("not", fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iu");
        PostingIterator itr(_fic.findFrozen("example", fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{9:2}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iau");
        PostingIterator itr(_fic.findFrozen("example", fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{9:2[e=0,l=9]}",
                  toString(tfmd.getIterator(), true, false));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iwu");
        PostingIterator itr(_fic.findFrozen("example", fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{9:2[e=0,w=4,l=9]}",
                  toString(tfmd.getIterator(), true, true));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        search::diskindex::IndexBuilder dib(_schema);
        dib.setPrefix("urldump");
        TuneFileIndexing tuneFileIndexing;
        DummyFileHeaderContext fileHeaderContext;
        dib.open(11, _fic.getNumUniqueWords(), tuneFileIndexing,
                 fileHeaderContext);
        _fic.dump(dib);
        dib.close();
    }
}

class CjkInverterTest : public InverterTest {
public:
    CjkInverterTest() : InverterTest(make_single_field_schema()) {}
};

TEST_F(CjkInverterTest, require_that_cjk_indexing_is_working)
{
    Document::UP doc;

    _b.startDocument("doc::10");
    _b.startIndexField("f0").
        addStr("我就是那个").
        setAutoSpace(false).
        addStr("大灰狼").
        setAutoSpace(true).
        endField();
    doc = _b.endDocument();
    _inv.invertDocument(10, *doc);
    _invertThreads.sync();
    myPushDocument(_inv, _fic);

    _pushThreads.sync();

    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    uint32_t fieldId = _schema.getIndexFieldId("f0");
    {
        PostingIterator itr(_fic.findFrozen("not", fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(_fic.findFrozen("我就"
                                    "是那个",
                                    fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{2:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(_fic.findFrozen("大灰"
                                    "狼",
                                    fieldId),
                            featureStoreRef(_fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQ(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQ("{2:1}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
}

void
insertAndAssertTuple(const vespalib::string &word, uint32_t fieldId, uint32_t docId,
                     FieldIndexCollection &dict)
{
    EntryRef wordRef = WrapInserter(dict, fieldId).rewind().word(word).
                       add(docId).flush().getWordRef();
    EXPECT_EQ(word, dict.getFieldIndex(fieldId)->getWordStore().getWord(wordRef));
    MyDrainRemoves(dict, fieldId).drain(docId);
}

TEST_F(FieldIndexCollectionTest, require_that_insert_tells_which_word_ref_that_was_inserted)
{
    insertAndAssertTuple("a", 1, 11, fic);
    insertAndAssertTuple("b", 1, 11, fic);
    insertAndAssertTuple("a", 2, 11, fic);

    insertAndAssertTuple("a", 1, 22, fic);
    insertAndAssertTuple("b", 2, 22, fic);
    insertAndAssertTuple("c", 2, 22, fic);
}

struct RemoverTest : public FieldIndexCollectionTest {
    SequencedTaskExecutor _invertThreads;
    SequencedTaskExecutor _pushThreads;

    RemoverTest()
        : FieldIndexCollectionTest(),
          _invertThreads(2),
          _pushThreads(2)
    {
    }
    void assertPostingLists(const vespalib::string &e1,
                            const vespalib::string &e2,
                            const vespalib::string &e3) {
        EXPECT_TRUE(assertPostingList(e1, fic.find("a", 1)));
        EXPECT_TRUE(assertPostingList(e2, fic.find("a", 2)));
        EXPECT_TRUE(assertPostingList(e3, fic.find("b", 1)));
    }
    void remove(uint32_t docId) {
        DocumentInverter inv(schema, _invertThreads, _pushThreads);
        myremove(docId, inv, fic, _invertThreads);
        _pushThreads.sync();
        EXPECT_FALSE(fic.getFieldIndex(0u)->getDocumentRemover().
                     getStore().get(docId).valid());
    }
};

TEST_F(RemoverTest, require_that_document_remover_can_remove_several_documents)
{
    WrapInserter(fic, 1).word("a").add(11).add(13).add(15).
            word("b").add(11).add(15).flush();
    WrapInserter(fic, 2).word("a").add(11).add(13).flush();
    assertPostingLists("[11,13,15]", "[11,13]", "[11,15]");

    remove(13);
    assertPostingLists("[11,15]", "[11]", "[11,15]");

    remove(11);
    assertPostingLists("[15]", "[]", "[15]");

    remove(15);
    assertPostingLists("[]", "[]", "[]");
}

TEST_F(RemoverTest, require_that_removal_of_non_existing_document_does_not_do_anything)
{
    WrapInserter(fic, 1).word("a").add(11).word("b").add(11).flush();
    WrapInserter(fic, 2).word("a").add(11).flush();
    assertPostingLists("[11]", "[11]", "[11]");
    remove(13);
    assertPostingLists("[11]", "[11]", "[11]");
}

}
}

GTEST_MAIN_RUN_ALL_TESTS()
