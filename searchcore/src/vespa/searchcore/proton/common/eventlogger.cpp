// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eventlogger.h"
#include <vespa/searchlib/util/logutil.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.eventlogger");

using search::util::LogUtil;
using vespalib::JSONStringer;
using vespalib::make_string;

namespace {

using search::SerialNum;
using vespalib::string;

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
    EV_STATE(eventName.c_str(), jstr.toString().c_str());
}

void
doTransactionLogReplayComplete(const string &domainName, int64_t elapsedTimeMs, const string &eventName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("domain").appendString(domainName);
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.endObject();
    EV_STATE(eventName.c_str(), jstr.toString().c_str());
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
    EV_STATE("transactionlog.replay.progress", jstr.toString().c_str());
}

void
EventLogger::transactionLogReplayComplete(const string &domainName, int64_t elapsedTimeMs)
{
    doTransactionLogReplayComplete(domainName, elapsedTimeMs, "transactionlog.replay.complete");
}

void
EventLogger::flushInit(const string &name)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.endObject();
    EV_STATE("flush.init", jstr.toString().c_str());
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
    EV_STATE("flush.start", jstr.toString().c_str());
}

void
EventLogger::flushComplete(const string &name, int64_t elapsedTimeMs,
                           const string &outputPath, size_t outputPathElems)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(name);
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    if (!outputPath.empty()) {
        jstr.appendKey("output");
        LogUtil::logDir(jstr, outputPath, outputPathElems);
    }
    jstr.endObject();
    EV_STATE("flush.complete", jstr.toString().c_str());
}

namespace {

void
addNames(JSONStringer &jstr, const std::vector<string> &names)
{
    jstr.appendKey("name");
    jstr.beginArray();
    for (auto name : names) {
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
    EV_STATE("populate.attribute.start", jstr.toString().c_str());
}

void
EventLogger::populateAttributeComplete(const std::vector<string> &names, int64_t documentsPopulated)
{
    JSONStringer jstr;
    jstr.beginObject();
    addNames(jstr, names);
    jstr.appendKey("documents.populated").appendInt64(documentsPopulated);
    jstr.endObject();
    EV_STATE("populate.attribute.complete", jstr.toString().c_str());
}

void
EventLogger::populateDocumentFieldStart(const string &fieldName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(fieldName);
    jstr.endObject();
    EV_STATE("populate.documentfield.start", jstr.toString().c_str());
}

void
EventLogger::populateDocumentFieldComplete(const string &fieldName, int64_t documentsPopulated)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("name").appendString(fieldName);
    jstr.appendKey("documents.populated").appendInt64(documentsPopulated);
    jstr.endObject();
    EV_STATE("populate.documentfield.complete", jstr.toString().c_str());
}

void
EventLogger::lidSpaceCompactionComplete(const string &subDbName, uint32_t lidLimit)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("lidlimit").appendInt64(lidLimit);
    jstr.endObject();
    EV_STATE("lidspace.compaction.complete", jstr.toString().c_str());
}


void
EventLogger::reprocessDocumentsStart(const string &subDb, double visitCost)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDb);
    jstr.appendKey("visitcost").appendDouble(visitCost);
    jstr.endObject();
    EV_STATE("reprocess.documents.start", jstr.toString().c_str());
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
    EV_STATE("reprocess.documents.progress", jstr.toString().c_str());
}
    

void
EventLogger::reprocessDocumentsComplete(const string &subDb, double visitCost, int64_t elapsedTimeMs)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDb);
    jstr.appendKey("visitcost").appendDouble(visitCost);
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.endObject();
    EV_STATE("reprocess.documents.complete", jstr.toString().c_str());
}

void
EventLogger::loadAttributeStart(const vespalib::string &subDbName, const vespalib::string &attrName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("name").appendString(attrName);
    jstr.endObject();
    EV_STATE("load.attribute.start", jstr.toString().c_str());
}

void
EventLogger::loadAttributeComplete(const vespalib::string &subDbName,
                                   const vespalib::string &attrName, int64_t elapsedTimeMs)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("name").appendString(attrName);
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.endObject();
    EV_STATE("load.attribute.complete", jstr.toString().c_str());
}

namespace {

void
loadComponentStart(const vespalib::string &subDbName, const vespalib::string &componentName)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.endObject();
    EV_STATE(make_string("load.%s.start", componentName.c_str()).c_str(), jstr.toString().c_str());
}

void
loadComponentComplete(const vespalib::string &subDbName, const vespalib::string &componentName, int64_t elapsedTimeMs)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("documentsubdb").appendString(subDbName);
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.endObject();
    EV_STATE(make_string("load.%s.complete", componentName.c_str()).c_str(), jstr.toString().c_str());
}

}

void
EventLogger::loadDocumentMetaStoreStart(const vespalib::string &subDbName)
{
    loadComponentStart(subDbName, "documentmetastore");
}

void
EventLogger::loadDocumentMetaStoreComplete(const vespalib::string &subDbName, int64_t elapsedTimeMs)
{
    loadComponentComplete(subDbName, "documentmetastore", elapsedTimeMs);
}

void
EventLogger::loadDocumentStoreStart(const vespalib::string &subDbName)
{
    loadComponentStart(subDbName, "documentstore");
}

void
EventLogger::loadDocumentStoreComplete(const vespalib::string &subDbName, int64_t elapsedTimeMs)
{
    loadComponentComplete(subDbName, "documentstore", elapsedTimeMs);
}

} // namespace proton
