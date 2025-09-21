// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <lib/modelinspect.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <sstream>

class Model {
public:
    config::ConfigUri uri;
    ModelInspect::Flags flags;
    std::stringstream stream;
    ModelInspect model;

    Model() : uri(configUri("file", "model.cfg")), flags(), stream(),
              model(flags, uri, stream) {
    };

    Model(ModelInspect::Flags _flags)
        : uri(configUri("file", "model.cfg")), flags(_flags), stream(),
          model(flags, uri, stream) {
    };

    void contains(std::string needle) {
        ASSERT_TRUE(stream.str().find(needle) != std::string::npos);
    };

    void missing(std::string needle) {
        ASSERT_TRUE(stream.str().find(needle) == std::string::npos);
    };

    ~Model() {
    };

    static config::ConfigUri configUri(const std::string &type, const std::string &name)
    {
        return config::ConfigUri(type + ":" + TEST_PATH(name));
    }
};

class MakeUriFlags : public ModelInspect::Flags {
public:
    MakeUriFlags() : ModelInspect::Flags() {
        makeuri = true;
    }
};

class TagFilterFlags : public ModelInspect::Flags {
public:
    TagFilterFlags(std::string tag) : ModelInspect::Flags() {
        tagfilt = true;
        tagFilter.push_back(tag);
    }
};

class ModelDummy : public ModelInspect {
public:
    bool _yamlDump,
        _listHosts,
        _listServices,
        _listCluster,
        _listConfigIds,
        _listHost,
        _listClusters,
        _listAllPorts,
        _listService,
        _listService2,
        _listConfigId,
        _getIndexOf;
    ModelDummy(std::stringstream &stream) 
        : ModelInspect(ModelInspect::Flags(),
                       config::ConfigUri(Model::configUri("file", "model.cfg")),
                       stream) {
        _yamlDump = 
            _listHosts =
            _listServices =
            _listCluster =
            _listConfigIds =
            _listHost =
            _listClusters =
            _listAllPorts =
            _listService =
            _listService2 =
            _listConfigId =
            _getIndexOf = false;
    };

    void yamlDump() override { _yamlDump = true; };
    void listHosts() override { _listHosts = true; };
    void listServices() override { _listServices = true; };
    void listClusters() override { _listClusters = true; };
    void listConfigIds() override { _listConfigIds = true; };
    int listHost(const std::string) override  { _listHost = true; return 0; };
    int listAllPorts() override { _listAllPorts = true; return 0; };
    int listCluster(const std::string) override  { _listCluster = true; return 0; };
    int listService(const std::string) override  { _listService = true; return 0; };
    int listService(const std::string, const std::string) override  { _listService2 = true; return 0; };
    int listConfigId(const std::string) override { _listConfigId = true; return 0; };
    int getIndexOf(const std::string, const std::string) override { _getIndexOf = true; return 0; };

    ~ModelDummy() override;
};

ModelDummy::~ModelDummy() = default;

TEST(ModelInspectTest, yamlDump)
{
    Model f1;
    ModelInspect & inspect = f1.model;
    inspect.yamlDump();

    // Make sure that we got at least some yaml data out
    // TODO: Parse yaml
    f1.contains("- servicename: logd");
}

TEST(ModelInspectTest, listHosts)
{
    Model f1;
    f1.model.listHosts();
    f1.contains("example.yahoo.com");
}

TEST(ModelInspectTest, listServices)
{
    Model f1;
    f1.model.listServices();
    f1.contains("logd");
    f1.contains("qrserver");
}

TEST(ModelInspectTest, listClusters)
{
    Model f1;
    f1.model.listClusters();
    f1.contains("admin");
    f1.contains("default");
    f1.contains("music");
}

TEST(ModelInspectTest, listConfigIds)
{
    Model f1;
    f1.model.listConfigIds();
    f1.contains("search/qrsclusters/default/qrserver.0");
    f1.contains("admin/configservers/configserver.0");
}

TEST(ModelInspectTest, listHost)
{
    Model f1;
    ASSERT_EQ(0, f1.model.listHost("example.yahoo.com"));
    f1.contains("search/qrsclusters/default/qrserver.0");
    ASSERT_EQ(1, f1.model.listHost("nothere"));
}

TEST(ModelInspectTest, listCluster)
{
    Model f1;
    ASSERT_EQ(0, f1.model.listCluster("default"));
    f1.contains("search/qrsclusters/default/qrserver.0");

    ASSERT_EQ(1, f1.model.listCluster("nothere"));
}

