// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskreporter.h"
#include "kernelmetrictool.h"

#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/string.h>
#include <iostream>

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

DiskReporter::DiskReporter() {}

DiskReporter::~DiskReporter() {}

void DiskReporter::report(vespalib::JsonStream& jsonreport) {
    vespalib::string content = readFile("/proc/diskstats");
    vespalib::StringTokenizer st(vespalib::StringTokenizer(content.c_str(), "\n", ""));
    jsonreport << "disk" << Object();
    for (uint32_t i=2; i<st.size(); ++i) {
        vespalib::string line(st[i]);
        /*
         *  The /proc/diskstats file displays the I/O statistics
         *  of block devices.
         *  0 - major number
         *  1 - minor mumber
         *  2 - device name
         *  3 - reads completed successfully
         *  4 - reads merged
         *  5 - sectors read
         *  6 - time spent reading (ms)
         *  7 - writes completed
         *  8 - writes merged
         *  9 - sectors written
         * 10 - time spent writing (ms)
         * 11 - I/Os currently in progress
         * 12 - time spent doing I/Os (ms)
         * 13 - weighted time spent doing I/Os (ms)
         */
        vespalib::string name = getToken(2, line);
        if (name.substr(0, 3) == "ram" || name.substr(0, 3) == "dm-"
                || name.substr(0, 4) == "loop") {
            continue;
        }
        jsonreport << name << Object();
        jsonreport << "reads merged" << toLong(getToken(4, line));
        jsonreport << "writes merged" << toLong(getToken(8, line));
        jsonreport << "reads" << toLong(getToken(3, line));
        jsonreport << "writes" << toLong(getToken(7, line));
        jsonreport << "in progress" << toLong(getToken(11, line));
        jsonreport << "sectors read" << toLong(getToken(5, line));
        jsonreport << "sectores written" << toLong(getToken(9, line));
        jsonreport << "time spent" << toLong(getToken(12, line));
        jsonreport << End();
    }
    jsonreport << End();
}
} /* namespace storage */
