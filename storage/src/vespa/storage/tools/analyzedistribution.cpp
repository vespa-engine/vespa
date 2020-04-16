// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/judyarray.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/guard.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/nodestate.h>
#include <iostream>
#include <sstream>

namespace storage {

struct Options : public vespalib::ProgramOptions {
    bool verbose;
    bool showSyntaxPage;
    std::string systemState;
    int numDisks;
    int diskDistribution;
    double redundancy;
    std::string testdir;

    Options(int argc, const char* const* argv);
    ~Options();
};

Options::Options(int argc, const char* const* argv)
    : vespalib::ProgramOptions(argc, argv),
      showSyntaxPage(false),
      systemState(""),
      numDisks(0),
      diskDistribution(1),
      redundancy(2.0)
{
    setSyntaxMessage("Analyzes distribution from a real cluster. "
                     "This tool reads gzipped files containing directory "
                     "listings from a live system and analyze how current "
                     "distribution and ideal distribution is in that cluster."
                     "The tool is typically run from the perl check_cluster script "
                     "to create raw data for further analysis of cluster "
                     "distribution."
    );
    addOption("h help", showSyntaxPage, false, "Shows this help page");
    addOption("v verbose", verbose, false, "Show verbose progress");
    addOption("c clusterstate", systemState, "Cluster state to use for ideal state calculations");
    addOption("n numdisks", numDisks, "The number of disks on each node");
    addOption("r redundancy", redundancy, 2.0, "The redundancy used");
    addOption("d distribution", diskDistribution, 1,
              "The disk distribution to use (0 = MODULO, 1 = MODULO_INDEX, 2 = MODULO_KNUTH, 3 = MODULO_BID");
    addArgument("Test directory", testdir, std::string("."),
                "The directory within to find gzipped file listings named storage.*.shell.filelist.gz");
}
Options::~Options() {}



struct Disk {
    struct Count {
        uint32_t bucketCount;
        uint64_t totalByteSize;

        Count() : bucketCount(0), totalByteSize(0) {}
        void add(uint32_t size) { ++bucketCount; totalByteSize += size; }
        std::string toString() const {
            std::ostringstream ost;
            ost << bucketCount << '/' << totalByteSize;
            return ost.str();
        }
    };
    lib::DiskState state;
    Count current;
    Count wrongDisk;
    Count wrongNode;
    Count ideal;

    Disk(const lib::DiskState& state_)
        : state(state_) {}

    void addBucket(uint32_t size, bool currentDistr,
                   bool correctDisk, bool correctNode)
    {
        if (currentDistr) {
            current.add(size);
            if (!correctNode) {
                wrongNode.add(size);
            } else if (!correctDisk) {
                wrongDisk.add(size);
            }
        } else {
            ideal.add(size);
        }
    }

    void print(std::ostream& out, uint32_t nodeIndex, uint32_t diskIndex) {
        if (state.getState() == lib::State::UP) {
            out << "N " << nodeIndex << " D " << diskIndex << ": "
                << current.toString() << ' ' << ideal.toString() << ' '
                << wrongNode.toString() << ' ' << wrongDisk.toString() << "\n";
        }
    }
};

struct Node {
    lib::NodeState distributorState;
    lib::NodeState storageState;
    std::vector<Disk> disks;
    Disk::Count distributor;

    Node(const lib::NodeState& dstate, const lib::NodeState& sstate, uint32_t diskCount);
    ~Node();

    void print(std::ostream& out, uint32_t nodeIndex) {
        if (distributorState.getState().oneOf("ui")) {
            out << "N " << nodeIndex << ": " << distributor.toString() << "\n";
        }
        if (storageState.getState().oneOf("uir")) {
            for (uint32_t i=0; i<disks.size(); ++i) {
                disks[i].print(out, nodeIndex, i);
            }
        }
    }
};

Node::Node(const lib::NodeState& dstate, const lib::NodeState& sstate, uint32_t diskCount)
    : distributorState(dstate),
      storageState(sstate),
      disks()
{
    for (uint32_t i=0; i<diskCount; ++i) {
        disks.push_back(Disk(storageState.getDiskState(i)));
    }
}
Node::~Node() = default;

struct Distribution {
    std::vector<Node> nodes;
    enum Type { INDEX, BID, TEST };
    Type type;
    document::BucketIdFactory factory;
    lib::NodeState nodeState;
    uint32_t diskCount;
    lib::ClusterState state;
    std::unique_ptr<lib::Distribution> distribution;

