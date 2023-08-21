import {
  j as o,
  C as ge,
  L as J,
  I as xe,
  H as ye,
  A as Se,
  B as E,
  P as Te,
  S as I,
  a as K,
  l as je,
  f as be,
  b as Ee,
  c as Oe,
  d as _e,
  e as Ce,
  g as Ie,
  h as De,
  i as Re,
  k as Pe,
  m as we,
  n as Ae,
  F as ve,
  o as Ne,
  p as Ue,
  q as Y,
  E as Le,
  r as Z,
  s as Be,
  t as B,
  G as _,
  u as H,
  v as D,
  w as ke,
  x as ee,
  T as M,
  y as Fe,
  z as te,
  D as $,
  J as qe,
  K as He,
  M as Me,
  N as $e,
  R as ze,
  O as Ge,
  Q as Ke,
  U as We,
  V as re,
  W as Qe,
  X as Xe,
  Y as Ve,
} from "./vendor-49a034fb.js";
import "./index-4acb67de.js";
(function () {
  const t = document.createElement("link").relList;
  if (t && t.supports && t.supports("modulepreload")) return;
  for (const s of document.querySelectorAll('link[rel="modulepreload"]')) n(s);
  new MutationObserver((s) => {
    for (const i of s)
      if (i.type === "childList")
        for (const l of i.addedNodes)
          l.tagName === "LINK" && l.rel === "modulepreload" && n(l);
  }).observe(document, { childList: !0, subtree: !0 });
  function r(s) {
    const i = {};
    return (
      s.integrity && (i.integrity = s.integrity),
      s.referrerPolicy && (i.referrerPolicy = s.referrerPolicy),
      s.crossOrigin === "use-credentials"
        ? (i.credentials = "include")
        : s.crossOrigin === "anonymous"
        ? (i.credentials = "omit")
        : (i.credentials = "same-origin"),
      i
    );
  }
  function n(s) {
    if (s.ep) return;
    s.ep = !0;
    const i = r(s);
    fetch(s.href, i);
  }
})();
var Wt =
  typeof globalThis < "u"
    ? globalThis
    : typeof window < "u"
    ? window
    : typeof global < "u"
    ? global
    : typeof self < "u"
    ? self
    : {};
