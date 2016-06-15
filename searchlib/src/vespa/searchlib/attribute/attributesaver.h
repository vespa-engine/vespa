// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include "iattributesavetarget.h"

namespace search
{

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
    IAttributeSaveTarget::Config _cfg;

protected:
    AttributeSaver(vespalib::GenerationHandler::Guard &&guard,
                         const IAttributeSaveTarget::Config &cfg);

    virtual bool onSave(IAttributeSaveTarget &saveTarget) = 0;

public:
    virtual ~AttributeSaver();

    bool save(IAttributeSaveTarget &saveTarget);
};

} // namespace search
