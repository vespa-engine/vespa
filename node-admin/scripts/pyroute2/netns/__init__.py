# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
Network namespaces management
=============================

Pyroute2 provides basic namespaces management support. The
`netns` module contains several tools for that.

Please be aware, that in order to run system calls the
library uses `ctypes` module. It can fail on platforms
where SELinux is enforced. If the Python interpreter,
loading this module, dumps the core, one can check the
SELinux state with `getenforce` command.

'''

import os
import errno
import ctypes
import sys

if sys.maxsize > 2**32:
    __NR_setns = 308
else:
    __NR_setns = 346

CLONE_NEWNET = 0x40000000
MNT_DETACH = 0x00000002
MS_BIND = 4096
MS_REC = 16384
MS_SHARED = 1 << 20
NETNS_RUN_DIR = '/var/run/netns'


def listnetns():
    '''
    List available network namespaces.
    '''
    try:
        return os.listdir(NETNS_RUN_DIR)
    except OSError as e:
        if e.errno == errno.ENOENT:
            return []
        else:
            raise


def create(netns, libc=None):
    '''
    Create a network namespace.
    '''
    libc = libc or ctypes.CDLL('libc.so.6', use_errno=True)
    # FIXME validate and prepare NETNS_RUN_DIR

    netnspath = '%s/%s' % (NETNS_RUN_DIR, netns)
    netnspath = netnspath.encode('ascii')
    netnsdir = NETNS_RUN_DIR.encode('ascii')

    # init netnsdir
    try:
        os.mkdir(netnsdir)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

    # this code is ported from iproute2
    done = False
    while libc.mount(b'', netnsdir, b'none', MS_SHARED | MS_REC, None) != 0:
        if done:
            raise OSError(ctypes.get_errno(), 'share rundir failed', netns)
        if libc.mount(netnsdir, netnsdir, b'none', MS_BIND, None) != 0:
            raise OSError(ctypes.get_errno(), 'mount rundir failed', netns)
        done = True

    # create mountpoint
    os.close(os.open(netnspath, os.O_RDONLY | os.O_CREAT | os.O_EXCL, 0))

    # unshare
    if libc.unshare(CLONE_NEWNET) < 0:
        raise OSError(ctypes.get_errno(), 'unshare failed', netns)

    # bind the namespace
    if libc.mount(b'/proc/self/ns/net', netnspath, b'none', MS_BIND, None) < 0:
        raise OSError(ctypes.get_errno(), 'mount failed', netns)


def remove(netns, libc=None):
    '''
    Remove a network namespace.
    '''
    libc = libc or ctypes.CDLL('libc.so.6', use_errno=True)
    netnspath = '%s/%s' % (NETNS_RUN_DIR, netns)
    netnspath = netnspath.encode('ascii')
    libc.umount2(netnspath, MNT_DETACH)
    os.unlink(netnspath)


def setns(netns, flags=os.O_CREAT, libc=None):
    '''
    Set netns for the current process.

    The flags semantics is the same as for the `open(2)`
    call:

        - O_CREAT -- create netns, if doesn't exist
        - O_CREAT | O_EXCL -- create only if doesn't exist
    '''
    libc = libc or ctypes.CDLL('libc.so.6', use_errno=True)
    netnspath = '%s/%s' % (NETNS_RUN_DIR, netns)
    netnspath = netnspath.encode('ascii')

    if netns in listnetns():
        if flags & (os.O_CREAT | os.O_EXCL) == (os.O_CREAT | os.O_EXCL):
            raise OSError(errno.EEXIST, 'netns exists', netns)
    else:
        if flags & os.O_CREAT:
            create(netns, libc=libc)

    nsfd = os.open(netnspath, os.O_RDONLY)
    ret = libc.syscall(__NR_setns, nsfd, CLONE_NEWNET)
    if ret != 0:
        raise OSError(ctypes.get_errno(), 'failed to open netns', netns)
    return nsfd
