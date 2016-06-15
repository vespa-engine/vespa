# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
'''
Netlink basics
==============

General netlink packet structure::

    nlmsg packet:
        + header
        + data

Generic netlink message header::

    nlmsg header:
        + uint32 length
        + uint16 type
        + uint16 flags
        + uint32 sequence number
        + uint32 pid

The `length` field is the length of all the packet, including
data and header. The `type` field is used to distinguish different
message types, commands etc. Please note, that there is no
explicit protocol field -- you choose a netlink protocol, when
you create a socket.

The `sequence number` is very important. Netlink is an asynchronous
protocol -- it means, that the packet order doesn't matter and is
not guaranteed. But responses to a request are always marked with
the same sequence number, so you can treat it as a cookie.

Please keep in mind, that a netlink request can initiate a
cascade of events, and netlink messages from these events can
carry sequence number == 0. E.g., it is so when you remove a
primary IP addr from an interface, when `promote_secondaries`
sysctl is set.

Beside of incapsulated headers and other protocol-specific data,
netlink messages can carry NLA (netlink attributes). NLA
structure is as follows::

    NLA header:
        + uint16 length
        + uint16 type
    NLA data:
        + data-specific struct
        # optional:
        + NLA
        + NLA
        + ...

So, NLA structures can be nested, forming a tree.

Complete structure of a netlink packet::

    nlmsg header:
        + uint32 length
        + uint16 type
        + uint16 flags
        + uint32 sequence number
        + uint32 pid
    [ optional protocol-specific data ]
    [ optional NLA tree ]

More information about netlink protocol you can find in
the man pages.

Pyroute2 and netlink
====================

packets
-------

To simplify the development, pyroute2 provides an easy way to
describe packet structure. As an example, you can take the
ifaddrmsg description -- `pyroute2/netlink/rtnl/ifaddrmsg.py`.

To describe a packet, you need to inherit from `nlmsg` class::

    from pyroute2.netlink import nlmsg

    class foo_msg(nlmsg):
        fields = ( ... )
        nla_map = ( ... )

NLA are described in the same way, but the parent class should be
`nla`, instead of `nlmsg`. And yes, it is important to use the
proper parent class -- it affects the header structure.

fields attribute
----------------

The `fields` attribute describes the structure of the
protocol-specific data. It is a tuple of tuples, where each
member contains a field name and its data format.

Field data format should be specified as for Python `struct`
module. E.g., ifaddrmsg structure::

    ifaddrmsg structure:
        + unsigned char ifa_family
        + unsigned char ifa_prefixlen
        + unsigned char ifa_flags
        + unsigned char ifa_scope
        + int ifa_index

should be described as follows::

    class ifaddrmsg(nlmsg):
        fields = (('family', 'B'),
                  ('prefixlen', 'B'),
                  ('flags', 'B'),
                  ('scope', 'B'),
                  ('index', 'I'))

Format strings are passed directly to the `struct` module,
so you can use all the notations like `>I`, `16s` etc. All
fields are parsed from the stream separately, so if you
want to explicitly fix alignemt, as if it were C struct,
use the `pack` attribute::

    class tstats(nla):
        pack = 'struct'
        fields = (('version', 'H'),
                  ('ac_exitcode', 'I'),
                  ('ac_flag', 'B'),
                  ...)

Explicit padding bytes also can be used, when struct
packing doesn't work well::

    class ipq_mode_msg(nlmsg):
        pack = 'struct'
        fields = (('value', 'B'),
                  ('__pad', '7x'),
                  ('range', 'I'),
                  ('__pad', '12x'))


nla_map attribute
-----------------

The `nla_map` attribute is a tuple of NLA descriptions. Each
description is also a tuple in two different forms: either
two fields, name and format, or three fields: type, name and
format.

Please notice, that the format field is a string name of
corresponding NLA class::

    class ifaddrmsg(nlmsg):
        ...
        nla_map = (('IFA_UNSPEC',  'hex'),
                   ('IFA_ADDRESS', 'ipaddr'),
                   ('IFA_LOCAL', 'ipaddr'),
                   ...)

This code will create mapping, where IFA_ADDRESS NLA will be of
type 1 and IFA_LOCAL -- of type 2, etc. Both NLA will be decoded
as IP addresses (class `ipaddr`). IFA_UNSPEC will be of type 0,
and if it will be in the NLA tree, it will be just dumped in hex.

NLA class names are should be specified as strings, since they
are resolved in runtime.

There are several pre-defined NLA types, that you will get with
`nla` class:

    - none  # forces pyroute2 just to skip this NLA
    - uint8
    - uint16
    - uint32  # there are dedicated NLA of these types as well
    - ipaddr  # IP address, IPv4 or IPv6, depending on the socket
    - l2addr  # MAC address
    - hex     # hex dump as a string -- useful for debugging
    - cdata   # just a binary string
    - asciiz  # zero-terminated ASCII string

Please refer to `pyroute2/netlink/__init__.py` for details.

You can also make your own NLA descriptions::

    class ifaddrmsg(nlmsg):
        ...
        nla_map = (...
                   ('IFA_CACHEINFO', 'cacheinfo'),
                   ...)

        class cacheinfo(nla):
            fields = (('ifa_prefered', 'I'),
                      ('ifa_valid', 'I'),
                      ('cstamp', 'I'),
                      ('tstamp', 'I'))

Custom NLA descriptions should be defined in the same class,
where they are used.

Also, it is possible to use not autogenerated type numbers, as
for ifaddrmsg, but specify them explicitly::

    class iw_event(nla):
        ...
        nla_map = ((0x8B00, 'SIOCSIWCOMMIT', 'hex'),
                   (0x8B01, 'SIOCGIWNAME', 'hex'),
                   (0x8B02, 'SIOCSIWNWID', 'hex'),
                   (0x8B03, 'SIOCGIWNWID', 'hex'),
                   ...)

Here you can see custom NLA type numbers -- 0x8B00, 0x8B01 etc.
It is not permitted to mix these two forms in one class: you should
use ether autogenerated type numbers (two fields tuples), or
explicit numbers (three fields typles).

parsed netlink message
----------------------

Netlink messages are represented by pyroute2 as dictionaries
as follows::

    {'header': {'pid': ...,
                'length: ...,
                'flags': ...,
                'error': None,  # if you are lucky
                'type': ...,
                'sequence_number': ...},

     # fields attributes
     'field_name1': value,
     ...
     'field_nameX': value,

     # nla tree
     'attrs': [['NLA_NAME1', value],
               ...
               ['NLA_NAMEX', value],
               ['NLA_NAMEY', {'field_name1': value,
                              ...
                              'field_nameX': value,
                              'attrs': [['NLA_NAME.... ]]}]]}

As an example, a message from the wireless subsystem about new
scan event::

    {'index': 4,
     'family': 0,
     '__align': 0,
     'header': {'pid': 0,
                'length': 64,
                'flags': 0,
                'error': None,
                'type': 16,
                'sequence_number': 0},
     'flags': 69699,
     'ifi_type': 1,
     'event': 'RTM_NEWLINK',
     'change': 0,
     'attrs': [['IFLA_IFNAME', 'wlp3s0'],
               ['IFLA_WIRELESS',
                {'attrs': [['SIOCGIWSCAN',
                            '00:00:00:00:00:00:00:00:00:00:00:00']]}]]}

create and send messages
------------------------

Using high-level interfaces like `IPRoute` or `IPDB`, you will never
need to manually construct and send netlink messages. But in the case
you really need it, it is simple as well.

Having a description class, like `ifaddrmsg` from above, you need to:

    - instantiate it
    - fill the fields
    - encode the packet
    - send the encoded data

The code::

    from pyroute2.netlink import NLM_F_REQUEST
    from pyroute2.netlink import NLM_F_ACK
    from pyroute2.netlink import NLM_F_CREATE
    from pyroute2.netlink import NLM_F_EXCL
    from pyroute2.iproute import RTM_NEWADDR
    from pyroute2.netlink.rtnl.ifaddrmsg import ifaddrmsg

    ##
    # add an addr to an interface
    #

    # create the message
    msg = ifaddrmsg()

    # fill the protocol-specific fields
    msg['index'] = index  # index of the interface
    msg['family'] = AF_INET  # address family
    msg['prefixlen'] = 24  # the address mask
    msg['scope'] = scope  # see /etc/iproute2/rt_scopes

    # attach NLA -- it MUST be a list / mutable
    msg['attrs'] = [['IFA_LOCAL', '192.168.0.1'],
                    ['IFA_ADDRESS', '192.162.0.1']]

    # fill generic netlink fields
    msg['header']['sequence_number'] = nonce  # an unique seq number
    msg['header']['pid'] = os.getpid()
    msg['header']['type'] = RTM_NEWADDR
    msg['header']['flags'] = NLM_F_REQUEST |\\
                             NLM_F_ACK |\\
                             NLM_F_CREATE |\\
                             NLM_F_EXCL

    # encode the packet
    msg.encode()

    # send the buffer
    nlsock.sendto(msg.buf.getvalue(), (0, 0))

Please notice, that NLA list *MUST* be mutable.

------------------

Module contents:
'''

