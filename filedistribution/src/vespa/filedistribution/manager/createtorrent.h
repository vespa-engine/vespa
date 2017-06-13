// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <libtorrent/create_torrent.hpp>

#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>

namespace filedistribution {

class CreateTorrent {
    Path _path;
    libtorrent::entry _entry;
public:

    CreateTorrent(const Path& path);
    Buffer bencode() const;
    const std::string fileReference() const;
};

} //namespace filedistribution

