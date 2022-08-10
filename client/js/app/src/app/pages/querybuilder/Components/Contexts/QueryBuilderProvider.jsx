import React, { useReducer } from 'react';
import { createContext, useContextSelector } from 'use-context-selector';
import { cloneDeep, last } from 'lodash';
import parameters from 'app/pages/querybuilder/parameters';

let _dispatch;
const root = { type: { children: parameters } };
const context = createContext(null);

export const ACTION = Object.freeze({
  SET_QUERY: 0,
  SET_HTTP: 1,

  INPUT_ADD: 10,
  INPUT_UPDATE: 11,
  INPUT_REMOVE: 12,
});

function inputsToJson(inputs) {
  return Object.fromEntries(
    inputs.map(({ children, input, type: { name, type } }) => [
      name,
      children ? inputsToJson(children) : parseInput(input, type),
    ])
  );
}

function jsonToInputs(json, parent) {
  return Object.entries(json).map(([key, value], i) => {
    const node = {
      id: parent.id ? `${parent.id}.${i}` : i.toString(),
      type: parent.type.children[key],
      parent,
    };
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

function inputAdd(query, { id: parentId, type: typeName }) {
  const inputs = cloneDeep(query.children);
  const parent = parentId ? findInput(inputs, parentId) : query;

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
  return { ...query, children: inputs };
}

function inputUpdate(query, { id, ...props }) {
  const keys = Object.keys(props);
  if (keys.length !== 1)
    throw new Error(`Expected to update exactly 1 input prop, got: ${keys}`);
  if (!['input', 'type'].includes(keys[0]))
    throw new Error(`Cannot update key ${keys[0]}`);

  const inputs = cloneDeep(query.children);
  const node = Object.assign(findInput(inputs, id), props);
  if (node.type.children) node.children = [];
  else delete node.children;
  return { ...query, children: inputs };
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
  if (state.query.children !== result.query.children) {
    const json = inputsToJson(result.query.children);
    result.query.input = JSON.stringify(json, null, 4);
  }

  return result;
}
function preReducer(state, { action, data }) {
  switch (action) {
    case ACTION.SET_QUERY: {
      try {
        const children = jsonToInputs(JSON.parse(data), root);
        return { ...state, query: { ...root, children } };
      } catch (error) {
        alert(`Failed to parse query: ${error}`); // TODO: Change to toast
        return state;
      }
    }
    case ACTION.SET_HTTP:
      return { ...state, http: data };

    case ACTION.INPUT_ADD:
      return { ...state, query: inputAdd(state.query, data) };
    case ACTION.INPUT_UPDATE:
      return { ...state, query: inputUpdate(state.query, data) };
    case ACTION.INPUT_REMOVE: {
      const inputs = cloneDeep(state.query.children);
      findInput(inputs, data, true);
      return { ...state, query: { ...state.query, children: inputs } };
    }

    default:
      throw new Error(`Unknown action ${action}`);
  }
}

export function QueryBuilderProvider({ children }) {
  const [value, dispatch] = useReducer(
    reducer,
    { http: {}, query: { ...root, input: '', children: [] } },
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
