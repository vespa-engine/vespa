// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <iomanip>
#include <iostream>
#include <algorithm>
#include <sstream>
#include <vespa/config-stor-distribution.h>

namespace storage {

    struct Options : public vespalib::ProgramOptions {
        uint32_t redundancy;
        uint32_t maxBit;
        std::vector<uint32_t> nodeCounts;
        std::vector<uint32_t> bitCounts;
        double hideUtilizationAbove;
        bool skipGood;
        bool highRange;
        bool printHtml;
        double htmlErrAbove;
        double htmlWarnAbove;
        double htmlInfoAbove;
        uint32_t skipBitsBelow;
        uint32_t skipNodeCountsBelow;
        uint32_t startAtNodeCount;

        Options(int argc, const char* const* argv);
        ~Options();

        void finalize() {
            if (highRange) {
                nodeCounts.insert(nodeCounts.begin(), {16, 20, 32, 48, 64, 100, 128, 160, 200, 256, 350, 500, 800, 1000, 5000});
            } else {
                nodeCounts.insert(nodeCounts.begin(), {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
            }
            for (uint32_t i=1; i<=maxBit; ++i) {
                bitCounts.push_back(i);
            }
            htmlErrAbove = hideUtilizationAbove;
            htmlWarnAbove = 0.10;
            htmlInfoAbove = 0.01;
        }
    };

    double generateSkew(uint32_t nodes, uint32_t distributionBits,
                        uint16_t redundancy)
    {
        lib::Distribution distr(lib::Distribution::getDefaultDistributionConfig(
                redundancy, nodes));
        lib::ClusterState state(
                vespalib::make_string("bits:%d storage:%d",
                                      distributionBits, nodes));

        std::vector<uint32_t> nodeList(nodes);
        uint32_t lastbucket = (distributionBits == 32
                ? 0xffffffff : (1 << distributionBits) - 1);

        for (uint32_t i = 0; i <= lastbucket; ++i) {
            std::vector<uint16_t> curr(
                distr.getIdealStorageNodes(state,
                        document::BucketId(distributionBits, i).stripUnused()));
            for (uint32_t j = 0; j < curr.size(); ++j) {
                ++nodeList[curr[j]];
            }
            if (i == 0xffffffff) break;
        }

        std::sort(nodeList.begin(), nodeList.end());
        uint64_t max = nodeList[nodeList.size() - 1];

        uint64_t maxArea = max * nodes;
        uint64_t wastedArea = 0;

        for (uint32_t i = 0; i < nodes; i++) {
            wastedArea += max - nodeList[i];
        }

        //std::cerr << "Least " << nodeList[0] << " Most "
        //          << nodeList[nodeList.size() - 1] << " " << "Total: "
        //          << buckets << " Max area " << maxArea << " Wasted area "
        //          << wastedArea << "\n";
        if (maxArea == 0) {
            return 0;
        } else {
            return ((double) wastedArea) / maxArea;
        }
    }


Options::Options(int argc, const char* const* argv)
    : vespalib::ProgramOptions(argc, argv)
{
    setSyntaxMessage("Utility program for calculating skew of buckets stored on storage nodes.");
    addOption("r redundancy", redundancy, 2u,
              "Number of copies stored on the nodes.");
    addOption("b maxbit", maxBit, 32u,
              "Maximum distribution bit count to calculate for.");
    addOption("h hide", hideUtilizationAbove, 0.3,
              "Hide utilizations worse than this.");
    addOption("s skip", skipGood, false,
              "Attempt to skip computations for node counts that already have good distributions");
    addOption("highrange", highRange, false,
              "Compute distribution for large systems instead of small systems");
    addOption("html", printHtml, false,
              "Print result as an HTML table");
    addOption("skipbitsbelow", skipBitsBelow, 0u,
              "Skip calculating for bits below given value");
    addOption("skipnodecountsbelow", skipNodeCountsBelow, 0u,
              "Skip calculating for node counts below given value");
    addOption("startatnodecount", startAtNodeCount, 0u,
              "Start calculating for first bit at given node count");
}
Options::~Options() {}

} // storage

int main(int argc, char** argv) {
    storage::Options o(argc, argv);
    try{
        o.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        std::cerr << e.getMessage() << "\n\n";
        o.writeSyntaxPage(std::cerr);
        std::cerr << "\n";
        return 1;
    }
    o.finalize();
    if (o.printHtml) { std::cout << "<b>"; }
    std::cout << "Distribution with redundancy " << std::setprecision(2)
              << o.redundancy << ":\n";
    if (o.printHtml) { std::cout << "</b>"; }
    if (o.printHtml) {
        std::cout << "<table border=\"1\">\n"
                  << "<tr>\n"
                  << "  <th><nobr>Bits \\ Nodes</nobr></th>\n";
        for (uint32_t i = 0; i<o.nodeCounts.size(); ++i) {
            std::cout << "  <td>" << o.nodeCounts[i] << "</td>\n";
        }
        std::cout << "</tr>\n";
    } else {
        std::cout << "\t";
        for (uint32_t i = 0; i<o.nodeCounts.size(); ++i) {
            std::cout << std::setw(8) << std::setfill(' ') << o.nodeCounts[i];
        }
        std::cout << "\nBits\n";
    }

    std::vector<double> tmpV(o.bitCounts.size(), -1);
    std::vector<std::vector<double> > results(o.nodeCounts.size(), tmpV);

    bool firstBitCalculated = true;
    int32_t firstBitIndex = -1;
    for (uint32_t bitIndex = 0; bitIndex < o.bitCounts.size();
         ++bitIndex)
    {
        uint32_t bits = o.bitCounts[bitIndex];
        if (bits < o.skipBitsBelow) {
            std::cerr << "Skipping calculating data for " << bits << " bit\n";
            continue;
        } else {
            if (firstBitIndex == -1) {
                firstBitIndex = bitIndex;
            } else {
                firstBitIndex = false;
            }
        }
        bool printedStart = false;
        std::ostringstream start;

        if (o.printHtml) {
            start << "<tr>\n"
                  << "  <td>" << bits << "</td>\n";
        } else {
            start << bits << "\t";
        }
        for (uint32_t nodeIndex = 0; nodeIndex < o.nodeCounts.size();
             ++nodeIndex)
        {
            uint32_t nodes = o.nodeCounts[nodeIndex];
            if (nodes < o.skipNodeCountsBelow ||
                (nodes < o.startAtNodeCount && firstBitCalculated))
            {
                std::cerr << "Skipping calculating data for " << bits
                          << " bits and " << nodes << " nodes\n";
                if (o.printHtml) {
                    (printedStart ? std::cout : start) << "  <td>-</td>\n";
                } else {
                    (printedStart ? std::cout : start)
                            << std::setw(8) << std::setfill(' ') << "-";
                }
            } else if (bitIndex - firstBitIndex > 3
                && results[nodeIndex][bitIndex - 1] <= o.htmlInfoAbove
                && results[nodeIndex][bitIndex - 2] <= o.htmlInfoAbove
                && results[nodeIndex][bitIndex - 3] <= o.htmlInfoAbove
                && results[nodeIndex][bitIndex - 4] <= o.htmlInfoAbove)
            {
                if (o.printHtml) {
                    (printedStart ? std::cout : start) << "  <td>-</td>\n";
                } else {
                    (printedStart ? std::cout : start)
                            << std::setw(8) << std::setfill(' ') << "-";
                }
            } else {
                double skew = storage::generateSkew(nodes, bits, o.redundancy);
                results[nodeIndex][bitIndex] = skew;
                std::string color = "";
                if (skew > o.htmlErrAbove) {
                    color = " bgcolor=\"red\"";
                } else if (skew > o.htmlWarnAbove) {
                    color = " bgcolor=\"#ffa500\""; // orange
                } else if (skew > o.htmlInfoAbove) {
                    color = " bgcolor=\"yellow\"";
                } else {
                    color = " bgcolor=\"#adff2f\""; // green
                }
                if (skew > o.hideUtilizationAbove) {
                    if (o.printHtml) {
                        (printedStart ? std::cout : start)
                                << "  <td" << color << ">"
                                << std::setprecision(4) << std::fixed << skew
                                << "</td>\n" << std::flush;
                        continue;
                    } else {
                        break;
                    }
                }
                if (!printedStart) {
                    std::cout << start.str();
                    printedStart = true;
                }
                if (o.printHtml) {
                    std::cout << "  <td" << color << ">" << std::setprecision(4)
                              << std::fixed << skew << "</td>\n" << std::flush;
                } else {
                    std::cout << std::setw(8) << std::setfill(' ')
                              << std::setprecision(4) << std::fixed << skew
                              << std::flush;
                }
            }
        }
        if (printedStart) {
            if (o.printHtml) {
                std::cout << "</tr>\n";
            } else {
                std::cout << "\n";
            }
        }
    }
    if (o.printHtml) {
        std::cout << "</table>\n";
    }

    return 0;
}
