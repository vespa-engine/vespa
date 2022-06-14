// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_disk_layout.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton_disk_layout");

using search::transactionlog::client::TransLogClient;

namespace proton {

namespace {

struct DocumentDBDirMeta
{
    bool normal;
    bool removed;

    DocumentDBDirMeta()
        : normal(false),
          removed(false)
    {
    }
};

using DocumentDBDirScan = std::map<DocTypeName, DocumentDBDirMeta>;

vespalib::string getDocumentsDir(const vespalib::string &baseDir)
{
    return baseDir + "/documents";
}

vespalib::string removedSuffix(".removed");

vespalib::string getNormalName(const vespalib::string removedName) {
    return removedName.substr(0, removedName.size() - removedSuffix.size());
}

vespalib::string getRemovedName(const vespalib::string &normalName)
{
    return normalName + removedSuffix;
}

bool isRemovedName(const vespalib::string &dirName)
{
    return dirName.size() > removedSuffix.size() && dirName.substr(dirName.size() - removedSuffix.size()) == removedSuffix;
}

void scanDir(const vespalib::string documentsDir, DocumentDBDirScan &dirs)
{
    auto names = vespalib::listDirectory(documentsDir);
    for (const auto &name : names) {
        if (vespalib::isDirectory(documentsDir + "/" + name)) {
            if (isRemovedName(name)) {
                dirs[DocTypeName(getNormalName(name))].removed = true;
            } else {
                dirs[DocTypeName(name)].normal = true;
            }
        }
    }
}

}

ProtonDiskLayout::ProtonDiskLayout(FNET_Transport & transport, const vespalib::string &baseDir, const vespalib::string &tlsSpec)
    : _transport(transport),
      _baseDir(baseDir),
      _tlsSpec(tlsSpec)
{
    std::filesystem::create_directories(std::filesystem::path(getDocumentsDir(_baseDir)));
}

ProtonDiskLayout::~ProtonDiskLayout() = default;

void
ProtonDiskLayout::remove(const DocTypeName &docTypeName)
{
    vespalib::string documentsDir(getDocumentsDir(_baseDir));
    vespalib::string name(docTypeName.toString());
    vespalib::string normalDir(documentsDir + "/" + name);
    vespalib::string removedDir(documentsDir + "/" + getRemovedName(name));
    vespalib::rename(normalDir, removedDir, false, false);
    vespalib::File::sync(documentsDir);
    TransLogClient tlc(_transport, _tlsSpec);
    if (!tlc.remove(name)) {
        LOG(fatal, "Failed to remove tls domain %s", name.c_str());
        LOG_ABORT("Failed to remove tls domain");
    }
    std::filesystem::remove_all(std::filesystem::path(removedDir));
    vespalib::File::sync(documentsDir);
}

void
ProtonDiskLayout::initAndPruneUnused(const std::set<DocTypeName> &docTypeNames)
{
    vespalib::string documentsDir(getDocumentsDir(_baseDir));
    DocumentDBDirScan dirs;
    scanDir(documentsDir, dirs);
    for (const auto &dir : dirs) {
        if (dir.second.removed) {
            // Complete interrupted removal
            if (dir.second.normal) {
                vespalib::string name(dir.first.toString());
                vespalib::string normalDir(documentsDir + "/" + name);
                std::filesystem::remove_all(std::filesystem::path(normalDir));
            }
            remove(dir.first);
        } else if (docTypeNames.count(dir.first) == 0) {
            // Remove unused directory
            remove(dir.first);
        }
    }
}

} // namespace proton

