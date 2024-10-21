// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvectordictionary.h"
#include "zcposoccrandread.h"
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/field_length_info.h>
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

    std::shared_ptr<DiskPostingFile> _posting_file;
    std::shared_ptr<BitVectorDictionary> _bit_vector_dict;
    std::unique_ptr<index::DictionaryFileRandRead> _dict;
    uint64_t _size_on_disk;

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

    index::DictionaryFileRandRead* get_dictionary() noexcept { return _dict.get(); }
    index::PostingListFileRandRead* get_posting_file() const noexcept { return _posting_file.get(); }
    BitVectorDictionary* get_bit_vector_dictionary() const noexcept { return _bit_vector_dict.get(); }
    uint64_t get_size_on_disk() const noexcept { return _size_on_disk; }
};

}
