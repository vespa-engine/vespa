// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplememfileiobuffer.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/log/log.h>

LOG_SETUP(".memfile.simpleiobuffer");

namespace storage::memfile {

namespace {

uint32_t calculateChecksum(const void* pos, uint32_t size) {
    vespalib::crc_32_type calculator;
    calculator.process_bytes(pos, size);
    return calculator.checksum();
}

}

constexpr size_t SimpleMemFileIOBuffer::WORKING_BUFFER_SIZE;

SimpleMemFileIOBuffer::SimpleMemFileIOBuffer(
        VersionSerializer& reader,
        vespalib::LazyFile::UP file,
        FileInfo::UP info,
        const FileSpecification& fileSpec,
        const Environment& env)
    : _reader(reader),
      _data(2),
      _workingBuffers(2),
      _file(std::move(file)),
      _fileInfo(std::move(info)),
      _fileSpec(fileSpec),
      _env(env),
      _options(env.acquireConfigReadLock().options())
{
}

SimpleMemFileIOBuffer::~SimpleMemFileIOBuffer() {}

void
SimpleMemFileIOBuffer::close()
{
    if (_file->isOpen()) {
        _file->close();
    }
}

const SimpleMemFileIOBuffer::Data&
SimpleMemFileIOBuffer::getData(DocumentPart part, DataLocation loc) const
{
    DataMap::const_iterator iter = _data[part].find(loc);

    if (iter == _data[part].end()) {
        std::ostringstream ost;
        ost << "Location " << loc
            << " was not found for " << (part == HEADER ? "Header" : "Body");
        throw PartNotCachedException(ost.str(), VESPA_STRLOC);
    }

    return iter->second;
}

document::Document::UP
SimpleMemFileIOBuffer::getDocumentHeader(
        const document::DocumentTypeRepo& repo,
        DataLocation loc) const
{
    const Data& data = getData(HEADER, loc);

    Document::UP doc(new Document());
    document::ByteBuffer buf(data.buf->getBuffer() + data.pos,
                             data.buf->getSize() - data.pos);

    doc->deserializeHeader(repo, buf);
    return doc;
}

document::DocumentId
SimpleMemFileIOBuffer::getDocumentId(DataLocation loc) const
{
    const Data& data = getData(HEADER, loc);

    const char* buf = data.buf->getBuffer() + data.pos + loc._size;
    buf -= 2 * sizeof(uint32_t);

    uint32_t nameLen = *(const uint32_t*)(buf);
    buf -= nameLen;

    return document::DocumentId(vespalib::stringref(buf, nameLen));
}

void
SimpleMemFileIOBuffer::readBody(
        const document::DocumentTypeRepo& repo,
        DataLocation loc,
        Document& doc) const
{
    const Data& data = getData(BODY, loc);

    document::ByteBuffer buf(data.buf->getBuffer() + data.pos,
                             data.buf->getSize() - data.pos);

    doc.deserializeBody(repo, buf);
}

DataLocation
SimpleMemFileIOBuffer::addLocation(DocumentPart part,
                                   BufferAllocation newData)
{
    if (!newData.getSharedBuffer().get()) {
        LOG(spam, "Not adding location since data is null");
        return DataLocation(0, 0);
    }

    DataMap& target = _data[part];
    DataLocation loc = DataLocation(_fileInfo->getBlockSize(part), newData.getSize());

    DataMap::reverse_iterator iter = target.rbegin();
    if (iter != target.rend() && iter->first.endPos() > loc._pos) {
        loc = DataLocation(iter->first.endPos(), newData.getSize());
    }

    std::pair<DataMap::iterator, bool> existing(
            target.insert(std::make_pair(loc, Data(newData.getSharedBuffer(),
                                                   newData.getBufferPosition(),
                                                   false))));
    if (!existing.second) {
        LOG(error, "%s: addLocation attempted %s insert with location %u,%u, "
            "but that location already exists",
            _fileSpec.toString().c_str(),
            getDocumentPartName(part),
            loc._pos,
            loc._size);
        assert(false);
    }

    LOG(spam, "%s: added %s at location %u,%u (buffer %p, position %u)",
        _fileSpec.getBucketId().toString().c_str(),
        getDocumentPartName(part),
        loc._pos,
        loc._size,
        newData.getSharedBuffer().get(),
        newData.getBufferPosition());
    return loc;
}

void
SimpleMemFileIOBuffer::HeaderChunkEncoder::bufferDocument(const Document& doc)
{
    assert(_serializedDoc.empty());
    doc.serializeHeader(_serializedDoc);
}

SimpleMemFileIOBuffer::HeaderChunkEncoder::HeaderChunkEncoder(const document::DocumentId& docId)
    : _serializedDoc(DEFAULT_STREAM_ALLOC_SIZE),
      _docId(docId.toString())
{ }
SimpleMemFileIOBuffer::HeaderChunkEncoder::~HeaderChunkEncoder() {}

/**
 * Buffer is comprised of the following:
 * - Document header blob (n bytes)
 * - CRC32 of header blob (4 bytes)
 * - Document Id (n bytes)
 * - Length of document id (4 bytes)
 * - CRC32 of document id and length (4 bytes)
 *
 * To a reader, the length of the header blob is inferred from length of
 * total buffer chunk minus the overhead by the doc id string and metadata in
 * the chunk trailer.
 */
void
SimpleMemFileIOBuffer::HeaderChunkEncoder::writeTo(BufferAllocation& buf) const
{
    assert(buf.getSize() >= encodedSize());
    // Note that docSize may be zero throughout this function.
    const uint32_t docSize = _serializedDoc.size();
    const uint32_t docChecksum = calculateChecksum(
            _serializedDoc.peek(), docSize);
    const uint32_t idLen = _docId.size();

    vespalib::crc_32_type nameChecksum;
    nameChecksum.process_bytes(_docId.c_str(), idLen);
    nameChecksum.process_bytes(reinterpret_cast<const char*>(&idLen),
                               sizeof(uint32_t));
    const uint32_t trailerChecksum = nameChecksum.checksum();

    memcpy(buf.getBuffer(), _serializedDoc.peek(), docSize);
    char* trailer = buf.getBuffer() + docSize;
    memcpy(trailer, &docChecksum, sizeof(uint32_t));
    trailer += sizeof(uint32_t);
    memcpy(trailer, _docId.c_str(), idLen);
    trailer += idLen;
    memcpy(trailer, &idLen, sizeof(uint32_t));
    trailer += sizeof(uint32_t);
    memcpy(trailer, &trailerChecksum, sizeof(uint32_t));
}

bool
SimpleMemFileIOBuffer::writeBackwardsCompatibleRemoves() const
{
    return !_options->_defaultRemoveDocType.empty();
}

document::Document::UP
SimpleMemFileIOBuffer::generateBlankDocument(
        const DocumentId& id,
        const document::DocumentTypeRepo& repo) const
{
    vespalib::string typeName(
            id.hasDocType() ? id.getDocType()
                            : _options->_defaultRemoveDocType);
    const document::DocumentType* docType(repo.getDocumentType(typeName));
    if (!docType) {
        throw vespalib::IllegalArgumentException(
                "Could not serialize document for remove with unknown "
                "doctype '" + typeName + "'");
    }
    return std::unique_ptr<Document>(new Document(*docType, id));
}

SimpleMemFileIOBuffer::BufferAllocation
SimpleMemFileIOBuffer::serializeHeader(const Document& doc)
{
    HeaderChunkEncoder encoder(doc.getId());
    encoder.bufferDocument(doc);
    BufferAllocation buf(allocateBuffer(HEADER, encoder.encodedSize()));
    encoder.writeTo(buf);

    return buf;
}

SimpleMemFileIOBuffer::BufferAllocation
SimpleMemFileIOBuffer::serializeDocumentIdOnlyHeader(
        const DocumentId& id,
        const document::DocumentTypeRepo& repo)
{
    HeaderChunkEncoder encoder(id);
    if (writeBackwardsCompatibleRemoves()) {
        Document::UP blankDoc(generateBlankDocument(id, repo));
        encoder.bufferDocument(*blankDoc);
    }
    BufferAllocation buf(allocateBuffer(HEADER, encoder.encodedSize()));
    encoder.writeTo(buf);

    return buf;
}

DataLocation
SimpleMemFileIOBuffer::addDocumentIdOnlyHeader(
        const DocumentId& docId,
        const document::DocumentTypeRepo& repo)
{
    return addLocation(HEADER, serializeDocumentIdOnlyHeader(docId, repo));
}

DataLocation
SimpleMemFileIOBuffer::addHeader(const Document& doc)
{
    return addLocation(HEADER, serializeHeader(doc));
}

SimpleMemFileIOBuffer::BufferAllocation
SimpleMemFileIOBuffer::serializeBody(const Document& doc)
{
    vespalib::nbostream output(5 * 1024);
    doc.serializeBody(output);

    if (output.empty()) {
        return BufferAllocation();
    }

    BufferAllocation val(allocateBuffer(BODY, output.size() + sizeof(uint32_t)));
    memcpy(val.getBuffer(), output.peek(), output.size());

    // Also append CRC32 of body block to buffer
    uint32_t checksum = calculateChecksum(output.peek(), output.size());
    char* trailer = val.getBuffer() + output.size();
    memcpy(trailer, &checksum, sizeof(uint32_t));

    return val;
}

SimpleMemFileIOBuffer::BufferAllocation
SimpleMemFileIOBuffer::allocateBuffer(DocumentPart part,
                                      uint32_t sz,
                                      SharedBuffer::Alignment align)
{
    // If the requested size is greater than or equal to our working buffer
    // size, simply allocate a separate buffer for it.
    if (sz >= WORKING_BUFFER_SIZE) {
        return BufferAllocation(std::make_shared<SharedBuffer>(sz), 0, sz);
    }

    SharedBuffer::SP &bufSP(_workingBuffers[part]);
    bool requireNewBlock = false;
    if (!bufSP.get()) {
        requireNewBlock = true;
    } else if (!bufSP->hasRoomFor(sz, align)) {
        requireNewBlock = true;
    }

    if (!requireNewBlock) {
        return BufferAllocation(bufSP,
                                static_cast<uint32_t>(bufSP->allocate(sz, align)),
                                sz);
    } else {
        auto newBuf = std::make_shared<SharedBuffer>(WORKING_BUFFER_SIZE);
        bufSP = newBuf;
        return BufferAllocation(newBuf,
                                static_cast<uint32_t>(newBuf->allocate(sz, align)),
                                sz);
    }
}

DataLocation
SimpleMemFileIOBuffer::addBody(const Document& doc)
{
    return addLocation(BODY, serializeBody(doc));
}

void
SimpleMemFileIOBuffer::clear(DocumentPart part)
{
    LOG(debug, "%s: cleared all data for part %s",
        _fileSpec.getBucketId().toString().c_str(),
        getDocumentPartName(part));
    _data[part].clear();
}

bool
SimpleMemFileIOBuffer::verifyConsistent() const
{
    return true;
}

void
SimpleMemFileIOBuffer::move(const FileSpecification& target)
{
    LOG(debug, "Moving %s -> %s",
        _file->getFilename().c_str(),
        target.getPath().c_str());
    _file->close();

    if (vespalib::fileExists(_file->getFilename())) {
        vespalib::rename(_file->getFilename(), target.getPath(), true, true);
    }

    _file.reset(
            new vespalib::LazyFile(target.getPath(), vespalib::File::DIRECTIO, true));
}

DataLocation
SimpleMemFileIOBuffer::copyCache(const MemFileIOInterface& source,
                                 DocumentPart part,
                                 DataLocation loc)
{
    if (loc._size == 0) {
        return loc;
    }

    const SimpleMemFileIOBuffer& srcBuf(
            static_cast<const SimpleMemFileIOBuffer&>(source));
    Data data = srcBuf.getData(part, loc);

    BufferAllocation val(allocateBuffer(part, loc._size));
    memcpy(val.getBuffer(), data.buf->getBuffer() + data.pos, loc._size);

    LOG(spam,
        "Copied cached data from %s to %s for location %u,%u buffer pos=%u",
        srcBuf._fileSpec.getBucketId().toString().c_str(),
        _fileSpec.getBucketId().toString().c_str(),
        loc._pos,
        loc._size,
        data.pos);

    return addLocation(part, val);
}


void
SimpleMemFileIOBuffer::cacheLocation(DocumentPart part,
                                     DataLocation loc,
                                     BufferType::SP buf,
                                     uint32_t bufferPos)
{
    LOG(spam,
        "%s: added existing %s buffer at location %u,%u "
        "buffer=%p buffer pos=%u",
        _fileSpec.toString().c_str(),
        getDocumentPartName(part),
        loc._pos,
        loc._size,
        buf.get(),
        bufferPos);
    _data[part][loc] = Data(std::move(buf), bufferPos, true);
}

bool
SimpleMemFileIOBuffer::isCached(DataLocation loc,
                                DocumentPart type) const
{
    if (loc._size == 0) {
        // Count zero-sized locations as cached
        return true;
    }

    return _data[type].find(loc) != _data[type].end();
}

bool
SimpleMemFileIOBuffer::isPersisted(DataLocation loc,
                                   DocumentPart type) const
{
    DataMap::const_iterator iter = _data[type].find(loc);

    // If the buffer doesn't know about the data at all,
    // we must assume it is already persisted. How else would the file
    // know about the location?
    if (iter == _data[type].end()) {
        return true;
    }

    return iter->second.persisted;
}

void
SimpleMemFileIOBuffer::ensureCached(Environment& env,
                                    DocumentPart part,
                                    const std::vector<DataLocation>& locations)
{
    std::vector<DataLocation> nonCached;
    nonCached.reserve(locations.size());

    for (uint32_t i = 0; i < locations.size(); ++i) {
        if (_data[part].find(locations[i]) == _data[part].end()) {
            nonCached.push_back(locations[i]);
        }
    }

    _reader.cacheLocations(*this, env, *_options, part, nonCached);
}

void
SimpleMemFileIOBuffer::persist(DocumentPart part,
                               DataLocation oldLoc,
                               DataLocation newLoc)
{
    Data newData = getData(part, oldLoc);
    newData.persisted = true;
    size_t erased = _data[part].erase(oldLoc);
    assert(erased > 0);
    (void) erased;
    _data[part][newLoc] = newData;

    LOG(spam, "%s: persisted %s for %u,%u -> %u,%u",
        _fileSpec.getBucketId().toString().c_str(),
        getDocumentPartName(part),
        oldLoc._pos, oldLoc._size,
        newLoc._pos, newLoc._size);
}

void
SimpleMemFileIOBuffer::remapAndPersistAllLocations(
        DocumentPart part,
        const std::map<DataLocation, DataLocation>& locs)
{
    DataMap remappedData;

    typedef std::map<DataLocation, DataLocation>::const_iterator Iter;
    for (Iter it(locs.begin()), e(locs.end()); it != e; ++it) {
        DataLocation oldLoc = it->first;
        DataLocation newLoc = it->second;

        LOG(spam, "%s: remapping %u,%u -> %u,%u",
            _fileSpec.getBucketId().toString().c_str(),
            oldLoc._pos, oldLoc._size,
            newLoc._pos, newLoc._size);

        Data newData = getData(part, oldLoc);
        newData.persisted = true;
        std::pair<DataMap::iterator, bool> inserted(
                remappedData.insert(std::make_pair(newLoc, newData)));
        assert(inserted.second);
    }
    _data[part].swap(remappedData);

    LOG(debug,
        "%s: remapped %zu locations. Discarded %zu locations that "
        "had no new mapping",
        _fileSpec.getBucketId().toString().c_str(),
        locs.size(),
        _data[part].size() - locs.size());
}

const char*
SimpleMemFileIOBuffer::getBuffer(DataLocation loc, DocumentPart part) const
{
    const Data& data = getData(part, loc);
    return data.buf->getBuffer() + data.pos;
}

uint32_t
SimpleMemFileIOBuffer::getSerializedSize(DocumentPart part,
                                         DataLocation loc) const
{
    if (part == HEADER) {
        const Data& data = getData(part, loc);
        assert(loc._size > sizeof(uint32_t)*3);
        const char* bufEnd = data.buf->getBuffer() + data.pos + loc._size;
        uint32_t docIdLen = *reinterpret_cast<const uint32_t*>(
                bufEnd - sizeof(uint32_t)*2);
        return loc._size - sizeof(uint32_t)*3 - docIdLen;
    } else {
        return loc._size - sizeof(uint32_t);
    }
}

size_t
SimpleMemFileIOBuffer::getCachedSize(DocumentPart part) const
{
    const DataMap& dm(_data[part]);
    vespalib::hash_set<const void*> seenBufs(dm.size());
    size_t ret = 0;
    for (DataMap::const_iterator it(dm.begin()), e(dm.end()); it != e; ++it) {
        if (seenBufs.find(it->second.buf->getBuffer()) != seenBufs.end()) {
            continue;
        }

        size_t bufSize = it->second.buf->getSize();
        // Account for (approximate) mmap overhead.
        bufSize = util::alignUpPow2<4096>(bufSize);
        ret += bufSize;
        seenBufs.insert(it->second.buf->getBuffer());
    }
    return ret;
}

}