TEST(ModelInspectTest, listAllPorts)
{
    Model f1;
    f1.model.listAllPorts();
    f1.contains("example.yahoo.com:19080");
}

TEST(ModelInspectTest, listService)
{
    Model f1;
    ASSERT_EQ(0, f1.model.listService("qrserver"));
    f1.contains("search/qrsclusters/default/qrserver.0");
    ASSERT_EQ(1, f1.model.listService("nothere"));
}

TEST(ModelInspectTest, listService_with_cluster)
{
    Model f1;
    ASSERT_EQ(0, f1.model.listService("default", "qrserver"));
    f1.contains("search/qrsclusters/default/qrserver.0");
    ASSERT_EQ(1, f1.model.listService("notacluster", "qrserver"));
}

TEST(ModelInspectTest, listConfigId)
{
    Model f1;
    ASSERT_EQ(0, f1.model.listConfigId("hosts/example.yahoo.com/logd"));
    f1.contains("logd @ example.yahoo.com : hosts");
    ASSERT_EQ(1, f1.model.listConfigId("nothere"));
}

TEST(ModelInspectTest, getIndexOf)
{
    Model f1;
    ASSERT_EQ(0, f1.model.getIndexOf("logd", "example.yahoo.com"));
    ASSERT_EQ(1, f1.model.getIndexOf("nothere", "example.yahoo.com"));
    ASSERT_EQ(1, f1.model.getIndexOf("nothere", "nothere"));
}

TEST(ModelInspectTest, tag_filter_match)
{
    TagFilterFlags f1("http");
    Model f2(f1);
    f2.model.listService("qrserver");
    f2.contains("tcp/example.yahoo.com:4080");
}

TEST(ModelInspectTest, tag_filter_no_match)
{
    TagFilterFlags f1("nothing");
    Model f2(f1);
    f2.model.listService("qrserver");
    f2.missing("tcp/example.yahoo.com:4080");
}

TEST(ModelInspectTest, makeuri)
{
    MakeUriFlags f1;
    Model f2(f1);
    f2.model.listService("qrserver");
    f2.contains("http://example.yahoo.com:4080");
}

TEST(ModelInspectTest, action_no_command)
{
    std::stringstream f1;
    ModelDummy f2(f1);
    char *argv[] = { strdup("notacommand"), nullptr, nullptr };
    ASSERT_EQ(1, f2.action(1, argv));
    ASSERT_EQ(1, f2.action(2, argv));
    ASSERT_EQ(1, f2.action(3, argv));
    free(argv[0]);
}

TEST(ModelInspectTest, action)
{
    std::stringstream f1;
    ModelDummy f2(f1);
    {
        char *arg[] = { strdup("yamldump") };
        ASSERT_EQ(0, f2.action(1, arg));
        ASSERT_TRUE(f2._yamlDump);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("hosts") };
        ASSERT_EQ(0, f2.action(1, arg));
        ASSERT_TRUE(f2._listHosts);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("services") };
        ASSERT_EQ(0, f2.action(1, arg));
        ASSERT_TRUE(f2._listServices);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("clusters") };
        ASSERT_EQ(0, f2.action(1, arg));
        ASSERT_TRUE(f2._listClusters);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("configids") };
        ASSERT_EQ(0, f2.action(1, arg));
        ASSERT_TRUE(f2._listConfigIds);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("host"), nullptr };
        ASSERT_EQ(0, f2.action(2, arg));
        ASSERT_TRUE(f2._listHost);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("cluster"), nullptr };
        ASSERT_EQ(0, f2.action(2, arg));
        ASSERT_TRUE(f2._listCluster);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("service"), nullptr };
        ASSERT_EQ(0, f2.action(2, arg));
        ASSERT_TRUE(f2._listService);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("configid"), nullptr };
        ASSERT_EQ(0, f2.action(2, arg));
        ASSERT_TRUE(f2._listConfigId);
        free(arg[0]);
    }
    {
        char *arg[] = { strdup("service"), strdup("a:b") };
        ASSERT_EQ(0, f2.action(2, arg));
        ASSERT_TRUE(f2._listService2);
        free(arg[0]);
        free(arg[1]);
    }
    {
        char *arg[] = { strdup("get-index-of"), nullptr, nullptr };
        ASSERT_EQ(0, f2.action(3, arg));
        ASSERT_TRUE(f2._getIndexOf);
        free(arg[0]);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
