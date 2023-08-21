import { g as lg, c as $l } from "./index-4acb67de.js";
function w8(e, t) {
  for (var n = 0; n < t.length; n++) {
    const r = t[n];
    if (typeof r != "string" && !Array.isArray(r)) {
      for (const o in r)
        if (o !== "default" && !(o in e)) {
          const a = Object.getOwnPropertyDescriptor(r, o);
          a &&
            Object.defineProperty(
              e,
              o,
              a.get ? a : { enumerable: !0, get: () => r[o] }
            );
        }
    }
  }
  return Object.freeze(
    Object.defineProperty(e, Symbol.toStringTag, { value: "Module" })
  );
}
var PS = { exports: {} },
  Ad = {},
  OS = { exports: {} },
  _e = {};
/**
 * @license React
 * react.production.min.js
 *
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */ var js = Symbol.for("react.element"),
  b8 = Symbol.for("react.portal"),
  x8 = Symbol.for("react.fragment"),
  S8 = Symbol.for("react.strict_mode"),
  P8 = Symbol.for("react.profiler"),
  O8 = Symbol.for("react.provider"),
  E8 = Symbol.for("react.context"),
  $8 = Symbol.for("react.forward_ref"),
  C8 = Symbol.for("react.suspense"),
  k8 = Symbol.for("react.memo"),
  R8 = Symbol.for("react.lazy"),
  __ = Symbol.iterator;
function N8(e) {
  return e === null || typeof e != "object"
    ? null
    : ((e = (__ && e[__]) || e["@@iterator"]),
      typeof e == "function" ? e : null);
}
var ES = {
    isMounted: function () {
      return !1;
    },
    enqueueForceUpdate: function () {},
    enqueueReplaceState: function () {},
    enqueueSetState: function () {},
  },
  $S = Object.assign,
  CS = {};
function Qa(e, t, n) {
  (this.props = e),
    (this.context = t),
    (this.refs = CS),
    (this.updater = n || ES);
}
Qa.prototype.isReactComponent = {};
Qa.prototype.setState = function (e, t) {
  if (typeof e != "object" && typeof e != "function" && e != null)
    throw Error(
      "setState(...): takes an object of state variables to update or a function which returns an object of state variables."
    );
  this.updater.enqueueSetState(this, e, t, "setState");
};
Qa.prototype.forceUpdate = function (e) {
  this.updater.enqueueForceUpdate(this, e, "forceUpdate");
};
function kS() {}
kS.prototype = Qa.prototype;
function sg(e, t, n) {
  (this.props = e),
    (this.context = t),
    (this.refs = CS),
    (this.updater = n || ES);
}
var ug = (sg.prototype = new kS());
ug.constructor = sg;
$S(ug, Qa.prototype);
ug.isPureReactComponent = !0;
var w_ = Array.isArray,
  RS = Object.prototype.hasOwnProperty,
  cg = { current: null },
  NS = { key: !0, ref: !0, __self: !0, __source: !0 };
function IS(e, t, n) {
  var r,
    o = {},
    a = null,
    s = null;
  if (t != null)
    for (r in (t.ref !== void 0 && (s = t.ref),
    t.key !== void 0 && (a = "" + t.key),
    t))
      RS.call(t, r) && !NS.hasOwnProperty(r) && (o[r] = t[r]);
  var u = arguments.length - 2;
  if (u === 1) o.children = n;
  else if (1 < u) {
    for (var f = Array(u), d = 0; d < u; d++) f[d] = arguments[d + 2];
    o.children = f;
  }
  if (e && e.defaultProps)
    for (r in ((u = e.defaultProps), u)) o[r] === void 0 && (o[r] = u[r]);
  return {
    $$typeof: js,
    type: e,
    key: a,
    ref: s,
    props: o,
    _owner: cg.current,
  };
}
function I8(e, t) {
  return {
    $$typeof: js,
    type: e.type,
    key: t,
    ref: e.ref,
    props: e.props,
    _owner: e._owner,
  };
}
function fg(e) {
  return typeof e == "object" && e !== null && e.$$typeof === js;
}
function T8(e) {
  var t = { "=": "=0", ":": "=2" };
  return (
    "$" +
    e.replace(/[=:]/g, function (n) {
      return t[n];
    })
  );
}
var b_ = /\/+/g;
function Wm(e, t) {
  return typeof e == "object" && e !== null && e.key != null
    ? T8("" + e.key)
    : t.toString(36);
}
function Nc(e, t, n, r, o) {
  var a = typeof e;
  (a === "undefined" || a === "boolean") && (e = null);
  var s = !1;
  if (e === null) s = !0;
  else
    switch (a) {
      case "string":
      case "number":
        s = !0;
        break;
      case "object":
        switch (e.$$typeof) {
          case js:
          case b8:
            s = !0;
        }
    }
  if (s)
    return (
      (s = e),
      (o = o(s)),
      (e = r === "" ? "." + Wm(s, 0) : r),
      w_(o)
        ? ((n = ""),
          e != null && (n = e.replace(b_, "$&/") + "/"),
          Nc(o, t, n, "", function (d) {
            return d;
          }))
        : o != null &&
          (fg(o) &&
            (o = I8(
              o,
              n +
                (!o.key || (s && s.key === o.key)
                  ? ""
                  : ("" + o.key).replace(b_, "$&/") + "/") +
                e
            )),
          t.push(o)),
      1
    );
  if (((s = 0), (r = r === "" ? "." : r + ":"), w_(e)))
    for (var u = 0; u < e.length; u++) {
      a = e[u];
      var f = r + Wm(a, u);
      s += Nc(a, t, n, f, o);
    }
  else if (((f = N8(e)), typeof f == "function"))
    for (e = f.call(e), u = 0; !(a = e.next()).done; )
      (a = a.value), (f = r + Wm(a, u++)), (s += Nc(a, t, n, f, o));
  else if (a === "object")
    throw (
      ((t = String(e)),
      Error(
        "Objects are not valid as a React child (found: " +
          (t === "[object Object]"
            ? "object with keys {" + Object.keys(e).join(", ") + "}"
            : t) +
          "). If you meant to render a collection of children, use an array instead."
      ))
    );
  return s;
}
function Yu(e, t, n) {
  if (e == null) return e;
  var r = [],
    o = 0;
  return (
    Nc(e, r, "", "", function (a) {
      return t.call(n, a, o++);
    }),
    r
  );
}
function z8(e) {
  if (e._status === -1) {
    var t = e._result;
    (t = t()),
      t.then(
        function (n) {
          (e._status === 0 || e._status === -1) &&
            ((e._status = 1), (e._result = n));
        },
        function (n) {
          (e._status === 0 || e._status === -1) &&
            ((e._status = 2), (e._result = n));
        }
      ),
      e._status === -1 && ((e._status = 0), (e._result = t));
  }
  if (e._status === 1) return e._result.default;
  throw e._result;
}
var Vt = { current: null },
  Ic = { transition: null },
  A8 = {
    ReactCurrentDispatcher: Vt,
    ReactCurrentBatchConfig: Ic,
    ReactCurrentOwner: cg,
  };
_e.Children = {
  map: Yu,
  forEach: function (e, t, n) {
    Yu(
      e,
      function () {
        t.apply(this, arguments);
      },
      n
    );
  },
  count: function (e) {
    var t = 0;
    return (
      Yu(e, function () {
        t++;
      }),
      t
    );
  },
  toArray: function (e) {
    return (
      Yu(e, function (t) {
        return t;
      }) || []
    );
  },
  only: function (e) {
    if (!fg(e))
      throw Error(
        "React.Children.only expected to receive a single React element child."
      );
    return e;
  },
};
_e.Component = Qa;
_e.Fragment = x8;
_e.Profiler = P8;
_e.PureComponent = sg;
_e.StrictMode = S8;
_e.Suspense = C8;
_e.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = A8;
_e.cloneElement = function (e, t, n) {
  if (e == null)
    throw Error(
      "React.cloneElement(...): The argument must be a React element, but you passed " +
        e +
        "."
    );
  var r = $S({}, e.props),
    o = e.key,
    a = e.ref,
    s = e._owner;
  if (t != null) {
    if (
      (t.ref !== void 0 && ((a = t.ref), (s = cg.current)),
      t.key !== void 0 && (o = "" + t.key),
      e.type && e.type.defaultProps)
    )
      var u = e.type.defaultProps;
    for (f in t)
      RS.call(t, f) &&
        !NS.hasOwnProperty(f) &&
        (r[f] = t[f] === void 0 && u !== void 0 ? u[f] : t[f]);
  }
  var f = arguments.length - 2;
  if (f === 1) r.children = n;
  else if (1 < f) {
    u = Array(f);
    for (var d = 0; d < f; d++) u[d] = arguments[d + 2];
    r.children = u;
  }
  return { $$typeof: js, type: e.type, key: o, ref: a, props: r, _owner: s };
};
_e.createContext = function (e) {
  return (
    (e = {
      $$typeof: E8,
      _currentValue: e,
      _currentValue2: e,
      _threadCount: 0,
      Provider: null,
      Consumer: null,
      _defaultValue: null,
      _globalName: null,
    }),
    (e.Provider = { $$typeof: O8, _context: e }),
    (e.Consumer = e)
  );
};
_e.createElement = IS;
_e.createFactory = function (e) {
  var t = IS.bind(null, e);
  return (t.type = e), t;
};
_e.createRef = function () {
  return { current: null };
};
_e.forwardRef = function (e) {
  return { $$typeof: $8, render: e };
};
_e.isValidElement = fg;
_e.lazy = function (e) {
  return { $$typeof: R8, _payload: { _status: -1, _result: e }, _init: z8 };
};
_e.memo = function (e, t) {
  return { $$typeof: k8, type: e, compare: t === void 0 ? null : t };
};
_e.startTransition = function (e) {
  var t = Ic.transition;
  Ic.transition = {};
  try {
    e();
  } finally {
    Ic.transition = t;
  }
};
_e.unstable_act = function () {
  throw Error("act(...) is not supported in production builds of React.");
};
_e.useCallback = function (e, t) {
  return Vt.current.useCallback(e, t);
};
_e.useContext = function (e) {
  return Vt.current.useContext(e);
};
_e.useDebugValue = function () {};
_e.useDeferredValue = function (e) {
  return Vt.current.useDeferredValue(e);
};
_e.useEffect = function (e, t) {
  return Vt.current.useEffect(e, t);
};
_e.useId = function () {
  return Vt.current.useId();
};
_e.useImperativeHandle = function (e, t, n) {
  return Vt.current.useImperativeHandle(e, t, n);
};
_e.useInsertionEffect = function (e, t) {
  return Vt.current.useInsertionEffect(e, t);
};
_e.useLayoutEffect = function (e, t) {
  return Vt.current.useLayoutEffect(e, t);
};
_e.useMemo = function (e, t) {
  return Vt.current.useMemo(e, t);
};
_e.useReducer = function (e, t, n) {
  return Vt.current.useReducer(e, t, n);
};
_e.useRef = function (e) {
  return Vt.current.useRef(e);
};
_e.useState = function (e) {
  return Vt.current.useState(e);
};
_e.useSyncExternalStore = function (e, t, n) {
  return Vt.current.useSyncExternalStore(e, t, n);
};
_e.useTransition = function () {
  return Vt.current.useTransition();
};
_e.version = "18.2.0";
OS.exports = _e;
var y = OS.exports;
const R = lg(y),
  qc = w8({ __proto__: null, default: R }, [y]);
/**
 * @license React
 * react-jsx-runtime.production.min.js
 *
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */ var L8 = y,
  D8 = Symbol.for("react.element"),
  M8 = Symbol.for("react.fragment"),
  j8 = Object.prototype.hasOwnProperty,
  F8 = L8.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED.ReactCurrentOwner,
  W8 = { key: !0, ref: !0, __self: !0, __source: !0 };
function TS(e, t, n) {
  var r,
    o = {},
    a = null,
    s = null;
  n !== void 0 && (a = "" + n),
    t.key !== void 0 && (a = "" + t.key),
    t.ref !== void 0 && (s = t.ref);
  for (r in t) j8.call(t, r) && !W8.hasOwnProperty(r) && (o[r] = t[r]);
  if (e && e.defaultProps)
    for (r in ((t = e.defaultProps), t)) o[r] === void 0 && (o[r] = t[r]);
  return {
    $$typeof: D8,
    type: e,
    key: a,
    ref: s,
    props: o,
    _owner: F8.current,
  };
}
Ad.Fragment = M8;
Ad.jsx = TS;
Ad.jsxs = TS;
PS.exports = Ad;
var cK = PS.exports,
  x_ = {},
  zS = { exports: {} },
  xn = {},
  AS = { exports: {} },
  LS = {};
/**
 * @license React
 * scheduler.production.min.js
 *
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */ (function (e) {
  function t(Y, re) {
    var ne = Y.length;
    Y.push(re);
    e: for (; 0 < ne; ) {
      var le = (ne - 1) >>> 1,
        ze = Y[le];
      if (0 < o(ze, re)) (Y[le] = re), (Y[ne] = ze), (ne = le);
      else break e;
    }
  }
  function n(Y) {
    return Y.length === 0 ? null : Y[0];
  }
  function r(Y) {
    if (Y.length === 0) return null;
    var re = Y[0],
      ne = Y.pop();
    if (ne !== re) {
      Y[0] = ne;
      e: for (var le = 0, ze = Y.length, Gt = ze >>> 1; le < Gt; ) {
        var Dt = 2 * (le + 1) - 1,
          $t = Y[Dt],
          vt = Dt + 1,
          $n = Y[vt];
        if (0 > o($t, ne))
          vt < ze && 0 > o($n, $t)
            ? ((Y[le] = $n), (Y[vt] = ne), (le = vt))
            : ((Y[le] = $t), (Y[Dt] = ne), (le = Dt));
        else if (vt < ze && 0 > o($n, ne))
          (Y[le] = $n), (Y[vt] = ne), (le = vt);
        else break e;
      }
    }
    return re;
  }
  function o(Y, re) {
    var ne = Y.sortIndex - re.sortIndex;
    return ne !== 0 ? ne : Y.id - re.id;
  }
  if (typeof performance == "object" && typeof performance.now == "function") {
    var a = performance;
    e.unstable_now = function () {
      return a.now();
    };
  } else {
    var s = Date,
      u = s.now();
    e.unstable_now = function () {
      return s.now() - u;
    };
  }
  var f = [],
    d = [],
    m = 1,
    h = null,
    v = 3,
    b = !1,
    O = !1,
    E = !1,
    $ = typeof setTimeout == "function" ? setTimeout : null,
    _ = typeof clearTimeout == "function" ? clearTimeout : null,
    w = typeof setImmediate < "u" ? setImmediate : null;
  typeof navigator < "u" &&
    navigator.scheduling !== void 0 &&
    navigator.scheduling.isInputPending !== void 0 &&
    navigator.scheduling.isInputPending.bind(navigator.scheduling);
  function P(Y) {
    for (var re = n(d); re !== null; ) {
      if (re.callback === null) r(d);
      else if (re.startTime <= Y)
        r(d), (re.sortIndex = re.expirationTime), t(f, re);
      else break;
      re = n(d);
    }
  }
  function k(Y) {
    if (((E = !1), P(Y), !O))
      if (n(f) !== null) (O = !0), ce(I);
      else {
        var re = n(d);
        re !== null && Se(k, re.startTime - Y);
      }
  }
  function I(Y, re) {
    (O = !1), E && ((E = !1), _(M), (M = -1)), (b = !0);
    var ne = v;
    try {
      for (
        P(re), h = n(f);
        h !== null && (!(h.expirationTime > re) || (Y && !q()));

      ) {
        var le = h.callback;
        if (typeof le == "function") {
          (h.callback = null), (v = h.priorityLevel);
          var ze = le(h.expirationTime <= re);
          (re = e.unstable_now()),
            typeof ze == "function" ? (h.callback = ze) : h === n(f) && r(f),
            P(re);
        } else r(f);
        h = n(f);
      }
      if (h !== null) var Gt = !0;
      else {
        var Dt = n(d);
        Dt !== null && Se(k, Dt.startTime - re), (Gt = !1);
      }
      return Gt;
    } finally {
      (h = null), (v = ne), (b = !1);
    }
  }
  var z = !1,
    A = null,
    M = -1,
    B = 5,
    H = -1;
  function q() {
    return !(e.unstable_now() - H < B);
  }
  function Z() {
    if (A !== null) {
      var Y = e.unstable_now();
      H = Y;
      var re = !0;
      try {
        re = A(!0, Y);
      } finally {
        re ? he() : ((z = !1), (A = null));
      }
    } else z = !1;
  }
  var he;
  if (typeof w == "function")
    he = function () {
      w(Z);
    };
  else if (typeof MessageChannel < "u") {
    var xe = new MessageChannel(),
      Ce = xe.port2;
    (xe.port1.onmessage = Z),
      (he = function () {
        Ce.postMessage(null);
      });
  } else
    he = function () {
      $(Z, 0);
    };
  function ce(Y) {
    (A = Y), z || ((z = !0), he());
  }
  function Se(Y, re) {
    M = $(function () {
      Y(e.unstable_now());
    }, re);
  }
  (e.unstable_IdlePriority = 5),
    (e.unstable_ImmediatePriority = 1),
    (e.unstable_LowPriority = 4),
    (e.unstable_NormalPriority = 3),
    (e.unstable_Profiling = null),
    (e.unstable_UserBlockingPriority = 2),
    (e.unstable_cancelCallback = function (Y) {
      Y.callback = null;
    }),
    (e.unstable_continueExecution = function () {
      O || b || ((O = !0), ce(I));
    }),
    (e.unstable_forceFrameRate = function (Y) {
      0 > Y || 125 < Y
        ? console.error(
            "forceFrameRate takes a positive int between 0 and 125, forcing frame rates higher than 125 fps is not supported"
          )
        : (B = 0 < Y ? Math.floor(1e3 / Y) : 5);
    }),
    (e.unstable_getCurrentPriorityLevel = function () {
      return v;
    }),
    (e.unstable_getFirstCallbackNode = function () {
      return n(f);
    }),
    (e.unstable_next = function (Y) {
      switch (v) {
        case 1:
        case 2:
        case 3:
          var re = 3;
          break;
        default:
          re = v;
      }
      var ne = v;
      v = re;
      try {
        return Y();
      } finally {
        v = ne;
      }
    }),
    (e.unstable_pauseExecution = function () {}),
    (e.unstable_requestPaint = function () {}),
    (e.unstable_runWithPriority = function (Y, re) {
      switch (Y) {
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
          break;
        default:
          Y = 3;
      }
      var ne = v;
      v = Y;
      try {
        return re();
      } finally {
        v = ne;
      }
    }),
    (e.unstable_scheduleCallback = function (Y, re, ne) {
      var le = e.unstable_now();
      switch (
        (typeof ne == "object" && ne !== null
          ? ((ne = ne.delay),
            (ne = typeof ne == "number" && 0 < ne ? le + ne : le))
          : (ne = le),
        Y)
      ) {
        case 1:
          var ze = -1;
          break;
        case 2:
          ze = 250;
          break;
        case 5:
          ze = 1073741823;
          break;
        case 4:
          ze = 1e4;
          break;
        default:
          ze = 5e3;
      }
      return (
        (ze = ne + ze),
        (Y = {
          id: m++,
          callback: re,
          priorityLevel: Y,
          startTime: ne,
          expirationTime: ze,
          sortIndex: -1,
        }),
        ne > le
          ? ((Y.sortIndex = ne),
            t(d, Y),
            n(f) === null &&
              Y === n(d) &&
              (E ? (_(M), (M = -1)) : (E = !0), Se(k, ne - le)))
          : ((Y.sortIndex = ze), t(f, Y), O || b || ((O = !0), ce(I))),
        Y
      );
    }),
    (e.unstable_shouldYield = q),
    (e.unstable_wrapCallback = function (Y) {
      var re = v;
      return function () {
        var ne = v;
        v = re;
        try {
          return Y.apply(this, arguments);
        } finally {
          v = ne;
        }
      };
    });
})(LS);
AS.exports = LS;
var Tc = AS.exports;
/**
 * @license React
 * react-dom.production.min.js
 *
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */ var DS = y,
  bn = Tc;
function V(e) {
  for (
    var t = "https://reactjs.org/docs/error-decoder.html?invariant=" + e, n = 1;
    n < arguments.length;
    n++
  )
    t += "&args[]=" + encodeURIComponent(arguments[n]);
  return (
    "Minified React error #" +
    e +
    "; visit " +
    t +
    " for the full message or use the non-minified dev environment for full errors and additional helpful warnings."
  );
}
var MS = new Set(),
  cs = {};
function Li(e, t) {
  Da(e, t), Da(e + "Capture", t);
}
function Da(e, t) {
  for (cs[e] = t, e = 0; e < t.length; e++) MS.add(t[e]);
}
var Xr = !(
    typeof window > "u" ||
    typeof window.document > "u" ||
    typeof window.document.createElement > "u"
  ),
  Lh = Object.prototype.hasOwnProperty,
  B8 =
    /^[:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD][:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD\-.0-9\u00B7\u0300-\u036F\u203F-\u2040]*$/,
  S_ = {},
  P_ = {};
function U8(e) {
  return Lh.call(P_, e)
    ? !0
    : Lh.call(S_, e)
    ? !1
    : B8.test(e)
    ? (P_[e] = !0)
    : ((S_[e] = !0), !1);
}
function H8(e, t, n, r) {
  if (n !== null && n.type === 0) return !1;
  switch (typeof t) {
    case "function":
    case "symbol":
      return !0;
    case "boolean":
      return r
        ? !1
        : n !== null
        ? !n.acceptsBooleans
        : ((e = e.toLowerCase().slice(0, 5)), e !== "data-" && e !== "aria-");
    default:
      return !1;
  }
}
function V8(e, t, n, r) {
  if (t === null || typeof t > "u" || H8(e, t, n, r)) return !0;
  if (r) return !1;
  if (n !== null)
    switch (n.type) {
      case 3:
        return !t;
      case 4:
        return t === !1;
      case 5:
        return isNaN(t);
      case 6:
        return isNaN(t) || 1 > t;
    }
  return !1;
}
function Yt(e, t, n, r, o, a, s) {
  (this.acceptsBooleans = t === 2 || t === 3 || t === 4),
    (this.attributeName = r),
    (this.attributeNamespace = o),
    (this.mustUseProperty = n),
    (this.propertyName = e),
    (this.type = t),
    (this.sanitizeURL = a),
    (this.removeEmptyString = s);
}
var Et = {};
"children dangerouslySetInnerHTML defaultValue defaultChecked innerHTML suppressContentEditableWarning suppressHydrationWarning style"
  .split(" ")
  .forEach(function (e) {
    Et[e] = new Yt(e, 0, !1, e, null, !1, !1);
  });
[
  ["acceptCharset", "accept-charset"],
  ["className", "class"],
  ["htmlFor", "for"],
  ["httpEquiv", "http-equiv"],
].forEach(function (e) {
  var t = e[0];
  Et[t] = new Yt(t, 1, !1, e[1], null, !1, !1);
});
["contentEditable", "draggable", "spellCheck", "value"].forEach(function (e) {
  Et[e] = new Yt(e, 2, !1, e.toLowerCase(), null, !1, !1);
});
[
  "autoReverse",
  "externalResourcesRequired",
  "focusable",
  "preserveAlpha",
].forEach(function (e) {
  Et[e] = new Yt(e, 2, !1, e, null, !1, !1);
});
"allowFullScreen async autoFocus autoPlay controls default defer disabled disablePictureInPicture disableRemotePlayback formNoValidate hidden loop noModule noValidate open playsInline readOnly required reversed scoped seamless itemScope"
  .split(" ")
  .forEach(function (e) {
    Et[e] = new Yt(e, 3, !1, e.toLowerCase(), null, !1, !1);
  });
["checked", "multiple", "muted", "selected"].forEach(function (e) {
  Et[e] = new Yt(e, 3, !0, e, null, !1, !1);
});
["capture", "download"].forEach(function (e) {
  Et[e] = new Yt(e, 4, !1, e, null, !1, !1);
});
["cols", "rows", "size", "span"].forEach(function (e) {
  Et[e] = new Yt(e, 6, !1, e, null, !1, !1);
});
["rowSpan", "start"].forEach(function (e) {
  Et[e] = new Yt(e, 5, !1, e.toLowerCase(), null, !1, !1);
});
var dg = /[\-:]([a-z])/g;
function pg(e) {
  return e[1].toUpperCase();
}
"accent-height alignment-baseline arabic-form baseline-shift cap-height clip-path clip-rule color-interpolation color-interpolation-filters color-profile color-rendering dominant-baseline enable-background fill-opacity fill-rule flood-color flood-opacity font-family font-size font-size-adjust font-stretch font-style font-variant font-weight glyph-name glyph-orientation-horizontal glyph-orientation-vertical horiz-adv-x horiz-origin-x image-rendering letter-spacing lighting-color marker-end marker-mid marker-start overline-position overline-thickness paint-order panose-1 pointer-events rendering-intent shape-rendering stop-color stop-opacity strikethrough-position strikethrough-thickness stroke-dasharray stroke-dashoffset stroke-linecap stroke-linejoin stroke-miterlimit stroke-opacity stroke-width text-anchor text-decoration text-rendering underline-position underline-thickness unicode-bidi unicode-range units-per-em v-alphabetic v-hanging v-ideographic v-mathematical vector-effect vert-adv-y vert-origin-x vert-origin-y word-spacing writing-mode xmlns:xlink x-height"
  .split(" ")
  .forEach(function (e) {
    var t = e.replace(dg, pg);
    Et[t] = new Yt(t, 1, !1, e, null, !1, !1);
  });
"xlink:actuate xlink:arcrole xlink:role xlink:show xlink:title xlink:type"
  .split(" ")
  .forEach(function (e) {
    var t = e.replace(dg, pg);
    Et[t] = new Yt(t, 1, !1, e, "http://www.w3.org/1999/xlink", !1, !1);
  });
["xml:base", "xml:lang", "xml:space"].forEach(function (e) {
  var t = e.replace(dg, pg);
  Et[t] = new Yt(t, 1, !1, e, "http://www.w3.org/XML/1998/namespace", !1, !1);
});
["tabIndex", "crossOrigin"].forEach(function (e) {
  Et[e] = new Yt(e, 1, !1, e.toLowerCase(), null, !1, !1);
});
Et.xlinkHref = new Yt(
  "xlinkHref",
  1,
  !1,
  "xlink:href",
  "http://www.w3.org/1999/xlink",
  !0,
  !1
);
["src", "href", "action", "formAction"].forEach(function (e) {
  Et[e] = new Yt(e, 1, !1, e.toLowerCase(), null, !0, !0);
});
function mg(e, t, n, r) {
  var o = Et.hasOwnProperty(t) ? Et[t] : null;
  (o !== null
    ? o.type !== 0
    : r ||
      !(2 < t.length) ||
      (t[0] !== "o" && t[0] !== "O") ||
      (t[1] !== "n" && t[1] !== "N")) &&
    (V8(t, n, o, r) && (n = null),
    r || o === null
      ? U8(t) && (n === null ? e.removeAttribute(t) : e.setAttribute(t, "" + n))
      : o.mustUseProperty
      ? (e[o.propertyName] = n === null ? (o.type === 3 ? !1 : "") : n)
      : ((t = o.attributeName),
        (r = o.attributeNamespace),
        n === null
          ? e.removeAttribute(t)
          : ((o = o.type),
            (n = o === 3 || (o === 4 && n === !0) ? "" : "" + n),
            r ? e.setAttributeNS(r, t, n) : e.setAttribute(t, n))));
}
var ro = DS.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED,
  Gu = Symbol.for("react.element"),
  pa = Symbol.for("react.portal"),
  ma = Symbol.for("react.fragment"),
  hg = Symbol.for("react.strict_mode"),
  Dh = Symbol.for("react.profiler"),
  jS = Symbol.for("react.provider"),
  FS = Symbol.for("react.context"),
  vg = Symbol.for("react.forward_ref"),
  Mh = Symbol.for("react.suspense"),
  jh = Symbol.for("react.suspense_list"),
  gg = Symbol.for("react.memo"),
  Po = Symbol.for("react.lazy"),
  WS = Symbol.for("react.offscreen"),
  O_ = Symbol.iterator;
function Cl(e) {
  return e === null || typeof e != "object"
    ? null
    : ((e = (O_ && e[O_]) || e["@@iterator"]),
      typeof e == "function" ? e : null);
}
var qe = Object.assign,
  Bm;
function Bl(e) {
  if (Bm === void 0)
    try {
      throw Error();
    } catch (n) {
      var t = n.stack.trim().match(/\n( *(at )?)/);
      Bm = (t && t[1]) || "";
    }
  return (
    `
` +
    Bm +
    e
  );
}
var Um = !1;
function Hm(e, t) {
  if (!e || Um) return "";
  Um = !0;
  var n = Error.prepareStackTrace;
  Error.prepareStackTrace = void 0;
  try {
    if (t)
      if (
        ((t = function () {
          throw Error();
        }),
        Object.defineProperty(t.prototype, "props", {
          set: function () {
            throw Error();
          },
        }),
        typeof Reflect == "object" && Reflect.construct)
      ) {
        try {
          Reflect.construct(t, []);
        } catch (d) {
          var r = d;
        }
        Reflect.construct(e, [], t);
      } else {
        try {
          t.call();
        } catch (d) {
          r = d;
        }
        e.call(t.prototype);
      }
    else {
      try {
        throw Error();
      } catch (d) {
        r = d;
      }
      e();
    }
  } catch (d) {
    if (d && r && typeof d.stack == "string") {
      for (
        var o = d.stack.split(`
`),
          a = r.stack.split(`
`),
          s = o.length - 1,
          u = a.length - 1;
        1 <= s && 0 <= u && o[s] !== a[u];

      )
        u--;
      for (; 1 <= s && 0 <= u; s--, u--)
        if (o[s] !== a[u]) {
          if (s !== 1 || u !== 1)
            do
              if ((s--, u--, 0 > u || o[s] !== a[u])) {
                var f =
                  `
` + o[s].replace(" at new ", " at ");
                return (
                  e.displayName &&
                    f.includes("<anonymous>") &&
                    (f = f.replace("<anonymous>", e.displayName)),
                  f
                );
              }
            while (1 <= s && 0 <= u);
          break;
        }
    }
  } finally {
    (Um = !1), (Error.prepareStackTrace = n);
  }
  return (e = e ? e.displayName || e.name : "") ? Bl(e) : "";
}
function Y8(e) {
  switch (e.tag) {
    case 5:
      return Bl(e.type);
    case 16:
      return Bl("Lazy");
    case 13:
      return Bl("Suspense");
    case 19:
      return Bl("SuspenseList");
    case 0:
    case 2:
    case 15:
      return (e = Hm(e.type, !1)), e;
    case 11:
      return (e = Hm(e.type.render, !1)), e;
    case 1:
      return (e = Hm(e.type, !0)), e;
    default:
      return "";
  }
}
function Fh(e) {
  if (e == null) return null;
  if (typeof e == "function") return e.displayName || e.name || null;
  if (typeof e == "string") return e;
  switch (e) {
    case ma:
      return "Fragment";
    case pa:
      return "Portal";
    case Dh:
      return "Profiler";
    case hg:
      return "StrictMode";
    case Mh:
      return "Suspense";
    case jh:
      return "SuspenseList";
  }
  if (typeof e == "object")
    switch (e.$$typeof) {
      case FS:
        return (e.displayName || "Context") + ".Consumer";
      case jS:
        return (e._context.displayName || "Context") + ".Provider";
      case vg:
        var t = e.render;
        return (
          (e = e.displayName),
          e ||
            ((e = t.displayName || t.name || ""),
            (e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef")),
          e
        );
      case gg:
        return (
          (t = e.displayName || null), t !== null ? t : Fh(e.type) || "Memo"
        );
      case Po:
        (t = e._payload), (e = e._init);
        try {
          return Fh(e(t));
        } catch {}
    }
  return null;
}
function G8(e) {
  var t = e.type;
  switch (e.tag) {
    case 24:
      return "Cache";
    case 9:
      return (t.displayName || "Context") + ".Consumer";
    case 10:
      return (t._context.displayName || "Context") + ".Provider";
    case 18:
      return "DehydratedFragment";
    case 11:
      return (
        (e = t.render),
        (e = e.displayName || e.name || ""),
        t.displayName || (e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef")
      );
    case 7:
      return "Fragment";
    case 5:
      return t;
    case 4:
      return "Portal";
    case 3:
      return "Root";
    case 6:
      return "Text";
    case 16:
      return Fh(t);
    case 8:
      return t === hg ? "StrictMode" : "Mode";
    case 22:
      return "Offscreen";
    case 12:
      return "Profiler";
    case 21:
      return "Scope";
    case 13:
      return "Suspense";
    case 19:
      return "SuspenseList";
    case 25:
      return "TracingMarker";
    case 1:
    case 0:
    case 17:
    case 2:
    case 14:
    case 15:
      if (typeof t == "function") return t.displayName || t.name || null;
      if (typeof t == "string") return t;
  }
  return null;
}
function Bo(e) {
  switch (typeof e) {
    case "boolean":
    case "number":
    case "string":
    case "undefined":
      return e;
    case "object":
      return e;
    default:
      return "";
  }
}
function BS(e) {
  var t = e.type;
  return (
    (e = e.nodeName) &&
    e.toLowerCase() === "input" &&
    (t === "checkbox" || t === "radio")
  );
}
function X8(e) {
  var t = BS(e) ? "checked" : "value",
    n = Object.getOwnPropertyDescriptor(e.constructor.prototype, t),
    r = "" + e[t];
  if (
    !e.hasOwnProperty(t) &&
    typeof n < "u" &&
    typeof n.get == "function" &&
    typeof n.set == "function"
  ) {
    var o = n.get,
      a = n.set;
    return (
      Object.defineProperty(e, t, {
        configurable: !0,
        get: function () {
          return o.call(this);
        },
        set: function (s) {
          (r = "" + s), a.call(this, s);
        },
      }),
      Object.defineProperty(e, t, { enumerable: n.enumerable }),
      {
        getValue: function () {
          return r;
        },
        setValue: function (s) {
          r = "" + s;
        },
        stopTracking: function () {
          (e._valueTracker = null), delete e[t];
        },
      }
    );
  }
}
function Xu(e) {
  e._valueTracker || (e._valueTracker = X8(e));
}
function US(e) {
  if (!e) return !1;
  var t = e._valueTracker;
  if (!t) return !0;
  var n = t.getValue(),
    r = "";
  return (
    e && (r = BS(e) ? (e.checked ? "true" : "false") : e.value),
    (e = r),
    e !== n ? (t.setValue(e), !0) : !1
  );
}
function Zc(e) {
  if (((e = e || (typeof document < "u" ? document : void 0)), typeof e > "u"))
    return null;
  try {
    return e.activeElement || e.body;
  } catch {
    return e.body;
  }
}
function Wh(e, t) {
  var n = t.checked;
  return qe({}, t, {
    defaultChecked: void 0,
    defaultValue: void 0,
    value: void 0,
    checked: n ?? e._wrapperState.initialChecked,
  });
}
function E_(e, t) {
  var n = t.defaultValue == null ? "" : t.defaultValue,
    r = t.checked != null ? t.checked : t.defaultChecked;
  (n = Bo(t.value != null ? t.value : n)),
    (e._wrapperState = {
      initialChecked: r,
      initialValue: n,
      controlled:
        t.type === "checkbox" || t.type === "radio"
          ? t.checked != null
          : t.value != null,
    });
}
function HS(e, t) {
  (t = t.checked), t != null && mg(e, "checked", t, !1);
}
function Bh(e, t) {
  HS(e, t);
  var n = Bo(t.value),
    r = t.type;
  if (n != null)
    r === "number"
      ? ((n === 0 && e.value === "") || e.value != n) && (e.value = "" + n)
      : e.value !== "" + n && (e.value = "" + n);
  else if (r === "submit" || r === "reset") {
    e.removeAttribute("value");
    return;
  }
  t.hasOwnProperty("value")
    ? Uh(e, t.type, n)
    : t.hasOwnProperty("defaultValue") && Uh(e, t.type, Bo(t.defaultValue)),
    t.checked == null &&
      t.defaultChecked != null &&
      (e.defaultChecked = !!t.defaultChecked);
}
function $_(e, t, n) {
  if (t.hasOwnProperty("value") || t.hasOwnProperty("defaultValue")) {
    var r = t.type;
    if (
      !(
        (r !== "submit" && r !== "reset") ||
        (t.value !== void 0 && t.value !== null)
      )
    )
      return;
    (t = "" + e._wrapperState.initialValue),
      n || t === e.value || (e.value = t),
      (e.defaultValue = t);
  }
  (n = e.name),
    n !== "" && (e.name = ""),
    (e.defaultChecked = !!e._wrapperState.initialChecked),
    n !== "" && (e.name = n);
}
function Uh(e, t, n) {
  (t !== "number" || Zc(e.ownerDocument) !== e) &&
    (n == null
      ? (e.defaultValue = "" + e._wrapperState.initialValue)
      : e.defaultValue !== "" + n && (e.defaultValue = "" + n));
}
var Ul = Array.isArray;
function Ca(e, t, n, r) {
  if (((e = e.options), t)) {
    t = {};
    for (var o = 0; o < n.length; o++) t["$" + n[o]] = !0;
    for (n = 0; n < e.length; n++)
      (o = t.hasOwnProperty("$" + e[n].value)),
        e[n].selected !== o && (e[n].selected = o),
        o && r && (e[n].defaultSelected = !0);
  } else {
    for (n = "" + Bo(n), t = null, o = 0; o < e.length; o++) {
      if (e[o].value === n) {
        (e[o].selected = !0), r && (e[o].defaultSelected = !0);
        return;
      }
      t !== null || e[o].disabled || (t = e[o]);
    }
    t !== null && (t.selected = !0);
  }
}
function Hh(e, t) {
  if (t.dangerouslySetInnerHTML != null) throw Error(V(91));
  return qe({}, t, {
    value: void 0,
    defaultValue: void 0,
    children: "" + e._wrapperState.initialValue,
  });
}
function C_(e, t) {
  var n = t.value;
  if (n == null) {
    if (((n = t.children), (t = t.defaultValue), n != null)) {
      if (t != null) throw Error(V(92));
      if (Ul(n)) {
        if (1 < n.length) throw Error(V(93));
        n = n[0];
      }
      t = n;
    }
    t == null && (t = ""), (n = t);
  }
  e._wrapperState = { initialValue: Bo(n) };
}
function VS(e, t) {
  var n = Bo(t.value),
    r = Bo(t.defaultValue);
  n != null &&
    ((n = "" + n),
    n !== e.value && (e.value = n),
    t.defaultValue == null && e.defaultValue !== n && (e.defaultValue = n)),
    r != null && (e.defaultValue = "" + r);
}
function k_(e) {
  var t = e.textContent;
  t === e._wrapperState.initialValue && t !== "" && t !== null && (e.value = t);
}
function YS(e) {
  switch (e) {
    case "svg":
      return "http://www.w3.org/2000/svg";
    case "math":
      return "http://www.w3.org/1998/Math/MathML";
    default:
      return "http://www.w3.org/1999/xhtml";
  }
}
function Vh(e, t) {
  return e == null || e === "http://www.w3.org/1999/xhtml"
    ? YS(t)
    : e === "http://www.w3.org/2000/svg" && t === "foreignObject"
    ? "http://www.w3.org/1999/xhtml"
    : e;
}
var Ku,
  GS = (function (e) {
    return typeof MSApp < "u" && MSApp.execUnsafeLocalFunction
      ? function (t, n, r, o) {
          MSApp.execUnsafeLocalFunction(function () {
            return e(t, n, r, o);
          });
        }
      : e;
  })(function (e, t) {
    if (e.namespaceURI !== "http://www.w3.org/2000/svg" || "innerHTML" in e)
      e.innerHTML = t;
    else {
      for (
        Ku = Ku || document.createElement("div"),
          Ku.innerHTML = "<svg>" + t.valueOf().toString() + "</svg>",
          t = Ku.firstChild;
        e.firstChild;

      )
        e.removeChild(e.firstChild);
      for (; t.firstChild; ) e.appendChild(t.firstChild);
    }
  });
function fs(e, t) {
  if (t) {
    var n = e.firstChild;
    if (n && n === e.lastChild && n.nodeType === 3) {
      n.nodeValue = t;
      return;
    }
  }
  e.textContent = t;
}
var Kl = {
    animationIterationCount: !0,
    aspectRatio: !0,
    borderImageOutset: !0,
    borderImageSlice: !0,
    borderImageWidth: !0,
    boxFlex: !0,
    boxFlexGroup: !0,
    boxOrdinalGroup: !0,
    columnCount: !0,
    columns: !0,
    flex: !0,
    flexGrow: !0,
    flexPositive: !0,
    flexShrink: !0,
    flexNegative: !0,
    flexOrder: !0,
    gridArea: !0,
    gridRow: !0,
    gridRowEnd: !0,
    gridRowSpan: !0,
    gridRowStart: !0,
    gridColumn: !0,
    gridColumnEnd: !0,
    gridColumnSpan: !0,
    gridColumnStart: !0,
    fontWeight: !0,
    lineClamp: !0,
    lineHeight: !0,
    opacity: !0,
    order: !0,
    orphans: !0,
    tabSize: !0,
    widows: !0,
    zIndex: !0,
    zoom: !0,
    fillOpacity: !0,
    floodOpacity: !0,
    stopOpacity: !0,
    strokeDasharray: !0,
    strokeDashoffset: !0,
    strokeMiterlimit: !0,
    strokeOpacity: !0,
    strokeWidth: !0,
  },
  K8 = ["Webkit", "ms", "Moz", "O"];
Object.keys(Kl).forEach(function (e) {
  K8.forEach(function (t) {
    (t = t + e.charAt(0).toUpperCase() + e.substring(1)), (Kl[t] = Kl[e]);
  });
});
function XS(e, t, n) {
  return t == null || typeof t == "boolean" || t === ""
    ? ""
    : n || typeof t != "number" || t === 0 || (Kl.hasOwnProperty(e) && Kl[e])
    ? ("" + t).trim()
    : t + "px";
}
function KS(e, t) {
  e = e.style;
  for (var n in t)
    if (t.hasOwnProperty(n)) {
      var r = n.indexOf("--") === 0,
        o = XS(n, t[n], r);
      n === "float" && (n = "cssFloat"), r ? e.setProperty(n, o) : (e[n] = o);
    }
}
var Q8 = qe(
  { menuitem: !0 },
  {
    area: !0,
    base: !0,
    br: !0,
    col: !0,
    embed: !0,
    hr: !0,
    img: !0,
    input: !0,
    keygen: !0,
    link: !0,
    meta: !0,
    param: !0,
    source: !0,
    track: !0,
    wbr: !0,
  }
);
function Yh(e, t) {
  if (t) {
    if (Q8[e] && (t.children != null || t.dangerouslySetInnerHTML != null))
      throw Error(V(137, e));
    if (t.dangerouslySetInnerHTML != null) {
      if (t.children != null) throw Error(V(60));
      if (
        typeof t.dangerouslySetInnerHTML != "object" ||
        !("__html" in t.dangerouslySetInnerHTML)
      )
        throw Error(V(61));
    }
    if (t.style != null && typeof t.style != "object") throw Error(V(62));
  }
}
function Gh(e, t) {
  if (e.indexOf("-") === -1) return typeof t.is == "string";
  switch (e) {
    case "annotation-xml":
    case "color-profile":
    case "font-face":
    case "font-face-src":
    case "font-face-uri":
    case "font-face-format":
    case "font-face-name":
    case "missing-glyph":
      return !1;
    default:
      return !0;
  }
}
var Xh = null;
function yg(e) {
  return (
    (e = e.target || e.srcElement || window),
    e.correspondingUseElement && (e = e.correspondingUseElement),
    e.nodeType === 3 ? e.parentNode : e
  );
}
var Kh = null,
  ka = null,
  Ra = null;
function R_(e) {
  if ((e = Bs(e))) {
    if (typeof Kh != "function") throw Error(V(280));
    var t = e.stateNode;
    t && ((t = Fd(t)), Kh(e.stateNode, e.type, t));
  }
}
function QS(e) {
  ka ? (Ra ? Ra.push(e) : (Ra = [e])) : (ka = e);
}
function qS() {
  if (ka) {
    var e = ka,
      t = Ra;
    if (((Ra = ka = null), R_(e), t)) for (e = 0; e < t.length; e++) R_(t[e]);
  }
}
function ZS(e, t) {
  return e(t);
}
function JS() {}
var Vm = !1;
function eP(e, t, n) {
  if (Vm) return e(t, n);
  Vm = !0;
  try {
    return ZS(e, t, n);
  } finally {
    (Vm = !1), (ka !== null || Ra !== null) && (JS(), qS());
  }
}
function ds(e, t) {
  var n = e.stateNode;
  if (n === null) return null;
  var r = Fd(n);
  if (r === null) return null;
  n = r[t];
  e: switch (t) {
    case "onClick":
    case "onClickCapture":
    case "onDoubleClick":
    case "onDoubleClickCapture":
    case "onMouseDown":
    case "onMouseDownCapture":
    case "onMouseMove":
    case "onMouseMoveCapture":
    case "onMouseUp":
    case "onMouseUpCapture":
    case "onMouseEnter":
      (r = !r.disabled) ||
        ((e = e.type),
        (r = !(
          e === "button" ||
          e === "input" ||
          e === "select" ||
          e === "textarea"
        ))),
        (e = !r);
      break e;
    default:
      e = !1;
  }
  if (e) return null;
  if (n && typeof n != "function") throw Error(V(231, t, typeof n));
  return n;
}
var Qh = !1;
if (Xr)
  try {
    var kl = {};
    Object.defineProperty(kl, "passive", {
      get: function () {
        Qh = !0;
      },
    }),
      window.addEventListener("test", kl, kl),
      window.removeEventListener("test", kl, kl);
  } catch {
    Qh = !1;
  }
function q8(e, t, n, r, o, a, s, u, f) {
  var d = Array.prototype.slice.call(arguments, 3);
  try {
    t.apply(n, d);
  } catch (m) {
    this.onError(m);
  }
}
var Ql = !1,
  Jc = null,
  ef = !1,
  qh = null,
  Z8 = {
    onError: function (e) {
      (Ql = !0), (Jc = e);
    },
  };
function J8(e, t, n, r, o, a, s, u, f) {
  (Ql = !1), (Jc = null), q8.apply(Z8, arguments);
}
function eT(e, t, n, r, o, a, s, u, f) {
  if ((J8.apply(this, arguments), Ql)) {
    if (Ql) {
      var d = Jc;
      (Ql = !1), (Jc = null);
    } else throw Error(V(198));
    ef || ((ef = !0), (qh = d));
  }
}
function Di(e) {
  var t = e,
    n = e;
  if (e.alternate) for (; t.return; ) t = t.return;
  else {
    e = t;
    do (t = e), t.flags & 4098 && (n = t.return), (e = t.return);
    while (e);
  }
  return t.tag === 3 ? n : null;
}
function tP(e) {
  if (e.tag === 13) {
    var t = e.memoizedState;
    if (
      (t === null && ((e = e.alternate), e !== null && (t = e.memoizedState)),
      t !== null)
    )
      return t.dehydrated;
  }
  return null;
}
function N_(e) {
  if (Di(e) !== e) throw Error(V(188));
}
function tT(e) {
  var t = e.alternate;
  if (!t) {
    if (((t = Di(e)), t === null)) throw Error(V(188));
    return t !== e ? null : e;
  }
  for (var n = e, r = t; ; ) {
    var o = n.return;
    if (o === null) break;
    var a = o.alternate;
    if (a === null) {
      if (((r = o.return), r !== null)) {
        n = r;
        continue;
      }
      break;
    }
    if (o.child === a.child) {
      for (a = o.child; a; ) {
        if (a === n) return N_(o), e;
        if (a === r) return N_(o), t;
        a = a.sibling;
      }
      throw Error(V(188));
    }
    if (n.return !== r.return) (n = o), (r = a);
    else {
      for (var s = !1, u = o.child; u; ) {
        if (u === n) {
          (s = !0), (n = o), (r = a);
          break;
        }
        if (u === r) {
          (s = !0), (r = o), (n = a);
          break;
        }
        u = u.sibling;
      }
      if (!s) {
        for (u = a.child; u; ) {
          if (u === n) {
            (s = !0), (n = a), (r = o);
            break;
          }
          if (u === r) {
            (s = !0), (r = a), (n = o);
            break;
          }
          u = u.sibling;
        }
        if (!s) throw Error(V(189));
      }
    }
    if (n.alternate !== r) throw Error(V(190));
  }
  if (n.tag !== 3) throw Error(V(188));
  return n.stateNode.current === n ? e : t;
}
function nP(e) {
  return (e = tT(e)), e !== null ? rP(e) : null;
}
function rP(e) {
  if (e.tag === 5 || e.tag === 6) return e;
  for (e = e.child; e !== null; ) {
    var t = rP(e);
    if (t !== null) return t;
    e = e.sibling;
  }
  return null;
}
var oP = bn.unstable_scheduleCallback,
  I_ = bn.unstable_cancelCallback,
  nT = bn.unstable_shouldYield,
  rT = bn.unstable_requestPaint,
  ot = bn.unstable_now,
  oT = bn.unstable_getCurrentPriorityLevel,
  _g = bn.unstable_ImmediatePriority,
  iP = bn.unstable_UserBlockingPriority,
  tf = bn.unstable_NormalPriority,
  iT = bn.unstable_LowPriority,
  aP = bn.unstable_IdlePriority,
  Ld = null,
  Sr = null;
function aT(e) {
  if (Sr && typeof Sr.onCommitFiberRoot == "function")
    try {
      Sr.onCommitFiberRoot(Ld, e, void 0, (e.current.flags & 128) === 128);
    } catch {}
}
var ar = Math.clz32 ? Math.clz32 : uT,
  lT = Math.log,
  sT = Math.LN2;
function uT(e) {
  return (e >>>= 0), e === 0 ? 32 : (31 - ((lT(e) / sT) | 0)) | 0;
}
var Qu = 64,
  qu = 4194304;
function Hl(e) {
  switch (e & -e) {
    case 1:
      return 1;
    case 2:
      return 2;
    case 4:
      return 4;
    case 8:
      return 8;
    case 16:
      return 16;
    case 32:
      return 32;
    case 64:
    case 128:
    case 256:
    case 512:
    case 1024:
    case 2048:
    case 4096:
    case 8192:
    case 16384:
    case 32768:
    case 65536:
    case 131072:
    case 262144:
    case 524288:
    case 1048576:
    case 2097152:
      return e & 4194240;
    case 4194304:
    case 8388608:
    case 16777216:
    case 33554432:
    case 67108864:
      return e & 130023424;
    case 134217728:
      return 134217728;
    case 268435456:
      return 268435456;
    case 536870912:
      return 536870912;
    case 1073741824:
      return 1073741824;
    default:
      return e;
  }
}
function nf(e, t) {
  var n = e.pendingLanes;
  if (n === 0) return 0;
  var r = 0,
    o = e.suspendedLanes,
    a = e.pingedLanes,
    s = n & 268435455;
  if (s !== 0) {
    var u = s & ~o;
    u !== 0 ? (r = Hl(u)) : ((a &= s), a !== 0 && (r = Hl(a)));
  } else (s = n & ~o), s !== 0 ? (r = Hl(s)) : a !== 0 && (r = Hl(a));
  if (r === 0) return 0;
  if (
    t !== 0 &&
    t !== r &&
    !(t & o) &&
    ((o = r & -r), (a = t & -t), o >= a || (o === 16 && (a & 4194240) !== 0))
  )
    return t;
  if ((r & 4 && (r |= n & 16), (t = e.entangledLanes), t !== 0))
    for (e = e.entanglements, t &= r; 0 < t; )
      (n = 31 - ar(t)), (o = 1 << n), (r |= e[n]), (t &= ~o);
  return r;
}
function cT(e, t) {
  switch (e) {
    case 1:
    case 2:
    case 4:
      return t + 250;
    case 8:
    case 16:
    case 32:
    case 64:
    case 128:
    case 256:
    case 512:
    case 1024:
    case 2048:
    case 4096:
    case 8192:
    case 16384:
    case 32768:
    case 65536:
    case 131072:
    case 262144:
    case 524288:
    case 1048576:
    case 2097152:
      return t + 5e3;
    case 4194304:
    case 8388608:
    case 16777216:
    case 33554432:
    case 67108864:
      return -1;
    case 134217728:
    case 268435456:
    case 536870912:
    case 1073741824:
      return -1;
    default:
      return -1;
  }
}
function fT(e, t) {
  for (
    var n = e.suspendedLanes,
      r = e.pingedLanes,
      o = e.expirationTimes,
      a = e.pendingLanes;
    0 < a;

  ) {
    var s = 31 - ar(a),
      u = 1 << s,
      f = o[s];
    f === -1
      ? (!(u & n) || u & r) && (o[s] = cT(u, t))
      : f <= t && (e.expiredLanes |= u),
      (a &= ~u);
  }
}
function Zh(e) {
  return (
    (e = e.pendingLanes & -1073741825),
    e !== 0 ? e : e & 1073741824 ? 1073741824 : 0
  );
}
function lP() {
  var e = Qu;
  return (Qu <<= 1), !(Qu & 4194240) && (Qu = 64), e;
}
function Ym(e) {
  for (var t = [], n = 0; 31 > n; n++) t.push(e);
  return t;
}
function Fs(e, t, n) {
  (e.pendingLanes |= t),
    t !== 536870912 && ((e.suspendedLanes = 0), (e.pingedLanes = 0)),
    (e = e.eventTimes),
    (t = 31 - ar(t)),
    (e[t] = n);
}
function dT(e, t) {
  var n = e.pendingLanes & ~t;
  (e.pendingLanes = t),
    (e.suspendedLanes = 0),
    (e.pingedLanes = 0),
    (e.expiredLanes &= t),
    (e.mutableReadLanes &= t),
    (e.entangledLanes &= t),
    (t = e.entanglements);
  var r = e.eventTimes;
  for (e = e.expirationTimes; 0 < n; ) {
    var o = 31 - ar(n),
      a = 1 << o;
    (t[o] = 0), (r[o] = -1), (e[o] = -1), (n &= ~a);
  }
}
function wg(e, t) {
  var n = (e.entangledLanes |= t);
  for (e = e.entanglements; n; ) {
    var r = 31 - ar(n),
      o = 1 << r;
    (o & t) | (e[r] & t) && (e[r] |= t), (n &= ~o);
  }
}
var Ne = 0;
function sP(e) {
  return (e &= -e), 1 < e ? (4 < e ? (e & 268435455 ? 16 : 536870912) : 4) : 1;
}
var uP,
  bg,
  cP,
  fP,
  dP,
  Jh = !1,
  Zu = [],
  Io = null,
  To = null,
  zo = null,
  ps = new Map(),
  ms = new Map(),
  Eo = [],
  pT =
    "mousedown mouseup touchcancel touchend touchstart auxclick dblclick pointercancel pointerdown pointerup dragend dragstart drop compositionend compositionstart keydown keypress keyup input textInput copy cut paste click change contextmenu reset submit".split(
      " "
    );
function T_(e, t) {
  switch (e) {
    case "focusin":
    case "focusout":
      Io = null;
      break;
    case "dragenter":
    case "dragleave":
      To = null;
      break;
    case "mouseover":
    case "mouseout":
      zo = null;
      break;
    case "pointerover":
    case "pointerout":
      ps.delete(t.pointerId);
      break;
    case "gotpointercapture":
    case "lostpointercapture":
      ms.delete(t.pointerId);
  }
}
function Rl(e, t, n, r, o, a) {
  return e === null || e.nativeEvent !== a
    ? ((e = {
        blockedOn: t,
        domEventName: n,
        eventSystemFlags: r,
        nativeEvent: a,
        targetContainers: [o],
      }),
      t !== null && ((t = Bs(t)), t !== null && bg(t)),
      e)
    : ((e.eventSystemFlags |= r),
      (t = e.targetContainers),
      o !== null && t.indexOf(o) === -1 && t.push(o),
      e);
}
function mT(e, t, n, r, o) {
  switch (t) {
    case "focusin":
      return (Io = Rl(Io, e, t, n, r, o)), !0;
    case "dragenter":
      return (To = Rl(To, e, t, n, r, o)), !0;
    case "mouseover":
      return (zo = Rl(zo, e, t, n, r, o)), !0;
    case "pointerover":
      var a = o.pointerId;
      return ps.set(a, Rl(ps.get(a) || null, e, t, n, r, o)), !0;
    case "gotpointercapture":
      return (
        (a = o.pointerId), ms.set(a, Rl(ms.get(a) || null, e, t, n, r, o)), !0
      );
  }
  return !1;
}
function pP(e) {
  var t = _i(e.target);
  if (t !== null) {
    var n = Di(t);
    if (n !== null) {
      if (((t = n.tag), t === 13)) {
        if (((t = tP(n)), t !== null)) {
          (e.blockedOn = t),
            dP(e.priority, function () {
              cP(n);
            });
          return;
        }
      } else if (t === 3 && n.stateNode.current.memoizedState.isDehydrated) {
        e.blockedOn = n.tag === 3 ? n.stateNode.containerInfo : null;
        return;
      }
    }
  }
  e.blockedOn = null;
}
function zc(e) {
  if (e.blockedOn !== null) return !1;
  for (var t = e.targetContainers; 0 < t.length; ) {
    var n = ev(e.domEventName, e.eventSystemFlags, t[0], e.nativeEvent);
    if (n === null) {
      n = e.nativeEvent;
      var r = new n.constructor(n.type, n);
      (Xh = r), n.target.dispatchEvent(r), (Xh = null);
    } else return (t = Bs(n)), t !== null && bg(t), (e.blockedOn = n), !1;
    t.shift();
  }
  return !0;
}
function z_(e, t, n) {
  zc(e) && n.delete(t);
}
function hT() {
  (Jh = !1),
    Io !== null && zc(Io) && (Io = null),
    To !== null && zc(To) && (To = null),
    zo !== null && zc(zo) && (zo = null),
    ps.forEach(z_),
    ms.forEach(z_);
}
function Nl(e, t) {
  e.blockedOn === t &&
    ((e.blockedOn = null),
    Jh ||
      ((Jh = !0),
      bn.unstable_scheduleCallback(bn.unstable_NormalPriority, hT)));
}
function hs(e) {
  function t(o) {
    return Nl(o, e);
  }
  if (0 < Zu.length) {
    Nl(Zu[0], e);
    for (var n = 1; n < Zu.length; n++) {
      var r = Zu[n];
      r.blockedOn === e && (r.blockedOn = null);
    }
  }
  for (
    Io !== null && Nl(Io, e),
      To !== null && Nl(To, e),
      zo !== null && Nl(zo, e),
      ps.forEach(t),
      ms.forEach(t),
      n = 0;
    n < Eo.length;
    n++
  )
    (r = Eo[n]), r.blockedOn === e && (r.blockedOn = null);
  for (; 0 < Eo.length && ((n = Eo[0]), n.blockedOn === null); )
    pP(n), n.blockedOn === null && Eo.shift();
}
var Na = ro.ReactCurrentBatchConfig,
  rf = !0;
function vT(e, t, n, r) {
  var o = Ne,
    a = Na.transition;
  Na.transition = null;
  try {
    (Ne = 1), xg(e, t, n, r);
  } finally {
    (Ne = o), (Na.transition = a);
  }
}
function gT(e, t, n, r) {
  var o = Ne,
    a = Na.transition;
  Na.transition = null;
  try {
    (Ne = 4), xg(e, t, n, r);
  } finally {
    (Ne = o), (Na.transition = a);
  }
}
function xg(e, t, n, r) {
  if (rf) {
    var o = ev(e, t, n, r);
    if (o === null) nh(e, t, r, of, n), T_(e, r);
    else if (mT(o, e, t, n, r)) r.stopPropagation();
    else if ((T_(e, r), t & 4 && -1 < pT.indexOf(e))) {
      for (; o !== null; ) {
        var a = Bs(o);
        if (
          (a !== null && uP(a),
          (a = ev(e, t, n, r)),
          a === null && nh(e, t, r, of, n),
          a === o)
        )
          break;
        o = a;
      }
      o !== null && r.stopPropagation();
    } else nh(e, t, r, null, n);
  }
}
var of = null;
function ev(e, t, n, r) {
  if (((of = null), (e = yg(r)), (e = _i(e)), e !== null))
    if (((t = Di(e)), t === null)) e = null;
    else if (((n = t.tag), n === 13)) {
      if (((e = tP(t)), e !== null)) return e;
      e = null;
    } else if (n === 3) {
      if (t.stateNode.current.memoizedState.isDehydrated)
        return t.tag === 3 ? t.stateNode.containerInfo : null;
      e = null;
    } else t !== e && (e = null);
  return (of = e), null;
}
function mP(e) {
  switch (e) {
    case "cancel":
    case "click":
    case "close":
    case "contextmenu":
    case "copy":
    case "cut":
    case "auxclick":
    case "dblclick":
    case "dragend":
    case "dragstart":
    case "drop":
    case "focusin":
    case "focusout":
    case "input":
    case "invalid":
    case "keydown":
    case "keypress":
    case "keyup":
    case "mousedown":
    case "mouseup":
    case "paste":
    case "pause":
    case "play":
    case "pointercancel":
    case "pointerdown":
    case "pointerup":
    case "ratechange":
    case "reset":
    case "resize":
    case "seeked":
    case "submit":
    case "touchcancel":
    case "touchend":
    case "touchstart":
    case "volumechange":
    case "change":
    case "selectionchange":
    case "textInput":
    case "compositionstart":
    case "compositionend":
    case "compositionupdate":
    case "beforeblur":
    case "afterblur":
    case "beforeinput":
    case "blur":
    case "fullscreenchange":
    case "focus":
    case "hashchange":
    case "popstate":
    case "select":
    case "selectstart":
      return 1;
    case "drag":
    case "dragenter":
    case "dragexit":
    case "dragleave":
    case "dragover":
    case "mousemove":
    case "mouseout":
    case "mouseover":
    case "pointermove":
    case "pointerout":
    case "pointerover":
    case "scroll":
    case "toggle":
    case "touchmove":
    case "wheel":
    case "mouseenter":
    case "mouseleave":
    case "pointerenter":
    case "pointerleave":
      return 4;
    case "message":
      switch (oT()) {
        case _g:
          return 1;
        case iP:
          return 4;
        case tf:
        case iT:
          return 16;
        case aP:
          return 536870912;
        default:
          return 16;
      }
    default:
      return 16;
  }
}
var Co = null,
  Sg = null,
  Ac = null;
function hP() {
  if (Ac) return Ac;
  var e,
    t = Sg,
    n = t.length,
    r,
    o = "value" in Co ? Co.value : Co.textContent,
    a = o.length;
  for (e = 0; e < n && t[e] === o[e]; e++);
  var s = n - e;
  for (r = 1; r <= s && t[n - r] === o[a - r]; r++);
  return (Ac = o.slice(e, 1 < r ? 1 - r : void 0));
}
function Lc(e) {
  var t = e.keyCode;
  return (
    "charCode" in e
      ? ((e = e.charCode), e === 0 && t === 13 && (e = 13))
      : (e = t),
    e === 10 && (e = 13),
    32 <= e || e === 13 ? e : 0
  );
}
function Ju() {
  return !0;
}
function A_() {
  return !1;
}
function Sn(e) {
  function t(n, r, o, a, s) {
    (this._reactName = n),
      (this._targetInst = o),
      (this.type = r),
      (this.nativeEvent = a),
      (this.target = s),
      (this.currentTarget = null);
    for (var u in e)
      e.hasOwnProperty(u) && ((n = e[u]), (this[u] = n ? n(a) : a[u]));
    return (
      (this.isDefaultPrevented = (
        a.defaultPrevented != null ? a.defaultPrevented : a.returnValue === !1
      )
        ? Ju
        : A_),
      (this.isPropagationStopped = A_),
      this
    );
  }
  return (
    qe(t.prototype, {
      preventDefault: function () {
        this.defaultPrevented = !0;
        var n = this.nativeEvent;
        n &&
          (n.preventDefault
            ? n.preventDefault()
            : typeof n.returnValue != "unknown" && (n.returnValue = !1),
          (this.isDefaultPrevented = Ju));
      },
      stopPropagation: function () {
        var n = this.nativeEvent;
        n &&
          (n.stopPropagation
            ? n.stopPropagation()
            : typeof n.cancelBubble != "unknown" && (n.cancelBubble = !0),
          (this.isPropagationStopped = Ju));
      },
      persist: function () {},
      isPersistent: Ju,
    }),
    t
  );
}
var qa = {
    eventPhase: 0,
    bubbles: 0,
    cancelable: 0,
    timeStamp: function (e) {
      return e.timeStamp || Date.now();
    },
    defaultPrevented: 0,
    isTrusted: 0,
  },
  Pg = Sn(qa),
  Ws = qe({}, qa, { view: 0, detail: 0 }),
  yT = Sn(Ws),
  Gm,
  Xm,
  Il,
  Dd = qe({}, Ws, {
    screenX: 0,
    screenY: 0,
    clientX: 0,
    clientY: 0,
    pageX: 0,
    pageY: 0,
    ctrlKey: 0,
    shiftKey: 0,
    altKey: 0,
    metaKey: 0,
    getModifierState: Og,
    button: 0,
    buttons: 0,
    relatedTarget: function (e) {
      return e.relatedTarget === void 0
        ? e.fromElement === e.srcElement
          ? e.toElement
          : e.fromElement
        : e.relatedTarget;
    },
    movementX: function (e) {
      return "movementX" in e
        ? e.movementX
        : (e !== Il &&
            (Il && e.type === "mousemove"
              ? ((Gm = e.screenX - Il.screenX), (Xm = e.screenY - Il.screenY))
              : (Xm = Gm = 0),
            (Il = e)),
          Gm);
    },
    movementY: function (e) {
      return "movementY" in e ? e.movementY : Xm;
    },
  }),
  L_ = Sn(Dd),
  _T = qe({}, Dd, { dataTransfer: 0 }),
  wT = Sn(_T),
  bT = qe({}, Ws, { relatedTarget: 0 }),
  Km = Sn(bT),
  xT = qe({}, qa, { animationName: 0, elapsedTime: 0, pseudoElement: 0 }),
  ST = Sn(xT),
  PT = qe({}, qa, {
    clipboardData: function (e) {
      return "clipboardData" in e ? e.clipboardData : window.clipboardData;
    },
  }),
  OT = Sn(PT),
  ET = qe({}, qa, { data: 0 }),
  D_ = Sn(ET),
  $T = {
    Esc: "Escape",
    Spacebar: " ",
    Left: "ArrowLeft",
    Up: "ArrowUp",
    Right: "ArrowRight",
    Down: "ArrowDown",
    Del: "Delete",
    Win: "OS",
    Menu: "ContextMenu",
    Apps: "ContextMenu",
    Scroll: "ScrollLock",
    MozPrintableKey: "Unidentified",
  },
  CT = {
    8: "Backspace",
    9: "Tab",
    12: "Clear",
    13: "Enter",
    16: "Shift",
    17: "Control",
    18: "Alt",
    19: "Pause",
    20: "CapsLock",
    27: "Escape",
    32: " ",
    33: "PageUp",
    34: "PageDown",
    35: "End",
    36: "Home",
    37: "ArrowLeft",
    38: "ArrowUp",
    39: "ArrowRight",
    40: "ArrowDown",
    45: "Insert",
    46: "Delete",
    112: "F1",
    113: "F2",
    114: "F3",
    115: "F4",
    116: "F5",
    117: "F6",
    118: "F7",
    119: "F8",
    120: "F9",
    121: "F10",
    122: "F11",
    123: "F12",
    144: "NumLock",
    145: "ScrollLock",
    224: "Meta",
  },
  kT = {
    Alt: "altKey",
    Control: "ctrlKey",
    Meta: "metaKey",
    Shift: "shiftKey",
  };
function RT(e) {
  var t = this.nativeEvent;
  return t.getModifierState ? t.getModifierState(e) : (e = kT[e]) ? !!t[e] : !1;
}
function Og() {
  return RT;
}
var NT = qe({}, Ws, {
    key: function (e) {
      if (e.key) {
        var t = $T[e.key] || e.key;
        if (t !== "Unidentified") return t;
      }
      return e.type === "keypress"
        ? ((e = Lc(e)), e === 13 ? "Enter" : String.fromCharCode(e))
        : e.type === "keydown" || e.type === "keyup"
        ? CT[e.keyCode] || "Unidentified"
        : "";
    },
    code: 0,
    location: 0,
    ctrlKey: 0,
    shiftKey: 0,
    altKey: 0,
    metaKey: 0,
    repeat: 0,
    locale: 0,
    getModifierState: Og,
    charCode: function (e) {
      return e.type === "keypress" ? Lc(e) : 0;
    },
    keyCode: function (e) {
      return e.type === "keydown" || e.type === "keyup" ? e.keyCode : 0;
    },
    which: function (e) {
      return e.type === "keypress"
        ? Lc(e)
        : e.type === "keydown" || e.type === "keyup"
        ? e.keyCode
        : 0;
    },
  }),
  IT = Sn(NT),
  TT = qe({}, Dd, {
    pointerId: 0,
    width: 0,
    height: 0,
    pressure: 0,
    tangentialPressure: 0,
    tiltX: 0,
    tiltY: 0,
    twist: 0,
    pointerType: 0,
    isPrimary: 0,
  }),
  M_ = Sn(TT),
  zT = qe({}, Ws, {
    touches: 0,
    targetTouches: 0,
    changedTouches: 0,
    altKey: 0,
    metaKey: 0,
    ctrlKey: 0,
    shiftKey: 0,
    getModifierState: Og,
  }),
  AT = Sn(zT),
  LT = qe({}, qa, { propertyName: 0, elapsedTime: 0, pseudoElement: 0 }),
  DT = Sn(LT),
  MT = qe({}, Dd, {
    deltaX: function (e) {
      return "deltaX" in e ? e.deltaX : "wheelDeltaX" in e ? -e.wheelDeltaX : 0;
    },
    deltaY: function (e) {
      return "deltaY" in e
        ? e.deltaY
        : "wheelDeltaY" in e
        ? -e.wheelDeltaY
        : "wheelDelta" in e
        ? -e.wheelDelta
        : 0;
    },
    deltaZ: 0,
    deltaMode: 0,
  }),
  jT = Sn(MT),
  FT = [9, 13, 27, 32],
  Eg = Xr && "CompositionEvent" in window,
  ql = null;
Xr && "documentMode" in document && (ql = document.documentMode);
var WT = Xr && "TextEvent" in window && !ql,
  vP = Xr && (!Eg || (ql && 8 < ql && 11 >= ql)),
  j_ = String.fromCharCode(32),
  F_ = !1;
function gP(e, t) {
  switch (e) {
    case "keyup":
      return FT.indexOf(t.keyCode) !== -1;
    case "keydown":
      return t.keyCode !== 229;
    case "keypress":
    case "mousedown":
    case "focusout":
      return !0;
    default:
      return !1;
  }
}
function yP(e) {
  return (e = e.detail), typeof e == "object" && "data" in e ? e.data : null;
}
var ha = !1;
function BT(e, t) {
  switch (e) {
    case "compositionend":
      return yP(t);
    case "keypress":
      return t.which !== 32 ? null : ((F_ = !0), j_);
    case "textInput":
      return (e = t.data), e === j_ && F_ ? null : e;
    default:
      return null;
  }
}
function UT(e, t) {
  if (ha)
    return e === "compositionend" || (!Eg && gP(e, t))
      ? ((e = hP()), (Ac = Sg = Co = null), (ha = !1), e)
      : null;
  switch (e) {
    case "paste":
      return null;
    case "keypress":
      if (!(t.ctrlKey || t.altKey || t.metaKey) || (t.ctrlKey && t.altKey)) {
        if (t.char && 1 < t.char.length) return t.char;
        if (t.which) return String.fromCharCode(t.which);
      }
      return null;
    case "compositionend":
      return vP && t.locale !== "ko" ? null : t.data;
    default:
      return null;
  }
}
var HT = {
  color: !0,
  date: !0,
  datetime: !0,
  "datetime-local": !0,
  email: !0,
  month: !0,
  number: !0,
  password: !0,
  range: !0,
  search: !0,
  tel: !0,
  text: !0,
  time: !0,
  url: !0,
  week: !0,
};
function W_(e) {
  var t = e && e.nodeName && e.nodeName.toLowerCase();
  return t === "input" ? !!HT[e.type] : t === "textarea";
}
function _P(e, t, n, r) {
  QS(r),
    (t = af(t, "onChange")),
    0 < t.length &&
      ((n = new Pg("onChange", "change", null, n, r)),
      e.push({ event: n, listeners: t }));
}
var Zl = null,
  vs = null;
function VT(e) {
  RP(e, 0);
}
function Md(e) {
  var t = ya(e);
  if (US(t)) return e;
}
function YT(e, t) {
  if (e === "change") return t;
}
var wP = !1;
if (Xr) {
  var Qm;
  if (Xr) {
    var qm = "oninput" in document;
    if (!qm) {
      var B_ = document.createElement("div");
      B_.setAttribute("oninput", "return;"),
        (qm = typeof B_.oninput == "function");
    }
    Qm = qm;
  } else Qm = !1;
  wP = Qm && (!document.documentMode || 9 < document.documentMode);
}
function U_() {
  Zl && (Zl.detachEvent("onpropertychange", bP), (vs = Zl = null));
}
function bP(e) {
  if (e.propertyName === "value" && Md(vs)) {
    var t = [];
    _P(t, vs, e, yg(e)), eP(VT, t);
  }
}
function GT(e, t, n) {
  e === "focusin"
    ? (U_(), (Zl = t), (vs = n), Zl.attachEvent("onpropertychange", bP))
    : e === "focusout" && U_();
}
function XT(e) {
  if (e === "selectionchange" || e === "keyup" || e === "keydown")
    return Md(vs);
}
function KT(e, t) {
  if (e === "click") return Md(t);
}
function QT(e, t) {
  if (e === "input" || e === "change") return Md(t);
}
function qT(e, t) {
  return (e === t && (e !== 0 || 1 / e === 1 / t)) || (e !== e && t !== t);
}
var cr = typeof Object.is == "function" ? Object.is : qT;
function gs(e, t) {
  if (cr(e, t)) return !0;
  if (typeof e != "object" || e === null || typeof t != "object" || t === null)
    return !1;
  var n = Object.keys(e),
    r = Object.keys(t);
  if (n.length !== r.length) return !1;
  for (r = 0; r < n.length; r++) {
    var o = n[r];
    if (!Lh.call(t, o) || !cr(e[o], t[o])) return !1;
  }
  return !0;
}
function H_(e) {
  for (; e && e.firstChild; ) e = e.firstChild;
  return e;
}
function V_(e, t) {
  var n = H_(e);
  e = 0;
  for (var r; n; ) {
    if (n.nodeType === 3) {
      if (((r = e + n.textContent.length), e <= t && r >= t))
        return { node: n, offset: t - e };
      e = r;
    }
    e: {
      for (; n; ) {
        if (n.nextSibling) {
          n = n.nextSibling;
          break e;
        }
        n = n.parentNode;
      }
      n = void 0;
    }
    n = H_(n);
  }
}
function xP(e, t) {
  return e && t
    ? e === t
      ? !0
      : e && e.nodeType === 3
      ? !1
      : t && t.nodeType === 3
      ? xP(e, t.parentNode)
      : "contains" in e
      ? e.contains(t)
      : e.compareDocumentPosition
      ? !!(e.compareDocumentPosition(t) & 16)
      : !1
    : !1;
}
function SP() {
  for (var e = window, t = Zc(); t instanceof e.HTMLIFrameElement; ) {
    try {
      var n = typeof t.contentWindow.location.href == "string";
    } catch {
      n = !1;
    }
    if (n) e = t.contentWindow;
    else break;
    t = Zc(e.document);
  }
  return t;
}
function $g(e) {
  var t = e && e.nodeName && e.nodeName.toLowerCase();
  return (
    t &&
    ((t === "input" &&
      (e.type === "text" ||
        e.type === "search" ||
        e.type === "tel" ||
        e.type === "url" ||
        e.type === "password")) ||
      t === "textarea" ||
      e.contentEditable === "true")
  );
}
function ZT(e) {
  var t = SP(),
    n = e.focusedElem,
    r = e.selectionRange;
  if (
    t !== n &&
    n &&
    n.ownerDocument &&
    xP(n.ownerDocument.documentElement, n)
  ) {
    if (r !== null && $g(n)) {
      if (
        ((t = r.start),
        (e = r.end),
        e === void 0 && (e = t),
        "selectionStart" in n)
      )
        (n.selectionStart = t), (n.selectionEnd = Math.min(e, n.value.length));
      else if (
        ((e = ((t = n.ownerDocument || document) && t.defaultView) || window),
        e.getSelection)
      ) {
        e = e.getSelection();
        var o = n.textContent.length,
          a = Math.min(r.start, o);
        (r = r.end === void 0 ? a : Math.min(r.end, o)),
          !e.extend && a > r && ((o = r), (r = a), (a = o)),
          (o = V_(n, a));
        var s = V_(n, r);
        o &&
          s &&
          (e.rangeCount !== 1 ||
            e.anchorNode !== o.node ||
            e.anchorOffset !== o.offset ||
            e.focusNode !== s.node ||
            e.focusOffset !== s.offset) &&
          ((t = t.createRange()),
          t.setStart(o.node, o.offset),
          e.removeAllRanges(),
          a > r
            ? (e.addRange(t), e.extend(s.node, s.offset))
            : (t.setEnd(s.node, s.offset), e.addRange(t)));
      }
    }
    for (t = [], e = n; (e = e.parentNode); )
      e.nodeType === 1 &&
        t.push({ element: e, left: e.scrollLeft, top: e.scrollTop });
    for (typeof n.focus == "function" && n.focus(), n = 0; n < t.length; n++)
      (e = t[n]),
        (e.element.scrollLeft = e.left),
        (e.element.scrollTop = e.top);
  }
}
var JT = Xr && "documentMode" in document && 11 >= document.documentMode,
  va = null,
  tv = null,
  Jl = null,
  nv = !1;
function Y_(e, t, n) {
  var r = n.window === n ? n.document : n.nodeType === 9 ? n : n.ownerDocument;
  nv ||
    va == null ||
    va !== Zc(r) ||
    ((r = va),
    "selectionStart" in r && $g(r)
      ? (r = { start: r.selectionStart, end: r.selectionEnd })
      : ((r = (
          (r.ownerDocument && r.ownerDocument.defaultView) ||
          window
        ).getSelection()),
        (r = {
          anchorNode: r.anchorNode,
          anchorOffset: r.anchorOffset,
          focusNode: r.focusNode,
          focusOffset: r.focusOffset,
        })),
    (Jl && gs(Jl, r)) ||
      ((Jl = r),
      (r = af(tv, "onSelect")),
      0 < r.length &&
        ((t = new Pg("onSelect", "select", null, t, n)),
        e.push({ event: t, listeners: r }),
        (t.target = va))));
}
function ec(e, t) {
  var n = {};
  return (
    (n[e.toLowerCase()] = t.toLowerCase()),
    (n["Webkit" + e] = "webkit" + t),
    (n["Moz" + e] = "moz" + t),
    n
  );
}
var ga = {
    animationend: ec("Animation", "AnimationEnd"),
    animationiteration: ec("Animation", "AnimationIteration"),
    animationstart: ec("Animation", "AnimationStart"),
    transitionend: ec("Transition", "TransitionEnd"),
  },
  Zm = {},
  PP = {};
Xr &&
  ((PP = document.createElement("div").style),
  "AnimationEvent" in window ||
    (delete ga.animationend.animation,
    delete ga.animationiteration.animation,
    delete ga.animationstart.animation),
  "TransitionEvent" in window || delete ga.transitionend.transition);
function jd(e) {
  if (Zm[e]) return Zm[e];
  if (!ga[e]) return e;
  var t = ga[e],
    n;
  for (n in t) if (t.hasOwnProperty(n) && n in PP) return (Zm[e] = t[n]);
  return e;
}
var OP = jd("animationend"),
  EP = jd("animationiteration"),
  $P = jd("animationstart"),
  CP = jd("transitionend"),
  kP = new Map(),
  G_ =
    "abort auxClick cancel canPlay canPlayThrough click close contextMenu copy cut drag dragEnd dragEnter dragExit dragLeave dragOver dragStart drop durationChange emptied encrypted ended error gotPointerCapture input invalid keyDown keyPress keyUp load loadedData loadedMetadata loadStart lostPointerCapture mouseDown mouseMove mouseOut mouseOver mouseUp paste pause play playing pointerCancel pointerDown pointerMove pointerOut pointerOver pointerUp progress rateChange reset resize seeked seeking stalled submit suspend timeUpdate touchCancel touchEnd touchStart volumeChange scroll toggle touchMove waiting wheel".split(
      " "
    );
function Xo(e, t) {
  kP.set(e, t), Li(t, [e]);
}
for (var Jm = 0; Jm < G_.length; Jm++) {
  var eh = G_[Jm],
    ez = eh.toLowerCase(),
    tz = eh[0].toUpperCase() + eh.slice(1);
  Xo(ez, "on" + tz);
}
Xo(OP, "onAnimationEnd");
Xo(EP, "onAnimationIteration");
Xo($P, "onAnimationStart");
Xo("dblclick", "onDoubleClick");
Xo("focusin", "onFocus");
Xo("focusout", "onBlur");
Xo(CP, "onTransitionEnd");
Da("onMouseEnter", ["mouseout", "mouseover"]);
Da("onMouseLeave", ["mouseout", "mouseover"]);
Da("onPointerEnter", ["pointerout", "pointerover"]);
Da("onPointerLeave", ["pointerout", "pointerover"]);
Li(
  "onChange",
  "change click focusin focusout input keydown keyup selectionchange".split(" ")
);
Li(
  "onSelect",
  "focusout contextmenu dragend focusin keydown keyup mousedown mouseup selectionchange".split(
    " "
  )
);
Li("onBeforeInput", ["compositionend", "keypress", "textInput", "paste"]);
Li(
  "onCompositionEnd",
  "compositionend focusout keydown keypress keyup mousedown".split(" ")
);
Li(
  "onCompositionStart",
  "compositionstart focusout keydown keypress keyup mousedown".split(" ")
);
Li(
  "onCompositionUpdate",
  "compositionupdate focusout keydown keypress keyup mousedown".split(" ")
);
var Vl =
    "abort canplay canplaythrough durationchange emptied encrypted ended error loadeddata loadedmetadata loadstart pause play playing progress ratechange resize seeked seeking stalled suspend timeupdate volumechange waiting".split(
      " "
    ),
  nz = new Set("cancel close invalid load scroll toggle".split(" ").concat(Vl));
function X_(e, t, n) {
  var r = e.type || "unknown-event";
  (e.currentTarget = n), eT(r, t, void 0, e), (e.currentTarget = null);
}
function RP(e, t) {
  t = (t & 4) !== 0;
  for (var n = 0; n < e.length; n++) {
    var r = e[n],
      o = r.event;
    r = r.listeners;
    e: {
      var a = void 0;
      if (t)
        for (var s = r.length - 1; 0 <= s; s--) {
          var u = r[s],
            f = u.instance,
            d = u.currentTarget;
          if (((u = u.listener), f !== a && o.isPropagationStopped())) break e;
          X_(o, u, d), (a = f);
        }
      else
        for (s = 0; s < r.length; s++) {
          if (
            ((u = r[s]),
            (f = u.instance),
            (d = u.currentTarget),
            (u = u.listener),
            f !== a && o.isPropagationStopped())
          )
            break e;
          X_(o, u, d), (a = f);
        }
    }
  }
  if (ef) throw ((e = qh), (ef = !1), (qh = null), e);
}
function We(e, t) {
  var n = t[lv];
  n === void 0 && (n = t[lv] = new Set());
  var r = e + "__bubble";
  n.has(r) || (NP(t, e, 2, !1), n.add(r));
}
function th(e, t, n) {
  var r = 0;
  t && (r |= 4), NP(n, e, r, t);
}
var tc = "_reactListening" + Math.random().toString(36).slice(2);
function ys(e) {
  if (!e[tc]) {
    (e[tc] = !0),
      MS.forEach(function (n) {
        n !== "selectionchange" && (nz.has(n) || th(n, !1, e), th(n, !0, e));
      });
    var t = e.nodeType === 9 ? e : e.ownerDocument;
    t === null || t[tc] || ((t[tc] = !0), th("selectionchange", !1, t));
  }
}
function NP(e, t, n, r) {
  switch (mP(t)) {
    case 1:
      var o = vT;
      break;
    case 4:
      o = gT;
      break;
    default:
      o = xg;
  }
  (n = o.bind(null, t, n, e)),
    (o = void 0),
    !Qh ||
      (t !== "touchstart" && t !== "touchmove" && t !== "wheel") ||
      (o = !0),
    r
      ? o !== void 0
        ? e.addEventListener(t, n, { capture: !0, passive: o })
        : e.addEventListener(t, n, !0)
      : o !== void 0
      ? e.addEventListener(t, n, { passive: o })
      : e.addEventListener(t, n, !1);
}
function nh(e, t, n, r, o) {
  var a = r;
  if (!(t & 1) && !(t & 2) && r !== null)
    e: for (;;) {
      if (r === null) return;
      var s = r.tag;
      if (s === 3 || s === 4) {
        var u = r.stateNode.containerInfo;
        if (u === o || (u.nodeType === 8 && u.parentNode === o)) break;
        if (s === 4)
          for (s = r.return; s !== null; ) {
            var f = s.tag;
            if (
              (f === 3 || f === 4) &&
              ((f = s.stateNode.containerInfo),
              f === o || (f.nodeType === 8 && f.parentNode === o))
            )
              return;
            s = s.return;
          }
        for (; u !== null; ) {
          if (((s = _i(u)), s === null)) return;
          if (((f = s.tag), f === 5 || f === 6)) {
            r = a = s;
            continue e;
          }
          u = u.parentNode;
        }
      }
      r = r.return;
    }
  eP(function () {
    var d = a,
      m = yg(n),
      h = [];
    e: {
      var v = kP.get(e);
      if (v !== void 0) {
        var b = Pg,
          O = e;
        switch (e) {
          case "keypress":
            if (Lc(n) === 0) break e;
          case "keydown":
          case "keyup":
            b = IT;
            break;
          case "focusin":
            (O = "focus"), (b = Km);
            break;
          case "focusout":
            (O = "blur"), (b = Km);
            break;
          case "beforeblur":
          case "afterblur":
            b = Km;
            break;
          case "click":
            if (n.button === 2) break e;
          case "auxclick":
          case "dblclick":
          case "mousedown":
          case "mousemove":
          case "mouseup":
          case "mouseout":
          case "mouseover":
          case "contextmenu":
            b = L_;
            break;
          case "drag":
          case "dragend":
          case "dragenter":
          case "dragexit":
          case "dragleave":
          case "dragover":
          case "dragstart":
          case "drop":
            b = wT;
            break;
          case "touchcancel":
          case "touchend":
          case "touchmove":
          case "touchstart":
            b = AT;
            break;
          case OP:
          case EP:
          case $P:
            b = ST;
            break;
          case CP:
            b = DT;
            break;
          case "scroll":
            b = yT;
            break;
          case "wheel":
            b = jT;
            break;
          case "copy":
          case "cut":
          case "paste":
            b = OT;
            break;
          case "gotpointercapture":
          case "lostpointercapture":
          case "pointercancel":
          case "pointerdown":
          case "pointermove":
          case "pointerout":
          case "pointerover":
          case "pointerup":
            b = M_;
        }
        var E = (t & 4) !== 0,
          $ = !E && e === "scroll",
          _ = E ? (v !== null ? v + "Capture" : null) : v;
        E = [];
        for (var w = d, P; w !== null; ) {
          P = w;
          var k = P.stateNode;
          if (
            (P.tag === 5 &&
              k !== null &&
              ((P = k),
              _ !== null && ((k = ds(w, _)), k != null && E.push(_s(w, k, P)))),
            $)
          )
            break;
          w = w.return;
        }
        0 < E.length &&
          ((v = new b(v, O, null, n, m)), h.push({ event: v, listeners: E }));
      }
    }
    if (!(t & 7)) {
      e: {
        if (
          ((v = e === "mouseover" || e === "pointerover"),
          (b = e === "mouseout" || e === "pointerout"),
          v &&
            n !== Xh &&
            (O = n.relatedTarget || n.fromElement) &&
            (_i(O) || O[Kr]))
        )
          break e;
        if (
          (b || v) &&
          ((v =
            m.window === m
              ? m
              : (v = m.ownerDocument)
              ? v.defaultView || v.parentWindow
              : window),
          b
            ? ((O = n.relatedTarget || n.toElement),
              (b = d),
              (O = O ? _i(O) : null),
              O !== null &&
                (($ = Di(O)), O !== $ || (O.tag !== 5 && O.tag !== 6)) &&
                (O = null))
            : ((b = null), (O = d)),
          b !== O)
        ) {
          if (
            ((E = L_),
            (k = "onMouseLeave"),
            (_ = "onMouseEnter"),
            (w = "mouse"),
            (e === "pointerout" || e === "pointerover") &&
              ((E = M_),
              (k = "onPointerLeave"),
              (_ = "onPointerEnter"),
              (w = "pointer")),
            ($ = b == null ? v : ya(b)),
            (P = O == null ? v : ya(O)),
            (v = new E(k, w + "leave", b, n, m)),
            (v.target = $),
            (v.relatedTarget = P),
            (k = null),
            _i(m) === d &&
              ((E = new E(_, w + "enter", O, n, m)),
              (E.target = P),
              (E.relatedTarget = $),
              (k = E)),
            ($ = k),
            b && O)
          )
            t: {
              for (E = b, _ = O, w = 0, P = E; P; P = la(P)) w++;
              for (P = 0, k = _; k; k = la(k)) P++;
              for (; 0 < w - P; ) (E = la(E)), w--;
              for (; 0 < P - w; ) (_ = la(_)), P--;
              for (; w--; ) {
                if (E === _ || (_ !== null && E === _.alternate)) break t;
                (E = la(E)), (_ = la(_));
              }
              E = null;
            }
          else E = null;
          b !== null && K_(h, v, b, E, !1),
            O !== null && $ !== null && K_(h, $, O, E, !0);
        }
      }
      e: {
        if (
          ((v = d ? ya(d) : window),
          (b = v.nodeName && v.nodeName.toLowerCase()),
          b === "select" || (b === "input" && v.type === "file"))
        )
          var I = YT;
        else if (W_(v))
          if (wP) I = QT;
          else {
            I = XT;
            var z = GT;
          }
        else
          (b = v.nodeName) &&
            b.toLowerCase() === "input" &&
            (v.type === "checkbox" || v.type === "radio") &&
            (I = KT);
        if (I && (I = I(e, d))) {
          _P(h, I, n, m);
          break e;
        }
        z && z(e, v, d),
          e === "focusout" &&
            (z = v._wrapperState) &&
            z.controlled &&
            v.type === "number" &&
            Uh(v, "number", v.value);
      }
      switch (((z = d ? ya(d) : window), e)) {
        case "focusin":
          (W_(z) || z.contentEditable === "true") &&
            ((va = z), (tv = d), (Jl = null));
          break;
        case "focusout":
          Jl = tv = va = null;
          break;
        case "mousedown":
          nv = !0;
          break;
        case "contextmenu":
        case "mouseup":
        case "dragend":
          (nv = !1), Y_(h, n, m);
          break;
        case "selectionchange":
          if (JT) break;
        case "keydown":
        case "keyup":
          Y_(h, n, m);
      }
      var A;
      if (Eg)
        e: {
          switch (e) {
            case "compositionstart":
              var M = "onCompositionStart";
              break e;
            case "compositionend":
              M = "onCompositionEnd";
              break e;
            case "compositionupdate":
              M = "onCompositionUpdate";
              break e;
          }
          M = void 0;
        }
      else
        ha
          ? gP(e, n) && (M = "onCompositionEnd")
          : e === "keydown" && n.keyCode === 229 && (M = "onCompositionStart");
      M &&
        (vP &&
          n.locale !== "ko" &&
          (ha || M !== "onCompositionStart"
            ? M === "onCompositionEnd" && ha && (A = hP())
            : ((Co = m),
              (Sg = "value" in Co ? Co.value : Co.textContent),
              (ha = !0))),
        (z = af(d, M)),
        0 < z.length &&
          ((M = new D_(M, e, null, n, m)),
          h.push({ event: M, listeners: z }),
          A ? (M.data = A) : ((A = yP(n)), A !== null && (M.data = A)))),
        (A = WT ? BT(e, n) : UT(e, n)) &&
          ((d = af(d, "onBeforeInput")),
          0 < d.length &&
            ((m = new D_("onBeforeInput", "beforeinput", null, n, m)),
            h.push({ event: m, listeners: d }),
            (m.data = A)));
    }
    RP(h, t);
  });
}
function _s(e, t, n) {
  return { instance: e, listener: t, currentTarget: n };
}
function af(e, t) {
  for (var n = t + "Capture", r = []; e !== null; ) {
    var o = e,
      a = o.stateNode;
    o.tag === 5 &&
      a !== null &&
      ((o = a),
      (a = ds(e, n)),
      a != null && r.unshift(_s(e, a, o)),
      (a = ds(e, t)),
      a != null && r.push(_s(e, a, o))),
      (e = e.return);
  }
  return r;
}
function la(e) {
  if (e === null) return null;
  do e = e.return;
  while (e && e.tag !== 5);
  return e || null;
}
function K_(e, t, n, r, o) {
  for (var a = t._reactName, s = []; n !== null && n !== r; ) {
    var u = n,
      f = u.alternate,
      d = u.stateNode;
    if (f !== null && f === r) break;
    u.tag === 5 &&
      d !== null &&
      ((u = d),
      o
        ? ((f = ds(n, a)), f != null && s.unshift(_s(n, f, u)))
        : o || ((f = ds(n, a)), f != null && s.push(_s(n, f, u)))),
      (n = n.return);
  }
  s.length !== 0 && e.push({ event: t, listeners: s });
}
var rz = /\r\n?/g,
  oz = /\u0000|\uFFFD/g;
function Q_(e) {
  return (typeof e == "string" ? e : "" + e)
    .replace(
      rz,
      `
`
    )
    .replace(oz, "");
}
function nc(e, t, n) {
  if (((t = Q_(t)), Q_(e) !== t && n)) throw Error(V(425));
}
function lf() {}
var rv = null,
  ov = null;
function iv(e, t) {
  return (
    e === "textarea" ||
    e === "noscript" ||
    typeof t.children == "string" ||
    typeof t.children == "number" ||
    (typeof t.dangerouslySetInnerHTML == "object" &&
      t.dangerouslySetInnerHTML !== null &&
      t.dangerouslySetInnerHTML.__html != null)
  );
}
var av = typeof setTimeout == "function" ? setTimeout : void 0,
  iz = typeof clearTimeout == "function" ? clearTimeout : void 0,
  q_ = typeof Promise == "function" ? Promise : void 0,
  az =
    typeof queueMicrotask == "function"
      ? queueMicrotask
      : typeof q_ < "u"
      ? function (e) {
          return q_.resolve(null).then(e).catch(lz);
        }
      : av;
function lz(e) {
  setTimeout(function () {
    throw e;
  });
}
function rh(e, t) {
  var n = t,
    r = 0;
  do {
    var o = n.nextSibling;
    if ((e.removeChild(n), o && o.nodeType === 8))
      if (((n = o.data), n === "/$")) {
        if (r === 0) {
          e.removeChild(o), hs(t);
          return;
        }
        r--;
      } else (n !== "$" && n !== "$?" && n !== "$!") || r++;
    n = o;
  } while (n);
  hs(t);
}
function Ao(e) {
  for (; e != null; e = e.nextSibling) {
    var t = e.nodeType;
    if (t === 1 || t === 3) break;
    if (t === 8) {
      if (((t = e.data), t === "$" || t === "$!" || t === "$?")) break;
      if (t === "/$") return null;
    }
  }
  return e;
}
function Z_(e) {
  e = e.previousSibling;
  for (var t = 0; e; ) {
    if (e.nodeType === 8) {
      var n = e.data;
      if (n === "$" || n === "$!" || n === "$?") {
        if (t === 0) return e;
        t--;
      } else n === "/$" && t++;
    }
    e = e.previousSibling;
  }
  return null;
}
var Za = Math.random().toString(36).slice(2),
  br = "__reactFiber$" + Za,
  ws = "__reactProps$" + Za,
  Kr = "__reactContainer$" + Za,
  lv = "__reactEvents$" + Za,
  sz = "__reactListeners$" + Za,
  uz = "__reactHandles$" + Za;
function _i(e) {
  var t = e[br];
  if (t) return t;
  for (var n = e.parentNode; n; ) {
    if ((t = n[Kr] || n[br])) {
      if (
        ((n = t.alternate),
        t.child !== null || (n !== null && n.child !== null))
      )
        for (e = Z_(e); e !== null; ) {
          if ((n = e[br])) return n;
          e = Z_(e);
        }
      return t;
    }
    (e = n), (n = e.parentNode);
  }
  return null;
}
function Bs(e) {
  return (
    (e = e[br] || e[Kr]),
    !e || (e.tag !== 5 && e.tag !== 6 && e.tag !== 13 && e.tag !== 3) ? null : e
  );
}
function ya(e) {
  if (e.tag === 5 || e.tag === 6) return e.stateNode;
  throw Error(V(33));
}
function Fd(e) {
  return e[ws] || null;
}
var sv = [],
  _a = -1;
function Ko(e) {
  return { current: e };
}
function Ue(e) {
  0 > _a || ((e.current = sv[_a]), (sv[_a] = null), _a--);
}
function Me(e, t) {
  _a++, (sv[_a] = e.current), (e.current = t);
}
var Uo = {},
  Lt = Ko(Uo),
  tn = Ko(!1),
  Ci = Uo;
function Ma(e, t) {
  var n = e.type.contextTypes;
  if (!n) return Uo;
  var r = e.stateNode;
  if (r && r.__reactInternalMemoizedUnmaskedChildContext === t)
    return r.__reactInternalMemoizedMaskedChildContext;
  var o = {},
    a;
  for (a in n) o[a] = t[a];
  return (
    r &&
      ((e = e.stateNode),
      (e.__reactInternalMemoizedUnmaskedChildContext = t),
      (e.__reactInternalMemoizedMaskedChildContext = o)),
    o
  );
}
function nn(e) {
  return (e = e.childContextTypes), e != null;
}
function sf() {
  Ue(tn), Ue(Lt);
}
function J_(e, t, n) {
  if (Lt.current !== Uo) throw Error(V(168));
  Me(Lt, t), Me(tn, n);
}
function IP(e, t, n) {
  var r = e.stateNode;
  if (((t = t.childContextTypes), typeof r.getChildContext != "function"))
    return n;
  r = r.getChildContext();
  for (var o in r) if (!(o in t)) throw Error(V(108, G8(e) || "Unknown", o));
  return qe({}, n, r);
}
function uf(e) {
  return (
    (e =
      ((e = e.stateNode) && e.__reactInternalMemoizedMergedChildContext) || Uo),
    (Ci = Lt.current),
    Me(Lt, e),
    Me(tn, tn.current),
    !0
  );
}
function ew(e, t, n) {
  var r = e.stateNode;
  if (!r) throw Error(V(169));
  n
    ? ((e = IP(e, t, Ci)),
      (r.__reactInternalMemoizedMergedChildContext = e),
      Ue(tn),
      Ue(Lt),
      Me(Lt, e))
    : Ue(tn),
    Me(tn, n);
}
var Hr = null,
  Wd = !1,
  oh = !1;
function TP(e) {
  Hr === null ? (Hr = [e]) : Hr.push(e);
}
function cz(e) {
  (Wd = !0), TP(e);
}
function Qo() {
  if (!oh && Hr !== null) {
    oh = !0;
    var e = 0,
      t = Ne;
    try {
      var n = Hr;
      for (Ne = 1; e < n.length; e++) {
        var r = n[e];
        do r = r(!0);
        while (r !== null);
      }
      (Hr = null), (Wd = !1);
    } catch (o) {
      throw (Hr !== null && (Hr = Hr.slice(e + 1)), oP(_g, Qo), o);
    } finally {
      (Ne = t), (oh = !1);
    }
  }
  return null;
}
var wa = [],
  ba = 0,
  cf = null,
  ff = 0,
  Dn = [],
  Mn = 0,
  ki = null,
  Vr = 1,
  Yr = "";
function pi(e, t) {
  (wa[ba++] = ff), (wa[ba++] = cf), (cf = e), (ff = t);
}
function zP(e, t, n) {
  (Dn[Mn++] = Vr), (Dn[Mn++] = Yr), (Dn[Mn++] = ki), (ki = e);
  var r = Vr;
  e = Yr;
  var o = 32 - ar(r) - 1;
  (r &= ~(1 << o)), (n += 1);
  var a = 32 - ar(t) + o;
  if (30 < a) {
    var s = o - (o % 5);
    (a = (r & ((1 << s) - 1)).toString(32)),
      (r >>= s),
      (o -= s),
      (Vr = (1 << (32 - ar(t) + o)) | (n << o) | r),
      (Yr = a + e);
  } else (Vr = (1 << a) | (n << o) | r), (Yr = e);
}
function Cg(e) {
  e.return !== null && (pi(e, 1), zP(e, 1, 0));
}
function kg(e) {
  for (; e === cf; )
    (cf = wa[--ba]), (wa[ba] = null), (ff = wa[--ba]), (wa[ba] = null);
  for (; e === ki; )
    (ki = Dn[--Mn]),
      (Dn[Mn] = null),
      (Yr = Dn[--Mn]),
      (Dn[Mn] = null),
      (Vr = Dn[--Mn]),
      (Dn[Mn] = null);
}
var _n = null,
  gn = null,
  Ge = !1,
  or = null;
function AP(e, t) {
  var n = jn(5, null, null, 0);
  (n.elementType = "DELETED"),
    (n.stateNode = t),
    (n.return = e),
    (t = e.deletions),
    t === null ? ((e.deletions = [n]), (e.flags |= 16)) : t.push(n);
}
function tw(e, t) {
  switch (e.tag) {
    case 5:
      var n = e.type;
      return (
        (t =
          t.nodeType !== 1 || n.toLowerCase() !== t.nodeName.toLowerCase()
            ? null
            : t),
        t !== null
          ? ((e.stateNode = t), (_n = e), (gn = Ao(t.firstChild)), !0)
          : !1
      );
    case 6:
      return (
        (t = e.pendingProps === "" || t.nodeType !== 3 ? null : t),
        t !== null ? ((e.stateNode = t), (_n = e), (gn = null), !0) : !1
      );
    case 13:
      return (
        (t = t.nodeType !== 8 ? null : t),
        t !== null
          ? ((n = ki !== null ? { id: Vr, overflow: Yr } : null),
            (e.memoizedState = {
              dehydrated: t,
              treeContext: n,
              retryLane: 1073741824,
            }),
            (n = jn(18, null, null, 0)),
            (n.stateNode = t),
            (n.return = e),
            (e.child = n),
            (_n = e),
            (gn = null),
            !0)
          : !1
      );
    default:
      return !1;
  }
}
function uv(e) {
  return (e.mode & 1) !== 0 && (e.flags & 128) === 0;
}
function cv(e) {
  if (Ge) {
    var t = gn;
    if (t) {
      var n = t;
      if (!tw(e, t)) {
        if (uv(e)) throw Error(V(418));
        t = Ao(n.nextSibling);
        var r = _n;
        t && tw(e, t)
          ? AP(r, n)
          : ((e.flags = (e.flags & -4097) | 2), (Ge = !1), (_n = e));
      }
    } else {
      if (uv(e)) throw Error(V(418));
      (e.flags = (e.flags & -4097) | 2), (Ge = !1), (_n = e);
    }
  }
}
function nw(e) {
  for (e = e.return; e !== null && e.tag !== 5 && e.tag !== 3 && e.tag !== 13; )
    e = e.return;
  _n = e;
}
function rc(e) {
  if (e !== _n) return !1;
  if (!Ge) return nw(e), (Ge = !0), !1;
  var t;
  if (
    ((t = e.tag !== 3) &&
      !(t = e.tag !== 5) &&
      ((t = e.type),
      (t = t !== "head" && t !== "body" && !iv(e.type, e.memoizedProps))),
    t && (t = gn))
  ) {
    if (uv(e)) throw (LP(), Error(V(418)));
    for (; t; ) AP(e, t), (t = Ao(t.nextSibling));
  }
  if ((nw(e), e.tag === 13)) {
    if (((e = e.memoizedState), (e = e !== null ? e.dehydrated : null), !e))
      throw Error(V(317));
    e: {
      for (e = e.nextSibling, t = 0; e; ) {
        if (e.nodeType === 8) {
          var n = e.data;
          if (n === "/$") {
            if (t === 0) {
              gn = Ao(e.nextSibling);
              break e;
            }
            t--;
          } else (n !== "$" && n !== "$!" && n !== "$?") || t++;
        }
        e = e.nextSibling;
      }
      gn = null;
    }
  } else gn = _n ? Ao(e.stateNode.nextSibling) : null;
  return !0;
}
function LP() {
  for (var e = gn; e; ) e = Ao(e.nextSibling);
}
function ja() {
  (gn = _n = null), (Ge = !1);
}
function Rg(e) {
  or === null ? (or = [e]) : or.push(e);
}
var fz = ro.ReactCurrentBatchConfig;
function nr(e, t) {
  if (e && e.defaultProps) {
    (t = qe({}, t)), (e = e.defaultProps);
    for (var n in e) t[n] === void 0 && (t[n] = e[n]);
    return t;
  }
  return t;
}
var df = Ko(null),
  pf = null,
  xa = null,
  Ng = null;
function Ig() {
  Ng = xa = pf = null;
}
function Tg(e) {
  var t = df.current;
  Ue(df), (e._currentValue = t);
}
function fv(e, t, n) {
  for (; e !== null; ) {
    var r = e.alternate;
    if (
      ((e.childLanes & t) !== t
        ? ((e.childLanes |= t), r !== null && (r.childLanes |= t))
        : r !== null && (r.childLanes & t) !== t && (r.childLanes |= t),
      e === n)
    )
      break;
    e = e.return;
  }
}
function Ia(e, t) {
  (pf = e),
    (Ng = xa = null),
    (e = e.dependencies),
    e !== null &&
      e.firstContext !== null &&
      (e.lanes & t && (en = !0), (e.firstContext = null));
}
function Bn(e) {
  var t = e._currentValue;
  if (Ng !== e)
    if (((e = { context: e, memoizedValue: t, next: null }), xa === null)) {
      if (pf === null) throw Error(V(308));
      (xa = e), (pf.dependencies = { lanes: 0, firstContext: e });
    } else xa = xa.next = e;
  return t;
}
var wi = null;
function zg(e) {
  wi === null ? (wi = [e]) : wi.push(e);
}
function DP(e, t, n, r) {
  var o = t.interleaved;
  return (
    o === null ? ((n.next = n), zg(t)) : ((n.next = o.next), (o.next = n)),
    (t.interleaved = n),
    Qr(e, r)
  );
}
function Qr(e, t) {
  e.lanes |= t;
  var n = e.alternate;
  for (n !== null && (n.lanes |= t), n = e, e = e.return; e !== null; )
    (e.childLanes |= t),
      (n = e.alternate),
      n !== null && (n.childLanes |= t),
      (n = e),
      (e = e.return);
  return n.tag === 3 ? n.stateNode : null;
}
var Oo = !1;
function Ag(e) {
  e.updateQueue = {
    baseState: e.memoizedState,
    firstBaseUpdate: null,
    lastBaseUpdate: null,
    shared: { pending: null, interleaved: null, lanes: 0 },
    effects: null,
  };
}
function MP(e, t) {
  (e = e.updateQueue),
    t.updateQueue === e &&
      (t.updateQueue = {
        baseState: e.baseState,
        firstBaseUpdate: e.firstBaseUpdate,
        lastBaseUpdate: e.lastBaseUpdate,
        shared: e.shared,
        effects: e.effects,
      });
}
function Gr(e, t) {
  return {
    eventTime: e,
    lane: t,
    tag: 0,
    payload: null,
    callback: null,
    next: null,
  };
}
function Lo(e, t, n) {
  var r = e.updateQueue;
  if (r === null) return null;
  if (((r = r.shared), be & 2)) {
    var o = r.pending;
    return (
      o === null ? (t.next = t) : ((t.next = o.next), (o.next = t)),
      (r.pending = t),
      Qr(e, n)
    );
  }
  return (
    (o = r.interleaved),
    o === null ? ((t.next = t), zg(r)) : ((t.next = o.next), (o.next = t)),
    (r.interleaved = t),
    Qr(e, n)
  );
}
function Dc(e, t, n) {
  if (
    ((t = t.updateQueue), t !== null && ((t = t.shared), (n & 4194240) !== 0))
  ) {
    var r = t.lanes;
    (r &= e.pendingLanes), (n |= r), (t.lanes = n), wg(e, n);
  }
}
function rw(e, t) {
  var n = e.updateQueue,
    r = e.alternate;
  if (r !== null && ((r = r.updateQueue), n === r)) {
    var o = null,
      a = null;
    if (((n = n.firstBaseUpdate), n !== null)) {
      do {
        var s = {
          eventTime: n.eventTime,
          lane: n.lane,
          tag: n.tag,
          payload: n.payload,
          callback: n.callback,
          next: null,
        };
        a === null ? (o = a = s) : (a = a.next = s), (n = n.next);
      } while (n !== null);
      a === null ? (o = a = t) : (a = a.next = t);
    } else o = a = t;
    (n = {
      baseState: r.baseState,
      firstBaseUpdate: o,
      lastBaseUpdate: a,
      shared: r.shared,
      effects: r.effects,
    }),
      (e.updateQueue = n);
    return;
  }
  (e = n.lastBaseUpdate),
    e === null ? (n.firstBaseUpdate = t) : (e.next = t),
    (n.lastBaseUpdate = t);
}
function mf(e, t, n, r) {
  var o = e.updateQueue;
  Oo = !1;
  var a = o.firstBaseUpdate,
    s = o.lastBaseUpdate,
    u = o.shared.pending;
  if (u !== null) {
    o.shared.pending = null;
    var f = u,
      d = f.next;
    (f.next = null), s === null ? (a = d) : (s.next = d), (s = f);
    var m = e.alternate;
    m !== null &&
      ((m = m.updateQueue),
      (u = m.lastBaseUpdate),
      u !== s &&
        (u === null ? (m.firstBaseUpdate = d) : (u.next = d),
        (m.lastBaseUpdate = f)));
  }
  if (a !== null) {
    var h = o.baseState;
    (s = 0), (m = d = f = null), (u = a);
    do {
      var v = u.lane,
        b = u.eventTime;
      if ((r & v) === v) {
        m !== null &&
          (m = m.next =
            {
              eventTime: b,
              lane: 0,
              tag: u.tag,
              payload: u.payload,
              callback: u.callback,
              next: null,
            });
        e: {
          var O = e,
            E = u;
          switch (((v = t), (b = n), E.tag)) {
            case 1:
              if (((O = E.payload), typeof O == "function")) {
                h = O.call(b, h, v);
                break e;
              }
              h = O;
              break e;
            case 3:
              O.flags = (O.flags & -65537) | 128;
            case 0:
              if (
                ((O = E.payload),
                (v = typeof O == "function" ? O.call(b, h, v) : O),
                v == null)
              )
                break e;
              h = qe({}, h, v);
              break e;
            case 2:
              Oo = !0;
          }
        }
        u.callback !== null &&
          u.lane !== 0 &&
          ((e.flags |= 64),
          (v = o.effects),
          v === null ? (o.effects = [u]) : v.push(u));
      } else
        (b = {
          eventTime: b,
          lane: v,
          tag: u.tag,
          payload: u.payload,
          callback: u.callback,
          next: null,
        }),
          m === null ? ((d = m = b), (f = h)) : (m = m.next = b),
          (s |= v);
      if (((u = u.next), u === null)) {
        if (((u = o.shared.pending), u === null)) break;
        (v = u),
          (u = v.next),
          (v.next = null),
          (o.lastBaseUpdate = v),
          (o.shared.pending = null);
      }
    } while (1);
    if (
      (m === null && (f = h),
      (o.baseState = f),
      (o.firstBaseUpdate = d),
      (o.lastBaseUpdate = m),
      (t = o.shared.interleaved),
      t !== null)
    ) {
      o = t;
      do (s |= o.lane), (o = o.next);
      while (o !== t);
    } else a === null && (o.shared.lanes = 0);
    (Ni |= s), (e.lanes = s), (e.memoizedState = h);
  }
}
function ow(e, t, n) {
  if (((e = t.effects), (t.effects = null), e !== null))
    for (t = 0; t < e.length; t++) {
      var r = e[t],
        o = r.callback;
      if (o !== null) {
        if (((r.callback = null), (r = n), typeof o != "function"))
          throw Error(V(191, o));
        o.call(r);
      }
    }
}
var jP = new DS.Component().refs;
function dv(e, t, n, r) {
  (t = e.memoizedState),
    (n = n(r, t)),
    (n = n == null ? t : qe({}, t, n)),
    (e.memoizedState = n),
    e.lanes === 0 && (e.updateQueue.baseState = n);
}
var Bd = {
  isMounted: function (e) {
    return (e = e._reactInternals) ? Di(e) === e : !1;
  },
  enqueueSetState: function (e, t, n) {
    e = e._reactInternals;
    var r = Ht(),
      o = Mo(e),
      a = Gr(r, o);
    (a.payload = t),
      n != null && (a.callback = n),
      (t = Lo(e, a, o)),
      t !== null && (lr(t, e, o, r), Dc(t, e, o));
  },
  enqueueReplaceState: function (e, t, n) {
    e = e._reactInternals;
    var r = Ht(),
      o = Mo(e),
      a = Gr(r, o);
    (a.tag = 1),
      (a.payload = t),
      n != null && (a.callback = n),
      (t = Lo(e, a, o)),
      t !== null && (lr(t, e, o, r), Dc(t, e, o));
  },
  enqueueForceUpdate: function (e, t) {
    e = e._reactInternals;
    var n = Ht(),
      r = Mo(e),
      o = Gr(n, r);
    (o.tag = 2),
      t != null && (o.callback = t),
      (t = Lo(e, o, r)),
      t !== null && (lr(t, e, r, n), Dc(t, e, r));
  },
};
function iw(e, t, n, r, o, a, s) {
  return (
    (e = e.stateNode),
    typeof e.shouldComponentUpdate == "function"
      ? e.shouldComponentUpdate(r, a, s)
      : t.prototype && t.prototype.isPureReactComponent
      ? !gs(n, r) || !gs(o, a)
      : !0
  );
}
function FP(e, t, n) {
  var r = !1,
    o = Uo,
    a = t.contextType;
  return (
    typeof a == "object" && a !== null
      ? (a = Bn(a))
      : ((o = nn(t) ? Ci : Lt.current),
        (r = t.contextTypes),
        (a = (r = r != null) ? Ma(e, o) : Uo)),
    (t = new t(n, a)),
    (e.memoizedState = t.state !== null && t.state !== void 0 ? t.state : null),
    (t.updater = Bd),
    (e.stateNode = t),
    (t._reactInternals = e),
    r &&
      ((e = e.stateNode),
      (e.__reactInternalMemoizedUnmaskedChildContext = o),
      (e.__reactInternalMemoizedMaskedChildContext = a)),
    t
  );
}
function aw(e, t, n, r) {
  (e = t.state),
    typeof t.componentWillReceiveProps == "function" &&
      t.componentWillReceiveProps(n, r),
    typeof t.UNSAFE_componentWillReceiveProps == "function" &&
      t.UNSAFE_componentWillReceiveProps(n, r),
    t.state !== e && Bd.enqueueReplaceState(t, t.state, null);
}
function pv(e, t, n, r) {
  var o = e.stateNode;
  (o.props = n), (o.state = e.memoizedState), (o.refs = jP), Ag(e);
  var a = t.contextType;
  typeof a == "object" && a !== null
    ? (o.context = Bn(a))
    : ((a = nn(t) ? Ci : Lt.current), (o.context = Ma(e, a))),
    (o.state = e.memoizedState),
    (a = t.getDerivedStateFromProps),
    typeof a == "function" && (dv(e, t, a, n), (o.state = e.memoizedState)),
    typeof t.getDerivedStateFromProps == "function" ||
      typeof o.getSnapshotBeforeUpdate == "function" ||
      (typeof o.UNSAFE_componentWillMount != "function" &&
        typeof o.componentWillMount != "function") ||
      ((t = o.state),
      typeof o.componentWillMount == "function" && o.componentWillMount(),
      typeof o.UNSAFE_componentWillMount == "function" &&
        o.UNSAFE_componentWillMount(),
      t !== o.state && Bd.enqueueReplaceState(o, o.state, null),
      mf(e, n, o, r),
      (o.state = e.memoizedState)),
    typeof o.componentDidMount == "function" && (e.flags |= 4194308);
}
function Tl(e, t, n) {
  if (
    ((e = n.ref), e !== null && typeof e != "function" && typeof e != "object")
  ) {
    if (n._owner) {
      if (((n = n._owner), n)) {
        if (n.tag !== 1) throw Error(V(309));
        var r = n.stateNode;
      }
      if (!r) throw Error(V(147, e));
      var o = r,
        a = "" + e;
      return t !== null &&
        t.ref !== null &&
        typeof t.ref == "function" &&
        t.ref._stringRef === a
        ? t.ref
        : ((t = function (s) {
            var u = o.refs;
            u === jP && (u = o.refs = {}),
              s === null ? delete u[a] : (u[a] = s);
          }),
          (t._stringRef = a),
          t);
    }
    if (typeof e != "string") throw Error(V(284));
    if (!n._owner) throw Error(V(290, e));
  }
  return e;
}
function oc(e, t) {
  throw (
    ((e = Object.prototype.toString.call(t)),
    Error(
      V(
        31,
        e === "[object Object]"
          ? "object with keys {" + Object.keys(t).join(", ") + "}"
          : e
      )
    ))
  );
}
function lw(e) {
  var t = e._init;
  return t(e._payload);
}
function WP(e) {
  function t(_, w) {
    if (e) {
      var P = _.deletions;
      P === null ? ((_.deletions = [w]), (_.flags |= 16)) : P.push(w);
    }
  }
  function n(_, w) {
    if (!e) return null;
    for (; w !== null; ) t(_, w), (w = w.sibling);
    return null;
  }
  function r(_, w) {
    for (_ = new Map(); w !== null; )
      w.key !== null ? _.set(w.key, w) : _.set(w.index, w), (w = w.sibling);
    return _;
  }
  function o(_, w) {
    return (_ = jo(_, w)), (_.index = 0), (_.sibling = null), _;
  }
  function a(_, w, P) {
    return (
      (_.index = P),
      e
        ? ((P = _.alternate),
          P !== null
            ? ((P = P.index), P < w ? ((_.flags |= 2), w) : P)
            : ((_.flags |= 2), w))
        : ((_.flags |= 1048576), w)
    );
  }
  function s(_) {
    return e && _.alternate === null && (_.flags |= 2), _;
  }
  function u(_, w, P, k) {
    return w === null || w.tag !== 6
      ? ((w = fh(P, _.mode, k)), (w.return = _), w)
      : ((w = o(w, P)), (w.return = _), w);
  }
  function f(_, w, P, k) {
    var I = P.type;
    return I === ma
      ? m(_, w, P.props.children, k, P.key)
      : w !== null &&
        (w.elementType === I ||
          (typeof I == "object" &&
            I !== null &&
            I.$$typeof === Po &&
            lw(I) === w.type))
      ? ((k = o(w, P.props)), (k.ref = Tl(_, w, P)), (k.return = _), k)
      : ((k = Uc(P.type, P.key, P.props, null, _.mode, k)),
        (k.ref = Tl(_, w, P)),
        (k.return = _),
        k);
  }
  function d(_, w, P, k) {
    return w === null ||
      w.tag !== 4 ||
      w.stateNode.containerInfo !== P.containerInfo ||
      w.stateNode.implementation !== P.implementation
      ? ((w = dh(P, _.mode, k)), (w.return = _), w)
      : ((w = o(w, P.children || [])), (w.return = _), w);
  }
  function m(_, w, P, k, I) {
    return w === null || w.tag !== 7
      ? ((w = Ei(P, _.mode, k, I)), (w.return = _), w)
      : ((w = o(w, P)), (w.return = _), w);
  }
  function h(_, w, P) {
    if ((typeof w == "string" && w !== "") || typeof w == "number")
      return (w = fh("" + w, _.mode, P)), (w.return = _), w;
    if (typeof w == "object" && w !== null) {
      switch (w.$$typeof) {
        case Gu:
          return (
            (P = Uc(w.type, w.key, w.props, null, _.mode, P)),
            (P.ref = Tl(_, null, w)),
            (P.return = _),
            P
          );
        case pa:
          return (w = dh(w, _.mode, P)), (w.return = _), w;
        case Po:
          var k = w._init;
          return h(_, k(w._payload), P);
      }
      if (Ul(w) || Cl(w))
        return (w = Ei(w, _.mode, P, null)), (w.return = _), w;
      oc(_, w);
    }
    return null;
  }
  function v(_, w, P, k) {
    var I = w !== null ? w.key : null;
    if ((typeof P == "string" && P !== "") || typeof P == "number")
      return I !== null ? null : u(_, w, "" + P, k);
    if (typeof P == "object" && P !== null) {
      switch (P.$$typeof) {
        case Gu:
          return P.key === I ? f(_, w, P, k) : null;
        case pa:
          return P.key === I ? d(_, w, P, k) : null;
        case Po:
          return (I = P._init), v(_, w, I(P._payload), k);
      }
      if (Ul(P) || Cl(P)) return I !== null ? null : m(_, w, P, k, null);
      oc(_, P);
    }
    return null;
  }
  function b(_, w, P, k, I) {
    if ((typeof k == "string" && k !== "") || typeof k == "number")
      return (_ = _.get(P) || null), u(w, _, "" + k, I);
    if (typeof k == "object" && k !== null) {
      switch (k.$$typeof) {
        case Gu:
          return (_ = _.get(k.key === null ? P : k.key) || null), f(w, _, k, I);
        case pa:
          return (_ = _.get(k.key === null ? P : k.key) || null), d(w, _, k, I);
        case Po:
          var z = k._init;
          return b(_, w, P, z(k._payload), I);
      }
      if (Ul(k) || Cl(k)) return (_ = _.get(P) || null), m(w, _, k, I, null);
      oc(w, k);
    }
    return null;
  }
  function O(_, w, P, k) {
    for (
      var I = null, z = null, A = w, M = (w = 0), B = null;
      A !== null && M < P.length;
      M++
    ) {
      A.index > M ? ((B = A), (A = null)) : (B = A.sibling);
      var H = v(_, A, P[M], k);
      if (H === null) {
        A === null && (A = B);
        break;
      }
      e && A && H.alternate === null && t(_, A),
        (w = a(H, w, M)),
        z === null ? (I = H) : (z.sibling = H),
        (z = H),
        (A = B);
    }
    if (M === P.length) return n(_, A), Ge && pi(_, M), I;
    if (A === null) {
      for (; M < P.length; M++)
        (A = h(_, P[M], k)),
          A !== null &&
            ((w = a(A, w, M)), z === null ? (I = A) : (z.sibling = A), (z = A));
      return Ge && pi(_, M), I;
    }
    for (A = r(_, A); M < P.length; M++)
      (B = b(A, _, M, P[M], k)),
        B !== null &&
          (e && B.alternate !== null && A.delete(B.key === null ? M : B.key),
          (w = a(B, w, M)),
          z === null ? (I = B) : (z.sibling = B),
          (z = B));
    return (
      e &&
        A.forEach(function (q) {
          return t(_, q);
        }),
      Ge && pi(_, M),
      I
    );
  }
  function E(_, w, P, k) {
    var I = Cl(P);
    if (typeof I != "function") throw Error(V(150));
    if (((P = I.call(P)), P == null)) throw Error(V(151));
    for (
      var z = (I = null), A = w, M = (w = 0), B = null, H = P.next();
      A !== null && !H.done;
      M++, H = P.next()
    ) {
      A.index > M ? ((B = A), (A = null)) : (B = A.sibling);
      var q = v(_, A, H.value, k);
      if (q === null) {
        A === null && (A = B);
        break;
      }
      e && A && q.alternate === null && t(_, A),
        (w = a(q, w, M)),
        z === null ? (I = q) : (z.sibling = q),
        (z = q),
        (A = B);
    }
    if (H.done) return n(_, A), Ge && pi(_, M), I;
    if (A === null) {
      for (; !H.done; M++, H = P.next())
        (H = h(_, H.value, k)),
          H !== null &&
            ((w = a(H, w, M)), z === null ? (I = H) : (z.sibling = H), (z = H));
      return Ge && pi(_, M), I;
    }
    for (A = r(_, A); !H.done; M++, H = P.next())
      (H = b(A, _, M, H.value, k)),
        H !== null &&
          (e && H.alternate !== null && A.delete(H.key === null ? M : H.key),
          (w = a(H, w, M)),
          z === null ? (I = H) : (z.sibling = H),
          (z = H));
    return (
      e &&
        A.forEach(function (Z) {
          return t(_, Z);
        }),
      Ge && pi(_, M),
      I
    );
  }
  function $(_, w, P, k) {
    if (
      (typeof P == "object" &&
        P !== null &&
        P.type === ma &&
        P.key === null &&
        (P = P.props.children),
      typeof P == "object" && P !== null)
    ) {
      switch (P.$$typeof) {
        case Gu:
          e: {
            for (var I = P.key, z = w; z !== null; ) {
              if (z.key === I) {
                if (((I = P.type), I === ma)) {
                  if (z.tag === 7) {
                    n(_, z.sibling),
                      (w = o(z, P.props.children)),
                      (w.return = _),
                      (_ = w);
                    break e;
                  }
                } else if (
                  z.elementType === I ||
                  (typeof I == "object" &&
                    I !== null &&
                    I.$$typeof === Po &&
                    lw(I) === z.type)
                ) {
                  n(_, z.sibling),
                    (w = o(z, P.props)),
                    (w.ref = Tl(_, z, P)),
                    (w.return = _),
                    (_ = w);
                  break e;
                }
                n(_, z);
                break;
              } else t(_, z);
              z = z.sibling;
            }
            P.type === ma
              ? ((w = Ei(P.props.children, _.mode, k, P.key)),
                (w.return = _),
                (_ = w))
              : ((k = Uc(P.type, P.key, P.props, null, _.mode, k)),
                (k.ref = Tl(_, w, P)),
                (k.return = _),
                (_ = k));
          }
          return s(_);
        case pa:
          e: {
            for (z = P.key; w !== null; ) {
              if (w.key === z)
                if (
                  w.tag === 4 &&
                  w.stateNode.containerInfo === P.containerInfo &&
                  w.stateNode.implementation === P.implementation
                ) {
                  n(_, w.sibling),
                    (w = o(w, P.children || [])),
                    (w.return = _),
                    (_ = w);
                  break e;
                } else {
                  n(_, w);
                  break;
                }
              else t(_, w);
              w = w.sibling;
            }
            (w = dh(P, _.mode, k)), (w.return = _), (_ = w);
          }
          return s(_);
        case Po:
          return (z = P._init), $(_, w, z(P._payload), k);
      }
      if (Ul(P)) return O(_, w, P, k);
      if (Cl(P)) return E(_, w, P, k);
      oc(_, P);
    }
    return (typeof P == "string" && P !== "") || typeof P == "number"
      ? ((P = "" + P),
        w !== null && w.tag === 6
          ? (n(_, w.sibling), (w = o(w, P)), (w.return = _), (_ = w))
          : (n(_, w), (w = fh(P, _.mode, k)), (w.return = _), (_ = w)),
        s(_))
      : n(_, w);
  }
  return $;
}
var Fa = WP(!0),
  BP = WP(!1),
  Us = {},
  Pr = Ko(Us),
  bs = Ko(Us),
  xs = Ko(Us);
function bi(e) {
  if (e === Us) throw Error(V(174));
  return e;
}
function Lg(e, t) {
  switch ((Me(xs, t), Me(bs, e), Me(Pr, Us), (e = t.nodeType), e)) {
    case 9:
    case 11:
      t = (t = t.documentElement) ? t.namespaceURI : Vh(null, "");
      break;
    default:
      (e = e === 8 ? t.parentNode : t),
        (t = e.namespaceURI || null),
        (e = e.tagName),
        (t = Vh(t, e));
  }
  Ue(Pr), Me(Pr, t);
}
function Wa() {
  Ue(Pr), Ue(bs), Ue(xs);
}
function UP(e) {
  bi(xs.current);
  var t = bi(Pr.current),
    n = Vh(t, e.type);
  t !== n && (Me(bs, e), Me(Pr, n));
}
function Dg(e) {
  bs.current === e && (Ue(Pr), Ue(bs));
}
var Ke = Ko(0);
function hf(e) {
  for (var t = e; t !== null; ) {
    if (t.tag === 13) {
      var n = t.memoizedState;
      if (
        n !== null &&
        ((n = n.dehydrated), n === null || n.data === "$?" || n.data === "$!")
      )
        return t;
    } else if (t.tag === 19 && t.memoizedProps.revealOrder !== void 0) {
      if (t.flags & 128) return t;
    } else if (t.child !== null) {
      (t.child.return = t), (t = t.child);
      continue;
    }
    if (t === e) break;
    for (; t.sibling === null; ) {
      if (t.return === null || t.return === e) return null;
      t = t.return;
    }
    (t.sibling.return = t.return), (t = t.sibling);
  }
  return null;
}
var ih = [];
function Mg() {
  for (var e = 0; e < ih.length; e++)
    ih[e]._workInProgressVersionPrimary = null;
  ih.length = 0;
}
var Mc = ro.ReactCurrentDispatcher,
  ah = ro.ReactCurrentBatchConfig,
  Ri = 0,
  Qe = null,
  pt = null,
  yt = null,
  vf = !1,
  es = !1,
  Ss = 0,
  dz = 0;
function It() {
  throw Error(V(321));
}
function jg(e, t) {
  if (t === null) return !1;
  for (var n = 0; n < t.length && n < e.length; n++)
    if (!cr(e[n], t[n])) return !1;
  return !0;
}
function Fg(e, t, n, r, o, a) {
  if (
    ((Ri = a),
    (Qe = t),
    (t.memoizedState = null),
    (t.updateQueue = null),
    (t.lanes = 0),
    (Mc.current = e === null || e.memoizedState === null ? vz : gz),
    (e = n(r, o)),
    es)
  ) {
    a = 0;
    do {
      if (((es = !1), (Ss = 0), 25 <= a)) throw Error(V(301));
      (a += 1),
        (yt = pt = null),
        (t.updateQueue = null),
        (Mc.current = yz),
        (e = n(r, o));
    } while (es);
  }
  if (
    ((Mc.current = gf),
    (t = pt !== null && pt.next !== null),
    (Ri = 0),
    (yt = pt = Qe = null),
    (vf = !1),
    t)
  )
    throw Error(V(300));
  return e;
}
function Wg() {
  var e = Ss !== 0;
  return (Ss = 0), e;
}
function gr() {
  var e = {
    memoizedState: null,
    baseState: null,
    baseQueue: null,
    queue: null,
    next: null,
  };
  return yt === null ? (Qe.memoizedState = yt = e) : (yt = yt.next = e), yt;
}
function Un() {
  if (pt === null) {
    var e = Qe.alternate;
    e = e !== null ? e.memoizedState : null;
  } else e = pt.next;
  var t = yt === null ? Qe.memoizedState : yt.next;
  if (t !== null) (yt = t), (pt = e);
  else {
    if (e === null) throw Error(V(310));
    (pt = e),
      (e = {
        memoizedState: pt.memoizedState,
        baseState: pt.baseState,
        baseQueue: pt.baseQueue,
        queue: pt.queue,
        next: null,
      }),
      yt === null ? (Qe.memoizedState = yt = e) : (yt = yt.next = e);
  }
  return yt;
}
function Ps(e, t) {
  return typeof t == "function" ? t(e) : t;
}
function lh(e) {
  var t = Un(),
    n = t.queue;
  if (n === null) throw Error(V(311));
  n.lastRenderedReducer = e;
  var r = pt,
    o = r.baseQueue,
    a = n.pending;
  if (a !== null) {
    if (o !== null) {
      var s = o.next;
      (o.next = a.next), (a.next = s);
    }
    (r.baseQueue = o = a), (n.pending = null);
  }
  if (o !== null) {
    (a = o.next), (r = r.baseState);
    var u = (s = null),
      f = null,
      d = a;
    do {
      var m = d.lane;
      if ((Ri & m) === m)
        f !== null &&
          (f = f.next =
            {
              lane: 0,
              action: d.action,
              hasEagerState: d.hasEagerState,
              eagerState: d.eagerState,
              next: null,
            }),
          (r = d.hasEagerState ? d.eagerState : e(r, d.action));
      else {
        var h = {
          lane: m,
          action: d.action,
          hasEagerState: d.hasEagerState,
          eagerState: d.eagerState,
          next: null,
        };
        f === null ? ((u = f = h), (s = r)) : (f = f.next = h),
          (Qe.lanes |= m),
          (Ni |= m);
      }
      d = d.next;
    } while (d !== null && d !== a);
    f === null ? (s = r) : (f.next = u),
      cr(r, t.memoizedState) || (en = !0),
      (t.memoizedState = r),
      (t.baseState = s),
      (t.baseQueue = f),
      (n.lastRenderedState = r);
  }
  if (((e = n.interleaved), e !== null)) {
    o = e;
    do (a = o.lane), (Qe.lanes |= a), (Ni |= a), (o = o.next);
    while (o !== e);
  } else o === null && (n.lanes = 0);
  return [t.memoizedState, n.dispatch];
}
function sh(e) {
  var t = Un(),
    n = t.queue;
  if (n === null) throw Error(V(311));
  n.lastRenderedReducer = e;
  var r = n.dispatch,
    o = n.pending,
    a = t.memoizedState;
  if (o !== null) {
    n.pending = null;
    var s = (o = o.next);
    do (a = e(a, s.action)), (s = s.next);
    while (s !== o);
    cr(a, t.memoizedState) || (en = !0),
      (t.memoizedState = a),
      t.baseQueue === null && (t.baseState = a),
      (n.lastRenderedState = a);
  }
  return [a, r];
}
function HP() {}
function VP(e, t) {
  var n = Qe,
    r = Un(),
    o = t(),
    a = !cr(r.memoizedState, o);
  if (
    (a && ((r.memoizedState = o), (en = !0)),
    (r = r.queue),
    Bg(XP.bind(null, n, r, e), [e]),
    r.getSnapshot !== t || a || (yt !== null && yt.memoizedState.tag & 1))
  ) {
    if (
      ((n.flags |= 2048),
      Os(9, GP.bind(null, n, r, o, t), void 0, null),
      _t === null)
    )
      throw Error(V(349));
    Ri & 30 || YP(n, t, o);
  }
  return o;
}
function YP(e, t, n) {
  (e.flags |= 16384),
    (e = { getSnapshot: t, value: n }),
    (t = Qe.updateQueue),
    t === null
      ? ((t = { lastEffect: null, stores: null }),
        (Qe.updateQueue = t),
        (t.stores = [e]))
      : ((n = t.stores), n === null ? (t.stores = [e]) : n.push(e));
}
function GP(e, t, n, r) {
  (t.value = n), (t.getSnapshot = r), KP(t) && QP(e);
}
function XP(e, t, n) {
  return n(function () {
    KP(t) && QP(e);
  });
}
function KP(e) {
  var t = e.getSnapshot;
  e = e.value;
  try {
    var n = t();
    return !cr(e, n);
  } catch {
    return !0;
  }
}
function QP(e) {
  var t = Qr(e, 1);
  t !== null && lr(t, e, 1, -1);
}
function sw(e) {
  var t = gr();
  return (
    typeof e == "function" && (e = e()),
    (t.memoizedState = t.baseState = e),
    (e = {
      pending: null,
      interleaved: null,
      lanes: 0,
      dispatch: null,
      lastRenderedReducer: Ps,
      lastRenderedState: e,
    }),
    (t.queue = e),
    (e = e.dispatch = hz.bind(null, Qe, e)),
    [t.memoizedState, e]
  );
}
function Os(e, t, n, r) {
  return (
    (e = { tag: e, create: t, destroy: n, deps: r, next: null }),
    (t = Qe.updateQueue),
    t === null
      ? ((t = { lastEffect: null, stores: null }),
        (Qe.updateQueue = t),
        (t.lastEffect = e.next = e))
      : ((n = t.lastEffect),
        n === null
          ? (t.lastEffect = e.next = e)
          : ((r = n.next), (n.next = e), (e.next = r), (t.lastEffect = e))),
    e
  );
}
function qP() {
  return Un().memoizedState;
}
function jc(e, t, n, r) {
  var o = gr();
  (Qe.flags |= e),
    (o.memoizedState = Os(1 | t, n, void 0, r === void 0 ? null : r));
}
function Ud(e, t, n, r) {
  var o = Un();
  r = r === void 0 ? null : r;
  var a = void 0;
  if (pt !== null) {
    var s = pt.memoizedState;
    if (((a = s.destroy), r !== null && jg(r, s.deps))) {
      o.memoizedState = Os(t, n, a, r);
      return;
    }
  }
  (Qe.flags |= e), (o.memoizedState = Os(1 | t, n, a, r));
}
function uw(e, t) {
  return jc(8390656, 8, e, t);
}
function Bg(e, t) {
  return Ud(2048, 8, e, t);
}
function ZP(e, t) {
  return Ud(4, 2, e, t);
}
function JP(e, t) {
  return Ud(4, 4, e, t);
}
function eO(e, t) {
  if (typeof t == "function")
    return (
      (e = e()),
      t(e),
      function () {
        t(null);
      }
    );
  if (t != null)
    return (
      (e = e()),
      (t.current = e),
      function () {
        t.current = null;
      }
    );
}
function tO(e, t, n) {
  return (
    (n = n != null ? n.concat([e]) : null), Ud(4, 4, eO.bind(null, t, e), n)
  );
}
function Ug() {}
function nO(e, t) {
  var n = Un();
  t = t === void 0 ? null : t;
  var r = n.memoizedState;
  return r !== null && t !== null && jg(t, r[1])
    ? r[0]
    : ((n.memoizedState = [e, t]), e);
}
function rO(e, t) {
  var n = Un();
  t = t === void 0 ? null : t;
  var r = n.memoizedState;
  return r !== null && t !== null && jg(t, r[1])
    ? r[0]
    : ((e = e()), (n.memoizedState = [e, t]), e);
}
function oO(e, t, n) {
  return Ri & 21
    ? (cr(n, t) || ((n = lP()), (Qe.lanes |= n), (Ni |= n), (e.baseState = !0)),
      t)
    : (e.baseState && ((e.baseState = !1), (en = !0)), (e.memoizedState = n));
}
function pz(e, t) {
  var n = Ne;
  (Ne = n !== 0 && 4 > n ? n : 4), e(!0);
  var r = ah.transition;
  ah.transition = {};
  try {
    e(!1), t();
  } finally {
    (Ne = n), (ah.transition = r);
  }
}
function iO() {
  return Un().memoizedState;
}
function mz(e, t, n) {
  var r = Mo(e);
  if (
    ((n = {
      lane: r,
      action: n,
      hasEagerState: !1,
      eagerState: null,
      next: null,
    }),
    aO(e))
  )
    lO(t, n);
  else if (((n = DP(e, t, n, r)), n !== null)) {
    var o = Ht();
    lr(n, e, r, o), sO(n, t, r);
  }
}
function hz(e, t, n) {
  var r = Mo(e),
    o = { lane: r, action: n, hasEagerState: !1, eagerState: null, next: null };
  if (aO(e)) lO(t, o);
  else {
    var a = e.alternate;
    if (
      e.lanes === 0 &&
      (a === null || a.lanes === 0) &&
      ((a = t.lastRenderedReducer), a !== null)
    )
      try {
        var s = t.lastRenderedState,
          u = a(s, n);
        if (((o.hasEagerState = !0), (o.eagerState = u), cr(u, s))) {
          var f = t.interleaved;
          f === null
            ? ((o.next = o), zg(t))
            : ((o.next = f.next), (f.next = o)),
            (t.interleaved = o);
          return;
        }
      } catch {
      } finally {
      }
    (n = DP(e, t, o, r)),
      n !== null && ((o = Ht()), lr(n, e, r, o), sO(n, t, r));
  }
}
function aO(e) {
  var t = e.alternate;
  return e === Qe || (t !== null && t === Qe);
}
function lO(e, t) {
  es = vf = !0;
  var n = e.pending;
  n === null ? (t.next = t) : ((t.next = n.next), (n.next = t)),
    (e.pending = t);
}
function sO(e, t, n) {
  if (n & 4194240) {
    var r = t.lanes;
    (r &= e.pendingLanes), (n |= r), (t.lanes = n), wg(e, n);
  }
}
var gf = {
    readContext: Bn,
    useCallback: It,
    useContext: It,
    useEffect: It,
    useImperativeHandle: It,
    useInsertionEffect: It,
    useLayoutEffect: It,
    useMemo: It,
    useReducer: It,
    useRef: It,
    useState: It,
    useDebugValue: It,
    useDeferredValue: It,
    useTransition: It,
    useMutableSource: It,
    useSyncExternalStore: It,
    useId: It,
    unstable_isNewReconciler: !1,
  },
  vz = {
    readContext: Bn,
    useCallback: function (e, t) {
      return (gr().memoizedState = [e, t === void 0 ? null : t]), e;
    },
    useContext: Bn,
    useEffect: uw,
    useImperativeHandle: function (e, t, n) {
      return (
        (n = n != null ? n.concat([e]) : null),
        jc(4194308, 4, eO.bind(null, t, e), n)
      );
    },
    useLayoutEffect: function (e, t) {
      return jc(4194308, 4, e, t);
    },
    useInsertionEffect: function (e, t) {
      return jc(4, 2, e, t);
    },
    useMemo: function (e, t) {
      var n = gr();
      return (
        (t = t === void 0 ? null : t), (e = e()), (n.memoizedState = [e, t]), e
      );
    },
    useReducer: function (e, t, n) {
      var r = gr();
      return (
        (t = n !== void 0 ? n(t) : t),
        (r.memoizedState = r.baseState = t),
        (e = {
          pending: null,
          interleaved: null,
          lanes: 0,
          dispatch: null,
          lastRenderedReducer: e,
          lastRenderedState: t,
        }),
        (r.queue = e),
        (e = e.dispatch = mz.bind(null, Qe, e)),
        [r.memoizedState, e]
      );
    },
    useRef: function (e) {
      var t = gr();
      return (e = { current: e }), (t.memoizedState = e);
    },
    useState: sw,
    useDebugValue: Ug,
    useDeferredValue: function (e) {
      return (gr().memoizedState = e);
    },
    useTransition: function () {
      var e = sw(!1),
        t = e[0];
      return (e = pz.bind(null, e[1])), (gr().memoizedState = e), [t, e];
    },
    useMutableSource: function () {},
    useSyncExternalStore: function (e, t, n) {
      var r = Qe,
        o = gr();
      if (Ge) {
        if (n === void 0) throw Error(V(407));
        n = n();
      } else {
        if (((n = t()), _t === null)) throw Error(V(349));
        Ri & 30 || YP(r, t, n);
      }
      o.memoizedState = n;
      var a = { value: n, getSnapshot: t };
      return (
        (o.queue = a),
        uw(XP.bind(null, r, a, e), [e]),
        (r.flags |= 2048),
        Os(9, GP.bind(null, r, a, n, t), void 0, null),
        n
      );
    },
    useId: function () {
      var e = gr(),
        t = _t.identifierPrefix;
      if (Ge) {
        var n = Yr,
          r = Vr;
        (n = (r & ~(1 << (32 - ar(r) - 1))).toString(32) + n),
          (t = ":" + t + "R" + n),
          (n = Ss++),
          0 < n && (t += "H" + n.toString(32)),
          (t += ":");
      } else (n = dz++), (t = ":" + t + "r" + n.toString(32) + ":");
      return (e.memoizedState = t);
    },
    unstable_isNewReconciler: !1,
  },
  gz = {
    readContext: Bn,
    useCallback: nO,
    useContext: Bn,
    useEffect: Bg,
    useImperativeHandle: tO,
    useInsertionEffect: ZP,
    useLayoutEffect: JP,
    useMemo: rO,
    useReducer: lh,
    useRef: qP,
    useState: function () {
      return lh(Ps);
    },
    useDebugValue: Ug,
    useDeferredValue: function (e) {
      var t = Un();
      return oO(t, pt.memoizedState, e);
    },
    useTransition: function () {
      var e = lh(Ps)[0],
        t = Un().memoizedState;
      return [e, t];
    },
    useMutableSource: HP,
    useSyncExternalStore: VP,
    useId: iO,
    unstable_isNewReconciler: !1,
  },
  yz = {
    readContext: Bn,
    useCallback: nO,
    useContext: Bn,
    useEffect: Bg,
    useImperativeHandle: tO,
    useInsertionEffect: ZP,
    useLayoutEffect: JP,
    useMemo: rO,
    useReducer: sh,
    useRef: qP,
    useState: function () {
      return sh(Ps);
    },
    useDebugValue: Ug,
    useDeferredValue: function (e) {
      var t = Un();
      return pt === null ? (t.memoizedState = e) : oO(t, pt.memoizedState, e);
    },
    useTransition: function () {
      var e = sh(Ps)[0],
        t = Un().memoizedState;
      return [e, t];
    },
    useMutableSource: HP,
    useSyncExternalStore: VP,
    useId: iO,
    unstable_isNewReconciler: !1,
  };
function Ba(e, t) {
  try {
    var n = "",
      r = t;
    do (n += Y8(r)), (r = r.return);
    while (r);
    var o = n;
  } catch (a) {
    o =
      `
Error generating stack: ` +
      a.message +
      `
` +
      a.stack;
  }
  return { value: e, source: t, stack: o, digest: null };
}
function uh(e, t, n) {
  return { value: e, source: null, stack: n ?? null, digest: t ?? null };
}
function mv(e, t) {
  try {
    console.error(t.value);
  } catch (n) {
    setTimeout(function () {
      throw n;
    });
  }
}
var _z = typeof WeakMap == "function" ? WeakMap : Map;
function uO(e, t, n) {
  (n = Gr(-1, n)), (n.tag = 3), (n.payload = { element: null });
  var r = t.value;
  return (
    (n.callback = function () {
      _f || ((_f = !0), (Pv = r)), mv(e, t);
    }),
    n
  );
}
function cO(e, t, n) {
  (n = Gr(-1, n)), (n.tag = 3);
  var r = e.type.getDerivedStateFromError;
  if (typeof r == "function") {
    var o = t.value;
    (n.payload = function () {
      return r(o);
    }),
      (n.callback = function () {
        mv(e, t);
      });
  }
  var a = e.stateNode;
  return (
    a !== null &&
      typeof a.componentDidCatch == "function" &&
      (n.callback = function () {
        mv(e, t),
          typeof r != "function" &&
            (Do === null ? (Do = new Set([this])) : Do.add(this));
        var s = t.stack;
        this.componentDidCatch(t.value, {
          componentStack: s !== null ? s : "",
        });
      }),
    n
  );
}
function cw(e, t, n) {
  var r = e.pingCache;
  if (r === null) {
    r = e.pingCache = new _z();
    var o = new Set();
    r.set(t, o);
  } else (o = r.get(t)), o === void 0 && ((o = new Set()), r.set(t, o));
  o.has(n) || (o.add(n), (e = Tz.bind(null, e, t, n)), t.then(e, e));
}
function fw(e) {
  do {
    var t;
    if (
      ((t = e.tag === 13) &&
        ((t = e.memoizedState), (t = t !== null ? t.dehydrated !== null : !0)),
      t)
    )
      return e;
    e = e.return;
  } while (e !== null);
  return null;
}
function dw(e, t, n, r, o) {
  return e.mode & 1
    ? ((e.flags |= 65536), (e.lanes = o), e)
    : (e === t
        ? (e.flags |= 65536)
        : ((e.flags |= 128),
          (n.flags |= 131072),
          (n.flags &= -52805),
          n.tag === 1 &&
            (n.alternate === null
              ? (n.tag = 17)
              : ((t = Gr(-1, 1)), (t.tag = 2), Lo(n, t, 1))),
          (n.lanes |= 1)),
      e);
}
var wz = ro.ReactCurrentOwner,
  en = !1;
function Ut(e, t, n, r) {
  t.child = e === null ? BP(t, null, n, r) : Fa(t, e.child, n, r);
}
function pw(e, t, n, r, o) {
  n = n.render;
  var a = t.ref;
  return (
    Ia(t, o),
    (r = Fg(e, t, n, r, a, o)),
    (n = Wg()),
    e !== null && !en
      ? ((t.updateQueue = e.updateQueue),
        (t.flags &= -2053),
        (e.lanes &= ~o),
        qr(e, t, o))
      : (Ge && n && Cg(t), (t.flags |= 1), Ut(e, t, r, o), t.child)
  );
}
function mw(e, t, n, r, o) {
  if (e === null) {
    var a = n.type;
    return typeof a == "function" &&
      !qg(a) &&
      a.defaultProps === void 0 &&
      n.compare === null &&
      n.defaultProps === void 0
      ? ((t.tag = 15), (t.type = a), fO(e, t, a, r, o))
      : ((e = Uc(n.type, null, r, t, t.mode, o)),
        (e.ref = t.ref),
        (e.return = t),
        (t.child = e));
  }
  if (((a = e.child), !(e.lanes & o))) {
    var s = a.memoizedProps;
    if (
      ((n = n.compare), (n = n !== null ? n : gs), n(s, r) && e.ref === t.ref)
    )
      return qr(e, t, o);
  }
  return (
    (t.flags |= 1),
    (e = jo(a, r)),
    (e.ref = t.ref),
    (e.return = t),
    (t.child = e)
  );
}
function fO(e, t, n, r, o) {
  if (e !== null) {
    var a = e.memoizedProps;
    if (gs(a, r) && e.ref === t.ref)
      if (((en = !1), (t.pendingProps = r = a), (e.lanes & o) !== 0))
        e.flags & 131072 && (en = !0);
      else return (t.lanes = e.lanes), qr(e, t, o);
  }
  return hv(e, t, n, r, o);
}
function dO(e, t, n) {
  var r = t.pendingProps,
    o = r.children,
    a = e !== null ? e.memoizedState : null;
  if (r.mode === "hidden")
    if (!(t.mode & 1))
      (t.memoizedState = { baseLanes: 0, cachePool: null, transitions: null }),
        Me(Pa, vn),
        (vn |= n);
    else {
      if (!(n & 1073741824))
        return (
          (e = a !== null ? a.baseLanes | n : n),
          (t.lanes = t.childLanes = 1073741824),
          (t.memoizedState = {
            baseLanes: e,
            cachePool: null,
            transitions: null,
          }),
          (t.updateQueue = null),
          Me(Pa, vn),
          (vn |= e),
          null
        );
      (t.memoizedState = { baseLanes: 0, cachePool: null, transitions: null }),
        (r = a !== null ? a.baseLanes : n),
        Me(Pa, vn),
        (vn |= r);
    }
  else
    a !== null ? ((r = a.baseLanes | n), (t.memoizedState = null)) : (r = n),
      Me(Pa, vn),
      (vn |= r);
  return Ut(e, t, o, n), t.child;
}
function pO(e, t) {
  var n = t.ref;
  ((e === null && n !== null) || (e !== null && e.ref !== n)) &&
    ((t.flags |= 512), (t.flags |= 2097152));
}
function hv(e, t, n, r, o) {
  var a = nn(n) ? Ci : Lt.current;
  return (
    (a = Ma(t, a)),
    Ia(t, o),
    (n = Fg(e, t, n, r, a, o)),
    (r = Wg()),
    e !== null && !en
      ? ((t.updateQueue = e.updateQueue),
        (t.flags &= -2053),
        (e.lanes &= ~o),
        qr(e, t, o))
      : (Ge && r && Cg(t), (t.flags |= 1), Ut(e, t, n, o), t.child)
  );
}
function hw(e, t, n, r, o) {
  if (nn(n)) {
    var a = !0;
    uf(t);
  } else a = !1;
  if ((Ia(t, o), t.stateNode === null))
    Fc(e, t), FP(t, n, r), pv(t, n, r, o), (r = !0);
  else if (e === null) {
    var s = t.stateNode,
      u = t.memoizedProps;
    s.props = u;
    var f = s.context,
      d = n.contextType;
    typeof d == "object" && d !== null
      ? (d = Bn(d))
      : ((d = nn(n) ? Ci : Lt.current), (d = Ma(t, d)));
    var m = n.getDerivedStateFromProps,
      h =
        typeof m == "function" ||
        typeof s.getSnapshotBeforeUpdate == "function";
    h ||
      (typeof s.UNSAFE_componentWillReceiveProps != "function" &&
        typeof s.componentWillReceiveProps != "function") ||
      ((u !== r || f !== d) && aw(t, s, r, d)),
      (Oo = !1);
    var v = t.memoizedState;
    (s.state = v),
      mf(t, r, s, o),
      (f = t.memoizedState),
      u !== r || v !== f || tn.current || Oo
        ? (typeof m == "function" && (dv(t, n, m, r), (f = t.memoizedState)),
          (u = Oo || iw(t, n, u, r, v, f, d))
            ? (h ||
                (typeof s.UNSAFE_componentWillMount != "function" &&
                  typeof s.componentWillMount != "function") ||
                (typeof s.componentWillMount == "function" &&
                  s.componentWillMount(),
                typeof s.UNSAFE_componentWillMount == "function" &&
                  s.UNSAFE_componentWillMount()),
              typeof s.componentDidMount == "function" && (t.flags |= 4194308))
            : (typeof s.componentDidMount == "function" && (t.flags |= 4194308),
              (t.memoizedProps = r),
              (t.memoizedState = f)),
          (s.props = r),
          (s.state = f),
          (s.context = d),
          (r = u))
        : (typeof s.componentDidMount == "function" && (t.flags |= 4194308),
          (r = !1));
  } else {
    (s = t.stateNode),
      MP(e, t),
      (u = t.memoizedProps),
      (d = t.type === t.elementType ? u : nr(t.type, u)),
      (s.props = d),
      (h = t.pendingProps),
      (v = s.context),
      (f = n.contextType),
      typeof f == "object" && f !== null
        ? (f = Bn(f))
        : ((f = nn(n) ? Ci : Lt.current), (f = Ma(t, f)));
    var b = n.getDerivedStateFromProps;
    (m =
      typeof b == "function" ||
      typeof s.getSnapshotBeforeUpdate == "function") ||
      (typeof s.UNSAFE_componentWillReceiveProps != "function" &&
        typeof s.componentWillReceiveProps != "function") ||
      ((u !== h || v !== f) && aw(t, s, r, f)),
      (Oo = !1),
      (v = t.memoizedState),
      (s.state = v),
      mf(t, r, s, o);
    var O = t.memoizedState;
    u !== h || v !== O || tn.current || Oo
      ? (typeof b == "function" && (dv(t, n, b, r), (O = t.memoizedState)),
        (d = Oo || iw(t, n, d, r, v, O, f) || !1)
          ? (m ||
              (typeof s.UNSAFE_componentWillUpdate != "function" &&
                typeof s.componentWillUpdate != "function") ||
              (typeof s.componentWillUpdate == "function" &&
                s.componentWillUpdate(r, O, f),
              typeof s.UNSAFE_componentWillUpdate == "function" &&
                s.UNSAFE_componentWillUpdate(r, O, f)),
            typeof s.componentDidUpdate == "function" && (t.flags |= 4),
            typeof s.getSnapshotBeforeUpdate == "function" && (t.flags |= 1024))
          : (typeof s.componentDidUpdate != "function" ||
              (u === e.memoizedProps && v === e.memoizedState) ||
              (t.flags |= 4),
            typeof s.getSnapshotBeforeUpdate != "function" ||
              (u === e.memoizedProps && v === e.memoizedState) ||
              (t.flags |= 1024),
            (t.memoizedProps = r),
            (t.memoizedState = O)),
        (s.props = r),
        (s.state = O),
        (s.context = f),
        (r = d))
      : (typeof s.componentDidUpdate != "function" ||
          (u === e.memoizedProps && v === e.memoizedState) ||
          (t.flags |= 4),
        typeof s.getSnapshotBeforeUpdate != "function" ||
          (u === e.memoizedProps && v === e.memoizedState) ||
          (t.flags |= 1024),
        (r = !1));
  }
  return vv(e, t, n, r, a, o);
}
function vv(e, t, n, r, o, a) {
  pO(e, t);
  var s = (t.flags & 128) !== 0;
  if (!r && !s) return o && ew(t, n, !1), qr(e, t, a);
  (r = t.stateNode), (wz.current = t);
  var u =
    s && typeof n.getDerivedStateFromError != "function" ? null : r.render();
  return (
    (t.flags |= 1),
    e !== null && s
      ? ((t.child = Fa(t, e.child, null, a)), (t.child = Fa(t, null, u, a)))
      : Ut(e, t, u, a),
    (t.memoizedState = r.state),
    o && ew(t, n, !0),
    t.child
  );
}
function mO(e) {
  var t = e.stateNode;
  t.pendingContext
    ? J_(e, t.pendingContext, t.pendingContext !== t.context)
    : t.context && J_(e, t.context, !1),
    Lg(e, t.containerInfo);
}
function vw(e, t, n, r, o) {
  return ja(), Rg(o), (t.flags |= 256), Ut(e, t, n, r), t.child;
}
var gv = { dehydrated: null, treeContext: null, retryLane: 0 };
function yv(e) {
  return { baseLanes: e, cachePool: null, transitions: null };
}
function hO(e, t, n) {
  var r = t.pendingProps,
    o = Ke.current,
    a = !1,
    s = (t.flags & 128) !== 0,
    u;
  if (
    ((u = s) ||
      (u = e !== null && e.memoizedState === null ? !1 : (o & 2) !== 0),
    u
      ? ((a = !0), (t.flags &= -129))
      : (e === null || e.memoizedState !== null) && (o |= 1),
    Me(Ke, o & 1),
    e === null)
  )
    return (
      cv(t),
      (e = t.memoizedState),
      e !== null && ((e = e.dehydrated), e !== null)
        ? (t.mode & 1
            ? e.data === "$!"
              ? (t.lanes = 8)
              : (t.lanes = 1073741824)
            : (t.lanes = 1),
          null)
        : ((s = r.children),
          (e = r.fallback),
          a
            ? ((r = t.mode),
              (a = t.child),
              (s = { mode: "hidden", children: s }),
              !(r & 1) && a !== null
                ? ((a.childLanes = 0), (a.pendingProps = s))
                : (a = Yd(s, r, 0, null)),
              (e = Ei(e, r, n, null)),
              (a.return = t),
              (e.return = t),
              (a.sibling = e),
              (t.child = a),
              (t.child.memoizedState = yv(n)),
              (t.memoizedState = gv),
              e)
            : Hg(t, s))
    );
  if (((o = e.memoizedState), o !== null && ((u = o.dehydrated), u !== null)))
    return bz(e, t, s, r, u, o, n);
  if (a) {
    (a = r.fallback), (s = t.mode), (o = e.child), (u = o.sibling);
    var f = { mode: "hidden", children: r.children };
    return (
      !(s & 1) && t.child !== o
        ? ((r = t.child),
          (r.childLanes = 0),
          (r.pendingProps = f),
          (t.deletions = null))
        : ((r = jo(o, f)), (r.subtreeFlags = o.subtreeFlags & 14680064)),
      u !== null ? (a = jo(u, a)) : ((a = Ei(a, s, n, null)), (a.flags |= 2)),
      (a.return = t),
      (r.return = t),
      (r.sibling = a),
      (t.child = r),
      (r = a),
      (a = t.child),
      (s = e.child.memoizedState),
      (s =
        s === null
          ? yv(n)
          : {
              baseLanes: s.baseLanes | n,
              cachePool: null,
              transitions: s.transitions,
            }),
      (a.memoizedState = s),
      (a.childLanes = e.childLanes & ~n),
      (t.memoizedState = gv),
      r
    );
  }
  return (
    (a = e.child),
    (e = a.sibling),
    (r = jo(a, { mode: "visible", children: r.children })),
    !(t.mode & 1) && (r.lanes = n),
    (r.return = t),
    (r.sibling = null),
    e !== null &&
      ((n = t.deletions),
      n === null ? ((t.deletions = [e]), (t.flags |= 16)) : n.push(e)),
    (t.child = r),
    (t.memoizedState = null),
    r
  );
}
function Hg(e, t) {
  return (
    (t = Yd({ mode: "visible", children: t }, e.mode, 0, null)),
    (t.return = e),
    (e.child = t)
  );
}
function ic(e, t, n, r) {
  return (
    r !== null && Rg(r),
    Fa(t, e.child, null, n),
    (e = Hg(t, t.pendingProps.children)),
    (e.flags |= 2),
    (t.memoizedState = null),
    e
  );
}
function bz(e, t, n, r, o, a, s) {
  if (n)
    return t.flags & 256
      ? ((t.flags &= -257), (r = uh(Error(V(422)))), ic(e, t, s, r))
      : t.memoizedState !== null
      ? ((t.child = e.child), (t.flags |= 128), null)
      : ((a = r.fallback),
        (o = t.mode),
        (r = Yd({ mode: "visible", children: r.children }, o, 0, null)),
        (a = Ei(a, o, s, null)),
        (a.flags |= 2),
        (r.return = t),
        (a.return = t),
        (r.sibling = a),
        (t.child = r),
        t.mode & 1 && Fa(t, e.child, null, s),
        (t.child.memoizedState = yv(s)),
        (t.memoizedState = gv),
        a);
  if (!(t.mode & 1)) return ic(e, t, s, null);
  if (o.data === "$!") {
    if (((r = o.nextSibling && o.nextSibling.dataset), r)) var u = r.dgst;
    return (r = u), (a = Error(V(419))), (r = uh(a, r, void 0)), ic(e, t, s, r);
  }
  if (((u = (s & e.childLanes) !== 0), en || u)) {
    if (((r = _t), r !== null)) {
      switch (s & -s) {
        case 4:
          o = 2;
          break;
        case 16:
          o = 8;
          break;
        case 64:
        case 128:
        case 256:
        case 512:
        case 1024:
        case 2048:
        case 4096:
        case 8192:
        case 16384:
        case 32768:
        case 65536:
        case 131072:
        case 262144:
        case 524288:
        case 1048576:
        case 2097152:
        case 4194304:
        case 8388608:
        case 16777216:
        case 33554432:
        case 67108864:
          o = 32;
          break;
        case 536870912:
          o = 268435456;
          break;
        default:
          o = 0;
      }
      (o = o & (r.suspendedLanes | s) ? 0 : o),
        o !== 0 &&
          o !== a.retryLane &&
          ((a.retryLane = o), Qr(e, o), lr(r, e, o, -1));
    }
    return Qg(), (r = uh(Error(V(421)))), ic(e, t, s, r);
  }
  return o.data === "$?"
    ? ((t.flags |= 128),
      (t.child = e.child),
      (t = zz.bind(null, e)),
      (o._reactRetry = t),
      null)
    : ((e = a.treeContext),
      (gn = Ao(o.nextSibling)),
      (_n = t),
      (Ge = !0),
      (or = null),
      e !== null &&
        ((Dn[Mn++] = Vr),
        (Dn[Mn++] = Yr),
        (Dn[Mn++] = ki),
        (Vr = e.id),
        (Yr = e.overflow),
        (ki = t)),
      (t = Hg(t, r.children)),
      (t.flags |= 4096),
      t);
}
function gw(e, t, n) {
  e.lanes |= t;
  var r = e.alternate;
  r !== null && (r.lanes |= t), fv(e.return, t, n);
}
function ch(e, t, n, r, o) {
  var a = e.memoizedState;
  a === null
    ? (e.memoizedState = {
        isBackwards: t,
        rendering: null,
        renderingStartTime: 0,
        last: r,
        tail: n,
        tailMode: o,
      })
    : ((a.isBackwards = t),
      (a.rendering = null),
      (a.renderingStartTime = 0),
      (a.last = r),
      (a.tail = n),
      (a.tailMode = o));
}
function vO(e, t, n) {
  var r = t.pendingProps,
    o = r.revealOrder,
    a = r.tail;
  if ((Ut(e, t, r.children, n), (r = Ke.current), r & 2))
    (r = (r & 1) | 2), (t.flags |= 128);
  else {
    if (e !== null && e.flags & 128)
      e: for (e = t.child; e !== null; ) {
        if (e.tag === 13) e.memoizedState !== null && gw(e, n, t);
        else if (e.tag === 19) gw(e, n, t);
        else if (e.child !== null) {
          (e.child.return = e), (e = e.child);
          continue;
        }
        if (e === t) break e;
        for (; e.sibling === null; ) {
          if (e.return === null || e.return === t) break e;
          e = e.return;
        }
        (e.sibling.return = e.return), (e = e.sibling);
      }
    r &= 1;
  }
  if ((Me(Ke, r), !(t.mode & 1))) t.memoizedState = null;
  else
    switch (o) {
      case "forwards":
        for (n = t.child, o = null; n !== null; )
          (e = n.alternate),
            e !== null && hf(e) === null && (o = n),
            (n = n.sibling);
        (n = o),
          n === null
            ? ((o = t.child), (t.child = null))
            : ((o = n.sibling), (n.sibling = null)),
          ch(t, !1, o, n, a);
        break;
      case "backwards":
        for (n = null, o = t.child, t.child = null; o !== null; ) {
          if (((e = o.alternate), e !== null && hf(e) === null)) {
            t.child = o;
            break;
          }
          (e = o.sibling), (o.sibling = n), (n = o), (o = e);
        }
        ch(t, !0, n, null, a);
        break;
      case "together":
        ch(t, !1, null, null, void 0);
        break;
      default:
        t.memoizedState = null;
    }
  return t.child;
}
function Fc(e, t) {
  !(t.mode & 1) &&
    e !== null &&
    ((e.alternate = null), (t.alternate = null), (t.flags |= 2));
}
function qr(e, t, n) {
  if (
    (e !== null && (t.dependencies = e.dependencies),
    (Ni |= t.lanes),
    !(n & t.childLanes))
  )
    return null;
  if (e !== null && t.child !== e.child) throw Error(V(153));
  if (t.child !== null) {
    for (
      e = t.child, n = jo(e, e.pendingProps), t.child = n, n.return = t;
      e.sibling !== null;

    )
      (e = e.sibling), (n = n.sibling = jo(e, e.pendingProps)), (n.return = t);
    n.sibling = null;
  }
  return t.child;
}
function xz(e, t, n) {
  switch (t.tag) {
    case 3:
      mO(t), ja();
      break;
    case 5:
      UP(t);
      break;
    case 1:
      nn(t.type) && uf(t);
      break;
    case 4:
      Lg(t, t.stateNode.containerInfo);
      break;
    case 10:
      var r = t.type._context,
        o = t.memoizedProps.value;
      Me(df, r._currentValue), (r._currentValue = o);
      break;
    case 13:
      if (((r = t.memoizedState), r !== null))
        return r.dehydrated !== null
          ? (Me(Ke, Ke.current & 1), (t.flags |= 128), null)
          : n & t.child.childLanes
          ? hO(e, t, n)
          : (Me(Ke, Ke.current & 1),
            (e = qr(e, t, n)),
            e !== null ? e.sibling : null);
      Me(Ke, Ke.current & 1);
      break;
    case 19:
      if (((r = (n & t.childLanes) !== 0), e.flags & 128)) {
        if (r) return vO(e, t, n);
        t.flags |= 128;
      }
      if (
        ((o = t.memoizedState),
        o !== null &&
          ((o.rendering = null), (o.tail = null), (o.lastEffect = null)),
        Me(Ke, Ke.current),
        r)
      )
        break;
      return null;
    case 22:
    case 23:
      return (t.lanes = 0), dO(e, t, n);
  }
  return qr(e, t, n);
}
var gO, _v, yO, _O;
gO = function (e, t) {
  for (var n = t.child; n !== null; ) {
    if (n.tag === 5 || n.tag === 6) e.appendChild(n.stateNode);
    else if (n.tag !== 4 && n.child !== null) {
      (n.child.return = n), (n = n.child);
      continue;
    }
    if (n === t) break;
    for (; n.sibling === null; ) {
      if (n.return === null || n.return === t) return;
      n = n.return;
    }
    (n.sibling.return = n.return), (n = n.sibling);
  }
};
_v = function () {};
yO = function (e, t, n, r) {
  var o = e.memoizedProps;
  if (o !== r) {
    (e = t.stateNode), bi(Pr.current);
    var a = null;
    switch (n) {
      case "input":
        (o = Wh(e, o)), (r = Wh(e, r)), (a = []);
        break;
      case "select":
        (o = qe({}, o, { value: void 0 })),
          (r = qe({}, r, { value: void 0 })),
          (a = []);
        break;
      case "textarea":
        (o = Hh(e, o)), (r = Hh(e, r)), (a = []);
        break;
      default:
        typeof o.onClick != "function" &&
          typeof r.onClick == "function" &&
          (e.onclick = lf);
    }
    Yh(n, r);
    var s;
    n = null;
    for (d in o)
      if (!r.hasOwnProperty(d) && o.hasOwnProperty(d) && o[d] != null)
        if (d === "style") {
          var u = o[d];
          for (s in u) u.hasOwnProperty(s) && (n || (n = {}), (n[s] = ""));
        } else
          d !== "dangerouslySetInnerHTML" &&
            d !== "children" &&
            d !== "suppressContentEditableWarning" &&
            d !== "suppressHydrationWarning" &&
            d !== "autoFocus" &&
            (cs.hasOwnProperty(d)
              ? a || (a = [])
              : (a = a || []).push(d, null));
    for (d in r) {
      var f = r[d];
      if (
        ((u = o != null ? o[d] : void 0),
        r.hasOwnProperty(d) && f !== u && (f != null || u != null))
      )
        if (d === "style")
          if (u) {
            for (s in u)
              !u.hasOwnProperty(s) ||
                (f && f.hasOwnProperty(s)) ||
                (n || (n = {}), (n[s] = ""));
            for (s in f)
              f.hasOwnProperty(s) &&
                u[s] !== f[s] &&
                (n || (n = {}), (n[s] = f[s]));
          } else n || (a || (a = []), a.push(d, n)), (n = f);
        else
          d === "dangerouslySetInnerHTML"
            ? ((f = f ? f.__html : void 0),
              (u = u ? u.__html : void 0),
              f != null && u !== f && (a = a || []).push(d, f))
            : d === "children"
            ? (typeof f != "string" && typeof f != "number") ||
              (a = a || []).push(d, "" + f)
            : d !== "suppressContentEditableWarning" &&
              d !== "suppressHydrationWarning" &&
              (cs.hasOwnProperty(d)
                ? (f != null && d === "onScroll" && We("scroll", e),
                  a || u === f || (a = []))
                : (a = a || []).push(d, f));
    }
    n && (a = a || []).push("style", n);
    var d = a;
    (t.updateQueue = d) && (t.flags |= 4);
  }
};
_O = function (e, t, n, r) {
  n !== r && (t.flags |= 4);
};
function zl(e, t) {
  if (!Ge)
    switch (e.tailMode) {
      case "hidden":
        t = e.tail;
        for (var n = null; t !== null; )
          t.alternate !== null && (n = t), (t = t.sibling);
        n === null ? (e.tail = null) : (n.sibling = null);
        break;
      case "collapsed":
        n = e.tail;
        for (var r = null; n !== null; )
          n.alternate !== null && (r = n), (n = n.sibling);
        r === null
          ? t || e.tail === null
            ? (e.tail = null)
            : (e.tail.sibling = null)
          : (r.sibling = null);
    }
}
function Tt(e) {
  var t = e.alternate !== null && e.alternate.child === e.child,
    n = 0,
    r = 0;
  if (t)
    for (var o = e.child; o !== null; )
      (n |= o.lanes | o.childLanes),
        (r |= o.subtreeFlags & 14680064),
        (r |= o.flags & 14680064),
        (o.return = e),
        (o = o.sibling);
  else
    for (o = e.child; o !== null; )
      (n |= o.lanes | o.childLanes),
        (r |= o.subtreeFlags),
        (r |= o.flags),
        (o.return = e),
        (o = o.sibling);
  return (e.subtreeFlags |= r), (e.childLanes = n), t;
}
function Sz(e, t, n) {
  var r = t.pendingProps;
  switch ((kg(t), t.tag)) {
    case 2:
    case 16:
    case 15:
    case 0:
    case 11:
    case 7:
    case 8:
    case 12:
    case 9:
    case 14:
      return Tt(t), null;
    case 1:
      return nn(t.type) && sf(), Tt(t), null;
    case 3:
      return (
        (r = t.stateNode),
        Wa(),
        Ue(tn),
        Ue(Lt),
        Mg(),
        r.pendingContext &&
          ((r.context = r.pendingContext), (r.pendingContext = null)),
        (e === null || e.child === null) &&
          (rc(t)
            ? (t.flags |= 4)
            : e === null ||
              (e.memoizedState.isDehydrated && !(t.flags & 256)) ||
              ((t.flags |= 1024), or !== null && ($v(or), (or = null)))),
        _v(e, t),
        Tt(t),
        null
      );
    case 5:
      Dg(t);
      var o = bi(xs.current);
      if (((n = t.type), e !== null && t.stateNode != null))
        yO(e, t, n, r, o),
          e.ref !== t.ref && ((t.flags |= 512), (t.flags |= 2097152));
      else {
        if (!r) {
          if (t.stateNode === null) throw Error(V(166));
          return Tt(t), null;
        }
        if (((e = bi(Pr.current)), rc(t))) {
          (r = t.stateNode), (n = t.type);
          var a = t.memoizedProps;
          switch (((r[br] = t), (r[ws] = a), (e = (t.mode & 1) !== 0), n)) {
            case "dialog":
              We("cancel", r), We("close", r);
              break;
            case "iframe":
            case "object":
            case "embed":
              We("load", r);
              break;
            case "video":
            case "audio":
              for (o = 0; o < Vl.length; o++) We(Vl[o], r);
              break;
            case "source":
              We("error", r);
              break;
            case "img":
            case "image":
            case "link":
              We("error", r), We("load", r);
              break;
            case "details":
              We("toggle", r);
              break;
            case "input":
              E_(r, a), We("invalid", r);
              break;
            case "select":
              (r._wrapperState = { wasMultiple: !!a.multiple }),
                We("invalid", r);
              break;
            case "textarea":
              C_(r, a), We("invalid", r);
          }
          Yh(n, a), (o = null);
          for (var s in a)
            if (a.hasOwnProperty(s)) {
              var u = a[s];
              s === "children"
                ? typeof u == "string"
                  ? r.textContent !== u &&
                    (a.suppressHydrationWarning !== !0 &&
                      nc(r.textContent, u, e),
                    (o = ["children", u]))
                  : typeof u == "number" &&
                    r.textContent !== "" + u &&
                    (a.suppressHydrationWarning !== !0 &&
                      nc(r.textContent, u, e),
                    (o = ["children", "" + u]))
                : cs.hasOwnProperty(s) &&
                  u != null &&
                  s === "onScroll" &&
                  We("scroll", r);
            }
          switch (n) {
            case "input":
              Xu(r), $_(r, a, !0);
              break;
            case "textarea":
              Xu(r), k_(r);
              break;
            case "select":
            case "option":
              break;
            default:
              typeof a.onClick == "function" && (r.onclick = lf);
          }
          (r = o), (t.updateQueue = r), r !== null && (t.flags |= 4);
        } else {
          (s = o.nodeType === 9 ? o : o.ownerDocument),
            e === "http://www.w3.org/1999/xhtml" && (e = YS(n)),
            e === "http://www.w3.org/1999/xhtml"
              ? n === "script"
                ? ((e = s.createElement("div")),
                  (e.innerHTML = "<script></script>"),
                  (e = e.removeChild(e.firstChild)))
                : typeof r.is == "string"
                ? (e = s.createElement(n, { is: r.is }))
                : ((e = s.createElement(n)),
                  n === "select" &&
                    ((s = e),
                    r.multiple
                      ? (s.multiple = !0)
                      : r.size && (s.size = r.size)))
              : (e = s.createElementNS(e, n)),
            (e[br] = t),
            (e[ws] = r),
            gO(e, t, !1, !1),
            (t.stateNode = e);
          e: {
            switch (((s = Gh(n, r)), n)) {
              case "dialog":
                We("cancel", e), We("close", e), (o = r);
                break;
              case "iframe":
              case "object":
              case "embed":
                We("load", e), (o = r);
                break;
              case "video":
              case "audio":
                for (o = 0; o < Vl.length; o++) We(Vl[o], e);
                o = r;
                break;
              case "source":
                We("error", e), (o = r);
                break;
              case "img":
              case "image":
              case "link":
                We("error", e), We("load", e), (o = r);
                break;
              case "details":
                We("toggle", e), (o = r);
                break;
              case "input":
                E_(e, r), (o = Wh(e, r)), We("invalid", e);
                break;
              case "option":
                o = r;
                break;
              case "select":
                (e._wrapperState = { wasMultiple: !!r.multiple }),
                  (o = qe({}, r, { value: void 0 })),
                  We("invalid", e);
                break;
              case "textarea":
                C_(e, r), (o = Hh(e, r)), We("invalid", e);
                break;
              default:
                o = r;
            }
            Yh(n, o), (u = o);
            for (a in u)
              if (u.hasOwnProperty(a)) {
                var f = u[a];
                a === "style"
                  ? KS(e, f)
                  : a === "dangerouslySetInnerHTML"
                  ? ((f = f ? f.__html : void 0), f != null && GS(e, f))
                  : a === "children"
                  ? typeof f == "string"
                    ? (n !== "textarea" || f !== "") && fs(e, f)
                    : typeof f == "number" && fs(e, "" + f)
                  : a !== "suppressContentEditableWarning" &&
                    a !== "suppressHydrationWarning" &&
                    a !== "autoFocus" &&
                    (cs.hasOwnProperty(a)
                      ? f != null && a === "onScroll" && We("scroll", e)
                      : f != null && mg(e, a, f, s));
              }
            switch (n) {
              case "input":
                Xu(e), $_(e, r, !1);
                break;
              case "textarea":
                Xu(e), k_(e);
                break;
              case "option":
                r.value != null && e.setAttribute("value", "" + Bo(r.value));
                break;
              case "select":
                (e.multiple = !!r.multiple),
                  (a = r.value),
                  a != null
                    ? Ca(e, !!r.multiple, a, !1)
                    : r.defaultValue != null &&
                      Ca(e, !!r.multiple, r.defaultValue, !0);
                break;
              default:
                typeof o.onClick == "function" && (e.onclick = lf);
            }
            switch (n) {
              case "button":
              case "input":
              case "select":
              case "textarea":
                r = !!r.autoFocus;
                break e;
              case "img":
                r = !0;
                break e;
              default:
                r = !1;
            }
          }
          r && (t.flags |= 4);
        }
        t.ref !== null && ((t.flags |= 512), (t.flags |= 2097152));
      }
      return Tt(t), null;
    case 6:
      if (e && t.stateNode != null) _O(e, t, e.memoizedProps, r);
      else {
        if (typeof r != "string" && t.stateNode === null) throw Error(V(166));
        if (((n = bi(xs.current)), bi(Pr.current), rc(t))) {
          if (
            ((r = t.stateNode),
            (n = t.memoizedProps),
            (r[br] = t),
            (a = r.nodeValue !== n) && ((e = _n), e !== null))
          )
            switch (e.tag) {
              case 3:
                nc(r.nodeValue, n, (e.mode & 1) !== 0);
                break;
              case 5:
                e.memoizedProps.suppressHydrationWarning !== !0 &&
                  nc(r.nodeValue, n, (e.mode & 1) !== 0);
            }
          a && (t.flags |= 4);
        } else
          (r = (n.nodeType === 9 ? n : n.ownerDocument).createTextNode(r)),
            (r[br] = t),
            (t.stateNode = r);
      }
      return Tt(t), null;
    case 13:
      if (
        (Ue(Ke),
        (r = t.memoizedState),
        e === null ||
          (e.memoizedState !== null && e.memoizedState.dehydrated !== null))
      ) {
        if (Ge && gn !== null && t.mode & 1 && !(t.flags & 128))
          LP(), ja(), (t.flags |= 98560), (a = !1);
        else if (((a = rc(t)), r !== null && r.dehydrated !== null)) {
          if (e === null) {
            if (!a) throw Error(V(318));
            if (
              ((a = t.memoizedState),
              (a = a !== null ? a.dehydrated : null),
              !a)
            )
              throw Error(V(317));
            a[br] = t;
          } else
            ja(), !(t.flags & 128) && (t.memoizedState = null), (t.flags |= 4);
          Tt(t), (a = !1);
        } else or !== null && ($v(or), (or = null)), (a = !0);
        if (!a) return t.flags & 65536 ? t : null;
      }
      return t.flags & 128
        ? ((t.lanes = n), t)
        : ((r = r !== null),
          r !== (e !== null && e.memoizedState !== null) &&
            r &&
            ((t.child.flags |= 8192),
            t.mode & 1 &&
              (e === null || Ke.current & 1 ? mt === 0 && (mt = 3) : Qg())),
          t.updateQueue !== null && (t.flags |= 4),
          Tt(t),
          null);
    case 4:
      return (
        Wa(), _v(e, t), e === null && ys(t.stateNode.containerInfo), Tt(t), null
      );
    case 10:
      return Tg(t.type._context), Tt(t), null;
    case 17:
      return nn(t.type) && sf(), Tt(t), null;
    case 19:
      if ((Ue(Ke), (a = t.memoizedState), a === null)) return Tt(t), null;
      if (((r = (t.flags & 128) !== 0), (s = a.rendering), s === null))
        if (r) zl(a, !1);
        else {
          if (mt !== 0 || (e !== null && e.flags & 128))
            for (e = t.child; e !== null; ) {
              if (((s = hf(e)), s !== null)) {
                for (
                  t.flags |= 128,
                    zl(a, !1),
                    r = s.updateQueue,
                    r !== null && ((t.updateQueue = r), (t.flags |= 4)),
                    t.subtreeFlags = 0,
                    r = n,
                    n = t.child;
                  n !== null;

                )
                  (a = n),
                    (e = r),
                    (a.flags &= 14680066),
                    (s = a.alternate),
                    s === null
                      ? ((a.childLanes = 0),
                        (a.lanes = e),
                        (a.child = null),
                        (a.subtreeFlags = 0),
                        (a.memoizedProps = null),
                        (a.memoizedState = null),
                        (a.updateQueue = null),
                        (a.dependencies = null),
                        (a.stateNode = null))
                      : ((a.childLanes = s.childLanes),
                        (a.lanes = s.lanes),
                        (a.child = s.child),
                        (a.subtreeFlags = 0),
                        (a.deletions = null),
                        (a.memoizedProps = s.memoizedProps),
                        (a.memoizedState = s.memoizedState),
                        (a.updateQueue = s.updateQueue),
                        (a.type = s.type),
                        (e = s.dependencies),
                        (a.dependencies =
                          e === null
                            ? null
                            : {
                                lanes: e.lanes,
                                firstContext: e.firstContext,
                              })),
                    (n = n.sibling);
                return Me(Ke, (Ke.current & 1) | 2), t.child;
              }
              e = e.sibling;
            }
          a.tail !== null &&
            ot() > Ua &&
            ((t.flags |= 128), (r = !0), zl(a, !1), (t.lanes = 4194304));
        }
      else {
        if (!r)
          if (((e = hf(s)), e !== null)) {
            if (
              ((t.flags |= 128),
              (r = !0),
              (n = e.updateQueue),
              n !== null && ((t.updateQueue = n), (t.flags |= 4)),
              zl(a, !0),
              a.tail === null && a.tailMode === "hidden" && !s.alternate && !Ge)
            )
              return Tt(t), null;
          } else
            2 * ot() - a.renderingStartTime > Ua &&
              n !== 1073741824 &&
              ((t.flags |= 128), (r = !0), zl(a, !1), (t.lanes = 4194304));
        a.isBackwards
          ? ((s.sibling = t.child), (t.child = s))
          : ((n = a.last),
            n !== null ? (n.sibling = s) : (t.child = s),
            (a.last = s));
      }
      return a.tail !== null
        ? ((t = a.tail),
          (a.rendering = t),
          (a.tail = t.sibling),
          (a.renderingStartTime = ot()),
          (t.sibling = null),
          (n = Ke.current),
          Me(Ke, r ? (n & 1) | 2 : n & 1),
          t)
        : (Tt(t), null);
    case 22:
    case 23:
      return (
        Kg(),
        (r = t.memoizedState !== null),
        e !== null && (e.memoizedState !== null) !== r && (t.flags |= 8192),
        r && t.mode & 1
          ? vn & 1073741824 && (Tt(t), t.subtreeFlags & 6 && (t.flags |= 8192))
          : Tt(t),
        null
      );
    case 24:
      return null;
    case 25:
      return null;
  }
  throw Error(V(156, t.tag));
}
function Pz(e, t) {
  switch ((kg(t), t.tag)) {
    case 1:
      return (
        nn(t.type) && sf(),
        (e = t.flags),
        e & 65536 ? ((t.flags = (e & -65537) | 128), t) : null
      );
    case 3:
      return (
        Wa(),
        Ue(tn),
        Ue(Lt),
        Mg(),
        (e = t.flags),
        e & 65536 && !(e & 128) ? ((t.flags = (e & -65537) | 128), t) : null
      );
    case 5:
      return Dg(t), null;
    case 13:
      if (
        (Ue(Ke), (e = t.memoizedState), e !== null && e.dehydrated !== null)
      ) {
        if (t.alternate === null) throw Error(V(340));
        ja();
      }
      return (
        (e = t.flags), e & 65536 ? ((t.flags = (e & -65537) | 128), t) : null
      );
    case 19:
      return Ue(Ke), null;
    case 4:
      return Wa(), null;
    case 10:
      return Tg(t.type._context), null;
    case 22:
    case 23:
      return Kg(), null;
    case 24:
      return null;
    default:
      return null;
  }
}
var ac = !1,
  At = !1,
  Oz = typeof WeakSet == "function" ? WeakSet : Set,
  Q = null;
function Sa(e, t) {
  var n = e.ref;
  if (n !== null)
    if (typeof n == "function")
      try {
        n(null);
      } catch (r) {
        Je(e, t, r);
      }
    else n.current = null;
}
function wv(e, t, n) {
  try {
    n();
  } catch (r) {
    Je(e, t, r);
  }
}
var yw = !1;
function Ez(e, t) {
  if (((rv = rf), (e = SP()), $g(e))) {
    if ("selectionStart" in e)
      var n = { start: e.selectionStart, end: e.selectionEnd };
    else
      e: {
        n = ((n = e.ownerDocument) && n.defaultView) || window;
        var r = n.getSelection && n.getSelection();
        if (r && r.rangeCount !== 0) {
          n = r.anchorNode;
          var o = r.anchorOffset,
            a = r.focusNode;
          r = r.focusOffset;
          try {
            n.nodeType, a.nodeType;
          } catch {
            n = null;
            break e;
          }
          var s = 0,
            u = -1,
            f = -1,
            d = 0,
            m = 0,
            h = e,
            v = null;
          t: for (;;) {
            for (
              var b;
              h !== n || (o !== 0 && h.nodeType !== 3) || (u = s + o),
                h !== a || (r !== 0 && h.nodeType !== 3) || (f = s + r),
                h.nodeType === 3 && (s += h.nodeValue.length),
                (b = h.firstChild) !== null;

            )
              (v = h), (h = b);
            for (;;) {
              if (h === e) break t;
              if (
                (v === n && ++d === o && (u = s),
                v === a && ++m === r && (f = s),
                (b = h.nextSibling) !== null)
              )
                break;
              (h = v), (v = h.parentNode);
            }
            h = b;
          }
          n = u === -1 || f === -1 ? null : { start: u, end: f };
        } else n = null;
      }
    n = n || { start: 0, end: 0 };
  } else n = null;
  for (ov = { focusedElem: e, selectionRange: n }, rf = !1, Q = t; Q !== null; )
    if (((t = Q), (e = t.child), (t.subtreeFlags & 1028) !== 0 && e !== null))
      (e.return = t), (Q = e);
    else
      for (; Q !== null; ) {
        t = Q;
        try {
          var O = t.alternate;
          if (t.flags & 1024)
            switch (t.tag) {
              case 0:
              case 11:
              case 15:
                break;
              case 1:
                if (O !== null) {
                  var E = O.memoizedProps,
                    $ = O.memoizedState,
                    _ = t.stateNode,
                    w = _.getSnapshotBeforeUpdate(
                      t.elementType === t.type ? E : nr(t.type, E),
                      $
                    );
                  _.__reactInternalSnapshotBeforeUpdate = w;
                }
                break;
              case 3:
                var P = t.stateNode.containerInfo;
                P.nodeType === 1
                  ? (P.textContent = "")
                  : P.nodeType === 9 &&
                    P.documentElement &&
                    P.removeChild(P.documentElement);
                break;
              case 5:
              case 6:
              case 4:
              case 17:
                break;
              default:
                throw Error(V(163));
            }
        } catch (k) {
          Je(t, t.return, k);
        }
        if (((e = t.sibling), e !== null)) {
          (e.return = t.return), (Q = e);
          break;
        }
        Q = t.return;
      }
  return (O = yw), (yw = !1), O;
}
function ts(e, t, n) {
  var r = t.updateQueue;
  if (((r = r !== null ? r.lastEffect : null), r !== null)) {
    var o = (r = r.next);
    do {
      if ((o.tag & e) === e) {
        var a = o.destroy;
        (o.destroy = void 0), a !== void 0 && wv(t, n, a);
      }
      o = o.next;
    } while (o !== r);
  }
}
function Hd(e, t) {
  if (
    ((t = t.updateQueue), (t = t !== null ? t.lastEffect : null), t !== null)
  ) {
    var n = (t = t.next);
    do {
      if ((n.tag & e) === e) {
        var r = n.create;
        n.destroy = r();
      }
      n = n.next;
    } while (n !== t);
  }
}
function bv(e) {
  var t = e.ref;
  if (t !== null) {
    var n = e.stateNode;
    switch (e.tag) {
      case 5:
        e = n;
        break;
      default:
        e = n;
    }
    typeof t == "function" ? t(e) : (t.current = e);
  }
}
function wO(e) {
  var t = e.alternate;
  t !== null && ((e.alternate = null), wO(t)),
    (e.child = null),
    (e.deletions = null),
    (e.sibling = null),
    e.tag === 5 &&
      ((t = e.stateNode),
      t !== null &&
        (delete t[br], delete t[ws], delete t[lv], delete t[sz], delete t[uz])),
    (e.stateNode = null),
    (e.return = null),
    (e.dependencies = null),
    (e.memoizedProps = null),
    (e.memoizedState = null),
    (e.pendingProps = null),
    (e.stateNode = null),
    (e.updateQueue = null);
}
function bO(e) {
  return e.tag === 5 || e.tag === 3 || e.tag === 4;
}
function _w(e) {
  e: for (;;) {
    for (; e.sibling === null; ) {
      if (e.return === null || bO(e.return)) return null;
      e = e.return;
    }
    for (
      e.sibling.return = e.return, e = e.sibling;
      e.tag !== 5 && e.tag !== 6 && e.tag !== 18;

    ) {
      if (e.flags & 2 || e.child === null || e.tag === 4) continue e;
      (e.child.return = e), (e = e.child);
    }
    if (!(e.flags & 2)) return e.stateNode;
  }
}
function xv(e, t, n) {
  var r = e.tag;
  if (r === 5 || r === 6)
    (e = e.stateNode),
      t
        ? n.nodeType === 8
          ? n.parentNode.insertBefore(e, t)
          : n.insertBefore(e, t)
        : (n.nodeType === 8
            ? ((t = n.parentNode), t.insertBefore(e, n))
            : ((t = n), t.appendChild(e)),
          (n = n._reactRootContainer),
          n != null || t.onclick !== null || (t.onclick = lf));
  else if (r !== 4 && ((e = e.child), e !== null))
    for (xv(e, t, n), e = e.sibling; e !== null; ) xv(e, t, n), (e = e.sibling);
}
function Sv(e, t, n) {
  var r = e.tag;
  if (r === 5 || r === 6)
    (e = e.stateNode), t ? n.insertBefore(e, t) : n.appendChild(e);
  else if (r !== 4 && ((e = e.child), e !== null))
    for (Sv(e, t, n), e = e.sibling; e !== null; ) Sv(e, t, n), (e = e.sibling);
}
var St = null,
  rr = !1;
function wo(e, t, n) {
  for (n = n.child; n !== null; ) xO(e, t, n), (n = n.sibling);
}
function xO(e, t, n) {
  if (Sr && typeof Sr.onCommitFiberUnmount == "function")
    try {
      Sr.onCommitFiberUnmount(Ld, n);
    } catch {}
  switch (n.tag) {
    case 5:
      At || Sa(n, t);
    case 6:
      var r = St,
        o = rr;
      (St = null),
        wo(e, t, n),
        (St = r),
        (rr = o),
        St !== null &&
          (rr
            ? ((e = St),
              (n = n.stateNode),
              e.nodeType === 8 ? e.parentNode.removeChild(n) : e.removeChild(n))
            : St.removeChild(n.stateNode));
      break;
    case 18:
      St !== null &&
        (rr
          ? ((e = St),
            (n = n.stateNode),
            e.nodeType === 8
              ? rh(e.parentNode, n)
              : e.nodeType === 1 && rh(e, n),
            hs(e))
          : rh(St, n.stateNode));
      break;
    case 4:
      (r = St),
        (o = rr),
        (St = n.stateNode.containerInfo),
        (rr = !0),
        wo(e, t, n),
        (St = r),
        (rr = o);
      break;
    case 0:
    case 11:
    case 14:
    case 15:
      if (
        !At &&
        ((r = n.updateQueue), r !== null && ((r = r.lastEffect), r !== null))
      ) {
        o = r = r.next;
        do {
          var a = o,
            s = a.destroy;
          (a = a.tag),
            s !== void 0 && (a & 2 || a & 4) && wv(n, t, s),
            (o = o.next);
        } while (o !== r);
      }
      wo(e, t, n);
      break;
    case 1:
      if (
        !At &&
        (Sa(n, t),
        (r = n.stateNode),
        typeof r.componentWillUnmount == "function")
      )
        try {
          (r.props = n.memoizedProps),
            (r.state = n.memoizedState),
            r.componentWillUnmount();
        } catch (u) {
          Je(n, t, u);
        }
      wo(e, t, n);
      break;
    case 21:
      wo(e, t, n);
      break;
    case 22:
      n.mode & 1
        ? ((At = (r = At) || n.memoizedState !== null), wo(e, t, n), (At = r))
        : wo(e, t, n);
      break;
    default:
      wo(e, t, n);
  }
}
function ww(e) {
  var t = e.updateQueue;
  if (t !== null) {
    e.updateQueue = null;
    var n = e.stateNode;
    n === null && (n = e.stateNode = new Oz()),
      t.forEach(function (r) {
        var o = Az.bind(null, e, r);
        n.has(r) || (n.add(r), r.then(o, o));
      });
  }
}
function tr(e, t) {
  var n = t.deletions;
  if (n !== null)
    for (var r = 0; r < n.length; r++) {
      var o = n[r];
      try {
        var a = e,
          s = t,
          u = s;
        e: for (; u !== null; ) {
          switch (u.tag) {
            case 5:
              (St = u.stateNode), (rr = !1);
              break e;
            case 3:
              (St = u.stateNode.containerInfo), (rr = !0);
              break e;
            case 4:
              (St = u.stateNode.containerInfo), (rr = !0);
              break e;
          }
          u = u.return;
        }
        if (St === null) throw Error(V(160));
        xO(a, s, o), (St = null), (rr = !1);
        var f = o.alternate;
        f !== null && (f.return = null), (o.return = null);
      } catch (d) {
        Je(o, t, d);
      }
    }
  if (t.subtreeFlags & 12854)
    for (t = t.child; t !== null; ) SO(t, e), (t = t.sibling);
}
function SO(e, t) {
  var n = e.alternate,
    r = e.flags;
  switch (e.tag) {
    case 0:
    case 11:
    case 14:
    case 15:
      if ((tr(t, e), vr(e), r & 4)) {
        try {
          ts(3, e, e.return), Hd(3, e);
        } catch (E) {
          Je(e, e.return, E);
        }
        try {
          ts(5, e, e.return);
        } catch (E) {
          Je(e, e.return, E);
        }
      }
      break;
    case 1:
      tr(t, e), vr(e), r & 512 && n !== null && Sa(n, n.return);
      break;
    case 5:
      if (
        (tr(t, e),
        vr(e),
        r & 512 && n !== null && Sa(n, n.return),
        e.flags & 32)
      ) {
        var o = e.stateNode;
        try {
          fs(o, "");
        } catch (E) {
          Je(e, e.return, E);
        }
      }
      if (r & 4 && ((o = e.stateNode), o != null)) {
        var a = e.memoizedProps,
          s = n !== null ? n.memoizedProps : a,
          u = e.type,
          f = e.updateQueue;
        if (((e.updateQueue = null), f !== null))
          try {
            u === "input" && a.type === "radio" && a.name != null && HS(o, a),
              Gh(u, s);
            var d = Gh(u, a);
            for (s = 0; s < f.length; s += 2) {
              var m = f[s],
                h = f[s + 1];
              m === "style"
                ? KS(o, h)
                : m === "dangerouslySetInnerHTML"
                ? GS(o, h)
                : m === "children"
                ? fs(o, h)
                : mg(o, m, h, d);
            }
            switch (u) {
              case "input":
                Bh(o, a);
                break;
              case "textarea":
                VS(o, a);
                break;
              case "select":
                var v = o._wrapperState.wasMultiple;
                o._wrapperState.wasMultiple = !!a.multiple;
                var b = a.value;
                b != null
                  ? Ca(o, !!a.multiple, b, !1)
                  : v !== !!a.multiple &&
                    (a.defaultValue != null
                      ? Ca(o, !!a.multiple, a.defaultValue, !0)
                      : Ca(o, !!a.multiple, a.multiple ? [] : "", !1));
            }
            o[ws] = a;
          } catch (E) {
            Je(e, e.return, E);
          }
      }
      break;
    case 6:
      if ((tr(t, e), vr(e), r & 4)) {
        if (e.stateNode === null) throw Error(V(162));
        (o = e.stateNode), (a = e.memoizedProps);
        try {
          o.nodeValue = a;
        } catch (E) {
          Je(e, e.return, E);
        }
      }
      break;
    case 3:
      if (
        (tr(t, e), vr(e), r & 4 && n !== null && n.memoizedState.isDehydrated)
      )
        try {
          hs(t.containerInfo);
        } catch (E) {
          Je(e, e.return, E);
        }
      break;
    case 4:
      tr(t, e), vr(e);
      break;
    case 13:
      tr(t, e),
        vr(e),
        (o = e.child),
        o.flags & 8192 &&
          ((a = o.memoizedState !== null),
          (o.stateNode.isHidden = a),
          !a ||
            (o.alternate !== null && o.alternate.memoizedState !== null) ||
            (Gg = ot())),
        r & 4 && ww(e);
      break;
    case 22:
      if (
        ((m = n !== null && n.memoizedState !== null),
        e.mode & 1 ? ((At = (d = At) || m), tr(t, e), (At = d)) : tr(t, e),
        vr(e),
        r & 8192)
      ) {
        if (
          ((d = e.memoizedState !== null),
          (e.stateNode.isHidden = d) && !m && e.mode & 1)
        )
          for (Q = e, m = e.child; m !== null; ) {
            for (h = Q = m; Q !== null; ) {
              switch (((v = Q), (b = v.child), v.tag)) {
                case 0:
                case 11:
                case 14:
                case 15:
                  ts(4, v, v.return);
                  break;
                case 1:
                  Sa(v, v.return);
                  var O = v.stateNode;
                  if (typeof O.componentWillUnmount == "function") {
                    (r = v), (n = v.return);
                    try {
                      (t = r),
                        (O.props = t.memoizedProps),
                        (O.state = t.memoizedState),
                        O.componentWillUnmount();
                    } catch (E) {
                      Je(r, n, E);
                    }
                  }
                  break;
                case 5:
                  Sa(v, v.return);
                  break;
                case 22:
                  if (v.memoizedState !== null) {
                    xw(h);
                    continue;
                  }
              }
              b !== null ? ((b.return = v), (Q = b)) : xw(h);
            }
            m = m.sibling;
          }
        e: for (m = null, h = e; ; ) {
          if (h.tag === 5) {
            if (m === null) {
              m = h;
              try {
                (o = h.stateNode),
                  d
                    ? ((a = o.style),
                      typeof a.setProperty == "function"
                        ? a.setProperty("display", "none", "important")
                        : (a.display = "none"))
                    : ((u = h.stateNode),
                      (f = h.memoizedProps.style),
                      (s =
                        f != null && f.hasOwnProperty("display")
                          ? f.display
                          : null),
                      (u.style.display = XS("display", s)));
              } catch (E) {
                Je(e, e.return, E);
              }
            }
          } else if (h.tag === 6) {
            if (m === null)
              try {
                h.stateNode.nodeValue = d ? "" : h.memoizedProps;
              } catch (E) {
                Je(e, e.return, E);
              }
          } else if (
            ((h.tag !== 22 && h.tag !== 23) ||
              h.memoizedState === null ||
              h === e) &&
            h.child !== null
          ) {
            (h.child.return = h), (h = h.child);
            continue;
          }
          if (h === e) break e;
          for (; h.sibling === null; ) {
            if (h.return === null || h.return === e) break e;
            m === h && (m = null), (h = h.return);
          }
          m === h && (m = null), (h.sibling.return = h.return), (h = h.sibling);
        }
      }
      break;
    case 19:
      tr(t, e), vr(e), r & 4 && ww(e);
      break;
    case 21:
      break;
    default:
      tr(t, e), vr(e);
  }
}
function vr(e) {
  var t = e.flags;
  if (t & 2) {
    try {
      e: {
        for (var n = e.return; n !== null; ) {
          if (bO(n)) {
            var r = n;
            break e;
          }
          n = n.return;
        }
        throw Error(V(160));
      }
      switch (r.tag) {
        case 5:
          var o = r.stateNode;
          r.flags & 32 && (fs(o, ""), (r.flags &= -33));
          var a = _w(e);
          Sv(e, a, o);
          break;
        case 3:
        case 4:
          var s = r.stateNode.containerInfo,
            u = _w(e);
          xv(e, u, s);
          break;
        default:
          throw Error(V(161));
      }
    } catch (f) {
      Je(e, e.return, f);
    }
    e.flags &= -3;
  }
  t & 4096 && (e.flags &= -4097);
}
function $z(e, t, n) {
  (Q = e), PO(e);
}
function PO(e, t, n) {
  for (var r = (e.mode & 1) !== 0; Q !== null; ) {
    var o = Q,
      a = o.child;
    if (o.tag === 22 && r) {
      var s = o.memoizedState !== null || ac;
      if (!s) {
        var u = o.alternate,
          f = (u !== null && u.memoizedState !== null) || At;
        u = ac;
        var d = At;
        if (((ac = s), (At = f) && !d))
          for (Q = o; Q !== null; )
            (s = Q),
              (f = s.child),
              s.tag === 22 && s.memoizedState !== null
                ? Sw(o)
                : f !== null
                ? ((f.return = s), (Q = f))
                : Sw(o);
        for (; a !== null; ) (Q = a), PO(a), (a = a.sibling);
        (Q = o), (ac = u), (At = d);
      }
      bw(e);
    } else
      o.subtreeFlags & 8772 && a !== null ? ((a.return = o), (Q = a)) : bw(e);
  }
}
function bw(e) {
  for (; Q !== null; ) {
    var t = Q;
    if (t.flags & 8772) {
      var n = t.alternate;
      try {
        if (t.flags & 8772)
          switch (t.tag) {
            case 0:
            case 11:
            case 15:
              At || Hd(5, t);
              break;
            case 1:
              var r = t.stateNode;
              if (t.flags & 4 && !At)
                if (n === null) r.componentDidMount();
                else {
                  var o =
                    t.elementType === t.type
                      ? n.memoizedProps
                      : nr(t.type, n.memoizedProps);
                  r.componentDidUpdate(
                    o,
                    n.memoizedState,
                    r.__reactInternalSnapshotBeforeUpdate
                  );
                }
              var a = t.updateQueue;
              a !== null && ow(t, a, r);
              break;
            case 3:
              var s = t.updateQueue;
              if (s !== null) {
                if (((n = null), t.child !== null))
                  switch (t.child.tag) {
                    case 5:
                      n = t.child.stateNode;
                      break;
                    case 1:
                      n = t.child.stateNode;
                  }
                ow(t, s, n);
              }
              break;
            case 5:
              var u = t.stateNode;
              if (n === null && t.flags & 4) {
                n = u;
                var f = t.memoizedProps;
                switch (t.type) {
                  case "button":
                  case "input":
                  case "select":
                  case "textarea":
                    f.autoFocus && n.focus();
                    break;
                  case "img":
                    f.src && (n.src = f.src);
                }
              }
              break;
            case 6:
              break;
            case 4:
              break;
            case 12:
              break;
            case 13:
              if (t.memoizedState === null) {
                var d = t.alternate;
                if (d !== null) {
                  var m = d.memoizedState;
                  if (m !== null) {
                    var h = m.dehydrated;
                    h !== null && hs(h);
                  }
                }
              }
              break;
            case 19:
            case 17:
            case 21:
            case 22:
            case 23:
            case 25:
              break;
            default:
              throw Error(V(163));
          }
        At || (t.flags & 512 && bv(t));
      } catch (v) {
        Je(t, t.return, v);
      }
    }
    if (t === e) {
      Q = null;
      break;
    }
    if (((n = t.sibling), n !== null)) {
      (n.return = t.return), (Q = n);
      break;
    }
    Q = t.return;
  }
}
function xw(e) {
  for (; Q !== null; ) {
    var t = Q;
    if (t === e) {
      Q = null;
      break;
    }
    var n = t.sibling;
    if (n !== null) {
      (n.return = t.return), (Q = n);
      break;
    }
    Q = t.return;
  }
}
function Sw(e) {
  for (; Q !== null; ) {
    var t = Q;
    try {
      switch (t.tag) {
        case 0:
        case 11:
        case 15:
          var n = t.return;
          try {
            Hd(4, t);
          } catch (f) {
            Je(t, n, f);
          }
          break;
        case 1:
          var r = t.stateNode;
          if (typeof r.componentDidMount == "function") {
            var o = t.return;
            try {
              r.componentDidMount();
            } catch (f) {
              Je(t, o, f);
            }
          }
          var a = t.return;
          try {
            bv(t);
          } catch (f) {
            Je(t, a, f);
          }
          break;
        case 5:
          var s = t.return;
          try {
            bv(t);
          } catch (f) {
            Je(t, s, f);
          }
      }
    } catch (f) {
      Je(t, t.return, f);
    }
    if (t === e) {
      Q = null;
      break;
    }
    var u = t.sibling;
    if (u !== null) {
      (u.return = t.return), (Q = u);
      break;
    }
    Q = t.return;
  }
}
var Cz = Math.ceil,
  yf = ro.ReactCurrentDispatcher,
  Vg = ro.ReactCurrentOwner,
  Fn = ro.ReactCurrentBatchConfig,
  be = 0,
  _t = null,
  st = null,
  Ot = 0,
  vn = 0,
  Pa = Ko(0),
  mt = 0,
  Es = null,
  Ni = 0,
  Vd = 0,
  Yg = 0,
  ns = null,
  Jt = null,
  Gg = 0,
  Ua = 1 / 0,
  Ur = null,
  _f = !1,
  Pv = null,
  Do = null,
  lc = !1,
  ko = null,
  wf = 0,
  rs = 0,
  Ov = null,
  Wc = -1,
  Bc = 0;
function Ht() {
  return be & 6 ? ot() : Wc !== -1 ? Wc : (Wc = ot());
}
function Mo(e) {
  return e.mode & 1
    ? be & 2 && Ot !== 0
      ? Ot & -Ot
      : fz.transition !== null
      ? (Bc === 0 && (Bc = lP()), Bc)
      : ((e = Ne),
        e !== 0 || ((e = window.event), (e = e === void 0 ? 16 : mP(e.type))),
        e)
    : 1;
}
function lr(e, t, n, r) {
  if (50 < rs) throw ((rs = 0), (Ov = null), Error(V(185)));
  Fs(e, n, r),
    (!(be & 2) || e !== _t) &&
      (e === _t && (!(be & 2) && (Vd |= n), mt === 4 && $o(e, Ot)),
      rn(e, r),
      n === 1 && be === 0 && !(t.mode & 1) && ((Ua = ot() + 500), Wd && Qo()));
}
function rn(e, t) {
  var n = e.callbackNode;
  fT(e, t);
  var r = nf(e, e === _t ? Ot : 0);
  if (r === 0)
    n !== null && I_(n), (e.callbackNode = null), (e.callbackPriority = 0);
  else if (((t = r & -r), e.callbackPriority !== t)) {
    if ((n != null && I_(n), t === 1))
      e.tag === 0 ? cz(Pw.bind(null, e)) : TP(Pw.bind(null, e)),
        az(function () {
          !(be & 6) && Qo();
        }),
        (n = null);
    else {
      switch (sP(r)) {
        case 1:
          n = _g;
          break;
        case 4:
          n = iP;
          break;
        case 16:
          n = tf;
          break;
        case 536870912:
          n = aP;
          break;
        default:
          n = tf;
      }
      n = IO(n, OO.bind(null, e));
    }
    (e.callbackPriority = t), (e.callbackNode = n);
  }
}
function OO(e, t) {
  if (((Wc = -1), (Bc = 0), be & 6)) throw Error(V(327));
  var n = e.callbackNode;
  if (Ta() && e.callbackNode !== n) return null;
  var r = nf(e, e === _t ? Ot : 0);
  if (r === 0) return null;
  if (r & 30 || r & e.expiredLanes || t) t = bf(e, r);
  else {
    t = r;
    var o = be;
    be |= 2;
    var a = $O();
    (_t !== e || Ot !== t) && ((Ur = null), (Ua = ot() + 500), Oi(e, t));
    do
      try {
        Nz();
        break;
      } catch (u) {
        EO(e, u);
      }
    while (1);
    Ig(),
      (yf.current = a),
      (be = o),
      st !== null ? (t = 0) : ((_t = null), (Ot = 0), (t = mt));
  }
  if (t !== 0) {
    if (
      (t === 2 && ((o = Zh(e)), o !== 0 && ((r = o), (t = Ev(e, o)))), t === 1)
    )
      throw ((n = Es), Oi(e, 0), $o(e, r), rn(e, ot()), n);
    if (t === 6) $o(e, r);
    else {
      if (
        ((o = e.current.alternate),
        !(r & 30) &&
          !kz(o) &&
          ((t = bf(e, r)),
          t === 2 && ((a = Zh(e)), a !== 0 && ((r = a), (t = Ev(e, a)))),
          t === 1))
      )
        throw ((n = Es), Oi(e, 0), $o(e, r), rn(e, ot()), n);
      switch (((e.finishedWork = o), (e.finishedLanes = r), t)) {
        case 0:
        case 1:
          throw Error(V(345));
        case 2:
          mi(e, Jt, Ur);
          break;
        case 3:
          if (
            ($o(e, r), (r & 130023424) === r && ((t = Gg + 500 - ot()), 10 < t))
          ) {
            if (nf(e, 0) !== 0) break;
            if (((o = e.suspendedLanes), (o & r) !== r)) {
              Ht(), (e.pingedLanes |= e.suspendedLanes & o);
              break;
            }
            e.timeoutHandle = av(mi.bind(null, e, Jt, Ur), t);
            break;
          }
          mi(e, Jt, Ur);
          break;
        case 4:
          if (($o(e, r), (r & 4194240) === r)) break;
          for (t = e.eventTimes, o = -1; 0 < r; ) {
            var s = 31 - ar(r);
            (a = 1 << s), (s = t[s]), s > o && (o = s), (r &= ~a);
          }
          if (
            ((r = o),
            (r = ot() - r),
            (r =
              (120 > r
                ? 120
                : 480 > r
                ? 480
                : 1080 > r
                ? 1080
                : 1920 > r
                ? 1920
                : 3e3 > r
                ? 3e3
                : 4320 > r
                ? 4320
                : 1960 * Cz(r / 1960)) - r),
            10 < r)
          ) {
            e.timeoutHandle = av(mi.bind(null, e, Jt, Ur), r);
            break;
          }
          mi(e, Jt, Ur);
          break;
        case 5:
          mi(e, Jt, Ur);
          break;
        default:
          throw Error(V(329));
      }
    }
  }
  return rn(e, ot()), e.callbackNode === n ? OO.bind(null, e) : null;
}
function Ev(e, t) {
  var n = ns;
  return (
    e.current.memoizedState.isDehydrated && (Oi(e, t).flags |= 256),
    (e = bf(e, t)),
    e !== 2 && ((t = Jt), (Jt = n), t !== null && $v(t)),
    e
  );
}
function $v(e) {
  Jt === null ? (Jt = e) : Jt.push.apply(Jt, e);
}
function kz(e) {
  for (var t = e; ; ) {
    if (t.flags & 16384) {
      var n = t.updateQueue;
      if (n !== null && ((n = n.stores), n !== null))
        for (var r = 0; r < n.length; r++) {
          var o = n[r],
            a = o.getSnapshot;
          o = o.value;
          try {
            if (!cr(a(), o)) return !1;
          } catch {
            return !1;
          }
        }
    }
    if (((n = t.child), t.subtreeFlags & 16384 && n !== null))
      (n.return = t), (t = n);
    else {
      if (t === e) break;
      for (; t.sibling === null; ) {
        if (t.return === null || t.return === e) return !0;
        t = t.return;
      }
      (t.sibling.return = t.return), (t = t.sibling);
    }
  }
  return !0;
}
function $o(e, t) {
  for (
    t &= ~Yg,
      t &= ~Vd,
      e.suspendedLanes |= t,
      e.pingedLanes &= ~t,
      e = e.expirationTimes;
    0 < t;

  ) {
    var n = 31 - ar(t),
      r = 1 << n;
    (e[n] = -1), (t &= ~r);
  }
}
function Pw(e) {
  if (be & 6) throw Error(V(327));
  Ta();
  var t = nf(e, 0);
  if (!(t & 1)) return rn(e, ot()), null;
  var n = bf(e, t);
  if (e.tag !== 0 && n === 2) {
    var r = Zh(e);
    r !== 0 && ((t = r), (n = Ev(e, r)));
  }
  if (n === 1) throw ((n = Es), Oi(e, 0), $o(e, t), rn(e, ot()), n);
  if (n === 6) throw Error(V(345));
  return (
    (e.finishedWork = e.current.alternate),
    (e.finishedLanes = t),
    mi(e, Jt, Ur),
    rn(e, ot()),
    null
  );
}
function Xg(e, t) {
  var n = be;
  be |= 1;
  try {
    return e(t);
  } finally {
    (be = n), be === 0 && ((Ua = ot() + 500), Wd && Qo());
  }
}
function Ii(e) {
  ko !== null && ko.tag === 0 && !(be & 6) && Ta();
  var t = be;
  be |= 1;
  var n = Fn.transition,
    r = Ne;
  try {
    if (((Fn.transition = null), (Ne = 1), e)) return e();
  } finally {
    (Ne = r), (Fn.transition = n), (be = t), !(be & 6) && Qo();
  }
}
function Kg() {
  (vn = Pa.current), Ue(Pa);
}
function Oi(e, t) {
  (e.finishedWork = null), (e.finishedLanes = 0);
  var n = e.timeoutHandle;
  if ((n !== -1 && ((e.timeoutHandle = -1), iz(n)), st !== null))
    for (n = st.return; n !== null; ) {
      var r = n;
      switch ((kg(r), r.tag)) {
        case 1:
          (r = r.type.childContextTypes), r != null && sf();
          break;
        case 3:
          Wa(), Ue(tn), Ue(Lt), Mg();
          break;
        case 5:
          Dg(r);
          break;
        case 4:
          Wa();
          break;
        case 13:
          Ue(Ke);
          break;
        case 19:
          Ue(Ke);
          break;
        case 10:
          Tg(r.type._context);
          break;
        case 22:
        case 23:
          Kg();
      }
      n = n.return;
    }
  if (
    ((_t = e),
    (st = e = jo(e.current, null)),
    (Ot = vn = t),
    (mt = 0),
    (Es = null),
    (Yg = Vd = Ni = 0),
    (Jt = ns = null),
    wi !== null)
  ) {
    for (t = 0; t < wi.length; t++)
      if (((n = wi[t]), (r = n.interleaved), r !== null)) {
        n.interleaved = null;
        var o = r.next,
          a = n.pending;
        if (a !== null) {
          var s = a.next;
          (a.next = o), (r.next = s);
        }
        n.pending = r;
      }
    wi = null;
  }
  return e;
}
function EO(e, t) {
  do {
    var n = st;
    try {
      if ((Ig(), (Mc.current = gf), vf)) {
        for (var r = Qe.memoizedState; r !== null; ) {
          var o = r.queue;
          o !== null && (o.pending = null), (r = r.next);
        }
        vf = !1;
      }
      if (
        ((Ri = 0),
        (yt = pt = Qe = null),
        (es = !1),
        (Ss = 0),
        (Vg.current = null),
        n === null || n.return === null)
      ) {
        (mt = 1), (Es = t), (st = null);
        break;
      }
      e: {
        var a = e,
          s = n.return,
          u = n,
          f = t;
        if (
          ((t = Ot),
          (u.flags |= 32768),
          f !== null && typeof f == "object" && typeof f.then == "function")
        ) {
          var d = f,
            m = u,
            h = m.tag;
          if (!(m.mode & 1) && (h === 0 || h === 11 || h === 15)) {
            var v = m.alternate;
            v
              ? ((m.updateQueue = v.updateQueue),
                (m.memoizedState = v.memoizedState),
                (m.lanes = v.lanes))
              : ((m.updateQueue = null), (m.memoizedState = null));
          }
          var b = fw(s);
          if (b !== null) {
            (b.flags &= -257),
              dw(b, s, u, a, t),
              b.mode & 1 && cw(a, d, t),
              (t = b),
              (f = d);
            var O = t.updateQueue;
            if (O === null) {
              var E = new Set();
              E.add(f), (t.updateQueue = E);
            } else O.add(f);
            break e;
          } else {
            if (!(t & 1)) {
              cw(a, d, t), Qg();
              break e;
            }
            f = Error(V(426));
          }
        } else if (Ge && u.mode & 1) {
          var $ = fw(s);
          if ($ !== null) {
            !($.flags & 65536) && ($.flags |= 256),
              dw($, s, u, a, t),
              Rg(Ba(f, u));
            break e;
          }
        }
        (a = f = Ba(f, u)),
          mt !== 4 && (mt = 2),
          ns === null ? (ns = [a]) : ns.push(a),
          (a = s);
        do {
          switch (a.tag) {
            case 3:
              (a.flags |= 65536), (t &= -t), (a.lanes |= t);
              var _ = uO(a, f, t);
              rw(a, _);
              break e;
            case 1:
              u = f;
              var w = a.type,
                P = a.stateNode;
              if (
                !(a.flags & 128) &&
                (typeof w.getDerivedStateFromError == "function" ||
                  (P !== null &&
                    typeof P.componentDidCatch == "function" &&
                    (Do === null || !Do.has(P))))
              ) {
                (a.flags |= 65536), (t &= -t), (a.lanes |= t);
                var k = cO(a, u, t);
                rw(a, k);
                break e;
              }
          }
          a = a.return;
        } while (a !== null);
      }
      kO(n);
    } catch (I) {
      (t = I), st === n && n !== null && (st = n = n.return);
      continue;
    }
    break;
  } while (1);
}
function $O() {
  var e = yf.current;
  return (yf.current = gf), e === null ? gf : e;
}
function Qg() {
  (mt === 0 || mt === 3 || mt === 2) && (mt = 4),
    _t === null || (!(Ni & 268435455) && !(Vd & 268435455)) || $o(_t, Ot);
}
function bf(e, t) {
  var n = be;
  be |= 2;
  var r = $O();
  (_t !== e || Ot !== t) && ((Ur = null), Oi(e, t));
  do
    try {
      Rz();
      break;
    } catch (o) {
      EO(e, o);
    }
  while (1);
  if ((Ig(), (be = n), (yf.current = r), st !== null)) throw Error(V(261));
  return (_t = null), (Ot = 0), mt;
}
function Rz() {
  for (; st !== null; ) CO(st);
}
function Nz() {
  for (; st !== null && !nT(); ) CO(st);
}
function CO(e) {
  var t = NO(e.alternate, e, vn);
  (e.memoizedProps = e.pendingProps),
    t === null ? kO(e) : (st = t),
    (Vg.current = null);
}
function kO(e) {
  var t = e;
  do {
    var n = t.alternate;
    if (((e = t.return), t.flags & 32768)) {
      if (((n = Pz(n, t)), n !== null)) {
        (n.flags &= 32767), (st = n);
        return;
      }
      if (e !== null)
        (e.flags |= 32768), (e.subtreeFlags = 0), (e.deletions = null);
      else {
        (mt = 6), (st = null);
        return;
      }
    } else if (((n = Sz(n, t, vn)), n !== null)) {
      st = n;
      return;
    }
    if (((t = t.sibling), t !== null)) {
      st = t;
      return;
    }
    st = t = e;
  } while (t !== null);
  mt === 0 && (mt = 5);
}
function mi(e, t, n) {
  var r = Ne,
    o = Fn.transition;
  try {
    (Fn.transition = null), (Ne = 1), Iz(e, t, n, r);
  } finally {
    (Fn.transition = o), (Ne = r);
  }
  return null;
}
function Iz(e, t, n, r) {
  do Ta();
  while (ko !== null);
  if (be & 6) throw Error(V(327));
  n = e.finishedWork;
  var o = e.finishedLanes;
  if (n === null) return null;
  if (((e.finishedWork = null), (e.finishedLanes = 0), n === e.current))
    throw Error(V(177));
  (e.callbackNode = null), (e.callbackPriority = 0);
  var a = n.lanes | n.childLanes;
  if (
    (dT(e, a),
    e === _t && ((st = _t = null), (Ot = 0)),
    (!(n.subtreeFlags & 2064) && !(n.flags & 2064)) ||
      lc ||
      ((lc = !0),
      IO(tf, function () {
        return Ta(), null;
      })),
    (a = (n.flags & 15990) !== 0),
    n.subtreeFlags & 15990 || a)
  ) {
    (a = Fn.transition), (Fn.transition = null);
    var s = Ne;
    Ne = 1;
    var u = be;
    (be |= 4),
      (Vg.current = null),
      Ez(e, n),
      SO(n, e),
      ZT(ov),
      (rf = !!rv),
      (ov = rv = null),
      (e.current = n),
      $z(n),
      rT(),
      (be = u),
      (Ne = s),
      (Fn.transition = a);
  } else e.current = n;
  if (
    (lc && ((lc = !1), (ko = e), (wf = o)),
    (a = e.pendingLanes),
    a === 0 && (Do = null),
    aT(n.stateNode),
    rn(e, ot()),
    t !== null)
  )
    for (r = e.onRecoverableError, n = 0; n < t.length; n++)
      (o = t[n]), r(o.value, { componentStack: o.stack, digest: o.digest });
  if (_f) throw ((_f = !1), (e = Pv), (Pv = null), e);
  return (
    wf & 1 && e.tag !== 0 && Ta(),
    (a = e.pendingLanes),
    a & 1 ? (e === Ov ? rs++ : ((rs = 0), (Ov = e))) : (rs = 0),
    Qo(),
    null
  );
}
function Ta() {
  if (ko !== null) {
    var e = sP(wf),
      t = Fn.transition,
      n = Ne;
    try {
      if (((Fn.transition = null), (Ne = 16 > e ? 16 : e), ko === null))
        var r = !1;
      else {
        if (((e = ko), (ko = null), (wf = 0), be & 6)) throw Error(V(331));
        var o = be;
        for (be |= 4, Q = e.current; Q !== null; ) {
          var a = Q,
            s = a.child;
          if (Q.flags & 16) {
            var u = a.deletions;
            if (u !== null) {
              for (var f = 0; f < u.length; f++) {
                var d = u[f];
                for (Q = d; Q !== null; ) {
                  var m = Q;
                  switch (m.tag) {
                    case 0:
                    case 11:
                    case 15:
                      ts(8, m, a);
                  }
                  var h = m.child;
                  if (h !== null) (h.return = m), (Q = h);
                  else
                    for (; Q !== null; ) {
                      m = Q;
                      var v = m.sibling,
                        b = m.return;
                      if ((wO(m), m === d)) {
                        Q = null;
                        break;
                      }
                      if (v !== null) {
                        (v.return = b), (Q = v);
                        break;
                      }
                      Q = b;
                    }
                }
              }
              var O = a.alternate;
              if (O !== null) {
                var E = O.child;
                if (E !== null) {
                  O.child = null;
                  do {
                    var $ = E.sibling;
                    (E.sibling = null), (E = $);
                  } while (E !== null);
                }
              }
              Q = a;
            }
          }
          if (a.subtreeFlags & 2064 && s !== null) (s.return = a), (Q = s);
          else
            e: for (; Q !== null; ) {
              if (((a = Q), a.flags & 2048))
                switch (a.tag) {
                  case 0:
                  case 11:
                  case 15:
                    ts(9, a, a.return);
                }
              var _ = a.sibling;
              if (_ !== null) {
                (_.return = a.return), (Q = _);
                break e;
              }
              Q = a.return;
            }
        }
        var w = e.current;
        for (Q = w; Q !== null; ) {
          s = Q;
          var P = s.child;
          if (s.subtreeFlags & 2064 && P !== null) (P.return = s), (Q = P);
          else
            e: for (s = w; Q !== null; ) {
              if (((u = Q), u.flags & 2048))
                try {
                  switch (u.tag) {
                    case 0:
                    case 11:
                    case 15:
                      Hd(9, u);
                  }
                } catch (I) {
                  Je(u, u.return, I);
                }
              if (u === s) {
                Q = null;
                break e;
              }
              var k = u.sibling;
              if (k !== null) {
                (k.return = u.return), (Q = k);
                break e;
              }
              Q = u.return;
            }
        }
        if (
          ((be = o), Qo(), Sr && typeof Sr.onPostCommitFiberRoot == "function")
        )
          try {
            Sr.onPostCommitFiberRoot(Ld, e);
          } catch {}
        r = !0;
      }
      return r;
    } finally {
      (Ne = n), (Fn.transition = t);
    }
  }
  return !1;
}
function Ow(e, t, n) {
  (t = Ba(n, t)),
    (t = uO(e, t, 1)),
    (e = Lo(e, t, 1)),
    (t = Ht()),
    e !== null && (Fs(e, 1, t), rn(e, t));
}
function Je(e, t, n) {
  if (e.tag === 3) Ow(e, e, n);
  else
    for (; t !== null; ) {
      if (t.tag === 3) {
        Ow(t, e, n);
        break;
      } else if (t.tag === 1) {
        var r = t.stateNode;
        if (
          typeof t.type.getDerivedStateFromError == "function" ||
          (typeof r.componentDidCatch == "function" &&
            (Do === null || !Do.has(r)))
        ) {
          (e = Ba(n, e)),
            (e = cO(t, e, 1)),
            (t = Lo(t, e, 1)),
            (e = Ht()),
            t !== null && (Fs(t, 1, e), rn(t, e));
          break;
        }
      }
      t = t.return;
    }
}
function Tz(e, t, n) {
  var r = e.pingCache;
  r !== null && r.delete(t),
    (t = Ht()),
    (e.pingedLanes |= e.suspendedLanes & n),
    _t === e &&
      (Ot & n) === n &&
      (mt === 4 || (mt === 3 && (Ot & 130023424) === Ot && 500 > ot() - Gg)
        ? Oi(e, 0)
        : (Yg |= n)),
    rn(e, t);
}
function RO(e, t) {
  t === 0 &&
    (e.mode & 1
      ? ((t = qu), (qu <<= 1), !(qu & 130023424) && (qu = 4194304))
      : (t = 1));
  var n = Ht();
  (e = Qr(e, t)), e !== null && (Fs(e, t, n), rn(e, n));
}
function zz(e) {
  var t = e.memoizedState,
    n = 0;
  t !== null && (n = t.retryLane), RO(e, n);
}
function Az(e, t) {
  var n = 0;
  switch (e.tag) {
    case 13:
      var r = e.stateNode,
        o = e.memoizedState;
      o !== null && (n = o.retryLane);
      break;
    case 19:
      r = e.stateNode;
      break;
    default:
      throw Error(V(314));
  }
  r !== null && r.delete(t), RO(e, n);
}
var NO;
NO = function (e, t, n) {
  if (e !== null)
    if (e.memoizedProps !== t.pendingProps || tn.current) en = !0;
    else {
      if (!(e.lanes & n) && !(t.flags & 128)) return (en = !1), xz(e, t, n);
      en = !!(e.flags & 131072);
    }
  else (en = !1), Ge && t.flags & 1048576 && zP(t, ff, t.index);
  switch (((t.lanes = 0), t.tag)) {
    case 2:
      var r = t.type;
      Fc(e, t), (e = t.pendingProps);
      var o = Ma(t, Lt.current);
      Ia(t, n), (o = Fg(null, t, r, e, o, n));
      var a = Wg();
      return (
        (t.flags |= 1),
        typeof o == "object" &&
        o !== null &&
        typeof o.render == "function" &&
        o.$$typeof === void 0
          ? ((t.tag = 1),
            (t.memoizedState = null),
            (t.updateQueue = null),
            nn(r) ? ((a = !0), uf(t)) : (a = !1),
            (t.memoizedState =
              o.state !== null && o.state !== void 0 ? o.state : null),
            Ag(t),
            (o.updater = Bd),
            (t.stateNode = o),
            (o._reactInternals = t),
            pv(t, r, e, n),
            (t = vv(null, t, r, !0, a, n)))
          : ((t.tag = 0), Ge && a && Cg(t), Ut(null, t, o, n), (t = t.child)),
        t
      );
    case 16:
      r = t.elementType;
      e: {
        switch (
          (Fc(e, t),
          (e = t.pendingProps),
          (o = r._init),
          (r = o(r._payload)),
          (t.type = r),
          (o = t.tag = Dz(r)),
          (e = nr(r, e)),
          o)
        ) {
          case 0:
            t = hv(null, t, r, e, n);
            break e;
          case 1:
            t = hw(null, t, r, e, n);
            break e;
          case 11:
            t = pw(null, t, r, e, n);
            break e;
          case 14:
            t = mw(null, t, r, nr(r.type, e), n);
            break e;
        }
        throw Error(V(306, r, ""));
      }
      return t;
    case 0:
      return (
        (r = t.type),
        (o = t.pendingProps),
        (o = t.elementType === r ? o : nr(r, o)),
        hv(e, t, r, o, n)
      );
    case 1:
      return (
        (r = t.type),
        (o = t.pendingProps),
        (o = t.elementType === r ? o : nr(r, o)),
        hw(e, t, r, o, n)
      );
    case 3:
      e: {
        if ((mO(t), e === null)) throw Error(V(387));
        (r = t.pendingProps),
          (a = t.memoizedState),
          (o = a.element),
          MP(e, t),
          mf(t, r, null, n);
        var s = t.memoizedState;
        if (((r = s.element), a.isDehydrated))
          if (
            ((a = {
              element: r,
              isDehydrated: !1,
              cache: s.cache,
              pendingSuspenseBoundaries: s.pendingSuspenseBoundaries,
              transitions: s.transitions,
            }),
            (t.updateQueue.baseState = a),
            (t.memoizedState = a),
            t.flags & 256)
          ) {
            (o = Ba(Error(V(423)), t)), (t = vw(e, t, r, n, o));
            break e;
          } else if (r !== o) {
            (o = Ba(Error(V(424)), t)), (t = vw(e, t, r, n, o));
            break e;
          } else
            for (
              gn = Ao(t.stateNode.containerInfo.firstChild),
                _n = t,
                Ge = !0,
                or = null,
                n = BP(t, null, r, n),
                t.child = n;
              n;

            )
              (n.flags = (n.flags & -3) | 4096), (n = n.sibling);
        else {
          if ((ja(), r === o)) {
            t = qr(e, t, n);
            break e;
          }
          Ut(e, t, r, n);
        }
        t = t.child;
      }
      return t;
    case 5:
      return (
        UP(t),
        e === null && cv(t),
        (r = t.type),
        (o = t.pendingProps),
        (a = e !== null ? e.memoizedProps : null),
        (s = o.children),
        iv(r, o) ? (s = null) : a !== null && iv(r, a) && (t.flags |= 32),
        pO(e, t),
        Ut(e, t, s, n),
        t.child
      );
    case 6:
      return e === null && cv(t), null;
    case 13:
      return hO(e, t, n);
    case 4:
      return (
        Lg(t, t.stateNode.containerInfo),
        (r = t.pendingProps),
        e === null ? (t.child = Fa(t, null, r, n)) : Ut(e, t, r, n),
        t.child
      );
    case 11:
      return (
        (r = t.type),
        (o = t.pendingProps),
        (o = t.elementType === r ? o : nr(r, o)),
        pw(e, t, r, o, n)
      );
    case 7:
      return Ut(e, t, t.pendingProps, n), t.child;
    case 8:
      return Ut(e, t, t.pendingProps.children, n), t.child;
    case 12:
      return Ut(e, t, t.pendingProps.children, n), t.child;
    case 10:
      e: {
        if (
          ((r = t.type._context),
          (o = t.pendingProps),
          (a = t.memoizedProps),
          (s = o.value),
          Me(df, r._currentValue),
          (r._currentValue = s),
          a !== null)
        )
          if (cr(a.value, s)) {
            if (a.children === o.children && !tn.current) {
              t = qr(e, t, n);
              break e;
            }
          } else
            for (a = t.child, a !== null && (a.return = t); a !== null; ) {
              var u = a.dependencies;
              if (u !== null) {
                s = a.child;
                for (var f = u.firstContext; f !== null; ) {
                  if (f.context === r) {
                    if (a.tag === 1) {
                      (f = Gr(-1, n & -n)), (f.tag = 2);
                      var d = a.updateQueue;
                      if (d !== null) {
                        d = d.shared;
                        var m = d.pending;
                        m === null
                          ? (f.next = f)
                          : ((f.next = m.next), (m.next = f)),
                          (d.pending = f);
                      }
                    }
                    (a.lanes |= n),
                      (f = a.alternate),
                      f !== null && (f.lanes |= n),
                      fv(a.return, n, t),
                      (u.lanes |= n);
                    break;
                  }
                  f = f.next;
                }
              } else if (a.tag === 10) s = a.type === t.type ? null : a.child;
              else if (a.tag === 18) {
                if (((s = a.return), s === null)) throw Error(V(341));
                (s.lanes |= n),
                  (u = s.alternate),
                  u !== null && (u.lanes |= n),
                  fv(s, n, t),
                  (s = a.sibling);
              } else s = a.child;
              if (s !== null) s.return = a;
              else
                for (s = a; s !== null; ) {
                  if (s === t) {
                    s = null;
                    break;
                  }
                  if (((a = s.sibling), a !== null)) {
                    (a.return = s.return), (s = a);
                    break;
                  }
                  s = s.return;
                }
              a = s;
            }
        Ut(e, t, o.children, n), (t = t.child);
      }
      return t;
    case 9:
      return (
        (o = t.type),
        (r = t.pendingProps.children),
        Ia(t, n),
        (o = Bn(o)),
        (r = r(o)),
        (t.flags |= 1),
        Ut(e, t, r, n),
        t.child
      );
    case 14:
      return (
        (r = t.type),
        (o = nr(r, t.pendingProps)),
        (o = nr(r.type, o)),
        mw(e, t, r, o, n)
      );
    case 15:
      return fO(e, t, t.type, t.pendingProps, n);
    case 17:
      return (
        (r = t.type),
        (o = t.pendingProps),
        (o = t.elementType === r ? o : nr(r, o)),
        Fc(e, t),
        (t.tag = 1),
        nn(r) ? ((e = !0), uf(t)) : (e = !1),
        Ia(t, n),
        FP(t, r, o),
        pv(t, r, o, n),
        vv(null, t, r, !0, e, n)
      );
    case 19:
      return vO(e, t, n);
    case 22:
      return dO(e, t, n);
  }
  throw Error(V(156, t.tag));
};
function IO(e, t) {
  return oP(e, t);
}
function Lz(e, t, n, r) {
  (this.tag = e),
    (this.key = n),
    (this.sibling =
      this.child =
      this.return =
      this.stateNode =
      this.type =
      this.elementType =
        null),
    (this.index = 0),
    (this.ref = null),
    (this.pendingProps = t),
    (this.dependencies =
      this.memoizedState =
      this.updateQueue =
      this.memoizedProps =
        null),
    (this.mode = r),
    (this.subtreeFlags = this.flags = 0),
    (this.deletions = null),
    (this.childLanes = this.lanes = 0),
    (this.alternate = null);
}
function jn(e, t, n, r) {
  return new Lz(e, t, n, r);
}
function qg(e) {
  return (e = e.prototype), !(!e || !e.isReactComponent);
}
function Dz(e) {
  if (typeof e == "function") return qg(e) ? 1 : 0;
  if (e != null) {
    if (((e = e.$$typeof), e === vg)) return 11;
    if (e === gg) return 14;
  }
  return 2;
}
function jo(e, t) {
  var n = e.alternate;
  return (
    n === null
      ? ((n = jn(e.tag, t, e.key, e.mode)),
        (n.elementType = e.elementType),
        (n.type = e.type),
        (n.stateNode = e.stateNode),
        (n.alternate = e),
        (e.alternate = n))
      : ((n.pendingProps = t),
        (n.type = e.type),
        (n.flags = 0),
        (n.subtreeFlags = 0),
        (n.deletions = null)),
    (n.flags = e.flags & 14680064),
    (n.childLanes = e.childLanes),
    (n.lanes = e.lanes),
    (n.child = e.child),
    (n.memoizedProps = e.memoizedProps),
    (n.memoizedState = e.memoizedState),
    (n.updateQueue = e.updateQueue),
    (t = e.dependencies),
    (n.dependencies =
      t === null ? null : { lanes: t.lanes, firstContext: t.firstContext }),
    (n.sibling = e.sibling),
    (n.index = e.index),
    (n.ref = e.ref),
    n
  );
}
function Uc(e, t, n, r, o, a) {
  var s = 2;
  if (((r = e), typeof e == "function")) qg(e) && (s = 1);
  else if (typeof e == "string") s = 5;
  else
    e: switch (e) {
      case ma:
        return Ei(n.children, o, a, t);
      case hg:
        (s = 8), (o |= 8);
        break;
      case Dh:
        return (
          (e = jn(12, n, t, o | 2)), (e.elementType = Dh), (e.lanes = a), e
        );
      case Mh:
        return (e = jn(13, n, t, o)), (e.elementType = Mh), (e.lanes = a), e;
      case jh:
        return (e = jn(19, n, t, o)), (e.elementType = jh), (e.lanes = a), e;
      case WS:
        return Yd(n, o, a, t);
      default:
        if (typeof e == "object" && e !== null)
          switch (e.$$typeof) {
            case jS:
              s = 10;
              break e;
            case FS:
              s = 9;
              break e;
            case vg:
              s = 11;
              break e;
            case gg:
              s = 14;
              break e;
            case Po:
              (s = 16), (r = null);
              break e;
          }
        throw Error(V(130, e == null ? e : typeof e, ""));
    }
  return (
    (t = jn(s, n, t, o)), (t.elementType = e), (t.type = r), (t.lanes = a), t
  );
}
function Ei(e, t, n, r) {
  return (e = jn(7, e, r, t)), (e.lanes = n), e;
}
function Yd(e, t, n, r) {
  return (
    (e = jn(22, e, r, t)),
    (e.elementType = WS),
    (e.lanes = n),
    (e.stateNode = { isHidden: !1 }),
    e
  );
}
function fh(e, t, n) {
  return (e = jn(6, e, null, t)), (e.lanes = n), e;
}
function dh(e, t, n) {
  return (
    (t = jn(4, e.children !== null ? e.children : [], e.key, t)),
    (t.lanes = n),
    (t.stateNode = {
      containerInfo: e.containerInfo,
      pendingChildren: null,
      implementation: e.implementation,
    }),
    t
  );
}
function Mz(e, t, n, r, o) {
  (this.tag = t),
    (this.containerInfo = e),
    (this.finishedWork =
      this.pingCache =
      this.current =
      this.pendingChildren =
        null),
    (this.timeoutHandle = -1),
    (this.callbackNode = this.pendingContext = this.context = null),
    (this.callbackPriority = 0),
    (this.eventTimes = Ym(0)),
    (this.expirationTimes = Ym(-1)),
    (this.entangledLanes =
      this.finishedLanes =
      this.mutableReadLanes =
      this.expiredLanes =
      this.pingedLanes =
      this.suspendedLanes =
      this.pendingLanes =
        0),
    (this.entanglements = Ym(0)),
    (this.identifierPrefix = r),
    (this.onRecoverableError = o),
    (this.mutableSourceEagerHydrationData = null);
}
function Zg(e, t, n, r, o, a, s, u, f) {
  return (
    (e = new Mz(e, t, n, u, f)),
    t === 1 ? ((t = 1), a === !0 && (t |= 8)) : (t = 0),
    (a = jn(3, null, null, t)),
    (e.current = a),
    (a.stateNode = e),
    (a.memoizedState = {
      element: r,
      isDehydrated: n,
      cache: null,
      transitions: null,
      pendingSuspenseBoundaries: null,
    }),
    Ag(a),
    e
  );
}
function jz(e, t, n) {
  var r = 3 < arguments.length && arguments[3] !== void 0 ? arguments[3] : null;
  return {
    $$typeof: pa,
    key: r == null ? null : "" + r,
    children: e,
    containerInfo: t,
    implementation: n,
  };
}
function TO(e) {
  if (!e) return Uo;
  e = e._reactInternals;
  e: {
    if (Di(e) !== e || e.tag !== 1) throw Error(V(170));
    var t = e;
    do {
      switch (t.tag) {
        case 3:
          t = t.stateNode.context;
          break e;
        case 1:
          if (nn(t.type)) {
            t = t.stateNode.__reactInternalMemoizedMergedChildContext;
            break e;
          }
      }
      t = t.return;
    } while (t !== null);
    throw Error(V(171));
  }
  if (e.tag === 1) {
    var n = e.type;
    if (nn(n)) return IP(e, n, t);
  }
  return t;
}
function zO(e, t, n, r, o, a, s, u, f) {
  return (
    (e = Zg(n, r, !0, e, o, a, s, u, f)),
    (e.context = TO(null)),
    (n = e.current),
    (r = Ht()),
    (o = Mo(n)),
    (a = Gr(r, o)),
    (a.callback = t ?? null),
    Lo(n, a, o),
    (e.current.lanes = o),
    Fs(e, o, r),
    rn(e, r),
    e
  );
}
function Gd(e, t, n, r) {
  var o = t.current,
    a = Ht(),
    s = Mo(o);
  return (
    (n = TO(n)),
    t.context === null ? (t.context = n) : (t.pendingContext = n),
    (t = Gr(a, s)),
    (t.payload = { element: e }),
    (r = r === void 0 ? null : r),
    r !== null && (t.callback = r),
    (e = Lo(o, t, s)),
    e !== null && (lr(e, o, s, a), Dc(e, o, s)),
    s
  );
}
function xf(e) {
  if (((e = e.current), !e.child)) return null;
  switch (e.child.tag) {
    case 5:
      return e.child.stateNode;
    default:
      return e.child.stateNode;
  }
}
function Ew(e, t) {
  if (((e = e.memoizedState), e !== null && e.dehydrated !== null)) {
    var n = e.retryLane;
    e.retryLane = n !== 0 && n < t ? n : t;
  }
}
function Jg(e, t) {
  Ew(e, t), (e = e.alternate) && Ew(e, t);
}
function Fz() {
  return null;
}
var AO =
  typeof reportError == "function"
    ? reportError
    : function (e) {
        console.error(e);
      };
function e0(e) {
  this._internalRoot = e;
}
Xd.prototype.render = e0.prototype.render = function (e) {
  var t = this._internalRoot;
  if (t === null) throw Error(V(409));
  Gd(e, t, null, null);
};
Xd.prototype.unmount = e0.prototype.unmount = function () {
  var e = this._internalRoot;
  if (e !== null) {
    this._internalRoot = null;
    var t = e.containerInfo;
    Ii(function () {
      Gd(null, e, null, null);
    }),
      (t[Kr] = null);
  }
};
function Xd(e) {
  this._internalRoot = e;
}
Xd.prototype.unstable_scheduleHydration = function (e) {
  if (e) {
    var t = fP();
    e = { blockedOn: null, target: e, priority: t };
    for (var n = 0; n < Eo.length && t !== 0 && t < Eo[n].priority; n++);
    Eo.splice(n, 0, e), n === 0 && pP(e);
  }
};
function t0(e) {
  return !(!e || (e.nodeType !== 1 && e.nodeType !== 9 && e.nodeType !== 11));
}
function Kd(e) {
  return !(
    !e ||
    (e.nodeType !== 1 &&
      e.nodeType !== 9 &&
      e.nodeType !== 11 &&
      (e.nodeType !== 8 || e.nodeValue !== " react-mount-point-unstable "))
  );
}
function $w() {}
function Wz(e, t, n, r, o) {
  if (o) {
    if (typeof r == "function") {
      var a = r;
      r = function () {
        var d = xf(s);
        a.call(d);
      };
    }
    var s = zO(t, r, e, 0, null, !1, !1, "", $w);
    return (
      (e._reactRootContainer = s),
      (e[Kr] = s.current),
      ys(e.nodeType === 8 ? e.parentNode : e),
      Ii(),
      s
    );
  }
  for (; (o = e.lastChild); ) e.removeChild(o);
  if (typeof r == "function") {
    var u = r;
    r = function () {
      var d = xf(f);
      u.call(d);
    };
  }
  var f = Zg(e, 0, !1, null, null, !1, !1, "", $w);
  return (
    (e._reactRootContainer = f),
    (e[Kr] = f.current),
    ys(e.nodeType === 8 ? e.parentNode : e),
    Ii(function () {
      Gd(t, f, n, r);
    }),
    f
  );
}
function Qd(e, t, n, r, o) {
  var a = n._reactRootContainer;
  if (a) {
    var s = a;
    if (typeof o == "function") {
      var u = o;
      o = function () {
        var f = xf(s);
        u.call(f);
      };
    }
    Gd(t, s, e, o);
  } else s = Wz(n, t, e, o, r);
  return xf(s);
}
uP = function (e) {
  switch (e.tag) {
    case 3:
      var t = e.stateNode;
      if (t.current.memoizedState.isDehydrated) {
        var n = Hl(t.pendingLanes);
        n !== 0 &&
          (wg(t, n | 1), rn(t, ot()), !(be & 6) && ((Ua = ot() + 500), Qo()));
      }
      break;
    case 13:
      Ii(function () {
        var r = Qr(e, 1);
        if (r !== null) {
          var o = Ht();
          lr(r, e, 1, o);
        }
      }),
        Jg(e, 1);
  }
};
bg = function (e) {
  if (e.tag === 13) {
    var t = Qr(e, 134217728);
    if (t !== null) {
      var n = Ht();
      lr(t, e, 134217728, n);
    }
    Jg(e, 134217728);
  }
};
cP = function (e) {
  if (e.tag === 13) {
    var t = Mo(e),
      n = Qr(e, t);
    if (n !== null) {
      var r = Ht();
      lr(n, e, t, r);
    }
    Jg(e, t);
  }
};
fP = function () {
  return Ne;
};
dP = function (e, t) {
  var n = Ne;
  try {
    return (Ne = e), t();
  } finally {
    Ne = n;
  }
};
Kh = function (e, t, n) {
  switch (t) {
    case "input":
      if ((Bh(e, n), (t = n.name), n.type === "radio" && t != null)) {
        for (n = e; n.parentNode; ) n = n.parentNode;
        for (
          n = n.querySelectorAll(
            "input[name=" + JSON.stringify("" + t) + '][type="radio"]'
          ),
            t = 0;
          t < n.length;
          t++
        ) {
          var r = n[t];
          if (r !== e && r.form === e.form) {
            var o = Fd(r);
            if (!o) throw Error(V(90));
            US(r), Bh(r, o);
          }
        }
      }
      break;
    case "textarea":
      VS(e, n);
      break;
    case "select":
      (t = n.value), t != null && Ca(e, !!n.multiple, t, !1);
  }
};
ZS = Xg;
JS = Ii;
var Bz = { usingClientEntryPoint: !1, Events: [Bs, ya, Fd, QS, qS, Xg] },
  Al = {
    findFiberByHostInstance: _i,
    bundleType: 0,
    version: "18.2.0",
    rendererPackageName: "react-dom",
  },
  Uz = {
    bundleType: Al.bundleType,
    version: Al.version,
    rendererPackageName: Al.rendererPackageName,
    rendererConfig: Al.rendererConfig,
    overrideHookState: null,
    overrideHookStateDeletePath: null,
    overrideHookStateRenamePath: null,
    overrideProps: null,
    overridePropsDeletePath: null,
    overridePropsRenamePath: null,
    setErrorHandler: null,
    setSuspenseHandler: null,
    scheduleUpdate: null,
    currentDispatcherRef: ro.ReactCurrentDispatcher,
    findHostInstanceByFiber: function (e) {
      return (e = nP(e)), e === null ? null : e.stateNode;
    },
    findFiberByHostInstance: Al.findFiberByHostInstance || Fz,
    findHostInstancesForRefresh: null,
    scheduleRefresh: null,
    scheduleRoot: null,
    setRefreshHandler: null,
    getCurrentFiber: null,
    reconcilerVersion: "18.2.0-next-9e3b772b8-20220608",
  };
if (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ < "u") {
  var sc = __REACT_DEVTOOLS_GLOBAL_HOOK__;
  if (!sc.isDisabled && sc.supportsFiber)
    try {
      (Ld = sc.inject(Uz)), (Sr = sc);
    } catch {}
}
xn.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = Bz;
xn.createPortal = function (e, t) {
  var n = 2 < arguments.length && arguments[2] !== void 0 ? arguments[2] : null;
  if (!t0(t)) throw Error(V(200));
  return jz(e, t, null, n);
};
xn.createRoot = function (e, t) {
  if (!t0(e)) throw Error(V(299));
  var n = !1,
    r = "",
    o = AO;
  return (
    t != null &&
      (t.unstable_strictMode === !0 && (n = !0),
      t.identifierPrefix !== void 0 && (r = t.identifierPrefix),
      t.onRecoverableError !== void 0 && (o = t.onRecoverableError)),
    (t = Zg(e, 1, !1, null, null, n, !1, r, o)),
    (e[Kr] = t.current),
    ys(e.nodeType === 8 ? e.parentNode : e),
    new e0(t)
  );
};
xn.findDOMNode = function (e) {
  if (e == null) return null;
  if (e.nodeType === 1) return e;
  var t = e._reactInternals;
  if (t === void 0)
    throw typeof e.render == "function"
      ? Error(V(188))
      : ((e = Object.keys(e).join(",")), Error(V(268, e)));
  return (e = nP(t)), (e = e === null ? null : e.stateNode), e;
};
xn.flushSync = function (e) {
  return Ii(e);
};
xn.hydrate = function (e, t, n) {
  if (!Kd(t)) throw Error(V(200));
  return Qd(null, e, t, !0, n);
};
xn.hydrateRoot = function (e, t, n) {
  if (!t0(e)) throw Error(V(405));
  var r = (n != null && n.hydratedSources) || null,
    o = !1,
    a = "",
    s = AO;
  if (
    (n != null &&
      (n.unstable_strictMode === !0 && (o = !0),
      n.identifierPrefix !== void 0 && (a = n.identifierPrefix),
      n.onRecoverableError !== void 0 && (s = n.onRecoverableError)),
    (t = zO(t, null, e, 1, n ?? null, o, !1, a, s)),
    (e[Kr] = t.current),
    ys(e),
    r)
  )
    for (e = 0; e < r.length; e++)
      (n = r[e]),
        (o = n._getVersion),
        (o = o(n._source)),
        t.mutableSourceEagerHydrationData == null
          ? (t.mutableSourceEagerHydrationData = [n, o])
          : t.mutableSourceEagerHydrationData.push(n, o);
  return new Xd(t);
};
xn.render = function (e, t, n) {
  if (!Kd(t)) throw Error(V(200));
  return Qd(null, e, t, !1, n);
};
xn.unmountComponentAtNode = function (e) {
  if (!Kd(e)) throw Error(V(40));
  return e._reactRootContainer
    ? (Ii(function () {
        Qd(null, null, e, !1, function () {
          (e._reactRootContainer = null), (e[Kr] = null);
        });
      }),
      !0)
    : !1;
};
xn.unstable_batchedUpdates = Xg;
xn.unstable_renderSubtreeIntoContainer = function (e, t, n, r) {
  if (!Kd(n)) throw Error(V(200));
  if (e == null || e._reactInternals === void 0) throw Error(V(38));
  return Qd(e, t, n, !1, r);
};
xn.version = "18.2.0-next-9e3b772b8-20220608";
function LO() {
  if (
    !(
      typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ > "u" ||
      typeof __REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE != "function"
    )
  )
    try {
      __REACT_DEVTOOLS_GLOBAL_HOOK__.checkDCE(LO);
    } catch (e) {
      console.error(e);
    }
}
LO(), (zS.exports = xn);
var Ja = zS.exports;
const ph = lg(Ja);
var Cw = Ja;
(x_.createRoot = Cw.createRoot), (x_.hydrateRoot = Cw.hydrateRoot);
/**
 * @remix-run/router v1.6.3
 *
 * Copyright (c) Remix Software Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE.md file in the root directory of this source tree.
 *
 * @license MIT
 */ function $s() {
  return (
    ($s = Object.assign
      ? Object.assign.bind()
      : function (e) {
          for (var t = 1; t < arguments.length; t++) {
            var n = arguments[t];
            for (var r in n)
              Object.prototype.hasOwnProperty.call(n, r) && (e[r] = n[r]);
          }
          return e;
        }),
    $s.apply(this, arguments)
  );
}
var Ro;
(function (e) {
  (e.Pop = "POP"), (e.Push = "PUSH"), (e.Replace = "REPLACE");
})(Ro || (Ro = {}));
const kw = "popstate";
function Hz(e) {
  e === void 0 && (e = {});
  function t(r, o) {
    let { pathname: a, search: s, hash: u } = r.location;
    return Cv(
      "",
      { pathname: a, search: s, hash: u },
      (o.state && o.state.usr) || null,
      (o.state && o.state.key) || "default"
    );
  }
  function n(r, o) {
    return typeof o == "string" ? o : Sf(o);
  }
  return Yz(t, n, null, e);
}
function it(e, t) {
  if (e === !1 || e === null || typeof e > "u") throw new Error(t);
}
function n0(e, t) {
  if (!e) {
    typeof console < "u" && console.warn(t);
    try {
      throw new Error(t);
    } catch {}
  }
}
function Vz() {
  return Math.random().toString(36).substr(2, 8);
}
function Rw(e, t) {
  return { usr: e.state, key: e.key, idx: t };
}
function Cv(e, t, n, r) {
  return (
    n === void 0 && (n = null),
    $s(
      { pathname: typeof e == "string" ? e : e.pathname, search: "", hash: "" },
      typeof t == "string" ? el(t) : t,
      { state: n, key: (t && t.key) || r || Vz() }
    )
  );
}
function Sf(e) {
  let { pathname: t = "/", search: n = "", hash: r = "" } = e;
  return (
    n && n !== "?" && (t += n.charAt(0) === "?" ? n : "?" + n),
    r && r !== "#" && (t += r.charAt(0) === "#" ? r : "#" + r),
    t
  );
}
function el(e) {
  let t = {};
  if (e) {
    let n = e.indexOf("#");
    n >= 0 && ((t.hash = e.substr(n)), (e = e.substr(0, n)));
    let r = e.indexOf("?");
    r >= 0 && ((t.search = e.substr(r)), (e = e.substr(0, r))),
      e && (t.pathname = e);
  }
  return t;
}
function Yz(e, t, n, r) {
  r === void 0 && (r = {});
  let { window: o = document.defaultView, v5Compat: a = !1 } = r,
    s = o.history,
    u = Ro.Pop,
    f = null,
    d = m();
  d == null && ((d = 0), s.replaceState($s({}, s.state, { idx: d }), ""));
  function m() {
    return (s.state || { idx: null }).idx;
  }
  function h() {
    u = Ro.Pop;
    let $ = m(),
      _ = $ == null ? null : $ - d;
    (d = $), f && f({ action: u, location: E.location, delta: _ });
  }
  function v($, _) {
    u = Ro.Push;
    let w = Cv(E.location, $, _);
    n && n(w, $), (d = m() + 1);
    let P = Rw(w, d),
      k = E.createHref(w);
    try {
      s.pushState(P, "", k);
    } catch (I) {
      if (I instanceof DOMException && I.name === "DataCloneError") throw I;
      o.location.assign(k);
    }
    a && f && f({ action: u, location: E.location, delta: 1 });
  }
  function b($, _) {
    u = Ro.Replace;
    let w = Cv(E.location, $, _);
    n && n(w, $), (d = m());
    let P = Rw(w, d),
      k = E.createHref(w);
    s.replaceState(P, "", k),
      a && f && f({ action: u, location: E.location, delta: 0 });
  }
  function O($) {
    let _ = o.location.origin !== "null" ? o.location.origin : o.location.href,
      w = typeof $ == "string" ? $ : Sf($);
    return (
      it(
        _,
        "No window.location.(origin|href) available to create URL for href: " +
          w
      ),
      new URL(w, _)
    );
  }
  let E = {
    get action() {
      return u;
    },
    get location() {
      return e(o, s);
    },
    listen($) {
      if (f) throw new Error("A history only accepts one active listener");
      return (
        o.addEventListener(kw, h),
        (f = $),
        () => {
          o.removeEventListener(kw, h), (f = null);
        }
      );
    },
    createHref($) {
      return t(o, $);
    },
    createURL: O,
    encodeLocation($) {
      let _ = O($);
      return { pathname: _.pathname, search: _.search, hash: _.hash };
    },
    push: v,
    replace: b,
    go($) {
      return s.go($);
    },
  };
  return E;
}
var Nw;
(function (e) {
  (e.data = "data"),
    (e.deferred = "deferred"),
    (e.redirect = "redirect"),
    (e.error = "error");
})(Nw || (Nw = {}));
function Gz(e, t, n) {
  n === void 0 && (n = "/");
  let r = typeof t == "string" ? el(t) : t,
    o = r0(r.pathname || "/", n);
  if (o == null) return null;
  let a = DO(e);
  Xz(a);
  let s = null;
  for (let u = 0; s == null && u < a.length; ++u) s = rA(a[u], aA(o));
  return s;
}
function DO(e, t, n, r) {
  t === void 0 && (t = []), n === void 0 && (n = []), r === void 0 && (r = "");
  let o = (a, s, u) => {
    let f = {
      relativePath: u === void 0 ? a.path || "" : u,
      caseSensitive: a.caseSensitive === !0,
      childrenIndex: s,
      route: a,
    };
    f.relativePath.startsWith("/") &&
      (it(
        f.relativePath.startsWith(r),
        'Absolute route path "' +
          f.relativePath +
          '" nested under path ' +
          ('"' + r + '" is not valid. An absolute child route path ') +
          "must start with the combined path of all its parent routes."
      ),
      (f.relativePath = f.relativePath.slice(r.length)));
    let d = Fo([r, f.relativePath]),
      m = n.concat(f);
    a.children &&
      a.children.length > 0 &&
      (it(
        a.index !== !0,
        "Index routes must not have child routes. Please remove " +
          ('all child routes from route path "' + d + '".')
      ),
      DO(a.children, t, m, d)),
      !(a.path == null && !a.index) &&
        t.push({ path: d, score: tA(d, a.index), routesMeta: m });
  };
  return (
    e.forEach((a, s) => {
      var u;
      if (a.path === "" || !((u = a.path) != null && u.includes("?"))) o(a, s);
      else for (let f of MO(a.path)) o(a, s, f);
    }),
    t
  );
}
function MO(e) {
  let t = e.split("/");
  if (t.length === 0) return [];
  let [n, ...r] = t,
    o = n.endsWith("?"),
    a = n.replace(/\?$/, "");
  if (r.length === 0) return o ? [a, ""] : [a];
  let s = MO(r.join("/")),
    u = [];
  return (
    u.push(...s.map((f) => (f === "" ? a : [a, f].join("/")))),
    o && u.push(...s),
    u.map((f) => (e.startsWith("/") && f === "" ? "/" : f))
  );
}
function Xz(e) {
  e.sort((t, n) =>
    t.score !== n.score
      ? n.score - t.score
      : nA(
          t.routesMeta.map((r) => r.childrenIndex),
          n.routesMeta.map((r) => r.childrenIndex)
        )
  );
}
const Kz = /^:\w+$/,
  Qz = 3,
  qz = 2,
  Zz = 1,
  Jz = 10,
  eA = -2,
  Iw = (e) => e === "*";
function tA(e, t) {
  let n = e.split("/"),
    r = n.length;
  return (
    n.some(Iw) && (r += eA),
    t && (r += qz),
    n
      .filter((o) => !Iw(o))
      .reduce((o, a) => o + (Kz.test(a) ? Qz : a === "" ? Zz : Jz), r)
  );
}
function nA(e, t) {
  return e.length === t.length && e.slice(0, -1).every((r, o) => r === t[o])
    ? e[e.length - 1] - t[t.length - 1]
    : 0;
}
function rA(e, t) {
  let { routesMeta: n } = e,
    r = {},
    o = "/",
    a = [];
  for (let s = 0; s < n.length; ++s) {
    let u = n[s],
      f = s === n.length - 1,
      d = o === "/" ? t : t.slice(o.length) || "/",
      m = oA(
        { path: u.relativePath, caseSensitive: u.caseSensitive, end: f },
        d
      );
    if (!m) return null;
    Object.assign(r, m.params);
    let h = u.route;
    a.push({
      params: r,
      pathname: Fo([o, m.pathname]),
      pathnameBase: cA(Fo([o, m.pathnameBase])),
      route: h,
    }),
      m.pathnameBase !== "/" && (o = Fo([o, m.pathnameBase]));
  }
  return a;
}
function oA(e, t) {
  typeof e == "string" && (e = { path: e, caseSensitive: !1, end: !0 });
  let [n, r] = iA(e.path, e.caseSensitive, e.end),
    o = t.match(n);
  if (!o) return null;
  let a = o[0],
    s = a.replace(/(.)\/+$/, "$1"),
    u = o.slice(1);
  return {
    params: r.reduce((d, m, h) => {
      if (m === "*") {
        let v = u[h] || "";
        s = a.slice(0, a.length - v.length).replace(/(.)\/+$/, "$1");
      }
      return (d[m] = lA(u[h] || "", m)), d;
    }, {}),
    pathname: a,
    pathnameBase: s,
    pattern: e,
  };
}
function iA(e, t, n) {
  t === void 0 && (t = !1),
    n === void 0 && (n = !0),
    n0(
      e === "*" || !e.endsWith("*") || e.endsWith("/*"),
      'Route path "' +
        e +
        '" will be treated as if it were ' +
        ('"' + e.replace(/\*$/, "/*") + '" because the `*` character must ') +
        "always follow a `/` in the pattern. To get rid of this warning, " +
        ('please change the route path to "' + e.replace(/\*$/, "/*") + '".')
    );
  let r = [],
    o =
      "^" +
      e
        .replace(/\/*\*?$/, "")
        .replace(/^\/*/, "/")
        .replace(/[\\.*+^$?{}|()[\]]/g, "\\$&")
        .replace(/\/:(\w+)/g, (s, u) => (r.push(u), "/([^\\/]+)"));
  return (
    e.endsWith("*")
      ? (r.push("*"),
        (o += e === "*" || e === "/*" ? "(.*)$" : "(?:\\/(.+)|\\/*)$"))
      : n
      ? (o += "\\/*$")
      : e !== "" && e !== "/" && (o += "(?:(?=\\/|$))"),
    [new RegExp(o, t ? void 0 : "i"), r]
  );
}
function aA(e) {
  try {
    return decodeURI(e);
  } catch (t) {
    return (
      n0(
        !1,
        'The URL path "' +
          e +
          '" could not be decoded because it is is a malformed URL segment. This is probably due to a bad percent ' +
          ("encoding (" + t + ").")
      ),
      e
    );
  }
}
function lA(e, t) {
  try {
    return decodeURIComponent(e);
  } catch (n) {
    return (
      n0(
        !1,
        'The value for the URL param "' +
          t +
          '" will not be decoded because' +
          (' the string "' +
            e +
            '" is a malformed URL segment. This is probably') +
          (" due to a bad percent encoding (" + n + ").")
      ),
      e
    );
  }
}
function r0(e, t) {
  if (t === "/") return e;
  if (!e.toLowerCase().startsWith(t.toLowerCase())) return null;
  let n = t.endsWith("/") ? t.length - 1 : t.length,
    r = e.charAt(n);
  return r && r !== "/" ? null : e.slice(n) || "/";
}
function sA(e, t) {
  t === void 0 && (t = "/");
  let {
    pathname: n,
    search: r = "",
    hash: o = "",
  } = typeof e == "string" ? el(e) : e;
  return {
    pathname: n ? (n.startsWith("/") ? n : uA(n, t)) : t,
    search: fA(r),
    hash: dA(o),
  };
}
function uA(e, t) {
  let n = t.replace(/\/+$/, "").split("/");
  return (
    e.split("/").forEach((o) => {
      o === ".." ? n.length > 1 && n.pop() : o !== "." && n.push(o);
    }),
    n.length > 1 ? n.join("/") : "/"
  );
}
function mh(e, t, n, r) {
  return (
    "Cannot include a '" +
    e +
    "' character in a manually specified " +
    ("`to." +
      t +
      "` field [" +
      JSON.stringify(r) +
      "].  Please separate it out to the ") +
    ("`to." + n + "` field. Alternatively you may provide the full path as ") +
    'a string in <Link to="..."> and the router will parse it for you.'
  );
}
function o0(e) {
  return e.filter(
    (t, n) => n === 0 || (t.route.path && t.route.path.length > 0)
  );
}
function i0(e, t, n, r) {
  r === void 0 && (r = !1);
  let o;
  typeof e == "string"
    ? (o = el(e))
    : ((o = $s({}, e)),
      it(
        !o.pathname || !o.pathname.includes("?"),
        mh("?", "pathname", "search", o)
      ),
      it(
        !o.pathname || !o.pathname.includes("#"),
        mh("#", "pathname", "hash", o)
      ),
      it(!o.search || !o.search.includes("#"), mh("#", "search", "hash", o)));
  let a = e === "" || o.pathname === "",
    s = a ? "/" : o.pathname,
    u;
  if (r || s == null) u = n;
  else {
    let h = t.length - 1;
    if (s.startsWith("..")) {
      let v = s.split("/");
      for (; v[0] === ".."; ) v.shift(), (h -= 1);
      o.pathname = v.join("/");
    }
    u = h >= 0 ? t[h] : "/";
  }
  let f = sA(o, u),
    d = s && s !== "/" && s.endsWith("/"),
    m = (a || s === ".") && n.endsWith("/");
  return !f.pathname.endsWith("/") && (d || m) && (f.pathname += "/"), f;
}
const Fo = (e) => e.join("/").replace(/\/\/+/g, "/"),
  cA = (e) => e.replace(/\/+$/, "").replace(/^\/*/, "/"),
  fA = (e) => (!e || e === "?" ? "" : e.startsWith("?") ? e : "?" + e),
  dA = (e) => (!e || e === "#" ? "" : e.startsWith("#") ? e : "#" + e);
function pA(e) {
  return (
    e != null &&
    typeof e.status == "number" &&
    typeof e.statusText == "string" &&
    typeof e.internal == "boolean" &&
    "data" in e
  );
}
const jO = ["post", "put", "patch", "delete"];
new Set(jO);
const mA = ["get", ...jO];
new Set(mA);
/**
 * React Router v6.12.0
 *
 * Copyright (c) Remix Software Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE.md file in the root directory of this source tree.
 *
 * @license MIT
 */ function Pf() {
  return (
    (Pf = Object.assign
      ? Object.assign.bind()
      : function (e) {
          for (var t = 1; t < arguments.length; t++) {
            var n = arguments[t];
            for (var r in n)
              Object.prototype.hasOwnProperty.call(n, r) && (e[r] = n[r]);
          }
          return e;
        }),
    Pf.apply(this, arguments)
  );
}
const a0 = y.createContext(null),
  hA = y.createContext(null),
  tl = y.createContext(null),
  qd = y.createContext(null),
  oo = y.createContext({ outlet: null, matches: [], isDataRoute: !1 }),
  FO = y.createContext(null);
function vA(e, t) {
  let { relative: n } = t === void 0 ? {} : t;
  nl() || it(!1);
  let { basename: r, navigator: o } = y.useContext(tl),
    { hash: a, pathname: s, search: u } = UO(e, { relative: n }),
    f = s;
  return (
    r !== "/" && (f = s === "/" ? r : Fo([r, s])),
    o.createHref({ pathname: f, search: u, hash: a })
  );
}
function nl() {
  return y.useContext(qd) != null;
}
function Hs() {
  return nl() || it(!1), y.useContext(qd).location;
}
function WO(e) {
  y.useContext(tl).static || y.useLayoutEffect(e);
}
function BO() {
  let { isDataRoute: e } = y.useContext(oo);
  return e ? kA() : gA();
}
function gA() {
  nl() || it(!1);
  let e = y.useContext(a0),
    { basename: t, navigator: n } = y.useContext(tl),
    { matches: r } = y.useContext(oo),
    { pathname: o } = Hs(),
    a = JSON.stringify(o0(r).map((f) => f.pathnameBase)),
    s = y.useRef(!1);
  return (
    WO(() => {
      s.current = !0;
    }),
    y.useCallback(
      function (f, d) {
        if ((d === void 0 && (d = {}), !s.current)) return;
        if (typeof f == "number") {
          n.go(f);
          return;
        }
        let m = i0(f, JSON.parse(a), o, d.relative === "path");
        e == null &&
          t !== "/" &&
          (m.pathname = m.pathname === "/" ? t : Fo([t, m.pathname])),
          (d.replace ? n.replace : n.push)(m, d.state, d);
      },
      [t, n, a, o, e]
    )
  );
}
function fK() {
  let { matches: e } = y.useContext(oo),
    t = e[e.length - 1];
  return t ? t.params : {};
}
function UO(e, t) {
  let { relative: n } = t === void 0 ? {} : t,
    { matches: r } = y.useContext(oo),
    { pathname: o } = Hs(),
    a = JSON.stringify(o0(r).map((s) => s.pathnameBase));
  return y.useMemo(() => i0(e, JSON.parse(a), o, n === "path"), [e, a, o, n]);
}
function yA(e, t) {
  return _A(e, t);
}
function _A(e, t, n) {
  nl() || it(!1);
  let { navigator: r } = y.useContext(tl),
    { matches: o } = y.useContext(oo),
    a = o[o.length - 1],
    s = a ? a.params : {};
  a && a.pathname;
  let u = a ? a.pathnameBase : "/";
  a && a.route;
  let f = Hs(),
    d;
  if (t) {
    var m;
    let E = typeof t == "string" ? el(t) : t;
    u === "/" || ((m = E.pathname) != null && m.startsWith(u)) || it(!1),
      (d = E);
  } else d = f;
  let h = d.pathname || "/",
    v = u === "/" ? h : h.slice(u.length) || "/",
    b = Gz(e, { pathname: v }),
    O = PA(
      b &&
        b.map((E) =>
          Object.assign({}, E, {
            params: Object.assign({}, s, E.params),
            pathname: Fo([
              u,
              r.encodeLocation
                ? r.encodeLocation(E.pathname).pathname
                : E.pathname,
            ]),
            pathnameBase:
              E.pathnameBase === "/"
                ? u
                : Fo([
                    u,
                    r.encodeLocation
                      ? r.encodeLocation(E.pathnameBase).pathname
                      : E.pathnameBase,
                  ]),
          })
        ),
      o,
      n
    );
  return t && O
    ? y.createElement(
        qd.Provider,
        {
          value: {
            location: Pf(
              {
                pathname: "/",
                search: "",
                hash: "",
                state: null,
                key: "default",
              },
              d
            ),
            navigationType: Ro.Pop,
          },
        },
        O
      )
    : O;
}
function wA() {
  let e = CA(),
    t = pA(e)
      ? e.status + " " + e.statusText
      : e instanceof Error
      ? e.message
      : JSON.stringify(e),
    n = e instanceof Error ? e.stack : null,
    o = { padding: "0.5rem", backgroundColor: "rgba(200,200,200, 0.5)" },
    a = null;
  return y.createElement(
    y.Fragment,
    null,
    y.createElement("h2", null, "Unexpected Application Error!"),
    y.createElement("h3", { style: { fontStyle: "italic" } }, t),
    n ? y.createElement("pre", { style: o }, n) : null,
    a
  );
}
const bA = y.createElement(wA, null);
class xA extends y.Component {
  constructor(t) {
    super(t),
      (this.state = {
        location: t.location,
        revalidation: t.revalidation,
        error: t.error,
      });
  }
  static getDerivedStateFromError(t) {
    return { error: t };
  }
  static getDerivedStateFromProps(t, n) {
    return n.location !== t.location ||
      (n.revalidation !== "idle" && t.revalidation === "idle")
      ? { error: t.error, location: t.location, revalidation: t.revalidation }
      : {
          error: t.error || n.error,
          location: n.location,
          revalidation: t.revalidation || n.revalidation,
        };
  }
  componentDidCatch(t, n) {
    console.error(
      "React Router caught the following error during render",
      t,
      n
    );
  }
  render() {
    return this.state.error
      ? y.createElement(
          oo.Provider,
          { value: this.props.routeContext },
          y.createElement(FO.Provider, {
            value: this.state.error,
            children: this.props.component,
          })
        )
      : this.props.children;
  }
}
function SA(e) {
  let { routeContext: t, match: n, children: r } = e,
    o = y.useContext(a0);
  return (
    o &&
      o.static &&
      o.staticContext &&
      (n.route.errorElement || n.route.ErrorBoundary) &&
      (o.staticContext._deepestRenderedBoundaryId = n.route.id),
    y.createElement(oo.Provider, { value: t }, r)
  );
}
function PA(e, t, n) {
  var r;
  if ((t === void 0 && (t = []), n === void 0 && (n = null), e == null)) {
    var o;
    if ((o = n) != null && o.errors) e = n.matches;
    else return null;
  }
  let a = e,
    s = (r = n) == null ? void 0 : r.errors;
  if (s != null) {
    let u = a.findIndex(
      (f) => f.route.id && (s == null ? void 0 : s[f.route.id])
    );
    u >= 0 || it(!1), (a = a.slice(0, Math.min(a.length, u + 1)));
  }
  return a.reduceRight((u, f, d) => {
    let m = f.route.id ? (s == null ? void 0 : s[f.route.id]) : null,
      h = null;
    n && (h = f.route.errorElement || bA);
    let v = t.concat(a.slice(0, d + 1)),
      b = () => {
        let O;
        return (
          m
            ? (O = h)
            : f.route.Component
            ? (O = y.createElement(f.route.Component, null))
            : f.route.element
            ? (O = f.route.element)
            : (O = u),
          y.createElement(SA, {
            match: f,
            routeContext: { outlet: u, matches: v, isDataRoute: n != null },
            children: O,
          })
        );
      };
    return n && (f.route.ErrorBoundary || f.route.errorElement || d === 0)
      ? y.createElement(xA, {
          location: n.location,
          revalidation: n.revalidation,
          component: h,
          error: m,
          children: b(),
          routeContext: { outlet: null, matches: v, isDataRoute: !0 },
        })
      : b();
  }, null);
}
var kv;
(function (e) {
  (e.UseBlocker = "useBlocker"),
    (e.UseRevalidator = "useRevalidator"),
    (e.UseNavigateStable = "useNavigate");
})(kv || (kv = {}));
var Cs;
(function (e) {
  (e.UseBlocker = "useBlocker"),
    (e.UseLoaderData = "useLoaderData"),
    (e.UseActionData = "useActionData"),
    (e.UseRouteError = "useRouteError"),
    (e.UseNavigation = "useNavigation"),
    (e.UseRouteLoaderData = "useRouteLoaderData"),
    (e.UseMatches = "useMatches"),
    (e.UseRevalidator = "useRevalidator"),
    (e.UseNavigateStable = "useNavigate"),
    (e.UseRouteId = "useRouteId");
})(Cs || (Cs = {}));
function OA(e) {
  let t = y.useContext(a0);
  return t || it(!1), t;
}
function EA(e) {
  let t = y.useContext(hA);
  return t || it(!1), t;
}
function $A(e) {
  let t = y.useContext(oo);
  return t || it(!1), t;
}
function HO(e) {
  let t = $A(),
    n = t.matches[t.matches.length - 1];
  return n.route.id || it(!1), n.route.id;
}
function CA() {
  var e;
  let t = y.useContext(FO),
    n = EA(Cs.UseRouteError),
    r = HO(Cs.UseRouteError);
  return t || ((e = n.errors) == null ? void 0 : e[r]);
}
function kA() {
  let { router: e } = OA(kv.UseNavigateStable),
    t = HO(Cs.UseNavigateStable),
    n = y.useRef(!1);
  return (
    WO(() => {
      n.current = !0;
    }),
    y.useCallback(
      function (o, a) {
        a === void 0 && (a = {}),
          n.current &&
            (typeof o == "number"
              ? e.navigate(o)
              : e.navigate(o, Pf({ fromRouteId: t }, a)));
      },
      [e, t]
    )
  );
}
function dK(e) {
  let { to: t, replace: n, state: r, relative: o } = e;
  nl() || it(!1);
  let { matches: a } = y.useContext(oo),
    { pathname: s } = Hs(),
    u = BO(),
    f = i0(
      t,
      o0(a).map((m) => m.pathnameBase),
      s,
      o === "path"
    ),
    d = JSON.stringify(f);
  return (
    y.useEffect(
      () => u(JSON.parse(d), { replace: n, state: r, relative: o }),
      [u, d, o, n, r]
    ),
    null
  );
}
function RA(e) {
  it(!1);
}
function NA(e) {
  let {
    basename: t = "/",
    children: n = null,
    location: r,
    navigationType: o = Ro.Pop,
    navigator: a,
    static: s = !1,
  } = e;
  nl() && it(!1);
  let u = t.replace(/^\/*/, "/"),
    f = y.useMemo(() => ({ basename: u, navigator: a, static: s }), [u, a, s]);
  typeof r == "string" && (r = el(r));
  let {
      pathname: d = "/",
      search: m = "",
      hash: h = "",
      state: v = null,
      key: b = "default",
    } = r,
    O = y.useMemo(() => {
      let E = r0(d, u);
      return E == null
        ? null
        : {
            location: { pathname: E, search: m, hash: h, state: v, key: b },
            navigationType: o,
          };
    }, [u, d, m, h, v, b, o]);
  return O == null
    ? null
    : y.createElement(
        tl.Provider,
        { value: f },
        y.createElement(qd.Provider, { children: n, value: O })
      );
}
function pK(e) {
  let { children: t, location: n } = e;
  return yA(Rv(t), n);
}
var Tw;
(function (e) {
  (e[(e.pending = 0)] = "pending"),
    (e[(e.success = 1)] = "success"),
    (e[(e.error = 2)] = "error");
})(Tw || (Tw = {}));
new Promise(() => {});
function Rv(e, t) {
  t === void 0 && (t = []);
  let n = [];
  return (
    y.Children.forEach(e, (r, o) => {
      if (!y.isValidElement(r)) return;
      let a = [...t, o];
      if (r.type === y.Fragment) {
        n.push.apply(n, Rv(r.props.children, a));
        return;
      }
      r.type !== RA && it(!1), !r.props.index || !r.props.children || it(!1);
      let s = {
        id: r.props.id || a.join("-"),
        caseSensitive: r.props.caseSensitive,
        element: r.props.element,
        Component: r.props.Component,
        index: r.props.index,
        path: r.props.path,
        loader: r.props.loader,
        action: r.props.action,
        errorElement: r.props.errorElement,
        ErrorBoundary: r.props.ErrorBoundary,
        hasErrorBoundary:
          r.props.ErrorBoundary != null || r.props.errorElement != null,
        shouldRevalidate: r.props.shouldRevalidate,
        handle: r.props.handle,
        lazy: r.props.lazy,
      };
      r.props.children && (s.children = Rv(r.props.children, a)), n.push(s);
    }),
    n
  );
}
/**
 * React Router DOM v6.12.0
 *
 * Copyright (c) Remix Software Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE.md file in the root directory of this source tree.
 *
 * @license MIT
 */ function Nv() {
  return (
    (Nv = Object.assign
      ? Object.assign.bind()
      : function (e) {
          for (var t = 1; t < arguments.length; t++) {
            var n = arguments[t];
            for (var r in n)
              Object.prototype.hasOwnProperty.call(n, r) && (e[r] = n[r]);
          }
          return e;
        }),
    Nv.apply(this, arguments)
  );
}
function IA(e, t) {
  if (e == null) return {};
  var n = {},
    r = Object.keys(e),
    o,
    a;
  for (a = 0; a < r.length; a++)
    (o = r[a]), !(t.indexOf(o) >= 0) && (n[o] = e[o]);
  return n;
}
function TA(e) {
  return !!(e.metaKey || e.altKey || e.ctrlKey || e.shiftKey);
}
function zA(e, t) {
  return e.button === 0 && (!t || t === "_self") && !TA(e);
}
const AA = [
  "onClick",
  "relative",
  "reloadDocument",
  "replace",
  "state",
  "target",
  "to",
  "preventScrollReset",
];
function mK(e) {
  let { basename: t, children: n, window: r } = e,
    o = y.useRef();
  o.current == null && (o.current = Hz({ window: r, v5Compat: !0 }));
  let a = o.current,
    [s, u] = y.useState({ action: a.action, location: a.location }),
    f = y.useCallback(
      (d) => {
        "startTransition" in qc ? y.startTransition(() => u(d)) : u(d);
      },
      [u]
    );
  return (
    y.useLayoutEffect(() => a.listen(f), [a, f]),
    y.createElement(NA, {
      basename: t,
      children: n,
      location: s.location,
      navigationType: s.action,
      navigator: a,
    })
  );
}
const LA =
    typeof window < "u" &&
    typeof window.document < "u" &&
    typeof window.document.createElement < "u",
  DA = /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i,
  hK = y.forwardRef(function (t, n) {
    let {
        onClick: r,
        relative: o,
        reloadDocument: a,
        replace: s,
        state: u,
        target: f,
        to: d,
        preventScrollReset: m,
      } = t,
      h = IA(t, AA),
      { basename: v } = y.useContext(tl),
      b,
      O = !1;
    if (typeof d == "string" && DA.test(d) && ((b = d), LA))
      try {
        let w = new URL(window.location.href),
          P = d.startsWith("//") ? new URL(w.protocol + d) : new URL(d),
          k = r0(P.pathname, v);
        P.origin === w.origin && k != null
          ? (d = k + P.search + P.hash)
          : (O = !0);
      } catch {}
    let E = vA(d, { relative: o }),
      $ = MA(d, {
        replace: s,
        state: u,
        target: f,
        preventScrollReset: m,
        relative: o,
      });
    function _(w) {
      r && r(w), w.defaultPrevented || $(w);
    }
    return y.createElement(
      "a",
      Nv({}, h, { href: b || E, onClick: O || a ? r : _, ref: n, target: f })
    );
  });
var zw;
(function (e) {
  (e.UseScrollRestoration = "useScrollRestoration"),
    (e.UseSubmitImpl = "useSubmitImpl"),
    (e.UseFetcher = "useFetcher");
})(zw || (zw = {}));
var Aw;
(function (e) {
  (e.UseFetchers = "useFetchers"),
    (e.UseScrollRestoration = "useScrollRestoration");
})(Aw || (Aw = {}));
function MA(e, t) {
  let {
      target: n,
      replace: r,
      state: o,
      preventScrollReset: a,
      relative: s,
    } = t === void 0 ? {} : t,
    u = BO(),
    f = Hs(),
    d = UO(e, { relative: s });
  return y.useCallback(
    (m) => {
      if (zA(m, n)) {
        m.preventDefault();
        let h = r !== void 0 ? r : Sf(f) === Sf(d);
        u(e, { replace: h, state: o, preventScrollReset: a, relative: s });
      }
    },
    [f, u, d, r, o, n, e, a, s]
  );
}
const VO = y.createContext(null);
VO.displayName = "@mantine/notifications/NotificationsContext";
function je() {
  return (
    (je = Object.assign
      ? Object.assign.bind()
      : function (e) {
          for (var t = 1; t < arguments.length; t++) {
            var n = arguments[t];
            for (var r in n)
              Object.prototype.hasOwnProperty.call(n, r) && (e[r] = n[r]);
          }
          return e;
        }),
    je.apply(this, arguments)
  );
}
function l0(e, t) {
  if (e == null) return {};
  var n = {},
    r = Object.keys(e),
    o,
    a;
  for (a = 0; a < r.length; a++)
    (o = r[a]), !(t.indexOf(o) >= 0) && (n[o] = e[o]);
  return n;
}
function Iv(e, t) {
  return (
    (Iv = Object.setPrototypeOf
      ? Object.setPrototypeOf.bind()
      : function (r, o) {
          return (r.__proto__ = o), r;
        }),
    Iv(e, t)
  );
}
function YO(e, t) {
  (e.prototype = Object.create(t.prototype)),
    (e.prototype.constructor = e),
    Iv(e, t);
}
var GO = { exports: {} },
  jA = "SECRET_DO_NOT_PASS_THIS_OR_YOU_WILL_BE_FIRED",
  FA = jA,
  WA = FA;
function XO() {}
function KO() {}
KO.resetWarningCache = XO;
var BA = function () {
  function e(r, o, a, s, u, f) {
    if (f !== WA) {
      var d = new Error(
        "Calling PropTypes validators directly is not supported by the `prop-types` package. Use PropTypes.checkPropTypes() to call them. Read more at http://fb.me/use-check-prop-types"
      );
      throw ((d.name = "Invariant Violation"), d);
    }
  }
  e.isRequired = e;
  function t() {
    return e;
  }
  var n = {
    array: e,
    bigint: e,
    bool: e,
    func: e,
    number: e,
    object: e,
    string: e,
    symbol: e,
    any: e,
    arrayOf: t,
    element: e,
    elementType: e,
    instanceOf: t,
    node: e,
    objectOf: t,
    oneOf: t,
    oneOfType: t,
    shape: t,
    exact: t,
    checkPropTypes: KO,
    resetWarningCache: XO,
  };
  return (n.PropTypes = n), n;
};
GO.exports = BA();
var UA = GO.exports;
const ge = lg(UA),
  Lw = { disabled: !1 },
  Of = R.createContext(null);
var Yl = "unmounted",
  hi = "exited",
  vi = "entering",
  fa = "entered",
  Tv = "exiting",
  io = (function (e) {
    YO(t, e);
    function t(r, o) {
      var a;
      a = e.call(this, r, o) || this;
      var s = o,
        u = s && !s.isMounting ? r.enter : r.appear,
        f;
      return (
        (a.appearStatus = null),
        r.in
          ? u
            ? ((f = hi), (a.appearStatus = vi))
            : (f = fa)
          : r.unmountOnExit || r.mountOnEnter
          ? (f = Yl)
          : (f = hi),
        (a.state = { status: f }),
        (a.nextCallback = null),
        a
      );
    }
    t.getDerivedStateFromProps = function (o, a) {
      var s = o.in;
      return s && a.status === Yl ? { status: hi } : null;
    };
    var n = t.prototype;
    return (
      (n.componentDidMount = function () {
        this.updateStatus(!0, this.appearStatus);
      }),
      (n.componentDidUpdate = function (o) {
        var a = null;
        if (o !== this.props) {
          var s = this.state.status;
          this.props.in
            ? s !== vi && s !== fa && (a = vi)
            : (s === vi || s === fa) && (a = Tv);
        }
        this.updateStatus(!1, a);
      }),
      (n.componentWillUnmount = function () {
        this.cancelNextCallback();
      }),
      (n.getTimeouts = function () {
        var o = this.props.timeout,
          a,
          s,
          u;
        return (
          (a = s = u = o),
          o != null &&
            typeof o != "number" &&
            ((a = o.exit),
            (s = o.enter),
            (u = o.appear !== void 0 ? o.appear : s)),
          { exit: a, enter: s, appear: u }
        );
      }),
      (n.updateStatus = function (o, a) {
        o === void 0 && (o = !1),
          a !== null
            ? (this.cancelNextCallback(),
              a === vi ? this.performEnter(o) : this.performExit())
            : this.props.unmountOnExit &&
              this.state.status === hi &&
              this.setState({ status: Yl });
      }),
      (n.performEnter = function (o) {
        var a = this,
          s = this.props.enter,
          u = this.context ? this.context.isMounting : o,
          f = this.props.nodeRef ? [u] : [ph.findDOMNode(this), u],
          d = f[0],
          m = f[1],
          h = this.getTimeouts(),
          v = u ? h.appear : h.enter;
        if ((!o && !s) || Lw.disabled) {
          this.safeSetState({ status: fa }, function () {
            a.props.onEntered(d);
          });
          return;
        }
        this.props.onEnter(d, m),
          this.safeSetState({ status: vi }, function () {
            a.props.onEntering(d, m),
              a.onTransitionEnd(v, function () {
                a.safeSetState({ status: fa }, function () {
                  a.props.onEntered(d, m);
                });
              });
          });
      }),
      (n.performExit = function () {
        var o = this,
          a = this.props.exit,
          s = this.getTimeouts(),
          u = this.props.nodeRef ? void 0 : ph.findDOMNode(this);
        if (!a || Lw.disabled) {
          this.safeSetState({ status: hi }, function () {
            o.props.onExited(u);
          });
          return;
        }
        this.props.onExit(u),
          this.safeSetState({ status: Tv }, function () {
            o.props.onExiting(u),
              o.onTransitionEnd(s.exit, function () {
                o.safeSetState({ status: hi }, function () {
                  o.props.onExited(u);
                });
              });
          });
      }),
      (n.cancelNextCallback = function () {
        this.nextCallback !== null &&
          (this.nextCallback.cancel(), (this.nextCallback = null));
      }),
      (n.safeSetState = function (o, a) {
        (a = this.setNextCallback(a)), this.setState(o, a);
      }),
      (n.setNextCallback = function (o) {
        var a = this,
          s = !0;
        return (
          (this.nextCallback = function (u) {
            s && ((s = !1), (a.nextCallback = null), o(u));
          }),
          (this.nextCallback.cancel = function () {
            s = !1;
          }),
          this.nextCallback
        );
      }),
      (n.onTransitionEnd = function (o, a) {
        this.setNextCallback(a);
        var s = this.props.nodeRef
            ? this.props.nodeRef.current
            : ph.findDOMNode(this),
          u = o == null && !this.props.addEndListener;
        if (!s || u) {
          setTimeout(this.nextCallback, 0);
          return;
        }
        if (this.props.addEndListener) {
          var f = this.props.nodeRef
              ? [this.nextCallback]
              : [s, this.nextCallback],
            d = f[0],
            m = f[1];
          this.props.addEndListener(d, m);
        }
        o != null && setTimeout(this.nextCallback, o);
      }),
      (n.render = function () {
        var o = this.state.status;
        if (o === Yl) return null;
        var a = this.props,
          s = a.children;
        a.in,
          a.mountOnEnter,
          a.unmountOnExit,
          a.appear,
          a.enter,
          a.exit,
          a.timeout,
          a.addEndListener,
          a.onEnter,
          a.onEntering,
          a.onEntered,
          a.onExit,
          a.onExiting,
          a.onExited,
          a.nodeRef;
        var u = l0(a, [
          "children",
          "in",
          "mountOnEnter",
          "unmountOnExit",
          "appear",
          "enter",
          "exit",
          "timeout",
          "addEndListener",
          "onEnter",
          "onEntering",
          "onEntered",
          "onExit",
          "onExiting",
          "onExited",
          "nodeRef",
        ]);
        return R.createElement(
          Of.Provider,
          { value: null },
          typeof s == "function"
            ? s(o, u)
            : R.cloneElement(R.Children.only(s), u)
        );
      }),
      t
    );
  })(R.Component);
io.contextType = Of;
io.propTypes = {};
function sa() {}
io.defaultProps = {
  in: !1,
  mountOnEnter: !1,
  unmountOnExit: !1,
  appear: !1,
  enter: !0,
  exit: !0,
  onEnter: sa,
  onEntering: sa,
  onEntered: sa,
  onExit: sa,
  onExiting: sa,
  onExited: sa,
};
io.UNMOUNTED = Yl;
io.EXITED = hi;
io.ENTERING = vi;
io.ENTERED = fa;
io.EXITING = Tv;
const HA = io;
function VA(e) {
  if (e === void 0)
    throw new ReferenceError(
      "this hasn't been initialised - super() hasn't been called"
    );
  return e;
}
function s0(e, t) {
  var n = function (a) {
      return t && y.isValidElement(a) ? t(a) : a;
    },
    r = Object.create(null);
  return (
    e &&
      y.Children.map(e, function (o) {
        return o;
      }).forEach(function (o) {
        r[o.key] = n(o);
      }),
    r
  );
}
function YA(e, t) {
  (e = e || {}), (t = t || {});
  function n(m) {
    return m in t ? t[m] : e[m];
  }
  var r = Object.create(null),
    o = [];
  for (var a in e) a in t ? o.length && ((r[a] = o), (o = [])) : o.push(a);
  var s,
    u = {};
  for (var f in t) {
    if (r[f])
      for (s = 0; s < r[f].length; s++) {
        var d = r[f][s];
        u[r[f][s]] = n(d);
      }
    u[f] = n(f);
  }
  for (s = 0; s < o.length; s++) u[o[s]] = n(o[s]);
  return u;
}
function xi(e, t, n) {
  return n[t] != null ? n[t] : e.props[t];
}
function GA(e, t) {
  return s0(e.children, function (n) {
    return y.cloneElement(n, {
      onExited: t.bind(null, n),
      in: !0,
      appear: xi(n, "appear", e),
      enter: xi(n, "enter", e),
      exit: xi(n, "exit", e),
    });
  });
}
function XA(e, t, n) {
  var r = s0(e.children),
    o = YA(t, r);
  return (
    Object.keys(o).forEach(function (a) {
      var s = o[a];
      if (y.isValidElement(s)) {
        var u = a in t,
          f = a in r,
          d = t[a],
          m = y.isValidElement(d) && !d.props.in;
        f && (!u || m)
          ? (o[a] = y.cloneElement(s, {
              onExited: n.bind(null, s),
              in: !0,
              exit: xi(s, "exit", e),
              enter: xi(s, "enter", e),
            }))
          : !f && u && !m
          ? (o[a] = y.cloneElement(s, { in: !1 }))
          : f &&
            u &&
            y.isValidElement(d) &&
            (o[a] = y.cloneElement(s, {
              onExited: n.bind(null, s),
              in: d.props.in,
              exit: xi(s, "exit", e),
              enter: xi(s, "enter", e),
            }));
      }
    }),
    o
  );
}
var KA =
    Object.values ||
    function (e) {
      return Object.keys(e).map(function (t) {
        return e[t];
      });
    },
  QA = {
    component: "div",
    childFactory: function (t) {
      return t;
    },
  },
  u0 = (function (e) {
    YO(t, e);
    function t(r, o) {
      var a;
      a = e.call(this, r, o) || this;
      var s = a.handleExited.bind(VA(a));
      return (
        (a.state = {
          contextValue: { isMounting: !0 },
          handleExited: s,
          firstRender: !0,
        }),
        a
      );
    }
    var n = t.prototype;
    return (
      (n.componentDidMount = function () {
        (this.mounted = !0),
          this.setState({ contextValue: { isMounting: !1 } });
      }),
      (n.componentWillUnmount = function () {
        this.mounted = !1;
      }),
      (t.getDerivedStateFromProps = function (o, a) {
        var s = a.children,
          u = a.handleExited,
          f = a.firstRender;
        return { children: f ? GA(o, u) : XA(o, s, u), firstRender: !1 };
      }),
      (n.handleExited = function (o, a) {
        var s = s0(this.props.children);
        o.key in s ||
          (o.props.onExited && o.props.onExited(a),
          this.mounted &&
            this.setState(function (u) {
              var f = je({}, u.children);
              return delete f[o.key], { children: f };
            }));
      }),
      (n.render = function () {
        var o = this.props,
          a = o.component,
          s = o.childFactory,
          u = l0(o, ["component", "childFactory"]),
          f = this.state.contextValue,
          d = KA(this.state.children).map(s);
        return (
          delete u.appear,
          delete u.enter,
          delete u.exit,
          a === null
            ? R.createElement(Of.Provider, { value: f }, d)
            : R.createElement(
                Of.Provider,
                { value: f },
                R.createElement(a, u, d)
              )
        );
      }),
      t
    );
  })(R.Component);
u0.propTypes = {};
u0.defaultProps = QA;
const qA = u0;
function ZA(e) {
  const t = y.createContext(null);
  return [
    ({ children: o, value: a }) => R.createElement(t.Provider, { value: a }, o),
    () => {
      const o = y.useContext(t);
      if (o === null) throw new Error(e);
      return o;
    },
  ];
}
function c0(e) {
  return Array.isArray(e) ? e : [e];
}
const JA = () => {};
function e9(e, t = { active: !0 }) {
  return typeof e != "function" || !t.active
    ? t.onKeyDown || JA
    : (n) => {
        var r;
        n.key === "Escape" && (e(n), (r = t.onTrigger) == null || r.call(t));
      };
}
function t9({ data: e }) {
  const t = [],
    n = [],
    r = e.reduce(
      (o, a, s) => (
        a.group
          ? o[a.group]
            ? o[a.group].push(s)
            : (o[a.group] = [s])
          : n.push(s),
        o
      ),
      {}
    );
  return (
    Object.keys(r).forEach((o) => {
      t.push(...r[o].map((a) => e[a]));
    }),
    t.push(...n.map((o) => e[o])),
    t
  );
}
function n9(e, t) {
  window.dispatchEvent(new CustomEvent(e, { detail: t }));
}
const r9 = typeof window < "u" ? y.useLayoutEffect : y.useEffect;
function o9(e) {
  function t(r) {
    const o = Object.keys(r).reduce(
      (a, s) => ((a[`${e}:${s}`] = (u) => r[s](u.detail)), a),
      {}
    );
    r9(
      () => (
        Object.keys(o).forEach((a) => {
          window.removeEventListener(a, o[a]), window.addEventListener(a, o[a]);
        }),
        () =>
          Object.keys(o).forEach((a) => {
            window.removeEventListener(a, o[a]);
          })
      ),
      [o]
    );
  }
  function n(r) {
    return (...o) => n9(`${e}:${String(r)}`, o[0]);
  }
  return [t, n];
}
function QO(e) {
  return Array.isArray(e) || e === null
    ? !1
    : typeof e == "object"
    ? e.type !== R.Fragment
    : !1;
}
function qO(e) {
  var t,
    n,
    r = "";
  if (typeof e == "string" || typeof e == "number") r += e;
  else if (typeof e == "object")
    if (Array.isArray(e))
      for (t = 0; t < e.length; t++)
        e[t] && (n = qO(e[t])) && (r && (r += " "), (r += n));
    else for (t in e) e[t] && (r && (r += " "), (r += t));
  return r;
}
function ZO() {
  for (var e = 0, t, n, r = ""; e < arguments.length; )
    (t = arguments[e++]) && (n = qO(t)) && (r && (r += " "), (r += n));
  return r;
}
const i9 = {
  dark: [
    "#C1C2C5",
    "#A6A7AB",
    "#909296",
    "#5c5f66",
    "#373A40",
    "#2C2E33",
    "#25262b",
    "#1A1B1E",
    "#141517",
    "#101113",
  ],
  gray: [
    "#f8f9fa",
    "#f1f3f5",
    "#e9ecef",
    "#dee2e6",
    "#ced4da",
    "#adb5bd",
    "#868e96",
    "#495057",
    "#343a40",
    "#212529",
  ],
  red: [
    "#fff5f5",
    "#ffe3e3",
    "#ffc9c9",
    "#ffa8a8",
    "#ff8787",
    "#ff6b6b",
    "#fa5252",
    "#f03e3e",
    "#e03131",
    "#c92a2a",
  ],
  pink: [
    "#fff0f6",
    "#ffdeeb",
    "#fcc2d7",
    "#faa2c1",
    "#f783ac",
    "#f06595",
    "#e64980",
    "#d6336c",
    "#c2255c",
    "#a61e4d",
  ],
  grape: [
    "#f8f0fc",
    "#f3d9fa",
    "#eebefa",
    "#e599f7",
    "#da77f2",
    "#cc5de8",
    "#be4bdb",
    "#ae3ec9",
    "#9c36b5",
    "#862e9c",
  ],
  violet: [
    "#f3f0ff",
    "#e5dbff",
    "#d0bfff",
    "#b197fc",
    "#9775fa",
    "#845ef7",
    "#7950f2",
    "#7048e8",
    "#6741d9",
    "#5f3dc4",
  ],
  indigo: [
    "#edf2ff",
    "#dbe4ff",
    "#bac8ff",
    "#91a7ff",
    "#748ffc",
    "#5c7cfa",
    "#4c6ef5",
    "#4263eb",
    "#3b5bdb",
    "#364fc7",
  ],
  blue: [
    "#e7f5ff",
    "#d0ebff",
    "#a5d8ff",
    "#74c0fc",
    "#4dabf7",
    "#339af0",
    "#228be6",
    "#1c7ed6",
    "#1971c2",
    "#1864ab",
  ],
  cyan: [
    "#e3fafc",
    "#c5f6fa",
    "#99e9f2",
    "#66d9e8",
    "#3bc9db",
    "#22b8cf",
    "#15aabf",
    "#1098ad",
    "#0c8599",
    "#0b7285",
  ],
  teal: [
    "#e6fcf5",
    "#c3fae8",
    "#96f2d7",
    "#63e6be",
    "#38d9a9",
    "#20c997",
    "#12b886",
    "#0ca678",
    "#099268",
    "#087f5b",
  ],
  green: [
    "#ebfbee",
    "#d3f9d8",
    "#b2f2bb",
    "#8ce99a",
    "#69db7c",
    "#51cf66",
    "#40c057",
    "#37b24d",
    "#2f9e44",
    "#2b8a3e",
  ],
  lime: [
    "#f4fce3",
    "#e9fac8",
    "#d8f5a2",
    "#c0eb75",
    "#a9e34b",
    "#94d82d",
    "#82c91e",
    "#74b816",
    "#66a80f",
    "#5c940d",
  ],
  yellow: [
    "#fff9db",
    "#fff3bf",
    "#ffec99",
    "#ffe066",
    "#ffd43b",
    "#fcc419",
    "#fab005",
    "#f59f00",
    "#f08c00",
    "#e67700",
  ],
  orange: [
    "#fff4e6",
    "#ffe8cc",
    "#ffd8a8",
    "#ffc078",
    "#ffa94d",
    "#ff922b",
    "#fd7e14",
    "#f76707",
    "#e8590c",
    "#d9480f",
  ],
};
function a9(e) {
  return () => ({ fontFamily: e.fontFamily || "sans-serif" });
}
var l9 = Object.defineProperty,
  Dw = Object.getOwnPropertySymbols,
  s9 = Object.prototype.hasOwnProperty,
  u9 = Object.prototype.propertyIsEnumerable,
  Mw = (e, t, n) =>
    t in e
      ? l9(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  jw = (e, t) => {
    for (var n in t || (t = {})) s9.call(t, n) && Mw(e, n, t[n]);
    if (Dw) for (var n of Dw(t)) u9.call(t, n) && Mw(e, n, t[n]);
    return e;
  };
function c9(e) {
  return (t) => ({
    WebkitTapHighlightColor: "transparent",
    [t || "&:focus"]: jw(
      {},
      e.focusRing === "always" || e.focusRing === "auto"
        ? e.focusRingStyles.styles(e)
        : e.focusRingStyles.resetStyles(e)
    ),
    [t
      ? t.replace(":focus", ":focus:not(:focus-visible)")
      : "&:focus:not(:focus-visible)"]: jw(
      {},
      e.focusRing === "auto" || e.focusRing === "never"
        ? e.focusRingStyles.resetStyles(e)
        : null
    ),
  });
}
function Vs(e) {
  return (t) =>
    typeof e.primaryShade == "number"
      ? e.primaryShade
      : e.primaryShade[t || e.colorScheme];
}
function f0(e) {
  const t = Vs(e);
  return (n, r, o = !0, a = !0) => {
    if (typeof n == "string" && n.includes(".")) {
      const [u, f] = n.split("."),
        d = parseInt(f, 10);
      if (u in e.colors && d >= 0 && d < 10)
        return e.colors[u][typeof r == "number" && !a ? r : d];
    }
    const s = typeof r == "number" ? r : t();
    return n in e.colors ? e.colors[n][s] : o ? e.colors[e.primaryColor][s] : n;
  };
}
function JO(e) {
  let t = "";
  for (let n = 1; n < e.length - 1; n += 1)
    t += `${e[n]} ${(n / (e.length - 1)) * 100}%, `;
  return `${e[0]} 0%, ${t}${e[e.length - 1]} 100%`;
}
function f9(e, ...t) {
  return `linear-gradient(${e}deg, ${JO(t)})`;
}
function d9(...e) {
  return `radial-gradient(circle, ${JO(e)})`;
}
function eE(e) {
  const t = f0(e),
    n = Vs(e);
  return (r) => {
    const o = {
      from: (r == null ? void 0 : r.from) || e.defaultGradient.from,
      to: (r == null ? void 0 : r.to) || e.defaultGradient.to,
      deg: (r == null ? void 0 : r.deg) || e.defaultGradient.deg,
    };
    return `linear-gradient(${o.deg}deg, ${t(o.from, n(), !1)} 0%, ${t(
      o.to,
      n(),
      !1
    )} 100%)`;
  };
}
function d0(e) {
  if (typeof e.size == "number") return e.size;
  const t = e.sizes[e.size];
  return t !== void 0 ? t : e.size || e.sizes.md;
}
function p9(e) {
  return (t) =>
    `@media (min-width: ${d0({ size: t, sizes: e.breakpoints })}px)`;
}
function m9(e) {
  return (t) =>
    `@media (max-width: ${d0({ size: t, sizes: e.breakpoints }) - 1}px)`;
}
function h9(e) {
  return /^#?([0-9A-F]{3}){1,2}$/i.test(e);
}
function v9(e) {
  let t = e.replace("#", "");
  if (t.length === 3) {
    const s = t.split("");
    t = [s[0], s[0], s[1], s[1], s[2], s[2]].join("");
  }
  const n = parseInt(t, 16),
    r = (n >> 16) & 255,
    o = (n >> 8) & 255,
    a = n & 255;
  return { r, g: o, b: a, a: 1 };
}
function g9(e) {
  const [t, n, r, o] = e
    .replace(/[^0-9,.]/g, "")
    .split(",")
    .map(Number);
  return { r: t, g: n, b: r, a: o || 1 };
}
function p0(e) {
  return h9(e)
    ? v9(e)
    : e.startsWith("rgb")
    ? g9(e)
    : { r: 0, g: 0, b: 0, a: 1 };
}
function da(e, t) {
  if (typeof e != "string" || t > 1 || t < 0) return "rgba(0, 0, 0, 1)";
  const { r: n, g: r, b: o } = p0(e);
  return `rgba(${n}, ${r}, ${o}, ${t})`;
}
function y9(e = 0) {
  return { position: "absolute", top: e, right: e, left: e, bottom: e };
}
function _9(e, t) {
  const { r: n, g: r, b: o, a } = p0(e),
    s = 1 - t,
    u = (f) => Math.round(f * s);
  return `rgba(${u(n)}, ${u(r)}, ${u(o)}, ${a})`;
}
function w9(e, t) {
  const { r: n, g: r, b: o, a } = p0(e),
    s = (u) => Math.round(u + (255 - u) * t);
  return `rgba(${s(n)}, ${s(r)}, ${s(o)}, ${a})`;
}
function b9(e) {
  return (t) => {
    if (typeof t == "number") return t;
    const n =
      typeof e.defaultRadius == "number"
        ? e.defaultRadius
        : e.radius[e.defaultRadius] || e.defaultRadius;
    return e.radius[t] || t || n;
  };
}
function x9(e, t) {
  if (typeof e == "string" && e.includes(".")) {
    const [n, r] = e.split("."),
      o = parseInt(r, 10);
    if (n in t.colors && o >= 0 && o < 10)
      return { isSplittedColor: !0, key: n, shade: o };
  }
  return { isSplittedColor: !1 };
}
function S9(e) {
  const t = f0(e),
    n = Vs(e),
    r = eE(e);
  return ({ variant: o, color: a, gradient: s, primaryFallback: u }) => {
    const f = x9(a, e);
    switch (o) {
      case "light":
        return {
          border: "transparent",
          background: da(
            t(a, e.colorScheme === "dark" ? 8 : 0, u, !1),
            e.colorScheme === "dark" ? 0.2 : 1
          ),
          color:
            a === "dark"
              ? e.colorScheme === "dark"
                ? e.colors.dark[0]
                : e.colors.dark[9]
              : t(a, e.colorScheme === "dark" ? 2 : n("light")),
          hover: da(
            t(a, e.colorScheme === "dark" ? 7 : 1, u, !1),
            e.colorScheme === "dark" ? 0.25 : 0.65
          ),
        };
      case "subtle":
        return {
          border: "transparent",
          background: "transparent",
          color:
            a === "dark"
              ? e.colorScheme === "dark"
                ? e.colors.dark[0]
                : e.colors.dark[9]
              : t(a, e.colorScheme === "dark" ? 2 : n("light")),
          hover: da(
            t(a, e.colorScheme === "dark" ? 8 : 0, u, !1),
            e.colorScheme === "dark" ? 0.2 : 1
          ),
        };
      case "outline":
        return {
          border: t(a, e.colorScheme === "dark" ? 5 : n("light")),
          background: "transparent",
          color: t(a, e.colorScheme === "dark" ? 5 : n("light")),
          hover:
            e.colorScheme === "dark"
              ? da(t(a, 5, u, !1), 0.05)
              : da(t(a, 0, u, !1), 0.35),
        };
      case "default":
        return {
          border:
            e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[4],
          background: e.colorScheme === "dark" ? e.colors.dark[6] : e.white,
          color: e.colorScheme === "dark" ? e.white : e.black,
          hover: e.colorScheme === "dark" ? e.colors.dark[5] : e.colors.gray[0],
        };
      case "white":
        return {
          border: "transparent",
          background: e.white,
          color: t(a, n()),
          hover: null,
        };
      case "transparent":
        return {
          border: "transparent",
          color:
            a === "dark"
              ? e.colorScheme === "dark"
                ? e.colors.dark[0]
                : e.colors.dark[9]
              : t(a, e.colorScheme === "dark" ? 2 : n("light")),
          background: "transparent",
          hover: null,
        };
      case "gradient":
        return {
          background: r(s),
          color: e.white,
          border: "transparent",
          hover: null,
        };
      default: {
        const d = n(),
          m = f.isSplittedColor ? f.shade : d,
          h = f.isSplittedColor ? f.key : a;
        return {
          border: "transparent",
          background: t(h, m, u),
          color: e.white,
          hover: t(h, m === 9 ? 8 : m + 1),
        };
      }
    }
  };
}
function P9(e) {
  return (t) => {
    const n = Vs(e)(t);
    return e.colors[e.primaryColor][n];
  };
}
function O9(e) {
  return {
    "@media (hover: hover)": { "&:hover": e },
    "@media (hover: none)": { "&:active": e },
  };
}
function E9(e) {
  return () => ({
    userSelect: "none",
    color: e.colorScheme === "dark" ? e.colors.dark[3] : e.colors.gray[5],
  });
}
const dt = {
  fontStyles: a9,
  themeColor: f0,
  focusStyles: c9,
  linearGradient: f9,
  radialGradient: d9,
  smallerThan: m9,
  largerThan: p9,
  rgba: da,
  size: d0,
  cover: y9,
  darken: _9,
  lighten: w9,
  radius: b9,
  variant: S9,
  primaryShade: Vs,
  hover: O9,
  gradient: eE,
  primaryColor: P9,
  placeholderStyles: E9,
};
var $9 = Object.defineProperty,
  C9 = Object.defineProperties,
  k9 = Object.getOwnPropertyDescriptors,
  Fw = Object.getOwnPropertySymbols,
  R9 = Object.prototype.hasOwnProperty,
  N9 = Object.prototype.propertyIsEnumerable,
  Ww = (e, t, n) =>
    t in e
      ? $9(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  I9 = (e, t) => {
    for (var n in t || (t = {})) R9.call(t, n) && Ww(e, n, t[n]);
    if (Fw) for (var n of Fw(t)) N9.call(t, n) && Ww(e, n, t[n]);
    return e;
  },
  T9 = (e, t) => C9(e, k9(t));
function tE(e) {
  return T9(I9({}, e), {
    fn: {
      fontStyles: dt.fontStyles(e),
      themeColor: dt.themeColor(e),
      focusStyles: dt.focusStyles(e),
      largerThan: dt.largerThan(e),
      smallerThan: dt.smallerThan(e),
      radialGradient: dt.radialGradient,
      linearGradient: dt.linearGradient,
      gradient: dt.gradient(e),
      rgba: dt.rgba,
      size: dt.size,
      cover: dt.cover,
      lighten: dt.lighten,
      darken: dt.darken,
      primaryShade: dt.primaryShade(e),
      radius: dt.radius(e),
      variant: dt.variant(e),
      hover: dt.hover,
      primaryColor: dt.primaryColor(e),
      placeholderStyles: dt.placeholderStyles(e),
    },
  });
}
const z9 = {
    dir: "ltr",
    primaryShade: { light: 6, dark: 8 },
    focusRing: "auto",
    loader: "oval",
    dateFormat: "MMMM D, YYYY",
    colorScheme: "light",
    white: "#fff",
    black: "#000",
    defaultRadius: "sm",
    transitionTimingFunction: "ease",
    colors: i9,
    lineHeight: 1.55,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji",
    fontFamilyMonospace:
      "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, Courier New, monospace",
    primaryColor: "blue",
    respectReducedMotion: !0,
    cursorType: "default",
    defaultGradient: { from: "indigo", to: "cyan", deg: 45 },
    shadows: {
      xs: "0 1px 3px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.1)",
      sm: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 10px 15px -5px, rgba(0, 0, 0, 0.04) 0px 7px 7px -5px",
      md: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 20px 25px -5px, rgba(0, 0, 0, 0.04) 0px 10px 10px -5px",
      lg: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 28px 23px -7px, rgba(0, 0, 0, 0.04) 0px 12px 12px -7px",
      xl: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 36px 28px -7px, rgba(0, 0, 0, 0.04) 0px 17px 17px -7px",
    },
    fontSizes: { xs: 12, sm: 14, md: 16, lg: 18, xl: 20 },
    radius: { xs: 2, sm: 4, md: 8, lg: 16, xl: 32 },
    spacing: { xs: 10, sm: 12, md: 16, lg: 20, xl: 24 },
    breakpoints: { xs: 576, sm: 768, md: 992, lg: 1200, xl: 1400 },
    headings: {
      fontFamily:
        "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji",
      fontWeight: 700,
      sizes: {
        h1: { fontSize: 34, lineHeight: 1.3, fontWeight: void 0 },
        h2: { fontSize: 26, lineHeight: 1.35, fontWeight: void 0 },
        h3: { fontSize: 22, lineHeight: 1.4, fontWeight: void 0 },
        h4: { fontSize: 18, lineHeight: 1.45, fontWeight: void 0 },
        h5: { fontSize: 16, lineHeight: 1.5, fontWeight: void 0 },
        h6: { fontSize: 14, lineHeight: 1.5, fontWeight: void 0 },
      },
    },
    other: {},
    components: {},
    activeStyles: { transform: "translateY(1px)" },
    datesLocale: "en",
    globalStyles: void 0,
    focusRingStyles: {
      styles: (e) => ({
        outlineOffset: 2,
        outline: `2px solid ${
          e.colors[e.primaryColor][e.colorScheme === "dark" ? 7 : 5]
        }`,
      }),
      resetStyles: () => ({ outline: "none" }),
      inputStyles: (e) => ({
        outline: "none",
        borderColor:
          e.colors[e.primaryColor][
            typeof e.primaryShade == "object"
              ? e.primaryShade[e.colorScheme]
              : e.primaryShade
          ],
      }),
    },
  },
  m0 = tE(z9);
function A9(e) {
  if (e.sheet) return e.sheet;
  for (var t = 0; t < document.styleSheets.length; t++)
    if (document.styleSheets[t].ownerNode === e) return document.styleSheets[t];
}
function L9(e) {
  var t = document.createElement("style");
  return (
    t.setAttribute("data-emotion", e.key),
    e.nonce !== void 0 && t.setAttribute("nonce", e.nonce),
    t.appendChild(document.createTextNode("")),
    t.setAttribute("data-s", ""),
    t
  );
}
var D9 = (function () {
    function e(n) {
      var r = this;
      (this._insertTag = function (o) {
        var a;
        r.tags.length === 0
          ? r.insertionPoint
            ? (a = r.insertionPoint.nextSibling)
            : r.prepend
            ? (a = r.container.firstChild)
            : (a = r.before)
          : (a = r.tags[r.tags.length - 1].nextSibling),
          r.container.insertBefore(o, a),
          r.tags.push(o);
      }),
        (this.isSpeedy = n.speedy === void 0 ? !0 : n.speedy),
        (this.tags = []),
        (this.ctr = 0),
        (this.nonce = n.nonce),
        (this.key = n.key),
        (this.container = n.container),
        (this.prepend = n.prepend),
        (this.insertionPoint = n.insertionPoint),
        (this.before = null);
    }
    var t = e.prototype;
    return (
      (t.hydrate = function (r) {
        r.forEach(this._insertTag);
      }),
      (t.insert = function (r) {
        this.ctr % (this.isSpeedy ? 65e3 : 1) === 0 &&
          this._insertTag(L9(this));
        var o = this.tags[this.tags.length - 1];
        if (this.isSpeedy) {
          var a = A9(o);
          try {
            a.insertRule(r, a.cssRules.length);
          } catch {}
        } else o.appendChild(document.createTextNode(r));
        this.ctr++;
      }),
      (t.flush = function () {
        this.tags.forEach(function (r) {
          return r.parentNode && r.parentNode.removeChild(r);
        }),
          (this.tags = []),
          (this.ctr = 0);
      }),
      e
    );
  })(),
  zt = "-ms-",
  Ef = "-moz-",
  Ee = "-webkit-",
  nE = "comm",
  h0 = "rule",
  v0 = "decl",
  M9 = "@import",
  rE = "@keyframes",
  j9 = "@layer",
  F9 = Math.abs,
  Zd = String.fromCharCode,
  W9 = Object.assign;
function B9(e, t) {
  return Pt(e, 0) ^ 45
    ? (((((((t << 2) ^ Pt(e, 0)) << 2) ^ Pt(e, 1)) << 2) ^ Pt(e, 2)) << 2) ^
        Pt(e, 3)
    : 0;
}
function oE(e) {
  return e.trim();
}
function U9(e, t) {
  return (e = t.exec(e)) ? e[0] : e;
}
function $e(e, t, n) {
  return e.replace(t, n);
}
function zv(e, t) {
  return e.indexOf(t);
}
function Pt(e, t) {
  return e.charCodeAt(t) | 0;
}
function ks(e, t, n) {
  return e.slice(t, n);
}
function yr(e) {
  return e.length;
}
function g0(e) {
  return e.length;
}
function uc(e, t) {
  return t.push(e), e;
}
function H9(e, t) {
  return e.map(t).join("");
}
var Jd = 1,
  Ha = 1,
  iE = 0,
  on = 0,
  lt = 0,
  rl = "";
function ep(e, t, n, r, o, a, s) {
  return {
    value: e,
    root: t,
    parent: n,
    type: r,
    props: o,
    children: a,
    line: Jd,
    column: Ha,
    length: s,
    return: "",
  };
}
function Ll(e, t) {
  return W9(ep("", null, null, "", null, null, 0), e, { length: -e.length }, t);
}
function V9() {
  return lt;
}
function Y9() {
  return (
    (lt = on > 0 ? Pt(rl, --on) : 0), Ha--, lt === 10 && ((Ha = 1), Jd--), lt
  );
}
function wn() {
  return (
    (lt = on < iE ? Pt(rl, on++) : 0), Ha++, lt === 10 && ((Ha = 1), Jd++), lt
  );
}
function Or() {
  return Pt(rl, on);
}
function Hc() {
  return on;
}
function Ys(e, t) {
  return ks(rl, e, t);
}
function Rs(e) {
  switch (e) {
    case 0:
    case 9:
    case 10:
    case 13:
    case 32:
      return 5;
    case 33:
    case 43:
    case 44:
    case 47:
    case 62:
    case 64:
    case 126:
    case 59:
    case 123:
    case 125:
      return 4;
    case 58:
      return 3;
    case 34:
    case 39:
    case 40:
    case 91:
      return 2;
    case 41:
    case 93:
      return 1;
  }
  return 0;
}
function aE(e) {
  return (Jd = Ha = 1), (iE = yr((rl = e))), (on = 0), [];
}
function lE(e) {
  return (rl = ""), e;
}
function Vc(e) {
  return oE(Ys(on - 1, Av(e === 91 ? e + 2 : e === 40 ? e + 1 : e)));
}
function G9(e) {
  for (; (lt = Or()) && lt < 33; ) wn();
  return Rs(e) > 2 || Rs(lt) > 3 ? "" : " ";
}
function X9(e, t) {
  for (
    ;
    --t &&
    wn() &&
    !(lt < 48 || lt > 102 || (lt > 57 && lt < 65) || (lt > 70 && lt < 97));

  );
  return Ys(e, Hc() + (t < 6 && Or() == 32 && wn() == 32));
}
function Av(e) {
  for (; wn(); )
    switch (lt) {
      case e:
        return on;
      case 34:
      case 39:
        e !== 34 && e !== 39 && Av(lt);
        break;
      case 40:
        e === 41 && Av(e);
        break;
      case 92:
        wn();
        break;
    }
  return on;
}
function K9(e, t) {
  for (; wn() && e + lt !== 47 + 10; )
    if (e + lt === 42 + 42 && Or() === 47) break;
  return "/*" + Ys(t, on - 1) + "*" + Zd(e === 47 ? e : wn());
}
function Q9(e) {
  for (; !Rs(Or()); ) wn();
  return Ys(e, on);
}
function q9(e) {
  return lE(Yc("", null, null, null, [""], (e = aE(e)), 0, [0], e));
}
function Yc(e, t, n, r, o, a, s, u, f) {
  for (
    var d = 0,
      m = 0,
      h = s,
      v = 0,
      b = 0,
      O = 0,
      E = 1,
      $ = 1,
      _ = 1,
      w = 0,
      P = "",
      k = o,
      I = a,
      z = r,
      A = P;
    $;

  )
    switch (((O = w), (w = wn()))) {
      case 40:
        if (O != 108 && Pt(A, h - 1) == 58) {
          zv((A += $e(Vc(w), "&", "&\f")), "&\f") != -1 && (_ = -1);
          break;
        }
      case 34:
      case 39:
      case 91:
        A += Vc(w);
        break;
      case 9:
      case 10:
      case 13:
      case 32:
        A += G9(O);
        break;
      case 92:
        A += X9(Hc() - 1, 7);
        continue;
      case 47:
        switch (Or()) {
          case 42:
          case 47:
            uc(Z9(K9(wn(), Hc()), t, n), f);
            break;
          default:
            A += "/";
        }
        break;
      case 123 * E:
        u[d++] = yr(A) * _;
      case 125 * E:
      case 59:
      case 0:
        switch (w) {
          case 0:
          case 125:
            $ = 0;
          case 59 + m:
            _ == -1 && (A = $e(A, /\f/g, "")),
              b > 0 &&
                yr(A) - h &&
                uc(
                  b > 32
                    ? Uw(A + ";", r, n, h - 1)
                    : Uw($e(A, " ", "") + ";", r, n, h - 2),
                  f
                );
            break;
          case 59:
            A += ";";
          default:
            if (
              (uc((z = Bw(A, t, n, d, m, o, u, P, (k = []), (I = []), h)), a),
              w === 123)
            )
              if (m === 0) Yc(A, t, z, z, k, a, h, u, I);
              else
                switch (v === 99 && Pt(A, 3) === 110 ? 100 : v) {
                  case 100:
                  case 108:
                  case 109:
                  case 115:
                    Yc(
                      e,
                      z,
                      z,
                      r && uc(Bw(e, z, z, 0, 0, o, u, P, o, (k = []), h), I),
                      o,
                      I,
                      h,
                      u,
                      r ? k : I
                    );
                    break;
                  default:
                    Yc(A, z, z, z, [""], I, 0, u, I);
                }
        }
        (d = m = b = 0), (E = _ = 1), (P = A = ""), (h = s);
        break;
      case 58:
        (h = 1 + yr(A)), (b = O);
      default:
        if (E < 1) {
          if (w == 123) --E;
          else if (w == 125 && E++ == 0 && Y9() == 125) continue;
        }
        switch (((A += Zd(w)), w * E)) {
          case 38:
            _ = m > 0 ? 1 : ((A += "\f"), -1);
            break;
          case 44:
            (u[d++] = (yr(A) - 1) * _), (_ = 1);
            break;
          case 64:
            Or() === 45 && (A += Vc(wn())),
              (v = Or()),
              (m = h = yr((P = A += Q9(Hc())))),
              w++;
            break;
          case 45:
            O === 45 && yr(A) == 2 && (E = 0);
        }
    }
  return a;
}
function Bw(e, t, n, r, o, a, s, u, f, d, m) {
  for (
    var h = o - 1, v = o === 0 ? a : [""], b = g0(v), O = 0, E = 0, $ = 0;
    O < r;
    ++O
  )
    for (var _ = 0, w = ks(e, h + 1, (h = F9((E = s[O])))), P = e; _ < b; ++_)
      (P = oE(E > 0 ? v[_] + " " + w : $e(w, /&\f/g, v[_]))) && (f[$++] = P);
  return ep(e, t, n, o === 0 ? h0 : u, f, d, m);
}
function Z9(e, t, n) {
  return ep(e, t, n, nE, Zd(V9()), ks(e, 2, -2), 0);
}
function Uw(e, t, n, r) {
  return ep(e, t, n, v0, ks(e, 0, r), ks(e, r + 1, -1), r);
}
function za(e, t) {
  for (var n = "", r = g0(e), o = 0; o < r; o++) n += t(e[o], o, e, t) || "";
  return n;
}
function J9(e, t, n, r) {
  switch (e.type) {
    case j9:
      if (e.children.length) break;
    case M9:
    case v0:
      return (e.return = e.return || e.value);
    case nE:
      return "";
    case rE:
      return (e.return = e.value + "{" + za(e.children, r) + "}");
    case h0:
      e.value = e.props.join(",");
  }
  return yr((n = za(e.children, r)))
    ? (e.return = e.value + "{" + n + "}")
    : "";
}
function e7(e) {
  var t = g0(e);
  return function (n, r, o, a) {
    for (var s = "", u = 0; u < t; u++) s += e[u](n, r, o, a) || "";
    return s;
  };
}
function t7(e) {
  return function (t) {
    t.root || ((t = t.return) && e(t));
  };
}
var Hw = function (t) {
  var n = new WeakMap();
  return function (r) {
    if (n.has(r)) return n.get(r);
    var o = t(r);
    return n.set(r, o), o;
  };
};
function n7(e) {
  var t = Object.create(null);
  return function (n) {
    return t[n] === void 0 && (t[n] = e(n)), t[n];
  };
}
var r7 = function (t, n, r) {
    for (
      var o = 0, a = 0;
      (o = a), (a = Or()), o === 38 && a === 12 && (n[r] = 1), !Rs(a);

    )
      wn();
    return Ys(t, on);
  },
  o7 = function (t, n) {
    var r = -1,
      o = 44;
    do
      switch (Rs(o)) {
        case 0:
          o === 38 && Or() === 12 && (n[r] = 1), (t[r] += r7(on - 1, n, r));
          break;
        case 2:
          t[r] += Vc(o);
          break;
        case 4:
          if (o === 44) {
            (t[++r] = Or() === 58 ? "&\f" : ""), (n[r] = t[r].length);
            break;
          }
        default:
          t[r] += Zd(o);
      }
    while ((o = wn()));
    return t;
  },
  i7 = function (t, n) {
    return lE(o7(aE(t), n));
  },
  Vw = new WeakMap(),
  a7 = function (t) {
    if (!(t.type !== "rule" || !t.parent || t.length < 1)) {
      for (
        var n = t.value,
          r = t.parent,
          o = t.column === r.column && t.line === r.line;
        r.type !== "rule";

      )
        if (((r = r.parent), !r)) return;
      if (
        !(t.props.length === 1 && n.charCodeAt(0) !== 58 && !Vw.get(r)) &&
        !o
      ) {
        Vw.set(t, !0);
        for (
          var a = [], s = i7(n, a), u = r.props, f = 0, d = 0;
          f < s.length;
          f++
        )
          for (var m = 0; m < u.length; m++, d++)
            t.props[d] = a[f] ? s[f].replace(/&\f/g, u[m]) : u[m] + " " + s[f];
      }
    }
  },
  l7 = function (t) {
    if (t.type === "decl") {
      var n = t.value;
      n.charCodeAt(0) === 108 &&
        n.charCodeAt(2) === 98 &&
        ((t.return = ""), (t.value = ""));
    }
  };
function sE(e, t) {
  switch (B9(e, t)) {
    case 5103:
      return Ee + "print-" + e + e;
    case 5737:
    case 4201:
    case 3177:
    case 3433:
    case 1641:
    case 4457:
    case 2921:
    case 5572:
    case 6356:
    case 5844:
    case 3191:
    case 6645:
    case 3005:
    case 6391:
    case 5879:
    case 5623:
    case 6135:
    case 4599:
    case 4855:
    case 4215:
    case 6389:
    case 5109:
    case 5365:
    case 5621:
    case 3829:
      return Ee + e + e;
    case 5349:
    case 4246:
    case 4810:
    case 6968:
    case 2756:
      return Ee + e + Ef + e + zt + e + e;
    case 6828:
    case 4268:
      return Ee + e + zt + e + e;
    case 6165:
      return Ee + e + zt + "flex-" + e + e;
    case 5187:
      return (
        Ee + e + $e(e, /(\w+).+(:[^]+)/, Ee + "box-$1$2" + zt + "flex-$1$2") + e
      );
    case 5443:
      return Ee + e + zt + "flex-item-" + $e(e, /flex-|-self/, "") + e;
    case 4675:
      return (
        Ee +
        e +
        zt +
        "flex-line-pack" +
        $e(e, /align-content|flex-|-self/, "") +
        e
      );
    case 5548:
      return Ee + e + zt + $e(e, "shrink", "negative") + e;
    case 5292:
      return Ee + e + zt + $e(e, "basis", "preferred-size") + e;
    case 6060:
      return (
        Ee +
        "box-" +
        $e(e, "-grow", "") +
        Ee +
        e +
        zt +
        $e(e, "grow", "positive") +
        e
      );
    case 4554:
      return Ee + $e(e, /([^-])(transform)/g, "$1" + Ee + "$2") + e;
    case 6187:
      return (
        $e(
          $e($e(e, /(zoom-|grab)/, Ee + "$1"), /(image-set)/, Ee + "$1"),
          e,
          ""
        ) + e
      );
    case 5495:
    case 3959:
      return $e(e, /(image-set\([^]*)/, Ee + "$1$`$1");
    case 4968:
      return (
        $e(
          $e(e, /(.+:)(flex-)?(.*)/, Ee + "box-pack:$3" + zt + "flex-pack:$3"),
          /s.+-b[^;]+/,
          "justify"
        ) +
        Ee +
        e +
        e
      );
    case 4095:
    case 3583:
    case 4068:
    case 2532:
      return $e(e, /(.+)-inline(.+)/, Ee + "$1$2") + e;
    case 8116:
    case 7059:
    case 5753:
    case 5535:
    case 5445:
    case 5701:
    case 4933:
    case 4677:
    case 5533:
    case 5789:
    case 5021:
    case 4765:
      if (yr(e) - 1 - t > 6)
        switch (Pt(e, t + 1)) {
          case 109:
            if (Pt(e, t + 4) !== 45) break;
          case 102:
            return (
              $e(
                e,
                /(.+:)(.+)-([^]+)/,
                "$1" +
                  Ee +
                  "$2-$3$1" +
                  Ef +
                  (Pt(e, t + 3) == 108 ? "$3" : "$2-$3")
              ) + e
            );
          case 115:
            return ~zv(e, "stretch")
              ? sE($e(e, "stretch", "fill-available"), t) + e
              : e;
        }
      break;
    case 4949:
      if (Pt(e, t + 1) !== 115) break;
    case 6444:
      switch (Pt(e, yr(e) - 3 - (~zv(e, "!important") && 10))) {
        case 107:
          return $e(e, ":", ":" + Ee) + e;
        case 101:
          return (
            $e(
              e,
              /(.+:)([^;!]+)(;|!.+)?/,
              "$1" +
                Ee +
                (Pt(e, 14) === 45 ? "inline-" : "") +
                "box$3$1" +
                Ee +
                "$2$3$1" +
                zt +
                "$2box$3"
            ) + e
          );
      }
      break;
    case 5936:
      switch (Pt(e, t + 11)) {
        case 114:
          return Ee + e + zt + $e(e, /[svh]\w+-[tblr]{2}/, "tb") + e;
        case 108:
          return Ee + e + zt + $e(e, /[svh]\w+-[tblr]{2}/, "tb-rl") + e;
        case 45:
          return Ee + e + zt + $e(e, /[svh]\w+-[tblr]{2}/, "lr") + e;
      }
      return Ee + e + zt + e + e;
  }
  return e;
}
var s7 = function (t, n, r, o) {
    if (t.length > -1 && !t.return)
      switch (t.type) {
        case v0:
          t.return = sE(t.value, t.length);
          break;
        case rE:
          return za([Ll(t, { value: $e(t.value, "@", "@" + Ee) })], o);
        case h0:
          if (t.length)
            return H9(t.props, function (a) {
              switch (U9(a, /(::plac\w+|:read-\w+)/)) {
                case ":read-only":
                case ":read-write":
                  return za(
                    [Ll(t, { props: [$e(a, /:(read-\w+)/, ":" + Ef + "$1")] })],
                    o
                  );
                case "::placeholder":
                  return za(
                    [
                      Ll(t, {
                        props: [$e(a, /:(plac\w+)/, ":" + Ee + "input-$1")],
                      }),
                      Ll(t, { props: [$e(a, /:(plac\w+)/, ":" + Ef + "$1")] }),
                      Ll(t, { props: [$e(a, /:(plac\w+)/, zt + "input-$1")] }),
                    ],
                    o
                  );
              }
              return "";
            });
      }
  },
  u7 = [s7],
  uE = function (t) {
    var n = t.key;
    if (n === "css") {
      var r = document.querySelectorAll("style[data-emotion]:not([data-s])");
      Array.prototype.forEach.call(r, function (E) {
        var $ = E.getAttribute("data-emotion");
        $.indexOf(" ") !== -1 &&
          (document.head.appendChild(E), E.setAttribute("data-s", ""));
      });
    }
    var o = t.stylisPlugins || u7,
      a = {},
      s,
      u = [];
    (s = t.container || document.head),
      Array.prototype.forEach.call(
        document.querySelectorAll('style[data-emotion^="' + n + ' "]'),
        function (E) {
          for (
            var $ = E.getAttribute("data-emotion").split(" "), _ = 1;
            _ < $.length;
            _++
          )
            a[$[_]] = !0;
          u.push(E);
        }
      );
    var f,
      d = [a7, l7];
    {
      var m,
        h = [
          J9,
          t7(function (E) {
            m.insert(E);
          }),
        ],
        v = e7(d.concat(o, h)),
        b = function ($) {
          return za(q9($), v);
        };
      f = function ($, _, w, P) {
        (m = w),
          b($ ? $ + "{" + _.styles + "}" : _.styles),
          P && (O.inserted[_.name] = !0);
      };
    }
    var O = {
      key: n,
      sheet: new D9({
        key: n,
        container: s,
        nonce: t.nonce,
        speedy: t.speedy,
        prepend: t.prepend,
        insertionPoint: t.insertionPoint,
      }),
      nonce: t.nonce,
      inserted: a,
      registered: {},
      insert: f,
    };
    return O.sheet.hydrate(u), O;
  },
  cE = { exports: {} },
  Te = {};
/** @license React v16.13.1
 * react-is.production.min.js
 *
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */ var wt = typeof Symbol == "function" && Symbol.for,
  y0 = wt ? Symbol.for("react.element") : 60103,
  _0 = wt ? Symbol.for("react.portal") : 60106,
  tp = wt ? Symbol.for("react.fragment") : 60107,
  np = wt ? Symbol.for("react.strict_mode") : 60108,
  rp = wt ? Symbol.for("react.profiler") : 60114,
  op = wt ? Symbol.for("react.provider") : 60109,
  ip = wt ? Symbol.for("react.context") : 60110,
  w0 = wt ? Symbol.for("react.async_mode") : 60111,
  ap = wt ? Symbol.for("react.concurrent_mode") : 60111,
  lp = wt ? Symbol.for("react.forward_ref") : 60112,
  sp = wt ? Symbol.for("react.suspense") : 60113,
  c7 = wt ? Symbol.for("react.suspense_list") : 60120,
  up = wt ? Symbol.for("react.memo") : 60115,
  cp = wt ? Symbol.for("react.lazy") : 60116,
  f7 = wt ? Symbol.for("react.block") : 60121,
  d7 = wt ? Symbol.for("react.fundamental") : 60117,
  p7 = wt ? Symbol.for("react.responder") : 60118,
  m7 = wt ? Symbol.for("react.scope") : 60119;
function Pn(e) {
  if (typeof e == "object" && e !== null) {
    var t = e.$$typeof;
    switch (t) {
      case y0:
        switch (((e = e.type), e)) {
          case w0:
          case ap:
          case tp:
          case rp:
          case np:
          case sp:
            return e;
          default:
            switch (((e = e && e.$$typeof), e)) {
              case ip:
              case lp:
              case cp:
              case up:
              case op:
                return e;
              default:
                return t;
            }
        }
      case _0:
        return t;
    }
  }
}
function fE(e) {
  return Pn(e) === ap;
}
Te.AsyncMode = w0;
Te.ConcurrentMode = ap;
Te.ContextConsumer = ip;
Te.ContextProvider = op;
Te.Element = y0;
Te.ForwardRef = lp;
Te.Fragment = tp;
Te.Lazy = cp;
Te.Memo = up;
Te.Portal = _0;
Te.Profiler = rp;
Te.StrictMode = np;
Te.Suspense = sp;
Te.isAsyncMode = function (e) {
  return fE(e) || Pn(e) === w0;
};
Te.isConcurrentMode = fE;
Te.isContextConsumer = function (e) {
  return Pn(e) === ip;
};
Te.isContextProvider = function (e) {
  return Pn(e) === op;
};
Te.isElement = function (e) {
  return typeof e == "object" && e !== null && e.$$typeof === y0;
};
Te.isForwardRef = function (e) {
  return Pn(e) === lp;
};
Te.isFragment = function (e) {
  return Pn(e) === tp;
};
Te.isLazy = function (e) {
  return Pn(e) === cp;
};
Te.isMemo = function (e) {
  return Pn(e) === up;
};
Te.isPortal = function (e) {
  return Pn(e) === _0;
};
Te.isProfiler = function (e) {
  return Pn(e) === rp;
};
Te.isStrictMode = function (e) {
  return Pn(e) === np;
};
Te.isSuspense = function (e) {
  return Pn(e) === sp;
};
Te.isValidElementType = function (e) {
  return (
    typeof e == "string" ||
    typeof e == "function" ||
    e === tp ||
    e === ap ||
    e === rp ||
    e === np ||
    e === sp ||
    e === c7 ||
    (typeof e == "object" &&
      e !== null &&
      (e.$$typeof === cp ||
        e.$$typeof === up ||
        e.$$typeof === op ||
        e.$$typeof === ip ||
        e.$$typeof === lp ||
        e.$$typeof === d7 ||
        e.$$typeof === p7 ||
        e.$$typeof === m7 ||
        e.$$typeof === f7))
  );
};
Te.typeOf = Pn;
cE.exports = Te;
var h7 = cE.exports,
  dE = h7,
  v7 = {
    $$typeof: !0,
    render: !0,
    defaultProps: !0,
    displayName: !0,
    propTypes: !0,
  },
  g7 = {
    $$typeof: !0,
    compare: !0,
    defaultProps: !0,
    displayName: !0,
    propTypes: !0,
    type: !0,
  },
  pE = {};
pE[dE.ForwardRef] = v7;
pE[dE.Memo] = g7;
var y7 = !0;
function _7(e, t, n) {
  var r = "";
  return (
    n.split(" ").forEach(function (o) {
      e[o] !== void 0 ? t.push(e[o] + ";") : (r += o + " ");
    }),
    r
  );
}
var w7 = function (t, n, r) {
    var o = t.key + "-" + n.name;
    (r === !1 || y7 === !1) &&
      t.registered[o] === void 0 &&
      (t.registered[o] = n.styles);
  },
  mE = function (t, n, r) {
    w7(t, n, r);
    var o = t.key + "-" + n.name;
    if (t.inserted[n.name] === void 0) {
      var a = n;
      do t.insert(n === a ? "." + o : "", a, t.sheet, !0), (a = a.next);
      while (a !== void 0);
    }
  };
function b7(e) {
  for (var t = 0, n, r = 0, o = e.length; o >= 4; ++r, o -= 4)
    (n =
      (e.charCodeAt(r) & 255) |
      ((e.charCodeAt(++r) & 255) << 8) |
      ((e.charCodeAt(++r) & 255) << 16) |
      ((e.charCodeAt(++r) & 255) << 24)),
      (n = (n & 65535) * 1540483477 + (((n >>> 16) * 59797) << 16)),
      (n ^= n >>> 24),
      (t =
        ((n & 65535) * 1540483477 + (((n >>> 16) * 59797) << 16)) ^
        ((t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16)));
  switch (o) {
    case 3:
      t ^= (e.charCodeAt(r + 2) & 255) << 16;
    case 2:
      t ^= (e.charCodeAt(r + 1) & 255) << 8;
    case 1:
      (t ^= e.charCodeAt(r) & 255),
        (t = (t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16));
  }
  return (
    (t ^= t >>> 13),
    (t = (t & 65535) * 1540483477 + (((t >>> 16) * 59797) << 16)),
    ((t ^ (t >>> 15)) >>> 0).toString(36)
  );
}
var x7 = {
    animationIterationCount: 1,
    aspectRatio: 1,
    borderImageOutset: 1,
    borderImageSlice: 1,
    borderImageWidth: 1,
    boxFlex: 1,
    boxFlexGroup: 1,
    boxOrdinalGroup: 1,
    columnCount: 1,
    columns: 1,
    flex: 1,
    flexGrow: 1,
    flexPositive: 1,
    flexShrink: 1,
    flexNegative: 1,
    flexOrder: 1,
    gridRow: 1,
    gridRowEnd: 1,
    gridRowSpan: 1,
    gridRowStart: 1,
    gridColumn: 1,
    gridColumnEnd: 1,
    gridColumnSpan: 1,
    gridColumnStart: 1,
    msGridRow: 1,
    msGridRowSpan: 1,
    msGridColumn: 1,
    msGridColumnSpan: 1,
    fontWeight: 1,
    lineHeight: 1,
    opacity: 1,
    order: 1,
    orphans: 1,
    tabSize: 1,
    widows: 1,
    zIndex: 1,
    zoom: 1,
    WebkitLineClamp: 1,
    fillOpacity: 1,
    floodOpacity: 1,
    stopOpacity: 1,
    strokeDasharray: 1,
    strokeDashoffset: 1,
    strokeMiterlimit: 1,
    strokeOpacity: 1,
    strokeWidth: 1,
  },
  S7 = /[A-Z]|^ms/g,
  P7 = /_EMO_([^_]+?)_([^]*?)_EMO_/g,
  hE = function (t) {
    return t.charCodeAt(1) === 45;
  },
  Yw = function (t) {
    return t != null && typeof t != "boolean";
  },
  hh = n7(function (e) {
    return hE(e) ? e : e.replace(S7, "-$&").toLowerCase();
  }),
  Gw = function (t, n) {
    switch (t) {
      case "animation":
      case "animationName":
        if (typeof n == "string")
          return n.replace(P7, function (r, o, a) {
            return (_r = { name: o, styles: a, next: _r }), o;
          });
    }
    return x7[t] !== 1 && !hE(t) && typeof n == "number" && n !== 0
      ? n + "px"
      : n;
  };
function Ns(e, t, n) {
  if (n == null) return "";
  if (n.__emotion_styles !== void 0) return n;
  switch (typeof n) {
    case "boolean":
      return "";
    case "object": {
      if (n.anim === 1)
        return (_r = { name: n.name, styles: n.styles, next: _r }), n.name;
      if (n.styles !== void 0) {
        var r = n.next;
        if (r !== void 0)
          for (; r !== void 0; )
            (_r = { name: r.name, styles: r.styles, next: _r }), (r = r.next);
        var o = n.styles + ";";
        return o;
      }
      return O7(e, t, n);
    }
    case "function": {
      if (e !== void 0) {
        var a = _r,
          s = n(e);
        return (_r = a), Ns(e, t, s);
      }
      break;
    }
  }
  if (t == null) return n;
  var u = t[n];
  return u !== void 0 ? u : n;
}
function O7(e, t, n) {
  var r = "";
  if (Array.isArray(n))
    for (var o = 0; o < n.length; o++) r += Ns(e, t, n[o]) + ";";
  else
    for (var a in n) {
      var s = n[a];
      if (typeof s != "object")
        t != null && t[s] !== void 0
          ? (r += a + "{" + t[s] + "}")
          : Yw(s) && (r += hh(a) + ":" + Gw(a, s) + ";");
      else if (
        Array.isArray(s) &&
        typeof s[0] == "string" &&
        (t == null || t[s[0]] === void 0)
      )
        for (var u = 0; u < s.length; u++)
          Yw(s[u]) && (r += hh(a) + ":" + Gw(a, s[u]) + ";");
      else {
        var f = Ns(e, t, s);
        switch (a) {
          case "animation":
          case "animationName": {
            r += hh(a) + ":" + f + ";";
            break;
          }
          default:
            r += a + "{" + f + "}";
        }
      }
    }
  return r;
}
var Xw = /label:\s*([^\s;\n{]+)\s*(;|$)/g,
  _r,
  b0 = function (t, n, r) {
    if (
      t.length === 1 &&
      typeof t[0] == "object" &&
      t[0] !== null &&
      t[0].styles !== void 0
    )
      return t[0];
    var o = !0,
      a = "";
    _r = void 0;
    var s = t[0];
    s == null || s.raw === void 0
      ? ((o = !1), (a += Ns(r, n, s)))
      : (a += s[0]);
    for (var u = 1; u < t.length; u++) (a += Ns(r, n, t[u])), o && (a += s[u]);
    Xw.lastIndex = 0;
    for (var f = "", d; (d = Xw.exec(a)) !== null; ) f += "-" + d[1];
    var m = b7(a) + f;
    return { name: m, styles: a, next: _r };
  },
  E7 = qc["useInsertionEffect"] ? qc["useInsertionEffect"] : !1,
  Kw = E7 || y.useLayoutEffect,
  vE = y.createContext(typeof HTMLElement < "u" ? uE({ key: "css" }) : null);
vE.Provider;
var $7 = function (t) {
    return y.forwardRef(function (n, r) {
      var o = y.useContext(vE);
      return t(n, o, r);
    });
  },
  Lv = y.createContext({}),
  C7 = function (t, n) {
    if (typeof n == "function") {
      var r = n(t);
      return r;
    }
    return je({}, t, n);
  },
  k7 = Hw(function (e) {
    return Hw(function (t) {
      return C7(e, t);
    });
  }),
  R7 = function (t) {
    var n = y.useContext(Lv);
    return (
      t.theme !== n && (n = k7(n)(t.theme)),
      y.createElement(Lv.Provider, { value: n }, t.children)
    );
  },
  Gs = $7(function (e, t) {
    var n = e.styles,
      r = b0([n], void 0, y.useContext(Lv)),
      o = y.useRef();
    return (
      Kw(
        function () {
          var a = t.key + "-global",
            s = new t.sheet.constructor({
              key: a,
              nonce: t.sheet.nonce,
              container: t.sheet.container,
              speedy: t.sheet.isSpeedy,
            }),
            u = !1,
            f = document.querySelector(
              'style[data-emotion="' + a + " " + r.name + '"]'
            );
          return (
            t.sheet.tags.length && (s.before = t.sheet.tags[0]),
            f !== null &&
              ((u = !0), f.setAttribute("data-emotion", a), s.hydrate([f])),
            (o.current = [s, u]),
            function () {
              s.flush();
            }
          );
        },
        [t]
      ),
      Kw(
        function () {
          var a = o.current,
            s = a[0],
            u = a[1];
          if (u) {
            a[1] = !1;
            return;
          }
          if ((r.next !== void 0 && mE(t, r.next, !0), s.tags.length)) {
            var f = s.tags[s.tags.length - 1].nextElementSibling;
            (s.before = f), s.flush();
          }
          t.insert("", r, s, !1);
        },
        [t, r.name]
      ),
      null
    );
  });
function N7() {
  for (var e = arguments.length, t = new Array(e), n = 0; n < e; n++)
    t[n] = arguments[n];
  return b0(t);
}
var I7 = Object.defineProperty,
  T7 = Object.defineProperties,
  z7 = Object.getOwnPropertyDescriptors,
  Qw = Object.getOwnPropertySymbols,
  A7 = Object.prototype.hasOwnProperty,
  L7 = Object.prototype.propertyIsEnumerable,
  qw = (e, t, n) =>
    t in e
      ? I7(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  D7 = (e, t) => {
    for (var n in t || (t = {})) A7.call(t, n) && qw(e, n, t[n]);
    if (Qw) for (var n of Qw(t)) L7.call(t, n) && qw(e, n, t[n]);
    return e;
  },
  M7 = (e, t) => T7(e, z7(t));
function j7({ theme: e }) {
  return R.createElement(Gs, {
    styles: {
      "*, *::before, *::after": { boxSizing: "border-box" },
      html: { colorScheme: e.colorScheme === "dark" ? "dark" : "light" },
      body: M7(D7({}, e.fn.fontStyles()), {
        backgroundColor: e.colorScheme === "dark" ? e.colors.dark[7] : e.white,
        color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
        lineHeight: e.lineHeight,
        fontSize: e.fontSizes.md,
        WebkitFontSmoothing: "antialiased",
        MozOsxFontSmoothing: "grayscale",
      }),
    },
  });
}
function cc(e, t, n) {
  Object.keys(t).forEach((r) => {
    e[`--mantine-${n}-${r}`] = typeof t[r] == "number" ? `${t[r]}px` : t[r];
  });
}
function F7({ theme: e }) {
  const t = {
    "--mantine-color-white": e.white,
    "--mantine-color-black": e.black,
    "--mantine-transition-timing-function": e.transitionTimingFunction,
    "--mantine-line-height": `${e.lineHeight}`,
    "--mantine-font-family": e.fontFamily,
    "--mantine-font-family-monospace": e.fontFamilyMonospace,
    "--mantine-font-family-headings": e.headings.fontFamily,
    "--mantine-heading-font-weight": `${e.headings.fontWeight}`,
  };
  cc(t, e.shadows, "shadow"),
    cc(t, e.fontSizes, "font-size"),
    cc(t, e.radius, "radius"),
    cc(t, e.spacing, "spacing"),
    Object.keys(e.colors).forEach((r) => {
      e.colors[r].forEach((o, a) => {
        t[`--mantine-color-${r}-${a}`] = o;
      });
    });
  const n = e.headings.sizes;
  return (
    Object.keys(n).forEach((r) => {
      (t[`--mantine-${r}-font-size`] = `${n[r].fontSize}px`),
        (t[`--mantine-${r}-line-height`] = `${n[r].lineHeight}`);
    }),
    R.createElement(Gs, { styles: { ":root": t } })
  );
}
var W7 = Object.defineProperty,
  B7 = Object.defineProperties,
  U7 = Object.getOwnPropertyDescriptors,
  Zw = Object.getOwnPropertySymbols,
  H7 = Object.prototype.hasOwnProperty,
  V7 = Object.prototype.propertyIsEnumerable,
  Jw = (e, t, n) =>
    t in e
      ? W7(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  di = (e, t) => {
    for (var n in t || (t = {})) H7.call(t, n) && Jw(e, n, t[n]);
    if (Zw) for (var n of Zw(t)) V7.call(t, n) && Jw(e, n, t[n]);
    return e;
  },
  eb = (e, t) => B7(e, U7(t));
function Y7(e, t) {
  if (!t) return e;
  const n = Object.keys(e).reduce((r, o) => {
    if (o === "headings" && t.headings) {
      const a = t.headings.sizes
        ? Object.keys(e.headings.sizes).reduce(
            (s, u) => (
              (s[u] = di(di({}, e.headings.sizes[u]), t.headings.sizes[u])), s
            ),
            {}
          )
        : e.headings.sizes;
      return eb(di({}, r), {
        headings: eb(di(di({}, e.headings), t.headings), { sizes: a }),
      });
    }
    return (
      (r[o] =
        typeof t[o] == "object"
          ? di(di({}, e[o]), t[o])
          : typeof t[o] == "number" ||
            typeof t[o] == "boolean" ||
            typeof t[o] == "function"
          ? t[o]
          : t[o] || e[o]),
      r
    );
  }, {});
  if (!(n.primaryColor in n.colors))
    throw new Error(
      "MantineProvider: Invalid theme.primaryColor, it accepts only key of theme.colors, learn more – https://mantine.dev/theming/colors/#primary-color"
    );
  return n;
}
function G7(e, t) {
  return tE(Y7(e, t));
}
function gE(e) {
  return Object.keys(e).reduce(
    (t, n) => (e[n] !== void 0 && (t[n] = e[n]), t),
    {}
  );
}
const X7 = {
  html: {
    fontFamily: "sans-serif",
    lineHeight: "1.15",
    textSizeAdjust: "100%",
  },
  body: { margin: 0 },
  "article, aside, footer, header, nav, section, figcaption, figure, main": {
    display: "block",
  },
  h1: { fontSize: "2em" },
  hr: { boxSizing: "content-box", height: 0, overflow: "visible" },
  pre: { fontFamily: "monospace, monospace", fontSize: "1em" },
  a: { background: "transparent", textDecorationSkip: "objects" },
  "a:active, a:hover": { outlineWidth: 0 },
  "abbr[title]": { borderBottom: "none", textDecoration: "underline" },
  "b, strong": { fontWeight: "bolder" },
  "code, kbp, samp": { fontFamily: "monospace, monospace", fontSize: "1em" },
  dfn: { fontStyle: "italic" },
  mark: { backgroundColor: "#ff0", color: "#000" },
  small: { fontSize: "80%" },
  "sub, sup": {
    fontSize: "75%",
    lineHeight: 0,
    position: "relative",
    verticalAlign: "baseline",
  },
  sup: { top: "-0.5em" },
  sub: { bottom: "-0.25em" },
  "audio, video": { display: "inline-block" },
  "audio:not([controls])": { display: "none", height: 0 },
  img: { borderStyle: "none", verticalAlign: "middle" },
  "svg:not(:root)": { overflow: "hidden" },
  "button, input, optgroup, select, textarea": {
    fontFamily: "sans-serif",
    fontSize: "100%",
    lineHeight: "1.15",
    margin: 0,
  },
  "button, input": { overflow: "visible" },
  "button, select": { textTransform: "none" },
  "button, [type=reset], [type=submit]": { WebkitAppearance: "button" },
  "button::-moz-focus-inner, [type=button]::-moz-focus-inner, [type=reset]::-moz-focus-inner, [type=submit]::-moz-focus-inner":
    { borderStyle: "none", padding: 0 },
  "button:-moz-focusring, [type=button]:-moz-focusring, [type=reset]:-moz-focusring, [type=submit]:-moz-focusring":
    { outline: "1px dotted ButtonText" },
  legend: {
    boxSizing: "border-box",
    color: "inherit",
    display: "table",
    maxWidth: "100%",
    padding: 0,
    whiteSpace: "normal",
  },
  progress: { display: "inline-block", verticalAlign: "baseline" },
  textarea: { overflow: "auto" },
  "[type=checkbox], [type=radio]": { boxSizing: "border-box", padding: 0 },
  "[type=number]::-webkit-inner-spin-button, [type=number]::-webkit-outer-spin-button":
    { height: "auto" },
  "[type=search]": { appearance: "none" },
  "[type=search]::-webkit-search-cancel-button, [type=search]::-webkit-search-decoration":
    { appearance: "none" },
  "::-webkit-file-upload-button": { appearance: "button", font: "inherit" },
  "details, menu": { display: "block" },
  summary: { display: "list-item" },
  canvas: { display: "inline-block" },
  template: { display: "none" },
  "[hidden]": { display: "none" },
};
function K7() {
  return R.createElement(Gs, { styles: X7 });
}
var Q7 = Object.defineProperty,
  tb = Object.getOwnPropertySymbols,
  q7 = Object.prototype.hasOwnProperty,
  Z7 = Object.prototype.propertyIsEnumerable,
  nb = (e, t, n) =>
    t in e
      ? Q7(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  os = (e, t) => {
    for (var n in t || (t = {})) q7.call(t, n) && nb(e, n, t[n]);
    if (tb) for (var n of tb(t)) Z7.call(t, n) && nb(e, n, t[n]);
    return e;
  };
const $f = y.createContext({ theme: m0 });
function On() {
  var e;
  return ((e = y.useContext($f)) == null ? void 0 : e.theme) || m0;
}
function J7(e) {
  const t = On(),
    n = (r) => {
      var o, a;
      return {
        styles: ((o = t.components[r]) == null ? void 0 : o.styles) || {},
        classNames:
          ((a = t.components[r]) == null ? void 0 : a.classNames) || {},
      };
    };
  return Array.isArray(e) ? e.map(n) : [n(e)];
}
function yE() {
  var e;
  return (e = y.useContext($f)) == null ? void 0 : e.emotionCache;
}
function me(e, t, n) {
  var r;
  const o = On(),
    a = (r = o.components[e]) == null ? void 0 : r.defaultProps,
    s = typeof a == "function" ? a(o) : a;
  return os(os(os({}, t), s), gE(n));
}
function eL({
  theme: e,
  emotionCache: t,
  withNormalizeCSS: n = !1,
  withGlobalStyles: r = !1,
  withCSSVariables: o = !1,
  inherit: a = !1,
  children: s,
}) {
  const u = y.useContext($f),
    f = G7(m0, a ? os(os({}, u.theme), e) : e);
  return R.createElement(
    R7,
    { theme: f },
    R.createElement(
      $f.Provider,
      { value: { theme: f, emotionCache: t } },
      n && R.createElement(K7, null),
      r && R.createElement(j7, { theme: f }),
      o && R.createElement(F7, { theme: f }),
      typeof f.globalStyles == "function" &&
        R.createElement(Gs, { styles: f.globalStyles(f) }),
      s
    )
  );
}
eL.displayName = "@mantine/core/MantineProvider";
const tL = { app: 100, modal: 200, popover: 300, overlay: 400, max: 9999 };
function fp(e) {
  return tL[e];
}
function nL(e, t) {
  const n = y.useRef();
  return (
    (!n.current ||
      t.length !== n.current.prevDeps.length ||
      n.current.prevDeps.map((r, o) => r === t[o]).indexOf(!1) >= 0) &&
      (n.current = { v: e(), prevDeps: [...t] }),
    n.current.v
  );
}
const rL = uE({ key: "mantine", prepend: !0 });
function oL() {
  return yE() || rL;
}
var iL = Object.defineProperty,
  rb = Object.getOwnPropertySymbols,
  aL = Object.prototype.hasOwnProperty,
  lL = Object.prototype.propertyIsEnumerable,
  ob = (e, t, n) =>
    t in e
      ? iL(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  sL = (e, t) => {
    for (var n in t || (t = {})) aL.call(t, n) && ob(e, n, t[n]);
    if (rb) for (var n of rb(t)) lL.call(t, n) && ob(e, n, t[n]);
    return e;
  };
const vh = "ref";
function uL(e) {
  let t;
  if (e.length !== 1) return { args: e, ref: t };
  const [n] = e;
  if (!(n instanceof Object)) return { args: e, ref: t };
  if (!(vh in n)) return { args: e, ref: t };
  t = n[vh];
  const r = sL({}, n);
  return delete r[vh], { args: [r], ref: t };
}
const { cssFactory: cL } = (() => {
  function e(n, r, o) {
    const a = [],
      s = _7(n, a, o);
    return a.length < 2 ? o : s + r(a);
  }
  function t(n) {
    const { cache: r } = n,
      o = (...s) => {
        const { ref: u, args: f } = uL(s),
          d = b0(f, r.registered);
        return mE(r, d, !1), `${r.key}-${d.name}${u === void 0 ? "" : ` ${u}`}`;
      };
    return { css: o, cx: (...s) => e(r.registered, o, ZO(s)) };
  }
  return { cssFactory: t };
})();
function _E() {
  const e = oL();
  return nL(() => cL({ cache: e }), [e]);
}
function fL({
  cx: e,
  classes: t,
  context: n,
  classNames: r,
  name: o,
  cache: a,
}) {
  const s = n.reduce(
    (u, f) => (
      Object.keys(f.classNames).forEach((d) => {
        typeof u[d] != "string"
          ? (u[d] = `${f.classNames[d]}`)
          : (u[d] = `${u[d]} ${f.classNames[d]}`);
      }),
      u
    ),
    {}
  );
  return Object.keys(t).reduce(
    (u, f) => (
      (u[f] = e(
        t[f],
        s[f],
        r != null && r[f],
        Array.isArray(o)
          ? o
              .filter(Boolean)
              .map(
                (d) => `${(a == null ? void 0 : a.key) || "mantine"}-${d}-${f}`
              )
              .join(" ")
          : o
          ? `${(a == null ? void 0 : a.key) || "mantine"}-${o}-${f}`
          : null
      )),
      u
    ),
    {}
  );
}
var dL = Object.defineProperty,
  ib = Object.getOwnPropertySymbols,
  pL = Object.prototype.hasOwnProperty,
  mL = Object.prototype.propertyIsEnumerable,
  ab = (e, t, n) =>
    t in e
      ? dL(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  gh = (e, t) => {
    for (var n in t || (t = {})) pL.call(t, n) && ab(e, n, t[n]);
    if (ib) for (var n of ib(t)) mL.call(t, n) && ab(e, n, t[n]);
    return e;
  };
function hL(e) {
  return `__mantine-ref-${e || ""}`;
}
function lb(e, t, n) {
  const r = (o) => (typeof o == "function" ? o(t, n || {}) : o || {});
  return Array.isArray(e)
    ? e
        .map((o) => r(o.styles))
        .reduce(
          (o, a) => (
            Object.keys(a).forEach((s) => {
              o[s] ? (o[s] = gh(gh({}, o[s]), a[s])) : (o[s] = gh({}, a[s]));
            }),
            o
          ),
          {}
        )
    : r(e);
}
function Pe(e) {
  const t = typeof e == "function" ? e : () => e;
  function n(r, o) {
    const a = On(),
      s = J7(o == null ? void 0 : o.name),
      u = yE(),
      { css: f, cx: d } = _E(),
      m = t(a, r, hL),
      h = lb(o == null ? void 0 : o.styles, a, r),
      v = lb(s, a, r),
      b = Object.fromEntries(
        Object.keys(m).map((O) => {
          const E = d(
            { [f(m[O])]: !(o != null && o.unstyled) },
            f(v[O]),
            f(h[O])
          );
          return [O, E];
        })
      );
    return {
      classes: fL({
        cx: d,
        classes: b,
        context: s,
        classNames: o == null ? void 0 : o.classNames,
        name: o == null ? void 0 : o.name,
        cache: u,
      }),
      cx: d,
      theme: a,
    };
  }
  return n;
}
function vL({ styles: e }) {
  const t = On();
  return R.createElement(Gs, { styles: N7(typeof e == "function" ? e(t) : e) });
}
var gL = Object.defineProperty,
  yL = Object.defineProperties,
  _L = Object.getOwnPropertyDescriptors,
  sb = Object.getOwnPropertySymbols,
  wL = Object.prototype.hasOwnProperty,
  bL = Object.prototype.propertyIsEnumerable,
  ub = (e, t, n) =>
    t in e
      ? gL(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Dl = (e, t) => {
    for (var n in t || (t = {})) wL.call(t, n) && ub(e, n, t[n]);
    if (sb) for (var n of sb(t)) bL.call(t, n) && ub(e, n, t[n]);
    return e;
  },
  Ml = (e, t) => yL(e, _L(t));
const jl = {
    in: { opacity: 1, transform: "scale(1)" },
    out: { opacity: 0, transform: "scale(.9) translateY(10px)" },
    transitionProperty: "transform, opacity",
  },
  fc = {
    fade: {
      in: { opacity: 1 },
      out: { opacity: 0 },
      transitionProperty: "opacity",
    },
    scale: {
      in: { opacity: 1, transform: "scale(1)" },
      out: { opacity: 0, transform: "scale(0)" },
      common: { transformOrigin: "top" },
      transitionProperty: "transform, opacity",
    },
    "scale-y": {
      in: { opacity: 1, transform: "scaleY(1)" },
      out: { opacity: 0, transform: "scaleY(0)" },
      common: { transformOrigin: "top" },
      transitionProperty: "transform, opacity",
    },
    "scale-x": {
      in: { opacity: 1, transform: "scaleX(1)" },
      out: { opacity: 0, transform: "scaleX(0)" },
      common: { transformOrigin: "left" },
      transitionProperty: "transform, opacity",
    },
    "skew-up": {
      in: { opacity: 1, transform: "translateY(0) skew(0deg, 0deg)" },
      out: { opacity: 0, transform: "translateY(-20px) skew(-10deg, -5deg)" },
      common: { transformOrigin: "top" },
      transitionProperty: "transform, opacity",
    },
    "skew-down": {
      in: { opacity: 1, transform: "translateY(0) skew(0deg, 0deg)" },
      out: { opacity: 0, transform: "translateY(20px) skew(-10deg, -5deg)" },
      common: { transformOrigin: "bottom" },
      transitionProperty: "transform, opacity",
    },
    "rotate-left": {
      in: { opacity: 1, transform: "translateY(0) rotate(0deg)" },
      out: { opacity: 0, transform: "translateY(20px) rotate(-5deg)" },
      common: { transformOrigin: "bottom" },
      transitionProperty: "transform, opacity",
    },
    "rotate-right": {
      in: { opacity: 1, transform: "translateY(0) rotate(0deg)" },
      out: { opacity: 0, transform: "translateY(20px) rotate(5deg)" },
      common: { transformOrigin: "top" },
      transitionProperty: "transform, opacity",
    },
    "slide-down": {
      in: { opacity: 1, transform: "translateY(0)" },
      out: { opacity: 0, transform: "translateY(-100%)" },
      common: { transformOrigin: "top" },
      transitionProperty: "transform, opacity",
    },
    "slide-up": {
      in: { opacity: 1, transform: "translateY(0)" },
      out: { opacity: 0, transform: "translateY(100%)" },
      common: { transformOrigin: "bottom" },
      transitionProperty: "transform, opacity",
    },
    "slide-left": {
      in: { opacity: 1, transform: "translateX(0)" },
      out: { opacity: 0, transform: "translateX(100%)" },
      common: { transformOrigin: "left" },
      transitionProperty: "transform, opacity",
    },
    "slide-right": {
      in: { opacity: 1, transform: "translateX(0)" },
      out: { opacity: 0, transform: "translateX(-100%)" },
      common: { transformOrigin: "right" },
      transitionProperty: "transform, opacity",
    },
    pop: Ml(Dl({}, jl), { common: { transformOrigin: "center center" } }),
    "pop-bottom-left": Ml(Dl({}, jl), {
      common: { transformOrigin: "bottom left" },
    }),
    "pop-bottom-right": Ml(Dl({}, jl), {
      common: { transformOrigin: "bottom right" },
    }),
    "pop-top-left": Ml(Dl({}, jl), { common: { transformOrigin: "top left" } }),
    "pop-top-right": Ml(Dl({}, jl), {
      common: { transformOrigin: "top right" },
    }),
  },
  cb = ["mousedown", "touchstart"];
function xL(e, t, n) {
  const r = y.useRef();
  return (
    y.useEffect(() => {
      const o = (a) => {
        const { target: s } = a ?? {};
        if (Array.isArray(n)) {
          const u =
            (s == null
              ? void 0
              : s.hasAttribute("data-ignore-outside-clicks")) ||
            (!document.body.contains(s) && s.tagName !== "HTML");
          n.every((d) => !!d && !a.composedPath().includes(d)) && !u && e();
        } else r.current && !r.current.contains(s) && e();
      };
      return (
        (t || cb).forEach((a) => document.addEventListener(a, o)),
        () => {
          (t || cb).forEach((a) => document.removeEventListener(a, o));
        }
      );
    }, [r, e, n]),
    r
  );
}
function SL({ timeout: e = 2e3 } = {}) {
  const [t, n] = y.useState(null),
    [r, o] = y.useState(!1),
    [a, s] = y.useState(null),
    u = (m) => {
      clearTimeout(a), s(setTimeout(() => o(!1), e)), o(m);
    };
  return {
    copy: (m) => {
      "clipboard" in navigator
        ? navigator.clipboard
            .writeText(m)
            .then(() => u(!0))
            .catch((h) => n(h))
        : n(new Error("useClipboard: navigator.clipboard is not supported"));
    },
    reset: () => {
      o(!1), n(null), clearTimeout(a);
    },
    error: t,
    copied: r,
  };
}
function PL(e, t) {
  try {
    return (
      e.addEventListener("change", t), () => e.removeEventListener("change", t)
    );
  } catch {
    return e.addListener(t), () => e.removeListener(t);
  }
}
function OL(e, t) {
  return typeof t == "boolean"
    ? t
    : typeof window < "u" && "matchMedia" in window
    ? window.matchMedia(e).matches
    : !1;
}
function EL(
  e,
  t,
  { getInitialValueInEffect: n } = { getInitialValueInEffect: !0 }
) {
  const [r, o] = y.useState(n ? t : OL(e, t)),
    a = y.useRef();
  return (
    y.useEffect(() => {
      if ("matchMedia" in window)
        return (
          (a.current = window.matchMedia(e)),
          o(a.current.matches),
          PL(a.current, (s) => o(s.matches))
        );
    }, [e]),
    r
  );
}
const wE = typeof document < "u" ? y.useLayoutEffect : y.useEffect;
function Zr(e, t) {
  const n = y.useRef(!1);
  y.useEffect(
    () => () => {
      n.current = !1;
    },
    []
  ),
    y.useEffect(() => {
      if (n.current) return e();
      n.current = !0;
    }, t);
}
function $L({ opened: e, shouldReturnFocus: t = !0 }) {
  const n = y.useRef(),
    r = () => {
      var o;
      n.current &&
        "focus" in n.current &&
        typeof n.current.focus == "function" &&
        ((o = n.current) == null || o.focus({ preventScroll: !0 }));
    };
  return (
    Zr(() => {
      let o = -1;
      const a = (s) => {
        s.key === "Tab" && window.clearTimeout(o);
      };
      return (
        document.addEventListener("keydown", a),
        e
          ? (n.current = document.activeElement)
          : t && (o = window.setTimeout(r, 10)),
        () => {
          window.clearTimeout(o), document.removeEventListener("keydown", a);
        }
      );
    }, [e, t]),
    r
  );
}
const CL = /input|select|textarea|button|object/,
  bE = "a, input, select, textarea, button, object, [tabindex]";
function kL(e) {
  return e.style.display === "none";
}
function RL(e) {
  if (
    e.getAttribute("aria-hidden") ||
    e.getAttribute("hidden") ||
    e.getAttribute("type") === "hidden"
  )
    return !1;
  let n = e;
  for (; n && !(n === document.body || n.nodeType === 11); ) {
    if (kL(n)) return !1;
    n = n.parentNode;
  }
  return !0;
}
function xE(e) {
  let t = e.getAttribute("tabindex");
  return t === null && (t = void 0), parseInt(t, 10);
}
function Dv(e) {
  const t = e.nodeName.toLowerCase(),
    n = !Number.isNaN(xE(e));
  return (
    ((CL.test(t) && !e.disabled) ||
      (e instanceof HTMLAnchorElement && e.href) ||
      n) &&
    RL(e)
  );
}
function SE(e) {
  const t = xE(e);
  return (Number.isNaN(t) || t >= 0) && Dv(e);
}
function NL(e) {
  return Array.from(e.querySelectorAll(bE)).filter(SE);
}
function IL(e, t) {
  const n = NL(e);
  if (!n.length) {
    t.preventDefault();
    return;
  }
  const r = n[t.shiftKey ? 0 : n.length - 1],
    o = e.getRootNode();
  if (!(r === o.activeElement || e === o.activeElement)) return;
  t.preventDefault();
  const s = n[t.shiftKey ? n.length - 1 : 0];
  s && s.focus();
}
function TL(e, t = "body > :not(script)") {
  const n = Array.from(document.querySelectorAll(t)).map((r) => {
    var o;
    if (
      ((o = r == null ? void 0 : r.shadowRoot) != null && o.contains(e)) ||
      r.contains(e)
    )
      return;
    const a = r.getAttribute("aria-hidden");
    return (
      (a === null || a === "false") && r.setAttribute("aria-hidden", "true"),
      { node: r, ariaHidden: a }
    );
  });
  return () => {
    n.forEach((r) => {
      r &&
        (r.ariaHidden === null
          ? r.node.removeAttribute("aria-hidden")
          : r.node.setAttribute("aria-hidden", r.ariaHidden));
    });
  };
}
function zL(e = !0) {
  const t = y.useRef(),
    n = y.useRef(null),
    r = y.useCallback(
      (o) => {
        if (e) {
          if (o === null) {
            n.current && (n.current(), (n.current = null));
            return;
          }
          if (((n.current = TL(o)), t.current !== o))
            if (o) {
              const a = () => {
                let s = o.querySelector("[data-autofocus]");
                if (!s) {
                  const u = Array.from(o.querySelectorAll(bE));
                  (s = u.find(SE) || u.find(Dv) || null),
                    !s && Dv(o) && (s = o);
                }
                s && s.focus({ preventScroll: !0 });
              };
              setTimeout(() => {
                o.getRootNode() && a();
              }),
                (t.current = o);
            } else t.current = null;
        }
      },
      [e]
    );
  return (
    y.useEffect(() => {
      if (!e) return;
      const o = (a) => {
        a.key === "Tab" && t.current && IL(t.current, a);
      };
      return (
        document.addEventListener("keydown", o),
        () => {
          document.removeEventListener("keydown", o), n.current && n.current();
        }
      );
    }, [e]),
    r
  );
}
const AL = (e) => (e + 1) % 1e6;
function LL() {
  const [, e] = y.useReducer(AL, 0);
  return e;
}
const DL = () => `mantine-${Math.random().toString(36).slice(2, 11)}`,
  ML = R["useId".toString()] || (() => {});
function jL() {
  const [e, t] = y.useState("");
  return (
    wE(() => {
      t(DL());
    }, []),
    e
  );
}
function FL() {
  const e = ML();
  return e ? `mantine-${e.replace(/:/g, "")}` : "";
}
function dp(e) {
  return typeof e == "string" ? e : FL() || jL();
}
function fb(e, t, n) {
  y.useEffect(
    () => (
      window.addEventListener(e, t, n),
      () => window.removeEventListener(e, t, n)
    ),
    [e, t]
  );
}
function WL(e, t) {
  typeof e == "function"
    ? e(t)
    : typeof e == "object" && e !== null && "current" in e && (e.current = t);
}
function BL(...e) {
  return (t) => {
    e.forEach((n) => WL(n, t));
  };
}
function x0(...e) {
  return y.useCallback(BL(...e), e);
}
function Is({
  value: e,
  defaultValue: t,
  finalValue: n,
  onChange: r = () => {},
}) {
  const [o, a] = y.useState(t !== void 0 ? t : n),
    s = (u) => {
      a(u), r == null || r(u);
    };
  return e !== void 0 ? [e, r, !0] : [o, s, !1];
}
function UL({ initialValues: e = [], limit: t }) {
  const [{ state: n, queue: r }, o] = y.useState({
    state: e.slice(0, t),
    queue: e.slice(t),
  });
  return {
    state: n,
    queue: r,
    add: (...f) =>
      o((d) => {
        const m = [...d.state, ...d.queue, ...f];
        return { state: m.slice(0, t), queue: m.slice(t) };
      }),
    update: (f) =>
      o((d) => {
        const m = f([...d.state, ...d.queue]);
        return { state: m.slice(0, t), queue: m.slice(t) };
      }),
    cleanQueue: () => o((f) => ({ state: f.state, queue: [] })),
  };
}
function S0(e, t) {
  return EL("(prefers-reduced-motion: reduce)", e, t);
}
const HL = (e) => (e < 0.5 ? 2 * e * e : -1 + (4 - 2 * e) * e),
  VL = ({
    axis: e,
    target: t,
    parent: n,
    alignment: r,
    offset: o,
    isList: a,
  }) => {
    if (!t || (!n && typeof document > "u")) return 0;
    const s = !!n,
      f = (n || document.body).getBoundingClientRect(),
      d = t.getBoundingClientRect(),
      m = (h) => d[h] - f[h];
    if (e === "y") {
      const h = m("top");
      if (h === 0) return 0;
      if (r === "start") {
        const b = h - o;
        return b <= d.height * (a ? 0 : 1) || !a ? b : 0;
      }
      const v = s ? f.height : window.innerHeight;
      if (r === "end") {
        const b = h + o - v + d.height;
        return b >= -d.height * (a ? 0 : 1) || !a ? b : 0;
      }
      return r === "center" ? h - v / 2 + d.height / 2 : 0;
    }
    if (e === "x") {
      const h = m("left");
      if (h === 0) return 0;
      if (r === "start") {
        const b = h - o;
        return b <= d.width || !a ? b : 0;
      }
      const v = s ? f.width : window.innerWidth;
      if (r === "end") {
        const b = h + o - v + d.width;
        return b >= -d.width || !a ? b : 0;
      }
      return r === "center" ? h - v / 2 + d.width / 2 : 0;
    }
    return 0;
  },
  YL = ({ axis: e, parent: t }) => {
    if (!t && typeof document > "u") return 0;
    const n = e === "y" ? "scrollTop" : "scrollLeft";
    if (t) return t[n];
    const { body: r, documentElement: o } = document;
    return r[n] + o[n];
  },
  GL = ({ axis: e, parent: t, distance: n }) => {
    if (!t && typeof document > "u") return;
    const r = e === "y" ? "scrollTop" : "scrollLeft";
    if (t) t[r] = n;
    else {
      const { body: o, documentElement: a } = document;
      (o[r] = n), (a[r] = n);
    }
  };
function XL({
  duration: e = 1250,
  axis: t = "y",
  onScrollFinish: n,
  easing: r = HL,
  offset: o = 0,
  cancelable: a = !0,
  isList: s = !1,
} = {}) {
  const u = y.useRef(0),
    f = y.useRef(0),
    d = y.useRef(!1),
    m = y.useRef(null),
    h = y.useRef(null),
    v = S0(),
    b = () => {
      u.current && cancelAnimationFrame(u.current);
    },
    O = y.useCallback(
      ({ alignment: $ = "start" } = {}) => {
        var _;
        (d.current = !1), u.current && b();
        const w = (_ = YL({ parent: m.current, axis: t })) != null ? _ : 0,
          P =
            VL({
              parent: m.current,
              target: h.current,
              axis: t,
              alignment: $,
              offset: o,
              isList: s,
            }) - (m.current ? 0 : w);
        function k() {
          f.current === 0 && (f.current = performance.now());
          const z = performance.now() - f.current,
            A = v || e === 0 ? 1 : z / e,
            M = w + P * r(A);
          GL({ parent: m.current, axis: t, distance: M }),
            !d.current && A < 1
              ? (u.current = requestAnimationFrame(k))
              : (typeof n == "function" && n(),
                (f.current = 0),
                (u.current = 0),
                b());
        }
        k();
      },
      [t, e, r, s, o, n, v]
    ),
    E = () => {
      a && (d.current = !0);
    };
  return (
    fb("wheel", E, { passive: !0 }),
    fb("touchmove", E, { passive: !0 }),
    y.useEffect(() => b, []),
    { scrollableRef: m, targetRef: h, scrollIntoView: O, cancel: b }
  );
}
function PE() {
  return `mantine-${Math.random().toString(36).slice(2, 11)}`;
}
var db = Object.getOwnPropertySymbols,
  KL = Object.prototype.hasOwnProperty,
  QL = Object.prototype.propertyIsEnumerable,
  qL = (e, t) => {
    var n = {};
    for (var r in e) KL.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && db)
      for (var r of db(e)) t.indexOf(r) < 0 && QL.call(e, r) && (n[r] = e[r]);
    return n;
  };
function Xs(e) {
  const t = e,
    {
      m: n,
      mx: r,
      my: o,
      mt: a,
      mb: s,
      ml: u,
      mr: f,
      p: d,
      px: m,
      py: h,
      pt: v,
      pb: b,
      pl: O,
      pr: E,
      bg: $,
      c: _,
      opacity: w,
      ff: P,
      fz: k,
      fw: I,
      lts: z,
      ta: A,
      lh: M,
      fs: B,
      tt: H,
      td: q,
      w: Z,
      miw: he,
      maw: xe,
      h: Ce,
      mih: ce,
      mah: Se,
      bgsz: Y,
      bgp: re,
      bgr: ne,
      bga: le,
      pos: ze,
      top: Gt,
      left: Dt,
      bottom: $t,
      right: vt,
      inset: $n,
      display: Vn,
    } = t,
    Yn = qL(t, [
      "m",
      "mx",
      "my",
      "mt",
      "mb",
      "ml",
      "mr",
      "p",
      "px",
      "py",
      "pt",
      "pb",
      "pl",
      "pr",
      "bg",
      "c",
      "opacity",
      "ff",
      "fz",
      "fw",
      "lts",
      "ta",
      "lh",
      "fs",
      "tt",
      "td",
      "w",
      "miw",
      "maw",
      "h",
      "mih",
      "mah",
      "bgsz",
      "bgp",
      "bgr",
      "bga",
      "pos",
      "top",
      "left",
      "bottom",
      "right",
      "inset",
      "display",
    ]);
  return {
    systemStyles: gE({
      m: n,
      mx: r,
      my: o,
      mt: a,
      mb: s,
      ml: u,
      mr: f,
      p: d,
      px: m,
      py: h,
      pt: v,
      pb: b,
      pl: O,
      pr: E,
      bg: $,
      c: _,
      opacity: w,
      ff: P,
      fz: k,
      fw: I,
      lts: z,
      ta: A,
      lh: M,
      fs: B,
      tt: H,
      td: q,
      w: Z,
      miw: he,
      maw: xe,
      h: Ce,
      mih: ce,
      mah: Se,
      bgsz: Y,
      bgp: re,
      bgr: ne,
      bga: le,
      pos: ze,
      top: Gt,
      left: Dt,
      bottom: $t,
      right: vt,
      inset: $n,
      display: Vn,
    }),
    rest: Yn,
  };
}
function ZL(e, t) {
  const n = Object.keys(e)
    .filter((r) => r !== "base")
    .sort(
      (r, o) =>
        t.fn.size({ size: r, sizes: t.breakpoints }) -
        t.fn.size({ size: o, sizes: t.breakpoints })
    );
  return "base" in e ? ["base", ...n] : n;
}
function JL({ value: e, theme: t, getValue: n, property: r }) {
  if (e == null) return;
  if (typeof e == "object")
    return ZL(e, t).reduce((s, u) => {
      if (u === "base" && e.base !== void 0) {
        const d = n(e.base, t);
        return Array.isArray(r)
          ? (r.forEach((m) => {
              s[m] = d;
            }),
            s)
          : ((s[r] = d), s);
      }
      const f = n(e[u], t);
      return Array.isArray(r)
        ? ((s[t.fn.largerThan(u)] = {}),
          r.forEach((d) => {
            s[t.fn.largerThan(u)][d] = f;
          }),
          s)
        : ((s[t.fn.largerThan(u)] = { [r]: f }), s);
    }, {});
  const o = n(e, t);
  return Array.isArray(r)
    ? r.reduce((a, s) => ((a[s] = o), a), {})
    : { [r]: o };
}
function eD(e, t) {
  return e === "dimmed"
    ? t.colorScheme === "dark"
      ? t.colors.dark[2]
      : t.colors.gray[6]
    : t.fn.variant({ variant: "filled", color: e, primaryFallback: !1 })
        .background;
}
function tD(e) {
  return e;
}
function nD(e, t) {
  return t.fn.size({ size: e, sizes: t.fontSizes });
}
const rD = ["-xs", "-sm", "-md", "-lg", "-xl"];
function oD(e, t) {
  return rD.includes(e)
    ? t.fn.size({ size: e.replace("-", ""), sizes: t.spacing }) * -1
    : t.fn.size({ size: e, sizes: t.spacing });
}
const iD = { color: eD, default: tD, fontSize: nD, spacing: oD },
  aD = {
    m: { type: "spacing", property: "margin" },
    mt: { type: "spacing", property: "marginTop" },
    mb: { type: "spacing", property: "marginBottom" },
    ml: { type: "spacing", property: "marginLeft" },
    mr: { type: "spacing", property: "marginRight" },
    mx: { type: "spacing", property: ["marginRight", "marginLeft"] },
    my: { type: "spacing", property: ["marginTop", "marginBottom"] },
    p: { type: "spacing", property: "padding" },
    pt: { type: "spacing", property: "paddingTop" },
    pb: { type: "spacing", property: "paddingBottom" },
    pl: { type: "spacing", property: "paddingLeft" },
    pr: { type: "spacing", property: "paddingRight" },
    px: { type: "spacing", property: ["paddingRight", "paddingLeft"] },
    py: { type: "spacing", property: ["paddingTop", "paddingBottom"] },
    bg: { type: "color", property: "background" },
    c: { type: "color", property: "color" },
    opacity: { type: "default", property: "opacity" },
    ff: { type: "default", property: "fontFamily" },
    fz: { type: "fontSize", property: "fontSize" },
    fw: { type: "default", property: "fontWeight" },
    lts: { type: "default", property: "letterSpacing" },
    ta: { type: "default", property: "textAlign" },
    lh: { type: "default", property: "lineHeight" },
    fs: { type: "default", property: "fontStyle" },
    tt: { type: "default", property: "textTransform" },
    td: { type: "default", property: "textDecoration" },
    w: { type: "spacing", property: "width" },
    miw: { type: "spacing", property: "minWidth" },
    maw: { type: "spacing", property: "maxWidth" },
    h: { type: "spacing", property: "height" },
    mih: { type: "spacing", property: "minHeight" },
    mah: { type: "spacing", property: "maxHeight" },
    bgsz: { type: "default", property: "background-size" },
    bgp: { type: "default", property: "background-position" },
    bgr: { type: "default", property: "background-repeat" },
    bga: { type: "default", property: "background-attachment" },
    pos: { type: "default", property: "position" },
    top: { type: "default", property: "top" },
    left: { type: "default", property: "left" },
    bottom: { type: "default", property: "bottom" },
    right: { type: "default", property: "right" },
    inset: { type: "default", property: "inset" },
    display: { type: "default", property: "display" },
  };
var lD = Object.defineProperty,
  pb = Object.getOwnPropertySymbols,
  sD = Object.prototype.hasOwnProperty,
  uD = Object.prototype.propertyIsEnumerable,
  mb = (e, t, n) =>
    t in e
      ? lD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  hb = (e, t) => {
    for (var n in t || (t = {})) sD.call(t, n) && mb(e, n, t[n]);
    if (pb) for (var n of pb(t)) uD.call(t, n) && mb(e, n, t[n]);
    return e;
  };
function vb(e, t, n = aD) {
  return Object.keys(n)
    .reduce(
      (o, a) => (
        a in e &&
          e[a] !== void 0 &&
          o.push(
            JL({
              value: e[a],
              getValue: iD[n[a].type],
              property: n[a].property,
              theme: t,
            })
          ),
        o
      ),
      []
    )
    .reduce(
      (o, a) => (
        Object.keys(a).forEach((s) => {
          typeof a[s] == "object" && a[s] !== null && s in o
            ? (o[s] = hb(hb({}, o[s]), a[s]))
            : (o[s] = a[s]);
        }),
        o
      ),
      {}
    );
}
function gb(e, t) {
  return typeof e == "function" ? e(t) : e;
}
function cD(e, t, n) {
  const r = On(),
    { css: o, cx: a } = _E();
  return Array.isArray(e)
    ? a(
        n,
        o(vb(t, r)),
        e.map((s) => o(gb(s, r)))
      )
    : a(n, o(gb(e, r)), o(vb(t, r)));
}
var fD = Object.defineProperty,
  Cf = Object.getOwnPropertySymbols,
  OE = Object.prototype.hasOwnProperty,
  EE = Object.prototype.propertyIsEnumerable,
  yb = (e, t, n) =>
    t in e
      ? fD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  dD = (e, t) => {
    for (var n in t || (t = {})) OE.call(t, n) && yb(e, n, t[n]);
    if (Cf) for (var n of Cf(t)) EE.call(t, n) && yb(e, n, t[n]);
    return e;
  },
  pD = (e, t) => {
    var n = {};
    for (var r in e) OE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Cf)
      for (var r of Cf(e)) t.indexOf(r) < 0 && EE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const $E = y.forwardRef((e, t) => {
  var n = e,
    { className: r, component: o, style: a, sx: s } = n,
    u = pD(n, ["className", "component", "style", "sx"]);
  const { systemStyles: f, rest: d } = Xs(u),
    m = o || "div";
  return R.createElement(
    m,
    dD({ ref: t, className: cD(s, f, r), style: a }, d)
  );
});
$E.displayName = "@mantine/core/Box";
const Ie = $E;
var mD = Object.defineProperty,
  hD = Object.defineProperties,
  vD = Object.getOwnPropertyDescriptors,
  _b = Object.getOwnPropertySymbols,
  gD = Object.prototype.hasOwnProperty,
  yD = Object.prototype.propertyIsEnumerable,
  wb = (e, t, n) =>
    t in e
      ? mD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  bb = (e, t) => {
    for (var n in t || (t = {})) gD.call(t, n) && wb(e, n, t[n]);
    if (_b) for (var n of _b(t)) yD.call(t, n) && wb(e, n, t[n]);
    return e;
  },
  _D = (e, t) => hD(e, vD(t)),
  wD = Pe((e) => ({
    root: _D(bb(bb({}, e.fn.focusStyles()), e.fn.fontStyles()), {
      cursor: "pointer",
      border: 0,
      padding: 0,
      appearance: "none",
      fontSize: e.fontSizes.md,
      backgroundColor: "transparent",
      textAlign: "left",
      color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
      textDecoration: "none",
      boxSizing: "border-box",
    }),
  }));
const bD = wD;
var xD = Object.defineProperty,
  kf = Object.getOwnPropertySymbols,
  CE = Object.prototype.hasOwnProperty,
  kE = Object.prototype.propertyIsEnumerable,
  xb = (e, t, n) =>
    t in e
      ? xD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  SD = (e, t) => {
    for (var n in t || (t = {})) CE.call(t, n) && xb(e, n, t[n]);
    if (kf) for (var n of kf(t)) kE.call(t, n) && xb(e, n, t[n]);
    return e;
  },
  PD = (e, t) => {
    var n = {};
    for (var r in e) CE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && kf)
      for (var r of kf(e)) t.indexOf(r) < 0 && kE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const RE = y.forwardRef((e, t) => {
  const n = me("UnstyledButton", {}, e),
    { className: r, component: o = "button", unstyled: a } = n,
    s = PD(n, ["className", "component", "unstyled"]),
    { classes: u, cx: f } = bD(null, { name: "UnstyledButton", unstyled: a });
  return R.createElement(
    Ie,
    SD(
      {
        component: o,
        ref: t,
        className: f(u.root, r),
        type: o === "button" ? "button" : void 0,
      },
      s
    )
  );
});
RE.displayName = "@mantine/core/UnstyledButton";
const NE = RE;
var OD = Object.defineProperty,
  ED = Object.defineProperties,
  $D = Object.getOwnPropertyDescriptors,
  Sb = Object.getOwnPropertySymbols,
  CD = Object.prototype.hasOwnProperty,
  kD = Object.prototype.propertyIsEnumerable,
  Pb = (e, t, n) =>
    t in e
      ? OD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  IE = (e, t) => {
    for (var n in t || (t = {})) CD.call(t, n) && Pb(e, n, t[n]);
    if (Sb) for (var n of Sb(t)) kD.call(t, n) && Pb(e, n, t[n]);
    return e;
  },
  RD = (e, t) => ED(e, $D(t));
const Gl = { xs: 18, sm: 22, md: 28, lg: 34, xl: 44 };
function ND({ variant: e, theme: t, color: n, gradient: r }) {
  const o = t.fn.variant({ color: n, variant: e, gradient: r });
  return e === "gradient"
    ? {
        border: 0,
        backgroundImage: o.background,
        color: o.color,
        "&:hover": t.fn.hover({ backgroundSize: "200%" }),
      }
    : IE(
        {
          border: `1px solid ${o.border}`,
          backgroundColor: o.background,
          color: o.color,
        },
        t.fn.hover({ backgroundColor: o.hover })
      );
}
var ID = Pe((e, { color: t, size: n, radius: r, variant: o, gradient: a }) => ({
  root: RD(IE({}, ND({ variant: o, theme: e, color: t, gradient: a })), {
    position: "relative",
    height: e.fn.size({ size: n, sizes: Gl }),
    minHeight: e.fn.size({ size: n, sizes: Gl }),
    width: e.fn.size({ size: n, sizes: Gl }),
    minWidth: e.fn.size({ size: n, sizes: Gl }),
    borderRadius: e.fn.radius(r),
    padding: 0,
    lineHeight: 1,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    "&:active": e.activeStyles,
    "&:disabled, &[data-disabled]": {
      color: e.colors.gray[e.colorScheme === "dark" ? 6 : 4],
      cursor: "not-allowed",
      backgroundColor:
        o === "transparent"
          ? void 0
          : e.fn.themeColor("gray", e.colorScheme === "dark" ? 8 : 1),
      borderColor:
        o === "transparent"
          ? void 0
          : e.fn.themeColor("gray", e.colorScheme === "dark" ? 8 : 1),
      backgroundImage: "none",
      pointerEvents: "none",
      "&:active": { transform: "none" },
    },
    "&[data-loading]": {
      pointerEvents: "none",
      "&::before": {
        content: '""',
        position: "absolute",
        top: -1,
        left: -1,
        right: -1,
        bottom: -1,
        backgroundColor:
          e.colorScheme === "dark"
            ? e.fn.rgba(e.colors.dark[7], 0.5)
            : "rgba(255, 255, 255, .5)",
        borderRadius: e.fn.radius(r),
        cursor: "not-allowed",
      },
    },
  }),
}));
const TD = ID;
var zD = Object.defineProperty,
  Rf = Object.getOwnPropertySymbols,
  TE = Object.prototype.hasOwnProperty,
  zE = Object.prototype.propertyIsEnumerable,
  Ob = (e, t, n) =>
    t in e
      ? zD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  AD = (e, t) => {
    for (var n in t || (t = {})) TE.call(t, n) && Ob(e, n, t[n]);
    if (Rf) for (var n of Rf(t)) zE.call(t, n) && Ob(e, n, t[n]);
    return e;
  },
  LD = (e, t) => {
    var n = {};
    for (var r in e) TE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Rf)
      for (var r of Rf(e)) t.indexOf(r) < 0 && zE.call(e, r) && (n[r] = e[r]);
    return n;
  };
function DD(e) {
  var t = e,
    { size: n, color: r } = t,
    o = LD(t, ["size", "color"]);
  return R.createElement(
    "svg",
    AD(
      {
        viewBox: "0 0 135 140",
        xmlns: "http://www.w3.org/2000/svg",
        fill: r,
        width: `${n}px`,
      },
      o
    ),
    R.createElement(
      "rect",
      { y: "10", width: "15", height: "120", rx: "6" },
      R.createElement("animate", {
        attributeName: "height",
        begin: "0.5s",
        dur: "1s",
        values: "120;110;100;90;80;70;60;50;40;140;120",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "y",
        begin: "0.5s",
        dur: "1s",
        values: "10;15;20;25;30;35;40;45;50;0;10",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "rect",
      { x: "30", y: "10", width: "15", height: "120", rx: "6" },
      R.createElement("animate", {
        attributeName: "height",
        begin: "0.25s",
        dur: "1s",
        values: "120;110;100;90;80;70;60;50;40;140;120",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "y",
        begin: "0.25s",
        dur: "1s",
        values: "10;15;20;25;30;35;40;45;50;0;10",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "rect",
      { x: "60", width: "15", height: "140", rx: "6" },
      R.createElement("animate", {
        attributeName: "height",
        begin: "0s",
        dur: "1s",
        values: "120;110;100;90;80;70;60;50;40;140;120",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "y",
        begin: "0s",
        dur: "1s",
        values: "10;15;20;25;30;35;40;45;50;0;10",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "rect",
      { x: "90", y: "10", width: "15", height: "120", rx: "6" },
      R.createElement("animate", {
        attributeName: "height",
        begin: "0.25s",
        dur: "1s",
        values: "120;110;100;90;80;70;60;50;40;140;120",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "y",
        begin: "0.25s",
        dur: "1s",
        values: "10;15;20;25;30;35;40;45;50;0;10",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "rect",
      { x: "120", y: "10", width: "15", height: "120", rx: "6" },
      R.createElement("animate", {
        attributeName: "height",
        begin: "0.5s",
        dur: "1s",
        values: "120;110;100;90;80;70;60;50;40;140;120",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "y",
        begin: "0.5s",
        dur: "1s",
        values: "10;15;20;25;30;35;40;45;50;0;10",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    )
  );
}
var MD = Object.defineProperty,
  Nf = Object.getOwnPropertySymbols,
  AE = Object.prototype.hasOwnProperty,
  LE = Object.prototype.propertyIsEnumerable,
  Eb = (e, t, n) =>
    t in e
      ? MD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  jD = (e, t) => {
    for (var n in t || (t = {})) AE.call(t, n) && Eb(e, n, t[n]);
    if (Nf) for (var n of Nf(t)) LE.call(t, n) && Eb(e, n, t[n]);
    return e;
  },
  FD = (e, t) => {
    var n = {};
    for (var r in e) AE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Nf)
      for (var r of Nf(e)) t.indexOf(r) < 0 && LE.call(e, r) && (n[r] = e[r]);
    return n;
  };
function WD(e) {
  var t = e,
    { size: n, color: r } = t,
    o = FD(t, ["size", "color"]);
  return R.createElement(
    "svg",
    jD(
      {
        width: `${n}px`,
        height: `${n}px`,
        viewBox: "0 0 38 38",
        xmlns: "http://www.w3.org/2000/svg",
        stroke: r,
      },
      o
    ),
    R.createElement(
      "g",
      { fill: "none", fillRule: "evenodd" },
      R.createElement(
        "g",
        { transform: "translate(2.5 2.5)", strokeWidth: "5" },
        R.createElement("circle", {
          strokeOpacity: ".5",
          cx: "16",
          cy: "16",
          r: "16",
        }),
        R.createElement(
          "path",
          { d: "M32 16c0-9.94-8.06-16-16-16" },
          R.createElement("animateTransform", {
            attributeName: "transform",
            type: "rotate",
            from: "0 16 16",
            to: "360 16 16",
            dur: "1s",
            repeatCount: "indefinite",
          })
        )
      )
    )
  );
}
var BD = Object.defineProperty,
  If = Object.getOwnPropertySymbols,
  DE = Object.prototype.hasOwnProperty,
  ME = Object.prototype.propertyIsEnumerable,
  $b = (e, t, n) =>
    t in e
      ? BD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  UD = (e, t) => {
    for (var n in t || (t = {})) DE.call(t, n) && $b(e, n, t[n]);
    if (If) for (var n of If(t)) ME.call(t, n) && $b(e, n, t[n]);
    return e;
  },
  HD = (e, t) => {
    var n = {};
    for (var r in e) DE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && If)
      for (var r of If(e)) t.indexOf(r) < 0 && ME.call(e, r) && (n[r] = e[r]);
    return n;
  };
function VD(e) {
  var t = e,
    { size: n, color: r } = t,
    o = HD(t, ["size", "color"]);
  return R.createElement(
    "svg",
    UD(
      {
        width: `${n}px`,
        height: `${n / 4}px`,
        viewBox: "0 0 120 30",
        xmlns: "http://www.w3.org/2000/svg",
        fill: r,
      },
      o
    ),
    R.createElement(
      "circle",
      { cx: "15", cy: "15", r: "15" },
      R.createElement("animate", {
        attributeName: "r",
        from: "15",
        to: "15",
        begin: "0s",
        dur: "0.8s",
        values: "15;9;15",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "fill-opacity",
        from: "1",
        to: "1",
        begin: "0s",
        dur: "0.8s",
        values: "1;.5;1",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "circle",
      { cx: "60", cy: "15", r: "9", fillOpacity: "0.3" },
      R.createElement("animate", {
        attributeName: "r",
        from: "9",
        to: "9",
        begin: "0s",
        dur: "0.8s",
        values: "9;15;9",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "fill-opacity",
        from: "0.5",
        to: "0.5",
        begin: "0s",
        dur: "0.8s",
        values: ".5;1;.5",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    ),
    R.createElement(
      "circle",
      { cx: "105", cy: "15", r: "15" },
      R.createElement("animate", {
        attributeName: "r",
        from: "15",
        to: "15",
        begin: "0s",
        dur: "0.8s",
        values: "15;9;15",
        calcMode: "linear",
        repeatCount: "indefinite",
      }),
      R.createElement("animate", {
        attributeName: "fill-opacity",
        from: "1",
        to: "1",
        begin: "0s",
        dur: "0.8s",
        values: "1;.5;1",
        calcMode: "linear",
        repeatCount: "indefinite",
      })
    )
  );
}
var YD = Object.defineProperty,
  Tf = Object.getOwnPropertySymbols,
  jE = Object.prototype.hasOwnProperty,
  FE = Object.prototype.propertyIsEnumerable,
  Cb = (e, t, n) =>
    t in e
      ? YD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  GD = (e, t) => {
    for (var n in t || (t = {})) jE.call(t, n) && Cb(e, n, t[n]);
    if (Tf) for (var n of Tf(t)) FE.call(t, n) && Cb(e, n, t[n]);
    return e;
  },
  XD = (e, t) => {
    var n = {};
    for (var r in e) jE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Tf)
      for (var r of Tf(e)) t.indexOf(r) < 0 && FE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const yh = { bars: DD, oval: WD, dots: VD },
  KD = { xs: 18, sm: 22, md: 36, lg: 44, xl: 58 },
  QD = { size: "md" };
function pp(e) {
  const t = me("Loader", QD, e),
    { size: n, color: r, variant: o } = t,
    a = XD(t, ["size", "color", "variant"]),
    s = On(),
    u = o in yh ? o : s.loader;
  return R.createElement(
    Ie,
    GD(
      {
        role: "presentation",
        component: yh[u] || yh.bars,
        size: s.fn.size({ size: n, sizes: KD }),
        color: s.fn.variant({
          variant: "filled",
          primaryFallback: !1,
          color: r || s.primaryColor,
        }).background,
      },
      a
    )
  );
}
pp.displayName = "@mantine/core/Loader";
var qD = Object.defineProperty,
  zf = Object.getOwnPropertySymbols,
  WE = Object.prototype.hasOwnProperty,
  BE = Object.prototype.propertyIsEnumerable,
  kb = (e, t, n) =>
    t in e
      ? qD(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Rb = (e, t) => {
    for (var n in t || (t = {})) WE.call(t, n) && kb(e, n, t[n]);
    if (zf) for (var n of zf(t)) BE.call(t, n) && kb(e, n, t[n]);
    return e;
  },
  ZD = (e, t) => {
    var n = {};
    for (var r in e) WE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && zf)
      for (var r of zf(e)) t.indexOf(r) < 0 && BE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const JD = { color: "gray", size: "md", variant: "subtle", loading: !1 },
  UE = y.forwardRef((e, t) => {
    const n = me("ActionIcon", JD, e),
      {
        className: r,
        color: o,
        children: a,
        radius: s,
        size: u,
        variant: f,
        gradient: d,
        disabled: m,
        loaderProps: h,
        loading: v,
        unstyled: b,
      } = n,
      O = ZD(n, [
        "className",
        "color",
        "children",
        "radius",
        "size",
        "variant",
        "gradient",
        "disabled",
        "loaderProps",
        "loading",
        "unstyled",
      ]),
      {
        classes: E,
        cx: $,
        theme: _,
      } = TD(
        { size: u, radius: s, color: o, variant: f, gradient: d },
        { name: "ActionIcon", unstyled: b }
      ),
      w = _.fn.variant({ color: o, variant: f }),
      P = R.createElement(
        pp,
        Rb({ color: w.color, size: _.fn.size({ size: u, sizes: Gl }) - 12 }, h)
      );
    return R.createElement(
      NE,
      Rb(
        {
          className: $(E.root, r),
          ref: t,
          disabled: m,
          "data-disabled": m || void 0,
          "data-loading": v || void 0,
          unstyled: b,
        },
        O
      ),
      v ? P : a
    );
  });
UE.displayName = "@mantine/core/ActionIcon";
const eM = UE;
function P0(e) {
  const { children: t, target: n, className: r } = me("Portal", {}, e),
    o = On(),
    [a, s] = y.useState(!1),
    u = y.useRef();
  return (
    wE(
      () => (
        s(!0),
        (u.current = n
          ? typeof n == "string"
            ? document.querySelector(n)
            : n
          : document.createElement("div")),
        n || document.body.appendChild(u.current),
        () => {
          !n && document.body.removeChild(u.current);
        }
      ),
      [n]
    ),
    a
      ? Ja.createPortal(
          R.createElement("div", { className: r, dir: o.dir }, t),
          u.current
        )
      : null
  );
}
P0.displayName = "@mantine/core/Portal";
var tM = Object.defineProperty,
  Af = Object.getOwnPropertySymbols,
  HE = Object.prototype.hasOwnProperty,
  VE = Object.prototype.propertyIsEnumerable,
  Nb = (e, t, n) =>
    t in e
      ? tM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  nM = (e, t) => {
    for (var n in t || (t = {})) HE.call(t, n) && Nb(e, n, t[n]);
    if (Af) for (var n of Af(t)) VE.call(t, n) && Nb(e, n, t[n]);
    return e;
  },
  rM = (e, t) => {
    var n = {};
    for (var r in e) HE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Af)
      for (var r of Af(e)) t.indexOf(r) < 0 && VE.call(e, r) && (n[r] = e[r]);
    return n;
  };
function YE(e) {
  var t = e,
    { withinPortal: n = !0, children: r } = t,
    o = rM(t, ["withinPortal", "children"]);
  return n
    ? R.createElement(P0, nM({}, o), r)
    : R.createElement(R.Fragment, null, r);
}
YE.displayName = "@mantine/core/OptionalPortal";
var oM = Object.defineProperty,
  Ib = Object.getOwnPropertySymbols,
  iM = Object.prototype.hasOwnProperty,
  aM = Object.prototype.propertyIsEnumerable,
  Tb = (e, t, n) =>
    t in e
      ? oM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  lM = (e, t) => {
    for (var n in t || (t = {})) iM.call(t, n) && Tb(e, n, t[n]);
    if (Ib) for (var n of Ib(t)) aM.call(t, n) && Tb(e, n, t[n]);
    return e;
  };
function GE(e) {
  return R.createElement(
    "svg",
    lM(
      {
        viewBox: "0 0 15 15",
        fill: "none",
        xmlns: "http://www.w3.org/2000/svg",
      },
      e
    ),
    R.createElement("path", {
      d: "M11.7816 4.03157C12.0062 3.80702 12.0062 3.44295 11.7816 3.2184C11.5571 2.99385 11.193 2.99385 10.9685 3.2184L7.50005 6.68682L4.03164 3.2184C3.80708 2.99385 3.44301 2.99385 3.21846 3.2184C2.99391 3.44295 2.99391 3.80702 3.21846 4.03157L6.68688 7.49999L3.21846 10.9684C2.99391 11.193 2.99391 11.557 3.21846 11.7816C3.44301 12.0061 3.80708 12.0061 4.03164 11.7816L7.50005 8.31316L10.9685 11.7816C11.193 12.0061 11.5571 12.0061 11.7816 11.7816C12.0062 11.557 12.0062 11.193 11.7816 10.9684L8.31322 7.49999L11.7816 4.03157Z",
      fill: "currentColor",
      fillRule: "evenodd",
      clipRule: "evenodd",
    })
  );
}
GE.displayName = "@mantine/core/CloseIcon";
var sM = Object.defineProperty,
  Lf = Object.getOwnPropertySymbols,
  XE = Object.prototype.hasOwnProperty,
  KE = Object.prototype.propertyIsEnumerable,
  zb = (e, t, n) =>
    t in e
      ? sM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  uM = (e, t) => {
    for (var n in t || (t = {})) XE.call(t, n) && zb(e, n, t[n]);
    if (Lf) for (var n of Lf(t)) KE.call(t, n) && zb(e, n, t[n]);
    return e;
  },
  cM = (e, t) => {
    var n = {};
    for (var r in e) XE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Lf)
      for (var r of Lf(e)) t.indexOf(r) < 0 && KE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const fM = { xs: 12, sm: 14, md: 16, lg: 20, xl: 24 },
  dM = { size: "md" },
  QE = y.forwardRef((e, t) => {
    const n = me("CloseButton", dM, e),
      { iconSize: r, size: o = "md" } = n,
      a = cM(n, ["iconSize", "size"]),
      s = On(),
      u = r || s.fn.size({ size: o, sizes: fM });
    return R.createElement(
      eM,
      uM({ size: o, ref: t }, a),
      R.createElement(GE, { width: u, height: u })
    );
  });
QE.displayName = "@mantine/core/CloseButton";
const qE = QE;
var pM = Object.defineProperty,
  mM = Object.defineProperties,
  hM = Object.getOwnPropertyDescriptors,
  Ab = Object.getOwnPropertySymbols,
  vM = Object.prototype.hasOwnProperty,
  gM = Object.prototype.propertyIsEnumerable,
  Lb = (e, t, n) =>
    t in e
      ? pM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Fl = (e, t) => {
    for (var n in t || (t = {})) vM.call(t, n) && Lb(e, n, t[n]);
    if (Ab) for (var n of Ab(t)) gM.call(t, n) && Lb(e, n, t[n]);
    return e;
  },
  yM = (e, t) => mM(e, hM(t));
function _M({ underline: e, strikethrough: t }) {
  const n = [];
  return (
    e && n.push("underline"),
    t && n.push("line-through"),
    n.length > 0 ? n.join(" ") : "none"
  );
}
function wM({ theme: e, color: t, variant: n }) {
  return t === "dimmed"
    ? e.colorScheme === "dark"
      ? e.colors.dark[2]
      : e.colors.gray[6]
    : typeof t == "string" && (t in e.colors || t.split(".")[0] in e.colors)
    ? e.fn.variant({ variant: "filled", color: t }).background
    : n === "link"
    ? e.colors[e.primaryColor][e.colorScheme === "dark" ? 4 : 7]
    : t || "inherit";
}
function bM(e) {
  return typeof e == "number"
    ? {
        overflow: "hidden",
        textOverflow: "ellipsis",
        display: "-webkit-box",
        WebkitLineClamp: e,
        WebkitBoxOrient: "vertical",
      }
    : null;
}
function xM({ theme: e, truncate: t }) {
  return t === "start"
    ? {
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        direction: e.dir === "ltr" ? "rtl" : "ltr",
        textAlign: e.dir === "ltr" ? "right" : "left",
      }
    : t
    ? { overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }
    : null;
}
var SM = Pe(
  (
    e,
    {
      color: t,
      variant: n,
      size: r,
      lineClamp: o,
      truncate: a,
      inline: s,
      inherit: u,
      underline: f,
      gradient: d,
      weight: m,
      transform: h,
      align: v,
      strikethrough: b,
      italic: O,
    }
  ) => {
    const E = e.fn.variant({ variant: "gradient", gradient: d });
    return {
      root: Fl(
        yM(
          Fl(
            Fl(Fl(Fl({}, e.fn.fontStyles()), e.fn.focusStyles()), bM(o)),
            xM({ theme: e, truncate: a })
          ),
          {
            color: wM({ color: t, theme: e, variant: n }),
            fontFamily: u ? "inherit" : e.fontFamily,
            fontSize:
              u || r === void 0
                ? "inherit"
                : e.fn.size({ size: r, sizes: e.fontSizes }),
            lineHeight: u ? "inherit" : s ? 1 : e.lineHeight,
            textDecoration: _M({ underline: f, strikethrough: b }),
            WebkitTapHighlightColor: "transparent",
            fontWeight: u ? "inherit" : m,
            textTransform: h,
            textAlign: v,
            fontStyle: O ? "italic" : void 0,
          }
        ),
        e.fn.hover(
          n === "link" && f === void 0
            ? { textDecoration: "underline" }
            : void 0
        )
      ),
      gradient: {
        backgroundImage: E.background,
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
      },
    };
  }
);
const PM = SM;
var OM = Object.defineProperty,
  Df = Object.getOwnPropertySymbols,
  ZE = Object.prototype.hasOwnProperty,
  JE = Object.prototype.propertyIsEnumerable,
  Db = (e, t, n) =>
    t in e
      ? OM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  EM = (e, t) => {
    for (var n in t || (t = {})) ZE.call(t, n) && Db(e, n, t[n]);
    if (Df) for (var n of Df(t)) JE.call(t, n) && Db(e, n, t[n]);
    return e;
  },
  $M = (e, t) => {
    var n = {};
    for (var r in e) ZE.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Df)
      for (var r of Df(e)) t.indexOf(r) < 0 && JE.call(e, r) && (n[r] = e[r]);
    return n;
  };
const CM = { variant: "text" },
  e$ = y.forwardRef((e, t) => {
    const n = me("Text", CM, e),
      {
        className: r,
        size: o,
        weight: a,
        transform: s,
        color: u,
        align: f,
        variant: d,
        lineClamp: m,
        truncate: h,
        gradient: v,
        inline: b,
        inherit: O,
        underline: E,
        strikethrough: $,
        italic: _,
        classNames: w,
        styles: P,
        unstyled: k,
        span: I,
      } = n,
      z = $M(n, [
        "className",
        "size",
        "weight",
        "transform",
        "color",
        "align",
        "variant",
        "lineClamp",
        "truncate",
        "gradient",
        "inline",
        "inherit",
        "underline",
        "strikethrough",
        "italic",
        "classNames",
        "styles",
        "unstyled",
        "span",
      ]),
      { classes: A, cx: M } = PM(
        {
          variant: d,
          color: u,
          size: o,
          lineClamp: m,
          truncate: h,
          inline: b,
          inherit: O,
          underline: E,
          strikethrough: $,
          italic: _,
          weight: a,
          transform: s,
          align: f,
          gradient: v,
        },
        { unstyled: k, name: "Text" }
      );
    return R.createElement(
      Ie,
      EM(
        {
          ref: t,
          className: M(A.root, { [A.gradient]: d === "gradient" }, r),
          component: I ? "span" : "div",
        },
        z
      )
    );
  });
e$.displayName = "@mantine/core/Text";
const Jr = e$;
var kM = Pe(() => ({
  root: {
    backgroundColor: "transparent",
    cursor: "pointer",
    padding: 0,
    border: 0,
  },
}));
const RM = kM;
var NM = Object.defineProperty,
  Mf = Object.getOwnPropertySymbols,
  t$ = Object.prototype.hasOwnProperty,
  n$ = Object.prototype.propertyIsEnumerable,
  Mb = (e, t, n) =>
    t in e
      ? NM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  jb = (e, t) => {
    for (var n in t || (t = {})) t$.call(t, n) && Mb(e, n, t[n]);
    if (Mf) for (var n of Mf(t)) n$.call(t, n) && Mb(e, n, t[n]);
    return e;
  },
  IM = (e, t) => {
    var n = {};
    for (var r in e) t$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Mf)
      for (var r of Mf(e)) t.indexOf(r) < 0 && n$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const TM = {},
  r$ = y.forwardRef((e, t) => {
    const n = me("Anchor", TM, e),
      { component: r, className: o, unstyled: a } = n,
      s = IM(n, ["component", "className", "unstyled"]),
      { classes: u, cx: f } = RM(null, { name: "Anchor", unstyled: a }),
      d = r === "button" ? { type: "button" } : null;
    return R.createElement(
      Jr,
      jb(
        jb(
          {
            component: r || "a",
            variant: "link",
            ref: t,
            className: f(u.root, o),
          },
          d
        ),
        s
      )
    );
  });
r$.displayName = "@mantine/core/Anchor";
const vK = r$,
  o$ = y.createContext({ zIndex: 1e3, fixed: !1, layout: "default" }),
  zM = o$.Provider;
function AM() {
  return y.useContext(o$);
}
function i$(e, t) {
  if (!e) return [];
  const n = Object.keys(e)
    .filter((r) => r !== "base")
    .map((r) => [t.fn.size({ size: r, sizes: t.breakpoints }), e[r]]);
  return n.sort((r, o) => r[0] - o[0]), n;
}
var LM = Object.defineProperty,
  DM = Object.defineProperties,
  MM = Object.getOwnPropertyDescriptors,
  Fb = Object.getOwnPropertySymbols,
  jM = Object.prototype.hasOwnProperty,
  FM = Object.prototype.propertyIsEnumerable,
  Wb = (e, t, n) =>
    t in e
      ? LM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  _h = (e, t) => {
    for (var n in t || (t = {})) jM.call(t, n) && Wb(e, n, t[n]);
    if (Fb) for (var n of Fb(t)) FM.call(t, n) && Wb(e, n, t[n]);
    return e;
  },
  Bb = (e, t) => DM(e, MM(t)),
  WM = Pe(
    (
      e,
      {
        height: t,
        fixed: n,
        position: r,
        zIndex: o,
        borderPosition: a,
        layout: s,
      }
    ) => {
      const u =
        typeof t == "object" && t !== null
          ? i$(t, e).reduce(
              (f, [d, m]) => (
                (f[`@media (min-width: ${d}px)`] = { height: m, minHeight: m }),
                f
              ),
              {}
            )
          : null;
      return {
        root: Bb(
          _h(
            Bb(_h(_h({}, e.fn.fontStyles()), r), {
              zIndex: o,
              left: s === "alt" ? "var(--mantine-navbar-width, 0)" : 0,
              right: s === "alt" ? "var(--mantine-aside-width, 0)" : 0,
              height:
                typeof t == "object"
                  ? (t == null ? void 0 : t.base) || "100%"
                  : t,
              maxHeight:
                typeof t == "object"
                  ? (t == null ? void 0 : t.base) || "100%"
                  : t,
              position: n ? "fixed" : "static",
              boxSizing: "border-box",
              backgroundColor:
                e.colorScheme === "dark" ? e.colors.dark[7] : e.white,
            }),
            u
          ),
          {
            borderBottom:
              a === "bottom"
                ? `1px solid ${
                    e.colorScheme === "dark"
                      ? e.colors.dark[5]
                      : e.colors.gray[2]
                  }`
                : void 0,
            borderTop:
              a === "top"
                ? `1px solid ${
                    e.colorScheme === "dark"
                      ? e.colors.dark[5]
                      : e.colors.gray[2]
                  }`
                : void 0,
          }
        ),
      };
    }
  );
const BM = WM;
var UM = Object.defineProperty,
  jf = Object.getOwnPropertySymbols,
  a$ = Object.prototype.hasOwnProperty,
  l$ = Object.prototype.propertyIsEnumerable,
  Ub = (e, t, n) =>
    t in e
      ? UM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Hb = (e, t) => {
    for (var n in t || (t = {})) a$.call(t, n) && Ub(e, n, t[n]);
    if (jf) for (var n of jf(t)) l$.call(t, n) && Ub(e, n, t[n]);
    return e;
  },
  HM = (e, t) => {
    var n = {};
    for (var r in e) a$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && jf)
      for (var r of jf(e)) t.indexOf(r) < 0 && l$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const s$ = y.forwardRef((e, t) => {
  var n = e,
    {
      children: r,
      className: o,
      classNames: a,
      styles: s,
      height: u,
      fixed: f = !1,
      withBorder: d = !0,
      position: m,
      zIndex: h,
      section: v,
      unstyled: b,
      __staticSelector: O,
    } = n,
    E = HM(n, [
      "children",
      "className",
      "classNames",
      "styles",
      "height",
      "fixed",
      "withBorder",
      "position",
      "zIndex",
      "section",
      "unstyled",
      "__staticSelector",
    ]);
  const $ = AM(),
    _ = h || $.zIndex || fp("app"),
    {
      classes: w,
      cx: P,
      theme: k,
    } = BM(
      {
        height: u,
        fixed: $.fixed || f,
        position: m,
        zIndex: typeof _ == "number" && $.layout === "default" ? _ + 1 : _,
        layout: $.layout,
        borderPosition: d ? (v === "header" ? "bottom" : "top") : "none",
      },
      { name: O, classNames: a, styles: s, unstyled: b }
    ),
    I =
      typeof u == "object" && u !== null
        ? i$(u, k).reduce(
            (z, [A, M]) => (
              (z[`@media (min-width: ${A}px)`] = {
                [`--mantine-${v}-height`]: `${M}px`,
              }),
              z
            ),
            {}
          )
        : null;
  return R.createElement(
    Ie,
    Hb(
      {
        component: v === "header" ? "header" : "footer",
        className: P(w.root, o),
        ref: t,
      },
      E
    ),
    r,
    R.createElement(vL, {
      styles: () => ({
        ":root": Hb(
          {
            [`--mantine-${v}-height`]:
              typeof u == "object"
                ? `${u == null ? void 0 : u.base}px` || "100%"
                : `${u}px`,
          },
          I
        ),
      }),
    })
  );
});
s$.displayName = "@mantine/core/VerticalSection";
var VM = Object.defineProperty,
  YM = Object.defineProperties,
  GM = Object.getOwnPropertyDescriptors,
  Vb = Object.getOwnPropertySymbols,
  XM = Object.prototype.hasOwnProperty,
  KM = Object.prototype.propertyIsEnumerable,
  Yb = (e, t, n) =>
    t in e
      ? VM(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  QM = (e, t) => {
    for (var n in t || (t = {})) XM.call(t, n) && Yb(e, n, t[n]);
    if (Vb) for (var n of Vb(t)) KM.call(t, n) && Yb(e, n, t[n]);
    return e;
  },
  qM = (e, t) => YM(e, GM(t));
const ZM = { fixed: !1, position: { top: 0, left: 0, right: 0 } },
  JM = y.forwardRef((e, t) => {
    const n = me("Header", ZM, e);
    return R.createElement(
      s$,
      qM(QM({ section: "header", __staticSelector: "Header" }, n), { ref: t })
    );
  });
JM.displayName = "@mantine/core/Header";
var ej = Object.defineProperty,
  Gb = Object.getOwnPropertySymbols,
  tj = Object.prototype.hasOwnProperty,
  nj = Object.prototype.propertyIsEnumerable,
  Xb = (e, t, n) =>
    t in e
      ? ej(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  rj = (e, t) => {
    for (var n in t || (t = {})) tj.call(t, n) && Xb(e, n, t[n]);
    if (Gb) for (var n of Gb(t)) nj.call(t, n) && Xb(e, n, t[n]);
    return e;
  };
function oj(e, t) {
  const n = t.fn.size({ size: e.padding, sizes: t.spacing }),
    r = e.navbarOffsetBreakpoint
      ? t.fn.size({ size: e.navbarOffsetBreakpoint, sizes: t.breakpoints })
      : null,
    o = e.asideOffsetBreakpoint
      ? t.fn.size({ size: e.asideOffsetBreakpoint, sizes: t.breakpoints })
      : null;
  return e.fixed
    ? {
        minHeight: "100vh",
        paddingTop: `calc(var(--mantine-header-height, 0px) + ${n}px)`,
        paddingBottom: `calc(var(--mantine-footer-height, 0px) + ${n}px)`,
        paddingLeft: `calc(var(--mantine-navbar-width, 0px) + ${n}px)`,
        paddingRight: `calc(var(--mantine-aside-width, 0px) + ${n}px)`,
        [`@media (max-width: ${r - 1}px)`]: { paddingLeft: n },
        [`@media (max-width: ${o - 1}px)`]: { paddingRight: n },
      }
    : { padding: n };
}
var ij = Pe((e, t) => ({
  root: { boxSizing: "border-box" },
  body: { display: "flex", boxSizing: "border-box" },
  main: rj({ flex: 1, width: "100vw", boxSizing: "border-box" }, oj(t, e)),
}));
const aj = ij;
var lj = Object.defineProperty,
  Ff = Object.getOwnPropertySymbols,
  u$ = Object.prototype.hasOwnProperty,
  c$ = Object.prototype.propertyIsEnumerable,
  Kb = (e, t, n) =>
    t in e
      ? lj(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  sj = (e, t) => {
    for (var n in t || (t = {})) u$.call(t, n) && Kb(e, n, t[n]);
    if (Ff) for (var n of Ff(t)) c$.call(t, n) && Kb(e, n, t[n]);
    return e;
  },
  uj = (e, t) => {
    var n = {};
    for (var r in e) u$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Ff)
      for (var r of Ff(e)) t.indexOf(r) < 0 && c$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const cj = { fixed: !0, padding: "md" },
  fj = y.forwardRef((e, t) => {
    const n = me("AppShell", cj, e),
      {
        children: r,
        navbar: o,
        header: a,
        footer: s,
        aside: u,
        fixed: f,
        zIndex: d,
        padding: m,
        navbarOffsetBreakpoint: h,
        asideOffsetBreakpoint: v,
        className: b,
        styles: O,
        classNames: E,
        unstyled: $,
        hidden: _,
        layout: w,
      } = n,
      P = uj(n, [
        "children",
        "navbar",
        "header",
        "footer",
        "aside",
        "fixed",
        "zIndex",
        "padding",
        "navbarOffsetBreakpoint",
        "asideOffsetBreakpoint",
        "className",
        "styles",
        "classNames",
        "unstyled",
        "hidden",
        "layout",
      ]),
      { classes: k, cx: I } = aj(
        {
          padding: m,
          fixed: f,
          navbarOffsetBreakpoint: h,
          asideOffsetBreakpoint: v,
        },
        { styles: O, classNames: E, unstyled: $, name: "AppShell" }
      );
    return _
      ? R.createElement(R.Fragment, null, r)
      : R.createElement(
          zM,
          { value: { fixed: f, zIndex: d, layout: w } },
          R.createElement(
            Ie,
            sj({ className: I(k.root, b), ref: t }, P),
            a,
            R.createElement(
              "div",
              { className: k.body },
              o,
              R.createElement("main", { className: k.main }, r),
              u
            ),
            s
          )
        );
  });
fj.displayName = "@mantine/core/AppShell";
const dc = { xs: 1, sm: 2, md: 3, lg: 4, xl: 5 };
function pc(e, t) {
  const n = e.fn.variant({ variant: "outline", color: t }).border;
  return typeof t == "string" && (t in e.colors || t.split(".")[0] in e.colors)
    ? n
    : t === void 0
    ? e.colorScheme === "dark"
      ? e.colors.dark[4]
      : e.colors.gray[4]
    : t;
}
var dj = Pe((e, { size: t, variant: n, color: r }) => ({
  root: {},
  withLabel: { borderTop: "0 !important" },
  left: { "&::before": { display: "none" } },
  right: { "&::after": { display: "none" } },
  label: {
    display: "flex",
    alignItems: "center",
    "&::before": {
      content: '""',
      flex: 1,
      height: 1,
      borderTop: `${e.fn.size({ size: t, sizes: dc })}px ${n} ${pc(e, r)}`,
      marginRight: e.spacing.xs,
    },
    "&::after": {
      content: '""',
      flex: 1,
      borderTop: `${e.fn.size({ size: t, sizes: dc })}px ${n} ${pc(e, r)}`,
      marginLeft: e.spacing.xs,
    },
  },
  labelDefaultStyles: {
    color:
      r === "dark"
        ? e.colors.dark[1]
        : e.fn.themeColor(
            r,
            e.colorScheme === "dark" ? 5 : e.fn.primaryShade(),
            !1
          ),
  },
  horizontal: {
    border: 0,
    borderTopWidth: e.fn.size({ size: t, sizes: dc }),
    borderTopColor: pc(e, r),
    borderTopStyle: n,
    margin: 0,
  },
  vertical: {
    border: 0,
    alignSelf: "stretch",
    height: "auto",
    borderLeftWidth: e.fn.size({ size: t, sizes: dc }),
    borderLeftColor: pc(e, r),
    borderLeftStyle: n,
  },
}));
const pj = dj;
var mj = Object.defineProperty,
  hj = Object.defineProperties,
  vj = Object.getOwnPropertyDescriptors,
  Wf = Object.getOwnPropertySymbols,
  f$ = Object.prototype.hasOwnProperty,
  d$ = Object.prototype.propertyIsEnumerable,
  Qb = (e, t, n) =>
    t in e
      ? mj(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  qb = (e, t) => {
    for (var n in t || (t = {})) f$.call(t, n) && Qb(e, n, t[n]);
    if (Wf) for (var n of Wf(t)) d$.call(t, n) && Qb(e, n, t[n]);
    return e;
  },
  gj = (e, t) => hj(e, vj(t)),
  yj = (e, t) => {
    var n = {};
    for (var r in e) f$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Wf)
      for (var r of Wf(e)) t.indexOf(r) < 0 && d$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const _j = {
    orientation: "horizontal",
    size: "xs",
    labelPosition: "left",
    variant: "solid",
  },
  Mv = y.forwardRef((e, t) => {
    const n = me("Divider", _j, e),
      {
        className: r,
        color: o,
        orientation: a,
        size: s,
        label: u,
        labelPosition: f,
        labelProps: d,
        variant: m,
        styles: h,
        classNames: v,
        unstyled: b,
      } = n,
      O = yj(n, [
        "className",
        "color",
        "orientation",
        "size",
        "label",
        "labelPosition",
        "labelProps",
        "variant",
        "styles",
        "classNames",
        "unstyled",
      ]),
      { classes: E, cx: $ } = pj(
        { color: o, size: s, variant: m },
        { classNames: v, styles: h, unstyled: b, name: "Divider" }
      ),
      _ = a === "vertical",
      w = a === "horizontal",
      P = !!u && w,
      k = !(d != null && d.color);
    return R.createElement(
      Ie,
      qb(
        {
          ref: t,
          className: $(
            E.root,
            { [E.vertical]: _, [E.horizontal]: w, [E.withLabel]: P },
            r
          ),
          role: "separator",
        },
        O
      ),
      P &&
        R.createElement(
          Jr,
          gj(qb({}, d), {
            size: (d == null ? void 0 : d.size) || "xs",
            sx: { marginTop: 2 },
            className: $(E.label, E[f], { [E.labelDefaultStyles]: k }),
          }),
          u
        )
    );
  });
Mv.displayName = "@mantine/core/Divider";
var wj = Object.defineProperty,
  bj = Object.defineProperties,
  xj = Object.getOwnPropertyDescriptors,
  Zb = Object.getOwnPropertySymbols,
  Sj = Object.prototype.hasOwnProperty,
  Pj = Object.prototype.propertyIsEnumerable,
  Jb = (e, t, n) =>
    t in e
      ? wj(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ex = (e, t) => {
    for (var n in t || (t = {})) Sj.call(t, n) && Jb(e, n, t[n]);
    if (Zb) for (var n of Zb(t)) Pj.call(t, n) && Jb(e, n, t[n]);
    return e;
  },
  Oj = (e, t) => bj(e, xj(t)),
  Ej = Pe((e, { size: t }) => ({
    item: Oj(ex({}, e.fn.fontStyles()), {
      boxSizing: "border-box",
      textAlign: "left",
      width: "100%",
      padding: `${e.fn.size({ size: t, sizes: e.spacing }) / 1.5}px ${e.fn.size(
        { size: t, sizes: e.spacing }
      )}px`,
      cursor: "pointer",
      fontSize: e.fn.size({ size: t, sizes: e.fontSizes }),
      color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
      borderRadius: e.fn.radius(),
      "&[data-hovered]": {
        backgroundColor:
          e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[1],
      },
      "&[data-selected]": ex(
        {
          backgroundColor: e.fn.variant({ variant: "filled" }).background,
          color: e.fn.variant({ variant: "filled" }).color,
        },
        e.fn.hover({
          backgroundColor: e.fn.variant({ variant: "filled" }).hover,
        })
      ),
      "&[data-disabled]": { cursor: "default", color: e.colors.dark[2] },
    }),
    nothingFound: {
      boxSizing: "border-box",
      color: e.colors.gray[6],
      paddingTop: e.fn.size({ size: t, sizes: e.spacing }) / 2,
      paddingBottom: e.fn.size({ size: t, sizes: e.spacing }) / 2,
      textAlign: "center",
    },
    separator: {
      boxSizing: "border-box",
      textAlign: "left",
      width: "100%",
      padding: `${e.fn.size({ size: t, sizes: e.spacing }) / 1.5}px ${e.fn.size(
        { size: t, sizes: e.spacing }
      )}px`,
    },
    separatorLabel: {
      color: e.colorScheme === "dark" ? e.colors.dark[3] : e.colors.gray[5],
    },
  }));
const $j = Ej;
var Cj = Object.defineProperty,
  tx = Object.getOwnPropertySymbols,
  kj = Object.prototype.hasOwnProperty,
  Rj = Object.prototype.propertyIsEnumerable,
  nx = (e, t, n) =>
    t in e
      ? Cj(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Nj = (e, t) => {
    for (var n in t || (t = {})) kj.call(t, n) && nx(e, n, t[n]);
    if (tx) for (var n of tx(t)) Rj.call(t, n) && nx(e, n, t[n]);
    return e;
  };
function p$({
  data: e,
  hovered: t,
  classNames: n,
  styles: r,
  isItemSelected: o,
  uuid: a,
  __staticSelector: s,
  onItemHover: u,
  onItemSelect: f,
  itemsRefs: d,
  itemComponent: m,
  size: h,
  nothingFound: v,
  creatable: b,
  createLabel: O,
  unstyled: E,
}) {
  const { classes: $ } = $j(
      { size: h },
      { classNames: n, styles: r, unstyled: E, name: s }
    ),
    _ = [],
    w = [];
  let P = null;
  const k = (z, A) => {
    const M = typeof o == "function" ? o(z.value) : !1;
    return R.createElement(
      m,
      Nj(
        {
          key: z.value,
          className: $.item,
          "data-disabled": z.disabled || void 0,
          "data-hovered": (!z.disabled && t === A) || void 0,
          "data-selected": (!z.disabled && M) || void 0,
          selected: M,
          onMouseEnter: () => u(A),
          id: `${a}-${A}`,
          role: "option",
          tabIndex: -1,
          "aria-selected": t === A,
          ref: (B) => {
            d && d.current && (d.current[z.value] = B);
          },
          onMouseDown: z.disabled
            ? null
            : (B) => {
                B.preventDefault(), f(z);
              },
          disabled: z.disabled,
        },
        z
      )
    );
  };
  let I = null;
  if (
    (e.forEach((z, A) => {
      z.creatable
        ? (P = A)
        : z.group
        ? (I !== z.group &&
            ((I = z.group),
            w.push(
              R.createElement(
                "div",
                { className: $.separator, key: `__mantine-divider-${A}` },
                R.createElement(Mv, {
                  classNames: { label: $.separatorLabel },
                  label: z.group,
                })
              )
            )),
          w.push(k(z, A)))
        : _.push(k(z, A));
    }),
    b)
  ) {
    const z = e[P];
    _.push(
      R.createElement(
        "div",
        {
          key: PE(),
          className: $.item,
          "data-hovered": t === P || void 0,
          onMouseEnter: () => u(P),
          onMouseDown: (A) => {
            A.preventDefault(), f(z);
          },
          tabIndex: -1,
          ref: (A) => {
            d && d.current && (d.current[z.value] = A);
          },
        },
        O
      )
    );
  }
  return (
    w.length > 0 &&
      _.length > 0 &&
      _.unshift(
        R.createElement(
          "div",
          { className: $.separator, key: "empty-group-separator" },
          R.createElement(Mv, null)
        )
      ),
    w.length > 0 || _.length > 0
      ? R.createElement(R.Fragment, null, w, _)
      : R.createElement(
          Jr,
          { size: h, unstyled: E, className: $.nothingFound },
          v
        )
  );
}
p$.displayName = "@mantine/core/SelectItems";
var Ij = Object.defineProperty,
  Bf = Object.getOwnPropertySymbols,
  m$ = Object.prototype.hasOwnProperty,
  h$ = Object.prototype.propertyIsEnumerable,
  rx = (e, t, n) =>
    t in e
      ? Ij(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Tj = (e, t) => {
    for (var n in t || (t = {})) m$.call(t, n) && rx(e, n, t[n]);
    if (Bf) for (var n of Bf(t)) h$.call(t, n) && rx(e, n, t[n]);
    return e;
  },
  zj = (e, t) => {
    var n = {};
    for (var r in e) m$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Bf)
      for (var r of Bf(e)) t.indexOf(r) < 0 && h$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const v$ = y.forwardRef((e, t) => {
  var n = e,
    { label: r, value: o } = n,
    a = zj(n, ["label", "value"]);
  return R.createElement("div", Tj({ ref: t }, a), r || o);
});
v$.displayName = "@mantine/core/DefaultItem";
function Aj(e, t) {
  typeof e == "function" ? e(t) : e != null && (e.current = t);
}
function g$(...e) {
  return (t) => e.forEach((n) => Aj(n, t));
}
function Mi(...e) {
  return y.useCallback(g$(...e), e);
}
const y$ = y.forwardRef((e, t) => {
  const { children: n, ...r } = e,
    o = y.Children.toArray(n),
    a = o.find(Dj);
  if (a) {
    const s = a.props.children,
      u = o.map((f) =>
        f === a
          ? y.Children.count(s) > 1
            ? y.Children.only(null)
            : y.isValidElement(s)
            ? s.props.children
            : null
          : f
      );
    return y.createElement(
      jv,
      je({}, r, { ref: t }),
      y.isValidElement(s) ? y.cloneElement(s, void 0, u) : null
    );
  }
  return y.createElement(jv, je({}, r, { ref: t }), n);
});
y$.displayName = "Slot";
const jv = y.forwardRef((e, t) => {
  const { children: n, ...r } = e;
  return y.isValidElement(n)
    ? y.cloneElement(n, { ...Mj(r, n.props), ref: g$(t, n.ref) })
    : y.Children.count(n) > 1
    ? y.Children.only(null)
    : null;
});
jv.displayName = "SlotClone";
const Lj = ({ children: e }) => y.createElement(y.Fragment, null, e);
function Dj(e) {
  return y.isValidElement(e) && e.type === Lj;
}
function Mj(e, t) {
  const n = { ...t };
  for (const r in t) {
    const o = e[r],
      a = t[r];
    /^on[A-Z]/.test(r)
      ? o && a
        ? (n[r] = (...u) => {
            a(...u), o(...u);
          })
        : o && (n[r] = o)
      : r === "style"
      ? (n[r] = { ...o, ...a })
      : r === "className" && (n[r] = [o, a].filter(Boolean).join(" "));
  }
  return { ...e, ...n };
}
const jj = [
    "a",
    "button",
    "div",
    "h2",
    "h3",
    "img",
    "label",
    "li",
    "nav",
    "ol",
    "p",
    "span",
    "svg",
    "ul",
  ],
  Ks = jj.reduce((e, t) => {
    const n = y.forwardRef((r, o) => {
      const { asChild: a, ...s } = r,
        u = a ? y$ : t;
      return (
        y.useEffect(() => {
          window[Symbol.for("radix-ui")] = !0;
        }, []),
        y.createElement(u, je({}, s, { ref: o }))
      );
    });
    return (n.displayName = `Primitive.${t}`), { ...e, [t]: n };
  }, {}),
  Fv = globalThis != null && globalThis.document ? y.useLayoutEffect : () => {};
function Fj(e, t) {
  return y.useReducer((n, r) => {
    const o = t[n][r];
    return o ?? n;
  }, e);
}
const Qs = (e) => {
  const { present: t, children: n } = e,
    r = Wj(t),
    o =
      typeof n == "function" ? n({ present: r.isPresent }) : y.Children.only(n),
    a = Mi(r.ref, o.ref);
  return typeof n == "function" || r.isPresent
    ? y.cloneElement(o, { ref: a })
    : null;
};
Qs.displayName = "Presence";
function Wj(e) {
  const [t, n] = y.useState(),
    r = y.useRef({}),
    o = y.useRef(e),
    a = y.useRef("none"),
    s = e ? "mounted" : "unmounted",
    [u, f] = Fj(s, {
      mounted: { UNMOUNT: "unmounted", ANIMATION_OUT: "unmountSuspended" },
      unmountSuspended: { MOUNT: "mounted", ANIMATION_END: "unmounted" },
      unmounted: { MOUNT: "mounted" },
    });
  return (
    y.useEffect(() => {
      const d = mc(r.current);
      a.current = u === "mounted" ? d : "none";
    }, [u]),
    Fv(() => {
      const d = r.current,
        m = o.current;
      if (m !== e) {
        const v = a.current,
          b = mc(d);
        e
          ? f("MOUNT")
          : b === "none" || (d == null ? void 0 : d.display) === "none"
          ? f("UNMOUNT")
          : f(m && v !== b ? "ANIMATION_OUT" : "UNMOUNT"),
          (o.current = e);
      }
    }, [e, f]),
    Fv(() => {
      if (t) {
        const d = (h) => {
            const b = mc(r.current).includes(h.animationName);
            h.target === t && b && Ja.flushSync(() => f("ANIMATION_END"));
          },
          m = (h) => {
            h.target === t && (a.current = mc(r.current));
          };
        return (
          t.addEventListener("animationstart", m),
          t.addEventListener("animationcancel", d),
          t.addEventListener("animationend", d),
          () => {
            t.removeEventListener("animationstart", m),
              t.removeEventListener("animationcancel", d),
              t.removeEventListener("animationend", d);
          }
        );
      } else f("ANIMATION_END");
    }, [t, f]),
    {
      isPresent: ["mounted", "unmountSuspended"].includes(u),
      ref: y.useCallback((d) => {
        d && (r.current = getComputedStyle(d)), n(d);
      }, []),
    }
  );
}
function mc(e) {
  return (e == null ? void 0 : e.animationName) || "none";
}
function Bj(e, t = []) {
  let n = [];
  function r(a, s) {
    const u = y.createContext(s),
      f = n.length;
    n = [...n, s];
    function d(h) {
      const { scope: v, children: b, ...O } = h,
        E = (v == null ? void 0 : v[e][f]) || u,
        $ = y.useMemo(() => O, Object.values(O));
      return y.createElement(E.Provider, { value: $ }, b);
    }
    function m(h, v) {
      const b = (v == null ? void 0 : v[e][f]) || u,
        O = y.useContext(b);
      if (O) return O;
      if (s !== void 0) return s;
      throw new Error(`\`${h}\` must be used within \`${a}\``);
    }
    return (d.displayName = a + "Provider"), [d, m];
  }
  const o = () => {
    const a = n.map((s) => y.createContext(s));
    return function (u) {
      const f = (u == null ? void 0 : u[e]) || a;
      return y.useMemo(() => ({ [`__scope${e}`]: { ...u, [e]: f } }), [u, f]);
    };
  };
  return (o.scopeName = e), [r, Uj(o, ...t)];
}
function Uj(...e) {
  const t = e[0];
  if (e.length === 1) return t;
  const n = () => {
    const r = e.map((o) => ({ useScope: o(), scopeName: o.scopeName }));
    return function (a) {
      const s = r.reduce((u, { useScope: f, scopeName: d }) => {
        const h = f(a)[`__scope${d}`];
        return { ...u, ...h };
      }, {});
      return y.useMemo(() => ({ [`__scope${t.scopeName}`]: s }), [s]);
    };
  };
  return (n.scopeName = t.scopeName), n;
}
function gi(e) {
  const t = y.useRef(e);
  return (
    y.useEffect(() => {
      t.current = e;
    }),
    y.useMemo(
      () =>
        (...n) => {
          var r;
          return (r = t.current) === null || r === void 0
            ? void 0
            : r.call(t, ...n);
        },
      []
    )
  );
}
const Hj = y.createContext(void 0);
function Vj(e) {
  const t = y.useContext(Hj);
  return e || t || "ltr";
}
function Yj(e, [t, n]) {
  return Math.min(n, Math.max(t, e));
}
function $i(e, t, { checkForDefaultPrevented: n = !0 } = {}) {
  return function (o) {
    if ((e == null || e(o), n === !1 || !o.defaultPrevented))
      return t == null ? void 0 : t(o);
  };
}
function Gj(e, t) {
  return y.useReducer((n, r) => {
    const o = t[n][r];
    return o ?? n;
  }, e);
}
const _$ = "ScrollArea",
  [w$, gK] = Bj(_$),
  [Xj, Hn] = w$(_$),
  Kj = y.forwardRef((e, t) => {
    const {
        __scopeScrollArea: n,
        type: r = "hover",
        dir: o,
        scrollHideDelay: a = 600,
        ...s
      } = e,
      [u, f] = y.useState(null),
      [d, m] = y.useState(null),
      [h, v] = y.useState(null),
      [b, O] = y.useState(null),
      [E, $] = y.useState(null),
      [_, w] = y.useState(0),
      [P, k] = y.useState(0),
      [I, z] = y.useState(!1),
      [A, M] = y.useState(!1),
      B = Mi(t, (q) => f(q)),
      H = Vj(o);
    return y.createElement(
      Xj,
      {
        scope: n,
        type: r,
        dir: H,
        scrollHideDelay: a,
        scrollArea: u,
        viewport: d,
        onViewportChange: m,
        content: h,
        onContentChange: v,
        scrollbarX: b,
        onScrollbarXChange: O,
        scrollbarXEnabled: I,
        onScrollbarXEnabledChange: z,
        scrollbarY: E,
        onScrollbarYChange: $,
        scrollbarYEnabled: A,
        onScrollbarYEnabledChange: M,
        onCornerWidthChange: w,
        onCornerHeightChange: k,
      },
      y.createElement(
        Ks.div,
        je({ dir: H }, s, {
          ref: B,
          style: {
            position: "relative",
            ["--radix-scroll-area-corner-width"]: _ + "px",
            ["--radix-scroll-area-corner-height"]: P + "px",
            ...e.style,
          },
        })
      )
    );
  }),
  Qj = "ScrollAreaViewport",
  qj = y.forwardRef((e, t) => {
    const { __scopeScrollArea: n, children: r, ...o } = e,
      a = Hn(Qj, n),
      s = y.useRef(null),
      u = Mi(t, s, a.onViewportChange);
    return y.createElement(
      y.Fragment,
      null,
      y.createElement("style", {
        dangerouslySetInnerHTML: {
          __html:
            "[data-radix-scroll-area-viewport]{scrollbar-width:none;-ms-overflow-style:none;-webkit-overflow-scrolling:touch;}[data-radix-scroll-area-viewport]::-webkit-scrollbar{display:none}",
        },
      }),
      y.createElement(
        Ks.div,
        je({ "data-radix-scroll-area-viewport": "" }, o, {
          ref: u,
          style: {
            overflowX: a.scrollbarXEnabled ? "scroll" : "hidden",
            overflowY: a.scrollbarYEnabled ? "scroll" : "hidden",
            ...e.style,
          },
        }),
        y.createElement(
          "div",
          {
            ref: a.onContentChange,
            style: { minWidth: "100%", display: "table" },
          },
          r
        )
      )
    );
  }),
  ao = "ScrollAreaScrollbar",
  Zj = y.forwardRef((e, t) => {
    const { forceMount: n, ...r } = e,
      o = Hn(ao, e.__scopeScrollArea),
      { onScrollbarXEnabledChange: a, onScrollbarYEnabledChange: s } = o,
      u = e.orientation === "horizontal";
    return (
      y.useEffect(
        () => (
          u ? a(!0) : s(!0),
          () => {
            u ? a(!1) : s(!1);
          }
        ),
        [u, a, s]
      ),
      o.type === "hover"
        ? y.createElement(Jj, je({}, r, { ref: t, forceMount: n }))
        : o.type === "scroll"
        ? y.createElement(eF, je({}, r, { ref: t, forceMount: n }))
        : o.type === "auto"
        ? y.createElement(b$, je({}, r, { ref: t, forceMount: n }))
        : o.type === "always"
        ? y.createElement(O0, je({}, r, { ref: t }))
        : null
    );
  }),
  Jj = y.forwardRef((e, t) => {
    const { forceMount: n, ...r } = e,
      o = Hn(ao, e.__scopeScrollArea),
      [a, s] = y.useState(!1);
    return (
      y.useEffect(() => {
        const u = o.scrollArea;
        let f = 0;
        if (u) {
          const d = () => {
              window.clearTimeout(f), s(!0);
            },
            m = () => {
              f = window.setTimeout(() => s(!1), o.scrollHideDelay);
            };
          return (
            u.addEventListener("pointerenter", d),
            u.addEventListener("pointerleave", m),
            () => {
              window.clearTimeout(f),
                u.removeEventListener("pointerenter", d),
                u.removeEventListener("pointerleave", m);
            }
          );
        }
      }, [o.scrollArea, o.scrollHideDelay]),
      y.createElement(
        Qs,
        { present: n || a },
        y.createElement(
          b$,
          je({ "data-state": a ? "visible" : "hidden" }, r, { ref: t })
        )
      )
    );
  }),
  eF = y.forwardRef((e, t) => {
    const { forceMount: n, ...r } = e,
      o = Hn(ao, e.__scopeScrollArea),
      a = e.orientation === "horizontal",
      s = hp(() => f("SCROLL_END"), 100),
      [u, f] = Gj("hidden", {
        hidden: { SCROLL: "scrolling" },
        scrolling: { SCROLL_END: "idle", POINTER_ENTER: "interacting" },
        interacting: { SCROLL: "interacting", POINTER_LEAVE: "idle" },
        idle: {
          HIDE: "hidden",
          SCROLL: "scrolling",
          POINTER_ENTER: "interacting",
        },
      });
    return (
      y.useEffect(() => {
        if (u === "idle") {
          const d = window.setTimeout(() => f("HIDE"), o.scrollHideDelay);
          return () => window.clearTimeout(d);
        }
      }, [u, o.scrollHideDelay, f]),
      y.useEffect(() => {
        const d = o.viewport,
          m = a ? "scrollLeft" : "scrollTop";
        if (d) {
          let h = d[m];
          const v = () => {
            const b = d[m];
            h !== b && (f("SCROLL"), s()), (h = b);
          };
          return (
            d.addEventListener("scroll", v),
            () => d.removeEventListener("scroll", v)
          );
        }
      }, [o.viewport, a, f, s]),
      y.createElement(
        Qs,
        { present: n || u !== "hidden" },
        y.createElement(
          O0,
          je({ "data-state": u === "hidden" ? "hidden" : "visible" }, r, {
            ref: t,
            onPointerEnter: $i(e.onPointerEnter, () => f("POINTER_ENTER")),
            onPointerLeave: $i(e.onPointerLeave, () => f("POINTER_LEAVE")),
          })
        )
      )
    );
  }),
  b$ = y.forwardRef((e, t) => {
    const n = Hn(ao, e.__scopeScrollArea),
      { forceMount: r, ...o } = e,
      [a, s] = y.useState(!1),
      u = e.orientation === "horizontal",
      f = hp(() => {
        if (n.viewport) {
          const d = n.viewport.offsetWidth < n.viewport.scrollWidth,
            m = n.viewport.offsetHeight < n.viewport.scrollHeight;
          s(u ? d : m);
        }
      }, 10);
    return (
      Va(n.viewport, f),
      Va(n.content, f),
      y.createElement(
        Qs,
        { present: r || a },
        y.createElement(
          O0,
          je({ "data-state": a ? "visible" : "hidden" }, o, { ref: t })
        )
      )
    );
  }),
  O0 = y.forwardRef((e, t) => {
    const { orientation: n = "vertical", ...r } = e,
      o = Hn(ao, e.__scopeScrollArea),
      a = y.useRef(null),
      s = y.useRef(0),
      [u, f] = y.useState({
        content: 0,
        viewport: 0,
        scrollbar: { size: 0, paddingStart: 0, paddingEnd: 0 },
      }),
      d = O$(u.viewport, u.content),
      m = {
        ...r,
        sizes: u,
        onSizesChange: f,
        hasThumb: d > 0 && d < 1,
        onThumbChange: (v) => (a.current = v),
        onThumbPointerUp: () => (s.current = 0),
        onThumbPointerDown: (v) => (s.current = v),
      };
    function h(v, b) {
      return sF(v, s.current, u, b);
    }
    return n === "horizontal"
      ? y.createElement(
          tF,
          je({}, m, {
            ref: t,
            onThumbPositionChange: () => {
              if (o.viewport && a.current) {
                const v = o.viewport.scrollLeft,
                  b = ox(v, u, o.dir);
                a.current.style.transform = `translate3d(${b}px, 0, 0)`;
              }
            },
            onWheelScroll: (v) => {
              o.viewport && (o.viewport.scrollLeft = v);
            },
            onDragScroll: (v) => {
              o.viewport && (o.viewport.scrollLeft = h(v, o.dir));
            },
          })
        )
      : n === "vertical"
      ? y.createElement(
          nF,
          je({}, m, {
            ref: t,
            onThumbPositionChange: () => {
              if (o.viewport && a.current) {
                const v = o.viewport.scrollTop,
                  b = ox(v, u);
                a.current.style.transform = `translate3d(0, ${b}px, 0)`;
              }
            },
            onWheelScroll: (v) => {
              o.viewport && (o.viewport.scrollTop = v);
            },
            onDragScroll: (v) => {
              o.viewport && (o.viewport.scrollTop = h(v));
            },
          })
        )
      : null;
  }),
  tF = y.forwardRef((e, t) => {
    const { sizes: n, onSizesChange: r, ...o } = e,
      a = Hn(ao, e.__scopeScrollArea),
      [s, u] = y.useState(),
      f = y.useRef(null),
      d = Mi(t, f, a.onScrollbarXChange);
    return (
      y.useEffect(() => {
        f.current && u(getComputedStyle(f.current));
      }, [f]),
      y.createElement(
        S$,
        je({ "data-orientation": "horizontal" }, o, {
          ref: d,
          sizes: n,
          style: {
            bottom: 0,
            left: a.dir === "rtl" ? "var(--radix-scroll-area-corner-width)" : 0,
            right:
              a.dir === "ltr" ? "var(--radix-scroll-area-corner-width)" : 0,
            ["--radix-scroll-area-thumb-width"]: mp(n) + "px",
            ...e.style,
          },
          onThumbPointerDown: (m) => e.onThumbPointerDown(m.x),
          onDragScroll: (m) => e.onDragScroll(m.x),
          onWheelScroll: (m, h) => {
            if (a.viewport) {
              const v = a.viewport.scrollLeft + m.deltaX;
              e.onWheelScroll(v), $$(v, h) && m.preventDefault();
            }
          },
          onResize: () => {
            f.current &&
              a.viewport &&
              s &&
              r({
                content: a.viewport.scrollWidth,
                viewport: a.viewport.offsetWidth,
                scrollbar: {
                  size: f.current.clientWidth,
                  paddingStart: Uf(s.paddingLeft),
                  paddingEnd: Uf(s.paddingRight),
                },
              });
          },
        })
      )
    );
  }),
  nF = y.forwardRef((e, t) => {
    const { sizes: n, onSizesChange: r, ...o } = e,
      a = Hn(ao, e.__scopeScrollArea),
      [s, u] = y.useState(),
      f = y.useRef(null),
      d = Mi(t, f, a.onScrollbarYChange);
    return (
      y.useEffect(() => {
        f.current && u(getComputedStyle(f.current));
      }, [f]),
      y.createElement(
        S$,
        je({ "data-orientation": "vertical" }, o, {
          ref: d,
          sizes: n,
          style: {
            top: 0,
            right: a.dir === "ltr" ? 0 : void 0,
            left: a.dir === "rtl" ? 0 : void 0,
            bottom: "var(--radix-scroll-area-corner-height)",
            ["--radix-scroll-area-thumb-height"]: mp(n) + "px",
            ...e.style,
          },
          onThumbPointerDown: (m) => e.onThumbPointerDown(m.y),
          onDragScroll: (m) => e.onDragScroll(m.y),
          onWheelScroll: (m, h) => {
            if (a.viewport) {
              const v = a.viewport.scrollTop + m.deltaY;
              e.onWheelScroll(v), $$(v, h) && m.preventDefault();
            }
          },
          onResize: () => {
            f.current &&
              a.viewport &&
              s &&
              r({
                content: a.viewport.scrollHeight,
                viewport: a.viewport.offsetHeight,
                scrollbar: {
                  size: f.current.clientHeight,
                  paddingStart: Uf(s.paddingTop),
                  paddingEnd: Uf(s.paddingBottom),
                },
              });
          },
        })
      )
    );
  }),
  [rF, x$] = w$(ao),
  S$ = y.forwardRef((e, t) => {
    const {
        __scopeScrollArea: n,
        sizes: r,
        hasThumb: o,
        onThumbChange: a,
        onThumbPointerUp: s,
        onThumbPointerDown: u,
        onThumbPositionChange: f,
        onDragScroll: d,
        onWheelScroll: m,
        onResize: h,
        ...v
      } = e,
      b = Hn(ao, n),
      [O, E] = y.useState(null),
      $ = Mi(t, (B) => E(B)),
      _ = y.useRef(null),
      w = y.useRef(""),
      P = b.viewport,
      k = r.content - r.viewport,
      I = gi(m),
      z = gi(f),
      A = hp(h, 10);
    function M(B) {
      if (_.current) {
        const H = B.clientX - _.current.left,
          q = B.clientY - _.current.top;
        d({ x: H, y: q });
      }
    }
    return (
      y.useEffect(() => {
        const B = (H) => {
          const q = H.target;
          (O == null ? void 0 : O.contains(q)) && I(H, k);
        };
        return (
          document.addEventListener("wheel", B, { passive: !1 }),
          () => document.removeEventListener("wheel", B, { passive: !1 })
        );
      }, [P, O, k, I]),
      y.useEffect(z, [r, z]),
      Va(O, A),
      Va(b.content, A),
      y.createElement(
        rF,
        {
          scope: n,
          scrollbar: O,
          hasThumb: o,
          onThumbChange: gi(a),
          onThumbPointerUp: gi(s),
          onThumbPositionChange: z,
          onThumbPointerDown: gi(u),
        },
        y.createElement(
          Ks.div,
          je({}, v, {
            ref: $,
            style: { position: "absolute", ...v.style },
            onPointerDown: $i(e.onPointerDown, (B) => {
              B.button === 0 &&
                (B.target.setPointerCapture(B.pointerId),
                (_.current = O.getBoundingClientRect()),
                (w.current = document.body.style.webkitUserSelect),
                (document.body.style.webkitUserSelect = "none"),
                M(B));
            }),
            onPointerMove: $i(e.onPointerMove, M),
            onPointerUp: $i(e.onPointerUp, (B) => {
              const H = B.target;
              H.hasPointerCapture(B.pointerId) &&
                H.releasePointerCapture(B.pointerId),
                (document.body.style.webkitUserSelect = w.current),
                (_.current = null);
            }),
          })
        )
      )
    );
  }),
  Wv = "ScrollAreaThumb",
  oF = y.forwardRef((e, t) => {
    const { forceMount: n, ...r } = e,
      o = x$(Wv, e.__scopeScrollArea);
    return y.createElement(
      Qs,
      { present: n || o.hasThumb },
      y.createElement(iF, je({ ref: t }, r))
    );
  }),
  iF = y.forwardRef((e, t) => {
    const { __scopeScrollArea: n, style: r, ...o } = e,
      a = Hn(Wv, n),
      s = x$(Wv, n),
      { onThumbPositionChange: u } = s,
      f = Mi(t, (h) => s.onThumbChange(h)),
      d = y.useRef(),
      m = hp(() => {
        d.current && (d.current(), (d.current = void 0));
      }, 100);
    return (
      y.useEffect(() => {
        const h = a.viewport;
        if (h) {
          const v = () => {
            if ((m(), !d.current)) {
              const b = uF(h, u);
              (d.current = b), u();
            }
          };
          return (
            u(),
            h.addEventListener("scroll", v),
            () => h.removeEventListener("scroll", v)
          );
        }
      }, [a.viewport, m, u]),
      y.createElement(
        Ks.div,
        je({ "data-state": s.hasThumb ? "visible" : "hidden" }, o, {
          ref: f,
          style: {
            width: "var(--radix-scroll-area-thumb-width)",
            height: "var(--radix-scroll-area-thumb-height)",
            ...r,
          },
          onPointerDownCapture: $i(e.onPointerDownCapture, (h) => {
            const b = h.target.getBoundingClientRect(),
              O = h.clientX - b.left,
              E = h.clientY - b.top;
            s.onThumbPointerDown({ x: O, y: E });
          }),
          onPointerUp: $i(e.onPointerUp, s.onThumbPointerUp),
        })
      )
    );
  }),
  P$ = "ScrollAreaCorner",
  aF = y.forwardRef((e, t) => {
    const n = Hn(P$, e.__scopeScrollArea),
      r = !!(n.scrollbarX && n.scrollbarY);
    return n.type !== "scroll" && r
      ? y.createElement(lF, je({}, e, { ref: t }))
      : null;
  }),
  lF = y.forwardRef((e, t) => {
    const { __scopeScrollArea: n, ...r } = e,
      o = Hn(P$, n),
      [a, s] = y.useState(0),
      [u, f] = y.useState(0),
      d = !!(a && u);
    return (
      Va(o.scrollbarX, () => {
        var m;
        const h =
          ((m = o.scrollbarX) === null || m === void 0
            ? void 0
            : m.offsetHeight) || 0;
        o.onCornerHeightChange(h), f(h);
      }),
      Va(o.scrollbarY, () => {
        var m;
        const h =
          ((m = o.scrollbarY) === null || m === void 0
            ? void 0
            : m.offsetWidth) || 0;
        o.onCornerWidthChange(h), s(h);
      }),
      d
        ? y.createElement(
            Ks.div,
            je({}, r, {
              ref: t,
              style: {
                width: a,
                height: u,
                position: "absolute",
                right: o.dir === "ltr" ? 0 : void 0,
                left: o.dir === "rtl" ? 0 : void 0,
                bottom: 0,
                ...e.style,
              },
            })
          )
        : null
    );
  });
function Uf(e) {
  return e ? parseInt(e, 10) : 0;
}
function O$(e, t) {
  const n = e / t;
  return isNaN(n) ? 0 : n;
}
function mp(e) {
  const t = O$(e.viewport, e.content),
    n = e.scrollbar.paddingStart + e.scrollbar.paddingEnd,
    r = (e.scrollbar.size - n) * t;
  return Math.max(r, 18);
}
function sF(e, t, n, r = "ltr") {
  const o = mp(n),
    a = o / 2,
    s = t || a,
    u = o - s,
    f = n.scrollbar.paddingStart + s,
    d = n.scrollbar.size - n.scrollbar.paddingEnd - u,
    m = n.content - n.viewport,
    h = r === "ltr" ? [0, m] : [m * -1, 0];
  return E$([f, d], h)(e);
}
function ox(e, t, n = "ltr") {
  const r = mp(t),
    o = t.scrollbar.paddingStart + t.scrollbar.paddingEnd,
    a = t.scrollbar.size - o,
    s = t.content - t.viewport,
    u = a - r,
    f = n === "ltr" ? [0, s] : [s * -1, 0],
    d = Yj(e, f);
  return E$([0, s], [0, u])(d);
}
function E$(e, t) {
  return (n) => {
    if (e[0] === e[1] || t[0] === t[1]) return t[0];
    const r = (t[1] - t[0]) / (e[1] - e[0]);
    return t[0] + r * (n - e[0]);
  };
}
function $$(e, t) {
  return e > 0 && e < t;
}
const uF = (e, t = () => {}) => {
  let n = { left: e.scrollLeft, top: e.scrollTop },
    r = 0;
  return (
    (function o() {
      const a = { left: e.scrollLeft, top: e.scrollTop },
        s = n.left !== a.left,
        u = n.top !== a.top;
      (s || u) && t(), (n = a), (r = window.requestAnimationFrame(o));
    })(),
    () => window.cancelAnimationFrame(r)
  );
};
function hp(e, t) {
  const n = gi(e),
    r = y.useRef(0);
  return (
    y.useEffect(() => () => window.clearTimeout(r.current), []),
    y.useCallback(() => {
      window.clearTimeout(r.current), (r.current = window.setTimeout(n, t));
    }, [n, t])
  );
}
function Va(e, t) {
  const n = gi(t);
  Fv(() => {
    let r = 0;
    if (e) {
      const o = new ResizeObserver(() => {
        cancelAnimationFrame(r), (r = window.requestAnimationFrame(n));
      });
      return (
        o.observe(e),
        () => {
          window.cancelAnimationFrame(r), o.unobserve(e);
        }
      );
    }
  }, [e, n]);
}
const cF = Kj,
  fF = qj,
  ix = Zj,
  ax = oF,
  dF = aF;
var pF = Pe(
  (
    e,
    { scrollbarSize: t, offsetScrollbars: n, scrollbarHovered: r, hidden: o },
    a
  ) => ({
    root: { overflow: "hidden" },
    viewport: {
      width: "100%",
      height: "100%",
      paddingRight: n ? t : void 0,
      paddingBottom: n ? t : void 0,
    },
    scrollbar: {
      display: o ? "none" : "flex",
      userSelect: "none",
      touchAction: "none",
      boxSizing: "border-box",
      padding: t / 5,
      transition: "background-color 150ms ease, opacity 150ms ease",
      "&:hover": {
        backgroundColor:
          e.colorScheme === "dark" ? e.colors.dark[8] : e.colors.gray[0],
        [`& .${a("thumb")}`]: {
          backgroundColor:
            e.colorScheme === "dark"
              ? e.fn.rgba(e.white, 0.5)
              : e.fn.rgba(e.black, 0.5),
        },
      },
      '&[data-orientation="vertical"]': { width: t },
      '&[data-orientation="horizontal"]': {
        flexDirection: "column",
        height: t,
      },
      '&[data-state="hidden"]': { display: "none", opacity: 0 },
    },
    thumb: {
      ref: a("thumb"),
      flex: 1,
      backgroundColor:
        e.colorScheme === "dark"
          ? e.fn.rgba(e.white, 0.4)
          : e.fn.rgba(e.black, 0.4),
      borderRadius: t,
      position: "relative",
      transition: "background-color 150ms ease",
      display: o ? "none" : void 0,
      overflow: "hidden",
      "&::before": {
        content: '""',
        position: "absolute",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        width: "100%",
        height: "100%",
        minWidth: 44,
        minHeight: 44,
      },
    },
    corner: {
      backgroundColor:
        e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[0],
      transition: "opacity 150ms ease",
      opacity: r ? 1 : 0,
      display: o ? "none" : void 0,
    },
  })
);
const mF = pF;
var hF = Object.defineProperty,
  vF = Object.defineProperties,
  gF = Object.getOwnPropertyDescriptors,
  Hf = Object.getOwnPropertySymbols,
  C$ = Object.prototype.hasOwnProperty,
  k$ = Object.prototype.propertyIsEnumerable,
  lx = (e, t, n) =>
    t in e
      ? hF(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Bv = (e, t) => {
    for (var n in t || (t = {})) C$.call(t, n) && lx(e, n, t[n]);
    if (Hf) for (var n of Hf(t)) k$.call(t, n) && lx(e, n, t[n]);
    return e;
  },
  R$ = (e, t) => vF(e, gF(t)),
  N$ = (e, t) => {
    var n = {};
    for (var r in e) C$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Hf)
      for (var r of Hf(e)) t.indexOf(r) < 0 && k$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const I$ = {
    scrollbarSize: 12,
    scrollHideDelay: 1e3,
    type: "hover",
    offsetScrollbars: !1,
  },
  vp = y.forwardRef((e, t) => {
    const n = me("ScrollArea", I$, e),
      {
        children: r,
        className: o,
        classNames: a,
        styles: s,
        scrollbarSize: u,
        scrollHideDelay: f,
        type: d,
        dir: m,
        offsetScrollbars: h,
        viewportRef: v,
        onScrollPositionChange: b,
        unstyled: O,
        viewportProps: E,
      } = n,
      $ = N$(n, [
        "children",
        "className",
        "classNames",
        "styles",
        "scrollbarSize",
        "scrollHideDelay",
        "type",
        "dir",
        "offsetScrollbars",
        "viewportRef",
        "onScrollPositionChange",
        "unstyled",
        "viewportProps",
      ]),
      [_, w] = y.useState(!1),
      P = On(),
      { classes: k, cx: I } = mF(
        {
          scrollbarSize: u,
          offsetScrollbars: h,
          scrollbarHovered: _,
          hidden: d === "never",
        },
        { name: "ScrollArea", classNames: a, styles: s, unstyled: O }
      );
    return R.createElement(
      cF,
      {
        type: d === "never" ? "always" : d,
        scrollHideDelay: f,
        dir: m || P.dir,
        ref: t,
        asChild: !0,
      },
      R.createElement(
        Ie,
        Bv({ className: I(k.root, o) }, $),
        R.createElement(
          fF,
          R$(Bv({}, E), {
            className: k.viewport,
            ref: v,
            onScroll:
              typeof b == "function"
                ? ({ currentTarget: z }) =>
                    b({ x: z.scrollLeft, y: z.scrollTop })
                : void 0,
          }),
          r
        ),
        R.createElement(
          ix,
          {
            orientation: "horizontal",
            className: k.scrollbar,
            forceMount: !0,
            onMouseEnter: () => w(!0),
            onMouseLeave: () => w(!1),
          },
          R.createElement(ax, { className: k.thumb })
        ),
        R.createElement(
          ix,
          {
            orientation: "vertical",
            className: k.scrollbar,
            forceMount: !0,
            onMouseEnter: () => w(!0),
            onMouseLeave: () => w(!1),
          },
          R.createElement(ax, { className: k.thumb })
        ),
        R.createElement(dF, { className: k.corner })
      )
    );
  }),
  T$ = y.forwardRef((e, t) => {
    const n = me("ScrollAreaAutosize", I$, e),
      {
        maxHeight: r,
        children: o,
        classNames: a,
        styles: s,
        scrollbarSize: u,
        scrollHideDelay: f,
        type: d,
        dir: m,
        offsetScrollbars: h,
        viewportRef: v,
        onScrollPositionChange: b,
        unstyled: O,
        sx: E,
      } = n,
      $ = N$(n, [
        "maxHeight",
        "children",
        "classNames",
        "styles",
        "scrollbarSize",
        "scrollHideDelay",
        "type",
        "dir",
        "offsetScrollbars",
        "viewportRef",
        "onScrollPositionChange",
        "unstyled",
        "sx",
      ]);
    return R.createElement(
      Ie,
      R$(Bv({}, $), {
        ref: t,
        sx: [{ display: "flex", maxHeight: r }, ...c0(E)],
      }),
      R.createElement(
        Ie,
        { sx: { display: "flex", flexDirection: "column", flex: 1 } },
        R.createElement(
          vp,
          {
            classNames: a,
            styles: s,
            scrollHideDelay: f,
            scrollbarSize: u,
            type: d,
            dir: m,
            offsetScrollbars: h,
            viewportRef: v,
            onScrollPositionChange: b,
            unstyled: O,
          },
          o
        )
      )
    );
  });
T$.displayName = "@mantine/core/ScrollAreaAutosize";
vp.displayName = "@mantine/core/ScrollArea";
vp.Autosize = T$;
const yF = vp;
var _F = Object.defineProperty,
  wF = Object.defineProperties,
  bF = Object.getOwnPropertyDescriptors,
  Vf = Object.getOwnPropertySymbols,
  z$ = Object.prototype.hasOwnProperty,
  A$ = Object.prototype.propertyIsEnumerable,
  sx = (e, t, n) =>
    t in e
      ? _F(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ux = (e, t) => {
    for (var n in t || (t = {})) z$.call(t, n) && sx(e, n, t[n]);
    if (Vf) for (var n of Vf(t)) A$.call(t, n) && sx(e, n, t[n]);
    return e;
  },
  xF = (e, t) => wF(e, bF(t)),
  SF = (e, t) => {
    var n = {};
    for (var r in e) z$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Vf)
      for (var r of Vf(e)) t.indexOf(r) < 0 && A$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const E0 = y.forwardRef((e, t) => {
  var n = e,
    { style: r } = n,
    o = SF(n, ["style"]);
  return R.createElement(
    yF,
    xF(ux({}, o), { style: ux({ width: "100%" }, r), viewportRef: t }),
    o.children
  );
});
E0.displayName = "@mantine/core/SelectScrollArea";
var PF = Pe(() => ({
  dropdown: {},
  itemsWrapper: {
    padding: 4,
    display: "flex",
    width: "100%",
    boxSizing: "border-box",
  },
}));
const OF = PF;
function ol(e) {
  return e.split("-")[1];
}
function $0(e) {
  return e === "y" ? "height" : "width";
}
function sr(e) {
  return e.split("-")[0];
}
function qo(e) {
  return ["top", "bottom"].includes(sr(e)) ? "x" : "y";
}
function cx(e, t, n) {
  let { reference: r, floating: o } = e;
  const a = r.x + r.width / 2 - o.width / 2,
    s = r.y + r.height / 2 - o.height / 2,
    u = qo(t),
    f = $0(u),
    d = r[f] / 2 - o[f] / 2,
    m = u === "x";
  let h;
  switch (sr(t)) {
    case "top":
      h = { x: a, y: r.y - o.height };
      break;
    case "bottom":
      h = { x: a, y: r.y + r.height };
      break;
    case "right":
      h = { x: r.x + r.width, y: s };
      break;
    case "left":
      h = { x: r.x - o.width, y: s };
      break;
    default:
      h = { x: r.x, y: r.y };
  }
  switch (ol(t)) {
    case "start":
      h[u] -= d * (n && m ? -1 : 1);
      break;
    case "end":
      h[u] += d * (n && m ? -1 : 1);
  }
  return h;
}
const EF = async (e, t, n) => {
  const {
      placement: r = "bottom",
      strategy: o = "absolute",
      middleware: a = [],
      platform: s,
    } = n,
    u = a.filter(Boolean),
    f = await (s.isRTL == null ? void 0 : s.isRTL(t));
  let d = await s.getElementRects({ reference: e, floating: t, strategy: o }),
    { x: m, y: h } = cx(d, r, f),
    v = r,
    b = {},
    O = 0;
  for (let E = 0; E < u.length; E++) {
    const { name: $, fn: _ } = u[E],
      {
        x: w,
        y: P,
        data: k,
        reset: I,
      } = await _({
        x: m,
        y: h,
        initialPlacement: r,
        placement: v,
        strategy: o,
        middlewareData: b,
        rects: d,
        platform: s,
        elements: { reference: e, floating: t },
      });
    (m = w ?? m),
      (h = P ?? h),
      (b = { ...b, [$]: { ...b[$], ...k } }),
      I &&
        O <= 50 &&
        (O++,
        typeof I == "object" &&
          (I.placement && (v = I.placement),
          I.rects &&
            (d =
              I.rects === !0
                ? await s.getElementRects({
                    reference: e,
                    floating: t,
                    strategy: o,
                  })
                : I.rects),
          ({ x: m, y: h } = cx(d, v, f))),
        (E = -1));
  }
  return { x: m, y: h, placement: v, strategy: o, middlewareData: b };
};
function C0(e) {
  return typeof e != "number"
    ? (function (t) {
        return { top: 0, right: 0, bottom: 0, left: 0, ...t };
      })(e)
    : { top: e, right: e, bottom: e, left: e };
}
function Ya(e) {
  return {
    ...e,
    top: e.y,
    left: e.x,
    right: e.x + e.width,
    bottom: e.y + e.height,
  };
}
async function k0(e, t) {
  var n;
  t === void 0 && (t = {});
  const { x: r, y: o, platform: a, rects: s, elements: u, strategy: f } = e,
    {
      boundary: d = "clippingAncestors",
      rootBoundary: m = "viewport",
      elementContext: h = "floating",
      altBoundary: v = !1,
      padding: b = 0,
    } = t,
    O = C0(b),
    E = u[v ? (h === "floating" ? "reference" : "floating") : h],
    $ = Ya(
      await a.getClippingRect({
        element:
          (n = await (a.isElement == null ? void 0 : a.isElement(E))) == null ||
          n
            ? E
            : E.contextElement ||
              (await (a.getDocumentElement == null
                ? void 0
                : a.getDocumentElement(u.floating))),
        boundary: d,
        rootBoundary: m,
        strategy: f,
      })
    ),
    _ = h === "floating" ? { ...s.floating, x: r, y: o } : s.reference,
    w = await (a.getOffsetParent == null
      ? void 0
      : a.getOffsetParent(u.floating)),
    P = ((await (a.isElement == null ? void 0 : a.isElement(w))) &&
      (await (a.getScale == null ? void 0 : a.getScale(w)))) || { x: 1, y: 1 },
    k = Ya(
      a.convertOffsetParentRelativeRectToViewportRelativeRect
        ? await a.convertOffsetParentRelativeRectToViewportRelativeRect({
            rect: _,
            offsetParent: w,
            strategy: f,
          })
        : _
    );
  return {
    top: ($.top - k.top + O.top) / P.y,
    bottom: (k.bottom - $.bottom + O.bottom) / P.y,
    left: ($.left - k.left + O.left) / P.x,
    right: (k.right - $.right + O.right) / P.x,
  };
}
const Ga = Math.min,
  wr = Math.max;
function Uv(e, t, n) {
  return wr(e, Ga(t, n));
}
const fx = (e) => ({
    name: "arrow",
    options: e,
    async fn(t) {
      const { element: n, padding: r = 0 } = e || {},
        { x: o, y: a, placement: s, rects: u, platform: f, elements: d } = t;
      if (n == null) return {};
      const m = C0(r),
        h = { x: o, y: a },
        v = qo(s),
        b = $0(v),
        O = await f.getDimensions(n),
        E = v === "y",
        $ = E ? "top" : "left",
        _ = E ? "bottom" : "right",
        w = E ? "clientHeight" : "clientWidth",
        P = u.reference[b] + u.reference[v] - h[v] - u.floating[b],
        k = h[v] - u.reference[v],
        I = await (f.getOffsetParent == null ? void 0 : f.getOffsetParent(n));
      let z = I ? I[w] : 0;
      (z && (await (f.isElement == null ? void 0 : f.isElement(I)))) ||
        (z = d.floating[w] || u.floating[b]);
      const A = P / 2 - k / 2,
        M = m[$],
        B = z - O[b] - m[_],
        H = z / 2 - O[b] / 2 + A,
        q = Uv(M, H, B),
        Z =
          ol(s) != null &&
          H != q &&
          u.reference[b] / 2 - (H < M ? m[$] : m[_]) - O[b] / 2 < 0;
      return {
        [v]: h[v] - (Z ? (H < M ? M - H : B - H) : 0),
        data: { [v]: q, centerOffset: H - q },
      };
    },
  }),
  $F = ["top", "right", "bottom", "left"];
$F.reduce((e, t) => e.concat(t, t + "-start", t + "-end"), []);
const CF = { left: "right", right: "left", bottom: "top", top: "bottom" };
function Yf(e) {
  return e.replace(/left|right|bottom|top/g, (t) => CF[t]);
}
function kF(e, t, n) {
  n === void 0 && (n = !1);
  const r = ol(e),
    o = qo(e),
    a = $0(o);
  let s =
    o === "x"
      ? r === (n ? "end" : "start")
        ? "right"
        : "left"
      : r === "start"
      ? "bottom"
      : "top";
  return (
    t.reference[a] > t.floating[a] && (s = Yf(s)), { main: s, cross: Yf(s) }
  );
}
const RF = { start: "end", end: "start" };
function wh(e) {
  return e.replace(/start|end/g, (t) => RF[t]);
}
const NF = function (e) {
  return (
    e === void 0 && (e = {}),
    {
      name: "flip",
      options: e,
      async fn(t) {
        var n;
        const {
            placement: r,
            middlewareData: o,
            rects: a,
            initialPlacement: s,
            platform: u,
            elements: f,
          } = t,
          {
            mainAxis: d = !0,
            crossAxis: m = !0,
            fallbackPlacements: h,
            fallbackStrategy: v = "bestFit",
            fallbackAxisSideDirection: b = "none",
            flipAlignment: O = !0,
            ...E
          } = e,
          $ = sr(r),
          _ = sr(s) === s,
          w = await (u.isRTL == null ? void 0 : u.isRTL(f.floating)),
          P =
            h ||
            (_ || !O
              ? [Yf(s)]
              : (function (q) {
                  const Z = Yf(q);
                  return [wh(q), Z, wh(Z)];
                })(s));
        h ||
          b === "none" ||
          P.push(
            ...(function (q, Z, he, xe) {
              const Ce = ol(q);
              let ce = (function (Se, Y, re) {
                const ne = ["left", "right"],
                  le = ["right", "left"],
                  ze = ["top", "bottom"],
                  Gt = ["bottom", "top"];
                switch (Se) {
                  case "top":
                  case "bottom":
                    return re ? (Y ? le : ne) : Y ? ne : le;
                  case "left":
                  case "right":
                    return Y ? ze : Gt;
                  default:
                    return [];
                }
              })(sr(q), he === "start", xe);
              return (
                Ce &&
                  ((ce = ce.map((Se) => Se + "-" + Ce)),
                  Z && (ce = ce.concat(ce.map(wh)))),
                ce
              );
            })(s, O, b, w)
          );
        const k = [s, ...P],
          I = await k0(t, E),
          z = [];
        let A = ((n = o.flip) == null ? void 0 : n.overflows) || [];
        if ((d && z.push(I[$]), m)) {
          const { main: q, cross: Z } = kF(r, a, w);
          z.push(I[q], I[Z]);
        }
        if (
          ((A = [...A, { placement: r, overflows: z }]),
          !z.every((q) => q <= 0))
        ) {
          var M, B;
          const q = (((M = o.flip) == null ? void 0 : M.index) || 0) + 1,
            Z = k[q];
          if (Z)
            return {
              data: { index: q, overflows: A },
              reset: { placement: Z },
            };
          let he =
            (B = A.filter((xe) => xe.overflows[0] <= 0).sort(
              (xe, Ce) => xe.overflows[1] - Ce.overflows[1]
            )[0]) == null
              ? void 0
              : B.placement;
          if (!he)
            switch (v) {
              case "bestFit": {
                var H;
                const xe =
                  (H = A.map((Ce) => [
                    Ce.placement,
                    Ce.overflows
                      .filter((ce) => ce > 0)
                      .reduce((ce, Se) => ce + Se, 0),
                  ]).sort((Ce, ce) => Ce[1] - ce[1])[0]) == null
                    ? void 0
                    : H[0];
                xe && (he = xe);
                break;
              }
              case "initialPlacement":
                he = s;
            }
          if (r !== he) return { reset: { placement: he } };
        }
        return {};
      },
    }
  );
};
function dx(e) {
  const t = Ga(...e.map((r) => r.left)),
    n = Ga(...e.map((r) => r.top));
  return {
    x: t,
    y: n,
    width: wr(...e.map((r) => r.right)) - t,
    height: wr(...e.map((r) => r.bottom)) - n,
  };
}
const IF = function (e) {
    return (
      e === void 0 && (e = {}),
      {
        name: "inline",
        options: e,
        async fn(t) {
          const {
              placement: n,
              elements: r,
              rects: o,
              platform: a,
              strategy: s,
            } = t,
            { padding: u = 2, x: f, y: d } = e,
            m = Array.from(
              (await (a.getClientRects == null
                ? void 0
                : a.getClientRects(r.reference))) || []
            ),
            h = (function (E) {
              const $ = E.slice().sort((P, k) => P.y - k.y),
                _ = [];
              let w = null;
              for (let P = 0; P < $.length; P++) {
                const k = $[P];
                !w || k.y - w.y > w.height / 2
                  ? _.push([k])
                  : _[_.length - 1].push(k),
                  (w = k);
              }
              return _.map((P) => Ya(dx(P)));
            })(m),
            v = Ya(dx(m)),
            b = C0(u),
            O = await a.getElementRects({
              reference: {
                getBoundingClientRect: function () {
                  if (
                    h.length === 2 &&
                    h[0].left > h[1].right &&
                    f != null &&
                    d != null
                  )
                    return (
                      h.find(
                        (E) =>
                          f > E.left - b.left &&
                          f < E.right + b.right &&
                          d > E.top - b.top &&
                          d < E.bottom + b.bottom
                      ) || v
                    );
                  if (h.length >= 2) {
                    if (qo(n) === "x") {
                      const I = h[0],
                        z = h[h.length - 1],
                        A = sr(n) === "top",
                        M = I.top,
                        B = z.bottom,
                        H = A ? I.left : z.left,
                        q = A ? I.right : z.right;
                      return {
                        top: M,
                        bottom: B,
                        left: H,
                        right: q,
                        width: q - H,
                        height: B - M,
                        x: H,
                        y: M,
                      };
                    }
                    const E = sr(n) === "left",
                      $ = wr(...h.map((I) => I.right)),
                      _ = Ga(...h.map((I) => I.left)),
                      w = h.filter((I) => (E ? I.left === _ : I.right === $)),
                      P = w[0].top,
                      k = w[w.length - 1].bottom;
                    return {
                      top: P,
                      bottom: k,
                      left: _,
                      right: $,
                      width: $ - _,
                      height: k - P,
                      x: _,
                      y: P,
                    };
                  }
                  return v;
                },
              },
              floating: r.floating,
              strategy: s,
            });
          return o.reference.x !== O.reference.x ||
            o.reference.y !== O.reference.y ||
            o.reference.width !== O.reference.width ||
            o.reference.height !== O.reference.height
            ? { reset: { rects: O } }
            : {};
        },
      }
    );
  },
  TF = function (e) {
    return (
      e === void 0 && (e = 0),
      {
        name: "offset",
        options: e,
        async fn(t) {
          const { x: n, y: r } = t,
            o = await (async function (a, s) {
              const { placement: u, platform: f, elements: d } = a,
                m = await (f.isRTL == null ? void 0 : f.isRTL(d.floating)),
                h = sr(u),
                v = ol(u),
                b = qo(u) === "x",
                O = ["left", "top"].includes(h) ? -1 : 1,
                E = m && b ? -1 : 1,
                $ = typeof s == "function" ? s(a) : s;
              let {
                mainAxis: _,
                crossAxis: w,
                alignmentAxis: P,
              } = typeof $ == "number"
                ? { mainAxis: $, crossAxis: 0, alignmentAxis: null }
                : { mainAxis: 0, crossAxis: 0, alignmentAxis: null, ...$ };
              return (
                v && typeof P == "number" && (w = v === "end" ? -1 * P : P),
                b ? { x: w * E, y: _ * O } : { x: _ * O, y: w * E }
              );
            })(t, e);
          return { x: n + o.x, y: r + o.y, data: o };
        },
      }
    );
  };
function L$(e) {
  return e === "x" ? "y" : "x";
}
const zF = function (e) {
    return (
      e === void 0 && (e = {}),
      {
        name: "shift",
        options: e,
        async fn(t) {
          const { x: n, y: r, placement: o } = t,
            {
              mainAxis: a = !0,
              crossAxis: s = !1,
              limiter: u = {
                fn: ($) => {
                  let { x: _, y: w } = $;
                  return { x: _, y: w };
                },
              },
              ...f
            } = e,
            d = { x: n, y: r },
            m = await k0(t, f),
            h = qo(sr(o)),
            v = L$(h);
          let b = d[h],
            O = d[v];
          if (a) {
            const $ = h === "y" ? "bottom" : "right";
            b = Uv(b + m[h === "y" ? "top" : "left"], b, b - m[$]);
          }
          if (s) {
            const $ = v === "y" ? "bottom" : "right";
            O = Uv(O + m[v === "y" ? "top" : "left"], O, O - m[$]);
          }
          const E = u.fn({ ...t, [h]: b, [v]: O });
          return { ...E, data: { x: E.x - n, y: E.y - r } };
        },
      }
    );
  },
  AF = function (e) {
    return (
      e === void 0 && (e = {}),
      {
        options: e,
        fn(t) {
          const { x: n, y: r, placement: o, rects: a, middlewareData: s } = t,
            { offset: u = 0, mainAxis: f = !0, crossAxis: d = !0 } = e,
            m = { x: n, y: r },
            h = qo(o),
            v = L$(h);
          let b = m[h],
            O = m[v];
          const E = typeof u == "function" ? u(t) : u,
            $ =
              typeof E == "number"
                ? { mainAxis: E, crossAxis: 0 }
                : { mainAxis: 0, crossAxis: 0, ...E };
          if (f) {
            const P = h === "y" ? "height" : "width",
              k = a.reference[h] - a.floating[P] + $.mainAxis,
              I = a.reference[h] + a.reference[P] - $.mainAxis;
            b < k ? (b = k) : b > I && (b = I);
          }
          if (d) {
            var _, w;
            const P = h === "y" ? "width" : "height",
              k = ["top", "left"].includes(sr(o)),
              I =
                a.reference[v] -
                a.floating[P] +
                ((k && ((_ = s.offset) == null ? void 0 : _[v])) || 0) +
                (k ? 0 : $.crossAxis),
              z =
                a.reference[v] +
                a.reference[P] +
                (k ? 0 : ((w = s.offset) == null ? void 0 : w[v]) || 0) -
                (k ? $.crossAxis : 0);
            O < I ? (O = I) : O > z && (O = z);
          }
          return { [h]: b, [v]: O };
        },
      }
    );
  },
  LF = function (e) {
    return (
      e === void 0 && (e = {}),
      {
        name: "size",
        options: e,
        async fn(t) {
          const { placement: n, rects: r, platform: o, elements: a } = t,
            { apply: s = () => {}, ...u } = e,
            f = await k0(t, u),
            d = sr(n),
            m = ol(n),
            h = qo(n) === "x",
            { width: v, height: b } = r.floating;
          let O, E;
          d === "top" || d === "bottom"
            ? ((O = d),
              (E =
                m ===
                ((await (o.isRTL == null ? void 0 : o.isRTL(a.floating)))
                  ? "start"
                  : "end")
                  ? "left"
                  : "right"))
            : ((E = d), (O = m === "end" ? "top" : "bottom"));
          const $ = b - f[O],
            _ = v - f[E],
            w = !t.middlewareData.shift;
          let P = $,
            k = _;
          if (h) {
            const z = v - f.left - f.right;
            k = m || w ? Ga(_, z) : z;
          } else {
            const z = b - f.top - f.bottom;
            P = m || w ? Ga($, z) : z;
          }
          if (w && !m) {
            const z = wr(f.left, 0),
              A = wr(f.right, 0),
              M = wr(f.top, 0),
              B = wr(f.bottom, 0);
            h
              ? (k = v - 2 * (z !== 0 || A !== 0 ? z + A : wr(f.left, f.right)))
              : (P =
                  b - 2 * (M !== 0 || B !== 0 ? M + B : wr(f.top, f.bottom)));
          }
          await s({ ...t, availableWidth: k, availableHeight: P });
          const I = await o.getDimensions(a.floating);
          return v !== I.width || b !== I.height
            ? { reset: { rects: !0 } }
            : {};
        },
      }
    );
  };
function Wn(e) {
  var t;
  return ((t = e.ownerDocument) == null ? void 0 : t.defaultView) || window;
}
function ur(e) {
  return Wn(e).getComputedStyle(e);
}
function D$(e) {
  return e instanceof Wn(e).Node;
}
function Ho(e) {
  return D$(e) ? (e.nodeName || "").toLowerCase() : "";
}
function fr(e) {
  return e instanceof Wn(e).HTMLElement;
}
function yn(e) {
  return e instanceof Wn(e).Element;
}
function px(e) {
  return typeof ShadowRoot > "u"
    ? !1
    : e instanceof Wn(e).ShadowRoot || e instanceof ShadowRoot;
}
function Ts(e) {
  const { overflow: t, overflowX: n, overflowY: r, display: o } = ur(e);
  return (
    /auto|scroll|overlay|hidden|clip/.test(t + r + n) &&
    !["inline", "contents"].includes(o)
  );
}
function DF(e) {
  return ["table", "td", "th"].includes(Ho(e));
}
function Hv(e) {
  const t = R0(),
    n = ur(e);
  return (
    n.transform !== "none" ||
    n.perspective !== "none" ||
    (!t && !!n.backdropFilter && n.backdropFilter !== "none") ||
    (!t && !!n.filter && n.filter !== "none") ||
    ["transform", "perspective", "filter"].some((r) =>
      (n.willChange || "").includes(r)
    ) ||
    ["paint", "layout", "strict", "content"].some((r) =>
      (n.contain || "").includes(r)
    )
  );
}
function R0() {
  return (
    !(typeof CSS > "u" || !CSS.supports) &&
    CSS.supports("-webkit-backdrop-filter", "none")
  );
}
function gp(e) {
  return ["html", "body", "#document"].includes(Ho(e));
}
const mx = Math.min,
  is = Math.max,
  Gf = Math.round;
function M$(e) {
  const t = ur(e);
  let n = parseFloat(t.width) || 0,
    r = parseFloat(t.height) || 0;
  const o = fr(e),
    a = o ? e.offsetWidth : n,
    s = o ? e.offsetHeight : r,
    u = Gf(n) !== a || Gf(r) !== s;
  return u && ((n = a), (r = s)), { width: n, height: r, fallback: u };
}
function j$(e) {
  return yn(e) ? e : e.contextElement;
}
const F$ = { x: 1, y: 1 };
function Aa(e) {
  const t = j$(e);
  if (!fr(t)) return F$;
  const n = t.getBoundingClientRect(),
    { width: r, height: o, fallback: a } = M$(t);
  let s = (a ? Gf(n.width) : n.width) / r,
    u = (a ? Gf(n.height) : n.height) / o;
  return (
    (s && Number.isFinite(s)) || (s = 1),
    (u && Number.isFinite(u)) || (u = 1),
    { x: s, y: u }
  );
}
const hx = { x: 0, y: 0 };
function W$(e, t, n) {
  var r, o;
  if ((t === void 0 && (t = !0), !R0())) return hx;
  const a = e ? Wn(e) : window;
  return !n || (t && n !== a)
    ? hx
    : {
        x: ((r = a.visualViewport) == null ? void 0 : r.offsetLeft) || 0,
        y: ((o = a.visualViewport) == null ? void 0 : o.offsetTop) || 0,
      };
}
function Ti(e, t, n, r) {
  t === void 0 && (t = !1), n === void 0 && (n = !1);
  const o = e.getBoundingClientRect(),
    a = j$(e);
  let s = F$;
  t && (r ? yn(r) && (s = Aa(r)) : (s = Aa(e)));
  const u = W$(a, n, r);
  let f = (o.left + u.x) / s.x,
    d = (o.top + u.y) / s.y,
    m = o.width / s.x,
    h = o.height / s.y;
  if (a) {
    const v = Wn(a),
      b = r && yn(r) ? Wn(r) : r;
    let O = v.frameElement;
    for (; O && r && b !== v; ) {
      const E = Aa(O),
        $ = O.getBoundingClientRect(),
        _ = getComputedStyle(O);
      ($.x += (O.clientLeft + parseFloat(_.paddingLeft)) * E.x),
        ($.y += (O.clientTop + parseFloat(_.paddingTop)) * E.y),
        (f *= E.x),
        (d *= E.y),
        (m *= E.x),
        (h *= E.y),
        (f += $.x),
        (d += $.y),
        (O = Wn(O).frameElement);
    }
  }
  return Ya({ width: m, height: h, x: f, y: d });
}
function Wo(e) {
  return ((D$(e) ? e.ownerDocument : e.document) || window.document)
    .documentElement;
}
function yp(e) {
  return yn(e)
    ? { scrollLeft: e.scrollLeft, scrollTop: e.scrollTop }
    : { scrollLeft: e.pageXOffset, scrollTop: e.pageYOffset };
}
function B$(e) {
  return Ti(Wo(e)).left + yp(e).scrollLeft;
}
function Xa(e) {
  if (Ho(e) === "html") return e;
  const t = e.assignedSlot || e.parentNode || (px(e) && e.host) || Wo(e);
  return px(t) ? t.host : t;
}
function U$(e) {
  const t = Xa(e);
  return gp(t) ? t.ownerDocument.body : fr(t) && Ts(t) ? t : U$(t);
}
function as(e, t) {
  var n;
  t === void 0 && (t = []);
  const r = U$(e),
    o = r === ((n = e.ownerDocument) == null ? void 0 : n.body),
    a = Wn(r);
  return o
    ? t.concat(a, a.visualViewport || [], Ts(r) ? r : [])
    : t.concat(r, as(r));
}
function vx(e, t, n) {
  let r;
  if (t === "viewport")
    r = (function (o, a) {
      const s = Wn(o),
        u = Wo(o),
        f = s.visualViewport;
      let d = u.clientWidth,
        m = u.clientHeight,
        h = 0,
        v = 0;
      if (f) {
        (d = f.width), (m = f.height);
        const b = R0();
        (!b || (b && a === "fixed")) && ((h = f.offsetLeft), (v = f.offsetTop));
      }
      return { width: d, height: m, x: h, y: v };
    })(e, n);
  else if (t === "document")
    r = (function (o) {
      const a = Wo(o),
        s = yp(o),
        u = o.ownerDocument.body,
        f = is(a.scrollWidth, a.clientWidth, u.scrollWidth, u.clientWidth),
        d = is(a.scrollHeight, a.clientHeight, u.scrollHeight, u.clientHeight);
      let m = -s.scrollLeft + B$(o);
      const h = -s.scrollTop;
      return (
        ur(u).direction === "rtl" &&
          (m += is(a.clientWidth, u.clientWidth) - f),
        { width: f, height: d, x: m, y: h }
      );
    })(Wo(e));
  else if (yn(t))
    r = (function (o, a) {
      const s = Ti(o, !0, a === "fixed"),
        u = s.top + o.clientTop,
        f = s.left + o.clientLeft,
        d = fr(o) ? Aa(o) : { x: 1, y: 1 };
      return {
        width: o.clientWidth * d.x,
        height: o.clientHeight * d.y,
        x: f * d.x,
        y: u * d.y,
      };
    })(t, n);
  else {
    const o = W$(e);
    r = { ...t, x: t.x - o.x, y: t.y - o.y };
  }
  return Ya(r);
}
function H$(e, t) {
  const n = Xa(e);
  return (
    !(n === t || !yn(n) || gp(n)) && (ur(n).position === "fixed" || H$(n, t))
  );
}
function gx(e, t) {
  return fr(e) && ur(e).position !== "fixed"
    ? t
      ? t(e)
      : e.offsetParent
    : null;
}
function yx(e, t) {
  const n = Wn(e);
  if (!fr(e)) return n;
  let r = gx(e, t);
  for (; r && DF(r) && ur(r).position === "static"; ) r = gx(r, t);
  return r &&
    (Ho(r) === "html" ||
      (Ho(r) === "body" && ur(r).position === "static" && !Hv(r)))
    ? n
    : r ||
        (function (o) {
          let a = Xa(o);
          for (; fr(a) && !gp(a); ) {
            if (Hv(a)) return a;
            a = Xa(a);
          }
          return null;
        })(e) ||
        n;
}
function MF(e, t, n) {
  const r = fr(t),
    o = Wo(t),
    a = n === "fixed",
    s = Ti(e, !0, a, t);
  let u = { scrollLeft: 0, scrollTop: 0 };
  const f = { x: 0, y: 0 };
  if (r || (!r && !a))
    if (((Ho(t) !== "body" || Ts(o)) && (u = yp(t)), fr(t))) {
      const d = Ti(t, !0, a, t);
      (f.x = d.x + t.clientLeft), (f.y = d.y + t.clientTop);
    } else o && (f.x = B$(o));
  return {
    x: s.left + u.scrollLeft - f.x,
    y: s.top + u.scrollTop - f.y,
    width: s.width,
    height: s.height,
  };
}
const jF = {
  getClippingRect: function (e) {
    let { element: t, boundary: n, rootBoundary: r, strategy: o } = e;
    const a =
        n === "clippingAncestors"
          ? (function (d, m) {
              const h = m.get(d);
              if (h) return h;
              let v = as(d).filter(($) => yn($) && Ho($) !== "body"),
                b = null;
              const O = ur(d).position === "fixed";
              let E = O ? Xa(d) : d;
              for (; yn(E) && !gp(E); ) {
                const $ = ur(E),
                  _ = Hv(E);
                _ || $.position !== "fixed" || (b = null),
                  (
                    O
                      ? !_ && !b
                      : (!_ &&
                          $.position === "static" &&
                          b &&
                          ["absolute", "fixed"].includes(b.position)) ||
                        (Ts(E) && !_ && H$(d, E))
                  )
                    ? (v = v.filter((w) => w !== E))
                    : (b = $),
                  (E = Xa(E));
              }
              return m.set(d, v), v;
            })(t, this._c)
          : [].concat(n),
      s = [...a, r],
      u = s[0],
      f = s.reduce((d, m) => {
        const h = vx(t, m, o);
        return (
          (d.top = is(h.top, d.top)),
          (d.right = mx(h.right, d.right)),
          (d.bottom = mx(h.bottom, d.bottom)),
          (d.left = is(h.left, d.left)),
          d
        );
      }, vx(t, u, o));
    return {
      width: f.right - f.left,
      height: f.bottom - f.top,
      x: f.left,
      y: f.top,
    };
  },
  convertOffsetParentRelativeRectToViewportRelativeRect: function (e) {
    let { rect: t, offsetParent: n, strategy: r } = e;
    const o = fr(n),
      a = Wo(n);
    if (n === a) return t;
    let s = { scrollLeft: 0, scrollTop: 0 },
      u = { x: 1, y: 1 };
    const f = { x: 0, y: 0 };
    if (
      (o || (!o && r !== "fixed")) &&
      ((Ho(n) !== "body" || Ts(a)) && (s = yp(n)), fr(n))
    ) {
      const d = Ti(n);
      (u = Aa(n)), (f.x = d.x + n.clientLeft), (f.y = d.y + n.clientTop);
    }
    return {
      width: t.width * u.x,
      height: t.height * u.y,
      x: t.x * u.x - s.scrollLeft * u.x + f.x,
      y: t.y * u.y - s.scrollTop * u.y + f.y,
    };
  },
  isElement: yn,
  getDimensions: function (e) {
    return M$(e);
  },
  getOffsetParent: yx,
  getDocumentElement: Wo,
  getScale: Aa,
  async getElementRects(e) {
    let { reference: t, floating: n, strategy: r } = e;
    const o = this.getOffsetParent || yx,
      a = this.getDimensions;
    return {
      reference: MF(t, await o(n), r),
      floating: { x: 0, y: 0, ...(await a(n)) },
    };
  },
  getClientRects: (e) => Array.from(e.getClientRects()),
  isRTL: (e) => ur(e).direction === "rtl",
};
function FF(e, t, n, r) {
  r === void 0 && (r = {});
  const {
      ancestorScroll: o = !0,
      ancestorResize: a = !0,
      elementResize: s = !0,
      animationFrame: u = !1,
    } = r,
    f =
      o || a
        ? [
            ...(yn(e) ? as(e) : e.contextElement ? as(e.contextElement) : []),
            ...as(t),
          ]
        : [];
  f.forEach((v) => {
    const b = !yn(v) && v.toString().includes("V");
    !o || (u && !b) || v.addEventListener("scroll", n, { passive: !0 }),
      a && v.addEventListener("resize", n);
  });
  let d,
    m = null;
  s &&
    ((m = new ResizeObserver(() => {
      n();
    })),
    yn(e) && !u && m.observe(e),
    yn(e) || !e.contextElement || u || m.observe(e.contextElement),
    m.observe(t));
  let h = u ? Ti(e) : null;
  return (
    u &&
      (function v() {
        const b = Ti(e);
        !h ||
          (b.x === h.x &&
            b.y === h.y &&
            b.width === h.width &&
            b.height === h.height) ||
          n(),
          (h = b),
          (d = requestAnimationFrame(v));
      })(),
    n(),
    () => {
      var v;
      f.forEach((b) => {
        o && b.removeEventListener("scroll", n),
          a && b.removeEventListener("resize", n);
      }),
        (v = m) == null || v.disconnect(),
        (m = null),
        u && cancelAnimationFrame(d);
    }
  );
}
const WF = (e, t, n) => {
    const r = new Map(),
      o = { platform: jF, ...n },
      a = { ...o.platform, _c: r };
    return EF(e, t, { ...o, platform: a });
  },
  BF = (e) => {
    const { element: t, padding: n } = e;
    function r(o) {
      return Object.prototype.hasOwnProperty.call(o, "current");
    }
    return {
      name: "arrow",
      options: e,
      fn(o) {
        return r(t)
          ? t.current != null
            ? fx({ element: t.current, padding: n }).fn(o)
            : {}
          : t
          ? fx({ element: t, padding: n }).fn(o)
          : {};
      },
    };
  };
var Gc = typeof document < "u" ? y.useLayoutEffect : y.useEffect;
function Xf(e, t) {
  if (e === t) return !0;
  if (typeof e != typeof t) return !1;
  if (typeof e == "function" && e.toString() === t.toString()) return !0;
  let n, r, o;
  if (e && t && typeof e == "object") {
    if (Array.isArray(e)) {
      if (((n = e.length), n != t.length)) return !1;
      for (r = n; r-- !== 0; ) if (!Xf(e[r], t[r])) return !1;
      return !0;
    }
    if (((o = Object.keys(e)), (n = o.length), n !== Object.keys(t).length))
      return !1;
    for (r = n; r-- !== 0; )
      if (!Object.prototype.hasOwnProperty.call(t, o[r])) return !1;
    for (r = n; r-- !== 0; ) {
      const a = o[r];
      if (!(a === "_owner" && e.$$typeof) && !Xf(e[a], t[a])) return !1;
    }
    return !0;
  }
  return e !== e && t !== t;
}
function _x(e) {
  const t = y.useRef(e);
  return (
    Gc(() => {
      t.current = e;
    }),
    t
  );
}
function UF(e) {
  e === void 0 && (e = {});
  const {
      placement: t = "bottom",
      strategy: n = "absolute",
      middleware: r = [],
      platform: o,
      whileElementsMounted: a,
      open: s,
    } = e,
    [u, f] = y.useState({
      x: null,
      y: null,
      strategy: n,
      placement: t,
      middlewareData: {},
      isPositioned: !1,
    }),
    [d, m] = y.useState(r);
  Xf(d, r) || m(r);
  const h = y.useRef(null),
    v = y.useRef(null),
    b = y.useRef(u),
    O = _x(a),
    E = _x(o),
    [$, _] = y.useState(null),
    [w, P] = y.useState(null),
    k = y.useCallback((H) => {
      h.current !== H && ((h.current = H), _(H));
    }, []),
    I = y.useCallback((H) => {
      v.current !== H && ((v.current = H), P(H));
    }, []),
    z = y.useCallback(() => {
      if (!h.current || !v.current) return;
      const H = { placement: t, strategy: n, middleware: d };
      E.current && (H.platform = E.current),
        WF(h.current, v.current, H).then((q) => {
          const Z = { ...q, isPositioned: !0 };
          A.current &&
            !Xf(b.current, Z) &&
            ((b.current = Z),
            Ja.flushSync(() => {
              f(Z);
            }));
        });
    }, [d, t, n, E]);
  Gc(() => {
    s === !1 &&
      b.current.isPositioned &&
      ((b.current.isPositioned = !1), f((H) => ({ ...H, isPositioned: !1 })));
  }, [s]);
  const A = y.useRef(!1);
  Gc(
    () => (
      (A.current = !0),
      () => {
        A.current = !1;
      }
    ),
    []
  ),
    Gc(() => {
      if ($ && w) {
        if (O.current) return O.current($, w, z);
        z();
      }
    }, [$, w, z, O]);
  const M = y.useMemo(
      () => ({ reference: h, floating: v, setReference: k, setFloating: I }),
      [k, I]
    ),
    B = y.useMemo(() => ({ reference: $, floating: w }), [$, w]);
  return y.useMemo(
    () => ({
      ...u,
      update: z,
      refs: M,
      elements: B,
      reference: k,
      floating: I,
    }),
    [u, z, M, B, k, I]
  );
}
var HF = typeof document < "u" ? y.useLayoutEffect : y.useEffect;
function VF() {
  const e = new Map();
  return {
    emit(t, n) {
      var r;
      (r = e.get(t)) == null || r.forEach((o) => o(n));
    },
    on(t, n) {
      e.set(t, [...(e.get(t) || []), n]);
    },
    off(t, n) {
      e.set(
        t,
        (e.get(t) || []).filter((r) => r !== n)
      );
    },
  };
}
const YF = y.createContext(null),
  GF = () => y.useContext(YF);
function XF(e) {
  return (e == null ? void 0 : e.ownerDocument) || document;
}
function KF(e) {
  return XF(e).defaultView || window;
}
function hc(e) {
  return e ? e instanceof KF(e).Element : !1;
}
const QF = qc["useInsertionEffect".toString()],
  qF = QF || ((e) => e());
function ZF(e) {
  const t = y.useRef(() => {});
  return (
    qF(() => {
      t.current = e;
    }),
    y.useCallback(function () {
      for (var n = arguments.length, r = new Array(n), o = 0; o < n; o++)
        r[o] = arguments[o];
      return t.current == null ? void 0 : t.current(...r);
    }, [])
  );
}
function JF(e) {
  e === void 0 && (e = {});
  const { open: t = !1, onOpenChange: n, nodeId: r } = e,
    o = UF(e),
    a = GF(),
    s = y.useRef(null),
    u = y.useRef({}),
    f = y.useState(() => VF())[0],
    [d, m] = y.useState(null),
    h = y.useCallback(
      (_) => {
        const w = hc(_)
          ? {
              getBoundingClientRect: () => _.getBoundingClientRect(),
              contextElement: _,
            }
          : _;
        o.refs.setReference(w);
      },
      [o.refs]
    ),
    v = y.useCallback(
      (_) => {
        (hc(_) || _ === null) && ((s.current = _), m(_)),
          (hc(o.refs.reference.current) ||
            o.refs.reference.current === null ||
            (_ !== null && !hc(_))) &&
            o.refs.setReference(_);
      },
      [o.refs]
    ),
    b = y.useMemo(
      () => ({
        ...o.refs,
        setReference: v,
        setPositionReference: h,
        domReference: s,
      }),
      [o.refs, v, h]
    ),
    O = y.useMemo(() => ({ ...o.elements, domReference: d }), [o.elements, d]),
    E = ZF(n),
    $ = y.useMemo(
      () => ({
        ...o,
        refs: b,
        elements: O,
        dataRef: u,
        nodeId: r,
        events: f,
        open: t,
        onOpenChange: E,
      }),
      [o, r, f, t, E, b, O]
    );
  return (
    HF(() => {
      const _ = a == null ? void 0 : a.nodesRef.current.find((w) => w.id === r);
      _ && (_.context = $);
    }),
    y.useMemo(
      () => ({ ...o, context: $, refs: b, reference: v, positionReference: h }),
      [o, b, $, v, h]
    )
  );
}
function eW({ opened: e, floating: t, positionDependencies: n }) {
  const [r, o] = y.useState(0);
  y.useEffect(() => {
    if (t.refs.reference.current && t.refs.floating.current)
      return FF(t.refs.reference.current, t.refs.floating.current, t.update);
  }, [t.refs.reference.current, t.refs.floating.current, e, r]),
    Zr(() => {
      t.update();
    }, n),
    Zr(() => {
      o((a) => a + 1);
    }, [e]);
}
function tW(e) {
  const t = [TF(e.offset)];
  return (
    e.middlewares.shift && t.push(zF({ limiter: AF() })),
    e.middlewares.flip && t.push(NF()),
    e.middlewares.inline && t.push(IF()),
    t.push(BF({ element: e.arrowRef, padding: e.arrowOffset })),
    t
  );
}
function nW(e) {
  const [t, n] = Is({
      value: e.opened,
      defaultValue: e.defaultOpened,
      finalValue: !1,
      onChange: e.onChange,
    }),
    r = () => {
      var s;
      (s = e.onClose) == null || s.call(e), n(!1);
    },
    o = () => {
      var s, u;
      t
        ? ((s = e.onClose) == null || s.call(e), n(!1))
        : ((u = e.onOpen) == null || u.call(e), n(!0));
    },
    a = JF({
      placement: e.position,
      middleware: [
        ...tW(e),
        ...(e.width === "target"
          ? [
              LF({
                apply({ rects: s }) {
                  var u, f;
                  Object.assign(
                    (f =
                      (u = a.refs.floating.current) == null
                        ? void 0
                        : u.style) != null
                      ? f
                      : {},
                    { width: `${s.reference.width}px` }
                  );
                },
              }),
            ]
          : []),
      ],
    });
  return (
    eW({
      opened: e.opened,
      positionDependencies: e.positionDependencies,
      floating: a,
    }),
    Zr(() => {
      var s;
      (s = e.onPositionChange) == null || s.call(e, a.placement);
    }, [a.placement]),
    {
      floating: a,
      controlled: typeof e.opened == "boolean",
      opened: t,
      onClose: r,
      onToggle: o,
    }
  );
}
const V$ = {
    context: "Popover component was not found in the tree",
    children:
      "Popover.Target component children should be an element or a component that accepts ref, fragments, strings, numbers and other primitive values are not supported",
  },
  [rW, Y$] = ZA(V$.context);
var oW = Object.defineProperty,
  iW = Object.defineProperties,
  aW = Object.getOwnPropertyDescriptors,
  Kf = Object.getOwnPropertySymbols,
  G$ = Object.prototype.hasOwnProperty,
  X$ = Object.prototype.propertyIsEnumerable,
  wx = (e, t, n) =>
    t in e
      ? oW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  vc = (e, t) => {
    for (var n in t || (t = {})) G$.call(t, n) && wx(e, n, t[n]);
    if (Kf) for (var n of Kf(t)) X$.call(t, n) && wx(e, n, t[n]);
    return e;
  },
  lW = (e, t) => iW(e, aW(t)),
  sW = (e, t) => {
    var n = {};
    for (var r in e) G$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Kf)
      for (var r of Kf(e)) t.indexOf(r) < 0 && X$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const uW = { refProp: "ref", popupType: "dialog" },
  K$ = y.forwardRef((e, t) => {
    const n = me("PopoverTarget", uW, e),
      { children: r, refProp: o, popupType: a } = n,
      s = sW(n, ["children", "refProp", "popupType"]);
    if (!QO(r)) throw new Error(V$.children);
    const u = s,
      f = Y$(),
      d = x0(f.reference, r.ref, t),
      m = f.withRoles
        ? {
            "aria-haspopup": a,
            "aria-expanded": f.opened,
            "aria-controls": f.getDropdownId(),
            id: f.getTargetId(),
          }
        : {};
    return y.cloneElement(
      r,
      vc(
        lW(vc(vc(vc({}, u), m), f.targetProps), {
          className: ZO(
            f.targetProps.className,
            u.className,
            r.props.className
          ),
          [o]: d,
        }),
        f.controlled ? null : { onClick: f.onToggle }
      )
    );
  });
K$.displayName = "@mantine/core/PopoverTarget";
var cW = Pe((e, { radius: t, shadow: n }) => ({
  dropdown: {
    position: "absolute",
    backgroundColor: e.white,
    background: e.colorScheme === "dark" ? e.colors.dark[6] : e.white,
    border: `1px solid ${
      e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2]
    }`,
    padding: `${e.spacing.sm}px ${e.spacing.md}px`,
    boxShadow: e.shadows[n] || n || "none",
    borderRadius: e.fn.radius(t),
    "&:focus": { outline: 0 },
  },
  arrow: {
    backgroundColor: "inherit",
    border: `1px solid ${
      e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2]
    }`,
    zIndex: 1,
  },
}));
const fW = cW;
var dW = Object.defineProperty,
  bx = Object.getOwnPropertySymbols,
  pW = Object.prototype.hasOwnProperty,
  mW = Object.prototype.propertyIsEnumerable,
  xx = (e, t, n) =>
    t in e
      ? dW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ua = (e, t) => {
    for (var n in t || (t = {})) pW.call(t, n) && xx(e, n, t[n]);
    if (bx) for (var n of bx(t)) mW.call(t, n) && xx(e, n, t[n]);
    return e;
  };
const Sx = {
  entering: "in",
  entered: "in",
  exiting: "out",
  exited: "out",
  "pre-exiting": "out",
  "pre-entering": "out",
};
function hW({ transition: e, state: t, duration: n, timingFunction: r }) {
  const o = { transitionDuration: `${n}ms`, transitionTimingFunction: r };
  return typeof e == "string"
    ? e in fc
      ? ua(
          ua(
            ua({ transitionProperty: fc[e].transitionProperty }, o),
            fc[e].common
          ),
          fc[e][Sx[t]]
        )
      : null
    : ua(
        ua(ua({ transitionProperty: e.transitionProperty }, o), e.common),
        e[Sx[t]]
      );
}
function vW({
  duration: e,
  exitDuration: t,
  timingFunction: n,
  mounted: r,
  onEnter: o,
  onExit: a,
  onEntered: s,
  onExited: u,
}) {
  const f = On(),
    d = S0(),
    m = f.respectReducedMotion ? d : !1,
    [h, v] = y.useState(r ? "entered" : "exited");
  let b = m ? 0 : e;
  const O = y.useRef(-1),
    E = ($) => {
      const _ = $ ? o : a,
        w = $ ? s : u;
      if (
        (v($ ? "pre-entering" : "pre-exiting"),
        window.clearTimeout(O.current),
        (b = m ? 0 : $ ? e : t),
        b === 0)
      )
        typeof _ == "function" && _(),
          typeof w == "function" && w(),
          v($ ? "entered" : "exited");
      else {
        const P = window.setTimeout(() => {
          typeof _ == "function" && _(), v($ ? "entering" : "exiting");
        }, 10);
        O.current = window.setTimeout(() => {
          window.clearTimeout(P),
            typeof w == "function" && w(),
            v($ ? "entered" : "exited");
        }, b);
      }
    };
  return (
    Zr(() => {
      E(r);
    }, [r]),
    y.useEffect(() => () => window.clearTimeout(O.current), []),
    {
      transitionDuration: b,
      transitionStatus: h,
      transitionTimingFunction: n || f.transitionTimingFunction,
    }
  );
}
function Q$({
  transition: e,
  duration: t = 250,
  exitDuration: n = t,
  mounted: r,
  children: o,
  timingFunction: a,
  onExit: s,
  onEntered: u,
  onEnter: f,
  onExited: d,
}) {
  const {
    transitionDuration: m,
    transitionStatus: h,
    transitionTimingFunction: v,
  } = vW({
    mounted: r,
    exitDuration: n,
    duration: t,
    timingFunction: a,
    onExit: s,
    onEntered: u,
    onEnter: f,
    onExited: d,
  });
  return m === 0
    ? r
      ? R.createElement(R.Fragment, null, o({}))
      : null
    : h === "exited"
    ? null
    : R.createElement(
        R.Fragment,
        null,
        o(hW({ transition: e, duration: m, state: h, timingFunction: v }))
      );
}
Q$.displayName = "@mantine/core/Transition";
function q$({ children: e, active: t = !0, refProp: n = "ref" }) {
  const r = zL(t),
    o = x0(r, e == null ? void 0 : e.ref);
  return QO(e) ? y.cloneElement(e, { [n]: o }) : e;
}
q$.displayName = "@mantine/core/FocusTrap";
var gW = Object.defineProperty,
  yW = Object.defineProperties,
  _W = Object.getOwnPropertyDescriptors,
  Px = Object.getOwnPropertySymbols,
  wW = Object.prototype.hasOwnProperty,
  bW = Object.prototype.propertyIsEnumerable,
  Ox = (e, t, n) =>
    t in e
      ? gW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  bo = (e, t) => {
    for (var n in t || (t = {})) wW.call(t, n) && Ox(e, n, t[n]);
    if (Px) for (var n of Px(t)) bW.call(t, n) && Ox(e, n, t[n]);
    return e;
  },
  gc = (e, t) => yW(e, _W(t));
function Ex(e, t, n, r) {
  return e === "center" || r === "center"
    ? { top: t }
    : e === "end"
    ? { bottom: n }
    : e === "start"
    ? { top: n }
    : {};
}
function $x(e, t, n, r, o) {
  return e === "center" || r === "center"
    ? { left: t }
    : e === "end"
    ? { [o === "ltr" ? "right" : "left"]: n }
    : e === "start"
    ? { [o === "ltr" ? "left" : "right"]: n }
    : {};
}
const xW = {
  bottom: "borderTopLeftRadius",
  left: "borderTopRightRadius",
  right: "borderBottomLeftRadius",
  top: "borderBottomRightRadius",
};
function SW({
  position: e,
  withBorder: t,
  arrowSize: n,
  arrowOffset: r,
  arrowRadius: o,
  arrowPosition: a,
  arrowX: s,
  arrowY: u,
  dir: f,
}) {
  const [d, m = "center"] = e.split("-"),
    h = {
      width: n,
      height: n,
      transform: "rotate(45deg)",
      position: "absolute",
      [xW[d]]: o,
    },
    v = t ? -n / 2 - 1 : -n / 2;
  return d === "left"
    ? gc(bo(bo({}, h), Ex(m, u, r, a)), {
        right: v,
        borderLeft: 0,
        borderBottom: 0,
      })
    : d === "right"
    ? gc(bo(bo({}, h), Ex(m, u, r, a)), {
        left: v,
        borderRight: 0,
        borderTop: 0,
      })
    : d === "top"
    ? gc(bo(bo({}, h), $x(m, s, r, a, f)), {
        bottom: v,
        borderTop: 0,
        borderLeft: 0,
      })
    : d === "bottom"
    ? gc(bo(bo({}, h), $x(m, s, r, a, f)), {
        top: v,
        borderBottom: 0,
        borderRight: 0,
      })
    : {};
}
var PW = Object.defineProperty,
  OW = Object.defineProperties,
  EW = Object.getOwnPropertyDescriptors,
  Qf = Object.getOwnPropertySymbols,
  Z$ = Object.prototype.hasOwnProperty,
  J$ = Object.prototype.propertyIsEnumerable,
  Cx = (e, t, n) =>
    t in e
      ? PW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  $W = (e, t) => {
    for (var n in t || (t = {})) Z$.call(t, n) && Cx(e, n, t[n]);
    if (Qf) for (var n of Qf(t)) J$.call(t, n) && Cx(e, n, t[n]);
    return e;
  },
  CW = (e, t) => OW(e, EW(t)),
  kW = (e, t) => {
    var n = {};
    for (var r in e) Z$.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Qf)
      for (var r of Qf(e)) t.indexOf(r) < 0 && J$.call(e, r) && (n[r] = e[r]);
    return n;
  };
const e3 = y.forwardRef((e, t) => {
  var n = e,
    {
      withBorder: r,
      position: o,
      arrowSize: a,
      arrowOffset: s,
      arrowRadius: u,
      arrowPosition: f,
      visible: d,
      arrowX: m,
      arrowY: h,
    } = n,
    v = kW(n, [
      "withBorder",
      "position",
      "arrowSize",
      "arrowOffset",
      "arrowRadius",
      "arrowPosition",
      "visible",
      "arrowX",
      "arrowY",
    ]);
  const b = On();
  return d
    ? R.createElement(
        "div",
        CW($W({}, v), {
          ref: t,
          style: SW({
            withBorder: r,
            position: o,
            arrowSize: a,
            arrowOffset: s,
            arrowRadius: u,
            arrowPosition: f,
            dir: b.dir,
            arrowX: m,
            arrowY: h,
          }),
        })
      )
    : null;
});
e3.displayName = "@mantine/core/FloatingArrow";
var RW = Object.defineProperty,
  NW = Object.defineProperties,
  IW = Object.getOwnPropertyDescriptors,
  qf = Object.getOwnPropertySymbols,
  t3 = Object.prototype.hasOwnProperty,
  n3 = Object.prototype.propertyIsEnumerable,
  kx = (e, t, n) =>
    t in e
      ? RW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  yc = (e, t) => {
    for (var n in t || (t = {})) t3.call(t, n) && kx(e, n, t[n]);
    if (qf) for (var n of qf(t)) n3.call(t, n) && kx(e, n, t[n]);
    return e;
  },
  Rx = (e, t) => NW(e, IW(t)),
  TW = (e, t) => {
    var n = {};
    for (var r in e) t3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && qf)
      for (var r of qf(e)) t.indexOf(r) < 0 && n3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const zW = {};
function r3(e) {
  const t = me("PopoverDropdown", zW, e),
    { style: n, className: r, children: o, onKeyDownCapture: a } = t,
    s = TW(t, ["style", "className", "children", "onKeyDownCapture"]),
    u = Y$(),
    { classes: f, cx: d } = fW(
      { radius: u.radius, shadow: u.shadow },
      {
        name: u.__staticSelector,
        classNames: u.classNames,
        styles: u.styles,
        unstyled: u.unstyled,
      }
    ),
    m = $L({ opened: u.opened, shouldReturnFocus: u.returnFocus }),
    h = u.withRoles
      ? {
          "aria-labelledby": u.getTargetId(),
          id: u.getDropdownId(),
          role: "dialog",
        }
      : {};
  return u.disabled
    ? null
    : R.createElement(
        YE,
        { withinPortal: u.withinPortal },
        R.createElement(
          Q$,
          {
            mounted: u.opened,
            transition: u.transition,
            duration: u.transitionDuration,
            exitDuration:
              typeof u.exitTransitionDuration == "number"
                ? u.exitTransitionDuration
                : u.transitionDuration,
          },
          (v) => {
            var b, O;
            return R.createElement(
              q$,
              { active: u.trapFocus },
              R.createElement(
                Ie,
                yc(
                  Rx(yc({}, h), {
                    tabIndex: -1,
                    key: u.placement,
                    ref: u.floating,
                    style: Rx(yc(yc({}, n), v), {
                      zIndex: u.zIndex,
                      top: (b = u.y) != null ? b : 0,
                      left: (O = u.x) != null ? O : 0,
                      width: u.width === "target" ? void 0 : u.width,
                    }),
                    className: d(f.dropdown, r),
                    onKeyDownCapture: e9(u.onClose, {
                      active: u.closeOnEscape,
                      onTrigger: m,
                      onKeyDown: a,
                    }),
                    "data-position": u.placement,
                  }),
                  s
                ),
                o,
                R.createElement(e3, {
                  ref: u.arrowRef,
                  arrowX: u.arrowX,
                  arrowY: u.arrowY,
                  visible: u.withArrow,
                  withBorder: !0,
                  position: u.placement,
                  arrowSize: u.arrowSize,
                  arrowRadius: u.arrowRadius,
                  arrowOffset: u.arrowOffset,
                  arrowPosition: u.arrowPosition,
                  className: f.arrow,
                })
              )
            );
          }
        )
      );
}
r3.displayName = "@mantine/core/PopoverDropdown";
function AW(e, t) {
  if (e === "rtl" && (t.includes("right") || t.includes("left"))) {
    const [n, r] = t.split("-"),
      o = n === "right" ? "left" : "right";
    return r === void 0 ? o : `${o}-${r}`;
  }
  return t;
}
var Nx = Object.getOwnPropertySymbols,
  LW = Object.prototype.hasOwnProperty,
  DW = Object.prototype.propertyIsEnumerable,
  MW = (e, t) => {
    var n = {};
    for (var r in e) LW.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Nx)
      for (var r of Nx(e)) t.indexOf(r) < 0 && DW.call(e, r) && (n[r] = e[r]);
    return n;
  };
const jW = {
  position: "bottom",
  offset: 8,
  positionDependencies: [],
  transition: "fade",
  transitionDuration: 150,
  middlewares: { flip: !0, shift: !0, inline: !1 },
  arrowSize: 7,
  arrowOffset: 5,
  arrowRadius: 0,
  arrowPosition: "side",
  closeOnClickOutside: !0,
  withinPortal: !1,
  closeOnEscape: !0,
  trapFocus: !1,
  withRoles: !0,
  returnFocus: !1,
  clickOutsideEvents: ["mousedown", "touchstart"],
  zIndex: fp("popover"),
  __staticSelector: "Popover",
  width: "max-content",
};
function il(e) {
  var t, n, r, o, a, s;
  const u = y.useRef(null),
    f = me("Popover", jW, e),
    {
      children: d,
      position: m,
      offset: h,
      onPositionChange: v,
      positionDependencies: b,
      opened: O,
      transition: E,
      transitionDuration: $,
      width: _,
      middlewares: w,
      withArrow: P,
      arrowSize: k,
      arrowOffset: I,
      arrowRadius: z,
      arrowPosition: A,
      unstyled: M,
      classNames: B,
      styles: H,
      closeOnClickOutside: q,
      withinPortal: Z,
      closeOnEscape: he,
      clickOutsideEvents: xe,
      trapFocus: Ce,
      onClose: ce,
      onOpen: Se,
      onChange: Y,
      zIndex: re,
      radius: ne,
      shadow: le,
      id: ze,
      defaultOpened: Gt,
      exitTransitionDuration: Dt,
      __staticSelector: $t,
      withRoles: vt,
      disabled: $n,
      returnFocus: Vn,
    } = f,
    Yn = MW(f, [
      "children",
      "position",
      "offset",
      "onPositionChange",
      "positionDependencies",
      "opened",
      "transition",
      "transitionDuration",
      "width",
      "middlewares",
      "withArrow",
      "arrowSize",
      "arrowOffset",
      "arrowRadius",
      "arrowPosition",
      "unstyled",
      "classNames",
      "styles",
      "closeOnClickOutside",
      "withinPortal",
      "closeOnEscape",
      "clickOutsideEvents",
      "trapFocus",
      "onClose",
      "onOpen",
      "onChange",
      "zIndex",
      "radius",
      "shadow",
      "id",
      "defaultOpened",
      "exitTransitionDuration",
      "__staticSelector",
      "withRoles",
      "disabled",
      "returnFocus",
    ]),
    [Zo, so] = y.useState(null),
    [uo, Fi] = y.useState(null),
    Ct = dp(ze),
    $r = On(),
    Ve = nW({
      middlewares: w,
      width: _,
      position: AW($r.dir, m),
      offset: h + (P ? k / 2 : 0),
      arrowRef: u,
      arrowOffset: I,
      onPositionChange: v,
      positionDependencies: b,
      opened: O,
      defaultOpened: Gt,
      onChange: Y,
      onOpen: Se,
      onClose: ce,
    });
  xL(() => q && Ve.onClose(), xe, [Zo, uo]);
  const Xt = y.useCallback(
      (Cr) => {
        so(Cr), Ve.floating.reference(Cr);
      },
      [Ve.floating.reference]
    ),
    Wi = y.useCallback(
      (Cr) => {
        Fi(Cr), Ve.floating.floating(Cr);
      },
      [Ve.floating.floating]
    );
  return R.createElement(
    rW,
    {
      value: {
        returnFocus: Vn,
        disabled: $n,
        controlled: Ve.controlled,
        reference: Xt,
        floating: Wi,
        x: Ve.floating.x,
        y: Ve.floating.y,
        arrowX:
          (r =
            (n = (t = Ve.floating) == null ? void 0 : t.middlewareData) == null
              ? void 0
              : n.arrow) == null
            ? void 0
            : r.x,
        arrowY:
          (s =
            (a = (o = Ve.floating) == null ? void 0 : o.middlewareData) == null
              ? void 0
              : a.arrow) == null
            ? void 0
            : s.y,
        opened: Ve.opened,
        arrowRef: u,
        transition: E,
        transitionDuration: $,
        exitTransitionDuration: Dt,
        width: _,
        withArrow: P,
        arrowSize: k,
        arrowOffset: I,
        arrowRadius: z,
        arrowPosition: A,
        placement: Ve.floating.placement,
        trapFocus: Ce,
        withinPortal: Z,
        zIndex: re,
        radius: ne,
        shadow: le,
        closeOnEscape: he,
        onClose: Ve.onClose,
        onToggle: Ve.onToggle,
        getTargetId: () => `${Ct}-target`,
        getDropdownId: () => `${Ct}-dropdown`,
        withRoles: vt,
        targetProps: Yn,
        __staticSelector: $t,
        classNames: B,
        styles: H,
        unstyled: M,
      },
    },
    d
  );
}
il.Target = K$;
il.Dropdown = r3;
il.displayName = "@mantine/core/Popover";
var FW = Object.defineProperty,
  Zf = Object.getOwnPropertySymbols,
  o3 = Object.prototype.hasOwnProperty,
  i3 = Object.prototype.propertyIsEnumerable,
  Ix = (e, t, n) =>
    t in e
      ? FW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  WW = (e, t) => {
    for (var n in t || (t = {})) o3.call(t, n) && Ix(e, n, t[n]);
    if (Zf) for (var n of Zf(t)) i3.call(t, n) && Ix(e, n, t[n]);
    return e;
  },
  BW = (e, t) => {
    var n = {};
    for (var r in e) o3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Zf)
      for (var r of Zf(e)) t.indexOf(r) < 0 && i3.call(e, r) && (n[r] = e[r]);
    return n;
  };
function UW(e) {
  var t = e,
    {
      children: n,
      component: r = "div",
      maxHeight: o = 220,
      direction: a = "column",
      id: s,
      innerRef: u,
      __staticSelector: f,
      styles: d,
      classNames: m,
      unstyled: h,
    } = t,
    v = BW(t, [
      "children",
      "component",
      "maxHeight",
      "direction",
      "id",
      "innerRef",
      "__staticSelector",
      "styles",
      "classNames",
      "unstyled",
    ]);
  const { classes: b } = OF(null, {
    name: f,
    styles: d,
    classNames: m,
    unstyled: h,
  });
  return R.createElement(
    il.Dropdown,
    WW({ p: 0, onMouseDown: (O) => O.preventDefault() }, v),
    R.createElement(
      "div",
      { style: { maxHeight: o, display: "flex" } },
      R.createElement(
        Ie,
        {
          component: r || "div",
          id: `${s}-items`,
          "aria-labelledby": `${s}-label`,
          role: "listbox",
          onMouseDown: (O) => O.preventDefault(),
          style: { flex: 1, overflowY: r !== E0 ? "auto" : void 0 },
          "data-combobox-popover": !0,
          ref: u,
        },
        R.createElement(
          "div",
          { className: b.itemsWrapper, style: { flexDirection: a } },
          n
        )
      )
    )
  );
}
function ls({
  opened: e,
  transition: t = "fade",
  transitionDuration: n = 0,
  shadow: r,
  withinPortal: o,
  children: a,
  __staticSelector: s,
  onDirectionChange: u,
  switchDirectionOnFlip: f,
  zIndex: d,
  dropdownPosition: m,
  positionDependencies: h = [],
  classNames: v,
  styles: b,
  unstyled: O,
  readOnly: E,
}) {
  return R.createElement(
    il,
    {
      unstyled: O,
      classNames: v,
      styles: b,
      width: "target",
      withRoles: !1,
      opened: e,
      middlewares: { flip: m === "flip", shift: !1 },
      position: m === "flip" ? "bottom" : m,
      positionDependencies: h,
      zIndex: d,
      __staticSelector: s,
      withinPortal: o,
      transition: t,
      transitionDuration: n,
      shadow: r,
      disabled: E,
      onPositionChange: ($) =>
        f &&
        (u == null ? void 0 : u($ === "top" ? "column-reverse" : "column")),
    },
    a
  );
}
ls.Target = il.Target;
ls.Dropdown = UW;
var HW = Object.defineProperty,
  VW = Object.defineProperties,
  YW = Object.getOwnPropertyDescriptors,
  Jf = Object.getOwnPropertySymbols,
  a3 = Object.prototype.hasOwnProperty,
  l3 = Object.prototype.propertyIsEnumerable,
  Tx = (e, t, n) =>
    t in e
      ? HW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  _c = (e, t) => {
    for (var n in t || (t = {})) a3.call(t, n) && Tx(e, n, t[n]);
    if (Jf) for (var n of Jf(t)) l3.call(t, n) && Tx(e, n, t[n]);
    return e;
  },
  GW = (e, t) => VW(e, YW(t)),
  XW = (e, t) => {
    var n = {};
    for (var r in e) a3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Jf)
      for (var r of Jf(e)) t.indexOf(r) < 0 && l3.call(e, r) && (n[r] = e[r]);
    return n;
  };
function s3(e, t, n) {
  const r = me(e, t, n),
    {
      label: o,
      description: a,
      error: s,
      required: u,
      classNames: f,
      styles: d,
      className: m,
      unstyled: h,
      __staticSelector: v,
      sx: b,
      errorProps: O,
      labelProps: E,
      descriptionProps: $,
      wrapperProps: _,
      id: w,
      size: P,
      style: k,
      inputContainer: I,
      inputWrapperOrder: z,
      withAsterisk: A,
    } = r,
    M = XW(r, [
      "label",
      "description",
      "error",
      "required",
      "classNames",
      "styles",
      "className",
      "unstyled",
      "__staticSelector",
      "sx",
      "errorProps",
      "labelProps",
      "descriptionProps",
      "wrapperProps",
      "id",
      "size",
      "style",
      "inputContainer",
      "inputWrapperOrder",
      "withAsterisk",
    ]),
    B = dp(w),
    { systemStyles: H, rest: q } = Xs(M),
    Z = _c(
      {
        label: o,
        description: a,
        error: s,
        required: u,
        classNames: f,
        className: m,
        __staticSelector: v,
        sx: b,
        errorProps: O,
        labelProps: E,
        descriptionProps: $,
        unstyled: h,
        styles: d,
        id: B,
        size: P,
        style: k,
        inputContainer: I,
        inputWrapperOrder: z,
        withAsterisk: A,
      },
      _
    );
  return GW(_c({}, q), {
    classNames: f,
    styles: d,
    unstyled: h,
    wrapperProps: _c(_c({}, Z), H),
    inputProps: {
      required: u,
      classNames: f,
      styles: d,
      unstyled: h,
      id: B,
      size: P,
      __staticSelector: v,
      invalid: !!s,
    },
  });
}
var KW = Pe((e, { size: t }) => ({
  label: {
    display: "inline-block",
    fontSize: e.fn.size({ size: t, sizes: e.fontSizes }),
    fontWeight: 500,
    color: e.colorScheme === "dark" ? e.colors.dark[0] : e.colors.gray[9],
    wordBreak: "break-word",
    cursor: "default",
    WebkitTapHighlightColor: "transparent",
  },
  required: {
    color: e.fn.variant({ variant: "filled", color: "red" }).background,
  },
}));
const QW = KW;
var qW = Object.defineProperty,
  ed = Object.getOwnPropertySymbols,
  u3 = Object.prototype.hasOwnProperty,
  c3 = Object.prototype.propertyIsEnumerable,
  zx = (e, t, n) =>
    t in e
      ? qW(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ZW = (e, t) => {
    for (var n in t || (t = {})) u3.call(t, n) && zx(e, n, t[n]);
    if (ed) for (var n of ed(t)) c3.call(t, n) && zx(e, n, t[n]);
    return e;
  },
  JW = (e, t) => {
    var n = {};
    for (var r in e) u3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && ed)
      for (var r of ed(e)) t.indexOf(r) < 0 && c3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const eB = { labelElement: "label", size: "sm" },
  N0 = y.forwardRef((e, t) => {
    const n = me("InputLabel", eB, e),
      {
        labelElement: r,
        children: o,
        required: a,
        size: s,
        classNames: u,
        styles: f,
        unstyled: d,
        className: m,
        htmlFor: h,
        __staticSelector: v,
      } = n,
      b = JW(n, [
        "labelElement",
        "children",
        "required",
        "size",
        "classNames",
        "styles",
        "unstyled",
        "className",
        "htmlFor",
        "__staticSelector",
      ]),
      { classes: O, cx: E } = QW(
        { size: s },
        { name: ["InputWrapper", v], classNames: u, styles: f, unstyled: d }
      );
    return R.createElement(
      Ie,
      ZW(
        {
          component: r,
          ref: t,
          className: E(O.label, m),
          htmlFor: r === "label" ? h : void 0,
        },
        b
      ),
      o,
      a &&
        R.createElement(
          "span",
          { className: O.required, "aria-hidden": !0 },
          " *"
        )
    );
  });
N0.displayName = "@mantine/core/InputLabel";
var tB = Pe((e, { size: t }) => ({
  error: {
    wordBreak: "break-word",
    color: e.fn.variant({ variant: "filled", color: "red" }).background,
    fontSize: e.fn.size({ size: t, sizes: e.fontSizes }) - 2,
    lineHeight: 1.2,
    display: "block",
  },
}));
const nB = tB;
var rB = Object.defineProperty,
  td = Object.getOwnPropertySymbols,
  f3 = Object.prototype.hasOwnProperty,
  d3 = Object.prototype.propertyIsEnumerable,
  Ax = (e, t, n) =>
    t in e
      ? rB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  oB = (e, t) => {
    for (var n in t || (t = {})) f3.call(t, n) && Ax(e, n, t[n]);
    if (td) for (var n of td(t)) d3.call(t, n) && Ax(e, n, t[n]);
    return e;
  },
  iB = (e, t) => {
    var n = {};
    for (var r in e) f3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && td)
      for (var r of td(e)) t.indexOf(r) < 0 && d3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const aB = { size: "sm" },
  I0 = y.forwardRef((e, t) => {
    const n = me("InputError", aB, e),
      {
        children: r,
        className: o,
        classNames: a,
        styles: s,
        unstyled: u,
        size: f,
        __staticSelector: d,
      } = n,
      m = iB(n, [
        "children",
        "className",
        "classNames",
        "styles",
        "unstyled",
        "size",
        "__staticSelector",
      ]),
      { classes: h, cx: v } = nB(
        { size: f },
        { name: ["InputWrapper", d], classNames: a, styles: s, unstyled: u }
      );
    return R.createElement(Jr, oB({ className: v(h.error, o), ref: t }, m), r);
  });
I0.displayName = "@mantine/core/InputError";
var lB = Pe((e, { size: t }) => ({
  description: {
    wordBreak: "break-word",
    color: e.colorScheme === "dark" ? e.colors.dark[2] : e.colors.gray[6],
    fontSize: e.fn.size({ size: t, sizes: e.fontSizes }) - 2,
    lineHeight: 1.2,
    display: "block",
  },
}));
const sB = lB;
var uB = Object.defineProperty,
  nd = Object.getOwnPropertySymbols,
  p3 = Object.prototype.hasOwnProperty,
  m3 = Object.prototype.propertyIsEnumerable,
  Lx = (e, t, n) =>
    t in e
      ? uB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  cB = (e, t) => {
    for (var n in t || (t = {})) p3.call(t, n) && Lx(e, n, t[n]);
    if (nd) for (var n of nd(t)) m3.call(t, n) && Lx(e, n, t[n]);
    return e;
  },
  fB = (e, t) => {
    var n = {};
    for (var r in e) p3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && nd)
      for (var r of nd(e)) t.indexOf(r) < 0 && m3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const dB = { size: "sm" },
  T0 = y.forwardRef((e, t) => {
    const n = me("InputDescription", dB, e),
      {
        children: r,
        className: o,
        classNames: a,
        styles: s,
        unstyled: u,
        size: f,
        __staticSelector: d,
      } = n,
      m = fB(n, [
        "children",
        "className",
        "classNames",
        "styles",
        "unstyled",
        "size",
        "__staticSelector",
      ]),
      { classes: h, cx: v } = sB(
        { size: f },
        { name: ["InputWrapper", d], classNames: a, styles: s, unstyled: u }
      );
    return R.createElement(
      Jr,
      cB(
        {
          color: "dimmed",
          className: v(h.description, o),
          ref: t,
          unstyled: u,
        },
        m
      ),
      r
    );
  });
T0.displayName = "@mantine/core/InputDescription";
const h3 = y.createContext({
    offsetBottom: !1,
    offsetTop: !1,
    describedBy: void 0,
  }),
  pB = h3.Provider,
  mB = () => y.useContext(h3);
function hB(e, { hasDescription: t, hasError: n }) {
  const r = e.findIndex((f) => f === "input"),
    o = e[r - 1],
    a = e[r + 1];
  return {
    offsetBottom: (t && a === "description") || (n && a === "error"),
    offsetTop: (t && o === "description") || (n && o === "error"),
  };
}
var vB = Object.defineProperty,
  gB = Object.defineProperties,
  yB = Object.getOwnPropertyDescriptors,
  Dx = Object.getOwnPropertySymbols,
  _B = Object.prototype.hasOwnProperty,
  wB = Object.prototype.propertyIsEnumerable,
  Mx = (e, t, n) =>
    t in e
      ? vB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  bB = (e, t) => {
    for (var n in t || (t = {})) _B.call(t, n) && Mx(e, n, t[n]);
    if (Dx) for (var n of Dx(t)) wB.call(t, n) && Mx(e, n, t[n]);
    return e;
  },
  xB = (e, t) => gB(e, yB(t)),
  SB = Pe((e) => ({
    root: xB(bB({}, e.fn.fontStyles()), { lineHeight: e.lineHeight }),
  }));
const PB = SB;
var OB = Object.defineProperty,
  EB = Object.defineProperties,
  $B = Object.getOwnPropertyDescriptors,
  rd = Object.getOwnPropertySymbols,
  v3 = Object.prototype.hasOwnProperty,
  g3 = Object.prototype.propertyIsEnumerable,
  jx = (e, t, n) =>
    t in e
      ? OB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  xo = (e, t) => {
    for (var n in t || (t = {})) v3.call(t, n) && jx(e, n, t[n]);
    if (rd) for (var n of rd(t)) g3.call(t, n) && jx(e, n, t[n]);
    return e;
  },
  Fx = (e, t) => EB(e, $B(t)),
  CB = (e, t) => {
    var n = {};
    for (var r in e) v3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && rd)
      for (var r of rd(e)) t.indexOf(r) < 0 && g3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const kB = {
    labelElement: "label",
    size: "sm",
    inputContainer: (e) => e,
    inputWrapperOrder: ["label", "description", "input", "error"],
  },
  y3 = y.forwardRef((e, t) => {
    const n = me("InputWrapper", kB, e),
      {
        className: r,
        label: o,
        children: a,
        required: s,
        id: u,
        error: f,
        description: d,
        labelElement: m,
        labelProps: h,
        descriptionProps: v,
        errorProps: b,
        classNames: O,
        styles: E,
        size: $,
        inputContainer: _,
        __staticSelector: w,
        unstyled: P,
        inputWrapperOrder: k,
        withAsterisk: I,
      } = n,
      z = CB(n, [
        "className",
        "label",
        "children",
        "required",
        "id",
        "error",
        "description",
        "labelElement",
        "labelProps",
        "descriptionProps",
        "errorProps",
        "classNames",
        "styles",
        "size",
        "inputContainer",
        "__staticSelector",
        "unstyled",
        "inputWrapperOrder",
        "withAsterisk",
      ]),
      { classes: A, cx: M } = PB(null, {
        classNames: O,
        styles: E,
        name: ["InputWrapper", w],
        unstyled: P,
      }),
      B = {
        classNames: O,
        styles: E,
        unstyled: P,
        size: $,
        __staticSelector: w,
      },
      H = typeof I == "boolean" ? I : s,
      q = u ? `${u}-error` : b == null ? void 0 : b.id,
      Z = u ? `${u}-description` : v == null ? void 0 : v.id,
      xe = `${!!f && typeof f != "boolean" ? q : ""} ${d ? Z : ""}`,
      Ce = xe.trim().length > 0 ? xe.trim() : void 0,
      ce =
        o &&
        R.createElement(
          N0,
          xo(
            xo(
              {
                key: "label",
                labelElement: m,
                id: u ? `${u}-label` : void 0,
                htmlFor: u,
                required: H,
              },
              B
            ),
            h
          ),
          o
        ),
      Se =
        d &&
        R.createElement(
          T0,
          Fx(xo(xo({ key: "description" }, v), B), {
            size: (v == null ? void 0 : v.size) || B.size,
            id: (v == null ? void 0 : v.id) || Z,
          }),
          d
        ),
      Y = R.createElement(y.Fragment, { key: "input" }, _(a)),
      re =
        typeof f != "boolean" &&
        f &&
        R.createElement(
          I0,
          Fx(xo(xo({}, b), B), {
            size: (b == null ? void 0 : b.size) || B.size,
            key: "error",
            id: (b == null ? void 0 : b.id) || q,
          }),
          f
        ),
      ne = k.map((le) => {
        switch (le) {
          case "label":
            return ce;
          case "input":
            return Y;
          case "description":
            return Se;
          case "error":
            return re;
          default:
            return null;
        }
      });
    return R.createElement(
      pB,
      {
        value: xo(
          { describedBy: Ce },
          hB(k, { hasDescription: !!Se, hasError: !!re })
        ),
      },
      R.createElement(Ie, xo({ className: M(A.root, r), ref: t }, z), ne)
    );
  });
y3.displayName = "@mantine/core/InputWrapper";
var RB = Object.defineProperty,
  od = Object.getOwnPropertySymbols,
  _3 = Object.prototype.hasOwnProperty,
  w3 = Object.prototype.propertyIsEnumerable,
  Wx = (e, t, n) =>
    t in e
      ? RB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  NB = (e, t) => {
    for (var n in t || (t = {})) _3.call(t, n) && Wx(e, n, t[n]);
    if (od) for (var n of od(t)) w3.call(t, n) && Wx(e, n, t[n]);
    return e;
  },
  IB = (e, t) => {
    var n = {};
    for (var r in e) _3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && od)
      for (var r of od(e)) t.indexOf(r) < 0 && w3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const TB = {},
  b3 = y.forwardRef((e, t) => {
    const n = me("InputPlaceholder", TB, e),
      { sx: r } = n,
      o = IB(n, ["sx"]);
    return R.createElement(
      Ie,
      NB(
        {
          component: "span",
          sx: [(a) => a.fn.placeholderStyles(), ...c0(r)],
          ref: t,
        },
        o
      )
    );
  });
b3.displayName = "@mantine/core/InputPlaceholder";
var zB = Object.defineProperty,
  AB = Object.defineProperties,
  LB = Object.getOwnPropertyDescriptors,
  Bx = Object.getOwnPropertySymbols,
  DB = Object.prototype.hasOwnProperty,
  MB = Object.prototype.propertyIsEnumerable,
  Ux = (e, t, n) =>
    t in e
      ? zB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  wc = (e, t) => {
    for (var n in t || (t = {})) DB.call(t, n) && Ux(e, n, t[n]);
    if (Bx) for (var n of Bx(t)) MB.call(t, n) && Ux(e, n, t[n]);
    return e;
  },
  bh = (e, t) => AB(e, LB(t));
const Ln = { xs: 30, sm: 36, md: 42, lg: 50, xl: 60 };
function jB({ theme: e, variant: t }) {
  return t === "default"
    ? {
        border: `1px solid ${
          e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[4]
        }`,
        backgroundColor: e.colorScheme === "dark" ? e.colors.dark[6] : e.white,
        transition: "border-color 100ms ease",
        "&:focus, &:focus-within": e.focusRingStyles.inputStyles(e),
      }
    : t === "filled"
    ? {
        border: "1px solid transparent",
        backgroundColor:
          e.colorScheme === "dark" ? e.colors.dark[5] : e.colors.gray[1],
        "&:focus, &:focus-within": e.focusRingStyles.inputStyles(e),
      }
    : {
        borderWidth: 0,
        color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
        backgroundColor: "transparent",
        minHeight: 28,
        outline: 0,
        "&:focus, &:focus-within": {
          outline: "none",
          borderColor: "transparent",
        },
        "&:disabled": {
          backgroundColor: "transparent",
          "&:focus, &:focus-within": {
            outline: "none",
            borderColor: "transparent",
          },
        },
      };
}
var FB = Pe(
  (
    e,
    {
      size: t,
      multiline: n,
      radius: r,
      variant: o,
      invalid: a,
      rightSectionWidth: s,
      withRightSection: u,
      iconWidth: f,
      offsetBottom: d,
      offsetTop: m,
      pointer: h,
    }
  ) => {
    const v = e.fn.variant({ variant: "filled", color: "red" }).background,
      b =
        o === "default" || o === "filled"
          ? {
              minHeight: e.fn.size({ size: t, sizes: Ln }),
              paddingLeft: e.fn.size({ size: t, sizes: Ln }) / 3,
              paddingRight: u ? s : e.fn.size({ size: t, sizes: Ln }) / 3,
              borderRadius: e.fn.radius(r),
            }
          : null;
    return {
      wrapper: {
        position: "relative",
        marginTop: m ? `calc(${e.spacing.xs}px / 2)` : void 0,
        marginBottom: d ? `calc(${e.spacing.xs}px / 2)` : void 0,
      },
      input: wc(
        bh(
          wc(
            bh(wc({}, e.fn.fontStyles()), {
              height: n
                ? o === "unstyled"
                  ? void 0
                  : "auto"
                : e.fn.size({ size: t, sizes: Ln }),
              WebkitTapHighlightColor: "transparent",
              lineHeight: n
                ? e.lineHeight
                : `${e.fn.size({ size: t, sizes: Ln }) - 2}px`,
              appearance: "none",
              resize: "none",
              boxSizing: "border-box",
              fontSize: e.fn.size({ size: t, sizes: e.fontSizes }),
              width: "100%",
              color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
              display: "block",
              textAlign: "left",
              cursor: h ? "pointer" : void 0,
            }),
            b
          ),
          {
            "&:disabled": {
              backgroundColor:
                e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[1],
              color: e.colors.dark[2],
              opacity: 0.6,
              cursor: "not-allowed",
              "&::placeholder": { color: e.colors.dark[2] },
            },
            "&::placeholder": bh(wc({}, e.fn.placeholderStyles()), {
              opacity: 1,
            }),
            "&::-webkit-inner-spin-button, &::-webkit-outer-spin-button, &::-webkit-search-decoration, &::-webkit-search-cancel-button, &::-webkit-search-results-button, &::-webkit-search-results-decoration":
              { appearance: "none" },
            "&[type=number]": { MozAppearance: "textfield" },
          }
        ),
        jB({ theme: e, variant: o })
      ),
      withIcon: {
        paddingLeft:
          typeof f == "number" ? f : e.fn.size({ size: t, sizes: Ln }),
      },
      invalid: {
        color: v,
        borderColor: v,
        "&::placeholder": { opacity: 1, color: v },
      },
      disabled: {
        backgroundColor:
          e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[1],
        color: e.colors.dark[2],
        opacity: 0.6,
        cursor: "not-allowed",
        "&::placeholder": { color: e.colors.dark[2] },
      },
      icon: {
        pointerEvents: "none",
        position: "absolute",
        zIndex: 1,
        left: 0,
        top: 0,
        bottom: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: typeof f == "number" ? f : e.fn.size({ size: t, sizes: Ln }),
        color: a
          ? e.colors.red[e.colorScheme === "dark" ? 6 : 7]
          : e.colorScheme === "dark"
          ? e.colors.dark[2]
          : e.colors.gray[5],
      },
      rightSection: {
        position: "absolute",
        top: 0,
        bottom: 0,
        right: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: s,
      },
    };
  }
);
const WB = FB;
var BB = Object.defineProperty,
  UB = Object.defineProperties,
  HB = Object.getOwnPropertyDescriptors,
  id = Object.getOwnPropertySymbols,
  x3 = Object.prototype.hasOwnProperty,
  S3 = Object.prototype.propertyIsEnumerable,
  Hx = (e, t, n) =>
    t in e
      ? BB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  bc = (e, t) => {
    for (var n in t || (t = {})) x3.call(t, n) && Hx(e, n, t[n]);
    if (id) for (var n of id(t)) S3.call(t, n) && Hx(e, n, t[n]);
    return e;
  },
  Vx = (e, t) => UB(e, HB(t)),
  VB = (e, t) => {
    var n = {};
    for (var r in e) x3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && id)
      for (var r of id(e)) t.indexOf(r) < 0 && S3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const YB = { rightSectionWidth: 36, size: "sm", variant: "default" },
  ji = y.forwardRef((e, t) => {
    const n = me("Input", YB, e),
      {
        className: r,
        invalid: o,
        required: a,
        disabled: s,
        variant: u,
        icon: f,
        style: d,
        rightSectionWidth: m,
        iconWidth: h,
        rightSection: v,
        rightSectionProps: b,
        radius: O,
        size: E,
        wrapperProps: $,
        classNames: _,
        styles: w,
        __staticSelector: P,
        multiline: k,
        sx: I,
        unstyled: z,
        pointer: A,
      } = n,
      M = VB(n, [
        "className",
        "invalid",
        "required",
        "disabled",
        "variant",
        "icon",
        "style",
        "rightSectionWidth",
        "iconWidth",
        "rightSection",
        "rightSectionProps",
        "radius",
        "size",
        "wrapperProps",
        "classNames",
        "styles",
        "__staticSelector",
        "multiline",
        "sx",
        "unstyled",
        "pointer",
      ]),
      { offsetBottom: B, offsetTop: H, describedBy: q } = mB(),
      { classes: Z, cx: he } = WB(
        {
          radius: O,
          size: E,
          multiline: k,
          variant: u,
          invalid: o,
          rightSectionWidth: m,
          iconWidth: h,
          withRightSection: !!v,
          offsetBottom: B,
          offsetTop: H,
          pointer: A,
        },
        { classNames: _, styles: w, name: ["Input", P], unstyled: z }
      ),
      { systemStyles: xe, rest: Ce } = Xs(M);
    return R.createElement(
      Ie,
      bc(bc({ className: he(Z.wrapper, r), sx: I, style: d }, xe), $),
      f && R.createElement("div", { className: Z.icon }, f),
      R.createElement(
        Ie,
        Vx(bc({ component: "input" }, Ce), {
          ref: t,
          required: a,
          "aria-invalid": o,
          "aria-describedby": q,
          disabled: s,
          className: he(Z[`${u}Variant`], Z.input, {
            [Z.withIcon]: f,
            [Z.invalid]: o,
            [Z.disabled]: s,
          }),
        })
      ),
      v &&
        R.createElement("div", Vx(bc({}, b), { className: Z.rightSection }), v)
    );
  });
ji.displayName = "@mantine/core/Input";
ji.Wrapper = y3;
ji.Label = N0;
ji.Description = T0;
ji.Error = I0;
ji.Placeholder = b3;
const Er = ji;
var GB = Object.defineProperty,
  XB = Object.defineProperties,
  KB = Object.getOwnPropertyDescriptors,
  Yx = Object.getOwnPropertySymbols,
  QB = Object.prototype.hasOwnProperty,
  qB = Object.prototype.propertyIsEnumerable,
  Gx = (e, t, n) =>
    t in e
      ? GB(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  xh = (e, t) => {
    for (var n in t || (t = {})) QB.call(t, n) && Gx(e, n, t[n]);
    if (Yx) for (var n of Yx(t)) qB.call(t, n) && Gx(e, n, t[n]);
    return e;
  },
  ZB = (e, t) => XB(e, KB(t));
const Sh = {
    xs: { fontSize: 9, height: 16 },
    sm: { fontSize: 10, height: 18 },
    md: { fontSize: 11, height: 20 },
    lg: { fontSize: 13, height: 26 },
    xl: { fontSize: 16, height: 32 },
  },
  JB = { xs: 4, sm: 4, md: 6, lg: 8, xl: 10 };
function eU({ theme: e, variant: t, color: n, size: r, gradient: o }) {
  if (t === "dot") {
    const s = e.fn.size({ size: r, sizes: JB });
    return {
      backgroundColor: "transparent",
      color: e.colorScheme === "dark" ? e.colors.dark[0] : e.colors.gray[7],
      border: `1px solid ${
        e.colorScheme === "dark" ? e.colors.dark[3] : e.colors.gray[3]
      }`,
      paddingLeft: e.fn.size({ size: r, sizes: e.spacing }) / 1.5 - s / 2,
      "&::before": {
        content: '""',
        display: "block",
        width: s,
        height: s,
        borderRadius: s,
        backgroundColor: e.fn.themeColor(
          n,
          e.colorScheme === "dark" ? 4 : e.fn.primaryShade("light"),
          !0
        ),
        marginRight: s,
      },
    };
  }
  const a = e.fn.variant({ color: n, variant: t, gradient: o });
  return {
    background: a.background,
    color: a.color,
    border: `${t === "gradient" ? 0 : 1}px solid ${a.border}`,
  };
}
var tU = Pe(
  (
    e,
    { color: t, size: n, radius: r, gradient: o, fullWidth: a, variant: s }
  ) => {
    const { fontSize: u, height: f } = n in Sh ? Sh[n] : Sh.md;
    return {
      leftSection: { marginRight: `calc(${e.spacing.xs}px / 2)` },
      rightSection: { marginLeft: `calc(${e.spacing.xs}px / 2)` },
      inner: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
      },
      root: xh(
        ZB(xh(xh({}, e.fn.focusStyles()), e.fn.fontStyles()), {
          fontSize: u,
          height: f,
          WebkitTapHighlightColor: "transparent",
          lineHeight: `${f - 2}px`,
          textDecoration: "none",
          padding: `0 ${e.fn.size({ size: n, sizes: e.spacing }) / 1.5}px`,
          boxSizing: "border-box",
          display: a ? "flex" : "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: a ? "100%" : "auto",
          textTransform: "uppercase",
          borderRadius: e.fn.radius(r),
          fontWeight: 700,
          letterSpacing: 0.25,
          cursor: "inherit",
          textOverflow: "ellipsis",
          overflow: "hidden",
        }),
        eU({ theme: e, variant: s, color: t, size: n, gradient: o })
      ),
    };
  }
);
const nU = tU;
var rU = Object.defineProperty,
  ad = Object.getOwnPropertySymbols,
  P3 = Object.prototype.hasOwnProperty,
  O3 = Object.prototype.propertyIsEnumerable,
  Xx = (e, t, n) =>
    t in e
      ? rU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  oU = (e, t) => {
    for (var n in t || (t = {})) P3.call(t, n) && Xx(e, n, t[n]);
    if (ad) for (var n of ad(t)) O3.call(t, n) && Xx(e, n, t[n]);
    return e;
  },
  iU = (e, t) => {
    var n = {};
    for (var r in e) P3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && ad)
      for (var r of ad(e)) t.indexOf(r) < 0 && O3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const aU = { variant: "light", size: "md", radius: "xl" },
  E3 = y.forwardRef((e, t) => {
    const n = me("Badge", aU, e),
      {
        className: r,
        color: o,
        variant: a,
        fullWidth: s,
        children: u,
        size: f,
        leftSection: d,
        rightSection: m,
        radius: h,
        gradient: v,
        classNames: b,
        styles: O,
        unstyled: E,
      } = n,
      $ = iU(n, [
        "className",
        "color",
        "variant",
        "fullWidth",
        "children",
        "size",
        "leftSection",
        "rightSection",
        "radius",
        "gradient",
        "classNames",
        "styles",
        "unstyled",
      ]),
      { classes: _, cx: w } = nU(
        { size: f, fullWidth: s, color: o, radius: h, variant: a, gradient: v },
        { classNames: b, styles: O, name: "Badge", unstyled: E }
      );
    return R.createElement(
      Ie,
      oU({ className: w(_.root, r), ref: t }, $),
      d && R.createElement("span", { className: _.leftSection }, d),
      R.createElement("span", { className: _.inner }, u),
      m && R.createElement("span", { className: _.rightSection }, m)
    );
  });
E3.displayName = "@mantine/core/Badge";
const yK = E3;
var lU = Pe((e, { orientation: t, buttonBorderWidth: n }) => ({
  root: {
    display: "flex",
    flexDirection: t === "vertical" ? "column" : "row",
    "& [data-button]": {
      "&:first-of-type": {
        borderBottomRightRadius: 0,
        [t === "vertical"
          ? "borderBottomLeftRadius"
          : "borderTopRightRadius"]: 0,
        [t === "vertical" ? "borderBottomWidth" : "borderRightWidth"]: n / 2,
      },
      "&:last-of-type": {
        borderTopLeftRadius: 0,
        [t === "vertical"
          ? "borderTopRightRadius"
          : "borderBottomLeftRadius"]: 0,
        [t === "vertical" ? "borderTopWidth" : "borderLeftWidth"]: n / 2,
      },
      "&:not(:first-of-type):not(:last-of-type)": {
        borderRadius: 0,
        [t === "vertical" ? "borderTopWidth" : "borderLeftWidth"]: n / 2,
        [t === "vertical" ? "borderBottomWidth" : "borderRightWidth"]: n / 2,
      },
      "& + [data-button]": {
        [t === "vertical" ? "marginTop" : "marginLeft"]: -n,
        "@media (min-resolution: 192dpi)": {
          [t === "vertical" ? "marginTop" : "marginLeft"]: 0,
        },
      },
    },
  },
}));
const sU = lU;
var uU = Object.defineProperty,
  ld = Object.getOwnPropertySymbols,
  $3 = Object.prototype.hasOwnProperty,
  C3 = Object.prototype.propertyIsEnumerable,
  Kx = (e, t, n) =>
    t in e
      ? uU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  cU = (e, t) => {
    for (var n in t || (t = {})) $3.call(t, n) && Kx(e, n, t[n]);
    if (ld) for (var n of ld(t)) C3.call(t, n) && Kx(e, n, t[n]);
    return e;
  },
  fU = (e, t) => {
    var n = {};
    for (var r in e) $3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && ld)
      for (var r of ld(e)) t.indexOf(r) < 0 && C3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const dU = { orientation: "horizontal", buttonBorderWidth: 1 },
  k3 = y.forwardRef((e, t) => {
    const n = me("ButtonGroup", dU, e),
      { className: r, orientation: o, buttonBorderWidth: a, unstyled: s } = n,
      u = fU(n, ["className", "orientation", "buttonBorderWidth", "unstyled"]),
      { classes: f, cx: d } = sU(
        { orientation: o, buttonBorderWidth: a },
        { name: "ButtonGroup", unstyled: s }
      );
    return R.createElement(Ie, cU({ className: d(f.root, r), ref: t }, u));
  });
k3.displayName = "@mantine/core/ButtonGroup";
var pU = Object.defineProperty,
  mU = Object.defineProperties,
  hU = Object.getOwnPropertyDescriptors,
  Qx = Object.getOwnPropertySymbols,
  vU = Object.prototype.hasOwnProperty,
  gU = Object.prototype.propertyIsEnumerable,
  qx = (e, t, n) =>
    t in e
      ? pU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  yi = (e, t) => {
    for (var n in t || (t = {})) vU.call(t, n) && qx(e, n, t[n]);
    if (Qx) for (var n of Qx(t)) gU.call(t, n) && qx(e, n, t[n]);
    return e;
  },
  Vv = (e, t) => mU(e, hU(t));
const Yv = {
  xs: { height: Ln.xs, paddingLeft: 14, paddingRight: 14 },
  sm: { height: Ln.sm, paddingLeft: 18, paddingRight: 18 },
  md: { height: Ln.md, paddingLeft: 22, paddingRight: 22 },
  lg: { height: Ln.lg, paddingLeft: 26, paddingRight: 26 },
  xl: { height: Ln.xl, paddingLeft: 32, paddingRight: 32 },
  "compact-xs": { height: 22, paddingLeft: 7, paddingRight: 7 },
  "compact-sm": { height: 26, paddingLeft: 8, paddingRight: 8 },
  "compact-md": { height: 30, paddingLeft: 10, paddingRight: 10 },
  "compact-lg": { height: 34, paddingLeft: 12, paddingRight: 12 },
  "compact-xl": { height: 40, paddingLeft: 14, paddingRight: 14 },
};
function yU({ compact: e, size: t, withLeftIcon: n, withRightIcon: r }) {
  if (e) return Yv[`compact-${t}`];
  const o = Yv[t];
  return Vv(yi({}, o), {
    paddingLeft: n ? o.paddingLeft / 1.5 : o.paddingLeft,
    paddingRight: r ? o.paddingRight / 1.5 : o.paddingRight,
  });
}
const _U = (e) => ({
  display: e ? "block" : "inline-block",
  width: e ? "100%" : "auto",
});
function wU({ variant: e, theme: t, color: n, gradient: r }) {
  const o = t.fn.variant({ color: n, variant: e, gradient: r });
  return e === "gradient"
    ? {
        border: 0,
        backgroundImage: o.background,
        color: o.color,
        "&:hover": t.fn.hover({ backgroundSize: "200%" }),
      }
    : yi(
        {
          border: `1px solid ${o.border}`,
          backgroundColor: o.background,
          color: o.color,
        },
        t.fn.hover({ backgroundColor: o.hover })
      );
}
var bU = Pe(
  (
    e,
    {
      color: t,
      size: n,
      radius: r,
      fullWidth: o,
      compact: a,
      gradient: s,
      variant: u,
      withLeftIcon: f,
      withRightIcon: d,
    }
  ) => ({
    root: Vv(
      yi(
        Vv(
          yi(
            yi(
              yi(
                yi(
                  {},
                  yU({ compact: a, size: n, withLeftIcon: f, withRightIcon: d })
                ),
                e.fn.fontStyles()
              ),
              e.fn.focusStyles()
            ),
            _U(o)
          ),
          {
            borderRadius: e.fn.radius(r),
            fontWeight: 600,
            position: "relative",
            lineHeight: 1,
            fontSize: e.fn.size({ size: n, sizes: e.fontSizes }),
            userSelect: "none",
            cursor: "pointer",
          }
        ),
        wU({ variant: u, theme: e, color: t, gradient: s })
      ),
      {
        "&:active": e.activeStyles,
        "&:disabled, &[data-disabled]": {
          borderColor: "transparent",
          backgroundColor:
            e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2],
          color: e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[5],
          cursor: "not-allowed",
          backgroundImage: "none",
          pointerEvents: "none",
          "&:active": { transform: "none" },
        },
        "&[data-loading]": {
          pointerEvents: "none",
          "&::before": {
            content: '""',
            position: "absolute",
            top: -1,
            left: -1,
            right: -1,
            bottom: -1,
            backgroundColor:
              e.colorScheme === "dark"
                ? e.fn.rgba(e.colors.dark[7], 0.5)
                : "rgba(255, 255, 255, .5)",
            borderRadius: e.fn.radius(r),
            cursor: "not-allowed",
          },
        },
      }
    ),
    icon: { display: "flex", alignItems: "center" },
    leftIcon: { marginRight: 10 },
    rightIcon: { marginLeft: 10 },
    centerLoader: {
      position: "absolute",
      left: "50%",
      transform: "translateX(-50%)",
      opacity: 0.5,
    },
    inner: {
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      height: "100%",
      overflow: "visible",
    },
    label: {
      whiteSpace: "nowrap",
      height: "100%",
      overflow: "hidden",
      display: "flex",
      alignItems: "center",
    },
  })
);
const xU = bU;
var SU = Object.defineProperty,
  sd = Object.getOwnPropertySymbols,
  R3 = Object.prototype.hasOwnProperty,
  N3 = Object.prototype.propertyIsEnumerable,
  Zx = (e, t, n) =>
    t in e
      ? SU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Jx = (e, t) => {
    for (var n in t || (t = {})) R3.call(t, n) && Zx(e, n, t[n]);
    if (sd) for (var n of sd(t)) N3.call(t, n) && Zx(e, n, t[n]);
    return e;
  },
  PU = (e, t) => {
    var n = {};
    for (var r in e) R3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && sd)
      for (var r of sd(e)) t.indexOf(r) < 0 && N3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const OU = {
    size: "sm",
    type: "button",
    variant: "filled",
    loaderPosition: "left",
  },
  z0 = y.forwardRef((e, t) => {
    const n = me("Button", OU, e),
      {
        className: r,
        size: o,
        color: a,
        type: s,
        disabled: u,
        children: f,
        leftIcon: d,
        rightIcon: m,
        fullWidth: h,
        variant: v,
        radius: b,
        uppercase: O,
        compact: E,
        loading: $,
        loaderPosition: _,
        loaderProps: w,
        gradient: P,
        classNames: k,
        styles: I,
        unstyled: z,
      } = n,
      A = PU(n, [
        "className",
        "size",
        "color",
        "type",
        "disabled",
        "children",
        "leftIcon",
        "rightIcon",
        "fullWidth",
        "variant",
        "radius",
        "uppercase",
        "compact",
        "loading",
        "loaderPosition",
        "loaderProps",
        "gradient",
        "classNames",
        "styles",
        "unstyled",
      ]),
      {
        classes: M,
        cx: B,
        theme: H,
      } = xU(
        {
          radius: b,
          color: a,
          size: o,
          fullWidth: h,
          compact: E,
          gradient: P,
          variant: v,
          withLeftIcon: !!d,
          withRightIcon: !!m,
        },
        { name: "Button", unstyled: z, classNames: k, styles: I }
      ),
      q = H.fn.variant({ color: a, variant: v }),
      Z = R.createElement(
        pp,
        Jx(
          {
            color: q.color,
            size: H.fn.size({ size: o, sizes: Yv }).height / 2,
          },
          w
        )
      );
    return R.createElement(
      NE,
      Jx(
        {
          className: B(M.root, r),
          type: s,
          disabled: u,
          "data-button": !0,
          "data-disabled": u || void 0,
          "data-loading": $ || void 0,
          ref: t,
          unstyled: z,
        },
        A
      ),
      R.createElement(
        "div",
        { className: M.inner },
        (d || ($ && _ === "left")) &&
          R.createElement(
            "span",
            { className: B(M.icon, M.leftIcon) },
            $ && _ === "left" ? Z : d
          ),
        $ &&
          _ === "center" &&
          R.createElement("span", { className: M.centerLoader }, Z),
        R.createElement(
          "span",
          {
            className: M.label,
            style: { textTransform: O ? "uppercase" : void 0 },
          },
          f
        ),
        (m || ($ && _ === "right")) &&
          R.createElement(
            "span",
            { className: B(M.icon, M.rightIcon) },
            $ && _ === "right" ? Z : m
          )
      )
    );
  });
z0.displayName = "@mantine/core/Button";
z0.Group = k3;
const _K = z0;
var EU = Pe((e, { radius: t, shadow: n, withBorder: r }) => ({
  root: {
    outline: 0,
    WebkitTapHighlightColor: "transparent",
    display: "block",
    textDecoration: "none",
    color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
    backgroundColor: e.colorScheme === "dark" ? e.colors.dark[7] : e.white,
    boxSizing: "border-box",
    borderRadius: e.fn.radius(t),
    boxShadow: e.shadows[n] || n || "none",
    border: r
      ? `1px solid ${
          e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[3]
        }`
      : void 0,
  },
}));
const $U = EU;
var CU = Object.defineProperty,
  ud = Object.getOwnPropertySymbols,
  I3 = Object.prototype.hasOwnProperty,
  T3 = Object.prototype.propertyIsEnumerable,
  e2 = (e, t, n) =>
    t in e
      ? CU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  kU = (e, t) => {
    for (var n in t || (t = {})) I3.call(t, n) && e2(e, n, t[n]);
    if (ud) for (var n of ud(t)) T3.call(t, n) && e2(e, n, t[n]);
    return e;
  },
  RU = (e, t) => {
    var n = {};
    for (var r in e) I3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && ud)
      for (var r of ud(e)) t.indexOf(r) < 0 && T3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const NU = {},
  z3 = y.forwardRef((e, t) => {
    const n = me("Paper", NU, e),
      {
        className: r,
        children: o,
        radius: a,
        withBorder: s,
        shadow: u,
        unstyled: f,
      } = n,
      d = RU(n, [
        "className",
        "children",
        "radius",
        "withBorder",
        "shadow",
        "unstyled",
      ]),
      { classes: m, cx: h } = $U(
        { radius: a, shadow: u, withBorder: s },
        { name: "Paper", unstyled: f }
      );
    return R.createElement(Ie, kU({ className: h(m.root, r), ref: t }, d), o);
  });
z3.displayName = "@mantine/core/Paper";
const wK = z3;
var IU = Object.defineProperty,
  cd = Object.getOwnPropertySymbols,
  A3 = Object.prototype.hasOwnProperty,
  L3 = Object.prototype.propertyIsEnumerable,
  t2 = (e, t, n) =>
    t in e
      ? IU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  TU = (e, t) => {
    for (var n in t || (t = {})) A3.call(t, n) && t2(e, n, t[n]);
    if (cd) for (var n of cd(t)) L3.call(t, n) && t2(e, n, t[n]);
    return e;
  },
  zU = (e, t) => {
    var n = {};
    for (var r in e) A3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && cd)
      for (var r of cd(e)) t.indexOf(r) < 0 && L3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const D3 = y.forwardRef((e, t) => {
  const n = me("Center", {}, e),
    { inline: r, sx: o } = n,
    a = zU(n, ["inline", "sx"]);
  return R.createElement(
    Ie,
    TU(
      {
        ref: t,
        sx: [
          {
            display: r ? "inline-flex" : "flex",
            alignItems: "center",
            justifyContent: "center",
          },
          ...c0(o),
        ],
      },
      a
    )
  );
});
D3.displayName = "@mantine/core/Center";
const bK = D3;
function AU(e) {
  return y.Children.toArray(e).filter(Boolean);
}
const LU = {
  left: "flex-start",
  center: "center",
  right: "flex-end",
  apart: "space-between",
};
var DU = Pe(
  (e, { spacing: t, position: n, noWrap: r, grow: o, align: a, count: s }) => ({
    root: {
      boxSizing: "border-box",
      display: "flex",
      flexDirection: "row",
      alignItems: a || "center",
      flexWrap: r ? "nowrap" : "wrap",
      justifyContent: LU[n],
      gap: e.fn.size({ size: t, sizes: e.spacing }),
      "& > *": {
        boxSizing: "border-box",
        maxWidth: o
          ? `calc(${100 / s}% - ${
              e.fn.size({ size: t, sizes: e.spacing }) -
              e.fn.size({ size: t, sizes: e.spacing }) / s
            }px)`
          : void 0,
        flexGrow: o ? 1 : 0,
      },
    },
  })
);
const MU = DU;
var jU = Object.defineProperty,
  fd = Object.getOwnPropertySymbols,
  M3 = Object.prototype.hasOwnProperty,
  j3 = Object.prototype.propertyIsEnumerable,
  n2 = (e, t, n) =>
    t in e
      ? jU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  FU = (e, t) => {
    for (var n in t || (t = {})) M3.call(t, n) && n2(e, n, t[n]);
    if (fd) for (var n of fd(t)) j3.call(t, n) && n2(e, n, t[n]);
    return e;
  },
  WU = (e, t) => {
    var n = {};
    for (var r in e) M3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && fd)
      for (var r of fd(e)) t.indexOf(r) < 0 && j3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const BU = { position: "left", spacing: "md" },
  F3 = y.forwardRef((e, t) => {
    const n = me("Group", BU, e),
      {
        className: r,
        position: o,
        align: a,
        children: s,
        noWrap: u,
        grow: f,
        spacing: d,
        unstyled: m,
      } = n,
      h = WU(n, [
        "className",
        "position",
        "align",
        "children",
        "noWrap",
        "grow",
        "spacing",
        "unstyled",
      ]),
      v = AU(s),
      { classes: b, cx: O } = MU(
        {
          align: a,
          grow: f,
          noWrap: u,
          spacing: d,
          position: o,
          count: v.length,
        },
        { unstyled: m, name: "Group" }
      );
    return R.createElement(Ie, FU({ className: O(b.root, r), ref: t }, h), v);
  });
F3.displayName = "@mantine/core/Group";
var UU = Pe((e, { spacing: t, align: n, justify: r }) => ({
  root: {
    display: "flex",
    flexDirection: "column",
    alignItems: n,
    justifyContent: r,
    gap: e.fn.size({ size: t, sizes: e.spacing }),
  },
}));
const HU = UU;
var VU = Object.defineProperty,
  dd = Object.getOwnPropertySymbols,
  W3 = Object.prototype.hasOwnProperty,
  B3 = Object.prototype.propertyIsEnumerable,
  r2 = (e, t, n) =>
    t in e
      ? VU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  YU = (e, t) => {
    for (var n in t || (t = {})) W3.call(t, n) && r2(e, n, t[n]);
    if (dd) for (var n of dd(t)) B3.call(t, n) && r2(e, n, t[n]);
    return e;
  },
  GU = (e, t) => {
    var n = {};
    for (var r in e) W3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && dd)
      for (var r of dd(e)) t.indexOf(r) < 0 && B3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const XU = { spacing: "md", align: "stretch", justify: "flex-start" },
  U3 = y.forwardRef((e, t) => {
    const n = me("Stack", XU, e),
      { spacing: r, className: o, align: a, justify: s, unstyled: u } = n,
      f = GU(n, ["spacing", "className", "align", "justify", "unstyled"]),
      { classes: d, cx: m } = HU(
        { spacing: r, align: a, justify: s },
        { name: "Stack", unstyled: u }
      );
    return R.createElement(Ie, YU({ className: m(d.root, o), ref: t }, f));
  });
U3.displayName = "@mantine/core/Stack";
function KU({
  spacing: e,
  offset: t,
  orientation: n,
  children: r,
  role: o,
  unstyled: a,
}) {
  return n === "horizontal"
    ? R.createElement(F3, { pt: t, spacing: e, role: o, unstyled: a }, r)
    : R.createElement(U3, { pt: t, spacing: e, role: o, unstyled: a }, r);
}
var QU = Object.defineProperty,
  qU = Object.defineProperties,
  ZU = Object.getOwnPropertyDescriptors,
  o2 = Object.getOwnPropertySymbols,
  JU = Object.prototype.hasOwnProperty,
  eH = Object.prototype.propertyIsEnumerable,
  i2 = (e, t, n) =>
    t in e
      ? QU(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  tH = (e, t) => {
    for (var n in t || (t = {})) JU.call(t, n) && i2(e, n, t[n]);
    if (o2) for (var n of o2(t)) eH.call(t, n) && i2(e, n, t[n]);
    return e;
  },
  nH = (e, t) => qU(e, ZU(t));
const rH = { xs: 16, sm: 20, md: 24, lg: 30, xl: 36 };
var oH = Pe((e, { labelPosition: t, size: n }) => ({
  root: {},
  body: { display: "flex" },
  labelWrapper: nH(tH({}, e.fn.fontStyles()), {
    display: "inline-flex",
    flexDirection: "column",
    WebkitTapHighlightColor: "transparent",
    fontSize: e.fn.size({ size: n, sizes: e.fontSizes }),
    lineHeight: `${e.fn.size({ size: n, sizes: rH })}px`,
    color: e.colorScheme === "dark" ? e.colors.dark[0] : e.black,
    cursor: e.cursorType,
    order: t === "left" ? 1 : 2,
  }),
  description: {
    marginTop: `calc(${e.spacing.xs}px / 2)`,
    [t === "left" ? "paddingRight" : "paddingLeft"]: e.spacing.sm,
  },
  error: {
    marginTop: `calc(${e.spacing.xs}px / 2)`,
    [t === "left" ? "paddingRight" : "paddingLeft"]: e.spacing.sm,
  },
  label: {
    cursor: e.cursorType,
    [t === "left" ? "paddingRight" : "paddingLeft"]: e.spacing.sm,
    "&[data-disabled]": {
      color: e.colorScheme === "dark" ? e.colors.dark[3] : e.colors.gray[5],
    },
  },
}));
const iH = oH;
var aH = Object.defineProperty,
  pd = Object.getOwnPropertySymbols,
  H3 = Object.prototype.hasOwnProperty,
  V3 = Object.prototype.propertyIsEnumerable,
  a2 = (e, t, n) =>
    t in e
      ? aH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  lH = (e, t) => {
    for (var n in t || (t = {})) H3.call(t, n) && a2(e, n, t[n]);
    if (pd) for (var n of pd(t)) V3.call(t, n) && a2(e, n, t[n]);
    return e;
  },
  sH = (e, t) => {
    var n = {};
    for (var r in e) H3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && pd)
      for (var r of pd(e)) t.indexOf(r) < 0 && V3.call(e, r) && (n[r] = e[r]);
    return n;
  };
function Y3(e) {
  var t = e,
    {
      __staticSelector: n,
      className: r,
      classNames: o,
      styles: a,
      unstyled: s,
      children: u,
      label: f,
      description: d,
      id: m,
      disabled: h,
      error: v,
      size: b,
      labelPosition: O,
    } = t,
    E = sH(t, [
      "__staticSelector",
      "className",
      "classNames",
      "styles",
      "unstyled",
      "children",
      "label",
      "description",
      "id",
      "disabled",
      "error",
      "size",
      "labelPosition",
    ]);
  const { classes: $, cx: _ } = iH(
    { size: b, labelPosition: O },
    { name: n, styles: a, classNames: o, unstyled: s }
  );
  return R.createElement(
    Ie,
    lH({ className: _($.root, r) }, E),
    R.createElement(
      "div",
      { className: _($.body) },
      u,
      R.createElement(
        "div",
        { className: $.labelWrapper },
        f &&
          R.createElement(
            "label",
            { className: $.label, "data-disabled": h || void 0, htmlFor: m },
            f
          ),
        d && R.createElement(Er.Description, { className: $.description }, d),
        v &&
          v !== "boolean" &&
          R.createElement(Er.Error, { className: $.error }, v)
      )
    )
  );
}
Y3.displayName = "@mantine/core/InlineInput";
var uH = Pe((e, { fluid: t, size: n, sizes: r }) => ({
  root: {
    paddingLeft: e.spacing.md,
    paddingRight: e.spacing.md,
    maxWidth: t ? "100%" : e.fn.size({ size: n, sizes: r }),
    marginLeft: "auto",
    marginRight: "auto",
  },
}));
const cH = uH;
var fH = Object.defineProperty,
  md = Object.getOwnPropertySymbols,
  G3 = Object.prototype.hasOwnProperty,
  X3 = Object.prototype.propertyIsEnumerable,
  l2 = (e, t, n) =>
    t in e
      ? fH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  dH = (e, t) => {
    for (var n in t || (t = {})) G3.call(t, n) && l2(e, n, t[n]);
    if (md) for (var n of md(t)) X3.call(t, n) && l2(e, n, t[n]);
    return e;
  },
  pH = (e, t) => {
    var n = {};
    for (var r in e) G3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && md)
      for (var r of md(e)) t.indexOf(r) < 0 && X3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const mH = { sizes: { xs: 540, sm: 720, md: 960, lg: 1140, xl: 1320 } },
  hH = y.forwardRef((e, t) => {
    const n = me("Container", mH, e),
      { className: r, fluid: o, size: a, unstyled: s, sizes: u } = n,
      f = pH(n, ["className", "fluid", "size", "unstyled", "sizes"]),
      { classes: d, cx: m } = cH(
        { fluid: o, size: a, sizes: u },
        { unstyled: s, name: "Container" }
      );
    return R.createElement(Ie, dH({ className: m(d.root, r), ref: t }, f));
  });
hH.displayName = "@mantine/core/Container";
var vH = Object.defineProperty,
  hd = Object.getOwnPropertySymbols,
  K3 = Object.prototype.hasOwnProperty,
  Q3 = Object.prototype.propertyIsEnumerable,
  s2 = (e, t, n) =>
    t in e
      ? vH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  gH = (e, t) => {
    for (var n in t || (t = {})) K3.call(t, n) && s2(e, n, t[n]);
    if (hd) for (var n of hd(t)) Q3.call(t, n) && s2(e, n, t[n]);
    return e;
  },
  yH = (e, t) => {
    var n = {};
    for (var r in e) K3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && hd)
      for (var r of hd(e)) t.indexOf(r) < 0 && Q3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const _H = { timeout: 1e3 };
function wH(e) {
  const t = me("CopyButton", _H, e),
    { children: n, timeout: r, value: o } = t,
    a = yH(t, ["children", "timeout", "value"]),
    s = SL({ timeout: r }),
    u = () => s.copy(o);
  return R.createElement(
    R.Fragment,
    null,
    n(gH({ copy: u, copied: s.copied }, a))
  );
}
wH.displayName = "@mantine/core/CopyButton";
var bH = Object.defineProperty,
  u2 = Object.getOwnPropertySymbols,
  xH = Object.prototype.hasOwnProperty,
  SH = Object.prototype.propertyIsEnumerable,
  c2 = (e, t, n) =>
    t in e
      ? bH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  PH = (e, t) => {
    for (var n in t || (t = {})) xH.call(t, n) && c2(e, n, t[n]);
    if (u2) for (var n of u2(t)) SH.call(t, n) && c2(e, n, t[n]);
    return e;
  };
function OH(e) {
  return R.createElement(
    "svg",
    PH(
      {
        width: "15",
        height: "15",
        viewBox: "0 0 15 15",
        fill: "none",
        xmlns: "http://www.w3.org/2000/svg",
      },
      e
    ),
    R.createElement("path", {
      d: "M2.5 1H12.5C13.3284 1 14 1.67157 14 2.5V12.5C14 13.3284 13.3284 14 12.5 14H2.5C1.67157 14 1 13.3284 1 12.5V2.5C1 1.67157 1.67157 1 2.5 1ZM2.5 2C2.22386 2 2 2.22386 2 2.5V8.3636L3.6818 6.6818C3.76809 6.59551 3.88572 6.54797 4.00774 6.55007C4.12975 6.55216 4.24568 6.60372 4.32895 6.69293L7.87355 10.4901L10.6818 7.6818C10.8575 7.50607 11.1425 7.50607 11.3182 7.6818L13 9.3636V2.5C13 2.22386 12.7761 2 12.5 2H2.5ZM2 12.5V9.6364L3.98887 7.64753L7.5311 11.4421L8.94113 13H2.5C2.22386 13 2 12.7761 2 12.5ZM12.5 13H10.155L8.48336 11.153L11 8.6364L13 10.6364V12.5C13 12.7761 12.7761 13 12.5 13ZM6.64922 5.5C6.64922 5.03013 7.03013 4.64922 7.5 4.64922C7.96987 4.64922 8.35078 5.03013 8.35078 5.5C8.35078 5.96987 7.96987 6.35078 7.5 6.35078C7.03013 6.35078 6.64922 5.96987 6.64922 5.5ZM7.5 3.74922C6.53307 3.74922 5.74922 4.53307 5.74922 5.5C5.74922 6.46693 6.53307 7.25078 7.5 7.25078C8.46693 7.25078 9.25078 6.46693 9.25078 5.5C9.25078 4.53307 8.46693 3.74922 7.5 3.74922Z",
      fill: "currentColor",
      fillRule: "evenodd",
      clipRule: "evenodd",
    })
  );
}
var EH = Object.defineProperty,
  $H = Object.defineProperties,
  CH = Object.getOwnPropertyDescriptors,
  f2 = Object.getOwnPropertySymbols,
  kH = Object.prototype.hasOwnProperty,
  RH = Object.prototype.propertyIsEnumerable,
  d2 = (e, t, n) =>
    t in e
      ? EH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  p2 = (e, t) => {
    for (var n in t || (t = {})) kH.call(t, n) && d2(e, n, t[n]);
    if (f2) for (var n of f2(t)) RH.call(t, n) && d2(e, n, t[n]);
    return e;
  },
  m2 = (e, t) => $H(e, CH(t)),
  NH = Pe((e, { radius: t }) => ({
    root: {},
    imageWrapper: { position: "relative" },
    figure: { margin: 0 },
    image: m2(p2({}, e.fn.fontStyles()), {
      display: "block",
      width: "100%",
      height: "100%",
      border: 0,
      borderRadius: e.fn.size({ size: t, sizes: e.radius }),
    }),
    caption: {
      color: e.colorScheme === "dark" ? e.colors.dark[2] : e.colors.gray[7],
      marginTop: e.spacing.xs,
    },
    placeholder: m2(p2({}, e.fn.cover()), {
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      color: e.colorScheme === "dark" ? e.colors.dark[2] : e.colors.gray[6],
      backgroundColor:
        e.colorScheme === "dark" ? e.colors.dark[8] : e.colors.gray[0],
      borderRadius: e.fn.size({ size: t, sizes: e.radius }),
    }),
  }));
const IH = NH;
var TH = Object.defineProperty,
  vd = Object.getOwnPropertySymbols,
  q3 = Object.prototype.hasOwnProperty,
  Z3 = Object.prototype.propertyIsEnumerable,
  h2 = (e, t, n) =>
    t in e
      ? TH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Ph = (e, t) => {
    for (var n in t || (t = {})) q3.call(t, n) && h2(e, n, t[n]);
    if (vd) for (var n of vd(t)) Z3.call(t, n) && h2(e, n, t[n]);
    return e;
  },
  zH = (e, t) => {
    var n = {};
    for (var r in e) q3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && vd)
      for (var r of vd(e)) t.indexOf(r) < 0 && Z3.call(e, r) && (n[r] = e[r]);
    return n;
  };
const AH = { fit: "cover", width: "100%", height: "auto", radius: 0 },
  LH = y.forwardRef((e, t) => {
    const n = me("Image", AH, e),
      {
        className: r,
        alt: o,
        src: a,
        fit: s,
        width: u,
        height: f,
        radius: d,
        imageProps: m,
        withPlaceholder: h,
        placeholder: v,
        imageRef: b,
        classNames: O,
        styles: E,
        caption: $,
        unstyled: _,
        style: w,
      } = n,
      P = zH(n, [
        "className",
        "alt",
        "src",
        "fit",
        "width",
        "height",
        "radius",
        "imageProps",
        "withPlaceholder",
        "placeholder",
        "imageRef",
        "classNames",
        "styles",
        "caption",
        "unstyled",
        "style",
      ]),
      { classes: k, cx: I } = IH(
        { radius: d },
        { classNames: O, styles: E, unstyled: _, name: "Image" }
      ),
      [z, A] = y.useState(!a),
      M = h && z;
    return (
      Zr(() => {
        A(!a);
      }, [a]),
      R.createElement(
        Ie,
        Ph({ className: I(k.root, r), ref: t, style: Ph({ width: u }, w) }, P),
        R.createElement(
          "figure",
          { className: k.figure },
          R.createElement(
            "div",
            { className: k.imageWrapper },
            R.createElement(
              "img",
              Ph(
                {
                  className: k.image,
                  src: a,
                  alt: o,
                  style: { objectFit: s, width: u, height: f },
                  ref: b,
                  onError: (B) => {
                    A(!0),
                      typeof (m == null ? void 0 : m.onError) == "function" &&
                        m.onError(B);
                  },
                },
                m
              )
            ),
            M &&
              R.createElement(
                "div",
                { className: k.placeholder, title: o },
                v ||
                  R.createElement(
                    "div",
                    null,
                    R.createElement(OH, { style: { width: 40, height: 40 } })
                  )
              )
          ),
          !!$ &&
            R.createElement(
              Jr,
              {
                component: "figcaption",
                size: "sm",
                align: "center",
                className: k.caption,
              },
              $
            )
        )
      )
    );
  });
LH.displayName = "@mantine/core/Image";
var DH = y.useLayoutEffect,
  MH = function (t) {
    var n = y.useRef(t);
    return (
      DH(function () {
        n.current = t;
      }),
      n
    );
  },
  v2 = function (t, n) {
    if (typeof t == "function") {
      t(n);
      return;
    }
    t.current = n;
  },
  jH = function (t, n) {
    var r = y.useRef();
    return y.useCallback(
      function (o) {
        (t.current = o),
          r.current && v2(r.current, null),
          (r.current = n),
          n && v2(n, o);
      },
      [n]
    );
  },
  g2 = {
    "min-height": "0",
    "max-height": "none",
    height: "0",
    visibility: "hidden",
    overflow: "hidden",
    position: "absolute",
    "z-index": "-1000",
    top: "0",
    right: "0",
  },
  y2 = function (t) {
    Object.keys(g2).forEach(function (n) {
      t.style.setProperty(n, g2[n], "important");
    });
  },
  hn = null,
  FH = function (t, n) {
    var r = t.scrollHeight;
    return n.sizingStyle.boxSizing === "border-box"
      ? r + n.borderSize
      : r - n.paddingSize;
  };
function WH(e, t, n, r) {
  n === void 0 && (n = 1),
    r === void 0 && (r = 1 / 0),
    hn ||
      ((hn = document.createElement("textarea")),
      hn.setAttribute("tabindex", "-1"),
      hn.setAttribute("aria-hidden", "true"),
      y2(hn)),
    hn.parentNode === null && document.body.appendChild(hn);
  var o = e.paddingSize,
    a = e.borderSize,
    s = e.sizingStyle,
    u = s.boxSizing;
  Object.keys(s).forEach(function (v) {
    var b = v;
    hn.style[b] = s[b];
  }),
    y2(hn),
    (hn.value = t);
  var f = FH(hn, e);
  hn.value = "x";
  var d = hn.scrollHeight - o,
    m = d * n;
  u === "border-box" && (m = m + o + a), (f = Math.max(m, f));
  var h = d * r;
  return u === "border-box" && (h = h + o + a), (f = Math.min(h, f)), [f, d];
}
var _2 = function () {},
  BH = function (t, n) {
    return t.reduce(function (r, o) {
      return (r[o] = n[o]), r;
    }, {});
  },
  UH = [
    "borderBottomWidth",
    "borderLeftWidth",
    "borderRightWidth",
    "borderTopWidth",
    "boxSizing",
    "fontFamily",
    "fontSize",
    "fontStyle",
    "fontWeight",
    "letterSpacing",
    "lineHeight",
    "paddingBottom",
    "paddingLeft",
    "paddingRight",
    "paddingTop",
    "tabSize",
    "textIndent",
    "textRendering",
    "textTransform",
    "width",
    "wordBreak",
  ],
  HH = !!document.documentElement.currentStyle,
  VH = function (t) {
    var n = window.getComputedStyle(t);
    if (n === null) return null;
    var r = BH(UH, n),
      o = r.boxSizing;
    if (o === "") return null;
    HH &&
      o === "border-box" &&
      (r.width =
        parseFloat(r.width) +
        parseFloat(r.borderRightWidth) +
        parseFloat(r.borderLeftWidth) +
        parseFloat(r.paddingRight) +
        parseFloat(r.paddingLeft) +
        "px");
    var a = parseFloat(r.paddingBottom) + parseFloat(r.paddingTop),
      s = parseFloat(r.borderBottomWidth) + parseFloat(r.borderTopWidth);
    return { sizingStyle: r, paddingSize: a, borderSize: s };
  },
  YH = function (t) {
    var n = MH(t);
    y.useLayoutEffect(function () {
      var r = function (a) {
        n.current(a);
      };
      return (
        window.addEventListener("resize", r),
        function () {
          window.removeEventListener("resize", r);
        }
      );
    }, []);
  },
  GH = function (t, n) {
    var r = t.cacheMeasurements,
      o = t.maxRows,
      a = t.minRows,
      s = t.onChange,
      u = s === void 0 ? _2 : s,
      f = t.onHeightChange,
      d = f === void 0 ? _2 : f,
      m = l0(t, [
        "cacheMeasurements",
        "maxRows",
        "minRows",
        "onChange",
        "onHeightChange",
      ]),
      h = m.value !== void 0,
      v = y.useRef(null),
      b = jH(v, n),
      O = y.useRef(0),
      E = y.useRef(),
      $ = function () {
        var P = v.current,
          k = r && E.current ? E.current : VH(P);
        if (k) {
          E.current = k;
          var I = WH(k, P.value || P.placeholder || "x", a, o),
            z = I[0],
            A = I[1];
          O.current !== z &&
            ((O.current = z),
            P.style.setProperty("height", z + "px", "important"),
            d(z, { rowHeight: A }));
        }
      },
      _ = function (P) {
        h || $(), u(P);
      };
    return (
      y.useLayoutEffect($),
      YH($),
      y.createElement("textarea", je({}, m, { onChange: _, ref: b }))
    );
  },
  XH = y.forwardRef(GH);
const KH = XH;
var QH = Pe((e) => ({
  input: { paddingTop: e.spacing.xs, paddingBottom: e.spacing.xs },
}));
const qH = QH;
var ZH = Object.defineProperty,
  JH = Object.defineProperties,
  eV = Object.getOwnPropertyDescriptors,
  gd = Object.getOwnPropertySymbols,
  J3 = Object.prototype.hasOwnProperty,
  e5 = Object.prototype.propertyIsEnumerable,
  w2 = (e, t, n) =>
    t in e
      ? ZH(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ca = (e, t) => {
    for (var n in t || (t = {})) J3.call(t, n) && w2(e, n, t[n]);
    if (gd) for (var n of gd(t)) e5.call(t, n) && w2(e, n, t[n]);
    return e;
  },
  Oh = (e, t) => JH(e, eV(t)),
  tV = (e, t) => {
    var n = {};
    for (var r in e) J3.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && gd)
      for (var r of gd(e)) t.indexOf(r) < 0 && e5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const nV = { autosize: !1, size: "sm", __staticSelector: "Textarea" },
  rV = y.forwardRef((e, t) => {
    const n = me("Textarea", nV, e),
      {
        autosize: r,
        maxRows: o,
        minRows: a,
        label: s,
        error: u,
        description: f,
        id: d,
        className: m,
        required: h,
        style: v,
        wrapperProps: b,
        classNames: O,
        styles: E,
        size: $,
        __staticSelector: _,
        sx: w,
        errorProps: P,
        descriptionProps: k,
        labelProps: I,
        inputWrapperOrder: z,
        inputContainer: A,
        unstyled: M,
        withAsterisk: B,
      } = n,
      H = tV(n, [
        "autosize",
        "maxRows",
        "minRows",
        "label",
        "error",
        "description",
        "id",
        "className",
        "required",
        "style",
        "wrapperProps",
        "classNames",
        "styles",
        "size",
        "__staticSelector",
        "sx",
        "errorProps",
        "descriptionProps",
        "labelProps",
        "inputWrapperOrder",
        "inputContainer",
        "unstyled",
        "withAsterisk",
      ]),
      q = dp(d),
      { classes: Z, cx: he } = qH(),
      { systemStyles: xe, rest: Ce } = Xs(H),
      ce = ca(
        {
          required: h,
          ref: t,
          invalid: !!u,
          id: q,
          classNames: Oh(ca({}, O), {
            input: he(Z.input, O == null ? void 0 : O.input),
          }),
          styles: E,
          __staticSelector: _,
          size: $,
          multiline: !0,
          unstyled: M,
        },
        Ce
      );
    return R.createElement(
      Er.Wrapper,
      ca(
        ca(
          {
            label: s,
            error: u,
            id: q,
            description: f,
            required: h,
            style: v,
            className: m,
            classNames: O,
            styles: E,
            size: $,
            __staticSelector: _,
            sx: w,
            errorProps: P,
            labelProps: I,
            descriptionProps: k,
            inputContainer: A,
            inputWrapperOrder: z,
            unstyled: M,
            withAsterisk: B,
          },
          xe
        ),
        b
      ),
      r
        ? R.createElement(
            Er,
            Oh(ca({}, ce), { component: KH, maxRows: o, minRows: a })
          )
        : R.createElement(
            Er,
            Oh(ca({}, ce), { component: "textarea", rows: a })
          )
    );
  });
rV.displayName = "@mantine/core/Textarea";
var oV = Object.defineProperty,
  yd = Object.getOwnPropertySymbols,
  t5 = Object.prototype.hasOwnProperty,
  n5 = Object.prototype.propertyIsEnumerable,
  b2 = (e, t, n) =>
    t in e
      ? oV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  x2 = (e, t) => {
    for (var n in t || (t = {})) t5.call(t, n) && b2(e, n, t[n]);
    if (yd) for (var n of yd(t)) n5.call(t, n) && b2(e, n, t[n]);
    return e;
  },
  iV = (e, t) => {
    var n = {};
    for (var r in e) t5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && yd)
      for (var r of yd(e)) t.indexOf(r) < 0 && n5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const aV = { xs: 14, sm: 18, md: 20, lg: 24, xl: 28 };
function lV(e) {
  var t = e,
    { size: n, error: r, style: o } = t,
    a = iV(t, ["size", "error", "style"]);
  const s = On(),
    u = s.fn.size({ size: n, sizes: aV });
  return R.createElement(
    "svg",
    x2(
      {
        width: u,
        height: u,
        viewBox: "0 0 15 15",
        fill: "none",
        xmlns: "http://www.w3.org/2000/svg",
        style: x2({ color: r ? s.colors.red[6] : s.colors.gray[6] }, o),
        "data-chevron": !0,
      },
      a
    ),
    R.createElement("path", {
      d: "M4.93179 5.43179C4.75605 5.60753 4.75605 5.89245 4.93179 6.06819C5.10753 6.24392 5.39245 6.24392 5.56819 6.06819L7.49999 4.13638L9.43179 6.06819C9.60753 6.24392 9.89245 6.24392 10.0682 6.06819C10.2439 5.89245 10.2439 5.60753 10.0682 5.43179L7.81819 3.18179C7.73379 3.0974 7.61933 3.04999 7.49999 3.04999C7.38064 3.04999 7.26618 3.0974 7.18179 3.18179L4.93179 5.43179ZM10.0682 9.56819C10.2439 9.39245 10.2439 9.10753 10.0682 8.93179C9.89245 8.75606 9.60753 8.75606 9.43179 8.93179L7.49999 10.8636L5.56819 8.93179C5.39245 8.75606 5.10753 8.75606 4.93179 8.93179C4.75605 9.10753 4.75605 9.39245 4.93179 9.56819L7.18179 11.8182C7.35753 11.9939 7.64245 11.9939 7.81819 11.8182L10.0682 9.56819Z",
      fill: "currentColor",
      fillRule: "evenodd",
      clipRule: "evenodd",
    })
  );
}
function r5({
  shouldClear: e,
  clearButtonLabel: t,
  onClear: n,
  size: r,
  error: o,
  clearButtonTabIndex: a,
}) {
  return e
    ? R.createElement(qE, {
        variant: "transparent",
        "aria-label": t,
        onClick: n,
        size: r,
        tabIndex: a,
        onMouseDown: (s) => s.preventDefault(),
      })
    : R.createElement(lV, { error: o, size: r });
}
r5.displayName = "@mantine/core/SelectRightSection";
var sV = Object.defineProperty,
  uV = Object.defineProperties,
  cV = Object.getOwnPropertyDescriptors,
  _d = Object.getOwnPropertySymbols,
  o5 = Object.prototype.hasOwnProperty,
  i5 = Object.prototype.propertyIsEnumerable,
  S2 = (e, t, n) =>
    t in e
      ? sV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Eh = (e, t) => {
    for (var n in t || (t = {})) o5.call(t, n) && S2(e, n, t[n]);
    if (_d) for (var n of _d(t)) i5.call(t, n) && S2(e, n, t[n]);
    return e;
  },
  P2 = (e, t) => uV(e, cV(t)),
  fV = (e, t) => {
    var n = {};
    for (var r in e) o5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && _d)
      for (var r of _d(e)) t.indexOf(r) < 0 && i5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const dV = { xs: 24, sm: 30, md: 34, lg: 44, xl: 54 };
function pV(e) {
  var t = e,
    { styles: n, rightSection: r, rightSectionWidth: o, theme: a } = t,
    s = fV(t, ["styles", "rightSection", "rightSectionWidth", "theme"]);
  if (r) return { rightSection: r, rightSectionWidth: o, styles: n };
  const u = typeof n == "function" ? n(a) : n;
  return {
    rightSectionWidth: a.fn.size({ size: s.size, sizes: dV }),
    rightSection:
      !s.readOnly &&
      !(s.disabled && s.shouldClear) &&
      R.createElement(r5, Eh({}, s)),
    styles: P2(Eh({}, u), {
      rightSection: P2(Eh({}, u == null ? void 0 : u.rightSection), {
        pointerEvents: s.shouldClear ? void 0 : "none",
      }),
    }),
  };
}
var mV = Pe((e, { color: t, radius: n, withTitle: r }, o) => {
  const a = e.fn.radius(n),
    s = Math.min(Math.max(a / 1.2, 4), 30),
    u = e.fn.variant({ variant: "filled", color: t });
  return {
    closeButton: e.fn.hover({
      backgroundColor:
        e.colorScheme === "dark" ? e.colors.dark[8] : e.colors.gray[0],
    }),
    icon: {
      ref: o("icon"),
      boxSizing: "border-box",
      marginRight: e.spacing.md,
      width: 28,
      height: 28,
      borderRadius: 28,
      display: "flex",
      flex: "none",
      alignItems: "center",
      justifyContent: "center",
      color: e.white,
    },
    withIcon: { paddingLeft: e.spacing.xs, "&::before": { display: "none" } },
    root: {
      boxSizing: "border-box",
      position: "relative",
      display: "flex",
      alignItems: "center",
      overflow: "hidden",
      paddingLeft: 22,
      paddingRight: 5,
      paddingTop: e.spacing.xs,
      paddingBottom: e.spacing.xs,
      borderRadius: a,
      backgroundColor: e.colorScheme === "dark" ? e.colors.dark[6] : e.white,
      boxShadow: e.shadows.lg,
      border: `1px solid ${
        e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[2]
      }`,
      "&::before": {
        content: '""',
        display: "block",
        position: "absolute",
        width: 6,
        top: s,
        bottom: s,
        left: 4,
        borderRadius: a,
        backgroundColor: u.background,
      },
      [`& .${o("icon")}`]: { backgroundColor: u.background, color: e.white },
    },
    body: { flex: 1, overflow: "hidden", marginRight: 10 },
    loader: { marginRight: e.spacing.md },
    title: {
      lineHeight: 1.4,
      marginBottom: 2,
      overflow: "hidden",
      textOverflow: "ellipsis",
      color: e.colorScheme === "dark" ? e.white : e.colors.gray[9],
    },
    description: {
      color: r
        ? e.colorScheme === "dark"
          ? e.colors.dark[2]
          : e.colors.gray[6]
        : e.colorScheme === "dark"
        ? e.colors.dark[0]
        : e.black,
      lineHeight: 1.4,
      overflow: "hidden",
      textOverflow: "ellipsis",
    },
  };
});
const hV = mV;
var vV = Object.defineProperty,
  gV = Object.defineProperties,
  yV = Object.getOwnPropertyDescriptors,
  wd = Object.getOwnPropertySymbols,
  a5 = Object.prototype.hasOwnProperty,
  l5 = Object.prototype.propertyIsEnumerable,
  O2 = (e, t, n) =>
    t in e
      ? vV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  E2 = (e, t) => {
    for (var n in t || (t = {})) a5.call(t, n) && O2(e, n, t[n]);
    if (wd) for (var n of wd(t)) l5.call(t, n) && O2(e, n, t[n]);
    return e;
  },
  _V = (e, t) => gV(e, yV(t)),
  wV = (e, t) => {
    var n = {};
    for (var r in e) a5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && wd)
      for (var r of wd(e)) t.indexOf(r) < 0 && l5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const s5 = y.forwardRef((e, t) => {
  const n = me("Notification", {}, e),
    {
      className: r,
      color: o,
      radius: a,
      loading: s,
      disallowClose: u,
      title: f,
      icon: d,
      children: m,
      onClose: h,
      closeButtonProps: v,
      classNames: b,
      styles: O,
      unstyled: E,
    } = n,
    $ = wV(n, [
      "className",
      "color",
      "radius",
      "loading",
      "disallowClose",
      "title",
      "icon",
      "children",
      "onClose",
      "closeButtonProps",
      "classNames",
      "styles",
      "unstyled",
    ]),
    { classes: _, cx: w } = hV(
      { color: o, radius: a, withTitle: !!f },
      { classNames: b, styles: O, unstyled: E, name: "Notification" }
    ),
    P = d || s;
  return R.createElement(
    Ie,
    E2(
      { className: w(_.root, { [_.withIcon]: P }, r), role: "alert", ref: t },
      $
    ),
    d && !s && R.createElement("div", { className: _.icon }, d),
    s && R.createElement(pp, { size: 28, color: o, className: _.loader }),
    R.createElement(
      "div",
      { className: _.body },
      f &&
        R.createElement(Jr, { className: _.title, size: "sm", weight: 500 }, f),
      R.createElement(
        Jr,
        { color: "dimmed", className: _.description, size: "sm" },
        m
      )
    ),
    !u &&
      R.createElement(
        qE,
        _V(E2({ iconSize: 16, color: "gray" }, v), {
          onClick: h,
          className: _.closeButton,
        })
      )
  );
});
s5.displayName = "@mantine/core/Notification";
var bV = Object.defineProperty,
  xV = Object.defineProperties,
  SV = Object.getOwnPropertyDescriptors,
  bd = Object.getOwnPropertySymbols,
  u5 = Object.prototype.hasOwnProperty,
  c5 = Object.prototype.propertyIsEnumerable,
  $2 = (e, t, n) =>
    t in e
      ? bV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  $h = (e, t) => {
    for (var n in t || (t = {})) u5.call(t, n) && $2(e, n, t[n]);
    if (bd) for (var n of bd(t)) c5.call(t, n) && $2(e, n, t[n]);
    return e;
  },
  PV = (e, t) => xV(e, SV(t)),
  OV = (e, t) => {
    var n = {};
    for (var r in e) u5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && bd)
      for (var r of bd(e)) t.indexOf(r) < 0 && c5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const EV = { type: "text", size: "sm", __staticSelector: "TextInput" },
  $V = y.forwardRef((e, t) => {
    const n = s3("TextInput", EV, e),
      { inputProps: r, wrapperProps: o } = n,
      a = OV(n, ["inputProps", "wrapperProps"]);
    return R.createElement(
      Er.Wrapper,
      $h({}, o),
      R.createElement(Er, PV($h($h({}, r), a), { ref: t }))
    );
  });
$V.displayName = "@mantine/core/TextInput";
function CV({
  data: e,
  searchable: t,
  limit: n,
  searchValue: r,
  filter: o,
  value: a,
  filterDataOnExactSearchMatch: s,
}) {
  if (!t) return e;
  const u = (a != null && e.find((d) => d.value === a)) || null;
  if (u && !s && (u == null ? void 0 : u.label) === r) {
    if (n) {
      if (n >= e.length) return e;
      const d = e.indexOf(u),
        m = d + n,
        h = m - e.length;
      return h > 0 ? e.slice(d - h) : e.slice(d, m);
    }
    return e;
  }
  const f = [];
  for (
    let d = 0;
    d < e.length && (o(r, e[d]) && f.push(e[d]), !(f.length >= n));
    d += 1
  );
  return f;
}
var kV = Pe(() => ({
  input: {
    "&:not(:disabled)": {
      cursor: "pointer",
      "&::selection": { backgroundColor: "transparent" },
    },
  },
}));
const RV = kV;
var NV = Object.defineProperty,
  IV = Object.defineProperties,
  TV = Object.getOwnPropertyDescriptors,
  xd = Object.getOwnPropertySymbols,
  f5 = Object.prototype.hasOwnProperty,
  d5 = Object.prototype.propertyIsEnumerable,
  C2 = (e, t, n) =>
    t in e
      ? NV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Wl = (e, t) => {
    for (var n in t || (t = {})) f5.call(t, n) && C2(e, n, t[n]);
    if (xd) for (var n of xd(t)) d5.call(t, n) && C2(e, n, t[n]);
    return e;
  },
  Ch = (e, t) => IV(e, TV(t)),
  zV = (e, t) => {
    var n = {};
    for (var r in e) f5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && xd)
      for (var r of xd(e)) t.indexOf(r) < 0 && d5.call(e, r) && (n[r] = e[r]);
    return n;
  };
function AV(e, t) {
  return t.label.toLowerCase().trim().includes(e.toLowerCase().trim());
}
function LV(e, t) {
  return !!e && !t.some((n) => n.label.toLowerCase() === e.toLowerCase());
}
const DV = {
    required: !1,
    size: "sm",
    shadow: "sm",
    itemComponent: v$,
    transition: "fade",
    transitionDuration: 0,
    initiallyOpened: !1,
    filter: AV,
    maxDropdownHeight: 220,
    searchable: !1,
    clearable: !1,
    limit: 1 / 0,
    disabled: !1,
    creatable: !1,
    shouldCreate: LV,
    selectOnBlur: !1,
    switchDirectionOnFlip: !1,
    filterDataOnExactSearchMatch: !1,
    zIndex: fp("popover"),
    clearButtonTabIndex: 0,
    positionDependencies: [],
    dropdownPosition: "flip",
  },
  MV = y.forwardRef((e, t) => {
    const n = s3("Select", DV, e),
      {
        inputProps: r,
        wrapperProps: o,
        shadow: a,
        data: s,
        value: u,
        defaultValue: f,
        onChange: d,
        itemComponent: m,
        onKeyDown: h,
        onBlur: v,
        onFocus: b,
        transition: O,
        transitionDuration: E,
        initiallyOpened: $,
        transitionTimingFunction: _,
        unstyled: w,
        classNames: P,
        styles: k,
        filter: I,
        maxDropdownHeight: z,
        searchable: A,
        clearable: M,
        nothingFound: B,
        clearButtonLabel: H,
        limit: q,
        disabled: Z,
        onSearchChange: he,
        searchValue: xe,
        rightSection: Ce,
        rightSectionWidth: ce,
        creatable: Se,
        getCreateLabel: Y,
        shouldCreate: re,
        selectOnBlur: ne,
        onCreate: le,
        dropdownComponent: ze,
        onDropdownClose: Gt,
        onDropdownOpen: Dt,
        withinPortal: $t,
        switchDirectionOnFlip: vt,
        zIndex: $n,
        name: Vn,
        dropdownPosition: Yn,
        allowDeselect: Zo,
        placeholder: so,
        filterDataOnExactSearchMatch: uo,
        clearButtonTabIndex: Fi,
        form: Ct,
        positionDependencies: $r,
        readOnly: Ve,
        hoverOnSearchChange: Xt,
      } = n,
      Wi = zV(n, [
        "inputProps",
        "wrapperProps",
        "shadow",
        "data",
        "value",
        "defaultValue",
        "onChange",
        "itemComponent",
        "onKeyDown",
        "onBlur",
        "onFocus",
        "transition",
        "transitionDuration",
        "initiallyOpened",
        "transitionTimingFunction",
        "unstyled",
        "classNames",
        "styles",
        "filter",
        "maxDropdownHeight",
        "searchable",
        "clearable",
        "nothingFound",
        "clearButtonLabel",
        "limit",
        "disabled",
        "onSearchChange",
        "searchValue",
        "rightSection",
        "rightSectionWidth",
        "creatable",
        "getCreateLabel",
        "shouldCreate",
        "selectOnBlur",
        "onCreate",
        "dropdownComponent",
        "onDropdownClose",
        "onDropdownOpen",
        "withinPortal",
        "switchDirectionOnFlip",
        "zIndex",
        "name",
        "dropdownPosition",
        "allowDeselect",
        "placeholder",
        "filterDataOnExactSearchMatch",
        "clearButtonTabIndex",
        "form",
        "positionDependencies",
        "readOnly",
        "hoverOnSearchChange",
      ]),
      { classes: Cr, cx: Jo, theme: an } = RV(),
      [ut, Bi] = y.useState($),
      [Gn, tt] = y.useState(-1),
      ll = y.useRef(),
      dr = y.useRef({}),
      [kr, sl] = y.useState("column"),
      Xn = kr === "column",
      {
        scrollIntoView: Rr,
        targetRef: ei,
        scrollableRef: ul,
      } = XL({ duration: 0, offset: 5, cancelable: !1, isList: !0 }),
      cl = Zo === void 0 ? M : Zo,
      kt = (te) => {
        if (ut !== te) {
          Bi(te);
          const Ae = te ? Dt : Gt;
          typeof Ae == "function" && Ae();
        }
      },
      ti = Se && typeof Y == "function";
    let ni = null;
    const Pp = s.map((te) =>
        typeof te == "string" ? { label: te, value: te } : te
      ),
      Ui = t9({ data: Pp }),
      [ln, Nr, fl] = Is({
        value: u,
        defaultValue: f,
        finalValue: null,
        onChange: d,
      }),
      Kn = Ui.find((te) => te.value === ln),
      [Qn, Op] = Is({
        value: xe,
        defaultValue: (Kn == null ? void 0 : Kn.label) || "",
        finalValue: void 0,
        onChange: he,
      }),
      Ir = (te) => {
        Op(te), A && typeof he == "function" && he(te);
      },
      tu = () => {
        var te;
        Ve || (Nr(null), fl || Ir(""), (te = ll.current) == null || te.focus());
      };
    y.useEffect(() => {
      const te = Ui.find((Ae) => Ae.value === ln);
      te ? Ir(te.label) : (!ti || !ln) && Ir("");
    }, [ln]),
      y.useEffect(() => {
        Kn && (!A || !ut) && Ir(Kn.label);
      }, [Kn == null ? void 0 : Kn.label]);
    const Hi = (te) => {
        if (!Ve)
          if (cl && (Kn == null ? void 0 : Kn.value) === te.value)
            Nr(null), kt(!1);
          else {
            if (te.creatable && typeof le == "function") {
              const Ae = le(te.value);
              typeof Ae < "u" &&
                Ae !== null &&
                Nr(typeof Ae == "string" ? Ae : Ae.value);
            } else Nr(te.value);
            fl || Ir(te.label), tt(-1), kt(!1), ll.current.focus();
          }
      },
      at = CV({
        data: Ui,
        searchable: A,
        limit: q,
        searchValue: Qn,
        filter: I,
        filterDataOnExactSearchMatch: uo,
        value: ln,
      });
    ti &&
      re(Qn, at) &&
      ((ni = Y(Qn)), at.push({ label: Qn, value: Qn, creatable: !0 }));
    const nu = (te, Ae, Mt) => {
      let jt = te;
      for (; Mt(jt); ) if (((jt = Ae(jt)), !at[jt].disabled)) return jt;
      return te;
    };
    Zr(() => {
      tt(Xt && Qn ? 0 : -1);
    }, [Qn, Xt]);
    const Tr = ln ? at.findIndex((te) => te.value === ln) : 0,
      sn = !Ve && (at.length > 0 ? ut : ut && !!B),
      Vi = () => {
        tt((te) => {
          var Ae;
          const Mt = nu(
            te,
            (jt) => jt - 1,
            (jt) => jt > 0
          );
          return (
            (ei.current =
              dr.current[(Ae = at[Mt]) == null ? void 0 : Ae.value]),
            sn && Rr({ alignment: Xn ? "start" : "end" }),
            Mt
          );
        });
      },
      ru = () => {
        tt((te) => {
          var Ae;
          const Mt = nu(
            te,
            (jt) => jt + 1,
            (jt) => jt < at.length - 1
          );
          return (
            (ei.current =
              dr.current[(Ae = at[Mt]) == null ? void 0 : Ae.value]),
            sn && Rr({ alignment: Xn ? "end" : "start" }),
            Mt
          );
        });
      },
      Yi = () =>
        window.setTimeout(() => {
          var te;
          (ei.current = dr.current[(te = at[Tr]) == null ? void 0 : te.value]),
            Rr({ alignment: Xn ? "end" : "start" });
        }, 0);
    Zr(() => {
      sn && Yi();
    }, [sn]);
    const Ep = (te) => {
        switch ((typeof h == "function" && h(te), te.key)) {
          case "ArrowUp": {
            te.preventDefault(),
              ut ? (Xn ? Vi() : ru()) : (tt(Tr), kt(!0), Yi());
            break;
          }
          case "ArrowDown": {
            te.preventDefault(),
              ut ? (Xn ? ru() : Vi()) : (tt(Tr), kt(!0), Yi());
            break;
          }
          case "Home": {
            if (!A) {
              te.preventDefault(), ut || kt(!0);
              const Ae = at.findIndex((Mt) => !Mt.disabled);
              tt(Ae), sn && Rr({ alignment: Xn ? "end" : "start" });
            }
            break;
          }
          case "End": {
            if (!A) {
              te.preventDefault(), ut || kt(!0);
              const Ae = at.map((Mt) => !!Mt.disabled).lastIndexOf(!1);
              tt(Ae), sn && Rr({ alignment: Xn ? "end" : "start" });
            }
            break;
          }
          case "Escape": {
            te.preventDefault(), kt(!1), tt(-1);
            break;
          }
          case " ": {
            A ||
              (te.preventDefault(),
              at[Gn] && ut ? Hi(at[Gn]) : (kt(!0), tt(Tr), Yi()));
            break;
          }
          case "Enter":
            A || te.preventDefault(),
              at[Gn] && ut && (te.preventDefault(), Hi(at[Gn]));
        }
      },
      $p = (te) => {
        typeof v == "function" && v(te);
        const Ae = Ui.find((Mt) => Mt.value === ln);
        ne && at[Gn] && ut && Hi(at[Gn]),
          Ir((Ae == null ? void 0 : Ae.label) || ""),
          kt(!1);
      },
      Cp = (te) => {
        typeof b == "function" && b(te), A && kt(!0);
      },
      kp = (te) => {
        Ve ||
          (Ir(te.currentTarget.value),
          M && te.currentTarget.value === "" && Nr(null),
          tt(-1),
          kt(!0));
      },
      Rp = () => {
        Ve || (kt(!ut), ln && !ut && tt(Tr));
      };
    return R.createElement(
      Er.Wrapper,
      Ch(Wl({}, o), { __staticSelector: "Select" }),
      R.createElement(
        ls,
        {
          opened: sn,
          transition: O,
          transitionDuration: E,
          shadow: "sm",
          withinPortal: $t,
          __staticSelector: "Select",
          onDirectionChange: sl,
          switchDirectionOnFlip: vt,
          zIndex: $n,
          dropdownPosition: Yn,
          positionDependencies: [...$r, Qn],
          classNames: P,
          styles: k,
          unstyled: w,
        },
        R.createElement(
          ls.Target,
          null,
          R.createElement(
            "div",
            {
              role: "combobox",
              "aria-haspopup": "listbox",
              "aria-owns": sn ? `${r.id}-items` : null,
              "aria-controls": r.id,
              "aria-expanded": sn,
              onMouseLeave: () => tt(-1),
              tabIndex: -1,
            },
            R.createElement("input", {
              type: "hidden",
              name: Vn,
              value: ln || "",
              form: Ct,
              disabled: Z,
            }),
            R.createElement(
              Er,
              Wl(
                Ch(Wl(Wl({ autoComplete: "off", type: "search" }, r), Wi), {
                  ref: x0(t, ll),
                  onKeyDown: Ep,
                  __staticSelector: "Select",
                  value: Qn,
                  placeholder: so,
                  onChange: kp,
                  "aria-autocomplete": "list",
                  "aria-controls": sn ? `${r.id}-items` : null,
                  "aria-activedescendant": Gn >= 0 ? `${r.id}-${Gn}` : null,
                  onMouseDown: Rp,
                  onBlur: $p,
                  onFocus: Cp,
                  readOnly: !A || Ve,
                  disabled: Z,
                  "data-mantine-stop-propagation": sn,
                  name: null,
                  classNames: Ch(Wl({}, P), {
                    input: Jo({ [Cr.input]: !A }, P == null ? void 0 : P.input),
                  }),
                }),
                pV({
                  theme: an,
                  rightSection: Ce,
                  rightSectionWidth: ce,
                  styles: k,
                  size: r.size,
                  shouldClear: M && !!Kn,
                  clearButtonLabel: H,
                  onClear: tu,
                  error: o.error,
                  clearButtonTabIndex: Fi,
                  disabled: Z,
                  readOnly: Ve,
                })
              )
            )
          )
        ),
        R.createElement(
          ls.Dropdown,
          {
            component: ze || E0,
            maxHeight: z,
            direction: kr,
            id: r.id,
            innerRef: ul,
            __staticSelector: "Select",
            classNames: P,
            styles: k,
          },
          R.createElement(p$, {
            data: at,
            hovered: Gn,
            classNames: P,
            styles: k,
            isItemSelected: (te) => te === ln,
            uuid: r.id,
            __staticSelector: "Select",
            onItemHover: tt,
            onItemSelect: Hi,
            itemsRefs: dr,
            itemComponent: m,
            size: r.size,
            nothingFound: B,
            creatable: ti && !!ni,
            createLabel: ni,
            "aria-label": o.label,
            unstyled: w,
          })
        )
      )
    );
  });
MV.displayName = "@mantine/core/Select";
function jV(e, t) {
  if (t.length === 0) return t;
  const n = "maxWidth" in t[0] ? "maxWidth" : "minWidth",
    r = [...t].sort(
      (o, a) =>
        e.fn.size({ size: a[n], sizes: e.breakpoints }) -
        e.fn.size({ size: o[n], sizes: e.breakpoints })
    );
  return n === "minWidth" ? r.reverse() : r;
}
var FV = Object.defineProperty,
  k2 = Object.getOwnPropertySymbols,
  WV = Object.prototype.hasOwnProperty,
  BV = Object.prototype.propertyIsEnumerable,
  R2 = (e, t, n) =>
    t in e
      ? FV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  UV = (e, t) => {
    for (var n in t || (t = {})) WV.call(t, n) && R2(e, n, t[n]);
    if (k2) for (var n of k2(t)) BV.call(t, n) && R2(e, n, t[n]);
    return e;
  },
  HV = Pe((e, { spacing: t, breakpoints: n, cols: r, verticalSpacing: o }) => {
    const a = o != null,
      s = jV(e, n).reduce((u, f) => {
        var d, m;
        const h = "maxWidth" in f ? "max-width" : "min-width",
          v = e.fn.size({
            size: h === "max-width" ? f.maxWidth : f.minWidth,
            sizes: e.breakpoints,
          });
        return (
          (u[`@media (${h}: ${v - (h === "max-width" ? 1 : 0)}px)`] = {
            gridTemplateColumns: `repeat(${f.cols}, minmax(0, 1fr))`,
            gap: `${e.fn.size({
              size: (d = f.verticalSpacing) != null ? d : a ? o : t,
              sizes: e.spacing,
            })}px ${e.fn.size({
              size: (m = f.spacing) != null ? m : t,
              sizes: e.spacing,
            })}px`,
          }),
          u
        );
      }, {});
    return {
      root: UV(
        {
          boxSizing: "border-box",
          display: "grid",
          gridTemplateColumns: `repeat(${r}, minmax(0, 1fr))`,
          gap: `${e.fn.size({
            size: a ? o : t,
            sizes: e.spacing,
          })}px ${e.fn.size({ size: t, sizes: e.spacing })}px`,
        },
        s
      ),
    };
  });
const VV = HV;
var YV = Object.defineProperty,
  Sd = Object.getOwnPropertySymbols,
  p5 = Object.prototype.hasOwnProperty,
  m5 = Object.prototype.propertyIsEnumerable,
  N2 = (e, t, n) =>
    t in e
      ? YV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  GV = (e, t) => {
    for (var n in t || (t = {})) p5.call(t, n) && N2(e, n, t[n]);
    if (Sd) for (var n of Sd(t)) m5.call(t, n) && N2(e, n, t[n]);
    return e;
  },
  XV = (e, t) => {
    var n = {};
    for (var r in e) p5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Sd)
      for (var r of Sd(e)) t.indexOf(r) < 0 && m5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const KV = { breakpoints: [], cols: 1, spacing: "md" },
  QV = y.forwardRef((e, t) => {
    const n = me("SimpleGrid", KV, e),
      {
        className: r,
        breakpoints: o,
        cols: a,
        spacing: s,
        verticalSpacing: u,
        children: f,
        unstyled: d,
      } = n,
      m = XV(n, [
        "className",
        "breakpoints",
        "cols",
        "spacing",
        "verticalSpacing",
        "children",
        "unstyled",
      ]),
      { classes: h, cx: v } = VV(
        { breakpoints: o, cols: a, spacing: s, verticalSpacing: u },
        { unstyled: d, name: "SimpleGrid" }
      );
    return R.createElement(Ie, GV({ className: v(h.root, r), ref: t }, m), f);
  });
QV.displayName = "@mantine/core/SimpleGrid";
var qV = Object.defineProperty,
  Pd = Object.getOwnPropertySymbols,
  h5 = Object.prototype.hasOwnProperty,
  v5 = Object.prototype.propertyIsEnumerable,
  I2 = (e, t, n) =>
    t in e
      ? qV(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  ZV = (e, t) => {
    for (var n in t || (t = {})) h5.call(t, n) && I2(e, n, t[n]);
    if (Pd) for (var n of Pd(t)) v5.call(t, n) && I2(e, n, t[n]);
    return e;
  },
  JV = (e, t) => {
    var n = {};
    for (var r in e) h5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Pd)
      for (var r of Pd(e)) t.indexOf(r) < 0 && v5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const eY = { w: 0, h: 0 },
  tY = y.forwardRef((e, t) => {
    const n = me("Space", eY, e),
      { w: r, h: o } = n,
      a = JV(n, ["w", "h"]);
    return R.createElement(Ie, ZV({ ref: t, w: r, miw: r, h: o, mih: o }, a));
  });
tY.displayName = "@mantine/core/Space";
const g5 = y.createContext(null),
  nY = g5.Provider,
  rY = () => y.useContext(g5);
var oY = Object.defineProperty,
  Od = Object.getOwnPropertySymbols,
  y5 = Object.prototype.hasOwnProperty,
  _5 = Object.prototype.propertyIsEnumerable,
  T2 = (e, t, n) =>
    t in e
      ? oY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  z2 = (e, t) => {
    for (var n in t || (t = {})) y5.call(t, n) && T2(e, n, t[n]);
    if (Od) for (var n of Od(t)) _5.call(t, n) && T2(e, n, t[n]);
    return e;
  },
  iY = (e, t) => {
    var n = {};
    for (var r in e) y5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Od)
      for (var r of Od(e)) t.indexOf(r) < 0 && _5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const aY = {
    orientation: "horizontal",
    spacing: "lg",
    size: "sm",
    offset: "xs",
  },
  w5 = y.forwardRef((e, t) => {
    const n = me("SwitchGroup", aY, e),
      {
        children: r,
        value: o,
        defaultValue: a,
        onChange: s,
        orientation: u,
        spacing: f,
        size: d,
        wrapperProps: m,
        offset: h,
      } = n,
      v = iY(n, [
        "children",
        "value",
        "defaultValue",
        "onChange",
        "orientation",
        "spacing",
        "size",
        "wrapperProps",
        "offset",
      ]),
      [b, O] = Is({ value: o, defaultValue: a, finalValue: [], onChange: s }),
      E = ($) => {
        const _ = $.currentTarget.value;
        O(b.includes(_) ? b.filter((w) => w !== _) : [...b, _]);
      };
    return R.createElement(
      nY,
      { value: { value: b, onChange: E, size: d } },
      R.createElement(
        Er.Wrapper,
        z2(
          z2(
            {
              labelElement: "div",
              size: d,
              __staticSelector: "SwitchGroup",
              ref: t,
            },
            m
          ),
          v
        ),
        R.createElement(KU, { spacing: f, orientation: u, offset: h }, r)
      )
    );
  });
w5.displayName = "@mantine/core/SwitchGroup";
var lY = Object.defineProperty,
  sY = Object.defineProperties,
  uY = Object.getOwnPropertyDescriptors,
  A2 = Object.getOwnPropertySymbols,
  cY = Object.prototype.hasOwnProperty,
  fY = Object.prototype.propertyIsEnumerable,
  L2 = (e, t, n) =>
    t in e
      ? lY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  dY = (e, t) => {
    for (var n in t || (t = {})) cY.call(t, n) && L2(e, n, t[n]);
    if (A2) for (var n of A2(t)) fY.call(t, n) && L2(e, n, t[n]);
    return e;
  },
  pY = (e, t) => sY(e, uY(t));
const mY = { xs: 16, sm: 20, md: 24, lg: 30, xl: 36 },
  hY = { xs: 32, sm: 38, md: 46, lg: 56, xl: 72 },
  vY = { xs: 12, sm: 14, md: 18, lg: 22, xl: 28 },
  gY = { xs: 5, sm: 6, md: 7, lg: 9, xl: 11 },
  yY = { xs: 4, sm: 5, md: 6, lg: 8, xl: 10 };
var _Y = Pe(
  (e, { size: t, radius: n, color: r, labelPosition: o, error: a }) => {
    const s = e.fn.size({ size: t, sizes: vY }),
      u = e.fn.size({ size: n, sizes: e.radius }),
      f = e.fn.variant({ variant: "filled", color: r }),
      d = e.fn.size({ size: t, sizes: hY }),
      m = t === "xs" ? 1 : 2,
      h = e.fn.variant({ variant: "filled", color: "red" }).background;
    return {
      input: {
        clip: "rect(1px, 1px, 1px, 1px)",
        height: 0,
        width: 0,
        overflow: "hidden",
        whiteSpace: "nowrap",
        padding: 0,
        WebkitClipPath: "inset(50%)",
        clipPath: "inset(50%)",
        position: "absolute",
      },
      track: pY(dY({}, e.fn.focusStyles("input:focus + &")), {
        cursor: e.cursorType,
        overflow: "hidden",
        WebkitTapHighlightColor: "transparent",
        position: "relative",
        borderRadius: u,
        backgroundColor:
          e.colorScheme === "dark" ? e.colors.dark[6] : e.colors.gray[2],
        border: `1px solid ${
          a ? h : e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[3]
        }`,
        height: e.fn.size({ size: t, sizes: mY }),
        minWidth: d,
        margin: 0,
        transitionProperty: "background-color, border-color",
        transitionTimingFunction: e.transitionTimingFunction,
        transitionDuration: "150ms",
        boxSizing: "border-box",
        appearance: "none",
        display: "flex",
        alignItems: "center",
        fontSize: e.fn.size({ size: t, sizes: gY }),
        fontWeight: 600,
        order: o === "left" ? 2 : 1,
        userSelect: "none",
        MozUserSelect: "none",
        WebkitUserSelect: "none",
        MsUserSelect: "none",
        zIndex: 0,
        lineHeight: 0,
        color: e.colorScheme === "dark" ? e.colors.dark[1] : e.colors.gray[6],
        transition: `color 150ms ${e.transitionTimingFunction}`,
        "input:checked + &": {
          backgroundColor: f.background,
          borderColor: f.background,
          color: e.white,
          transition: `color 150ms ${e.transitionTimingFunction}`,
        },
        "input:disabled + &": {
          backgroundColor:
            e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2],
          borderColor:
            e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2],
          cursor: "not-allowed",
        },
      }),
      thumb: {
        position: "absolute",
        zIndex: 1,
        borderRadius: u,
        boxSizing: "border-box",
        display: "flex",
        backgroundColor: e.white,
        height: s,
        width: s,
        border: `1px solid ${
          e.colorScheme === "dark" ? e.white : e.colors.gray[3]
        }`,
        left: `${m}px`,
        transition: `left 150ms ${e.transitionTimingFunction}`,
        "& > *": { margin: "auto" },
        "@media (prefers-reduced-motion)": {
          transitionDuration: e.respectReducedMotion ? "0ms" : "",
        },
        "input:checked + * > &": {
          left: `calc(100% - ${s}px - ${m}px)`,
          borderColor: e.white,
        },
        "input:disabled + * > &": {
          borderColor:
            e.colorScheme === "dark" ? e.colors.dark[4] : e.colors.gray[2],
          backgroundColor:
            e.colorScheme === "dark" ? e.colors.dark[3] : e.colors.gray[0],
        },
      },
      trackLabel: {
        height: "100%",
        display: "grid",
        placeContent: "center",
        minWidth: d - s,
        paddingInline: e.fn.size({ size: t, sizes: yY }),
        margin: `0 0 0 ${s + m}px`,
        transition: `margin 150ms ${e.transitionTimingFunction}`,
        "input:checked + * > &": { margin: `0 ${s + m}px 0 0` },
      },
    };
  }
);
const wY = _Y;
var bY = Object.defineProperty,
  xY = Object.defineProperties,
  SY = Object.getOwnPropertyDescriptors,
  Ed = Object.getOwnPropertySymbols,
  b5 = Object.prototype.hasOwnProperty,
  x5 = Object.prototype.propertyIsEnumerable,
  D2 = (e, t, n) =>
    t in e
      ? bY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  kh = (e, t) => {
    for (var n in t || (t = {})) b5.call(t, n) && D2(e, n, t[n]);
    if (Ed) for (var n of Ed(t)) x5.call(t, n) && D2(e, n, t[n]);
    return e;
  },
  PY = (e, t) => xY(e, SY(t)),
  OY = (e, t) => {
    var n = {};
    for (var r in e) b5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Ed)
      for (var r of Ed(e)) t.indexOf(r) < 0 && x5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const EY = { offLabel: "", onLabel: "", size: "sm", radius: "xl", error: !1 },
  S5 = y.forwardRef((e, t) => {
    var n;
    const r = me("Switch", EY, e),
      {
        className: o,
        color: a,
        label: s,
        offLabel: u,
        onLabel: f,
        id: d,
        style: m,
        size: h,
        radius: v,
        wrapperProps: b,
        children: O,
        unstyled: E,
        styles: $,
        classNames: _,
        thumbIcon: w,
        sx: P,
        checked: k,
        defaultChecked: I,
        onChange: z,
        labelPosition: A,
        description: M,
        error: B,
        disabled: H,
      } = r,
      q = OY(r, [
        "className",
        "color",
        "label",
        "offLabel",
        "onLabel",
        "id",
        "style",
        "size",
        "radius",
        "wrapperProps",
        "children",
        "unstyled",
        "styles",
        "classNames",
        "thumbIcon",
        "sx",
        "checked",
        "defaultChecked",
        "onChange",
        "labelPosition",
        "description",
        "error",
        "disabled",
      ]),
      Z = rY(),
      { classes: he } = wY(
        {
          size: (Z == null ? void 0 : Z.size) || h,
          color: a,
          radius: v,
          labelPosition: A,
          error: !!B,
        },
        { unstyled: E, styles: $, classNames: _, name: "Switch" }
      ),
      { systemStyles: xe, rest: Ce } = Xs(q),
      ce = dp(d),
      Se = Z
        ? { checked: Z.value.includes(Ce.value), onChange: Z.onChange }
        : {},
      [Y, re] = Is({
        value: (n = Se.checked) != null ? n : k,
        defaultValue: I,
        finalValue: !1,
      });
    return R.createElement(
      Y3,
      kh(
        kh(
          {
            className: o,
            sx: P,
            style: m,
            id: ce,
            size: (Z == null ? void 0 : Z.size) || h,
            labelPosition: A,
            label: s,
            description: M,
            error: B,
            disabled: H,
            __staticSelector: "Switch",
            classNames: _,
            styles: $,
            unstyled: E,
            "data-checked": Se.checked || void 0,
          },
          xe
        ),
        b
      ),
      R.createElement(
        "input",
        PY(kh({}, Ce), {
          disabled: H,
          checked: Y,
          onChange: (ne) => {
            Z ? Se.onChange(ne) : z == null || z(ne),
              re(ne.currentTarget.checked);
          },
          id: ce,
          ref: t,
          type: "checkbox",
          className: he.input,
        })
      ),
      R.createElement(
        "label",
        { htmlFor: ce, className: he.track },
        R.createElement("div", { className: he.thumb }, w),
        R.createElement("div", { className: he.trackLabel }, Y ? f : u)
      )
    );
  });
S5.displayName = "@mantine/core/Switch";
S5.Group = w5;
var $Y = Object.defineProperty,
  CY = Object.defineProperties,
  kY = Object.getOwnPropertyDescriptors,
  M2 = Object.getOwnPropertySymbols,
  RY = Object.prototype.hasOwnProperty,
  NY = Object.prototype.propertyIsEnumerable,
  j2 = (e, t, n) =>
    t in e
      ? $Y(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  IY = (e, t) => {
    for (var n in t || (t = {})) RY.call(t, n) && j2(e, n, t[n]);
    if (M2) for (var n of M2(t)) NY.call(t, n) && j2(e, n, t[n]);
    return e;
  },
  TY = (e, t) => CY(e, kY(t));
function zY(e, t, n) {
  return typeof e < "u"
    ? e in n.headings.sizes
      ? n.headings.sizes[e].fontSize
      : e
    : n.headings.sizes[t].fontSize;
}
function AY(e, t, n) {
  return typeof e < "u" && e in n.headings.sizes
    ? n.headings.sizes[e].lineHeight
    : n.headings.sizes[t].lineHeight;
}
var LY = Pe((e, { element: t, weight: n, size: r, inline: o }) => ({
  root: TY(IY({}, e.fn.fontStyles()), {
    fontFamily: e.headings.fontFamily,
    fontWeight: n || e.headings.sizes[t].fontWeight || e.headings.fontWeight,
    fontSize: zY(r, t, e),
    lineHeight: o ? 1 : AY(r, t, e),
    margin: 0,
  }),
}));
const DY = LY;
var MY = Object.defineProperty,
  $d = Object.getOwnPropertySymbols,
  P5 = Object.prototype.hasOwnProperty,
  O5 = Object.prototype.propertyIsEnumerable,
  F2 = (e, t, n) =>
    t in e
      ? MY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  jY = (e, t) => {
    for (var n in t || (t = {})) P5.call(t, n) && F2(e, n, t[n]);
    if ($d) for (var n of $d(t)) O5.call(t, n) && F2(e, n, t[n]);
    return e;
  },
  FY = (e, t) => {
    var n = {};
    for (var r in e) P5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && $d)
      for (var r of $d(e)) t.indexOf(r) < 0 && O5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const WY = { order: 1 },
  BY = y.forwardRef((e, t) => {
    const n = me("Title", WY, e),
      {
        className: r,
        order: o,
        children: a,
        unstyled: s,
        size: u,
        weight: f,
        inline: d,
      } = n,
      m = FY(n, [
        "className",
        "order",
        "children",
        "unstyled",
        "size",
        "weight",
        "inline",
      ]),
      { classes: h, cx: v } = DY(
        { element: `h${o}`, weight: f, size: u, inline: d },
        { name: "Title", unstyled: s }
      );
    return [1, 2, 3, 4, 5, 6].includes(o)
      ? R.createElement(
          Jr,
          jY({ component: `h${o}`, ref: t, className: v(h.root, r) }, m),
          a
        )
      : null;
  });
BY.displayName = "@mantine/core/Title";
const [UY, qs] = o9("mantine-notifications"),
  xK = qs("show");
qs("hide");
qs("clean");
qs("cleanQueue");
qs("update");
function HY([e, t], n) {
  const r = {};
  return (
    e === "top" && (r.top = n),
    e === "bottom" && (r.bottom = n),
    t === "left" && (r.left = n),
    t === "right" && (r.right = n),
    t === "center" && ((r.left = "50%"), (r.transform = "translateX(-50%)")),
    r
  );
}
var VY = Object.defineProperty,
  W2 = Object.getOwnPropertySymbols,
  YY = Object.prototype.hasOwnProperty,
  GY = Object.prototype.propertyIsEnumerable,
  B2 = (e, t, n) =>
    t in e
      ? VY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  U2 = (e, t) => {
    for (var n in t || (t = {})) YY.call(t, n) && B2(e, n, t[n]);
    if (W2) for (var n of W2(t)) GY.call(t, n) && B2(e, n, t[n]);
    return e;
  };
const H2 = {
    left: "translateX(-100%)",
    right: "translateX(100%)",
    "top-center": "translateY(-100%)",
    "bottom-center": "translateY(100%)",
  },
  XY = {
    left: "translateX(0)",
    right: "translateX(0)",
    "top-center": "translateY(0)",
    "bottom-center": "translateY(0)",
  };
function KY({ state: e, maxHeight: t, positioning: n, transitionDuration: r }) {
  const [o, a] = n,
    s = a === "center" ? `${o}-center` : a,
    u = {
      opacity: 0,
      maxHeight: t,
      transform: H2[s],
      transitionDuration: `${r}ms, ${r}ms, ${r}ms`,
      transitionTimingFunction:
        "cubic-bezier(.51,.3,0,1.21), cubic-bezier(.51,.3,0,1.21), linear",
      transitionProperty: "opacity, transform, max-height",
    },
    f = { opacity: 1, transform: XY[s] },
    d = { opacity: 0, maxHeight: 0, transform: H2[s] },
    m = { entering: f, entered: f, exiting: d, exited: d };
  return U2(U2({}, u), m[e]);
}
function QY(e, t) {
  return typeof t == "number" ? t : t === !1 || e === !1 ? !1 : e;
}
var qY = Object.defineProperty,
  ZY = Object.defineProperties,
  JY = Object.getOwnPropertyDescriptors,
  Cd = Object.getOwnPropertySymbols,
  E5 = Object.prototype.hasOwnProperty,
  $5 = Object.prototype.propertyIsEnumerable,
  V2 = (e, t, n) =>
    t in e
      ? qY(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Y2 = (e, t) => {
    for (var n in t || (t = {})) E5.call(t, n) && V2(e, n, t[n]);
    if (Cd) for (var n of Cd(t)) $5.call(t, n) && V2(e, n, t[n]);
    return e;
  },
  eG = (e, t) => ZY(e, JY(t)),
  G2 = (e, t) => {
    var n = {};
    for (var r in e) E5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && Cd)
      for (var r of Cd(e)) t.indexOf(r) < 0 && $5.call(e, r) && (n[r] = e[r]);
    return n;
  };
function C5(e) {
  var t = e,
    { notification: n, autoClose: r, onHide: o, innerRef: a } = t,
    s = G2(t, ["notification", "autoClose", "onHide", "innerRef"]);
  const u = n,
    { autoClose: f, message: d } = u,
    m = G2(u, ["autoClose", "message"]),
    h = QY(r, f),
    v = y.useRef(),
    b = () => {
      o(n.id), window.clearTimeout(v.current);
    },
    O = () => {
      clearTimeout(v.current);
    },
    E = () => {
      typeof h == "number" && (v.current = window.setTimeout(b, h));
    };
  return (
    y.useEffect(() => {
      typeof n.onOpen == "function" && n.onOpen(n);
    }, []),
    y.useEffect(() => (E(), O), [r, n.autoClose]),
    R.createElement(
      s5,
      eG(Y2(Y2({}, m), s), {
        onClose: b,
        onMouseEnter: O,
        onMouseLeave: E,
        ref: a,
      }),
      d
    )
  );
}
C5.displayName = "@mantine/notifications/NotificationContainer";
var tG = Pe((e, { zIndex: t }) => ({
  notifications: {
    width: `calc(100% - ${e.spacing.md * 2}px)`,
    boxSizing: "border-box",
    position: "fixed",
    zIndex: t,
  },
  notification: { "&:not(:first-of-type)": { marginTop: e.spacing.sm } },
}));
const nG = tG;
var rG = Object.defineProperty,
  oG = Object.defineProperties,
  iG = Object.getOwnPropertyDescriptors,
  X2 = Object.getOwnPropertySymbols,
  aG = Object.prototype.hasOwnProperty,
  lG = Object.prototype.propertyIsEnumerable,
  K2 = (e, t, n) =>
    t in e
      ? rG(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  sG = (e, t) => {
    for (var n in t || (t = {})) aG.call(t, n) && K2(e, n, t[n]);
    if (X2) for (var n of X2(t)) lG.call(t, n) && K2(e, n, t[n]);
    return e;
  },
  uG = (e, t) => oG(e, iG(t));
function cG({ limit: e }) {
  const {
    state: t,
    queue: n,
    update: r,
    cleanQueue: o,
  } = UL({ initialValues: [], limit: e });
  return {
    notifications: t,
    queue: n,
    showNotification: (d) => {
      const m = d.id || PE();
      return (
        r((h) =>
          d.id && h.some((v) => v.id === d.id)
            ? h
            : [...h, uG(sG({}, d), { id: m })]
        ),
        m
      );
    },
    updateNotification: (d) =>
      r((m) => {
        const h = m.findIndex((b) => b.id === d.id);
        if (h === -1) return m;
        const v = [...m];
        return (v[h] = d), v;
      }),
    hideNotification: (d) =>
      r((m) =>
        m.filter((h) =>
          h.id === d ? (typeof h.onClose == "function" && h.onClose(h), !1) : !0
        )
      ),
    cleanQueue: o,
    clean: () => r(() => []),
  };
}
var fG = Object.defineProperty,
  kd = Object.getOwnPropertySymbols,
  k5 = Object.prototype.hasOwnProperty,
  R5 = Object.prototype.propertyIsEnumerable,
  Q2 = (e, t, n) =>
    t in e
      ? fG(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n })
      : (e[t] = n),
  Rh = (e, t) => {
    for (var n in t || (t = {})) k5.call(t, n) && Q2(e, n, t[n]);
    if (kd) for (var n of kd(t)) R5.call(t, n) && Q2(e, n, t[n]);
    return e;
  },
  dG = (e, t) => {
    var n = {};
    for (var r in e) k5.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
    if (e != null && kd)
      for (var r of kd(e)) t.indexOf(r) < 0 && R5.call(e, r) && (n[r] = e[r]);
    return n;
  };
const pG = [
  "top-left",
  "top-right",
  "top-center",
  "bottom-left",
  "bottom-right",
  "bottom-center",
];
function mG(e) {
  var t = e,
    {
      className: n,
      position: r = "bottom-right",
      autoClose: o = 4e3,
      transitionDuration: a = 250,
      containerWidth: s = 440,
      notificationMaxHeight: u = 200,
      limit: f = 5,
      zIndex: d = fp("overlay"),
      style: m,
      children: h,
      target: v,
    } = t,
    b = dG(t, [
      "className",
      "position",
      "autoClose",
      "transitionDuration",
      "containerWidth",
      "notificationMaxHeight",
      "limit",
      "zIndex",
      "style",
      "children",
      "target",
    ]);
  const O = LL(),
    E = y.useRef({}),
    $ = y.useRef(0),
    {
      notifications: _,
      queue: w,
      showNotification: P,
      updateNotification: k,
      hideNotification: I,
      clean: z,
      cleanQueue: A,
    } = cG({ limit: f }),
    { classes: M, cx: B, theme: H } = nG({ zIndex: d }),
    q = S0(),
    he = (H.respectReducedMotion ? q : !1) ? 1 : a,
    xe = (pG.includes(r) ? r : "bottom-right").split("-");
  Zr(() => {
    _.length > $.current && setTimeout(() => O(), 0), ($.current = _.length);
  }, [_]),
    UY({ show: P, hide: I, update: k, clean: z, cleanQueue: A });
  const Ce = _.map((ce) =>
    R.createElement(
      HA,
      {
        key: ce.id,
        timeout: he,
        onEnter: () => E.current[ce.id].offsetHeight,
        nodeRef: { current: E.current[ce.id] },
      },
      (Se) =>
        R.createElement(C5, {
          innerRef: (Y) => {
            E.current[ce.id] = Y;
          },
          notification: ce,
          onHide: I,
          className: M.notification,
          autoClose: o,
          sx: [
            Rh(
              {},
              KY({
                state: Se,
                positioning: xe,
                transitionDuration: he,
                maxHeight: u,
              })
            ),
            ...(Array.isArray(ce.sx) ? ce.sx : [ce.sx]),
          ],
        })
    )
  );
  return R.createElement(
    VO.Provider,
    { value: { notifications: _, queue: w } },
    R.createElement(
      P0,
      { target: v },
      R.createElement(
        Ie,
        Rh(
          {
            className: B(M.notifications, n),
            style: m,
            sx: Rh({ maxWidth: s }, HY(xe, H.spacing.md)),
          },
          b
        ),
        R.createElement(qA, null, Ce)
      )
    ),
    h
  );
}
mG.displayName = "@mantine/notifications/NotificationsProvider";
function q2(e, t) {
  var n = Object.keys(e);
  if (Object.getOwnPropertySymbols) {
    var r = Object.getOwnPropertySymbols(e);
    t &&
      (r = r.filter(function (o) {
        return Object.getOwnPropertyDescriptor(e, o).enumerable;
      })),
      n.push.apply(n, r);
  }
  return n;
}
function K(e) {
  for (var t = 1; t < arguments.length; t++) {
    var n = arguments[t] != null ? arguments[t] : {};
    t % 2
      ? q2(Object(n), !0).forEach(function (r) {
          ht(e, r, n[r]);
        })
      : Object.getOwnPropertyDescriptors
      ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(n))
      : q2(Object(n)).forEach(function (r) {
          Object.defineProperty(e, r, Object.getOwnPropertyDescriptor(n, r));
        });
  }
  return e;
}
function Rd(e) {
  "@babel/helpers - typeof";
  return (
    (Rd =
      typeof Symbol == "function" && typeof Symbol.iterator == "symbol"
        ? function (t) {
            return typeof t;
          }
        : function (t) {
            return t &&
              typeof Symbol == "function" &&
              t.constructor === Symbol &&
              t !== Symbol.prototype
              ? "symbol"
              : typeof t;
          }),
    Rd(e)
  );
}
function hG(e, t) {
  if (!(e instanceof t))
    throw new TypeError("Cannot call a class as a function");
}
function Z2(e, t) {
  for (var n = 0; n < t.length; n++) {
    var r = t[n];
    (r.enumerable = r.enumerable || !1),
      (r.configurable = !0),
      "value" in r && (r.writable = !0),
      Object.defineProperty(e, r.key, r);
  }
}
function vG(e, t, n) {
  return (
    t && Z2(e.prototype, t),
    n && Z2(e, n),
    Object.defineProperty(e, "prototype", { writable: !1 }),
    e
  );
}
function ht(e, t, n) {
  return (
    t in e
      ? Object.defineProperty(e, t, {
          value: n,
          enumerable: !0,
          configurable: !0,
          writable: !0,
        })
      : (e[t] = n),
    e
  );
}
function A0(e, t) {
  return yG(e) || wG(e, t) || N5(e, t) || xG();
}
function Zs(e) {
  return gG(e) || _G(e) || N5(e) || bG();
}
function gG(e) {
  if (Array.isArray(e)) return Gv(e);
}
function yG(e) {
  if (Array.isArray(e)) return e;
}
function _G(e) {
  if (
    (typeof Symbol < "u" && e[Symbol.iterator] != null) ||
    e["@@iterator"] != null
  )
    return Array.from(e);
}
function wG(e, t) {
  var n =
    e == null
      ? null
      : (typeof Symbol < "u" && e[Symbol.iterator]) || e["@@iterator"];
  if (n != null) {
    var r = [],
      o = !0,
      a = !1,
      s,
      u;
    try {
      for (
        n = n.call(e);
        !(o = (s = n.next()).done) && (r.push(s.value), !(t && r.length === t));
        o = !0
      );
    } catch (f) {
      (a = !0), (u = f);
    } finally {
      try {
        !o && n.return != null && n.return();
      } finally {
        if (a) throw u;
      }
    }
    return r;
  }
}
function N5(e, t) {
  if (e) {
    if (typeof e == "string") return Gv(e, t);
    var n = Object.prototype.toString.call(e).slice(8, -1);
    if (
      (n === "Object" && e.constructor && (n = e.constructor.name),
      n === "Map" || n === "Set")
    )
      return Array.from(e);
    if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n))
      return Gv(e, t);
  }
}
function Gv(e, t) {
  (t == null || t > e.length) && (t = e.length);
  for (var n = 0, r = new Array(t); n < t; n++) r[n] = e[n];
  return r;
}
function bG() {
  throw new TypeError(`Invalid attempt to spread non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
}
function xG() {
  throw new TypeError(`Invalid attempt to destructure non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
}
var J2 = function () {},
  L0 = {},
  I5 = {},
  T5 = null,
  z5 = { mark: J2, measure: J2 };
try {
  typeof window < "u" && (L0 = window),
    typeof document < "u" && (I5 = document),
    typeof MutationObserver < "u" && (T5 = MutationObserver),
    typeof performance < "u" && (z5 = performance);
} catch {}
var SG = L0.navigator || {},
  eS = SG.userAgent,
  tS = eS === void 0 ? "" : eS,
  Vo = L0,
  He = I5,
  nS = T5,
  xc = z5;
Vo.document;
var lo =
    !!He.documentElement &&
    !!He.head &&
    typeof He.addEventListener == "function" &&
    typeof He.createElement == "function",
  A5 = ~tS.indexOf("MSIE") || ~tS.indexOf("Trident/"),
  Sc,
  Pc,
  Oc,
  Ec,
  $c,
  eo = "___FONT_AWESOME___",
  Xv = 16,
  L5 = "fa",
  D5 = "svg-inline--fa",
  zi = "data-fa-i2svg",
  Kv = "data-fa-pseudo-element",
  PG = "data-fa-pseudo-element-pending",
  D0 = "data-prefix",
  M0 = "data-icon",
  rS = "fontawesome-i2svg",
  OG = "async",
  EG = ["HTML", "HEAD", "STYLE", "SCRIPT"],
  M5 = (function () {
    try {
      return !0;
    } catch {
      return !1;
    }
  })(),
  Be = "classic",
  et = "sharp",
  j0 = [Be, et];
function Js(e) {
  return new Proxy(e, {
    get: function (n, r) {
      return r in n ? n[r] : n[Be];
    },
  });
}
var zs = Js(
    ((Sc = {}),
    ht(Sc, Be, {
      fa: "solid",
      fas: "solid",
      "fa-solid": "solid",
      far: "regular",
      "fa-regular": "regular",
      fal: "light",
      "fa-light": "light",
      fat: "thin",
      "fa-thin": "thin",
      fad: "duotone",
      "fa-duotone": "duotone",
      fab: "brands",
      "fa-brands": "brands",
      fak: "kit",
      "fa-kit": "kit",
    }),
    ht(Sc, et, {
      fa: "solid",
      fass: "solid",
      "fa-solid": "solid",
      fasr: "regular",
      "fa-regular": "regular",
      fasl: "light",
      "fa-light": "light",
    }),
    Sc)
  ),
  As = Js(
    ((Pc = {}),
    ht(Pc, Be, {
      solid: "fas",
      regular: "far",
      light: "fal",
      thin: "fat",
      duotone: "fad",
      brands: "fab",
      kit: "fak",
    }),
    ht(Pc, et, { solid: "fass", regular: "fasr", light: "fasl" }),
    Pc)
  ),
  Ls = Js(
    ((Oc = {}),
    ht(Oc, Be, {
      fab: "fa-brands",
      fad: "fa-duotone",
      fak: "fa-kit",
      fal: "fa-light",
      far: "fa-regular",
      fas: "fa-solid",
      fat: "fa-thin",
    }),
    ht(Oc, et, { fass: "fa-solid", fasr: "fa-regular", fasl: "fa-light" }),
    Oc)
  ),
  $G = Js(
    ((Ec = {}),
    ht(Ec, Be, {
      "fa-brands": "fab",
      "fa-duotone": "fad",
      "fa-kit": "fak",
      "fa-light": "fal",
      "fa-regular": "far",
      "fa-solid": "fas",
      "fa-thin": "fat",
    }),
    ht(Ec, et, {
      "fa-solid": "fass",
      "fa-regular": "fasr",
      "fa-light": "fasl",
    }),
    Ec)
  ),
  CG = /fa(s|r|l|t|d|b|k|ss|sr|sl)?[\-\ ]/,
  j5 = "fa-layers-text",
  kG =
    /Font ?Awesome ?([56 ]*)(Solid|Regular|Light|Thin|Duotone|Brands|Free|Pro|Sharp|Kit)?.*/i,
  RG = Js(
    (($c = {}),
    ht($c, Be, {
      900: "fas",
      400: "far",
      normal: "far",
      300: "fal",
      100: "fat",
    }),
    ht($c, et, { 900: "fass", 400: "fasr", 300: "fasl" }),
    $c)
  ),
  F5 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  NG = F5.concat([11, 12, 13, 14, 15, 16, 17, 18, 19, 20]),
  IG = [
    "class",
    "data-prefix",
    "data-icon",
    "data-fa-transform",
    "data-fa-mask",
  ],
  Si = {
    GROUP: "duotone-group",
    SWAP_OPACITY: "swap-opacity",
    PRIMARY: "primary",
    SECONDARY: "secondary",
  },
  Ds = new Set();
Object.keys(As[Be]).map(Ds.add.bind(Ds));
Object.keys(As[et]).map(Ds.add.bind(Ds));
var TG = []
    .concat(j0, Zs(Ds), [
      "2xs",
      "xs",
      "sm",
      "lg",
      "xl",
      "2xl",
      "beat",
      "border",
      "fade",
      "beat-fade",
      "bounce",
      "flip-both",
      "flip-horizontal",
      "flip-vertical",
      "flip",
      "fw",
      "inverse",
      "layers-counter",
      "layers-text",
      "layers",
      "li",
      "pull-left",
      "pull-right",
      "pulse",
      "rotate-180",
      "rotate-270",
      "rotate-90",
      "rotate-by",
      "shake",
      "spin-pulse",
      "spin-reverse",
      "spin",
      "stack-1x",
      "stack-2x",
      "stack",
      "ul",
      Si.GROUP,
      Si.SWAP_OPACITY,
      Si.PRIMARY,
      Si.SECONDARY,
    ])
    .concat(
      F5.map(function (e) {
        return "".concat(e, "x");
      })
    )
    .concat(
      NG.map(function (e) {
        return "w-".concat(e);
      })
    ),
  ss = Vo.FontAwesomeConfig || {};
function zG(e) {
  var t = He.querySelector("script[" + e + "]");
  if (t) return t.getAttribute(e);
}
function AG(e) {
  return e === "" ? !0 : e === "false" ? !1 : e === "true" ? !0 : e;
}
if (He && typeof He.querySelector == "function") {
  var LG = [
    ["data-family-prefix", "familyPrefix"],
    ["data-css-prefix", "cssPrefix"],
    ["data-family-default", "familyDefault"],
    ["data-style-default", "styleDefault"],
    ["data-replacement-class", "replacementClass"],
    ["data-auto-replace-svg", "autoReplaceSvg"],
    ["data-auto-add-css", "autoAddCss"],
    ["data-auto-a11y", "autoA11y"],
    ["data-search-pseudo-elements", "searchPseudoElements"],
    ["data-observe-mutations", "observeMutations"],
    ["data-mutate-approach", "mutateApproach"],
    ["data-keep-original-source", "keepOriginalSource"],
    ["data-measure-performance", "measurePerformance"],
    ["data-show-missing-icons", "showMissingIcons"],
  ];
  LG.forEach(function (e) {
    var t = A0(e, 2),
      n = t[0],
      r = t[1],
      o = AG(zG(n));
    o != null && (ss[r] = o);
  });
}
var W5 = {
  styleDefault: "solid",
  familyDefault: "classic",
  cssPrefix: L5,
  replacementClass: D5,
  autoReplaceSvg: !0,
  autoAddCss: !0,
  autoA11y: !0,
  searchPseudoElements: !1,
  observeMutations: !0,
  mutateApproach: "async",
  keepOriginalSource: !0,
  measurePerformance: !1,
  showMissingIcons: !0,
};
ss.familyPrefix && (ss.cssPrefix = ss.familyPrefix);
var Ka = K(K({}, W5), ss);
Ka.autoReplaceSvg || (Ka.observeMutations = !1);
var ee = {};
Object.keys(W5).forEach(function (e) {
  Object.defineProperty(ee, e, {
    enumerable: !0,
    set: function (n) {
      (Ka[e] = n),
        us.forEach(function (r) {
          return r(ee);
        });
    },
    get: function () {
      return Ka[e];
    },
  });
});
Object.defineProperty(ee, "familyPrefix", {
  enumerable: !0,
  set: function (t) {
    (Ka.cssPrefix = t),
      us.forEach(function (n) {
        return n(ee);
      });
  },
  get: function () {
    return Ka.cssPrefix;
  },
});
Vo.FontAwesomeConfig = ee;
var us = [];
function DG(e) {
  return (
    us.push(e),
    function () {
      us.splice(us.indexOf(e), 1);
    }
  );
}
var So = Xv,
  xr = { size: 16, x: 0, y: 0, rotate: 0, flipX: !1, flipY: !1 };
function MG(e) {
  if (!(!e || !lo)) {
    var t = He.createElement("style");
    t.setAttribute("type", "text/css"), (t.innerHTML = e);
    for (var n = He.head.childNodes, r = null, o = n.length - 1; o > -1; o--) {
      var a = n[o],
        s = (a.tagName || "").toUpperCase();
      ["STYLE", "LINK"].indexOf(s) > -1 && (r = a);
    }
    return He.head.insertBefore(t, r), e;
  }
}
var jG = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
function Ms() {
  for (var e = 12, t = ""; e-- > 0; ) t += jG[(Math.random() * 62) | 0];
  return t;
}
function al(e) {
  for (var t = [], n = (e || []).length >>> 0; n--; ) t[n] = e[n];
  return t;
}
function F0(e) {
  return e.classList
    ? al(e.classList)
    : (e.getAttribute("class") || "").split(" ").filter(function (t) {
        return t;
      });
}
function B5(e) {
  return ""
    .concat(e)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
function FG(e) {
  return Object.keys(e || {})
    .reduce(function (t, n) {
      return t + "".concat(n, '="').concat(B5(e[n]), '" ');
    }, "")
    .trim();
}
function _p(e) {
  return Object.keys(e || {}).reduce(function (t, n) {
    return t + "".concat(n, ": ").concat(e[n].trim(), ";");
  }, "");
}
function W0(e) {
  return (
    e.size !== xr.size ||
    e.x !== xr.x ||
    e.y !== xr.y ||
    e.rotate !== xr.rotate ||
    e.flipX ||
    e.flipY
  );
}
function WG(e) {
  var t = e.transform,
    n = e.containerWidth,
    r = e.iconWidth,
    o = { transform: "translate(".concat(n / 2, " 256)") },
    a = "translate(".concat(t.x * 32, ", ").concat(t.y * 32, ") "),
    s = "scale("
      .concat((t.size / 16) * (t.flipX ? -1 : 1), ", ")
      .concat((t.size / 16) * (t.flipY ? -1 : 1), ") "),
    u = "rotate(".concat(t.rotate, " 0 0)"),
    f = { transform: "".concat(a, " ").concat(s, " ").concat(u) },
    d = { transform: "translate(".concat((r / 2) * -1, " -256)") };
  return { outer: o, inner: f, path: d };
}
function BG(e) {
  var t = e.transform,
    n = e.width,
    r = n === void 0 ? Xv : n,
    o = e.height,
    a = o === void 0 ? Xv : o,
    s = e.startCentered,
    u = s === void 0 ? !1 : s,
    f = "";
  return (
    u && A5
      ? (f += "translate("
          .concat(t.x / So - r / 2, "em, ")
          .concat(t.y / So - a / 2, "em) "))
      : u
      ? (f += "translate(calc(-50% + "
          .concat(t.x / So, "em), calc(-50% + ")
          .concat(t.y / So, "em)) "))
      : (f += "translate(".concat(t.x / So, "em, ").concat(t.y / So, "em) ")),
    (f += "scale("
      .concat((t.size / So) * (t.flipX ? -1 : 1), ", ")
      .concat((t.size / So) * (t.flipY ? -1 : 1), ") ")),
    (f += "rotate(".concat(t.rotate, "deg) ")),
    f
  );
}
var UG = `:root, :host {
  --fa-font-solid: normal 900 1em/1 "Font Awesome 6 Solid";
  --fa-font-regular: normal 400 1em/1 "Font Awesome 6 Regular";
  --fa-font-light: normal 300 1em/1 "Font Awesome 6 Light";
  --fa-font-thin: normal 100 1em/1 "Font Awesome 6 Thin";
  --fa-font-duotone: normal 900 1em/1 "Font Awesome 6 Duotone";
  --fa-font-sharp-solid: normal 900 1em/1 "Font Awesome 6 Sharp";
  --fa-font-sharp-regular: normal 400 1em/1 "Font Awesome 6 Sharp";
  --fa-font-sharp-light: normal 300 1em/1 "Font Awesome 6 Sharp";
  --fa-font-brands: normal 400 1em/1 "Font Awesome 6 Brands";
}

svg:not(:root).svg-inline--fa, svg:not(:host).svg-inline--fa {
  overflow: visible;
  box-sizing: content-box;
}

.svg-inline--fa {
  display: var(--fa-display, inline-block);
  height: 1em;
  overflow: visible;
  vertical-align: -0.125em;
}
.svg-inline--fa.fa-2xs {
  vertical-align: 0.1em;
}
.svg-inline--fa.fa-xs {
  vertical-align: 0em;
}
.svg-inline--fa.fa-sm {
  vertical-align: -0.0714285705em;
}
.svg-inline--fa.fa-lg {
  vertical-align: -0.2em;
}
.svg-inline--fa.fa-xl {
  vertical-align: -0.25em;
}
.svg-inline--fa.fa-2xl {
  vertical-align: -0.3125em;
}
.svg-inline--fa.fa-pull-left {
  margin-right: var(--fa-pull-margin, 0.3em);
  width: auto;
}
.svg-inline--fa.fa-pull-right {
  margin-left: var(--fa-pull-margin, 0.3em);
  width: auto;
}
.svg-inline--fa.fa-li {
  width: var(--fa-li-width, 2em);
  top: 0.25em;
}
.svg-inline--fa.fa-fw {
  width: var(--fa-fw-width, 1.25em);
}

.fa-layers svg.svg-inline--fa {
  bottom: 0;
  left: 0;
  margin: auto;
  position: absolute;
  right: 0;
  top: 0;
}

.fa-layers-counter, .fa-layers-text {
  display: inline-block;
  position: absolute;
  text-align: center;
}

.fa-layers {
  display: inline-block;
  height: 1em;
  position: relative;
  text-align: center;
  vertical-align: -0.125em;
  width: 1em;
}
.fa-layers svg.svg-inline--fa {
  -webkit-transform-origin: center center;
          transform-origin: center center;
}

.fa-layers-text {
  left: 50%;
  top: 50%;
  -webkit-transform: translate(-50%, -50%);
          transform: translate(-50%, -50%);
  -webkit-transform-origin: center center;
          transform-origin: center center;
}

.fa-layers-counter {
  background-color: var(--fa-counter-background-color, #ff253a);
  border-radius: var(--fa-counter-border-radius, 1em);
  box-sizing: border-box;
  color: var(--fa-inverse, #fff);
  line-height: var(--fa-counter-line-height, 1);
  max-width: var(--fa-counter-max-width, 5em);
  min-width: var(--fa-counter-min-width, 1.5em);
  overflow: hidden;
  padding: var(--fa-counter-padding, 0.25em 0.5em);
  right: var(--fa-right, 0);
  text-overflow: ellipsis;
  top: var(--fa-top, 0);
  -webkit-transform: scale(var(--fa-counter-scale, 0.25));
          transform: scale(var(--fa-counter-scale, 0.25));
  -webkit-transform-origin: top right;
          transform-origin: top right;
}

.fa-layers-bottom-right {
  bottom: var(--fa-bottom, 0);
  right: var(--fa-right, 0);
  top: auto;
  -webkit-transform: scale(var(--fa-layers-scale, 0.25));
          transform: scale(var(--fa-layers-scale, 0.25));
  -webkit-transform-origin: bottom right;
          transform-origin: bottom right;
}

.fa-layers-bottom-left {
  bottom: var(--fa-bottom, 0);
  left: var(--fa-left, 0);
  right: auto;
  top: auto;
  -webkit-transform: scale(var(--fa-layers-scale, 0.25));
          transform: scale(var(--fa-layers-scale, 0.25));
  -webkit-transform-origin: bottom left;
          transform-origin: bottom left;
}

.fa-layers-top-right {
  top: var(--fa-top, 0);
  right: var(--fa-right, 0);
  -webkit-transform: scale(var(--fa-layers-scale, 0.25));
          transform: scale(var(--fa-layers-scale, 0.25));
  -webkit-transform-origin: top right;
          transform-origin: top right;
}

.fa-layers-top-left {
  left: var(--fa-left, 0);
  right: auto;
  top: var(--fa-top, 0);
  -webkit-transform: scale(var(--fa-layers-scale, 0.25));
          transform: scale(var(--fa-layers-scale, 0.25));
  -webkit-transform-origin: top left;
          transform-origin: top left;
}

.fa-1x {
  font-size: 1em;
}

.fa-2x {
  font-size: 2em;
}

.fa-3x {
  font-size: 3em;
}

.fa-4x {
  font-size: 4em;
}

.fa-5x {
  font-size: 5em;
}

.fa-6x {
  font-size: 6em;
}

.fa-7x {
  font-size: 7em;
}

.fa-8x {
  font-size: 8em;
}

.fa-9x {
  font-size: 9em;
}

.fa-10x {
  font-size: 10em;
}

.fa-2xs {
  font-size: 0.625em;
  line-height: 0.1em;
  vertical-align: 0.225em;
}

.fa-xs {
  font-size: 0.75em;
  line-height: 0.0833333337em;
  vertical-align: 0.125em;
}

.fa-sm {
  font-size: 0.875em;
  line-height: 0.0714285718em;
  vertical-align: 0.0535714295em;
}

.fa-lg {
  font-size: 1.25em;
  line-height: 0.05em;
  vertical-align: -0.075em;
}

.fa-xl {
  font-size: 1.5em;
  line-height: 0.0416666682em;
  vertical-align: -0.125em;
}

.fa-2xl {
  font-size: 2em;
  line-height: 0.03125em;
  vertical-align: -0.1875em;
}

.fa-fw {
  text-align: center;
  width: 1.25em;
}

.fa-ul {
  list-style-type: none;
  margin-left: var(--fa-li-margin, 2.5em);
  padding-left: 0;
}
.fa-ul > li {
  position: relative;
}

.fa-li {
  left: calc(var(--fa-li-width, 2em) * -1);
  position: absolute;
  text-align: center;
  width: var(--fa-li-width, 2em);
  line-height: inherit;
}

.fa-border {
  border-color: var(--fa-border-color, #eee);
  border-radius: var(--fa-border-radius, 0.1em);
  border-style: var(--fa-border-style, solid);
  border-width: var(--fa-border-width, 0.08em);
  padding: var(--fa-border-padding, 0.2em 0.25em 0.15em);
}

.fa-pull-left {
  float: left;
  margin-right: var(--fa-pull-margin, 0.3em);
}

.fa-pull-right {
  float: right;
  margin-left: var(--fa-pull-margin, 0.3em);
}

.fa-beat {
  -webkit-animation-name: fa-beat;
          animation-name: fa-beat;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, ease-in-out);
          animation-timing-function: var(--fa-animation-timing, ease-in-out);
}

.fa-bounce {
  -webkit-animation-name: fa-bounce;
          animation-name: fa-bounce;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.28, 0.84, 0.42, 1));
          animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.28, 0.84, 0.42, 1));
}

.fa-fade {
  -webkit-animation-name: fa-fade;
          animation-name: fa-fade;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.4, 0, 0.6, 1));
          animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.4, 0, 0.6, 1));
}

.fa-beat-fade {
  -webkit-animation-name: fa-beat-fade;
          animation-name: fa-beat-fade;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.4, 0, 0.6, 1));
          animation-timing-function: var(--fa-animation-timing, cubic-bezier(0.4, 0, 0.6, 1));
}

.fa-flip {
  -webkit-animation-name: fa-flip;
          animation-name: fa-flip;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, ease-in-out);
          animation-timing-function: var(--fa-animation-timing, ease-in-out);
}

.fa-shake {
  -webkit-animation-name: fa-shake;
          animation-name: fa-shake;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, linear);
          animation-timing-function: var(--fa-animation-timing, linear);
}

.fa-spin {
  -webkit-animation-name: fa-spin;
          animation-name: fa-spin;
  -webkit-animation-delay: var(--fa-animation-delay, 0s);
          animation-delay: var(--fa-animation-delay, 0s);
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 2s);
          animation-duration: var(--fa-animation-duration, 2s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, linear);
          animation-timing-function: var(--fa-animation-timing, linear);
}

.fa-spin-reverse {
  --fa-animation-direction: reverse;
}

.fa-pulse,
.fa-spin-pulse {
  -webkit-animation-name: fa-spin;
          animation-name: fa-spin;
  -webkit-animation-direction: var(--fa-animation-direction, normal);
          animation-direction: var(--fa-animation-direction, normal);
  -webkit-animation-duration: var(--fa-animation-duration, 1s);
          animation-duration: var(--fa-animation-duration, 1s);
  -webkit-animation-iteration-count: var(--fa-animation-iteration-count, infinite);
          animation-iteration-count: var(--fa-animation-iteration-count, infinite);
  -webkit-animation-timing-function: var(--fa-animation-timing, steps(8));
          animation-timing-function: var(--fa-animation-timing, steps(8));
}

@media (prefers-reduced-motion: reduce) {
  .fa-beat,
.fa-bounce,
.fa-fade,
.fa-beat-fade,
.fa-flip,
.fa-pulse,
.fa-shake,
.fa-spin,
.fa-spin-pulse {
    -webkit-animation-delay: -1ms;
            animation-delay: -1ms;
    -webkit-animation-duration: 1ms;
            animation-duration: 1ms;
    -webkit-animation-iteration-count: 1;
            animation-iteration-count: 1;
    -webkit-transition-delay: 0s;
            transition-delay: 0s;
    -webkit-transition-duration: 0s;
            transition-duration: 0s;
  }
}
@-webkit-keyframes fa-beat {
  0%, 90% {
    -webkit-transform: scale(1);
            transform: scale(1);
  }
  45% {
    -webkit-transform: scale(var(--fa-beat-scale, 1.25));
            transform: scale(var(--fa-beat-scale, 1.25));
  }
}
@keyframes fa-beat {
  0%, 90% {
    -webkit-transform: scale(1);
            transform: scale(1);
  }
  45% {
    -webkit-transform: scale(var(--fa-beat-scale, 1.25));
            transform: scale(var(--fa-beat-scale, 1.25));
  }
}
@-webkit-keyframes fa-bounce {
  0% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
  10% {
    -webkit-transform: scale(var(--fa-bounce-start-scale-x, 1.1), var(--fa-bounce-start-scale-y, 0.9)) translateY(0);
            transform: scale(var(--fa-bounce-start-scale-x, 1.1), var(--fa-bounce-start-scale-y, 0.9)) translateY(0);
  }
  30% {
    -webkit-transform: scale(var(--fa-bounce-jump-scale-x, 0.9), var(--fa-bounce-jump-scale-y, 1.1)) translateY(var(--fa-bounce-height, -0.5em));
            transform: scale(var(--fa-bounce-jump-scale-x, 0.9), var(--fa-bounce-jump-scale-y, 1.1)) translateY(var(--fa-bounce-height, -0.5em));
  }
  50% {
    -webkit-transform: scale(var(--fa-bounce-land-scale-x, 1.05), var(--fa-bounce-land-scale-y, 0.95)) translateY(0);
            transform: scale(var(--fa-bounce-land-scale-x, 1.05), var(--fa-bounce-land-scale-y, 0.95)) translateY(0);
  }
  57% {
    -webkit-transform: scale(1, 1) translateY(var(--fa-bounce-rebound, -0.125em));
            transform: scale(1, 1) translateY(var(--fa-bounce-rebound, -0.125em));
  }
  64% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
  100% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
}
@keyframes fa-bounce {
  0% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
  10% {
    -webkit-transform: scale(var(--fa-bounce-start-scale-x, 1.1), var(--fa-bounce-start-scale-y, 0.9)) translateY(0);
            transform: scale(var(--fa-bounce-start-scale-x, 1.1), var(--fa-bounce-start-scale-y, 0.9)) translateY(0);
  }
  30% {
    -webkit-transform: scale(var(--fa-bounce-jump-scale-x, 0.9), var(--fa-bounce-jump-scale-y, 1.1)) translateY(var(--fa-bounce-height, -0.5em));
            transform: scale(var(--fa-bounce-jump-scale-x, 0.9), var(--fa-bounce-jump-scale-y, 1.1)) translateY(var(--fa-bounce-height, -0.5em));
  }
  50% {
    -webkit-transform: scale(var(--fa-bounce-land-scale-x, 1.05), var(--fa-bounce-land-scale-y, 0.95)) translateY(0);
            transform: scale(var(--fa-bounce-land-scale-x, 1.05), var(--fa-bounce-land-scale-y, 0.95)) translateY(0);
  }
  57% {
    -webkit-transform: scale(1, 1) translateY(var(--fa-bounce-rebound, -0.125em));
            transform: scale(1, 1) translateY(var(--fa-bounce-rebound, -0.125em));
  }
  64% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
  100% {
    -webkit-transform: scale(1, 1) translateY(0);
            transform: scale(1, 1) translateY(0);
  }
}
@-webkit-keyframes fa-fade {
  50% {
    opacity: var(--fa-fade-opacity, 0.4);
  }
}
@keyframes fa-fade {
  50% {
    opacity: var(--fa-fade-opacity, 0.4);
  }
}
@-webkit-keyframes fa-beat-fade {
  0%, 100% {
    opacity: var(--fa-beat-fade-opacity, 0.4);
    -webkit-transform: scale(1);
            transform: scale(1);
  }
  50% {
    opacity: 1;
    -webkit-transform: scale(var(--fa-beat-fade-scale, 1.125));
            transform: scale(var(--fa-beat-fade-scale, 1.125));
  }
}
@keyframes fa-beat-fade {
  0%, 100% {
    opacity: var(--fa-beat-fade-opacity, 0.4);
    -webkit-transform: scale(1);
            transform: scale(1);
  }
  50% {
    opacity: 1;
    -webkit-transform: scale(var(--fa-beat-fade-scale, 1.125));
            transform: scale(var(--fa-beat-fade-scale, 1.125));
  }
}
@-webkit-keyframes fa-flip {
  50% {
    -webkit-transform: rotate3d(var(--fa-flip-x, 0), var(--fa-flip-y, 1), var(--fa-flip-z, 0), var(--fa-flip-angle, -180deg));
            transform: rotate3d(var(--fa-flip-x, 0), var(--fa-flip-y, 1), var(--fa-flip-z, 0), var(--fa-flip-angle, -180deg));
  }
}
@keyframes fa-flip {
  50% {
    -webkit-transform: rotate3d(var(--fa-flip-x, 0), var(--fa-flip-y, 1), var(--fa-flip-z, 0), var(--fa-flip-angle, -180deg));
            transform: rotate3d(var(--fa-flip-x, 0), var(--fa-flip-y, 1), var(--fa-flip-z, 0), var(--fa-flip-angle, -180deg));
  }
}
@-webkit-keyframes fa-shake {
  0% {
    -webkit-transform: rotate(-15deg);
            transform: rotate(-15deg);
  }
  4% {
    -webkit-transform: rotate(15deg);
            transform: rotate(15deg);
  }
  8%, 24% {
    -webkit-transform: rotate(-18deg);
            transform: rotate(-18deg);
  }
  12%, 28% {
    -webkit-transform: rotate(18deg);
            transform: rotate(18deg);
  }
  16% {
    -webkit-transform: rotate(-22deg);
            transform: rotate(-22deg);
  }
  20% {
    -webkit-transform: rotate(22deg);
            transform: rotate(22deg);
  }
  32% {
    -webkit-transform: rotate(-12deg);
            transform: rotate(-12deg);
  }
  36% {
    -webkit-transform: rotate(12deg);
            transform: rotate(12deg);
  }
  40%, 100% {
    -webkit-transform: rotate(0deg);
            transform: rotate(0deg);
  }
}
@keyframes fa-shake {
  0% {
    -webkit-transform: rotate(-15deg);
            transform: rotate(-15deg);
  }
  4% {
    -webkit-transform: rotate(15deg);
            transform: rotate(15deg);
  }
  8%, 24% {
    -webkit-transform: rotate(-18deg);
            transform: rotate(-18deg);
  }
  12%, 28% {
    -webkit-transform: rotate(18deg);
            transform: rotate(18deg);
  }
  16% {
    -webkit-transform: rotate(-22deg);
            transform: rotate(-22deg);
  }
  20% {
    -webkit-transform: rotate(22deg);
            transform: rotate(22deg);
  }
  32% {
    -webkit-transform: rotate(-12deg);
            transform: rotate(-12deg);
  }
  36% {
    -webkit-transform: rotate(12deg);
            transform: rotate(12deg);
  }
  40%, 100% {
    -webkit-transform: rotate(0deg);
            transform: rotate(0deg);
  }
}
@-webkit-keyframes fa-spin {
  0% {
    -webkit-transform: rotate(0deg);
            transform: rotate(0deg);
  }
  100% {
    -webkit-transform: rotate(360deg);
            transform: rotate(360deg);
  }
}
@keyframes fa-spin {
  0% {
    -webkit-transform: rotate(0deg);
            transform: rotate(0deg);
  }
  100% {
    -webkit-transform: rotate(360deg);
            transform: rotate(360deg);
  }
}
.fa-rotate-90 {
  -webkit-transform: rotate(90deg);
          transform: rotate(90deg);
}

.fa-rotate-180 {
  -webkit-transform: rotate(180deg);
          transform: rotate(180deg);
}

.fa-rotate-270 {
  -webkit-transform: rotate(270deg);
          transform: rotate(270deg);
}

.fa-flip-horizontal {
  -webkit-transform: scale(-1, 1);
          transform: scale(-1, 1);
}

.fa-flip-vertical {
  -webkit-transform: scale(1, -1);
          transform: scale(1, -1);
}

.fa-flip-both,
.fa-flip-horizontal.fa-flip-vertical {
  -webkit-transform: scale(-1, -1);
          transform: scale(-1, -1);
}

.fa-rotate-by {
  -webkit-transform: rotate(var(--fa-rotate-angle, none));
          transform: rotate(var(--fa-rotate-angle, none));
}

.fa-stack {
  display: inline-block;
  vertical-align: middle;
  height: 2em;
  position: relative;
  width: 2.5em;
}

.fa-stack-1x,
.fa-stack-2x {
  bottom: 0;
  left: 0;
  margin: auto;
  position: absolute;
  right: 0;
  top: 0;
  z-index: var(--fa-stack-z-index, auto);
}

.svg-inline--fa.fa-stack-1x {
  height: 1em;
  width: 1.25em;
}
.svg-inline--fa.fa-stack-2x {
  height: 2em;
  width: 2.5em;
}

.fa-inverse {
  color: var(--fa-inverse, #fff);
}

.sr-only,
.fa-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

.sr-only-focusable:not(:focus),
.fa-sr-only-focusable:not(:focus) {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

.svg-inline--fa .fa-primary {
  fill: var(--fa-primary-color, currentColor);
  opacity: var(--fa-primary-opacity, 1);
}

.svg-inline--fa .fa-secondary {
  fill: var(--fa-secondary-color, currentColor);
  opacity: var(--fa-secondary-opacity, 0.4);
}

.svg-inline--fa.fa-swap-opacity .fa-primary {
  opacity: var(--fa-secondary-opacity, 0.4);
}

.svg-inline--fa.fa-swap-opacity .fa-secondary {
  opacity: var(--fa-primary-opacity, 1);
}

.svg-inline--fa mask .fa-primary,
.svg-inline--fa mask .fa-secondary {
  fill: black;
}

.fad.fa-inverse,
.fa-duotone.fa-inverse {
  color: var(--fa-inverse, #fff);
}`;
function U5() {
  var e = L5,
    t = D5,
    n = ee.cssPrefix,
    r = ee.replacementClass,
    o = UG;
  if (n !== e || r !== t) {
    var a = new RegExp("\\.".concat(e, "\\-"), "g"),
      s = new RegExp("\\--".concat(e, "\\-"), "g"),
      u = new RegExp("\\.".concat(t), "g");
    o = o
      .replace(a, ".".concat(n, "-"))
      .replace(s, "--".concat(n, "-"))
      .replace(u, ".".concat(r));
  }
  return o;
}
var oS = !1;
function Nh() {
  ee.autoAddCss && !oS && (MG(U5()), (oS = !0));
}
var HG = {
    mixout: function () {
      return { dom: { css: U5, insertCss: Nh } };
    },
    hooks: function () {
      return {
        beforeDOMElementCreation: function () {
          Nh();
        },
        beforeI2svg: function () {
          Nh();
        },
      };
    },
  },
  to = Vo || {};
to[eo] || (to[eo] = {});
to[eo].styles || (to[eo].styles = {});
to[eo].hooks || (to[eo].hooks = {});
to[eo].shims || (to[eo].shims = []);
var ir = to[eo],
  H5 = [],
  VG = function e() {
    He.removeEventListener("DOMContentLoaded", e),
      (Nd = 1),
      H5.map(function (t) {
        return t();
      });
  },
  Nd = !1;
lo &&
  ((Nd = (He.documentElement.doScroll ? /^loaded|^c/ : /^loaded|^i|^c/).test(
    He.readyState
  )),
  Nd || He.addEventListener("DOMContentLoaded", VG));
function YG(e) {
  lo && (Nd ? setTimeout(e, 0) : H5.push(e));
}
function eu(e) {
  var t = e.tag,
    n = e.attributes,
    r = n === void 0 ? {} : n,
    o = e.children,
    a = o === void 0 ? [] : o;
  return typeof e == "string"
    ? B5(e)
    : "<"
        .concat(t, " ")
        .concat(FG(r), ">")
        .concat(a.map(eu).join(""), "</")
        .concat(t, ">");
}
function iS(e, t, n) {
  if (e && e[t] && e[t][n]) return { prefix: t, iconName: n, icon: e[t][n] };
}
var GG = function (t, n) {
    return function (r, o, a, s) {
      return t.call(n, r, o, a, s);
    };
  },
  Ih = function (t, n, r, o) {
    var a = Object.keys(t),
      s = a.length,
      u = o !== void 0 ? GG(n, o) : n,
      f,
      d,
      m;
    for (
      r === void 0 ? ((f = 1), (m = t[a[0]])) : ((f = 0), (m = r));
      f < s;
      f++
    )
      (d = a[f]), (m = u(m, t[d], d, t));
    return m;
  };
function XG(e) {
  for (var t = [], n = 0, r = e.length; n < r; ) {
    var o = e.charCodeAt(n++);
    if (o >= 55296 && o <= 56319 && n < r) {
      var a = e.charCodeAt(n++);
      (a & 64512) == 56320
        ? t.push(((o & 1023) << 10) + (a & 1023) + 65536)
        : (t.push(o), n--);
    } else t.push(o);
  }
  return t;
}
function Qv(e) {
  var t = XG(e);
  return t.length === 1 ? t[0].toString(16) : null;
}
function KG(e, t) {
  var n = e.length,
    r = e.charCodeAt(t),
    o;
  return r >= 55296 &&
    r <= 56319 &&
    n > t + 1 &&
    ((o = e.charCodeAt(t + 1)), o >= 56320 && o <= 57343)
    ? (r - 55296) * 1024 + o - 56320 + 65536
    : r;
}
function aS(e) {
  return Object.keys(e).reduce(function (t, n) {
    var r = e[n],
      o = !!r.icon;
    return o ? (t[r.iconName] = r.icon) : (t[n] = r), t;
  }, {});
}
function qv(e, t) {
  var n = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {},
    r = n.skipHooks,
    o = r === void 0 ? !1 : r,
    a = aS(t);
  typeof ir.hooks.addPack == "function" && !o
    ? ir.hooks.addPack(e, aS(t))
    : (ir.styles[e] = K(K({}, ir.styles[e] || {}), a)),
    e === "fas" && qv("fa", t);
}
var Cc,
  kc,
  Rc,
  Oa = ir.styles,
  QG = ir.shims,
  qG =
    ((Cc = {}),
    ht(Cc, Be, Object.values(Ls[Be])),
    ht(Cc, et, Object.values(Ls[et])),
    Cc),
  B0 = null,
  V5 = {},
  Y5 = {},
  G5 = {},
  X5 = {},
  K5 = {},
  ZG =
    ((kc = {}),
    ht(kc, Be, Object.keys(zs[Be])),
    ht(kc, et, Object.keys(zs[et])),
    kc);
function JG(e) {
  return ~TG.indexOf(e);
}
function eX(e, t) {
  var n = t.split("-"),
    r = n[0],
    o = n.slice(1).join("-");
  return r === e && o !== "" && !JG(o) ? o : null;
}
var Q5 = function () {
  var t = function (a) {
    return Ih(
      Oa,
      function (s, u, f) {
        return (s[f] = Ih(u, a, {})), s;
      },
      {}
    );
  };
  (V5 = t(function (o, a, s) {
    if ((a[3] && (o[a[3]] = s), a[2])) {
      var u = a[2].filter(function (f) {
        return typeof f == "number";
      });
      u.forEach(function (f) {
        o[f.toString(16)] = s;
      });
    }
    return o;
  })),
    (Y5 = t(function (o, a, s) {
      if (((o[s] = s), a[2])) {
        var u = a[2].filter(function (f) {
          return typeof f == "string";
        });
        u.forEach(function (f) {
          o[f] = s;
        });
      }
      return o;
    })),
    (K5 = t(function (o, a, s) {
      var u = a[2];
      return (
        (o[s] = s),
        u.forEach(function (f) {
          o[f] = s;
        }),
        o
      );
    }));
  var n = "far" in Oa || ee.autoFetchSvg,
    r = Ih(
      QG,
      function (o, a) {
        var s = a[0],
          u = a[1],
          f = a[2];
        return (
          u === "far" && !n && (u = "fas"),
          typeof s == "string" && (o.names[s] = { prefix: u, iconName: f }),
          typeof s == "number" &&
            (o.unicodes[s.toString(16)] = { prefix: u, iconName: f }),
          o
        );
      },
      { names: {}, unicodes: {} }
    );
  (G5 = r.names),
    (X5 = r.unicodes),
    (B0 = wp(ee.styleDefault, { family: ee.familyDefault }));
};
DG(function (e) {
  B0 = wp(e.styleDefault, { family: ee.familyDefault });
});
Q5();
function U0(e, t) {
  return (V5[e] || {})[t];
}
function tX(e, t) {
  return (Y5[e] || {})[t];
}
function Pi(e, t) {
  return (K5[e] || {})[t];
}
function q5(e) {
  return G5[e] || { prefix: null, iconName: null };
}
function nX(e) {
  var t = X5[e],
    n = U0("fas", e);
  return (
    t ||
    (n ? { prefix: "fas", iconName: n } : null) || {
      prefix: null,
      iconName: null,
    }
  );
}
function Yo() {
  return B0;
}
var H0 = function () {
  return { prefix: null, iconName: null, rest: [] };
};
function wp(e) {
  var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {},
    n = t.family,
    r = n === void 0 ? Be : n,
    o = zs[r][e],
    a = As[r][e] || As[r][o],
    s = e in ir.styles ? e : null;
  return a || s || null;
}
var lS =
  ((Rc = {}),
  ht(Rc, Be, Object.keys(Ls[Be])),
  ht(Rc, et, Object.keys(Ls[et])),
  Rc);
function bp(e) {
  var t,
    n = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {},
    r = n.skipLookups,
    o = r === void 0 ? !1 : r,
    a =
      ((t = {}),
      ht(t, Be, "".concat(ee.cssPrefix, "-").concat(Be)),
      ht(t, et, "".concat(ee.cssPrefix, "-").concat(et)),
      t),
    s = null,
    u = Be;
  (e.includes(a[Be]) ||
    e.some(function (d) {
      return lS[Be].includes(d);
    })) &&
    (u = Be),
    (e.includes(a[et]) ||
      e.some(function (d) {
        return lS[et].includes(d);
      })) &&
      (u = et);
  var f = e.reduce(function (d, m) {
    var h = eX(ee.cssPrefix, m);
    if (
      (Oa[m]
        ? ((m = qG[u].includes(m) ? $G[u][m] : m), (s = m), (d.prefix = m))
        : ZG[u].indexOf(m) > -1
        ? ((s = m), (d.prefix = wp(m, { family: u })))
        : h
        ? (d.iconName = h)
        : m !== ee.replacementClass &&
          m !== a[Be] &&
          m !== a[et] &&
          d.rest.push(m),
      !o && d.prefix && d.iconName)
    ) {
      var v = s === "fa" ? q5(d.iconName) : {},
        b = Pi(d.prefix, d.iconName);
      v.prefix && (s = null),
        (d.iconName = v.iconName || b || d.iconName),
        (d.prefix = v.prefix || d.prefix),
        d.prefix === "far" &&
          !Oa.far &&
          Oa.fas &&
          !ee.autoFetchSvg &&
          (d.prefix = "fas");
    }
    return d;
  }, H0());
  return (
    (e.includes("fa-brands") || e.includes("fab")) && (f.prefix = "fab"),
    (e.includes("fa-duotone") || e.includes("fad")) && (f.prefix = "fad"),
    !f.prefix &&
      u === et &&
      (Oa.fass || ee.autoFetchSvg) &&
      ((f.prefix = "fass"),
      (f.iconName = Pi(f.prefix, f.iconName) || f.iconName)),
    (f.prefix === "fa" || s === "fa") && (f.prefix = Yo() || "fas"),
    f
  );
}
var rX = (function () {
    function e() {
      hG(this, e), (this.definitions = {});
    }
    return (
      vG(e, [
        {
          key: "add",
          value: function () {
            for (
              var n = this, r = arguments.length, o = new Array(r), a = 0;
              a < r;
              a++
            )
              o[a] = arguments[a];
            var s = o.reduce(this._pullDefinitions, {});
            Object.keys(s).forEach(function (u) {
              (n.definitions[u] = K(K({}, n.definitions[u] || {}), s[u])),
                qv(u, s[u]);
              var f = Ls[Be][u];
              f && qv(f, s[u]), Q5();
            });
          },
        },
        {
          key: "reset",
          value: function () {
            this.definitions = {};
          },
        },
        {
          key: "_pullDefinitions",
          value: function (n, r) {
            var o = r.prefix && r.iconName && r.icon ? { 0: r } : r;
            return (
              Object.keys(o).map(function (a) {
                var s = o[a],
                  u = s.prefix,
                  f = s.iconName,
                  d = s.icon,
                  m = d[2];
                n[u] || (n[u] = {}),
                  m.length > 0 &&
                    m.forEach(function (h) {
                      typeof h == "string" && (n[u][h] = d);
                    }),
                  (n[u][f] = d);
              }),
              n
            );
          },
        },
      ]),
      e
    );
  })(),
  sS = [],
  Ea = {},
  La = {},
  oX = Object.keys(La);
function iX(e, t) {
  var n = t.mixoutsTo;
  return (
    (sS = e),
    (Ea = {}),
    Object.keys(La).forEach(function (r) {
      oX.indexOf(r) === -1 && delete La[r];
    }),
    sS.forEach(function (r) {
      var o = r.mixout ? r.mixout() : {};
      if (
        (Object.keys(o).forEach(function (s) {
          typeof o[s] == "function" && (n[s] = o[s]),
            Rd(o[s]) === "object" &&
              Object.keys(o[s]).forEach(function (u) {
                n[s] || (n[s] = {}), (n[s][u] = o[s][u]);
              });
        }),
        r.hooks)
      ) {
        var a = r.hooks();
        Object.keys(a).forEach(function (s) {
          Ea[s] || (Ea[s] = []), Ea[s].push(a[s]);
        });
      }
      r.provides && r.provides(La);
    }),
    n
  );
}
function Zv(e, t) {
  for (
    var n = arguments.length, r = new Array(n > 2 ? n - 2 : 0), o = 2;
    o < n;
    o++
  )
    r[o - 2] = arguments[o];
  var a = Ea[e] || [];
  return (
    a.forEach(function (s) {
      t = s.apply(null, [t].concat(r));
    }),
    t
  );
}
function Ai(e) {
  for (
    var t = arguments.length, n = new Array(t > 1 ? t - 1 : 0), r = 1;
    r < t;
    r++
  )
    n[r - 1] = arguments[r];
  var o = Ea[e] || [];
  o.forEach(function (a) {
    a.apply(null, n);
  });
}
function no() {
  var e = arguments[0],
    t = Array.prototype.slice.call(arguments, 1);
  return La[e] ? La[e].apply(null, t) : void 0;
}
function Jv(e) {
  e.prefix === "fa" && (e.prefix = "fas");
  var t = e.iconName,
    n = e.prefix || Yo();
  if (t)
    return (t = Pi(n, t) || t), iS(Z5.definitions, n, t) || iS(ir.styles, n, t);
}
var Z5 = new rX(),
  aX = function () {
    (ee.autoReplaceSvg = !1), (ee.observeMutations = !1), Ai("noAuto");
  },
  lX = {
    i2svg: function () {
      var t =
        arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
      return lo
        ? (Ai("beforeI2svg", t), no("pseudoElements2svg", t), no("i2svg", t))
        : Promise.reject("Operation requires a DOM of some kind.");
    },
    watch: function () {
      var t =
          arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {},
        n = t.autoReplaceSvgRoot;
      ee.autoReplaceSvg === !1 && (ee.autoReplaceSvg = !0),
        (ee.observeMutations = !0),
        YG(function () {
          uX({ autoReplaceSvgRoot: n }), Ai("watch", t);
        });
    },
  },
  sX = {
    icon: function (t) {
      if (t === null) return null;
      if (Rd(t) === "object" && t.prefix && t.iconName)
        return {
          prefix: t.prefix,
          iconName: Pi(t.prefix, t.iconName) || t.iconName,
        };
      if (Array.isArray(t) && t.length === 2) {
        var n = t[1].indexOf("fa-") === 0 ? t[1].slice(3) : t[1],
          r = wp(t[0]);
        return { prefix: r, iconName: Pi(r, n) || n };
      }
      if (
        typeof t == "string" &&
        (t.indexOf("".concat(ee.cssPrefix, "-")) > -1 || t.match(CG))
      ) {
        var o = bp(t.split(" "), { skipLookups: !0 });
        return {
          prefix: o.prefix || Yo(),
          iconName: Pi(o.prefix, o.iconName) || o.iconName,
        };
      }
      if (typeof t == "string") {
        var a = Yo();
        return { prefix: a, iconName: Pi(a, t) || t };
      }
    },
  },
  En = {
    noAuto: aX,
    config: ee,
    dom: lX,
    parse: sX,
    library: Z5,
    findIconDefinition: Jv,
    toHtml: eu,
  },
  uX = function () {
    var t = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {},
      n = t.autoReplaceSvgRoot,
      r = n === void 0 ? He : n;
    (Object.keys(ir.styles).length > 0 || ee.autoFetchSvg) &&
      lo &&
      ee.autoReplaceSvg &&
      En.dom.i2svg({ node: r });
  };
function xp(e, t) {
  return (
    Object.defineProperty(e, "abstract", { get: t }),
    Object.defineProperty(e, "html", {
      get: function () {
        return e.abstract.map(function (r) {
          return eu(r);
        });
      },
    }),
    Object.defineProperty(e, "node", {
      get: function () {
        if (lo) {
          var r = He.createElement("div");
          return (r.innerHTML = e.html), r.children;
        }
      },
    }),
    e
  );
}
function cX(e) {
  var t = e.children,
    n = e.main,
    r = e.mask,
    o = e.attributes,
    a = e.styles,
    s = e.transform;
  if (W0(s) && n.found && !r.found) {
    var u = n.width,
      f = n.height,
      d = { x: u / f / 2, y: 0.5 };
    o.style = _p(
      K(
        K({}, a),
        {},
        {
          "transform-origin": ""
            .concat(d.x + s.x / 16, "em ")
            .concat(d.y + s.y / 16, "em"),
        }
      )
    );
  }
  return [{ tag: "svg", attributes: o, children: t }];
}
function fX(e) {
  var t = e.prefix,
    n = e.iconName,
    r = e.children,
    o = e.attributes,
    a = e.symbol,
    s = a === !0 ? "".concat(t, "-").concat(ee.cssPrefix, "-").concat(n) : a;
  return [
    {
      tag: "svg",
      attributes: { style: "display: none;" },
      children: [
        { tag: "symbol", attributes: K(K({}, o), {}, { id: s }), children: r },
      ],
    },
  ];
}
function V0(e) {
  var t = e.icons,
    n = t.main,
    r = t.mask,
    o = e.prefix,
    a = e.iconName,
    s = e.transform,
    u = e.symbol,
    f = e.title,
    d = e.maskId,
    m = e.titleId,
    h = e.extra,
    v = e.watchable,
    b = v === void 0 ? !1 : v,
    O = r.found ? r : n,
    E = O.width,
    $ = O.height,
    _ = o === "fak",
    w = [ee.replacementClass, a ? "".concat(ee.cssPrefix, "-").concat(a) : ""]
      .filter(function (B) {
        return h.classes.indexOf(B) === -1;
      })
      .filter(function (B) {
        return B !== "" || !!B;
      })
      .concat(h.classes)
      .join(" "),
    P = {
      children: [],
      attributes: K(
        K({}, h.attributes),
        {},
        {
          "data-prefix": o,
          "data-icon": a,
          class: w,
          role: h.attributes.role || "img",
          xmlns: "http://www.w3.org/2000/svg",
          viewBox: "0 0 ".concat(E, " ").concat($),
        }
      ),
    },
    k =
      _ && !~h.classes.indexOf("fa-fw")
        ? { width: "".concat((E / $) * 16 * 0.0625, "em") }
        : {};
  b && (P.attributes[zi] = ""),
    f &&
      (P.children.push({
        tag: "title",
        attributes: {
          id: P.attributes["aria-labelledby"] || "title-".concat(m || Ms()),
        },
        children: [f],
      }),
      delete P.attributes.title);
  var I = K(
      K({}, P),
      {},
      {
        prefix: o,
        iconName: a,
        main: n,
        mask: r,
        maskId: d,
        transform: s,
        symbol: u,
        styles: K(K({}, k), h.styles),
      }
    ),
    z =
      r.found && n.found
        ? no("generateAbstractMask", I) || { children: [], attributes: {} }
        : no("generateAbstractIcon", I) || { children: [], attributes: {} },
    A = z.children,
    M = z.attributes;
  return (I.children = A), (I.attributes = M), u ? fX(I) : cX(I);
}
function uS(e) {
  var t = e.content,
    n = e.width,
    r = e.height,
    o = e.transform,
    a = e.title,
    s = e.extra,
    u = e.watchable,
    f = u === void 0 ? !1 : u,
    d = K(
      K(K({}, s.attributes), a ? { title: a } : {}),
      {},
      { class: s.classes.join(" ") }
    );
  f && (d[zi] = "");
  var m = K({}, s.styles);
  W0(o) &&
    ((m.transform = BG({
      transform: o,
      startCentered: !0,
      width: n,
      height: r,
    })),
    (m["-webkit-transform"] = m.transform));
  var h = _p(m);
  h.length > 0 && (d.style = h);
  var v = [];
  return (
    v.push({ tag: "span", attributes: d, children: [t] }),
    a &&
      v.push({ tag: "span", attributes: { class: "sr-only" }, children: [a] }),
    v
  );
}
function dX(e) {
  var t = e.content,
    n = e.title,
    r = e.extra,
    o = K(
      K(K({}, r.attributes), n ? { title: n } : {}),
      {},
      { class: r.classes.join(" ") }
    ),
    a = _p(r.styles);
  a.length > 0 && (o.style = a);
  var s = [];
  return (
    s.push({ tag: "span", attributes: o, children: [t] }),
    n &&
      s.push({ tag: "span", attributes: { class: "sr-only" }, children: [n] }),
    s
  );
}
var Th = ir.styles;
function eg(e) {
  var t = e[0],
    n = e[1],
    r = e.slice(4),
    o = A0(r, 1),
    a = o[0],
    s = null;
  return (
    Array.isArray(a)
      ? (s = {
          tag: "g",
          attributes: { class: "".concat(ee.cssPrefix, "-").concat(Si.GROUP) },
          children: [
            {
              tag: "path",
              attributes: {
                class: "".concat(ee.cssPrefix, "-").concat(Si.SECONDARY),
                fill: "currentColor",
                d: a[0],
              },
            },
            {
              tag: "path",
              attributes: {
                class: "".concat(ee.cssPrefix, "-").concat(Si.PRIMARY),
                fill: "currentColor",
                d: a[1],
              },
            },
          ],
        })
      : (s = { tag: "path", attributes: { fill: "currentColor", d: a } }),
    { found: !0, width: t, height: n, icon: s }
  );
}
var pX = { found: !1, width: 512, height: 512 };
function mX(e, t) {
  !M5 &&
    !ee.showMissingIcons &&
    e &&
    console.error(
      'Icon with name "'.concat(e, '" and prefix "').concat(t, '" is missing.')
    );
}
function tg(e, t) {
  var n = t;
  return (
    t === "fa" && ee.styleDefault !== null && (t = Yo()),
    new Promise(function (r, o) {
      if ((no("missingIconAbstract"), n === "fa")) {
        var a = q5(e) || {};
        (e = a.iconName || e), (t = a.prefix || t);
      }
      if (e && t && Th[t] && Th[t][e]) {
        var s = Th[t][e];
        return r(eg(s));
      }
      mX(e, t),
        r(
          K(
            K({}, pX),
            {},
            {
              icon:
                ee.showMissingIcons && e ? no("missingIconAbstract") || {} : {},
            }
          )
        );
    })
  );
}
var cS = function () {},
  ng =
    ee.measurePerformance && xc && xc.mark && xc.measure
      ? xc
      : { mark: cS, measure: cS },
  Xl = 'FA "6.4.0"',
  hX = function (t) {
    return (
      ng.mark("".concat(Xl, " ").concat(t, " begins")),
      function () {
        return J5(t);
      }
    );
  },
  J5 = function (t) {
    ng.mark("".concat(Xl, " ").concat(t, " ends")),
      ng.measure(
        "".concat(Xl, " ").concat(t),
        "".concat(Xl, " ").concat(t, " begins"),
        "".concat(Xl, " ").concat(t, " ends")
      );
  },
  Y0 = { begin: hX, end: J5 },
  Xc = function () {};
function fS(e) {
  var t = e.getAttribute ? e.getAttribute(zi) : null;
  return typeof t == "string";
}
function vX(e) {
  var t = e.getAttribute ? e.getAttribute(D0) : null,
    n = e.getAttribute ? e.getAttribute(M0) : null;
  return t && n;
}
function gX(e) {
  return (
    e &&
    e.classList &&
    e.classList.contains &&
    e.classList.contains(ee.replacementClass)
  );
}
function yX() {
  if (ee.autoReplaceSvg === !0) return Kc.replace;
  var e = Kc[ee.autoReplaceSvg];
  return e || Kc.replace;
}
function _X(e) {
  return He.createElementNS("http://www.w3.org/2000/svg", e);
}
function wX(e) {
  return He.createElement(e);
}
function eC(e) {
  var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {},
    n = t.ceFn,
    r = n === void 0 ? (e.tag === "svg" ? _X : wX) : n;
  if (typeof e == "string") return He.createTextNode(e);
  var o = r(e.tag);
  Object.keys(e.attributes || []).forEach(function (s) {
    o.setAttribute(s, e.attributes[s]);
  });
  var a = e.children || [];
  return (
    a.forEach(function (s) {
      o.appendChild(eC(s, { ceFn: r }));
    }),
    o
  );
}
function bX(e) {
  var t = " ".concat(e.outerHTML, " ");
  return (t = "".concat(t, "Font Awesome fontawesome.com ")), t;
}
var Kc = {
  replace: function (t) {
    var n = t[0];
    if (n.parentNode)
      if (
        (t[1].forEach(function (o) {
          n.parentNode.insertBefore(eC(o), n);
        }),
        n.getAttribute(zi) === null && ee.keepOriginalSource)
      ) {
        var r = He.createComment(bX(n));
        n.parentNode.replaceChild(r, n);
      } else n.remove();
  },
  nest: function (t) {
    var n = t[0],
      r = t[1];
    if (~F0(n).indexOf(ee.replacementClass)) return Kc.replace(t);
    var o = new RegExp("".concat(ee.cssPrefix, "-.*"));
    if ((delete r[0].attributes.id, r[0].attributes.class)) {
      var a = r[0].attributes.class.split(" ").reduce(
        function (u, f) {
          return (
            f === ee.replacementClass || f.match(o)
              ? u.toSvg.push(f)
              : u.toNode.push(f),
            u
          );
        },
        { toNode: [], toSvg: [] }
      );
      (r[0].attributes.class = a.toSvg.join(" ")),
        a.toNode.length === 0
          ? n.removeAttribute("class")
          : n.setAttribute("class", a.toNode.join(" "));
    }
    var s = r.map(function (u) {
      return eu(u);
    }).join(`
`);
    n.setAttribute(zi, ""), (n.innerHTML = s);
  },
};
function dS(e) {
  e();
}
function tC(e, t) {
  var n = typeof t == "function" ? t : Xc;
  if (e.length === 0) n();
  else {
    var r = dS;
    ee.mutateApproach === OG && (r = Vo.requestAnimationFrame || dS),
      r(function () {
        var o = yX(),
          a = Y0.begin("mutate");
        e.map(o), a(), n();
      });
  }
}
var G0 = !1;
function nC() {
  G0 = !0;
}
function rg() {
  G0 = !1;
}
var Id = null;
function pS(e) {
  if (nS && ee.observeMutations) {
    var t = e.treeCallback,
      n = t === void 0 ? Xc : t,
      r = e.nodeCallback,
      o = r === void 0 ? Xc : r,
      a = e.pseudoElementsCallback,
      s = a === void 0 ? Xc : a,
      u = e.observeMutationsRoot,
      f = u === void 0 ? He : u;
    (Id = new nS(function (d) {
      if (!G0) {
        var m = Yo();
        al(d).forEach(function (h) {
          if (
            (h.type === "childList" &&
              h.addedNodes.length > 0 &&
              !fS(h.addedNodes[0]) &&
              (ee.searchPseudoElements && s(h.target), n(h.target)),
            h.type === "attributes" &&
              h.target.parentNode &&
              ee.searchPseudoElements &&
              s(h.target.parentNode),
            h.type === "attributes" &&
              fS(h.target) &&
              ~IG.indexOf(h.attributeName))
          )
            if (h.attributeName === "class" && vX(h.target)) {
              var v = bp(F0(h.target)),
                b = v.prefix,
                O = v.iconName;
              h.target.setAttribute(D0, b || m),
                O && h.target.setAttribute(M0, O);
            } else gX(h.target) && o(h.target);
        });
      }
    })),
      lo &&
        Id.observe(f, {
          childList: !0,
          attributes: !0,
          characterData: !0,
          subtree: !0,
        });
  }
}
function xX() {
  Id && Id.disconnect();
}
function SX(e) {
  var t = e.getAttribute("style"),
    n = [];
  return (
    t &&
      (n = t.split(";").reduce(function (r, o) {
        var a = o.split(":"),
          s = a[0],
          u = a.slice(1);
        return s && u.length > 0 && (r[s] = u.join(":").trim()), r;
      }, {})),
    n
  );
}
function PX(e) {
  var t = e.getAttribute("data-prefix"),
    n = e.getAttribute("data-icon"),
    r = e.innerText !== void 0 ? e.innerText.trim() : "",
    o = bp(F0(e));
  return (
    o.prefix || (o.prefix = Yo()),
    t && n && ((o.prefix = t), (o.iconName = n)),
    (o.iconName && o.prefix) ||
      (o.prefix &&
        r.length > 0 &&
        (o.iconName =
          tX(o.prefix, e.innerText) || U0(o.prefix, Qv(e.innerText))),
      !o.iconName &&
        ee.autoFetchSvg &&
        e.firstChild &&
        e.firstChild.nodeType === Node.TEXT_NODE &&
        (o.iconName = e.firstChild.data)),
    o
  );
}
function OX(e) {
  var t = al(e.attributes).reduce(function (o, a) {
      return (
        o.name !== "class" && o.name !== "style" && (o[a.name] = a.value), o
      );
    }, {}),
    n = e.getAttribute("title"),
    r = e.getAttribute("data-fa-title-id");
  return (
    ee.autoA11y &&
      (n
        ? (t["aria-labelledby"] = ""
            .concat(ee.replacementClass, "-title-")
            .concat(r || Ms()))
        : ((t["aria-hidden"] = "true"), (t.focusable = "false"))),
    t
  );
}
function EX() {
  return {
    iconName: null,
    title: null,
    titleId: null,
    prefix: null,
    transform: xr,
    symbol: !1,
    mask: { iconName: null, prefix: null, rest: [] },
    maskId: null,
    extra: { classes: [], styles: {}, attributes: {} },
  };
}
function mS(e) {
  var t =
      arguments.length > 1 && arguments[1] !== void 0
        ? arguments[1]
        : { styleParser: !0 },
    n = PX(e),
    r = n.iconName,
    o = n.prefix,
    a = n.rest,
    s = OX(e),
    u = Zv("parseNodeAttributes", {}, e),
    f = t.styleParser ? SX(e) : [];
  return K(
    {
      iconName: r,
      title: e.getAttribute("title"),
      titleId: e.getAttribute("data-fa-title-id"),
      prefix: o,
      transform: xr,
      mask: { iconName: null, prefix: null, rest: [] },
      maskId: null,
      symbol: !1,
      extra: { classes: a, styles: f, attributes: s },
    },
    u
  );
}
var $X = ir.styles;
function rC(e) {
  var t = ee.autoReplaceSvg === "nest" ? mS(e, { styleParser: !1 }) : mS(e);
  return ~t.extra.classes.indexOf(j5)
    ? no("generateLayersText", e, t)
    : no("generateSvgReplacementMutation", e, t);
}
var Go = new Set();
j0.map(function (e) {
  Go.add("fa-".concat(e));
});
Object.keys(zs[Be]).map(Go.add.bind(Go));
Object.keys(zs[et]).map(Go.add.bind(Go));
Go = Zs(Go);
function hS(e) {
  var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : null;
  if (!lo) return Promise.resolve();
  var n = He.documentElement.classList,
    r = function (h) {
      return n.add("".concat(rS, "-").concat(h));
    },
    o = function (h) {
      return n.remove("".concat(rS, "-").concat(h));
    },
    a = ee.autoFetchSvg
      ? Go
      : j0
          .map(function (m) {
            return "fa-".concat(m);
          })
          .concat(Object.keys($X));
  a.includes("fa") || a.push("fa");
  var s = [".".concat(j5, ":not([").concat(zi, "])")]
    .concat(
      a.map(function (m) {
        return ".".concat(m, ":not([").concat(zi, "])");
      })
    )
    .join(", ");
  if (s.length === 0) return Promise.resolve();
  var u = [];
  try {
    u = al(e.querySelectorAll(s));
  } catch {}
  if (u.length > 0) r("pending"), o("complete");
  else return Promise.resolve();
  var f = Y0.begin("onTree"),
    d = u.reduce(function (m, h) {
      try {
        var v = rC(h);
        v && m.push(v);
      } catch (b) {
        M5 || (b.name === "MissingIcon" && console.error(b));
      }
      return m;
    }, []);
  return new Promise(function (m, h) {
    Promise.all(d)
      .then(function (v) {
        tC(v, function () {
          r("active"),
            r("complete"),
            o("pending"),
            typeof t == "function" && t(),
            f(),
            m();
        });
      })
      .catch(function (v) {
        f(), h(v);
      });
  });
}
function CX(e) {
  var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : null;
  rC(e).then(function (n) {
    n && tC([n], t);
  });
}
function kX(e) {
  return function (t) {
    var n = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {},
      r = (t || {}).icon ? t : Jv(t || {}),
      o = n.mask;
    return (
      o && (o = (o || {}).icon ? o : Jv(o || {})),
      e(r, K(K({}, n), {}, { mask: o }))
    );
  };
}
var RX = function (t) {
    var n = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {},
      r = n.transform,
      o = r === void 0 ? xr : r,
      a = n.symbol,
      s = a === void 0 ? !1 : a,
      u = n.mask,
      f = u === void 0 ? null : u,
      d = n.maskId,
      m = d === void 0 ? null : d,
      h = n.title,
      v = h === void 0 ? null : h,
      b = n.titleId,
      O = b === void 0 ? null : b,
      E = n.classes,
      $ = E === void 0 ? [] : E,
      _ = n.attributes,
      w = _ === void 0 ? {} : _,
      P = n.styles,
      k = P === void 0 ? {} : P;
    if (t) {
      var I = t.prefix,
        z = t.iconName,
        A = t.icon;
      return xp(K({ type: "icon" }, t), function () {
        return (
          Ai("beforeDOMElementCreation", { iconDefinition: t, params: n }),
          ee.autoA11y &&
            (v
              ? (w["aria-labelledby"] = ""
                  .concat(ee.replacementClass, "-title-")
                  .concat(O || Ms()))
              : ((w["aria-hidden"] = "true"), (w.focusable = "false"))),
          V0({
            icons: {
              main: eg(A),
              mask: f
                ? eg(f.icon)
                : { found: !1, width: null, height: null, icon: {} },
            },
            prefix: I,
            iconName: z,
            transform: K(K({}, xr), o),
            symbol: s,
            title: v,
            maskId: m,
            titleId: O,
            extra: { attributes: w, styles: k, classes: $ },
          })
        );
      });
    }
  },
  NX = {
    mixout: function () {
      return { icon: kX(RX) };
    },
    hooks: function () {
      return {
        mutationObserverCallbacks: function (n) {
          return (n.treeCallback = hS), (n.nodeCallback = CX), n;
        },
      };
    },
    provides: function (t) {
      (t.i2svg = function (n) {
        var r = n.node,
          o = r === void 0 ? He : r,
          a = n.callback,
          s = a === void 0 ? function () {} : a;
        return hS(o, s);
      }),
        (t.generateSvgReplacementMutation = function (n, r) {
          var o = r.iconName,
            a = r.title,
            s = r.titleId,
            u = r.prefix,
            f = r.transform,
            d = r.symbol,
            m = r.mask,
            h = r.maskId,
            v = r.extra;
          return new Promise(function (b, O) {
            Promise.all([
              tg(o, u),
              m.iconName
                ? tg(m.iconName, m.prefix)
                : Promise.resolve({
                    found: !1,
                    width: 512,
                    height: 512,
                    icon: {},
                  }),
            ])
              .then(function (E) {
                var $ = A0(E, 2),
                  _ = $[0],
                  w = $[1];
                b([
                  n,
                  V0({
                    icons: { main: _, mask: w },
                    prefix: u,
                    iconName: o,
                    transform: f,
                    symbol: d,
                    maskId: h,
                    title: a,
                    titleId: s,
                    extra: v,
                    watchable: !0,
                  }),
                ]);
              })
              .catch(O);
          });
        }),
        (t.generateAbstractIcon = function (n) {
          var r = n.children,
            o = n.attributes,
            a = n.main,
            s = n.transform,
            u = n.styles,
            f = _p(u);
          f.length > 0 && (o.style = f);
          var d;
          return (
            W0(s) &&
              (d = no("generateAbstractTransformGrouping", {
                main: a,
                transform: s,
                containerWidth: a.width,
                iconWidth: a.width,
              })),
            r.push(d || a.icon),
            { children: r, attributes: o }
          );
        });
    },
  },
  IX = {
    mixout: function () {
      return {
        layer: function (n) {
          var r =
              arguments.length > 1 && arguments[1] !== void 0
                ? arguments[1]
                : {},
            o = r.classes,
            a = o === void 0 ? [] : o;
          return xp({ type: "layer" }, function () {
            Ai("beforeDOMElementCreation", { assembler: n, params: r });
            var s = [];
            return (
              n(function (u) {
                Array.isArray(u)
                  ? u.map(function (f) {
                      s = s.concat(f.abstract);
                    })
                  : (s = s.concat(u.abstract));
              }),
              [
                {
                  tag: "span",
                  attributes: {
                    class: ["".concat(ee.cssPrefix, "-layers")]
                      .concat(Zs(a))
                      .join(" "),
                  },
                  children: s,
                },
              ]
            );
          });
        },
      };
    },
  },
  TX = {
    mixout: function () {
      return {
        counter: function (n) {
          var r =
              arguments.length > 1 && arguments[1] !== void 0
                ? arguments[1]
                : {},
            o = r.title,
            a = o === void 0 ? null : o,
            s = r.classes,
            u = s === void 0 ? [] : s,
            f = r.attributes,
            d = f === void 0 ? {} : f,
            m = r.styles,
            h = m === void 0 ? {} : m;
          return xp({ type: "counter", content: n }, function () {
            return (
              Ai("beforeDOMElementCreation", { content: n, params: r }),
              dX({
                content: n.toString(),
                title: a,
                extra: {
                  attributes: d,
                  styles: h,
                  classes: ["".concat(ee.cssPrefix, "-layers-counter")].concat(
                    Zs(u)
                  ),
                },
              })
            );
          });
        },
      };
    },
  },
  zX = {
    mixout: function () {
      return {
        text: function (n) {
          var r =
              arguments.length > 1 && arguments[1] !== void 0
                ? arguments[1]
                : {},
            o = r.transform,
            a = o === void 0 ? xr : o,
            s = r.title,
            u = s === void 0 ? null : s,
            f = r.classes,
            d = f === void 0 ? [] : f,
            m = r.attributes,
            h = m === void 0 ? {} : m,
            v = r.styles,
            b = v === void 0 ? {} : v;
          return xp({ type: "text", content: n }, function () {
            return (
              Ai("beforeDOMElementCreation", { content: n, params: r }),
              uS({
                content: n,
                transform: K(K({}, xr), a),
                title: u,
                extra: {
                  attributes: h,
                  styles: b,
                  classes: ["".concat(ee.cssPrefix, "-layers-text")].concat(
                    Zs(d)
                  ),
                },
              })
            );
          });
        },
      };
    },
    provides: function (t) {
      t.generateLayersText = function (n, r) {
        var o = r.title,
          a = r.transform,
          s = r.extra,
          u = null,
          f = null;
        if (A5) {
          var d = parseInt(getComputedStyle(n).fontSize, 10),
            m = n.getBoundingClientRect();
          (u = m.width / d), (f = m.height / d);
        }
        return (
          ee.autoA11y && !o && (s.attributes["aria-hidden"] = "true"),
          Promise.resolve([
            n,
            uS({
              content: n.innerHTML,
              width: u,
              height: f,
              transform: a,
              title: o,
              extra: s,
              watchable: !0,
            }),
          ])
        );
      };
    },
  },
  AX = new RegExp('"', "ug"),
  vS = [1105920, 1112319];
function LX(e) {
  var t = e.replace(AX, ""),
    n = KG(t, 0),
    r = n >= vS[0] && n <= vS[1],
    o = t.length === 2 ? t[0] === t[1] : !1;
  return { value: Qv(o ? t[0] : t), isSecondary: r || o };
}
function gS(e, t) {
  var n = "".concat(PG).concat(t.replace(":", "-"));
  return new Promise(function (r, o) {
    if (e.getAttribute(n) !== null) return r();
    var a = al(e.children),
      s = a.filter(function (A) {
        return A.getAttribute(Kv) === t;
      })[0],
      u = Vo.getComputedStyle(e, t),
      f = u.getPropertyValue("font-family").match(kG),
      d = u.getPropertyValue("font-weight"),
      m = u.getPropertyValue("content");
    if (s && !f) return e.removeChild(s), r();
    if (f && m !== "none" && m !== "") {
      var h = u.getPropertyValue("content"),
        v = ~["Sharp"].indexOf(f[2]) ? et : Be,
        b = ~[
          "Solid",
          "Regular",
          "Light",
          "Thin",
          "Duotone",
          "Brands",
          "Kit",
        ].indexOf(f[2])
          ? As[v][f[2].toLowerCase()]
          : RG[v][d],
        O = LX(h),
        E = O.value,
        $ = O.isSecondary,
        _ = f[0].startsWith("FontAwesome"),
        w = U0(b, E),
        P = w;
      if (_) {
        var k = nX(E);
        k.iconName && k.prefix && ((w = k.iconName), (b = k.prefix));
      }
      if (
        w &&
        !$ &&
        (!s || s.getAttribute(D0) !== b || s.getAttribute(M0) !== P)
      ) {
        e.setAttribute(n, P), s && e.removeChild(s);
        var I = EX(),
          z = I.extra;
        (z.attributes[Kv] = t),
          tg(w, b)
            .then(function (A) {
              var M = V0(
                  K(
                    K({}, I),
                    {},
                    {
                      icons: { main: A, mask: H0() },
                      prefix: b,
                      iconName: P,
                      extra: z,
                      watchable: !0,
                    }
                  )
                ),
                B = He.createElement("svg");
              t === "::before"
                ? e.insertBefore(B, e.firstChild)
                : e.appendChild(B),
                (B.outerHTML = M.map(function (H) {
                  return eu(H);
                }).join(`
`)),
                e.removeAttribute(n),
                r();
            })
            .catch(o);
      } else r();
    } else r();
  });
}
function DX(e) {
  return Promise.all([gS(e, "::before"), gS(e, "::after")]);
}
function MX(e) {
  return (
    e.parentNode !== document.head &&
    !~EG.indexOf(e.tagName.toUpperCase()) &&
    !e.getAttribute(Kv) &&
    (!e.parentNode || e.parentNode.tagName !== "svg")
  );
}
function yS(e) {
  if (lo)
    return new Promise(function (t, n) {
      var r = al(e.querySelectorAll("*")).filter(MX).map(DX),
        o = Y0.begin("searchPseudoElements");
      nC(),
        Promise.all(r)
          .then(function () {
            o(), rg(), t();
          })
          .catch(function () {
            o(), rg(), n();
          });
    });
}
var jX = {
    hooks: function () {
      return {
        mutationObserverCallbacks: function (n) {
          return (n.pseudoElementsCallback = yS), n;
        },
      };
    },
    provides: function (t) {
      t.pseudoElements2svg = function (n) {
        var r = n.node,
          o = r === void 0 ? He : r;
        ee.searchPseudoElements && yS(o);
      };
    },
  },
  _S = !1,
  FX = {
    mixout: function () {
      return {
        dom: {
          unwatch: function () {
            nC(), (_S = !0);
          },
        },
      };
    },
    hooks: function () {
      return {
        bootstrap: function () {
          pS(Zv("mutationObserverCallbacks", {}));
        },
        noAuto: function () {
          xX();
        },
        watch: function (n) {
          var r = n.observeMutationsRoot;
          _S
            ? rg()
            : pS(Zv("mutationObserverCallbacks", { observeMutationsRoot: r }));
        },
      };
    },
  },
  wS = function (t) {
    var n = { size: 16, x: 0, y: 0, flipX: !1, flipY: !1, rotate: 0 };
    return t
      .toLowerCase()
      .split(" ")
      .reduce(function (r, o) {
        var a = o.toLowerCase().split("-"),
          s = a[0],
          u = a.slice(1).join("-");
        if (s && u === "h") return (r.flipX = !0), r;
        if (s && u === "v") return (r.flipY = !0), r;
        if (((u = parseFloat(u)), isNaN(u))) return r;
        switch (s) {
          case "grow":
            r.size = r.size + u;
            break;
          case "shrink":
            r.size = r.size - u;
            break;
          case "left":
            r.x = r.x - u;
            break;
          case "right":
            r.x = r.x + u;
            break;
          case "up":
            r.y = r.y - u;
            break;
          case "down":
            r.y = r.y + u;
            break;
          case "rotate":
            r.rotate = r.rotate + u;
            break;
        }
        return r;
      }, n);
  },
  WX = {
    mixout: function () {
      return {
        parse: {
          transform: function (n) {
            return wS(n);
          },
        },
      };
    },
    hooks: function () {
      return {
        parseNodeAttributes: function (n, r) {
          var o = r.getAttribute("data-fa-transform");
          return o && (n.transform = wS(o)), n;
        },
      };
    },
    provides: function (t) {
      t.generateAbstractTransformGrouping = function (n) {
        var r = n.main,
          o = n.transform,
          a = n.containerWidth,
          s = n.iconWidth,
          u = { transform: "translate(".concat(a / 2, " 256)") },
          f = "translate(".concat(o.x * 32, ", ").concat(o.y * 32, ") "),
          d = "scale("
            .concat((o.size / 16) * (o.flipX ? -1 : 1), ", ")
            .concat((o.size / 16) * (o.flipY ? -1 : 1), ") "),
          m = "rotate(".concat(o.rotate, " 0 0)"),
          h = { transform: "".concat(f, " ").concat(d, " ").concat(m) },
          v = { transform: "translate(".concat((s / 2) * -1, " -256)") },
          b = { outer: u, inner: h, path: v };
        return {
          tag: "g",
          attributes: K({}, b.outer),
          children: [
            {
              tag: "g",
              attributes: K({}, b.inner),
              children: [
                {
                  tag: r.icon.tag,
                  children: r.icon.children,
                  attributes: K(K({}, r.icon.attributes), b.path),
                },
              ],
            },
          ],
        };
      };
    },
  },
  zh = { x: 0, y: 0, width: "100%", height: "100%" };
function bS(e) {
  var t = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : !0;
  return (
    e.attributes && (e.attributes.fill || t) && (e.attributes.fill = "black"), e
  );
}
function BX(e) {
  return e.tag === "g" ? e.children : [e];
}
var UX = {
    hooks: function () {
      return {
        parseNodeAttributes: function (n, r) {
          var o = r.getAttribute("data-fa-mask"),
            a = o
              ? bp(
                  o.split(" ").map(function (s) {
                    return s.trim();
                  })
                )
              : H0();
          return (
            a.prefix || (a.prefix = Yo()),
            (n.mask = a),
            (n.maskId = r.getAttribute("data-fa-mask-id")),
            n
          );
        },
      };
    },
    provides: function (t) {
      t.generateAbstractMask = function (n) {
        var r = n.children,
          o = n.attributes,
          a = n.main,
          s = n.mask,
          u = n.maskId,
          f = n.transform,
          d = a.width,
          m = a.icon,
          h = s.width,
          v = s.icon,
          b = WG({ transform: f, containerWidth: h, iconWidth: d }),
          O = { tag: "rect", attributes: K(K({}, zh), {}, { fill: "white" }) },
          E = m.children ? { children: m.children.map(bS) } : {},
          $ = {
            tag: "g",
            attributes: K({}, b.inner),
            children: [
              bS(
                K({ tag: m.tag, attributes: K(K({}, m.attributes), b.path) }, E)
              ),
            ],
          },
          _ = { tag: "g", attributes: K({}, b.outer), children: [$] },
          w = "mask-".concat(u || Ms()),
          P = "clip-".concat(u || Ms()),
          k = {
            tag: "mask",
            attributes: K(
              K({}, zh),
              {},
              {
                id: w,
                maskUnits: "userSpaceOnUse",
                maskContentUnits: "userSpaceOnUse",
              }
            ),
            children: [O, _],
          },
          I = {
            tag: "defs",
            children: [
              { tag: "clipPath", attributes: { id: P }, children: BX(v) },
              k,
            ],
          };
        return (
          r.push(I, {
            tag: "rect",
            attributes: K(
              {
                fill: "currentColor",
                "clip-path": "url(#".concat(P, ")"),
                mask: "url(#".concat(w, ")"),
              },
              zh
            ),
          }),
          { children: r, attributes: o }
        );
      };
    },
  },
  HX = {
    provides: function (t) {
      var n = !1;
      Vo.matchMedia &&
        (n = Vo.matchMedia("(prefers-reduced-motion: reduce)").matches),
        (t.missingIconAbstract = function () {
          var r = [],
            o = { fill: "currentColor" },
            a = { attributeType: "XML", repeatCount: "indefinite", dur: "2s" };
          r.push({
            tag: "path",
            attributes: K(
              K({}, o),
              {},
              {
                d: "M156.5,447.7l-12.6,29.5c-18.7-9.5-35.9-21.2-51.5-34.9l22.7-22.7C127.6,430.5,141.5,440,156.5,447.7z M40.6,272H8.5 c1.4,21.2,5.4,41.7,11.7,61.1L50,321.2C45.1,305.5,41.8,289,40.6,272z M40.6,240c1.4-18.8,5.2-37,11.1-54.1l-29.5-12.6 C14.7,194.3,10,216.7,8.5,240H40.6z M64.3,156.5c7.8-14.9,17.2-28.8,28.1-41.5L69.7,92.3c-13.7,15.6-25.5,32.8-34.9,51.5 L64.3,156.5z M397,419.6c-13.9,12-29.4,22.3-46.1,30.4l11.9,29.8c20.7-9.9,39.8-22.6,56.9-37.6L397,419.6z M115,92.4 c13.9-12,29.4-22.3,46.1-30.4l-11.9-29.8c-20.7,9.9-39.8,22.6-56.8,37.6L115,92.4z M447.7,355.5c-7.8,14.9-17.2,28.8-28.1,41.5 l22.7,22.7c13.7-15.6,25.5-32.9,34.9-51.5L447.7,355.5z M471.4,272c-1.4,18.8-5.2,37-11.1,54.1l29.5,12.6 c7.5-21.1,12.2-43.5,13.6-66.8H471.4z M321.2,462c-15.7,5-32.2,8.2-49.2,9.4v32.1c21.2-1.4,41.7-5.4,61.1-11.7L321.2,462z M240,471.4c-18.8-1.4-37-5.2-54.1-11.1l-12.6,29.5c21.1,7.5,43.5,12.2,66.8,13.6V471.4z M462,190.8c5,15.7,8.2,32.2,9.4,49.2h32.1 c-1.4-21.2-5.4-41.7-11.7-61.1L462,190.8z M92.4,397c-12-13.9-22.3-29.4-30.4-46.1l-29.8,11.9c9.9,20.7,22.6,39.8,37.6,56.9 L92.4,397z M272,40.6c18.8,1.4,36.9,5.2,54.1,11.1l12.6-29.5C317.7,14.7,295.3,10,272,8.5V40.6z M190.8,50 c15.7-5,32.2-8.2,49.2-9.4V8.5c-21.2,1.4-41.7,5.4-61.1,11.7L190.8,50z M442.3,92.3L419.6,115c12,13.9,22.3,29.4,30.5,46.1 l29.8-11.9C470,128.5,457.3,109.4,442.3,92.3z M397,92.4l22.7-22.7c-15.6-13.7-32.8-25.5-51.5-34.9l-12.6,29.5 C370.4,72.1,384.4,81.5,397,92.4z",
              }
            ),
          });
          var s = K(K({}, a), {}, { attributeName: "opacity" }),
            u = {
              tag: "circle",
              attributes: K(K({}, o), {}, { cx: "256", cy: "364", r: "28" }),
              children: [],
            };
          return (
            n ||
              u.children.push(
                {
                  tag: "animate",
                  attributes: K(
                    K({}, a),
                    {},
                    { attributeName: "r", values: "28;14;28;28;14;28;" }
                  ),
                },
                {
                  tag: "animate",
                  attributes: K(K({}, s), {}, { values: "1;0;1;1;0;1;" }),
                }
              ),
            r.push(u),
            r.push({
              tag: "path",
              attributes: K(
                K({}, o),
                {},
                {
                  opacity: "1",
                  d: "M263.7,312h-16c-6.6,0-12-5.4-12-12c0-71,77.4-63.9,77.4-107.8c0-20-17.8-40.2-57.4-40.2c-29.1,0-44.3,9.6-59.2,28.7 c-3.9,5-11.1,6-16.2,2.4l-13.1-9.2c-5.6-3.9-6.9-11.8-2.6-17.2c21.2-27.2,46.4-44.7,91.2-44.7c52.3,0,97.4,29.8,97.4,80.2 c0,67.6-77.4,63.5-77.4,107.8C275.7,306.6,270.3,312,263.7,312z",
                }
              ),
              children: n
                ? []
                : [
                    {
                      tag: "animate",
                      attributes: K(K({}, s), {}, { values: "1;0;0;0;0;1;" }),
                    },
                  ],
            }),
            n ||
              r.push({
                tag: "path",
                attributes: K(
                  K({}, o),
                  {},
                  {
                    opacity: "0",
                    d: "M232.5,134.5l7,168c0.3,6.4,5.6,11.5,12,11.5h9c6.4,0,11.7-5.1,12-11.5l7-168c0.3-6.8-5.2-12.5-12-12.5h-23 C237.7,122,232.2,127.7,232.5,134.5z",
                  }
                ),
                children: [
                  {
                    tag: "animate",
                    attributes: K(K({}, s), {}, { values: "0;0;1;1;0;0;" }),
                  },
                ],
              }),
            { tag: "g", attributes: { class: "missing" }, children: r }
          );
        });
    },
  },
  VX = {
    hooks: function () {
      return {
        parseNodeAttributes: function (n, r) {
          var o = r.getAttribute("data-fa-symbol"),
            a = o === null ? !1 : o === "" ? !0 : o;
          return (n.symbol = a), n;
        },
      };
    },
  },
  YX = [HG, NX, IX, TX, zX, jX, FX, WX, UX, HX, VX];
iX(YX, { mixoutsTo: En });
En.noAuto;
En.config;
var SK = En.library;
En.dom;
var og = En.parse;
En.findIconDefinition;
En.toHtml;
var GX = En.icon;
En.layer;
En.text;
En.counter;
function xS(e, t) {
  var n = Object.keys(e);
  if (Object.getOwnPropertySymbols) {
    var r = Object.getOwnPropertySymbols(e);
    t &&
      (r = r.filter(function (o) {
        return Object.getOwnPropertyDescriptor(e, o).enumerable;
      })),
      n.push.apply(n, r);
  }
  return n;
}
function No(e) {
  for (var t = 1; t < arguments.length; t++) {
    var n = arguments[t] != null ? arguments[t] : {};
    t % 2
      ? xS(Object(n), !0).forEach(function (r) {
          $a(e, r, n[r]);
        })
      : Object.getOwnPropertyDescriptors
      ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(n))
      : xS(Object(n)).forEach(function (r) {
          Object.defineProperty(e, r, Object.getOwnPropertyDescriptor(n, r));
        });
  }
  return e;
}
function Td(e) {
  "@babel/helpers - typeof";
  return (
    (Td =
      typeof Symbol == "function" && typeof Symbol.iterator == "symbol"
        ? function (t) {
            return typeof t;
          }
        : function (t) {
            return t &&
              typeof Symbol == "function" &&
              t.constructor === Symbol &&
              t !== Symbol.prototype
              ? "symbol"
              : typeof t;
          }),
    Td(e)
  );
}
function $a(e, t, n) {
  return (
    t in e
      ? Object.defineProperty(e, t, {
          value: n,
          enumerable: !0,
          configurable: !0,
          writable: !0,
        })
      : (e[t] = n),
    e
  );
}
function XX(e, t) {
  if (e == null) return {};
  var n = {},
    r = Object.keys(e),
    o,
    a;
  for (a = 0; a < r.length; a++)
    (o = r[a]), !(t.indexOf(o) >= 0) && (n[o] = e[o]);
  return n;
}
function KX(e, t) {
  if (e == null) return {};
  var n = XX(e, t),
    r,
    o;
  if (Object.getOwnPropertySymbols) {
    var a = Object.getOwnPropertySymbols(e);
    for (o = 0; o < a.length; o++)
      (r = a[o]),
        !(t.indexOf(r) >= 0) &&
          Object.prototype.propertyIsEnumerable.call(e, r) &&
          (n[r] = e[r]);
  }
  return n;
}
function ig(e) {
  return QX(e) || qX(e) || ZX(e) || JX();
}
function QX(e) {
  if (Array.isArray(e)) return ag(e);
}
function qX(e) {
  if (
    (typeof Symbol < "u" && e[Symbol.iterator] != null) ||
    e["@@iterator"] != null
  )
    return Array.from(e);
}
function ZX(e, t) {
  if (e) {
    if (typeof e == "string") return ag(e, t);
    var n = Object.prototype.toString.call(e).slice(8, -1);
    if (
      (n === "Object" && e.constructor && (n = e.constructor.name),
      n === "Map" || n === "Set")
    )
      return Array.from(e);
    if (n === "Arguments" || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(n))
      return ag(e, t);
  }
}
function ag(e, t) {
  (t == null || t > e.length) && (t = e.length);
  for (var n = 0, r = new Array(t); n < t; n++) r[n] = e[n];
  return r;
}
function JX() {
  throw new TypeError(`Invalid attempt to spread non-iterable instance.
In order to be iterable, non-array objects must have a [Symbol.iterator]() method.`);
}
function eK(e) {
  var t,
    n = e.beat,
    r = e.fade,
    o = e.beatFade,
    a = e.bounce,
    s = e.shake,
    u = e.flash,
    f = e.spin,
    d = e.spinPulse,
    m = e.spinReverse,
    h = e.pulse,
    v = e.fixedWidth,
    b = e.inverse,
    O = e.border,
    E = e.listItem,
    $ = e.flip,
    _ = e.size,
    w = e.rotation,
    P = e.pull,
    k =
      ((t = {
        "fa-beat": n,
        "fa-fade": r,
        "fa-beat-fade": o,
        "fa-bounce": a,
        "fa-shake": s,
        "fa-flash": u,
        "fa-spin": f,
        "fa-spin-reverse": m,
        "fa-spin-pulse": d,
        "fa-pulse": h,
        "fa-fw": v,
        "fa-inverse": b,
        "fa-border": O,
        "fa-li": E,
        "fa-flip": $ === !0,
        "fa-flip-horizontal": $ === "horizontal" || $ === "both",
        "fa-flip-vertical": $ === "vertical" || $ === "both",
      }),
      $a(t, "fa-".concat(_), typeof _ < "u" && _ !== null),
      $a(t, "fa-rotate-".concat(w), typeof w < "u" && w !== null && w !== 0),
      $a(t, "fa-pull-".concat(P), typeof P < "u" && P !== null),
      $a(t, "fa-swap-opacity", e.swapOpacity),
      t);
  return Object.keys(k)
    .map(function (I) {
      return k[I] ? I : null;
    })
    .filter(function (I) {
      return I;
    });
}
function tK(e) {
  return (e = e - 0), e === e;
}
function oC(e) {
  return tK(e)
    ? e
    : ((e = e.replace(/[\-_\s]+(.)?/g, function (t, n) {
        return n ? n.toUpperCase() : "";
      })),
      e.substr(0, 1).toLowerCase() + e.substr(1));
}
var nK = ["style"];
function rK(e) {
  return e.charAt(0).toUpperCase() + e.slice(1);
}
function oK(e) {
  return e
    .split(";")
    .map(function (t) {
      return t.trim();
    })
    .filter(function (t) {
      return t;
    })
    .reduce(function (t, n) {
      var r = n.indexOf(":"),
        o = oC(n.slice(0, r)),
        a = n.slice(r + 1).trim();
      return o.startsWith("webkit") ? (t[rK(o)] = a) : (t[o] = a), t;
    }, {});
}
function iC(e, t) {
  var n = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {};
  if (typeof t == "string") return t;
  var r = (t.children || []).map(function (f) {
      return iC(e, f);
    }),
    o = Object.keys(t.attributes || {}).reduce(
      function (f, d) {
        var m = t.attributes[d];
        switch (d) {
          case "class":
            (f.attrs.className = m), delete t.attributes.class;
            break;
          case "style":
            f.attrs.style = oK(m);
            break;
          default:
            d.indexOf("aria-") === 0 || d.indexOf("data-") === 0
              ? (f.attrs[d.toLowerCase()] = m)
              : (f.attrs[oC(d)] = m);
        }
        return f;
      },
      { attrs: {} }
    ),
    a = n.style,
    s = a === void 0 ? {} : a,
    u = KX(n, nK);
  return (
    (o.attrs.style = No(No({}, o.attrs.style), s)),
    e.apply(void 0, [t.tag, No(No({}, o.attrs), u)].concat(ig(r)))
  );
}
var aC = !1;
try {
  aC = !0;
} catch {}
function iK() {
  if (!aC && console && typeof console.error == "function") {
    var e;
    (e = console).error.apply(e, arguments);
  }
}
function SS(e) {
  if (e && Td(e) === "object" && e.prefix && e.iconName && e.icon) return e;
  if (og.icon) return og.icon(e);
  if (e === null) return null;
  if (e && Td(e) === "object" && e.prefix && e.iconName) return e;
  if (Array.isArray(e) && e.length === 2)
    return { prefix: e[0], iconName: e[1] };
  if (typeof e == "string") return { prefix: "fas", iconName: e };
}
function Ah(e, t) {
  return (Array.isArray(t) && t.length > 0) || (!Array.isArray(t) && t)
    ? $a({}, e, t)
    : {};
}
var Sp = R.forwardRef(function (e, t) {
  var n = e.icon,
    r = e.mask,
    o = e.symbol,
    a = e.className,
    s = e.title,
    u = e.titleId,
    f = e.maskId,
    d = SS(n),
    m = Ah("classes", [].concat(ig(eK(e)), ig(a.split(" ")))),
    h = Ah(
      "transform",
      typeof e.transform == "string" ? og.transform(e.transform) : e.transform
    ),
    v = Ah("mask", SS(r)),
    b = GX(
      d,
      No(
        No(No(No({}, m), h), v),
        {},
        { symbol: o, title: s, titleId: u, maskId: f }
      )
    );
  if (!b) return iK("Could not find icon", d), null;
  var O = b.abstract,
    E = { ref: t };
  return (
    Object.keys(e).forEach(function ($) {
      Sp.defaultProps.hasOwnProperty($) || (E[$] = e[$]);
    }),
    aK(O[0], E)
  );
});
Sp.displayName = "FontAwesomeIcon";
Sp.propTypes = {
  beat: ge.bool,
  border: ge.bool,
  beatFade: ge.bool,
  bounce: ge.bool,
  className: ge.string,
  fade: ge.bool,
  flash: ge.bool,
  mask: ge.oneOfType([ge.object, ge.array, ge.string]),
  maskId: ge.string,
  fixedWidth: ge.bool,
  inverse: ge.bool,
  flip: ge.oneOf([!0, !1, "horizontal", "vertical", "both"]),
  icon: ge.oneOfType([ge.object, ge.array, ge.string]),
  listItem: ge.bool,
  pull: ge.oneOf(["right", "left"]),
  pulse: ge.bool,
  rotation: ge.oneOf([0, 90, 180, 270]),
  shake: ge.bool,
  size: ge.oneOf([
    "2xs",
    "xs",
    "sm",
    "lg",
    "xl",
    "2xl",
    "1x",
    "2x",
    "3x",
    "4x",
    "5x",
    "6x",
    "7x",
    "8x",
    "9x",
    "10x",
  ]),
  spin: ge.bool,
  spinPulse: ge.bool,
  spinReverse: ge.bool,
  symbol: ge.oneOfType([ge.bool, ge.string]),
  title: ge.string,
  titleId: ge.string,
  transform: ge.oneOfType([ge.string, ge.object]),
  swapOpacity: ge.bool,
};
Sp.defaultProps = {
  border: !1,
  className: "",
  mask: null,
  maskId: null,
  fixedWidth: !1,
  inverse: !1,
  flip: !1,
  icon: null,
  listItem: !1,
  pull: null,
  pulse: !1,
  rotation: null,
  size: null,
  spin: !1,
  spinPulse: !1,
  spinReverse: !1,
  beat: !1,
  fade: !1,
  beatFade: !1,
  bounce: !1,
  shake: !1,
  symbol: !1,
  title: "",
  titleId: null,
  transform: null,
  swapOpacity: !1,
};
var aK = iC.bind(null, R.createElement),
  PK = {
    prefix: "fas",
    iconName: "info",
    icon: [
      192,
      512,
      [],
      "f129",
      "M48 80a48 48 0 1 1 96 0A48 48 0 1 1 48 80zM0 224c0-17.7 14.3-32 32-32H96c17.7 0 32 14.3 32 32V448h32c17.7 0 32 14.3 32 32s-14.3 32-32 32H32c-17.7 0-32-14.3-32-32s14.3-32 32-32H64V256H32c-17.7 0-32-14.3-32-32z",
    ],
  },
  OK = {
    prefix: "fas",
    iconName: "circle-minus",
    icon: [
      512,
      512,
      ["minus-circle"],
      "f056",
      "M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM184 232H328c13.3 0 24 10.7 24 24s-10.7 24-24 24H184c-13.3 0-24-10.7-24-24s10.7-24 24-24z",
    ],
  },
  EK = {
    prefix: "fas",
    iconName: "paste",
    icon: [
      512,
      512,
      ["file-clipboard"],
      "f0ea",
      "M160 0c-23.7 0-44.4 12.9-55.4 32H48C21.5 32 0 53.5 0 80V400c0 26.5 21.5 48 48 48H192V176c0-44.2 35.8-80 80-80h48V80c0-26.5-21.5-48-48-48H215.4C204.4 12.9 183.7 0 160 0zM272 128c-26.5 0-48 21.5-48 48V448v16c0 26.5 21.5 48 48 48H464c26.5 0 48-21.5 48-48V243.9c0-12.7-5.1-24.9-14.1-33.9l-67.9-67.9c-9-9-21.2-14.1-33.9-14.1H320 272zM160 40a24 24 0 1 1 0 48 24 24 0 1 1 0-48z",
    ],
  },
  $K = {
    prefix: "fas",
    iconName: "arrows-to-dot",
    icon: [
      512,
      512,
      [],
      "e4be",
      "M256 0c17.7 0 32 14.3 32 32V64h32c12.9 0 24.6 7.8 29.6 19.8s2.2 25.7-6.9 34.9l-64 64c-12.5 12.5-32.8 12.5-45.3 0l-64-64c-9.2-9.2-11.9-22.9-6.9-34.9s16.6-19.8 29.6-19.8h32V32c0-17.7 14.3-32 32-32zM169.4 393.4l64-64c12.5-12.5 32.8-12.5 45.3 0l64 64c9.2 9.2 11.9 22.9 6.9 34.9s-16.6 19.8-29.6 19.8H288v32c0 17.7-14.3 32-32 32s-32-14.3-32-32V448H192c-12.9 0-24.6-7.8-29.6-19.8s-2.2-25.7 6.9-34.9zM32 224H64V192c0-12.9 7.8-24.6 19.8-29.6s25.7-2.2 34.9 6.9l64 64c12.5 12.5 12.5 32.8 0 45.3l-64 64c-9.2 9.2-22.9 11.9-34.9 6.9s-19.8-16.6-19.8-29.6V288H32c-17.7 0-32-14.3-32-32s14.3-32 32-32zm297.4 54.6c-12.5-12.5-12.5-32.8 0-45.3l64-64c9.2-9.2 22.9-11.9 34.9-6.9s19.8 16.6 19.8 29.6v32h32c17.7 0 32 14.3 32 32s-14.3 32-32 32H448v32c0 12.9-7.8 24.6-19.8 29.6s-25.7 2.2-34.9-6.9l-64-64zM256 224a32 32 0 1 1 0 64 32 32 0 1 1 0-64z",
    ],
  },
  CK = {
    prefix: "fas",
    iconName: "chart-gantt",
    icon: [
      512,
      512,
      [],
      "e0e4",
      "M32 32c17.7 0 32 14.3 32 32V400c0 8.8 7.2 16 16 16H480c17.7 0 32 14.3 32 32s-14.3 32-32 32H80c-44.2 0-80-35.8-80-80V64C0 46.3 14.3 32 32 32zm96 96c0-17.7 14.3-32 32-32l96 0c17.7 0 32 14.3 32 32s-14.3 32-32 32H160c-17.7 0-32-14.3-32-32zm96 64H352c17.7 0 32 14.3 32 32s-14.3 32-32 32H224c-17.7 0-32-14.3-32-32s14.3-32 32-32zm160 96h64c17.7 0 32 14.3 32 32s-14.3 32-32 32H384c-17.7 0-32-14.3-32-32s14.3-32 32-32z",
    ],
  },
  kK = {
    prefix: "fas",
    iconName: "download",
    icon: [
      512,
      512,
      [],
      "f019",
      "M288 32c0-17.7-14.3-32-32-32s-32 14.3-32 32V274.7l-73.4-73.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l128 128c12.5 12.5 32.8 12.5 45.3 0l128-128c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L288 274.7V32zM64 352c-35.3 0-64 28.7-64 64v32c0 35.3 28.7 64 64 64H448c35.3 0 64-28.7 64-64V416c0-35.3-28.7-64-64-64H346.5l-45.3 45.3c-25 25-65.5 25-90.5 0L165.5 352H64zm368 56a24 24 0 1 1 0 48 24 24 0 1 1 0-48z",
    ],
  },
  RK = {
    prefix: "fas",
    iconName: "copy",
    icon: [
      512,
      512,
      [],
      "f0c5",
      "M272 0H396.1c12.7 0 24.9 5.1 33.9 14.1l67.9 67.9c9 9 14.1 21.2 14.1 33.9V336c0 26.5-21.5 48-48 48H272c-26.5 0-48-21.5-48-48V48c0-26.5 21.5-48 48-48zM48 128H192v64H64V448H256V416h64v48c0 26.5-21.5 48-48 48H48c-26.5 0-48-21.5-48-48V176c0-26.5 21.5-48 48-48z",
    ],
  },
  NK = {
    prefix: "fas",
    iconName: "plus",
    icon: [
      448,
      512,
      [10133, 61543, "add"],
      "2b",
      "M256 80c0-17.7-14.3-32-32-32s-32 14.3-32 32V224H48c-17.7 0-32 14.3-32 32s14.3 32 32 32H192V432c0 17.7 14.3 32 32 32s32-14.3 32-32V288H400c17.7 0 32-14.3 32-32s-14.3-32-32-32H256V80z",
    ],
  },
  IK = {
    prefix: "fas",
    iconName: "xmark",
    icon: [
      384,
      512,
      [
        128473,
        10005,
        10006,
        10060,
        215,
        "close",
        "multiply",
        "remove",
        "times",
      ],
      "f00d",
      "M342.6 150.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L192 210.7 86.6 105.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3L146.7 256 41.4 361.4c-12.5 12.5-12.5 32.8 0 45.3s32.8 12.5 45.3 0L192 301.3 297.4 406.6c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L237.3 256 342.6 150.6z",
    ],
  },
  TK = {
    prefix: "fas",
    iconName: "check",
    icon: [
      448,
      512,
      [10003, 10004],
      "f00c",
      "M438.6 105.4c12.5 12.5 12.5 32.8 0 45.3l-256 256c-12.5 12.5-32.8 12.5-45.3 0l-128-128c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0L160 338.7 393.4 105.4c12.5-12.5 32.8-12.5 45.3 0z",
    ],
  },
  zK = {
    prefix: "fas",
    iconName: "exclamation",
    icon: [
      64,
      512,
      [10069, 10071, 61738],
      "21",
      "M64 64c0-17.7-14.3-32-32-32S0 46.3 0 64V320c0 17.7 14.3 32 32 32s32-14.3 32-32V64zM32 480a40 40 0 1 0 0-80 40 40 0 1 0 0 80z",
    ],
  },
  zd = { exports: {} };
/**
 * @license
 * Lodash <https://lodash.com/>
 * Copyright OpenJS Foundation and other contributors <https://openjsf.org/>
 * Released under MIT license <https://lodash.com/license>
 * Based on Underscore.js 1.8.3 <http://underscorejs.org/LICENSE>
 * Copyright Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors
 */ zd.exports;
(function (e, t) {
  (function () {
    var n,
      r = "4.17.21",
      o = 200,
      a = "Unsupported core-js use. Try https://npms.io/search?q=ponyfill.",
      s = "Expected a function",
      u = "Invalid `variable` option passed into `_.template`",
      f = "__lodash_hash_undefined__",
      d = 500,
      m = "__lodash_placeholder__",
      h = 1,
      v = 2,
      b = 4,
      O = 1,
      E = 2,
      $ = 1,
      _ = 2,
      w = 4,
      P = 8,
      k = 16,
      I = 32,
      z = 64,
      A = 128,
      M = 256,
      B = 512,
      H = 30,
      q = "...",
      Z = 800,
      he = 16,
      xe = 1,
      Ce = 2,
      ce = 3,
      Se = 1 / 0,
      Y = 9007199254740991,
      re = 17976931348623157e292,
      ne = 0 / 0,
      le = 4294967295,
      ze = le - 1,
      Gt = le >>> 1,
      Dt = [
        ["ary", A],
        ["bind", $],
        ["bindKey", _],
        ["curry", P],
        ["curryRight", k],
        ["flip", B],
        ["partial", I],
        ["partialRight", z],
        ["rearg", M],
      ],
      $t = "[object Arguments]",
      vt = "[object Array]",
      $n = "[object AsyncFunction]",
      Vn = "[object Boolean]",
      Yn = "[object Date]",
      Zo = "[object DOMException]",
      so = "[object Error]",
      uo = "[object Function]",
      Fi = "[object GeneratorFunction]",
      Ct = "[object Map]",
      $r = "[object Number]",
      Ve = "[object Null]",
      Xt = "[object Object]",
      Wi = "[object Promise]",
      Cr = "[object Proxy]",
      Jo = "[object RegExp]",
      an = "[object Set]",
      ut = "[object String]",
      Bi = "[object Symbol]",
      Gn = "[object Undefined]",
      tt = "[object WeakMap]",
      ll = "[object WeakSet]",
      dr = "[object ArrayBuffer]",
      kr = "[object DataView]",
      sl = "[object Float32Array]",
      Xn = "[object Float64Array]",
      Rr = "[object Int8Array]",
      ei = "[object Int16Array]",
      ul = "[object Int32Array]",
      cl = "[object Uint8Array]",
      kt = "[object Uint8ClampedArray]",
      ti = "[object Uint16Array]",
      ni = "[object Uint32Array]",
      Pp = /\b__p \+= '';/g,
      Ui = /\b(__p \+=) '' \+/g,
      ln = /(__e\(.*?\)|\b__t\)) \+\n'';/g,
      Nr = /&(?:amp|lt|gt|quot|#39);/g,
      fl = /[&<>"']/g,
      Kn = RegExp(Nr.source),
      Qn = RegExp(fl.source),
      Op = /<%-([\s\S]+?)%>/g,
      Ir = /<%([\s\S]+?)%>/g,
      tu = /<%=([\s\S]+?)%>/g,
      Hi = /\.|\[(?:[^[\]]*|(["'])(?:(?!\1)[^\\]|\\.)*?\1)\]/,
      at = /^\w*$/,
      nu =
        /[^.[\]]+|\[(?:(-?\d+(?:\.\d+)?)|(["'])((?:(?!\2)[^\\]|\\.)*?)\2)\]|(?=(?:\.|\[\])(?:\.|\[\]|$))/g,
      Tr = /[\\^$.*+?()[\]{}|]/g,
      sn = RegExp(Tr.source),
      Vi = /^\s+/,
      ru = /\s/,
      Yi = /\{(?:\n\/\* \[wrapped with .+\] \*\/)?\n?/,
      Ep = /\{\n\/\* \[wrapped with (.+)\] \*/,
      $p = /,? & /,
      Cp = /[^\x00-\x2f\x3a-\x40\x5b-\x60\x7b-\x7f]+/g,
      kp = /[()=,{}\[\]\/\s]/,
      Rp = /\\(\\)?/g,
      te = /\$\{([^\\}]*(?:\\.[^\\}]*)*)\}/g,
      Ae = /\w*$/,
      Mt = /^[-+]0x[0-9a-f]+$/i,
      jt = /^0b[01]+$/i,
      sC = /^\[object .+?Constructor\]$/,
      uC = /^0o[0-7]+$/i,
      cC = /^(?:0|[1-9]\d*)$/,
      fC = /[\xc0-\xd6\xd8-\xf6\xf8-\xff\u0100-\u017f]/g,
      ou = /($^)/,
      dC = /['\n\r\u2028\u2029\\]/g,
      iu = "\\ud800-\\udfff",
      pC = "\\u0300-\\u036f",
      mC = "\\ufe20-\\ufe2f",
      hC = "\\u20d0-\\u20ff",
      X0 = pC + mC + hC,
      K0 = "\\u2700-\\u27bf",
      Q0 = "a-z\\xdf-\\xf6\\xf8-\\xff",
      vC = "\\xac\\xb1\\xd7\\xf7",
      gC = "\\x00-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\xbf",
      yC = "\\u2000-\\u206f",
      _C =
        " \\t\\x0b\\f\\xa0\\ufeff\\n\\r\\u2028\\u2029\\u1680\\u180e\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006\\u2007\\u2008\\u2009\\u200a\\u202f\\u205f\\u3000",
      q0 = "A-Z\\xc0-\\xd6\\xd8-\\xde",
      Z0 = "\\ufe0e\\ufe0f",
      J0 = vC + gC + yC + _C,
      Np = "['’]",
      wC = "[" + iu + "]",
      ey = "[" + J0 + "]",
      au = "[" + X0 + "]",
      ty = "\\d+",
      bC = "[" + K0 + "]",
      ny = "[" + Q0 + "]",
      ry = "[^" + iu + J0 + ty + K0 + Q0 + q0 + "]",
      Ip = "\\ud83c[\\udffb-\\udfff]",
      xC = "(?:" + au + "|" + Ip + ")",
      oy = "[^" + iu + "]",
      Tp = "(?:\\ud83c[\\udde6-\\uddff]){2}",
      zp = "[\\ud800-\\udbff][\\udc00-\\udfff]",
      Gi = "[" + q0 + "]",
      iy = "\\u200d",
      ay = "(?:" + ny + "|" + ry + ")",
      SC = "(?:" + Gi + "|" + ry + ")",
      ly = "(?:" + Np + "(?:d|ll|m|re|s|t|ve))?",
      sy = "(?:" + Np + "(?:D|LL|M|RE|S|T|VE))?",
      uy = xC + "?",
      cy = "[" + Z0 + "]?",
      PC = "(?:" + iy + "(?:" + [oy, Tp, zp].join("|") + ")" + cy + uy + ")*",
      OC = "\\d*(?:1st|2nd|3rd|(?![123])\\dth)(?=\\b|[A-Z_])",
      EC = "\\d*(?:1ST|2ND|3RD|(?![123])\\dTH)(?=\\b|[a-z_])",
      fy = cy + uy + PC,
      $C = "(?:" + [bC, Tp, zp].join("|") + ")" + fy,
      CC = "(?:" + [oy + au + "?", au, Tp, zp, wC].join("|") + ")",
      kC = RegExp(Np, "g"),
      RC = RegExp(au, "g"),
      Ap = RegExp(Ip + "(?=" + Ip + ")|" + CC + fy, "g"),
      NC = RegExp(
        [
          Gi + "?" + ny + "+" + ly + "(?=" + [ey, Gi, "$"].join("|") + ")",
          SC + "+" + sy + "(?=" + [ey, Gi + ay, "$"].join("|") + ")",
          Gi + "?" + ay + "+" + ly,
          Gi + "+" + sy,
          EC,
          OC,
          ty,
          $C,
        ].join("|"),
        "g"
      ),
      IC = RegExp("[" + iy + iu + X0 + Z0 + "]"),
      TC = /[a-z][A-Z]|[A-Z]{2}[a-z]|[0-9][a-zA-Z]|[a-zA-Z][0-9]|[^a-zA-Z0-9 ]/,
      zC = [
        "Array",
        "Buffer",
        "DataView",
        "Date",
        "Error",
        "Float32Array",
        "Float64Array",
        "Function",
        "Int8Array",
        "Int16Array",
        "Int32Array",
        "Map",
        "Math",
        "Object",
        "Promise",
        "RegExp",
        "Set",
        "String",
        "Symbol",
        "TypeError",
        "Uint8Array",
        "Uint8ClampedArray",
        "Uint16Array",
        "Uint32Array",
        "WeakMap",
        "_",
        "clearTimeout",
        "isFinite",
        "parseInt",
        "setTimeout",
      ],
      AC = -1,
      Fe = {};
    (Fe[sl] =
      Fe[Xn] =
      Fe[Rr] =
      Fe[ei] =
      Fe[ul] =
      Fe[cl] =
      Fe[kt] =
      Fe[ti] =
      Fe[ni] =
        !0),
      (Fe[$t] =
        Fe[vt] =
        Fe[dr] =
        Fe[Vn] =
        Fe[kr] =
        Fe[Yn] =
        Fe[so] =
        Fe[uo] =
        Fe[Ct] =
        Fe[$r] =
        Fe[Xt] =
        Fe[Jo] =
        Fe[an] =
        Fe[ut] =
        Fe[tt] =
          !1);
    var De = {};
    (De[$t] =
      De[vt] =
      De[dr] =
      De[kr] =
      De[Vn] =
      De[Yn] =
      De[sl] =
      De[Xn] =
      De[Rr] =
      De[ei] =
      De[ul] =
      De[Ct] =
      De[$r] =
      De[Xt] =
      De[Jo] =
      De[an] =
      De[ut] =
      De[Bi] =
      De[cl] =
      De[kt] =
      De[ti] =
      De[ni] =
        !0),
      (De[so] = De[uo] = De[tt] = !1);
    var LC = {
        À: "A",
        Á: "A",
        Â: "A",
        Ã: "A",
        Ä: "A",
        Å: "A",
        à: "a",
        á: "a",
        â: "a",
        ã: "a",
        ä: "a",
        å: "a",
        Ç: "C",
        ç: "c",
        Ð: "D",
        ð: "d",
        È: "E",
        É: "E",
        Ê: "E",
        Ë: "E",
        è: "e",
        é: "e",
        ê: "e",
        ë: "e",
        Ì: "I",
        Í: "I",
        Î: "I",
        Ï: "I",
        ì: "i",
        í: "i",
        î: "i",
        ï: "i",
        Ñ: "N",
        ñ: "n",
        Ò: "O",
        Ó: "O",
        Ô: "O",
        Õ: "O",
        Ö: "O",
        Ø: "O",
        ò: "o",
        ó: "o",
        ô: "o",
        õ: "o",
        ö: "o",
        ø: "o",
        Ù: "U",
        Ú: "U",
        Û: "U",
        Ü: "U",
        ù: "u",
        ú: "u",
        û: "u",
        ü: "u",
        Ý: "Y",
        ý: "y",
        ÿ: "y",
        Æ: "Ae",
        æ: "ae",
        Þ: "Th",
        þ: "th",
        ß: "ss",
        Ā: "A",
        Ă: "A",
        Ą: "A",
        ā: "a",
        ă: "a",
        ą: "a",
        Ć: "C",
        Ĉ: "C",
        Ċ: "C",
        Č: "C",
        ć: "c",
        ĉ: "c",
        ċ: "c",
        č: "c",
        Ď: "D",
        Đ: "D",
        ď: "d",
        đ: "d",
        Ē: "E",
        Ĕ: "E",
        Ė: "E",
        Ę: "E",
        Ě: "E",
        ē: "e",
        ĕ: "e",
        ė: "e",
        ę: "e",
        ě: "e",
        Ĝ: "G",
        Ğ: "G",
        Ġ: "G",
        Ģ: "G",
        ĝ: "g",
        ğ: "g",
        ġ: "g",
        ģ: "g",
        Ĥ: "H",
        Ħ: "H",
        ĥ: "h",
        ħ: "h",
        Ĩ: "I",
        Ī: "I",
        Ĭ: "I",
        Į: "I",
        İ: "I",
        ĩ: "i",
        ī: "i",
        ĭ: "i",
        į: "i",
        ı: "i",
        Ĵ: "J",
        ĵ: "j",
        Ķ: "K",
        ķ: "k",
        ĸ: "k",
        Ĺ: "L",
        Ļ: "L",
        Ľ: "L",
        Ŀ: "L",
        Ł: "L",
        ĺ: "l",
        ļ: "l",
        ľ: "l",
        ŀ: "l",
        ł: "l",
        Ń: "N",
        Ņ: "N",
        Ň: "N",
        Ŋ: "N",
        ń: "n",
        ņ: "n",
        ň: "n",
        ŋ: "n",
        Ō: "O",
        Ŏ: "O",
        Ő: "O",
        ō: "o",
        ŏ: "o",
        ő: "o",
        Ŕ: "R",
        Ŗ: "R",
        Ř: "R",
        ŕ: "r",
        ŗ: "r",
        ř: "r",
        Ś: "S",
        Ŝ: "S",
        Ş: "S",
        Š: "S",
        ś: "s",
        ŝ: "s",
        ş: "s",
        š: "s",
        Ţ: "T",
        Ť: "T",
        Ŧ: "T",
        ţ: "t",
        ť: "t",
        ŧ: "t",
        Ũ: "U",
        Ū: "U",
        Ŭ: "U",
        Ů: "U",
        Ű: "U",
        Ų: "U",
        ũ: "u",
        ū: "u",
        ŭ: "u",
        ů: "u",
        ű: "u",
        ų: "u",
        Ŵ: "W",
        ŵ: "w",
        Ŷ: "Y",
        ŷ: "y",
        Ÿ: "Y",
        Ź: "Z",
        Ż: "Z",
        Ž: "Z",
        ź: "z",
        ż: "z",
        ž: "z",
        Ĳ: "IJ",
        ĳ: "ij",
        Œ: "Oe",
        œ: "oe",
        ŉ: "'n",
        ſ: "s",
      },
      DC = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      },
      MC = {
        "&amp;": "&",
        "&lt;": "<",
        "&gt;": ">",
        "&quot;": '"',
        "&#39;": "'",
      },
      jC = {
        "\\": "\\",
        "'": "'",
        "\n": "n",
        "\r": "r",
        "\u2028": "u2028",
        "\u2029": "u2029",
      },
      FC = parseFloat,
      WC = parseInt,
      dy = typeof $l == "object" && $l && $l.Object === Object && $l,
      BC = typeof self == "object" && self && self.Object === Object && self,
      bt = dy || BC || Function("return this")(),
      Lp = t && !t.nodeType && t,
      ri = Lp && !0 && e && !e.nodeType && e,
      py = ri && ri.exports === Lp,
      Dp = py && dy.process,
      Cn = (function () {
        try {
          var T = ri && ri.require && ri.require("util").types;
          return T || (Dp && Dp.binding && Dp.binding("util"));
        } catch {}
      })(),
      my = Cn && Cn.isArrayBuffer,
      hy = Cn && Cn.isDate,
      vy = Cn && Cn.isMap,
      gy = Cn && Cn.isRegExp,
      yy = Cn && Cn.isSet,
      _y = Cn && Cn.isTypedArray;
    function un(T, j, D) {
      switch (D.length) {
        case 0:
          return T.call(j);
        case 1:
          return T.call(j, D[0]);
        case 2:
          return T.call(j, D[0], D[1]);
        case 3:
          return T.call(j, D[0], D[1], D[2]);
      }
      return T.apply(j, D);
    }
    function UC(T, j, D, X) {
      for (var se = -1, Oe = T == null ? 0 : T.length; ++se < Oe; ) {
        var ct = T[se];
        j(X, ct, D(ct), T);
      }
      return X;
    }
    function kn(T, j) {
      for (
        var D = -1, X = T == null ? 0 : T.length;
        ++D < X && j(T[D], D, T) !== !1;

      );
      return T;
    }
    function HC(T, j) {
      for (var D = T == null ? 0 : T.length; D-- && j(T[D], D, T) !== !1; );
      return T;
    }
    function wy(T, j) {
      for (var D = -1, X = T == null ? 0 : T.length; ++D < X; )
        if (!j(T[D], D, T)) return !1;
      return !0;
    }
    function co(T, j) {
      for (
        var D = -1, X = T == null ? 0 : T.length, se = 0, Oe = [];
        ++D < X;

      ) {
        var ct = T[D];
        j(ct, D, T) && (Oe[se++] = ct);
      }
      return Oe;
    }
    function lu(T, j) {
      var D = T == null ? 0 : T.length;
      return !!D && Xi(T, j, 0) > -1;
    }
    function Mp(T, j, D) {
      for (var X = -1, se = T == null ? 0 : T.length; ++X < se; )
        if (D(j, T[X])) return !0;
      return !1;
    }
    function Ye(T, j) {
      for (var D = -1, X = T == null ? 0 : T.length, se = Array(X); ++D < X; )
        se[D] = j(T[D], D, T);
      return se;
    }
    function fo(T, j) {
      for (var D = -1, X = j.length, se = T.length; ++D < X; ) T[se + D] = j[D];
      return T;
    }
    function jp(T, j, D, X) {
      var se = -1,
        Oe = T == null ? 0 : T.length;
      for (X && Oe && (D = T[++se]); ++se < Oe; ) D = j(D, T[se], se, T);
      return D;
    }
    function VC(T, j, D, X) {
      var se = T == null ? 0 : T.length;
      for (X && se && (D = T[--se]); se--; ) D = j(D, T[se], se, T);
      return D;
    }
    function Fp(T, j) {
      for (var D = -1, X = T == null ? 0 : T.length; ++D < X; )
        if (j(T[D], D, T)) return !0;
      return !1;
    }
    var YC = Wp("length");
    function GC(T) {
      return T.split("");
    }
    function XC(T) {
      return T.match(Cp) || [];
    }
    function by(T, j, D) {
      var X;
      return (
        D(T, function (se, Oe, ct) {
          if (j(se, Oe, ct)) return (X = Oe), !1;
        }),
        X
      );
    }
    function su(T, j, D, X) {
      for (var se = T.length, Oe = D + (X ? 1 : -1); X ? Oe-- : ++Oe < se; )
        if (j(T[Oe], Oe, T)) return Oe;
      return -1;
    }
    function Xi(T, j, D) {
      return j === j ? a4(T, j, D) : su(T, xy, D);
    }
    function KC(T, j, D, X) {
      for (var se = D - 1, Oe = T.length; ++se < Oe; )
        if (X(T[se], j)) return se;
      return -1;
    }
    function xy(T) {
      return T !== T;
    }
    function Sy(T, j) {
      var D = T == null ? 0 : T.length;
      return D ? Up(T, j) / D : ne;
    }
    function Wp(T) {
      return function (j) {
        return j == null ? n : j[T];
      };
    }
    function Bp(T) {
      return function (j) {
        return T == null ? n : T[j];
      };
    }
    function Py(T, j, D, X, se) {
      return (
        se(T, function (Oe, ct, Le) {
          D = X ? ((X = !1), Oe) : j(D, Oe, ct, Le);
        }),
        D
      );
    }
    function QC(T, j) {
      var D = T.length;
      for (T.sort(j); D--; ) T[D] = T[D].value;
      return T;
    }
    function Up(T, j) {
      for (var D, X = -1, se = T.length; ++X < se; ) {
        var Oe = j(T[X]);
        Oe !== n && (D = D === n ? Oe : D + Oe);
      }
      return D;
    }
    function Hp(T, j) {
      for (var D = -1, X = Array(T); ++D < T; ) X[D] = j(D);
      return X;
    }
    function qC(T, j) {
      return Ye(j, function (D) {
        return [D, T[D]];
      });
    }
    function Oy(T) {
      return T && T.slice(0, ky(T) + 1).replace(Vi, "");
    }
    function cn(T) {
      return function (j) {
        return T(j);
      };
    }
    function Vp(T, j) {
      return Ye(j, function (D) {
        return T[D];
      });
    }
    function dl(T, j) {
      return T.has(j);
    }
    function Ey(T, j) {
      for (var D = -1, X = T.length; ++D < X && Xi(j, T[D], 0) > -1; );
      return D;
    }
    function $y(T, j) {
      for (var D = T.length; D-- && Xi(j, T[D], 0) > -1; );
      return D;
    }
    function ZC(T, j) {
      for (var D = T.length, X = 0; D--; ) T[D] === j && ++X;
      return X;
    }
    var JC = Bp(LC),
      e4 = Bp(DC);
    function t4(T) {
      return "\\" + jC[T];
    }
    function n4(T, j) {
      return T == null ? n : T[j];
    }
    function Ki(T) {
      return IC.test(T);
    }
    function r4(T) {
      return TC.test(T);
    }
    function o4(T) {
      for (var j, D = []; !(j = T.next()).done; ) D.push(j.value);
      return D;
    }
    function Yp(T) {
      var j = -1,
        D = Array(T.size);
      return (
        T.forEach(function (X, se) {
          D[++j] = [se, X];
        }),
        D
      );
    }
    function Cy(T, j) {
      return function (D) {
        return T(j(D));
      };
    }
    function po(T, j) {
      for (var D = -1, X = T.length, se = 0, Oe = []; ++D < X; ) {
        var ct = T[D];
        (ct === j || ct === m) && ((T[D] = m), (Oe[se++] = D));
      }
      return Oe;
    }
    function uu(T) {
      var j = -1,
        D = Array(T.size);
      return (
        T.forEach(function (X) {
          D[++j] = X;
        }),
        D
      );
    }
    function i4(T) {
      var j = -1,
        D = Array(T.size);
      return (
        T.forEach(function (X) {
          D[++j] = [X, X];
        }),
        D
      );
    }
    function a4(T, j, D) {
      for (var X = D - 1, se = T.length; ++X < se; ) if (T[X] === j) return X;
      return -1;
    }
    function l4(T, j, D) {
      for (var X = D + 1; X--; ) if (T[X] === j) return X;
      return X;
    }
    function Qi(T) {
      return Ki(T) ? u4(T) : YC(T);
    }
    function qn(T) {
      return Ki(T) ? c4(T) : GC(T);
    }
    function ky(T) {
      for (var j = T.length; j-- && ru.test(T.charAt(j)); );
      return j;
    }
    var s4 = Bp(MC);
    function u4(T) {
      for (var j = (Ap.lastIndex = 0); Ap.test(T); ) ++j;
      return j;
    }
    function c4(T) {
      return T.match(Ap) || [];
    }
    function f4(T) {
      return T.match(NC) || [];
    }
    var d4 = function T(j) {
        j = j == null ? bt : qi.defaults(bt.Object(), j, qi.pick(bt, zC));
        var D = j.Array,
          X = j.Date,
          se = j.Error,
          Oe = j.Function,
          ct = j.Math,
          Le = j.Object,
          Gp = j.RegExp,
          p4 = j.String,
          Rn = j.TypeError,
          cu = D.prototype,
          m4 = Oe.prototype,
          Zi = Le.prototype,
          fu = j["__core-js_shared__"],
          du = m4.toString,
          Re = Zi.hasOwnProperty,
          h4 = 0,
          Ry = (function () {
            var i = /[^.]+$/.exec((fu && fu.keys && fu.keys.IE_PROTO) || "");
            return i ? "Symbol(src)_1." + i : "";
          })(),
          pu = Zi.toString,
          v4 = du.call(Le),
          g4 = bt._,
          y4 = Gp(
            "^" +
              du
                .call(Re)
                .replace(Tr, "\\$&")
                .replace(
                  /hasOwnProperty|(function).*?(?=\\\()| for .+?(?=\\\])/g,
                  "$1.*?"
                ) +
              "$"
          ),
          mu = py ? j.Buffer : n,
          mo = j.Symbol,
          hu = j.Uint8Array,
          Ny = mu ? mu.allocUnsafe : n,
          vu = Cy(Le.getPrototypeOf, Le),
          Iy = Le.create,
          Ty = Zi.propertyIsEnumerable,
          gu = cu.splice,
          zy = mo ? mo.isConcatSpreadable : n,
          pl = mo ? mo.iterator : n,
          oi = mo ? mo.toStringTag : n,
          yu = (function () {
            try {
              var i = ui(Le, "defineProperty");
              return i({}, "", {}), i;
            } catch {}
          })(),
          _4 = j.clearTimeout !== bt.clearTimeout && j.clearTimeout,
          w4 = X && X.now !== bt.Date.now && X.now,
          b4 = j.setTimeout !== bt.setTimeout && j.setTimeout,
          _u = ct.ceil,
          wu = ct.floor,
          Xp = Le.getOwnPropertySymbols,
          x4 = mu ? mu.isBuffer : n,
          Ay = j.isFinite,
          S4 = cu.join,
          P4 = Cy(Le.keys, Le),
          ft = ct.max,
          Rt = ct.min,
          O4 = X.now,
          E4 = j.parseInt,
          Ly = ct.random,
          $4 = cu.reverse,
          Kp = ui(j, "DataView"),
          ml = ui(j, "Map"),
          Qp = ui(j, "Promise"),
          Ji = ui(j, "Set"),
          hl = ui(j, "WeakMap"),
          vl = ui(Le, "create"),
          bu = hl && new hl(),
          ea = {},
          C4 = ci(Kp),
          k4 = ci(ml),
          R4 = ci(Qp),
          N4 = ci(Ji),
          I4 = ci(hl),
          xu = mo ? mo.prototype : n,
          gl = xu ? xu.valueOf : n,
          Dy = xu ? xu.toString : n;
        function x(i) {
          if (Ze(i) && !ue(i) && !(i instanceof ye)) {
            if (i instanceof Nn) return i;
            if (Re.call(i, "__wrapped__")) return M1(i);
          }
          return new Nn(i);
        }
        var ta = (function () {
          function i() {}
          return function (l) {
            if (!Xe(l)) return {};
            if (Iy) return Iy(l);
            i.prototype = l;
            var c = new i();
            return (i.prototype = n), c;
          };
        })();
        function Su() {}
        function Nn(i, l) {
          (this.__wrapped__ = i),
            (this.__actions__ = []),
            (this.__chain__ = !!l),
            (this.__index__ = 0),
            (this.__values__ = n);
        }
        (x.templateSettings = {
          escape: Op,
          evaluate: Ir,
          interpolate: tu,
          variable: "",
          imports: { _: x },
        }),
          (x.prototype = Su.prototype),
          (x.prototype.constructor = x),
          (Nn.prototype = ta(Su.prototype)),
          (Nn.prototype.constructor = Nn);
        function ye(i) {
          (this.__wrapped__ = i),
            (this.__actions__ = []),
            (this.__dir__ = 1),
            (this.__filtered__ = !1),
            (this.__iteratees__ = []),
            (this.__takeCount__ = le),
            (this.__views__ = []);
        }
        function T4() {
          var i = new ye(this.__wrapped__);
          return (
            (i.__actions__ = Kt(this.__actions__)),
            (i.__dir__ = this.__dir__),
            (i.__filtered__ = this.__filtered__),
            (i.__iteratees__ = Kt(this.__iteratees__)),
            (i.__takeCount__ = this.__takeCount__),
            (i.__views__ = Kt(this.__views__)),
            i
          );
        }
        function z4() {
          if (this.__filtered__) {
            var i = new ye(this);
            (i.__dir__ = -1), (i.__filtered__ = !0);
          } else (i = this.clone()), (i.__dir__ *= -1);
          return i;
        }
        function A4() {
          var i = this.__wrapped__.value(),
            l = this.__dir__,
            c = ue(i),
            p = l < 0,
            g = c ? i.length : 0,
            S = Gk(0, g, this.__views__),
            C = S.start,
            N = S.end,
            L = N - C,
            F = p ? N : C - 1,
            W = this.__iteratees__,
            U = W.length,
            G = 0,
            J = Rt(L, this.__takeCount__);
          if (!c || (!p && g == L && J == L)) return l1(i, this.__actions__);
          var ie = [];
          e: for (; L-- && G < J; ) {
            F += l;
            for (var de = -1, ae = i[F]; ++de < U; ) {
              var ve = W[de],
                we = ve.iteratee,
                pn = ve.type,
                Bt = we(ae);
              if (pn == Ce) ae = Bt;
              else if (!Bt) {
                if (pn == xe) continue e;
                break e;
              }
            }
            ie[G++] = ae;
          }
          return ie;
        }
        (ye.prototype = ta(Su.prototype)), (ye.prototype.constructor = ye);
        function ii(i) {
          var l = -1,
            c = i == null ? 0 : i.length;
          for (this.clear(); ++l < c; ) {
            var p = i[l];
            this.set(p[0], p[1]);
          }
        }
        function L4() {
          (this.__data__ = vl ? vl(null) : {}), (this.size = 0);
        }
        function D4(i) {
          var l = this.has(i) && delete this.__data__[i];
          return (this.size -= l ? 1 : 0), l;
        }
        function M4(i) {
          var l = this.__data__;
          if (vl) {
            var c = l[i];
            return c === f ? n : c;
          }
          return Re.call(l, i) ? l[i] : n;
        }
        function j4(i) {
          var l = this.__data__;
          return vl ? l[i] !== n : Re.call(l, i);
        }
        function F4(i, l) {
          var c = this.__data__;
          return (
            (this.size += this.has(i) ? 0 : 1),
            (c[i] = vl && l === n ? f : l),
            this
          );
        }
        (ii.prototype.clear = L4),
          (ii.prototype.delete = D4),
          (ii.prototype.get = M4),
          (ii.prototype.has = j4),
          (ii.prototype.set = F4);
        function zr(i) {
          var l = -1,
            c = i == null ? 0 : i.length;
          for (this.clear(); ++l < c; ) {
            var p = i[l];
            this.set(p[0], p[1]);
          }
        }
        function W4() {
          (this.__data__ = []), (this.size = 0);
        }
        function B4(i) {
          var l = this.__data__,
            c = Pu(l, i);
          if (c < 0) return !1;
          var p = l.length - 1;
          return c == p ? l.pop() : gu.call(l, c, 1), --this.size, !0;
        }
        function U4(i) {
          var l = this.__data__,
            c = Pu(l, i);
          return c < 0 ? n : l[c][1];
        }
        function H4(i) {
          return Pu(this.__data__, i) > -1;
        }
        function V4(i, l) {
          var c = this.__data__,
            p = Pu(c, i);
          return p < 0 ? (++this.size, c.push([i, l])) : (c[p][1] = l), this;
        }
        (zr.prototype.clear = W4),
          (zr.prototype.delete = B4),
          (zr.prototype.get = U4),
          (zr.prototype.has = H4),
          (zr.prototype.set = V4);
        function Ar(i) {
          var l = -1,
            c = i == null ? 0 : i.length;
          for (this.clear(); ++l < c; ) {
            var p = i[l];
            this.set(p[0], p[1]);
          }
        }
        function Y4() {
          (this.size = 0),
            (this.__data__ = {
              hash: new ii(),
              map: new (ml || zr)(),
              string: new ii(),
            });
        }
        function G4(i) {
          var l = Lu(this, i).delete(i);
          return (this.size -= l ? 1 : 0), l;
        }
        function X4(i) {
          return Lu(this, i).get(i);
        }
        function K4(i) {
          return Lu(this, i).has(i);
        }
        function Q4(i, l) {
          var c = Lu(this, i),
            p = c.size;
          return c.set(i, l), (this.size += c.size == p ? 0 : 1), this;
        }
        (Ar.prototype.clear = Y4),
          (Ar.prototype.delete = G4),
          (Ar.prototype.get = X4),
          (Ar.prototype.has = K4),
          (Ar.prototype.set = Q4);
        function ai(i) {
          var l = -1,
            c = i == null ? 0 : i.length;
          for (this.__data__ = new Ar(); ++l < c; ) this.add(i[l]);
        }
        function q4(i) {
          return this.__data__.set(i, f), this;
        }
        function Z4(i) {
          return this.__data__.has(i);
        }
        (ai.prototype.add = ai.prototype.push = q4), (ai.prototype.has = Z4);
        function Zn(i) {
          var l = (this.__data__ = new zr(i));
          this.size = l.size;
        }
        function J4() {
          (this.__data__ = new zr()), (this.size = 0);
        }
        function ek(i) {
          var l = this.__data__,
            c = l.delete(i);
          return (this.size = l.size), c;
        }
        function tk(i) {
          return this.__data__.get(i);
        }
        function nk(i) {
          return this.__data__.has(i);
        }
        function rk(i, l) {
          var c = this.__data__;
          if (c instanceof zr) {
            var p = c.__data__;
            if (!ml || p.length < o - 1)
              return p.push([i, l]), (this.size = ++c.size), this;
            c = this.__data__ = new Ar(p);
          }
          return c.set(i, l), (this.size = c.size), this;
        }
        (Zn.prototype.clear = J4),
          (Zn.prototype.delete = ek),
          (Zn.prototype.get = tk),
          (Zn.prototype.has = nk),
          (Zn.prototype.set = rk);
        function My(i, l) {
          var c = ue(i),
            p = !c && fi(i),
            g = !c && !p && _o(i),
            S = !c && !p && !g && ia(i),
            C = c || p || g || S,
            N = C ? Hp(i.length, p4) : [],
            L = N.length;
          for (var F in i)
            (l || Re.call(i, F)) &&
              !(
                C &&
                (F == "length" ||
                  (g && (F == "offset" || F == "parent")) ||
                  (S &&
                    (F == "buffer" ||
                      F == "byteLength" ||
                      F == "byteOffset")) ||
                  jr(F, L))
              ) &&
              N.push(F);
          return N;
        }
        function jy(i) {
          var l = i.length;
          return l ? i[lm(0, l - 1)] : n;
        }
        function ok(i, l) {
          return Du(Kt(i), li(l, 0, i.length));
        }
        function ik(i) {
          return Du(Kt(i));
        }
        function qp(i, l, c) {
          ((c !== n && !Jn(i[l], c)) || (c === n && !(l in i))) && Lr(i, l, c);
        }
        function yl(i, l, c) {
          var p = i[l];
          (!(Re.call(i, l) && Jn(p, c)) || (c === n && !(l in i))) &&
            Lr(i, l, c);
        }
        function Pu(i, l) {
          for (var c = i.length; c--; ) if (Jn(i[c][0], l)) return c;
          return -1;
        }
        function ak(i, l, c, p) {
          return (
            ho(i, function (g, S, C) {
              l(p, g, c(g), C);
            }),
            p
          );
        }
        function Fy(i, l) {
          return i && mr(l, gt(l), i);
        }
        function lk(i, l) {
          return i && mr(l, qt(l), i);
        }
        function Lr(i, l, c) {
          l == "__proto__" && yu
            ? yu(i, l, {
                configurable: !0,
                enumerable: !0,
                value: c,
                writable: !0,
              })
            : (i[l] = c);
        }
        function Zp(i, l) {
          for (var c = -1, p = l.length, g = D(p), S = i == null; ++c < p; )
            g[c] = S ? n : Im(i, l[c]);
          return g;
        }
        function li(i, l, c) {
          return (
            i === i &&
              (c !== n && (i = i <= c ? i : c),
              l !== n && (i = i >= l ? i : l)),
            i
          );
        }
        function In(i, l, c, p, g, S) {
          var C,
            N = l & h,
            L = l & v,
            F = l & b;
          if ((c && (C = g ? c(i, p, g, S) : c(i)), C !== n)) return C;
          if (!Xe(i)) return i;
          var W = ue(i);
          if (W) {
            if (((C = Kk(i)), !N)) return Kt(i, C);
          } else {
            var U = Nt(i),
              G = U == uo || U == Fi;
            if (_o(i)) return c1(i, N);
            if (U == Xt || U == $t || (G && !g)) {
              if (((C = L || G ? {} : k1(i)), !N))
                return L ? Mk(i, lk(C, i)) : Dk(i, Fy(C, i));
            } else {
              if (!De[U]) return g ? i : {};
              C = Qk(i, U, N);
            }
          }
          S || (S = new Zn());
          var J = S.get(i);
          if (J) return J;
          S.set(i, C),
            o_(i)
              ? i.forEach(function (ae) {
                  C.add(In(ae, l, c, ae, i, S));
                })
              : n_(i) &&
                i.forEach(function (ae, ve) {
                  C.set(ve, In(ae, l, c, ve, i, S));
                });
          var ie = F ? (L ? ym : gm) : L ? qt : gt,
            de = W ? n : ie(i);
          return (
            kn(de || i, function (ae, ve) {
              de && ((ve = ae), (ae = i[ve])),
                yl(C, ve, In(ae, l, c, ve, i, S));
            }),
            C
          );
        }
        function sk(i) {
          var l = gt(i);
          return function (c) {
            return Wy(c, i, l);
          };
        }
        function Wy(i, l, c) {
          var p = c.length;
          if (i == null) return !p;
          for (i = Le(i); p--; ) {
            var g = c[p],
              S = l[g],
              C = i[g];
            if ((C === n && !(g in i)) || !S(C)) return !1;
          }
          return !0;
        }
        function By(i, l, c) {
          if (typeof i != "function") throw new Rn(s);
          return Ol(function () {
            i.apply(n, c);
          }, l);
        }
        function _l(i, l, c, p) {
          var g = -1,
            S = lu,
            C = !0,
            N = i.length,
            L = [],
            F = l.length;
          if (!N) return L;
          c && (l = Ye(l, cn(c))),
            p
              ? ((S = Mp), (C = !1))
              : l.length >= o && ((S = dl), (C = !1), (l = new ai(l)));
          e: for (; ++g < N; ) {
            var W = i[g],
              U = c == null ? W : c(W);
            if (((W = p || W !== 0 ? W : 0), C && U === U)) {
              for (var G = F; G--; ) if (l[G] === U) continue e;
              L.push(W);
            } else S(l, U, p) || L.push(W);
          }
          return L;
        }
        var ho = h1(pr),
          Uy = h1(em, !0);
        function uk(i, l) {
          var c = !0;
          return (
            ho(i, function (p, g, S) {
              return (c = !!l(p, g, S)), c;
            }),
            c
          );
        }
        function Ou(i, l, c) {
          for (var p = -1, g = i.length; ++p < g; ) {
            var S = i[p],
              C = l(S);
            if (C != null && (N === n ? C === C && !dn(C) : c(C, N)))
              var N = C,
                L = S;
          }
          return L;
        }
        function ck(i, l, c, p) {
          var g = i.length;
          for (
            c = fe(c),
              c < 0 && (c = -c > g ? 0 : g + c),
              p = p === n || p > g ? g : fe(p),
              p < 0 && (p += g),
              p = c > p ? 0 : a_(p);
            c < p;

          )
            i[c++] = l;
          return i;
        }
        function Hy(i, l) {
          var c = [];
          return (
            ho(i, function (p, g, S) {
              l(p, g, S) && c.push(p);
            }),
            c
          );
        }
        function xt(i, l, c, p, g) {
          var S = -1,
            C = i.length;
          for (c || (c = Zk), g || (g = []); ++S < C; ) {
            var N = i[S];
            l > 0 && c(N)
              ? l > 1
                ? xt(N, l - 1, c, p, g)
                : fo(g, N)
              : p || (g[g.length] = N);
          }
          return g;
        }
        var Jp = v1(),
          Vy = v1(!0);
        function pr(i, l) {
          return i && Jp(i, l, gt);
        }
        function em(i, l) {
          return i && Vy(i, l, gt);
        }
        function Eu(i, l) {
          return co(l, function (c) {
            return Fr(i[c]);
          });
        }
        function si(i, l) {
          l = go(l, i);
          for (var c = 0, p = l.length; i != null && c < p; ) i = i[hr(l[c++])];
          return c && c == p ? i : n;
        }
        function Yy(i, l, c) {
          var p = l(i);
          return ue(i) ? p : fo(p, c(i));
        }
        function Ft(i) {
          return i == null
            ? i === n
              ? Gn
              : Ve
            : oi && oi in Le(i)
            ? Yk(i)
            : i6(i);
        }
        function tm(i, l) {
          return i > l;
        }
        function fk(i, l) {
          return i != null && Re.call(i, l);
        }
        function dk(i, l) {
          return i != null && l in Le(i);
        }
        function pk(i, l, c) {
          return i >= Rt(l, c) && i < ft(l, c);
        }
        function nm(i, l, c) {
          for (
            var p = c ? Mp : lu,
              g = i[0].length,
              S = i.length,
              C = S,
              N = D(S),
              L = 1 / 0,
              F = [];
            C--;

          ) {
            var W = i[C];
            C && l && (W = Ye(W, cn(l))),
              (L = Rt(W.length, L)),
              (N[C] =
                !c && (l || (g >= 120 && W.length >= 120))
                  ? new ai(C && W)
                  : n);
          }
          W = i[0];
          var U = -1,
            G = N[0];
          e: for (; ++U < g && F.length < L; ) {
            var J = W[U],
              ie = l ? l(J) : J;
            if (((J = c || J !== 0 ? J : 0), !(G ? dl(G, ie) : p(F, ie, c)))) {
              for (C = S; --C; ) {
                var de = N[C];
                if (!(de ? dl(de, ie) : p(i[C], ie, c))) continue e;
              }
              G && G.push(ie), F.push(J);
            }
          }
          return F;
        }
        function mk(i, l, c, p) {
          return (
            pr(i, function (g, S, C) {
              l(p, c(g), S, C);
            }),
            p
          );
        }
        function wl(i, l, c) {
          (l = go(l, i)), (i = T1(i, l));
          var p = i == null ? i : i[hr(zn(l))];
          return p == null ? n : un(p, i, c);
        }
        function Gy(i) {
          return Ze(i) && Ft(i) == $t;
        }
        function hk(i) {
          return Ze(i) && Ft(i) == dr;
        }
        function vk(i) {
          return Ze(i) && Ft(i) == Yn;
        }
        function bl(i, l, c, p, g) {
          return i === l
            ? !0
            : i == null || l == null || (!Ze(i) && !Ze(l))
            ? i !== i && l !== l
            : gk(i, l, c, p, bl, g);
        }
        function gk(i, l, c, p, g, S) {
          var C = ue(i),
            N = ue(l),
            L = C ? vt : Nt(i),
            F = N ? vt : Nt(l);
          (L = L == $t ? Xt : L), (F = F == $t ? Xt : F);
          var W = L == Xt,
            U = F == Xt,
            G = L == F;
          if (G && _o(i)) {
            if (!_o(l)) return !1;
            (C = !0), (W = !1);
          }
          if (G && !W)
            return (
              S || (S = new Zn()),
              C || ia(i) ? E1(i, l, c, p, g, S) : Hk(i, l, L, c, p, g, S)
            );
          if (!(c & O)) {
            var J = W && Re.call(i, "__wrapped__"),
              ie = U && Re.call(l, "__wrapped__");
            if (J || ie) {
              var de = J ? i.value() : i,
                ae = ie ? l.value() : l;
              return S || (S = new Zn()), g(de, ae, c, p, S);
            }
          }
          return G ? (S || (S = new Zn()), Vk(i, l, c, p, g, S)) : !1;
        }
        function yk(i) {
          return Ze(i) && Nt(i) == Ct;
        }
        function rm(i, l, c, p) {
          var g = c.length,
            S = g,
            C = !p;
          if (i == null) return !S;
          for (i = Le(i); g--; ) {
            var N = c[g];
            if (C && N[2] ? N[1] !== i[N[0]] : !(N[0] in i)) return !1;
          }
          for (; ++g < S; ) {
            N = c[g];
            var L = N[0],
              F = i[L],
              W = N[1];
            if (C && N[2]) {
              if (F === n && !(L in i)) return !1;
            } else {
              var U = new Zn();
              if (p) var G = p(F, W, L, i, l, U);
              if (!(G === n ? bl(W, F, O | E, p, U) : G)) return !1;
            }
          }
          return !0;
        }
        function Xy(i) {
          if (!Xe(i) || e6(i)) return !1;
          var l = Fr(i) ? y4 : sC;
          return l.test(ci(i));
        }
        function _k(i) {
          return Ze(i) && Ft(i) == Jo;
        }
        function wk(i) {
          return Ze(i) && Nt(i) == an;
        }
        function bk(i) {
          return Ze(i) && Uu(i.length) && !!Fe[Ft(i)];
        }
        function Ky(i) {
          return typeof i == "function"
            ? i
            : i == null
            ? Zt
            : typeof i == "object"
            ? ue(i)
              ? Zy(i[0], i[1])
              : qy(i)
            : g_(i);
        }
        function om(i) {
          if (!Pl(i)) return P4(i);
          var l = [];
          for (var c in Le(i)) Re.call(i, c) && c != "constructor" && l.push(c);
          return l;
        }
        function xk(i) {
          if (!Xe(i)) return o6(i);
          var l = Pl(i),
            c = [];
          for (var p in i)
            (p == "constructor" && (l || !Re.call(i, p))) || c.push(p);
          return c;
        }
        function im(i, l) {
          return i < l;
        }
        function Qy(i, l) {
          var c = -1,
            p = Qt(i) ? D(i.length) : [];
          return (
            ho(i, function (g, S, C) {
              p[++c] = l(g, S, C);
            }),
            p
          );
        }
        function qy(i) {
          var l = wm(i);
          return l.length == 1 && l[0][2]
            ? N1(l[0][0], l[0][1])
            : function (c) {
                return c === i || rm(c, i, l);
              };
        }
        function Zy(i, l) {
          return xm(i) && R1(l)
            ? N1(hr(i), l)
            : function (c) {
                var p = Im(c, i);
                return p === n && p === l ? Tm(c, i) : bl(l, p, O | E);
              };
        }
        function $u(i, l, c, p, g) {
          i !== l &&
            Jp(
              l,
              function (S, C) {
                if ((g || (g = new Zn()), Xe(S))) Sk(i, l, C, c, $u, p, g);
                else {
                  var N = p ? p(Pm(i, C), S, C + "", i, l, g) : n;
                  N === n && (N = S), qp(i, C, N);
                }
              },
              qt
            );
        }
        function Sk(i, l, c, p, g, S, C) {
          var N = Pm(i, c),
            L = Pm(l, c),
            F = C.get(L);
          if (F) {
            qp(i, c, F);
            return;
          }
          var W = S ? S(N, L, c + "", i, l, C) : n,
            U = W === n;
          if (U) {
            var G = ue(L),
              J = !G && _o(L),
              ie = !G && !J && ia(L);
            (W = L),
              G || J || ie
                ? ue(N)
                  ? (W = N)
                  : nt(N)
                  ? (W = Kt(N))
                  : J
                  ? ((U = !1), (W = c1(L, !0)))
                  : ie
                  ? ((U = !1), (W = f1(L, !0)))
                  : (W = [])
                : El(L) || fi(L)
                ? ((W = N),
                  fi(N) ? (W = l_(N)) : (!Xe(N) || Fr(N)) && (W = k1(L)))
                : (U = !1);
          }
          U && (C.set(L, W), g(W, L, p, S, C), C.delete(L)), qp(i, c, W);
        }
        function Jy(i, l) {
          var c = i.length;
          if (c) return (l += l < 0 ? c : 0), jr(l, c) ? i[l] : n;
        }
        function e1(i, l, c) {
          l.length
            ? (l = Ye(l, function (S) {
                return ue(S)
                  ? function (C) {
                      return si(C, S.length === 1 ? S[0] : S);
                    }
                  : S;
              }))
            : (l = [Zt]);
          var p = -1;
          l = Ye(l, cn(oe()));
          var g = Qy(i, function (S, C, N) {
            var L = Ye(l, function (F) {
              return F(S);
            });
            return { criteria: L, index: ++p, value: S };
          });
          return QC(g, function (S, C) {
            return Lk(S, C, c);
          });
        }
        function Pk(i, l) {
          return t1(i, l, function (c, p) {
            return Tm(i, p);
          });
        }
        function t1(i, l, c) {
          for (var p = -1, g = l.length, S = {}; ++p < g; ) {
            var C = l[p],
              N = si(i, C);
            c(N, C) && xl(S, go(C, i), N);
          }
          return S;
        }
        function Ok(i) {
          return function (l) {
            return si(l, i);
          };
        }
        function am(i, l, c, p) {
          var g = p ? KC : Xi,
            S = -1,
            C = l.length,
            N = i;
          for (i === l && (l = Kt(l)), c && (N = Ye(i, cn(c))); ++S < C; )
            for (
              var L = 0, F = l[S], W = c ? c(F) : F;
              (L = g(N, W, L, p)) > -1;

            )
              N !== i && gu.call(N, L, 1), gu.call(i, L, 1);
          return i;
        }
        function n1(i, l) {
          for (var c = i ? l.length : 0, p = c - 1; c--; ) {
            var g = l[c];
            if (c == p || g !== S) {
              var S = g;
              jr(g) ? gu.call(i, g, 1) : cm(i, g);
            }
          }
          return i;
        }
        function lm(i, l) {
          return i + wu(Ly() * (l - i + 1));
        }
        function Ek(i, l, c, p) {
          for (var g = -1, S = ft(_u((l - i) / (c || 1)), 0), C = D(S); S--; )
            (C[p ? S : ++g] = i), (i += c);
          return C;
        }
        function sm(i, l) {
          var c = "";
          if (!i || l < 1 || l > Y) return c;
          do l % 2 && (c += i), (l = wu(l / 2)), l && (i += i);
          while (l);
          return c;
        }
        function pe(i, l) {
          return Om(I1(i, l, Zt), i + "");
        }
        function $k(i) {
          return jy(aa(i));
        }
        function Ck(i, l) {
          var c = aa(i);
          return Du(c, li(l, 0, c.length));
        }
        function xl(i, l, c, p) {
          if (!Xe(i)) return i;
          l = go(l, i);
          for (
            var g = -1, S = l.length, C = S - 1, N = i;
            N != null && ++g < S;

          ) {
            var L = hr(l[g]),
              F = c;
            if (L === "__proto__" || L === "constructor" || L === "prototype")
              return i;
            if (g != C) {
              var W = N[L];
              (F = p ? p(W, L, N) : n),
                F === n && (F = Xe(W) ? W : jr(l[g + 1]) ? [] : {});
            }
            yl(N, L, F), (N = N[L]);
          }
          return i;
        }
        var r1 = bu
            ? function (i, l) {
                return bu.set(i, l), i;
              }
            : Zt,
          kk = yu
            ? function (i, l) {
                return yu(i, "toString", {
                  configurable: !0,
                  enumerable: !1,
                  value: Am(l),
                  writable: !0,
                });
              }
            : Zt;
        function Rk(i) {
          return Du(aa(i));
        }
        function Tn(i, l, c) {
          var p = -1,
            g = i.length;
          l < 0 && (l = -l > g ? 0 : g + l),
            (c = c > g ? g : c),
            c < 0 && (c += g),
            (g = l > c ? 0 : (c - l) >>> 0),
            (l >>>= 0);
          for (var S = D(g); ++p < g; ) S[p] = i[p + l];
          return S;
        }
        function Nk(i, l) {
          var c;
          return (
            ho(i, function (p, g, S) {
              return (c = l(p, g, S)), !c;
            }),
            !!c
          );
        }
        function Cu(i, l, c) {
          var p = 0,
            g = i == null ? p : i.length;
          if (typeof l == "number" && l === l && g <= Gt) {
            for (; p < g; ) {
              var S = (p + g) >>> 1,
                C = i[S];
              C !== null && !dn(C) && (c ? C <= l : C < l)
                ? (p = S + 1)
                : (g = S);
            }
            return g;
          }
          return um(i, l, Zt, c);
        }
        function um(i, l, c, p) {
          var g = 0,
            S = i == null ? 0 : i.length;
          if (S === 0) return 0;
          l = c(l);
          for (
            var C = l !== l, N = l === null, L = dn(l), F = l === n;
            g < S;

          ) {
            var W = wu((g + S) / 2),
              U = c(i[W]),
              G = U !== n,
              J = U === null,
              ie = U === U,
              de = dn(U);
            if (C) var ae = p || ie;
            else
              F
                ? (ae = ie && (p || G))
                : N
                ? (ae = ie && G && (p || !J))
                : L
                ? (ae = ie && G && !J && (p || !de))
                : J || de
                ? (ae = !1)
                : (ae = p ? U <= l : U < l);
            ae ? (g = W + 1) : (S = W);
          }
          return Rt(S, ze);
        }
        function o1(i, l) {
          for (var c = -1, p = i.length, g = 0, S = []; ++c < p; ) {
            var C = i[c],
              N = l ? l(C) : C;
            if (!c || !Jn(N, L)) {
              var L = N;
              S[g++] = C === 0 ? 0 : C;
            }
          }
          return S;
        }
        function i1(i) {
          return typeof i == "number" ? i : dn(i) ? ne : +i;
        }
        function fn(i) {
          if (typeof i == "string") return i;
          if (ue(i)) return Ye(i, fn) + "";
          if (dn(i)) return Dy ? Dy.call(i) : "";
          var l = i + "";
          return l == "0" && 1 / i == -Se ? "-0" : l;
        }
        function vo(i, l, c) {
          var p = -1,
            g = lu,
            S = i.length,
            C = !0,
            N = [],
            L = N;
          if (c) (C = !1), (g = Mp);
          else if (S >= o) {
            var F = l ? null : Bk(i);
            if (F) return uu(F);
            (C = !1), (g = dl), (L = new ai());
          } else L = l ? [] : N;
          e: for (; ++p < S; ) {
            var W = i[p],
              U = l ? l(W) : W;
            if (((W = c || W !== 0 ? W : 0), C && U === U)) {
              for (var G = L.length; G--; ) if (L[G] === U) continue e;
              l && L.push(U), N.push(W);
            } else g(L, U, c) || (L !== N && L.push(U), N.push(W));
          }
          return N;
        }
        function cm(i, l) {
          return (
            (l = go(l, i)), (i = T1(i, l)), i == null || delete i[hr(zn(l))]
          );
        }
        function a1(i, l, c, p) {
          return xl(i, l, c(si(i, l)), p);
        }
        function ku(i, l, c, p) {
          for (
            var g = i.length, S = p ? g : -1;
            (p ? S-- : ++S < g) && l(i[S], S, i);

          );
          return c
            ? Tn(i, p ? 0 : S, p ? S + 1 : g)
            : Tn(i, p ? S + 1 : 0, p ? g : S);
        }
        function l1(i, l) {
          var c = i;
          return (
            c instanceof ye && (c = c.value()),
            jp(
              l,
              function (p, g) {
                return g.func.apply(g.thisArg, fo([p], g.args));
              },
              c
            )
          );
        }
        function fm(i, l, c) {
          var p = i.length;
          if (p < 2) return p ? vo(i[0]) : [];
          for (var g = -1, S = D(p); ++g < p; )
            for (var C = i[g], N = -1; ++N < p; )
              N != g && (S[g] = _l(S[g] || C, i[N], l, c));
          return vo(xt(S, 1), l, c);
        }
        function s1(i, l, c) {
          for (var p = -1, g = i.length, S = l.length, C = {}; ++p < g; ) {
            var N = p < S ? l[p] : n;
            c(C, i[p], N);
          }
          return C;
        }
        function dm(i) {
          return nt(i) ? i : [];
        }
        function pm(i) {
          return typeof i == "function" ? i : Zt;
        }
        function go(i, l) {
          return ue(i) ? i : xm(i, l) ? [i] : D1(ke(i));
        }
        var Ik = pe;
        function yo(i, l, c) {
          var p = i.length;
          return (c = c === n ? p : c), !l && c >= p ? i : Tn(i, l, c);
        }
        var u1 =
          _4 ||
          function (i) {
            return bt.clearTimeout(i);
          };
        function c1(i, l) {
          if (l) return i.slice();
          var c = i.length,
            p = Ny ? Ny(c) : new i.constructor(c);
          return i.copy(p), p;
        }
        function mm(i) {
          var l = new i.constructor(i.byteLength);
          return new hu(l).set(new hu(i)), l;
        }
        function Tk(i, l) {
          var c = l ? mm(i.buffer) : i.buffer;
          return new i.constructor(c, i.byteOffset, i.byteLength);
        }
        function zk(i) {
          var l = new i.constructor(i.source, Ae.exec(i));
          return (l.lastIndex = i.lastIndex), l;
        }
        function Ak(i) {
          return gl ? Le(gl.call(i)) : {};
        }
        function f1(i, l) {
          var c = l ? mm(i.buffer) : i.buffer;
          return new i.constructor(c, i.byteOffset, i.length);
        }
        function d1(i, l) {
          if (i !== l) {
            var c = i !== n,
              p = i === null,
              g = i === i,
              S = dn(i),
              C = l !== n,
              N = l === null,
              L = l === l,
              F = dn(l);
            if (
              (!N && !F && !S && i > l) ||
              (S && C && L && !N && !F) ||
              (p && C && L) ||
              (!c && L) ||
              !g
            )
              return 1;
            if (
              (!p && !S && !F && i < l) ||
              (F && c && g && !p && !S) ||
              (N && c && g) ||
              (!C && g) ||
              !L
            )
              return -1;
          }
          return 0;
        }
        function Lk(i, l, c) {
          for (
            var p = -1,
              g = i.criteria,
              S = l.criteria,
              C = g.length,
              N = c.length;
            ++p < C;

          ) {
            var L = d1(g[p], S[p]);
            if (L) {
              if (p >= N) return L;
              var F = c[p];
              return L * (F == "desc" ? -1 : 1);
            }
          }
          return i.index - l.index;
        }
        function p1(i, l, c, p) {
          for (
            var g = -1,
              S = i.length,
              C = c.length,
              N = -1,
              L = l.length,
              F = ft(S - C, 0),
              W = D(L + F),
              U = !p;
            ++N < L;

          )
            W[N] = l[N];
          for (; ++g < C; ) (U || g < S) && (W[c[g]] = i[g]);
          for (; F--; ) W[N++] = i[g++];
          return W;
        }
        function m1(i, l, c, p) {
          for (
            var g = -1,
              S = i.length,
              C = -1,
              N = c.length,
              L = -1,
              F = l.length,
              W = ft(S - N, 0),
              U = D(W + F),
              G = !p;
            ++g < W;

          )
            U[g] = i[g];
          for (var J = g; ++L < F; ) U[J + L] = l[L];
          for (; ++C < N; ) (G || g < S) && (U[J + c[C]] = i[g++]);
          return U;
        }
        function Kt(i, l) {
          var c = -1,
            p = i.length;
          for (l || (l = D(p)); ++c < p; ) l[c] = i[c];
          return l;
        }
        function mr(i, l, c, p) {
          var g = !c;
          c || (c = {});
          for (var S = -1, C = l.length; ++S < C; ) {
            var N = l[S],
              L = p ? p(c[N], i[N], N, c, i) : n;
            L === n && (L = i[N]), g ? Lr(c, N, L) : yl(c, N, L);
          }
          return c;
        }
        function Dk(i, l) {
          return mr(i, bm(i), l);
        }
        function Mk(i, l) {
          return mr(i, $1(i), l);
        }
        function Ru(i, l) {
          return function (c, p) {
            var g = ue(c) ? UC : ak,
              S = l ? l() : {};
            return g(c, i, oe(p, 2), S);
          };
        }
        function na(i) {
          return pe(function (l, c) {
            var p = -1,
              g = c.length,
              S = g > 1 ? c[g - 1] : n,
              C = g > 2 ? c[2] : n;
            for (
              S = i.length > 3 && typeof S == "function" ? (g--, S) : n,
                C && Wt(c[0], c[1], C) && ((S = g < 3 ? n : S), (g = 1)),
                l = Le(l);
              ++p < g;

            ) {
              var N = c[p];
              N && i(l, N, p, S);
            }
            return l;
          });
        }
        function h1(i, l) {
          return function (c, p) {
            if (c == null) return c;
            if (!Qt(c)) return i(c, p);
            for (
              var g = c.length, S = l ? g : -1, C = Le(c);
              (l ? S-- : ++S < g) && p(C[S], S, C) !== !1;

            );
            return c;
          };
        }
        function v1(i) {
          return function (l, c, p) {
            for (var g = -1, S = Le(l), C = p(l), N = C.length; N--; ) {
              var L = C[i ? N : ++g];
              if (c(S[L], L, S) === !1) break;
            }
            return l;
          };
        }
        function jk(i, l, c) {
          var p = l & $,
            g = Sl(i);
          function S() {
            var C = this && this !== bt && this instanceof S ? g : i;
            return C.apply(p ? c : this, arguments);
          }
          return S;
        }
        function g1(i) {
          return function (l) {
            l = ke(l);
            var c = Ki(l) ? qn(l) : n,
              p = c ? c[0] : l.charAt(0),
              g = c ? yo(c, 1).join("") : l.slice(1);
            return p[i]() + g;
          };
        }
        function ra(i) {
          return function (l) {
            return jp(h_(m_(l).replace(kC, "")), i, "");
          };
        }
        function Sl(i) {
          return function () {
            var l = arguments;
            switch (l.length) {
              case 0:
                return new i();
              case 1:
                return new i(l[0]);
              case 2:
                return new i(l[0], l[1]);
              case 3:
                return new i(l[0], l[1], l[2]);
              case 4:
                return new i(l[0], l[1], l[2], l[3]);
              case 5:
                return new i(l[0], l[1], l[2], l[3], l[4]);
              case 6:
                return new i(l[0], l[1], l[2], l[3], l[4], l[5]);
              case 7:
                return new i(l[0], l[1], l[2], l[3], l[4], l[5], l[6]);
            }
            var c = ta(i.prototype),
              p = i.apply(c, l);
            return Xe(p) ? p : c;
          };
        }
        function Fk(i, l, c) {
          var p = Sl(i);
          function g() {
            for (var S = arguments.length, C = D(S), N = S, L = oa(g); N--; )
              C[N] = arguments[N];
            var F = S < 3 && C[0] !== L && C[S - 1] !== L ? [] : po(C, L);
            if (((S -= F.length), S < c))
              return x1(i, l, Nu, g.placeholder, n, C, F, n, n, c - S);
            var W = this && this !== bt && this instanceof g ? p : i;
            return un(W, this, C);
          }
          return g;
        }
        function y1(i) {
          return function (l, c, p) {
            var g = Le(l);
            if (!Qt(l)) {
              var S = oe(c, 3);
              (l = gt(l)),
                (c = function (N) {
                  return S(g[N], N, g);
                });
            }
            var C = i(l, c, p);
            return C > -1 ? g[S ? l[C] : C] : n;
          };
        }
        function _1(i) {
          return Mr(function (l) {
            var c = l.length,
              p = c,
              g = Nn.prototype.thru;
            for (i && l.reverse(); p--; ) {
              var S = l[p];
              if (typeof S != "function") throw new Rn(s);
              if (g && !C && Au(S) == "wrapper") var C = new Nn([], !0);
            }
            for (p = C ? p : c; ++p < c; ) {
              S = l[p];
              var N = Au(S),
                L = N == "wrapper" ? _m(S) : n;
              L &&
              Sm(L[0]) &&
              L[1] == (A | P | I | M) &&
              !L[4].length &&
              L[9] == 1
                ? (C = C[Au(L[0])].apply(C, L[3]))
                : (C = S.length == 1 && Sm(S) ? C[N]() : C.thru(S));
            }
            return function () {
              var F = arguments,
                W = F[0];
              if (C && F.length == 1 && ue(W)) return C.plant(W).value();
              for (var U = 0, G = c ? l[U].apply(this, F) : W; ++U < c; )
                G = l[U].call(this, G);
              return G;
            };
          });
        }
        function Nu(i, l, c, p, g, S, C, N, L, F) {
          var W = l & A,
            U = l & $,
            G = l & _,
            J = l & (P | k),
            ie = l & B,
            de = G ? n : Sl(i);
          function ae() {
            for (var ve = arguments.length, we = D(ve), pn = ve; pn--; )
              we[pn] = arguments[pn];
            if (J)
              var Bt = oa(ae),
                mn = ZC(we, Bt);
            if (
              (p && (we = p1(we, p, g, J)),
              S && (we = m1(we, S, C, J)),
              (ve -= mn),
              J && ve < F)
            ) {
              var rt = po(we, Bt);
              return x1(i, l, Nu, ae.placeholder, c, we, rt, N, L, F - ve);
            }
            var er = U ? c : this,
              Br = G ? er[i] : i;
            return (
              (ve = we.length),
              N ? (we = a6(we, N)) : ie && ve > 1 && we.reverse(),
              W && L < ve && (we.length = L),
              this && this !== bt && this instanceof ae && (Br = de || Sl(Br)),
              Br.apply(er, we)
            );
          }
          return ae;
        }
        function w1(i, l) {
          return function (c, p) {
            return mk(c, i, l(p), {});
          };
        }
        function Iu(i, l) {
          return function (c, p) {
            var g;
            if (c === n && p === n) return l;
            if ((c !== n && (g = c), p !== n)) {
              if (g === n) return p;
              typeof c == "string" || typeof p == "string"
                ? ((c = fn(c)), (p = fn(p)))
                : ((c = i1(c)), (p = i1(p))),
                (g = i(c, p));
            }
            return g;
          };
        }
        function hm(i) {
          return Mr(function (l) {
            return (
              (l = Ye(l, cn(oe()))),
              pe(function (c) {
                var p = this;
                return i(l, function (g) {
                  return un(g, p, c);
                });
              })
            );
          });
        }
        function Tu(i, l) {
          l = l === n ? " " : fn(l);
          var c = l.length;
          if (c < 2) return c ? sm(l, i) : l;
          var p = sm(l, _u(i / Qi(l)));
          return Ki(l) ? yo(qn(p), 0, i).join("") : p.slice(0, i);
        }
        function Wk(i, l, c, p) {
          var g = l & $,
            S = Sl(i);
          function C() {
            for (
              var N = -1,
                L = arguments.length,
                F = -1,
                W = p.length,
                U = D(W + L),
                G = this && this !== bt && this instanceof C ? S : i;
              ++F < W;

            )
              U[F] = p[F];
            for (; L--; ) U[F++] = arguments[++N];
            return un(G, g ? c : this, U);
          }
          return C;
        }
        function b1(i) {
          return function (l, c, p) {
            return (
              p && typeof p != "number" && Wt(l, c, p) && (c = p = n),
              (l = Wr(l)),
              c === n ? ((c = l), (l = 0)) : (c = Wr(c)),
              (p = p === n ? (l < c ? 1 : -1) : Wr(p)),
              Ek(l, c, p, i)
            );
          };
        }
        function zu(i) {
          return function (l, c) {
            return (
              (typeof l == "string" && typeof c == "string") ||
                ((l = An(l)), (c = An(c))),
              i(l, c)
            );
          };
        }
        function x1(i, l, c, p, g, S, C, N, L, F) {
          var W = l & P,
            U = W ? C : n,
            G = W ? n : C,
            J = W ? S : n,
            ie = W ? n : S;
          (l |= W ? I : z), (l &= ~(W ? z : I)), l & w || (l &= ~($ | _));
          var de = [i, l, g, J, U, ie, G, N, L, F],
            ae = c.apply(n, de);
          return Sm(i) && z1(ae, de), (ae.placeholder = p), A1(ae, i, l);
        }
        function vm(i) {
          var l = ct[i];
          return function (c, p) {
            if (
              ((c = An(c)), (p = p == null ? 0 : Rt(fe(p), 292)), p && Ay(c))
            ) {
              var g = (ke(c) + "e").split("e"),
                S = l(g[0] + "e" + (+g[1] + p));
              return (
                (g = (ke(S) + "e").split("e")), +(g[0] + "e" + (+g[1] - p))
              );
            }
            return l(c);
          };
        }
        var Bk =
          Ji && 1 / uu(new Ji([, -0]))[1] == Se
            ? function (i) {
                return new Ji(i);
              }
            : Mm;
        function S1(i) {
          return function (l) {
            var c = Nt(l);
            return c == Ct ? Yp(l) : c == an ? i4(l) : qC(l, i(l));
          };
        }
        function Dr(i, l, c, p, g, S, C, N) {
          var L = l & _;
          if (!L && typeof i != "function") throw new Rn(s);
          var F = p ? p.length : 0;
          if (
            (F || ((l &= ~(I | z)), (p = g = n)),
            (C = C === n ? C : ft(fe(C), 0)),
            (N = N === n ? N : fe(N)),
            (F -= g ? g.length : 0),
            l & z)
          ) {
            var W = p,
              U = g;
            p = g = n;
          }
          var G = L ? n : _m(i),
            J = [i, l, c, p, g, W, U, S, C, N];
          if (
            (G && r6(J, G),
            (i = J[0]),
            (l = J[1]),
            (c = J[2]),
            (p = J[3]),
            (g = J[4]),
            (N = J[9] = J[9] === n ? (L ? 0 : i.length) : ft(J[9] - F, 0)),
            !N && l & (P | k) && (l &= ~(P | k)),
            !l || l == $)
          )
            var ie = jk(i, l, c);
          else
            l == P || l == k
              ? (ie = Fk(i, l, N))
              : (l == I || l == ($ | I)) && !g.length
              ? (ie = Wk(i, l, c, p))
              : (ie = Nu.apply(n, J));
          var de = G ? r1 : z1;
          return A1(de(ie, J), i, l);
        }
        function P1(i, l, c, p) {
          return i === n || (Jn(i, Zi[c]) && !Re.call(p, c)) ? l : i;
        }
        function O1(i, l, c, p, g, S) {
          return (
            Xe(i) && Xe(l) && (S.set(l, i), $u(i, l, n, O1, S), S.delete(l)), i
          );
        }
        function Uk(i) {
          return El(i) ? n : i;
        }
        function E1(i, l, c, p, g, S) {
          var C = c & O,
            N = i.length,
            L = l.length;
          if (N != L && !(C && L > N)) return !1;
          var F = S.get(i),
            W = S.get(l);
          if (F && W) return F == l && W == i;
          var U = -1,
            G = !0,
            J = c & E ? new ai() : n;
          for (S.set(i, l), S.set(l, i); ++U < N; ) {
            var ie = i[U],
              de = l[U];
            if (p) var ae = C ? p(de, ie, U, l, i, S) : p(ie, de, U, i, l, S);
            if (ae !== n) {
              if (ae) continue;
              G = !1;
              break;
            }
            if (J) {
              if (
                !Fp(l, function (ve, we) {
                  if (!dl(J, we) && (ie === ve || g(ie, ve, c, p, S)))
                    return J.push(we);
                })
              ) {
                G = !1;
                break;
              }
            } else if (!(ie === de || g(ie, de, c, p, S))) {
              G = !1;
              break;
            }
          }
          return S.delete(i), S.delete(l), G;
        }
        function Hk(i, l, c, p, g, S, C) {
          switch (c) {
            case kr:
              if (i.byteLength != l.byteLength || i.byteOffset != l.byteOffset)
                return !1;
              (i = i.buffer), (l = l.buffer);
            case dr:
              return !(
                i.byteLength != l.byteLength || !S(new hu(i), new hu(l))
              );
            case Vn:
            case Yn:
            case $r:
              return Jn(+i, +l);
            case so:
              return i.name == l.name && i.message == l.message;
            case Jo:
            case ut:
              return i == l + "";
            case Ct:
              var N = Yp;
            case an:
              var L = p & O;
              if ((N || (N = uu), i.size != l.size && !L)) return !1;
              var F = C.get(i);
              if (F) return F == l;
              (p |= E), C.set(i, l);
              var W = E1(N(i), N(l), p, g, S, C);
              return C.delete(i), W;
            case Bi:
              if (gl) return gl.call(i) == gl.call(l);
          }
          return !1;
        }
        function Vk(i, l, c, p, g, S) {
          var C = c & O,
            N = gm(i),
            L = N.length,
            F = gm(l),
            W = F.length;
          if (L != W && !C) return !1;
          for (var U = L; U--; ) {
            var G = N[U];
            if (!(C ? G in l : Re.call(l, G))) return !1;
          }
          var J = S.get(i),
            ie = S.get(l);
          if (J && ie) return J == l && ie == i;
          var de = !0;
          S.set(i, l), S.set(l, i);
          for (var ae = C; ++U < L; ) {
            G = N[U];
            var ve = i[G],
              we = l[G];
            if (p) var pn = C ? p(we, ve, G, l, i, S) : p(ve, we, G, i, l, S);
            if (!(pn === n ? ve === we || g(ve, we, c, p, S) : pn)) {
              de = !1;
              break;
            }
            ae || (ae = G == "constructor");
          }
          if (de && !ae) {
            var Bt = i.constructor,
              mn = l.constructor;
            Bt != mn &&
              "constructor" in i &&
              "constructor" in l &&
              !(
                typeof Bt == "function" &&
                Bt instanceof Bt &&
                typeof mn == "function" &&
                mn instanceof mn
              ) &&
              (de = !1);
          }
          return S.delete(i), S.delete(l), de;
        }
        function Mr(i) {
          return Om(I1(i, n, W1), i + "");
        }
        function gm(i) {
          return Yy(i, gt, bm);
        }
        function ym(i) {
          return Yy(i, qt, $1);
        }
        var _m = bu
          ? function (i) {
              return bu.get(i);
            }
          : Mm;
        function Au(i) {
          for (
            var l = i.name + "", c = ea[l], p = Re.call(ea, l) ? c.length : 0;
            p--;

          ) {
            var g = c[p],
              S = g.func;
            if (S == null || S == i) return g.name;
          }
          return l;
        }
        function oa(i) {
          var l = Re.call(x, "placeholder") ? x : i;
          return l.placeholder;
        }
        function oe() {
          var i = x.iteratee || Lm;
          return (
            (i = i === Lm ? Ky : i),
            arguments.length ? i(arguments[0], arguments[1]) : i
          );
        }
        function Lu(i, l) {
          var c = i.__data__;
          return Jk(l) ? c[typeof l == "string" ? "string" : "hash"] : c.map;
        }
        function wm(i) {
          for (var l = gt(i), c = l.length; c--; ) {
            var p = l[c],
              g = i[p];
            l[c] = [p, g, R1(g)];
          }
          return l;
        }
        function ui(i, l) {
          var c = n4(i, l);
          return Xy(c) ? c : n;
        }
        function Yk(i) {
          var l = Re.call(i, oi),
            c = i[oi];
          try {
            i[oi] = n;
            var p = !0;
          } catch {}
          var g = pu.call(i);
          return p && (l ? (i[oi] = c) : delete i[oi]), g;
        }
        var bm = Xp
            ? function (i) {
                return i == null
                  ? []
                  : ((i = Le(i)),
                    co(Xp(i), function (l) {
                      return Ty.call(i, l);
                    }));
              }
            : jm,
          $1 = Xp
            ? function (i) {
                for (var l = []; i; ) fo(l, bm(i)), (i = vu(i));
                return l;
              }
            : jm,
          Nt = Ft;
        ((Kp && Nt(new Kp(new ArrayBuffer(1))) != kr) ||
          (ml && Nt(new ml()) != Ct) ||
          (Qp && Nt(Qp.resolve()) != Wi) ||
          (Ji && Nt(new Ji()) != an) ||
          (hl && Nt(new hl()) != tt)) &&
          (Nt = function (i) {
            var l = Ft(i),
              c = l == Xt ? i.constructor : n,
              p = c ? ci(c) : "";
            if (p)
              switch (p) {
                case C4:
                  return kr;
                case k4:
                  return Ct;
                case R4:
                  return Wi;
                case N4:
                  return an;
                case I4:
                  return tt;
              }
            return l;
          });
        function Gk(i, l, c) {
          for (var p = -1, g = c.length; ++p < g; ) {
            var S = c[p],
              C = S.size;
            switch (S.type) {
              case "drop":
                i += C;
                break;
              case "dropRight":
                l -= C;
                break;
              case "take":
                l = Rt(l, i + C);
                break;
              case "takeRight":
                i = ft(i, l - C);
                break;
            }
          }
          return { start: i, end: l };
        }
        function Xk(i) {
          var l = i.match(Ep);
          return l ? l[1].split($p) : [];
        }
        function C1(i, l, c) {
          l = go(l, i);
          for (var p = -1, g = l.length, S = !1; ++p < g; ) {
            var C = hr(l[p]);
            if (!(S = i != null && c(i, C))) break;
            i = i[C];
          }
          return S || ++p != g
            ? S
            : ((g = i == null ? 0 : i.length),
              !!g && Uu(g) && jr(C, g) && (ue(i) || fi(i)));
        }
        function Kk(i) {
          var l = i.length,
            c = new i.constructor(l);
          return (
            l &&
              typeof i[0] == "string" &&
              Re.call(i, "index") &&
              ((c.index = i.index), (c.input = i.input)),
            c
          );
        }
        function k1(i) {
          return typeof i.constructor == "function" && !Pl(i) ? ta(vu(i)) : {};
        }
        function Qk(i, l, c) {
          var p = i.constructor;
          switch (l) {
            case dr:
              return mm(i);
            case Vn:
            case Yn:
              return new p(+i);
            case kr:
              return Tk(i, c);
            case sl:
            case Xn:
            case Rr:
            case ei:
            case ul:
            case cl:
            case kt:
            case ti:
            case ni:
              return f1(i, c);
            case Ct:
              return new p();
            case $r:
            case ut:
              return new p(i);
            case Jo:
              return zk(i);
            case an:
              return new p();
            case Bi:
              return Ak(i);
          }
        }
        function qk(i, l) {
          var c = l.length;
          if (!c) return i;
          var p = c - 1;
          return (
            (l[p] = (c > 1 ? "& " : "") + l[p]),
            (l = l.join(c > 2 ? ", " : " ")),
            i.replace(
              Yi,
              `{
/* [wrapped with ` +
                l +
                `] */
`
            )
          );
        }
        function Zk(i) {
          return ue(i) || fi(i) || !!(zy && i && i[zy]);
        }
        function jr(i, l) {
          var c = typeof i;
          return (
            (l = l ?? Y),
            !!l &&
              (c == "number" || (c != "symbol" && cC.test(i))) &&
              i > -1 &&
              i % 1 == 0 &&
              i < l
          );
        }
        function Wt(i, l, c) {
          if (!Xe(c)) return !1;
          var p = typeof l;
          return (
            p == "number" ? Qt(c) && jr(l, c.length) : p == "string" && l in c
          )
            ? Jn(c[l], i)
            : !1;
        }
        function xm(i, l) {
          if (ue(i)) return !1;
          var c = typeof i;
          return c == "number" ||
            c == "symbol" ||
            c == "boolean" ||
            i == null ||
            dn(i)
            ? !0
            : at.test(i) || !Hi.test(i) || (l != null && i in Le(l));
        }
        function Jk(i) {
          var l = typeof i;
          return l == "string" ||
            l == "number" ||
            l == "symbol" ||
            l == "boolean"
            ? i !== "__proto__"
            : i === null;
        }
        function Sm(i) {
          var l = Au(i),
            c = x[l];
          if (typeof c != "function" || !(l in ye.prototype)) return !1;
          if (i === c) return !0;
          var p = _m(c);
          return !!p && i === p[0];
        }
        function e6(i) {
          return !!Ry && Ry in i;
        }
        var t6 = fu ? Fr : Fm;
        function Pl(i) {
          var l = i && i.constructor,
            c = (typeof l == "function" && l.prototype) || Zi;
          return i === c;
        }
        function R1(i) {
          return i === i && !Xe(i);
        }
        function N1(i, l) {
          return function (c) {
            return c == null ? !1 : c[i] === l && (l !== n || i in Le(c));
          };
        }
        function n6(i) {
          var l = Wu(i, function (p) {
              return c.size === d && c.clear(), p;
            }),
            c = l.cache;
          return l;
        }
        function r6(i, l) {
          var c = i[1],
            p = l[1],
            g = c | p,
            S = g < ($ | _ | A),
            C =
              (p == A && c == P) ||
              (p == A && c == M && i[7].length <= l[8]) ||
              (p == (A | M) && l[7].length <= l[8] && c == P);
          if (!(S || C)) return i;
          p & $ && ((i[2] = l[2]), (g |= c & $ ? 0 : w));
          var N = l[3];
          if (N) {
            var L = i[3];
            (i[3] = L ? p1(L, N, l[4]) : N), (i[4] = L ? po(i[3], m) : l[4]);
          }
          return (
            (N = l[5]),
            N &&
              ((L = i[5]),
              (i[5] = L ? m1(L, N, l[6]) : N),
              (i[6] = L ? po(i[5], m) : l[6])),
            (N = l[7]),
            N && (i[7] = N),
            p & A && (i[8] = i[8] == null ? l[8] : Rt(i[8], l[8])),
            i[9] == null && (i[9] = l[9]),
            (i[0] = l[0]),
            (i[1] = g),
            i
          );
        }
        function o6(i) {
          var l = [];
          if (i != null) for (var c in Le(i)) l.push(c);
          return l;
        }
        function i6(i) {
          return pu.call(i);
        }
        function I1(i, l, c) {
          return (
            (l = ft(l === n ? i.length - 1 : l, 0)),
            function () {
              for (
                var p = arguments, g = -1, S = ft(p.length - l, 0), C = D(S);
                ++g < S;

              )
                C[g] = p[l + g];
              g = -1;
              for (var N = D(l + 1); ++g < l; ) N[g] = p[g];
              return (N[l] = c(C)), un(i, this, N);
            }
          );
        }
        function T1(i, l) {
          return l.length < 2 ? i : si(i, Tn(l, 0, -1));
        }
        function a6(i, l) {
          for (var c = i.length, p = Rt(l.length, c), g = Kt(i); p--; ) {
            var S = l[p];
            i[p] = jr(S, c) ? g[S] : n;
          }
          return i;
        }
        function Pm(i, l) {
          if (
            !(l === "constructor" && typeof i[l] == "function") &&
            l != "__proto__"
          )
            return i[l];
        }
        var z1 = L1(r1),
          Ol =
            b4 ||
            function (i, l) {
              return bt.setTimeout(i, l);
            },
          Om = L1(kk);
        function A1(i, l, c) {
          var p = l + "";
          return Om(i, qk(p, l6(Xk(p), c)));
        }
        function L1(i) {
          var l = 0,
            c = 0;
          return function () {
            var p = O4(),
              g = he - (p - c);
            if (((c = p), g > 0)) {
              if (++l >= Z) return arguments[0];
            } else l = 0;
            return i.apply(n, arguments);
          };
        }
        function Du(i, l) {
          var c = -1,
            p = i.length,
            g = p - 1;
          for (l = l === n ? p : l; ++c < l; ) {
            var S = lm(c, g),
              C = i[S];
            (i[S] = i[c]), (i[c] = C);
          }
          return (i.length = l), i;
        }
        var D1 = n6(function (i) {
          var l = [];
          return (
            i.charCodeAt(0) === 46 && l.push(""),
            i.replace(nu, function (c, p, g, S) {
              l.push(g ? S.replace(Rp, "$1") : p || c);
            }),
            l
          );
        });
        function hr(i) {
          if (typeof i == "string" || dn(i)) return i;
          var l = i + "";
          return l == "0" && 1 / i == -Se ? "-0" : l;
        }
        function ci(i) {
          if (i != null) {
            try {
              return du.call(i);
            } catch {}
            try {
              return i + "";
            } catch {}
          }
          return "";
        }
        function l6(i, l) {
          return (
            kn(Dt, function (c) {
              var p = "_." + c[0];
              l & c[1] && !lu(i, p) && i.push(p);
            }),
            i.sort()
          );
        }
        function M1(i) {
          if (i instanceof ye) return i.clone();
          var l = new Nn(i.__wrapped__, i.__chain__);
          return (
            (l.__actions__ = Kt(i.__actions__)),
            (l.__index__ = i.__index__),
            (l.__values__ = i.__values__),
            l
          );
        }
        function s6(i, l, c) {
          (c ? Wt(i, l, c) : l === n) ? (l = 1) : (l = ft(fe(l), 0));
          var p = i == null ? 0 : i.length;
          if (!p || l < 1) return [];
          for (var g = 0, S = 0, C = D(_u(p / l)); g < p; )
            C[S++] = Tn(i, g, (g += l));
          return C;
        }
        function u6(i) {
          for (
            var l = -1, c = i == null ? 0 : i.length, p = 0, g = [];
            ++l < c;

          ) {
            var S = i[l];
            S && (g[p++] = S);
          }
          return g;
        }
        function c6() {
          var i = arguments.length;
          if (!i) return [];
          for (var l = D(i - 1), c = arguments[0], p = i; p--; )
            l[p - 1] = arguments[p];
          return fo(ue(c) ? Kt(c) : [c], xt(l, 1));
        }
        var f6 = pe(function (i, l) {
            return nt(i) ? _l(i, xt(l, 1, nt, !0)) : [];
          }),
          d6 = pe(function (i, l) {
            var c = zn(l);
            return (
              nt(c) && (c = n), nt(i) ? _l(i, xt(l, 1, nt, !0), oe(c, 2)) : []
            );
          }),
          p6 = pe(function (i, l) {
            var c = zn(l);
            return nt(c) && (c = n), nt(i) ? _l(i, xt(l, 1, nt, !0), n, c) : [];
          });
        function m6(i, l, c) {
          var p = i == null ? 0 : i.length;
          return p
            ? ((l = c || l === n ? 1 : fe(l)), Tn(i, l < 0 ? 0 : l, p))
            : [];
        }
        function h6(i, l, c) {
          var p = i == null ? 0 : i.length;
          return p
            ? ((l = c || l === n ? 1 : fe(l)),
              (l = p - l),
              Tn(i, 0, l < 0 ? 0 : l))
            : [];
        }
        function v6(i, l) {
          return i && i.length ? ku(i, oe(l, 3), !0, !0) : [];
        }
        function g6(i, l) {
          return i && i.length ? ku(i, oe(l, 3), !0) : [];
        }
        function y6(i, l, c, p) {
          var g = i == null ? 0 : i.length;
          return g
            ? (c && typeof c != "number" && Wt(i, l, c) && ((c = 0), (p = g)),
              ck(i, l, c, p))
            : [];
        }
        function j1(i, l, c) {
          var p = i == null ? 0 : i.length;
          if (!p) return -1;
          var g = c == null ? 0 : fe(c);
          return g < 0 && (g = ft(p + g, 0)), su(i, oe(l, 3), g);
        }
        function F1(i, l, c) {
          var p = i == null ? 0 : i.length;
          if (!p) return -1;
          var g = p - 1;
          return (
            c !== n && ((g = fe(c)), (g = c < 0 ? ft(p + g, 0) : Rt(g, p - 1))),
            su(i, oe(l, 3), g, !0)
          );
        }
        function W1(i) {
          var l = i == null ? 0 : i.length;
          return l ? xt(i, 1) : [];
        }
        function _6(i) {
          var l = i == null ? 0 : i.length;
          return l ? xt(i, Se) : [];
        }
        function w6(i, l) {
          var c = i == null ? 0 : i.length;
          return c ? ((l = l === n ? 1 : fe(l)), xt(i, l)) : [];
        }
        function b6(i) {
          for (var l = -1, c = i == null ? 0 : i.length, p = {}; ++l < c; ) {
            var g = i[l];
            p[g[0]] = g[1];
          }
          return p;
        }
        function B1(i) {
          return i && i.length ? i[0] : n;
        }
        function x6(i, l, c) {
          var p = i == null ? 0 : i.length;
          if (!p) return -1;
          var g = c == null ? 0 : fe(c);
          return g < 0 && (g = ft(p + g, 0)), Xi(i, l, g);
        }
        function S6(i) {
          var l = i == null ? 0 : i.length;
          return l ? Tn(i, 0, -1) : [];
        }
        var P6 = pe(function (i) {
            var l = Ye(i, dm);
            return l.length && l[0] === i[0] ? nm(l) : [];
          }),
          O6 = pe(function (i) {
            var l = zn(i),
              c = Ye(i, dm);
            return (
              l === zn(c) ? (l = n) : c.pop(),
              c.length && c[0] === i[0] ? nm(c, oe(l, 2)) : []
            );
          }),
          E6 = pe(function (i) {
            var l = zn(i),
              c = Ye(i, dm);
            return (
              (l = typeof l == "function" ? l : n),
              l && c.pop(),
              c.length && c[0] === i[0] ? nm(c, n, l) : []
            );
          });
        function $6(i, l) {
          return i == null ? "" : S4.call(i, l);
        }
        function zn(i) {
          var l = i == null ? 0 : i.length;
          return l ? i[l - 1] : n;
        }
        function C6(i, l, c) {
          var p = i == null ? 0 : i.length;
          if (!p) return -1;
          var g = p;
          return (
            c !== n && ((g = fe(c)), (g = g < 0 ? ft(p + g, 0) : Rt(g, p - 1))),
            l === l ? l4(i, l, g) : su(i, xy, g, !0)
          );
        }
        function k6(i, l) {
          return i && i.length ? Jy(i, fe(l)) : n;
        }
        var R6 = pe(U1);
        function U1(i, l) {
          return i && i.length && l && l.length ? am(i, l) : i;
        }
        function N6(i, l, c) {
          return i && i.length && l && l.length ? am(i, l, oe(c, 2)) : i;
        }
        function I6(i, l, c) {
          return i && i.length && l && l.length ? am(i, l, n, c) : i;
        }
        var T6 = Mr(function (i, l) {
          var c = i == null ? 0 : i.length,
            p = Zp(i, l);
          return (
            n1(
              i,
              Ye(l, function (g) {
                return jr(g, c) ? +g : g;
              }).sort(d1)
            ),
            p
          );
        });
        function z6(i, l) {
          var c = [];
          if (!(i && i.length)) return c;
          var p = -1,
            g = [],
            S = i.length;
          for (l = oe(l, 3); ++p < S; ) {
            var C = i[p];
            l(C, p, i) && (c.push(C), g.push(p));
          }
          return n1(i, g), c;
        }
        function Em(i) {
          return i == null ? i : $4.call(i);
        }
        function A6(i, l, c) {
          var p = i == null ? 0 : i.length;
          return p
            ? (c && typeof c != "number" && Wt(i, l, c)
                ? ((l = 0), (c = p))
                : ((l = l == null ? 0 : fe(l)), (c = c === n ? p : fe(c))),
              Tn(i, l, c))
            : [];
        }
        function L6(i, l) {
          return Cu(i, l);
        }
        function D6(i, l, c) {
          return um(i, l, oe(c, 2));
        }
        function M6(i, l) {
          var c = i == null ? 0 : i.length;
          if (c) {
            var p = Cu(i, l);
            if (p < c && Jn(i[p], l)) return p;
          }
          return -1;
        }
        function j6(i, l) {
          return Cu(i, l, !0);
        }
        function F6(i, l, c) {
          return um(i, l, oe(c, 2), !0);
        }
        function W6(i, l) {
          var c = i == null ? 0 : i.length;
          if (c) {
            var p = Cu(i, l, !0) - 1;
            if (Jn(i[p], l)) return p;
          }
          return -1;
        }
        function B6(i) {
          return i && i.length ? o1(i) : [];
        }
        function U6(i, l) {
          return i && i.length ? o1(i, oe(l, 2)) : [];
        }
        function H6(i) {
          var l = i == null ? 0 : i.length;
          return l ? Tn(i, 1, l) : [];
        }
        function V6(i, l, c) {
          return i && i.length
            ? ((l = c || l === n ? 1 : fe(l)), Tn(i, 0, l < 0 ? 0 : l))
            : [];
        }
        function Y6(i, l, c) {
          var p = i == null ? 0 : i.length;
          return p
            ? ((l = c || l === n ? 1 : fe(l)),
              (l = p - l),
              Tn(i, l < 0 ? 0 : l, p))
            : [];
        }
        function G6(i, l) {
          return i && i.length ? ku(i, oe(l, 3), !1, !0) : [];
        }
        function X6(i, l) {
          return i && i.length ? ku(i, oe(l, 3)) : [];
        }
        var K6 = pe(function (i) {
            return vo(xt(i, 1, nt, !0));
          }),
          Q6 = pe(function (i) {
            var l = zn(i);
            return nt(l) && (l = n), vo(xt(i, 1, nt, !0), oe(l, 2));
          }),
          q6 = pe(function (i) {
            var l = zn(i);
            return (
              (l = typeof l == "function" ? l : n), vo(xt(i, 1, nt, !0), n, l)
            );
          });
        function Z6(i) {
          return i && i.length ? vo(i) : [];
        }
        function J6(i, l) {
          return i && i.length ? vo(i, oe(l, 2)) : [];
        }
        function eR(i, l) {
          return (
            (l = typeof l == "function" ? l : n),
            i && i.length ? vo(i, n, l) : []
          );
        }
        function $m(i) {
          if (!(i && i.length)) return [];
          var l = 0;
          return (
            (i = co(i, function (c) {
              if (nt(c)) return (l = ft(c.length, l)), !0;
            })),
            Hp(l, function (c) {
              return Ye(i, Wp(c));
            })
          );
        }
        function H1(i, l) {
          if (!(i && i.length)) return [];
          var c = $m(i);
          return l == null
            ? c
            : Ye(c, function (p) {
                return un(l, n, p);
              });
        }
        var tR = pe(function (i, l) {
            return nt(i) ? _l(i, l) : [];
          }),
          nR = pe(function (i) {
            return fm(co(i, nt));
          }),
          rR = pe(function (i) {
            var l = zn(i);
            return nt(l) && (l = n), fm(co(i, nt), oe(l, 2));
          }),
          oR = pe(function (i) {
            var l = zn(i);
            return (l = typeof l == "function" ? l : n), fm(co(i, nt), n, l);
          }),
          iR = pe($m);
        function aR(i, l) {
          return s1(i || [], l || [], yl);
        }
        function lR(i, l) {
          return s1(i || [], l || [], xl);
        }
        var sR = pe(function (i) {
          var l = i.length,
            c = l > 1 ? i[l - 1] : n;
          return (c = typeof c == "function" ? (i.pop(), c) : n), H1(i, c);
        });
        function V1(i) {
          var l = x(i);
          return (l.__chain__ = !0), l;
        }
        function uR(i, l) {
          return l(i), i;
        }
        function Mu(i, l) {
          return l(i);
        }
        var cR = Mr(function (i) {
          var l = i.length,
            c = l ? i[0] : 0,
            p = this.__wrapped__,
            g = function (S) {
              return Zp(S, i);
            };
          return l > 1 ||
            this.__actions__.length ||
            !(p instanceof ye) ||
            !jr(c)
            ? this.thru(g)
            : ((p = p.slice(c, +c + (l ? 1 : 0))),
              p.__actions__.push({ func: Mu, args: [g], thisArg: n }),
              new Nn(p, this.__chain__).thru(function (S) {
                return l && !S.length && S.push(n), S;
              }));
        });
        function fR() {
          return V1(this);
        }
        function dR() {
          return new Nn(this.value(), this.__chain__);
        }
        function pR() {
          this.__values__ === n && (this.__values__ = i_(this.value()));
          var i = this.__index__ >= this.__values__.length,
            l = i ? n : this.__values__[this.__index__++];
          return { done: i, value: l };
        }
        function mR() {
          return this;
        }
        function hR(i) {
          for (var l, c = this; c instanceof Su; ) {
            var p = M1(c);
            (p.__index__ = 0),
              (p.__values__ = n),
              l ? (g.__wrapped__ = p) : (l = p);
            var g = p;
            c = c.__wrapped__;
          }
          return (g.__wrapped__ = i), l;
        }
        function vR() {
          var i = this.__wrapped__;
          if (i instanceof ye) {
            var l = i;
            return (
              this.__actions__.length && (l = new ye(this)),
              (l = l.reverse()),
              l.__actions__.push({ func: Mu, args: [Em], thisArg: n }),
              new Nn(l, this.__chain__)
            );
          }
          return this.thru(Em);
        }
        function gR() {
          return l1(this.__wrapped__, this.__actions__);
        }
        var yR = Ru(function (i, l, c) {
          Re.call(i, c) ? ++i[c] : Lr(i, c, 1);
        });
        function _R(i, l, c) {
          var p = ue(i) ? wy : uk;
          return c && Wt(i, l, c) && (l = n), p(i, oe(l, 3));
        }
        function wR(i, l) {
          var c = ue(i) ? co : Hy;
          return c(i, oe(l, 3));
        }
        var bR = y1(j1),
          xR = y1(F1);
        function SR(i, l) {
          return xt(ju(i, l), 1);
        }
        function PR(i, l) {
          return xt(ju(i, l), Se);
        }
        function OR(i, l, c) {
          return (c = c === n ? 1 : fe(c)), xt(ju(i, l), c);
        }
        function Y1(i, l) {
          var c = ue(i) ? kn : ho;
          return c(i, oe(l, 3));
        }
        function G1(i, l) {
          var c = ue(i) ? HC : Uy;
          return c(i, oe(l, 3));
        }
        var ER = Ru(function (i, l, c) {
          Re.call(i, c) ? i[c].push(l) : Lr(i, c, [l]);
        });
        function $R(i, l, c, p) {
          (i = Qt(i) ? i : aa(i)), (c = c && !p ? fe(c) : 0);
          var g = i.length;
          return (
            c < 0 && (c = ft(g + c, 0)),
            Hu(i) ? c <= g && i.indexOf(l, c) > -1 : !!g && Xi(i, l, c) > -1
          );
        }
        var CR = pe(function (i, l, c) {
            var p = -1,
              g = typeof l == "function",
              S = Qt(i) ? D(i.length) : [];
            return (
              ho(i, function (C) {
                S[++p] = g ? un(l, C, c) : wl(C, l, c);
              }),
              S
            );
          }),
          kR = Ru(function (i, l, c) {
            Lr(i, c, l);
          });
        function ju(i, l) {
          var c = ue(i) ? Ye : Qy;
          return c(i, oe(l, 3));
        }
        function RR(i, l, c, p) {
          return i == null
            ? []
            : (ue(l) || (l = l == null ? [] : [l]),
              (c = p ? n : c),
              ue(c) || (c = c == null ? [] : [c]),
              e1(i, l, c));
        }
        var NR = Ru(
          function (i, l, c) {
            i[c ? 0 : 1].push(l);
          },
          function () {
            return [[], []];
          }
        );
        function IR(i, l, c) {
          var p = ue(i) ? jp : Py,
            g = arguments.length < 3;
          return p(i, oe(l, 4), c, g, ho);
        }
        function TR(i, l, c) {
          var p = ue(i) ? VC : Py,
            g = arguments.length < 3;
          return p(i, oe(l, 4), c, g, Uy);
        }
        function zR(i, l) {
          var c = ue(i) ? co : Hy;
          return c(i, Bu(oe(l, 3)));
        }
        function AR(i) {
          var l = ue(i) ? jy : $k;
          return l(i);
        }
        function LR(i, l, c) {
          (c ? Wt(i, l, c) : l === n) ? (l = 1) : (l = fe(l));
          var p = ue(i) ? ok : Ck;
          return p(i, l);
        }
        function DR(i) {
          var l = ue(i) ? ik : Rk;
          return l(i);
        }
        function MR(i) {
          if (i == null) return 0;
          if (Qt(i)) return Hu(i) ? Qi(i) : i.length;
          var l = Nt(i);
          return l == Ct || l == an ? i.size : om(i).length;
        }
        function jR(i, l, c) {
          var p = ue(i) ? Fp : Nk;
          return c && Wt(i, l, c) && (l = n), p(i, oe(l, 3));
        }
        var FR = pe(function (i, l) {
            if (i == null) return [];
            var c = l.length;
            return (
              c > 1 && Wt(i, l[0], l[1])
                ? (l = [])
                : c > 2 && Wt(l[0], l[1], l[2]) && (l = [l[0]]),
              e1(i, xt(l, 1), [])
            );
          }),
          Fu =
            w4 ||
            function () {
              return bt.Date.now();
            };
        function WR(i, l) {
          if (typeof l != "function") throw new Rn(s);
          return (
            (i = fe(i)),
            function () {
              if (--i < 1) return l.apply(this, arguments);
            }
          );
        }
        function X1(i, l, c) {
          return (
            (l = c ? n : l),
            (l = i && l == null ? i.length : l),
            Dr(i, A, n, n, n, n, l)
          );
        }
        function K1(i, l) {
          var c;
          if (typeof l != "function") throw new Rn(s);
          return (
            (i = fe(i)),
            function () {
              return (
                --i > 0 && (c = l.apply(this, arguments)), i <= 1 && (l = n), c
              );
            }
          );
        }
        var Cm = pe(function (i, l, c) {
            var p = $;
            if (c.length) {
              var g = po(c, oa(Cm));
              p |= I;
            }
            return Dr(i, p, l, c, g);
          }),
          Q1 = pe(function (i, l, c) {
            var p = $ | _;
            if (c.length) {
              var g = po(c, oa(Q1));
              p |= I;
            }
            return Dr(l, p, i, c, g);
          });
        function q1(i, l, c) {
          l = c ? n : l;
          var p = Dr(i, P, n, n, n, n, n, l);
          return (p.placeholder = q1.placeholder), p;
        }
        function Z1(i, l, c) {
          l = c ? n : l;
          var p = Dr(i, k, n, n, n, n, n, l);
          return (p.placeholder = Z1.placeholder), p;
        }
        function J1(i, l, c) {
          var p,
            g,
            S,
            C,
            N,
            L,
            F = 0,
            W = !1,
            U = !1,
            G = !0;
          if (typeof i != "function") throw new Rn(s);
          (l = An(l) || 0),
            Xe(c) &&
              ((W = !!c.leading),
              (U = "maxWait" in c),
              (S = U ? ft(An(c.maxWait) || 0, l) : S),
              (G = "trailing" in c ? !!c.trailing : G));
          function J(rt) {
            var er = p,
              Br = g;
            return (p = g = n), (F = rt), (C = i.apply(Br, er)), C;
          }
          function ie(rt) {
            return (F = rt), (N = Ol(ve, l)), W ? J(rt) : C;
          }
          function de(rt) {
            var er = rt - L,
              Br = rt - F,
              y_ = l - er;
            return U ? Rt(y_, S - Br) : y_;
          }
          function ae(rt) {
            var er = rt - L,
              Br = rt - F;
            return L === n || er >= l || er < 0 || (U && Br >= S);
          }
          function ve() {
            var rt = Fu();
            if (ae(rt)) return we(rt);
            N = Ol(ve, de(rt));
          }
          function we(rt) {
            return (N = n), G && p ? J(rt) : ((p = g = n), C);
          }
          function pn() {
            N !== n && u1(N), (F = 0), (p = L = g = N = n);
          }
          function Bt() {
            return N === n ? C : we(Fu());
          }
          function mn() {
            var rt = Fu(),
              er = ae(rt);
            if (((p = arguments), (g = this), (L = rt), er)) {
              if (N === n) return ie(L);
              if (U) return u1(N), (N = Ol(ve, l)), J(L);
            }
            return N === n && (N = Ol(ve, l)), C;
          }
          return (mn.cancel = pn), (mn.flush = Bt), mn;
        }
        var BR = pe(function (i, l) {
            return By(i, 1, l);
          }),
          UR = pe(function (i, l, c) {
            return By(i, An(l) || 0, c);
          });
        function HR(i) {
          return Dr(i, B);
        }
        function Wu(i, l) {
          if (typeof i != "function" || (l != null && typeof l != "function"))
            throw new Rn(s);
          var c = function () {
            var p = arguments,
              g = l ? l.apply(this, p) : p[0],
              S = c.cache;
            if (S.has(g)) return S.get(g);
            var C = i.apply(this, p);
            return (c.cache = S.set(g, C) || S), C;
          };
          return (c.cache = new (Wu.Cache || Ar)()), c;
        }
        Wu.Cache = Ar;
        function Bu(i) {
          if (typeof i != "function") throw new Rn(s);
          return function () {
            var l = arguments;
            switch (l.length) {
              case 0:
                return !i.call(this);
              case 1:
                return !i.call(this, l[0]);
              case 2:
                return !i.call(this, l[0], l[1]);
              case 3:
                return !i.call(this, l[0], l[1], l[2]);
            }
            return !i.apply(this, l);
          };
        }
        function VR(i) {
          return K1(2, i);
        }
        var YR = Ik(function (i, l) {
            l =
              l.length == 1 && ue(l[0])
                ? Ye(l[0], cn(oe()))
                : Ye(xt(l, 1), cn(oe()));
            var c = l.length;
            return pe(function (p) {
              for (var g = -1, S = Rt(p.length, c); ++g < S; )
                p[g] = l[g].call(this, p[g]);
              return un(i, this, p);
            });
          }),
          km = pe(function (i, l) {
            var c = po(l, oa(km));
            return Dr(i, I, n, l, c);
          }),
          e_ = pe(function (i, l) {
            var c = po(l, oa(e_));
            return Dr(i, z, n, l, c);
          }),
          GR = Mr(function (i, l) {
            return Dr(i, M, n, n, n, l);
          });
        function XR(i, l) {
          if (typeof i != "function") throw new Rn(s);
          return (l = l === n ? l : fe(l)), pe(i, l);
        }
        function KR(i, l) {
          if (typeof i != "function") throw new Rn(s);
          return (
            (l = l == null ? 0 : ft(fe(l), 0)),
            pe(function (c) {
              var p = c[l],
                g = yo(c, 0, l);
              return p && fo(g, p), un(i, this, g);
            })
          );
        }
        function QR(i, l, c) {
          var p = !0,
            g = !0;
          if (typeof i != "function") throw new Rn(s);
          return (
            Xe(c) &&
              ((p = "leading" in c ? !!c.leading : p),
              (g = "trailing" in c ? !!c.trailing : g)),
            J1(i, l, { leading: p, maxWait: l, trailing: g })
          );
        }
        function qR(i) {
          return X1(i, 1);
        }
        function ZR(i, l) {
          return km(pm(l), i);
        }
        function JR() {
          if (!arguments.length) return [];
          var i = arguments[0];
          return ue(i) ? i : [i];
        }
        function eN(i) {
          return In(i, b);
        }
        function tN(i, l) {
          return (l = typeof l == "function" ? l : n), In(i, b, l);
        }
        function nN(i) {
          return In(i, h | b);
        }
        function rN(i, l) {
          return (l = typeof l == "function" ? l : n), In(i, h | b, l);
        }
        function oN(i, l) {
          return l == null || Wy(i, l, gt(l));
        }
        function Jn(i, l) {
          return i === l || (i !== i && l !== l);
        }
        var iN = zu(tm),
          aN = zu(function (i, l) {
            return i >= l;
          }),
          fi = Gy(
            (function () {
              return arguments;
            })()
          )
            ? Gy
            : function (i) {
                return Ze(i) && Re.call(i, "callee") && !Ty.call(i, "callee");
              },
          ue = D.isArray,
          lN = my ? cn(my) : hk;
        function Qt(i) {
          return i != null && Uu(i.length) && !Fr(i);
        }
        function nt(i) {
          return Ze(i) && Qt(i);
        }
        function sN(i) {
          return i === !0 || i === !1 || (Ze(i) && Ft(i) == Vn);
        }
        var _o = x4 || Fm,
          uN = hy ? cn(hy) : vk;
        function cN(i) {
          return Ze(i) && i.nodeType === 1 && !El(i);
        }
        function fN(i) {
          if (i == null) return !0;
          if (
            Qt(i) &&
            (ue(i) ||
              typeof i == "string" ||
              typeof i.splice == "function" ||
              _o(i) ||
              ia(i) ||
              fi(i))
          )
            return !i.length;
          var l = Nt(i);
          if (l == Ct || l == an) return !i.size;
          if (Pl(i)) return !om(i).length;
          for (var c in i) if (Re.call(i, c)) return !1;
          return !0;
        }
        function dN(i, l) {
          return bl(i, l);
        }
        function pN(i, l, c) {
          c = typeof c == "function" ? c : n;
          var p = c ? c(i, l) : n;
          return p === n ? bl(i, l, n, c) : !!p;
        }
        function Rm(i) {
          if (!Ze(i)) return !1;
          var l = Ft(i);
          return (
            l == so ||
            l == Zo ||
            (typeof i.message == "string" &&
              typeof i.name == "string" &&
              !El(i))
          );
        }
        function mN(i) {
          return typeof i == "number" && Ay(i);
        }
        function Fr(i) {
          if (!Xe(i)) return !1;
          var l = Ft(i);
          return l == uo || l == Fi || l == $n || l == Cr;
        }
        function t_(i) {
          return typeof i == "number" && i == fe(i);
        }
        function Uu(i) {
          return typeof i == "number" && i > -1 && i % 1 == 0 && i <= Y;
        }
        function Xe(i) {
          var l = typeof i;
          return i != null && (l == "object" || l == "function");
        }
        function Ze(i) {
          return i != null && typeof i == "object";
        }
        var n_ = vy ? cn(vy) : yk;
        function hN(i, l) {
          return i === l || rm(i, l, wm(l));
        }
        function vN(i, l, c) {
          return (c = typeof c == "function" ? c : n), rm(i, l, wm(l), c);
        }
        function gN(i) {
          return r_(i) && i != +i;
        }
        function yN(i) {
          if (t6(i)) throw new se(a);
          return Xy(i);
        }
        function _N(i) {
          return i === null;
        }
        function wN(i) {
          return i == null;
        }
        function r_(i) {
          return typeof i == "number" || (Ze(i) && Ft(i) == $r);
        }
        function El(i) {
          if (!Ze(i) || Ft(i) != Xt) return !1;
          var l = vu(i);
          if (l === null) return !0;
          var c = Re.call(l, "constructor") && l.constructor;
          return typeof c == "function" && c instanceof c && du.call(c) == v4;
        }
        var Nm = gy ? cn(gy) : _k;
        function bN(i) {
          return t_(i) && i >= -Y && i <= Y;
        }
        var o_ = yy ? cn(yy) : wk;
        function Hu(i) {
          return typeof i == "string" || (!ue(i) && Ze(i) && Ft(i) == ut);
        }
        function dn(i) {
          return typeof i == "symbol" || (Ze(i) && Ft(i) == Bi);
        }
        var ia = _y ? cn(_y) : bk;
        function xN(i) {
          return i === n;
        }
        function SN(i) {
          return Ze(i) && Nt(i) == tt;
        }
        function PN(i) {
          return Ze(i) && Ft(i) == ll;
        }
        var ON = zu(im),
          EN = zu(function (i, l) {
            return i <= l;
          });
        function i_(i) {
          if (!i) return [];
          if (Qt(i)) return Hu(i) ? qn(i) : Kt(i);
          if (pl && i[pl]) return o4(i[pl]());
          var l = Nt(i),
            c = l == Ct ? Yp : l == an ? uu : aa;
          return c(i);
        }
        function Wr(i) {
          if (!i) return i === 0 ? i : 0;
          if (((i = An(i)), i === Se || i === -Se)) {
            var l = i < 0 ? -1 : 1;
            return l * re;
          }
          return i === i ? i : 0;
        }
        function fe(i) {
          var l = Wr(i),
            c = l % 1;
          return l === l ? (c ? l - c : l) : 0;
        }
        function a_(i) {
          return i ? li(fe(i), 0, le) : 0;
        }
        function An(i) {
          if (typeof i == "number") return i;
          if (dn(i)) return ne;
          if (Xe(i)) {
            var l = typeof i.valueOf == "function" ? i.valueOf() : i;
            i = Xe(l) ? l + "" : l;
          }
          if (typeof i != "string") return i === 0 ? i : +i;
          i = Oy(i);
          var c = jt.test(i);
          return c || uC.test(i)
            ? WC(i.slice(2), c ? 2 : 8)
            : Mt.test(i)
            ? ne
            : +i;
        }
        function l_(i) {
          return mr(i, qt(i));
        }
        function $N(i) {
          return i ? li(fe(i), -Y, Y) : i === 0 ? i : 0;
        }
        function ke(i) {
          return i == null ? "" : fn(i);
        }
        var CN = na(function (i, l) {
            if (Pl(l) || Qt(l)) {
              mr(l, gt(l), i);
              return;
            }
            for (var c in l) Re.call(l, c) && yl(i, c, l[c]);
          }),
          s_ = na(function (i, l) {
            mr(l, qt(l), i);
          }),
          Vu = na(function (i, l, c, p) {
            mr(l, qt(l), i, p);
          }),
          kN = na(function (i, l, c, p) {
            mr(l, gt(l), i, p);
          }),
          RN = Mr(Zp);
        function NN(i, l) {
          var c = ta(i);
          return l == null ? c : Fy(c, l);
        }
        var IN = pe(function (i, l) {
            i = Le(i);
            var c = -1,
              p = l.length,
              g = p > 2 ? l[2] : n;
            for (g && Wt(l[0], l[1], g) && (p = 1); ++c < p; )
              for (var S = l[c], C = qt(S), N = -1, L = C.length; ++N < L; ) {
                var F = C[N],
                  W = i[F];
                (W === n || (Jn(W, Zi[F]) && !Re.call(i, F))) && (i[F] = S[F]);
              }
            return i;
          }),
          TN = pe(function (i) {
            return i.push(n, O1), un(u_, n, i);
          });
        function zN(i, l) {
          return by(i, oe(l, 3), pr);
        }
        function AN(i, l) {
          return by(i, oe(l, 3), em);
        }
        function LN(i, l) {
          return i == null ? i : Jp(i, oe(l, 3), qt);
        }
        function DN(i, l) {
          return i == null ? i : Vy(i, oe(l, 3), qt);
        }
        function MN(i, l) {
          return i && pr(i, oe(l, 3));
        }
        function jN(i, l) {
          return i && em(i, oe(l, 3));
        }
        function FN(i) {
          return i == null ? [] : Eu(i, gt(i));
        }
        function WN(i) {
          return i == null ? [] : Eu(i, qt(i));
        }
        function Im(i, l, c) {
          var p = i == null ? n : si(i, l);
          return p === n ? c : p;
        }
        function BN(i, l) {
          return i != null && C1(i, l, fk);
        }
        function Tm(i, l) {
          return i != null && C1(i, l, dk);
        }
        var UN = w1(function (i, l, c) {
            l != null && typeof l.toString != "function" && (l = pu.call(l)),
              (i[l] = c);
          }, Am(Zt)),
          HN = w1(function (i, l, c) {
            l != null && typeof l.toString != "function" && (l = pu.call(l)),
              Re.call(i, l) ? i[l].push(c) : (i[l] = [c]);
          }, oe),
          VN = pe(wl);
        function gt(i) {
          return Qt(i) ? My(i) : om(i);
        }
        function qt(i) {
          return Qt(i) ? My(i, !0) : xk(i);
        }
        function YN(i, l) {
          var c = {};
          return (
            (l = oe(l, 3)),
            pr(i, function (p, g, S) {
              Lr(c, l(p, g, S), p);
            }),
            c
          );
        }
        function GN(i, l) {
          var c = {};
          return (
            (l = oe(l, 3)),
            pr(i, function (p, g, S) {
              Lr(c, g, l(p, g, S));
            }),
            c
          );
        }
        var XN = na(function (i, l, c) {
            $u(i, l, c);
          }),
          u_ = na(function (i, l, c, p) {
            $u(i, l, c, p);
          }),
          KN = Mr(function (i, l) {
            var c = {};
            if (i == null) return c;
            var p = !1;
            (l = Ye(l, function (S) {
              return (S = go(S, i)), p || (p = S.length > 1), S;
            })),
              mr(i, ym(i), c),
              p && (c = In(c, h | v | b, Uk));
            for (var g = l.length; g--; ) cm(c, l[g]);
            return c;
          });
        function QN(i, l) {
          return c_(i, Bu(oe(l)));
        }
        var qN = Mr(function (i, l) {
          return i == null ? {} : Pk(i, l);
        });
        function c_(i, l) {
          if (i == null) return {};
          var c = Ye(ym(i), function (p) {
            return [p];
          });
          return (
            (l = oe(l)),
            t1(i, c, function (p, g) {
              return l(p, g[0]);
            })
          );
        }
        function ZN(i, l, c) {
          l = go(l, i);
          var p = -1,
            g = l.length;
          for (g || ((g = 1), (i = n)); ++p < g; ) {
            var S = i == null ? n : i[hr(l[p])];
            S === n && ((p = g), (S = c)), (i = Fr(S) ? S.call(i) : S);
          }
          return i;
        }
        function JN(i, l, c) {
          return i == null ? i : xl(i, l, c);
        }
        function eI(i, l, c, p) {
          return (
            (p = typeof p == "function" ? p : n), i == null ? i : xl(i, l, c, p)
          );
        }
        var f_ = S1(gt),
          d_ = S1(qt);
        function tI(i, l, c) {
          var p = ue(i),
            g = p || _o(i) || ia(i);
          if (((l = oe(l, 4)), c == null)) {
            var S = i && i.constructor;
            g
              ? (c = p ? new S() : [])
              : Xe(i)
              ? (c = Fr(S) ? ta(vu(i)) : {})
              : (c = {});
          }
          return (
            (g ? kn : pr)(i, function (C, N, L) {
              return l(c, C, N, L);
            }),
            c
          );
        }
        function nI(i, l) {
          return i == null ? !0 : cm(i, l);
        }
        function rI(i, l, c) {
          return i == null ? i : a1(i, l, pm(c));
        }
        function oI(i, l, c, p) {
          return (
            (p = typeof p == "function" ? p : n),
            i == null ? i : a1(i, l, pm(c), p)
          );
        }
        function aa(i) {
          return i == null ? [] : Vp(i, gt(i));
        }
        function iI(i) {
          return i == null ? [] : Vp(i, qt(i));
        }
        function aI(i, l, c) {
          return (
            c === n && ((c = l), (l = n)),
            c !== n && ((c = An(c)), (c = c === c ? c : 0)),
            l !== n && ((l = An(l)), (l = l === l ? l : 0)),
            li(An(i), l, c)
          );
        }
        function lI(i, l, c) {
          return (
            (l = Wr(l)),
            c === n ? ((c = l), (l = 0)) : (c = Wr(c)),
            (i = An(i)),
            pk(i, l, c)
          );
        }
        function sI(i, l, c) {
          if (
            (c && typeof c != "boolean" && Wt(i, l, c) && (l = c = n),
            c === n &&
              (typeof l == "boolean"
                ? ((c = l), (l = n))
                : typeof i == "boolean" && ((c = i), (i = n))),
            i === n && l === n
              ? ((i = 0), (l = 1))
              : ((i = Wr(i)), l === n ? ((l = i), (i = 0)) : (l = Wr(l))),
            i > l)
          ) {
            var p = i;
            (i = l), (l = p);
          }
          if (c || i % 1 || l % 1) {
            var g = Ly();
            return Rt(i + g * (l - i + FC("1e-" + ((g + "").length - 1))), l);
          }
          return lm(i, l);
        }
        var uI = ra(function (i, l, c) {
          return (l = l.toLowerCase()), i + (c ? p_(l) : l);
        });
        function p_(i) {
          return zm(ke(i).toLowerCase());
        }
        function m_(i) {
          return (i = ke(i)), i && i.replace(fC, JC).replace(RC, "");
        }
        function cI(i, l, c) {
          (i = ke(i)), (l = fn(l));
          var p = i.length;
          c = c === n ? p : li(fe(c), 0, p);
          var g = c;
          return (c -= l.length), c >= 0 && i.slice(c, g) == l;
        }
        function fI(i) {
          return (i = ke(i)), i && Qn.test(i) ? i.replace(fl, e4) : i;
        }
        function dI(i) {
          return (i = ke(i)), i && sn.test(i) ? i.replace(Tr, "\\$&") : i;
        }
        var pI = ra(function (i, l, c) {
            return i + (c ? "-" : "") + l.toLowerCase();
          }),
          mI = ra(function (i, l, c) {
            return i + (c ? " " : "") + l.toLowerCase();
          }),
          hI = g1("toLowerCase");
        function vI(i, l, c) {
          (i = ke(i)), (l = fe(l));
          var p = l ? Qi(i) : 0;
          if (!l || p >= l) return i;
          var g = (l - p) / 2;
          return Tu(wu(g), c) + i + Tu(_u(g), c);
        }
        function gI(i, l, c) {
          (i = ke(i)), (l = fe(l));
          var p = l ? Qi(i) : 0;
          return l && p < l ? i + Tu(l - p, c) : i;
        }
        function yI(i, l, c) {
          (i = ke(i)), (l = fe(l));
          var p = l ? Qi(i) : 0;
          return l && p < l ? Tu(l - p, c) + i : i;
        }
        function _I(i, l, c) {
          return (
            c || l == null ? (l = 0) : l && (l = +l),
            E4(ke(i).replace(Vi, ""), l || 0)
          );
        }
        function wI(i, l, c) {
          return (
            (c ? Wt(i, l, c) : l === n) ? (l = 1) : (l = fe(l)), sm(ke(i), l)
          );
        }
        function bI() {
          var i = arguments,
            l = ke(i[0]);
          return i.length < 3 ? l : l.replace(i[1], i[2]);
        }
        var xI = ra(function (i, l, c) {
          return i + (c ? "_" : "") + l.toLowerCase();
        });
        function SI(i, l, c) {
          return (
            c && typeof c != "number" && Wt(i, l, c) && (l = c = n),
            (c = c === n ? le : c >>> 0),
            c
              ? ((i = ke(i)),
                i &&
                (typeof l == "string" || (l != null && !Nm(l))) &&
                ((l = fn(l)), !l && Ki(i))
                  ? yo(qn(i), 0, c)
                  : i.split(l, c))
              : []
          );
        }
        var PI = ra(function (i, l, c) {
          return i + (c ? " " : "") + zm(l);
        });
        function OI(i, l, c) {
          return (
            (i = ke(i)),
            (c = c == null ? 0 : li(fe(c), 0, i.length)),
            (l = fn(l)),
            i.slice(c, c + l.length) == l
          );
        }
        function EI(i, l, c) {
          var p = x.templateSettings;
          c && Wt(i, l, c) && (l = n), (i = ke(i)), (l = Vu({}, l, p, P1));
          var g = Vu({}, l.imports, p.imports, P1),
            S = gt(g),
            C = Vp(g, S),
            N,
            L,
            F = 0,
            W = l.interpolate || ou,
            U = "__p += '",
            G = Gp(
              (l.escape || ou).source +
                "|" +
                W.source +
                "|" +
                (W === tu ? te : ou).source +
                "|" +
                (l.evaluate || ou).source +
                "|$",
              "g"
            ),
            J =
              "//# sourceURL=" +
              (Re.call(l, "sourceURL")
                ? (l.sourceURL + "").replace(/\s/g, " ")
                : "lodash.templateSources[" + ++AC + "]") +
              `
`;
          i.replace(G, function (ae, ve, we, pn, Bt, mn) {
            return (
              we || (we = pn),
              (U += i.slice(F, mn).replace(dC, t4)),
              ve &&
                ((N = !0),
                (U +=
                  `' +
__e(` +
                  ve +
                  `) +
'`)),
              Bt &&
                ((L = !0),
                (U +=
                  `';
` +
                  Bt +
                  `;
__p += '`)),
              we &&
                (U +=
                  `' +
((__t = (` +
                  we +
                  `)) == null ? '' : __t) +
'`),
              (F = mn + ae.length),
              ae
            );
          }),
            (U += `';
`);
          var ie = Re.call(l, "variable") && l.variable;
          if (!ie)
            U =
              `with (obj) {
` +
              U +
              `
}
`;
          else if (kp.test(ie)) throw new se(u);
          (U = (L ? U.replace(Pp, "") : U)
            .replace(Ui, "$1")
            .replace(ln, "$1;")),
            (U =
              "function(" +
              (ie || "obj") +
              `) {
` +
              (ie
                ? ""
                : `obj || (obj = {});
`) +
              "var __t, __p = ''" +
              (N ? ", __e = _.escape" : "") +
              (L
                ? `, __j = Array.prototype.join;
function print() { __p += __j.call(arguments, '') }
`
                : `;
`) +
              U +
              `return __p
}`);
          var de = v_(function () {
            return Oe(S, J + "return " + U).apply(n, C);
          });
          if (((de.source = U), Rm(de))) throw de;
          return de;
        }
        function $I(i) {
          return ke(i).toLowerCase();
        }
        function CI(i) {
          return ke(i).toUpperCase();
        }
        function kI(i, l, c) {
          if (((i = ke(i)), i && (c || l === n))) return Oy(i);
          if (!i || !(l = fn(l))) return i;
          var p = qn(i),
            g = qn(l),
            S = Ey(p, g),
            C = $y(p, g) + 1;
          return yo(p, S, C).join("");
        }
        function RI(i, l, c) {
          if (((i = ke(i)), i && (c || l === n))) return i.slice(0, ky(i) + 1);
          if (!i || !(l = fn(l))) return i;
          var p = qn(i),
            g = $y(p, qn(l)) + 1;
          return yo(p, 0, g).join("");
        }
        function NI(i, l, c) {
          if (((i = ke(i)), i && (c || l === n))) return i.replace(Vi, "");
          if (!i || !(l = fn(l))) return i;
          var p = qn(i),
            g = Ey(p, qn(l));
          return yo(p, g).join("");
        }
        function II(i, l) {
          var c = H,
            p = q;
          if (Xe(l)) {
            var g = "separator" in l ? l.separator : g;
            (c = "length" in l ? fe(l.length) : c),
              (p = "omission" in l ? fn(l.omission) : p);
          }
          i = ke(i);
          var S = i.length;
          if (Ki(i)) {
            var C = qn(i);
            S = C.length;
          }
          if (c >= S) return i;
          var N = c - Qi(p);
          if (N < 1) return p;
          var L = C ? yo(C, 0, N).join("") : i.slice(0, N);
          if (g === n) return L + p;
          if ((C && (N += L.length - N), Nm(g))) {
            if (i.slice(N).search(g)) {
              var F,
                W = L;
              for (
                g.global || (g = Gp(g.source, ke(Ae.exec(g)) + "g")),
                  g.lastIndex = 0;
                (F = g.exec(W));

              )
                var U = F.index;
              L = L.slice(0, U === n ? N : U);
            }
          } else if (i.indexOf(fn(g), N) != N) {
            var G = L.lastIndexOf(g);
            G > -1 && (L = L.slice(0, G));
          }
          return L + p;
        }
        function TI(i) {
          return (i = ke(i)), i && Kn.test(i) ? i.replace(Nr, s4) : i;
        }
        var zI = ra(function (i, l, c) {
            return i + (c ? " " : "") + l.toUpperCase();
          }),
          zm = g1("toUpperCase");
        function h_(i, l, c) {
          return (
            (i = ke(i)),
            (l = c ? n : l),
            l === n ? (r4(i) ? f4(i) : XC(i)) : i.match(l) || []
          );
        }
        var v_ = pe(function (i, l) {
            try {
              return un(i, n, l);
            } catch (c) {
              return Rm(c) ? c : new se(c);
            }
          }),
          AI = Mr(function (i, l) {
            return (
              kn(l, function (c) {
                (c = hr(c)), Lr(i, c, Cm(i[c], i));
              }),
              i
            );
          });
        function LI(i) {
          var l = i == null ? 0 : i.length,
            c = oe();
          return (
            (i = l
              ? Ye(i, function (p) {
                  if (typeof p[1] != "function") throw new Rn(s);
                  return [c(p[0]), p[1]];
                })
              : []),
            pe(function (p) {
              for (var g = -1; ++g < l; ) {
                var S = i[g];
                if (un(S[0], this, p)) return un(S[1], this, p);
              }
            })
          );
        }
        function DI(i) {
          return sk(In(i, h));
        }
        function Am(i) {
          return function () {
            return i;
          };
        }
        function MI(i, l) {
          return i == null || i !== i ? l : i;
        }
        var jI = _1(),
          FI = _1(!0);
        function Zt(i) {
          return i;
        }
        function Lm(i) {
          return Ky(typeof i == "function" ? i : In(i, h));
        }
        function WI(i) {
          return qy(In(i, h));
        }
        function BI(i, l) {
          return Zy(i, In(l, h));
        }
        var UI = pe(function (i, l) {
            return function (c) {
              return wl(c, i, l);
            };
          }),
          HI = pe(function (i, l) {
            return function (c) {
              return wl(i, c, l);
            };
          });
        function Dm(i, l, c) {
          var p = gt(l),
            g = Eu(l, p);
          c == null &&
            !(Xe(l) && (g.length || !p.length)) &&
            ((c = l), (l = i), (i = this), (g = Eu(l, gt(l))));
          var S = !(Xe(c) && "chain" in c) || !!c.chain,
            C = Fr(i);
          return (
            kn(g, function (N) {
              var L = l[N];
              (i[N] = L),
                C &&
                  (i.prototype[N] = function () {
                    var F = this.__chain__;
                    if (S || F) {
                      var W = i(this.__wrapped__),
                        U = (W.__actions__ = Kt(this.__actions__));
                      return (
                        U.push({ func: L, args: arguments, thisArg: i }),
                        (W.__chain__ = F),
                        W
                      );
                    }
                    return L.apply(i, fo([this.value()], arguments));
                  });
            }),
            i
          );
        }
        function VI() {
          return bt._ === this && (bt._ = g4), this;
        }
        function Mm() {}
        function YI(i) {
          return (
            (i = fe(i)),
            pe(function (l) {
              return Jy(l, i);
            })
          );
        }
        var GI = hm(Ye),
          XI = hm(wy),
          KI = hm(Fp);
        function g_(i) {
          return xm(i) ? Wp(hr(i)) : Ok(i);
        }
        function QI(i) {
          return function (l) {
            return i == null ? n : si(i, l);
          };
        }
        var qI = b1(),
          ZI = b1(!0);
        function jm() {
          return [];
        }
        function Fm() {
          return !1;
        }
        function JI() {
          return {};
        }
        function e8() {
          return "";
        }
        function t8() {
          return !0;
        }
        function n8(i, l) {
          if (((i = fe(i)), i < 1 || i > Y)) return [];
          var c = le,
            p = Rt(i, le);
          (l = oe(l)), (i -= le);
          for (var g = Hp(p, l); ++c < i; ) l(c);
          return g;
        }
        function r8(i) {
          return ue(i) ? Ye(i, hr) : dn(i) ? [i] : Kt(D1(ke(i)));
        }
        function o8(i) {
          var l = ++h4;
          return ke(i) + l;
        }
        var i8 = Iu(function (i, l) {
            return i + l;
          }, 0),
          a8 = vm("ceil"),
          l8 = Iu(function (i, l) {
            return i / l;
          }, 1),
          s8 = vm("floor");
        function u8(i) {
          return i && i.length ? Ou(i, Zt, tm) : n;
        }
        function c8(i, l) {
          return i && i.length ? Ou(i, oe(l, 2), tm) : n;
        }
        function f8(i) {
          return Sy(i, Zt);
        }
        function d8(i, l) {
          return Sy(i, oe(l, 2));
        }
        function p8(i) {
          return i && i.length ? Ou(i, Zt, im) : n;
        }
        function m8(i, l) {
          return i && i.length ? Ou(i, oe(l, 2), im) : n;
        }
        var h8 = Iu(function (i, l) {
            return i * l;
          }, 1),
          v8 = vm("round"),
          g8 = Iu(function (i, l) {
            return i - l;
          }, 0);
        function y8(i) {
          return i && i.length ? Up(i, Zt) : 0;
        }
        function _8(i, l) {
          return i && i.length ? Up(i, oe(l, 2)) : 0;
        }
        return (
          (x.after = WR),
          (x.ary = X1),
          (x.assign = CN),
          (x.assignIn = s_),
          (x.assignInWith = Vu),
          (x.assignWith = kN),
          (x.at = RN),
          (x.before = K1),
          (x.bind = Cm),
          (x.bindAll = AI),
          (x.bindKey = Q1),
          (x.castArray = JR),
          (x.chain = V1),
          (x.chunk = s6),
          (x.compact = u6),
          (x.concat = c6),
          (x.cond = LI),
          (x.conforms = DI),
          (x.constant = Am),
          (x.countBy = yR),
          (x.create = NN),
          (x.curry = q1),
          (x.curryRight = Z1),
          (x.debounce = J1),
          (x.defaults = IN),
          (x.defaultsDeep = TN),
          (x.defer = BR),
          (x.delay = UR),
          (x.difference = f6),
          (x.differenceBy = d6),
          (x.differenceWith = p6),
          (x.drop = m6),
          (x.dropRight = h6),
          (x.dropRightWhile = v6),
          (x.dropWhile = g6),
          (x.fill = y6),
          (x.filter = wR),
          (x.flatMap = SR),
          (x.flatMapDeep = PR),
          (x.flatMapDepth = OR),
          (x.flatten = W1),
          (x.flattenDeep = _6),
          (x.flattenDepth = w6),
          (x.flip = HR),
          (x.flow = jI),
          (x.flowRight = FI),
          (x.fromPairs = b6),
          (x.functions = FN),
          (x.functionsIn = WN),
          (x.groupBy = ER),
          (x.initial = S6),
          (x.intersection = P6),
          (x.intersectionBy = O6),
          (x.intersectionWith = E6),
          (x.invert = UN),
          (x.invertBy = HN),
          (x.invokeMap = CR),
          (x.iteratee = Lm),
          (x.keyBy = kR),
          (x.keys = gt),
          (x.keysIn = qt),
          (x.map = ju),
          (x.mapKeys = YN),
          (x.mapValues = GN),
          (x.matches = WI),
          (x.matchesProperty = BI),
          (x.memoize = Wu),
          (x.merge = XN),
          (x.mergeWith = u_),
          (x.method = UI),
          (x.methodOf = HI),
          (x.mixin = Dm),
          (x.negate = Bu),
          (x.nthArg = YI),
          (x.omit = KN),
          (x.omitBy = QN),
          (x.once = VR),
          (x.orderBy = RR),
          (x.over = GI),
          (x.overArgs = YR),
          (x.overEvery = XI),
          (x.overSome = KI),
          (x.partial = km),
          (x.partialRight = e_),
          (x.partition = NR),
          (x.pick = qN),
          (x.pickBy = c_),
          (x.property = g_),
          (x.propertyOf = QI),
          (x.pull = R6),
          (x.pullAll = U1),
          (x.pullAllBy = N6),
          (x.pullAllWith = I6),
          (x.pullAt = T6),
          (x.range = qI),
          (x.rangeRight = ZI),
          (x.rearg = GR),
          (x.reject = zR),
          (x.remove = z6),
          (x.rest = XR),
          (x.reverse = Em),
          (x.sampleSize = LR),
          (x.set = JN),
          (x.setWith = eI),
          (x.shuffle = DR),
          (x.slice = A6),
          (x.sortBy = FR),
          (x.sortedUniq = B6),
          (x.sortedUniqBy = U6),
          (x.split = SI),
          (x.spread = KR),
          (x.tail = H6),
          (x.take = V6),
          (x.takeRight = Y6),
          (x.takeRightWhile = G6),
          (x.takeWhile = X6),
          (x.tap = uR),
          (x.throttle = QR),
          (x.thru = Mu),
          (x.toArray = i_),
          (x.toPairs = f_),
          (x.toPairsIn = d_),
          (x.toPath = r8),
          (x.toPlainObject = l_),
          (x.transform = tI),
          (x.unary = qR),
          (x.union = K6),
          (x.unionBy = Q6),
          (x.unionWith = q6),
          (x.uniq = Z6),
          (x.uniqBy = J6),
          (x.uniqWith = eR),
          (x.unset = nI),
          (x.unzip = $m),
          (x.unzipWith = H1),
          (x.update = rI),
          (x.updateWith = oI),
          (x.values = aa),
          (x.valuesIn = iI),
          (x.without = tR),
          (x.words = h_),
          (x.wrap = ZR),
          (x.xor = nR),
          (x.xorBy = rR),
          (x.xorWith = oR),
          (x.zip = iR),
          (x.zipObject = aR),
          (x.zipObjectDeep = lR),
          (x.zipWith = sR),
          (x.entries = f_),
          (x.entriesIn = d_),
          (x.extend = s_),
          (x.extendWith = Vu),
          Dm(x, x),
          (x.add = i8),
          (x.attempt = v_),
          (x.camelCase = uI),
          (x.capitalize = p_),
          (x.ceil = a8),
          (x.clamp = aI),
          (x.clone = eN),
          (x.cloneDeep = nN),
          (x.cloneDeepWith = rN),
          (x.cloneWith = tN),
          (x.conformsTo = oN),
          (x.deburr = m_),
          (x.defaultTo = MI),
          (x.divide = l8),
          (x.endsWith = cI),
          (x.eq = Jn),
          (x.escape = fI),
          (x.escapeRegExp = dI),
          (x.every = _R),
          (x.find = bR),
          (x.findIndex = j1),
          (x.findKey = zN),
          (x.findLast = xR),
          (x.findLastIndex = F1),
          (x.findLastKey = AN),
          (x.floor = s8),
          (x.forEach = Y1),
          (x.forEachRight = G1),
          (x.forIn = LN),
          (x.forInRight = DN),
          (x.forOwn = MN),
          (x.forOwnRight = jN),
          (x.get = Im),
          (x.gt = iN),
          (x.gte = aN),
          (x.has = BN),
          (x.hasIn = Tm),
          (x.head = B1),
          (x.identity = Zt),
          (x.includes = $R),
          (x.indexOf = x6),
          (x.inRange = lI),
          (x.invoke = VN),
          (x.isArguments = fi),
          (x.isArray = ue),
          (x.isArrayBuffer = lN),
          (x.isArrayLike = Qt),
          (x.isArrayLikeObject = nt),
          (x.isBoolean = sN),
          (x.isBuffer = _o),
          (x.isDate = uN),
          (x.isElement = cN),
          (x.isEmpty = fN),
          (x.isEqual = dN),
          (x.isEqualWith = pN),
          (x.isError = Rm),
          (x.isFinite = mN),
          (x.isFunction = Fr),
          (x.isInteger = t_),
          (x.isLength = Uu),
          (x.isMap = n_),
          (x.isMatch = hN),
          (x.isMatchWith = vN),
          (x.isNaN = gN),
          (x.isNative = yN),
          (x.isNil = wN),
          (x.isNull = _N),
          (x.isNumber = r_),
          (x.isObject = Xe),
          (x.isObjectLike = Ze),
          (x.isPlainObject = El),
          (x.isRegExp = Nm),
          (x.isSafeInteger = bN),
          (x.isSet = o_),
          (x.isString = Hu),
          (x.isSymbol = dn),
          (x.isTypedArray = ia),
          (x.isUndefined = xN),
          (x.isWeakMap = SN),
          (x.isWeakSet = PN),
          (x.join = $6),
          (x.kebabCase = pI),
          (x.last = zn),
          (x.lastIndexOf = C6),
          (x.lowerCase = mI),
          (x.lowerFirst = hI),
          (x.lt = ON),
          (x.lte = EN),
          (x.max = u8),
          (x.maxBy = c8),
          (x.mean = f8),
          (x.meanBy = d8),
          (x.min = p8),
          (x.minBy = m8),
          (x.stubArray = jm),
          (x.stubFalse = Fm),
          (x.stubObject = JI),
          (x.stubString = e8),
          (x.stubTrue = t8),
          (x.multiply = h8),
          (x.nth = k6),
          (x.noConflict = VI),
          (x.noop = Mm),
          (x.now = Fu),
          (x.pad = vI),
          (x.padEnd = gI),
          (x.padStart = yI),
          (x.parseInt = _I),
          (x.random = sI),
          (x.reduce = IR),
          (x.reduceRight = TR),
          (x.repeat = wI),
          (x.replace = bI),
          (x.result = ZN),
          (x.round = v8),
          (x.runInContext = T),
          (x.sample = AR),
          (x.size = MR),
          (x.snakeCase = xI),
          (x.some = jR),
          (x.sortedIndex = L6),
          (x.sortedIndexBy = D6),
          (x.sortedIndexOf = M6),
          (x.sortedLastIndex = j6),
          (x.sortedLastIndexBy = F6),
          (x.sortedLastIndexOf = W6),
          (x.startCase = PI),
          (x.startsWith = OI),
          (x.subtract = g8),
          (x.sum = y8),
          (x.sumBy = _8),
          (x.template = EI),
          (x.times = n8),
          (x.toFinite = Wr),
          (x.toInteger = fe),
          (x.toLength = a_),
          (x.toLower = $I),
          (x.toNumber = An),
          (x.toSafeInteger = $N),
          (x.toString = ke),
          (x.toUpper = CI),
          (x.trim = kI),
          (x.trimEnd = RI),
          (x.trimStart = NI),
          (x.truncate = II),
          (x.unescape = TI),
          (x.uniqueId = o8),
          (x.upperCase = zI),
          (x.upperFirst = zm),
          (x.each = Y1),
          (x.eachRight = G1),
          (x.first = B1),
          Dm(
            x,
            (function () {
              var i = {};
              return (
                pr(x, function (l, c) {
                  Re.call(x.prototype, c) || (i[c] = l);
                }),
                i
              );
            })(),
            { chain: !1 }
          ),
          (x.VERSION = r),
          kn(
            [
              "bind",
              "bindKey",
              "curry",
              "curryRight",
              "partial",
              "partialRight",
            ],
            function (i) {
              x[i].placeholder = x;
            }
          ),
          kn(["drop", "take"], function (i, l) {
            (ye.prototype[i] = function (c) {
              c = c === n ? 1 : ft(fe(c), 0);
              var p = this.__filtered__ && !l ? new ye(this) : this.clone();
              return (
                p.__filtered__
                  ? (p.__takeCount__ = Rt(c, p.__takeCount__))
                  : p.__views__.push({
                      size: Rt(c, le),
                      type: i + (p.__dir__ < 0 ? "Right" : ""),
                    }),
                p
              );
            }),
              (ye.prototype[i + "Right"] = function (c) {
                return this.reverse()[i](c).reverse();
              });
          }),
          kn(["filter", "map", "takeWhile"], function (i, l) {
            var c = l + 1,
              p = c == xe || c == ce;
            ye.prototype[i] = function (g) {
              var S = this.clone();
              return (
                S.__iteratees__.push({ iteratee: oe(g, 3), type: c }),
                (S.__filtered__ = S.__filtered__ || p),
                S
              );
            };
          }),
          kn(["head", "last"], function (i, l) {
            var c = "take" + (l ? "Right" : "");
            ye.prototype[i] = function () {
              return this[c](1).value()[0];
            };
          }),
          kn(["initial", "tail"], function (i, l) {
            var c = "drop" + (l ? "" : "Right");
            ye.prototype[i] = function () {
              return this.__filtered__ ? new ye(this) : this[c](1);
            };
          }),
          (ye.prototype.compact = function () {
            return this.filter(Zt);
          }),
          (ye.prototype.find = function (i) {
            return this.filter(i).head();
          }),
          (ye.prototype.findLast = function (i) {
            return this.reverse().find(i);
          }),
          (ye.prototype.invokeMap = pe(function (i, l) {
            return typeof i == "function"
              ? new ye(this)
              : this.map(function (c) {
                  return wl(c, i, l);
                });
          })),
          (ye.prototype.reject = function (i) {
            return this.filter(Bu(oe(i)));
          }),
          (ye.prototype.slice = function (i, l) {
            i = fe(i);
            var c = this;
            return c.__filtered__ && (i > 0 || l < 0)
              ? new ye(c)
              : (i < 0 ? (c = c.takeRight(-i)) : i && (c = c.drop(i)),
                l !== n &&
                  ((l = fe(l)), (c = l < 0 ? c.dropRight(-l) : c.take(l - i))),
                c);
          }),
          (ye.prototype.takeRightWhile = function (i) {
            return this.reverse().takeWhile(i).reverse();
          }),
          (ye.prototype.toArray = function () {
            return this.take(le);
          }),
          pr(ye.prototype, function (i, l) {
            var c = /^(?:filter|find|map|reject)|While$/.test(l),
              p = /^(?:head|last)$/.test(l),
              g = x[p ? "take" + (l == "last" ? "Right" : "") : l],
              S = p || /^find/.test(l);
            g &&
              (x.prototype[l] = function () {
                var C = this.__wrapped__,
                  N = p ? [1] : arguments,
                  L = C instanceof ye,
                  F = N[0],
                  W = L || ue(C),
                  U = function (ve) {
                    var we = g.apply(x, fo([ve], N));
                    return p && G ? we[0] : we;
                  };
                W &&
                  c &&
                  typeof F == "function" &&
                  F.length != 1 &&
                  (L = W = !1);
                var G = this.__chain__,
                  J = !!this.__actions__.length,
                  ie = S && !G,
                  de = L && !J;
                if (!S && W) {
                  C = de ? C : new ye(this);
                  var ae = i.apply(C, N);
                  return (
                    ae.__actions__.push({ func: Mu, args: [U], thisArg: n }),
                    new Nn(ae, G)
                  );
                }
                return ie && de
                  ? i.apply(this, N)
                  : ((ae = this.thru(U)),
                    ie ? (p ? ae.value()[0] : ae.value()) : ae);
              });
          }),
          kn(
            ["pop", "push", "shift", "sort", "splice", "unshift"],
            function (i) {
              var l = cu[i],
                c = /^(?:push|sort|unshift)$/.test(i) ? "tap" : "thru",
                p = /^(?:pop|shift)$/.test(i);
              x.prototype[i] = function () {
                var g = arguments;
                if (p && !this.__chain__) {
                  var S = this.value();
                  return l.apply(ue(S) ? S : [], g);
                }
                return this[c](function (C) {
                  return l.apply(ue(C) ? C : [], g);
                });
              };
            }
          ),
          pr(ye.prototype, function (i, l) {
            var c = x[l];
            if (c) {
              var p = c.name + "";
              Re.call(ea, p) || (ea[p] = []), ea[p].push({ name: l, func: c });
            }
          }),
          (ea[Nu(n, _).name] = [{ name: "wrapper", func: n }]),
          (ye.prototype.clone = T4),
          (ye.prototype.reverse = z4),
          (ye.prototype.value = A4),
          (x.prototype.at = cR),
          (x.prototype.chain = fR),
          (x.prototype.commit = dR),
          (x.prototype.next = pR),
          (x.prototype.plant = hR),
          (x.prototype.reverse = vR),
          (x.prototype.toJSON = x.prototype.valueOf = x.prototype.value = gR),
          (x.prototype.first = x.prototype.head),
          pl && (x.prototype[pl] = mR),
          x
        );
      },
      qi = d4();
    ri ? (((ri.exports = qi)._ = qi), (Lp._ = qi)) : (bt._ = qi);
  }).call($l);
})(zd, zd.exports);
var AK = zd.exports;
const Qc = Symbol(),
  lK = Symbol(),
  lC =
    typeof window > "u" ||
    /ServerSideRendering/.test(window.navigator && window.navigator.userAgent)
      ? y.useEffect
      : y.useLayoutEffect,
  sK = Tc.unstable_runWithPriority
    ? (e) => Tc.unstable_runWithPriority(Tc.unstable_NormalPriority, e)
    : (e) => e();
function LK(e) {
  const t = y.createContext({
    [Qc]: {
      v: { current: e },
      n: { current: -1 },
      l: new Set(),
      u: (r) => r(),
    },
  });
  var n;
  return (
    (t[lK] = t.Provider),
    (t.Provider =
      ((n = t.Provider),
      ({ value: r, children: o }) => {
        const a = y.useRef(r),
          s = y.useRef(0),
          [u, f] = y.useState(null);
        u && (u(r), f(null));
        const d = y.useRef();
        if (!d.current) {
          const m = new Set(),
            h = (v, b) => {
              Ja.unstable_batchedUpdates(() => {
                s.current += 1;
                const O = { n: s.current };
                b != null &&
                  b.suspense &&
                  ((O.n *= -1),
                  (O.p = new Promise((E) => {
                    f(() => ($) => {
                      (O.v = $), delete O.p, E($);
                    });
                  }))),
                  m.forEach((E) => E(O)),
                  v();
              });
            };
          d.current = { [Qc]: { v: a, n: s, l: m, u: h } };
        }
        return (
          lC(() => {
            (a.current = r),
              (s.current += 1),
              sK(() => {
                d.current[Qc].l.forEach((m) => {
                  m({ n: s.current, v: r });
                });
              });
          }, [r]),
          y.createElement(n, { value: d.current }, o)
        );
      })),
    delete t.Consumer,
    t
  );
}
function DK(e, t) {
  const n = y.useContext(e)[Qc],
    {
      v: { current: r },
      n: { current: o },
      l: a,
    } = n,
    s = t(r),
    [u, f] = y.useReducer(
      (d, m) => {
        if (!m) return [r, s];
        if ("p" in m) throw m.p;
        if (m.n === o) return Object.is(d[1], s) ? d : [r, s];
        try {
          if ("v" in m) {
            if (Object.is(d[0], m.v)) return d;
            const h = t(m.v);
            return Object.is(d[1], h) ? d : [m.v, h];
          }
        } catch {}
        return [...d];
      },
      [r, s]
    );
  return (
    Object.is(u[1], s) || f(),
    lC(
      () => (
        a.add(f),
        () => {
          a.delete(f);
        }
      ),
      [a]
    ),
    u[1]
  );
}
export {
  fj as A,
  Ie as B,
  bK as C,
  rV as D,
  LK as E,
  Sp as F,
  F3 as G,
  JM as H,
  LH as I,
  xK as J,
  BY as K,
  hK as L,
  eL as M,
  vL as N,
  RA as O,
  wK as P,
  dK as Q,
  pK as R,
  U3 as S,
  $V as T,
  fK as U,
  R as V,
  mK as W,
  mG as X,
  x_ as Y,
  vK as a,
  CK as b,
  OK as c,
  NK as d,
  EK as e,
  $K as f,
  kK as g,
  RK as h,
  TK as i,
  cK as j,
  IK as k,
  SK as l,
  PK as m,
  zK as n,
  hH as o,
  tY as p,
  QV as q,
  y as r,
  DK as s,
  AK as t,
  yK as u,
  _K as v,
  eM as w,
  MV as x,
  S5 as y,
  wH as z,
};
