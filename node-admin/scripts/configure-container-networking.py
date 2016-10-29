#!/usr/bin/env python
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Quick and dirty script to specify the default gateway on docker interface

from __future__ import print_function

import os
import sys

from pyroute2 import NetNS
from socket import AF_INET6

def create_directory_ignore_exists(path, permissions):
    if not os.path.isdir(path):
        os.mkdir(path, permissions)


def create_symlink_ignore_exists(path_to_point_to, symlink_location):
    if not os.path.islink(symlink_location):
        os.symlink(path_to_point_to, symlink_location)


def get_attribute(struct_with_attrs, name):
    try:
        matching_values = [attribute[1] for attribute in struct_with_attrs['attrs'] if attribute[0] == name]
        if len(matching_values) == 0:
            raise RuntimeError("No such attribute: %s" % name)
        elif len(matching_values) != 1:
            raise RuntimeError("Multiple values for attribute %s" %name)
        else:
            return matching_values[0]
    except Exception as e:
        raise RuntimeError("Couldn't find attribute %s for value: %s" % (name, struct_with_attrs), e)

def net_namespace_path(pid):
    return "/host/proc/%d/ns/net" % pid

def get_net_namespace_for_pid(pid):
    net_ns_path = net_namespace_path(pid)
    if not os.path.isfile(net_ns_path):
        raise RuntimeError("No such net namespace %s" % net_ns_path )
    create_directory_ignore_exists("/var/run/netns", 0766)
    create_symlink_ignore_exists(net_ns_path,  "/var/run/netns/%d" % pid)
    return NetNS(str(pid))

def index_of_interface_in_namespace(interface_name, namespace):
    interface_index_list = namespace.link_lookup(ifname=interface_name)
    if not interface_index_list:
        return None
    assert len(interface_index_list) == 1
    return interface_index_list[0]


def get_default_route(net_namespace, family):
    # route format: {
    #     'family': 2,
    #     'dst_len': 0,
    #     'proto': 3,
    #     'tos': 0,
    #     'event': 'RTM_NEWROUTE',
    #     'header': {
    #         'pid': 43,
    #         'length': 52,
    #         'flags': 2,
    #         'error': None,
    #         'type': 24,
    #         'sequence_number': 255
    #     },
    #     'flags': 0,
    #     'attrs': [
    #         ['RTA_TABLE', 254],
    #         ['RTA_GATEWAY', '172.17.42.1'],
    #         ['RTA_OIF', 18]
    #     ],
    #     'table': 254,
    #     'src_len': 0,
    #     'type': 1,
    #     'scope': 0
    # }
    default_routes = net_namespace.get_default_routes()
    for route in default_routes:
        if route['family'] == family:
            return route
    raise RuntimeError("Couldn't find default route: " + str(default_routes))


# There is a bug in the Docker networking setup which requires us to manually specify the default gateway
# https://github.com/docker/libnetwork/issues/1443
def set_docker_gateway_on_docker_interface():
    if len(sys.argv) != 2:
        raise RuntimeError("Usage: %s --fix-docker-gateway <container-pid>" % sys.argv[0])
    try:
        container_pid = int(sys.argv[1])
    except ValueError:
        raise RuntimeError("Container pid must be an integer, got %s" % sys.argv[1])
    host_ns = get_net_namespace_for_pid(1)
    container_ns = get_net_namespace_for_pid(container_pid)
    container_interface_index = index_of_interface_in_namespace(interface_name="eth1", namespace=container_ns)

    host_default_route = get_default_route(net_namespace=host_ns, family=AF_INET6)
    host_default_route_gateway = get_attribute(host_default_route, 'RTA_GATEWAY')
    container_ns.route(command="replace", gateway=host_default_route_gateway, index=container_interface_index, family=AF_INET6)


# Parse arguments
flag_fix_docker_gateway = "--fix-docker-gateway"
fix_docker_gateway = flag_fix_docker_gateway in sys.argv
if fix_docker_gateway:
    sys.argv.remove(flag_fix_docker_gateway)

if fix_docker_gateway:
    set_docker_gateway_on_docker_interface()
else:
    raise RuntimeError("Only valid flag is %s, got %s" % (flag_fix_docker_gateway, sys.argv[1]))