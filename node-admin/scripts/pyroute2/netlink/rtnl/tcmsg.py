# By Peter V. Saveliev https://pypi.python.org/pypi/pyroute2. Dual licensed under the Apache 2 and GPLv2+ see https://github.com/svinota/pyroute2 for License details.
import re
import os
import struct

from pyroute2.common import size_suffixes
from pyroute2.common import time_suffixes
from pyroute2.common import rate_suffixes
from pyroute2.common import basestring
from pyroute2.netlink import nlmsg
from pyroute2.netlink import nla


TCA_ACT_MAX_PRIO = 32

LINKLAYER_UNSPEC = 0
LINKLAYER_ETHERNET = 1
LINKLAYER_ATM = 2

ATM_CELL_SIZE = 53
ATM_CELL_PAYLOAD = 48

TC_RED_ECN = 1
TC_RED_HARDDROP = 2
TC_RED_ADAPTATIVE = 4

TIME_UNITS_PER_SEC = 1000000

_psched = open('/proc/net/psched', 'r')
[_t2us,
 _us2t,
 _clock_res,
 _wee] = [int(i, 16) for i in _psched.read().split()]
_clock_factor = float(_clock_res) / TIME_UNITS_PER_SEC
_tick_in_usec = float(_t2us) / _us2t * _clock_factor
_first_letter = re.compile('[^0-9]+')
_psched.close()


def _get_hz():
    if _clock_res == 1000000:
        return _wee
    else:
        return os.environ.get('HZ', 1000)


def _get_by_suffix(value, default, func):
    if not isinstance(value, basestring):
        return value
    pos = _first_letter.search(value)
    if pos is None:
        suffix = default
    else:
        pos = pos.start()
        value, suffix = value[:pos], value[pos:]
    value = int(value)
    return func(value, suffix)


def _get_size(size):
    return _get_by_suffix(size, 'b',
                          lambda x, y: x * size_suffixes[y])


def _get_time(lat):
    return _get_by_suffix(lat, 'ms',
                          lambda x, y: (x * TIME_UNITS_PER_SEC) /
                          time_suffixes[y])


def _get_rate(rate):
    return _get_by_suffix(rate, 'bit',
                          lambda x, y: (x * rate_suffixes[y]) / 8)


def _time2tick(time):
    # The current code is ported from tc utility
    return int(time) * _tick_in_usec


def _calc_xmittime(rate, size):
    # The current code is ported from tc utility
    return int(_time2tick(TIME_UNITS_PER_SEC * (float(size) / rate)))


def _percent2u32(pct):
    '''xlate a percentage to an uint32 value
    0% -> 0
    100% -> 2**32 - 1'''
    return int((2**32 - 1)*pct/100)


def _red_eval_ewma(qmin, burst, avpkt):
    # The current code is ported from tc utility
    wlog = 1
    W = 0.5
    a = float(burst) + 1 - float(qmin) / avpkt
    assert a < 1

    while wlog < 32:
        wlog += 1
        W /= 2
        if (a <= (1 - pow(1 - W, burst)) / W):
            return wlog
    return -1


def _red_eval_P(qmin, qmax, probability):
    # The current code is ported from tc utility
    i = qmax - qmin
    assert i > 0
    assert i < 32

    probability /= i
    while i < 32:
        i += 1
        if probability > 1:
            break
        probability *= 2
    return i


def _get_rate_parameters(kwarg):
    # rate and burst are required
    rate = _get_rate(kwarg['rate'])
    burst = kwarg['burst']

    # if peak, mtu is required
    peak = _get_rate(kwarg.get('peak', 0))
    mtu = kwarg.get('mtu', 0)
    if peak:
        assert mtu

    # limit OR latency is required
    limit = kwarg.get('limit', None)
    latency = _get_time(kwarg.get('latency', None))
    assert limit is not None or latency is not None

    # calculate limit from latency
    if limit is None:
        rate_limit = rate * float(latency) /\
            TIME_UNITS_PER_SEC + burst
        if peak:
            peak_limit = peak * float(latency) /\
                TIME_UNITS_PER_SEC + mtu
            if rate_limit > peak_limit:
                rate_limit = peak_limit
        limit = rate_limit

    return {'rate': int(rate),
            'mtu': mtu,
            'buffer': _calc_xmittime(rate, burst),
            'limit': int(limit)}


