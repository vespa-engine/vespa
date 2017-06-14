// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "cpureporter.h"
#include "kernelmetrictool.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/text/stringtokenizer.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".cpureporter");

namespace storage {
namespace {

using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
using kernelmetrictool::readFile;
using kernelmetrictool::getLine;
using kernelmetrictool::getTokenCount;
using kernelmetrictool::getToken;
using kernelmetrictool::toLong;

const int proprityLevels = 7;
const vespalib::string priorityText[proprityLevels] =
    { "user", "nice", "system", "idle", "iowait", "ira", "softirq"};

struct CpuInfo {
    int _cpuIndex;
    std::array<uint64_t, proprityLevels> _usage;
    CpuInfo(int index) : _cpuIndex(index) {}

    uint64_t getTotalUsage() const {
        uint64_t total = 0;
        for (uint32_t i=0; i<_usage.size(); ++i) total += _usage[i];
        return total;
    }
};

struct CpuReport {
    std::vector<CpuInfo> _cpuInfo;
    uint64_t _contextSwitches = 0;
    int64_t _swappedIn = 0;
    int64_t _swappedOut = 0;
    uint64_t _processesCreated = 0;
    uint64_t _processesBlocked = 0;
    uint64_t _processesRunning = 0;

    CpuInfo getTotalCpuInfo() const {
        CpuInfo total(0);
        for (uint32_t i=0; i < 7; ++i) total._usage[i] = 0;
        for (uint32_t i=0; i < _cpuInfo.size(); ++i) {
            for (uint32_t j=0; j < _cpuInfo[i]._usage.size(); ++j) {
                total._usage[j] += _cpuInfo[i]._usage[j];
            }
        }
        return total;
    }
};

long getValueWithLog(
        const vespalib::string &content,
        const vespalib::string &lineStart,
        int pos) {
    vespalib::string line = getLine(lineStart, content);
    if (!line.empty()) {
        return toLong(getToken(pos, line));
    } else {
        LOGBP(debug, "Line not found in /proc/stat : '%s'\nLine start: %s",
                content.c_str(), lineStart.c_str());
    }
    return 0;
}

void populateCpus(const vespalib::string &content, std::vector<CpuInfo> &cpuInfo) {
    for (uint32_t i=0; true; ++i) {
        vespalib::string line = getLine("cpu" + std::to_string(i), content);
        if (line.empty()) break;
        if (getTokenCount(line) < 8) {
            LOGBP(warning, "Unexpected line found in /proc/stat. Expected at "
                    "least 8 tokens in cpu line: '%s'", line.c_str());
            continue;
        }
        CpuInfo info(i);
        for (uint32_t j=0; j<info._usage.size(); ++j) {
            info._usage[j] = toLong(getToken(j + 1, line));
        }
        cpuInfo.push_back(info);
    }
}

void populate(CpuReport& cpu) {
    /*
     *  Parse /proc/stat. Expected format:
     *  cpu  82190434 7180 85600255 12799031291 18183765 36669 458570
     *  cpu0 10564061 448 10381577 1598933932 3065407 36668 206231
     *  cpu1 10763472 763 10191606 1599538223 2655481 0 38988
     *  cpu2 10206570 720 9845299 1600695947 2402795 0 37218
     *  cpu3 10051762 966 9993106 1600750639 2354533 0 37565
     *  cpu4 10176554 961 10818954 1600288785 1871033 0 32228
     *  cpu5 10261736 845 11475459 1599497420 1917617 0 35456
     *  cpu6 10244739 1050 11095848 1599960998 1851423 0 34488
     *  cpu7 9921536 1422 11798403 1599365345 2065473 0 36392
     *  intr 16439148517 3349609784 9 0 6 17 0 0 0 54121 0 0 0 3 0 0 0 204582604 0 0 0 0 0 85 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
     *  swap 49238 9161900
     *  ctxt 17421122498
     *  btime 1296732462
     *  processes 83383001
     *  procs_running 1
     *  procs_blocked 0
     */
    vespalib::string content(readFile("/proc/stat"));

    populateCpus(content, cpu._cpuInfo);
    cpu._contextSwitches = getValueWithLog(content, "ctxt", 1);
    cpu._swappedIn = getValueWithLog(content, "swap", 1);
    cpu._swappedOut =  getValueWithLog(content, "swap", 2);
    cpu._processesCreated = getValueWithLog(content, "processes", 1);
    cpu._processesRunning = getValueWithLog(content, "procs_running", 1);
    cpu._processesBlocked = getValueWithLog(content, "procs_blocked", 1);
}
}

void CpuReporter::report(vespalib::JsonStream& jsonreport) {
    jsonreport << "cpu" << Object();
    CpuReport current;
    populate(current);
    CpuInfo currTotal = current.getTotalCpuInfo();

    jsonreport << "context switches" << current._contextSwitches;
    jsonreport << "pages swapped in"<< current._swappedIn;
    jsonreport << "pages swapped out"  << current._swappedOut;


    for (uint32_t i=0; i<=current._cpuInfo.size(); ++i) {
        const CpuInfo& post(i == 0 ? currTotal : current._cpuInfo[i-1]);
        jsonreport << (i == 0 ? "cputotal" : "cpu" + std::to_string(post._cpuIndex))
                                << Object();
        for (uint32_t j=0; j < proprityLevels; ++j) {
            double total = post.getTotalUsage();
            jsonreport << priorityText[j] << (total < 0.00001 ? 0 : total);
        }
        jsonreport << End();
    }
    jsonreport << End();
}
} /* namespace storage */
