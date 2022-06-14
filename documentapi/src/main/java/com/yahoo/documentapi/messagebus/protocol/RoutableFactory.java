// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;
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
     * <p>This method decodes the given byte buffer to a routable.</p> <p>Return false to signal failure.</p> <p>This
     * method is NOT exception safe.</p>
     *
     * @param in        The buffer to read from.
     * @return The decoded routable.
     */
    Routable decode(DocumentDeserializer in);

}
