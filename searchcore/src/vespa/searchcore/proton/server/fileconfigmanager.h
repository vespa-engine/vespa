// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configstore.h"
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/objects/nbostream.h>

class FNET_Transport;

namespace proton {

class FileConfigManager : public ConfigStore {
private:
    FNET_Transport        & _transport;
    vespalib::string        _baseDir;
    vespalib::string        _configId;
    vespalib::string        _docTypeName;
    search::IndexMetaInfo   _info;
    ProtonConfigSP          _protonConfig;

public:
    /**
     * Creates a new file config manager.
     *
     * @param baseDir the directory in which config snapshots are saved and loaded.
     * @param configId the configId that was used to subscribe to config that is later handled by this manager.
     */
    FileConfigManager(FNET_Transport & transport, const vespalib::string &baseDir,
                      const vespalib::string &configId, const vespalib::string &docTypeName);

    ~FileConfigManager() override;

    SerialNum getBestSerialNum() const override;
    SerialNum getOldestSerialNum() const override;

    void saveConfig(const DocumentDBConfig &snapshot, SerialNum serialNum) override;

    /**
     * Load a config snapshot from disk corresponding to the given
     * serial number.  The config id of this manager is set on the
     * loaded config snapshot.
     *
     * @param currentSnapshot the current snapshot, for reusing
     *                        unchanged parts .
     * @param serialNum the serial number of the config snapshot to load.
     * @param loadedSnapshot the shared pointer in which to store the
     *                       resulting config snapshot.
     */
    void loadConfig(const DocumentDBConfig &currentSnapshot, SerialNum serialNum,
                    std::shared_ptr<DocumentDBConfig> &loadedSnapshot) override;

    void removeInvalid() override;
    void prune(SerialNum serialNum) override;
    bool hasValidSerial(SerialNum serialNum) const override;

    SerialNum getPrevValidSerial(SerialNum serialNum) const override;

    /**
     * Serialize config files.
     *
     * Used for serializing config into transaction log.
     */
    void serializeConfig(SerialNum serialNum, vespalib::nbostream &stream) override;


    /**
     * Deserialize config files.
     *
     * Used for deserializing config from transaction log when it is
     * not already present on disk.  Config files on disk
     * takes precedence over the serialized config files in the
     * transaction log.
     */
    void deserializeConfig(SerialNum serialNum, vespalib::nbostream &stream) override;

    void setProtonConfig(const ProtonConfigSP &protonConfig) override;
};

} // namespace proton

