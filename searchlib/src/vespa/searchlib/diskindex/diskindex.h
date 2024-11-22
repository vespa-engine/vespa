// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_index.h"
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/util/index_stats.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/cache.h>
#include <string>

namespace search::diskindex {

/**
 * This class represents a disk index that contains a set of field indexes that are independent of each other.
 */
class DiskIndex : public queryeval::Searchable {
public:
    /**
     * The result after performing a disk dictionary lookup.
     **/
    struct LookupResult : public search::index::DictionaryLookupResult {
        uint32_t                         indexId;
        LookupResult() noexcept;
        void swap(LookupResult & rhs) noexcept {
            DictionaryLookupResult::swap(rhs);
            std::swap(indexId , rhs.indexId);
        }
    };
    using LookupResultVector = std::vector<LookupResult>;
    using IndexList = std::vector<uint32_t>;

    class Key {
    public:
        Key() noexcept;
        Key(IndexList indexes, std::string_view word) noexcept;
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
        const std::string & getWord() const noexcept { return _word; }
    private:
        std::string _word;
        IndexList        _indexes;
    };

private:
    using Cache = vespalib::cache<vespalib::CacheParam<vespalib::LruParam<Key, LookupResultVector>, DiskIndex>>;

    std::string                       _indexDir;
    size_t                                 _dictionary_cache_size;
    index::Schema                          _schema;
    std::vector<FieldIndex>                _field_indexes;
    uint32_t                               _nonfield_size_on_disk;
    TuneFileSearch                         _tuneFileSearch;
    std::shared_ptr<IPostingListCache>     _posting_list_cache;
    Cache                                  _cache;

    void calculate_nonfield_size_on_disk();
    bool loadSchema();
    bool openDictionaries(const TuneFileSearch &tuneFileSearch);

public:
    /**
     * Create a view of the disk index located in the given directory.
     *
     * @param indexDir the directory where the disk index is located.
     * @param dictionary_cache_size optional size (in bytes) of the disk dictionary lookup cache.
     */
    explicit DiskIndex(const std::string &indexDir, std::shared_ptr<IPostingListCache> posting_list_cache,
                       size_t dictionary_cache_size = 0);
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
    LookupResult lookup(uint32_t indexId, std::string_view word);

    LookupResultVector lookup(const std::vector<uint32_t> & indexes, std::string_view word);

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpec &field,
                                                          const query::Node &term) override;

    std::unique_ptr<queryeval::Blueprint> createBlueprint(const queryeval::IRequestContext & requestContext,
                                                          const queryeval::FieldSpecList &fields,
                                                          const query::Node &term) override;

    /**
     * Get stats for this index.
     */
    IndexStats get_stats(bool clear_disk_io_stats) const;
    const index::Schema &getSchema() const { return _schema; }
    const std::string &getIndexDir() const { return _indexDir; }

    /**
     * Needed for the Cache::BackingStore interface.
     */
    bool read(const Key & key, LookupResultVector & result);

    index::FieldLengthInfo get_field_length_info(const std::string& field_name) const;
    const std::shared_ptr<IPostingListCache>& get_posting_list_cache() const noexcept { return _posting_list_cache; }
    const FieldIndex& get_field_index(uint32_t field_id) const noexcept { return _field_indexes[field_id]; }
};

void swap(DiskIndex::LookupResult & a, DiskIndex::LookupResult & b);

}
