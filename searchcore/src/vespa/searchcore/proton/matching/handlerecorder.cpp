// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handlerecorder.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>
#include <vespa/vespalib/util/array_equal.hpp>
#include <vespa/vespalib/util/tls_linkage.h>
#include <algorithm>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.handlerecorder");

using search::fef::MatchData;
using search::fef::MatchDataDetails;
using search::fef::TermFieldHandle;

namespace proton::matching {

namespace {
     __thread HandleRecorder * _T_recorder TLS_LINKAGE = nullptr;
     __thread bool _T_assert_all_handles_are_registered = false;
}

HandleRecorder::HandleRecorder() :
    _handles()
{
}

HandleRecorder::HandleRecorder(const HandleRecorder::HandleMap& initial_handles)
    : _handles(initial_handles)
{
}

namespace {

std::string
handles_to_string(const HandleRecorder::HandleMap& handles, MatchDataDetails requested_details)
{
    vespalib::asciistream os;
    std::vector<TermFieldHandle> sorted;
    for (const auto &handle : handles) {
        if ((static_cast<int>(handle.second) & static_cast<int>(requested_details)) != 0) {
            sorted.push_back(handle.first);
        }
    }
    std::sort(sorted.begin(), sorted.end());
    if ( !sorted.empty() ) {
        os << sorted[0];
        for (size_t i(1); i < sorted.size(); i++) {
            os << ',' << sorted[i];
        }
    }
    return os.str();
}

}

std::string
HandleRecorder::to_string() const
{
    vespalib::asciistream os;
    os << "normal: [" << handles_to_string(_handles, MatchDataDetails::Normal) << "], ";
    os << "interleaved: [" << handles_to_string(_handles, MatchDataDetails::Interleaved) << "]";
    return os.str();
}

HandleRecorder::Binder::Binder(HandleRecorder & recorder)
{
    _T_recorder = & recorder;
}

HandleRecorder::Asserter::Asserter()
{
    _T_assert_all_handles_are_registered = true;
}

HandleRecorder::Asserter::~Asserter()
{
    _T_assert_all_handles_are_registered = false;
}

HandleRecorder::~HandleRecorder()
{
}

HandleRecorder::Binder::~Binder()
{
    _T_recorder = nullptr;
}

void
HandleRecorder::register_handle(TermFieldHandle handle,
                                MatchDataDetails requested_details)
{
    LOG(debug, "register handle %d", handle);

    // There should be no registration of handles that is not recorded.
    // That will lead to issues later on.
    if (_T_recorder != nullptr) {
        _T_recorder->add(handle, requested_details);
    } else if (_T_assert_all_handles_are_registered) {
        assert(_T_recorder != nullptr);
    }
}

void
HandleRecorder::add(TermFieldHandle handle,
                    MatchDataDetails requested_details)

{
    LOG(debug, "add handle %d", handle);
    if (requested_details == MatchDataDetails::Normal ||
        requested_details == MatchDataDetails::Interleaved) {
        _handles[handle] = static_cast<MatchDataDetails>(static_cast<int>(_handles[handle]) | static_cast<int>(requested_details));
    } else {
        abort();
    }
}

void
HandleRecorder::tag_match_data(MatchData &match_data)
{
    for (TermFieldHandle handle = 0; handle < match_data.getNumTermFields(); ++handle) {
        auto &tfmd = *match_data.resolveTermField(handle);
        auto recorded = _handles.find(handle);
        if (recorded == _handles.end()) {
            LOG(debug, "handle %d not needed", handle);
            tfmd.tagAsNotNeeded();
        } else {
            LOG(debug, "handle %d needed", handle);
            tfmd.setNeedNormalFeatures((static_cast<int>(recorded->second) & static_cast<int>(MatchDataDetails::Normal)) != 0);
            tfmd.setNeedInterleavedFeatures((static_cast<int>(recorded->second) & static_cast<int>(MatchDataDetails::Interleaved)) != 0);
        }
    }
}

}

VESPALIB_HASH_MAP_INSTANTIATE(search::fef::TermFieldHandle, search::fef::MatchDataDetails);
