// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handlerecorder.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <cassert>

using search::fef::MatchDataDetails;
using search::fef::TermFieldHandle;

namespace proton::matching {

#ifdef __PIC__
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("initial-exec")))
#else
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("local-exec")))
#endif

namespace {
     __thread HandleRecorder * _T_recorder TLS_LINKAGE = nullptr;
     __thread bool _T_assert_all_handles_are_registered = false;
}

HandleRecorder::HandleRecorder() :
    _normal_handles(),
    _cheap_handles()
{
}

namespace {

vespalib::string
handles_to_string(const HandleRecorder::HandleSet& handles)
{
    vespalib::asciistream os;
    std::vector<TermFieldHandle> sorted;
    for (TermFieldHandle handle : handles) {
        sorted.push_back(handle);
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

vespalib::string
HandleRecorder::to_string() const
{
    vespalib::asciistream os;
    os << "normal: [" << handles_to_string(_normal_handles) << "], ";
    os << "cheap: [" << handles_to_string(_cheap_handles) << "]";
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
    if (requested_details == MatchDataDetails::Normal) {
        _normal_handles.insert(handle);
    } else if (requested_details == MatchDataDetails::Cheap) {
        _cheap_handles.insert(handle);
    } else {
        abort();
    }
}

}
