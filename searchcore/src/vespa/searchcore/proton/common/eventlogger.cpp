// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eventlogger.h"
#include <vespa/searchlib/util/logutil.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.eventlogger");

using search::util::LogUtil;
using vespalib::JSONStringer;
using vespalib::make_string;
using vespalib::count_ms;

namespace {

using search::SerialNum;
using std::string;

void
doTransactionLogReplayStart(const string &domainName, SerialNum first, SerialNum last, const string &eventName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("domain").appendString(domainName);
    jstr.appendKey("serialnum")
        .beginObject()
        .appendKey("first").appendInt64(first)
        .appendKey("last").appendInt64(last)
        .endObject();
    jstr.endObject();
    EV_STATE(eventName.c_str(), jstr.str().c_str() );
}

void
doTransactionLogReplayComplete(const string &domainName, vespalib::duration elapsedTime, const string &eventName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("domain").appendString(domainName);
    jstr.appendKey("time.elapsed.ms").appendInt64(count_ms(elapsedTime));
    jstr.endObject();
    EV_STATE(eventName.c_str(), jstr.str().c_str() );
}

}

namespace proton {

void
EventLogger::transactionLogReplayStart(const string &domainName, SerialNum first, SerialNum last)
{
    doTransactionLogReplayStart(domainName, first, last, "transactionlog.replay.start");
}

void
EventLogger::transactionLogReplayProgress(const string &domainName, float progress,
                                          SerialNum first, SerialNum last, SerialNum current)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("domain").appendString(domainName);
    jstr.appendKey("progress").appendFloat(progress);
    jstr.appendKey("serialnum")
        .beginObject()
        .appendKey("first").appendInt64(first)
        .appendKey("last").appendInt64(last)
        .appendKey("current").appendInt64(current)
        .endObject();
    jstr.endObject();
    EV_STATE("transactionlog.replay.progress", jstr.str().c_str() );
}

void
EventLogger::transactionLogReplayComplete(const string &domainName, vespalib::duration elapsedTime)
{
    doTransactionLogReplayComplete(domainName, elapsedTime, "transactionlog.replay.complete");
}

void
EventLogger::flushInit(const string &name)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.endObject();
    EV_STATE("flush.init", jstr.str().c_str() );
}

void
EventLogger::flushStart(const string &name, int64_t beforeMemory, int64_t afterMemory,
                        int64_t toFreeMemory, SerialNum unflushed, SerialNum current)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.appendKey("memory")
        .beginObject()
        .appendKey("before").appendInt64(beforeMemory)
        .appendKey("after").appendInt64(afterMemory)
        .appendKey("tofree").appendInt64(toFreeMemory)
        .endObject();
    jstr.appendKey("serialnum")
        .beginObject()
        .appendKey("unflushed").appendInt64(unflushed)
        .appendKey("current").appendInt64(current)
        .endObject();
    jstr.endObject();
    EV_STATE("flush.start", jstr.str().c_str() );
}

void
EventLogger::flushComplete(const string &name, vespalib::duration elapsedTime, SerialNum flushed,
                           const string &outputPath, size_t outputPathElems)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.appendKey("time.elapsed.ms").appendInt64(count_ms(elapsedTime));
    jstr.appendKey("serialnum")
            .beginObject()
            .appendKey("flushed").appendInt64(flushed)
            .endObject();
    if (!outputPath.empty()) {
        jstr.appendKey("output");
        LogUtil::logDir(jstr, outputPath, outputPathElems);
    }
    jstr.endObject();
    EV_STATE("flush.complete", jstr.str().c_str() );
}

void
EventLogger::flushPrune(const string &name, SerialNum oldestFlushed)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.appendKey("serialnum")
            .beginObject()
            .appendKey("oldestflushed").appendInt64(oldestFlushed)
            .endObject();
    jstr.endObject();
    EV_STATE("flush.prune", jstr.str().c_str() );
}

namespace {

void
addNames(JSONStringer &jstr, const std::vector<string> &names)
{
    jstr.appendKey("name");
    jstr.beginArray();
    for (const auto& name : names) {
        jstr.appendString(name);
    }
    jstr.endArray();
}

}

void
EventLogger::populateAttributeStart(const std::vector<string> &names)
{
    JSONStringer jstr;
    jstr.beginObject();
    addNames(jstr, names);
    jstr.endObject();
    EV_STATE("populate.attribute.start", jstr.str().c_str() );
}

void
EventLogger::populateAttributeComplete(const std::vector<string> &names, int64_t documentsPopulated)
{
    JSONStringer jstr;
    jstr.beginObject();
    addNames(jstr, names);
    jstr.appendKey("documents.populated").appendInt64(documentsPopulated);
    jstr.endObject();
    EV_STATE("populate.attribute.complete", jstr.str().c_str() );
}

void
EventLogger::populateDocumentFieldStart(const string &fieldName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(fieldName);
    jstr.endObject();
    EV_STATE("populate.documentfield.start", jstr.str().c_str());
}

void
EventLogger::populateDocumentFieldComplete(const string &fieldName, int64_t documentsPopulated)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(fieldName);
    jstr.appendKey("documents.populated").appendInt64(documentsPopulated);
    jstr.endObject();
    EV_STATE("populate.documentfield.complete", jstr.str().c_str());
}

