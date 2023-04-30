// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/exception.h>

namespace search {

class ChunkException : public vespalib::Exception
{
public:
    ChunkException(const vespalib::string & msg, vespalib::stringref location);
};

// This is an interface for implementing a chunk format
class ChunkFormat
{
public:
    virtual ~ChunkFormat();
    using UP = std::unique_ptr<ChunkFormat>;
    using CompressionConfig = vespalib::compression::CompressionConfig;
    vespalib::nbostream & getBuffer() { return _dataBuf; }
    const vespalib::nbostream & getBuffer() const { return _dataBuf; }

    /**
     * Will serialze your chunk.
     * @param lastSerial The last serial number of any entry in the packet.
     * @param compressed The buffer where the serialized data shall be placed.
     * @param compression What kind of compression shall be employed.
     */
    void pack(uint64_t lastSerial, vespalib::DataBuffer & compressed, CompressionConfig compression);
    /**
     * Will deserialize and create a representation of the uncompressed data.
     * param buffer Pointer to the serialized data
     * @param len Length of serialized data
     */
    static ChunkFormat::UP deserialize(const void * buffer, size_t len);
    /**
     * return the maximum size a packet can have. It allows correct size estimation
     * need for direct io alignment.
     * @param compression Compression config to be used.
     * @return maximum number of bytes a packet can take in serialized form.
     */
    size_t getMaxPackSize(CompressionConfig compression) const;
protected:
    /**
     * Constructor used when deserializing
     */
    ChunkFormat();
    /**
     * Constructor used when creating a new chunk.
     * @param maxSize The maximum size the chunk can take before it will need to be closed.
     */
    ChunkFormat(size_t maxSize);
    /**
     * Will deserialize and uncompress the body.
     * @param the potentially compressed stream.
     */
    void deserializeBody(vespalib::nbostream & is);
    /**
     * Wille compute and check the crc of the incoming stream.
     * Will start 1 byte earlier and stop 4 bytes ahead of end.
     * Thows exception if check fails.
     */
    void verifyCrc(const vespalib::nbostream & is, uint32_t expected) const;
private:
    /**
     * Used when serializing to obtain correct version.
     * @return version
     */
    virtual uint8_t getVersion() const = 0;
    /**
     * Used to compute maximum size needed for a serialized chunk.
     * @return size of header this format will produce.
     */
    virtual size_t getHeaderSize() const = 0;
    /**
     * Does this format require the length of the serialized data to be include.
     * Length will is inclusive. From and including version to end of crc.
     * @return if length is required.
     */
    virtual bool includeSerializedSize() const = 0;
    /**
     * Will compute the crc for verifying the data.
     * @param buf Start of buffer
     * @param sz Size of buffer
     * @return computed crc.
     */
    virtual uint32_t computeCrc(const void * buf, size_t sz) const = 0;
    /**
     * Allows each format to write its special stuff after the version byte.
     * Must be reflected in @getHeaderSize
     * @param buf Buffer to write into.
     */
    virtual void writeHeader(vespalib::DataBuffer & buf) const = 0;

    static void verifyCompression(uint8_t type);

    vespalib::nbostream _dataBuf;
};

} // namespace search

