// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index.h"
#include "fileheader.h"
#include "pagedict4randread.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.field_index");

using search::index::DictionaryLookupResult;
using search::index::PostingListHandle;

namespace search::diskindex {

namespace {

const std::vector<std::string> field_file_names{
    "boolocc.bdat",
    "boolocc.idx",
    "posocc.dat.compressed",
    "dictionary.pdat",
    "dictionary.spdat",
    "dictionary.ssdat"
};

}

FieldIndex::LockedDiskIoStats::LockedDiskIoStats() noexcept
    : DiskIoStats(),
      _mutex()
{
}

FieldIndex::LockedDiskIoStats::~LockedDiskIoStats() = default;

FieldIndex::FieldIndex()
    : _posting_file(),
      _bit_vector_dict(),
      _dict(),
      _size_on_disk(0),
      _disk_io_stats(std::make_shared<LockedDiskIoStats>())
{
}

FieldIndex::FieldIndex(FieldIndex&&) = default;

FieldIndex::~FieldIndex() = default;

uint64_t
FieldIndex::calculate_size_on_disk(const std::string& dir, const std::vector<std::string>& file_names)
{
    uint64_t size_on_disk = 0;
    std::error_code ec;
    DiskSpaceCalculator calc;
    for (auto& file_name : file_names) {
        // Note: dir ends with slash
        std::filesystem::path path(dir + file_name);
        auto size = std::filesystem::file_size(path, ec);
        if (!ec) {
            size_on_disk += calc(size);
        }
    }
    return size_on_disk;
}

uint64_t
FieldIndex::calculate_field_index_size_on_disk(const std::string& field_dir)
{
    return calculate_size_on_disk(field_dir, field_file_names);
}



bool
FieldIndex::open_dictionary(const std::string& field_dir, const TuneFileSearch& tune_file_search)
{
    std::string dictName = field_dir + "/dictionary";
    auto dict = std::make_unique<PageDict4RandRead>();
    if (!dict->open(dictName, tune_file_search._read)) {
        LOG(warning, "Could not open disk dictionary '%s'", dictName.c_str());
        return false;
    }
    _dict = std::move(dict);
    return true;
}

bool
FieldIndex::open(const std::string& field_dir, const TuneFileSearch& tune_file_search)
{
    std::string postingName = field_dir + "posocc.dat.compressed";

    DiskPostingFile::SP pFile;
    BitVectorDictionary::SP bDict;
    FileHeader fileHeader;
    bool dynamicK = false;
    if (fileHeader.taste(postingName, tune_file_search._read)) {
        if (fileHeader.getVersion() == 1 &&
            fileHeader.getBigEndian() &&
            fileHeader.getFormats().size() == 2 &&
            fileHeader.getFormats()[0] ==
            DiskPostingFileDynamicKReal::getIdentifier() &&
            fileHeader.getFormats()[1] ==
            DiskPostingFileDynamicKReal::getSubIdentifier()) {
            dynamicK = true;
        } else if (fileHeader.getVersion() == 1 &&
                   fileHeader.getBigEndian() &&
                   fileHeader.getFormats().size() == 2 &&
                   fileHeader.getFormats()[0] ==
                   DiskPostingFileReal::getIdentifier() &&
                   fileHeader.getFormats()[1] ==
                   DiskPostingFileReal::getSubIdentifier()) {
            dynamicK = false;
        } else {
            LOG(warning, "Could not detect format for posocc file read %s", postingName.c_str());
        }
    }
    pFile.reset(dynamicK
                ? new DiskPostingFileDynamicKReal()
                : new DiskPostingFileReal());
    if (!pFile->open(postingName, tune_file_search._read)) {
        LOG(warning, "Could not open posting list file '%s'", postingName.c_str());
        return false;
    }

    bDict.reset(new BitVectorDictionary());
    if (!bDict->open(field_dir, tune_file_search._read, BitVectorKeyScope::PERFIELD_WORDS)) {
        LOG(warning, "Could not open bit vector dictionary in '%s'", field_dir.c_str());
        return false;
    }
    _posting_file = std::move(pFile);
    _bit_vector_dict = std::move(bDict);
    _size_on_disk = calculate_field_index_size_on_disk(field_dir);
    return true;
}

void
FieldIndex::reuse_files(const FieldIndex& rhs)
{
    _posting_file = rhs._posting_file;
    _bit_vector_dict = rhs._bit_vector_dict;
    _size_on_disk = rhs._size_on_disk;
}

PostingListHandle
FieldIndex::read_posting_list(const DictionaryLookupResult& lookup_result) const
{
    auto file = _posting_file.get();
    if (file == nullptr) {
        return {};
    }
    auto handle = file->read_posting_list(lookup_result);
    if (handle._read_bytes != 0) {
        _disk_io_stats->add_read_operation(handle._read_bytes);
    }
    return handle;
}

std::unique_ptr<BitVector>
FieldIndex::read_bit_vector(const DictionaryLookupResult& lookup_result) const
{
    if (!_bit_vector_dict) {
        return {};
    }
    return _bit_vector_dict->lookup(lookup_result.wordNum);
}

std::unique_ptr<search::queryeval::SearchIterator>
FieldIndex::create_iterator(const search::index::DictionaryLookupResult& lookup_result,
                            const index::PostingListHandle& handle,
                            const search::fef::TermFieldMatchDataArray& tfmda) const
{
    return _posting_file->createIterator(lookup_result, handle, tfmda);
}


index::FieldLengthInfo
FieldIndex::get_field_length_info() const
{
    return _posting_file->get_field_length_info();
}

FieldIndexStats
FieldIndex::get_stats() const
{
    auto disk_io_stats = _disk_io_stats->read_and_clear();
    return FieldIndexStats().size_on_disk(_size_on_disk).disk_io_stats(disk_io_stats);
}

}
