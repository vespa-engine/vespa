// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "versionreporter.h"
#include <vespa/vespalib/component/vtag.h>

namespace storage {

namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
}
void VersionReporter::report(vespalib::JsonStream& jsonreport) {
    jsonreport << "vtag" << Object()
               << "version" << vespalib::Vtag::currentVersion.toString()
               << End();
}

} /* namespace storage */
