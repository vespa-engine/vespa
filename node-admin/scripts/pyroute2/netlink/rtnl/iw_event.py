# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
from pyroute2.netlink import nla


class iw_event(nla):

    nla_map = ((0x8B00, 'SIOCSIWCOMMIT', 'hex'),
               (0x8B01, 'SIOCGIWNAME', 'hex'),
               # Basic operations
               (0x8B02, 'SIOCSIWNWID', 'hex'),
               (0x8B03, 'SIOCGIWNWID', 'hex'),
               (0x8B04, 'SIOCSIWFREQ', 'hex'),
               (0x8B05, 'SIOCGIWFREQ', 'hex'),
               (0x8B06, 'SIOCSIWMODE', 'hex'),
               (0x8B07, 'SIOCGIWMODE', 'hex'),
               (0x8B08, 'SIOCSIWSENS', 'hex'),
               (0x8B09, 'SIOCGIWSENS', 'hex'),
               # Informative stuff
               (0x8B0A, 'SIOCSIWRANGE', 'hex'),
               (0x8B0B, 'SIOCGIWRANGE', 'hex'),
               (0x8B0C, 'SIOCSIWPRIV', 'hex'),
               (0x8B0D, 'SIOCGIWPRIV', 'hex'),
               (0x8B0E, 'SIOCSIWSTATS', 'hex'),
               (0x8B0F, 'SIOCGIWSTATS', 'hex'),
               # Spy support (statistics per MAC address -
               # used for Mobile IP support)
               (0x8B10, 'SIOCSIWSPY', 'hex'),
               (0x8B11, 'SIOCGIWSPY', 'hex'),
               (0x8B12, 'SIOCSIWTHRSPY', 'hex'),
               (0x8B13, 'SIOCGIWTHRSPY', 'hex'),
               # Access Point manipulation
               (0x8B14, 'SIOCSIWAP', 'hex'),
               (0x8B15, 'SIOCGIWAP', 'hex'),
               (0x8B17, 'SIOCGIWAPLIST', 'hex'),
               (0x8B18, 'SIOCSIWSCAN', 'hex'),
               (0x8B19, 'SIOCGIWSCAN', 'hex'),
               # 802.11 specific support
               (0x8B1A, 'SIOCSIWESSID', 'hex'),
               (0x8B1B, 'SIOCGIWESSID', 'hex'),
               (0x8B1C, 'SIOCSIWNICKN', 'hex'),
               (0x8B1D, 'SIOCGIWNICKN', 'hex'),
               # Other parameters useful in 802.11 and
               # some other devices
               (0x8B20, 'SIOCSIWRATE', 'hex'),
               (0x8B21, 'SIOCGIWRATE', 'hex'),
               (0x8B22, 'SIOCSIWRTS', 'hex'),
               (0x8B23, 'SIOCGIWRTS', 'hex'),
               (0x8B24, 'SIOCSIWFRAG', 'hex'),
               (0x8B25, 'SIOCGIWFRAG', 'hex'),
               (0x8B26, 'SIOCSIWTXPOW', 'hex'),
               (0x8B27, 'SIOCGIWTXPOW', 'hex'),
               (0x8B28, 'SIOCSIWRETRY', 'hex'),
               (0x8B29, 'SIOCGIWRETRY', 'hex'),
               # Encoding stuff (scrambling, hardware security, WEP...)
               (0x8B2A, 'SIOCSIWENCODE', 'hex'),
               (0x8B2B, 'SIOCGIWENCODE', 'hex'),
               # Power saving stuff (power management, unicast
               # and multicast)
               (0x8B2C, 'SIOCSIWPOWER', 'hex'),
               (0x8B2D, 'SIOCGIWPOWER', 'hex'),
               # WPA : Generic IEEE 802.11 informatiom element
               # (e.g., for WPA/RSN/WMM).
               (0x8B30, 'SIOCSIWGENIE', 'hex'),
               (0x8B31, 'SIOCGIWGENIE', 'hex'),
               # WPA : IEEE 802.11 MLME requests
               (0x8B16, 'SIOCSIWMLME', 'hex'),
               # WPA : Authentication mode parameters
               (0x8B32, 'SIOCSIWAUTH', 'hex'),
               (0x8B33, 'SIOCGIWAUTH', 'hex'),
               # WPA : Extended version of encoding configuration
               (0x8B34, 'SIOCSIWENCODEEXT', 'hex'),
               (0x8B35, 'SIOCGIWENCODEEXT', 'hex'),
               # WPA2 : PMKSA cache management
               (0x8B36, 'SIOCSIWPMKSA', 'hex'),
               # Events s.str.
               (0x8C00, 'IWEVTXDROP', 'hex'),
               (0x8C01, 'IWEVQUAL', 'hex'),
               (0x8C02, 'IWEVCUSTOM', 'hex'),
               (0x8C03, 'IWEVREGISTERED', 'hex'),
               (0x8C04, 'IWEVEXPIRED', 'hex'),
               (0x8C05, 'IWEVGENIE', 'hex'),
               (0x8C06, 'IWEVMICHAELMICFAILURE', 'hex'),
               (0x8C07, 'IWEVASSOCREQIE', 'hex'),
               (0x8C08, 'IWEVASSOCRESPIE', 'hex'),
               (0x8C09, 'IWEVPMKIDCAND', 'hex'))
