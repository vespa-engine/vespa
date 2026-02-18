// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "channellookup.h"

#include "controlpacket.h"
#include "vespa/fnet/channel.h"

#include <vespa/vespalib/stllike/hash_map.hpp>

#include <cassert>

namespace fnet {

struct ChannelMap : public vespalib::hash_map<uint32_t, FNET_Channel*> {
    using Parent = vespalib::hash_map<uint32_t, FNET_Channel*>;
    ChannelMap(size_t sz) : Parent(sz) {}
};

} // namespace fnet
using fnet::ChannelMap;

FNET_ChannelLookup::FNET_ChannelLookup(uint32_t hashSize) : _map(std::make_unique<ChannelMap>(hashSize)) {
    assert(hashSize > 0);
}

FNET_ChannelLookup::~FNET_ChannelLookup() { assert(_map->empty()); }

void FNET_ChannelLookup::Register(FNET_Channel* channel) {
    assert(channel->GetHandler() != nullptr);
    (*_map)[channel->GetID()] = channel;
}

FNET_Channel* FNET_ChannelLookup::Lookup(uint32_t id) {
    auto found = _map->find(id);
    return ((found != _map->end()) ? found->second : nullptr);
}

std::vector<FNET_Channel::UP> FNET_ChannelLookup::Broadcast(FNET_ControlPacket* cpacket) {
    std::vector<uint32_t>         toRemove;
    std::vector<FNET_Channel::UP> toFree;
    for (const auto& pair : *_map) {
        FNET_Channel*                   ch = pair.second;
        FNET_IPacketHandler::HP_RetCode hp_rc = ch->Receive(cpacket);
        if (hp_rc > FNET_IPacketHandler::FNET_KEEP_CHANNEL) {
            toRemove.push_back(pair.first);
            if (hp_rc == FNET_IPacketHandler::FNET_FREE_CHANNEL) {
                toFree.emplace_back(ch);
            }
        }
    }
    for (uint32_t id : toRemove) {
        _map->erase(id);
    }
    return toFree;
}

bool FNET_ChannelLookup::Unregister(FNET_Channel* channel) {
    auto found = _map->find(channel->GetID());
    if (found == _map->end()) {
        return false;
    }
    _map->erase(found);
    return true;
}