    static lib::Distribution::DiskDistribution getDistr(Type t) {
        switch (t) {
            case INDEX: return lib::Distribution::MODULO_INDEX;
            case BID: return lib::Distribution::MODULO_BID;
            case TEST: return lib::Distribution::MODULO_BID;
        }
            // Compiler refuse to detect that the above is all possibilities
        assert(false);
        return lib::Distribution::MODULO_BID;
    }

    static uint8_t getDistributionBits(const lib::ClusterState& state, Type t)
    {
        switch (t) {
            case INDEX:
            case BID: return 16;
            case TEST:
            {
                uint32_t nodeCount(
                        state.getNodeCount(lib::NodeType::STORAGE));
                uint32_t minBuckets = 65536 * nodeCount;
                uint32_t distributionBits = 16;
                uint32_t buckets = 65536;
                while (buckets < minBuckets) {
                    ++distributionBits;
                    buckets *= 2;
                }
                return distributionBits;
            }
        }
            // Compiler refuse to detect that the above is all possibilities
        assert(false);
        return lib::Distribution::MODULO_BID;
    }

    Distribution(const lib::ClusterState& state_, uint32_t diskCount_, Type t)
        : nodes(),
          type(t),
          factory(), // getDistributionBits(state, t), 26, getDistr(t)),
          nodeState(),
          diskCount(diskCount_),
          state(state_),
          distribution(new lib::Distribution("storage/cluster.storage"))
    {
        for (uint32_t i=0, n=state.getNodeCount(lib::NodeType::STORAGE);
             i < n; ++i)
        {
            nodes.push_back(Node(state.getNodeState(
                    lib::Node(lib::NodeType::DISTRIBUTOR, i)),
                                 state.getNodeState(
                    lib::Node(lib::NodeType::STORAGE, i)), diskCount));
        }
        nodeState.setDiskCount(diskCount);
    }

    std::vector<uint16_t> getIdealStorageNodes(const document::BucketId& bucket,
                                               double reliability) const
    {
        (void) reliability;
        std::vector<uint16_t> nodes_;
        switch (type) {
            case INDEX:
            case BID:
            case TEST:
                nodes_ = distribution->getIdealStorageNodes(state, bucket);
        }
        return nodes_;
    }

    uint16_t getIdealDistributorNode(const document::BucketId& bucket) const {
        std::vector<uint16_t> nodes_;
        switch (type) {
            case INDEX:
            case BID:
            case TEST:
                return distribution->getIdealDistributorNode(state, bucket);
        }
            // Compiler refuse to detect that the above is all possibilities
        assert(false);
        return 0;
    }

    uint16_t getDisk(const document::BucketId& bucket, uint16_t nodeIndex) const
    {
        uint16_t disk = 65535;
        switch (type) {
            case INDEX:
            case BID:
            case TEST:
                disk = distribution->getIdealDisk(
                        nodeState, nodeIndex, bucket,
                        lib::Distribution::IDEAL_DISK_EVEN_IF_DOWN);
                break;
            default:
                assert(false);
        }
        return disk;
    }

    void print(std::ostream& out) {
        switch (type) {
            case INDEX: out << "Modulo index distribution\n"; break;
            case BID: out << "Modulo BID distribution\n"; break;
            case TEST: out << "Test distribution\n"; break;
        }
        for (uint32_t i=0; i<nodes.size(); ++i) {
            nodes[i].print(out, i);
        }
    }
};

struct BucketDatabase {
    JudyArray _judyArray;

    BucketDatabase() {}

    bool add(const document::BucketId& id) {
        bool preExisted;
        JudyArray::iterator it = _judyArray.find(id.getId(), true, preExisted);
        if (it.value() == 0) {
            it.setValue(1);
            return true;
        } else {
            return false;
        }
    }

