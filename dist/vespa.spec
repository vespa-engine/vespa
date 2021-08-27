# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Only strip debug info
%global _find_debuginfo_opts -g

# Force special prefix for Vespa
%define _prefix /opt/vespa
%define _vespa_deps_prefix /opt/vespa-deps
%define _vespa_user vespa
%define _vespa_group vespa
%undefine _vespa_user_uid
%define _create_vespa_group 1
%define _create_vespa_user 1
%define _create_vespa_service 1
%define _defattr_is_vespa_vespa 0
%define _command_cmake cmake3

Name:           vespa
Version:        _VESPA_VERSION_
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai
Source0:        vespa-%{version}.tar.gz

%if 0%{?centos} || 0%{?rocky}
BuildRequires: epel-release
%endif
%if 0%{?centos}
%if 0%{?el7} && ! 0%{?amzn2}
BuildRequires: centos-release-scl
%endif
%endif
%if 0%{?el7}
%if 0%{?amzn2}
BuildRequires: gcc10-c++
BuildRequires: libatomic10-devel
BuildRequires: gcc10-binutils
BuildRequires: maven
%define _use_mvn_wrapper 1
%define _java_home /usr/lib/jvm/java-11-amazon-corretto.%{?_arch}
BuildRequires: python3-pytest
%else
BuildRequires: devtoolset-10-gcc-c++
BuildRequires: devtoolset-10-libatomic-devel
BuildRequires: devtoolset-10-binutils
BuildRequires: rh-maven35
%define _devtoolset_enable /opt/rh/devtoolset-10/enable
%define _rhmaven35_enable /opt/rh/rh-maven35/enable
BuildRequires: python36-pytest
%endif
BuildRequires: vespa-pybind11-devel
BuildRequires: python3-devel
%endif
%if 0%{?el8}
BuildRequires: gcc-toolset-10-gcc-c++
BuildRequires: gcc-toolset-10-binutils
%define _devtoolset_enable /opt/rh/gcc-toolset-10/enable
BuildRequires: maven
BuildRequires: pybind11-devel
BuildRequires: python3-pytest
BuildRequires: python36-devel
BuildRequires: glibc-langpack-en
%endif
%if 0%{?fedora}
BuildRequires: gcc-c++
BuildRequires: libatomic
BuildRequires: pybind11-devel
BuildRequires: python3-pytest
BuildRequires: python3-devel
BuildRequires: glibc-langpack-en
%endif
%if 0%{?el7}
BuildRequires: cmake3
BuildRequires: llvm7.0-devel
BuildRequires: vespa-boost-devel >= 1.76.0-1
BuildRequires: vespa-gtest >= 1.8.1-1
BuildRequires: vespa-icu-devel >= 65.1.0-1
BuildRequires: vespa-lz4-devel >= 1.9.2-2
BuildRequires: vespa-onnxruntime-devel = 1.7.1
BuildRequires: vespa-openssl-devel >= 1.1.1k-1
BuildRequires: vespa-protobuf-devel = 3.17.3
BuildRequires: vespa-libzstd-devel >= 1.4.5-2
%endif
%if 0%{?el8}
BuildRequires: cmake >= 3.11.4-3
%if 0%{?centos} || 0%{?rocky}
%if 0%{?centos}
# Current cmake on CentOS 8 is broken and manually requires libarchive install
BuildRequires: libarchive
%endif
%define _command_cmake cmake
%global _centos_stream %(grep -qs '^NAME="CentOS Stream"' /etc/os-release && echo 1 || echo 0)
%if 0%{?_centos_stream}
BuildRequires: (llvm-devel >= 12.0.0 and llvm-devel < 13)
%else
BuildRequires: (llvm-devel >= 11.0.0 and llvm-devel < 12)
%endif
%else
BuildRequires: (llvm-devel >= 10.0.1 and llvm-devel < 11)
%endif
BuildRequires: vespa-boost-devel >= 1.76.0-1
BuildRequires: openssl-devel
BuildRequires: vespa-gtest >= 1.8.1-1
BuildRequires: vespa-lz4-devel >= 1.9.2-2
BuildRequires: vespa-onnxruntime-devel = 1.7.1
BuildRequires: vespa-protobuf-devel = 3.17.3
BuildRequires: vespa-libzstd-devel >= 1.4.5-2
%endif
%if 0%{?fedora}
BuildRequires: cmake >= 3.9.1
BuildRequires: maven
BuildRequires: openssl-devel
BuildRequires: vespa-lz4-devel >= 1.9.2-2
BuildRequires: vespa-onnxruntime-devel = 1.7.1
BuildRequires: vespa-libzstd-devel >= 1.4.5-2
%if 0%{?fc32}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 10.0.0
BuildRequires: boost-devel >= 1.69
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc33}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 11.0.0
BuildRequires: boost-devel >= 1.73
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc34}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 12.0.0
BuildRequires: boost-devel >= 1.75
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc35}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 13.0.0
BuildRequires: boost-devel >= 1.76
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc36}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 13.0.0
BuildRequires: boost-devel >= 1.76
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%endif
%if 0%{?el7} && 0%{?amzn2}
BuildRequires: vespa-xxhash-devel = 0.8.0
%define _use_vespa_xxhash 1
BuildRequires: vespa-openblas-devel = 0.3.17
%define _use_vespa_openblas 1
BuildRequires: vespa-re2-devel = 20210801
%define _use_vespa_re2 1
%else
BuildRequires: xxhash-devel >= 0.8.0
%if 0%{?el7} || 0%{?el8}
BuildRequires: vespa-openblas-devel = 0.3.17
%define _use_vespa_openblas 1
%else
BuildRequires: openblas-devel
%endif
BuildRequires: re2-devel
%endif
BuildRequires: zlib-devel
%if ! 0%{?el7}
BuildRequires: libicu-devel
%endif
%if 0%{?el7} && 0%{?amzn2}
BuildRequires: java-11-amazon-corretto
%else
BuildRequires: java-11-openjdk-devel
%endif
BuildRequires: rpm-build
BuildRequires: make
BuildRequires: git
BuildRequires: systemd
BuildRequires: flex >= 2.5.0
BuildRequires: bison >= 3.0.0
Requires: which
Requires: initscripts
Requires: libcgroup-tools
Requires: numactl
Requires: perl
Requires: perl-Carp
Requires: perl-Data-Dumper
Requires: perl-Digest-MD5
Requires: perl-Env
Requires: perl-Exporter
Requires: perl-File-Path
Requires: perl-File-Temp
Requires: perl-Getopt-Long
Requires: perl-IO-Socket-IP
Requires: perl-JSON
Requires: perl-libwww-perl
Requires: perl-LWP-Protocol-https
Requires: perl-Net-INET6Glue
Requires: perl-Pod-Usage
Requires: perl-URI
%if ! 0%{?el7}
Requires: valgrind
%endif
%if 0%{?el7} && 0%{?amzn2}
Requires: vespa-xxhash = 0.8.0
%else
Requires: xxhash
Requires: xxhash-libs >= 0.8.0
%endif
Requires: zlib
Requires: perf
Requires: gdb
Requires: nc
Requires: nghttp2
Requires: net-tools
Requires: unzip
Requires: zstd
%if 0%{?el7}
Requires: llvm7.0
%if ! 0%{?amzn2}
Requires: vespa-telegraf >= 1.1.1-1
Requires: vespa-valgrind >= 3.17.0-1
%endif
%define _vespa_llvm_version 7
%define _extra_link_directory /usr/lib64/llvm7.0/lib;%{_vespa_deps_prefix}/lib64
%define _extra_include_directory /usr/include/llvm7.0;%{_vespa_deps_prefix}/include
%endif
%if 0%{?el8}
%if 0%{?centos} || 0%{?rocky}
%if 0%{?_centos_stream}
%define _vespa_llvm_version 12
%else
%define _vespa_llvm_version 11
%endif
%else
%define _vespa_llvm_version 10
%endif
%define _extra_link_directory %{_vespa_deps_prefix}/lib64
%define _extra_include_directory %{_vespa_deps_prefix}/include
%endif
%if 0%{?fedora}
%if 0%{?fc32}
%define _vespa_llvm_version 10
%endif
%if 0%{?fc33}
%define _vespa_llvm_version 11
%endif
%if 0%{?fc34}
%define _vespa_llvm_version 12
%endif
%if 0%{?fc35}
%define _vespa_llvm_version 13
%endif
%if 0%{?fc36}
%define _vespa_llvm_version 13
%endif
%define _extra_link_directory %{_vespa_deps_prefix}/lib64
%define _extra_include_directory %{_vespa_deps_prefix}/include;/usr/include/openblas
%endif
%ifnarch x86_64
%define _skip_vespamalloc 1
%endif
Requires: %{name}-base = %{version}-%{release}
Requires: %{name}-libs = %{version}-%{release}
Requires: %{name}-clients = %{version}-%{release}
Requires: %{name}-config-model-fat = %{version}-%{release}
Requires: %{name}-jars = %{version}-%{release}
%if ! 0%{?_skip_vespamalloc:1}
Requires: %{name}-malloc = %{version}-%{release}
%endif
Requires: %{name}-tools = %{version}-%{release}

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function. Exclude automated reqires for libraries in /opt/vespa-deps/lib64.
%global __requires_exclude ^lib(c\\.so\\.6\\(GLIBC_PRIVATE\\)|pthread\\.so\\.0\\(GLIBC_PRIVATE\\)|(crypto|icui18n|icuuc|lz4|protobuf|ssl|zstd|onnxruntime%{?_use_vespa_openblas:|openblas}%{?_use_vespa_re2:|re2}%{?_use_vespa_xxhash:|xxhash})\\.so\\.[0-9.]*\\([A-Z._0-9]*\\))\\(64bit\\)$


