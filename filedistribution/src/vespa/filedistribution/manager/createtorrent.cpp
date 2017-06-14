// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "createtorrent.h"

#include <libtorrent/torrent_info.hpp>
#include <boost/filesystem/convenience.hpp>

#include <iostream>
#include <fstream>
#include <cmath>
#include <iterator>
#include <sstream>
#include <string>

namespace fs = boost::filesystem;

namespace {

const libtorrent::size_type targetTorrentSize = 64 * 1024;

void
aggregateFilenames(std::vector<std::string>& accumulator, const fs::path& path, std::string currPrefix)
{
  if (fs::is_directory(path)) {
    for (fs::directory_iterator i(path), end;
      i != end;
     ++i) {
      std::string newPrefix = currPrefix + "/" + std::string(i->path().filename().c_str());
      accumulator.push_back(newPrefix);
      aggregateFilenames(accumulator, *i, newPrefix);
    }
  }
}


libtorrent::entry
createEntry(const fs::path& path) {
    if (!fs::exists(path))
        throw std::runtime_error("Path '" + std::string(path.filename().c_str()) + " does not exists");

    libtorrent::file_storage fileStorage;
    libtorrent::add_files(fileStorage, path.string());

    libtorrent::create_torrent torrent(fileStorage);
    torrent.set_creator("vespa-filedistributor");
    torrent.set_priv(true);
    torrent.add_tracker("");

    libtorrent::set_piece_hashes(torrent, path.branch_path().string());
    return torrent.generate();
}

std::string
fileReferenceToString(const libtorrent::sha1_hash& fileReference) {
    std::ostringstream fileReferenceString;
    fileReferenceString <<fileReference;
    return fileReferenceString.str();
}

} //anonymous namespace

filedistribution::
CreateTorrent::
CreateTorrent(const Path& path)
    :_path(path),
     _entry(createEntry(_path))
{}

filedistribution::Buffer
filedistribution::
CreateTorrent::
bencode() const
{
    Buffer buffer(static_cast<int>(targetTorrentSize));
    libtorrent::bencode(std::back_inserter(buffer), _entry);
    return buffer;
}

const std::string
filedistribution::
CreateTorrent::
fileReference() const
{
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
    libtorrent::torrent_info info(_entry);
#pragma GCC diagnostic pop
    return fileReferenceToString(info.info_hash());
}
