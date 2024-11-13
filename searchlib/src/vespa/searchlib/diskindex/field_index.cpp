// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index.h"
#include "fileheader.h"
#include "pagedict4randread.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.field_index");

using search::index::BitVectorDictionaryLookupResult;
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

std::atomic<uint64_t> FieldIndex::_file_id_source(0);

FieldIndex::LockedCacheDiskIoStats::LockedCacheDiskIoStats() noexcept
    : _stats(),
      _mutex()
{
}

FieldIndex::LockedCacheDiskIoStats::~LockedCacheDiskIoStats() = default;

FieldIndex::FieldIndex()
    : _posting_file(),
      _bit_vector_dict(),
      _dict(),
      _file_id(0),
      _size_on_disk(0),
      _cache_disk_io_stats(std::make_shared<LockedCacheDiskIoStats>()),
      _posting_list_cache(),
      _field_id(0)
{
}

FieldIndex::FieldIndex(uint32_t field_id, std::shared_ptr<IPostingListCache> posting_list_cache)
    : FieldIndex()
{
    _field_id = field_id;
    _posting_list_cache = std::move(posting_list_cache);
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

    bDict = std::make_shared<BitVectorDictionary>();
    // Always memory map bitvectors for now
    auto force_mmap = tune_file_search._read;
    force_mmap.setWantMemoryMap();
    if (!bDict->open(field_dir, force_mmap, BitVectorKeyScope::PERFIELD_WORDS)) {
        LOG(warning, "Could not open bit vector dictionary in '%s'", field_dir.c_str());
        return false;
    }
    _posting_file = std::move(pFile);
    _bit_vector_dict = std::move(bDict);
    _file_id = get_next_file_id();
    _size_on_disk = calculate_field_index_size_on_disk(field_dir);
    return true;
}

void
FieldIndex::reuse_files(const FieldIndex& rhs)
{
    _posting_file = rhs._posting_file;
    _bit_vector_dict = rhs._bit_vector_dict;
    _file_id = rhs._file_id;
    _size_on_disk = rhs._size_on_disk;
    _cache_disk_io_stats = rhs._cache_disk_io_stats;
}

PostingListHandle
FieldIndex::read_uncached_posting_list(const DictionaryLookupResult& lookup_result) const
{
    auto handle = _posting_file->read_posting_list(lookup_result);
    if (handle._read_bytes != 0) {
        _cache_disk_io_stats->add_uncached_read_operation(handle._read_bytes);
    }
    return handle;
}

PostingListHandle
FieldIndex::read(const IPostingListCache::Key& key) const
{
    DictionaryLookupResult lookup_result;
    lookup_result.bitOffset = key.bit_offset;
    lookup_result.counts._bitLength = key.bit_length;
    key.backing_store_file = nullptr; // Signal cache miss back to layer above cache
    return read_uncached_posting_list(lookup_result);
}

PostingListHandle
FieldIndex::read_posting_list(const DictionaryLookupResult& lookup_result) const
{
    auto file = _posting_file.get();
    if (file == nullptr || lookup_result.counts._bitLength == 0) {
        return {};
    }
    if (file->getMemoryMapped() || !_posting_list_cache) {
        return read_uncached_posting_list(lookup_result);
    }
    IPostingListCache::Key key;
    key.backing_store_file = this;
    key.file_id = _file_id;
    key.bit_offset = lookup_result.bitOffset;
    key.bit_length = lookup_result.counts._bitLength;
    auto result = _posting_list_cache->read(key);
    auto cache_hit = key.backing_store_file == this;
    if (cache_hit && result._read_bytes != 0) {
        _cache_disk_io_stats->add_cached_read_operation(result._read_bytes);
    }
    return result;
}

BitVectorDictionaryLookupResult
FieldIndex::lookup_bit_vector(const DictionaryLookupResult& lookup_result) const
{
    if (!_bit_vector_dict) {
        return {};
    }
    return _bit_vector_dict->lookup(lookup_result.wordNum);
}

std::unique_ptr<BitVector>
FieldIndex::read_bit_vector(BitVectorDictionaryLookupResult lookup_result) const
{
    if (!_bit_vector_dict) {
        return {};
    }
    return _bit_vector_dict->read_bitvector(lookup_result);
}

std::unique_ptr<search::queryeval::SearchIterator>
FieldIndex::create_iterator(const DictionaryLookupResult& lookup_result,
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
    auto cache_disk_io_stats = _cache_disk_io_stats->read_and_clear();
    return FieldIndexStats().size_on_disk(_size_on_disk).cache_disk_io_stats(cache_disk_io_stats);
}

}