%description

Vespa - The open big data serving engine

%package base

Summary: Vespa - The open big data serving engine - base

%if 0%{?el7} && 0%{?amzn2}
Requires: java-11-amazon-corretto
%else
Requires: java-11-openjdk-devel
%endif
Requires: perl
Requires: perl-Getopt-Long
Requires(pre): shadow-utils

%description base

Vespa - The open big data serving engine - base

%package base-libs

Summary: Vespa - The open big data serving engine - base C++ libraries

%if 0%{?centos} || 0%{?rocky}
Requires: epel-release
%endif
%if 0%{?amzn2}
Requires: vespa-xxhash = 0.8.0
%else
Requires: xxhash-libs >= 0.8.0
%endif
%if 0%{?el7}
Requires: vespa-openssl >= 1.1.1k-1
%else
Requires: openssl-libs
%endif
Requires: vespa-lz4 >= 1.9.2-2
Requires: vespa-libzstd >= 1.4.5-2
%if 0%{?el8} || 0%{?el7}
Requires: vespa-openblas = 0.3.17
%else
Requires: openblas-serial
%endif
%if 0%{?amzn2}
Requires: vespa-re2 = 20210801
%else
Requires: re2
%endif
%if 0%{?fedora} || 0%{?el8}
Requires: glibc-langpack-en
%endif