def get_tbf_parameters(kwarg):
    parms = _get_rate_parameters(kwarg)
    # fill parameters
    return {'attrs': [['TCA_TBF_PARMS', parms],
                      ['TCA_TBF_RTAB', True]]}


def _get_filter_police_parameter(kwarg):
    # if no limit specified, set it to zero to make
    # the next call happy
    kwarg['limit'] = kwarg.get('limit', 0)
    tbfp = _get_rate_parameters(kwarg)
    # create an alias -- while TBF uses 'buffer', rate
    # policy uses 'burst'
    tbfp['burst'] = tbfp['buffer']
    # action resolver
    actions = nla_plus_police.police.police_tbf.actions
    tbfp['action'] = actions[kwarg.get('action', 'reclassify')]
    police = [['TCA_POLICE_TBF', tbfp],
              ['TCA_POLICE_RATE', True]]
    return police


def get_u32_parameters(kwarg):
    ret = {'attrs': []}

    if kwarg.get('rate'):
        ret['attrs'].append([
            'TCA_U32_POLICE',
            {'attrs': _get_filter_police_parameter(kwarg)}
        ])

    ret['attrs'].append(['TCA_U32_CLASSID', kwarg['target']])
    ret['attrs'].append(['TCA_U32_SEL', {'keys': kwarg['keys']}])

    return ret


def get_fw_parameters(kwarg):
    ret = {'attrs': []}
    attrs_map = (
        ('classid', 'TCA_FW_CLASSID'),
        ('act', 'TCA_FW_ACT'),
        # ('police', 'TCA_FW_POLICE'),
        # Handled in _get_filter_police_parameter
        ('indev', 'TCA_FW_INDEV'),
        ('mask', 'TCA_FW_MASK'),
    )

    if kwarg.get('rate'):
        ret['attrs'].append([
            'TCA_FW_POLICE',
            {'attrs': _get_filter_police_parameter(kwarg)}
        ])

    for k, v in attrs_map:
        r = kwarg.get(k, None)
        if r is not None:
            ret['attrs'].append([v, r])

    return ret


def get_sfq_parameters(kwarg):
    kwarg['quantum'] = _get_size(kwarg.get('quantum', 0))
    kwarg['perturb_period'] = kwarg.get('perturb', 0) or \
        kwarg.get('perturb_period', 0)
    limit = kwarg['limit'] = kwarg.get('limit', 0) or \
        kwarg.get('redflowlimit', 0)
    qth_min = kwarg.get('min', 0)
    qth_max = kwarg.get('max', 0)
    avpkt = kwarg.get('avpkt', 1000)
    probability = kwarg.get('probability', 0.02)
    ecn = kwarg.get('ecn', False)
    harddrop = kwarg.get('harddrop', False)
    kwarg['flags'] = kwarg.get('flags', 0)
    if ecn:
        kwarg['flags'] |= TC_RED_ECN
    if harddrop:
        kwarg['flags'] |= TC_RED_HARDDROP
    if kwarg.get('redflowlimit'):
        qth_max = qth_max or limit / 4
        qth_min = qth_min or qth_max / 3
        kwarg['burst'] = kwarg['burst'] or \
            (2 * qth_min + qth_max) / (3 * avpkt)
        assert limit > qth_max
        assert qth_max > qth_min
        kwarg['qth_min'] = qth_min
        kwarg['qth_max'] = qth_max
        kwarg['Wlog'] = _red_eval_ewma(qth_min, kwarg['burst'], avpkt)
        kwarg['Plog'] = _red_eval_P(qth_min, qth_max, probability)
        assert kwarg['Wlog'] >= 0
        assert kwarg['Plog'] >= 0
        kwarg['max_P'] = int(probability * pow(2, 23))

    return kwarg


