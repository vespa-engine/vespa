// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configreader.h"

namespace config {

template <typename ConfigType>
class FileConfigReader : public ConfigReader<ConfigType> {
public:
    FileConfigReader(const vespalib::string & fileName);

    // Implements ConfigReader
    std::unique_ptr<ConfigType> read(const ConfigFormatter & formatter) override;

    /**
     * Read config from this file using old config format.
     *
     * @return An instance of the correct type.
     */
    std::unique_ptr<ConfigType> read();
private:
    const vespalib::string _fileName;
};

} // namespace config
