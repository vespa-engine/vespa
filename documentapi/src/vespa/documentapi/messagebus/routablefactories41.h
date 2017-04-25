// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/messages/feedmessage.h>
#include <vespa/documentapi/messagebus/messages/feedreply.h>
#include <vespa/messagebus/routable.h>
#include <vespa/messagebus/blob.h>
#include <vespa/messagebus/blobref.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

namespace documentapi {

/**
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement factories for the document
 * routable. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 */
class RoutableFactories41 {
private:
    RoutableFactories41() { /* abstract */ }

public:

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * This is a complement for the vespalib::GrowableByteBuffer.putString() method.
     *
     * @param in The byte buffer to read from.
     * @return The decoded string.
     */
    static string decodeString(document::ByteBuffer &in);

    /**
     * This is a complement for the vespalib::GrowableByteBuffer.putBoolean() method.
     *
     * @param in The byte buffer to read from.
     * @return The decoded bool.
     */
    static bool decodeBoolean(document::ByteBuffer &in);

    /**
     * Convenience method to decode a 32-bit int from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded int.
     */
    static int32_t decodeInt(document::ByteBuffer &in);

    /**
     * Convenience method to decode a 64-bit int from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded int.
     */
    static int64_t decodeLong(document::ByteBuffer &in);

    /**
     * Convenience method to decode a document id from the given byte buffer.
     *
     * @param in The byte buffer to read from.
     * @return The decoded document id.
     */
    static document::DocumentId decodeDocumentId(document::ByteBuffer &in);

    /**
     * Convenience method to encode a document id to the given byte buffer.
     *
     * @param id  The document id to encode.
     * @param out The byte buffer to write to.
     */
    static void encodeDocumentId(const document::DocumentId &id, vespalib::GrowableByteBuffer &out);
};

}

