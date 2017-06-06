// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mountpointlist.h"
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/persistence/spi/exceptions.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <fstream>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.mountpointlist");

namespace storage::memfile {

using vespalib::getLastErrorString;
using vespalib::DirPointer;

MountPointList::MountPointList(const std::string& vdsRoot,
                               const std::vector<vespalib::string>& diskPath,
                               DeviceManager::UP manager)
  : framework::XmlStatusReporter("mountpointlist", "Disk directories"),
    _deviceManager(std::move(manager)),
    _vdsRoot(vdsRoot),
    _diskPath(diskPath),
    _mountPoints(0)
{
}

MountPointList::~MountPointList() {}

spi::PartitionStateList
MountPointList::getPartitionStates() const
{
    spi::PartitionStateList list(_mountPoints.size());
    for (uint32_t i=0; i<_mountPoints.size(); ++i) {
        if (!(_mountPoints[i]->isOk())) {
            const IOEvent* event = _mountPoints[i]->getLastEvent();

            list[i] = spi::PartitionState(spi::PartitionState::DOWN,
                                          event->getDescription());
        }
    }

    return list;
}

void
MountPointList::init(uint16_t diskCount)
{
    initDisks();
    scanForDisks();
    readFromFile();
    if (verifyHealthyDisks(diskCount == 0 ? -1 : diskCount)) {
        // Initialize monitors after having initialized disks, such as to
        // not create them for invalid disks.
        initializePartitionMonitors();
    }
    if (diskCount != 0 && _mountPoints.size() != diskCount) {
        std::ostringstream ost;
        ost << _mountPoints.size()
            << " mount points found. Expected " << diskCount
            << " mount points to exist.";
        LOG(error, "%s", ost.str().c_str());
        throw config::InvalidConfigException(ost.str(), VESPA_STRLOC);
    }
}

void
MountPointList::initDisks()
{
    if (_diskPath.empty()) return;

    using vespalib::make_string;

    vespalib::string vdsDisksPath = make_string("%s/disks", _vdsRoot.c_str());
    vespalib::mkdir(vdsDisksPath);

    for (size_t diskIndex = 0; diskIndex < _diskPath.size(); ++diskIndex) {
        auto disk_path = make_string(
                "%s/d%zu", vdsDisksPath.c_str(), diskIndex);
        if (pathExists(disk_path)) continue;

        vespalib::mkdir(_diskPath[diskIndex]);

        try {
            vespalib::symlink(_diskPath[diskIndex], disk_path);
        } catch (vespalib::IoException& dummy) {
            // The above mkdir() created disk_path as a directory, or a
            // subdirectory of disk_path, which is OK.
            (void) dummy;
        }
    }
}

void
MountPointList::initializePartitionMonitors()
{
    std::set<Partition*> seen;
    for (uint32_t i=0; i<_mountPoints.size(); ++i) {
        if (!(_mountPoints[i]->isOk())) continue;
        Partition* part = &_mountPoints[i]->getPartition();
        std::set<Partition*>::const_iterator it(seen.find(part));
        if (it == seen.end()) {
            part->initializeMonitor();
            seen.insert(part);
        }
    }
}

void
MountPointList::scanForDisks()
{
    _mountPoints.clear();
    std::vector<Directory::SP> entries;
    DirPointer dir(opendir((_vdsRoot + "/disks").c_str()));
    struct dirent* entry;
    if (dir) while ((entry = readdir(dir))) {
        if (entry == 0) {
            std::ostringstream ost;
            ost << "Failed to read directory \"" << _vdsRoot << "/disks\", "
                << "errno " << errno << ": " << getLastErrorString();
            throw vespalib::IoException(ost.str(),
                            vespalib::IoException::DISK_PROBLEM, VESPA_STRLOC);
        }
        std::string name(reinterpret_cast<char*>(&entry->d_name));
        assert(name.size() > 0);
        if (name[0] == '.') continue;
            // To be a valid d<digit> name, size must be at least 2
        if (name.size() < 2 || name[0] != 'd') {
            LOG(warning, "File %s in disks directory is faulty named for a "
                         "disk directory, ignoring it.", name.c_str());
            continue;
        }
        char* endp;
        uint32_t diskNr = strtoul(name.c_str()+1, &endp, 10);
            // If rest of name is not a number, ignore
        if (*endp != '\0') {
            LOG(warning, "File %s in disks directory is faulty named for a "
                         "disk directory, ignoring it.", name.c_str());
            continue;
        }
            // If number is out of range, ignore..
        if (diskNr >= 254) {
            LOG(warning, "Ignoring disk directory %s, as max directories have "
                         "been set to 254.", name.c_str());
            continue;
        }

        // Valid disk directory.. Add entry..
        if (entries.size() <= diskNr) {
            entries.resize(diskNr + 1);
        }
        LOG(debug, "Found disk directory %u: %s", diskNr, name.c_str());
        entries[diskNr] = _deviceManager->getDirectory(
                _vdsRoot + "/disks/" + name, diskNr);

        // We only care about directories (or symlinks). DT_UNKNOWN must be handled explicitly.
        if (entry->d_type != DT_DIR && entry->d_type != DT_LNK && entry->d_type != DT_UNKNOWN) {
            std::ostringstream ost;
            ost << "File " << name << " in disks directory is not a directory.";
            LOG(warning, "%s", ost.str().c_str());
            entries[diskNr]->addEvent(Device::PATH_FAILURE,
                                      ost.str(), VESPA_STRLOC);
        }

        // Not all filesystems support d_type. Have to stat if this equals DT_UNKNOWN.
        if (entry->d_type == DT_UNKNOWN) {
            struct stat st;
            lstat(entries[diskNr]->getPath().c_str(), &st);
            if (!S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode)) {
                std::ostringstream ost;
                ost << "File " << name << " in disks directory is not a directory.";
                LOG(warning, "%s", ost.str().c_str());
                entries[diskNr]->addEvent(Device::PATH_FAILURE,
                                          ost.str(), VESPA_STRLOC);
            }
        }
    } else if (errno == ENOENT) {
        std::ostringstream ost;
        ost << "Disk directory \"" << _vdsRoot << "/disks\" not created. VDS "
            << "needs this to know which disks to use. See vespa doc.";
        throw NoDisksException(ost.str(), VESPA_STRLOC);
    } else {
        std::ostringstream ost;
        ost << "Failed to open directory \"" << _vdsRoot << "/disks\", errno "
            << errno << ": " << getLastErrorString();
        throw vespalib::IoException(ost.str(),
                        vespalib::IoException::DISK_PROBLEM, VESPA_STRLOC);
    }
        // Assign found disks to the instance
    _mountPoints.resize(entries.size());
    for (uint32_t i=0; i<_mountPoints.size(); ++i) {
        if (!entries[i].get()) {
            if (!_mountPoints[i].get() ||
                 _mountPoints[i]->getState() == Device::OK)
            {
                std::ostringstream ost;
                ost << _vdsRoot + "/disks/d" << i;
                _mountPoints[i] = _deviceManager->getDirectory(ost.str(), i);
                _mountPoints[i]->addEvent(
                        Device::NOT_FOUND,
                        "Disk not found during scanning of disks directory",
                        VESPA_STRLOC);
            }
            LOG(warning, "Disk %u was not found.", i);
        } else if (!_mountPoints[i].get() ||
                   _mountPoints[i]->getState() == Device::NOT_FOUND)
        {
            _mountPoints[i] = entries[i];
        }
    }
}

