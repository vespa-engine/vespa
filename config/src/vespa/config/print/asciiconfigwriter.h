// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configwriter.h"
#include "configformatter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

class AsciiConfigWriter : public ConfigWriter {
public:
    AsciiConfigWriter(vespalib::asciistream & os);
    bool write(const ConfigInstance & config) override;
    bool write(const ConfigInstance & config, const ConfigFormatter & formatter) override;
private:
    vespalib::asciistream & _os;
};

} // namespace config

