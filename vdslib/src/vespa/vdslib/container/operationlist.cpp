// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationlist.h"
#include "documentlist.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace vdslib {

OperationList::Operation::~Operation() {}
OperationList::OperationList() {}
OperationList::~OperationList() {}

int OperationList::getRequiredBufferSize() const {
    int bufferSize = 4;

    vespalib::nbostream stream;

    // Creating a document by fetching the first document type declared
    for(uint32_t i=0; i < _operations.size();i++){
        stream.clear();
        switch(_operations[i].opt) {
        case OperationList::Operation::REMOVE:
        {
            document::Document doc(*document::DataType::DOCUMENT,
                                   _operations[i].docId);
            doc.serializeHeader(stream);
            break;
        }
        case OperationList::Operation::PUT:
        {
            _operations[i].document->serializeHeader(stream);
            _operations[i].document->serializeBody(stream);
            break;
        }
        case OperationList::Operation::UPDATE:
        {
            _operations[i].documentUpdate->serialize42(stream);
            break;
        }
        }
        bufferSize += stream.size();
    }

    return bufferSize + (_operations.size() * sizeof(DocumentList::MetaEntry));
}

} // vdslib
