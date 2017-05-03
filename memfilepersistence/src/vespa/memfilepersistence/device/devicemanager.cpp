// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "devicemanager.h"
#include <vespa/vespalib/util/exceptions.h>

namespace storage::memfile {

DeviceManager::DeviceManager(DeviceMapper::UP mapper,
                             const framework::Clock& clock)
    : _deviceMapper(std::move(mapper)),
      _disks(),
      _partitions(),
      _directories(),
      _eventListeners(),
      _statPolicy(vespa::config::storage::StorDevicesConfig::STAT_DYNAMIC),
      _statPeriod(0),
      _clock(clock)
{
}

DeviceManager::~DeviceManager() {}

void
DeviceManager::setPartitionMonitorPolicy(
        vespa::config::storage::StorDevicesConfig::StatfsPolicy policy, uint32_t period)
{
    _statPolicy = policy;
    _statPeriod = period;
    for (std::map<std::string, Partition::SP>::iterator it
            = _partitions.begin(); it != _partitions.end(); ++it)
    {
        Partition& p(*it->second);
        if (p.getMonitor() != 0) p.getMonitor()->setPolicy(policy, period);
    }
}

void DeviceManager::notifyDiskEvent(Disk& d, const IOEvent& e)
{
    for (std::set<IOEventListener*>::iterator it = _eventListeners.begin();
         it != _eventListeners.end(); ++it)
    {
        assert(*it != 0);
        (*it)->handleDiskEvent(d, e);
    }
}

void
DeviceManager::notifyDirectoryEvent(Directory& dir, const IOEvent& e)
{
    for (std::set<IOEventListener*>::iterator it = _eventListeners.begin();
         it != _eventListeners.end(); ++it)
    {
        assert(*it != 0);
        (*it)->handleDirectoryEvent(dir, e);
    }
}

void
DeviceManager::notifyPartitionEvent(Partition& part, const IOEvent& e)
{
    for (std::set<IOEventListener*>::iterator it = _eventListeners.begin();
         it != _eventListeners.end(); ++it)
    {
        assert(*it != 0);
        (*it)->handlePartitionEvent(part, e);
    }
}

void
DeviceManager::addIOEventListener(IOEventListener& listener)
{
    _eventListeners.insert(&listener);
}

void
DeviceManager::removeIOEventListener(IOEventListener& listener)
{
    _eventListeners.erase(&listener);
}

Directory::SP
DeviceManager::getDirectory(const std::string& dir, uint16_t index)
{
    std::map<std::string, Directory::SP>::iterator it =
        _directories.find(dir);
    if (it != _directories.end()) {
        return it->second;
    }
    Directory::SP d(new Directory(*this, index, dir));
    _directories[dir] = d;
    return d;
}

Directory::SP
DeviceManager::deserializeDirectory(const std::string& serialized)
{
        // Deserialize object
    Directory::SP d(new Directory(serialized, *this));
        // If not existing, just add it.
    std::map<std::string, Directory::SP>::iterator it =
        _directories.find(d->getPath());
    if (it == _directories.end()) {
        _directories[d->getPath()] = d;
        return d;
    }
        // If already existing, merge info with existing entry.
    it->second->addEvents(*d);
    return it->second;
}

Partition::SP
DeviceManager::getPartition(const std::string& path)
{
    try{
        std::string mountPoint(_deviceMapper->getMountPoint(path));
        uint64_t id = _deviceMapper->getPartitionId(mountPoint);
        std::map<std::string, Partition::SP>::iterator it(
            _partitions.find(mountPoint));
        if (it != _partitions.end()) {
            return it->second;
        }
        Partition::SP part(new Partition(*this, id, mountPoint));
        if (part->getMonitor() != 0) {
            part->getMonitor()->setPolicy(_statPolicy, _statPeriod);
        }
        _partitions[mountPoint] = part;
        return part;
    } catch (vespalib::IoException& e) {
        // If we fail to create partition, due to having IO troubles getting
        // partition id or mount point, create a partition that doesn't
        // correspond to a physical device containing the error found.
        Partition::SP part(new Partition(*this, -1, path));
        part->addEvent(IOEvent::createEventFromIoException(
                               e,
                               _clock.getTimeInSeconds().getTime()));
        _partitions[path] = part;
        return part;
    }
}

Disk::SP
DeviceManager::getDisk(const std::string& path)
{
    try{
        int devnr = _deviceMapper->getDeviceId(path);
        std::map<int, Disk::SP>::iterator it = _disks.find(devnr);
        if (it != _disks.end()) {
            return it->second;
        }
        Disk::SP disk(new Disk(*this, devnr));
        _disks[devnr] = disk;
        return disk;
    } catch (vespalib::IoException& e) {
            // Use negative ints for illegal ids. Make sure they don't already
            // exist
        int devnr = -1;
        while (_disks.find(devnr) != _disks.end()) --devnr;
        // If we fail to create partition, due to having IO troubles getting
        // partition id or mount point, create a partition that doesn't
        // correspond to a physical device containing the error found.
        Disk::SP disk(new Disk(*this, devnr));
        disk->addEvent(IOEvent::createEventFromIoException(
                               e,
                               _clock.getTimeInSeconds().getTime()));
        _disks[devnr] = disk;
        return disk;
    }
}

void
DeviceManager::printXml(vespalib::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlTag("devicemanager");
    xos << XmlTag("mapper") << XmlAttribute("type", _deviceMapper->getName())
        << XmlEndTag();
    xos << XmlTag("devices");
    for (std::map<int, Disk::SP>::const_iterator diskIt = _disks.begin();
         diskIt != _disks.end(); ++diskIt)
    {
        xos << XmlTag("disk") << XmlAttribute("deviceId", diskIt->first);
        for (std::map<std::string, Partition::SP>::const_iterator partIt
                = _partitions.begin(); partIt != _partitions.end(); ++partIt)
        {
            if (partIt->second->getDisk() != *diskIt->second) continue;
            xos << XmlTag("partition")
                << XmlAttribute("id", partIt->second->getId())
                << XmlAttribute("mountpoint", partIt->second->getMountPoint());
            if (partIt->second->getMonitor() != 0) {
                xos << *partIt->second->getMonitor();
            }
            for (std::map<std::string, Directory::SP>::const_iterator dirIt
                    = _directories.begin(); dirIt != _directories.end();
                 ++dirIt)
            {
                if (dirIt->second->getPartition() != *partIt->second) continue;
                xos << XmlTag("directory")
                    << XmlAttribute("index", dirIt->second->getIndex())
                    << XmlAttribute("path", dirIt->second->getPath())
                    << XmlEndTag();
            }
            xos << XmlEndTag();
        }
        xos << XmlEndTag();
    }
    xos << XmlEndTag() << XmlEndTag();
}

} // memfile
