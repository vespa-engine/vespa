# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
##
# Defer all root imports
#
# This allows to safely import config, change it, and
# only after that actually run imports, though the
# import statement can be on the top of the file
#
# Viva PEP8, morituri te salutant!
#
# Surely, you still can import modules directly from their
# places, like `from pyroute2.iproute import IPRoute`
##
__all__ = []
_modules = {'IPRoute': 'pyroute2.iproute',
            'IPSet': 'pyroute2.ipset',
            'IPDB': 'pyroute2.ipdb',
            'IW': 'pyroute2.iwutil',
            'NetNS': 'pyroute2.netns.nslink',
            'NSPopen': 'pyroute2.netns.process.proxy',
            'IPRSocket': 'pyroute2.netlink.rtnl.iprsocket',
            'TaskStats': 'pyroute2.netlink.taskstats',
            'NL80211': 'pyroute2.netlink.nl80211',
            'IPQSocket': 'pyroute2.netlink.ipq',
            'GenericNetlinkSocket': 'pyroute2.netlink.generic',
            'NetlinkError': 'pyroute2.netlink'}

_DISCLAIMER = '''\n\nNotice:\n
This is a proxy class. To read full docs, please run
the `help()` method on the instance instead.

Usage of the proxy allows to postpone the module load,
thus providing a safe way to substitute base classes,
if it is required. More details see in the `pyroute2.config`
module.
\n'''


class _ProxyMeta(type):
    '''
    All this metaclass alchemy is implemented to provide a
    reasonable, though not exhaustive documentation on the
    proxy classes.
    '''

    def __init__(cls, name, bases, dct):

        class doc(str):
            def __repr__(self):
                return repr(cls.proxy['doc'])

            def __str__(self):
                return str(cls.proxy['doc'])

            def expandtabs(self, ts=4):
                return cls.proxy['doc'].expandtabs(ts)

        class proxy(object):
            def __init__(self):
                self.target = {}

            def __getitem__(self, key):
                if not self.target:
                    module = __import__(_modules[cls.name],
                                        globals(),
                                        locals(),
                                        [cls.name], 0)
                    self.target['constructor'] = getattr(module, cls.name)
                    self.target['doc'] = self.target['constructor'].__doc__
                    try:
                        self.target['doc'] += _DISCLAIMER
                    except TypeError:
                        # ignore cases, when __doc__ is not a string, e.g. None
                        pass
                return self.target[key]

        def __call__(self, *argv, **kwarg):
            '''
            Actually load the module and call the constructor.
            '''
            return self.proxy['constructor'](*argv, **kwarg)

        cls.name = name
        cls.proxy = proxy()
        cls.__call__ = __call__
        cls.__doc__ = doc()

        super(_ProxyMeta, cls).__init__(name, bases, dct)


for name in _modules:

    f = _ProxyMeta(name, (), {})()
    globals()[name] = f
    __all__.append(name)