import traceback
import logging
import socket
import struct
import types
import sys
import io
import re
import os

from pyroute2.common import hexdump
from pyroute2.common import basestring

_letters = re.compile('[A-Za-z]')
_fmt_letters = re.compile('[^!><@=][!><@=]')

##
# That's a hack for the code linter, which works under
# Python3, see unicode reference in the code below
if sys.version[0] == '3':
    unicode = str

NLMSG_MIN_TYPE = 0x10

GENL_NAMSIZ = 16    # length of family name
GENL_MIN_ID = NLMSG_MIN_TYPE
GENL_MAX_ID = 1023

GENL_ADMIN_PERM = 0x01
GENL_CMD_CAP_DO = 0x02
GENL_CMD_CAP_DUMP = 0x04
GENL_CMD_CAP_HASPOL = 0x08

#
# List of reserved static generic netlink identifiers:
#
GENL_ID_GENERATE = 0
GENL_ID_CTRL = NLMSG_MIN_TYPE

#
# Controller
#

CTRL_CMD_UNSPEC = 0x0
CTRL_CMD_NEWFAMILY = 0x1
CTRL_CMD_DELFAMILY = 0x2
CTRL_CMD_GETFAMILY = 0x3
CTRL_CMD_NEWOPS = 0x4
CTRL_CMD_DELOPS = 0x5
CTRL_CMD_GETOPS = 0x6
CTRL_CMD_NEWMCAST_GRP = 0x7
CTRL_CMD_DELMCAST_GRP = 0x8
CTRL_CMD_GETMCAST_GRP = 0x9  # unused


