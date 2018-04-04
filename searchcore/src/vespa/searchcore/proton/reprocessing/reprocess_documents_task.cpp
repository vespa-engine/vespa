// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reprocess_documents_task.h"
#include "attribute_reprocessing_initializer.h"
#include "document_reprocessing_handler.h"
#include <vespa/searchcore/proton/common/eventlogger.h>

namespace proton {

ReprocessDocumentsTask::
ReprocessDocumentsTask(IReprocessingInitializer &initializer,
                       const proton::ISummaryManager::SP &sm,
                       const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo,
                       const vespalib::string &subDbName,
                       uint32_t docIdLimit)
    : _sm(sm),
      _docTypeRepo(docTypeRepo),
      _subDbName(subDbName),
      _visitorProgress(0.0),
      _visitorCost(0.0),
      _handler(docIdLimit),
      _startTime(0),
      _loggedProgress(0.0),
      _loggedTime(0)
{
    initializer.initialize(_handler);
    if (_handler.hasProcessors()) {
        search::IDocumentStore &docstore = _sm->getBackingStore();
        _visitorCost = docstore.getVisitCost();
    }
}


void
ReprocessDocumentsTask::run()
{
    if (_handler.hasProcessors()) {
        EventLogger::reprocessDocumentsStart(_subDbName,
                                             _visitorCost);
        fastos::TimeStamp ts(fastos::ClockSystem::now());
        _startTime = ts.ms();
        _loggedTime = _startTime;
        search::IDocumentStore &docstore = _sm->getBackingStore();
        if (_handler.hasRewriters()) {
            docstore.accept(_handler.getRewriteVisitor(),
                    *this,
                    *_docTypeRepo);
        } else {
            docstore.accept(_handler,
                    *this,
                    *_docTypeRepo);
        }
        _handler.done();
        ts = fastos::ClockSystem::now();
        int64_t elapsedTime = ts.ms() - _startTime;
        EventLogger::reprocessDocumentsComplete(_subDbName,
                                                _visitorCost,
                                                elapsedTime);
    }
}


void
ReprocessDocumentsTask::updateProgress(double progress)
{
    _visitorProgress = progress;
    double deltaProgress = progress - _loggedProgress;
    if (deltaProgress >= 0.01) {
        fastos::TimeStamp ts = fastos::ClockSystem::now();
        int64_t logDelayTime = ts.ms() - _loggedTime;
        if (logDelayTime >= 60000 || deltaProgress >= 0.10) {
            EventLogger::reprocessDocumentsProgress(_subDbName,
                                                    progress,
                                                    _visitorCost);
            _loggedTime = ts.ms();
            _loggedProgress = progress;
        }
    }
}


IReprocessingTask::Progress
ReprocessDocumentsTask::getProgress() const
{
    return Progress(_visitorProgress, _visitorCost);
}


} // namespace proton
