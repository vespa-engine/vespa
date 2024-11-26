// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposoccrandread.h"
#include "zcposocciterators.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <cassert>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.zcposoccrandread");

using search::bitcompression::EG2PosOccEncodeContext;
using search::bitcompression::EGPosOccEncodeContext;
using search::bitcompression::EG2PosOccDecodeContext;
using search::bitcompression::EG2PosOccDecodeContextCooked;
using search::bitcompression::EGPosOccDecodeContext;
using search::bitcompression::EGPosOccDecodeContextCooked;
using search::bitcompression::PosOccFieldsParams;
using search::bitcompression::FeatureDecodeContext;
using search::index::DictionaryLookupResult;
using search::index::FieldLengthInfo;
using search::index::PostingListCounts;
using search::index::PostingListFileRange;
using search::index::PostingListHandle;
using search::ComprFileReadContext;

namespace {

std::string myId4("Zc.4");
std::string myId5("Zc.5");
std::string interleaved_features("interleaved_features");

PostingListFileRange get_file_range(const DictionaryLookupResult& lookup_result, uint64_t header_bit_size)
{
    uint64_t start_offset = (lookup_result.bitOffset + header_bit_size) >> 3;
    // Align start at 64-bit boundary
    start_offset -= (start_offset & 7);
    uint64_t end_offset = (lookup_result.bitOffset + header_bit_size + lookup_result.counts._bitLength + 7) >> 3;
    // Align end at 64-bit boundary
    end_offset += (-end_offset & 7);
    return {start_offset, end_offset};
}

}