void
EventLogger::lidSpaceCompactionStart(const string &subDbName,
                                     uint32_t lidBloat, uint32_t allowedLidBloat,
                                     double lidBloatFactor, double allowedLidBloatFactor,
                                     uint32_t lidLimit, uint32_t lowestFreeLid)
{
    // lidspace compaction throttling might cause a lot of stop-and-go for compaction,
    // causing an excessive amount of (re-)start edge events. For now, only log if debug
    // level has been enabled on this component.
    if (LOG_WOULD_LOG(debug)) {
        JSONStringer jstr;
        jstr.beginObject();
        jstr.appendKey("documentsubdb").appendString(subDbName)
            .appendKey("lidbloat").appendInt64(lidBloat)
            .appendKey("allowedlidbloat").appendInt64(allowedLidBloat)
            .appendKey("lidbloatfactor").appendDouble(lidBloatFactor)
            .appendKey("allowedlidbloatfactor").appendDouble(allowedLidBloatFactor)
            .appendKey("lidlimit").appendInt64(lidLimit)
            .appendKey("lowestfreelid").appendInt64(lowestFreeLid);
        jstr.endObject();
        EV_STATE("lidspace.compaction.start", jstr.str().c_str());
    }
}

void
EventLogger::lidSpaceCompactionRestart(const string &subDbName,
                                       uint32_t usedLids, uint32_t allowedLidBloat,
                                       uint32_t highestUsedLid, uint32_t lowestFreeLid)
{
    // lidspace compaction throttling might cause a lot of stop-and-go for compaction,
    // causing an excessive amount of (re-)start edge events. For now, only log if debug
    // level has been enabled on this component.
    if (LOG_WOULD_LOG(debug)) {
        JSONStringer jstr;
        jstr.beginObject();
        jstr.appendKey("documentsubdb").appendString(subDbName)
            .appendKey("usedlids").appendInt64(usedLids)
            .appendKey("allowedlidbloat").appendInt64(allowedLidBloat)
            .appendKey("highestusedlid").appendInt64(highestUsedLid)
            .appendKey("lowestfreelid").appendInt64(lowestFreeLid);
        jstr.endObject();
        EV_STATE("lidspace.compaction.restart", jstr.str().c_str());
    }
}

void
EventLogger::lidSpaceCompactionComplete(const string &subDbName, uint32_t lidLimit)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("lidlimit").appendInt64(lidLimit);
    jstr.endObject();
    EV_STATE("lidspace.compaction.complete", jstr.str().c_str());
}


void
EventLogger::reprocessDocumentsStart(const string &subDb, double visitCost)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDb);
    jstr.appendKey("visitcost").appendDouble(visitCost);
    jstr.endObject();
    EV_STATE("reprocess.documents.start", jstr.str().c_str());
}
    

void
EventLogger::reprocessDocumentsProgress(const string &subDb, double progress, double visitCost)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDb);
    jstr.appendKey("progress").appendDouble(progress);
    jstr.appendKey("visitcost").appendDouble(visitCost);
    jstr.endObject();
    EV_STATE("reprocess.documents.progress", jstr.str().c_str());
}
    

void
EventLogger::reprocessDocumentsComplete(const string &subDb, double visitCost, vespalib::duration elapsedTime)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDb);
    jstr.appendKey("visitcost").appendDouble(visitCost);
    jstr.appendKey("time.elapsed.ms").appendInt64(count_ms(elapsedTime));
    jstr.endObject();
    EV_STATE("reprocess.documents.complete", jstr.str().c_str());
}

void
EventLogger::loadAttributeStart(const std::string &subDbName, const std::string &attrName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("name").appendString(attrName);
    jstr.endObject();
    EV_STATE("load.attribute.start", jstr.str().c_str());
}

void
EventLogger::loadAttributeComplete(const std::string &subDbName,
                                   const std::string &attrName, vespalib::duration elapsedTime)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("name").appendString(attrName);
    jstr.appendKey("time.elapsed.ms").appendInt64(count_ms(elapsedTime));
    jstr.endObject();
    EV_STATE("load.attribute.complete", jstr.str().c_str() );
}

namespace {

void
loadComponentStart(const std::string &subDbName, const std::string &componentName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.endObject();
    EV_STATE(make_string("load.%s.start", componentName.c_str()).c_str(), jstr.str().c_str() );
}

void
loadComponentComplete(const std::string &subDbName, const std::string &componentName, vespalib::duration elapsedTime)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("time.elapsed.ms").appendInt64(count_ms(elapsedTime));
    jstr.endObject();
    EV_STATE(make_string("load.%s.complete", componentName.c_str()).c_str(), jstr.str().c_str() );
}

}

void
EventLogger::loadDocumentMetaStoreStart(const std::string &subDbName)
{
    loadComponentStart(subDbName, "documentmetastore");
}

void
EventLogger::loadDocumentMetaStoreComplete(const std::string &subDbName, vespalib::duration elapsedTime)
{
    loadComponentComplete(subDbName, "documentmetastore", elapsedTime);
}

void
EventLogger::loadDocumentStoreStart(const std::string &subDbName)
{
    loadComponentStart(subDbName, "documentstore");
}

void
EventLogger::loadDocumentStoreComplete(const std::string &subDbName, vespalib::duration elapsedTime)
{
    loadComponentComplete(subDbName, "documentstore", elapsedTime);
}

void
EventLogger::transactionLogPruneComplete(const string &domainName, SerialNum prunedSerial)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("domain").appendString(domainName);
    jstr.appendKey("serialnum")
        .beginObject()
        .appendKey("pruned").appendInt64(prunedSerial)
        .endObject();
    jstr.endObject();
    EV_STATE("transactionlog.prune.complete", jstr.str().c_str() );
}

} // namespace proton
