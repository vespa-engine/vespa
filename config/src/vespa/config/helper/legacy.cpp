// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy.h"
#include <vespa/vespalib/io/fileutil.h>

namespace {
    bool isFileLegacy(const std::string & configId) {
        return configId.compare(0, 5, "file:") == 0;
    }
    bool isDirLegacy(const std::string & configId) {
        return configId.compare(0, 4, "dir:") == 0;
    }
    const std::string dirNameFromId(const std::string & configId) {
        return configId.substr(4);
    }
    const std::string createFileSpecFromId(const std::string & configId) {
        return configId.substr(5);
    }
    const std::string createBaseId(const std::string & configId) {
        std::string::size_type end = configId.find_last_of(".");
        return configId.substr(5, end - 5);
    }
    bool isRawLegacy(const std::string & configId) {
        return configId.compare(0, 4, "raw:") == 0;
    }
    const std::string createRawSpecFromId(const std::string & configId) {
        return configId.substr(4);
    }
}

namespace config {

bool
isLegacyConfigId(const std::string & configId)
{
    return (isRawLegacy(configId) ||
            isFileLegacy(configId) ||
            isDirLegacy(configId));
}

std::unique_ptr<SourceSpec>
legacyConfigId2Spec(const std::string & configId)
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
legacyConfigId2ConfigId(const std::string & configId)
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
