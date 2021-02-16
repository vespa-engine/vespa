// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlenumericattributesaver.h"
#include "iattributesavetarget.h"
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

using vespalib::GenerationHandler;

namespace search {

namespace {

const uint32_t MIN_ALIGNMENT = 4_Ki;

}


SingleValueNumericAttributeSaver::
SingleValueNumericAttributeSaver(const attribute::AttributeHeader &header,
                                 const void *data, size_t size)
  : AttributeSaver(vespalib::GenerationHandler::Guard(), header),
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


SingleValueNumericAttributeSaver::~SingleValueNumericAttributeSaver() = default;



bool
SingleValueNumericAttributeSaver::onSave(IAttributeSaveTarget &saveTarget)
{
    saveTarget.datWriter().writeBuf(std::move(_buf));
    return true;
}


}  // namespace search