namespace search::diskindex {

using vespalib::getLastErrorString;

ZcPosOccRandRead::ZcPosOccRandRead()
    : _file(std::make_unique<FastOS_File>()),
      _fileSize(0),
      _posting_params(64, 1 << 30, 10000000, true, true, false),
      _numWords(0),
      _fileBitSize(0),
      _headerBitSize(0),
      _fieldsParams()
{
}


ZcPosOccRandRead::~ZcPosOccRandRead()
{
    if (_file->IsOpened()) {
        close();
    }
}


std::unique_ptr<search::queryeval::SearchIterator>
ZcPosOccRandRead::createIterator(const DictionaryLookupResult& lookup_result,
                                 const PostingListHandle &handle,
                                 const search::fef::TermFieldMatchDataArray &matchData) const
{
    assert((lookup_result.counts._numDocs != 0) == (lookup_result.counts._bitLength != 0));
    assert(handle._bitOffsetMem <= lookup_result.bitOffset);

    if (lookup_result.counts._bitLength == 0) {
        return std::make_unique<search::queryeval::EmptySearch>();
    }

    const char *cmem = static_cast<const char *>(handle._mem);
    uint64_t memOffset = reinterpret_cast<unsigned long>(cmem) & 7;
    const uint64_t *mem = reinterpret_cast<const uint64_t *>
                          (cmem - memOffset) +
                          (memOffset * 8 + lookup_result.bitOffset -
                           handle._bitOffsetMem) / 64;
    int bitOffset = (memOffset * 8 + lookup_result.bitOffset -
                     handle._bitOffsetMem) & 63;

    Position start(mem, bitOffset);
    return create_zc_posocc_iterator(true, lookup_result.counts, start, lookup_result.counts._bitLength, _posting_params, _fieldsParams, matchData);
}


PostingListHandle
ZcPosOccRandRead::read_posting_list(const DictionaryLookupResult& lookup_result)
{
    PostingListHandle handle;
    if (lookup_result.counts._bitLength == 0) {
        return handle;
    }

    auto file_range = get_file_range(lookup_result, _headerBitSize);
    void *mapPtr = _file->MemoryMapPtr(file_range.start_offset);
    if (mapPtr != nullptr) {
        handle._mem = mapPtr;
        size_t pad_before = file_range.start_offset - vespalib::round_down_to_page_boundary(file_range.start_offset);
        handle._read_bytes = vespalib::round_up_to_page_size(pad_before + file_range.size() + decode_prefetch_size);
    } else {
        uint64_t vectorLen = file_range.size();
        size_t padBefore;
        size_t padAfter;
        size_t padExtraAfter;       // Decode prefetch space
        _file->DirectIOPadding(file_range.start_offset, vectorLen, padBefore, padAfter);
        padExtraAfter = 0;
        if (padAfter < decode_prefetch_size) {
            padExtraAfter = decode_prefetch_size - padAfter;
        }

        size_t mallocLen = padBefore + vectorLen + padAfter + padExtraAfter;
        void *alignedBuffer = nullptr;
        if (mallocLen > 0) {
            alignedBuffer = _file->AllocateDirectIOBuffer(mallocLen);
            assert(alignedBuffer != nullptr);
            assert(file_range.end_offset + padAfter + padExtraAfter <= _fileSize);
            _file->ReadBuf(alignedBuffer,
                           padBefore + vectorLen + padAfter,
                           file_range.start_offset - padBefore);
        }
        // Zero decode prefetch memory to avoid uninitialized reads
        if (padExtraAfter > 0) {
            memset(reinterpret_cast<char *>(alignedBuffer) + padBefore + vectorLen + padAfter,
                   '\0',
                   padExtraAfter);
        }
        handle._mem = static_cast<char *>(alignedBuffer) + padBefore;
        handle._allocMem = std::shared_ptr<void>(alignedBuffer, free);
        handle._allocSize = mallocLen;
        handle._read_bytes = padBefore + vectorLen + padAfter;
    }
    handle._bitOffsetMem = (file_range.start_offset << 3) - _headerBitSize;
    return handle;
}

void
ZcPosOccRandRead::consider_trim_posting_list(const DictionaryLookupResult &lookup_result, PostingListHandle &handle,
                                             double bloat_factor) const
{
    if (lookup_result.counts._bitLength == 0 || _memoryMapped) {
        return;
    }
    auto file_range = get_file_range(lookup_result, _headerBitSize);
    size_t malloc_len = file_range.size() + decode_prefetch_size;
    if (handle._allocSize == malloc_len) {
        assert(handle._allocMem.get() == handle._mem);
        return;
    }
    assert(handle._allocSize >= malloc_len);
    if (handle._allocSize <= malloc_len * (1.0 + bloat_factor)) {
        return;
    }
    auto *mem = malloc(malloc_len);
    assert(mem != nullptr);
    memcpy(mem, handle._mem, malloc_len);
    handle._allocMem = std::shared_ptr<void>(mem, free);
    handle._mem = mem;
    handle._allocSize = malloc_len;
    handle._read_bytes = file_range.size();
}

PostingListFileRange
ZcPosOccRandRead::get_posting_list_file_range(const DictionaryLookupResult& lookup_result) const
{
    if (lookup_result.counts._bitLength == 0) {
        return {0, 0};
    }
    return get_file_range(lookup_result, _headerBitSize);
}

bool
ZcPosOccRandRead::
open(const std::string &name, const TuneFileRandRead &tuneFileRead)
{
    _file->setFAdviseOptions(tuneFileRead.getAdvise());
    if (tuneFileRead.getWantMemoryMap()) {
        _file->enableMemoryMap(tuneFileRead.getMemoryMapFlags());
    } else  if (tuneFileRead.getWantDirectIO()) {
        _file->EnableDirectIO();
    }
    bool res = _file->OpenReadOnly(name.c_str());
    if (!res) {
        LOG(error, "could not open %s: %s", _file->GetFileName(), getLastErrorString().c_str());
        return false;
    }
    _fileSize = _file->getSize();

    readHeader();
    afterOpen(*_file);
    return true;
}


bool
ZcPosOccRandRead::close()
{
    return _file->Close();
}


template <typename DecodeContext>
void
ZcPosOccRandRead::readHeader(const std::string &identifier)
{
    DecodeContext d(&_fieldsParams);
    ComprFileReadContext drc(d);

    drc.setFile(_file.get());
    drc.setFileSize(_file->getSize());
    drc.allocComprBuf(512, 32768u);
    d.emptyBuffer(0);
    drc.readComprBuffer();
    d.setReadContext(&drc);

    vespalib::FileHeader header;
    d.readHeader(header, _file->getSize());
    uint32_t headerLen = header.getSize();
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(header.hasTag("format.1"));
    assert(!header.hasTag("format.2"));
    assert(header.hasTag("numWords"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("minSkipDocs"));
    assert(header.getTag("frozen").asInteger() != 0);
    _fileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == identifier);
    assert(header.getTag("format.1").asString() == d.getIdentifier());
    _numWords = header.getTag("numWords").asInteger();
    _posting_params._min_chunk_docs = header.getTag("minChunkDocs").asInteger();
    _posting_params._doc_id_limit = header.getTag("docIdLimit").asInteger();
    _posting_params._min_skip_docs = header.getTag("minSkipDocs").asInteger();
    if (header.hasTag(interleaved_features) && (header.getTag(interleaved_features).asInteger() != 0)) {
        _posting_params._encode_interleaved_features = true;
    }
    // Read feature decoding specific subheader
    d.readHeader(header, "features.");
    // Align on 64-bit unit
    d.smallAlign(64);
    headerLen += (-headerLen & 7);
    assert(d.getReadOffset() == headerLen * 8);
    _headerBitSize = d.getReadOffset();
}

void
ZcPosOccRandRead::readHeader()
{
    readHeader<EGPosOccDecodeContext<true>>(myId5);
}

const std::string &
ZcPosOccRandRead::getIdentifier()
{
    return myId5;
}


const std::string &
ZcPosOccRandRead::getSubIdentifier()
{
    PosOccFieldsParams fieldsParams;
    EGPosOccDecodeContext<true> d(&fieldsParams);
    return d.getIdentifier();
}

const FieldLengthInfo &
ZcPosOccRandRead::get_field_length_info() const
{
    return _fieldsParams.getFieldParams()->get_field_length_info();
}

Zc4PosOccRandRead::
Zc4PosOccRandRead()
    : ZcPosOccRandRead()
{
    _posting_params._dynamic_k = false;
}


void
Zc4PosOccRandRead::readHeader()
{
    readHeader<EG2PosOccDecodeContext<true> >(myId4);
}

const std::string &
Zc4PosOccRandRead::getIdentifier()
{
    return myId4;
}

const std::string &
Zc4PosOccRandRead::getSubIdentifier()
{
    PosOccFieldsParams fieldsParams;
    EG2PosOccDecodeContext<true> d(&fieldsParams);
    return d.getIdentifier();
}

}