function Qt(e) {
  return e && e.__esModule && Object.prototype.hasOwnProperty.call(e, "default")
    ? e.default
    : e;
}
function Je(e, t) {
  switch (
    parseInt(
      e || new URLSearchParams(t == null ? void 0 : t.search).get("code")
    ) ||
    404
  ) {
    case 403:
      return "Sorry, you are not authorized to view this page.";
    case 404:
      return "Sorry, the page you were looking for does not exist.";
    case 500:
      return "Oops... something went wrong.";
    default:
      return "Unknown error - really, I have no idea what is going on here.";
  }
}
function Ye({ code: e, location: t }) {
  const r = Je(e, t);
  return o.jsx(ge, { sx: { minHeight: "89px" }, children: r });
}
const Ze = "/assets/vespa-logo-11c6d4db.svg";
function et() {
  return o.jsx(J, { to: "/", children: o.jsx(xe, { height: 34, src: Ze }) });
}
function tt() {
  return o.jsx(ye, {
    height: 55,
    sx: (e) => ({
      display: "flex",
      alignItems: "center",
      paddingLeft: e.spacing.md,
      paddingRight: e.spacing.md,
      background: e.cr.getSolidBackground(),
      borderBottom: `1px solid ${e.cr.getSubtleBorderAndSeparator()}`,
    }),
    children: o.jsx(et, {}),
  });
}
function rt({ children: e }) {
  return o.jsx(Se, { header: o.jsx(tt, {}), children: e });
}
function C({ sx: e, ...t }) {
  return o.jsx(E, { sx: () => ({ display: "grid", ...e }), ...t });
}
function O({
  transparent: e,
  withBorder: t,
  padding: r,
  borderStyle: n = "solid",
  stack: s = !0,
  sx: i,
  ...l
}) {
  const c = s ? I : E;
  return o.jsx(Te, {
    sx: (f) => ({
      background: e && "transparent",
      border: t ? `1px ${n} ${f.cr.getSubtleBorderAndSeparator()}` : 0,
    }),
    children: o.jsx(c, {
      sx: (f) => ({ padding: r ?? f.spacing.md, ...i }),
      ...l,
    }),
  });
}
const nt = (e) => (e ? !/^[a-z]+:\/\//.test(e) : !1);
function W({ to: e, api: t = !1, ...r }) {
  const n = !t && nt(e);
  if (!r.download && e && n) return o.jsx(K, { component: J, to: e, ...r });
  const s = Object.assign(
    e ? { href: (t ? window.config.api : "") + e } : {},
    e && !n && { target: "_blank", rel: "noopener noreferrer" },
    r
  );
  return o.jsx(K, { ...s });
}
function Q({
  sx: e,
  withBorder: t = !0,
  borderStyle: r = "solid",
  minHeight: n = "89px",
  minWidth: s = "auto",
  ...i
}) {
  return o.jsx(E, {
    sx: (l) => ({
      minHeight: n,
      minWidth: s,
      display: "grid",
      placeContent: "center",
      justifyItems: "center",
      rowGap: "8px",
      ...l.fn.hover({
        cursor: "pointer",
        background: l.cr.getSubtleBackground(),
        border: t ? `1px ${r} ${l.cr.getUiElementBorderAndFocus()}` : 0,
      }),
      border: t ? `1px ${r} ${l.cr.getSubtleBorderAndSeparator()}` : 0,
      ...e,
    }),
    ...i,
  });
}
je.add(be, Ee, Oe, _e, Ce, Ie, De, Re, Pe, we, Ae);
function x({ name: e, type: t = "solid", color: r, ...n }) {
  const s = `fa-${t} fa-${e}`;
  return o.jsx(E, {
    sx: (i) => ({ ...(r && { color: i.cr.getSolidBackground(r) }) }),
    component: ve,
    icon: s,
    ...n,
  });
}
function st() {
  return o.jsxs(Ne, {
    children: [
      o.jsx(Ue, { h: 55 }),
      o.jsxs(Y, {
        style: { gridAutoRows: "minmax(0, 144px)" },
        breakpoints: [
          { maxWidth: "sm", cols: 2, spacing: "sm" },
          { maxWidth: "xs", cols: 1, spacing: "sm" },
        ],
        spacing: "lg",
        cols: 2,
        children: [
          o.jsxs(Q, {
            component: W,
            to: "/querybuilder",
            children: [
              o.jsx(x, { name: "arrows-to-dot", size: "2x" }),
              "query builder",
            ],
          }),
          o.jsxs(Q, {
            component: W,
            to: "/querytracer",
            children: [
              o.jsx(x, { name: "chart-gantt", size: "2x" }),
              "query tracer",
            ],
          }),
        ],
      }),
    ],
  });
}
function a(e, t, r = {}) {
  let n;
  return (
    Array.isArray(t) &&
      ((n = Object.fromEntries(t.map((s) => [s.name, s]))), (t = "Parent")),
    Object.assign({ name: e, type: t }, n && { children: n }, r)
  );
}
const ot = a("root", [
  a("yql", "String"),
  a("hits", "Integer", { min: 0, default: 10 }),
  a("offset", "Integer", { min: 0, default: 0 }),
  a("queryProfile", "String", { default: "default" }),
  a("groupingSessionCache", "Boolean", { default: !0 }),
  a("searchChain", "String", { default: "default" }),
  a("timeout", "Float", { min: 0, default: 0.5 }),
  a("noCache", "Boolean", { default: !1 }),
  a("model", [
    a("defaultIndex", "String", { default: "default" }),
    a("encoding", "String", { default: "utf-8" }),
    a("filter", "String"),
    a("locale", "String"),
    a("language", "String"),
    a("queryString", "String"),
    a("restrict", "String"),
    a("searchPath", "String"),
    a("sources", "String"),
    a("type", "String"),
  ]),
  a("ranking", [
    a("location", "String"),
    a("features", "Parent", { children: "String" }),
    a("listFeatures", "Boolean", { default: !1 }),
    a("profile", "String", { default: "default" }),
    a("properties", "String", { children: "String" }),
    a("softtimeout", [
      a("enable", "Boolean", { default: !0 }),
      a("factor", "Float", { min: 0, max: 1, default: 0.7 }),
    ]),
    a("sorting", "String"),
    a("freshness", "String"),
    a("queryCache", "Boolean", { default: !1 }),
    a("rerankCount", "Integer", { min: 0 }),
    a("matching", [
      a("numThreadsPerSearch", "Integer", { min: 0 }),
      a("minHitsPerThread", "Integer", { min: 0 }),
      a("numSearchPartitions", "Integer", { min: 0 }),
      a("termwiseLimit", "Float", { min: 0, max: 1 }),
      a("postFilterThreshold", "Float", { min: 0, max: 1 }),
      a("approximateThreshold", "Float", { min: 0, max: 1 }),
    ]),
    a("matchPhase", [
      a("attribute", "String"),
      a("maxHits", "Integer", { min: 0 }),
      a("ascending", "Boolean"),
      a("diversity", [
        a("attribute", "String"),
        a("minGroups", "Integer", { min: 0 }),
      ]),
    ]),
  ]),
  a("collapsesize", "Integer", { min: 1, default: 1 }),
  a("collapsefield", "String"),
  a("collapse", [a("summary", "String")]),
  a("grouping", [
    a("defaultMaxGroups", "Integer", { min: -1, default: 10 }),
    a("defaultMaxHits", "Integer", { min: -1, default: 10 }),
    a("globalMaxGroups", "Integer", { min: -1, default: 1e4 }),
    a("defaultPrecisionFactor", "Float", { min: 0, default: 2 }),
  ]),
  a("presentation", [
    a("bolding", "Boolean", { default: !0 }),
    a("format", "String", { default: "default" }),
    a("template", "String"),
    a("summary", "String"),
    a("timing", "Boolean", { default: !1 }),
  ]),
  a("trace", [
    a("level", "Integer", { min: 1 }),
    a("explainLevel", "Integer", { min: 1 }),
    a("profileDepth", "Integer", { min: 1 }),
    a("timestamps", "Boolean", { default: !1 }),
    a("query", "Boolean", { default: !0 }),
  ]),
  a("rules", [a("off", "Boolean", { default: !0 }), a("rulebase", "String")]),
  a("tracelevel", [a("rules", "Integer", { min: 0 })]),
  a("dispatch", [a("topKProbability", "Float", { min: 0, max: 1 })]),
  a("recall", "String"),
  a("user", "String"),
  a("hitcountestimate", "Boolean", { default: !1 }),
  a("metrics", [a("ignore", "Boolean", { default: !1 })]),
  a("weakAnd", [a("replace", "Boolean", { default: !1 })]),
  a("wand", [a("hits", "Integer", { default: 100 })]),
  a("sorting", [a("degrading", "Boolean", { default: !0 })]),
  a("streaming", [
    a("userid", "Integer"),
    a("groupname", "String"),
    a("selection", "String"),
    a("priority", "String"),
    a("maxbucketspervisitor", "Integer"),
  ]),
]).children;
let ne;
const se = { type: { children: ot } },
  oe = Le(null),
  d = Object.freeze({
    SET_QUERY: 0,
    SET_HTTP: 1,
    SET_METHOD: 2,
    SET_URL: 3,
    INPUT_ADD: 10,
    INPUT_UPDATE: 11,
    INPUT_REMOVE: 12,
  });
function v(e) {
  return Array.isArray(e.value) ? { ...e, value: e.value.map(v) } : { ...e };
}
function at(e, t) {
  if (e === "POST") {
    const n = (s, i) =>
      Object.fromEntries(
        s.map(({ value: l, type: { name: c, type: f, children: p } }) => [
          c,
          p ? n(l) : lt(l, f),
        ])
      );
    return JSON.stringify(n(t), null, 4);
  }
  const r = (n, s) =>
    n.reduce((i, { value: l, type: { name: c, children: f } }) => {
      const p = s ? `${s}.${c}` : c;
      return Object.assign(i, f ? r(l, p) : { [p]: l });
    }, {});
  return new URLSearchParams(r(t)).toString();
}
function it(e, t) {
  if (e === "POST") return k(JSON.parse(t));
  const r = [...new URLSearchParams(t).entries()].reduce(
    (n, [s, i]) => B.set(n, s, i),
    {}
  );
  return k(r);
}
function k(e, t = se) {
  return Object.entries(e).map(([r, n], s) => {
    const i = {
      id: t.id ? `${t.id}.${s}` : s.toString(),
      type:
        typeof t.type.children == "string"
          ? { name: r, type: t.type.children }
          : t.type.children[r],
    };
    if (!i.type) {
      const l = t.type.name ? `under '${t.type.name}'` : "on root level";
      throw new Error(`Unknown property '${r}' ${l}`);
    }
    if (n != null && typeof n == "object") {
      if (!i.type.children)
        throw new Error(`Expected property '${r}' to be ${i.type.type}`);
      i.value = k(n, i);
    } else {
      if (i.type.children)
        throw new Error(
          `Property '${r}' cannot have a value, supported children: ${Object.keys(
            i.type.children
          ).sort()}`
        );
      i.value = n == null ? void 0 : n.toString();
    }
    return i;
  });
}
function lt(e, t) {
  return t === "Integer"
    ? parseInt(e)
    : t === "Float"
    ? parseFloat(e)
    : t === "Boolean"
    ? e.toLowerCase() === "true"
    : e;
}
function ct(e, { id: t, type: r }) {
  var f, p, m;
  const n = v(e),
    s = R(n, t),
    i =
      parseInt(
        B.last(
          (p = (f = B.last(s.value)) == null ? void 0 : f.id) == null
            ? void 0
            : p.split(".")
        ) ?? -1
      ) + 1,
    l = t ? `${t}.${i}` : i.toString(),
    c =
      typeof s.type.children == "string"
        ? { name: r, type: s.type.children }
        : s.type.children[r];
  return (
    s.value.push({
      id: l,
      value: c.children
        ? []
        : ((m = c.default) == null ? void 0 : m.toString()) ?? "",
      type: c,
    }),
    n
  );
}
function ft(e, { id: t, type: r, value: n }) {
  var l;
  const s = v(e),
    i = R(s, t);
  if (r) {
    const c = R(s, t.substring(0, t.lastIndexOf("."))),
      f =
        typeof c.type.children == "string"
          ? { name: r, type: c.type.children }
          : c.type.children[r];
    i.type.type !== f.type.type &&
      (i.value = f.children
        ? []
        : ((l = f.default) == null ? void 0 : l.toString()) ?? ""),
      (i.type = f);
  }
  return n != null && (i.value = n), s;
}
function R(e, t, r = !1) {
  if (!t) return e;
  let n = -1;
  for (; (n = t.indexOf(".", n + 1)) > 0; )
    e = e.value.find((i) => i.id === t.substring(0, n));
  const s = e.value.findIndex((i) => i.id === t);
  return r ? e.value.splice(s, 1)[0] : e.value[s];
}
function F(e, t) {
  if (e == null)
    return (
      (e = { http: {}, params: {}, query: {}, request: {} }),
      [
        [d.SET_URL, "http://localhost:8080/search/"],
        [d.SET_QUERY, "yql="],
        [d.SET_METHOD, "POST"],
      ].reduce((m, [T, j]) => F(m, { action: T, data: j }), e)
    );
  const r = ut(e, t),
    { request: n, params: s, query: i } = e,
    { request: l, params: c, query: f } = r;
  ((s.value !== c.value && i === f) || n.method !== l.method) &&
    (r.query = { input: at(l.method, c.value) });
  const p = r.query.input;
  if (n.url !== l.url || i.input !== p || n.method !== l.method)
    if (l.method === "POST")
      r.request = { ...r.request, fullUrl: l.url, body: p };
    else {
      const m = new URL(l.url);
      (m.search = p),
        (r.request = { ...r.request, fullUrl: m.toString(), body: null });
    }
  return r;
}
function ut(e, { action: t, data: r }) {
  switch (t) {
    case d.SET_QUERY:
      try {
        const n = it(e.request.method, r);
        return { ...e, params: { ...se, value: n }, query: { input: r } };
      } catch (n) {
        return { ...e, query: { input: r, error: n.message } };
      }
    case d.SET_HTTP:
      return { ...e, http: r };
    case d.SET_METHOD:
      return { ...e, request: { ...e.request, method: r } };
    case d.SET_URL:
      return { ...e, request: { ...e.request, url: r } };
    case d.INPUT_ADD:
      return { ...e, params: ct(e.params, r) };
    case d.INPUT_UPDATE:
      return { ...e, params: ft(e.params, r) };
    case d.INPUT_REMOVE: {
      const n = v(e.params);
      return R(n, r, !0), { ...e, params: n };
    }
    default:
      throw new Error(`Unknown action ${t}`);
  }
}
function dt({ children: e }) {
  const [t, r] = Z.useReducer(F, null, F);
  return (ne = r), o.jsx(oe.Provider, { value: t, children: e });
}
function b(e) {
  return Be(oe, typeof e == "string" ? (r) => r[e] : e);
}
function h(e, t) {
  ne({ action: e, data: t });
}
const u = Object.freeze({
  APP_BACKGROUND: 0,
  SUBTLE_BACKGROUND: 1,
  UI_ELEMENT_BACKGROUND: 2,
  HOVERED_ELEMENT_BACKGROUND: 3,
  SUBTLE_BORDER_AND_SEPARATOR: 4,
  UI_ELEMENT_BORDER_AND_FOCUS: 5,
  SOLID_BACKGROUND: 6,
  HOVERED_SOLID_BACKGROUND: 7,
  LOW_CONTRAST_TEXT: 8,
  HIGH_CONTRAST_TEXT: 9,
});
class pt {
  constructor(t) {
    (this.theme = t), (this.themeColor = t.colors.blue);
  }
  getAppBackground() {
    return this.themeColor[u.APP_BACKGROUND];
  }
  getSubtleBackground() {
    return this.themeColor[u.SUBTLE_BACKGROUND];
  }
  getUiElementBackground() {
    return this.themeColor[u.UI_ELEMENT_BACKGROUND];
  }
  getHoveredUiElementBackground() {
    return this.themeColor[u.HOVERED_ELEMENT_BACKGROUND];
  }
  getSubtleBorderAndSeparator() {
    return this.themeColor[u.SUBTLE_BORDER_AND_SEPARATOR];
  }
  getUiElementBorderAndFocus() {
    return this.themeColor[u.UI_ELEMENT_BORDER_AND_FOCUS];
  }
  getSolidBackground(t) {
    return this.theme.fn.themeColor(t, u.SOLID_BACKGROUND);
  }
  getHoveredSolidBackground(t) {
    return this.theme.fn.themeColor(t, u.HOVERED_SOLID_BACKGROUND);
  }
  getLowContrastText() {
    return this.theme.colors.gray[u.LOW_CONTRAST_TEXT];
  }
  getHighContrastText() {
    return this.theme.colors.gray[u.HIGH_CONTRAST_TEXT];
  }
  getText(t, r) {
    return t === "dimmed"
      ? this.theme.fn.themeColor("gray", u.SOLID_BACKGROUND)
      : t in this.theme.colors
      ? this.theme.fn.themeColor(t, u.LOW_CONTRAST_TEXT)
      : r === "link"
      ? this.theme.fn.themeColor(t, u.SOLID_BACKGROUND)
      : t || "inherit";
  }
}
function X(e) {
  return o.jsx(D, {
    leftIcon: o.jsx(x, { name: "plus" }),
    ...e,
    children: "Add property",
  });
}
function mt({ id: e, type: t, types: r }) {
  return r
    ? o.jsx(ee, {
        sx: { flex: 1 },
        data: Object.values({ [t.name]: t, ...r }).map(({ name: n }) => n),
        onChange: (n) => h(d.INPUT_UPDATE, { id: e, type: n }),
        value: t.name,
        searchable: !0,
      })
    : o.jsx(M, {
        sx: { flex: 1 },
        onChange: (n) =>
          h(d.INPUT_UPDATE, { id: e, type: n.currentTarget.value }),
        placeholder: "String",
        value: t.name,
      });
}
function ht({ id: e, type: t, value: r }) {
  if (t.children) return null;
  if (t.type === "Boolean")
    return o.jsx(Fe, {
      sx: { flex: 1 },
      onLabel: "true",
      offLabel: "false",
      size: "xl",
      checked: r === "true",
      onChange: (s) =>
        h(d.INPUT_UPDATE, { id: e, value: s.currentTarget.checked.toString() }),
    });
  const n = { value: r, placeholder: t.type };
  if (t.type === "Integer" || t.type === "Float") {
    n.type = "number";
    let s;
    t.min != null ? ((n.min = t.min), (s = `[${n.min}, `)) : (s = "(-∞, "),
      t.max != null ? ((n.max = t.max), (s += n.max + "]")) : (s += "∞)"),
      (n.placeholder += ` in ${s}`),
      t.type === "Float" &&
        t.min != null &&
        t.max != null &&
        (n.step = (t.max - t.min) / 100),
      (parseFloat(r) < t.min || parseFloat(r) > t.max) &&
        (n.error = `Must be within ${s}`);
  }
  return o.jsx(M, {
    sx: { flex: 1 },
    onChange: (s) => h(d.INPUT_UPDATE, { id: e, value: s.currentTarget.value }),
    ...n,
  });
}
function gt({ id: e, value: t, types: r, type: n }) {
  return o.jsxs(o.Fragment, {
    children: [
      o.jsxs(E, {
        sx: { display: "flex", gap: "5px" },
        children: [
          o.jsx(mt, { id: e, type: n, types: r }),
          o.jsx(ht, { id: e, type: n, value: t }),
          o.jsx(ke, {
            sx: { marginTop: 5 },
            onClick: () => h(d.INPUT_REMOVE, e),
            children: o.jsx(x, { name: "circle-minus" }),
          }),
        ],
      }),
      n.children &&
        o.jsx(E, {
          py: 8,
          sx: (s) => ({
            borderLeft: `1px dashed ${s.fn.themeColor(
              "gray",
              u.UI_ELEMENT_BORDER_AND_FOCUS
            )}`,
            marginLeft: "13px",
            paddingLeft: "13px",
          }),
          children: o.jsx(ae, { id: e, type: n.children, inputs: t }),
        }),
    ],
  });
}
function ae({ id: e, type: t, inputs: r }) {
  const n = r.map(({ type: l }) => l.name),
    s =
      typeof t == "string"
        ? null
        : Object.fromEntries(Object.entries(t).filter(([l]) => !n.includes(l))),
    i = s ? Object.keys(s)[0] : "";
  return o.jsxs(C, {
    sx: { rowGap: "5px" },
    children: [
      r.map(({ id: l, value: c, type: f }) =>
        o.jsx(gt, { types: s, id: l, value: c, type: f }, l)
      ),
      i != null &&
        o.jsx(o.Fragment, {
          children:
            e != null
              ? o.jsx(C, {
                  sx: { justifyContent: "start" },
                  children: o.jsx(X, {
                    onClick: () => h(d.INPUT_ADD, { id: e, type: i }),
                    variant: "subtle",
                    size: "xs",
                    compact: !0,
                  }),
                })
              : o.jsx(X, {
                  onClick: () => h(d.INPUT_ADD, { id: e, type: i }),
                  mt: 13,
                }),
        }),
    ],
  });
}
function xt() {
  const { value: e, type: t } = b("params");
  return o.jsxs(I, {
    children: [
      o.jsx(_, {
        children: o.jsx(H, { variant: "filled", children: "Parameters" }),
      }),
      o.jsx(C, {
        sx: { alignContent: "start" },
        children: o.jsx(O, {
          padding: 0,
          children: o.jsx(ae, { type: t.children, inputs: e }),
        }),
      }),
    ],
  });
}
function yt() {
  const { input: e, error: t } = b("query");
  return o.jsxs(I, {
    children: [
      o.jsxs(_, {
        position: "apart",
        children: [
          o.jsx(H, { variant: "filled", children: "Query" }),
          o.jsx(_, {
            spacing: "xs",
            children: o.jsx(te, {
              value: e,
              children: ({ copied: r, copy: n }) =>
                o.jsx(D, {
                  leftIcon: o.jsx(x, { name: r ? "check" : "copy" }),
                  color: r ? "teal" : "blue",
                  variant: "outline",
                  onClick: n,
                  size: "xs",
                  compact: !0,
                  children: "Copy",
                }),
            }),
          }),
        ],
      }),
      o.jsx($, {
        styles: (r) => ({
          input: {
            fontFamily: r.fontFamilyMonospace,
            fontSize: r.fontSizes.xs,
          },
        }),
        value: e,
        error: t,
        onChange: ({ target: r }) => h(d.SET_QUERY, r.value),
        variant: "unstyled",
        minRows: 21,
        autosize: !0,
      }),
    ],
  });
}
function St(e) {
  return (t) => ({
    root: {
      color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT),
      background: t.fn.themeColor(e, u.UI_ELEMENT_BACKGROUND),
      borderColor: t.fn.themeColor(e, u.UI_ELEMENT_BORDER_AND_FOCUS),
    },
    title: {
      fontWeight: 700,
      color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT),
      "&:hover": { color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT) },
    },
    description: {
      color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT),
      "&:hover": { color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT) },
    },
    closeButton: {
      color: t.fn.themeColor(e, u.LOW_CONTRAST_TEXT),
      "&:hover": {
        backgroundColor: t.fn.themeColor(e, u.HOVERED_UI_ELEMENT_BACKGROUND),
      },
    },
  });
}
const Tt = ({ title: e, icon: t, color: r, message: n }) =>
    qe({
      styles: St(r),
      icon: o.jsx(x, { name: t }),
      title: e,
      color: r,
      message: n,
    }),
  q = (e, t = "Error", r = "xmark", n = "red") =>
    Tt({ title: t, icon: r, color: n, message: e }),
  P = (e) =>
    [...Array(e)]
      .map(() => Math.floor(Math.random() * 16).toString(16))
      .join(""),
  y = P(32),
  z = { data: [{ traceID: y, spans: [], processes: {} }] },
  w = z.data[0].processes,
  U = new Map(),
  g = z.data[0].spans;
