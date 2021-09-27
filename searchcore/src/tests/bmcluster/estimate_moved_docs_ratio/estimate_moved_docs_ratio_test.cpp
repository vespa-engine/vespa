// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchcore/bmcluster/calculate_moved_docs_ratio.h>
#include <vespa/searchcore/bmcluster/estimate_moved_docs_ratio.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("estimate_moved_docs_ratio_test");

using search::bmcluster::CalculateMovedDocsRatio;
using search::bmcluster::EstimateMovedDocsRatio;

namespace {

bool verbose;

TEST(EstimateMovedDocsRatioTest, estimate_lost_docs_ratio)
{
    for (uint32_t nodes = 1; nodes < 2; ++nodes) {
        for (uint32_t redundancy = 1; redundancy <= nodes; ++redundancy) {
            for (uint32_t lost_nodes = 0; lost_nodes <= nodes; ++lost_nodes) {
                auto scanner = CalculateMovedDocsRatio::make_crash_calculator(redundancy, lost_nodes, nodes);
                scanner.scan();
                double lost_docs_base_ratio = scanner.get_lost_docs_base_ratio();
                double estimated_lost_docs_base_ratio = EstimateMovedDocsRatio().estimate_lost_docs_base_ratio(redundancy, lost_nodes, nodes);
                EXPECT_DOUBLE_EQ(lost_docs_base_ratio, estimated_lost_docs_base_ratio);
            }
        }
    }
}

TEST(EstimateMovedDocsRatioTest, estimate_moved_docs_ratio_grow)
{
    for (uint32_t nodes = 1; nodes < 10; ++nodes) {
        for (uint32_t redundancy = 1; redundancy <= nodes; ++redundancy) {
            for (uint32_t added_nodes = 0; added_nodes <= nodes; ++added_nodes) {
                auto scanner = CalculateMovedDocsRatio::make_grow_calculator(redundancy, added_nodes, nodes);
                scanner.scan();
                double moved_docs_ratio = scanner.get_moved_docs_ratio();
                double estimated_moved_docs_ratio = EstimateMovedDocsRatio().estimate_moved_docs_ratio_grow(redundancy, added_nodes, nodes);
                EXPECT_DOUBLE_EQ(moved_docs_ratio, estimated_moved_docs_ratio);
            }
        }
    }
}

TEST(EstimateMovedDocsRatioTest, estimate_moved_docs_ratio_shrink)
{
    for (uint32_t nodes = 1; nodes < 10; ++nodes) {
        for (uint32_t redundancy = 1; redundancy <= nodes; ++redundancy) {
            for (uint32_t retired_nodes = 0; retired_nodes <= nodes; ++retired_nodes) {
                auto scanner = CalculateMovedDocsRatio::make_shrink_calculator(redundancy, retired_nodes, nodes);
                scanner.scan();
                double moved_docs_ratio = scanner.get_moved_docs_ratio();
                double estimated_moved_docs_ratio = EstimateMovedDocsRatio().estimate_moved_docs_ratio_shrink(redundancy, retired_nodes, nodes);
                EXPECT_DOUBLE_EQ(moved_docs_ratio, estimated_moved_docs_ratio);
            }
        }
    }
}

TEST(EstimateMovedDocsRatioTest, estimate_moved_docs_ratio_crash)
{
    double epsilon = 1e-15;
    for (uint32_t nodes = 1; nodes < 10; ++nodes) {
        for (uint32_t redundancy = 1; redundancy <= nodes; ++redundancy) {
            for (uint32_t crashed_nodes = 0; crashed_nodes <= nodes; ++crashed_nodes) {
                auto scanner = CalculateMovedDocsRatio::make_crash_calculator(redundancy, crashed_nodes, nodes);
                scanner.scan();
                double moved_docs_ratio = scanner.get_moved_docs_ratio();
                double estimated_moved_docs_ratio = EstimateMovedDocsRatio().estimate_moved_docs_ratio_crash(redundancy, crashed_nodes, nodes);
                EXPECT_NEAR(moved_docs_ratio, estimated_moved_docs_ratio, epsilon);
            }
        }
    }
}

TEST(EstimateMovedDocsRatioTest, estimate_moved_docs_ratio_replace)
{
    uint32_t bad_cases = 0;
    uint32_t really_bad_cases = 0;
    if (verbose) {
        std::cout << "Summary: HDR Red   A Ret   N          Act          Est      ScaleMv      ScaleEs  States" << std::endl;
    }
    for (uint32_t nodes = 1; nodes < 6; ++nodes) {
        for (uint32_t redundancy = 1; redundancy <= nodes; ++redundancy) {
            for (uint32_t retired_nodes = 0; retired_nodes <= nodes; ++retired_nodes) {
                for (uint32_t added_nodes = 0; added_nodes <= nodes - retired_nodes; ++added_nodes) {
                    // std::cout << "Estimate moved docs ratio replace " << retired_nodes << " of " << nodes << " retired, added " << added_nodes << " nodes ,redundancy " << redundancy << std::endl;
                    auto scanner = CalculateMovedDocsRatio::make_replace_calculator(redundancy, added_nodes, retired_nodes, nodes);
                    scanner.scan();
                    double moved_docs_ratio = scanner.get_moved_docs_ratio();
                    double estimated_moved_docs_ratio = EstimateMovedDocsRatio(verbose).estimate_moved_docs_ratio_replace(redundancy, added_nodes, retired_nodes, nodes);
                    double error_ratio = abs(moved_docs_ratio - estimated_moved_docs_ratio);
                    bool bad = error_ratio > 1e-8;
                    bool really_bad = error_ratio > 0.2 * estimated_moved_docs_ratio + 1e-8;
                    if (bad) {
                        ++bad_cases;
                    }
                    if (really_bad) {
                        ++really_bad_cases;
                    }
                    if (verbose) {
                        double scaled_moved = moved_docs_ratio * scanner.get_checked_states();
                        double scaled_estimated_moved = estimated_moved_docs_ratio * scanner.get_checked_states();
                        std::cout << "Summary: " << (bad ? "BAD" : "OK ") << std::setw(4) << redundancy << std::setw(4) << added_nodes << std::setw(4) << retired_nodes << std::setw(4) << nodes << " " << std::setw(12) << std::setprecision(5) << std::fixed << moved_docs_ratio << " " << std::setw(12) << std::setprecision(5) << std::fixed << estimated_moved_docs_ratio << " " << std::setw(12) << std::setprecision(5) << std::fixed << scaled_moved << " " << std::setw(12) << std::setprecision(5) << std::fixed << scaled_estimated_moved << std::setw(8) << scanner.get_checked_states();
                        std::cout << " [";
                        for (uint32_t node_idx = 0; node_idx < nodes; ++node_idx) {
                            std::cout << std::setw(8) << scanner.get_moved_docs_per_node()[node_idx];
                        }
                        std::cout << " ]" << std::endl;
                    }
                    // TODO: Fix calculation so we get zero bad cases.
                    // EXPECT_DOUBLE_EQ(moved_docs_ratio, estimated_moved_docs_ratio);
                }
            }
        }
    }
    EXPECT_LE(6, bad_cases);
    EXPECT_LE(1, really_bad_cases);
}

}  // namespace

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 1 && std::string("--verbose") == argv[1]) {
        verbose = true;
    }
    return RUN_ALL_TESTS();
}
