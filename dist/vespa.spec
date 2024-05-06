# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Only strip debug info
%global _find_debuginfo_opts -g

# Don't enable LTO
%global _lto_cflags %{nil}

# Disable hardened package build.
%global _preprocessor_defines %{nil}
%undefine _hardened_build

# Libraries and binaries use shared libraries in /opt/vespa/lib64 and
# /opt/vespa-deps/lib64
%global __brp_check_rpaths %{nil}

# Go binaries' build-ids are not recognized by RPMs yet, see
# https://github.com/rpm-software-management/rpm/issues/367 and
# https://github.com/tpokorra/lbs-mono-fedora/issues/3#issuecomment-219857688.
%undefine _missing_build_ids_terminate_build

# Force special prefix for Vespa
%define _prefix /opt/vespa
%define _vespa_user vespa
%define _vespa_group vespa
%undefine _vespa_user_uid
%define _create_vespa_group 1
%define _create_vespa_user 1
%define _create_vespa_service 1
%define _defattr_is_vespa_vespa 0
%define _command_cmake cmake3
%global _vespa_abseil_cpp_version 20240116.1
%global _vespa_build_depencencies_version 1.3.2
%global _vespa_gtest_version 1.14.0
%global _vespa_protobuf_version 5.26.1
%global _vespa_openblas_version 0.3.27
%global _use_vespa_abseil_cpp 1
%global _use_vespa_protobuf 1
%global _use_vespa_openblas 1

Name:           vespa
Version:        _VESPA_VERSION_
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai
Source0:        vespa-%{version}.tar.gz

BuildRequires: vespa-build-dependencies = %{_vespa_build_depencencies_version}

Requires: %{name}-base             = %{version}-%{release}
Requires: %{name}-base-libs        = %{version}-%{release}
Requires: %{name}-config-model-fat = %{version}-%{release}
Requires: %{name}-clients          = %{version}-%{release}
Requires: %{name}-jars             = %{version}-%{release}
Requires: %{name}-libs             = %{version}-%{release}
Requires: %{name}-malloc           = %{version}-%{release}
Requires: %{name}-tools            = %{version}-%{release}

Requires: gdb
Requires: initscripts
Requires: hostname
Requires: libedit
Requires: nc
Requires: net-tools
Requires: nghttp2
Requires: numactl
Requires: which
Requires: unzip
Requires: zlib
Requires: zstd

%if 0%{?el8}
%global _centos_stream %(grep -qs '^NAME="CentOS Stream"' /etc/os-release && echo 1 || echo 0)
%define _devtoolset_enable /opt/rh/gcc-toolset/enable

%define _use_vespa_gtest 1
%define _use_vespa_openssl 1

%if 0%{?centos} || 0%{?rocky} || 0%{?oraclelinux}
%define _command_cmake cmake
%endif

Requires: vespa-gtest = %{_vespa_gtest_version}

%endif

%if 0%{?el9}
%global _centos_stream %(grep -qs '^NAME="CentOS Stream"' /etc/os-release && echo 1 || echo 0)
%define _devtoolset_enable /opt/rh/gcc-toolset/enable

%define _use_vespa_gtest 1

Requires: vespa-gtest = %{_vespa_gtest_version}
%endif

%if 0%{?amzn2023}
%define _java_home /usr/lib/jvm/java-17-amazon-corretto
%define _use_vespa_gtest 1
%define _use_vespa_re2 1
%define _use_vespa_xxhash 1

Requires: vespa-gtest = %{_vespa_gtest_version}
Requires: vespa-xxhash >= 0.8.1
%endif

%if 0%{?fedora} && !0%{?amzn2023}
Requires: gtest
%endif

%if ! 0%{?el9}
Requires: libcgroup-tools
%endif

