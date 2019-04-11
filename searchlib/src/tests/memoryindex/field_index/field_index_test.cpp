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
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/memoryindex/postingiterator.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("dictionary_test");

namespace search {

using namespace btree;
using namespace datastore;
using namespace fef;
using namespace index;

using document::Document;
using queryeval::SearchIterator;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using test::SearchIteratorVerifier;
using vespalib::GenerationHandler;

namespace memoryindex {

typedef FieldIndex::PostingList PostingList;
typedef PostingList::ConstIterator PostingConstItr;

class MyBuilder : public IndexBuilder {
private:
    std::stringstream _ss;
    bool              _insideWord;
    bool              _insideField;
    bool              _insideDoc;
    bool              _insideElem;
    bool              _firstWord;
    bool              _firstField;
    bool              _firstDoc;
    bool              _firstElem;
    bool              _firstPos;
public:

    MyBuilder(const Schema &schema)
        : IndexBuilder(schema),
          _ss(),
          _insideWord(false),
          _insideField(false),
          _insideDoc(false),
          _insideElem(false),
          _firstWord(true),
          _firstField(true),
          _firstDoc(true),
          _firstElem(true),
          _firstPos(true)
    {}

    virtual void
    startWord(vespalib::stringref word) override
    {
        assert(_insideField);
        assert(!_insideWord);
        if (!_firstWord)
            _ss << ",";
        _ss << "w=" << word << "[";
        _firstDoc = true;
        _insideWord = true;
    }

    virtual void
    endWord() override
    {
        assert(_insideWord);
        assert(!_insideDoc);
        _ss << "]";
        _firstWord = false;
        _insideWord = false;
    }

    virtual void
    startField(uint32_t fieldId) override
    {
        assert(!_insideField);
        if (!_firstField) _ss << ",";
        _ss << "f=" << fieldId << "[";
        _firstWord = true;
        _insideField = true;
    }

    virtual void
    endField() override
    {
        assert(_insideField);
        assert(!_insideWord);
        _ss << "]";
        _firstField = false;
        _insideField = false;
    }

    virtual void
    startDocument(uint32_t docId) override
    {
        assert(_insideWord);
        assert(!_insideDoc);
        if (!_firstDoc) _ss << ",";
        _ss << "d=" << docId << "[";
        _firstElem = true;
        _insideDoc = true;
    }

    virtual void
    endDocument() override
    {
        assert(_insideDoc);
        assert(!_insideElem);
        _ss << "]";
        _firstDoc = false;
        _insideDoc = false;
    }

    virtual void
    startElement(uint32_t elementId,
                 int32_t weight,
                 uint32_t elementLen) override
    {
        assert(_insideDoc);
        assert(!_insideElem);
        if (!_firstElem)
            _ss << ",";
        _ss << "e=" << elementId <<
            ",w=" << weight << ",l=" << elementLen << "[";
        _firstPos = true;
        _insideElem = true;
    }

    virtual void
    endElement() override
    {
        assert(_insideElem);
        _ss << "]";
        _firstElem = false;
        _insideElem = false;
    }

    virtual void
    addOcc(const WordDocElementWordPosFeatures &features) override
    {
        assert(_insideElem);
        if (!_firstPos) _ss << ",";
        _ss << features.getWordPos();
        _firstPos = false;
    }

