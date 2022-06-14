// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feedconfigstore.h"
#include <vespa/searchcommon/common/schema.h>

namespace vespa::config::search::core::internal {
    class InternalProtonType;
}
namespace proton {

class DocumentDBConfig;

struct ConfigStore : FeedConfigStore {
    typedef std::unique_ptr<ConfigStore> UP;
    typedef search::SerialNum SerialNum;
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;
    using ProtonConfigSP = std::shared_ptr<ProtonConfig>;

    /**
     * @param currentSnapshot the current snapshot, for reusing
     *                        unchanged parts .
     * @param serialNum the serial number of the config snapshot to load.
     * @param loadedSnapshot the shared pointer in which to store the
     *                       resulting config snapshot.
     */
    virtual void loadConfig(const DocumentDBConfig &currentSnapshot, SerialNum serialNum,
                            std::shared_ptr<DocumentDBConfig> &loadedSnapshot) = 0;
    virtual void saveConfig(const DocumentDBConfig &snapshot, SerialNum serialNum) = 0;

    virtual void removeInvalid() = 0;
    /**
     * Perform prune after everything up to and including serialNum has been
     * flushed to stable storage.
     *
     * @param serialNum The serial number flushed to stable storage.
     */
    virtual void prune(SerialNum serialNum) = 0;

    virtual SerialNum getBestSerialNum() const = 0;
    virtual SerialNum getOldestSerialNum() const = 0;
    virtual bool hasValidSerial(SerialNum serialNum) const = 0;
    virtual SerialNum getPrevValidSerial(SerialNum serialNum) const = 0;
    virtual void setProtonConfig(const ProtonConfigSP &protonConfig) = 0;
};

}  // namespace proton

