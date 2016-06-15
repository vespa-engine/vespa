# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
# -*- coding: utf-8 -*-
'''
IW module
=========

Experimental wireless module — nl80211 support.

Disclaimer
----------

Unlike IPRoute, which is mostly usable, though is far from
complete yet, the IW module is in the very initial state.
Neither the module itself, nor the message class cover the
nl80211 functionality reasonably enough. So if you're
going to use it, brace yourself — debug is coming.

Messages
--------

nl80211 messages are defined here::

    pyroute2/netlink/nl80211/__init__.py

Pls notice NLAs of type `hex`. On the early development stage
`hex` allows to inspect incoming data as a hex dump and,
occasionally, even make requests with such NLAs. But it's
not a production way.

The type `hex` in the NLA definitions means that this
particular NLA is not handled yet properly. If you want to
use some NLA which is defined as `hex` yet, pls find out a
specific type, patch the message class and submit your pull
request on github.

If you're not familiar with NLA types, take a look at RTNL
definitions::

    pyroute2/netlink/rtnl/ndmsg.py

and so on.

Communication with the kernel
-----------------------------

There are several methods of the communication with the kernel.

    * `sendto()` — lowest possible, send a raw binary data
    * `put()` — send a netlink message
    * `nlm_request()` — send a message, return the response
    * `get()` — get a netlink message
    * `recv()` — get a raw binary data from the kernel

There are no errors on `put()` usually. Any `permission denied`,
any `invalid value` errors are returned from the kernel with
netlink also. So if you do `put()`, but don't do `get()`, be
prepared to miss errors.

The preferred method for the communication is `nlm_request()`.
It tracks the message ID, returns the corresponding response.
In the case of errors `nlm_request()` raises an exception.
To get the response on any operation with nl80211, use flag
`NLM_F_ACK`.

Reverse it
----------

If you're too lazy to read the kernel sources, but still need
something not implemented here, you can use reverse engineering
on a reference implementation. E.g.::

    # strace -e trace=network -f -x -s 4096 \\
            iw phy phy0 interface add test type monitor

Will dump all the netlink traffic between the program `iw` and
the kernel. Three first packets are the generic netlink protocol
discovery, you can ignore them. All that follows, is the
nl80211 traffic::

    sendmsg(3, {msg_name(12)={sa_family=AF_NETLINK, ... },
        msg_iov(1)=[{"\\x30\\x00\\x00\\x00\\x1b\\x00\\x05 ...", 48}],
        msg_controllen=0, msg_flags=0}, 0) = 48
    recvmsg(3, {msg_name(12)={sa_family=AF_NETLINK, ... },
        msg_iov(1)=[{"\\x58\\x00\\x00\\x00\\x1b\\x00\\x00 ...", 16384}],
        msg_controllen=0, msg_flags=0}, 0) = 88
    ...

With `-s 4096` you will get the full dump. Then copy the strings
from `msg_iov` to a file, let's say `data`, and run the decoder::

    $ pwd
    /home/user/Projects/pyroute2
    $ export PYTHONPATH=`pwd`
    $ python scripts/decoder.py pyroute2.netlink.nl80211.nl80211cmd data

You will get the session decoded::

    {'attrs': [['NL80211_ATTR_WIPHY', 0],
               ['NL80211_ATTR_IFNAME', 'test'],
               ['NL80211_ATTR_IFTYPE', 6]],
     'cmd': 7,
     'header': {'flags': 5,
                'length': 48,
                'pid': 3292542647,
                'sequence_number': 1430426434,
                'type': 27},
     'reserved': 0,
     'version': 0}
    {'attrs': [['NL80211_ATTR_IFINDEX', 23811],
               ['NL80211_ATTR_IFNAME', 'test'],
               ['NL80211_ATTR_WIPHY', 0],
               ['NL80211_ATTR_IFTYPE', 6],
               ['NL80211_ATTR_WDEV', 4],
               ['NL80211_ATTR_MAC', 'a4:4e:31:43:1c:7c'],
               ['NL80211_ATTR_GENERATION', '02:00:00:00']],
     'cmd': 7,
     'header': {'flags': 0,
                'length': 88,
                'pid': 3292542647,
                'sequence_number': 1430426434,
                'type': 27},
     'reserved': 0,
     'version': 1}

Now you know, how to do a request and what you will get as a
response. Sample collected data is in the `scripts` directory.

Submit changes
--------------

Please do not hesitate to submit the changes on github. Without
your patches this module will not evolve.
'''
from pyroute2.netlink import NLM_F_ACK
from pyroute2.netlink import NLM_F_REQUEST
from pyroute2.netlink import NLM_F_DUMP
from pyroute2.netlink.nl80211 import NL80211
from pyroute2.netlink.nl80211 import nl80211cmd
from pyroute2.netlink.nl80211 import NL80211_NAMES
from pyroute2.netlink.nl80211 import IFTYPE_NAMES


