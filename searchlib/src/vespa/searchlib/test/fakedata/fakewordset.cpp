// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakewordset.h"
#include "fakeword.h"
#include <vespa/vespalib/util/time.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".fakewordset");

namespace search::fakedata {

using FakeWordVector = FakeWordSet::FakeWordVector;
using index::PostingListParams;
using index::SchemaUtil;
using index::schema::CollectionType;
using index::schema::DataType;

namespace {

void
applyDocIdBiasToVector(FakeWordVector& words, uint32_t docIdBias)
{
    for (auto& word : words) {
        word->addDocIdBias(docIdBias);
    }
}

}

FakeWordSet::FakeWordSet()
    : _words(NUM_WORDCLASSES),
      _schema(),
      _fieldsParams(),
      _numDocs(0)
{
    setupParams(false, false);
}

FakeWordSet::FakeWordSet(bool hasElements,
                         bool hasElementWeights)
    : _words(NUM_WORDCLASSES),
      _schema(),
      _fieldsParams(),
      _numDocs(0)
{
    setupParams(hasElements, hasElementWeights);
}

FakeWordSet::~FakeWordSet() = default;

void
FakeWordSet::setupParams(bool hasElements,
                         bool hasElementWeights)
{
    _schema.clear();

    assert(hasElements || !hasElementWeights);
    Schema::CollectionType collectionType(CollectionType::SINGLE);
    if (hasElements) {
        if (hasElementWeights) {
            collectionType = CollectionType::WEIGHTEDSET;
        } else {
            collectionType = CollectionType::ARRAY;
        }
    }
    Schema::IndexField indexField("field0", DataType::STRING, collectionType);
    indexField.setAvgElemLen(512u);
    _schema.addIndexField(indexField);
    _fieldsParams.resize(_schema.getNumIndexFields());
    SchemaUtil::IndexIterator it(_schema);
    for(; it.isValid(); ++it) {
        _fieldsParams[it.getIndex()].
            setSchemaParams(_schema, it.getIndex());
    }
}

void
FakeWordSet::setupWords(vespalib::Rand48 &rnd,
                        uint32_t numDocs,
                        uint32_t commonDocFreq,
                        uint32_t numWordsPerWordClass)
{
    setupWords(rnd, numDocs, commonDocFreq, 1000, 10, numWordsPerWordClass);
}

void
FakeWordSet::setupWords(vespalib::Rand48 &rnd,
                        uint32_t numDocs,
                        uint32_t commonDocFreq,
                        uint32_t mediumDocFreq,
                        uint32_t rareDocFreq,
                        uint32_t numWordsPerWordClass)
{
    std::string common = "common";
    std::string medium = "medium";
    std::string rare = "rare";
    _numDocs = numDocs;

    LOG(info, "enter setupWords");
    vespalib::Timer tv;

    uint32_t packedIndex = _fieldsParams.size() - 1;
    for (uint32_t i = 0; i < numWordsPerWordClass; ++i) {
        std::ostringstream vi;

        vi << (i + 1);
        _words[COMMON_WORD].push_back(std::make_unique<FakeWord>(numDocs, commonDocFreq, commonDocFreq / 2,
                                                                 common + vi.str(), rnd,
                                                                 _fieldsParams[packedIndex],
                                                                 packedIndex));

        _words[MEDIUM_WORD].push_back(std::make_unique<FakeWord>(numDocs, mediumDocFreq, mediumDocFreq / 2,
                                                                 medium + vi.str(), rnd,
                                                                 _fieldsParams[packedIndex],
                                                                 packedIndex));

        _words[RARE_WORD].push_back(std::make_unique<FakeWord>(numDocs, rareDocFreq, rareDocFreq / 2,
                                                               rare + vi.str(), rnd,
                                                               _fieldsParams[packedIndex],
                                                               packedIndex));
    }

    LOG(info, "leave setupWords, elapsed %10.6f s", vespalib::to_s(tv.elapsed()));
}

int
FakeWordSet::getNumWords() const
{
    int ret = 0;
    for (const auto& words : _words) {
        ret += words.size();
    }
    return ret;
}

void
FakeWordSet::addDocIdBias(uint32_t docIdBias)
{
    for (auto& words : _words) {
        applyDocIdBiasToVector(words, docIdBias);
    }
}

}
