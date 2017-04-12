// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configstore.h"
#include "documentdbconfigmanager.h"
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace proton {

class FileConfigManager : public ConfigStore {
public:
    typedef std::unique_ptr<FileConfigManager> UP;
    typedef std::shared_ptr<FileConfigManager> SP;
    typedef search::IndexMetaInfo::Snapshot Snapshot;

private:
    vespalib::string _baseDir;
    vespalib::string _configId;
    vespalib::string _docTypeName;
    search::IndexMetaInfo _info;
    ProtonConfigSP _protonConfig;

public:
    /**
     * Creates a new file config manager.
     *
     * @param baseDir the directory in which config snapshots are saved and loaded.
     * @param configId the configId that was used to subscribe to config that is later handled by this manager.
     */
    FileConfigManager(const vespalib::string &baseDir,
                      const vespalib::string &configId,
                      const vespalib::string &docTypeName);

    virtual
    ~FileConfigManager(void);

    virtual SerialNum getBestSerialNum() const override;
    virtual SerialNum getOldestSerialNum() const override;

    virtual void saveConfig(const DocumentDBConfig &snapshot,
                            const search::index::Schema &historySchema,
                            SerialNum serialNum) override;

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
     * @param historySchema the shared pointer in which to store the
     *                      resulting history schema.
     */
    virtual void loadConfig(const DocumentDBConfig &currentSnapshot,
                            SerialNum serialNum,
                            DocumentDBConfig::SP &loadedSnapshot,
                            search::index::Schema::SP &historySchema) override;

    virtual void removeInvalid() override;
    virtual void prune(SerialNum serialNum) override;
    virtual bool hasValidSerial(SerialNum serialNum) const override;

    virtual SerialNum getPrevValidSerial(SerialNum serialNum) const override;

    /**
     * Clone config except for history schema.
     * Used when wiping history.
     */
    virtual void saveWipeHistoryConfig(SerialNum serialNum,
                                       fastos::TimeStamp wipeTimeLimit) override;


    /**
     * Serialize config files.
     *
     * Used for serializing config into transaction log.
     */
    virtual void
    serializeConfig(SerialNum serialNum, vespalib::nbostream &stream) override;


    /**
     * Deserialize config files.
     *
     * Used for deserializing config from transaction log when it is
     * not already present on disk.  Config files on disk
     * takes precedence over the serialized config files in the
     * transaction log.
     */
    virtual void
    deserializeConfig(SerialNum serialNum, vespalib::nbostream &stream) override;

    virtual void setProtonConfig(const ProtonConfigSP &protonConfig) override;
};

} // namespace proton

