// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "opslogger.h"
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/config/subscription/configuri.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".operationslogger");

namespace storage {

OpsLogger::OpsLogger(StorageComponentRegister& compReg,
                     const config::ConfigUri & configUri)
    : StorageLink("Operations logger"),
      _lock(),
      _fileName(),
      _targetFile(nullptr),
      _component(compReg, "opslogger"),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext()))
{
    _configFetcher->subscribe<vespa::config::content::core::StorOpsloggerConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
}

OpsLogger::~OpsLogger()
{
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());

    if (_targetFile) {
        fclose(_targetFile);
    }
}

void
OpsLogger::onClose()
{
    // Avoid getting config during shutdown
    _configFetcher->close();
}

void
OpsLogger::configure(std::unique_ptr<vespa::config::content::core::StorOpsloggerConfig> config)
{
    std::lock_guard lock(_lock);
        // If no change in state, ignore
    if (config->targetfile == _fileName) return;
        // If a change we need to close old handle if open
    if (_targetFile != nullptr) {
        fclose(_targetFile);
        _targetFile = nullptr;
    }
        // Set up the new operations log file
    _fileName = config->targetfile;
    if (_fileName.length() > 0) {
        _targetFile = fopen(_fileName.c_str(), "a+b");

        if (!_targetFile) {
            LOG(warning, "Could not open file %s for operations logging",
                _fileName.c_str());
        }
    }
}

void
OpsLogger::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "OpsLogger()";
}

bool
OpsLogger::onPutReply(const std::shared_ptr<api::PutReply>& msg)
{
    if (_targetFile == nullptr) return false;
    std::ostringstream ost;
    ost << vespalib::to_string(_component.getClock().getSystemTime())
        << "\tPUT\t" << msg->getDocumentId() << "\t"
        << msg->getResult() << "\n";
    {
        std::lock_guard lock(_lock);
        if (_targetFile == nullptr) return false;
        fwrite(ost.str().c_str(), ost.str().length(), 1, _targetFile);
        fflush(_targetFile);
    }
    return false;
}

bool
OpsLogger::onUpdateReply(const std::shared_ptr<api::UpdateReply>& msg)
{
    if (_targetFile == nullptr) return false;
    std::ostringstream ost;
    ost << vespalib::to_string(_component.getClock().getSystemTime())
        << "\tUPDATE\t" << msg->getDocumentId() << "\t"
        << msg->getResult() << "\n";
    {
        std::lock_guard lock(_lock);
        if (_targetFile == nullptr) return false;
        fwrite(ost.str().c_str(), ost.str().length(), 1, _targetFile);
        fflush(_targetFile);
    }
    return false;
}

bool
OpsLogger::onRemoveReply(const std::shared_ptr<api::RemoveReply>& msg)
{
    if (_targetFile == nullptr) return false;
    std::ostringstream ost;
    ost << vespalib::to_string(_component.getClock().getSystemTime())
        << "\tREMOVE\t" << msg->getDocumentId() << "\t"
        << msg->getResult() << "\n";
    {
        std::lock_guard lock(_lock);
        if (_targetFile == nullptr) return false;
        fwrite(ost.str().c_str(), ost.str().length(), 1, _targetFile);
        fflush(_targetFile);
    }
    return false;
}

bool
OpsLogger::onGetReply(const std::shared_ptr<api::GetReply>& msg)
{
    if (_targetFile == nullptr) return false;
    std::ostringstream ost;
    ost << vespalib::to_string(_component.getClock().getSystemTime())
        << "\tGET\t" << msg->getDocumentId() << "\t"
        << msg->getResult() << "\n";
    {
        std::lock_guard lock(_lock);
        if (_targetFile == nullptr) return false;
        fwrite(ost.str().c_str(), ost.str().length(), 1, _targetFile);
        fflush(_targetFile);
    }
    return false;
}

} // storage
