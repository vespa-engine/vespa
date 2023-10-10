// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configwriter.h"
#include "configformatter.h"

namespace config {

/**
 * Write config to an ostream.
 */
class OstreamConfigWriter : public ConfigWriter
{
public:
    OstreamConfigWriter(std::ostream & os);
    bool write(const ConfigInstance & config) override;
    bool write(const ConfigInstance & config, const ConfigFormatter & formatter) override;
private:
    std::ostream & _os;
};

} // namespace config

