// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultset.h"

namespace document::select {

std::vector<ResultSet> ResultSet::_ands;
std::vector<ResultSet> ResultSet::_ors;
std::vector<ResultSet> ResultSet::_nots;

/*
 * Precalculate possible outcomes of boolean operations, given possible
 * inputs.
 */
void
ResultSet::preCalc()
{
    uint32_t erange = Result::enumRange;
    uint32_t range = illegalMask();
    _ands.resize(range * range);
    _ors.resize(range * range);
    _nots.resize(range);
    for (ResultSet lset; lset.pcvalid(); lset.pcnext()) {
        for (ResultSet rset; rset.pcvalid(); rset.pcnext()) {
            ResultSet myand;
            ResultSet myor;
            for (uint32_t lenum = 0; lenum < erange; ++lenum) {
                if (!lset.hasEnum(lenum))
                    continue;
                const Result &lhs(Result::fromEnum(lenum));
                for (uint32_t renum = 0; renum < erange; ++renum) {
                    if (!rset.hasEnum(renum))
                        continue;
                    const Result &rhs(Result::fromEnum(renum));
                    myand.add(lhs && rhs);
                    myor.add(lhs || rhs);
                }
            }
            _ands[(lset._val << erange) + rset._val] = myand;
            _ors[(lset._val << erange) + rset._val] = myor;
        }
        ResultSet mynot;
        for (uint32_t lenum = 0; lenum < erange; ++lenum) {
            if (!lset.hasEnum(lenum))
                continue;
            const Result &lhs(Result::fromEnum(lenum));
            mynot.add(!lhs);
        }
        _nots[lset._val] = mynot;
    }
}


namespace {

class Precalc
{
public:
    Precalc()
    {
        ResultSet::preCalc();
    }
};


Precalc precalc;

}

}