CTRL_ATTR_UNSPEC = 0x0
CTRL_ATTR_FAMILY_ID = 0x1
CTRL_ATTR_FAMILY_NAME = 0x2
CTRL_ATTR_VERSION = 0x3
CTRL_ATTR_HDRSIZE = 0x4
CTRL_ATTR_MAXATTR = 0x5
CTRL_ATTR_OPS = 0x6
CTRL_ATTR_MCAST_GROUPS = 0x7

CTRL_ATTR_OP_UNSPEC = 0x0
CTRL_ATTR_OP_ID = 0x1
CTRL_ATTR_OP_FLAGS = 0x2

CTRL_ATTR_MCAST_GRP_UNSPEC = 0x0
CTRL_ATTR_MCAST_GRP_NAME = 0x1
CTRL_ATTR_MCAST_GRP_ID = 0x2


#  Different Netlink families
#
NETLINK_ROUTE = 0            # Routing/device hook
NETLINK_UNUSED = 1           # Unused number
NETLINK_USERSOCK = 2         # Reserved for user mode socket protocols
NETLINK_FIREWALL = 3         # Firewalling hook
NETLINK_INET_DIAG = 4        # INET socket monitoring
NETLINK_NFLOG = 5            # netfilter/iptables ULOG
NETLINK_XFRM = 6             # ipsec
NETLINK_SELINUX = 7          # SELinux event notifications
NETLINK_ISCSI = 8            # Open-iSCSI
NETLINK_AUDIT = 9            # auditing
NETLINK_FIB_LOOKUP = 10
NETLINK_CONNECTOR = 11
NETLINK_NETFILTER = 12       # netfilter subsystem
NETLINK_IP6_FW = 13
NETLINK_DNRTMSG = 14         # DECnet routing messages
NETLINK_KOBJECT_UEVENT = 15  # Kernel messages to userspace
NETLINK_GENERIC = 16
# leave room for NETLINK_DM (DM Events)
NETLINK_SCSITRANSPORT = 18   # SCSI Transports

# NLA flags
NLA_F_NESTED = 1 << 15
NLA_F_NET_BYTEORDER = 1 << 14

NLMSG_ALIGNTO = 4


class NetlinkError(Exception):
    '''
    Base netlink error
    '''
    def __init__(self, code, msg=None):
        msg = msg or os.strerror(code)
        super(NetlinkError, self).__init__(code, msg)
        self.code = code


class NetlinkDecodeError(Exception):
    '''
    Base decoding error class.

    Incapsulates underlying error for the following analysis
    '''
    def __init__(self, exception):
        self.exception = exception


class NetlinkHeaderDecodeError(NetlinkDecodeError):
    '''
    The error occured while decoding a header
    '''
    pass


class NetlinkDataDecodeError(NetlinkDecodeError):
    '''
    The error occured while decoding the message fields
    '''
    pass


class NetlinkNLADecodeError(NetlinkDecodeError):
    '''
    The error occured while decoding NLA chain
    '''
    pass


def NLMSG_ALIGN(l):
    return (l + NLMSG_ALIGNTO - 1) & ~ (NLMSG_ALIGNTO - 1)


class NotInitialized(Exception):
    pass


# Netlink message flags values (nlmsghdr.flags)
#
NLM_F_REQUEST = 1    # It is request message.
NLM_F_MULTI = 2    # Multipart message, terminated by NLMSG_DONE
NLM_F_ACK = 4    # Reply with ack, with zero or error code
NLM_F_ECHO = 8    # Echo this request
# Modifiers to GET request
NLM_F_ROOT = 0x100    # specify tree    root
NLM_F_MATCH = 0x200    # return all matching
NLM_F_ATOMIC = 0x400    # atomic GET
NLM_F_DUMP = (NLM_F_ROOT | NLM_F_MATCH)
# Modifiers to NEW request
NLM_F_REPLACE = 0x100    # Override existing
NLM_F_EXCL = 0x200    # Do not touch, if it exists
NLM_F_CREATE = 0x400    # Create, if it does not exist
NLM_F_APPEND = 0x800    # Add to end of list

