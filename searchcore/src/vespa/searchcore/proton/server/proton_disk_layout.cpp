// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_disk_layout.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/fastos/file.h>
#include <cassert>

namespace proton {

ProtonDiskLayout::ProtonDiskLayout(const vespalib::string &baseDir, const vespalib::string &tlsSpec)
    : _baseDir(baseDir),
      _tlsSpec(tlsSpec)
{
    vespalib::mkdir(_baseDir + "/documents", true);
}

ProtonDiskLayout::~ProtonDiskLayout() = default;

void
ProtonDiskLayout::remove(const DocTypeName &docTypeName)
{
    (void) docTypeName;
}

void
ProtonDiskLayout::init(const std::set<DocTypeName> &docTypeNames)
{
    (void) docTypeNames;
}

} // namespace proton

