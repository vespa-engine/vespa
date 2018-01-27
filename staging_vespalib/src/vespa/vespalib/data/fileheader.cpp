// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fileheader.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".fileheader");

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION(IllegalHeaderException, vespalib::Exception);

const uint32_t           GenericHeader::MAGIC(0x5ca1ab1e);
const uint32_t           GenericHeader::VERSION(1);
const GenericHeader::Tag GenericHeader::EMPTY;
const size_t             ALIGNMENT=0x1000;

GenericHeader::Tag::~Tag()  = default;
GenericHeader::Tag::Tag(const Tag &) = default;
GenericHeader::Tag & GenericHeader::Tag::operator=(const Tag &) = default;

GenericHeader::Tag::Tag() :
    _type(TYPE_EMPTY),
    _name(""),
    _fVal(0),
    _iVal(0),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, float val) :
    _type(TYPE_FLOAT),
    _name(name),
    _fVal(val),
    _iVal(0),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, double val) :
    _type(TYPE_FLOAT),
    _name(name),
    _fVal(val),
    _iVal(0),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, int8_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, uint8_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, int16_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, uint16_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, int32_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, uint32_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, int64_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, uint64_t val) :
    _type(TYPE_INTEGER),
    _name(name),
    _fVal(0),
    _iVal(val),
    _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, bool val)
    : _type(TYPE_INTEGER),
      _name(name),
      _fVal(0),
      _iVal(val ? 1 : 0),
      _sVal("")
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, const char *val)
    : _type(TYPE_STRING),
      _name(name),
      _fVal(0),
      _iVal(0),
      _sVal(val)
{
    // empty
}

GenericHeader::Tag::Tag(const vespalib::string &name, const vespalib::string &val) :
    _type(TYPE_STRING),
    _name(name),
    _fVal(0),
    _iVal(0),
    _sVal(val)
{
    // empty
}

size_t
GenericHeader::Tag::getSize() const
{
    size_t ret = _name.size() + 2;
    switch (_type) {
    case TYPE_FLOAT:
    case TYPE_INTEGER:
        ret += 8;
        break;
    case TYPE_STRING:
        ret += _sVal.size() + 1;
        break;
    default:
        LOG_ASSERT(false);
    }
    return ret;
}

size_t
GenericHeader::Tag::read(DataBuffer &buf)
{
    char *pos = buf.getData();
    vespalib::string name(pos);
    buf.moveDataToDead(name.size() + 1);
    uint8_t type = buf.readInt8();
    switch (type) {
    case TYPE_FLOAT:
        _fVal = buf.readDouble();
        break;
    case TYPE_INTEGER:
        _iVal = buf.readInt64();
        break;
    case TYPE_STRING:
        _sVal = vespalib::string(buf.getData());
        buf.moveDataToDead(_sVal.size() + 1);
        break;
    default:
        throw IllegalHeaderException("Can not deserialize empty tag.");
    }
    _name = name; // assign here for exception safety
    _type = (Type)type;
    return buf.getData() - pos;
}

size_t
GenericHeader::Tag::write(DataBuffer &buf) const
{
    int pos = buf.getDataLen();
    buf.writeBytes(_name.c_str(), _name.size() + 1);
    buf.writeInt8(_type);
    switch (_type) {
    case TYPE_FLOAT:
        buf.writeDouble(_fVal);
        break;
    case TYPE_INTEGER:
        buf.writeInt64(_iVal);
        break;
    case TYPE_STRING:
        buf.writeBytes(_sVal.c_str(), _sVal.size() + 1);
        break;
    default:
        LOG_ASSERT(false);
    }
    return buf.getDataLen() - pos;
}

GenericHeader::BufferReader::BufferReader(DataBuffer &buf) :
    _buf(buf)
{
    // empty
}

size_t
GenericHeader::BufferReader::getData(char *buf, size_t len)
{
    if (len > _buf.getDataLen()) {
        len = _buf.getDataLen();
    }
    _buf.readBytes(buf, len);
    return len;
}

GenericHeader::BufferWriter::BufferWriter(DataBuffer &buf) :
    _buf(buf)
{
    // empty
}

size_t
GenericHeader::BufferWriter::putData(const char *buf, size_t len)
{
    if (len > _buf.getFreeLen()) {
        len = _buf.getFreeLen();
    }
    _buf.writeBytes(buf, len);
    return len;
}


GenericHeader::MMapReader::MMapReader(const char *buf, size_t sz)
    : _buf(buf),
      _sz(sz)
{
}


size_t
GenericHeader::MMapReader::getData(char *buf, size_t len)
{
    size_t clen = std::min(len, _sz);
    memcpy(buf, _buf, clen);
    _buf += clen;
    _sz -= clen;
    return clen;
}


