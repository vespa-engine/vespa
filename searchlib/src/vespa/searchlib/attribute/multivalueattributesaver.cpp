// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multivalueattributesaver.h"

using vespalib::GenerationHandler;

namespace search {

MultiValueAttributeSaver::
MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                         const attribute::AttributeHeader &header,
                         const MvMappingBase &mvMapping)
    : AttributeSaver(std::move(guard), header),
      _frozenIndices(mvMapping.getRefCopy(header.getNumDocs()))
{
}


MultiValueAttributeSaver::~MultiValueAttributeSaver()
{
}

}  // namespace search
