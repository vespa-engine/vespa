FROM docker-registry.ops.yahoo.com:4443/vespa/ci:6.50.12

# Don't autostart anything when building image
RUN yinst set root.autostart=off

RUN yinst install -branch test vespa_systemtest_base vespa_systemtest_gems

ENTRYPOINT /systemtests/bin/node_server.rb