%description base-libs

Vespa - The open big data serving engine - base C++ libraries

%package libs

Summary: Vespa - The open big data serving engine - C++ libraries

Requires: %{name}-base-libs = %{version}-%{release}
%if 0%{?el7}
Requires: llvm7.0-libs
Requires: vespa-icu >= 65.1.0-1
Requires: vespa-openssl >= 1.1.1k-1
Requires: vespa-protobuf = 3.17.3
%else
Requires: libicu
Requires: openssl-libs
%endif
%if 0%{?el8}
%if 0%{?centos} || 0%{?rocky}
%if 0%{?_centos_stream}
Requires: (llvm-libs >= 12.0.0 and llvm-libs < 13)
%else
Requires: (llvm-libs >= 11.0.0 and llvm-libs < 12)
%endif
%else
Requires: (llvm-libs >= 10.0.1 and llvm-libs < 11)
%endif
Requires: vespa-protobuf = 3.17.3
%endif
%if 0%{?fedora}
Requires: protobuf
%if 0%{?fc32}
Requires: llvm-libs >= 10.0.0
%endif
%if 0%{?fc33}
Requires: llvm-libs >= 11.0.0
%endif
%if 0%{?fc34}
Requires: llvm-libs >= 12.0.0
%endif
%if 0%{?fc35}
Requires: llvm-libs >= 13.0.0
%endif
%if 0%{?fc36}
Requires: llvm-libs >= 13.0.0
%endif
%endif
Requires: vespa-onnxruntime = 1.7.1

%description libs

Vespa - The open big data serving engine - C++ libraries

%package clients

Summary: Vespa - The open big data serving engine - clients

%description clients

Vespa - The open big data serving engine - clients

