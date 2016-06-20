
# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa
Version:        VESPA_VERSION
Release:        1%{?dist}
Summary:        Vespa

Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.corp.yahoo.com
Source0:        vespa-%{version}.tar.gz


#BuildRequires: vespa-boost-devel >= 1.59
#BuildRequires: vespa-cppunit-devel >= 1.12.1
#BuildRequires: vespa-libtorrent-devel >= 1.0.9
#BuildRequires: vespa-zookeeper-c-client-devel >= 3.4.8
#BuildRequires: cmake3 >= 3.5 
#BuildRequires: epel-release 
#BuildRequires: centos-release-scl
#BuildRequires: devtoolset-4 >= 4.0
#BuildRequires: devtoolset-4-libatomic-devel
#BuildRequires: Judy-devel >= 1.0.5
#BuildRequires: lz4-devel >= r131
#BuildRequires: maven >= 3.0
#BuildRequires: libicu-devel >= 50.1.2
#BuildRequires: llvm-devel >= 3.4.2
#BuildRequires: llvm-static >= 3.4.2
#Requires:  vespa-boost
#Requires:  vespa-cppunit
#Requires:  vespa-libtorrent
#Requires:  vespa-zookeeper-c-client
#Requires:  numactl
Requires(pre): shadow-utils

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
Provides: libc.so.6(GLIBC_PRIVATE)(64bit)

%description

This is the Vespa!

%prep
%setup -q

%build

source /opt/rh/devtoolset-4/enable || true
sh bootstrap.sh
cmake3 -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
       -DEXTRA_LINK_DIRECTORY="/opt/vespa-boost/lib;/opt/vespa-libtorrent/lib;/opt/vespa-zookeeper-c-client/lib;/opt/vespa-cppunit/lib;/usr/lib64/llvm" \
       -DEXTRA_INCLUDE_DIRECTORY="/opt/vespa-boost/include;/opt/vespa-libtorrent/include;/opt/vespa-zookeeper-c-client/include;/opt/vespa-cppunit/include" \
       -DCMAKE_INSTALL_RPATH=%{_prefix}/lib64 \
       -DCMAKE_BUILD_RPATH=%{_prefix}/lib64 \
       .

make %{_smp_mflags}
mvn install -DskipTests -Dmaven.javadoc.skip=true

%install

rm -rf $RPM_BUILD_ROOT
make install DESTDIR=%{buildroot}

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
mkdir -p %{buildroot}/%{_prefix}/var/zookeeper/

ln -s %{_prefix}/lib/jars/config-model-fat.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/config-model-fat.jar
ln -s %{_prefix}/lib/jars/configserver-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/configserver.jar
ln -s %{_prefix}/lib/jars/orchestrator-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/orchestrator.jar
ln -s %{_prefix}/lib/jars/node-repository-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/node-repository.jar
ln -s %{_prefix}/lib/jars/zkfacade-jar-with-dependencies.jar %{buildroot}/%{_prefix}/conf/configserver-app/components/zkfacade.jar
ln -s %{_prefix}/conf/configserver-app/components %{buildroot}/%{_prefix}/lib/jars/config-models

%clean

rm -rf $RPM_BUILD_ROOT

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d /opt/vespa -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
exit 0


%files
%defattr(-,root,root,-)
%doc

%dir %attr( 755, vespa, vespa) %{_prefix}/conf/configserver/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/configserver-app/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/configserver-app/config-models/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/configserver-app/components/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/filedistributor/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/node-admin-app/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/node-admin-app/components/
%dir %attr( 755, vespa, vespa) %{_prefix}/conf/zookeeper/
%dir %attr( 777,     -,     -) %{_prefix}/libexec/jdisc_core/
%dir %attr( 775, vespa, vespa) %{_prefix}/libexec/vespa/modelplugins/
%dir %attr( 755, vespa, vespa) %{_prefix}/libexec/vespa/plugins/qrs/
%dir %attr( 755, vespa, vespa) %{_prefix}/libexec/yjava_daemon/bin/
%dir %attr( 777, vespa, vespa) %{_prefix}/logs/jdisc_core/
%dir %attr(1777, vespa, vespa) %{_prefix}/logs/vespa/
%dir %attr(1777, vespa, vespa) %{_prefix}/logs/vespa/
%dir %attr( 755, vespa, vespa) %{_prefix}/logs/vespa/configserver/
%dir %attr( 755, vespa, vespa) %{_prefix}/logs/vespa/search/
%dir %attr( 755, vespa, vespa) %{_prefix}/logs/vespa/qrs/
%dir %attr( 755, vespa, vespa) %{_prefix}/share/vespa/
%dir %attr( 755, vespa, vespa) %{_prefix}/share/vespa/schema/version/6.x/schema/
%dir %attr(1777, vespa, vespa) %{_prefix}/tmp/vespa/
%dir %attr( 777, vespa, vespa) %{_prefix}/var/db/jdisc/logcontrol/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/db/vespa/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/db/vespa/config_server/serverdb/configs/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/db/vespa/config_server/serverdb/configs/application/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/db/vespa/config_server/serverdb/applications/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/db/vespa/logcontrol/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/jdisc_container/
%dir %attr( 777,     -,     -) %{_prefix}/var/jdisc_core/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/run/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/spool/vespa/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/spool/master/inbox/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/vespa/bundlecache/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/vespa/cache/config/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/vespa/cmdlines/
%dir %attr( 755, vespa, vespa) %{_prefix}/var/zookeeper/

%{_prefix}/libexec/vespa/vespa-config.pl
%{_prefix}/libexec/vespa/common-env.sh
%{_prefix}/libexec/vespa/start-vespa-base.sh
%{_prefix}/libexec/vespa/stop-vespa-base.sh
%{_prefix}/libexec/vespa/start-filedistribution
%{_prefix}/libexec/vespa/ping-configserver
%{_prefix}/libexec/vespa/start-configserver
%{_prefix}/libexec/vespa/start-logd
%{_prefix}/libexec/vespa/stop-configserver
%{_prefix}/var/db/vespa/config_server/serverdb/classes/*.def
%{_prefix}/lib/jars/*
%{_prefix}/lib/perl5/site_perl/Yahoo/Vespa/*.pm
%{_prefix}/lib64/*.so
%{_prefix}/bin/*
%{_prefix}/sbin/*
%{_prefix}/man/*
%{_prefix}/include/*
%{_prefix}/etc/*
%{_prefix}/conf/*

%changelog