def get_htb_class_parameters(kwarg):
    #
    prio = kwarg.get('prio', 0)
    mtu = kwarg.get('mtu', 1600)
    mpu = kwarg.get('mpu', 0)
    overhead = kwarg.get('overhead', 0)
    quantum = kwarg.get('quantum', 0)
    #
    rate = _get_rate(kwarg.get('rate', None))
    ceil = _get_rate(kwarg.get('ceil', 0)) or rate

    burst = kwarg.get('burst', None) or \
        kwarg.get('maxburst', None) or \
        kwarg.get('buffer', None)

    if rate is not None:
        if burst is None:
            burst = rate / _get_hz() + mtu
        burst = _calc_xmittime(rate, burst)

    cburst = kwarg.get('cburst', None) or \
        kwarg.get('cmaxburst', None) or \
        kwarg.get('cbuffer', None)

    if ceil is not None:
        if cburst is None:
            cburst = ceil / _get_hz() + mtu
        cburst = _calc_xmittime(ceil, cburst)

    return {'attrs': [['TCA_HTB_PARMS', {'buffer': burst,
                                         'cbuffer': cburst,
                                         'quantum': quantum,
                                         'prio': prio,
                                         'rate': rate,
                                         'ceil': ceil,
                                         'ceil_overhead': overhead,
                                         'rate_overhead': overhead,
                                         'rate_mpu': mpu,
                                         'ceil_mpu': mpu}],
                      ['TCA_HTB_RTAB', True],
                      ['TCA_HTB_CTAB', True]]}


def get_htb_parameters(kwarg):
    rate2quantum = kwarg.get('r2q', 0xa)
    version = kwarg.get('version', 3)
    defcls = kwarg.get('default', 0x10)

    return {'attrs': [['TCA_HTB_INIT', {'debug': 0,
                                        'defcls': defcls,
                                        'direct_pkts': 0,
                                        'rate2quantum': rate2quantum,
                                        'version': version}]]}


def get_netem_parameters(kwarg):
    delay = _time2tick(kwarg.get('delay', 0))  # in microsecond
    limit = kwarg.get('limit', 1000)  # fifo limit (packets) see netem.c:230
    loss = _percent2u32(kwarg.get('loss', 0))  # int percentage
    gap = kwarg.get('gap', 0)
    duplicate = kwarg.get('duplicate', 0)
    jitter = _time2tick(kwarg.get('jitter', 0))  # in microsecond

    opts = {
        'delay': delay,
        'limit': limit,
        'loss': loss,
        'gap': gap,
        'duplicate': duplicate,
        'jitter': jitter,
        'attrs': []
    }

    # correlation (delay, loss, duplicate)
    delay_corr = _percent2u32(kwarg.get('delay_corr', 0))
    loss_corr = _percent2u32(kwarg.get('loss_corr', 0))
    dup_corr = _percent2u32(kwarg.get('dup_corr', 0))
    if delay_corr or loss_corr or dup_corr:
        # delay_corr requires that both jitter and delay are != 0
        if delay_corr and not (delay and jitter):
            raise Exception('delay correlation requires delay'
                            ' and jitter to be set')
        # loss correlation and loss
        if loss_corr and not loss:
            raise Exception('loss correlation requires loss to be set')
        # duplicate correlation and duplicate
        if dup_corr and not duplicate:
            raise Exception('duplicate correlation requires '
                            'duplicate to be set')

        opts['attrs'].append(['TCA_NETEM_CORR', {'delay_corr': delay_corr,
                                                 'loss_corr': loss_corr,
                                                 'dup_corr': dup_corr}])

    # reorder (probability, correlation)
    prob_reorder = _percent2u32(kwarg.get('prob_reorder', 0))
    corr_reorder = _percent2u32(kwarg.get('corr_reorder', 0))
    if prob_reorder != 0:
        # gap defaults to 1 if equal to 0
        if gap == 0:
            opts['gap'] = gap = 1
        opts['attrs'].append(['TCA_NETEM_REORDER',
                             {'prob_reorder': prob_reorder,
                              'corr_reorder': corr_reorder}])
    else:
        if gap != 0:
            raise Exception('gap can only be set when prob_reorder is set')
        elif corr_reorder != 0:
            raise Exception('corr_reorder can only be set when '
                            'prob_reorder is set')

    # corrupt (probability, correlation)
    prob_corrupt = _percent2u32(kwarg.get('prob_corrupt', 0))
    corr_corrupt = _percent2u32(kwarg.get('corr_corrupt', 0))
    if prob_corrupt:
        opts['attrs'].append(['TCA_NETEM_CORRUPT',
                             {'prob_corrupt': prob_corrupt,
                              'corr_corrupt': corr_corrupt}])
    elif corr_corrupt != 0:
        raise Exception('corr_corrupt can only be set when '
                        'prob_corrupt is set')

    # TODO
    # delay distribution (dist_size, dist_data)
    return opts