%package config-model-fat

Summary: Vespa - The open big data serving engine - config models

%description config-model-fat

Vespa - The open big data serving engine - config models

%package node-admin

Summary: Vespa - The open big data serving engine - node-admin

Requires: %{name}-base = %{version}-%{release}
Requires: %{name}-jars = %{version}-%{release}

%description node-admin

Vespa - The open big data serving engine - node-admin

%package jars

Summary: Vespa - The open big data serving engine - shared java jar files

%description jars

Vespa - The open big data serving engine - shared java jar files

%if ! 0%{?_skip_vespamalloc:1}
%package malloc

Summary: Vespa - The open big data serving engine - malloc library

%description malloc

Vespa - The open big data serving engine - malloc library
%endif

%package tools

Summary: Vespa - The open big data serving engine - tools

Requires: %{name}-base = %{version}-%{release}
Requires: %{name}-base-libs = %{version}-%{release}

%description tools

Vespa - The open big data serving engine - tools

%package ann-benchmark

Summary: Vespa - The open big data serving engine - ann-benchmark

Requires: %{name}-libs = %{version}-%{release}
%if 0%{?el7}
Requires: python3
%endif
%if 0%{?el8}
Requires: python36
%endif
%if 0%{?fedora}
Requires: python3
%endif

%description ann-benchmark

Vespa - The open big data serving engine - ann-benchmark

Python binding for the Vespa implementation of an HNSW index for
nearest neighbor search used for low-level benchmarking.

%prep
%if 0%{?installdir:1}
%setup -c -D -T
%else
%setup -q
%endif

%build
%if ! 0%{?installdir:1}
%if 0%{?_devtoolset_enable:1}
source %{_devtoolset_enable} || true
%endif
%if 0%{?_rhmaven35_enable:1}
source %{_rhmaven35_enable} || true
%endif

%if 0%{?_java_home:1}
export JAVA_HOME=%{?_java_home}
%else
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
%endif
export PATH="$JAVA_HOME/bin:$PATH"
export FACTORY_VESPA_VERSION=%{version}

%if 0%{?_use_mvn_wrapper}
mvn --batch-mode -e -N io.takari:maven:wrapper -Dmaven=3.6.3
%endif
%{?_use_mvn_wrapper:env VESPA_MAVEN_COMMAND=$(pwd)/mvnw }sh bootstrap.sh java
%{?_use_mvn_wrapper:./mvnw}%{!?_use_mvn_wrapper:mvn} --batch-mode -nsu -T 1C  install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
%{_command_cmake} -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=$JAVA_HOME \
       -DCMAKE_PREFIX_PATH=%{_vespa_deps_prefix} \
       -DEXTRA_LINK_DIRECTORY="%{_extra_link_directory}" \
       -DEXTRA_INCLUDE_DIRECTORY="%{_extra_include_directory}" \
       -DCMAKE_INSTALL_RPATH="%{_prefix}/lib64%{?_extra_link_directory:;%{_extra_link_directory}}" \
       %{?_vespa_llvm_version:-DVESPA_LLVM_VERSION="%{_vespa_llvm_version}"} \
       -DVESPA_USER=%{_vespa_user} \
       -DVESPA_UNPRIVILEGED=no \
       .

make %{_smp_mflags}
%endif

%install
rm -rf %{buildroot}

%if 0%{?installdir:1}
cp -r %{installdir} %{buildroot}
%else
make install DESTDIR=%{buildroot}
%endif

%if %{_create_vespa_service}
mkdir -p %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa.service %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa-configserver.service %{buildroot}/usr/lib/systemd/system
%endif

ln -s /usr/lib/jvm/jre-11-openjdk %{buildroot}/%{_prefix}/jdk

%clean
rm -rf $RPM_BUILD_ROOT

%pre base
%if %{_create_vespa_group}
getent group %{_vespa_group} >/dev/null || groupadd -r %{_vespa_group}
%endif
%if %{_create_vespa_user}
getent passwd %{_vespa_user} >/dev/null || \
    useradd -r %{?_vespa_user_uid:-u %{_vespa_user_uid}} -g %{_vespa_group} --home-dir %{_prefix} -s /sbin/nologin \
    -c "Create owner of all Vespa data files" %{_vespa_user}
