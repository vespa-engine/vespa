// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/message/bucket.h>

namespace storage {

/*
 * Class for remapping bit masks from a partial set of nodes to a full
 * set of nodes.
 */
class HasMaskRemapper
{
    std::vector<uint16_t> _mask_remap;
    uint16_t _all_remapped;

public:
    HasMaskRemapper(const std::vector<api::MergeBucketCommand::Node> &all_nodes,
                 const std::vector<api::MergeBucketCommand::Node> &nodes);
    ~HasMaskRemapper();

    uint16_t operator()(uint16_t mask) const;
    uint16_t operator()(uint16_t mask, uint16_t keep_from_full_mask) const;
};

}
