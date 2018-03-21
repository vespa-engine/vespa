// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <tests/testhelper.h>
#include <vespa/vespalib/io/fileutil.h>

#include <vespa/log/log.h>
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

    for (uint32_t i = 0; i < 50; i++) {
        std::ostringstream key; key << "group[0].nodes[" << i << "].index";
        std::ostringstream val; val << i;
        config->set(key.str(), val.str());
    }
}

vdstestlib::DirConfig getStandardConfig(bool storagenode) {
    vdstestlib::DirConfig dc;
    vdstestlib::DirConfig::Config* config;
    config = &dc.addConfig("upgrading");
    config = &dc.addConfig("load-type");
    config = &dc.addConfig("bucket");
    config = &dc.addConfig("messagebus");
    config = &dc.addConfig("stor-prioritymapping");
    config = &dc.addConfig("stor-bucketdbupdater");
    config = &dc.addConfig("stor-bucket-init");
    config = &dc.addConfig("metricsmanager");
    config->set("consumer[1]");
    config->set("consumer[0].name", "\"status\"");
    config->set("consumer[0].addedmetrics[1]");
    config->set("consumer[0].addedmetrics[0]", "\"*\"");
    config = &dc.addConfig("stor-communicationmanager");
    config->set("rpcport", "0");
    config->set("mbusport", "0");
    config = &dc.addConfig("stor-bucketdb");
    config->set("chunklevel", "0");
    config = &dc.addConfig("stor-distributormanager");
    config = &dc.addConfig("stor-opslogger");
    config = &dc.addConfig("stor-filestor");
    // Easier to see what goes wrong with only 1 thread per disk.
    config->set("minimum_file_meta_slots", "2");
    config->set("minimum_file_header_block_size", "368");
    config->set("minimum_file_size", "4096");
    config->set("threads[1]");
    config->set("threads[0].lowestpri 255");
    config->set("dir_spread", "4");
    config->set("dir_levels", "0");
    config->set("maximum_versions_of_single_document_stored", "0");
    //config->set("enable_slotfile_cache", "false");
    // Unit tests typically use fake low time values, so don't complain
    // about them or compact/delete them by default. Override in tests testing that
    // behavior
    config->set("keep_remove_time_period", "2000000000");
    config->set("revert_time_period", "2000000000");
    config = &dc.addConfig("stor-memfilepersistence");
    // Easier to see what goes wrong with only 1 thread per disk.
    config->set("minimum_file_meta_slots", "2");
    config->set("minimum_file_header_block_size", "368");
    config->set("minimum_file_size", "4096");
    config->set("dir_spread", "4");
    config->set("dir_levels", "0");
    config = &dc.addConfig("persistence");
    config->set("keep_remove_time_period", "2000000000");
    config->set("revert_time_period", "2000000000");
    config->set("fail_disk_after_error_count", "0");
    config = &dc.addConfig("stor-bouncer");
    config = &dc.addConfig("stor-integritychecker");
    config = &dc.addConfig("stor-bucketmover");
    config = &dc.addConfig("stor-messageforwarder");
    config = &dc.addConfig("stor-server");
    config->set("enable_dead_lock_detector", "false");
    config->set("enable_dead_lock_detector_warnings", "false");
    config->set("max_merges_per_node", "25");
    config->set("max_merge_queue_size", "20");
    config->set("root_folder",
                    (storagenode ? "vdsroot" : "vdsroot.distributor"));
    config->set("is_distributor",
                    (storagenode ? "false" : "true"));
    config = &dc.addConfig("stor-devices");
    config->set("root_folder",
                    (storagenode ? "vdsroot" : "vdsroot.distributor"));
    config = &dc.addConfig("stor-status");
    config->set("httpport", "0");
    config = &dc.addConfig("stor-visitor");
    config->set("defaultdocblocksize", "8192");
    // By default, need "old" behaviour of maxconcurrent
    config->set("maxconcurrentvisitors_fixed", "4");
    config->set("maxconcurrentvisitors_variable", "0");
    config = &dc.addConfig("stor-visitordispatcher");
    addFileConfig(dc, "documenttypes", "config-doctypes.cfg");
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
