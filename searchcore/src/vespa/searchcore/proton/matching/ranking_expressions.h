// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <map>

namespace proton::matching {

/**
 * Class representing a collection of named ranking expressions
 * obtained through file-distribution.
 */
class RankingExpressions
{
private:
    // expression name -> full_path of expression file
    std::map<vespalib::string,vespalib::string> _expressions;

public:
    using SP = std::shared_ptr<RankingExpressions>;
    RankingExpressions();
    RankingExpressions(RankingExpressions &&rhs) noexcept;
    RankingExpressions & operator=(RankingExpressions &&rhs) = delete;
    RankingExpressions(const RankingExpressions &rhs) = delete;
    RankingExpressions & operator=(const RankingExpressions &rhs) = delete;
    ~RankingExpressions();
    bool operator==(const RankingExpressions &rhs) const {
        return _expressions == rhs._expressions;
    }
    size_t size() const { return _expressions.size(); }
    RankingExpressions &add(const vespalib::string &name, const vespalib::string &path);
    vespalib::string loadExpression(const vespalib::string &name) const;
};

}
