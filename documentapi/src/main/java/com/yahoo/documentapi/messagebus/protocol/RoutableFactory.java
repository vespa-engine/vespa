// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.messagebus.Routable;

/**
 * <p>This interface defines the necessary methods of a routable factory that can be plugged into a {@link
 * DocumentProtocol} using the {@link DocumentProtocol#putRoutableFactory(int, RoutableFactory,
 * com.yahoo.component.VersionSpecification)} method. </p>
 *
 * <p>Notice that no routable type is passed to the
 * {@link #decode(DocumentDeserializer)} method, so
 * you may NOT share a factory across multiple routable types. To share serialization logic between factory use a common
 * superclass or composition with a common serialization utility.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface RoutableFactory {

    /**
     * <p>This method encodes the content of the given routable into a byte buffer that can later be decoded using the
     * {@link #decode(DocumentDeserializer)} method.</p> <p>Return false to signal failure.</p>
     * <p>This method is NOT exception safe.</p>
     *
     * @param obj The routable to encode.
     * @param out The buffer to write into.
     * @return True if the routable could be encoded.
     */
    boolean encode(Routable obj, DocumentSerializer out);

    /**
     * <p>Encode a message type and object payload to a byte array. This is an alternative,
     * optional method to {@link #encode(Routable, DocumentSerializer)}, but which defers all
     * buffer management to the callee. This allows protocol implementations to make more
     * efficient use of memory, as they do not have to deal with DocumentSerializer indirections.</p>
     *
     * <p>Implementations <strong>must</strong> ensure that the first 4 bytes of the returned
     * byte array contain a 32-bit integer (in network order) equal to the provided msgType value.</p>
     *
     * @param msgType A positive integer indicating the concrete message type of obj.
     * @param obj The message to encode.
     * @return A byte buffer encapsulating the message type and the serialized representation
     *         of obj, or null if encoding failed.
     */
    default byte[] encode(int msgType, Routable obj) {
        var out = DocumentSerializerFactory.createHead(new GrowableByteBuffer(8192));
        out.putInt(null, msgType);
        if (!encode(obj, out)) {
            return null;
        }
        byte[] ret = new byte[out.getBuf().position()];
        out.getBuf().rewind();
        out.getBuf().get(ret);
        return ret;
    }

    /**
     * <p>This method decodes the given byte buffer to a routable.</p> <p>Return false to signal failure.</p> <p>This
     * method is NOT exception safe.</p>
     *
     * @param in        The buffer to read from.
     * @return The decoded routable.
     */
    Routable decode(DocumentDeserializer in);

}