NLMSG_NOOP = 0x1    # Nothing
NLMSG_ERROR = 0x2    # Error
NLMSG_DONE = 0x3    # End of a dump
NLMSG_OVERRUN = 0x4    # Data lost
NLMSG_CONTROL = 0xe    # Custom message type for messaging control
NLMSG_TRANSPORT = 0xf    # Custom message type for NL as a transport
NLMSG_MIN_TYPE = 0x10    # < 0x10: reserved control messages
NLMSG_MAX_LEN = 0xffff  # Max message length

mtypes = {1: 'NLMSG_NOOP',
          2: 'NLMSG_ERROR',
          3: 'NLMSG_DONE',
          4: 'NLMSG_OVERRUN'}

IPRCMD_NOOP = 0
IPRCMD_STOP = 1
IPRCMD_ACK = 2
IPRCMD_ERR = 3
IPRCMD_REGISTER = 4
IPRCMD_RELOAD = 5
IPRCMD_ROUTE = 6
IPRCMD_CONNECT = 7
IPRCMD_DISCONNECT = 8
IPRCMD_SERVE = 9
IPRCMD_SHUTDOWN = 10
IPRCMD_SUBSCRIBE = 11
IPRCMD_UNSUBSCRIBE = 12
IPRCMD_PROVIDE = 13
IPRCMD_REMOVE = 14
IPRCMD_DISCOVER = 15
IPRCMD_UNREGISTER = 16

SOL_NETLINK = 270

NETLINK_ADD_MEMBERSHIP = 1
NETLINK_DROP_MEMBERSHIP = 2
NETLINK_PKTINFO = 3
NETLINK_BROADCAST_ERROR = 4
NETLINK_NO_ENOBUFS = 5
NETLINK_RX_RING = 6
NETLINK_TX_RING = 7


