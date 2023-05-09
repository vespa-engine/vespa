// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <vector>
#include <memory>

namespace search::fef {

/**
 * Class representing a set of configured ranking constants, with name, type and file path (where constant is stored).
 */
class RankingConstants {
public:
    struct Constant {
        vespalib::string name;
        vespalib::string type;
        vespalib::string filePath;

        Constant(const vespalib::string &name_in,
                 const vespalib::string &type_in,
                 const vespalib::string &filePath_in);
        ~Constant();
        bool operator==(const Constant &rhs) const;
    };

    using Vector = std::vector<Constant>;

private:
    using Map = std::map<vespalib::string, Constant>;
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
    const Constant *getConstant(const vespalib::string &name) const;
    size_t size() const { return _constants.size(); }
};

}
