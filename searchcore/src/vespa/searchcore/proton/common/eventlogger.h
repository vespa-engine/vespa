// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace proton {

/**
 * Class used to log various events.
 **/
class EventLogger {
private:
    typedef search::SerialNum SerialNum;
    typedef vespalib::string string;
public:
    static void transactionLogReplayComplete(const string &domainName, int64_t elapsedTimeMs);
    static void populateAttributeStart(const std::vector<string> &names);
    static void populateAttributeComplete(const std::vector<string> &names, int64_t documentsVisisted);
    static void populateDocumentFieldStart(const string &fieldName);
    static void populateDocumentFieldComplete(const string &fieldName, int64_t documentsVisisted);
    static void lidSpaceCompactionComplete(const string &subDbName, uint32_t lidLimit);
    static void reprocessDocumentsStart(const string &subDb, double visitCost);
    static void reprocessDocumentsProgress(const string &subDb, double progress, double visitCost);
    static void reprocessDocumentsComplete(const string &subDb, double visitCost, int64_t elapsedTimeMs);
    static void transactionLogReplayStart(const string &domainName,
                                          SerialNum first,
                                          SerialNum last);
    static void transactionLogReplayProgress(const string &domainName,
                                             float progress,
                                             SerialNum first,
                                             SerialNum last,
                                             SerialNum current);
    static void flushInit(const string &name);
    static void flushStart(const string &name,
                           int64_t beforeMemory,
                           int64_t afterMemory,
                           int64_t toFreeMemory,
                           SerialNum unflushed,
                           SerialNum current);
    static void flushComplete(const string &name,
                              int64_t elapsedTimeMs,
                              const string &outputPath,
                              size_t outputPathElems);
    static void flushPrune(const string &name, SerialNum oldestFlushed);
    static void loadAttributeStart(const vespalib::string &subDbName, const vespalib::string &attrName);
    static void loadAttributeComplete(const vespalib::string &subDbName,
                                      const vespalib::string &attrName, int64_t elapsedTimeMs);
    static void loadDocumentMetaStoreStart(const vespalib::string &subDbName);
    static void loadDocumentMetaStoreComplete(const vespalib::string &subDbName, int64_t elapsedTimeMs);
    static void loadDocumentStoreStart(const vespalib::string &subDbName);
    static void loadDocumentStoreComplete(const vespalib::string &subDbName, int64_t elapsedTimeMs);
    static void transactionLogPruneComplete(const string &domainName, SerialNum prunedSerial);
};

} // namespace proton

