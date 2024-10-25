// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvectordictionary.h"
#include "zcposoccrandread.h"
#include <vespa/searchlib/index/dictionary_lookup_result.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/util/field_index_stats.h>
#include <string>

namespace search::diskindex {

/**
 * This class represents a field index that has a dictionary, posting list files and bit vector files.
 * Parts of the disk dictionary and all bit vector dictionaries are loaded into memory during setup.
 * All other files are just opened, ready for later access.
 */
class FieldIndex {
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
    uint64_t _size_on_disk;
    std::shared_ptr<LockedDiskIoStats> _disk_io_stats;

public:
    FieldIndex();
    FieldIndex(const FieldIndex& rhs) = delete;
    FieldIndex(FieldIndex&& rhs);
    ~FieldIndex();
    static uint64_t calculate_size_on_disk(const std::string& dir, const std::vector<std::string>& file_names);
    static uint64_t calculate_field_index_size_on_disk(const std::string& field_dir);
    bool open_dictionary(const std::string& field_dir, const TuneFileSearch& tune_file_search);
    bool open(const std::string& field_dir, const TuneFileSearch &tune_file_search);
    void reuse_files(const FieldIndex& rhs);
    std::unique_ptr<index::PostingListHandle> read_posting_list(const search::index::DictionaryLookupResult& lookup_result) const;
    std::unique_ptr<BitVector> read_bit_vector(const search::index::DictionaryLookupResult& lookup_result) const;
    index::FieldLengthInfo get_field_length_info() const;

    index::DictionaryFileRandRead* get_dictionary() noexcept { return _dict.get(); }
    FieldIndexStats get_stats() const;
};

}
