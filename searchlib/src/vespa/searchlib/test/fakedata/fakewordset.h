// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search
{
class Rand48;
}

namespace search
{

namespace fakedata
{

class FakeWord;

class FakeWordSet
{
public:
    typedef bitcompression::PosOccFieldsParams PosOccFieldsParams;
    typedef bitcompression::PosOccFieldParams PosOccFieldParams;
    typedef index::Schema Schema;

    enum {
        COMMON_WORD,
        MEDIUM_WORD,
        RARE_WORD,
        NUM_WORDCLASSES,
    };
    std::vector<std::vector<FakeWord *> > _words;
    Schema _schema;
    std::vector<PosOccFieldsParams> _fieldsParams;

    FakeWordSet(void);

    FakeWordSet(bool hasElements,
                bool hasElementWeights);

    ~FakeWordSet(void);

    void
    setupParams(bool hasElements,
                bool hasElementWeights);

    void
    setupWords(search::Rand48 &rnd,
               unsigned int numDocs,
               unsigned int commonDocFreq,
               unsigned int numWordsPerWordClass);

    void
    dropWords(void);

    int
    getNumWords(void);

    const PosOccFieldsParams &
    getFieldsParams(void) const
    {
        return _fieldsParams.back();
    }

    uint32_t
    getPackedIndex(void) const
    {
        return _fieldsParams.size() - 1;
    }

    const std::vector<PosOccFieldsParams> &
    getAllFieldsParams(void) const
    {
        return _fieldsParams;
    }

    const Schema &
    getSchema(void) const
    {
        return _schema;
    }

    void
    addDocIdBias(uint32_t docIdBias);
};

} // namespace fakedata

} // namespace search

