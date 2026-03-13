// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/ordered_field_index_inserter.h>
#include <vespa/searchlib/memoryindex/posting_iterator.h>
#include <vespa/searchlib/queryeval/iterators.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/searchlib/test/memoryindex/wrap_inserter.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <unordered_set>

#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("field_index_test");

using namespace vespalib::btree;
using namespace vespalib::datastore;

namespace search {

using namespace fef;
using namespace index;

using document::ArrayFieldValue;
using document::DataType;
using document::Document;
using document::StringFieldValue;
using document::WeightedSetFieldValue;
using queryeval::RankedSearchIteratorBase;
using queryeval::SearchIterator;
using search::index::test::MockFieldLengthInspector;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using vespalib::GenerationHandler;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;


namespace memoryindex {

using test::WrapInserter;
using NormalFieldIndex = FieldIndex<false>;

class MyBuilder : public IndexBuilder {
private:
    std::stringstream _ss;
    bool              _firstField;

    class FieldIndexBuilder : public index::FieldIndexBuilder {
    public:
        explicit FieldIndexBuilder(std::stringstream & ss)
            : _ss(ss),
              _insideWord(false),
              _firstWord(true),
              _firstDoc(true)
        {}
        ~FieldIndexBuilder() override {
            assert(!_insideWord);
            _ss << "]";
        }
        void startWord(std::string_view word) override {
            assert(!_insideWord);
            if (!_firstWord)
                _ss << ",";
            _ss << "w=" << word << "[";
            _firstDoc = true;
            _insideWord = true;
        }

        void endWord() override {
            assert(_insideWord);
            _ss << "]";
            _firstWord = false;
            _insideWord = false;
        }
        void add_document(const DocIdAndFeatures &features) override {
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
    private:
        std::stringstream & _ss;
        bool                _insideWord;
        bool                _firstWord;
        bool                _firstDoc;
    };
public:
    explicit MyBuilder(const Schema &schema);
    ~MyBuilder() override;

    std::unique_ptr<index::FieldIndexBuilder>
    startField(uint32_t fieldId) override {
        if (!_firstField) _ss << ",";
        _ss << "f=" << fieldId << "[";
        _firstField = false;
        return std::make_unique<FieldIndexBuilder>(_ss);
    }

