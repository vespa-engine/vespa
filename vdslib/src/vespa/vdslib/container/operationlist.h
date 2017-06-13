// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>

namespace document { class DocumentUpdate; }

namespace vdslib {

class OperationList{
public:

    struct Operation{
        enum OpType{
            PUT=0,
            UPDATE=1,
            REMOVE=2
        };

        Operation(document::DocumentId dId)
            : docId(dId),
              opt(REMOVE)
        {
        }

        Operation(std::shared_ptr<document::Document> doc)
            : document(doc),
              opt(PUT)
        {
        }

        Operation(std::shared_ptr<document::DocumentUpdate> doc)
            : documentUpdate(doc),
              opt(UPDATE)
        {
        }
        ~Operation();

        document::DocumentId docId;
        std::shared_ptr<document::Document> document;
        std::shared_ptr<document::DocumentUpdate> documentUpdate;
        OpType opt;
    };

    void addPut(std::shared_ptr<document::Document> doc) {
        _operations.push_back(Operation(doc));
    }

    void addUpdate(std::shared_ptr<document::DocumentUpdate> docUpdate) {
        _operations.push_back(Operation(docUpdate));
    }

    void addRemove(document::DocumentId docId) {
        _operations.push_back(Operation(docId));
    }

    int getRequiredBufferSize() const;

    const std::vector<Operation>& getOperationList() const{
        return _operations;
    }

    // Deprecated functions. This list used to const cast and ruin source
    // objects in copy constructor. Keeping deprecated functions to avoid
    // breaking factory now, as they still work fine. Just code bloat.

    void addPut(std::unique_ptr<document::Document> doc) {
        _operations.push_back(
                Operation(std::shared_ptr<document::Document>(std::move(doc))));
    }

    void addUpdate(std::unique_ptr<document::DocumentUpdate> docUpdate) {
        _operations.push_back(
                Operation(std::shared_ptr<document::DocumentUpdate>(std::move(docUpdate))));
    }

    OperationList();
    ~OperationList();
private:
    std::vector<Operation> _operations;

};
}