%if ! 0%{?amzn2023}
Requires: xxhash-libs >= 0.8.1
%endif

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
# Exclude automated requires for libraries in /opt/vespa-deps/lib64.
%global __requires_exclude ^lib(c\\.so\\.6\\(GLIBC_PRIVATE\\)|pthread\\.so\\.0\\(GLIBC_PRIVATE\\)|(lz4%{?_use_vespa_protobuf:|protobuf}|zstd|onnxruntime%{?_use_vespa_openssl:|crypto|ssl}%{?_use_vespa_openblas:|openblas}%{?_use_vespa_re2:|re2}%{?_use_vespa_xxhash:|xxhash}%{?_use_vespa_gtest:|(gtest|gmock)(_main)?}%{?_use_vespa_abseil_cpp:|absl_[a-z_0-9]*})\\.so\\.[0-9.]*\\([A-Za-z._0-9]*\\))\\(64bit\\)$

%description

Vespa - The open big data serving engine

%package base

Summary: Vespa - The open big data serving engine - base

%if 0%{?amzn2023}
Requires: java-17-amazon-corretto-devel
Requires: java-17-amazon-corretto
%else
Requires: java-17-openjdk-devel
%endif
Requires(pre): shadow-utils

%description base

Vespa - The open big data serving engine - base

%package base-libs

Summary: Vespa - The open big data serving engine - base C++ libraries

%if 0%{?centos} || 0%{?rocky} || 0%{?oraclelinux}
Requires: epel-release
%endif
%if 0%{?amzn2023}
Requires: vespa-xxhash >= 0.8.1
%else
Requires: xxhash-libs >= 0.8.1
%endif
%if 0%{?el8}
Requires: vespa-openssl >= 3.1.5
%else
Requires: openssl-libs
%endif
Requires: vespa-lz4 >= 1.9.4-1
Requires: vespa-libzstd >= 1.5.6-1
Requires: vespa-openblas >= %{_vespa_openblas_version}
%if 0%{?amzn2023}
Requires: vespa-re2 = 20210801
%else
Requires: re2
%endif
%if 0%{?fedora} || 0%{?el8} || 0%{?el9}
Requires: glibc-langpack-en
%endif

%description base-libs

Vespa - The open big data serving engine - base C++ libraries

%package libs

Summary: Vespa - The open big data serving engine - C++ libraries

Requires: %{name}-base-libs = %{version}-%{release}
Requires: libicu
%if 0%{?el8}
Requires: vespa-openssl >= 3.1.5
%else
Requires: openssl-libs
%endif
%if 0%{?el8}
Requires: llvm-libs
Requires: vespa-protobuf = %{_vespa_protobuf_version}
%endif
%if 0%{?el9}
Requires: llvm-libs
Requires: vespa-protobuf = %{_vespa_protobuf_version}
%endif
%if 0%{?fedora}
Requires: vespa-protobuf = %{_vespa_protobuf_version}
Requires: llvm-libs
%endif
Requires: vespa-onnxruntime = 1.17.3
Requires: vespa-jllama >= 3.0.1

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

%package jars

Summary: Vespa - The open big data serving engine - shared java jar files

%description jars

Vespa - The open big data serving engine - shared java jar files

%package malloc

Summary: Vespa - The open big data serving engine - malloc library

%description malloc

Vespa - The open big data serving engine - malloc library

%package tools

Summary: Vespa - The open big data serving engine - tools

Requires: %{name}-base = %{version}-%{release}
Requires: %{name}-base-libs = %{version}-%{release}

%description tools

Vespa - The open big data serving engine - tools

%package systemtest-tools

Summary: Vespa - The open big data serving engine - tools for system tests

Requires: %{name} = %{version}-%{release}
Requires: %{name}-base-libs = %{version}-%{release}
Requires: valgrind
Requires: perf

%description systemtest-tools

Vespa - The open big data serving engine - tools for system tests

%package devel

Summary: Vespa - The open big data serving engine - devel package

