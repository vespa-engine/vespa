// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "wand_bench_setup.hpp"

TEST_FF("benchmark", VespaWandFactory(1000),             WandSetup(f1,    10, 10000000)) { f2.benchmark(); }
TEST_FF("benchmark", TermFrequencyRiseWandFactory(1000), WandSetup(f1,    10, 10000000)) { f2.benchmark(); }
TEST_FF("benchmark", VespaWandFactory(1000),             WandSetup(f1,   100, 10000000)) { f2.benchmark(); }
TEST_FF("benchmark", TermFrequencyRiseWandFactory(1000), WandSetup(f1,   100, 10000000)) { f2.benchmark(); }
TEST_FF("benchmark", VespaWandFactory(1000),             WandSetup(f1,  1000, 10000000)) { f2.benchmark(); }
TEST_FF("benchmark", TermFrequencyRiseWandFactory(1000), WandSetup(f1,  1000, 10000000)) { f2.benchmark(); }

TEST_FFF("benchmark", VespaWandFactory(1000),             FilterFactory(f1, 2), WandSetup(f2,    10, 10000000)) { f3.benchmark(); }
TEST_FFF("benchmark", TermFrequencyRiseWandFactory(1000), FilterFactory(f1, 2), WandSetup(f2,    10, 10000000)) { f3.benchmark(); }
TEST_FFF("benchmark", VespaWandFactory(1000),             FilterFactory(f1, 2), WandSetup(f2,   100, 10000000)) { f3.benchmark(); }
TEST_FFF("benchmark", TermFrequencyRiseWandFactory(1000), FilterFactory(f1, 2), WandSetup(f2,   100, 10000000)) { f3.benchmark(); }
TEST_FFF("benchmark", VespaWandFactory(1000),             FilterFactory(f1, 2), WandSetup(f2,  1000, 10000000)) { f3.benchmark(); }
TEST_FFF("benchmark", TermFrequencyRiseWandFactory(1000), FilterFactory(f1, 2), WandSetup(f2,  1000, 10000000)) { f3.benchmark(); }

TEST_MAIN() { TEST_RUN_ALL(); }
