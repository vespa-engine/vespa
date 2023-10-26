// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusreport.h"

namespace proton {

StatusReport::Params::Params(const vespalib::string &component)
    : _component(component),
      _state(DOWN),
      _internalState(),
      _internalConfigState(),
      _progress(std::numeric_limits<float>::quiet_NaN()),
      _message()
{}

StatusReport::Params::~Params() { }

StatusReport::StatusReport(const Params &params)
    : _component(params._component),
      _state(params._state),
      _internalState(params._internalState),
      _internalConfigState(params._internalConfigState),
      _progress(params._progress),
      _message(params._message)
{}

StatusReport::~StatusReport() { }

}
