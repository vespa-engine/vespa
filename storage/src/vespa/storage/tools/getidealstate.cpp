// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/config/print/ostreamconfigwriter.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configgetter.hpp>
#include <iostream>
#include <sstream>

namespace storage {

struct Options : public vespalib::ProgramOptions {
    bool showSyntaxPage;
    std::string clusterName;
    std::string clusterState;
    uint32_t redundancy;
    std::string bucket;
    std::string upStates;
    bool bucketsOnStdIn;
    bool verbose;

    Options(int argc, const char* const* argv);
    ~Options();

    bool useConfig() const { return !clusterName.empty(); }

    std::string getConfigId() const {
        std::ostringstream ost;
        ost << "storage/cluster." << clusterName << "/distributor/0";
        return ost.str();
    }
};

Options::Options(int argc, const char* const* argv)
    : vespalib::ProgramOptions(argc, argv)
{
    setSyntaxMessage("Utility program for calculating the ideal state of buckets."
                     " Useful to verify correctness of distribution operations.");
    addOption("h help", showSyntaxPage, false,
              "Shows this help page");
    addOption("s clusterstate", clusterState, std::string(""),
              "The state of the cluster to calculate position in");
    addOption("r redundancy", redundancy, uint32_t(2),
              "The redundancy to keep for each bucket");
    addOption("u upstates", upStates, std::string("uims"),
              "States to consider as up in ideal state calculations");
    addOption("i stdin", bucketsOnStdIn, false,
              "Read stdin to get buckets to calculate ideal position for");
    addOption("v verbose", verbose, false,
              "Print extra information while running");
    addArgument("bucket", bucket, std::string(""),
                "Bucket for which to calculate ideal state");
    addOptionHeader("By default, it will be assumed that all nodes are in one top "
                    "group, and no config will be read to calculate bucket "
                    "positions. If a cluster name is specified, config will be "
                    "read to get group hierarchy correctly for cluster.");
    addOption("c clustername", clusterName, std::string(""),
              "Name of the cluster to get config from");
}
Options::~Options() {}


void processBucket(const lib::Distribution& distribution,
                   const lib::ClusterState& clusterState,
                   const std::string& upStates,
                   const document::BucketId& bucket)
{
    std::ostringstream ost;
    std::vector<uint16_t> storageNodes(distribution.getIdealStorageNodes(
                clusterState, bucket, upStates.c_str()));
    uint16_t distributorNode(distribution.getIdealDistributorNode(
                clusterState, bucket, upStates.c_str()));
    ost << bucket << " distributor: " << distributorNode
              << ", storage:";
    for (uint32_t i=0; i<storageNodes.size(); ++i) {
        ost << " " << storageNodes[i];
    }
    ost << "\n";
    std::cout << ost.str() << std::flush;
}

int run(int argc, char** argv) {
    Options o(argc, argv);
    try{
        o.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        if (!o.showSyntaxPage) {
            std::cerr << e.getMessage() << "\n\n";
            o.writeSyntaxPage(std::cerr);
            std::cerr << "\n";
            return 1;
        }
    }
    if (o.showSyntaxPage) {
        o.writeSyntaxPage(std::cerr);
        std::cerr << "\n";
        return 0;
    }

    uint16_t redundancy(o.redundancy);
    std::unique_ptr<lib::Distribution> distribution;
    lib::ClusterState clusterState(o.clusterState);

    if (o.useConfig()) {
        try{
            if (o.verbose) {
                std::cerr << "Fetching distribution config using config id '"
                          << o.getConfigId() << "'.\n";
            }
            config::ConfigUri uri(o.getConfigId());
            std::unique_ptr<vespa::config::content::StorDistributionConfig> config = config::ConfigGetter<vespa::config::content::StorDistributionConfig>::getConfig(uri.getConfigId(), uri.getContext());
            redundancy = config->redundancy;
            distribution.reset(new lib::Distribution(*config));
            if (o.verbose) {
                std::cerr << "Using distribution config: '";
                config::OstreamConfigWriter ocw(std::cerr);
                ocw.write(*config);
                std::cerr << "'.\n";
            }
        } catch (std::exception& e) {
            std::cerr << "Failed to initialize from config:\n" << e.what()
                      << "\n";
            return 1;
        }
    } else {
        uint16_t distributorCount(
                clusterState.getNodeCount(lib::NodeType::DISTRIBUTOR));
        if (o.verbose) {
            std::cerr << "Not reading config. Assuming one top group with all "
                      << distributorCount << " distributors having redundancy "
                      << redundancy << " with cluster state " << clusterState
                      << "\n";
        }
        lib::Distribution::ConfigWrapper config(lib::Distribution::getDefaultDistributionConfig(
                    redundancy, clusterState.getNodeCount(lib::NodeType::DISTRIBUTOR)));
        distribution.reset(new lib::Distribution(config));
        if (o.verbose) {
            std::cerr << "Using distribution config: '";
            config::OstreamConfigWriter ocw(std::cerr);
            ocw.write(config.get());
            std::cerr << "'.\n";
        }
    }
    if (o.verbose) {
        std::cerr << "Using cluster state '" << clusterState.toString(true)
                  << "'.\n";
    }

    if (!o.bucket.empty()) {
        char* endp;
        document::BucketId bucket(strtoull(o.bucket.c_str(), &endp, 16));
        if (*endp == '\0') {
            processBucket(*distribution, clusterState, o.upStates, bucket);
        } else {
            std::cerr << "Skipping bucket " << o.bucket
                      << " which failed to parse as a bucket. Failed to "
                      << "parse: " << endp << "\n";
        }
    } else if (o.bucketsOnStdIn) {
        std::string line;
        while (getline(std::cin, line)) {
            char* endp;
            document::BucketId bucket(strtoull(line.c_str(), &endp, 16));
            if (*endp == '\0') {
                processBucket(*distribution, clusterState, o.upStates, bucket);
            } else {
                std::cerr << "Skipping bucket " << line
                          << " which failed to parse as a bucket. Failed to "
                          << "parse: " << endp << "\n";
            }
        }
    } else {
        std::cerr << "Bucket not specified. Option for using stdin not used.\n"
                  << "No buckets to calculate ideal state for.\n";
        return 1;
    }
    return 0;
}

} // storage

int main(int argc, char** argv) {
    return storage::run(argc, argv);
}