Requires: vespa-build-dependencies = %{_vespa_build_depencencies_version}
Requires: %{name}           = %{version}-%{release}
Requires: %{name}-base-libs = %{version}-%{release}

%description devel

Vespa - The open big data serving engine - devel package

%prep
%if 0%{?installdir:1}
%if 0%{?source_base:1}
%setup -q
%else
%setup -c -D -T
%endif
%else
%setup -q

echo '%{version}' > VERSION
case '%{version}' in
    *.0)
	:
	;;
    *)
	sed -i -e 's,<version>[0-9].*-SNAPSHOT</version>,<version>%{version}</version>,' $(find . -name pom.xml -print)
	;;
esac
%endif

%build
%if ! 0%{?installdir:1}
%if 0%{?_devtoolset_enable:1}
source %{_devtoolset_enable} || true
%endif
%if 0%{?_rhmaven35_enable:1}
source %{_rhmaven35_enable} || true
%endif
%if 0%{?_rhgit227_enable:1}
source %{_rhgit227_enable} || true
%endif

%if 0%{?_java_home:1}
export JAVA_HOME=%{?_java_home}
%else
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
%endif
export PATH="$JAVA_HOME/bin:$PATH"
export FACTORY_VESPA_VERSION=%{version}

%if 0%{?_use_mvn_wrapper}
mvn --batch-mode -e -N io.takari:maven:wrapper -Dmaven=3.6.3
%endif
%{?_use_mvn_wrapper:env VESPA_MAVEN_COMMAND=$(pwd)/mvnw }sh bootstrap.sh java
%{?_use_mvn_wrapper:./mvnw}%{!?_use_mvn_wrapper:mvn} --batch-mode -nsu -T 1C install -DskipTests -Dmaven.javadoc.skip=true
%{_command_cmake} -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=$JAVA_HOME \
       -DVESPA_USER=%{_vespa_user} \
       -DVESPA_UNPRIVILEGED=no \
       %{_cmake_extra_opts} \
       .

make %{_smp_mflags}
VERSION=%{version} CI=true make -C client/go install-all
%endif

%check
%if ! 0%{?installdir:1}
%if 0%{?_java_home:1}
export JAVA_HOME=%{?_java_home}
%else
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
%endif
export PATH="$JAVA_HOME/bin:$PATH"
#%{?_use_mvn_wrapper:./mvnw}%{!?_use_mvn_wrapper:mvn} --batch-mode -nsu -T 1C -Dmaven.javadoc.skip=true test
make test ARGS="--output-on-failure %{_smp_mflags}"
%endif

%install
rm -rf %{buildroot}

