import { last, set } from 'lodash';
import React, { useReducer } from 'react';
import { createContext, useContextSelector } from 'use-context-selector';
import parameters from 'app/pages/querybuilder/context/parameters';

let _dispatch;
const root = { type: { children: parameters } };
const context = createContext(null);

export const ACTION = Object.freeze({
  SET_QUERY: 0,
  SET_HTTP: 1,
  SET_METHOD: 2,
  SET_URL: 3,

  INPUT_ADD: 10,
  INPUT_UPDATE: 11,
  INPUT_REMOVE: 12,
});

function cloneParams(params) {
  if (!Array.isArray(params.value)) return { ...params };
  return { ...params, value: params.value.map(cloneParams) };
}

function inputsToQuery(method, inputs) {
  if (method === 'POST') {
    const inputsToJson = (inputs, parent) =>
      Object.fromEntries(
        inputs.map(({ value, type: { name, type, children } }) => [
          name,
          children ? inputsToJson(value) : parseInput(value, type),
        ])
      );
    return JSON.stringify(inputsToJson(inputs), null, 4);
  }

  const inputsToSearchParams = (inputs, parent) =>
    inputs.reduce((acc, { value, type: { name, children } }) => {
      const key = parent ? `${parent}.${name}` : name;
      return Object.assign(
        acc,
        children ? inputsToSearchParams(value, key) : { [key]: value }
      );
    }, {});
  return new URLSearchParams(inputsToSearchParams(inputs)).toString();
}

function queryToInputs(method, query) {
  if (method === 'POST') return jsonToInputs(JSON.parse(query));

  const json = [...new URLSearchParams(query).entries()].reduce(
    (acc, [key, value]) => set(acc, key, value),
    {}
  );
  return jsonToInputs(json);
}

function jsonToInputs(json, parent = root) {
  return Object.entries(json).map(([key, value], i) => {
    const node = {
      id: parent.id ? `${parent.id}.${i}` : i.toString(),
      type:
        typeof parent.type.children === 'string'
          ? { name: key, type: parent.type.children }
          : parent.type.children[key],
    };
    if (!node.type) {
      const location = parent.type.name
        ? `under '${parent.type.name}'`
        : 'on root level';
      throw new Error(`Unknown property '${key}' ${location}`);
    }
    if (value != null && typeof value === 'object') {
      if (!node.type.children)
        throw new Error(`Expected property '${key}' to be ${node.type.type}`);
      node.value = jsonToInputs(value, node);
    } else {
      if (node.type.children)
        throw new Error(
          `Property '${key}' cannot have a value, supported children: ${Object.keys(
            node.type.children
          ).sort()}`
        );
      node.value = value?.toString();
    }
    return node;
  });
}

function parseInput(value, type) {
  if (type === 'Integer') return parseInt(value);
  if (type === 'Float') return parseFloat(value);
  if (type === 'Boolean') return value.toLowerCase() === 'true';
  return value;
}

function inputAdd(params, { id: parentId, type: typeName }) {
  const cloned = cloneParams(params);
  const parent = findInput(cloned, parentId);

  const nextId = parseInt(last(last(parent.value)?.id?.split('.')) ?? -1) + 1;
  const id = parentId ? `${parentId}.${nextId}` : nextId.toString();
  const type =
    typeof parent.type.children === 'string'
      ? { name: typeName, type: parent.type.children }
      : parent.type.children[typeName];

  parent.value.push({
    id,
    value: type.children ? [] : type.default?.toString() ?? '',
    type,
  });

  return cloned;
}

function inputUpdate(params, { id, type, value }) {
  const cloned = cloneParams(params);
  const node = findInput(cloned, id);
  if (type) {
    const parent = findInput(cloned, id.substring(0, id.lastIndexOf('.')));
    const newType =
      typeof parent.type.children === 'string'
        ? { name: type, type: parent.type.children }
        : parent.type.children[type];
    if (node.type.type !== newType.type.type)
      node.value = newType.children ? [] : newType.default?.toString() ?? '';
    node.type = newType;
  }
  if (value != null) node.value = value;

  return cloned;
}

function findInput(params, id, Delete = false) {
  if (!id) return params;
  let end = -1;
  while ((end = id.indexOf('.', end + 1)) > 0)
    params = params.value.find((input) => input.id === id.substring(0, end));
  const index = params.value.findIndex((input) => input.id === id);
  return Delete ? params.value.splice(index, 1)[0] : params.value[index];
}

export function reducer(state, action) {
  if (state == null) {
    state = { http: {}, params: {}, query: {}, request: {} };

    return [
      [ACTION.SET_URL, 'http://localhost:8080/search/'],
      [ACTION.SET_QUERY, 'yql='],
      [ACTION.SET_METHOD, 'POST'],
    ].reduce((s, [action, data]) => reducer(s, { action, data }), state);
  }

  const result = preReducer(state, action);
  const { request: sr, params: sp, query: sq } = state;
  const { request: rr, params: rp, query: rq } = result;

  if ((sp.value !== rp.value && sq === rq) || sr.method !== rr.method)
    result.query = { input: inputsToQuery(rr.method, rp.value) };

  const input = result.query.input;
  if (sr.url !== rr.url || sq.input !== input || sr.method !== rr.method) {
    if (rr.method === 'POST') {
      result.request = { ...result.request, fullUrl: rr.url, body: input };
    } else {
      const url = new URL(rr.url);
      url.search = input;
      result.request = {
        ...result.request,
        fullUrl: url.toString(),
        body: null,
      };
    }
  }

  return result;
}

function preReducer(state, { action, data }) {
  switch (action) {
    case ACTION.SET_QUERY: {
      try {
        const value = queryToInputs(state.request.method, data);
        return {
          ...state,
          params: { ...root, value },
          query: { input: data },
        };
      } catch (error) {
        return { ...state, query: { input: data, error: error.message } };
      }
    }
    case ACTION.SET_HTTP:
      return { ...state, http: data };
    case ACTION.SET_METHOD:
      return { ...state, request: { ...state.request, method: data } };
    case ACTION.SET_URL:
      return { ...state, request: { ...state.request, url: data } };

    case ACTION.INPUT_ADD:
      return { ...state, params: inputAdd(state.params, data) };
    case ACTION.INPUT_UPDATE:
      return { ...state, params: inputUpdate(state.params, data) };
    case ACTION.INPUT_REMOVE: {
      const cloned = cloneParams(state.params);
      findInput(cloned, data, true);
      return { ...state, params: cloned };
    }

    default:
      throw new Error(`Unknown action ${action}`);
  }
}

export function QueryBuilderProvider({ children }) {
  const [value, dispatch] = useReducer(reducer, null, reducer);
  _dispatch = dispatch;
  return <context.Provider value={value}>{children}</context.Provider>;
}

export function useQueryBuilderContext(selector) {
  const func = typeof selector === 'string' ? (c) => c[selector] : selector;
  return useContextSelector(context, func);
}

export function dispatch(action, data) {
  _dispatch({ action, data });
}