    std::string
    toStr() const
    {
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
            if (hasWeights)
                ss << ",w=" << posItr.getElementWeight();
            ss << ",l=" << posItr.getElementLen() << "]";
        }
    }
    ss << "}";
    return ss.str();
}

bool
assertPostingList(const std::string &exp,
                  PostingConstItr itr,
                  const FeatureStore *store = NULL)
{
    std::stringstream ss;
    FeatureStore::DecodeContextCooked decoder(NULL);
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    ss << "[";
    for (size_t i = 0; itr.valid(); ++itr, ++i) {
        if (i > 0) ss << ",";
        uint32_t docId = itr.getKey();
        ss << docId;
        if (store != NULL) { // consider features as well
            EntryRef ref(itr.getData());
            store->setupForField(0, decoder);
            store->setupForUnpackFeatures(ref, decoder);
            decoder.unpackFeatures(matchData, docId);
            ss << toString(tfmd.getIterator());
        }
    }
    ss << "]";
    return EXPECT_EQUAL(exp, ss.str());
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


namespace
{

/**
 * A simple mockup of a memory field index, used to verify
 * that we get correct posting lists from real memory field index.
 */
class MockFieldIndex
{
    std::map<std::pair<vespalib::string, uint32_t>, std::set<uint32_t>> _dict;
    vespalib::string _word;
    uint32_t _fieldId;

public:
    ~MockFieldIndex();
    void
    setNextWord(const vespalib::string &word)
    {
        _word = word;
    }

    void
    setNextField(uint32_t fieldId)
    {
        _fieldId = fieldId;
    }

    void
    add(uint32_t docId)
    {
        _dict[std::make_pair(_word, _fieldId)].insert(docId);
    }

    void
    remove(uint32_t docId)
    {
        _dict[std::make_pair(_word, _fieldId)].erase(docId);
    }

    std::vector<uint32_t>
    find(const vespalib::string &word, uint32_t fieldId)
    {
        std::vector<uint32_t> res;
        for (auto docId : _dict[std::make_pair(word, fieldId)] ) {
            res.push_back(docId);
        }
        return res;
    }

    auto begin()
    {
        return _dict.begin();
    }

    auto end()
    {
        return _dict.end();
    }
};

MockFieldIndex::~MockFieldIndex() = default;

/**
 * MockWordStoreScan is a helper class to ensure that previous word is
 * still stored safely in memory, to satisfy OrderedFieldIndexInserter
 * needs.
 */
class MockWordStoreScan
{
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

    const vespalib::string &
    getWord() const
    {
        return *_word;
    }

    const vespalib::string &
    setWord(const vespalib::string &word)
    {
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
class MyInserter
{
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

    void
    setNextWord(const vespalib::string &word)
    {
        const vespalib::string &w = _wordStoreScan.setWord(word);
        _inserter->setNextWord(w);
        _mock.setNextWord(w);
    }

    void
    setNextField(uint32_t fieldId)
    {
        if (_inserter != nullptr) {
            _inserter->flush();
        }
        _inserter = &_fieldIndexes.getFieldIndex(fieldId)->getInserter();
        _inserter->rewind();
        _mock.setNextField(fieldId);
    }

    void
    add(uint32_t docId)
    {
        _inserter->add(docId, _features);
        _mock.add(docId);
    }

    void
    remove(uint32_t docId)
    {
        _inserter->remove(docId);
        _mock.remove(docId);
    }

    bool
    assertPosting(const vespalib::string &word,
                  uint32_t fieldId)
    {
        std::vector<uint32_t> exp = _mock.find(word, fieldId);
        PostingConstItr itr = _fieldIndexes.find(word, fieldId);
        return EXPECT_TRUE(assertPostingList(exp, itr));
    }

    bool
    assertPostings()
    {
        if (_inserter != nullptr) {
            _inserter->flush();
        }
        for (auto wfp : _mock) {
            auto &wf = wfp.first;
            auto &word = wf.first;
            auto fieldId = wf.second;
            if (!EXPECT_TRUE(assertPosting(word, fieldId))) {
                return false;
            }
        }
        return true;
    }

    void
    rewind()
    {
        if (_inserter != nullptr) {
            _inserter->flush();
            _inserter = nullptr;
        }
    }

    uint32_t
    getNumUniqueWords()
    {
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


class WrapInserter
{
    OrderedFieldIndexInserter &_inserter;
public:
    WrapInserter(FieldIndexCollection &fieldIndexes, uint32_t fieldId)
        : _inserter(fieldIndexes.getFieldIndex(fieldId)->getInserter())
    {
    }

    WrapInserter &word(vespalib::stringref word_)
    {
        _inserter.setNextWord(word_);
        return *this;
    }

    WrapInserter &add(uint32_t docId, const index::DocIdAndFeatures &features)
    {
        _inserter.add(docId, features);
        return *this;
    }

    WrapInserter &add(uint32_t docId)
    {
        DocIdAndPosOccFeatures features;
        features.addNextOcc(0, 0, 1, 1);
        return add(docId, features);
    }

    WrapInserter &remove(uint32_t docId)
    {
        _inserter.remove(docId);
        return *this;
    }

    WrapInserter &flush()
    {
        _inserter.flush();
        return *this;
    }

    WrapInserter &rewind()
    {
        _inserter.rewind();
        return *this;
    }

    datastore::EntryRef
    getWordRef()
    {
        return _inserter.getWordRef();
    }
};


class MyDrainRemoves : IFieldIndexRemoveListener
{
    FieldIndexRemover &_remover;
public:
    virtual void remove(const vespalib::stringref, uint32_t) override { }

    MyDrainRemoves(FieldIndexCollection &fieldIndexes, uint32_t fieldId)
        : _remover(fieldIndexes.getFieldIndex(fieldId)->getDocumentRemover())
    {
    }

    void drain(uint32_t docId)
    {
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


void myCommit(FieldIndexCollection &fieldIndexes, ISequencedTaskExecutor &pushThreads)
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


struct Fixture
{
    Schema _schema;
    Fixture() : _schema() {
        _schema.addIndexField(Schema::IndexField("f0", DataType::STRING));
        _schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
        _schema.addIndexField(Schema::IndexField("f2", DataType::STRING, CollectionType::ARRAY));
        _schema.addIndexField(Schema::IndexField("f3", DataType::STRING, CollectionType::WEIGHTEDSET));
    }
    const Schema & getSchema() const { return _schema; }
};

// TODO: Rewrite most tests to use FieldIndex directly instead of going via FieldIndexCollection.

TEST_F("requireThatFreshInsertWorks", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
    SequencedTaskExecutor pushThreads(2);
    EXPECT_TRUE(assertPostingList("[]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[]", fic.findFrozen("a", 0)));
    EXPECT_EQUAL(0u, fic.getNumUniqueWords());
    WrapInserter(fic, 0).word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[]", fic.findFrozen("a", 0)));
    myCommit(fic, pushThreads);
    EXPECT_TRUE(assertPostingList("[10]", fic.findFrozen("a", 0)));
    EXPECT_EQUAL(1u, fic.getNumUniqueWords());
}

TEST_F("requireThatAppendInsertWorks", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
    SequencedTaskExecutor pushThreads(2);
    WrapInserter(fic, 0).word("a").add(10).flush().rewind().
        word("a").add(5).flush();
    EXPECT_TRUE(assertPostingList("[5,10]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[]", fic.findFrozen("a", 0)));
    WrapInserter(fic, 0).rewind().word("a").add(20).flush();
    EXPECT_TRUE(assertPostingList("[5,10,20]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[]", fic.findFrozen("a", 0)));
    myCommit(fic, pushThreads);
    EXPECT_TRUE(assertPostingList("[5,10,20]", fic.findFrozen("a", 0)));
}

TEST_F("requireThatMultiplePostingListsCanExist", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
    WrapInserter(fic, 0).word("a").add(10).word("b").add(11).add(15).flush();
    WrapInserter(fic, 1).word("a").add(5).word("b").add(12).flush();
    EXPECT_EQUAL(4u, fic.getNumUniqueWords());
    EXPECT_TRUE(assertPostingList("[10]", fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[5]", fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[11,15]", fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[12]", fic.find("b", 1)));
    EXPECT_TRUE(assertPostingList("[]", fic.find("a", 2)));
    EXPECT_TRUE(assertPostingList("[]", fic.find("c", 0)));
}

TEST_F("requireThatRemoveWorks", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
    WrapInserter(fic, 0).word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[]", fic.find("a", 0)));
    WrapInserter(fic, 0).add(10).add(20).add(30).flush();
    EXPECT_TRUE(assertPostingList("[10,20,30]", fic.find("a", 0)));
    WrapInserter(fic, 0).rewind().word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[20,30]", fic.find("a", 0)));
    WrapInserter(fic, 0).remove(20).flush();
    EXPECT_TRUE(assertPostingList("[30]", fic.find("a", 0)));
    WrapInserter(fic, 0).remove(30).flush();
    EXPECT_TRUE(assertPostingList("[]", fic.find("a", 0)));
    EXPECT_EQUAL(1u, fic.getNumUniqueWords());
    MyDrainRemoves(fic, 0).drain(10);
    WrapInserter(fic, 0).rewind().word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", fic.find("a", 0)));
}

TEST_F("requireThatMultipleInsertAndRemoveWorks", Fixture)
{
    MyInserter inserter(f.getSchema());
    uint32_t numFields = 4;
    for (uint32_t fi = 0; fi < numFields; ++fi) {
        inserter.setNextField(fi);
        for (char w = 'a'; w <= 'z'; ++w) {
            std::string word(&w, 1);
            inserter.setNextWord(word);
            for (uint32_t di = 0; di < (uint32_t) w; ++di) { // insert
                inserter.add(di * 3);
            }
            EXPECT_EQUAL((w - 'a' + 1u) + ('z' - 'a' +1u) * fi,
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
    f._elements.push_back(WordDocElementFeatures(f._elements.size()));
    f._elements.back().setElementLen(elemLen);
    f._elements.back().setWeight(weight);
    f._elements.back().setNumOccs(numOccs);
    for (uint32_t i = 0; i < numOccs; ++i) {
        f._wordPositions.push_back(WordDocElementWordPosFeatures(i));
    }
}

DocIdAndFeatures
getFeatures(uint32_t elemLen, uint32_t numOccs, int32_t weight = 1)
{
    DocIdAndFeatures f;
    addElement(f, elemLen, numOccs, weight);
    return f;
}

TEST_F("requireThatFeaturesAreInPostingLists", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
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

class Verifier : public SearchIteratorVerifier {
public:
    Verifier(const Schema & schema);
    ~Verifier();

    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        TermFieldMatchDataArray matchData;
        matchData.add(&_tfmd);
        return std::make_unique<PostingIterator>(_fieldIndexes.find("a", 0), featureStoreRef(_fieldIndexes, 0), 0, matchData);
    }

private:
    mutable TermFieldMatchData _tfmd;
    FieldIndexCollection _fieldIndexes;
};


Verifier::Verifier(const Schema & schema)
    : _tfmd(),
      _fieldIndexes(schema)
{
    WrapInserter inserter(_fieldIndexes, 0);
    inserter.word("a");
    for (uint32_t docId : getExpectedDocIds()) {
        inserter.add(docId);
    }
    inserter.flush();
}
Verifier::~Verifier() {}

TEST_F("require that postingiterator conforms", Fixture) {
    Verifier verifier(f.getSchema());
    verifier.verify();

}

TEST_F("requireThatPostingIteratorIsWorking", Fixture)
{
    FieldIndexCollection fic(f.getSchema());
    WrapInserter(fic, 0).word("a").add(10, getFeatures(4, 1)).
        add(20, getFeatures(5, 2)).
        add(30, getFeatures(6, 1)).
        add(40, getFeatures(7, 2)).flush();
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        PostingIterator itr(fic.find("not", 0),
                            featureStoreRef(fic, 0),
                            0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(fic.find("a", 0),
                            featureStoreRef(fic, 0),
                            0, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{4:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_EQUAL(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQUAL("{6:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(itr.seek(40));
        EXPECT_EQUAL(40u, itr.getDocId());
        itr.unpack(40);
        EXPECT_EQUAL("{7:0,1}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(41));
        EXPECT_TRUE(itr.isAtEnd());
    }
}

TEST_F("requireThatDumpingToIndexBuilderIsWorking", Fixture)
{
    {
        MyBuilder b(f.getSchema());
        WordDocElementWordPosFeatures wpf;
        b.startField(4);
        b.startWord("a");
        b.startDocument(2);
        b.startElement(0, 10, 20);
        wpf.setWordPos(1);
        b.addOcc(wpf);
        wpf.setWordPos(3);
        b.addOcc(wpf);
        b.endElement();
        b.endDocument();
        b.endWord();
        b.endField();
        EXPECT_EQUAL("f=4[w=a[d=2[e=0,w=10,l=20[1,3]]]]", b.toStr());
    }
    {
        FieldIndexCollection fic(f.getSchema());
        MyBuilder b(f.getSchema());
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

        EXPECT_EQUAL("f=0[],"
                     "f=1[w=a[d=5[e=0,w=1,l=2[0]],d=7[e=0,w=1,l=3[0,1]]],"
                     "w=b[d=5[e=0,w=1,l=12[0,1]]]],"
                     "f=2[w=a[d=5[e=0,w=1,l=4[0],e=1,w=1,l=5[0,1]],"
                     "d=7[e=0,w=1,l=6[0],e=1,w=1,l=7[0,1]]]],"
                     "f=3[w=a[d=5[e=0,w=12,l=8[0],e=1,w=13,l=9[0,1]],"
                     "d=7[e=0,w=14,l=10[0],e=1,w=15,l=11[0,1]]]]",
                     b.toStr());
    }
    { // test word with no docs
        FieldIndexCollection fic(f.getSchema());
        WrapInserter(fic, 0).word("a").add(2, getFeatures(2, 1)).
            word("b").add(4, getFeatures(4, 1)).flush().rewind().
            word("a").remove(2).flush();
        {
            MyBuilder b(f.getSchema());
            fic.dump(b);
            EXPECT_EQUAL("f=0[w=b[d=4[e=0,w=1,l=4[0]]]],f=1[],f=2[],f=3[]",
                         b.toStr());
        }
        {
            search::diskindex::IndexBuilder b(f.getSchema());
            b.setPrefix("dump");
            TuneFileIndexing tuneFileIndexing;
            DummyFileHeaderContext fileHeaderContext;
            b.open(5, 2, tuneFileIndexing, fileHeaderContext);
            fic.dump(b);
            b.close();
        }
    }
}


template <typename FixtureBase>
class FieldIndexFixture : public FixtureBase
{
public:
    using FixtureBase::getSchema;
    FieldIndexCollection _fic;
    DocBuilder _b;
    SequencedTaskExecutor _invertThreads;
    SequencedTaskExecutor _pushThreads;
    DocumentInverter _inv;

    FieldIndexFixture()
        : FixtureBase(),
          _fic(getSchema()),
          _b(getSchema()),
          _invertThreads(2),
          _pushThreads(2),
          _inv(getSchema(), _invertThreads, _pushThreads)
    {
    }
};


TEST_F("requireThatInversionIsWorking", FieldIndexFixture<Fixture>)
{
    Document::UP doc;

    f._b.startDocument("doc::10");
    f._b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    doc = f._b.endDocument();
    f._inv.invertDocument(10, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    f._b.startDocument("doc::20");
    f._b.startIndexField("f0").
        addStr("a").addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    doc = f._b.endDocument();
    f._inv.invertDocument(20, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    f._b.startDocument("doc::30");
    f._b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        addStr("e").addStr("f").
        endField();
    f._b.startIndexField("f1").
        addStr("\nw2").addStr("w").addStr("x").
        addStr("\nw3").addStr("y").addStr("z").
        endField();
    f._b.startIndexField("f2").
        startElement(4).
        addStr("w").addStr("x").
        endElement().
        startElement(5).
        addStr("y").addStr("z").
        endElement().
        endField();
    f._b.startIndexField("f3").
        startElement(6).
        addStr("w").addStr("x").
        endElement().
        startElement(7).
        addStr("y").addStr("z").
        endElement().
        endField();
    doc = f._b.endDocument();
    f._inv.invertDocument(30, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    f._b.startDocument("doc::40");
    f._b.startIndexField("f0").
        addStr("a").addStr("a").addStr("b").addStr("c").addStr("a").
        addStr("e").addStr("f").
        endField();
    doc = f._b.endDocument();
    f._inv.invertDocument(40, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    f._b.startDocument("doc::999");
    f._b.startIndexField("f0").
        addStr("this").addStr("is").addStr("_a_").addStr("test").
        addStr("for").addStr("insertion").addStr("speed").addStr("with").
        addStr("more").addStr("than").addStr("just").addStr("__a__").
        addStr("few").addStr("words").addStr("present").addStr("in").
        addStr("some").addStr("of").addStr("the").addStr("fields").
        endField();
    f._b.startIndexField("f1").
        addStr("the").addStr("other").addStr("field").addStr("also").
        addStr("has").addStr("some").addStr("content").
        endField();
    f._b.startIndexField("f2").
        startElement(1).
        addStr("strange").addStr("things").addStr("here").
        addStr("has").addStr("some").addStr("content").
        endElement().
        endField();
    f._b.startIndexField("f3").
        startElement(3).
        addStr("not").addStr("a").addStr("weighty").addStr("argument").
        endElement().
        endField();
    doc = f._b.endDocument();
    for (uint32_t docId = 10000; docId < 20000; ++docId) {
        f._inv.invertDocument(docId, *doc);
        f._invertThreads.sync();
        myPushDocument(f._inv, f._fic);
        f._pushThreads.sync();
    }

    f._pushThreads.sync();
    DataStoreBase::MemStats beforeStats = getFeatureStoreMemStats(f._fic);
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
    myCompactFeatures(f._fic, f._pushThreads);
    std::vector<std::unique_ptr<GenerationHandler::Guard>> guards;
    for (auto &fieldIndex : f._fic.getFieldIndexes()) {
        guards.push_back(std::make_unique<GenerationHandler::Guard>
                         (fieldIndex->takeGenerationGuard()));
    }
    myCommit(f._fic, f._pushThreads);
    DataStoreBase::MemStats duringStats = getFeatureStoreMemStats(f._fic);
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
    myCommit(f._fic, f._pushThreads);
    DataStoreBase::MemStats afterStats = getFeatureStoreMemStats(f._fic);
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
        PostingIterator itr(f._fic.findFrozen("not", 0), featureStoreRef(f._fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(f._fic.findFrozen("a", 0), featureStoreRef(f._fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{4:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_EQUAL(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQUAL("{6:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(itr.seek(40));
        EXPECT_EQUAL(40u, itr.getDocId());
        itr.unpack(40);
        EXPECT_EQUAL("{7:0,1,4}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(41));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(f._fic.findFrozen("x", 0), featureStoreRef(f._fic, 0), 0, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        PostingIterator itr(f._fic.findFrozen("x", 1), featureStoreRef(f._fic, 1), 1, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQUAL("{6:2[e=0,w=1,l=6]}", toString(tfmd.getIterator(), true, true));
    }
    {
        PostingIterator itr(f._fic.findFrozen("x", 2), featureStoreRef(f._fic, 2), 2, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(30u, itr.getDocId());
        itr.unpack(30);
        // weight is hardcoded to 1 for new style il doc array field
        EXPECT_EQUAL("{2:1[e=0,w=1,l=2]}", toString(tfmd.getIterator(), true, true));
    }
    {
        PostingIterator itr(f._fic.findFrozen("x", 3), featureStoreRef(f._fic, 3), 3, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(30u, itr.getDocId());
        itr.unpack(30);
        EXPECT_EQUAL("{2:1[e=0,w=6,l=2]}",
                   toString(tfmd.getIterator(), true, true));
    }
}

TEST_F("requireThatInverterHandlesRemoveViaDocumentRemover",
       FieldIndexFixture<Fixture>)
{
    Document::UP doc;

    f._b.startDocument("doc::1");
    f._b.startIndexField("f0").addStr("a").addStr("b").endField();
    f._b.startIndexField("f1").addStr("a").addStr("c").endField();
    Document::UP doc1 = f._b.endDocument();
    f._inv.invertDocument(1, *doc1.get());
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    f._b.startDocument("doc::2");
    f._b.startIndexField("f0").addStr("b").addStr("c").endField();
    Document::UP doc2 = f._b.endDocument();
    f._inv.invertDocument(2, *doc2.get());
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);
    f._pushThreads.sync();

    EXPECT_TRUE(assertPostingList("[1]", f._fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[1,2]", f._fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", f._fic.find("c", 0)));
    EXPECT_TRUE(assertPostingList("[1]", f._fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[1]", f._fic.find("c", 1)));

    myremove(1, f._inv, f._fic, f._invertThreads);
    f._pushThreads.sync();

    EXPECT_TRUE(assertPostingList("[]", f._fic.find("a", 0)));
    EXPECT_TRUE(assertPostingList("[2]", f._fic.find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", f._fic.find("c", 0)));
    EXPECT_TRUE(assertPostingList("[]", f._fic.find("a", 1)));
    EXPECT_TRUE(assertPostingList("[]", f._fic.find("c", 1)));
}

class UriFixture
{
public:
    Schema _schema;
    UriFixture()
        : _schema()
    {
        _schema.addUriIndexFields(Schema::IndexField("iu", DataType::STRING));
        _schema.addUriIndexFields(Schema::IndexField("iau", DataType::STRING, CollectionType::ARRAY));
        _schema.addUriIndexFields(Schema::IndexField("iwu", DataType::STRING, CollectionType::WEIGHTEDSET));
    }
    const Schema & getSchema() const { return _schema; }
};


TEST_F("requireThatUriIndexingIsWorking", FieldIndexFixture<UriFixture>)
{
    Document::UP doc;

    f._b.startDocument("doc::10");
    f._b.startIndexField("iu").
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
    f._b.startIndexField("iau").
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
    f._b.startIndexField("iwu").
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
    doc = f._b.endDocument();
    f._inv.invertDocument(10, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);

    f._pushThreads.sync();

    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("iu");
        PostingIterator itr(f._fic.findFrozen("not", fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("iu");
        PostingIterator itr(f._fic.findFrozen("example", fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{9:2}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("iau");
        PostingIterator itr(f._fic.findFrozen("example", fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{9:2[e=0,l=9]}",
                   toString(tfmd.getIterator(), true, false));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("iwu");
        PostingIterator itr(f._fic.findFrozen("example", fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{9:2[e=0,w=4,l=9]}",
                   toString(tfmd.getIterator(), true, true));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        search::diskindex::IndexBuilder dib(f.getSchema());
        dib.setPrefix("urldump");
        TuneFileIndexing tuneFileIndexing;
        DummyFileHeaderContext fileHeaderContext;
        dib.open(11, f._fic.getNumUniqueWords(), tuneFileIndexing,
                 fileHeaderContext);
        f._fic.dump(dib);
        dib.close();
    }
}


class SingleFieldFixture
{
public:
    Schema _schema;
    SingleFieldFixture()
        : _schema()
    {
        _schema.addIndexField(Schema::IndexField("i", DataType::STRING));
    }
    const Schema & getSchema() const { return _schema; }
};

TEST_F("requireThatCjkIndexingIsWorking", FieldIndexFixture<SingleFieldFixture>)
{
    Document::UP doc;

    f._b.startDocument("doc::10");
    f._b.startIndexField("i").
        addStr("我就是那个").
        setAutoSpace(false).
        addStr("大灰狼").
        setAutoSpace(true).
        endField();
    doc = f._b.endDocument();
    f._inv.invertDocument(10, *doc);
    f._invertThreads.sync();
    myPushDocument(f._inv, f._fic);

    f._pushThreads.sync();

    TermFieldMatchData tfmd;
    TermFieldMatchDataArray matchData;
    matchData.add(&tfmd);
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("i");
        PostingIterator itr(f._fic.findFrozen("not", fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("i");
        PostingIterator itr(f._fic.findFrozen("我就"
                                    "是那个",
                                    fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{2:0}", toString(tfmd.getIterator()));
        EXPECT_TRUE(!itr.seek(25));
        EXPECT_TRUE(itr.isAtEnd());
    }
    {
        uint32_t fieldId = f.getSchema().getIndexFieldId("i");
        PostingIterator itr(f._fic.findFrozen("大灰"
                                    "狼",
                                    fieldId),
                            featureStoreRef(f._fic, fieldId),
                            fieldId, matchData);
        itr.initFullRange();
        EXPECT_EQUAL(10u, itr.getDocId());
        itr.unpack(10);
        EXPECT_EQUAL("{2:1}", toString(tfmd.getIterator()));
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
    EXPECT_EQUAL(word,
                 dict.getFieldIndex(fieldId)->getWordStore().getWord(wordRef));
    MyDrainRemoves(dict, fieldId).drain(docId);
}

TEST_F("require that insert tells which word ref that was inserted", Fixture)
{
    FieldIndexCollection d(f.getSchema());
    insertAndAssertTuple("a", 1, 11, d);
    insertAndAssertTuple("b", 1, 11, d);
    insertAndAssertTuple("a", 2, 11, d);

    insertAndAssertTuple("a", 1, 22, d);
    insertAndAssertTuple("b", 2, 22, d);
    insertAndAssertTuple("c", 2, 22, d);
}

struct RemoverFixture : public Fixture
{
    FieldIndexCollection  _fic;
    SequencedTaskExecutor _invertThreads;
    SequencedTaskExecutor _pushThreads;

    RemoverFixture()
        :
        Fixture(),
        _fic(getSchema()),
        _invertThreads(2),
        _pushThreads(2)
    {
    }
    void assertPostingLists(const vespalib::string &e1,
                            const vespalib::string &e2,
                            const vespalib::string &e3) {
        EXPECT_TRUE(assertPostingList(e1, _fic.find("a", 1)));
        EXPECT_TRUE(assertPostingList(e2, _fic.find("a", 2)));
        EXPECT_TRUE(assertPostingList(e3, _fic.find("b", 1)));
    }
    void remove(uint32_t docId) {
        DocumentInverter inv(getSchema(), _invertThreads, _pushThreads);
        myremove(docId, inv, _fic, _invertThreads);
        _pushThreads.sync();
        EXPECT_FALSE(_fic.getFieldIndex(0u)->getDocumentRemover().
                     getStore().get(docId).valid());
    }
};

TEST_F("require that document remover can remove several documents", RemoverFixture)
{
    WrapInserter(f._fic, 1).word("a").add(11).add(13).add(15).
        word("b").add(11).add(15).flush();
    WrapInserter(f._fic, 2).word("a").add(11).add(13).flush();
    f.assertPostingLists("[11,13,15]", "[11,13]", "[11,15]");

    f.remove(13);
    f.assertPostingLists("[11,15]", "[11]", "[11,15]");

    f.remove(11);
    f.assertPostingLists("[15]", "[]", "[15]");

    f.remove(15);
    f.assertPostingLists("[]", "[]", "[]");
}

TEST_F("require that removal of non-existing document does not do anything", RemoverFixture)
{
    WrapInserter(f._fic, 1).word("a").add(11).word("b").add(11).flush();
    WrapInserter(f._fic, 2).word("a").add(11).flush();
    f.assertPostingLists("[11]", "[11]", "[11]");
    f.remove(13);
    f.assertPostingLists("[11]", "[11]", "[11]");
}

} // namespace memoryindex
} // namespace search

TEST_MAIN() { TEST_RUN_ALL(); }