class IW(NL80211):

    def __init__(self, *argv, **kwarg):
        # get specific groups kwarg
        if 'groups' in kwarg:
            groups = kwarg['groups']
            del kwarg['groups']
        else:
            groups = None

        # get specific async kwarg
        if 'async' in kwarg:
            async = kwarg['async']
            del kwarg['async']
        else:
            async = False

        # align groups with async
        if groups is None:
            groups = ~0 if async else 0

        # continue with init
        super(IW, self).__init__(*argv, **kwarg)

        # do automatic bind
        # FIXME: unfortunately we can not omit it here
        self.bind(groups, async)

    def del_interface(self, dev):
        '''
        Delete a virtual interface

            - dev — device index
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_DEL_INTERFACE']
        msg['attrs'] = [['NL80211_ATTR_IFINDEX', dev]]
        self.nlm_request(msg,
                         msg_type=self.prid,
                         msg_flags=NLM_F_REQUEST | NLM_F_ACK)

    def add_interface(self, ifname, iftype, dev=None, phy=0):
        '''
        Create a virtual interface

            - ifname — name of the interface to create
            - iftype — interface type to create
            - dev — device index
            - phy — phy index

        One should specify `dev` (device index) or `phy`
        (phy index). If no one specified, phy == 0.

        `iftype` can be integer or string:

        1. adhoc
        2. station
        3. ap
        4. ap_vlan
        5. wds
        6. monitor
        7. mesh_point
        8. p2p_client
        9. p2p_go
        10. p2p_device
        11. ocb
        '''
        # lookup the interface type
        iftype = IFTYPE_NAMES.get(iftype, iftype)
        assert isinstance(iftype, int)

        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_NEW_INTERFACE']
        msg['attrs'] = [['NL80211_ATTR_IFNAME', ifname],
                        ['NL80211_ATTR_IFTYPE', iftype]]
        if dev is not None:
            msg['attrs'].append(['NL80211_ATTR_IFINDEX', dev])
        elif phy is not None:
            msg['attrs'].append(['NL80211_ATTR_WIPHY', phy])
        else:
            raise TypeError('no device specified')
        self.nlm_request(msg,
                         msg_type=self.prid,
                         msg_flags=NLM_F_REQUEST | NLM_F_ACK)

    def list_wiphy(self):
        '''
        Get all list of phy device
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_GET_WIPHY']
        return self.nlm_request(msg,
                                msg_type=self.prid,
                                msg_flags=NLM_F_REQUEST | NLM_F_DUMP)

    def _get_phy_name(self, attr):
        return 'phy%i' % attr.get_attr('NL80211_ATTR_WIPHY')

    def _get_frequency(self, attr):
        try:
            return attr.get_attr('NL80211_ATTR_WIPHY_FREQ') + 2304
        except:
            return 0

    def get_interfaces_dict(self):
        '''
        Get interfaces dictionary
        '''
        ret = {}
        for wif in self.get_interfaces_dump():
            chan_width = wif.get_attr('NL80211_ATTR_CHANNEL_WIDTH')
            freq = self._get_frequency(wif) if chan_width is not None else 0
            wifname = wif.get_attr('NL80211_ATTR_IFNAME')
            ret[wifname] = [wif.get_attr('NL80211_ATTR_IFINDEX'),
                            self._get_phy_name(wif),
                            wif.get_attr('NL80211_ATTR_MAC'),
                            freq, chan_width]
        return ret

    def get_interfaces_dump(self):
        '''
        Get interfaces dump
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_GET_INTERFACE']
        return self.nlm_request(msg,
                                msg_type=self.prid,
                                msg_flags=NLM_F_REQUEST | NLM_F_DUMP)

    def get_interface_by_phy(self, attr):
        '''
        Get interface by phy ( use x.get_attr('NL80211_ATTR_WIPHY') )
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_GET_INTERFACE']
        msg['attrs'] = [['NL80211_ATTR_WIPHY', attr]]
        return self.nlm_request(msg,
                                msg_type=self.prid,
                                msg_flags=NLM_F_REQUEST | NLM_F_DUMP)

    def get_interface_by_ifindex(self, ifindex):
        '''
        Get interface by ifindex ( use x.get_attr('NL80211_ATTR_IFINDEX')
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_GET_INTERFACE']
        msg['attrs'] = [['NL80211_ATTR_IFINDEX', ifindex]]
        return self.nlm_request(msg,
                                msg_type=self.prid,
                                msg_flags=NLM_F_REQUEST)

    def connect(self, ifindex, ssid, bssid=None):
        '''
        Connect to the ap with ssid and bssid
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_CONNECT']
        msg['attrs'] = [['NL80211_ATTR_IFINDEX', ifindex],
                        ['NL80211_ATTR_SSID', ssid]]
        if bssid is not None:
            msg['attrs'].append(['NL80211_ATTR_MAC', bssid])

        self.nlm_request(msg,
                         msg_type=self.prid,
                         msg_flags=NLM_F_REQUEST | NLM_F_ACK)

    def disconnect(self, ifindex):
        '''
        Disconnect the device
        '''
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_DISCONNECT']
        msg['attrs'] = [['NL80211_ATTR_IFINDEX', ifindex]]
        self.nlm_request(msg,
                         msg_type=self.prid,
                         msg_flags=NLM_F_REQUEST | NLM_F_ACK)

    def scan(self, ifindex):
        '''
        Scan wifi
        '''
        # Prepare a second netlink socket to get the scan results.
        # The issue is that the kernel can send the results notification
        # before we get answer for the NL80211_CMD_TRIGGER_SCAN
        nsock = NL80211()
        nsock.bind()
        nsock.add_membership('scan')

        # send scan request
        msg = nl80211cmd()
        msg['cmd'] = NL80211_NAMES['NL80211_CMD_TRIGGER_SCAN']
        msg['attrs'] = [['NL80211_ATTR_IFINDEX', ifindex]]
        self.nlm_request(msg,
                         msg_type=self.prid,
                         msg_flags=NLM_F_REQUEST | NLM_F_ACK)

        # monitor the results notification on the secondary socket
        scanResultNotFound = True
        while scanResultNotFound:
            listMsg = nsock.get()
            for msg in listMsg:
                if msg["event"] == "NL80211_CMD_NEW_SCAN_RESULTS":
                    scanResultNotFound = False
                    break
        # close the secondary socket
        nsock.close()

        # request the results
        msg2 = nl80211cmd()
        msg2['cmd'] = NL80211_NAMES['NL80211_CMD_GET_SCAN']
        msg2['attrs'] = [['NL80211_ATTR_IFINDEX', ifindex]]
        return self.nlm_request(msg2, msg_type=self.prid,
                                msg_flags=NLM_F_REQUEST | NLM_F_DUMP)
