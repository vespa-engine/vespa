// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributesaver.h"

#include "iattributesavetarget.h"

using vespalib::GenerationGuard;

namespace search {

AttributeSaver::AttributeSaver(GenerationGuard&& guard, const attribute::AttributeHeader& header)
    : _guard(std::move(guard)), _header(header) {
}

AttributeSaver::~AttributeSaver() = default;

bool AttributeSaver::save(IAttributeSaveTarget& saveTarget) {
    saveTarget.setHeader(_header);
    if (!saveTarget.setup()) {
        return false;
    }
    if (!onSave(saveTarget)) {
        return false;
    }
    saveTarget.close();
    return true;
}

bool AttributeSaver::hasGenerationGuard() const {
    return _guard.valid();
}

} // namespace search