    std::string toStr() const {
        return _ss.str();
    }
};

MyBuilder::MyBuilder(const Schema &schema)
    : IndexBuilder(schema),
      _ss(),
      _firstField(true)
{}
MyBuilder::~MyBuilder() = default;

struct SimpleMatchData {
    TermFieldMatchData term;
    TermFieldMatchDataArray array;
    SimpleMatchData() : term(), array() {
        array.add(&term);
    }
    ~SimpleMatchData();
};

SimpleMatchData::~SimpleMatchData() = default;

std::string
toString(const SimpleMatchData& match_data,
         bool hasElements = false,
         bool hasWeights = false)
{
    auto posItr = match_data.term.getIterator();
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

template <typename PostingIteratorType>
bool
assertPostingList(const std::string &exp,
                  PostingIteratorType itr,
                  const FeatureStore *store = nullptr)
{
    std::stringstream ss;
    FeatureStore::DecodeContextCooked decoder(nullptr);
    SimpleMatchData match_data;
    ss << "[";
    for (size_t i = 0; itr.valid(); ++itr, ++i) {
        if (i > 0) ss << ",";
        uint32_t docId = itr.getKey();
        ss << docId;
        if (store != nullptr) { // consider features as well
            EntryRef ref(itr.getData().get_features());
            store->setupForField(0, decoder);
            store->setupForUnpackFeatures(ref, decoder);
            decoder.unpackFeatures(match_data.array, docId);
            ss << toString(match_data);
        }
    }
    ss << "]";
    bool result = (exp == ss.str());
    EXPECT_EQ(exp, ss.str());
    return result;
}

template <typename PostingIteratorType>
bool
assertPostingList(std::vector<uint32_t> &exp, PostingIteratorType itr)
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

template <bool interleaved_features>
typename FieldIndex<interleaved_features>::PostingList::Iterator
find_in_field_index(const std::string_view word,
                    uint32_t field_id,
                    const FieldIndexCollection& fic)
{
    using FieldIndexType = FieldIndex<interleaved_features>;
    auto* field_index = dynamic_cast<FieldIndexType*>(fic.getFieldIndex(field_id));
    assert(field_index != nullptr);
    return field_index->find(word);
}

template <bool interleaved_features>
typename FieldIndex<interleaved_features>::PostingList::ConstIterator
find_frozen_in_field_index(const std::string_view word,
                           uint32_t field_id,
                           const FieldIndexCollection& fic)
{
    using FieldIndexType = FieldIndex<interleaved_features>;
    auto* field_index = dynamic_cast<FieldIndexType*>(fic.getFieldIndex(field_id));
    assert(field_index != nullptr);
    return field_index->findFrozen(word);
}

namespace {

/**
 * A simple mockup of a memory field index, used to verify
 * that we get correct posting lists from real memory field index.
 */
class MockFieldIndex {
    std::map<std::pair<std::string, uint32_t>, std::set<uint32_t>> _dict;
    std::string _word;
    uint32_t _fieldId;

public:
    MockFieldIndex();
    ~MockFieldIndex();
    void
    setNextWord(const std::string &word) {
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

    std::vector<uint32_t> find(const std::string &word, uint32_t fieldId) {
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

MockFieldIndex::MockFieldIndex()
    : _dict(),
      _word(),
      _fieldId()
{}

MockFieldIndex::~MockFieldIndex() = default;

/**
 * MockWordStoreScan is a helper class to ensure that previous words are
 * still stored safely in memory, to satisfy OrderedFieldIndexInserter
 * needs.
 */
class MockWordStoreScan {
    std::unordered_set<std::string, vespalib::hash<std::string>> _words;

public:
    MockWordStoreScan()
        : _words()
    { }
    ~MockWordStoreScan();

    const std::string &setWord(const std::string &word) {
        return *_words.insert(word).first;
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
    explicit MyInserter(const Schema &schema)
        : _wordStoreScan(),
          _mock(),
          _fieldIndexes(schema, MockFieldLengthInspector()),
          _features(),
          _inserter(nullptr)
    {
        _features.addNextOcc(0, 0, 1, 1);
    }
    ~MyInserter();

    void setNextWord(const std::string &word) {
        const std::string &w = _wordStoreScan.setWord(word);
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

    bool assertPosting(const std::string &word,
                       uint32_t fieldId) {
        std::vector<uint32_t> exp = _mock.find(word, fieldId);
        auto itr = find_in_field_index<false>(word, fieldId, _fieldIndexes);
        bool result = assertPostingList(exp, itr);
        EXPECT_TRUE(result);
        return result;
    }

    bool assertPostings() {
        if (_inserter != nullptr) {
            _inserter->flush();
        }
        for (const auto& wfp : _mock) {
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
myremove(uint32_t docId, DocumentInverter &inv)
{
    inv.removeDocument(docId);
    vespalib::Gate gate;
    inv.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

class MyDrainRemoves : IFieldIndexRemoveListener {
    FieldIndexRemover &_remover;
public:
    void remove(const std::string_view, uint32_t) override { }

    MyDrainRemoves(FieldIndexCollection &fieldIndexes, uint32_t fieldId)
        : _remover(fieldIndexes.getFieldIndex(fieldId)->getDocumentRemover())
    {
    }

    explicit MyDrainRemoves(IFieldIndex& field_index)
        : _remover(field_index.getDocumentRemover())
    {
    }

    void drain(uint32_t docId) {
        _remover.remove(docId, *this);
    }
};

void
myPushDocument(DocumentInverter &inv)
{
    vespalib::Gate gate;
    inv.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
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

MemoryStats
getFeatureStoreMemStats(const FieldIndexCollection &fieldIndexes)
{
    MemoryStats res;
    uint32_t numFields = fieldIndexes.getNumFields();
    for (uint32_t fieldId = 0; fieldId < numFields; ++fieldId) {
        auto stats = fieldIndexes.getFieldIndex(fieldId)->getFeatureStore().getMemStats();
        res += stats;
    }
    return res;
}

void
myCommit(FieldIndexCollection &fieldIndexes, ISequencedTaskExecutor &pushThreads)
{
    vespalib::Gate gate;
    {
        auto gate_callback = std::make_shared<vespalib::GateCallback>(gate);
        uint32_t fieldId = 0;
        for (auto &fieldIndex : fieldIndexes.getFieldIndexes()) {
            pushThreads.execute(fieldId,
                                [fieldIndex(fieldIndex.get()), gate_callback]()
                                {
                                    (void) gate_callback;
                                    fieldIndex->commit();
                                });
            ++fieldId;
        }
    }
    gate.await();
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
make_all_index_schema(DocBuilder::AddFieldsType add_fields)
{
    DocBuilder db(std::move(add_fields));
    return SchemaBuilder(db).add_all_indexes().build();
}

DocBuilder::AddFieldsType
make_single_add_fields()
{
    return [](auto& builder, auto& doc) noexcept { doc.addField("f0", builder.stringTypeRef()); };
}

template <typename FieldIndexType>
struct FieldIndexTest : public ::testing::Test {
    Schema schema;
    FieldIndexType idx;
    FieldIndexTest()
        : schema(make_all_index_schema(make_single_add_fields())),
          idx(schema, 0)
    {
    }
    ~FieldIndexTest() override;
    SearchIterator::UP search(const std::string_view word,
                              const SimpleMatchData& match_data) {
        return make_search_iterator<FieldIndexType::has_interleaved_features>(idx.find(word), idx.getFeatureStore(), 0, match_data.array);
    }
};

template <typename FieldIndexType>
FieldIndexTest<FieldIndexType>::~FieldIndexTest() = default;


using FieldIndexTestTypes = ::testing::Types<FieldIndex<false>, FieldIndex<true>>;
TYPED_TEST_SUITE(FieldIndexTest, FieldIndexTestTypes);

TYPED_TEST(FieldIndexTest, require_that_fresh_insert_works)
{
    EXPECT_TRUE(assertPostingList("[]", this->idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", this->idx.findFrozen("a")));
    EXPECT_EQ(0u, this->idx.getNumUniqueWords());
    WrapInserter(this->idx).word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", this->idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", this->idx.findFrozen("a")));
    this->idx.commit();
    EXPECT_TRUE(assertPostingList("[10]", this->idx.findFrozen("a")));
    EXPECT_EQ(1u, this->idx.getNumUniqueWords());
}

TYPED_TEST(FieldIndexTest, require_that_append_insert_works)
{
    WrapInserter(this->idx).word("a").add(10).flush().rewind().
            word("a").add(5).flush();
    EXPECT_TRUE(assertPostingList("[5,10]", this->idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", this->idx.findFrozen("a")));
    WrapInserter(this->idx).rewind().word("a").add(20).flush();
    EXPECT_TRUE(assertPostingList("[5,10,20]", this->idx.find("a")));
    EXPECT_TRUE(assertPostingList("[]", this->idx.findFrozen("a")));
    this->idx.commit();
    EXPECT_TRUE(assertPostingList("[5,10,20]", this->idx.findFrozen("a")));
}

TYPED_TEST(FieldIndexTest, require_that_remove_works)
{
    WrapInserter(this->idx).word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[]", this->idx.find("a")));
    WrapInserter(this->idx).add(10).add(20).add(30).flush();
    EXPECT_TRUE(assertPostingList("[10,20,30]", this->idx.find("a")));
    WrapInserter(this->idx).rewind().word("a").remove(10).flush();
    EXPECT_TRUE(assertPostingList("[20,30]", this->idx.find("a")));
    WrapInserter(this->idx).remove(20).flush();
    EXPECT_TRUE(assertPostingList("[30]", this->idx.find("a")));
    WrapInserter(this->idx).remove(30).flush();
    EXPECT_TRUE(assertPostingList("[]", this->idx.find("a")));
    EXPECT_EQ(1u, this->idx.getNumUniqueWords());
    MyDrainRemoves(this->idx).drain(10);
    WrapInserter(this->idx).rewind().word("a").add(10).flush();
    EXPECT_TRUE(assertPostingList("[10]", this->idx.find("a")));
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
    f.set_num_occs(numOccs);
    f.set_field_length(elemLen);
    return f;
}

TYPED_TEST(FieldIndexTest, require_that_posting_iterator_is_working)
{
    WrapInserter(this->idx).word("a").add(10, getFeatures(4, 1)).
        add(20, getFeatures(5, 2)).
        add(30, getFeatures(6, 1)).
        add(40, getFeatures(7, 2)).flush();
    SimpleMatchData match_data;
    {
        auto itr = this->search("not", match_data);
        itr->initFullRange();
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = this->search("a", match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{4:0}", toString(match_data));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_EQ(30u, itr->getDocId());
        itr->unpack(30);
        EXPECT_EQ("{6:0}", toString(match_data));
        EXPECT_TRUE(itr->seek(40));
        EXPECT_EQ(40u, itr->getDocId());
        itr->unpack(40);
        EXPECT_EQ("{7:0,1}", toString(match_data));
        EXPECT_TRUE(!itr->seek(41));
        EXPECT_TRUE(itr->isAtEnd());
    }
}

struct FieldIndexInterleavedFeaturesTest : public FieldIndexTest<FieldIndex<true>> {
    SimpleMatchData match_data;
    FieldIndexInterleavedFeaturesTest()
        : FieldIndexTest<FieldIndex<true>>()
    {
        WrapInserter(idx).word("a").add(10, getFeatures(5, 2)).flush();
    }
    void
    expect_features_unpacked(const std::string& exp_field_positions,
                             uint32_t exp_num_occs,
                             uint32_t exp_field_length) {
        auto itr = search("a", match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ(exp_field_positions, toString(match_data));
        EXPECT_EQ(exp_num_occs, match_data.term.getNumOccs());
        EXPECT_EQ(exp_field_length, match_data.term.getFieldLength());
        EXPECT_TRUE(match_data.term.has_ranking_data(10));
        auto& ranked_itr = dynamic_cast<RankedSearchIteratorBase&>(*itr);
        EXPECT_TRUE(ranked_itr.getUnpacked());
        EXPECT_TRUE(!itr->seek(11));
        EXPECT_TRUE(itr->isAtEnd());
    }
};

TEST_F(FieldIndexInterleavedFeaturesTest, only_normal_features_are_unpacked)
{
    match_data.term.setNeedNormalFeatures(true);
    match_data.term.setNeedInterleavedFeatures(false);
    expect_features_unpacked("{5:0,1}", 0, 0);
}

TEST_F(FieldIndexInterleavedFeaturesTest, only_interleaved_features_are_unpacked)
{
    match_data.term.setNeedNormalFeatures(false);
    match_data.term.setNeedInterleavedFeatures(true);
    expect_features_unpacked("{1000000:}", 2, 5);
}

TEST_F(FieldIndexInterleavedFeaturesTest, both_normal_and_interleaved_features_are_unpacked)
{
    match_data.term.setNeedNormalFeatures(true);
    match_data.term.setNeedInterleavedFeatures(true);
    expect_features_unpacked("{5:0,1}", 2, 5);
}

TEST_F(FieldIndexInterleavedFeaturesTest, no_features_are_unpacked)
{
    match_data.term.setNeedNormalFeatures(false);
    match_data.term.setNeedInterleavedFeatures(false);
    expect_features_unpacked("{1000000:}", 0, 0);
}

TEST_F(FieldIndexInterleavedFeaturesTest, interleaved_features_are_capped)
{
    FeatureStore::DecodeContextCooked decoder(nullptr);
    WrapInserter(idx).word("b").add(11, getFeatures(66001, 66000)).flush();
    auto itr = this->idx.find("b");
    EXPECT_EQ(11, itr.getKey());
    auto &entry = itr.getData();
    EXPECT_EQ(std::numeric_limits<uint16_t>::max(), entry.get_num_occs());
    EXPECT_EQ(std::numeric_limits<uint16_t>::max(), entry.get_field_length());
}

DocBuilder::AddFieldsType
make_multi_field_add_fields()
{
    return [](auto& builder, auto& doc) noexcept { using namespace document::new_config_builder;
        auto string_array = doc.createArray(builder.stringTypeRef()).ref();
        auto string_wset = doc.createWset(builder.stringTypeRef()).ref();
        doc.addField("f0", builder.stringTypeRef())
            .addField("f1", builder.stringTypeRef())
            .addField("f2", string_array)
            .addField("f3", string_wset);
           };
}

struct FieldIndexCollectionTest : public ::testing::Test {
    Schema schema;
    FieldIndexCollection fic;
    FieldIndexCollectionTest();
    ~FieldIndexCollectionTest() override;

    [[nodiscard]]NormalFieldIndex::PostingList::Iterator
    find(const std::string_view word, uint32_t field_id) const {
        return find_in_field_index<false>(word, field_id, fic);
    }
};

FieldIndexCollectionTest::FieldIndexCollectionTest()
    : schema(make_all_index_schema(make_multi_field_add_fields())),
      fic(schema, MockFieldLengthInspector())
{
}
FieldIndexCollectionTest::~FieldIndexCollectionTest() = default;

TEST_F(FieldIndexCollectionTest, require_that_multiple_posting_lists_across_multiple_fields_can_exist)
{
    WrapInserter(fic, 0).word("a").add(10).word("b").add(11).add(15).flush();
    WrapInserter(fic, 1).word("a").add(5).word("b").add(12).flush();
    EXPECT_EQ(4u, fic.getNumUniqueWords());
    EXPECT_TRUE(assertPostingList("[10]", find("a", 0)));
    EXPECT_TRUE(assertPostingList("[5]", find("a", 1)));
    EXPECT_TRUE(assertPostingList("[11,15]", find("b", 0)));
    EXPECT_TRUE(assertPostingList("[12]", find("b", 1)));
    EXPECT_TRUE(assertPostingList("[]", find("a", 2)));
    EXPECT_TRUE(assertPostingList("[]", find("c", 0)));
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
        }
    }
    EXPECT_TRUE(inserter.assertPostings());
    EXPECT_EQ(('z' - 'a' +1u) * numFields, inserter.getNumUniqueWords());
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

TEST_F(FieldIndexCollectionTest, require_that_features_are_in_posting_lists)
{
    WrapInserter(fic, 0).word("a").add(1, getFeatures(4, 2)).flush();
    EXPECT_TRUE(assertPostingList("[1{4:0,1}]",
                                  find("a", 0),
                                  featureStorePtr(fic, 0)));
    WrapInserter(fic, 0).word("b").add(2, getFeatures(5, 1)).
        add(3, getFeatures(6, 2)).flush();
    EXPECT_TRUE(assertPostingList("[2{5:0},3{6:0,1}]",
                                  find("b", 0),
                                  featureStorePtr(fic, 0)));
    WrapInserter(fic, 1).word("c").add(4, getFeatures(7, 2)).flush();
    EXPECT_TRUE(assertPostingList("[4{7:0,1}]",
                                  find("c", 1),
                                  featureStorePtr(fic, 1)));
}

TEST_F(FieldIndexCollectionTest, require_that_basic_dumping_to_index_builder_is_working)
{
    MyBuilder b(schema);
    {
        WordDocElementWordPosFeatures wpf;
        auto fb = b.startField(4);
        fb->startWord("a");
        DocIdAndFeatures features;
        features.set_doc_id(2);
        features.elements().emplace_back(0, 10, 20);
        features.elements().back().setNumOccs(2);
        features.word_positions().emplace_back(1);
        features.word_positions().emplace_back(3);
        fb->add_document(features);
        fb->endWord();
    }
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
        TuneFileIndexing tuneFileIndexing;
        DummyFileHeaderContext fileHeaderContext;
        MockFieldLengthInspector fieldLengthInspector;
        search::diskindex::IndexBuilder b(schema, "dump", 5, 2, fieldLengthInspector,
                                          tuneFileIndexing, fileHeaderContext);
        fic.dump(b);
    }
}


struct FieldIndexCollectionTypeTest : public ::testing::Test {
    Schema schema;
    FieldIndexCollection fic;
    FieldIndexCollectionTypeTest()
        : schema(make_schema()),
          fic(schema, MockFieldLengthInspector())
    {
    }
    static Schema make_schema() {
        Schema result;
        using DataType = search::index::schema::DataType;
        result.addIndexField(Schema::IndexField("normal", DataType::STRING));
        Schema::IndexField interleaved("interleaved", DataType::STRING);
        interleaved.set_interleaved_features(true);
        result.addIndexField(interleaved);
        return result;
    }
};

template <typename FieldIndexType>
void
expect_field_index_type(const IFieldIndex* field_index)
{
    auto* other_type = dynamic_cast<const FieldIndexType*>(field_index);
    EXPECT_TRUE(other_type != nullptr);
}

TEST_F(FieldIndexCollectionTypeTest, instantiates_field_index_type_based_on_schema_config)
{
    expect_field_index_type<FieldIndex<false>>(fic.getFieldIndex(0));
    expect_field_index_type<FieldIndex<true>>(fic.getFieldIndex(1));
}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

class InverterTest : public ::testing::Test {
public:
    DocBuilder _b;
    Schema _schema;
    FieldIndexCollection _fic;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    DocumentInverterContext _inv_context;
    DocumentInverter _inv;

    explicit InverterTest(DocBuilder::AddFieldsType add_fields)
        : _b(std::move(add_fields)),
          _schema(SchemaBuilder(_b).add_all_indexes().build()),
          _fic(_schema, MockFieldLengthInspector()),
          _invertThreads(SequencedTaskExecutor::create(invert_executor, 2)),
          _pushThreads(SequencedTaskExecutor::create(push_executor, 2)),
          _inv_context(_schema, *_invertThreads, *_pushThreads, _fic),
          _inv(_inv_context)
    {
    }
    [[nodiscard]] NormalFieldIndex::PostingList::Iterator find(const std::string_view word, uint32_t field_id) const {
        return find_in_field_index<false>(word, field_id, _fic);
    }
    [[nodiscard]] NormalFieldIndex::PostingList::ConstIterator findFrozen(const std::string_view word, uint32_t field_id) const {
        return find_frozen_in_field_index<false>(word, field_id, _fic);
    }
    [[nodiscard]] SearchIterator::UP
    search(const std::string_view word, uint32_t field_id,const SimpleMatchData& match_data) const {
        return make_search_iterator<false>(findFrozen(word, field_id), featureStoreRef(_fic, field_id),
                                           field_id, match_data.array);
    }
};

class BasicInverterTest : public InverterTest {
public:
    BasicInverterTest() : InverterTest(make_multi_field_add_fields()) {}
};

TEST_F(BasicInverterTest, require_that_inversion_is_working)
{
    Document::UP doc;
    StringFieldBuilder sfb(_b);

    doc = _b.make_document("id:ns:searchdocument::10");
    doc->setValue("f0", sfb.tokenize("a b c d").build());
    _inv.invertDocument(10, *doc, {});
    myPushDocument(_inv);

    doc = _b.make_document("id:ns:searchdocument::20");
    doc->setValue("f0", sfb.tokenize("a a b c d").build());
    _inv.invertDocument(20, *doc, {});
    myPushDocument(_inv);

    doc = _b.make_document("id:ns:searchdocument::30");
    doc->setValue("f0", sfb.tokenize("a b c d e f").build());
    doc->setValue("f1", sfb.word("\nw2").tokenize(" w x ").
                  word("\nw3").tokenize(" y z").build());
    {
        auto string_array = _b.make_array("f2");
        string_array.add(sfb.tokenize("w x").build());
        string_array.add(sfb.tokenize("y z").build());
        doc->setValue("f2", string_array);
    }
    {
        auto string_wset = _b.make_wset("f3");
        string_wset.add(sfb.tokenize("w x").build(), 6);
        string_wset.add(sfb.tokenize("y z").build(), 7);
        doc->setValue("f3", string_wset);
    }
    _inv.invertDocument(30, *doc, {});
    myPushDocument(_inv);

    doc = _b.make_document("id:ns:searchdocument::40");
    doc->setValue("f0", sfb.tokenize("a a b c a e f").build());
    _inv.invertDocument(40, *doc, {});
    myPushDocument(_inv);

    doc = _b.make_document("id:ns:searchdocument::999");
    doc->setValue("f0", sfb.tokenize("this is ").word("_a_").
                  tokenize(" test for insertion speed with more than just ").
                  word("__a__").tokenize(" few words present in some of the fields").build());
    doc->setValue("f1", sfb.tokenize("the other field also has some content").build());
    {
        auto string_array = _b.make_array("f2");
        string_array.add(sfb.tokenize("strange things here has some content").build());
        doc->setValue("f2", string_array);
    }
    {
        auto string_wset = _b.make_wset("f3");
        string_wset.add(sfb.tokenize("not a weighty argument").build(), 3);
        doc->setValue("f3", string_wset);
    }
    for (uint32_t docId = 10000; docId < 20000; ++docId) {
        _inv.invertDocument(docId, *doc, {});
        myPushDocument(_inv);
    }

    auto beforeStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "Before feature compaction: alloc_entries=%zu, used_entries=%zu"
        ", dead_entries=%zu, hold_entries=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        beforeStats._alloc_entries,
        beforeStats._used_entries,
        beforeStats._dead_entries,
        beforeStats._hold_entries,
        beforeStats._freeBuffers,
        beforeStats._activeBuffers,
        beforeStats._holdBuffers);
    myCompactFeatures(_fic, *_pushThreads);
    std::vector<std::unique_ptr<GenerationHandler::Guard>> guards;
    for (auto &fieldIndex : _fic.getFieldIndexes()) {
        guards.push_back(std::make_unique<GenerationHandler::Guard>
                         (fieldIndex->takeGenerationGuard()));
    }
    myCommit(_fic, *_pushThreads);
    auto duringStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "During feature compaction: alloc_entries=%zu, used_entries=%zu"
        ", dead_entries=%zu, hold_entries=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        duringStats._alloc_entries,
        duringStats._used_entries,
        duringStats._dead_entries,
        duringStats._hold_entries,
        duringStats._freeBuffers,
        duringStats._activeBuffers,
        duringStats._holdBuffers);
    guards.clear();
    myCommit(_fic, *_pushThreads);
    auto afterStats = getFeatureStoreMemStats(_fic);
    LOG(info,
        "After feature compaction: alloc_entries=%zu, used_entries=%zu"
        ", dead_entries=%zu, hold_entries=%zu"
        ", freeBuffers=%u, activeBuffers=%u"
        ", holdBuffers=%u",
        afterStats._alloc_entries,
        afterStats._used_entries,
        afterStats._dead_entries,
        afterStats._hold_entries,
        afterStats._freeBuffers,
        afterStats._activeBuffers,
        afterStats._holdBuffers);

    SimpleMatchData match_data;
    {
        auto itr = search("not", 0, match_data);
        itr->initFullRange();
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = search("a", 0, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{4:0}", toString(match_data));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_EQ(30u, itr->getDocId());
        itr->unpack(30);
        EXPECT_EQ("{6:0}", toString(match_data));
        EXPECT_TRUE(itr->seek(40));
        EXPECT_EQ(40u, itr->getDocId());
        itr->unpack(40);
        EXPECT_EQ("{7:0,1,4}", toString(match_data));
        EXPECT_TRUE(!itr->seek(41));
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = search("x", 0, match_data);
        itr->initFullRange();
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = search("x", 1, match_data);
        itr->initFullRange();
        EXPECT_EQ(30u, itr->getDocId());
        itr->unpack(30);
        EXPECT_EQ("{6:2[e=0,w=1,l=6]}", toString(match_data, true, true));
    }
    {
        auto itr = search("x", 2, match_data);
        itr->initFullRange();
        EXPECT_EQ(30u, itr->getDocId());
        itr->unpack(30);
        // weight is hardcoded to 1 for new style il doc array field
        EXPECT_EQ("{2:1[e=0,w=1,l=2]}", toString(match_data, true, true));
    }
    {
        auto itr = search("x", 3, match_data);
        itr->initFullRange();
        EXPECT_EQ(30u, itr->getDocId());
        itr->unpack(30);
        EXPECT_EQ("{2:1[e=0,w=6,l=2]}",
                  toString(match_data, true, true));
    }
}

TEST_F(BasicInverterTest, require_that_inverter_handles_remove_via_document_remover)
{
    StringFieldBuilder sfb(_b);

    auto doc1 = _b.make_document("id:ns:searchdocument::1");
    doc1->setValue("f0", sfb.tokenize("a b").build());
    doc1->setValue("f1", sfb.tokenize("a c").build());
    _inv.invertDocument(1, *doc1, {});
    myPushDocument(_inv);

    auto doc2 = _b.make_document("id:ns:searchdocument::2");
    doc2->setValue("f0", sfb.tokenize("b c").build());
    _inv.invertDocument(2, *doc2, {});
    myPushDocument(_inv);

    EXPECT_TRUE(assertPostingList("[1]", find("a", 0)));
    EXPECT_TRUE(assertPostingList("[1,2]", find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", find("c", 0)));
    EXPECT_TRUE(assertPostingList("[1]", find("a", 1)));
    EXPECT_TRUE(assertPostingList("[1]", find("c", 1)));

    myremove(1, _inv);

    EXPECT_TRUE(assertPostingList("[]", find("a", 0)));
    EXPECT_TRUE(assertPostingList("[2]", find("b", 0)));
    EXPECT_TRUE(assertPostingList("[2]", find("c", 0)));
    EXPECT_TRUE(assertPostingList("[]", find("a", 1)));
    EXPECT_TRUE(assertPostingList("[]", find("c", 1)));
}

DocBuilder::AddFieldsType
make_uri_add_fields()
{
    return [](auto& builder, auto& doc) noexcept { using namespace document::new_config_builder;
        auto uri_array = doc.createArray(builder.uriTypeRef()).ref();
        auto uri_wset = doc.createWset(builder.uriTypeRef()).ref();
        doc.addField("iu", builder.uriTypeRef())
            .addField("iau", uri_array)
            .addField("iwu", uri_wset);
           };
}

class UriInverterTest : public InverterTest {
public:
    UriInverterTest() : InverterTest(make_uri_add_fields()) {}
};

TEST_F(UriInverterTest, require_that_uri_indexing_is_working)
{
    Document::UP doc;
    StringFieldBuilder sfb(_b);

    doc = _b.make_document("id:ns:searchdocument::10");
    doc->setValue("iu", StringFieldValue("http://www.example.com:81/fluke?ab=2#4"));
    auto url_array = _b.make_array("iau");
    url_array.add(StringFieldValue("http://www.example.com:82/fluke?ab=2#8"));
    url_array.add(StringFieldValue("http://www.flickr.com:82/fluke?ab=2#9"));
    doc->setValue("iau", url_array);
    auto url_wset = _b.make_wset("iwu");
    url_wset.add(StringFieldValue("http://www.example.com:83/fluke?ab=2#12"), 4);
    url_wset.add(StringFieldValue("http://www.flickr.com:85/fluke?ab=2#13"), 7);
    doc->setValue("iwu", url_wset);
    _inv.invertDocument(10, *doc, {});
    myPushDocument(_inv);

    SimpleMatchData match_data;
    {
        uint32_t fieldId = _schema.getIndexFieldId("iu");
        auto itr = search("not", fieldId, match_data);
        itr->initFullRange();
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iu");
        auto itr = search("example", fieldId, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{9:2}", toString(match_data));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iau");
        auto itr = search("example", fieldId, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{9:2[e=0,l=9]}",
                  toString(match_data, true, false));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        uint32_t fieldId = _schema.getIndexFieldId("iwu");
        auto itr = search("example", fieldId, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{9:2[e=0,w=4,l=9]}",
                  toString(match_data, true, true));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        TuneFileIndexing tuneFileIndexing;
        DummyFileHeaderContext fileHeaderContext;
        MockFieldLengthInspector fieldLengthInspector;
        search::diskindex::IndexBuilder dib(_schema, "urldump", 11, _fic.getNumUniqueWords(),
                                            fieldLengthInspector, tuneFileIndexing, fileHeaderContext);
        _fic.dump(dib);
    }
}

class CjkInverterTest : public InverterTest {
public:
    CjkInverterTest() : InverterTest(make_single_add_fields()) {}
};

TEST_F(CjkInverterTest, require_that_cjk_indexing_is_working)
{
    Document::UP doc;
    StringFieldBuilder sfb(_b);

    doc = _b.make_document("id:ns:searchdocument::10");
    doc->setValue("f0", sfb.word("我就是那个").word("大灰狼").build());
    _inv.invertDocument(10, *doc, {});
    myPushDocument(_inv);

    SimpleMatchData match_data;
    uint32_t fieldId = _schema.getIndexFieldId("f0");
    {
        auto itr = search("not", fieldId, match_data);
        itr->initFullRange();
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = search("我就"
                          "是那个",
                          fieldId, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{2:0}", toString(match_data));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_TRUE(itr->isAtEnd());
    }
    {
        auto itr = search("大灰"
                          "狼",
                          fieldId, match_data);
        itr->initFullRange();
        EXPECT_EQ(10u, itr->getDocId());
        itr->unpack(10);
        EXPECT_EQ("{2:1}", toString(match_data));
        EXPECT_TRUE(!itr->seek(25));
        EXPECT_TRUE(itr->isAtEnd());
    }
}

void
insertAndAssertTuple(const std::string &word, uint32_t fieldId, uint32_t docId,
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
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;

    RemoverTest()
        : FieldIndexCollectionTest(),
          _invertThreads(SequencedTaskExecutor::create(invert_executor, 2)),
          _pushThreads(SequencedTaskExecutor::create(push_executor, 2))
    {
    }
    void assertPostingLists(const std::string &e1,
                            const std::string &e2,
                            const std::string &e3) {
        EXPECT_TRUE(assertPostingList(e1, find("a", 1)));
        EXPECT_TRUE(assertPostingList(e2, find("a", 2)));
        EXPECT_TRUE(assertPostingList(e3, find("b", 1)));
    }
    void remove(uint32_t docId) {
        DocumentInverterContext inv_context(schema, *_invertThreads, *_pushThreads, fic);
        DocumentInverter inv(inv_context);
        myremove(docId, inv);
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
