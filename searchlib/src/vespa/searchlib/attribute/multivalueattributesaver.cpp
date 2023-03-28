// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multivalueattributesaver.h"
#include "multi_value_mapping_base.h"

using vespalib::GenerationHandler;

namespace search {

MultiValueAttributeSaver::
MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                         const attribute::AttributeHeader &header,
                         const MvMappingBase &mvMapping)
    : AttributeSaver(std::move(guard), header),
      _frozenIndices(attribute::make_entry_ref_vector_snapshot(mvMapping.get_ref_vector(), header.getNumDocs()))
{
}


MultiValueAttributeSaver::~MultiValueAttributeSaver()
{
}

}  // namespace search
