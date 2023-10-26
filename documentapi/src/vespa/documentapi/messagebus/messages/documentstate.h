// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/documentid.h>

namespace document {
    class ByteBuffer;
}
namespace vespalib {
    class GrowableByteBuffer;
}

namespace documentapi {

class DocumentState {
    std::unique_ptr<document::DocumentId> _docId;
    document::GlobalId _gid;
    uint64_t _timestamp;
    bool _removeEntry;

public:
    DocumentState();
    DocumentState(const DocumentState&);
    DocumentState(const document::DocumentId&,
                  uint64_t timestamp, bool removeEntry);
    DocumentState(const document::GlobalId&,
                  uint64_t timestamp, bool removeEntry);
    DocumentState(document::ByteBuffer &buf);

    DocumentState& operator=(const DocumentState&);
    
    void serialize(vespalib::GrowableByteBuffer &buf) const;

    const document::GlobalId& getGlobalId() const { return _gid; }
    const document::DocumentId* getDocumentId() const { return _docId.get(); }
    uint64_t getTimestamp() const { return _timestamp; }
    bool isRemoveEntry() const { return _removeEntry; }
};

} // documentapi