let A = 0,
  V = 1;
function jt(e) {
  let t = e.trace.children,
    r = t[0].message.split(" ");
  w.p0 = { serviceName: r[3], tags: [] };
  let n = _t(t);
  A = fe(n);
  let s = S(A, 0, "p0", r[6]);
  const i = It(n);
  return (s.duration = i), g.push(s), bt(n, s), z;
}
function ie(e, t) {
  let r;
  if (e.hasOwnProperty("children")) {
    let n =
      (e.children[e.children.length - 1].timestamp - e.children[0].timestamp) *
      1e3;
    (isNaN(n) || n <= 0) && (n = 1), (t.duration = n);
    for (let s = 0; s < e.children.length; s++) {
      let i = e.children[s];
      if (i.hasOwnProperty("children")) {
        let l = me(t.operationName),
          c = l === "" ? "p0" : de(l);
        (r = S(A + i.timestamp * 1e3, n, c, t.operationName, [
          { refType: "CHILD_OF", traceID: y, spanID: t.spanID },
        ])),
          g.push(r),
          ie(i, r);
      } else if (Array.isArray(i.message)) Et(i.message, t.spanID);
      else if (i.hasOwnProperty("message") && i.hasOwnProperty("timestamp")) {
        let l;
        s >= e.children.length - 1
          ? (l = 1)
          : (l = ue(e.children, s) - i.timestamp * 1e3),
          (isNaN(l) || l <= 0) && (l = 1),
          le(g, i, l, t);
      }
    }
  }
}
function bt(e, t) {
  for (let r = 0; r < e.length; r++)
    if (e[r].hasOwnProperty("children")) ie(e[r], g[g.length - 1]);
    else if (
      e[r].hasOwnProperty("message") &&
      e[r].hasOwnProperty("timestamp")
    ) {
      let n;
      r >= e.length - 1 ? (n = 1) : (n = ue(e, r) - e[r].timestamp * 1e3),
        (isNaN(n) || n <= 0) && (n = 1),
        le(g, e[r], n, t);
    }
}
function le(e, t, r, n) {
  let s = A + t.timestamp * 1e3,
    i = me(t.message),
    l = i === "" ? "p0" : de(i),
    c = S(s, r, l, t.message, [
      { refType: "CHILD_OF", traceID: y, spanID: n.spanID },
    ]);
  e.push(c);
}
function Et(e, t) {
  let r = e[0],
    n = P(5);
  w[n] = { serviceName: "Proton:" + P(3), tags: [] };
  let s = Date.parse(r.start_time) * 1e3,
    i = S(s, r.duration_ms * 1e3, n, "Search Dispatch", [
      { refType: "CHILD_OF", traceID: y, spanID: t },
    ]);
  if ((g.push(i), !r.hasOwnProperty("traces"))) return;
  let l = r.traces;
  for (let c = 0; c < l.length; c++) {
    let f = l[c],
      p = f.timestamp_ms,
      m,
      T,
      j;
    if (
      ((n = pe()),
      (w[n] = { serviceName: f.tag, tags: [] }),
      f.tag === "query_execution")
    )
      Ot(f, p, s, n, i.spanID);
    else {
      if (f.tag === "query_execution_plan") {
        m = [];
        let N = l[c + 1];
        (T = f),
          N.tag === "query_execution"
            ? (j = (N.threads[0].traces[0].timestamp_ms - p) * 1e3)
            : (j = (N.timestamp_ms - p) * 1e3);
      } else (m = f.traces), (T = m[0]), (j = (p - T.timestamp_ms) * 1e3);
      let G = S(s + T.timestamp_ms * 1e3, j, n, f.tag, [
        { refType: "CHILD_OF", traceID: y, spanID: i.spanID },
      ]);
      g.push(G), ce(m, G, s, p);
    }
  }
}
function Ot(e, t, r, n, s) {
  let i = e.threads;
  for (let l = 0; l < i.length; l++) {
    let c = i[l].traces,
      f = c[0],
      p = (t - f.timestamp_ms) * 1e3,
      m = S(r + f.timestamp_ms * 1e3, p, n, e.tag, [
        { refType: "CHILD_OF", traceID: y, spanID: s },
      ]);
    g.push(m), ce(c, m, r, t);
  }
}
function ce(e, t, r, n) {
  for (let s = 0; s < e.length; s++) {
    let i = e[s],
      l = i.timestamp_ms,
      c,
      f;
    i.hasOwnProperty("event")
      ? ((c = i.event),
        c === "Complete query setup" || c === "MatchThread::run Done"
          ? (f = (n - l) * 1e3)
          : (f = (e[s + 1].timestamp_ms - l) * 1e3))
      : ((c = i.tag), (f = (e[s + 1].timestamp_ms - l) * 1e3));
    let p = S(r + l * 1e3, f, t.processID, c, [
      { refType: "CHILD_OF", traceID: y, spanID: t.spanID },
    ]);
    g.push(p);
  }
}
function _t(e) {
  for (let t of e) if (t.hasOwnProperty("children")) return t.children;
}
function Ct(e) {
  if (Array.isArray(e.message)) {
    let t = Date.parse(e.message[0].start_time) * 1e3,
      r = e.timestamp * 1e3;
    return t - r;
  }
}
function fe(e) {
  let t = 0;
  for (let r of e) {
    if (r.hasOwnProperty("children")) t = fe(r.children);
    else if (r.hasOwnProperty("message") && Array.isArray(r.message))
      return Ct(r);
    if (t !== 0) return t;
  }
  return t;
}
function It(e) {
  let t = e.length - 1;
  for (; t >= 0; ) {
    if (e[t].hasOwnProperty("timestamp")) return e[t].timestamp * 1e3;
    t--;
  }
  return 0;
}
function ue(e, t) {
  for (t = t + 1; t < e.length; ) {
    if (e[t].hasOwnProperty("timestamp")) return e[t].timestamp * 1e3;
    t++;
  }
  return 0;
}
function S(e = 0, t = 1, r = "p0", n = "Complete", s = []) {
  let i = P(16);
  return {
    traceID: y,
    spanID: i,
    operationName: n,
    startTime: e,
    duration: t,
    references: s,
    tags: [],
    logs: [],
    processID: r,
  };
}
function de(e) {
  if (U.has(e)) return U.get(e);
  {
    let t = "p" + pe();
    return (w[t] = { serviceName: e, tags: [] }), U.set(e, t), t;
  }
}
function pe() {
  return (V += 1), V;
}
function me(e) {
  let t = /(?:[a-z]+\.)+[a-zA-Z]+/gm,
    r = e.match(t);
  if (r != null && r.length > 0) {
    let n = r[0];
    return (n = n.split(".")), n[n.length - 1];
  } else return "";
}
function Dt(e, t) {
  const r = URL.createObjectURL(t),
    n = document.createElement("a");
  (n.href = r),
    (n.download = e),
    document.body.appendChild(n),
    n.click(),
    document.body.removeChild(n),
    URL.revokeObjectURL(r);
}
function he({ response: e, ...t }) {
  const r = () => {
    try {
      const n = JSON.parse(e);
      try {
        const s = JSON.stringify(jt(n), null, 4);
        Dt("vespa-response.json", new Blob([s], { type: "application/json" }));
      } catch {
        q(
          "Request must be made with tracelevel ≥ 4",
          "Failed to transform response to Jaeger format"
        );
      }
    } catch (n) {
      q(n.message);
    }
  };
  return o.jsx(D, {
    ...t,
    leftIcon: o.jsx(x, { name: "download" }),
    onClick: r,
    disabled: !((e == null ? void 0 : e.length) > 0),
    children: "Jaeger Format",
  });
}
function Rt() {
  const e = b((t) => t.http.response);
  return o.jsxs(I, {
    children: [
      o.jsxs(_, {
        position: "apart",
        children: [
          o.jsx(H, { variant: "filled", children: "Response" }),
          o.jsxs(_, {
            spacing: "xs",
            children: [
              o.jsx(te, {
                value: e,
                children: ({ copied: t, copy: r }) =>
                  o.jsx(D, {
                    leftIcon: o.jsx(x, { name: t ? "check" : "copy" }),
                    color: t ? "teal" : "blue",
                    variant: "outline",
                    onClick: r,
                    size: "xs",
                    compact: !0,
                    children: "Copy",
                  }),
              }),
              o.jsx(he, {
                variant: "outline",
                size: "xs",
                compact: !0,
                response: e,
              }),
            ],
          }),
        ],
      }),
      o.jsx($, {
        styles: (t) => ({
          input: {
            fontFamily: t.fontFamilyMonospace,
            fontSize: t.fontSizes.xs,
          },
        }),
        value: e ?? "",
        variant: "unstyled",
        minRows: 21,
        autosize: !0,
      }),
    ],
  });
}
function Pt(e, t, r, n) {
  e.preventDefault(),
    h(d.SET_HTTP, { loading: !0 }),
    fetch(r, {
      method: t,
      headers: { "Content-Type": "application/json;charset=utf-8" },
      body: n,
    })
      .then((s) => s.json())
      .then((s) => h(d.SET_HTTP, { response: JSON.stringify(s, null, 4) }))
      .catch((s) => {
        q(s.message), h(d.SET_HTTP, {});
      });
}
function wt() {
  const { method: e, url: t, fullUrl: r, body: n } = b("request"),
    s = b((l) => l.query.error != null),
    i = b((l) => l.http.loading);
  return o.jsx(O, {
    children: o.jsx("form", {
      onSubmit: (l) => Pt(l, e, r, n),
      children: o.jsxs(C, {
        sx: { gridTemplateColumns: "max-content auto max-content" },
        children: [
          o.jsx(ee, {
            data: ["POST", "GET"],
            onChange: (l) => h(d.SET_METHOD, l),
            value: e,
            radius: 0,
          }),
          o.jsx(M, {
            onChange: (l) => h(d.SET_URL, l.currentTarget.value),
            value: t,
            radius: 0,
          }),
          o.jsx(D, {
            radius: 0,
            type: "submit",
            loading: i,
            disabled: s,
            children: "Send",
          }),
        ],
      }),
    }),
  });
}
function At() {
  return o.jsx(dt, {
    children: o.jsxs(C, {
      sx: { rowGap: "21px" },
      children: [
        o.jsxs(He, {
          order: 2,
          children: [o.jsx(x, { name: "arrows-to-dot" }), " Query Builder"],
        }),
        o.jsx(wt, {}),
        o.jsxs(Y, {
          breakpoints: [{ maxWidth: "sm", cols: 1 }],
          cols: 3,
          spacing: "lg",
          children: [
            o.jsx(O, { children: o.jsx(xt, {}) }),
            o.jsx(O, { children: o.jsx(yt, {}) }),
            o.jsx(O, { children: o.jsx(Rt, {}) }),
          ],
        }),
      ],
    }),
  });
}
function vt() {
  const [e, t] = Z.useState("");
  return o.jsxs(I, {
    children: [
      o.jsx($, {
        styles: (r) => ({
          input: {
            fontFamily: r.fontFamilyMonospace,
            fontSize: r.fontSizes.xs,
          },
        }),
        minRows: 21,
        autosize: !0,
        value: e,
        onChange: ({ target: r }) => t(r.value),
      }),
      o.jsx(he, { fullWidth: !0, response: e }),
    ],
  });
}
const Nt = (e) => ({
    "*, *::before, *::after": { boxSizing: "border-box" },
    "*": { margin: "0" },
    html: { height: "100%" },
    body: {
      height: "100%",
      WebkitFontSmoothing: "antialiased",
      lineHeight: e.lineHeight,
      background: e.cr.getAppBackground(),
      color: e.cr.getHighContrastText(),
      ...e.fn.fontStyles(),
    },
    "img, picture, video, canvas, svg": { display: "block" },
    "input, button, textarea, select": { font: "inherit" },
    "p, h1, h2, h3, h4, h5, h6": { overflowWrap: "break-word" },
    "#root": { height: "100%", isolation: "isolate" },
  }),
  Ut = {
    primaryShade: 6,
    loader: "oval",
    white: "#fff",
    black: "#303030",
    defaultRadius: "xs",
    primaryColor: "blue",
    lineHeight: 1.5,
    fontFamily: "Lato, sans-serif",
    shadows: {
      xs: "0 1px 3px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.1)",
      sm: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 10px 15px -5px, rgba(0, 0, 0, 0.04) 0px 7px 7px -5px",
      md: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 20px 25px -5px, rgba(0, 0, 0, 0.04) 0px 10px 10px -5px",
      lg: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 28px 23px -7px, rgba(0, 0, 0, 0.04) 0px 12px 12px -7px",
      xl: "0 1px 3px rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05) 0px 36px 28px -7px, rgba(0, 0, 0, 0.04) 0px 17px 17px -7px",
    },
    fontSizes: { xs: 12, sm: 14, md: 16, lg: 18, xl: 20 },
    radius: { xs: 5, sm: 8, md: 13, lg: 21, xl: 34 },
    spacing: { xs: 5, sm: 8, md: 13, lg: 21, xl: 34 },
    breakpoints: { xs: 576, sm: 768, md: 992, lg: 1200, xl: 1400 },
    headings: {
      fontFamily: "Lato, sans-serif",
      sizes: {
        h1: { fontSize: "1.5rem" },
        h2: { fontSize: "1.3333rem" },
        h3: { fontSize: "1.125rem" },
        h4: { fontSize: "1rem" },
        h5: { fontSize: "0.9375rem" },
        h6: { fontSize: "0.875rem" },
      },
    },
    other: {},
    datesLocale: "en",
    fn: {},
  },
  Lt = {
    AppShell: {
      styles: () => ({ main: { maxWidth: "1920px", margin: "0 auto" } }),
    },
  },
  Bt = {
    gray: [
      "#fcfcfc",
      "#f8f8f8",
      "#f3f3f3",
      "#ededed",
      "#e8e8e8",
      "#e2e2e2",
      "#8f8f8f",
      "#858585",
      "#6f6f6f",
      "#171717",
    ],
    red: [
      "#fffcfc",
      "#fff8f8",
      "#ffefef",
      "#ffe5e5",
      "#fdd8d8",
      "#f9c6c6",
      "#e5484d",
      "#dc3d43",
      "#cd2b31",
      "#381316",
    ],
    pink: [
      "#fffcfe",
      "#fff7fc",
      "#feeef8",
      "#fce5f3",
      "#f9d8ec",
      "#f3c6e2",
      "#d6409f",
      "#d23197",
      "#cd1d8d",
      "#3b0a2a",
    ],
    grape: [
      "#fefcfe",
      "#fdfaff",
      "#f9f1fe",
      "#f3e7fc",
      "#eddbf9",
      "#e3ccf4",
      "#8e4ec6",
      "#8445bc",
      "#793aaf",
      "#2b0e44",
    ],
    violet: [
      "#fdfcfe",
      "#fbfaff",
      "#f5f2ff",
      "#ede9fe",
      "#e4defc",
      "#d7cff9",
      "#6e56cf",
      "#644fc1",
      "#5746af",
      "#20134b",
    ],
    indigo: [
      "#fdfdfe",
      "#f8faff",
      "#f0f4ff",
      "#e6edfe",
      "#d9e2fc",
      "#c6d4f9",
      "#3e63dd",
      "#3a5ccc",
      "#3451b2",
      "#101d46",
    ],
    blue: [
      "#f5fbff",
      "#feffff",
      "#edf8ff",
      "#e1f4ff",
      "#ceecfe",
      "#b7e0f8",
      "#00598c",
      "#00507e",
      "#00436a",
      "#002033",
    ],
    cyan: [
      "#fafdfe",
      "#f2fcfd",
      "#e7f9fb",
      "#d8f3f6",
      "#c4eaef",
      "#aadee6",
      "#05a2c2",
      "#0894b3",
      "#0c7792",
      "#04313c",
    ],
    teal: [
      "#fafefd",
      "#f1fcfa",
      "#e7f9f5",
      "#d9f3ee",
      "#c7ebe5",
      "#afdfd7",
      "#12a594",
      "#0e9888",
      "#067a6f",
      "#10302b",
    ],
    green: [
      "#fbfefc",
      "#f2fcf5",
      "#e9f9ee",
      "#ddf3e4",
      "#ccebd7",
      "#b4dfc4",
      "#30a46c",
      "#299764",
      "#18794e",
      "#153226",
    ],
    lime: [
      "#fcfdfa",
      "#f7fcf0",
      "#eefadc",
      "#e4f7c7",
      "#d7f2b0",
      "#c9e894",
      "#99d52a",
      "#93c926",
      "#5d770d",
      "#263209",
    ],
    yellow: [
      "#fdfdf9",
      "#fffce8",
      "#fffbd1",
      "#fff8bb",
      "#fef2a4",
      "#f9e68c",
      "#f5d90a",
      "#f7ce00",
      "#946800",
      "#35290f",
    ],
    orange: [
      "#fefcfb",
      "#fef8f4",
      "#fff1e7",
      "#ffe8d7",
      "#ffdcc3",
      "#ffcca7",
      "#f76808",
      "#ed5f00",
      "#bd4b00",
      "#451e11",
    ],
  },
  kt = () => ({ ...Ut, components: Lt, colors: Bt });