    uint64_t size() const {
        return _judyArray.size();
    }
};

std::vector<std::string> getFileNames(const std::string& testdir) {
    std::vector<std::string> files;
    vespalib::DirPointer dir(opendir(testdir.c_str()));
    struct dirent* entry;
    assert(dir);
    while ((entry = readdir(dir))) {
        assert(entry != 0);
        std::string name(reinterpret_cast<char*>(&entry->d_name));
        assert(name.size() > 0);
        if (name.size() < 27) {
            continue;
        }
        if (name.substr(0, 8) != "storage.") {
            continue;
        }
        std::string::size_type pos = name.find('.', 8);
        if (pos == std::string::npos) {
            continue;
        }
        if (name.substr(pos) != ".shell.filelist.gz") {
            continue;
        }
        files.push_back(name);
    }
    return files;
}

struct Analyzer {
    const Options& o;
    const lib::ClusterState& state;
    BucketDatabase bucketdb;
    std::vector<std::shared_ptr<Distribution> > distributions;

    Analyzer(const lib::ClusterState& state_, const Options& o_)
        : o(o_),
          state(state_),
          bucketdb(),
          distributions()
    {
        distributions.push_back(std::shared_ptr<Distribution>(
                    new Distribution(state, o.numDisks, Distribution::INDEX)));
        distributions.push_back(std::shared_ptr<Distribution>(
                    new Distribution(state, o.numDisks, Distribution::BID)));
        distributions.push_back(std::shared_ptr<Distribution>(
                    new Distribution(state, o.numDisks, Distribution::TEST)));
    }

