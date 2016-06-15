// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("status-filedistribution");

#include <iostream>
#include <map>

#include <boost/program_options.hpp>
#include <boost/foreach.hpp>
#include <boost/thread.hpp>

#include <vespa/filedistribution/common/exceptionrethrower.h>
#include <vespa/filedistribution/model/zkfacade.h>
#include <vespa/filedistribution/model/filedistributionmodel.h>
#include <vespa/filedistribution/model/filedistributionmodelimpl.h>
#include <zookeeper/zookeeper.h>

using namespace filedistribution;

std::string
plural(size_t size)
{
    if (size == 1)
        return "";
    else
        return "s";
}

template <class CONT>
std::string
plural(const CONT& cont)
{
    size_t size = cont.size();
    return plural(size);
}

typedef FileDBModel::HostStatus HostStatus;
typedef std::map<std::string, HostStatus> StatusByHostName;

void
printWaitingForHosts(const StatusByHostName& notFinishedHosts)
{
    std::cout <<"Waiting for the following host" <<plural(notFinishedHosts) <<":" <<std::endl;
    BOOST_FOREACH(const StatusByHostName::value_type hostNameAndStatus, notFinishedHosts) {
        std::cout <<hostNameAndStatus.first <<"  (";

        const HostStatus& hostStatus = hostNameAndStatus.second;
        if (hostStatus._state == HostStatus::notStarted) {
            std::cout <<"Not started";
        } else {
            std::cout <<"Downloading, "
                      <<hostStatus._numFilesFinished <<"/" <<hostStatus._numFilesToDownload
                      <<" file" <<plural(hostStatus._numFilesToDownload) <<" completed";
        }
        std::cout <<")" <<std::endl;
    }
}

//TODO:refactor
int printStatus(const std::string& zkservers)
{
    boost::shared_ptr<ExceptionRethrower> exceptionRethrower;
    boost::shared_ptr<ZKFacade> zk(new ZKFacade(zkservers, exceptionRethrower));

    boost::shared_ptr<FileDBModel> model(new ZKFileDBModel(zk));

    std::vector<std::string> hosts = model->getHosts();

    StatusByHostName notFinishedHosts;
    StatusByHostName finishedHosts;
    bool hasStarted = false;

    BOOST_FOREACH(std::string host, hosts) {
        HostStatus hostStatus = model->getHostStatus(host);
        switch (hostStatus._state) {
          case HostStatus::finished:
            hasStarted = true;
            finishedHosts[host] = hostStatus;
            break;
          case HostStatus::inProgress:
            hasStarted = true;
          case HostStatus::notStarted:
            notFinishedHosts[host] = hostStatus;
            break;
        }
    }

    if (notFinishedHosts.empty()) {
        std::cout <<"Finished distributing files to all hosts." <<std::endl;
        return 0;
    } else if (!hasStarted) {
        std::cout <<"File distribution has not yet started." <<std::endl;
        return 0;
    } else {
        printWaitingForHosts(notFinishedHosts);
        return 5;
    }
}

int
printStatusRetryIfZKProblem(const std::string& zkservers, const std::string& zkLogFile)
{
    FILE* file = fopen(zkLogFile.c_str(), "w");
    if (file == NULL) {
         std::cerr <<"Could not open file " <<zkLogFile <<std::endl;
    } else {
         zoo_set_log_stream(file);
    }
    zoo_set_debug_level(ZOO_LOG_LEVEL_ERROR);

    for (int i = 0; i < 5; ++i) {
        try {
            return printStatus(zkservers);
        } catch (ZKNodeDoesNotExistsException& e) {
            LOG(debug, "Node does not exists, assuming concurrent update. %s", boost::diagnostic_information(e).c_str());

        } catch (ZKSessionExpired& e) {
            LOG(debug, "Session expired.");
        }
        boost::this_thread::sleep(boost::posix_time::milliseconds(500));
    }
    return 4;
}


//TODO: move to common
struct ProgramOptionException {
    std::string _msg;
    ProgramOptionException(const std::string & msg)
        : _msg(msg)
    {}
};

bool exists(const std::string& optionName, const boost::program_options::variables_map& map) {
    return map.find(optionName) != map.end();
}

void ensureExists(const std::string& optionName, const boost::program_options::variables_map& map \
                  ) {
    if (!exists(optionName, map)) {
        throw ProgramOptionException("Error: Missing option " + optionName);
    }
}
//END: move to common


int
main(int argc, char** argv) {
    const char
        *zkstring = "zkstring",
        *zkLogFile = "zkLogFile",
        *help = "help";

    namespace po = boost::program_options;
    boost::program_options::options_description description;
    description.add_options()
        (zkstring, po::value<std::string > (), "The zookeeper servers to connect to, separated by comma")
        (zkLogFile, po::value<std::string >() -> default_value("/dev/null"), "Zookeeper log file")
        (help, "help");

    try {
        boost::program_options::variables_map values;
        po::store(
                boost::program_options::parse_command_line(argc, argv, description),
                values);

        if (exists(help, values)) {
            std::cout <<description;
            return 0;
        }

        ensureExists(zkstring, values);

        return printStatusRetryIfZKProblem(
                values[zkstring] .as<std::string>(),
                values[zkLogFile].as<std::string>());
    } catch (const ProgramOptionException& e) {
        std::cerr <<e._msg <<std::endl;
        return 3;
    } catch(const std::exception& e) {
        std::cerr <<"Error: " <<e.what() <<std::endl;
        return 3;
    }
}
