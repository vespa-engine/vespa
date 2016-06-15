// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/config/retriever/configkeyset.h>
#include <vespa/config/retriever/configsnapshot.h>

namespace proton {

/**
 * This class represents all config classes required by proton to bootstrap.
 */
class BootstrapConfig
{
public:
    typedef std::shared_ptr<BootstrapConfig> SP;
    typedef std::shared_ptr<vespa::config::search::core::ProtonConfig> ProtonConfigSP;
    typedef DocumentDBConfig::DocumenttypesConfigSP DocumenttypesConfigSP;

private:
    DocumenttypesConfigSP _documenttypes;
    document::DocumentTypeRepo::SP _repo;
    ProtonConfigSP _proton;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    int64_t _generation;

public:
    BootstrapConfig(int64_t generation,
                    const DocumenttypesConfigSP & documenttypes,
                    const document::DocumentTypeRepo::SP &repo,
                    const ProtonConfigSP &protonConfig,
                    const search::TuneFileDocumentDB::SP &
                    _tuneFileDocumentDB);

    const document::DocumenttypesConfig &
    getDocumenttypesConfig(void) const
    {
        return *_documenttypes;
    }

    const DocumenttypesConfigSP &
    getDocumenttypesConfigSP(void) const
    {
        return _documenttypes;
    }

    const document::DocumentTypeRepo::SP &
    getDocumentTypeRepoSP() const
    {
        return _repo;
    }

    const vespa::config::search::core::ProtonConfig &
    getProtonConfig(void) const
    {
        return *_proton;
    }

    const ProtonConfigSP &
    getProtonConfigSP(void) const
    {
        return _proton;
    }

    const search::TuneFileDocumentDB::SP &
    getTuneFileDocumentDBSP(void) const
    {
        return _tuneFileDocumentDB;
    }

    int64_t
    getGeneration() const
    {
        return _generation;
    }

    void
    setGeneration(int64_t generation)
    {
        _generation = generation;
    }

    /**
     * Shared pointers are checked for identity, not equality.
     */
    bool
    operator==(const BootstrapConfig &rhs) const;

    bool
    valid(void) const;
};

} // namespace proton

