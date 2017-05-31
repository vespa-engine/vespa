// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "networkreporter.h"
#include "kernelmetrictool.h"
#include <vespa/vespalib/text/stringtokenizer.h>

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
using kernelmetrictool::readFile;
using kernelmetrictool::getLine;
using kernelmetrictool::getToken;
using kernelmetrictool::toLong;
using kernelmetrictool::stripWhitespace;
}

void NetworkReporter::report(vespalib::JsonStream& jsonreport) {

    vespalib::string content = readFile("/proc/net/dev");
    vespalib::StringTokenizer st(content.c_str(), "\n", "");

    jsonreport << "network" << Object();

    for (uint32_t i=2; i<st.size(); ++i) {
        vespalib::string line = st[i];
        vespalib::string::size_type pos = line.find(':');
        if (pos == vespalib::string::npos) {
            continue;
        }
        jsonreport << stripWhitespace(line.substr(0, pos)) << Object();
        vespalib::string data(line.substr(pos+1));
        jsonreport << "input" << Object();
        jsonreport << "bytes" << toLong(getToken(0, data));
        jsonreport << "packets" << toLong(getToken(1, data));
        jsonreport << "errors" << toLong(getToken(2, data));
        jsonreport << "drops" << toLong(getToken(3, data));
        jsonreport << End() << "output" << Object();
        jsonreport << "bytes" << toLong(getToken(8, data));
        jsonreport << "packets" << toLong(getToken(9, data));
        jsonreport << "errors" << toLong(getToken(10, data));
        jsonreport << "drops" << toLong(getToken(11, data));
        jsonreport << End();
        jsonreport << End();
    }
    jsonreport << End();
}
} /* namespace storage */
