// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/common/compressiontype.h>
#include <vespa/vespalib/util/alloc.h>

namespace vespalib {
    class Slime;
    namespace slime {
        class Inspector;
    }
}

namespace config {

namespace protocol {

int readProtocolVersion();
int readTraceLevel();
CompressionType readProtocolCompressionType();

struct Payload {
    virtual ~Payload() {}
    virtual const vespalib::slime::Inspector & getSlimePayload() const = 0;
};


namespace v2 {

extern const vespalib::Memory REQUEST_VERSION;
extern const vespalib::Memory REQUEST_DEF_NAME;
extern const vespalib::Memory REQUEST_DEF_NAMESPACE;
extern const vespalib::Memory REQUEST_DEF_MD5;
extern const vespalib::Memory REQUEST_DEF_CONTENT;
extern const vespalib::Memory REQUEST_CLIENT_CONFIGID;
extern const vespalib::Memory REQUEST_CLIENT_HOSTNAME;
extern const vespalib::Memory REQUEST_CONFIG_MD5;
extern const vespalib::Memory REQUEST_CURRENT_GENERATION;
extern const vespalib::Memory REQUEST_WANTED_GENERATION;
extern const vespalib::Memory REQUEST_TIMEOUT;
extern const vespalib::Memory REQUEST_TRACE;
extern const vespalib::Memory REQUEST_VESPA_VERSION;

extern const vespalib::Memory RESPONSE_VERSION;
extern const vespalib::Memory RESPONSE_DEF_NAME;
extern const vespalib::Memory RESPONSE_DEF_NAMESPACE;
extern const vespalib::Memory RESPONSE_DEF_MD5;
extern const vespalib::Memory RESPONSE_CONFIGID;
extern const vespalib::Memory RESPONSE_CLIENT_HOSTNAME;
extern const vespalib::Memory RESPONSE_CONFIG_MD5;
extern const vespalib::Memory RESPONSE_CONFIG_GENERATION;
extern const vespalib::Memory RESPONSE_PAYLOAD;
extern const vespalib::Memory RESPONSE_TRACE;
extern const vespalib::Memory RESPONSE_INTERNAL_REDEPLOY;

const vespalib::slime::Inspector & extractPayload(const vespalib::Slime & data);

}

namespace v3 {

extern const vespalib::Memory REQUEST_COMPRESSION_TYPE;
extern const vespalib::Memory RESPONSE_COMPRESSION_INFO;
extern const vespalib::Memory RESPONSE_COMPRESSION_INFO_TYPE;
extern const vespalib::Memory RESPONSE_COMPRESSION_INFO_UNCOMPRESSED_SIZE;

struct DecompressedData {
    DecompressedData(vespalib::alloc::Alloc mem, uint32_t sz)
        : memory(std::move(mem)),
          memRef(static_cast<const char *>(memory.get()), sz),
          size(sz)
    { }
    DecompressedData(const vespalib::Memory & mem, uint32_t sz)
        : memory(),
          memRef(mem),
          size(sz)
    {}

    vespalib::alloc::Alloc memory;
    vespalib::Memory memRef;
    uint32_t size;
};

DecompressedData decompress(const char * buf, uint32_t len, const CompressionType & compressionType, uint32_t uncompressedLength);

}

}

}

