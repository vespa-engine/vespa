// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/searchcommon/common/schema.h>


namespace vespalib { class Rand48; }

namespace search::fakedata {

class FakeWord;

/**
 * Contains lists of fake words for 3 word classes categorized based on number of occurrences.
 */
class FakeWordSet {
public:
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;
    using Schema = index::Schema;
    using FakeWordPtr = std::unique_ptr<FakeWord>;
    using FakeWordVector = std::vector<FakeWordPtr>;

    enum {
        COMMON_WORD,
        MEDIUM_WORD,
        RARE_WORD,
        NUM_WORDCLASSES,
    };

private:
    std::vector<FakeWordVector> _words;
    Schema _schema;
    std::vector<PosOccFieldsParams> _fieldsParams;
    uint32_t _numDocs;

public:
    FakeWordSet();

    FakeWordSet(bool hasElements,
                bool hasElementWeights);

    ~FakeWordSet();

    void setupParams(bool hasElements,
                     bool hasElementWeights);

    void setupWords(vespalib::Rand48 &rnd,
                    uint32_t numDocs,
                    uint32_t commonDocFreq,
                    uint32_t numWordsPerWordClass);

    void setupWords(vespalib::Rand48 &rnd,
                    uint32_t numDocs,
                    uint32_t commonDocFreq,
                    uint32_t mediumDocFreq,
                    uint32_t rareDocFreq,
                    uint32_t numWordsPerWordClass);

    const std::vector<FakeWordVector>& words() const { return _words; }

    int getNumWords() const;

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

    uint32_t numDocs() const { return _numDocs; }

    void addDocIdBias(uint32_t docIdBias);
};

}

