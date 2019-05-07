// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search { class Rand48; }

namespace search::fakedata {

class FakeWord;

/**
 * Contains lists of fake words for 3 word classes categorized based on number of occurrences.
 */
class FakeWordSet {
public:
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;
    using Schema = index::Schema;

    enum {
        COMMON_WORD,
        MEDIUM_WORD,
        RARE_WORD,
        NUM_WORDCLASSES,
    };
    std::vector<std::vector<FakeWord *> > _words;
    Schema _schema;
    std::vector<PosOccFieldsParams> _fieldsParams;

    FakeWordSet();

    FakeWordSet(bool hasElements,
                bool hasElementWeights);

    ~FakeWordSet();

    void setupParams(bool hasElements,
                     bool hasElementWeights);

    void setupWords(search::Rand48 &rnd,
                    unsigned int numDocs,
                    unsigned int commonDocFreq,
                    unsigned int numWordsPerWordClass);

    void dropWords();

    int getNumWords();

    const PosOccFieldsParams& getFieldsParams() const {
        return _fieldsParams.back();
    }

    uint32_t getPackedIndex() const {
        return _fieldsParams.size() - 1;
    }

    const std::vector<PosOccFieldsParams>& getAllFieldsParams() const {
        return _fieldsParams;
    }

    const Schema& getSchema() const {
        return _schema;
    }

    void addDocIdBias(uint32_t docIdBias);
};

}

