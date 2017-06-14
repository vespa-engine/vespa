// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_explorer.h"

#include "document_meta_store_read_guards.h"
#include "document_subdb_collection_explorer.h"
#include "maintenance_controller_explorer.h"
#include <vespa/searchcore/proton/common/state_reporter_utils.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_explorer.h>
#include <vespa/searchcore/proton/matching/session_manager_explorer.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::StateExplorer;
using namespace vespalib::slime;

namespace proton {

DocumentDBExplorer::DocumentDBExplorer(const DocumentDB::SP &docDb)
    : _docDb(docDb)
{
}

DocumentDBExplorer::~DocumentDBExplorer() {}

void
DocumentDBExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    Cursor &object = inserter.insertObject();
    object.setString("documentType", _docDb->getDocTypeName().toString());
    {
        StateReporterUtils::convertToSlime(*_docDb->reportStatus(), ObjectInserter(object, "status"));
    }
    {
        // TODO(geirst): Avoid const cast by adding const interface to
        // IDocumentMetaStoreContext as seen from IDocumentSubDB.
        DocumentMetaStoreReadGuards dmss
            (const_cast<DocumentSubDBCollection &>(_docDb->getDocumentSubDBs()));
        Cursor &documents = object.setObject("documents");
        documents.setLong("active", dmss.numActiveDocs());
        documents.setLong("indexed", dmss.numIndexedDocs());
        documents.setLong("stored", dmss.numStoredDocs());
        documents.setLong("removed", dmss.numRemovedDocs());
    }
}

const vespalib::string SUB_DB = "subdb";
const vespalib::string BUCKET_DB = "bucketdb";
const vespalib::string MAINTENANCE_CONTROLLER = "maintenancecontroller";
const vespalib::string SESSION = "session";

std::vector<vespalib::string>
DocumentDBExplorer::get_children_names() const
{
    return {SUB_DB, BUCKET_DB, MAINTENANCE_CONTROLLER, SESSION};
}

std::unique_ptr<StateExplorer>
DocumentDBExplorer::get_child(vespalib::stringref name) const
{
    if (name == SUB_DB) {
        return std::unique_ptr<StateExplorer>
            (new DocumentSubDBCollectionExplorer(_docDb->getDocumentSubDBs()));
    } else if (name == BUCKET_DB) {
        // TODO(geirst): const_cast can be avoided if we add const guard to BucketDBOwner.
        return std::unique_ptr<StateExplorer>(new BucketDBExplorer(
            (const_cast<DocumentSubDBCollection &>(_docDb->getDocumentSubDBs())).getBucketDB().takeGuard()));
    } else if (name == MAINTENANCE_CONTROLLER) {
        return std::unique_ptr<StateExplorer>
            (new MaintenanceControllerExplorer(_docDb->getMaintenanceController().getJobList()));
    } else if (name == SESSION) {
        return std::unique_ptr<StateExplorer>
            (new matching::SessionManagerExplorer(_docDb->session_manager()));
    }
    return std::unique_ptr<StateExplorer>(nullptr);
}

} // namespace proton
