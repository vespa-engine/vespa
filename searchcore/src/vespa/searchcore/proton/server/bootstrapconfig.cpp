// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bootstrapconfig.h"

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
               const DocumentTypeRepo::SP &repo,
               const ProtonConfigSP &protonConfig,
               const FiledistributorrpcConfigSP &filedistRpcConfSP,
               const search::TuneFileDocumentDB::SP &tuneFileDocumentDB)
    : _documenttypes(documenttypes),
      _repo(repo),
      _proton(protonConfig),
      _fileDistributorRpc(filedistRpcConfSP),
      _tuneFileDocumentDB(tuneFileDocumentDB),
      _generation(generation)
{ }

BootstrapConfig::~BootstrapConfig() { }

bool
BootstrapConfig::operator==(const BootstrapConfig &rhs) const
{
    return equals<DocumenttypesConfig>(_documenttypes.get(),
                                       rhs._documenttypes.get()) &&
        _repo.get() == rhs._repo.get() &&
        equals<ProtonConfig>(_proton.get(), rhs._proton.get()) &&
        equals<TuneFileDocumentDB>(_tuneFileDocumentDB.get(),
                                   rhs._tuneFileDocumentDB.get());
}


bool
BootstrapConfig::valid() const
{
    return _documenttypes.get() != NULL &&
                    _repo.get() != NULL &&
                  _proton.get() != NULL &&
      _tuneFileDocumentDB.get() != NULL;
}


} // namespace proton
