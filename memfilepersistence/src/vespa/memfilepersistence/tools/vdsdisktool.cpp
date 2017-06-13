// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/document/util/stringutil.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/memfilepersistence/device/mountpointlist.h>
#include <vespa/memfilepersistence/tools/vdsdisktool.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".vdsdiskapp");

using std::vector;

namespace storage {
namespace memfile {

using vespalib::getLastErrorString;

namespace {

    struct Sorter {
        bool operator()(const std::pair<std::string, std::string>& first,
                        const std::pair<std::string, std::string>& second)
            { return (first.first < second.first); }
    };

    /**
     * Read pid from pid file. In case we want to extend pid file to contain
     * more information later, accept multiple lines in file as long as pid is
     * in first, and allow a pid: prefix to the pid.
     */
    uint32_t readPid(const std::string& pidFile) {
        vespalib::LazyFile lf(pidFile, vespalib::File::READONLY);
        vector<char> data(32);
        size_t read = lf.read(&data[0], 32, 0);
            // If pid file has been extended to have more data, ignore it.
        for (uint32_t i=0; i<32; ++i) {
            if (data[i] == '\n') {
                data[i] = '\0';
                read = i;
                break;
            }
        }
            // Allow a "pid:" prefix if it exists.
        int start = 0;
        if (strncmp("pid:", &data[0], 4) == 0) {
            start = 4;
        }
            // Fail unless the first line was just a number with the pid
        char* endp;
        uint32_t pid = strtoull(&data[start], &endp, 10);
        if (*endp != '\0' || read >= 32) {
            throw vespalib::IllegalStateException(
                    "Unexpected content in pid file " + pidFile,
                    VESPA_STRLOC);
        }
        if (pid == 0) {
            throw vespalib::IllegalStateException(
                    "Read pid 0 from pidfile which is illegal.",
                    VESPA_STRLOC);
        }
        return pid;
    }
}

struct CmdLineOptions : public vespalib::ProgramOptions {
    std::ostream& _err;
    std::string _rootpath;
    bool _showSyntax;
    std::string _cluster;
    uint32_t _nodeIndex;
    std::string _mode;
    uint32_t _diskIndex;
    std::string _message;
    /*
    std::string _slobrokConfigId;
    std::string _slobrokConnectionSpec;
    */

    CmdLineOptions(int argc, const char * const * argv,
                   const std::string& rootpath, std::ostream& err)
        : vespalib::ProgramOptions(argc, argv),
          _err(err),
          _rootpath(rootpath)
    {
        setSyntaxMessage(
            "This tool is used to stop VDS from using a given partition "
            "you no longer want it to use, or to reenable use of a partition "
            "that previously have been disabled. Note that currently, this "
            "requires a restart of the storage node, which this tool will "
            "do automatically. Note that the tool must be run on the storage "
            "node where you want to enable/disable a partition.\n\n"
            "Examples:\n"
            "  vdsdisktool disable 2 \"Seeing a lot of smart warnings on this one\"\n"
            "  vdsdisktool -c mycluster -i 3 disable 0 \"Shouldn't have put this on OS drive\"\n"
            "  vdsdisktool enable 2\n"
        );
        addOption("h help", _showSyntax, false,
                  "Show this help page.");
        addOption("c cluster", _cluster, std::string(""),
                  "Which cluster the storage node whose disks should be "
                  "adjusted. If only data from one cluster is detected "
                  "on the node, this does not have to be specified");
        addOption("i index", _nodeIndex, uint32_t(0xffffffff),
                  "The node index of the storage node whose disks should be "
                  "adjusted. If only data from one storage node is detected "
                  "on the node, this does not have to be specified");
        addArgument("Mode", _mode,
                    "There are three modes. They are status, enable and disable"
                    ". The status mode is used to just query current disk "
                    "status without. The enable and disable modes will enable "
                    "or disable a disk.");
        addArgument("Disk Index", _diskIndex, uint32_t(0xffffffff),
                    "The disk index which you want to enable/disable. Not "
                    "specified in status mode, but required otherwise.");
        addArgument("Reason", _message, std::string(""),
                    "Give a reason for why we're enabling or disabling a disk. "
                    "Required when disabling a disk, such that other "
                    "administrators can see why it has happened.");
    }
    ~CmdLineOptions();