namespace {
    /**
     * Get the disk nr of the given mountpoint,
     * or -1 if the mountpoint is illegal.
     */
    int getDiskNr(const std::string& mountPoint) {
        std::string::size_type pos1 = mountPoint.rfind('/');
        if (pos1 == std::string::npos ||
            pos1 + 2 >= mountPoint.size() ||
            mountPoint[pos1+1] != 'd')
        {
            return -1;
        }
        char* endp;
        std::string digit(mountPoint.substr(pos1+2));
        const char* digitptr = digit.c_str();
        int diskNr = strtoul(digitptr, &endp, 10);
        if (digitptr[0] == '\0' || *endp != '\0') return -1;
        return diskNr;
    }
}

void
MountPointList::readFromFile()
{
    std::vector<Directory::SP> entries;
        // Read entries from disk
    std::ifstream is;
        // Throw exception if failing to read file
    is.exceptions(std::ifstream::badbit);
    is.open(getDiskStatusFileName().c_str());
    std::string line("EOF");
    while (std::getline(is, line)) {
        if (line == "EOF") { break; }
        Directory::SP dir = _deviceManager->deserializeDirectory(line);
        int diskNr = getDiskNr(dir->getPath());
        if (diskNr == -1) {
            LOG(warning, "Found illegal disk entry '%s' in vds disk file %s.",
                         line.c_str(), getDiskStatusFileName().c_str());
        } else {
            dir->setIndex(diskNr);
            if (entries.size() <= static_cast<uint32_t>(diskNr)) {
                entries.resize(diskNr + 1);
            }
            entries[diskNr] = dir;
        }
    }
    if (line != "EOF" || std::getline(is, line)) {
        LOG(warning, "Disk status file %s did not end in EOF.",
                     getDiskStatusFileName().c_str());
    }
        // Assign entries to this instance
    if (_mountPoints.size() < entries.size()) {
        _mountPoints.resize(entries.size());
    }
    for (uint32_t i=0; i<entries.size(); ++i) {
        if (entries[i].get() &&
            entries[i]->getState() != Device::OK &&
            entries[i]->getState() != Device::NOT_FOUND)
        {
            _mountPoints[i] = entries[i];
        }
    }
}