class nlmsg_base(dict):
    '''
    Netlink base class. You do not need to inherit it directly, unless
    you're inventing completely new protocol structure.

    Use nlmsg or nla classes.

    The class provides several methods, but often one need to customize
    only `decode()` and `encode()`.
    '''

    fields = []                  # data field names, to build a dictionary
    header = None                # optional header class
    pack = None                  # pack pragma
    array = False
    nla_map = {}                 # NLA mapping
    nla_flags = 0                # NLA flags
    value_map = {}

    def __init__(self, buf=None, length=None, parent=None, debug=False):
        dict.__init__(self)
        for i in self.fields:
            self[i[0]] = 0  # FIXME: only for number values
        self.raw = None
        self.debug = debug
        self.length = length or 0
        self.parent = parent
        self.offset = 0
        self.prefix = None
        self['attrs'] = []
        self['value'] = NotInitialized
        self.value = NotInitialized
        self.register_nlas()
        self.r_value_map = dict([(x[1], x[0]) for x in self.value_map.items()])
        self.reset(buf)
        self.clean_cbs = []
        if self.header is not None:
            self['header'] = self.header(self.buf)

    def copy(self):
        '''
        Return a decoded copy of the netlink message. Works
        correctly only if the message was encoded, or is
        received from the socket.
        '''
        ret = type(self)(self.buf.getvalue())
        ret.decode()
        return ret

    def reset(self, buf=None):
        '''
        Reset the message buffer. Optionally, set the message
        from the `buf` parameter. This parameter can be either
        string, or io.BytesIO, or dict instance.
        '''
        if isinstance(buf, basestring):
            b = io.BytesIO()
            b.write(buf)
            b.seek(0)
            buf = b
        if isinstance(buf, dict):
            self.setvalue(buf)
            buf = None
        self.buf = buf or io.BytesIO()
        if 'header' in self:
            self['header'].buf = self.buf

    def register_clean_cb(self, cb):
        if self.parent is not None:
            return self.parent.register_clean_cb(cb)
        else:
            self.clean_cbs.append(cb)

    def _strip_one(self, name):
        for i in tuple(self['attrs']):
            if i[0] == name:
                self['attrs'].remove(i)
        return self

    def strip(self, attrs):
        '''
        Remove an NLA from the attrs chain. The `attrs`
        parameter can be either string, or iterable. In
        the latter case, will be stripped NLAs, specified
        in the provided list.
        '''
        if isinstance(attrs, basestring):
            self._strip_one(attrs)
        else:
            for name in attrs:
                self._strip_one(name)
        return self

    def __ops(self, rvalue, op0, op1):
        lvalue = self.getvalue()
        res = self.__class__()
        for key in lvalue:
            if key not in ('header', 'attrs'):
                if op0 == '__sub__':
                    # operator -, complement
                    if (key not in rvalue) or (lvalue[key] != rvalue[key]):
                        res[key] = lvalue[key]
                elif op0 == '__and__':
                    # operator &, intersection
                    if (key in rvalue) and (lvalue[key] == rvalue[key]):
                        res[key] = lvalue[key]
        if 'attrs' in lvalue:
            res['attrs'] = []
            for attr in lvalue['attrs']:
                if isinstance(attr[1], nla):
                    diff = getattr(attr[1], op0)(rvalue.get_attr(attr[0]))
                    if diff is not None:
                        res['attrs'].append([attr[0], diff])
                else:
                    if op0 == '__sub__':
                        # operator -, complement
                        if rvalue.get_attr(attr[0]) != attr[1]:
                            res['attrs'].append(attr)
                    elif op0 == '__and__':
                        # operator &, intersection
                        if rvalue.get_attr(attr[0]) == attr[1]:
                            res['attrs'].append(attr)
        if not len(res):
            return None
        else:
            if 'header' in res:
                del res['header']
            if 'value' in res:
                del res['value']
            if 'attrs' in res and not len(res['attrs']):
                del res['attrs']
            return res

    def __sub__(self, rvalue):
        '''
        Subjunction operation.
        '''
        return self.__ops(rvalue, '__sub__', '__ne__')

    def __and__(self, rvalue):
        '''
        Conjunction operation.
        '''
        return self.__ops(rvalue, '__and__', '__eq__')

    def __eq__(self, rvalue):
        '''
        Having nla, we are able to use it in operations like::

            if nla == 'some value':
                ...
        '''
        lvalue = self.getvalue()
        if lvalue is self:
            for key in self:
                try:
                    assert self.get(key) == rvalue.get(key)
                except Exception:
                    # on any error -- is not equal
                    return False
            return True
        else:
            return lvalue == rvalue

    @classmethod
    def get_size(self):
        size = 0
        for field in self.fields:
            size += struct.calcsize(field[1])
        return size

    @classmethod
    def nla2name(self, name):
        '''
        Convert NLA name into human-friendly name

        Example: IFLA_ADDRESS -> address

        Requires self.prefix to be set
        '''
        return name[(name.find(self.prefix) + 1) * len(self.prefix):].lower()

    @classmethod
    def name2nla(self, name):
        '''
        Convert human-friendly name into NLA name

        Example: address -> IFLA_ADDRESS

        Requires self.prefix to be set
        '''
        name = name.upper()
        if name.find(self.prefix) == -1:
            name = "%s%s" % (self.prefix, name)
        return name

    def reserve(self):
        '''
        Reserve space in the buffer for data. This can be used
        to skip encoding of the header until some fields will
        be known.
        '''
        size = 0
        for i in self.fields:
            size += struct.calcsize(i[1])
        self.buf.seek(size, 1)

    def decode(self):
        '''
        Decode the message. The message should have the `buf`
        attribute initialized. e.g.::

            data = sock.recv(16384)
            msg = ifinfmsg(data)

        If you want to customize the decoding process, override
        the method, but don't forget to call parent's `decode()`::

            class CustomMessage(nlmsg):

                def decode(self):
                    nlmsg.decode(self)
                    ...  # do some custom data tuning
        '''
        self.offset = self.buf.tell()
        # decode the header
        if self.header is not None:
            try:
                self['header'].decode()
                # update length from header
                # it can not be less than 4
                self.length = max(self['header']['length'], 4)
                save = self.buf.tell()
                self.buf.seek(self.offset)
                self.raw = self.buf.read(self.length)
                self.buf.seek(save)
            except Exception as e:
                raise NetlinkHeaderDecodeError(e)
        # handle the array case
        if self.array:
            self.setvalue([])
            while self.buf.tell() < self.offset + self.length:
                cell = type(self)(self.buf, parent=self, debug=self.debug)
                cell.array = False
                cell.decode()
                self.value.append(cell)
        # decode the data
        try:
            if self.pack == 'struct':
                names = []
                formats = []
                for field in self.fields:
                    names.append(field[0])
                    formats.append(field[1])
                fields = ((','.join(names), ''.join(formats)), )
            else:
                fields = self.fields

            for field in fields:
                name = field[0]
                fmt = field[1]

                # 's' and 'z' can be used only in connection with
                # length, encoded in the header
                if field[1] in ('s', 'z'):
                    fmt = '%is' % (self.length - 4)

                size = struct.calcsize(fmt)
                raw = self.buf.read(size)
                actual_size = len(raw)

                # FIXME: adjust string size again
                if field[1] in ('s', 'z'):
                    size = actual_size
                    fmt = '%is' % (actual_size)
                if size == actual_size:
                    value = struct.unpack(fmt, raw)
                    if len(value) == 1:
                        self[name] = value[0]
                        # cut zero-byte from z-strings
                        # 0x00 -- python3; '\0' -- python2
                        if field[1] == 'z' and self[name][-1] in (0x00, '\0'):
                            self[name] = self[name][:-1]
                    else:
                        if self.pack == 'struct':
                            names = name.split(',')
                            values = list(value)
                            for name in names:
                                if name[0] != '_':
                                    self[name] = values.pop(0)
                        else:
                            self[name] = value

                else:
                    # FIXME: log an error
                    pass

        except Exception as e:
            raise NetlinkDataDecodeError(e)
        # decode NLA
        try:
            # align NLA chain start
            self.buf.seek(NLMSG_ALIGN(self.buf.tell()))
            # read NLA chain
            if self.nla_map:
                self.decode_nlas()
        except Exception as e:
            logging.warning(traceback.format_exc())
            raise NetlinkNLADecodeError(e)
        if len(self['attrs']) == 0:
            del self['attrs']
        if self['value'] is NotInitialized:
            del self['value']

    def encode(self):
        '''
        Encode the message into the binary buffer::

            msg.encode()
            sock.send(msg.buf.getvalue())

        If you want to customize the encoding process, override
        the method::

            class CustomMessage(nlmsg):

                def encode(self):
                    ...  # do some custom data tuning
                    nlmsg.encode(self)
        '''
        init = self.buf.tell()
        diff = 0
        # reserve space for the header
        if self.header is not None:
            self['header'].reserve()

        if self.getvalue() is not None:

            payload = b''
            for i in self.fields:
                name = i[0]
                fmt = i[1]
                value = self[name]

                if fmt == 's':
                    length = len(value)
                    fmt = '%is' % (length)
                elif fmt == 'z':
                    length = len(value) + 1
                    fmt = '%is' % (length)

                # in python3 we should force it
                if sys.version[0] == '3':
                    if isinstance(value, str):
                        value = bytes(value, 'utf-8')
                    elif isinstance(value, float):
                        value = int(value)
                elif sys.version[0] == '2':
                    if isinstance(value, unicode):
                        value = value.encode('utf-8')

                try:
                    if fmt[-1] == 'x':
                        payload += struct.pack(fmt)
                    elif type(value) in (list, tuple, set):
                        payload += struct.pack(fmt, *value)
                    else:
                        payload += struct.pack(fmt, value)
                except struct.error:
                    logging.error(traceback.format_exc())
                    logging.error("error pack: %s %s %s" %
                                  (fmt, value, type(value)))
                    raise

            diff = NLMSG_ALIGN(len(payload)) - len(payload)
            self.buf.write(payload)
            self.buf.write(b'\0' * diff)
        # write NLA chain
        if self.nla_map:
            diff = 0
            self.encode_nlas()
        # calculate the size and write it
        if self.header is not None:
            self.update_length(init, diff)

    def update_length(self, start, diff=0):
        save = self.buf.tell()
        self['header']['length'] = save - start - diff
        self.buf.seek(start)
        self['header'].encode()
        self.buf.seek(save)

    def setvalue(self, value):
        if isinstance(value, dict):
            self.update(value)
        else:
            try:
                value = self.r_value_map.get(value, value)
            except TypeError:
                pass
            self['value'] = value
            self.value = value

    def get_encoded(self, attr, default=None):
        '''
        Return the first encoded NLA by name
        '''
        return self.get_attr(attr, default, 'encoded')

    def get_attr(self, attr, default=None, fmt='raw'):
        '''
        Return the first attr by name or None
        '''
        try:
            attrs = self.get_attrs(attr, fmt)
        except KeyError:
            return default
        if attrs:
            return attrs[0]
        else:
            return default

    def get_attrs(self, attr, fmt='raw'):
        '''
        Return attrs by name
        '''
        fmt_map = {'raw': 1,
                   'encoded': 2}
        return [i[fmt_map[fmt]] for i in self['attrs'] if i[0] == attr]

    def getvalue(self):
        '''
        Atomic NLAs return their value in the 'value' field,
        not as a dictionary. Complex NLAs return whole dictionary.
        '''
        if self.value != NotInitialized:
            # value decoded by custom decoder
            return self.value

        if 'value' in self and self['value'] != NotInitialized:
            # raw value got by generic decoder
            return self.value_map.get(self['value'], self['value'])

        return self

    def register_nlas(self):
        '''
        Convert 'nla_map' tuple into two dictionaries for mapping
        and reverse mapping of NLA types.

        ex: given::

            nla_map = (('TCA_HTB_UNSPEC', 'none'),
                       ('TCA_HTB_PARMS', 'htb_parms'),
                       ('TCA_HTB_INIT', 'htb_glob'))

        creates::

            t_nla_map = {0: (<class 'pyroute2...none'>, 'TCA_HTB_UNSPEC'),
                         1: (<class 'pyroute2...htb_parms'>, 'TCA_HTB_PARMS'),
                         2: (<class 'pyroute2...htb_glob'>, 'TCA_HTB_INIT')}
            r_nla_map = {'TCA_HTB_UNSPEC': (<class 'pyroute2...none'>, 0),
                         'TCA_HTB_PARMS': (<class 'pyroute2...htb_parms'>, 1),
                         'TCA_HTB_INIT': (<class 'pyroute2...htb_glob'>, 2)}

        nla_map format::

            nla_map = (([ID, ] NAME, TYPE[, FLAGS]), ...)

        Items in `[...]` are optional. If ID is not given, then the map will
        be autonumerated from 0. If flags are not given, they are 0 by default.

        '''
        # clean up NLA mappings
        self.t_nla_map = {}
        self.r_nla_map = {}

        # work only on non-empty mappings
        if not self.nla_map:
            return

        # fix nla flags
        nla_map = []
        for item in self.nla_map:
            if not isinstance(item[-1], int):
                item = list(item)
                item.append(0)
            nla_map.append(item)

        # detect, whether we have pre-defined keys
        if not isinstance(nla_map[0][0], int):
            # create enumeration
            nla_types = enumerate((i[0] for i in nla_map))
            # that's a little bit tricky, but to reduce
            # the required amount of code in modules, we have
            # to jump over the head
            zipped = [(k[1][0], k[0][0], k[0][1], k[0][2]) for k in
                      zip(nla_map, nla_types)]
        else:
            zipped = nla_map

        for (key, name, nla_class, nla_flags) in zipped:
            # is it an array
            if nla_class[0] == '*':
                nla_class = nla_class[1:]
                array = True
            else:
                array = False
            # lookup NLA class
            if nla_class == 'recursive':
                nla_class = type(self)
            else:
                nla_class = getattr(self, nla_class)
            # update mappings
            self.t_nla_map[key] = (nla_class, name, nla_flags, array)
            self.r_nla_map[name] = (nla_class, key, nla_flags, array)

    def encode_nlas(self):
        '''
        Encode the NLA chain. Should not be called manually, since
        it is called from `encode()` routine.
        '''
        for i in self['attrs']:
            if i[0] in self.r_nla_map:
                msg_class = self.r_nla_map[i[0]][0]
                msg_type = self.r_nla_map[i[0]][1]
                # is it a class or a function?
                if isinstance(msg_class, types.MethodType):
                    # if it is a function -- use it to get the class
                    msg_class = msg_class()
                # encode NLA
                nla = msg_class(self.buf, parent=self)
                nla.nla_flags |= self.r_nla_map[i[0]][2]
                nla['header']['type'] = msg_type | nla.nla_flags
                nla.setvalue(i[1])
                try:
                    nla.encode()
                except:
                    raise
                else:
                    if len(i) == 2:
                        i.append(nla)
                    elif len(i) == 3:
                        i[2] = nla

    def decode_nlas(self):
        '''
        Decode the NLA chain. Should not be called manually, since
        it is called from `decode()` routine.
        '''
        while self.buf.tell() < (self.offset + self.length):
            init = self.buf.tell()
            nla = None
            # pick the length and the type
            (length, msg_type) = struct.unpack('HH', self.buf.read(4))
            # first two bits of msg_type are flags:
            msg_type = msg_type & ~(NLA_F_NESTED | NLA_F_NET_BYTEORDER)
            # rewind to the beginning
            self.buf.seek(init)
            length = min(max(length, 4),
                         (self.length - self.buf.tell() + self.offset))

            # we have a mapping for this NLA
            if msg_type in self.t_nla_map:
                # get the class
                msg_class = self.t_nla_map[msg_type][0]
                # is it a class or a function?
                if isinstance(msg_class, types.MethodType):
                    # if it is a function -- use it to get the class
                    msg_class = msg_class(buf=self.buf, length=length)
                # and the name
                msg_name = self.t_nla_map[msg_type][1]
                # is it an array?
                msg_array = self.t_nla_map[msg_type][3]
                # decode NLA
                nla = msg_class(self.buf, length, self, debug=self.debug)
                nla.array = msg_array
                try:
                    nla.decode()
                    nla.nla_flags = msg_type & (NLA_F_NESTED |
                                                NLA_F_NET_BYTEORDER)
                except Exception:
                    logging.warning("decoding %s" % (msg_name))
                    logging.warning(traceback.format_exc())
                    self.buf.seek(init)
                    msg_name = 'UNDECODED'
                    msg_value = hexdump(self.buf.read(length))
                else:
                    msg_value = nla.getvalue()
            else:
                msg_name = 'UNKNOWN'
                msg_value = hexdump(self.buf.read(length))

            self['attrs'].append([msg_name, msg_value])

            # fix the offset
            self.buf.seek(init + NLMSG_ALIGN(length))


