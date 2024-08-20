// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace search::fef {

/**
 * Class representing a set of configured ranking constants, with name, type and file path (where constant is stored).
 */
class RankingConstants {
public:
    struct Constant {
        std::string name;
        std::string type;
        std::string filePath;

        Constant(const std::string &name_in,
                 const std::string &type_in,
                 const std::string &filePath_in);
        ~Constant();
        bool operator==(const Constant &rhs) const;
    };

    using Vector = std::vector<Constant>;

private:
    using Map = std::map<std::string, Constant>;
    Map _constants;

public:
    RankingConstants();
    RankingConstants(RankingConstants &&) noexcept;
    RankingConstants & operator =(RankingConstants &&) = delete;
    RankingConstants(const RankingConstants &) = delete;
    RankingConstants & operator =(const RankingConstants &) = delete;
    explicit RankingConstants(const Vector &constants);
    ~RankingConstants();
    bool operator==(const RankingConstants &rhs) const;
    const Constant *getConstant(const std::string &name) const;
    size_t size() const { return _constants.size(); }
};

}
