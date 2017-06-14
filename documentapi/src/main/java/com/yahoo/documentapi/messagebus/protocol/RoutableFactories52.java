// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.google.common.annotations.Beta;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;

import static com.yahoo.documentapi.messagebus.protocol.AbstractRoutableFactory.decodeString;
import static com.yahoo.documentapi.messagebus.protocol.AbstractRoutableFactory.encodeString;

/**
 * @author Vegard Sjonfjell
 */

@Beta
public abstract class RoutableFactories52 extends RoutableFactories51 {
    protected static class PutDocumentMessageFactory extends RoutableFactories51.PutDocumentMessageFactory {
        @Override
        protected void decodeInto(PutDocumentMessage msg, DocumentDeserializer buf) {
            super.decodeInto(msg, buf);
            decodeTasCondition(msg, buf);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            if (!super.doEncode(obj, buf)) {
                return false;
            }

            // If the serialized buffer exists, the test and set condition has already been encoded
            if (((PutDocumentMessage)obj).getSerializedBuffer() == null) {
                encodeTasCondition(buf, (TestAndSetMessage) obj);
            }

            return true;
       }
    }

    protected static class RemoveDocumentMessageFactory extends RoutableFactories51.RemoveDocumentMessageFactory {
        @Override
        protected void decodeInto(RemoveDocumentMessage msg, DocumentDeserializer buf) {
            super.decodeInto(msg, buf);
            decodeTasCondition(msg, buf);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            if (!super.doEncode(obj, buf)) {
                return false;
            }

            encodeTasCondition(buf, (TestAndSetMessage) obj);
            return true;
        }
    }

    protected static class UpdateDocumentMessageFactory extends RoutableFactories51.UpdateDocumentMessageFactory {
        @Override
        protected void decodeInto(UpdateDocumentMessage msg, DocumentDeserializer buf) {
            super.decodeInto(msg, buf);
            decodeTasCondition(msg, buf);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            if (!super.doEncode(obj, buf)) {
                return false;
            }

            // If the serialized buffer exists, the test and set condition has already been encoded
            if (((UpdateDocumentMessage)obj).getSerializedBuffer() == null) {
                encodeTasCondition(buf, (TestAndSetMessage) obj);
            }

            return true;
        }
    }

    static void decodeTasCondition(TestAndSetMessage msg, DocumentDeserializer buf) {
        msg.setCondition(new TestAndSetCondition(decodeString(buf)));
    }

    static void encodeTasCondition(DocumentSerializer buf, TestAndSetMessage msg) {
        encodeString(msg.getCondition().getSelection(), buf);
    }
}
