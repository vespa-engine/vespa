// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy.h"
#include <vespa/vespalib/io/fileutil.h>

namespace {
    bool isFileLegacy(std::string_view configId) {
        return configId.compare(0, 5, "file:") == 0;
    }
    bool isDirLegacy(std::string_view configId) {
        return configId.compare(0, 4, "dir:") == 0;
    }
    std::string_view dirNameFromId(std::string_view configId) {
        return configId.substr(4);
    }
    std::string_view createFileSpecFromId(std::string_view configId) {
        return configId.substr(5);
    }
    std::string_view createBaseId(std::string_view configId) {
        std::string::size_type end = configId.find_last_of('.');
        return configId.substr(5, end - 5);
    }
    bool isRawLegacy(std::string_view configId) {
        return configId.compare(0, 4, "raw:") == 0;
    }
    std::string_view createRawSpecFromId(std::string_view configId) {
        return configId.substr(4);
    }
}

namespace config {

bool
isLegacyConfigId(std::string_view configId)
{
    return (isRawLegacy(configId) ||
            isFileLegacy(configId) ||
            isDirLegacy(configId));
}

std::unique_ptr<SourceSpec>
legacyConfigId2Spec(std::string_view configId)
{
    if (isFileLegacy(configId)) {
        return std::make_unique<FileSpec>(createFileSpecFromId(configId));
    } else if (isDirLegacy(configId)) {
        return std::make_unique<DirSpec>(dirNameFromId(configId));
    } else if (isRawLegacy(configId)) {
        return std::make_unique<RawSpec>(createRawSpecFromId(configId));
    }
    return std::make_unique<ServerSpec>();
}

const std::string
legacyConfigId2ConfigId(std::string_view configId)
{
    std::string newId(configId);
    if (isFileLegacy(configId)) {
        newId = createBaseId(configId);
    } else if (isRawLegacy(configId) || isDirLegacy(configId)) {
        newId = "";
    }
    return newId;
}

}