void
MountPointList::writeToFile() const
{
    try{
        std::string filename(getDiskStatusFileName());
        std::string tmpFilename(filename + ".tmp");
        std::ofstream os(tmpFilename.c_str());
        if (os.fail()) {
            LOG(warning, "Failed to open %s.tmp for writing. Not writing "
                         "disks.status file.", filename.c_str());
            return;
        }
        for (std::vector<Directory::SP>::const_iterator it
                = _mountPoints.begin(); it != _mountPoints.end(); ++it)
        {
            if (it->get() &&
                (*it)->getState() != Device::OK)
            {
                os << **it << "\n";
            }
        }
        os << "EOF";
        os.close();
        if (os.fail()) {
            LOG(warning, "Failed to write %s.tmp. disks.status file might now "
                         "be corrupt as we failed while writing it.",
                filename.c_str());
            return;
        }
        vespalib::rename(tmpFilename, filename, false, false);
        LOG(debug, "Mount point list saved to file %s.", filename.c_str());
    } catch (std::exception& e) {
        LOG(warning, "Failed to write disk status file: %s", e.what());
    }
}

namespace {
    void testMountPoint(Directory& mountPoint) {
        struct stat filestats;
        if (stat(mountPoint.getPath().c_str(), &filestats) != 0) {
            switch (errno) {
                case ENOTDIR:
                case ENAMETOOLONG:
                case ENOENT:
                case EACCES:
                case ELOOP:
                {
                    mountPoint.addEvent(Device::PATH_FAILURE,
                                        getLastErrorString(),
                                        VESPA_STRLOC);
                    return;
                }
                case EIO:
                {
                    mountPoint.addEvent(Device::IO_FAILURE,
                                        getLastErrorString(), VESPA_STRLOC);
                    return;
                }
                case EFAULT:
                default:
                    assert(0); // Should never happen
            }
        }
            // At this point we know the mount point exists..
        if (!(S_ISDIR(filestats.st_mode))) {
            mountPoint.addEvent(
                    Device::PATH_FAILURE,
                    "The path exist, but is not a directory.",
                    VESPA_STRLOC);
        }
    }

    struct Chunk {
        uint32_t nr;
        uint32_t total;

        Chunk() : nr(0), total(0) {} // Invalid
        bool valid() const { return (nr < total); }
    };

    Chunk getChunkDef(const std::string& mountPoint) {
        vespalib::File file(mountPoint + "/chunkinfo");
        file.open(vespalib::File::READONLY);
        std::string buffer;
        buffer.resize(200, '\0');
        size_t read(file.read(&buffer[0], buffer.size(), 0));
        buffer.resize(read);
        vespalib::StringTokenizer tokenizer(buffer, "\n", "");

        Chunk chunk;
        if (tokenizer.size() < 3) {
            return chunk;
        }

        char *c;
        chunk.nr = strtoul(tokenizer[1].c_str(), &c, 10);
        if (tokenizer[1].c_str() + tokenizer[1].size() != c) return Chunk();
        chunk.total = strtoul(tokenizer[2].c_str(), &c, 10);
        if (tokenizer[2].c_str() + tokenizer[2].size() != c) return Chunk();
        return chunk;
    }

    void writeChunkDef(Chunk c, const std::string& mountPoint) {
        vespalib::File file(mountPoint + "/chunkinfo");
        file.open(vespalib::File::CREATE | vespalib::File::TRUNC, true);
        std::ostringstream ost;
        ost << "# This file tells VDS what data this mountpoint may contain.\n"
            << c.nr << "\n"
            << c.total << "\n";
        std::string content(ost.str());
        file.write(&content[0], content.size(), 0);
    }

    Device::State getDeviceState(vespalib::IoException::Type type) {
        using vespalib::IoException;
        switch (type) {
            case IoException::ILLEGAL_PATH: return Device::PATH_FAILURE;
            case IoException::NO_PERMISSION: return Device::NO_PERMISSION;
            case IoException::DISK_PROBLEM: return Device::IO_FAILURE;
            case IoException::INTERNAL_FAILURE: return Device::INTERNAL_FAILURE;
            default: ;
        }
        return Device::OK;
    }