function Ft(e) {
  return e.cr || (e.cr = new pt(e)), e;
}
function qt({ children: e }) {
  return o.jsxs(Me, {
    theme: kt(),
    children: [o.jsx($e, { styles: (t) => Nt(Ft(t)) }), e],
  });
}
const L = "Vespa App";
function Ht({ element: e, title: t, default: r, ...n }) {
  const s = We(),
    i = re.cloneElement(e, Object.assign(n, s));
  if (t != null) {
    const l = typeof t == "function" ? t(s) : t;
    document.title = l.endsWith(L) ? l : `${l} - ${L}`;
  } else r && (document.title = L);
  return i;
}
function Mt({ children: e }) {
  return (
    Array.isArray(e) || (e = [e]),
    (e = e.filter(({ props: t }) => t.enabled ?? !0)),
    e.some((t) => t.props.default) ||
      e.push(o.jsx(Ye, { code: 404, default: !0 })),
    o.jsx(ze, {
      children: e.map(({ props: t, ...r }, n) =>
        o.jsx(
          Ge,
          {
            path: t.default ? "*" : t.path,
            element:
              r.type === $t
                ? Object.assign({ props: t }, r)
                : o.jsx(Ht, { element: r, ...t }),
          },
          `${n}-${t.path}`
        )
      ),
    })
  );
}
function $t({ to: e, replace: t }) {
  return o.jsx(Ke, { to: e, replace: t });
}
function zt() {
  return o.jsx(Qe, {
    children: o.jsx(qt, {
      children: o.jsx(Xe, {
        children: o.jsx(rt, {
          children: o.jsxs(Mt, {
            children: [
              o.jsx(st, { path: "/", title: "Home" }),
              o.jsx(At, { path: "querybuilder", title: "Query Builder" }),
              o.jsx(vt, { path: "querytracer", title: "Query Tracer" }),
            ],
          }),
        }),
      }),
    }),
  });
}
Ve.createRoot(document.getElementById("root")).render(
  o.jsx(re.StrictMode, { children: o.jsx(zt, {}) })
);
export { Wt as c, Qt as g };
