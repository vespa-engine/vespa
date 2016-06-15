// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "singlenumericattributesaver.h"

using vespalib::GenerationHandler;
using search::IAttributeSaveTarget;

namespace search {

namespace
{

const uint32_t MIN_ALIGNMENT = 4096;

}


SingleValueNumericAttributeSaver::
SingleValueNumericAttributeSaver(const IAttributeSaveTarget::Config &cfg,
                                 const void *data, size_t size)
  : AttributeSaver(vespalib::GenerationHandler::Guard(), cfg),
    _buf()
{
    _buf = std::make_unique<BufferBuf>(size, MIN_ALIGNMENT);
    assert(_buf->getFreeLen() >= size);
    if (size > 0) {
        memcpy(_buf->getFree(), data, size);
        _buf->moveFreeToData(size);
    }
    assert(_buf->getDataLen() == size);
}


SingleValueNumericAttributeSaver::~SingleValueNumericAttributeSaver()
{
}


bool
SingleValueNumericAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    saveTarget.datWriter().writeBuf(std::move(_buf));
    return true;
}


}  // namespace search
