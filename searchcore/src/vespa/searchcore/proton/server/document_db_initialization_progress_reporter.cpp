// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_initialization_progress_reporter.h"

#include "document_db_initialization_status.h"
#include "documentdb.h"
#include "feedhandler.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/data/slime/slime.h>


#include <vespa/log/log.h>
LOG_SETUP(".proton.server.document_db_initialization_progress_reporter");

namespace proton {

DocumentDBInitializationProgressReporter::DocumentDBInitializationProgressReporter(const std::string &name, DocumentDB &documentDB) :
    _name(name),
    _documentDB(documentDB) {
}

void DocumentDBInitializationProgressReporter::reportProgress(const vespalib::slime::Inserter &inserter) const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    vespalib::slime::Cursor &dbCursor = inserter.insertObject();
    dbCursor.setString("name", _name);

    DocumentDBInitializationStatus::State state = _documentDB.getInitializationStatus().getState();
    dbCursor.setString("state", DocumentDBInitializationStatus::stateToString(state));

    if (state == DocumentDBInitializationStatus::State::REPLAYING) {
        dbCursor.setDouble("replay_progress", _documentDB.getFeedHandler().getReplayProgress());

    } else if (state == DocumentDBInitializationStatus::State::LOAD) {
        vespalib::slime::Cursor &subdbCursor = dbCursor.setObject("ready_subdb");
        vespalib::slime::Cursor &loadedCursor = subdbCursor.setArray("loaded_attributes");
        vespalib::slime::Cursor &loadingCursor = subdbCursor.setArray("loading_attributes");
        vespalib::slime::ArrayInserter loadingArrayInserter(loadingCursor);
        vespalib::slime::Cursor &queuedCursor = subdbCursor.setArray("queued_attributes");

        for (const auto &reporter : _attributes) {
            vespalib::Slime slime;
            vespalib::slime::SlimeInserter ins(slime);
            reporter->reportProgress(ins);

            vespalib::slime::Inspector &inspector = slime.get();
            std::string name = inspector[slime.lookup("name")].asString().make_string();
            std::string status = inspector[slime.lookup("status")].asString().make_string();

            if (status == "queued") {
                queuedCursor.addString(name);

            } else if (status == "loaded") {
                loadedCursor.addString(name);

            } else {
                reporter->reportProgress(loadingArrayInserter);
            }
        }
    }
}

}
