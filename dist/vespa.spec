# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Force special prefix for Vespa
%define _prefix /opt/vespa
%define _vespa_deps_prefix /opt/vespa-deps
%define _vespa_user vespa
%define _vespa_group vespa
%define _create_vespa_group 1
%define _create_vespa_user 1
%define _create_vespa_service 1
%define _defattr_is_vespa_vespa 0

Name:           vespa
Version:        _VESPA_VERSION_
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai
Source0:        vespa-%{version}.tar.gz

%if 0%{?centos}
BuildRequires: epel-release
%if 0%{?el7}
BuildRequires: centos-release-scl
%endif
%endif
%if 0%{?el7}
BuildRequires: devtoolset-8-gcc-c++
BuildRequires: devtoolset-8-libatomic-devel
BuildRequires: devtoolset-8-binutils
BuildRequires: rh-maven35
%define _devtoolset_enable /opt/rh/devtoolset-8/enable
%define _rhmaven35_enable /opt/rh/rh-maven35/enable
%endif
%if 0%{?el8}
BuildRequires: gcc-c++
BuildRequires: libatomic
BuildRequires: maven
%endif
%if 0%{?fedora}
BuildRequires: gcc-c++
BuildRequires: libatomic
%endif
BuildRequires: Judy-devel
%if 0%{?el7}
BuildRequires: cmake3
BuildRequires: llvm5.0-devel
BuildRequires: vespa-boost-devel >= 1.59.0-6
BuildRequires: vespa-gtest >= 1.8.1-1
BuildRequires: vespa-protobuf-devel >= 3.7.0-4
BuildRequires: vespa-openssl-devel >= 1.1.1c-1
BuildRequires: vespa-icu-devel >= 65.1.0-1
%endif
%if 0%{?el8}
BuildRequires: cmake >= 3.11.4-3
BuildRequires: llvm-devel >= 8.0.1
BuildRequires: boost-devel >= 1.66
BuildRequires: openssl-devel
BuildRequires: vespa-gtest >= 1.8.1-1
BuildRequires: vespa-protobuf-devel >= 3.7.0-4
%endif
%if 0%{?fedora}
BuildRequires: cmake >= 3.9.1
BuildRequires: maven
BuildRequires: openssl-devel
%if 0%{?fc29}
BuildRequires: vespa-protobuf-devel >= 3.7.0-4
BuildRequires: llvm-devel >= 7.0.0
BuildRequires: boost-devel >= 1.66
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc30}
BuildRequires: vespa-protobuf-devel >= 3.7.0-4
BuildRequires: llvm-devel >= 8.0.0
BuildRequires: boost-devel >= 1.69
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc31}
BuildRequires: vespa-protobuf-devel >= 3.7.0-4
BuildRequires: llvm-devel >= 9.0.0
BuildRequires: boost-devel >= 1.69
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc32}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 10.0.0
BuildRequires: boost-devel >= 1.69
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc33}
BuildRequires: protobuf-devel
BuildRequires: llvm-devel >= 10.0.0
BuildRequires: boost-devel >= 1.69
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%endif
BuildRequires: xxhash-devel >= 0.6.5
BuildRequires: openblas-devel
BuildRequires: lz4-devel
BuildRequires: libzstd-devel
BuildRequires: zlib-devel
%if ! 0%{?el7}
BuildRequires: libicu-devel
%endif
BuildRequires: java-11-openjdk-devel
BuildRequires: rpm-build
BuildRequires: make
BuildRequires: git
BuildRequires: systemd
BuildRequires: flex >= 2.5.0
BuildRequires: bison >= 3.0.0
%if 0%{?centos}
Requires: epel-release
%endif
Requires: which
Requires: initscripts
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
Requires: valgrind
Requires: Judy
Requires: xxhash
Requires: xxhash-libs >= 0.6.5
%if 0%{?el8}
Requires: openblas
%else
Requires: openblas-serial
%endif
Requires: lz4
Requires: libzstd
Requires: zlib
%if ! 0%{?el7}
Requires: libicu
%endif
Requires: perf
Requires: gdb
Requires: net-tools
%if 0%{?el7}
Requires: llvm5.0
Requires: vespa-openssl >= 1.1.1c-1
Requires: vespa-icu >= 65.1.0-1
Requires: vespa-protobuf >= 3.7.0-4
Requires: vespa-telegraf >= 1.1.1-1
%define _vespa_llvm_version 5.0
%define _extra_link_directory /usr/lib64/llvm5.0/lib;%{_vespa_deps_prefix}/lib64
%define _extra_include_directory /usr/include/llvm5.0;%{_vespa_deps_prefix}/include;/usr/include/openblas
%endif
%if 0%{?el8}
Requires: llvm-libs >= 8.0.1
Requires: vespa-protobuf >= 3.7.0-4
Requires: openssl-libs
%define _vespa_llvm_version 8
%define _extra_link_directory %{_vespa_deps_prefix}/lib64
%define _extra_include_directory %{_vespa_deps_prefix}/include;/usr/include/openblas
%endif
%if 0%{?fedora}
Requires: openssl-libs
%if 0%{?fc29}
Requires: vespa-protobuf >= 3.7.0-4
Requires: llvm-libs >= 7.0.0
%define _vespa_llvm_version 7
%endif
%if 0%{?fc30}
Requires: vespa-protobuf >= 3.7.0-4
Requires: llvm-libs >= 8.0.0
%define _vespa_llvm_version 8
%endif
%if 0%{?fc31}
Requires: vespa-protobuf >= 3.7.0-4
Requires: llvm-libs >= 9.0.0
%define _vespa_llvm_version 9
%endif
%if 0%{?fc32}
Requires: protobuf
Requires: llvm-libs >= 10.0.0
%define _vespa_llvm_version 10
%endif
%if 0%{?fc33}
Requires: protobuf
Requires: llvm-libs >= 10.0.0
%define _vespa_llvm_version 10
%endif
%define _extra_link_directory %{_vespa_deps_prefix}/lib64
%define _extra_include_directory %{_vespa_deps_prefix}/include;/usr/include/openblas
%endif
Requires: java-11-openjdk
Requires(pre): shadow-utils

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
Provides: libc.so.6(GLIBC_PRIVATE)(64bit)

