// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_proton_disk_layout.h"
#include <string>

class FNET_Transport;

namespace proton {

/**
 * Class with utility functions for handling the disk directory layout
 * for proton instance.
 */
class ProtonDiskLayout : public IProtonDiskLayout
{
private:
    FNET_Transport         & _transport;
    const std::string   _baseDir;
    const std::string   _tlsSpec;

public:
    ProtonDiskLayout(FNET_Transport & transport, const std::string &baseDir, const std::string &tlsSpec);
    ~ProtonDiskLayout() override;
    void remove(const DocTypeName &docTypeName) override;
    void initAndPruneUnused(const std::set<DocTypeName> &docTypeNames) override;
};

} // namespace proton
