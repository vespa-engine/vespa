// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_proton_disk_layout.h"
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Class with utility functions for handling the disk directory layout
 * for proton instance.
 */
class ProtonDiskLayout : public IProtonDiskLayout
{
private:
    const vespalib::string _baseDir;
    const vespalib::string _tlsSpec;

public:
    ProtonDiskLayout(const vespalib::string &baseDir, const vespalib::string &tlsSpec);
    ~ProtonDiskLayout() override;
    void remove(const DocTypeName &docTypeName) override;
    void initAndPruneUnused(const std::set<DocTypeName> &docTypeNames) override;
};

} // namespace proton