class nla_plus_rtab(nla):
    class parms(nla):
        def adjust_size(self, size, mpu, linklayer):
            # The current code is ported from tc utility
            if size < mpu:
                size = mpu

            if linklayer == LINKLAYER_ATM:
                cells = size / ATM_CELL_PAYLOAD
                if size % ATM_CELL_PAYLOAD > 0:
                    cells += 1
                size = cells * ATM_CELL_SIZE

            return size

        def calc_rtab(self, kind):
            # The current code is ported from tc utility
            rtab = []
            mtu = self.get('mtu', 0) or 1600
            cell_log = self['%s_cell_log' % (kind)]
            mpu = self['%s_mpu' % (kind)]
            rate = self.get(kind, 'rate')

            # calculate cell_log
            if cell_log == 0:
                while (mtu >> cell_log) > 255:
                    cell_log += 1

            # fill up the table
            for i in range(256):
                size = self.adjust_size((i + 1) << cell_log,
                                        mpu,
                                        LINKLAYER_ETHERNET)
                rtab.append(_calc_xmittime(rate, size))

            self['%s_cell_align' % (kind)] = -1
            self['%s_cell_log' % (kind)] = cell_log
            return rtab

        def encode(self):
            self.rtab = None
            self.ptab = None
            if self.get('rate', False):
                self.rtab = self.calc_rtab('rate')
            if self.get('peak', False):
                self.ptab = self.calc_rtab('peak')
            if self.get('ceil', False):
                self.ctab = self.calc_rtab('ceil')
            nla.encode(self)

    class rtab(nla):
        fields = (('value', 's'), )

        def encode(self):
            parms = self.parent.get_encoded('TCA_TBF_PARMS') or \
                self.parent.get_encoded('TCA_HTB_PARMS') or \
                self.parent.get_encoded('TCA_POLICE_TBF')
            if parms is not None:
                self.value = getattr(parms, self.__class__.__name__)
                self['value'] = struct.pack('I' * 256,
                                            *(int(x) for x in self.value))
            nla.encode(self)

        def decode(self):
            nla.decode(self)
            parms = self.parent.get_attr('TCA_TBF_PARMS') or \
                self.parent.get_attr('TCA_HTB_PARMS') or \
                self.parent.get_attr('TCA_POLICE_TBF')
            if parms is not None:
                rtab = struct.unpack('I' * (len(self['value']) / 4),
                                     self['value'])
                self.value = rtab
                setattr(parms, self.__class__.__name__, rtab)

    class ptab(rtab):
        pass

    class ctab(rtab):
        pass


