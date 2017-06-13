// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancedocumentsubdb.h"

namespace proton {

MaintenanceDocumentSubDB::MaintenanceDocumentSubDB()
    : _metaStore(),
      _retriever(),
      _subDbId(0u)
{ }

MaintenanceDocumentSubDB::~MaintenanceDocumentSubDB() { }

MaintenanceDocumentSubDB::MaintenanceDocumentSubDB(const IDocumentMetaStore::SP & metaStore,
                                                   const IDocumentRetriever::SP & retriever,
                                                   uint32_t subDbId)
    : _metaStore(metaStore),
      _retriever(retriever),
      _subDbId(subDbId)
{ }

void
MaintenanceDocumentSubDB::clear() {
    _metaStore.reset();
    _retriever.reset();
    _subDbId = 0u;
} 

} // namespace proton