GenericHeader::GenericHeader() :
    _tags()
{
    // empty
}

GenericHeader::~GenericHeader() { }

const GenericHeader::Tag &
GenericHeader::getTag(size_t idx) const
{
    if (idx >= _tags.size()) {
        return EMPTY;
    }
    TagMap::const_iterator it = _tags.begin();
    std::advance(it, idx);
    return it->second;
}

const GenericHeader::Tag &
GenericHeader::getTag(const vespalib::string &key) const
{
    TagMap::const_iterator it = _tags.find(key);
    if (it == _tags.end()) {
        return EMPTY;
    }
    return it->second;
}

bool
GenericHeader::hasTag(const vespalib::string &key) const
{
    return _tags.find(key) != _tags.end();
}

bool
GenericHeader::putTag(const GenericHeader::Tag &tag)
{
    const vespalib::string &key = tag.getName();
    TagMap::iterator it = _tags.find(key);
    if (it != _tags.end()) {
        it->second = tag;
        return false;
    }
    _tags.insert(TagMap::value_type(key, tag));
    return true;
}
bool
GenericHeader::removeTag(const vespalib::string &key)
{
    TagMap::iterator it = _tags.find(key);
    if (it == _tags.end()) {
        return false;
    }
    _tags.erase(it);
    return true;
}


size_t
GenericHeader::getMinSize(void)
{
    return 4 /* magic */ + 4 /* size */ + 4 /* version */ + 4 /* num tags */;
}


size_t
GenericHeader::getSize() const
{
    size_t ret = getMinSize();
    for (TagMap::const_iterator it = _tags.begin();
         it != _tags.end(); ++it)
    {
        ret += it->second.getSize();
    }
    return ret;
}


size_t
GenericHeader::readSize(IDataReader &reader)
{
    size_t hhSize = getMinSize();
    DataBuffer buf(hhSize, ALIGNMENT);
    size_t numBytesRead = reader.getData(buf.getFree(), hhSize);
    buf.moveFreeToData(numBytesRead);

    if (numBytesRead < hhSize) {
        throw IllegalHeaderException("Failed to read header info.");
    }
    uint32_t magic = buf.readInt32();
    if (magic != MAGIC) {
        throw IllegalHeaderException("Failed to verify magic bits.");
    }
    uint32_t numBytesTotal = buf.readInt32();
    if (numBytesTotal == 0) {
        throw IllegalHeaderException("Failed to read header size.");
    }
    if (numBytesTotal < getMinSize()) {
        throw IllegalHeaderException("Failed to verify header size.");
    }
    uint32_t version = buf.readInt32();
    if (version != VERSION) {
        throw IllegalHeaderException("Failed to verify header version.");
    }
    return numBytesTotal;
}


size_t
GenericHeader::read(IDataReader &reader)
{
    size_t bufLen = 1024 * 32;
    DataBuffer buf(bufLen, ALIGNMENT);
    size_t numBytesRead = reader.getData(buf.getFree(), bufLen);
    buf.moveFreeToData(numBytesRead);

    if (numBytesRead < 4 /* magic */ + 4 /* size */) {
        throw IllegalHeaderException("Failed to read header info.");
    }
    uint32_t magic = buf.readInt32();
    if (magic != MAGIC) {
        throw IllegalHeaderException("Failed to verify magic bits.");
    }
    uint32_t numBytesTotal = buf.readInt32();
    if (numBytesTotal == 0) {
        throw IllegalHeaderException("Failed to read header size.");
    }
    if (numBytesTotal < getMinSize()) {
        throw IllegalHeaderException("Failed to verify header size.");
    }
    if (numBytesRead < numBytesTotal) {
        LOG(debug, "Read %d of %d header bytes, performing backfill.",
            (uint32_t)numBytesRead, numBytesTotal);
        uint32_t numBytesRemain = numBytesTotal - numBytesRead;
        buf.ensureFree(numBytesRemain);
        LOG(debug, "Reading remaining %d bytes of header.", numBytesRemain);
        numBytesRead += reader.getData(buf.getFree(), numBytesRemain);
        if (numBytesRead != numBytesTotal) {
            throw IllegalHeaderException("Failed to read full header.");
        }
        buf.moveFreeToData(numBytesRemain);
    } else {
        buf.moveDataToFree(numBytesRead - numBytesTotal);
    }

    uint32_t version = buf.readInt32();
    if (version != VERSION) {
        throw IllegalHeaderException("Failed to verify header version.");
    }
    uint32_t numTags = buf.readInt32();
    TagMap tags;
    for (uint32_t i = 0; i < numTags; ++i) {
        Tag tag;
        tag.read(buf);
        tags.insert(TagMap::value_type(tag.getName(), tag));
    }
    _tags.swap(tags);
    return numBytesTotal;
}

