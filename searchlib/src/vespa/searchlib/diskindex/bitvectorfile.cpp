// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include "bitvectorfile.h"
#include <vespa/searchlib/index/bitvectorkeys.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/fileheader.h>

namespace search::diskindex {

using vespalib::nbostream;
using search::index::BitVectorWordSingleKey;
using search::common::FileHeaderContext;

namespace {

void
readHeader(vespalib::FileHeader &h,
           const vespalib::string &name)
{
    Fast_BufferedFile file(32768u);
    file.OpenReadOnly(name.c_str());
    h.readFile(file);
    file.Close();
}

const size_t FILE_HEADERSIZE_ALIGNMENT = 4096;

}

BitVectorFileWrite::BitVectorFileWrite(BitVectorKeyScope scope)
    : BitVectorIdxFileWrite(scope),
      _datFile(NULL),
      _datHeaderLen(0)
{
}


BitVectorFileWrite::~BitVectorFileWrite()
{
    // No implicit close() call, but cleanup memory allocations.
    delete _datFile;
}


void
BitVectorFileWrite::checkPointWrite(nbostream &out)
{
    flush();
    Parent::checkPointWriteCommon(out);
    out << _datHeaderLen;
    sync();
}


void
BitVectorFileWrite::checkPointRead(nbostream &in)
{
    Parent::checkPointRead(in);
    in >> _datHeaderLen;
}


void
BitVectorFileWrite::open(const vespalib::string &name,
                         uint32_t docIdLimit,
                         const TuneFileSeqWrite &tuneFileWrite,
                         const FileHeaderContext &fileHeaderContext)
{
    vespalib::string datname = name + ".bdat";

    assert(_datFile == NULL);

    Parent::open(name, docIdLimit, tuneFileWrite, fileHeaderContext);

    FastOS_FileInterface *datfile = new FastOS_File;
    _datFile = new Fast_BufferedFile(datfile);
    if (tuneFileWrite.getWantSyncWrites()) {
        _datFile->EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _datFile->EnableDirectIO();
    }
    // XXX no checking for success:
    _datFile->OpenWriteOnly(datname.c_str());

    if (_datHeaderLen == 0) {
        assert(_numKeys == 0);
        makeDatHeader(fileHeaderContext);
    }

    int64_t pos;
    size_t bitmapbytes;

    bitmapbytes = BitVector::getFileBytes(_docIdLimit);

    pos = static_cast<int64_t>(_numKeys) *
          static_cast<int64_t>(bitmapbytes) + _datHeaderLen;

    int64_t olddatsize = _datFile->GetSize();
    assert(olddatsize >= pos);
    (void) olddatsize;

    _datFile->SetSize(pos);

    assert(pos == _datFile->GetPosition());
}


void
BitVectorFileWrite::makeDatHeader(const FileHeaderContext &fileHeaderContext)
{
    vespalib::FileHeader h(FILE_HEADERSIZE_ALIGNMENT); // 64 byte alignment on bitvector.dat header
    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(h, _datFile->GetFileName());
    h.putTag(Tag("docIdLimit", _docIdLimit));
    h.putTag(Tag("numKeys", _numKeys));
    h.putTag(Tag("frozen", 0));
    h.putTag(Tag("fileBitSize", 0));
    h.putTag(Tag("desc", "Bitvector data file"));
    _datFile->SetPosition(0);
    _datHeaderLen = h.writeFile(*_datFile);
    _datFile->Flush();
}


void
BitVectorFileWrite::updateDatHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(FILE_HEADERSIZE_ALIGNMENT);
    typedef vespalib::GenericHeader::Tag Tag;
    readHeader(h, _datFile->GetFileName());
    FileHeaderContext::setFreezeTime(h);
    h.putTag(Tag("numKeys", _numKeys));
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    _datFile->Flush();
    _datFile->Sync();
    assert(h.getSize() == _datHeaderLen);
    _datFile->SetPosition(0);
    h.writeFile(*_datFile);
    _datFile->Flush();
    _datFile->Sync();
}


void
BitVectorFileWrite::addWordSingle(uint64_t wordNum,
                                  const BitVector &bitVector)
{
    assert(bitVector.size() == _docIdLimit);
    bitVector.invalidateCachedCount();
    Parent::addWordSingle(wordNum, bitVector.countTrueBits());
    _datFile->WriteBuf(bitVector.getStart(),
                       bitVector.getFileBytes());
}


void
BitVectorFileWrite::flush()
{
    Parent::flush();
    _datFile->Flush();
}


void
BitVectorFileWrite::sync()
{
    flush();
    Parent::syncCommon();
    _datFile->Sync();
}


void
BitVectorFileWrite::close()
{
    size_t bitmapbytes = BitVector::getFileBytes(_docIdLimit);

    if (_datFile != NULL) {
        if (_datFile->IsOpened()) {
            uint64_t pos = _datFile->GetPosition();
            assert(pos == static_cast<uint64_t>(_numKeys) *
                   static_cast<uint64_t>(bitmapbytes) + _datHeaderLen);
            (void) bitmapbytes;
            _datFile->alignEndForDirectIO();
            updateDatHeader(pos * 8);
            _datFile->Close();
        }
        delete _datFile;
        _datFile = NULL;
    }
    Parent::close();
}

BitVectorCandidate::~BitVectorCandidate() {
}

void
BitVectorCandidate::checkPointWrite(nbostream &out)
{
    uint32_t docIdLimit = _bv->size();
    out << docIdLimit << _numDocs << _bitVectorLimit;
    out.saveVector(_array);
    if (getCrossedBitVectorLimit())
        out << *_bv;
}


void
BitVectorCandidate::checkPointRead(nbostream &in)
{
    uint32_t docIdLimit = _bv->size();
    uint32_t checkDocIdLimit;
    uint32_t checkBitVectorLimit;
    in >> checkDocIdLimit >> _numDocs >> checkBitVectorLimit;
    assert(checkDocIdLimit == docIdLimit);
    (void) docIdLimit;
    assert(checkBitVectorLimit == _bitVectorLimit);
    in.restoreVector(_array);
    if (getCrossedBitVectorLimit()) {
        in >> *_bv;
    } else {
        _bv->clear();
    }
}

}
