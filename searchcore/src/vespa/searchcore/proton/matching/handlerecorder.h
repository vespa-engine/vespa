// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/handle.h>
#include <vespa/searchlib/fef/match_data_details.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/noncopyable.hpp>

namespace search::fef { class MatchData; }

namespace proton::matching {

/**
 * This is a recorder that will register all handles used by any features for a given query.
 * It is activated using thread locals by using the Binder.
 * In order to ensure that no handles goes by unnoticed and asserter is added. It should typically have the
 * same lifespan as the recorder itself.
 * After the Binders has gone out of scope this recorder has a list of all feature handles that might be
 * by this query. This can then be used to avoid a lot of unpacking of data.
 */
class HandleRecorder
{
public:
    using HandleMap = vespalib::hash_map<search::fef::TermFieldHandle, search::fef::MatchDataDetails>;
    class Binder : public vespalib::noncopyable {
    public:
        Binder(HandleRecorder & recorder);
        ~Binder();
    };
    class Asserter : public vespalib::noncopyable {
    public:
        Asserter();
        ~Asserter();
    };
    HandleRecorder();
    ~HandleRecorder();
    const HandleMap& get_handles() const { return _handles; }
    HandleMap steal_handles() && { return std::move(_handles); }
    static void register_handle(search::fef::TermFieldHandle handle,
                                search::fef::MatchDataDetails requested_details);
    vespalib::string to_string() const;
    void tag_match_data(search::fef::MatchData &match_data);
private:
    void add(search::fef::TermFieldHandle handle,
             search::fef::MatchDataDetails requested_details);
    HandleMap _handles;
};

}