    void recordBucket(const document::BucketId& bucket, uint32_t size,
                      uint16_t nodeIndex, uint16_t diskIndex)
    {
        bool newBucket = bucketdb.add(bucket);
        //std::cout << "Recording file " << nodeIndex << " " << diskIndex
        //          << ": " << size << ' ' << bucket << "\n";
        for (uint32_t i=0; i<distributions.size(); ++i) {
            std::vector<uint16_t> ideal(distributions[i]->getIdealStorageNodes(
                         bucket, o.redundancy));
            bool correctNode = false;
            for (uint32_t j=0; j<ideal.size(); ++j) {
                if (ideal[j] == nodeIndex) correctNode = true;
            }
            uint16_t idealDisk = distributions[i]->getDisk(bucket, nodeIndex);
            distributions[i]->nodes[nodeIndex].disks[diskIndex].addBucket(
                    size, true, diskIndex == idealDisk, correctNode);
            if (newBucket) {
                for (uint32_t j=0; j<ideal.size(); ++j) {
                    idealDisk = distributions[i]->getDisk(bucket, ideal[j]);
                    distributions[i]->nodes[ideal[j]].disks[idealDisk]
                            .addBucket(size, false, true, true);
                }
                uint16_t distributor(
                        distributions[i]->getIdealDistributorNode(bucket));
                distributions[i]->nodes[distributor].distributor.add(size);
            }
        }
    }
    void recordDirectory(const std::string& name, uint32_t size,
                         uint16_t nodeIndex, uint16_t diskIndex)
    {
        (void) name; (void) size; (void) nodeIndex; (void) diskIndex;
    }
    void report() {
        std::cout << "Found " << bucketdb.size() << " buckets\n";
        for (uint32_t i=0; i<distributions.size(); ++i) {
            distributions[i]->print(std::cout);
        }
    }
};

void analyze(const Options& o) {
    lib::ClusterState state(o.systemState);

    if (o.verbose) {
        std::cerr << "Using test directory " << o.testdir << "\n";
    }

    Analyzer analyzer(state, o);
    std::vector<std::string> filenames(getFileNames(o.testdir));

    std::vector<char> buffer(256);
    std::string path;
    uint32_t nodeIndex = 0x10000;
    uint32_t diskIndex = 0x10000;
    double shownProgress = 0.0001;
    for (uint32_t j=0; j<filenames.size(); ++j) {
        std::string cmd("zcat " + o.testdir + "/" + filenames[j]);
        if (o.verbose) {
            std::cerr << "Running '" << cmd << "'.\n";
        } else {
            double currentProgress = 79.0 * j / filenames.size();
            while (currentProgress > shownProgress) {
                std::cerr << ".";
                shownProgress += 1;
            }
        }
        FILE* file = popen(cmd.c_str(), "r");
        assert(file);
        while (fgets(&buffer[0], buffer.size(), file)) {
            //std::cout << "Read line: " << &buffer[0];
            if (buffer[0] == '/') {
                nodeIndex = 0x10000;
                diskIndex = 0x10000;
                uint32_t slashcount = 0;
                uint32_t lastslash = 0;
                for (uint32_t i=1; i<buffer.size(); ++i) {
                    if (buffer[i] == ':') {
                        path = std::string(&buffer[0], i);
                        break;
                    } else if (buffer[i] == '\n' || buffer[i] == '\0') {
                        assert(0);
                    } else if (buffer[i] == '/') {
                        if (slashcount == 8) {
                            std::string indexs(&buffer[lastslash] + 1,
                                               i - lastslash - 1);
                            char* endp;
                            nodeIndex = strtoul(indexs.c_str(), &endp, 10);
                            if (*endp != '\0') {
                                std::cerr << "'" << indexs
                                          << "' is not a number.\n";
                            }
                            assert(*endp == '\0');
                        } else if (slashcount == 10) {
                            assert(buffer[lastslash + 1] == 'd');
                            std::string indexs(&buffer[lastslash] + 2,
                                               i - lastslash - 2);
                            char* endp;
                            diskIndex = strtoul(indexs.c_str(), &endp, 10);
                            if (*endp != '\0') {
                                std::cerr << "'" << indexs
                                          << "' is not a number.\n";
                            }
                            assert(*endp == '\0');
                        }
                        lastslash = i;
                        ++slashcount;
                    }
                }
            } else {
                uint32_t firstDigit, space, dot;
                firstDigit = space = dot = buffer.size();
                bool isDirectory = false;
                for (uint32_t i=0; i<buffer.size(); ++i) {
                    if (firstDigit == buffer.size()) {
                        if (buffer[i] >= '0' && buffer[i] <= '9') {
                            firstDigit = i;
                        } else if (buffer[i] == ' ' || buffer[i] == '\t') {
                            continue;
                        } else {
                            break;
                        }
                    } else if (space == buffer.size()) {
                        if (buffer[i] >= '0' && buffer[i] <= '9') {
                            continue;
                        } else if (buffer[i] == ' ') {
                            space = i;
                        } else {
                            break;
                        }
                    } else if (dot == buffer.size()) {
                        if (   (buffer[i] >= '0' && buffer[i] <= '9')
                            || (buffer[i] >= 'a' && buffer[i] <= 'f')
                            || (buffer[i] >= 'A' && buffer[i] <= 'F'))
                        {
                            continue;
                        } else if (buffer[i] == '.') {
                            dot = i;
                        } else if (buffer[i] == '\n' || buffer[i] == '\0') {
                            isDirectory = true;
                            dot = i;
                        }
                        break;
                    }
                }
                if (dot != buffer.size()) {
                    std::string sizes(&buffer[firstDigit], space - firstDigit);
                    char* endp;
                    uint32_t size = strtoul(sizes.c_str(), &endp, 10);
                    assert(*endp == '\0');
                    std::string bucket(&buffer[space + 1], dot - space - 1);
                    if (isDirectory) {
                        analyzer.recordDirectory(path + '/' + bucket, size,
                                                 nodeIndex, diskIndex);
                    } else {
                        uint64_t bid = strtoull(bucket.c_str(), &endp, 16);
                        assert(*endp == '\0');
                        document::BucketId bucketid(bid);
                        analyzer.recordBucket(bucketid, size,
                                              nodeIndex, diskIndex);
                    }
                } else {
                    // std::cout << "Did not find bucket from line: "
                    //           << &buffer[0] << "\n";
                    // std::cout << " " << firstDigit << " " << space << " "
                    //           << dot << "\n";
                }
            }
        }
        assert(ferror(file) == 0);
        assert(feof(file));
        assert(pclose(file) == 0);
    }
    if (!o.verbose) {
        std::cerr << "\n";
    }
    analyzer.report();
}

} // storage

int main(int argc, char** argv) {
    storage::Options o(argc, argv);
    o.parse();

    if (o.showSyntaxPage) {
        o.writeSyntaxPage(std::cerr);
        return 1;
    }
    analyze(o);
    return 0;
}

