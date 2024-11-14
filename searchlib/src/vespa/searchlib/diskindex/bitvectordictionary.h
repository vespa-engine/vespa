// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitvectorkeyscope.h"
#include <vespa/searchlib/index/bitvector_dictionary_lookup_result.h>
#include <vespa/searchlib/index/bitvectorkeys.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <string>
#include <vector>

class FastOS_FileInterface;

namespace search { class BitVector; }

namespace search::diskindex {

/**
 * This dictionary provides a sparse mapping from word number -> BitVector.
 * The dictionary is constructed based on the boolocc idx file and
 * the actual bit vectors are stored in the boolocc dat file.
 **/
class BitVectorDictionary
{
private:
    using WordSingleKey = search::index::BitVectorWordSingleKey;

    uint32_t                              _docIdLimit;
    std::vector<WordSingleKey>            _entries;
    size_t                                _vectorSize;
    std::unique_ptr<FastOS_FileInterface> _datFile;
    uint32_t                              _datHeaderLen;

public:
    using SP = std::shared_ptr<BitVectorDictionary>;
    BitVectorDictionary(const BitVectorDictionary &rhs) = delete;
    BitVectorDictionary &operator=(const BitVectorDictionary &rhs) = delete;
    BitVectorDictionary();
    ~BitVectorDictionary();

    /**
     * Open this dictionary using the following path prefix to where
     * the files are located.  The boolocc idx file is loaded into
     * memory while the dat file is just opened.
     *
     * @param pathPrefix the path prefix to where the boolocc files
     *                   are located.
     * @return true if the files could be opened.
     **/
    bool
    open(const std::string &pathPrefix,
         const TuneFileRandRead &tuneFileRead,
         BitVectorKeyScope scope);

    /**
     * Lookup the given word number.
     *
     * @param word_num the word number to lookup a bit vector for.
     * @return a bitvector dictionary lookup result that can be passed to read_bitvector member function.
     **/
    index::BitVectorDictionaryLookupResult lookup(uint64_t word_num);
    /**
     * Load and return the associated bit vector if lookup result is valid.
     *
     * @param lookup_result the result returned from lookup.
     * @return the loaded bit vector or empty if lookup result was invalid.
     **/
    std::unique_ptr<BitVector> read_bitvector(index::BitVectorDictionaryLookupResult lookup_result);

    uint32_t getDocIdLimit() const { return _docIdLimit; }

    const std::vector<WordSingleKey> & getEntries() const { return _entries; }
};

}

