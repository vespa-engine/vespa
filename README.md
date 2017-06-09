# Vespa
Vespa is an engine for low-latency computation over large data sets.
It stores and indexes your data such that queries, selection and processing over the
data can be performed at serving time.

This README describes how to build and develop the Vespa engine. To get started, read the
[quick start](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/vespa-quick-start.html), or find the full
documentation at https://git.corp.yahoo.com/pages/vespa/documentation/.

Code licensed under the Apache 2.0 license. See [LICENSE](LICENSE) for terms.

## Get started developing

### Set up local git config
    git config --global user.name "John Doe"
    git config --global user.email johndoe@host.com

### Set up build environment
C++ building is supported on CentOS 7.

#### Install required build dependencies
    sudo yum -y install epel-release centos-release-scl yum-utils
    sudo yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo
    sudo yum -y --enablerepo=epel-testing install devtoolset-6-gcc-c++ devtoolset-6-libatomic-devel devtoolset-6-binutils \
        Judy-devel cmake3 ccache lz4-devel zlib-devel maven libicu-devel llvm3.9-devel \
        llvm3.9-static java-1.8.0-openjdk-devel openssl-devel rpm-build make \
        vespa-boost-devel vespa-libtorrent-devel vespa-zookeeper-c-client-devel vespa-cppunit-devel
or use the prebuilt docker image

    # TODO: Add docker command

### Build Java modules
Java modules can be built on any environment having Java and Maven:

    sh bootstrap.sh
    mvn install

### Build C++ modules
`<builddir>` should be replaced with the name of the directory in which you'd like to build Vespa. `<sourcedir>` should be replaced with the directory in which you've cloned/unpacked the source tree.

    source /opt/rh/devtoolset-6/enable
    sh bootstrap.sh full
    mkdir <builddir>
    cd <builddir>
    cmake3 -DCMAKE_INSTALL_PREFIX=/opt/vespa \
          -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
          -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm3.9/lib" \
          -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include;/usr/include/llvm3.9" \
          -DCMAKE_INSTALL_RPATH="/opt/vespa/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server;/usr/include/llvm3.9" \
          -DCMAKE_BUILD_RPATH=/opt/vespa/lib64 \
          <sourcedir>
    make
    make test

### Create RPM packages
    sh dist.sh VERSION && rpmbuild -ba ~/rpmbuild/SPECS/vespa-VERSION.spec


## Run Vespa on a local machine
A basic, single-node install if found in the 
[quick start](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/vespa-quick-start.html).
For multi-node and using Node Admin, read [node-admin/README.md](node-admin/README.md).

## Write documentation
See [README-documentation.md](README-documentation.md).
