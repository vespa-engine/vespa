// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <memory>
#include <string>

namespace search::fef {

/**
 * Class representing a collection of named ranking expressions
 * obtained through file-distribution.
 */
class RankingExpressions
{
private:
    // expression name -> full_path of expression file
    std::map<std::string,std::string> _expressions;

public:
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
    RankingExpressions &add(const std::string &name, const std::string &path);
    std::string loadExpression(const std::string &name) const;
};

}