class nla_plus_stats2(object):
    class stats2(nla):
        nla_map = (('TCA_STATS_UNSPEC', 'none'),
                   ('TCA_STATS_BASIC', 'basic'),
                   ('TCA_STATS_RATE_EST', 'rate_est'),
                   ('TCA_STATS_QUEUE', 'queue'),
                   ('TCA_STATS_APP', 'hex'))

        class basic(nla):
            fields = (('bytes', 'Q'),
                      ('packets', 'Q'))

        class rate_est(nla):
            fields = (('bps', 'I'),
                      ('pps', 'I'))

        class queue(nla):
            fields = (('qlen', 'I'),
                      ('backlog', 'I'),
                      ('drops', 'I'),
                      ('requeues', 'I'),
                      ('overlimits', 'I'))

    class stats2_hfsc(stats2):
        nla_map = (('TCA_STATS_UNSPEC', 'none'),
                   ('TCA_STATS_BASIC', 'basic'),
                   ('TCA_STATS_RATE_EST', 'rate_est'),
                   ('TCA_STATS_QUEUE', 'queue'),
                   ('TCA_STATS_APP', 'stats_app_hfsc'))

        class stats_app_hfsc(nla):
            fields = (('work', 'Q'),  # total work done
                      ('rtwork', 'Q'),  # total work done by real-time criteria
                      ('period', 'I'),  # current period
                      ('level', 'I'))  # class level in hierarchy


class nla_plus_police(object):
    class police(nla_plus_rtab):
        nla_map = (('TCA_POLICE_UNSPEC', 'none'),
                   ('TCA_POLICE_TBF', 'police_tbf'),
                   ('TCA_POLICE_RATE', 'rtab'),
                   ('TCA_POLICE_PEAKRATE', 'ptab'),
                   ('TCA_POLICE_AVRATE', 'uint32'),
                   ('TCA_POLICE_RESULT', 'uint32'))

        class police_tbf(nla_plus_rtab.parms):
            fields = (('index', 'I'),
                      ('action', 'i'),
                      ('limit', 'I'),
                      ('burst', 'I'),
                      ('mtu', 'I'),
                      ('rate_cell_log', 'B'),
                      ('rate___reserved', 'B'),
                      ('rate_overhead', 'H'),
                      ('rate_cell_align', 'h'),
                      ('rate_mpu', 'H'),
                      ('rate', 'I'),
                      ('peak_cell_log', 'B'),
                      ('peak___reserved', 'B'),
                      ('peak_overhead', 'H'),
                      ('peak_cell_align', 'h'),
                      ('peak_mpu', 'H'),
                      ('peak', 'I'),
                      ('refcnt', 'i'),
                      ('bindcnt', 'i'),
                      ('capab', 'I'))

            actions = {'unspec': -1,     # TC_POLICE_UNSPEC
                       'ok': 0,          # TC_POLICE_OK
                       'reclassify': 1,  # TC_POLICE_RECLASSIFY
                       'shot': 2,        # TC_POLICE_SHOT
                       'drop': 2,        # TC_POLICE_SHOT
                       'pipe': 3}        # TC_POLICE_PIPE


