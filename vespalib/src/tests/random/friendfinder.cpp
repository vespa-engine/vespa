// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <vector>

int main(int argc, char **argv)
{
    vespalib::RandomGen rnd(1);

    double logmean = std::log(1000.0);
    double lstddev = std::log(2.0);

    if (argc > 2) {
        lstddev = std::log(vespalib::locale::c::strtod(argv[--argc], NULL));
        logmean = std::log(vespalib::locale::c::strtod(argv[--argc], NULL));
    } else if (argc > 1) {
        logmean = std::log(vespalib::locale::c::strtod(argv[--argc], NULL));
    }

    fprintf(stderr, "100 typical friendlist sizes: ");
    for (int i = 0; i < 100; ++i) {
        int32_t want = (uint32_t)std::exp(rnd.nextNormal(logmean, lstddev));
        fprintf(stderr, " %u", want);
    }
    fprintf(stderr, "\n");

    uint32_t person = 0;
    while (!feof(stdin)) {
        ++person;
        std::vector<vespalib::string> friends;
        int32_t want = (uint32_t)std::exp(rnd.nextNormal(logmean, lstddev));
        if (want < 17) want = (uint32_t)(std::exp(logmean)+0.99);
        if (want < 1) want = 1;

        printf("me: %u friends:", person);
        while (want > 0) {
            char line[100];
            if (fgets(line, 100, stdin) == NULL) {
                break;
            }
            if (rnd.nextUint32() % 42 == 17) {
                vespalib::string s(line);
                s.chomp();
                friends.push_back(s);
                --want;
            }
        }
        while (!friends.empty()) {
            printf(" %s", friends.back().c_str());
            friends.pop_back();
        }
        printf("\n");
        fflush(stdout);
    }
}

