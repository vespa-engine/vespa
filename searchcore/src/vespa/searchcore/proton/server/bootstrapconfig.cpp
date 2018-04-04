// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bootstrapconfig.h"
#include <vespa/config-bucketspaces.h>

using namespace vespa::config::search;
using namespace config;
using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using vespa::config::search::core::ProtonConfig;
using document::DocumenttypesConfig;

namespace {
    template <typename T>
    bool equals(const T * lhs, const T * rhs)
    {
        if (lhs == NULL)
            return rhs == NULL;
        return rhs != NULL && *lhs == *rhs;
    }
}

namespace proton {

BootstrapConfig::BootstrapConfig(
               int64_t generation,
               const DocumenttypesConfigSP &documenttypes,
               const std::shared_ptr<const DocumentTypeRepo> &repo,
               const ProtonConfigSP &protonConfig,
               const FiledistributorrpcConfigSP &filedistRpcConfSP,
               const BucketspacesConfigSP &bucketspaces,
               const search::TuneFileDocumentDB::SP &tuneFileDocumentDB,
               const HwInfo & hwInfo)
    : _documenttypes(documenttypes),
      _repo(repo),
      _proton(protonConfig),
      _fileDistributorRpc(filedistRpcConfSP),
      _bucketspaces(bucketspaces),
      _tuneFileDocumentDB(tuneFileDocumentDB),
      _hwInfo(hwInfo),
      _generation(generation)
{ }

BootstrapConfig::~BootstrapConfig() = default;

bool
BootstrapConfig::operator==(const BootstrapConfig &rhs) const
{
    return equals<DocumenttypesConfig>(_documenttypes.get(), rhs._documenttypes.get()) &&
        _repo.get() == rhs._repo.get() &&
        equals<ProtonConfig>(_proton.get(), rhs._proton.get()) &&
        equals<FiledistributorrpcConfig>(_fileDistributorRpc.get(), rhs._fileDistributorRpc.get()) &&
        equals<BucketspacesConfig>(_bucketspaces.get(), rhs._bucketspaces.get()) &&
        equals<TuneFileDocumentDB>(_tuneFileDocumentDB.get(), rhs._tuneFileDocumentDB.get()) &&
        (_hwInfo == rhs._hwInfo);
}


bool
BootstrapConfig::valid() const
{
    return _documenttypes && _repo && _proton && _fileDistributorRpc && _bucketspaces && _tuneFileDocumentDB;
}

} // namespace proton
