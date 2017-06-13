// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketvector.h"
#include "randombucket.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <cppunit/extensions/HelperMacros.h>
#include <fstream>
#include <list>
#include <ctime>



using std::cout;
using std::ios;
using std::string;
using std::vector;

namespace storage {
namespace lib {

class DataDistribution_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(DataDistribution_Test);
//    CPPUNIT_TEST(testDistributionBits);
    CPPUNIT_TEST(testgqLargeScale);
//    CPPUNIT_TEST(testDiskFailure);
//    CPPUNIT_TEST(testBucketGeneration);
//    CPPUNIT_TEST(testDocSchemes);
//    CPPUNIT_TEST(testNodeCapacity);
//    CPPUNIT_TEST(testGetWaste);
    CPPUNIT_TEST_SUITE_END();

public:
    DataDistribution_Test() : _distributionbits(16),
                              _gidbits(26),
                              _locationbits(32),
                              _scheme(DOC),
                              _num_users(1),
                              _index(0){}
protected:

    enum Scheme_t {DOC, USERDOC};

    void testDistributionBits();
    void testgqLargeScale();
    void testDiskFailure();
    void testDocSchemes();
    void testBucketGeneration();
    void testDiskDistributions();
    void testNodeCapacity();
    void testGetWaste();
    void testDiskDistribution(vespa::config::content::StorDistributionConfig::DiskDistribution, string dist_name);
    string getFileName(string params);
    void getDistribution(const vector<document::BucketId>& buckets,
                         vector<vector<float> >& nodes_disks,
                         vespa::config::content::StorDistributionConfig::DiskDistribution,
                         ClusterState state);
    vector<document::BucketId> generateBuckets(uint64_t n);
    void writeToFile(string file_name, vector<vector<float> >& results, int max_times);
    void moment(vector<float>& data, vector<float>& capacities, float* waste);