%description

Vespa - The open big data serving engine

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

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
export FACTORY_VESPA_VERSION=%{version}

sh bootstrap.sh java
mvn --batch-mode -nsu -T 1C  install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
cmake3 -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk \
       -DCMAKE_PREFIX_PATH=%{_vespa_deps_prefix} \
       -DEXTRA_LINK_DIRECTORY="%{_extra_link_directory}" \
       -DEXTRA_INCLUDE_DIRECTORY="%{_extra_include_directory}" \
       -DCMAKE_INSTALL_RPATH="%{_prefix}/lib64%{?_extra_link_directory:;%{_extra_link_directory}};/usr/lib/jvm/jre-11-openjdk/lib" \
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

%clean
rm -rf $RPM_BUILD_ROOT

%pre
%if %{_create_vespa_group}
getent group %{_vespa_group} >/dev/null || groupadd -r %{_vespa_group}
%endif
%if %{_create_vespa_user}
getent passwd %{_vespa_user} >/dev/null || \
    useradd -r -g %{_vespa_group} --home-dir %{_prefix} -s /sbin/nologin \
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

%postun
%if %{_create_vespa_service}
%systemd_postun_with_restart vespa.service
%systemd_postun_with_restart vespa-configserver.service
%endif
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
%if %{_create_vespa_user}
    ! getent passwd %{_vespa_user} >/dev/null || userdel %{_vespa_user}
%endif
%if %{_create_vespa_group}
    ! getent group %{_vespa_group} >/dev/null || groupdel %{_vespa_group}
%endif
fi

%files
%if %{_defattr_is_vespa_vespa}
%defattr(-,%{_vespa_user},%{_vespa_group},-)
%endif
%doc
%dir %{_prefix}
%{_prefix}/bin
%dir %{_prefix}/conf
%{_prefix}/conf/configserver
%{_prefix}/conf/configserver-app
%dir %{_prefix}/conf/logd
%{_prefix}/conf/node-admin-app
%dir %{_prefix}/conf/vespa
%dir %attr(-,%{_vespa_user},-) %{_prefix}/conf/zookeeper
%dir %{_prefix}/etc
%{_prefix}/etc/systemd
%{_prefix}/etc/vespa
%{_prefix}/include
%{_prefix}/lib
%{_prefix}/lib64
%{_prefix}/libexec
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
%config(noreplace) %{_prefix}/conf/vespa/default-env.txt
%config(noreplace) %{_prefix}/etc/vespamalloc.conf
%if %{_create_vespa_service}
%attr(644,root,root) /usr/lib/systemd/system/vespa.service
%attr(644,root,root) /usr/lib/systemd/system/vespa-configserver.service
%endif

%changelog