%endif
echo "pathmunge %{_prefix}/bin" > /etc/profile.d/vespa.sh
echo "export VESPA_HOME=%{_prefix}" >> /etc/profile.d/vespa.sh
exit 0

%if %{_create_vespa_service}
%post
%systemd_post vespa-configserver.service
%systemd_post vespa.service
%endif

%if %{_create_vespa_service}
%preun
%systemd_preun vespa.service
%systemd_preun vespa-configserver.service
%endif

%if %{_create_vespa_service}
%postun
%systemd_postun_with_restart vespa.service
%systemd_postun_with_restart vespa-configserver.service
%endif

%postun base
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
%if %{_create_vespa_user}
    ! getent passwd %{_vespa_user} >/dev/null || userdel %{_vespa_user}
%endif
%if %{_create_vespa_group}
    ! getent group %{_vespa_group} >/dev/null || groupdel %{_vespa_group}
%endif
fi
# Keep modifications to conf/vespa/default-env.txt across
# package uninstall + install.
if test -f %{_prefix}/conf/vespa/default-env.txt.rpmsave
then
    if test -f %{_prefix}/conf/vespa/default-env.txt
    then
	# Temporarily remove default-env.txt.rpmsave when
	# default-env.txt exists
	rm -f %{_prefix}/conf/vespa/default-env.txt.rpmsave
    else
	mv %{_prefix}/conf/vespa/default-env.txt.rpmsave %{_prefix}/conf/vespa/default-env.txt
    fi
fi

%files
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%doc
%dir %{_prefix}
%{_prefix}/bin
%exclude %{_prefix}/bin/vespa-destination
%exclude %{_prefix}/bin/vespa-document-statistics
%exclude %{_prefix}/bin/vespa-fbench
%exclude %{_prefix}/bin/vespa-feeder
%exclude %{_prefix}/bin/vespa-get
%exclude %{_prefix}/bin/vespa-logfmt
%exclude %{_prefix}/bin/vespa-query-profile-dump-tool
%exclude %{_prefix}/bin/vespa-stat
%exclude %{_prefix}/bin/vespa-security-env
%exclude %{_prefix}/bin/vespa-summary-benchmark
%exclude %{_prefix}/bin/vespa-visit
%exclude %{_prefix}/bin/vespa-visit-target
%dir %{_prefix}/conf
%{_prefix}/conf/configserver
%{_prefix}/conf/configserver-app
%exclude %{_prefix}/conf/configserver-app/components/config-model-fat.jar
%exclude %{_prefix}/conf/configserver-app/config-models.xml
%dir %{_prefix}/conf/logd
%dir %{_prefix}/conf/vespa
%dir %attr(-,%{_vespa_user},-) %{_prefix}/conf/zookeeper
%dir %{_prefix}/etc
%{_prefix}/etc/systemd
%{_prefix}/etc/vespa
%if ! 0%{?_skip_vespamalloc:1}
%exclude %{_prefix}/etc/vespamalloc.conf
%endif
%{_prefix}/include
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/application-model-jar-with-dependencies.jar
%{_prefix}/lib/jars/application-preprocessor-jar-with-dependencies.jar
%{_prefix}/lib/jars/athenz-identity-provider-service-jar-with-dependencies.jar
%{_prefix}/lib/jars/cloud-tenant-cd-jar-with-dependencies.jar
%{_prefix}/lib/jars/clustercontroller-apps-jar-with-dependencies.jar
%{_prefix}/lib/jars/clustercontroller-core-jar-with-dependencies.jar
%{_prefix}/lib/jars/clustercontroller-reindexer-jar-with-dependencies.jar
%{_prefix}/lib/jars/clustercontroller-utils-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-models
%{_prefix}/lib/jars/config-proxy-jar-with-dependencies.jar
%{_prefix}/lib/jars/configserver-flags-jar-with-dependencies.jar
%{_prefix}/lib/jars/configserver-jar-with-dependencies.jar
%{_prefix}/lib/jars/document.jar
%{_prefix}/lib/jars/filedistribution-jar-with-dependencies.jar
%{_prefix}/lib/jars/jdisc_jetty.jar
%{_prefix}/lib/jars/logserver-jar-with-dependencies.jar
%{_prefix}/lib/jars/metrics-proxy-jar-with-dependencies.jar
%{_prefix}/lib/jars/node-repository-jar-with-dependencies.jar
%{_prefix}/lib/jars/orchestrator-jar-with-dependencies.jar
%{_prefix}/lib/jars/predicate-search-jar-with-dependencies.jar
%{_prefix}/lib/jars/searchlib.jar
%{_prefix}/lib/jars/searchlib-jar-with-dependencies.jar
%{_prefix}/lib/jars/service-monitor-jar-with-dependencies.jar
%{_prefix}/lib/jars/tenant-cd-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa_feed_perf-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa-osgi-testrunner-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa-testrunner-components.jar
%{_prefix}/lib/jars/vespa-testrunner-components-jar-with-dependencies.jar
%{_prefix}/lib/jars/zookeeper-command-line-client-jar-with-dependencies.jar
%{_prefix}/lib/perl5
%{_prefix}/libexec
%exclude %{_prefix}/libexec/vespa_ann_benchmark
%exclude %{_prefix}/libexec/vespa/common-env.sh
%exclude %{_prefix}/libexec/vespa/node-admin.sh
%exclude %{_prefix}/libexec/vespa/standalone-container.sh
%exclude %{_prefix}/libexec/vespa/vespa-curl-wrapper
%dir %attr(1777,-,-) %{_prefix}/logs
%dir %attr(1777,%{_vespa_user},-) %{_prefix}/logs/vespa
%dir %attr(-,%{_vespa_user},-) %{_prefix}/logs/vespa/configserver
%dir %attr(-,%{_vespa_user},-) %{_prefix}/logs/vespa/node-admin
%dir %attr(-,%{_vespa_user},-) %{_prefix}/logs/vespa/search
%{_prefix}/man
%{_prefix}/sbin
%{_prefix}/share
%dir %attr(1777,-,-) %{_prefix}/tmp
%dir %attr(1777,%{_vespa_user},-) %{_prefix}/tmp/vespa
%dir %{_prefix}/var
%dir %{_prefix}/var/db
%dir %attr(-,%{_vespa_user},-) %{_prefix}/var/db/vespa
%dir %attr(-,%{_vespa_user},-) %{_prefix}/var/db/vespa/logcontrol
%dir %attr(-,%{_vespa_user},-) %{_prefix}/var/zookeeper
%config(noreplace) %{_prefix}/conf/logd/logd.cfg
%if %{_create_vespa_service}
%attr(644,root,root) /usr/lib/systemd/system/vespa.service
%attr(644,root,root) /usr/lib/systemd/system/vespa-configserver.service
%endif

