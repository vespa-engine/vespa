// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;

/**
 * Factory class for {@link com.yahoo.documentapi.messagebus.MessageBusDocumentAccess}.
 *
 * @author bjorncs
 */
public class DocumentAccessFactory {

    public MessageBusDocumentAccess createDocumentAccess(MessageBusParams messageBusParams) {
        return new MessageBusDocumentAccess(messageBusParams);
    }
}
