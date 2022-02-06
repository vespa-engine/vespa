// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposoccrandread.h"
#include "zcposocciterators.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/fastos/file.h>

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
using search::index::FieldLengthInfo;
using search::index::PostingListCounts;
using search::index::PostingListHandle;
using search::ComprFileReadContext;

namespace {

vespalib::string myId4("Zc.4");
vespalib::string myId5("Zc.5");
vespalib::string interleaved_features("interleaved_features");

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
{ }


ZcPosOccRandRead::~ZcPosOccRandRead()
{
    if (_file->IsOpened()) {
        close();
    }
}


search::queryeval::SearchIterator *
ZcPosOccRandRead::
createIterator(const PostingListCounts &counts,
               const PostingListHandle &handle,
               const search::fef::TermFieldMatchDataArray &matchData,
               bool usebitVector) const
{
    (void) usebitVector;

    assert((handle._bitLength != 0) == (counts._bitLength != 0));
    assert((counts._numDocs != 0) == (counts._bitLength != 0));
    assert(handle._bitOffsetMem <= handle._bitOffset);

    if (handle._bitLength == 0) {
        return new search::queryeval::EmptySearch;
    }

    const char *cmem = static_cast<const char *>(handle._mem);
    uint64_t memOffset = reinterpret_cast<unsigned long>(cmem) & 7;
    const uint64_t *mem = reinterpret_cast<const uint64_t *>
                          (cmem - memOffset) +
                          (memOffset * 8 + handle._bitOffset -
                           handle._bitOffsetMem) / 64;
    int bitOffset = (memOffset * 8 + handle._bitOffset -
                     handle._bitOffsetMem) & 63;

    Position start(mem, bitOffset);
    return create_zc_posocc_iterator(true, counts, start, handle._bitLength, _posting_params, _fieldsParams, matchData).release();
}


void
ZcPosOccRandRead::readPostingList(const PostingListCounts &counts,
                                  uint32_t firstSegment,
                                  uint32_t numSegments,
                                  PostingListHandle &handle)
{
    // XXX: Ignore segments for now.
    (void) firstSegment;
    (void) numSegments;
    (void) counts;

    handle.drop();
    if (handle._bitLength == 0) {
        return;
    }

    uint64_t startOffset = (handle._bitOffset + _headerBitSize) >> 3;
    // Align start at 64-bit boundary
    startOffset -= (startOffset & 7);

    void *mapPtr = _file->MemoryMapPtr(startOffset);
    if (mapPtr != nullptr) {
        handle._mem = mapPtr;
        handle._allocMem = nullptr;
        handle._allocSize = 0;
    } else {
        uint64_t endOffset = (handle._bitOffset + _headerBitSize +
                              handle._bitLength + 7) >> 3;
        // Align end at 64-bit boundary
        endOffset += (-endOffset & 7);

        uint64_t vectorLen = endOffset - startOffset;
        size_t padBefore;
        size_t padAfter;
        size_t padExtraAfter;       // Decode prefetch space
        _file->DirectIOPadding(startOffset, vectorLen, padBefore, padAfter);
        padExtraAfter = 0;
        if (padAfter < 16) {
            padExtraAfter = 16 - padAfter;
        }

        size_t mallocLen = padBefore + vectorLen + padAfter + padExtraAfter;
        void *mallocStart = nullptr;
        void *alignedBuffer = nullptr;
        if (mallocLen > 0) {
            alignedBuffer = _file->AllocateDirectIOBuffer(mallocLen, mallocStart);
            assert(mallocStart != nullptr);
            assert(endOffset + padAfter + padExtraAfter <= _fileSize);
            _file->ReadBuf(alignedBuffer,
                           padBefore + vectorLen + padAfter,
                           startOffset - padBefore);
        }
        // Zero decode prefetch memory to avoid uninitialized reads
        if (padExtraAfter > 0) {
            memset(reinterpret_cast<char *>(alignedBuffer) + padBefore + vectorLen + padAfter,
                   '\0',
                   padExtraAfter);
        }
        handle._mem = static_cast<char *>(alignedBuffer) + padBefore;
        handle._allocMem = mallocStart;
        handle._allocSize = mallocLen;
    }
    handle._bitOffsetMem = (startOffset << 3) - _headerBitSize;
}


bool
ZcPosOccRandRead::
open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead)
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
    _fileSize = _file->GetSize();

    readHeader();
    return true;
}


bool
ZcPosOccRandRead::close()
{
    return _file->Close();
}


template <typename DecodeContext>
void
ZcPosOccRandRead::readHeader(const vespalib::string &identifier)
{
    DecodeContext d(&_fieldsParams);
    ComprFileReadContext drc(d);

    drc.setFile(_file.get());
    drc.setFileSize(_file->GetSize());
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

const vespalib::string &
ZcPosOccRandRead::getIdentifier()
{
    return myId5;
}


const vespalib::string &
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

const vespalib::string &
Zc4PosOccRandRead::getIdentifier()
{
    return myId4;
}

const vespalib::string &
Zc4PosOccRandRead::getSubIdentifier()
{
    PosOccFieldsParams fieldsParams;
    EG2PosOccDecodeContext<true> d(&fieldsParams);
    return d.getIdentifier();
}

}
