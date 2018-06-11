// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "protocol.h"
#include <lz4.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.protocol");

using namespace vespalib;
using vespalib::alloc::Alloc;
using namespace vespalib::slime;

namespace config {
namespace protocol {
namespace v2 {

const Memory REQUEST_VERSION = "version";
const Memory REQUEST_DEF_NAME = "defName";
const Memory REQUEST_DEF_NAMESPACE = "defNamespace";
const Memory REQUEST_DEF_MD5 = "defMD5";
const Memory REQUEST_DEF_CONTENT = "defContent";
const Memory REQUEST_CLIENT_CONFIGID = "configId";
const Memory REQUEST_CLIENT_HOSTNAME = "clientHostname";
const Memory REQUEST_CONFIG_MD5 = "configMD5";
const Memory REQUEST_CURRENT_GENERATION = "currentGeneration";
const Memory REQUEST_WANTED_GENERATION = "wantedGeneration";
const Memory REQUEST_TIMEOUT = "timeout";
const Memory REQUEST_TRACE = "trace";
const Memory REQUEST_VESPA_VERSION = "vespaVersion";

const Memory RESPONSE_VERSION = "version";
const Memory RESPONSE_DEF_NAME = "defName";
const Memory RESPONSE_DEF_NAMESPACE = "defNamespace";
const Memory RESPONSE_DEF_MD5 = "defMD5";
const Memory RESPONSE_CONFIGID = "configId";
const Memory RESPONSE_CLIENT_HOSTNAME = "clientHostname";
const Memory RESPONSE_CONFIG_MD5 = "configMD5";
const Memory RESPONSE_CONFIG_GENERATION = "generation";
const Memory RESPONSE_PAYLOAD = "payload";
const Memory RESPONSE_TRACE = "trace";
const Memory RESPONSE_INTERNAL_REDEPLOY = "internalRedeploy";

const Inspector &
extractPayload(const Slime & data)
{
    const Inspector & payload(data.get()[RESPONSE_PAYLOAD]);
    if (LOG_WOULD_LOG(debug)) {
        LOG(debug, "payload: %s", payload.toString().c_str());
    }
    return payload;
}

}

namespace v3 {
const Memory REQUEST_COMPRESSION_TYPE = "compressionType";
const Memory RESPONSE_COMPRESSION_INFO = "compressionInfo";
const Memory RESPONSE_COMPRESSION_INFO_TYPE = "compressionType";
const Memory RESPONSE_COMPRESSION_INFO_UNCOMPRESSED_SIZE = "uncompressedSize";

DecompressedData
decompress_lz4(const char * input, uint32_t inputLen, int uncompressedLength)
{
    Alloc memory( Alloc::alloc(uncompressedLength));
    int sz = LZ4_decompress_safe(input, static_cast<char *>(memory.get()), inputLen, uncompressedLength);
    if (sz >= 0 && sz != uncompressedLength) {
        if (LOG_WOULD_LOG(debug)) {
            LOG(debug, "Returned compressed size (%d) is not the same as uncompressed size(%d)", sz, uncompressedLength);
        }
        Alloc copy = memory.create(sz);
        memcpy(copy.get(), memory.get(), sz);
        memory = std::move(copy);
    }
    assert(sz >= 0);
    return DecompressedData(std::move(memory), static_cast<uint32_t>(sz));
}

DecompressedData
decompress(const char * input, uint32_t len, const CompressionType & compressionType, uint32_t uncompressedLength)
{
    // No payload means no data
    if (len == 0) {
        return DecompressedData(Memory(input, len), len);
    }
    switch (compressionType) {
        case CompressionType::LZ4:
            return decompress_lz4(input, len, uncompressedLength);
            break;
        case CompressionType::UNCOMPRESSED:
        default:
            return DecompressedData(Memory(input, len), len);
            break;
    }
}

}

const int DEFAULT_PROTOCOL_VERSION = 3;
const int DEFAULT_TRACE_LEVEL = 0;

int
verifyProtocolVersion(int protocolVersion)
{
    if (1 == protocolVersion || 2 == protocolVersion || 3 == protocolVersion) {
        return protocolVersion;
    }
    LOG(info, "Unknown protocol version %d, using default (%d)", protocolVersion, DEFAULT_PROTOCOL_VERSION);
    return DEFAULT_PROTOCOL_VERSION;
}

int
readProtocolVersion()
{
    int protocolVersion = DEFAULT_PROTOCOL_VERSION;
    char *versionStringPtr = getenv("VESPA_CONFIG_PROTOCOL_VERSION");
    if (versionStringPtr == NULL) {
        versionStringPtr = getenv("services__config_protocol_version_override");
    }
    if (versionStringPtr != NULL) {
        std::stringstream versionString(versionStringPtr);
        versionString >> protocolVersion;
    }
    return verifyProtocolVersion(protocolVersion);
}

int
readTraceLevel()
{
    int traceLevel = DEFAULT_TRACE_LEVEL;
    char *traceLevelStringPtr = getenv("VESPA_CONFIG_PROTOCOL_TRACELEVEL");
    if (traceLevelStringPtr == NULL) {
        traceLevelStringPtr = getenv("services__config_protocol_tracelevel");
    }
    if (traceLevelStringPtr != NULL) {
        std::stringstream traceLevelString(traceLevelStringPtr);
        traceLevelString >> traceLevel;
    }
    return traceLevel;
}

CompressionType
readProtocolCompressionType()
{
    CompressionType type = CompressionType::LZ4;
    char *compressionTypeStringPtr = getenv("VESPA_CONFIG_PROTOCOL_COMPRESSION");
    if (compressionTypeStringPtr == NULL) {
        compressionTypeStringPtr = getenv("services__config_protocol_compression");
    }
    if (compressionTypeStringPtr != NULL) {
        type = stringToCompressionType(vespalib::string(compressionTypeStringPtr));
    }
    return type;
}

}
}
