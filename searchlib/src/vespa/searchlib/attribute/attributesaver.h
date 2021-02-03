// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_header.h"
#include <vespa/vespalib/util/generationhandler.h>

namespace search {

class IAttributeSaveTarget;

/*
 * Abstract class used to hold data outside attribute vector needed
 * during a save operation, e.g. copy of data structure without
 * snapshot property, and guards to protect frozen views on structures
 * with snapshot properties.
 */
class AttributeSaver
{
private:
    vespalib::GenerationHandler::Guard _guard;
    attribute::AttributeHeader _header;

protected:
    AttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                   const attribute::AttributeHeader &header);

    virtual bool onSave(IAttributeSaveTarget &saveTarget) = 0;

public:
    virtual ~AttributeSaver();

    bool save(IAttributeSaveTarget &saveTarget);

    bool hasGenerationGuard() const;

    const vespalib::string &get_file_name() const { return _header.getFileName(); }
};

} // namespace search
