// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multivalueattributesaver.h"

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace search {

MultiValueAttributeSaver::
MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                         const IAttributeSaveTarget::Config &cfg,
                         const MvMappingBase &mvMapping)
    : AttributeSaver(std::move(guard), cfg),
      _frozenIndices(mvMapping.getRefCopy(cfg.getNumDocs()))
{
}


MultiValueAttributeSaver::~MultiValueAttributeSaver()
{
}

}  // namespace search