    vector<std::string> listDir(const std::string& dir) {
        DIR* dirp = opendir(dir.c_str());
        struct dirent* entry;
        vector<std::string> result;
        if (dirp) while ((entry = readdir(dirp))) {
            if (entry == 0) {
                std::ostringstream ost;
                ost << "Failed to read directory '" << dir << "', errno "
                    << errno << ": " << getLastErrorString() << "\n";
                int tmp = closedir(dirp);
                assert(tmp == 0);
                (void) tmp;
                throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
            }
            std::string name(reinterpret_cast<char*>(&entry->d_name));
            assert(name.size() > 0);
            if (name[0] == '.') continue;
            result.push_back(name);
        }
        int tmp = closedir(dirp);
        assert(tmp == 0);
        (void) tmp;
        return result;
    }

    std::set<std::string> detectPossibleClusters() {
        if (!vespalib::fileExists(_rootpath)) {
            throw vespalib::IllegalStateException(
                    "No VDS installations found at all in " + _rootpath,
                    VESPA_STRLOC);
        }
        vector<std::string> files(listDir(_rootpath));
        std::set<std::string> result(files.begin(), files.end());
        return result;
    }

    std::set<uint16_t>
    detectPossibleNodeIndexes(const std::string& cluster)
    {
        std::string dir = _rootpath + "/" + cluster + "/storage";
        if (!vespalib::fileExists(dir)) {
            throw vespalib::IllegalStateException(
                    "No VDS installations found at all in " + dir,
                    VESPA_STRLOC);
        }
        vector<std::string> files(listDir(dir));
        std::set<uint16_t> result;
        for (uint32_t i=0; i<files.size(); ++i) {
            char* endp;
            uint64_t index = strtoull(files[i].c_str(), &endp, 10);
            if (*endp != '\0' || index > 0xffff) {
                _err << "Found strange file in directory supposed to "
                          << "contain node indexes: '" << files[i] << "'.\n";
            } else {
                result.insert(index);
            }
        }
        return result;
    }

    bool validate() {
            // Validate that cluster was in fact found. Uses storage disk
            // directories to scan for legal targets.
        LOG(debug, "Detecting clusters");
        std::set<std::string> clusters(detectPossibleClusters());
        if (clusters.size() == 0) {
            _err << "No VDS clusters at all detected on this node.\n";
            return false;
        }
        bool clusterFound = false;
        if (_cluster != "") {
            if (clusters.find(_cluster) == clusters.end()) {
                _err << "No cluster named '" << _cluster
                          << "' found.\n";
            } else {
                clusterFound = true;
            }
        } else if (clusters.size() != 1u) {
            _err << "Cluster must be specified as there are multiple "
                         "targets.\n";
        } else {
            _cluster = *clusters.begin();
            clusterFound = true;
        }
        if (!clusterFound) {
            _err << "Detected cluster names on local node:\n";
            for (std::set<std::string>::const_iterator it = clusters.begin();
                 it != clusters.end(); ++it)
            {
                _err << "  " << *it << "\n";
            }
            return false;
        }
            // Validate that node index was in fact found. Uses storage disk
            // directories to scan for legal targets.
        LOG(debug, "Detecting node indexes");
        std::set<uint16_t> nodeIndexes(
                detectPossibleNodeIndexes(_cluster));
        if (nodeIndexes.size() == 0) {
            _err << "No node indexes at all detected on this node in "
                         "cluster '" << _cluster << ".\n";
            return false;
        }
        bool indexFound = false;
        if (_nodeIndex != uint32_t(0xffffffff)) {
            if (_nodeIndex > 0xffff) {
                _err << "Illegal node index " << _nodeIndex
                          << " specified. Nodes must be in the range of "
                          << "0-65535.\n";
                return false;
            }
            if (nodeIndexes.find(_nodeIndex) == nodeIndexes.end()) {
                _err << "No node with index " << _nodeIndex
                          << " found in cluster '" << _cluster
                          << "'.\n";
            } else {
                indexFound = true;
            }
        } else if (nodeIndexes.size() != 1u) {
            _err << "Node index must be specified as there are multiple "
                         "targets.\n";
        } else {
            _nodeIndex = *nodeIndexes.begin();
            indexFound = true;
        }
        if (!indexFound) {
            _err << "Detected node indexes on local node in cluster '"
                      << _cluster << "':\n";
            for (std::set<uint16_t>::const_iterator it = nodeIndexes.begin();
                 it != nodeIndexes.end(); ++it)
            {
                _err << "  " << *it << "\n";
            }
            return false;
        }
            // Validate modes
        if (_mode != "enable" && _mode != "disable" && _mode != "status") {
            _err << "Illegal mode '" << _mode << "'.\n";
            return false;
        }
            // Warn if senseless options are given in status mode
        if (_mode == "status" && (_diskIndex != 0xffffffff || _message != "")) {
            _err << "Warning: Disk index and/or reason makes no sense in "
                      << "status mode.\n";
        }
        if ((_mode == "enable" || _mode == "disable")
            && _diskIndex == 0xffffffff)
        {
            _err << "A disk index must be given to specify which disk to "
                      << _mode << ".\n";
            return false;
        }
        if (_mode == "disable" && _message == "") {
            _err << "A reason must be given for why you are disabling the "
                         "disk.\n";
            return false;
        }
        if (_mode == "enable" || _mode == "disable") {
            std::ostringstream dir;
            dir << _rootpath << "/" << _cluster << "/storage/" << _nodeIndex
                << "/disks/d" << _diskIndex;
            if (!vespalib::fileExists(dir.str())) {
                _err << "Cannot " << _mode << " missing disk "
                          << _diskIndex << ". No disk detected at "
                          << dir.str() << "\n";
                return false;
            }
        }
        return true;
    }

