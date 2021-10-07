// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rankresult.h"
#include <cmath>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".fef.rankresult");

namespace search::fef::test {

RankResult::RankResult() :
    _rankScores(),
    _epsilon(0.0)
{
    // empty
}

RankResult &
RankResult::addScore(const vespalib::string & featureName, feature_t score)
{
    _rankScores[featureName] = score;
    return *this;
}

feature_t
RankResult::getScore(const vespalib::string & featureName) const
{
    RankScores::const_iterator itr = _rankScores.find(featureName);
    if (itr != _rankScores.end()) {
        return itr->second;
    }
    return 0.0f;
}

bool
RankResult::operator==(const RankResult & rhs) const
{
    return includes(rhs) && rhs.includes(*this);
}

bool
RankResult::includes(const RankResult & rhs) const
{
    double epsilon = std::max(_epsilon, rhs._epsilon);

    RankScores::const_iterator findItr;
    for (RankScores::const_iterator itr = rhs._rankScores.begin(); itr != rhs._rankScores.end(); ++itr) {
        findItr = _rankScores.find(itr->first);
        if (findItr == _rankScores.end()) {
            LOG(info, "Did not find expected feature '%s' in this rank result", itr->first.c_str());
            return false;
        }
        if (itr->second < findItr->second - epsilon ||
            itr->second > findItr->second + epsilon ||
            (std::isnan(findItr->second) && !std::isnan(itr->second)))
        {
            LOG(info, "Feature '%s' did not have expected score.", itr->first.c_str());
            LOG(info, "Expected: %f ~ %f", itr->second, epsilon);
            LOG(info, "Actual  : %f", findItr->second);
            return false;
        }
    }
    return true;
}

RankResult &
RankResult::clear()
{
    _rankScores.clear();
    return *this;
}

std::vector<vespalib::string> &
RankResult::getKeys(std::vector<vespalib::string> &ret)
{
    for (RankScores::const_iterator it = _rankScores.begin(); it != _rankScores.end(); ++it) {
        ret.push_back(it->first);
    }
    return ret;
}

std::vector<vespalib::string>
RankResult::getKeys()
{
    std::vector<vespalib::string> ret;
    return getKeys(ret);
}

RankResult &
RankResult::setEpsilon(double epsilon) {
    _epsilon = epsilon;
    return *this;
}

double
RankResult::getEpsilon() const {
    return _epsilon;
}

std::ostream & operator<<(std::ostream & os, const RankResult & rhs) {
    os << "[";
    for (RankResult::RankScores::const_iterator itr = rhs._rankScores.begin(); itr != rhs._rankScores.end(); ++itr) {
        os << "['" << itr->first << "' = " << itr->second << "]";
    }
    return os << "]";
}

}
