# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
NSPopen
=======

The `NSPopen` class has nothing to do with netlink at
all, but it is required to have a reasonable network
namespace support.

'''


import types
import atexit
import threading
import subprocess
from pyroute2.netns import setns
from pyroute2.config import MpQueue
from pyroute2.config import MpProcess
try:
    from pyroute2.netns.process.base_p3 import NSPopenBase
except Exception:
    from pyroute2.netns.process.base_p2 import NSPopenBase


def _handle(result):
    if result['code'] == 500:
        raise result['data']
    elif result['code'] == 200:
        return result['data']
    else:
        raise TypeError('unsupported return code')


def NSPopenServer(nsname, flags, channel_in, channel_out, argv, kwarg):
    # set netns
    try:
        setns(nsname, flags=flags)
    except Exception as e:
        channel_out.put(e)
        return
    # create the Popen object
    child = subprocess.Popen(*argv, **kwarg)
    # send the API map
    channel_out.put(None)

    while True:
        # synchronous mode
        # 1. get the command from the API
        call = channel_in.get()
        # 2. stop?
        if call['name'] == 'release':
            break
        # 3. run the call
        try:
            attr = getattr(child, call['name'])
            if isinstance(attr, types.MethodType):
                result = attr(*call['argv'], **call['kwarg'])
            else:
                result = attr
            channel_out.put({'code': 200, 'data': result})
        except Exception as e:
            channel_out.put({'code': 500, 'data': e})
    child.wait()


class NSPopen(NSPopenBase):
    '''
    A proxy class to run `Popen()` object in some network namespace.

    Sample to run `ip ad` command in `nsname` network namespace::

        nsp = NSPopen('nsname', ['ip', 'ad'], stdout=subprocess.PIPE)
        print(nsp.communicate())
        nsp.wait()
        nsp.release()

    The only difference in the `release()` call. It explicitly ends
    the proxy process and releases all the resources.
    '''

    def __init__(self, nsname, *argv, **kwarg):
        '''
        The only differences from the `subprocess.Popen` init are:
        * `nsname` -- network namespace name
        * `flags` keyword argument

        All other arguments are passed directly to `subprocess.Popen`.

        Flags usage samples. Create a network namespace, if it doesn't
        exist yet::

            import os
            nsp = NSPopen('nsname', ['command'], flags=os.O_CREAT)

        Create a network namespace only if it doesn't exist, otherwise
        fail and raise an exception::

            import os
            nsp = NSPopen('nsname', ['command'], flags=os.O_CREAT | os.O_EXCL)
        '''
        # create a child
        self.nsname = nsname
        if 'flags' in kwarg:
            self.flags = kwarg.pop('flags')
        else:
            self.flags = 0
        self.channel_out = MpQueue()
        self.channel_in = MpQueue()
        self.lock = threading.Lock()
        self.released = False
        self.server = MpProcess(target=NSPopenServer,
                                args=(self.nsname,
                                      self.flags,
                                      self.channel_out,
                                      self.channel_in,
                                      argv, kwarg))
        # start the child and check the status
        self.server.start()
        response = self.channel_in.get()
        if isinstance(response, Exception):
            self.server.join()
            raise response
        else:
            atexit.register(self.release)

    def release(self):
        '''
        Explicitly stop the proxy process and release all the
        resources. The `NSPopen` object can not be used after
        the `release()` call.
        '''
        with self.lock:
            if self.released:
                return
            self.released = True
            self.channel_out.put({'name': 'release'})
            self.channel_out.close()
            self.channel_in.close()
            self.server.join()

    def __dir__(self):
        return list(self.api.keys()) + ['release']

    def __getattribute__(self, key):
        try:
            return object.__getattribute__(self, key)
        except AttributeError:
            with self.lock:
                if self.released:
                    raise RuntimeError('the object is released')

                if self.api.get(key) and self.api[key]['callable']:
                    def proxy(*argv, **kwarg):
                        self.channel_out.put({'name': key,
                                              'argv': argv,
                                              'kwarg': kwarg})
                        return _handle(self.channel_in.get())
                    proxy.__doc__ = self.api[key]['doc']
                    return proxy
                else:
                    self.channel_out.put({'name': key})
                    return _handle(self.channel_in.get())
