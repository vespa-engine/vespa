// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "attributesaver.h"


using vespalib::GenerationHandler;

namespace search
{

AttributeSaver::AttributeSaver(GenerationHandler::Guard &&guard,
                               const IAttributeSaveTarget::Config &cfg)
    : _guard(std::move(guard)),
      _cfg(cfg)
{
}


AttributeSaver::~AttributeSaver()
{
}


bool
AttributeSaver::save(IAttributeSaveTarget &saveTarget)
{
    saveTarget.setConfig(_cfg);
    if (!saveTarget.setup()) {
        return false;
    }
    if (!onSave(saveTarget)) {
        return false;
    }
    saveTarget.close();
    return true;
}

bool
AttributeSaver::hasGenerationGuard() const
{
    return _guard.valid();
}

} // namespace search