%files base
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/bin
%{_prefix}/bin/vespa-jvm-dumper
%{_prefix}/bin/vespa-logfmt
%{_prefix}/bin/vespa-security-env
%dir %{_prefix}/conf
%dir %{_prefix}/conf/vespa
%config(noreplace) %{_prefix}/conf/vespa/default-env.txt
%{_prefix}/jdk
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/security-tools-jar-with-dependencies.jar
%dir %{_prefix}/libexec
%dir %{_prefix}/libexec/vespa
%{_prefix}/libexec/vespa/common-env.sh
%{_prefix}/libexec/vespa/vespa-curl-wrapper

%files base-libs
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/lib64
%{_prefix}/lib64/libfastos.so
%{_prefix}/lib64/libfnet.so
%{_prefix}/lib64/libstaging_vespalib.so
%{_prefix}/lib64/libvespadefaults.so
%{_prefix}/lib64/libvespalib.so
%{_prefix}/lib64/libvespalog.so

%files libs
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%{_prefix}/lib64
%exclude %{_prefix}/lib64/libfastos.so
%exclude %{_prefix}/lib64/libfnet.so
%exclude %{_prefix}/lib64/libstaging_vespalib.so
%exclude %{_prefix}/lib64/libvespadefaults.so
%exclude %{_prefix}/lib64/libvespalib.so
%exclude %{_prefix}/lib64/libvespalog.so
%if ! 0%{?_skip_vespamalloc:1}
%exclude %{_prefix}/lib64/vespa
%endif

%files clients
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/conf
%dir %{_prefix}/conf/vespa-feed-client
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/bin/vespa-feed-client
%{_prefix}/conf/vespa-feed-client/logging.properties
%{_prefix}/lib/jars/vespa-http-client-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa-feed-client-cli-jar-with-dependencies.jar