    uint32_t _distributionbits;
    uint32_t _gidbits;
    uint32_t _locationbits;
    Scheme_t _scheme;
    uint64_t _num_users;
    uint16_t _index;
};

CPPUNIT_TEST_SUITE_REGISTRATION( DataDistribution_Test );


uint64_t cumulativePick(vector<float> capacity, RandomGen* rg);
vector<uint64_t> pickN(uint64_t n, vector<float> capacity);
ClusterState createClusterState(uint64_t num_nodes, uint64_t num_disks_per_node, vector<uint64_t>& failed_disks, vector<float> node_capacity);
void printVector(vector<uint64_t> v);
void printVector(vector<float> v);
void printVector(vector<vector<float> > v);
vector<float> filterFailedDisks(vector<vector<float> >& distribution, vector<uint64_t> indexes);
vector<float> filterFailedDisks(vector<float>& disks, vector<uint64_t> indexes);
string getFileName(string params);
vector<float> getCapacity(uint64_t n, float min_capacity, float max_capacity);
int readBucketsFromFile(std::string file, vector<document::BucketId>& buckets);
void getDisks(const vector<vector<float> >& distribution, vector<float>& disks);

void DataDistribution_Test::testBucketGeneration()
{

    _index=0;
    uint64_t total_buckets = 50000000;

    vector<document::BucketId> buckets_doc;
    vector<document::BucketId> buckets_userdoc;
    vector<uint32_t> countbits_doc(58,0);
    vector<uint32_t> countbits_userdoc(58,0);

    //generate buckets with doc scheme
    _scheme = DOC;
    buckets_doc = generateBuckets(total_buckets);
    for(size_t i=0; i < buckets_doc.size(); i++){
        countbits_doc[buckets_doc[i].getUsedBits()]++;
    }

    //generate buckets with userdoc scheme
    _scheme = USERDOC;
    _num_users = static_cast<uint64_t>(1.5*total_buckets);
    buckets_userdoc = generateBuckets(total_buckets);
    for(size_t i=0; i < buckets_userdoc.size(); i++){
        countbits_userdoc[buckets_userdoc[i].getUsedBits()]++;
    }

    // write data to file
    std::ostringstream ost;
    ost << "buckets-generation";
    string file_name = getFileName(ost.str());
    FastOS_File file;
    CPPUNIT_ASSERT(file.OpenWriteOnlyTruncate(file_name.c_str()));
    for(size_t i=0; i < countbits_doc.size(); i++){
        std::ostringstream ost2;
        ost2 << countbits_doc[i] << " " << countbits_userdoc[i] << "\n";
        std::cerr << countbits_doc[i] << " " << countbits_userdoc[i] << "\n";
        CPPUNIT_ASSERT(file.CheckedWrite(ost2.str().c_str(), ost2.str().size()));
    }
    CPPUNIT_ASSERT(file.Close());
}


void getDisks(const vector<vector<float> >& distribution, vector<float>& disks)
{
    for(size_t i=0; i < distribution.size();i++){
        for(size_t j=0; j < distribution[i].size();j++){
            disks.push_back(distribution[i][j]);
        }
    }
}

int readBucketsFromFile(std::string file, vector<document::BucketId>& buckets)
{
    std::string line;
    std::ifstream ifs;
    ifs.open(file.c_str(), ios::in);
    if (ifs.is_open()){
        uint64_t b;
        while (ifs >> b) {
            buckets.push_back(document::BucketId(b));
        }
        ifs.close();
        return 1;
    }
    else cout << "Unable to open file";

    return 0;
}

vector<document::BucketId> DataDistribution_Test::generateBuckets(uint64_t n)
{
    std::cerr << "Generating " << n << " buckets...\n";
    if(_scheme == USERDOC){
        RandomBucket::setUserDocScheme(_num_users, _locationbits);
    }
    else{
        RandomBucket::setDocScheme();
    }

    RandomBucket::setSeed();

    BucketVector::reserve(n);

    for(uint64_t i=0; i < n; i++){
        uint64_t u = RandomBucket::get();
        BucketVector::addBucket(u);
    }

    vector<document::BucketId> buckets;
    BucketVector::getBuckets(_distributionbits, buckets);
    BucketVector::clear();



    vector<uint64_t> countbits(58,0);
    for(size_t i=0; i < buckets.size(); i++){
        countbits[buckets[i].getUsedBits()]++;
    }
    printVector(countbits);
    std::cerr << "Generating buckets...DONE\n";

    return buckets;
}


void DataDistribution_Test::testDistributionBits()
{
    uint64_t num_disks_per_node=11;
    uint64_t num_buckets_per_disk=100000;
    uint64_t num_copies = 2;
    _scheme = USERDOC;
    float waste;

    for(uint64_t num_nodes=40; num_nodes <= 100; num_nodes+=20){
        vector<vector<float> > distribution(num_nodes, vector<float>(num_disks_per_node));
        uint64_t total_buckets = num_nodes*num_disks_per_node*num_buckets_per_disk/num_copies;
        _num_users = static_cast<uint64_t>(1.5*total_buckets);
        vector<float> disk_capacity(num_disks_per_node*num_nodes, 1.0);
        vector<uint64_t> failed_disks;
        vector<float> node_capacity = getCapacity(num_nodes, 1.0, 1.0);
        ClusterState state = createClusterState(num_nodes, num_disks_per_node, failed_disks, node_capacity);
        std::cerr << "testing " << state << "\n";
        vector<document::BucketId> buckets = generateBuckets(total_buckets);
        std::cerr << "Get distribution...\n";
        getDistribution(buckets, distribution, vespa::config::content::StorDistributionConfig::MODULO_INDEX, state);
        std::cerr << "Get distribution...DONE\n";
        vector<float> disks;
        getDisks(distribution, disks);

        printVector(disks);
        moment(disks, disk_capacity, &waste);
        std::cerr << "waste=" << waste << "\n";
    }


}

void DataDistribution_Test::testgqLargeScale()
{
    uint64_t num_disks_per_node=11;
    uint64_t num_copies = 2;
    uint64_t num_buckets_per_disk = 115000;
    _scheme = USERDOC;

    {
        string file_name = "waste_modulo_index_used_bits";
        FastOS_File file;
        CPPUNIT_ASSERT(file.OpenWriteOnlyTruncate(file_name.c_str()));

        for(uint64_t nodes=201; nodes <= 1000; nodes++){
            uint64_t total_buckets = nodes*num_disks_per_node*num_buckets_per_disk/num_copies;
            _num_users = static_cast<uint64_t>(1.5*total_buckets);
            vector<document::BucketId> buckets = generateBuckets(total_buckets);

            std::ostringstream ost;
            ost << "storage:" << nodes;
            ClusterState state(ost.str());
            float waste_mod, waste_used(0);
//            {
//                document::BucketIdFactory bucketIdFactory(_distributionbits, _gidbits, document::BucketConfig::USED_BITS);
//                vector<vector<float> > distribution(nodes, vector<float>(11));
//                getDistribution(buckets, distribution, bucketIdFactory, state);
//
//                vector<float> distribution2;
//                getDisks(distribution, distribution2);
//
//                vector<float> disk_capacity(num_disks_per_node*nodes, 1.0);
//                moment(distribution2, disk_capacity, &waste_used);
//            }
            std::cerr << "modulo_index DONE, waste:" << waste_mod << "\n";
            {
                vector<vector<float> > distribution(nodes, vector<float>(11));
                getDistribution(buckets, distribution, vespa::config::content::StorDistributionConfig::MODULO_INDEX, state);

                vector<float> distribution2;
                getDisks(distribution, distribution2);

                vector<float> disk_capacity(num_disks_per_node*nodes, 1.0);
                moment(distribution2, disk_capacity, &waste_mod);
            }

            std::ostringstream ost2;
            ost2 << nodes << " " << waste_mod << " " << waste_used << "\n";
            std::cerr << nodes << " " << waste_mod << " " << waste_used << "\n";
            CPPUNIT_ASSERT(file.CheckedWrite(ost2.str().c_str(), ost2.str().size()));
        }
        CPPUNIT_ASSERT(file.Close());
    }
}


void DataDistribution_Test::testDiskFailure()
{
    int num_disks_per_node=2;
    int num_nodes = 2;

    ClusterState state("storage: 2 .0.d:2 .0.d.0:d");

    _scheme = DOC;
    uint64_t total_buckets=1000000;
    vector<document::BucketId> buckets = generateBuckets(total_buckets);

    vector<float> disk_capacity(num_disks_per_node*num_nodes-1, 1.0);
    vector<uint64_t> failed_disks(1,0);
    float waste;
    {
        vector<vector<float> > distribution(num_nodes, vector<float>(2));
        getDistribution(buckets, distribution, vespa::config::content::StorDistributionConfig::MODULO, state);
        printVector(distribution);

        vector<float> distribution2 = filterFailedDisks(distribution, failed_disks);

        printVector(distribution2);
        moment(distribution2, disk_capacity, &waste);
        std::cerr << "waste=" << waste << "\n";
    }
    {
        vector<vector<float> > distribution(num_nodes, vector<float>(2));
        getDistribution(buckets, distribution, vespa::config::content::StorDistributionConfig::MODULO_INDEX, state);
        printVector(distribution);

        vector<float> distribution2 = filterFailedDisks(distribution, failed_disks);

        printVector(distribution2);
        moment(distribution2, disk_capacity, &waste);
        std::cerr << "waste=" << waste << "\n";
    }
    {
        vector<vector<float> > distribution(num_nodes, vector<float>(2));
        getDistribution(buckets, distribution, vespa::config::content::StorDistributionConfig::MODULO_KNUTH, state);
        printVector(distribution);

        vector<float> distribution2 = filterFailedDisks(distribution, failed_disks);

        printVector(distribution2);
        moment(distribution2, disk_capacity, &waste);
        std::cerr << "waste=" << waste << "\n";
    }
}


void DataDistribution_Test::testDocSchemes()
{
    _index=1;
    _scheme = DOC;
    testDiskDistributions();

    _scheme = USERDOC;
    testDiskDistributions();
}

void DataDistribution_Test::testDiskDistributions()
{
    {
        testDiskDistribution(vespa::config::content::StorDistributionConfig::MODULO, "modulo" );
    }

    {
        testDiskDistribution(vespa::config::content::StorDistributionConfig::MODULO_INDEX, "modulo_index" );
    }

    {
        testDiskDistribution(vespa::config::content::StorDistributionConfig::MODULO_KNUTH, "modulo_knuth");
    }


}


void DataDistribution_Test::testNodeCapacity()
{
    _index=1;
    {
        _scheme = DOC;
        testDiskDistribution(vespa::config::content::StorDistributionConfig::MODULO_INDEX, "capacity");
    }
    {
        _scheme = USERDOC;
        testDiskDistribution(vespa::config::content::StorDistributionConfig::MODULO_INDEX, "capacity");
    }
}



void DataDistribution_Test::testDiskDistribution(vespa::config::content::StorDistributionConfig::DiskDistribution diskDistribution, string dist_name)
{
    uint64_t num_buckets_per_disk = 10000;

    for(uint64_t num_copies=0; num_copies <= 3; num_copies++){
        num_buckets_per_disk = num_buckets_per_disk/num_copies;
        for(uint64_t num_failures=0; num_failures <= 5; num_failures++){
            vector<vector<float> > results(20, vector<float>(10, 0.0));
            std::ostringstream ost;
            ost << num_copies<< "copy_";
            ost << num_failures <<"faileddisks_";
            ost << "["<< 1 <<","<< 1 <<"]dcap_";
            ost << "["<< 1 <<","<< 1 <<"]ncap";
            ost << dist_name << "_";
            string file_name = getFileName(ost.str());
            for(uint64_t num_times=1; num_times <= 3; num_times++){
                std::cerr << file_name << " " << num_times << " time\n";
                for(uint64_t num_nodes=1; num_nodes <= 20; num_nodes++){
                    for(uint64_t num_disks_per_node=1; num_disks_per_node <= 10; num_disks_per_node++){
                        uint64_t num_total_buckets = num_nodes*num_disks_per_node*num_buckets_per_disk;
                        vector<float> disk_capacity(num_disks_per_node*num_nodes, 1.0);
                        vector<uint64_t> failed_disks = pickN(num_failures, disk_capacity);
                        std::sort(failed_disks.begin(), failed_disks.end());
                        vector<float> node_capacity = getCapacity(num_nodes, 1, 1);
                        ClusterState state = createClusterState(num_nodes, num_disks_per_node, failed_disks, node_capacity);
                        vector<document::BucketId> buckets = generateBuckets(num_total_buckets);
                        vector<vector<float> > distribution;
                        getDistribution(buckets, distribution, diskDistribution, state);
                        //std::cerr << "2D dist:\n";
                        //printVector(distribution);

                        for(uint64_t i=0; i<num_nodes; i++){
                            for(uint64_t j=0; j<num_disks_per_node ; j++){
                                disk_capacity[i*num_disks_per_node+j] = node_capacity[i]/num_disks_per_node;
                            }
                        }
                        //std::cerr << "failed disks: ";
                        //printVector(failed_disks);
                        //std::cerr << "disks capac:\n";
                        //printVector(disk_capacity);
                        vector<float> distribution2 = filterFailedDisks(distribution, failed_disks);
                        std::cerr << "dist2: ";
                        printVector(distribution2);

                        vector<float> disk_capacity2 = filterFailedDisks(disk_capacity, failed_disks);
                        //std::cerr << "disks2 capac:\n";
                        //printVector(disk_capacity2);
                        float waste;
                        moment(distribution2, disk_capacity2, &waste);

                        results[num_nodes-1][num_disks_per_node-1] += waste;
                        std::cerr << num_nodes << " " << num_disks_per_node << " " << waste << " \n";
                    }
                }
            }
            writeToFile(file_name, results, 3);
        }
    }
}


void DataDistribution_Test::writeToFile(string file_name, vector<vector<float> >& results, int max_times)
{
    fprintf(stderr, "%s\n", file_name.c_str());
    FastOS_File file;
    CPPUNIT_ASSERT(file.OpenWriteOnlyTruncate(file_name.c_str()));
    for(size_t node=1; node <= results.size(); node++){
        for(uint64_t disk=1; disk <= results[node].size(); disk++){
            std::ostringstream ost;
            ost << node << " " << disk << " " << results[node-1][disk-1]/max_times << "\n";
            std::cerr << node << " " << disk << " " << results[node-1][disk-1]/max_times << "\n";
            CPPUNIT_ASSERT(file.CheckedWrite(ost.str().c_str(), ost.str().size()));
        }
    }
    CPPUNIT_ASSERT(file.Close());
}

void DataDistribution_Test::getDistribution(
        const vector<document::BucketId>& buckets,
        vector<vector<float> >& nodes_disks,
        vespa::config::content::StorDistributionConfig::DiskDistribution diskDistribution,
        ClusterState state)
{
    vector<uint16_t> copies;

    Distribution distr(Distribution::getDefaultDistributionConfig(
                3, state.getNodeCount(NodeType::STORAGE), diskDistribution));

    NodeState nodeState;
    for (size_t i = 0; i < buckets.size(); i++) {
        copies = distr.getIdealStorageNodes(state, buckets[i], "u");

        for (unsigned k = 0; k < copies.size(); k++) {
            uint16_t node_index = copies[k];
            nodeState.setDiskCount(nodes_disks[node_index].size());
            uint16_t disk_index = distr.getIdealDisk(
                    nodeState, node_index, buckets[i]);
            nodes_disks[node_index][disk_index]++;
        }
    }
}

void scaleCapacity(vector<vector<float> >& distribution, vector<float>& capacity)
{
    for(size_t i=0; i< distribution.size(); i++){
        for(size_t j=0; j< distribution[i].size(); j++){
            distribution[i][j] = distribution[i][j]/capacity[i];
        }
    }
}

vector<float> getCapacity(uint64_t n, float min_capacity, float max_capacity)
{
    vector<float> capacity(n, 1.0);
    if(min_capacity == max_capacity){
        return capacity;
    }
    RandomGen rg(0);
    for(uint64_t i=0; i < n; i++){
        float r = rg.nextDouble();
        capacity[i] = min_capacity + r*(max_capacity-min_capacity);
    }
    return capacity;

}

vector<float> filterFailedDisks(vector<vector<float> >& distribution, vector<uint64_t> indexes)
{
    vector<float> distribution2;
    uint64_t d=0;
    for(size_t i=0; i< distribution.size(); i++){
        for(size_t j=0; j< distribution[i].size(); j++){
            if(d == indexes.size() || (i*distribution[i].size()+j) != indexes[d]){
                distribution2.push_back(distribution[i][j]);
            }
            else{
                d++;
            }
        }
    }
    return distribution2;
}

vector<float> filterFailedDisks(vector<float>& disks, vector<uint64_t> indexes)
{
    vector<float> disks2;
    uint64_t d=0;
    for(size_t i=0; i< disks.size(); i++){
        if(d == indexes.size() || i != indexes[d]){
            disks2.push_back(disks[i]);
        }
        else{
            d++;
        }
    }
    return disks2;
}


string DataDistribution_Test::getFileName(string params)
{
    string scheme = (_scheme == DOC) ? "doc" : "userdoc";
    std::ostringstream ost;
    ost << "datadistribution_" << _index << "_" << scheme << "_" << params << ".dat";
    return ost.str();
}


void printVector(vector<float> v)
{
    for(size_t i=0; i < v.size();i++){
        std::cerr << v[i] << " ";
    }
    std::cerr << "\n";
}


void printVector(vector<uint64_t> v)
{
    for(size_t i=0; i < v.size();i++){
        std::cerr << v[i] << " ";
    }
    std::cerr << "\n";
}


void printVector(vector<vector<float> > v)
{
    for(size_t i=0; i < v.size();i++){
        for(size_t j=0; j < v[i].size();j++){
            std::cerr << v[i][j] << "\n";
        }
    }
}

//pick N elements (indexes) with capacity weights
vector<uint64_t> pickN(uint64_t n, vector<float> capacity)
{
    vector<uint64_t> picked;
    // Initialize random generator
    RandomGen rg;

    if(n > capacity.size()){
        n = capacity.size();
    }

    for(size_t i=0; i<capacity.size(); i++){
        uint64_t x = cumulativePick(capacity, &rg);
        picked.push_back(x);
        capacity[x] = 0;
    }
    return picked;
}


uint64_t cumulativePick(vector<float> capacity, RandomGen* rg)
{
    uint64_t picked = 0;
    float s=0.0;

    for(size_t i=0; i< capacity.size(); i++){
        float r = rg->nextDouble();
        if(capacity[i] > 0){
            s+=capacity[i];
            if(r < (capacity[i]/s) ){
                picked = i;
            }
        }
    }
    return picked;
}

void DataDistribution_Test::moment(vector<float>& data, vector<float>& capacities, float* waste)
{

    *waste = 0.0;
    if(!data.empty() || data.size() == 1){
        if(data.size() != capacities.size()){
            std::cerr << "data: ";
            printVector(data);
            std::cerr << "capacities: ";
            printVector(capacities);
        }
        CPPUNIT_ASSERT(data.size() == capacities.size());
        uint64_t i_max=0;
        for (size_t i=1; i<data.size(); i++){
            CPPUNIT_ASSERT(capacities[i] != 0);
            if((data[i_max]/capacities[i_max]) < (data[i]/capacities[i])){
                i_max = i;
            }
        }

        float waste_coef = data[i_max]/capacities[i_max];

        float s = 0.0;
        for (size_t i=0; i<data.size(); i++){
            *waste += waste_coef*capacities[i]-data[i];
            s+=waste_coef*capacities[i];
        }
        *waste /= (s == 0)? 1 : s;
    }
}

ClusterState createClusterState(uint64_t num_nodes, uint64_t num_disks_per_node, vector<uint64_t>& failed_disks, vector<float> node_capacity)
{
    std::ostringstream ost;
    uint64_t node;

    ost << "storage:" << num_nodes;

    uint64_t j = 0;
    for (uint64_t i = 0; i < num_nodes; i++) {
        if(!node_capacity.empty() && node_capacity[i] != 1.0){
            ost << " ." << i <<".c:"<< node_capacity[i];
        }
        if(!failed_disks.empty()){
            node = failed_disks[j]/num_disks_per_node;
            if(node == i){
                ost << " ." << node <<".d:"<< num_disks_per_node ;
                while (node == i && j < failed_disks.size()) {
                    ost << " ." << node << ".d." << failed_disks[j]%num_disks_per_node<<":d";
                    j++;
                    node = failed_disks[j]/num_disks_per_node;
                }
            }
        }
    }
    //std::cerr << "Creating system state: "<< ost.str() << "\n";

    return ClusterState(ost.str());

}

} // lib
} // storage
