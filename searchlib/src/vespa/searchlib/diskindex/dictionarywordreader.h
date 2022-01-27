// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "pagedict4file.h"


namespace search::diskindex {

/*
 * Helper class, will be used by fusion later to handle generation of
 * word numbering without writing a word list file.
 */
class WordAggregator
{
private:
    vespalib::string _word;
    uint64_t _wordNum;

public:
    WordAggregator()
        : _word(),
          _wordNum(0)
    {
    }

    void tryWriteWord(vespalib::stringref word) {
        if (word != _word || _wordNum == 0) {
            ++_wordNum;
            _word = word;
        }
    }

    uint64_t getWordNum() const { return _wordNum; }
};


/*
 * Class used to merge words in multiple dictionaries for
 * new style fusion (using WordAggregator).
 */
class DictionaryWordReader
{
public:
    vespalib::string _word;
    uint64_t _wordNum;
    index::PostingListCounts _counts;

private:
    // "owners" of file handles.
    std::unique_ptr<FastOS_FileInterface> _old2newwordfile;

    using DictionaryFileSeqRead = index::DictionaryFileSeqRead;
    std::unique_ptr<DictionaryFileSeqRead> _dictFile;

    static uint64_t noWordNumHigh() { return std::numeric_limits<uint64_t>::max(); }
    static uint64_t noWordNum() { return 0u; }
public:
    DictionaryWordReader();
    ~DictionaryWordReader();

    bool isValid() const {
        return _wordNum != noWordNumHigh();
    }

    bool operator<(const DictionaryWordReader &rhs) const {
        if (!isValid()) {
            return false;
        }
        if (!rhs.isValid()) {
            return true;
        }
        return _word < rhs._word;
    }

    void read() {
        _dictFile->readWord(_word, _wordNum, _counts);
    }

    bool open(const vespalib::string & dictionaryName,
              const vespalib::string & wordMapName,
              const TuneFileSeqRead &tuneFileRead);

    void close();

    void writeNewWordNum(uint64_t newWordNum);

    void write(WordAggregator &writer) {
        writer.tryWriteWord(_word);
        writeNewWordNum(writer.getWordNum());
    }
};

}
