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
  if (!params.children) return { ...params };
  return { ...params, children: params.children.map(cloneParams) };
}

function inputsToSearchParams(inputs, parent) {
  return inputs.reduce((acc, { input, type: { name }, children }) => {
    const key = parent ? `${parent}.${name}` : name;
    return Object.assign(
      acc,
      children ? inputsToSearchParams(children, key) : { [key]: input }
    );
  }, {});
}

function searchParamsToInputs(search) {
  const json = [...new URLSearchParams(search).entries()].reduce(
    (acc, [key, value]) => set(acc, key, value),
    {}
  );
  return jsonToInputs(json);
}

function inputsToJson(inputs) {
  return Object.fromEntries(
    inputs.map(({ children, input, type: { name, type } }) => [
      name,
      children ? inputsToJson(children) : parseInput(input, type),
    ])
  );
}

function jsonToInputs(json, parent = root) {
  return Object.entries(json).map(([key, value], i) => {
    const node = {
      id: parent.id ? `${parent.id}.${i}` : i.toString(),
      type: parent.type.children[key],
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
      node.input = '';
      node.children = jsonToInputs(value, node);
    } else {
      if (node.type.children)
        throw new Error(
          `Property '${key}' cannot have value, supported children: ${Object.keys(
            node.type.children
          ).sort()}`
        );
      node.input = value?.toString();
    }
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
  const cloned = cloneParams(params);
  const parent = findInput(cloned, parentId);

  const nextId =
    parseInt(last(last(parent.children)?.id?.split('.')) ?? -1) + 1;
  const id = parentId ? `${parentId}.${nextId}` : nextId.toString();
  const type = parent.type.children[typeName];

  parent.children.push(
    Object.assign({ id, input: '', type }, type.children && { children: [] })
  );

  return cloned;
}

function inputUpdate(params, { id, type, input }) {
  const cloned = cloneParams(params);
  const node = findInput(cloned, id);
  if (type) {
    const parent = findInput(cloned, id.substring(0, id.lastIndexOf('.')));
    node.type = parent.type.children[type];
  }
  if (input) node.input = input;

  if (node.type.children) node.children = [];
  else delete node.children;
  return cloned;
}

function findInput(params, id, Delete = false) {
  if (!id) return params;
  let end = -1;
  while ((end = id.indexOf('.', end + 1)) > 0)
    params = params.children.find((input) => input.id === id.substring(0, end));
  const index = params.children.findIndex((input) => input.id === id);
  return Delete ? params.children.splice(index, 1)[0] : params.children[index];
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

  if ((sp.children !== rp.children && sq === rq) || sr.method !== rr.method)
    result.query = {
      input:
        rr.method === 'POST'
          ? JSON.stringify(inputsToJson(rp.children), null, 4)
          : new URLSearchParams(inputsToSearchParams(rp.children)).toString(),
    };

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
        const children =
          state.request.method === 'POST'
            ? jsonToInputs(JSON.parse(data))
            : searchParamsToInputs(data);
        return {
          ...state,
          params: { ...root, children },
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
