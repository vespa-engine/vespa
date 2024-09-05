// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cmath>
#include <limits>
#include <memory>
#include <string>
#include <vector>

namespace proton {

/**
 * A StatusReport describes the status of a search component.
 */
class StatusReport {
public:
    using UP = std::unique_ptr<StatusReport>;
    using SP = std::shared_ptr<StatusReport>;
    using List = std::vector<SP>;

    enum State {
        DOWN = 0,
        PARTIAL,
        UPOK
    };

    struct Params {
        std::string _component;
        State _state;
        std::string _internalState;
        std::string _internalConfigState;
        float _progress;
        std::string _message;

        Params(const std::string &component);
        ~Params();
        Params &state(State value) {
            _state = value;
            return *this;
        }
        Params &internalState(const std::string &value) {
            _internalState = value;
            return *this;
        }
        Params &internalConfigState(const std::string &value) {
            _internalConfigState = value;
            return *this;
        }
        Params &progress(float value) {
            _progress = value;
            return *this;
        }
        Params &message(const std::string &value) {
            _message = value;
            return *this;
        }
    };

private:
    std::string _component;
    State _state;
    std::string _internalState;
    std::string _internalConfigState;
    float _progress;
    std::string _message;

public:
    StatusReport(const Params &params);
    ~StatusReport();

    static StatusReport::UP create(const Params &params) {
        return std::make_unique<StatusReport>(params);
    }

    const std::string &getComponent() const {
        return _component;
    }

    State getState() const {
        return _state;
    }

    const std::string &getInternalState() const {
        return _internalState;
    }

    const std::string &getInternalConfigState() const {
        return _internalConfigState;
    }

    bool hasProgress() const {
        return !std::isnan(_progress);
    }

    float getProgress() const {
        return _progress;
    }

    const std::string &getMessage() const {
        return _message;
    }

    std::string getInternalStatesStr() const {
        std::string retval = "state=" + _internalState;
        if (!_internalConfigState.empty()) {
            retval = retval + " configstate=" + _internalConfigState;
        }
        return retval;
    }

};

/**
 * A StatusProducer is able to produce a list of StatusReport objects
 * when needed.
 **/
struct StatusProducer {
    virtual StatusReport::List getStatusReports() const = 0;
    virtual ~StatusProducer() {}
};

} // namespace proton

