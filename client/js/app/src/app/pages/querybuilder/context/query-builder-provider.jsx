import { cloneDeep, last } from 'lodash';
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

function inputsToSearchParams(inputs, parent) {
  return inputs.reduce((acc, { input, type: { name }, children }) => {
    const key = parent ? `${parent}.${name}` : name;
    return Object.assign(
      acc,
      children ? inputsToSearchParams(children, key) : { [key]: input }
    );
  }, {});
}

function inputsToJson(inputs) {
  return Object.fromEntries(
    inputs.map(({ children, input, type: { name, type } }) => [
      name,
      children ? inputsToJson(children) : parseInput(input, type),
    ])
  );
}

export function jsonToInputs(json, parent = root) {
  return Object.entries(json).map(([key, value], i) => {
    const node = {
      id: parent.id ? `${parent.id}.${i}` : i.toString(),
      type: parent.type.children[key],
      parent,
    };
    if (!node.type) {
      const location = parent.type.name
        ? `under ${parent.type.name}`
        : 'on root level';
      throw new Error(`Unknown property '${key}' ${location}`);
    }
    if (typeof value === 'object') {
      node.input = '';
      node.children = jsonToInputs(value, node);
    } else node.input = value.toString();
    return node;
  });
}

function parseInput(input, type) {
  if (type === 'Integer' || type === 'Long') return parseInt(input);
  if (type === 'Float') return parseFloat(input);
  if (type === 'Boolean') return input.toLowerCase() === 'true';
  return input;
}

function inputAdd(params, { id: parentId, type: typeName }) {
  const inputs = cloneDeep(params.children);
  const parent = parentId ? findInput(inputs, parentId) : params;

  const nextId =
    parseInt(last(last(parent.children)?.id?.split('.')) ?? -1) + 1;
  const id = parentId ? `${parentId}.${nextId}` : nextId.toString();
  const type = parent.type.children[typeName];

  parent.children.push(
    Object.assign(
      { id, input: '', type, parent },
      type.children && { children: [] }
    )
  );
  return { ...params, children: inputs };
}

function inputUpdate(params, { id, ...props }) {
  const keys = Object.keys(props);
  if (keys.length !== 1)
    throw new Error(`Expected to update exactly 1 input prop, got: ${keys}`);
  if (!['input', 'type'].includes(keys[0]))
    throw new Error(`Cannot update key ${keys[0]}`);

  const inputs = cloneDeep(params.children);
  const node = Object.assign(findInput(inputs, id), props);
  if (node.type.children) node.children = [];
  else delete node.children;
  return { ...params, children: inputs };
}

function findInput(inputs, id, Delete = false) {
  let end = -1;
  while ((end = id.indexOf('.', end + 1)) > 0)
    inputs = inputs.find((input) => input.id === id.substring(0, end)).children;
  const index = inputs.findIndex((input) => input.id === id);
  return Delete ? inputs.splice(index, 1)[0] : inputs[index];
}

function reducer(state, action) {
  const result = preReducer(state, action);
  const { request: sr, params: sp } = state;
  const { request: rr, params: rp } = result;
  if (sp.children !== rp.children || sr.method !== rr.method) {
    result.query =
      rr.method === 'POST'
        ? JSON.stringify(inputsToJson(rp.children), null, 4)
        : new URLSearchParams(inputsToSearchParams(rp.children)).toString();
  }

  if (sr.url !== rr.url || state.query !== result.query) {
    if (rr.method === 'POST') {
      rr.fullUrl = rr.url;
      rr.body = result.query;
    } else {
      const url = new URL(rr.url);
      url.search = result.query;
      rr.fullUrl = url.toString();
      rr.body = null;
    }
  }
  return result;
}

function preReducer(state, { action, data }) {
  switch (action) {
    case ACTION.SET_QUERY: {
      try {
        const children = jsonToInputs(JSON.parse(data));
        return { ...state, params: { ...root, children } };
      } catch (error) {
        return state;
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
      const inputs = cloneDeep(state.params.children);
      findInput(inputs, data, true);
      return { ...state, params: { ...state.params, children: inputs } };
    }

    default:
      throw new Error(`Unknown action ${action}`);
  }
}

export function QueryBuilderProvider({ children }) {
  const [value, dispatch] = useReducer(
    reducer,
    {
      request: { url: 'http://localhost:8080/search/', method: 'POST' },
      http: {},
      params: { ...root, children: [] },
    },
    (s) => reducer(s, { action: ACTION.SET_QUERY, data: '{"yql":""}' })
  );
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
