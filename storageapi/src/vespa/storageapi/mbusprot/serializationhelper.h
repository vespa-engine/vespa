// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/types.h>
#include <vespa/document/base/globalid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

namespace storage::mbusprot {

class SerializationHelper
{
public:
    static int64_t getLong(document::ByteBuffer& buf) {
        int64_t tmp;
        buf.getLongNetwork(tmp);
        return tmp;
    }

    static int32_t getInt(document::ByteBuffer& buf) {
        int32_t tmp;
        buf.getIntNetwork(tmp);
        return tmp;
    }

    static int16_t getShort(document::ByteBuffer& buf) {
        int16_t tmp;
        buf.getShortNetwork(tmp);
        return tmp;
    }

    static uint8_t getByte(document::ByteBuffer& buf) {
        uint8_t tmp;
        buf.getByte(tmp);
        return tmp;
    }

    static vespalib::stringref getString(document::ByteBuffer& buf) {
        uint32_t tmp;
        buf.getIntNetwork((int32_t&) tmp);
        const char * p = buf.getBufferAtPos();
        buf.incPos(tmp);
        vespalib::stringref s(p, tmp);
        return s;
    }

    static bool getBoolean(document::ByteBuffer& buf) {
        uint8_t tmp;
        buf.getByte(tmp);
        return (tmp == 1);
    }

    static api::ReturnCode getReturnCode(document::ByteBuffer& buf) {
        api::ReturnCode::Result result = (api::ReturnCode::Result) getInt(buf);
        vespalib::stringref message = getString(buf);
        return api::ReturnCode(result, message);
    }

    static void putReturnCode(const api::ReturnCode& code, vespalib::GrowableByteBuffer& buf)
    {
        buf.putInt(code.getResult());
        buf.putString(code.getMessage());
    }

    static const uint32_t BUCKET_INFO_SERIALIZED_SIZE = sizeof(uint32_t) * 3;

    static document::GlobalId getGlobalId(document::ByteBuffer& buf) {
        std::vector<char> buffer(getShort(buf));
        for (uint32_t i=0; i<buffer.size(); ++i) {
            buffer[i] = getByte(buf);
        }
        return document::GlobalId(&buffer[0]);
    }

    static void putGlobalId(const document::GlobalId& gid, vespalib::GrowableByteBuffer& buf)
    {
        buf.putShort(document::GlobalId::LENGTH);
        for (uint32_t i=0; i<document::GlobalId::LENGTH; ++i) {
            buf.putByte(gid.get()[i]);
        }
    }
    static document::Document::UP getDocument(document::ByteBuffer& buf, const document::DocumentTypeRepo& repo)
    {
        uint32_t size = getInt(buf);
        if (size == 0) {
            return document::Document::UP();
        } else {
            document::ByteBuffer bbuf(buf.getBufferAtPos(), size);
            buf.incPos(size);
            return document::Document::UP(new document::Document(repo, bbuf));
        }
    }

    static void putDocument(document::Document* doc, vespalib::GrowableByteBuffer& buf)
    {
        if (doc) {
            vespalib::nbostream stream;
            doc->serialize(stream);
            buf.putInt(stream.size());
            buf.putBytes(stream.peek(), stream.size());
        } else {
            buf.putInt(0);
        }
    }

};

}
