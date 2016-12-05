# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
from pyroute2.netlink import nla
from pyroute2.netlink import NLA_F_NESTED
from pyroute2.netlink import NLA_F_NET_BYTEORDER
from pyroute2.netlink.nfnetlink import nfgen_msg


IPSET_MAXNAMELEN = 32

IPSET_CMD_NONE = 0
IPSET_CMD_PROTOCOL = 1  # Return protocol version
IPSET_CMD_CREATE = 2  # Create a new (empty) set
IPSET_CMD_DESTROY = 3  # Destroy a (empty) set
IPSET_CMD_FLUSH = 4  # Remove all elements from a set
IPSET_CMD_RENAME = 5  # Rename a set
IPSET_CMD_SWAP = 6  # Swap two sets
IPSET_CMD_LIST = 7  # List sets
IPSET_CMD_SAVE = 8  # Save sets
IPSET_CMD_ADD = 9  # Add an element to a set
IPSET_CMD_DEL = 10  # Delete an element from a set
IPSET_CMD_TEST = 11  # Test an element in a set
IPSET_CMD_HEADER = 12  # Get set header data only
IPSET_CMD_TYPE = 13  # 13: Get set type


class ipset_msg(nfgen_msg):
    '''
    Since the support just begins to be developed,
    many attrs are still in `hex` format -- just to
    dump the content.
    '''
    nla_map = (('IPSET_ATTR_UNSPEC', 'none'),
               ('IPSET_ATTR_PROTOCOL', 'uint8'),
               ('IPSET_ATTR_SETNAME', 'asciiz'),
               ('IPSET_ATTR_TYPENAME', 'asciiz'),
               ('IPSET_ATTR_REVISION', 'uint8'),
               ('IPSET_ATTR_FAMILY', 'uint8'),
               ('IPSET_ATTR_FLAGS', 'hex'),
               ('IPSET_ATTR_DATA', 'data'),
               ('IPSET_ATTR_ADT', 'data'),
               ('IPSET_ATTR_LINENO', 'hex'),
               ('IPSET_ATTR_PROTOCOL_MIN', 'hex'))

    class data(nla):
        nla_flags = NLA_F_NESTED
        nla_map = ((0, 'IPSET_ATTR_UNSPEC', 'none'),
                   (1, 'IPSET_ATTR_IP', 'ipset_ip'),
                   (1, 'IPSET_ATTR_IP_FROM', 'ipset_ip'),
                   (2, 'IPSET_ATTR_IP_TO', 'ipset_ip'),
                   (3, 'IPSET_ATTR_CIDR', 'hex'),
                   (4, 'IPSET_ATTR_PORT', 'hex'),
                   (4, 'IPSET_ATTR_PORT_FROM', 'hex'),
                   (5, 'IPSET_ATTR_PORT_TO', 'hex'),
                   (6, 'IPSET_ATTR_TIMEOUT', 'hex'),
                   (7, 'IPSET_ATTR_PROTO', 'recursive'),
                   (8, 'IPSET_ATTR_CADT_FLAGS', 'hex'),
                   (9, 'IPSET_ATTR_CADT_LINENO', 'be32'),
                   (10, 'IPSET_ATTR_MARK', 'hex'),
                   (11, 'IPSET_ATTR_MARKMASK', 'hex'),
                   (17, 'IPSET_ATTR_GC', 'hex'),
                   (18, 'IPSET_ATTR_HASHSIZE', 'be32'),
                   (19, 'IPSET_ATTR_MAXELEM', 'be32'),
                   (20, 'IPSET_ATTR_NETMASK', 'hex'),
                   (21, 'IPSET_ATTR_PROBES', 'hex'),
                   (22, 'IPSET_ATTR_RESIZE', 'hex'),
                   (23, 'IPSET_ATTR_SIZE', 'hex'),
                   (24, 'IPSET_ATTR_ELEMENTS', 'hex'),
                   (25, 'IPSET_ATTR_REFERENCES', 'be32'),
                   (26, 'IPSET_ATTR_MEMSIZE', 'be32'))

        class ipset_ip(nla):
            nla_flags = NLA_F_NESTED
            nla_map = (('IPSET_ATTR_UNSPEC', 'none'),
                       ('IPSET_ATTR_IPADDR_IPV4', 'ip4addr',
                        NLA_F_NET_BYTEORDER),
                       ('IPSET_ATTR_IPADDR_IPV6', 'ip6addr',
                        NLA_F_NET_BYTEORDER))
