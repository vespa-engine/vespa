// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileconfigmanager.h"
#include "bootstrapconfig.h"
#include "documentdbconfigmanager.h"
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/fastos/file.h>
#include <filesystem>
#include <sstream>
#include <cassert>
#include <cinttypes>
#include <fcntl.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.fileconfigmanager");

using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using search::IndexMetaInfo;
using search::SerialNum;
using search::index::Schema;
using cloud::config::filedistribution::FiledistributorrpcConfig;
using vespa::config::search::AttributesConfig;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::SummaryConfig;
using vespa::config::search::core::ProtonConfig;
using vespa::config::search::summary::JuniperrcConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::nbostream;

using SnapshotList = IndexMetaInfo::SnapshotList;
using Snapshot = IndexMetaInfo::Snapshot;
using namespace std::chrono_literals;

namespace proton {

namespace {

vespalib::string
makeSnapDirBaseName(SerialNum serialNum)
{
    std::ostringstream os;
    os << "config-" << serialNum;
    return os.str();
}

void
fsyncFile(const vespalib::string &fileName)
{
    FastOS_File f;
    f.OpenReadWrite(fileName.c_str());
    if (!f.IsOpened()) {
        LOG(error, "Could not open file '%s' for fsync", fileName.c_str());
        return;
    }
    if (!f.Sync()) {
        LOG(error, "Could not fsync file '%s'", fileName.c_str());
    }
}

template <class Config>
void
saveHelper(const vespalib::string &snapDir, const vespalib::string &name, const Config &config)
{
    vespalib::string fileName(snapDir + "/" + name + ".cfg");
    config::FileConfigWriter writer(fileName);
    bool ok = writer.write(config);
    assert(ok);
    (void) ok;
    fsyncFile(fileName);
}

template <class Config>
void
save(const vespalib::string &snapDir, const Config &config)
{
    saveHelper(snapDir, config.defName(), config);
}

class ConfigFile
{
    using SP = std::shared_ptr<ConfigFile>;

    vespalib::string      _name;
    std::vector<char>     _content;

public:
    ConfigFile();
    ~ConfigFile();

    ConfigFile(const vespalib::string &name, const vespalib::string &fullName);

