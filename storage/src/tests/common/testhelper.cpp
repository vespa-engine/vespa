// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <tests/common/testhelper.h>

#include <vespa/log/log.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>

LOG_SETUP(".testhelper");

namespace storage {

void addStorageDistributionConfig(vdstestlib::DirConfig& dc)
{
    vdstestlib::DirConfig::Config* config;
    config = &dc.getConfig("stor-distribution", true);
    config->clear();
    config->set("group[1]");
    config->set("group[0].name", "invalid");
    config->set("group[0].index", "invalid");
    config->set("group[0].nodes[50]");
    config->set("redundancy", "2");

    for (uint32_t i = 0; i < 50; i++) {
        std::ostringstream key; key << "group[0].nodes[" << i << "].index";
        std::ostringstream val; val << i;
        config->set(key.str(), val.str());
    }
}

std::string getRootFolder(vdstestlib::DirConfig & dc) {
    std::string defaultValue("");
    return dc.getConfig("stor-server").getValue("root_folder", defaultValue);
}

vdstestlib::DirConfig getStandardConfig(bool storagenode, const std::string & rootOfRoot) {
    std::string clusterName("storage");
    vdstestlib::DirConfig dc;
    vdstestlib::DirConfig::Config* config;
    config = &dc.addConfig("fleetcontroller");
    config->set("cluster_name", clusterName);
    config->set("index", "0");
    config->set("zookeeper_server", "\"\"");
    config->set("total_distributor_count", "10");
    config->set("total_storage_count", "10");
    config = &dc.addConfig("upgrading");
    config = &dc.addConfig("load-type");
    config = &dc.addConfig("bucket");
    config = &dc.addConfig("messagebus");
    config = &dc.addConfig("stor-prioritymapping");
    config = &dc.addConfig("stor-bucketdbupdater");
    config = &dc.addConfig("metricsmanager");
    config->set("consumer[2]");
    config->set("consumer[0].name", "\"status\"");
    config->set("consumer[0].addedmetrics[1]");
    config->set("consumer[0].addedmetrics[0]", "\"*\"");
    config->set("consumer[1].name", "\"statereporter\"");
    config->set("consumer[1].addedmetrics[1]");
    config->set("consumer[1].addedmetrics[0]", "\"*\"");
    config = &dc.addConfig("stor-communicationmanager");
    config->set("rpcport", "0");
    config->set("mbusport", "0");
    config = &dc.addConfig("stor-bucketdb");
    config->set("chunklevel", "0");
    config = &dc.addConfig("stor-distributormanager");
    config->set("splitcount", "1000");
    config->set("splitsize", "10000000");
    config->set("joincount", "500");
    config->set("joinsize", "5000000");
    config->set("max_clock_skew_sec", "0");
    config = &dc.addConfig("stor-opslogger");
    config = &dc.addConfig("persistence");
    config->set("abort_operations_with_changed_bucket_ownership", "true");
    config = &dc.addConfig("stor-filestor");
    // Easier to see what goes wrong with only 1 thread per disk.
    config->set("num_threads", "1");
    config->set("num_response_threads", "1");
    config->set("maximum_versions_of_single_document_stored", "0");
    config->set("keep_remove_time_period", "2000000000");
    config->set("revert_time_period", "2000000000");
    // Don't want test to call exit()
    config->set("fail_disk_after_error_count", "0");
    config = &dc.addConfig("stor-bouncer");
    config = &dc.addConfig("stor-server");
    config->set("cluster_name", clusterName);
    config->set("enable_dead_lock_detector", "false");
    config->set("enable_dead_lock_detector_warnings", "false");
    config->set("max_merges_per_node", "25");
    config->set("max_merge_queue_size", "20");
    config->set("resource_exhaustion_merge_back_pressure_duration_secs", "15.0");
    vespalib::string rootFolder = rootOfRoot + "_";
    rootFolder += (storagenode ? "vdsroot" : "vdsroot.distributor");
    config->set("root_folder", rootFolder);
    config->set("is_distributor", (storagenode ? "false" : "true"));
    config = &dc.addConfig("stor-devices");
    config->set("root_folder", rootFolder);
    config = &dc.addConfig("stor-status");
    config->set("httpport", "0");
    config = &dc.addConfig("stor-visitor");
    config->set("defaultdocblocksize", "8192");
    // By default, need "old" behaviour of maxconcurrent
    config->set("maxconcurrentvisitors_fixed", "4");
    config->set("maxconcurrentvisitors_variable", "0");
    config = &dc.addConfig("stor-visitordispatcher");
    addFileConfig(dc, "documenttypes", TEST_PATH("config-doctypes.cfg"));
    addStorageDistributionConfig(dc);
    return dc;
}

void addSlobrokConfig(vdstestlib::DirConfig& dc,
                          const mbus::Slobrok& slobrok)
{
    std::ostringstream ost;
    ost << "tcp/localhost:" << slobrok.port();
    vdstestlib::DirConfig::Config* config;
    config = &dc.getConfig("slobroks", true);
    config->clear();
    config->set("slobrok[1]");
    config->set("slobrok[0].connectionspec", ost.str());
}

void addFileConfig(vdstestlib::DirConfig& dc,
                       const std::string& configDefName,
                       const std::string& fileName)
{
    vdstestlib::DirConfig::Config* config;
    config = &dc.getConfig(configDefName, true);
    config->clear();
    std::ifstream in(fileName.c_str());
    std::string line;
    while (std::getline(in, line, '\n')) {
        std::string::size_type pos = line.find(' ');
        if (pos == std::string::npos) {
            config->set(line);
        } else {
            config->set(line.substr(0, pos), line.substr(pos + 1));
        }
    }
    in.close();
}

TestName::TestName(const std::string& n)
    : name(n)
{
    LOG(debug, "Starting test %s", name.c_str());
}

TestName::~TestName() {
    LOG(debug, "Done with test %s", name.c_str());
}

} // storage
