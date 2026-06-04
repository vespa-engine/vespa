// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlenumericattributesaver.h"

#include "iattributesavetarget.h"

#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

using vespalib::GenerationGuard;

namespace search {

SingleValueNumericAttributeSaver::SingleValueNumericAttributeSaver(const attribute::AttributeHeader& header,
                                                                   const void* data, size_t size)
    : AttributeSaver(GenerationGuard(), header), _buf(), _tracker() {
    auto lock = _tracker.acquire_lock();
    _buf = std::make_unique<BufferBuf>(size, FileSettings::DIRECTIO_ALIGNMENT);
    assert(_buf->getFreeLen() >= size);
    if (size > 0) {
        memcpy(_buf->getFree(), data, size);
        _buf->moveFreeToData(size);
    }
    assert(_buf->getDataLen() == size);
    _tracker.set_transient_memory(std::move(lock), size);
}

SingleValueNumericAttributeSaver::~SingleValueNumericAttributeSaver() {
    if (_buf && _buf->getDataLen() > 0) {
        auto lock = _tracker.acquire_lock();
        _buf.reset();
        _tracker.set_transient_memory(std::move(lock), 0);
    }
}

bool SingleValueNumericAttributeSaver::onSave(IAttributeSaveTarget& saveTarget) {
    saveTarget.datWriter().write_buf(std::move(_buf), std::move(_tracker));
    return true;
}

} // namespace search
