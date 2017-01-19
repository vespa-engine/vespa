// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "routablefactories41.h"
#include <vespa/document/document.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vespalib/objects/nbostream.h>

using vespalib::nbostream;

namespace documentapi {

string
RoutableFactories41::decodeString(document::ByteBuffer &in)
{
    int32_t len = decodeInt(in);
    string ret = string(in.getBufferAtPos(), len);
    in.incPos(len);
    return ret;
}

bool
RoutableFactories41::decodeBoolean(document::ByteBuffer &in)
{
    char ret;
    in.getBytes(&ret, 1);
    return (bool)ret;
}

int32_t
RoutableFactories41::decodeInt(document::ByteBuffer &in)
{
    int32_t ret;
    in.getIntNetwork(ret);
    return ret;
}

int64_t
RoutableFactories41::decodeLong(document::ByteBuffer &in)
{
    int64_t ret;
    in.getLongNetwork(ret);
    return ret;
}

document::DocumentId
RoutableFactories41::decodeDocumentId(document::ByteBuffer &in)
{
    nbostream stream(in.getBufferAtPos(), in.getRemaining());
    document::DocumentId ret(stream);
    in.incPos(stream.rp());
    return ret;
}

void
RoutableFactories41::encodeDocumentId(const document::DocumentId &id, vespalib::GrowableByteBuffer &out)
{
    string str = id.toString();
    out.putBytes(str.c_str(), str.size() + 1);
}

}
