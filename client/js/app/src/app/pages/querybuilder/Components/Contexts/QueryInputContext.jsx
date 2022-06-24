import React, { useState, createContext } from 'react';

export const QueryInputContext = createContext();

export const QueryInputProvider = (prop) => {
  // inputs reflect the state of the individual QueryInputs
  const [inputs, setInputs] = useState([
    {
      id: 1,
      type: '',
      input: '',
      hasChildren: false,
      children: [],
    },
  ]);

  // id is the id if the newest QueryInput, gets updated each time a new one is added
  const [id, setId] = useState(1);

  // These are the methods that can be chosen in a QueryInput
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

  // Children of the levelZeroParameters that has attributes
  const childMap = {
    collapse: {
      summary: { child: 'summary', type: 'String', hasChildren: false },
    },
    metrics: {
      ignore: { child: 'ignore', type: 'Boolean', hasChildren: false },
    },
    model: {
      defaultIndex: {
        child: 'defaultIndex',
        type: 'String',
        hasChildren: false,
      },
      encoding: { child: 'encoding', type: 'String', hasChildren: false },
      language: { child: 'language', type: 'String', hasChildren: false },
      queryString: { child: 'queryString', type: 'String', hasChildren: false },
      restrict: { child: 'restrict', type: 'List', hasChildren: false },
      searchPath: { child: 'searchPath', type: 'String', hasChildren: false },
      sources: { child: 'sources', type: 'List', hasChildren: false },
      type: { child: 'type', type: 'String', hasChildren: false },
    },
    pos: {
      ll: { child: 'll', type: 'String', hasChildren: false },
      radius: { child: 'radius', type: 'String', hasChildren: false },
      bb: { child: 'bb', type: 'List', hasChildren: false },
      attribute: { child: 'attribute', type: 'String', hasChildren: false },
    },
    presentation: {
      bolding: { child: 'bolding', type: 'Boolean', hasChildren: false },
      format: { child: 'format', type: 'String', hasChildren: false },
      summary: { child: 'summary', type: 'String', hasChildren: false },
      template: { child: 'template', type: 'String', hasChildren: false },
      timing: { child: 'timing', type: 'Boolean', hasChildren: false },
    },
    ranking: {
      location: { child: 'location', type: 'String', hasChildren: false },
      features: { child: 'features', type: 'String', hasChildren: false },
      listFeatures: {
        child: 'listFeatures',
        type: 'Boolean',
        hasChildren: false,
      },
      profile: { child: 'profile', type: 'String', hasChildren: false },
      properties: { child: 'properties', type: 'String', hasChildren: false },
      sorting: { child: 'sorting', type: 'String', hasChildren: false },
      freshness: { child: 'freshness', type: 'String', hasChildren: false },
      queryCache: { child: 'queryCache', type: 'Boolean', hasChildren: false },
      matchPhase: { child: 'matchPhase', type: 'Parent', hasChildren: true },
    },
    ranking_matchPhase: {
      maxHits: { child: 'maxHits', type: 'Long', hasChildren: false },
      attribute: { child: 'attribute', type: 'String', hasChildren: false },
      ascending: { child: 'ascending', type: 'Boolean', hasChildren: false },
      diversity: { child: 'diversity', type: 'Parent', hasChildren: true },
    },
    ranking_matchPhase_diversity: {
      attribute: { child: 'attribute', type: 'String', hasChildren: false },
      minGroups: { child: 'minGroups', type: 'Long', hasChildren: false },
    },
    rules: {
      off: { child: 'off', type: 'Boolean', hasChildren: false },
      rulebase: { child: 'rulebase', type: 'String', hasChildren: false },
    },
    streaming: {
      userid: { child: 'userid', type: 'Integer', hasChildren: false },
      groupname: { child: 'groupname', type: 'String', hasChildren: false },
      selection: { child: 'selection', type: 'String', hasChildren: false },
      priority: { child: 'priority', type: 'String', hasChildren: false },
      maxbucketspervisitor: {
        child: 'maxbucketspervisitor',
        type: 'Integer',
        hasChildren: false,
      },
    },
    trace: {
      timestamps: { child: 'timestamps', type: 'Boolean', hasChildren: false },
    },
    tracelevel: {
      rules: { child: 'rules', type: 'Integer', hasChildren: false },
    },
  };

  return (
    <QueryInputContext.Provider
      value={{ inputs, setInputs, id, setId, levelZeroParameters, childMap }}
    >
      {prop.children}
    </QueryInputContext.Provider>
  );
};