class nla_header(nlmsg_base):
    '''
    The NLA header structure: uin16 length and uint16 type.
    '''
    fields = (('length', 'H'),
              ('type', 'H'))


class nla_base(nlmsg_base):
    '''
    The NLA base class. Use `nla_header` class as the header.
    '''
    header = nla_header


class nlmsg_header(nlmsg_base):
    '''
    Common netlink message header
    '''
    fields = (('length', 'I'),
              ('type', 'H'),
              ('flags', 'H'),
              ('sequence_number', 'I'),
              ('pid', 'I'))


class nlmsg_atoms(nlmsg_base):
    '''
    A collection of base NLA types
    '''
    class none(nla_base):
        '''
        'none' type is used to skip decoding of NLA. You can
        also use 'hex' type to dump NLA's content.
        '''
        def decode(self):
            nla_base.decode(self)
            self.value = None

    class uint8(nla_base):
        fields = [('value', 'B')]

    class uint16(nla_base):
        fields = [('value', 'H')]

    class uint32(nla_base):
        fields = [('value', 'I')]

    class uint64(nla_base):
        fields = [('value', 'Q')]

    class be8(nla_base):
        fields = [('value', '>B')]

    class be16(nla_base):
        fields = [('value', '>H')]

    class be32(nla_base):
        fields = [('value', '>I')]

    class be64(nla_base):
        fields = [('value', '>Q')]

    class ipXaddr(nla_base):
        fields = [('value', 's')]
        family = None

        def encode(self):
            self['value'] = socket.inet_pton(self.family,
                                             self.value)
            nla_base.encode(self)

        def decode(self):
            nla_base.decode(self)
            self.value = socket.inet_ntop(self.family,
                                          self['value'])

    class ip4addr(ipXaddr):
        '''
        Explicit IPv4 address type class.
        '''
        family = socket.AF_INET

    class ip6addr(ipXaddr):
        '''
        Explicit IPv6 address type class.
        '''
        family = socket.AF_INET6

    class ipaddr(nla_base):
        '''
        This class is used to decode IP addresses according to
        the family. Socket library currently supports only two
        families, AF_INET and AF_INET6.

        We do not specify here the string size, it will be
        calculated in runtime.
        '''
        fields = [('value', 's')]
        family_map = {socket.AF_INET: socket.AF_INET,
                      socket.AF_BRIDGE: socket.AF_INET,
                      socket.AF_INET6: socket.AF_INET6}

        def encode(self):
            family = self.family_map[self.parent['family']]
            self['value'] = socket.inet_pton(family, self.value)
            nla_base.encode(self)

        def decode(self):
            nla_base.decode(self)
            family = self.family_map[self.parent['family']]
            self.value = socket.inet_ntop(family, self['value'])

    class l2addr(nla_base):
        '''
        Decode MAC address.
        '''
        fields = [('value', '=6s')]

        def encode(self):
            self['value'] = struct.pack('BBBBBB',
                                        *[int(i, 16) for i in
                                          self.value.split(':')])
            nla_base.encode(self)

        def decode(self):
            nla_base.decode(self)
            self.value = ':'.join('%02x' % (i) for i in
                                  struct.unpack('BBBBBB', self['value']))

    class hex(nla_base):
        '''
        Represent NLA's content with header as hex string.
        '''
        fields = [('value', 's')]

        def decode(self):
            nla_base.decode(self)
            self.value = hexdump(self['value'])

    class cdata(nla_base):
        '''
        Binary data
        '''
        fields = [('value', 's')]

    class asciiz(nla_base):
        '''
        Zero-terminated string.
        '''
        # FIXME: move z-string hacks from general decode here?
        fields = [('value', 'z')]

        def encode(self):
            if isinstance(self['value'], str) and sys.version[0] == '3':
                self['value'] = bytes(self['value'], 'utf-8')
            nla_base.encode(self)

        def decode(self):
            nla_base.decode(self)
            try:
                assert sys.version[0] == '3'
                self.value = self['value'].decode('utf-8')
            except (AssertionError, UnicodeDecodeError):
                self.value = self['value']