size_t
GenericHeader::write(IDataWriter &writer) const
{
    size_t numBytesTotal = getSize();
    DataBuffer buf(numBytesTotal, ALIGNMENT);
    buf.writeInt32(MAGIC);
    buf.writeInt32((uint32_t)numBytesTotal);
    buf.writeInt32(VERSION);
    buf.writeInt32((uint32_t)_tags.size());
    uint32_t numBytesInBuf = 16;
    for (TagMap::const_iterator it = _tags.begin();
         it != _tags.end(); ++it)
    {
        numBytesInBuf += it->second.write(buf);
    }
    if (numBytesInBuf < numBytesTotal) {
        buf.zeroFill(numBytesTotal - numBytesInBuf);
    }
    size_t numBytesWritten = writer.putData(buf.getData(), numBytesTotal);
    if (numBytesWritten != numBytesTotal) {
        throw IllegalHeaderException("Failed to write header.");
    }
    return numBytesWritten;
}

FileHeader::FileReader::FileReader(FastOS_FileInterface &file) :
    _file(file)
{
    // empty
}

size_t
FileHeader::FileReader::getData(char *buf, size_t len)
{
    LOG_ASSERT(_file.IsOpened());
    LOG_ASSERT(_file.IsReadMode());

    return _file.Read(buf, len);
}

FileHeader::FileWriter::FileWriter(FastOS_FileInterface &file) :
    _file(file)
{
    // empty
}

size_t
FileHeader::FileWriter::putData(const char *buf, size_t len)
{
    LOG_ASSERT(_file.IsOpened());
    LOG_ASSERT(_file.IsWriteMode());

    return _file.Write2(buf, len);
}

FileHeader::FileHeader(size_t alignTo, size_t minSize) :
    _alignTo(alignTo),
    _minSize(minSize),
    _fileSize(0)
{
    // empty
}

size_t
FileHeader::getSize() const
{
    size_t ret = GenericHeader::getSize();
    if (_fileSize > ret) {
        return _fileSize;
    }
    if (_minSize > ret) {
        return _minSize;
    }
    size_t pad = ret % _alignTo;
    return ret + (pad > 0 ? _alignTo - pad : 0);
}

size_t
FileHeader::readFile(FastOS_FileInterface &file)
{
    FileReader reader(file);
    return GenericHeader::read(reader);
}

size_t
FileHeader::writeFile(FastOS_FileInterface &file) const
{
    FileWriter writer(file);
    return GenericHeader::write(writer);
}

size_t
FileHeader::rewriteFile(FastOS_FileInterface &file)
{
    LOG_ASSERT(file.IsOpened());
    LOG_ASSERT(file.IsReadMode());
    LOG_ASSERT(file.IsWriteMode());

    // Store current position in file.
    int64_t pos = file.GetPosition();
    if (pos != 0) {
        file.SetPosition(0);
    }

    // Assert that header size agrees with file content.
    FileReader reader(file);
    size_t wantSize = 4 /* magic */ + 4 /* size */;
    DataBuffer buf(wantSize, ALIGNMENT);
    size_t numBytesRead = reader.getData(buf.getFree(), wantSize);
    if (numBytesRead < wantSize) {
        throw IllegalHeaderException("Failed to read header info.");
    }
    uint32_t magic = buf.readInt32();
    if (magic != MAGIC) {
        throw IllegalHeaderException("Failed to verify magic bits.");
    }
    uint32_t size = buf.readInt32();
    if (size == 0) {
        throw IllegalHeaderException("Failed to read header size.");
    }
    if (size < GenericHeader::getSize()) {
        throw IllegalHeaderException("Failed to rewrite resized header.");
    }
    _fileSize = size;

    // Write new header and reset file position.
    file.SetPosition(0);
    size_t ret = writeFile(file);
    if (file.GetPosition() != pos) {
        file.SetPosition(pos);
    }
    return ret;
}

vespalib::asciistream &
operator<<(vespalib::asciistream &out, const GenericHeader::Tag &tag)
{
    switch (tag.getType()) {
    case GenericHeader::Tag::TYPE_FLOAT:
        out << tag.asFloat();
        break;
    case GenericHeader::Tag::TYPE_INTEGER:
        out << tag.asInteger();
        break;
    case GenericHeader::Tag::TYPE_STRING:
        out << tag.asString();
        break;
    default:
        LOG_ASSERT(false);
    }
    return out;
}

} // namespace
