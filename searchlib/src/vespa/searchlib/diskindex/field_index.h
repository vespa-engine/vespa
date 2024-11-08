// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvectordictionary.h"
#include "i_posting_list_cache.h"
#include "zcposoccrandread.h"
#include <vespa/searchlib/index/dictionary_lookup_result.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/util/field_index_stats.h>
#include <mutex>
#include <string>

namespace search::diskindex {

/**
 * This class represents a field index that has a dictionary, posting list files and bit vector files.
 * Parts of the disk dictionary and all bit vector dictionaries are loaded into memory during setup.
 * All other files are just opened, ready for later access.
 */
class FieldIndex : public IPostingListCache::IPostingListFileBacking {
    using DiskPostingFile = index::PostingListFileRandRead;
    using DiskPostingFileReal = Zc4PosOccRandRead;
    using DiskPostingFileDynamicKReal = ZcPosOccRandRead;

    class LockedDiskIoStats : public DiskIoStats {
        std::mutex _mutex;

    public:
        LockedDiskIoStats() noexcept;
        ~LockedDiskIoStats();

        void add_read_operation(uint64_t bytes) {
            std::lock_guard guard(_mutex);
            DiskIoStats::add_read_operation(bytes);
        }

        DiskIoStats read_and_clear() {
            std::lock_guard guard(_mutex);
            return DiskIoStats::read_and_clear();
        }
    };

    std::shared_ptr<DiskPostingFile> _posting_file;
    std::shared_ptr<BitVectorDictionary> _bit_vector_dict;
    std::unique_ptr<index::DictionaryFileRandRead> _dict;
    uint64_t _file_id;
    uint64_t _size_on_disk;
    std::shared_ptr<LockedDiskIoStats> _disk_io_stats;
    std::shared_ptr<IPostingListCache> _posting_list_cache;
    static std::atomic<uint64_t> _file_id_source;

    static uint64_t get_next_file_id() noexcept { return _file_id_source.fetch_add(1) + 1; }
public:
    FieldIndex();
    FieldIndex(std::shared_ptr<IPostingListCache> posting_list_cache);
    FieldIndex(const FieldIndex& rhs) = delete;
    FieldIndex(FieldIndex&& rhs);
    ~FieldIndex();
    static uint64_t calculate_size_on_disk(const std::string& dir, const std::vector<std::string>& file_names);
    static uint64_t calculate_field_index_size_on_disk(const std::string& field_dir);
    bool open_dictionary(const std::string& field_dir, const TuneFileSearch& tune_file_search);
    bool open(const std::string& field_dir, const TuneFileSearch &tune_file_search);
    void reuse_files(const FieldIndex& rhs);
    index::PostingListHandle read_uncached_posting_list(const search::index::DictionaryLookupResult& lookup_result) const;
    index::PostingListHandle read(const IPostingListCache::Key& key) const override;
    index::PostingListHandle read_posting_list(const search::index::DictionaryLookupResult& lookup_result) const;
    std::unique_ptr<BitVector> read_bit_vector(const search::index::DictionaryLookupResult& lookup_result) const;
    std::unique_ptr<search::queryeval::SearchIterator> create_iterator(const search::index::DictionaryLookupResult& lookup_result,
                                                                       const index::PostingListHandle& handle,
                                                                       const search::fef::TermFieldMatchDataArray& tfmda) const;
    index::FieldLengthInfo get_field_length_info() const;

    index::DictionaryFileRandRead* get_dictionary() noexcept { return _dict.get(); }
    FieldIndexStats get_stats() const;
};

}