%if 0%{?installdir:1}
cp -r %{installdir} %{buildroot}
%if 0%{?source_base:1}
find %{buildroot} -exec file {} \; | grep ': ELF ' | cut -d: -f1 | xargs --no-run-if-empty -n1 /usr/lib/rpm/debugedit -b %{source_base} -d %{_builddir}/%{name}-%{version}
%endif
%else
make install DESTDIR=%{buildroot}
cp client/go/bin/vespa %{buildroot}%{_prefix}/bin/vespa
mkdir -p %{buildroot}/usr/share
cp -a client/go/share/* %{buildroot}/usr/share
%endif

%if %{_create_vespa_service}
mkdir -p %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa.service %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa-configserver.service %{buildroot}/usr/lib/systemd/system
%endif

ln -s /usr/lib/jvm/jre-17-openjdk %{buildroot}/%{_prefix}/jdk

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
%if 0%{?el8} || 0%{?el9}
# TODO Hardcoded toolset version, should be detected in a better way.
mkdir -p /opt/rh
ln -sf /opt/rh/gcc-toolset-13 /opt/rh/gcc-toolset
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

%post base

ln -sf %{_prefix}/var/tmp %{_prefix}/tmp

%postun base
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
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
if test -L %{_prefix}/tmp
then
    rm -f %{_prefix}/tmp
fi

%files
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%doc
%dir %{_prefix}
%{_prefix}/bin
%exclude %{_prefix}/bin/vespa
%exclude %{_prefix}/bin/vespa-destination
%exclude %{_prefix}/bin/vespa-document-statistics
%exclude %{_prefix}/bin/vespa-fbench
%exclude %{_prefix}/bin/vespa-feed-client
%exclude %{_prefix}/bin/vespa-feeder
%exclude %{_prefix}/bin/vespa-get
%exclude %{_prefix}/bin/vespa-jvm-dumper
%exclude %{_prefix}/bin/vespa-logfmt
%exclude %{_prefix}/bin/vespa-query-profile-dump-tool
%exclude %{_prefix}/bin/vespa-stat
%exclude %{_prefix}/bin/vespa-security-env
%exclude %{_prefix}/bin/vespa-summary-benchmark
%exclude %{_prefix}/bin/vespa-tensor-conformance
%exclude %{_prefix}/bin/vespa-tensor-instructions-benchmark
%exclude %{_prefix}/bin/vespa-visit
%exclude %{_prefix}/bin/vespa-visit-target
%dir %{_prefix}/conf
%{_prefix}/conf/configserver
%{_prefix}/conf/configserver-app
%exclude %{_prefix}/conf/configserver-app/components/config-model-fat.jar
%exclude %{_prefix}/conf/configserver-app/config-models.xml
%dir %{_prefix}/conf/logd
%dir %{_prefix}/conf/vespa
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/zookeeper/conf
%dir %{_prefix}/etc
%{_prefix}/etc/systemd
%{_prefix}/etc/vespa
%exclude %{_prefix}/etc/vespamalloc.conf
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
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
%{_prefix}/lib/jars/http-client-jar-with-dependencies.jar
%{_prefix}/lib/jars/logserver-jar-with-dependencies.jar
%{_prefix}/lib/jars/metrics-proxy-jar-with-dependencies.jar
%{_prefix}/lib/jars/node-repository-jar-with-dependencies.jar
%{_prefix}/lib/jars/orchestrator-jar-with-dependencies.jar
%{_prefix}/lib/jars/predicate-search-jar-with-dependencies.jar
%{_prefix}/lib/jars/searchlib.jar
%{_prefix}/lib/jars/service-monitor-jar-with-dependencies.jar
%{_prefix}/lib/jars/tenant-cd-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa-osgi-testrunner-jar-with-dependencies.jar
%{_prefix}/lib/jars/vespa-testrunner-components.jar
%{_prefix}/lib/jars/vespa-testrunner-components-jar-with-dependencies.jar
%{_prefix}/lib/jars/zookeeper-command-line-client-jar-with-dependencies.jar
%{_prefix}/lib/perl5
%{_prefix}/libexec
%exclude %{_prefix}/libexec/vespa/common-env.sh
%exclude %{_prefix}/libexec/vespa/vespa-wrapper
%exclude %{_prefix}/libexec/vespa/find-pid
%exclude %{_prefix}/libexec/vespa/standalone-container.sh
%exclude %{_prefix}/libexec/vespa/vespa-curl-wrapper
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/logs
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/logs/vespa
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/logs/vespa/access
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/logs/vespa/configserver
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/logs/vespa/search
%{_prefix}/man
%{_prefix}/sbin
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/crash
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/config_server
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/config_server/serverdb
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/config_server/serverdb/tenants
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/download
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/filedistribution
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/index
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/logcontrol
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/search
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/db/vespa/tmp
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/jdisc_container
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/run
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/tmp
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/tmp/vespa
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa/application
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa/bundlecache
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa/bundlecache/configserver
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa/cache
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/vespa/cache/config
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/zookeeper
%dir %attr(-,%{_vespa_user},%{_vespa_group}) %{_prefix}/var/zookeeper/version-2
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
%config(noreplace) %{_prefix}/conf/vespa/java.security.override
%{_prefix}/jdk
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%dir %{_prefix}/libexec
%dir %{_prefix}/libexec/vespa
%{_prefix}/libexec/vespa/common-env.sh
%{_prefix}/libexec/vespa/vespa-wrapper
%{_prefix}/libexec/vespa/find-pid
%{_prefix}/libexec/vespa/vespa-curl-wrapper
%{_prefix}/share
%exclude %{_prefix}/share/cmake

%files base-libs
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/lib64
%{_prefix}/lib64/libfnet.so
%{_prefix}/lib64/libvespadefaults.so
%{_prefix}/lib64/libvespalib.so
%{_prefix}/lib64/libvespalog.so

%files libs
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%{_prefix}/lib64
%exclude %{_prefix}/lib64/libfnet.so
%exclude %{_prefix}/lib64/libvespadefaults.so
%exclude %{_prefix}/lib64/libvespalib.so
%exclude %{_prefix}/lib64/libvespalog.so
%exclude %{_prefix}/lib64/vespa

%files clients
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/bin
%dir %{_prefix}/conf
%dir %{_prefix}/conf/vespa-feed-client
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/bin/vespa
%{_prefix}/bin/vespa-feed-client
%{_prefix}/conf/vespa-feed-client/logging.properties
%{_prefix}/lib/jars/vespa-feed-client-cli-jar-with-dependencies.jar
%docdir /usr/share/man
/usr/share/man

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

%files jars
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/lib
%dir %{_prefix}/lib/jars
%{_prefix}/lib/jars/application-model-jar-with-dependencies.jar
%{_prefix}/lib/jars/bc*-jdk18on-*.jar
%{_prefix}/lib/jars/config-bundle-jar-with-dependencies.jar
%{_prefix}/lib/jars/configdefinitions-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-model-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-model-jar-with-dependencies.jar
%{_prefix}/lib/jars/config-provisioning-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-apache-http-client-bundle-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-disc-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-llama.jar
%{_prefix}/lib/jars/container-onnxruntime.jar
%{_prefix}/lib/jars/container-search-and-docproc-jar-with-dependencies.jar
%{_prefix}/lib/jars/container-spifly.jar
%{_prefix}/lib/jars/docprocs-jar-with-dependencies.jar
%{_prefix}/lib/jars/flags-jar-with-dependencies.jar
%{_prefix}/lib/jars/hosted-zone-api-jar-with-dependencies.jar
%{_prefix}/lib/jars/jackson-*.jar
%{_prefix}/lib/jars/javax.*.jar
%{_prefix}/lib/jars/jdisc-cloud-aws-jar-with-dependencies.jar
%{_prefix}/lib/jars/jdisc_core-jar-with-dependencies.jar
%{_prefix}/lib/jars/jdisc-security-filters-jar-with-dependencies.jar
%{_prefix}/lib/jars/jna-*.jar
%{_prefix}/lib/jars/linguistics-components-jar-with-dependencies.jar
%{_prefix}/lib/jars/lucene-linguistics-jar-with-dependencies.jar
%{_prefix}/lib/jars/model-evaluation-jar-with-dependencies.jar
%{_prefix}/lib/jars/model-integration-jar-with-dependencies.jar
%{_prefix}/lib/jars/security-utils.jar
%{_prefix}/lib/jars/standalone-container-jar-with-dependencies.jar
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

%files malloc
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/etc
%config(noreplace) %{_prefix}/etc/vespamalloc.conf
%dir %{_prefix}/lib64
%{_prefix}/lib64/vespa

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

%files systemtest-tools
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%dir %{_prefix}
%dir %{_prefix}/bin
%{_prefix}/bin/vespa-tensor-conformance
%{_prefix}/bin/vespa-tensor-instructions-benchmark

%files devel
%defattr(-,root,root,-)
%dir %{_prefix}
%{_prefix}/include
%{_prefix}/share/cmake

%changelog