class tcmsg(nlmsg, nla_plus_stats2):

    prefix = 'TCA_'

    fields = (('family', 'B'),
              ('pad1', 'B'),
              ('pad2', 'H'),
              ('index', 'i'),
              ('handle', 'I'),
              ('parent', 'I'),
              ('info', 'I'))

    nla_map = (('TCA_UNSPEC', 'none'),
               ('TCA_KIND', 'asciiz'),
               ('TCA_OPTIONS', 'get_options'),
               ('TCA_STATS', 'stats'),
               ('TCA_XSTATS', 'get_xstats'),
               ('TCA_RATE', 'hex'),
               ('TCA_FCNT', 'hex'),
               ('TCA_STATS2', 'get_stats2'),
               ('TCA_STAB', 'hex'))

    class stats(nla):
        fields = (('bytes', 'Q'),
                  ('packets', 'I'),
                  ('drop', 'I'),
                  ('overlimits', 'I'),
                  ('bps', 'I'),
                  ('pps', 'I'),
                  ('qlen', 'I'),
                  ('backlog', 'I'))

    def get_stats2(self, *argv, **kwarg):
        kind = self.get_attr('TCA_KIND')
        if kind == 'hfsc':
            return self.stats2_hfsc
        return self.stats2

    def get_xstats(self, *argv, **kwarg):
        kind = self.get_attr('TCA_KIND')
        if kind == 'htb':
            return self.xstats_htb
        return self.hex

    class xstats_htb(nla):
        fields = (('lends', 'I'),
                  ('borrows', 'I'),
                  ('giants', 'I'),
                  ('tokens', 'I'),
                  ('ctokens', 'I'))

    def get_options(self, *argv, **kwarg):
        kind = self.get_attr('TCA_KIND')
        if kind == 'ingress':
            return self.options_ingress
        elif kind == 'pfifo_fast':
            return self.options_pfifo_fast
        elif kind == 'tbf':
            return self.options_tbf
        elif kind == 'sfq':
            if kwarg.get('length', 0) >= self.options_sfq_v1.get_size():
                return self.options_sfq_v1
            else:
                return self.options_sfq_v0
        elif kind == 'hfsc':
            return self.options_hfsc
        elif kind == 'htb':
            return self.options_htb
        elif kind == 'netem':
            return self.options_netem
        elif kind == 'u32':
            return self.options_u32
        elif kind == 'fw':
            return self.options_fw
        return self.hex

    class options_ingress(nla):
        fields = (('value', 'I'), )

    class options_hfsc(nla):
        nla_map = (('TCA_HFSC_UNSPEC', 'hfsc_qopt'),
                   ('TCA_HFSC_RSC', 'hfsc_curve'),  # real-time curve
                   ('TCA_HFSC_FSC', 'hfsc_curve'),  # link-share curve
                   ('TCA_HFSC_USC', 'hfsc_curve'))  # upper-limit curve

        class hfsc_qopt(nla):
            fields = (('defcls', 'H'),)  # default class

        class hfsc_curve(nla):
            fields = (('m1', 'I'),  # slope of the first segment in bps
                      ('d', 'I'),  # x-projection of the first segment in us
                      ('m2', 'I'))  # slope of the second segment in bps

    class options_htb(nla_plus_rtab):
        nla_map = (('TCA_HTB_UNSPEC', 'none'),
                   ('TCA_HTB_PARMS', 'htb_parms'),
                   ('TCA_HTB_INIT', 'htb_glob'),
                   ('TCA_HTB_CTAB', 'ctab'),
                   ('TCA_HTB_RTAB', 'rtab'))

        class htb_glob(nla):
            fields = (('version', 'I'),
                      ('rate2quantum', 'I'),
                      ('defcls', 'I'),
                      ('debug', 'I'),
                      ('direct_pkts', 'I'))

        class htb_parms(nla_plus_rtab.parms):
            fields = (('rate_cell_log', 'B'),
                      ('rate___reserved', 'B'),
                      ('rate_overhead', 'H'),
                      ('rate_cell_align', 'h'),
                      ('rate_mpu', 'H'),
                      ('rate', 'I'),
                      ('ceil_cell_log', 'B'),
                      ('ceil___reserved', 'B'),
                      ('ceil_overhead', 'H'),
                      ('ceil_cell_align', 'h'),
                      ('ceil_mpu', 'H'),
                      ('ceil', 'I'),
                      ('buffer', 'I'),
                      ('cbuffer', 'I'),
                      ('quantum', 'I'),
                      ('level', 'I'),
                      ('prio', 'I'))

    class options_netem(nla):
        nla_map = (('TCA_NETEM_UNSPEC', 'none'),
                   ('TCA_NETEM_CORR', 'netem_corr'),
                   ('TCA_NETEM_DELAY_DIST', 'none'),
                   ('TCA_NETEM_REORDER', 'netem_reorder'),
                   ('TCA_NETEM_CORRUPT', 'netem_corrupt'),
                   ('TCA_NETEM_LOSS', 'none'),
                   ('TCA_NETEM_RATE', 'none'))

        fields = (('delay', 'I'),
                  ('limit', 'I'),
                  ('loss', 'I'),
                  ('gap', 'I'),
                  ('duplicate', 'I'),
                  ('jitter', 'I'))

        class netem_corr(nla):
            '''correlation'''
            fields = (('delay_corr', 'I'),
                      ('loss_corr', 'I'),
                      ('dup_corr', 'I'))

        class netem_reorder(nla):
            '''reorder has probability and correlation'''
            fields = (('prob_reorder', 'I'),
                      ('corr_reorder', 'I'))

        class netem_corrupt(nla):
            '''corruption has probability and correlation'''
            fields = (('prob_corrupt', 'I'),
                      ('corr_corrupt', 'I'))

    class options_fw(nla, nla_plus_police):
        nla_map = (('TCA_FW_UNSPEC', 'none'),
                   ('TCA_FW_CLASSID', 'uint32'),
                   ('TCA_FW_POLICE', 'police'),  # TODO string?
                   ('TCA_FW_INDEV', 'hex'),  # TODO string
                   ('TCA_FW_ACT', 'hex'),  # TODO
                   ('TCA_FW_MASK', 'uint32'))

    class options_u32(nla, nla_plus_police):
        nla_map = (('TCA_U32_UNSPEC', 'none'),
                   ('TCA_U32_CLASSID', 'uint32'),
                   ('TCA_U32_HASH', 'uint32'),
                   ('TCA_U32_LINK', 'hex'),
                   ('TCA_U32_DIVISOR', 'uint32'),
                   ('TCA_U32_SEL', 'u32_sel'),
                   ('TCA_U32_POLICE', 'police'),
                   ('TCA_U32_ACT', 'tca_act_prio'),
                   ('TCA_U32_INDEV', 'hex'),
                   ('TCA_U32_PCNT', 'u32_pcnt'),
                   ('TCA_U32_MARK', 'u32_mark'))

        class tca_act_prio(nla):
            nla_map = tuple([('TCA_ACT_PRIO_%i' % x, 'tca_act') for x
                             in range(TCA_ACT_MAX_PRIO)])

            class tca_act(nla, nla_plus_police, nla_plus_stats2):
                nla_map = (('TCA_ACT_UNSPEC', 'none'),
                           ('TCA_ACT_KIND', 'asciiz'),
                           ('TCA_ACT_OPTIONS', 'police'),
                           ('TCA_ACT_INDEX', 'hex'),
                           ('TCA_ACT_STATS', 'stats2'))

        class u32_sel(nla):
            fields = (('flags', 'B'),
                      ('offshift', 'B'),
                      ('nkeys', 'B'),
                      ('__align', 'B'),
                      ('offmask', '>H'),
                      ('off', 'H'),
                      ('offoff', 'h'),
                      ('hoff', 'h'),
                      ('hmask', '>I'))

            class u32_key(nlmsg):
                header = None
                fields = (('key_mask', '>I'),
                          ('key_val', '>I'),
                          ('key_off', 'i'),
                          ('key_offmask', 'i'))

            def encode(self):
                '''
                Key sample::

                    'keys': ['0x0006/0x00ff+8',
                             '0x0000/0xffc0+2',
                             '0x5/0xf+0',
                             '0x10/0xff+33']

                    => 00060000/00ff0000 + 8
                       05000000/0f00ffc0 + 0
                       00100000/00ff0000 + 32
                '''

                def cut_field(key, separator):
                    '''
                    split a field from the end of the string
                    '''
                    field = '0'
                    pos = key.find(separator)
                    new_key = key
                    if pos > 0:
                        field = key[pos + 1:]
                        new_key = key[:pos]
                    return (new_key, field)

                # 'header' array to pack keys to
                header = [(0, 0) for i in range(256)]

                keys = []
                # iterate keys and pack them to the 'header'
                for key in self['keys']:
                    # TODO tags: filter
                    (key, nh) = cut_field(key, '@')  # FIXME: do not ignore nh
                    (key, offset) = cut_field(key, '+')
                    offset = int(offset, 0)
                    # a little trick: if you provide /00ff+8, that
                    # really means /ff+9, so we should take it into
                    # account
                    (key, mask) = cut_field(key, '/')
                    if mask[:2] == '0x':
                        mask = mask[2:]
                        while True:
                            if mask[:2] == '00':
                                offset += 1
                                mask = mask[2:]
                            else:
                                break
                        mask = '0x' + mask
                    mask = int(mask, 0)
                    value = int(key, 0)
                    bits = 24
                    if mask == 0 and value == 0:
                        key = self.u32_key(self.buf)
                        key['key_off'] = offset
                        key['key_mask'] = mask
                        key['key_val'] = value
                        keys.append(key)
                    for bmask in struct.unpack('4B', struct.pack('>I', mask)):
                        if bmask > 0:
                            bvalue = (value & (bmask << bits)) >> bits
                            header[offset] = (bvalue, bmask)
                            offset += 1
                        bits -= 8

                # recalculate keys from 'header'
                key = None
                value = 0
                mask = 0
                for offset in range(256):
                    (bvalue, bmask) = header[offset]
                    if bmask > 0 and key is None:
                        key = self.u32_key(self.buf)
                        key['key_off'] = offset
                        key['key_mask'] = 0
                        key['key_val'] = 0
                        bits = 24
                    if key is not None and bits >= 0:
                        key['key_mask'] |= bmask << bits
                        key['key_val'] |= bvalue << bits
                        bits -= 8
                        if (bits < 0 or offset == 255):
                            keys.append(key)
                            key = None

                assert keys
                self['nkeys'] = len(keys)
                # FIXME: do not hardcode flags :)
                self['flags'] = 1
                start = self.buf.tell()

                nla.encode(self)
                for key in keys:
                    key.encode()
                self.update_length(start)

            def decode(self):
                nla.decode(self)
                self['keys'] = []
                nkeys = self['nkeys']
                while nkeys:
                    key = self.u32_key(self.buf)
                    key.decode()
                    self['keys'].append(key)
                    nkeys -= 1

        class u32_mark(nla):
            fields = (('val', 'I'),
                      ('mask', 'I'),
                      ('success', 'I'))

        class u32_pcnt(nla):
            fields = (('rcnt', 'Q'),
                      ('rhit', 'Q'),
                      ('kcnts', 'Q'))

    class options_pfifo_fast(nla):
        fields = (('bands', 'i'),
                  ('priomap', '16B'))

    class options_tbf(nla_plus_rtab):
        nla_map = (('TCA_TBF_UNSPEC', 'none'),
                   ('TCA_TBF_PARMS', 'tbf_parms'),
                   ('TCA_TBF_RTAB', 'rtab'),
                   ('TCA_TBF_PTAB', 'ptab'))

        class tbf_parms(nla_plus_rtab.parms):
            fields = (('rate_cell_log', 'B'),
                      ('rate___reserved', 'B'),
                      ('rate_overhead', 'H'),
                      ('rate_cell_align', 'h'),
                      ('rate_mpu', 'H'),
                      ('rate', 'I'),
                      ('peak_cell_log', 'B'),
                      ('peak___reserved', 'B'),
                      ('peak_overhead', 'H'),
                      ('peak_cell_align', 'h'),
                      ('peak_mpu', 'H'),
                      ('peak', 'I'),
                      ('limit', 'I'),
                      ('buffer', 'I'),
                      ('mtu', 'I'))

    class options_sfq_v0(nla):
        fields = (('quantum', 'I'),
                  ('perturb_period', 'i'),
                  ('limit', 'I'),
                  ('divisor', 'I'),
                  ('flows', 'I'))

    class options_sfq_v1(nla):
        fields = (('quantum', 'I'),
                  ('perturb_period', 'i'),
                  ('limit_v0', 'I'),
                  ('divisor', 'I'),
                  ('flows', 'I'),
                  ('depth', 'I'),
                  ('headdrop', 'I'),
                  ('limit_v1', 'I'),
                  ('qth_min', 'I'),
                  ('qth_max', 'I'),
                  ('Wlog', 'B'),
                  ('Plog', 'B'),
                  ('Scell_log', 'B'),
                  ('flags', 'B'),
                  ('max_P', 'I'),
                  ('prob_drop', 'I'),
                  ('forced_drop', 'I'),
                  ('prob_mark', 'I'),
                  ('forced_mark', 'I'),
                  ('prob_mark_head', 'I'),
                  ('forced_mark_head', 'I'))