class nla(nla_base, nlmsg_atoms):
    '''
    Main NLA class
    '''
    def decode(self):
        nla_base.decode(self)
        if not self.debug:
            del self['header']


class nlmsg(nlmsg_atoms):
    '''
    Main netlink message class
    '''
    header = nlmsg_header


class genlmsg(nlmsg):
    '''
    Generic netlink message
    '''
    fields = (('cmd', 'B'),
              ('version', 'B'),
              ('reserved', 'H'))


class ctrlmsg(genlmsg):
    '''
    Netlink control message
    '''
    # FIXME: to be extended
    nla_map = (('CTRL_ATTR_UNSPEC', 'none'),
               ('CTRL_ATTR_FAMILY_ID', 'uint16'),
               ('CTRL_ATTR_FAMILY_NAME', 'asciiz'),
               ('CTRL_ATTR_VERSION', 'uint32'),
               ('CTRL_ATTR_HDRSIZE', 'uint32'),
               ('CTRL_ATTR_MAXATTR', 'uint32'),
               ('CTRL_ATTR_OPS', '*ops'),
               ('CTRL_ATTR_MCAST_GROUPS', '*mcast_groups'))

    class ops(nla):
        nla_map = (('CTRL_ATTR_OP_UNSPEC', 'none'),
                   ('CTRL_ATTR_OP_ID', 'uint32'),
                   ('CTRL_ATTR_OP_FLAGS', 'uint32'))

    class mcast_groups(nla):
        nla_map = (('CTRL_ATTR_MCAST_GRP_UNSPEC', 'none'),
                   ('CTRL_ATTR_MCAST_GRP_NAME', 'asciiz'),
                   ('CTRL_ATTR_MCAST_GRP_ID', 'uint32'))