    bool emptyDir(Directory& dir) {
        const std::string& path(dir.getPath());
        errno = 0;
        DirPointer dirdesc(opendir(path.c_str()));
        struct dirent* entry;
        if (dirdesc) while ((entry = readdir(dirdesc))) {
            if (errno) break;
            std::string name(reinterpret_cast<char*>(&entry->d_name));
            if (name == "." || name == "..") continue;
            return false;
        }
        if (dirdesc == 0 || errno) {
            std::ostringstream ost;
            ost << "Failed to read directory \"" << path << "\", "
                << "errno " << errno << ": " << getLastErrorString();
            dir.addEvent(getDeviceState(vespalib::IoException::getErrorType(errno)),
                         ost.str(),
                         VESPA_STRLOC);
            throw vespalib::IoException(ost.str(),
                            vespalib::IoException::DISK_PROBLEM, VESPA_STRLOC);
        }
        return true;
    }

    struct WriteStatusFileIfFailing {
        MountPointList& _list;
        bool _failed;

        WriteStatusFileIfFailing(MountPointList& list)
            : _list(list), _failed(false) {}
        ~WriteStatusFileIfFailing() {
            if (_failed) _list.writeToFile();
        }

        void reportFailure() { _failed = true; }
    };
}

bool
MountPointList::verifyHealthyDisks(int mountPointCount)
{
    WriteStatusFileIfFailing statusWriter(*this);
    int usable = 0, empty = 0;
    std::map<uint32_t, Directory::SP> lackingChunkDef;
        // Test disks and get chunkinfo
    for (uint32_t i=0, n=_mountPoints.size(); i<n; ++i) {
        Directory::SP dir(_mountPoints[i]);
            // Insert NOT_FOUND disk if not found, such that operator[]
            // can return only valid pointers
        if (!dir.get()) {
            std::ostringstream ost;
            ost << _vdsRoot + "/disks/d" << i;
            dir = _deviceManager->getDirectory(ost.str(), i);
            dir->addEvent(Device::NOT_FOUND,
                          "Disk not found during scanning of disks directory",
                          VESPA_STRLOC);
            _mountPoints[i] = dir;
            statusWriter.reportFailure();
        }
        if (dir->isOk()) {
            testMountPoint(*dir);
            if (!dir->isOk()) statusWriter.reportFailure();
        }
            // Don't touch unhealthy or non-existing disks.
        if (!dir->isOk()) {
            std::ostringstream ost;
            ost << "Not using disk " << i << " marked bad: ";
            dir->getLastEvent()->print(ost, true, "  ");
            LOG(warning, "%s", ost.str().c_str());
            continue;
        }

            // Read chunkinfo
        using vespalib::IoException;
        Chunk chunk;
        try{
            chunk = getChunkDef(dir->getPath());
        } catch (IoException& e) {
            chunk = Chunk();
            if (e.getType() == IoException::NOT_FOUND) {
                if (!emptyDir(*dir)) {
                    dir->addEvent(Device::INTERNAL_FAILURE,
                                    "Foreign data in mountpoint. New "
                                    "mountpoints added should be empty.", "");
                }
            } else {
                LOG(warning, "Failed to read chunkinfo file from mountpoint %s",
                             dir->getPath().c_str());
                Device::State newState(getDeviceState(e.getType()));
                if (newState != Device::OK) {
                    dir->addEvent(newState, e.what(), VESPA_STRLOC);
                }
            }
        } catch (std::exception& e) {
            LOG(warning, "Failed to read chunkinfo file from mountpoint %s",
                dir->getPath().c_str());
            dir->addEvent(Device::INTERNAL_FAILURE, e.what(), VESPA_STRLOC);
        }

            // If disk was found unusable, don't use it.
        if (!dir->isOk()) {
            LOG(warning, "Unusable disk %d: %s",
                i, dir->getLastEvent()->toString(true).c_str());
            statusWriter.reportFailure();
            continue;
        }
        ++usable;
            // Ensure disk fits in with the already detected ones.
        if (!chunk.valid()) {
            ++empty;
            lackingChunkDef[i] = dir;
        } else if (chunk.nr != i) {
            std::ostringstream ost;
            ost << "Disk " << dir->getPath() << " thinks it's disk " << chunk.nr
                << " (instead of " << i << ").";
            LOG(error, "%s", ost.str().c_str());
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        } else if (mountPointCount == -1) {
            mountPointCount = chunk.total;
        } else if (static_cast<uint32_t>(mountPointCount) != chunk.total) {
            std::ostringstream ost;
            ost << "Disk " << dir->getPath() << " thinks it's disk " << chunk.nr
                << " of " << chunk.total << " (instead of " << i << " of "
                << mountPointCount << ").";
            LOG(error, "%s", ost.str().c_str());
            throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
        }
    }
    if (empty == usable && usable != mountPointCount && mountPointCount != -1) {
        std::ostringstream ost;
        ost << "Found " << usable << " disks and config says we're "
            << "supposed to have " << mountPointCount << ". Not initializing "
            << "disks.";
        throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
    }
    bool retval = true;
        // Handle case where no chunkinfo file present (none/unusable/new disks)
    if (mountPointCount == -1) {
        if (_mountPoints.size() == 0) {
            LOG(error, "No disks configured for storage node. Disk "
                         "directories/symlinks for this node should be created "
                         "in %s/disks/. Please refer to VDS documentation to "
                         "learn how to add disks", _vdsRoot.c_str());
            throw spi::HandledException("No disks configured", VESPA_STRLOC);
        } else if (usable == 0) {
            LOG(error, "All of the configured disks are unusable. "
                       "Please refer to previous warnings and the VDS "
                       "documentation for troubleshooting");
            throw spi::HandledException("All disks unusable", VESPA_STRLOC);
        } else {
            mountPointCount = _mountPoints.size();
            LOG(info, "All disks empty. Setting up node to run with the %u "
                      "found disks.", mountPointCount);
            retval = false;
        }
    }
        // Write chunkdef files where these are missing
    for (std::map<uint32_t, Directory::SP>::const_iterator it
            = lackingChunkDef.begin(); it != lackingChunkDef.end(); ++it)
    {
        const Directory::SP &dir = it->second;
        Chunk c;
        c.nr = it->first;
        c.total = mountPointCount;
        if (c.nr >= c.total) {
            LOG(warning, "Can't use disk %u of %u as the index is too high. "
                         "(Disks are indexed from zero)", c.nr, c.total);
            continue;
        }
        if (!emptyDir(*dir)) {
            LOG(warning, "Not creating chunkinfo file on disk %u as it already "
                         "contains data. If you want to include the disk, "
                         "create chunkinfo file manually.", c.nr);
            assert(!dir->isOk());
            continue;
        }
        using vespalib::IoException;
        try{
            writeChunkDef(c, dir->getPath());
            retval = true;
        } catch (IoException& e) {
            statusWriter.reportFailure();
            LOG(warning, "Failed to write chunkinfo file to mountpoint %s.",
                         dir->getPath().c_str());
            Device::State newState(getDeviceState(e.getType()));
            if (newState != Device::OK) {
                dir->addEvent(newState, e.what(), VESPA_STRLOC);
            }
        } catch (std::exception& e) {
            statusWriter.reportFailure();
            LOG(warning, "Failed to write chunkinfo file to mountpoint %s",
                dir->getPath().c_str());
            dir->addEvent(Device::INTERNAL_FAILURE, e.what(), VESPA_STRLOC);
        }
    }
        // If we need more entries in mountpointlist, due to chunkinfo
        // showing more indexes, add them.
    for (int i = _mountPoints.size(); i < mountPointCount; ++i) {
        std::ostringstream ost;
        ost << _vdsRoot + "/disks/d" << i;
        Directory::SP dir(_deviceManager->getDirectory(ost.str(), i));
        dir->addEvent(Device::NOT_FOUND,
                      "Disk not found during scanning of disks directory",
                      VESPA_STRLOC);
        _mountPoints.push_back(dir);
    }
    if (static_cast<int>(_mountPoints.size()) > mountPointCount) {
        _mountPoints.resize(mountPointCount);
    }
    return retval;
}

uint16_t
MountPointList::findIndex(const Directory& dir) const
{
    for (uint16_t i = 0; i < _mountPoints.size(); ++i) {
        if (_mountPoints[i].get() != 0 && dir == *_mountPoints[i]) return i;
    }
    throw vespalib::IllegalArgumentException(
            "Could not find directory " + dir.toString(), VESPA_STRLOC);
}

std::string
MountPointList::getDiskStatusFileName() const
{
    return _vdsRoot + "/disks.status";
}

vespalib::string
MountPointList::reportXmlStatus(vespalib::xml::XmlOutputStream& xos,
                                const framework::HttpUrlPath&) const
{
    xos << *_deviceManager;
    return "";
}

}
