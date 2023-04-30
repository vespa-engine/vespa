// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvectordictionary.h"
#include "zcposoccrandread.h"
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/cache.h>

namespace search::diskindex {

/**
 * This class represents a disk index that contains a set of field indexes that are independent of each other.
 *
 * Each field index has a dictionary, posting list files and bit vector files.
 * Parts of the disk dictionary and all bit vector dictionaries are loaded into memory during setup.
 * All other files are just opened, ready for later access.
 */
class DiskIndex : public queryeval::Searchable {
public:
    /**
     * The result after performing a disk dictionary lookup.
     **/
    struct LookupResult {
        uint32_t                         indexId;
        uint64_t                         wordNum;
        index::PostingListCounts         counts;
        uint64_t                         bitOffset;
        using UP = std::unique_ptr<LookupResult>;
        LookupResult() noexcept;
        bool valid() const noexcept { return counts._numDocs > 0; }
        void swap(LookupResult & rhs) noexcept {
            std::swap(indexId , rhs.indexId);
            std::swap(wordNum , rhs.wordNum);
            counts.swap(rhs.counts);
            std::swap(bitOffset , rhs.bitOffset);
        }
    };
    using LookupResultVector = std::vector<LookupResult>;
    using IndexList = std::vector<uint32_t>;

    class Key {
    public:
        Key() noexcept;
        Key(IndexList indexes, vespalib::stringref word) noexcept;
        Key(const Key &);
        Key & operator = (const Key &);
        Key(Key &&) noexcept = default;
        Key & operator = (Key &&) noexcept = default;
        ~Key();
        uint32_t hash() const noexcept {
            return vespalib::hashValue(_word.c_str(), _word.size());
        }
        bool operator == (const Key & rhs) const noexcept {
            return _word == rhs._word;
        }
        void push_back(uint32_t indexId) { _indexes.push_back(indexId); }
        const IndexList & getIndexes() const noexcept { return _indexes; }
        const vespalib::string & getWord() const noexcept { return _word; }
    private:
        vespalib::string _word;
        IndexList        _indexes;
    };

private:
    using DiskPostingFile = index::PostingListFileRandRead;
    using DiskPostingFileReal = Zc4PosOccRandRead;
    using DiskPostingFileDynamicKReal = ZcPosOccRandRead;
    using Cache = vespalib::cache<vespalib::CacheParam<vespalib::LruParam<Key, LookupResultVector>, DiskIndex>>;

    vespalib::string                       _indexDir;
    size_t                                 _cacheSize;
    index::Schema                          _schema;
    std::vector<DiskPostingFile::SP>       _postingFiles;
    std::vector<BitVectorDictionary::SP>   _bitVectorDicts;
    std::vector<std::unique_ptr<index::DictionaryFileRandRead>> _dicts;
    TuneFileSearch                         _tuneFileSearch;
    Cache                                  _cache;
    uint64_t                               _size;

    void calculateSize();
    bool loadSchema();
    bool openDictionaries(const TuneFileSearch &tuneFileSearch);
    bool openField(const vespalib::string &fieldDir, const TuneFileSearch &tuneFileSearch);

public:
    /**
     * Create a view of the disk index located in the given directory.
     *
     * @param indexDir the directory where the disk index is located.
     * @param cacheSize optional size (in bytes) of the disk dictionary lookup cache.
     */
    explicit DiskIndex(const vespalib::string &indexDir, size_t cacheSize=0);
    ~DiskIndex() override;

    /**
     * Setup this instance by opening and loading relevant index files.
     *
     * @return true if this instance was successfully setup.
     */
    bool setup(const TuneFileSearch &tuneFileSearch);
    bool setup(const TuneFileSearch &tuneFileSearch, const DiskIndex &old);

    /**
     * Perform a dictionary lookup for the given word in the given field.
     *
     * @param indexId the id of the field to perform lookup for.
     * @param word the word to lookup.
     * @return the lookup result or nullptr if the word is not found.
     */
    LookupResult::UP lookup(uint32_t indexId, vespalib::stringref word);

    LookupResultVector lookup(const std::vector<uint32_t> & indexes, vespalib::stringref word);

    /**
     * Read the posting list corresponding to the given lookup result.
     *
     * @param lookupRes the result of the previous dictionary lookup.
     * @return a handle for the posting list in memory.
     */
    index::PostingListHandle::UP readPostingList(const LookupResult &lookupRes) const;

    /**
     * Read the bit vector corresponding to the given lookup result.
     *
     * @param lookupRes the result of the previous dictionary lookup.
     * @return the bit vector or nullptr if no bit vector exists for the
     *         word in the lookup result.
     */
    BitVector::UP readBitVector(const LookupResult &lookupRes) const;

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpec &field,
                                                          const query::Node &term) override;

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpecList &fields,
                                                          const query::Node &term) override;

    /**
     * Get the size on disk of this index.
     */
    uint64_t getSize() const { return _size; }

    const index::Schema &getSchema() const { return _schema; }
    const vespalib::string &getIndexDir() const { return _indexDir; }

    /**
     * Needed for the Cache::BackingStore interface.
     */
    bool read(const Key & key, LookupResultVector & result);

    index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const;
};

void swap(DiskIndex::LookupResult & a, DiskIndex::LookupResult & b);

}