    vector<uint16_t> getNodeIndexes() {
        vector<uint16_t> indexes;
        indexes.push_back(_nodeIndex);
        return indexes;
    }

    std::string getNodePath(uint16_t nodeIndex) {
        std::ostringstream ost;
        ost << _rootpath << "/" << _cluster << "/storage/" << nodeIndex;
        return ost.str();
    }

    std::string getPidFile(uint16_t nodeIndex) {
        return getNodePath(nodeIndex) + "/pidfile";
    }

};

CmdLineOptions::~CmdLineOptions() {}

int
VdsDiskTool::run(int argc, const char * const * argv,
                 const std::string& rootPath,
                 std::ostream& out, std::ostream& err)
{
    CmdLineOptions options(argc, argv, rootPath, err);
    try{
        LOG(debug, "Parsing command line options");
        options.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        LOG(debug, "Failed parsing command line options");
        if (!options._showSyntax) {
            err << e.getMessage() << "\n";
            options.writeSyntaxPage(err, false);
            err << "\n";
            return 1;
        }
    }
    if (options._showSyntax) {
        options.writeSyntaxPage(err, false);
        err << "\n";
        return 0;
    }
    LOG(debug, "Validating options");
    if (!options.validate()) {
        LOG(debug, "Options failed validation");
        options.writeSyntaxPage(err, false);
        return 1;
    }
    LOG(debug, "Iterate over all nodes to operate on");
        // Iterate over all node indexes to operate on.
    for (uint32_t indexIterator = 0;
         indexIterator < options.getNodeIndexes().size(); ++indexIterator)
    {
        uint16_t nodeIndex = options.getNodeIndexes()[indexIterator];
        std::string pidFile = options.getPidFile(nodeIndex);

            // Read pid if process is running
        uint32_t pid = 0;
        try{
            if (vespalib::fileExists(pidFile)) {
                pid = readPid(pidFile);
                if (kill(pid, 0) != 0) {
                    err << "Failed to signal process with pid "
                              << pid << " (" << errno << "): "
                              << getLastErrorString() << ". If storage node is "
                              << "running it needs to be manually restarted"
                              << " before changes take effect.\n";
                } else if (options._mode == "status") {
                    out << "Storage node " << nodeIndex
                              << " in cluster " << options._cluster
                              << " is running with pid " << pid << ".\n";
                }
            }
        } catch (vespalib::IoException& e) {
            err << "Failed to read pid file: " << e.getMessage()
                      << "\n";
            if (options._mode != "status") {
                err << "Not restarting storage node after changes.\n";
            }
        }
        framework::defaultimplementation::RealClock clock;
        // Read the disk status file.
        DeviceManager::UP devMan(new DeviceManager(
                DeviceMapper::UP(new SimpleDeviceMapper),
                clock));
        MountPointList mountPointList(options.getNodePath(nodeIndex),
                                      vector<vespalib::string>(),
                                      std::move(devMan));
        mountPointList.scanForDisks();
        if (options._mode == "enable" || options._mode == "disable") {
            if (mountPointList.getSize() <= options._diskIndex
                || mountPointList[options._diskIndex].getState()
                        == Device::NOT_FOUND)
            {
                err << "Disk " << options._diskIndex << " on node "
                          << nodeIndex << " in cluster "
                          << options._cluster << " does not exist. "
                          << "Cannot enable or disable a non-existing "
                          << "disk.\n";
                return 1;
            }
            if (mountPointList[options._diskIndex].getState()
                        != Device::OK)
            {
                err << "Disk " << options._diskIndex << " on node "
                          << nodeIndex << " in cluster "
                          << options._cluster << " fails pre-initialize "
                          << "routine. Cannot enable or disable disk with "
                          << "such a problem: "
                          << mountPointList[options._diskIndex] << "\n";
                return 1;
            }
        }
        vector<Device::State> preFileStates(
                mountPointList.getSize());
        for (uint32_t i=0; i<mountPointList.getSize(); ++i) {
            preFileStates[i] = mountPointList[i].getState();
        }
        mountPointList.readFromFile();
        if (options._mode == "enable") {
            Directory& dir(mountPointList[options._diskIndex]);
            if (dir.getState() == Device::OK) {
                out << "Disk " << options._diskIndex << " on node "
                          << nodeIndex << " in cluster "
                          << options._cluster << " is already enabled. "
                          << "Nothing to do.\n";
                continue;
            }
                // Shouldn't be null when state is not OK
            assert(dir.getLastEvent() != 0);
            IOEvent oldEvent(*dir.getLastEvent());
            dir.clearEvents();
            dir.getPartition().clearEvents();
            dir.getPartition().getDisk().clearEvents();
            if (preFileStates[options._diskIndex] != Device::OK) {
                out << "Cannot enable disk " << options._diskIndex
                          << " on node " << nodeIndex << " in cluster "
                          << options._cluster << ", as it has a failure "
                          << "that must be fixed by an admin.\n";
                if (preFileStates[options._diskIndex]
                        != oldEvent.getState())
                {
                    out << "Clearing any stored state such that the "
                              << "disk will work once admin fixes\n"
                              << "the current error.\n";
                }
            } else {
                out << "Reactivating disk " << options._diskIndex
                          << " on node " << nodeIndex << " in cluster "
                          << options._cluster << ". Removed stored event: "
                          << oldEvent << "\n";
            }
        } else if (options._mode == "disable") {
            Directory& dir(mountPointList[options._diskIndex]);
            if (dir.getState() != Device::OK) {
                    // Shouldn't be null when state is not OK
                assert(dir.getLastEvent() != 0);
                IOEvent oldEvent(*dir.getLastEvent());
                out << "Disk " << options._diskIndex << " on node "
                          << nodeIndex << " in cluster "
                          << options._cluster << " is already disabled. "
                          << "Overriding old event: " << oldEvent << "\n";
            }
            dir.clearEvents();
            dir.getPartition().clearEvents();
            dir.getPartition().getDisk().clearEvents();
            IOEvent newEvent(clock.getTimeInSeconds().getTime(),
                             Device::DISABLED_BY_ADMIN,
                             options._message, "vdsdisktool");
            dir.addEvent(newEvent);
            out << "Deactivated disk " << options._diskIndex
                      << " on node " << nodeIndex << " in cluster "
                      << options._cluster << ". Added event: "
                      << newEvent << "\n";
        } else if (options._mode == "status") {
            out << "Disks on storage node " << nodeIndex
                      << " in cluster " << options._cluster << ":\n";
            if (mountPointList.getSize() == 0) {
                out << "  No disks at all are set up.\n";
            }
            for (uint32_t i=0; i<mountPointList.getSize(); ++i) {
                out << "  Disk " << i << ": ";
                Directory& dir(mountPointList[i]);
                if (dir.isOk()) {
                    out << "OK\n";
                } else {
                    const IOEvent* event(dir.getLastEvent());
                    assert(event != 0); // If so disk is ok
                    out << Device::getStateString(
                                    event->getState())
                              << " - " << event->getDescription() << "\n";
                }
            }
        }
        if (options._mode == "enable" || options._mode == "disable") {
            out << "Writing disk status file to disk\n";
            mountPointList.writeToFile();
            if (pid != 0) {
                out << "Killing node such that it reads new data\n";
                int result = kill(pid, SIGTERM);
                if (result != 0) {
                    if (errno == EINVAL) {
                        err << "Signal SIGTERM not recognized.\n";
                    } else if (errno == EPERM) {
                        err << "No permission to send kill signal to "
                                     "storage process\n";
                    } else if (errno == ESRCH) {
                        err << "No process or process group found "
                                     "using pid " << pid << "\n";
                    }
                }
            }
            out << "Done\n";
            continue;
        }
    }
    return 0;
}

} // memfile
} // storage
