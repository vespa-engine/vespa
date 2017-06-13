// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace storage {
namespace memfile {

class OptionsBuilder
{
    Options _newOptions;
public:
    OptionsBuilder(const Options& opts)
        : _newOptions(opts)
    {
    }

    OptionsBuilder& maximumReadThroughGap(uint32_t readThroughGap) {
        _newOptions._maximumGapToReadThrough = readThroughGap;
        return *this;
    }

    OptionsBuilder& initialIndexRead(uint32_t bytesToRead) {
        _newOptions._initialIndexRead = bytesToRead;
        return *this;
    }

    OptionsBuilder& revertTimePeriod(framework::MicroSecTime revertTime) {
        _newOptions._revertTimePeriod = revertTime;
        return *this;
    }

    OptionsBuilder& defaultRemoveDocType(vespalib::stringref typeName) {
        _newOptions._defaultRemoveDocType = typeName;
        return *this;
    }

    OptionsBuilder& maxDocumentVersions(uint32_t maxVersions) {
        _newOptions._maxDocumentVersions = maxVersions;
        return *this;
    }

    std::unique_ptr<Options> build() const {
        return std::unique_ptr<Options>(new Options(_newOptions));
    }
};

} // memfile
} // storage

