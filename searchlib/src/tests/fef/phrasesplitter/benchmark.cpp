// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/phrasesplitter.h>
#include <vespa/searchlib/fef/phrase_splitter_query_env.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <iomanip>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("phrasesplitter_test");

namespace search::fef {

class Benchmark : public vespalib::TestApp
{
private:
    vespalib::Timer _timer;
    vespalib::duration _sample;

    void start() { _timer = vespalib::Timer(); }
    void sample() { _sample = _timer.elapsed(); }
    void run(size_t numRuns, size_t numPositions);

public:
    Benchmark() : _timer(), _sample(0) {}
    ~Benchmark() override;
    int Main() override;
};

Benchmark::~Benchmark() = default;

void
Benchmark::run(size_t numRuns, size_t numPositions)
{
    test::QueryEnvironment qe;
    std::vector<SimpleTermData> &terms = qe.getTerms();
    MatchDataLayout mdl;
    terms.push_back(SimpleTermData());
    terms.back().setUniqueId(1);
    terms.back().setPhraseLength(3); // phrase with 3 terms
    terms.back().addField(0).setHandle(mdl.allocTermField(0));
    MatchData::UP md = mdl.createMatchData();
    TermFieldMatchData *tmd = md->resolveTermField(terms[0].lookupField(0)->getHandle());
    for (size_t i = 0; i < numPositions; ++i) {
        tmd->appendPosition(TermFieldMatchDataPosition(0, i, 0, numPositions));
    }

    PhraseSplitterQueryEnv ps_query_env(qe, 0);
    PhraseSplitter ps(ps_query_env);

    std::cout << "Start benchmark with numRuns(" << numRuns << ") and numPositions(" << numPositions << ")" << std::endl;

    start();

    ps.bind_match_data(*md);
    for (size_t i = 0; i < numRuns; ++i) {
        ps.update();
    }

    sample();
}

int
Benchmark::Main()
{

    TEST_INIT("benchmark");

    if (_argc != 3) {
        std::cout << "Must specify <numRuns> and <numPositions>" << std::endl;
        return 0;
    }

    size_t numRuns = strtoull(_argv[1], nullptr, 10);
    size_t numPositions = strtoull(_argv[2], nullptr, 10);

    run(numRuns, numPositions);

    std::cout << "TET:  " << vespalib::count_ms(_sample) << " (ms)" << std::endl;
    std::cout << "ETPD: " << std::fixed << std::setprecision(10) << (vespalib::count_ns(_sample) / (numRuns * 1000000.0)) << " (ms)" << std::endl;

    TEST_DONE();
}

}

TEST_APPHOOK(search::fef::Benchmark);
