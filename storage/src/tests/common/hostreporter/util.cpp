// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "util.h"
#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/jsonstream.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::util {

namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
using JsonFormat = vespalib::slime::JsonFormat;
using Memory = vespalib::Memory;
}

void
reporterToSlime(HostReporter &hostReporter, vespalib::Slime &slime) {
    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);

    stream << Object();
    hostReporter.report(stream);
    stream << End();
    std::string jsonData = json.str();
    size_t parsed = JsonFormat::decode(Memory(jsonData), slime);

    if (parsed == 0) {
        throw std::runtime_error("jsonData is not json:\n" + jsonData);
    }
}

}
