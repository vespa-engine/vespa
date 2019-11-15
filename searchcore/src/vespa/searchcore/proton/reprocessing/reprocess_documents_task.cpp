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
      _loggedProgress(0.0)
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
        EventLogger::reprocessDocumentsStart(_subDbName, _visitorCost);
        _stopWatch = fastos::StopWatch();
        search::IDocumentStore &docstore = _sm->getBackingStore();
        if (_handler.hasRewriters()) {
            docstore.accept(_handler.getRewriteVisitor(), *this, *_docTypeRepo);
        } else {
            docstore.accept(_handler, *this, *_docTypeRepo);
        }
        _handler.done();
        _stopWatch.stop();
        EventLogger::reprocessDocumentsComplete(_subDbName, _visitorCost, _stopWatch.elapsed().ms());
    }
}

void
ReprocessDocumentsTask::updateProgress(double progress)
{
    _visitorProgress = progress;
    double deltaProgress = progress - _loggedProgress;
    if (deltaProgress >= 0.01) {
        fastos::StopWatch intermediate = _stopWatch;
        fastos::TimeStamp logDelayTime = intermediate.stop().elapsed() - _stopWatch.elapsed();
        if (logDelayTime.ms() >= 60000 || deltaProgress >= 0.10) {
            EventLogger::reprocessDocumentsProgress(_subDbName, progress, _visitorCost);
            _stopWatch.stop();
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
