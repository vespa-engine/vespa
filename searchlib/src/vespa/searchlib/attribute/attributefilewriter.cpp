// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefilewriter.h"
#include "attributefilebufferwriter.h"
#include "attribute_header.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attributefilewriter");

using search::common::FileHeaderContext;
using vespalib::getLastErrorString;

namespace search {

namespace {

const uint32_t headerAlign = 4_Ki;
const uint32_t MIN_ALIGNMENT = 4_Ki;

void
writeDirectIOAligned(FastOS_FileInterface &file, const void *buf, size_t length)
{
    const char * data(static_cast<const char *>(buf));
    size_t remaining(length);
    for (size_t maxChunk(2_Mi); maxChunk >= MIN_ALIGNMENT; maxChunk >>= 1) {
        for ( ; remaining > maxChunk; remaining -= maxChunk, data += maxChunk) {
            file.WriteBuf(data, maxChunk);
        }
    }
    if (remaining > 0) {
        file.WriteBuf(data, remaining);
    }
}

void
updateHeader(const vespalib::string &name, uint64_t fileBitSize)
{
    vespalib::FileHeader h(headerAlign);
    FastOS_File f;
    f.OpenReadWrite(name.c_str());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}

/*
 * BufferWriter implementation that passes full buffers on to
 * AttributeFileWriter.
 */
class FileBackedBufferWriter : public AttributeFileBufferWriter
{
public:
    FileBackedBufferWriter(AttributeFileWriter &fileWriter);
    ~FileBackedBufferWriter() override;

    void onFlush(size_t nowLen) override;
};


FileBackedBufferWriter::FileBackedBufferWriter(AttributeFileWriter &fileWriter)
    : AttributeFileBufferWriter(fileWriter)
{ }

FileBackedBufferWriter::~FileBackedBufferWriter() = default;

void
FileBackedBufferWriter::onFlush(size_t nowLen) {
    // Note: Must use const ptr to indicate that buffer is pre-filled.
    auto buf(std::make_unique<BufferBuf>(static_cast<const void *>(_buf->getFree()), nowLen));
    assert(buf->getDataLen() == nowLen);
    assert(buf->getData() == _buf->getFree());
    _fileWriter.writeBuf(std::move(buf));
}

}

AttributeFileWriter::
AttributeFileWriter(const TuneFileAttributes &tuneFileAttributes,
                    const FileHeaderContext &fileHeaderContext,
                    const attribute::AttributeHeader &header,
                    const vespalib::string &desc)
    : _file(new FastOS_File()),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _header(header),
      _desc(desc),
      _fileBitSize(0)
{ }

AttributeFileWriter::~AttributeFileWriter() = default;

bool
AttributeFileWriter::open(const vespalib::string &fileName)
{
    if (_tuneFileAttributes._write.getWantSyncWrites()) {
        _file->EnableSyncWrites();
    }
    if (_tuneFileAttributes._write.getWantDirectIO()) {
        _file->EnableDirectIO();
    }
    _file->OpenWriteOnlyTruncate(fileName.c_str());
    if (!_file->IsOpened()) {
        LOG(error, "Could not open attribute vector '%s' for writing: %s",
            fileName.c_str(), getLastErrorString().c_str());
        return false;
    }
    writeHeader();
    return true;
}

void
AttributeFileWriter::writeHeader()
{
    vespalib::FileHeader header(headerAlign);
    _fileHeaderContext.addTags(header, _file->GetFileName());
    addTags(header);
    size_t headerLen = header.writeFile(*_file);
    assert((headerLen % MIN_ALIGNMENT) == 0);
    _fileBitSize = headerLen * 8;
}

void
AttributeFileWriter::addTags(vespalib::GenericHeader &header)
{
    _header.addTags(header);
    using Tag = vespalib::GenericHeader::Tag;
    header.putTag(Tag("desc", _desc));
}

AttributeFileWriter::Buffer
AttributeFileWriter::allocBuf(size_t size)
{
    return std::make_unique<BufferBuf>(size, MIN_ALIGNMENT);
}

void
AttributeFileWriter::writeBuf(Buffer buf)
{
    size_t bufLen = buf->getDataLen();
    // TODO: pad to DirectIO boundary when burning bridges
    writeDirectIOAligned(*_file, buf->getData(), bufLen);
    _fileBitSize += bufLen * 8;
}

void
AttributeFileWriter::close()
{
    if (_file->IsOpened()) {
        _file->Sync();
        _file->Close();
        updateHeader(_file->GetFileName(), _fileBitSize);
    }
}

std::unique_ptr<BufferWriter>
AttributeFileWriter::allocBufferWriter()
{
    return std::make_unique<FileBackedBufferWriter>(*this);
}

} // namespace search