    nbostream &serialize(nbostream &stream) const;
    nbostream &deserialize(nbostream &stream);
    void save(const vespalib::string &snapDir) const;
};

ConfigFile::ConfigFile()
    : _name(),
      _content()
{
}

ConfigFile::~ConfigFile() = default;

ConfigFile::ConfigFile(const vespalib::string &name, const vespalib::string &fullName)
    : _name(name),
      _content()
{
    FastOS_File file;
    bool openRes = file.OpenReadOnlyExisting(false, fullName.c_str());
    if (!openRes)
        return;
    int64_t fileSize = file.getSize();
    _content.resize(fileSize);
    file.ReadBuf(_content.data(), fileSize);
}

nbostream &
ConfigFile::serialize(nbostream &stream) const
{
    assert(strchr(_name.c_str(), '/') == nullptr);
    stream << _name;
    stream << int64_t(0ul);   // Used to be modtime => unused
    uint32_t sz = _content.size();
    stream << sz;
    stream.write(_content.data(), sz);
    return stream;
}

nbostream &
ConfigFile::deserialize(nbostream &stream)
{
    stream >> _name;
    assert(strchr(_name.c_str(), '/') == nullptr);
    int64_t unused_modTime;
    stream >> unused_modTime;
    uint32_t sz;
    stream >> sz;
    _content.resize(sz);
    assert(stream.size() >= sz);
    if (sz > 0) {
        memcpy(_content.data(), stream.peek(), sz);
    }
    stream.adjustReadPos(sz);
    return stream;
}

void
ConfigFile::save(const vespalib::string &snapDir) const
{
    vespalib::string fullName = snapDir + "/" + _name;
    FastOS_File file;
    bool openRes = file.OpenWriteOnlyTruncate(fullName.c_str());
    assert(openRes);
    (void) openRes;

    file.WriteBuf(_content.data(), _content.size());
    bool closeRes = file.Close();
    assert(closeRes);
    (void) closeRes;

    fsyncFile(fullName);
}

nbostream &
operator<<(nbostream &stream, const ConfigFile &configFile)
{
    return configFile.serialize(stream);
}

nbostream &
operator>>(nbostream &stream, ConfigFile &configFile)
{
    return configFile.deserialize(stream);
}

std::vector<vespalib::string>
getFileList(const vespalib::string &snapDir)
{
    std::vector<vespalib::string> res;
    FastOS_DirectoryScan dirScan(snapDir.c_str());
    while (dirScan.ReadNext()) {
        if (strcmp(dirScan.GetName(), ".") == 0 ||
            strcmp(dirScan.GetName(), "..") == 0)
            continue;
        res.push_back(dirScan.GetName());
    }
    std::sort(res.begin(), res.end());
    return res;
}

// add an empty file if it's not already present
void addEmptyFile(vespalib::string snapDir, vespalib::string fileName)
{
    vespalib::string path = snapDir + "/" + fileName;
    if (access(path.c_str(), R_OK) == 0) {
        // exists OK
        return;
    }
    int fd = creat(path.c_str(), 0444);
    if (fd < 0) {
        LOG(error, "Could not create empty file '%s': %s", path.c_str(), strerror(errno));
        return;
    }
    fsync(fd);
    close(fd);
}

}

FileConfigManager::FileConfigManager(FNET_Transport & transport,
                                     const vespalib::string &baseDir,
                                     const vespalib::string &configId,
                                     const vespalib::string &docTypeName)
    : _transport(transport),
      _baseDir(baseDir),
      _configId(configId),
      _docTypeName(docTypeName),
      _info(baseDir),
      _protonConfig()
{
    std::filesystem::create_directory(std::filesystem::path(baseDir));
    vespalib::File::sync(vespalib::dirname(baseDir));
    if (!_info.load())
        _info.save();
    removeInvalid();
    _protonConfig.reset(new ProtonConfig());
}

FileConfigManager::~FileConfigManager() = default;

SerialNum
FileConfigManager::getBestSerialNum() const
{
    Snapshot snap = _info.getBestSnapshot();
    return snap.valid ? snap.syncToken : UINT64_C(0);
}

SerialNum
FileConfigManager::getOldestSerialNum() const
{
    SerialNum res = 0;
    const SnapshotList &snaps = _info.snapshots();
    for (const auto &snap : snaps) {
        if (!snap.valid || snap.syncToken == 0)
            continue;
        if (res == 0 || res > snap.syncToken)
            res = snap.syncToken;
    }
    return res;
}

void
FileConfigManager::saveConfig(const DocumentDBConfig &snapshot, SerialNum serialNum)
{
    if (getBestSerialNum() >= serialNum) {
        LOG(warning, "Config for serial >= %" PRIu64 " already saved",
            static_cast<uint64_t>(serialNum));
        return;
    }
    vespalib::string snapDirBaseName(makeSnapDirBaseName(serialNum));
    vespalib::string snapDir(_baseDir + "/" + snapDirBaseName);
    Snapshot snap(false, serialNum, snapDirBaseName);
    _info.addSnapshot(snap);
    bool saveInvalidSnap = _info.save();
    assert(saveInvalidSnap);
    (void) saveInvalidSnap;
    std::filesystem::create_directory(std::filesystem::path(snapDir));
    save(snapDir, snapshot.getRankProfilesConfig());
    save(snapDir, snapshot.getIndexschemaConfig());
    save(snapDir, snapshot.getAttributesConfig());
    save(snapDir, snapshot.getSummaryConfig());
    save(snapDir, snapshot.getJuniperrcConfig());
    save(snapDir, snapshot.getDocumenttypesConfig());
    addEmptyFile(snapDir, "summarymap.cfg");

    vespalib::File::sync(snapDir);
    vespalib::File::sync(_baseDir);

    _info.validateSnapshot(serialNum);

    bool saveValidSnap = _info.save();
    assert(saveValidSnap);
    (void) saveValidSnap;
}

void
FileConfigManager::loadConfig(const DocumentDBConfig &currentSnapshot, search::SerialNum serialNum,
                              DocumentDBConfig::SP &loadedSnapshot)
{
    vespalib::string snapDirBaseName(makeSnapDirBaseName(serialNum));
    vespalib::string snapDir(_baseDir + "/" + snapDirBaseName);
    config::DirSpec spec(snapDir);

    addEmptyFile(snapDir, "ranking-constants.cfg");
    addEmptyFile(snapDir, "ranking-expressions.cfg");
    addEmptyFile(snapDir, "onnx-models.cfg");
    addEmptyFile(snapDir, "imported-fields.cfg");
    addEmptyFile(snapDir, "summarymap.cfg");

    DocumentDBConfigHelper dbc(spec, _docTypeName);

    using DTC = DocumenttypesConfig;
    using DTCSP =  DocumentDBConfig::DocumenttypesConfigSP;
    DTCSP docTypesCfg = config::ConfigGetter<DTC>::getConfig("", spec);
    std::shared_ptr<const DocumentTypeRepo> repo;
    if (currentSnapshot.getDocumenttypesConfigSP() &&
        currentSnapshot.getDocumentTypeRepoSP() &&
        (currentSnapshot.getDocumenttypesConfig() == *docTypesCfg))
    {
        docTypesCfg = currentSnapshot.getDocumenttypesConfigSP();
        repo = currentSnapshot.getDocumentTypeRepoSP();
    } else {
        repo = DocumentTypeRepoFactory::make(*docTypesCfg);
    }

    auto filedistRpcConf = std::make_shared<FiledistributorrpcConfig>();
    auto bucketspaces = std::make_shared<BucketspacesConfig>();

    /*
     * XXX: If non-default maintenance config is used then an extra config
     * snapshot is saved after replaying transaction log due to the use
     * of default values here instead of the current values from the config
     * server.
     */
    const ProtonConfig &protonConfig = *_protonConfig;
    const auto &hwDiskCfg = protonConfig.hwinfo.disk;
    const auto &hwMemoryCfg = protonConfig.hwinfo.memory;
    const auto &hwCpuCfg = protonConfig.hwinfo.cpu;
    HwInfoSampler::Config samplerCfg(hwDiskCfg.size, hwDiskCfg.writespeed, hwDiskCfg.slowwritespeedlimit,
                                     hwDiskCfg.samplewritesize, hwDiskCfg.shared, hwMemoryCfg.size, hwCpuCfg.cores);
    HwInfoSampler sampler(protonConfig.basedir, samplerCfg);
    auto bootstrap = std::make_shared<BootstrapConfig>(1, docTypesCfg, repo, _protonConfig, filedistRpcConf,
                                                       bucketspaces,currentSnapshot.getTuneFileDocumentDBSP(),
                                                       sampler.hwInfo());
    dbc.forwardConfig(bootstrap);
    dbc.nextGeneration(_transport, 0ms);

    loadedSnapshot = dbc.getConfig();
    loadedSnapshot->setConfigId(_configId);
}

void
FileConfigManager::removeInvalid()
{
    using RemVec = std::vector<SerialNum>;
    RemVec toRem;

    const SnapshotList &snaps = _info.snapshots();
    for (const auto &snap : snaps) {
        if (!snap.valid)
            toRem.push_back(snap.syncToken);
    }
    if (toRem.empty())
        return;

    for (const auto &serial : toRem) {
        vespalib::string snapDirBaseName(makeSnapDirBaseName(serial));
        vespalib::string snapDir(_baseDir + "/" + snapDirBaseName);
        try {
            std::filesystem::remove_all(std::filesystem::path(snapDir));
        } catch (const std::exception &e) {
            LOG(warning, "Removing obsolete config directory '%s' failed due to %s", snapDir.c_str(), e.what());
        }
    }
    vespalib::File::sync(_baseDir);
    for (const auto &serial : toRem) {
        _info.removeSnapshot(serial);
    }
    bool saveRemInvalidSnap = _info.save();
    assert(saveRemInvalidSnap);
    (void) saveRemInvalidSnap;
}

void
FileConfigManager::prune(SerialNum serialNum)
{
    using PruneVec = std::vector<SerialNum>;
    PruneVec toPrune;

    const SnapshotList &snaps = _info.snapshots();
    for (const auto &snap : snaps) {
        if (snap.valid && snap.syncToken <= serialNum)
            toPrune.push_back(snap.syncToken);
    }
    std::sort(toPrune.begin(), toPrune.end());
    if (!toPrune.empty())
        toPrune.pop_back(); // Keep newest old entry
    if (toPrune.empty())
        return;
    for (const auto &serial : toPrune) {
        _info.invalidateSnapshot(serial);
    }
    bool saveInvalidSnap = _info.save();
    assert(saveInvalidSnap);
    (void) saveInvalidSnap;
    removeInvalid();
}

bool
FileConfigManager::hasValidSerial(SerialNum serialNum) const
{
    IndexMetaInfo::Snapshot snap = _info.getSnapshot(serialNum);
    return snap.valid;
}

SerialNum
FileConfigManager::getPrevValidSerial(SerialNum serialNum) const
{
    SerialNum res = 0;
    const SnapshotList &snaps = _info.snapshots();
    for (const auto &snap : snaps) {
        if (!snap.valid || snap.syncToken >= serialNum)
            continue;
        if (res < snap.syncToken)
            res = snap.syncToken;
    }
    return res;
}

void
FileConfigManager::serializeConfig(SerialNum serialNum, nbostream &stream)
{
    vespalib::string snapDirBaseName(makeSnapDirBaseName(serialNum));
    vespalib::string snapDir(_baseDir + "/" + snapDirBaseName);

    assert(hasValidSerial(serialNum));

    std::vector<vespalib::string> configs = getFileList(snapDir);
    uint32_t numConfigs = configs.size();
    stream << numConfigs;
    for (const auto &config : configs) {
        ConfigFile file(config, snapDir + "/" + config);
        stream << file;
    }
}

void
FileConfigManager::deserializeConfig(SerialNum serialNum, nbostream &stream)
{
    vespalib::string snapDirBaseName(makeSnapDirBaseName(serialNum));
    vespalib::string snapDir(_baseDir + "/" + snapDirBaseName);

    bool skip = hasValidSerial(serialNum);

    Snapshot snap(false, serialNum, snapDirBaseName);
    if (!skip) {
        _info.addSnapshot(snap);
        bool saveInvalidSnap = _info.save();
        assert(saveInvalidSnap);
        (void) saveInvalidSnap;
        std::filesystem::create_directory(std::filesystem::path(snapDir));
    }

    uint32_t numConfigs;
    stream >> numConfigs;
    for (uint32_t i = 0; i < numConfigs; ++i) {
        ConfigFile file;
        stream >> file;
        if (!skip)
            file.save(snapDir);
    }
    assert(stream.size() == 0);
    if (!skip) {
        _info.validateSnapshot(serialNum);
        bool saveValidSnap = _info.save();
        assert(saveValidSnap);
        (void) saveValidSnap;
    }
}

void
FileConfigManager::setProtonConfig(const ProtonConfigSP &protonConfig)
{
    _protonConfig = protonConfig;
}

} // namespace proton
