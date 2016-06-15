// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "util.h"
#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/jsonstream.h>

namespace storage {
namespace util {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
using JsonFormat = vespalib::slime::JsonFormat;
using Memory = vespalib::slime::Memory;
}

void
reporterToSlime(HostReporter &hostReporter, vespalib::Slime &slime) {
    vespalib::asciistream json;
    vespalib::JsonStream stream(json, true);

    stream << Object();
    hostReporter.report(stream);
    stream << End();
    std::string jsonData = json.str();
    size_t parsedSize = JsonFormat::decode(Memory(jsonData), slime);

    if (jsonData.size() != parsedSize) {
        CPPUNIT_FAIL("Sizes of jsonData mismatched, probably not json:\n" + jsonData);
    }
}
}
}
