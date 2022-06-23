import React, { useState, createContext } from 'react';

export const QueryInputContext = createContext();

export const QueryInputProvider = (prop) => {
  const [inputs, setInputs] = useState([
    {
      id: 1,
      type: '',
      input: '',
      hasChildren: false,
      children: [],
    },
  ]);
  const [id, setId] = useState(1);
  const levelZeroParameters = {
    yql: { name: 'yql', type: 'String', hasChildren: false },
    hits: { name: 'hits', type: 'Integer', hasChildren: false },
    offset: { name: 'offset', type: 'Integer', hasChildren: false },
    queryProfile: { name: 'queryProfile', type: 'String', hasChildren: false },
    noCache: { name: 'noCache', type: 'Boolean', hasChildren: false },
    groupingSessionCache: {
      name: 'groupingSessionCache',
      type: 'Boolean',
      hasChildren: false,
    },
    searchChain: { name: 'searchChain', type: 'String', hasChildren: false },
    timeout: { name: 'timeout', type: 'Float', hasChildren: false },
    tracelevel: { name: 'tracelevel', type: 'Parent', hasChildren: true },
    traceLevel: { name: 'traceLevel', type: 'Integer', hasChildren: false },
    explainLevel: { name: 'explainLevel', type: 'Integer', hasChildren: false },
    explainlevel: { name: 'explainlevel', type: 'Integer', hasChildren: false },
    model: { name: 'model', type: 'Parent', hasChildren: true },
    ranking: { name: 'ranking', type: 'Parent', hasChildren: true },
    collapse: { name: 'collapse', type: 'Parent', hasChildren: true },
    collapsesize: { name: 'collapsesize', type: 'Integer', hasChildren: false },
    collapsefield: {
      name: 'collapsefield',
      type: 'String',
      hasChildren: false,
    },
    presentation: { name: 'presentation', type: 'Parent', hasChildren: true },
    pos: { name: 'pos', type: 'Parent', hasChildren: true },
    streaming: { name: 'streaming', type: 'Parent', hasChildren: true },
    rules: { name: 'rules', type: 'Parent', hasChildren: true },
    recall: { name: 'recall', type: 'List', hasChildren: false },
    user: { name: 'user', type: 'String', hasChildren: false },
    metrics: { name: 'metrics', type: 'Parent', hasChildren: true },
  };
  const childMap = {
    collapse: [{ child: 'summary', type: 'String', hasChildren: false }],
    metrics: [{ child: 'ignore', type: 'Boolean', hasChildren: false }],
    model: [
      { child: 'defaultIndex', type: 'String', hasChildren: false },
      { child: 'encoding', type: 'String', hasChildren: false },
      { child: 'language', type: 'String', hasChildren: false },
      { child: 'queryString', type: 'String', hasChildren: false },
      { child: 'restrict', type: 'List', hasChildren: false },
      { child: 'searchPath', type: 'String', hasChildren: false },
      { child: 'sources', type: 'List', hasChildren: false },
      { child: 'type', type: 'String', hasChildren: false },
    ],
    pos: [
      { child: 'll', type: 'String', hasChildren: false },
      { child: 'radius', type: 'String', hasChildren: false },
      { child: 'bb', type: 'List', hasChildren: false },
      { child: 'attribute', type: 'String', hasChildren: false },
    ],
    presentation: [
      { child: 'bolding', type: 'Boolean', hasChildren: false },
      { child: 'format', type: 'String', hasChildren: false },
      { child: 'summary', type: 'String', hasChildren: false },
      { child: 'template', type: 'String', hasChildren: false },
      { child: 'timing', type: 'Boolean', hasChildren: false },
    ],
    ranking: [
      { child: 'location', type: 'String', hasChildren: false },
      { child: 'features', type: 'String', hasChildren: false },
      { child: 'listFeatures', type: 'Boolean', hasChildren: false },
      { child: 'profile', type: 'String', hasChildren: false },
      { child: 'properties', type: 'String', hasChildren: false },
      { child: 'sorting', type: 'String', hasChildren: false },
      { child: 'freshness', type: 'String', hasChildren: false },
      { child: 'queryCache', type: 'Boolean', hasChildren: false },
      { child: 'matchPhase', type: 'Parent', hasChildren: true },
    ],
    ranking_matchPhase: [
      { child: 'maxHits', type: 'Long', hasChildren: false },
      { child: 'attribute', type: 'String', hasChildren: false },
      { child: 'ascending', type: 'Boolean', hasChildren: false },
      { child: 'diversity', type: 'Parent', hasChildren: true },
    ],
    ranking_matchPhase_diversity: [
      { child: 'attribute', type: 'String', hasChildren: false },
      { child: 'minGroups', type: 'Long', hasChildren: false },
    ],
    rules: [
      { child: 'off', type: 'Boolean', hasChildren: false },
      { child: 'rulebase', type: 'String', hasChildren: false },
    ],
    streaming: [
      { child: 'userid', type: 'Integer', hasChildren: false },
      { child: 'groupname', type: 'String', hasChildren: false },
      { child: 'selection', type: 'String', hasChildren: false },
      { child: 'priority', type: 'String', hasChildren: false },
      { child: 'maxbucketspervisitor', type: 'Integer', hasChildren: false },
    ],
    trace: [{ child: 'timestamps', type: 'Boolean', hasChildren: false }],
    tracelevel: [{ child: 'rules', type: 'Integer', hasChildren: false }],
  };

  return (
    <QueryInputContext.Provider
      value={{ inputs, setInputs, id, setId, levelZeroParameters, childMap }}
    >
      {prop.children}
    </QueryInputContext.Provider>
  );
};
