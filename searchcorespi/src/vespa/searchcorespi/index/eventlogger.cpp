// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eventlogger.h"
#include <vespa/searchlib/util/logutil.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.eventlogger");

using vespalib::JSONStringer;
using search::util::LogUtil;

namespace searchcorespi::index {

void
EventLogger::diskIndexLoadStart(const vespalib::string &indexDir)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("input");
    LogUtil::logDir(jstr, indexDir, 6);
    jstr.endObject();
    EV_STATE("diskindex.load.start", jstr.toString().data());
}

void
EventLogger::diskIndexLoadComplete(const vespalib::string &indexDir,
                                   int64_t elapsedTimeMs)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.appendKey("input");
    LogUtil::logDir(jstr, indexDir, 6);
    jstr.endObject();
    EV_STATE("diskindex.load.complete", jstr.toString().data());
}

void
EventLogger::diskFusionStart(const std::vector<vespalib::string> &sources,
                             const vespalib::string &fusionDir)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("inputs");
    jstr.beginArray();
    for (size_t i = 0; i < sources.size(); ++i) {
        LogUtil::logDir(jstr, sources[i], 6);
    }
    jstr.endArray();
    jstr.appendKey("output");
    LogUtil::logDir(jstr, fusionDir, 6);
    jstr.endObject();
    EV_STATE("fusion.start", jstr.toString().data());
}

void
EventLogger::diskFusionComplete(const vespalib::string &fusionDir,
                                int64_t elapsedTimeMs)
{
    JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("time.elapsed.ms").appendInt64(elapsedTimeMs);
    jstr.appendKey("output");
    LogUtil::logDir(jstr, fusionDir, 6);
    jstr.endObject();
    EV_STATE("fusion.complete", jstr.toString().data());
}

}
