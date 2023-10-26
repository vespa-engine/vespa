// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/serialization/fieldreader.h>

namespace document {
class DocumentId;
class DocumentType;

class DocumentReader : public FieldReader {
public:
    virtual void read(Document &value) = 0;
    virtual void read(DocumentId &value) = 0;
    virtual void read(DocumentType &value) = 0;
};

}
