// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/serialization/fieldwriter.h>

namespace document {
class DocumentId;
class DocumentType;

class DocumentWriter : public FieldWriter {
public:
    virtual void write(const Document &value) = 0;
    virtual void write(const DocumentId &value) = 0;
    virtual void write(const DocumentType &value) = 0;
};

}
