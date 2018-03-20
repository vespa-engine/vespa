# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa
Version:        6.223.147
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai
Source0:        vespa-%{version}.tar.gz

%if 0%{?centos}
BuildRequires: epel-release
BuildRequires: centos-release-scl
BuildRequires: devtoolset-7-gcc-c++
BuildRequires: devtoolset-7-libatomic-devel
BuildRequires: devtoolset-7-binutils
BuildRequires: rh-maven33
%define _devtoolset_enable /opt/rh/devtoolset-7/enable
%define _rhmaven33_enable /opt/rh/rh-maven33/enable
%endif
%if 0%{?fedora}
BuildRequires: gcc-c++
BuildRequires: libatomic
%endif
BuildRequires: Judy-devel
%if 0%{?centos}
BuildRequires: cmake3
BuildRequires: llvm3.9-devel
BuildRequires: vespa-boost-devel >= 1.59.0-6
%endif
%if 0%{?fedora}
BuildRequires: cmake >= 3.9.1
BuildRequires: maven
%if 0%{?fc25}
BuildRequires: llvm-devel >= 3.9.1
BuildRequires: boost-devel >= 1.60
%endif
%if 0%{?fc26}
BuildRequires: llvm-devel >= 4.0
BuildRequires: boost-devel >= 1.63
%endif
%if 0%{?fc27}
BuildRequires: llvm4.0-devel >= 4.0
BuildRequires: boost-devel >= 1.64
%endif
%if 0%{?fc28}
BuildRequires: llvm4.0-devel >= 4.0
BuildRequires: boost-devel >= 1.64
%endif
BuildRequires: zookeeper-devel >= 3.4.9
%endif
BuildRequires: lz4-devel
BuildRequires: libzstd-devel
BuildRequires: zlib-devel
BuildRequires: libicu-devel
BuildRequires: java-1.8.0-openjdk-devel
BuildRequires: openssl-devel
BuildRequires: rpm-build
BuildRequires: make
BuildRequires: vespa-cppunit-devel >= 1.12.1-6
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
Requires: perl-Net-INET6Glue
Requires: perl-Pod-Usage
Requires: perl-URI
Requires: valgrind
Requires: Judy
Requires: lz4
Requires: libzstd
Requires: zlib
Requires: libicu
Requires: perf
Requires: gdb
Requires: net-tools
%if 0%{?centos}
Requires: llvm3.9
Requires: vespa-boost >= 1.59.0-6
%define _extra_link_directory /usr/lib64/llvm3.9/lib;/opt/vespa-boost/lib;/opt/vespa-cppunit/lib
%define _extra_include_directory /usr/include/llvm3.9;/opt/vespa-boost/include;/opt/vespa-cppunit/include
%endif
%if 0%{?fedora}
%if 0%{?fc25}
Requires: llvm-libs >= 3.9.1
Requires: boost >= 1.60
%endif
%if 0%{?fc26}
Requires: llvm-libs >= 4.0
Requires: boost >= 1.63
%define _vespa_llvm_version 4.0
%endif
%if 0%{?fc27}
Requires: llvm4.0-libs >= 4.0
Requires: boost >= 1.64
%define _vespa_llvm_version 4.0
%define _vespa_llvm_link_directory /usr/lib64/llvm4.0/lib
%define _vespa_llvm_include_directory /usr/include/llvm4.0
%endif
%if 0%{?fc28}
Requires: llvm4.0-libs >= 4.0
Requires: boost >= 1.64
%define _vespa_llvm_version 4.0
%define _vespa_llvm_link_directory /usr/lib64/llvm4.0/lib
%define _vespa_llvm_include_directory /usr/include/llvm4.0
%endif
Requires: zookeeper >= 3.4.9
%define _extra_link_directory /opt/vespa-cppunit/lib%{?_vespa_llvm_link_directory:;%{_vespa_llvm_link_directory}}
%define _extra_include_directory /opt/vespa-cppunit/include%{?_vespa_llvm_include_directory:;%{_vespa_llvm_include_directory}}
%define _vespa_boost_lib_suffix %{nil}
%endif
Requires: java-1.8.0-openjdk
Requires: openssl
Requires: vespa-cppunit >= 1.12.1-6
Requires(pre): shadow-utils

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
Provides: libc.so.6(GLIBC_PRIVATE)(64bit)

%description

Vespa - The open big data serving engine

%prep
%setup -q

%build
%if 0%{?_devtoolset_enable:1}
source %{_devtoolset_enable} || true
%endif
%if 0%{?_rhmaven33_enable:1}
source %{_rhmaven33_enable} || true
%endif
sh bootstrap.sh java
mvn -nsu -T 2C install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
cmake3 -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=/usr/lib/jvm/java-openjdk \
       -DEXTRA_LINK_DIRECTORY="%{_extra_link_directory}" \
       -DEXTRA_INCLUDE_DIRECTORY="%{_extra_include_directory}" \
       -DCMAKE_INSTALL_RPATH="%{_prefix}/lib64%{?_extra_link_directory:;%{_extra_link_directory}};/usr/lib/jvm/java-1.8.0/jre/lib/amd64/server" \
       %{?_vespa_llvm_version:-DVESPA_LLVM_VERSION="%{_vespa_llvm_version}"} \
       %{?_vespa_boost_lib_suffix:-DVESPA_BOOST_LIB_SUFFIX="%{_vespa_boost_lib_suffix}"} \
       .

make %{_smp_mflags}

%install
rm -rf $RPM_BUILD_ROOT
make install DESTDIR=%{buildroot}

mkdir -p %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa.service %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa-configserver.service %{buildroot}/usr/lib/systemd/system

%clean
rm -rf $RPM_BUILD_ROOT

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d %{_prefix} -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
echo "pathmunge %{_prefix}/bin" > /etc/profile.d/vespa.sh
echo "export VESPA_HOME=%{_prefix}" >> /etc/profile.d/vespa.sh
chmod +x /etc/profile.d/vespa.sh
exit 0

%post
%systemd_post vespa-configserver.service
%systemd_post vespa.service

%preun
%systemd_preun vespa.service
%systemd_preun vespa-configserver.service

%postun
%systemd_postun_with_restart vespa.service
%systemd_postun_with_restart vespa-configserver.service
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
    ! getent passwd vespa >/dev/null || userdel vespa
    ! getent group vespa >/dev/null || groupdel vespa
fi

%files
%defattr(-,vespa,vespa,-)
%doc
%{_prefix}/*
%config(noreplace) %{_prefix}/conf/logd/logd.cfg
%config(noreplace) %{_prefix}/conf/vespa/default-env.txt
%config(noreplace) %{_prefix}/etc/vespamalloc.conf
%attr(644,root,root) /usr/lib/systemd/system/vespa.service
%attr(644,root,root) /usr/lib/systemd/system/vespa-configserver.service

%changelog
