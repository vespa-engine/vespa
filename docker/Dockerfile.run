# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
FROM centos:7
ARG VESPA_VERSION

# Needed to run vespa
RUN yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo && \
    yum -y install epel-release && \
    yum -y install centos-release-scl

COPY vespa*-${VESPA_VERSION}-*.rpm /tmp/
RUN yum localinstall -y $(ls /tmp/vespa*-${VESPA_VERSION}-*.rpm | xargs)

ENV VESPA_HOME=/opt/vespa
ENV PATH="${PATH}:${VESPA_HOME}/bin"

