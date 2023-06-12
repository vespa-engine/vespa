// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread_meets.h"

namespace vespalib::test {

void
ThreadMeets::Nop::mingle()
{
}

void
ThreadMeets::Avg::mingle()
{
    double sum = 0;
    for (size_t i = 0; i < size(); ++i) {
        sum += in(i);
    }
    double result = sum / size();
    for (size_t i = 0; i < size(); ++i) {
        out(i) = result;
    }
}

void
ThreadMeets::Vote::mingle()
{
    size_t true_cnt = 0;
    size_t false_cnt = 0;
    for (size_t i = 0; i < size(); ++i) {
        if (in(i)) {
            ++true_cnt;
        } else {
            ++false_cnt;
        }
    }
    bool result = (true_cnt > false_cnt);
    for (size_t i = 0; i < size(); ++i) {
        out(i) = result;
    }
}

}
