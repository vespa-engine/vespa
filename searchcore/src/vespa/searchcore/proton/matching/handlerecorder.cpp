// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "handlerecorder.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>


using search::fef::TermFieldHandle;

namespace proton {
namespace matching {

#ifdef __PIC__
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("initial-exec")))
#else
    #define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("local-exec")))
#endif

namespace {
     __thread HandleRecorder * _T_recorder TLS_LINKAGE = NULL;
     __thread bool _T_assert_all_handles_are_registered = false;
}

HandleRecorder::HandleRecorder() :
    _handles()
{
}

vespalib::string
HandleRecorder::toString() const
{
    vespalib::asciistream os;
    std::vector<TermFieldHandle> sorted;
    for (TermFieldHandle handle : _handles) {
        sorted.push_back(handle);
    }
    std::sort(sorted.begin(), sorted.end());
    if ( !sorted.empty() ) {
        os << sorted[0];
        for (size_t i(1); i < sorted.size(); i++) {
            os << ' ' << sorted[i];
        }
    }
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
    _T_recorder = NULL;
}

void HandleRecorder::registerHandle(TermFieldHandle handle)
{
    // There should be no registration of handles that is not recorded.
    // That will lead to issues later on.
    if (_T_recorder != NULL) {
        _T_recorder->add(handle);
    } else if (_T_assert_all_handles_are_registered) {
        assert(_T_recorder != NULL);
    }
}

}  // namespace matching
}  // namespace proton
