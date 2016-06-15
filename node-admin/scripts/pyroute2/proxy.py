# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
Netlink proxy engine
'''
import errno
import struct
import logging
import traceback
import threading


class NetlinkProxy(object):
    '''
    Proxy schemes::

        User -> NetlinkProxy -> Kernel
                       |
             <---------+

        User <- NetlinkProxy <- Kernel

    '''

    def __init__(self, policy='forward', nl=None, lock=None):
        self.nl = nl
        self.lock = lock or threading.Lock()
        self.pmap = {}
        self.policy = policy

    def handle(self, data):
        #
        # match the packet
        #
        ptype = struct.unpack('H', data[4:6])[0]
        plugin = self.pmap.get(ptype, None)
        if plugin is not None:
            with self.lock:
                try:
                    ret = plugin(data, self.nl)
                    if ret is None:
                        msg = struct.pack('IHH', 40, 2, 0)
                        msg += data[8:16]
                        msg += struct.pack('I', 0)
                        # nlmsgerr struct alignment
                        msg += b'\0' * 20
                        return {'verdict': self.policy,
                                'data': msg}
                    else:
                        return ret

                except Exception as e:
                    logging.error(traceback.format_exc())
                    # errmsg
                    if isinstance(e, (OSError, IOError)):
                        code = e.errno
                    else:
                        code = errno.ECOMM
                    msg = struct.pack('HH', 2, 0)
                    msg += data[8:16]
                    msg += struct.pack('I', code)
                    msg += data
                    msg = struct.pack('I', len(msg) + 4) + msg
                    return {'verdict': 'error',
                            'data': msg}
        return None
