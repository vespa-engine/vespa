// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include "routablefactories51.h"
#include <vespa/documentapi/messagebus/messages/testandsetmessage.h>

namespace document { class DocumentTypeRepo; }

namespace documentapi {
/**
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement factories for the document
 * routable. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 */
class RoutableFactories52 : public RoutableFactories51 {
public:
    RoutableFactories52() = delete;

    class PutDocumentMessageFactory : public RoutableFactories50::PutDocumentMessageFactory {
        using super = RoutableFactories50::PutDocumentMessageFactory;
    protected:
        DocumentMessage::UP doDecode(document::ByteBuffer & buf) const override {
            return decodeMessage<PutDocumentMessage>(this, buf);
        }

        bool doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const override;
    public:
        void decodeInto(PutDocumentMessage & msg, document::ByteBuffer & buf) const;
        PutDocumentMessageFactory(const document::DocumentTypeRepo & r) : super::PutDocumentMessageFactory(r) {}
    };

    class RemoveDocumentMessageFactory : public RoutableFactories50::RemoveDocumentMessageFactory {
        using super = RoutableFactories50::RemoveDocumentMessageFactory;
    protected:
        DocumentMessage::UP doDecode(document::ByteBuffer & buf) const override {
            return decodeMessage<RemoveDocumentMessage>(this, buf);
        }

        bool doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const override;
    public:
        void decodeInto(RemoveDocumentMessage & msg, document::ByteBuffer & buf) const;
    };

    class UpdateDocumentMessageFactory : public RoutableFactories50::UpdateDocumentMessageFactory {
        using super = RoutableFactories50::UpdateDocumentMessageFactory;
    protected:
        DocumentMessage::UP doDecode(document::ByteBuffer & buf) const override {
            return decodeMessage<UpdateDocumentMessage>(this, buf);
        }

        bool doEncode(const DocumentMessage & msg, vespalib::GrowableByteBuffer & buf) const override;
    public:
        void decodeInto(UpdateDocumentMessage & msg, document::ByteBuffer & buf) const;
        UpdateDocumentMessageFactory(const document::DocumentTypeRepo & r) : super::UpdateDocumentMessageFactory(r) {}
    };

    static void decodeTasCondition(DocumentMessage & docMsg, document::ByteBuffer & buf);
    static void encodeTasCondition(vespalib::GrowableByteBuffer & buf, const DocumentMessage & docMsg);
};

}
