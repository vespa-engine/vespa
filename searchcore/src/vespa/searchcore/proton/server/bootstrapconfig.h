// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>

namespace vespa::config::content::core::internal { class InternalBucketspacesType; }

namespace proton {

/**
 * This class represents all config classes required by proton to bootstrap.
 */
class BootstrapConfig
{
public:
    typedef std::shared_ptr<BootstrapConfig> SP;
    typedef std::shared_ptr<vespa::config::search::core::ProtonConfig> ProtonConfigSP;
    using FiledistributorrpcConfig = const cloud::config::filedistribution::FiledistributorrpcConfig;
    using FiledistributorrpcConfigSP = std::shared_ptr<FiledistributorrpcConfig>;

    typedef DocumentDBConfig::DocumenttypesConfigSP DocumenttypesConfigSP;
    using BucketspacesConfig = const vespa::config::content::core::internal::InternalBucketspacesType;
    using BucketspacesConfigSP = std::shared_ptr<BucketspacesConfig>;

private:
    DocumenttypesConfigSP          _documenttypes;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    ProtonConfigSP                 _proton;
    FiledistributorrpcConfigSP     _fileDistributorRpc;
    BucketspacesConfigSP           _bucketspaces;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    HwInfo                         _hwInfo;
    int64_t                        _generation;

public:
    BootstrapConfig(int64_t generation,
                    const DocumenttypesConfigSP & documenttypes,
                    const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                    const ProtonConfigSP &protonConfig,
                    const FiledistributorrpcConfigSP &filedistRpcConfSP,
                    const BucketspacesConfigSP &bucketspaces,
                    const search::TuneFileDocumentDB::SP &_tuneFileDocumentDB,
                    const HwInfo & hwInfo);
    ~BootstrapConfig();

    const document::DocumenttypesConfig &getDocumenttypesConfig() const { return *_documenttypes; }
    const FiledistributorrpcConfig &getFiledistributorrpcConfig() const { return *_fileDistributorRpc; }
    const FiledistributorrpcConfigSP &getFiledistributorrpcConfigSP() const { return _fileDistributorRpc; }
    const DocumenttypesConfigSP &getDocumenttypesConfigSP() const { return _documenttypes; }
    const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepoSP() const { return _repo; }
    const vespa::config::search::core::ProtonConfig &getProtonConfig() const { return *_proton; }
    const ProtonConfigSP &getProtonConfigSP() const { return _proton; }
    const BucketspacesConfigSP &getBucketspacesConfigSP() const { return _bucketspaces; }
    const search::TuneFileDocumentDB::SP &getTuneFileDocumentDBSP() const { return _tuneFileDocumentDB; }
    int64_t getGeneration() const { return _generation; }
    const HwInfo & getHwInfo() const { return _hwInfo; }

    /**
     * Shared pointers are checked for identity, not equality.
     */
    bool operator==(const BootstrapConfig &rhs) const;
    bool valid() const;
};

} // namespace proton
