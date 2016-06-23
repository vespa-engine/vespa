
# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa
Version:        VESPA_VERSION
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.corp.yahoo.com
Source0:        vespa-%{version}.tar.gz

BuildRequires: epel-release 
BuildRequires: centos-release-scl
BuildRequires: devtoolset-4-gcc-c++
BuildRequires: devtoolset-4-libatomic-devel
BuildRequires: Judy-devel
BuildRequires: cmake3
BuildRequires: lz4-devel
BuildRequires: zlib-devel
BuildRequires: maven
BuildRequires: libicu-devel
BuildRequires: llvm-devel
BuildRequires: llvm-static
BuildRequires: java-1.8.0-openjdk-devel
BuildRequires: openssl-devel
BuildRequires: rpm-build
BuildRequires: make
BuildRequires: vespa-boost-devel >= 1.59
BuildRequires: vespa-cppunit-devel >= 1.12.1
BuildRequires: vespa-libtorrent-devel >= 1.0.9
BuildRequires: vespa-zookeeper-c-client-devel >= 3.4.8
Requires: epel-release 
Requires: Judy
Requires: cmake3
Requires: lz4
Requires: zlib
Requires: maven
Requires: libicu
Requires: llvm
Requires: llvm-static
Requires: java-1.8.0-openjdk
Requires: openssl
Requires: rpm-build
Requires: make
Requires: vespa-boost >= 1.59
Requires: vespa-cppunit >= 1.12.1
Requires: vespa-libtorrent >= 1.0.9
Requires: vespa-zookeeper-c-client >= 3.4.8
Requires: numactl
Requires(pre): shadow-utils

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
Provides: libc.so.6(GLIBC_PRIVATE)(64bit)

%description

Vespa - The open big data serving engine

%prep
%setup -q

%build
source /opt/rh/devtoolset-4/enable || true
sh bootstrap.sh
mvn install -DskipTests -Dmaven.javadoc.skip=true
cmake3 -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
       -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm" \
       -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include" \
       -DCMAKE_INSTALL_RPATH="%{_prefix}/lib64;/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server" \
       -DCMAKE_BUILD_RPATH=%{_prefix}/lib64 \
       .

make %{_smp_mflags}

%install
rm -rf $RPM_BUILD_ROOT
make install DESTDIR=%{buildroot}

# BEGIN - Put this in post install script called by make install
# Rewrite config def file names
for path in %{buildroot}/%{_prefix}var/db/vespa/config_server/serverdb/classes/*.def; do
    dir=$(dirname $path)
    filename=$(basename $path)
    namespace=$(grep '^ *namespace *=' $path | sed 's/ *namespace *= *//')
    if [ "$namespace" ]; then
        case $filename in
            $namespace.*)
                ;;
            *)
                mv $path $dir/$namespace.$filename ;;
        esac
    fi
done

mkdir -p %{buildroot}/%{_prefix}/conf/configserver/
mkdir -p %{buildroot}/%{_prefix}/conf/configserver-app/
mkdir -p %{buildroot}/%{_prefix}/conf/configserver-app/config-models/
mkdir -p %{buildroot}/%{_prefix}/conf/configserver-app/components/
mkdir -p %{buildroot}/%{_prefix}/conf/filedistributor/
mkdir -p %{buildroot}/%{_prefix}/conf/node-admin-app/
mkdir -p %{buildroot}/%{_prefix}/conf/node-admin-app/components/
mkdir -p %{buildroot}/%{_prefix}/conf/zookeeper/
mkdir -p %{buildroot}/%{_prefix}/libexec/jdisc_core/
mkdir -p %{buildroot}/%{_prefix}/libexec/vespa/modelplugins/
mkdir -p %{buildroot}/%{_prefix}/libexec/vespa/plugins/qrs/
mkdir -p %{buildroot}/%{_prefix}/libexec/yjava_daemon/bin/
mkdir -p %{buildroot}/%{_prefix}/logs/jdisc_core/
mkdir -p %{buildroot}/%{_prefix}/logs/vespa/
mkdir -p %{buildroot}/%{_prefix}/logs/vespa/
mkdir -p %{buildroot}/%{_prefix}/logs/vespa/configserver/
mkdir -p %{buildroot}/%{_prefix}/logs/vespa/search/
mkdir -p %{buildroot}/%{_prefix}/logs/vespa/qrs/
mkdir -p %{buildroot}/%{_prefix}/share/vespa/
mkdir -p %{buildroot}/%{_prefix}/share/vespa/schema/version/6.x/schema/
mkdir -p %{buildroot}/%{_prefix}/tmp/vespa/
mkdir -p %{buildroot}/%{_prefix}/var/db/jdisc/logcontrol/
mkdir -p %{buildroot}/%{_prefix}/var/db/vespa/
mkdir -p %{buildroot}/%{_prefix}/var/db/vespa/config_server/serverdb/configs/
mkdir -p %{buildroot}/%{_prefix}/var/db/vespa/config_server/serverdb/configs/application/
mkdir -p %{buildroot}/%{_prefix}/var/db/vespa/config_server/serverdb/applications/
mkdir -p %{buildroot}/%{_prefix}/var/db/vespa/logcontrol/
mkdir -p %{buildroot}/%{_prefix}/var/jdisc_container/
mkdir -p %{buildroot}/%{_prefix}/var/jdisc_core/
mkdir -p %{buildroot}/%{_prefix}/var/run/
mkdir -p %{buildroot}/%{_prefix}/var/spool/vespa/
mkdir -p %{buildroot}/%{_prefix}/var/spool/master/inbox/
mkdir -p %{buildroot}/%{_prefix}/var/vespa/bundlecache/
mkdir -p %{buildroot}/%{_prefix}/var/vespa/cache/config/
mkdir -p %{buildroot}/%{_prefix}/var/vespa/cmdlines/
mkdir -p %{buildroot}/%{_prefix}/var/zookeeper/version-2/

ln -s %{_prefix}/lib/jars/config-model-fat.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/config-model-fat.jar
ln -s %{_prefix}/lib/jars/configserver-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/configserver.jar
ln -s %{_prefix}/lib/jars/orchestrator-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/orchestrator.jar
ln -s %{_prefix}/lib/jars/node-repository-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/node-repository.jar
ln -s %{_prefix}/lib/jars/zkfacade-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/zkfacade.jar
ln -s %{_prefix}/conf/configserver-app/components %{buildroot}/%{_prefix}/lib/jars/config-models
ln -s storaged-bin %{buildroot}/%{_prefix}/sbin/distributord-bin
# END - Put this in post install script called by make install

mkdir -p %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa.service %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa-configserver.service %{buildroot}/usr/lib/systemd/system

%clean
rm -rf $RPM_BUILD_ROOT

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d /opt/vespa -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
exit 0

%post

%preun

%postun


%files
%defattr(-,vespa,vespa,-)
%doc
%{_prefix}/*
/usr/lib/systemd/system/vespa.service
/usr/lib/systemd/system/vespa-configserver.service

%changelog
