// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <boost/filesystem/path.hpp>
#include <libtorrent/create_torrent.hpp>

#include <vespa/filedistribution/common/buffer.h>

namespace filedistribution {

class CreateTorrent {
    boost::filesystem::path _path;
    libtorrent::entry _entry;
public:

    CreateTorrent(const boost::filesystem::path& path);
    const Move<Buffer> bencode() const;
    const std::string fileReference() const;
};

} //namespace filedistribution