%files config-model-fat
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/conf
%dir %{_prefix}/conf/configserver-app
%dir %{_prefix}/conf/configserver-app/components
%{_prefix}/conf/configserver-app/components/config-model-fat.jar
%{_prefix}/conf/configserver-app/config-models.xml
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/config-model-fat.jar

%files node-admin
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/conf
%{_prefix}/conf/node-admin-app
%dir %{_prefix}/libexec
%dir %{_prefix}/libexec/vespa
%{_prefix}/libexec/vespa/node-admin.sh

%files jars
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/asm-*.jar
%{_prefix}/lib/jars/aopalliance-repackaged-*.jar
%{_prefix}/lib/jars/bcpkix-jdk15on-*.jar
%{_prefix}/lib/jars/bcprov-jdk15on-*.jar
%{_prefix}/lib/jars/config-bundle-jar-with-dependencies.jar
%{_prefix}/lib/jars/configdefinitions-jar-with-dependencies.jar
%{_prefix}/lib/jars/configgen.jar
%{_prefix}/lib/jars/config-model-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-model-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-provisioning-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-apache-http-client-bundle-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-disc-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-search-and-docproc-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-search-gui-jar-with-dependencies.jar
%{_prefix}/lib/jars/defaults-jar-with-dependencies.jar
%{_prefix}/lib/jars/docprocs-jar-with-dependencies.jar
%{_prefix}/lib/jars/flags-jar-with-dependencies.jar
%{_prefix}/lib/jars/hk2-*.jar
%{_prefix}/lib/jars/hosted-zone-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/jackson-*.jar
%{_prefix}/lib/jars/javassist-*.jar
%{_prefix}/lib/jars/javax.*.jar
%{_prefix}/lib/jars/jdisc-cloud-aws-jar-with-dependencies.jar
%{_prefix}/lib/jars/jdisc_core-jar-with-dependencies.jar
%{_prefix}/lib/jars/jdisc-security-filters-jar-with-dependencies.jar
%{_prefix}/lib/jars/jersey-*.jar
%{_prefix}/lib/jars/alpn-*.jar
%{_prefix}/lib/jars/http2-*.jar
%{_prefix}/lib/jars/jetty-*.jar
%{_prefix}/lib/jars/mimepull-*.jar
%{_prefix}/lib/jars/model-evaluation-jar-with-dependencies.jar
%{_prefix}/lib/jars/model-integration-jar-with-dependencies.jar
%{_prefix}/lib/jars/org.apache.aries.spifly.dynamic.bundle-*.jar
%{_prefix}/lib/jars/osgi-resource-locator-*.jar
%{_prefix}/lib/jars/security-utils-jar-with-dependencies.jar
%{_prefix}/lib/jars/standalone-container-jar-with-dependencies.jar
%{_prefix}/lib/jars/validation-api-*.jar
%{_prefix}/lib/jars/vespa-athenz-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespaclient-container-plugin-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespajlib.jar
%{_prefix}/lib/jars/zkfacade-jar-with-dependencies.jar
%{_prefix}/lib/jars/zookeeper-server-*-jar-with-dependencies.jar
%{_prefix}/lib/jars/zookeeper-server-common-jar-with-dependencies.jar
%{_prefix}/lib/jars/zookeeper-server-jar-with-dependencies.jar
%dir %{_prefix}/libexec
%dir %{_prefix}/libexec/vespa
%{_prefix}/libexec/vespa/standalone-container.sh

%if ! 0%{?_skip_vespamalloc:1}
%files malloc
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/etc
%config(noreplace) %{_prefix}/etc/vespamalloc.conf
%dir %{_prefix}/lib64
%{_prefix}/lib64/vespa
%endif

%files tools
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/bin
%{_prefix}/bin/vespa-destination
%{_prefix}/bin/vespa-document-statistics
%{_prefix}/bin/vespa-fbench
%{_prefix}/bin/vespa-feeder
%{_prefix}/bin/vespa-get
%{_prefix}/bin/vespa-query-profile-dump-tool
%{_prefix}/bin/vespa-stat
%{_prefix}/bin/vespa-summary-benchmark
%{_prefix}/bin/vespa-visit
%{_prefix}/bin/vespa-visit-target
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/vespaclient-java-jar-with-dependencies.jar

%files ann-benchmark
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/libexec
%{_prefix}/libexec/vespa_ann_benchmark

%changelog
