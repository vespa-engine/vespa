// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multivalueattributesaver.h"

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace search {

template <typename IndexT>
MultiValueAttributeSaver<IndexT>::
MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                         const IAttributeSaveTarget::Config &cfg,
                         const MvMappingBase &mvMapping)
    : AttributeSaver(std::move(guard), cfg),
      _frozenIndices(mvMapping.getRefCopy(cfg.getNumDocs()))
{
}


template <typename IndexT>
MultiValueAttributeSaver<IndexT>::~MultiValueAttributeSaver()
{
}

template class MultiValueAttributeSaver<multivalue::Index32>;

template class MultiValueAttributeSaver<multivalue::Index64>;


}  // namespace search
