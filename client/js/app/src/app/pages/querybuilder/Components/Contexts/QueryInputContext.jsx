import React, { useState, createContext } from 'react';

export const QueryInputContext = createContext();

export const QueryInputProvider = (prop) => {
  // This is the id of the newest QueryInput, gets updated each time a new one is added
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

  // Children of the levelZeroParameters that have child attributes
  const childMap = {
    collapse: {
      summary: { name: 'summary', type: 'String', hasChildren: false },
    },
    metrics: {
      ignore: { name: 'ignore', type: 'Boolean', hasChildren: false },
    },
    model: {
      defaultIndex: {
        name: 'defaultIndex',
        type: 'String',
        hasChildren: false,
      },
      encoding: { name: 'encoding', type: 'String', hasChildren: false },
      language: { name: 'language', type: 'String', hasChildren: false },
      queryString: { name: 'queryString', type: 'String', hasChildren: false },
      restrict: { name: 'restrict', type: 'List', hasChildren: false },
      searchPath: { name: 'searchPath', type: 'String', hasChildren: false },
      sources: { name: 'sources', type: 'List', hasChildren: false },
      type: { name: 'type', type: 'String', hasChildren: false },
    },
    pos: {
      ll: { name: 'll', type: 'String', hasChildren: false },
      radius: { name: 'radius', type: 'String', hasChildren: false },
      bb: { name: 'bb', type: 'List', hasChildren: false },
      attribute: { name: 'attribute', type: 'String', hasChildren: false },
    },
    presentation: {
      bolding: { name: 'bolding', type: 'Boolean', hasChildren: false },
      format: { name: 'format', type: 'String', hasChildren: false },
      summary: { name: 'summary', type: 'String', hasChildren: false },
      template: { name: 'template', type: 'String', hasChildren: false },
      timing: { name: 'timing', type: 'Boolean', hasChildren: false },
    },
    ranking: {
      location: { name: 'location', type: 'String', hasChildren: false },
      features: { name: 'features', type: 'String', hasChildren: false },
      listFeatures: {
        name: 'listFeatures',
        type: 'Boolean',
        hasChildren: false,
      },
      profile: { name: 'profile', type: 'String', hasChildren: false },
      properties: { name: 'properties', type: 'String', hasChildren: false },
      sorting: { name: 'sorting', type: 'String', hasChildren: false },
      freshness: { name: 'freshness', type: 'String', hasChildren: false },
      queryCache: { name: 'queryCache', type: 'Boolean', hasChildren: false },
      matchPhase: { name: 'matchPhase', type: 'Parent', hasChildren: true },
    },
    ranking_matchPhase: {
      maxHits: { name: 'maxHits', type: 'Long', hasChildren: false },
      attribute: { name: 'attribute', type: 'String', hasChildren: false },
      ascending: { name: 'ascending', type: 'Boolean', hasChildren: false },
      diversity: { name: 'diversity', type: 'Parent', hasChildren: true },
    },
    ranking_matchPhase_diversity: {
      attribute: { name: 'attribute', type: 'String', hasChildren: false },
      minGroups: { name: 'minGroups', type: 'Long', hasChildren: false },
    },
    rules: {
      off: { name: 'off', type: 'Boolean', hasChildren: false },
      rulebase: { name: 'rulebase', type: 'String', hasChildren: false },
    },
    streaming: {
      userid: { name: 'userid', type: 'Integer', hasChildren: false },
      groupname: { name: 'groupname', type: 'String', hasChildren: false },
      selection: { name: 'selection', type: 'String', hasChildren: false },
      priority: { name: 'priority', type: 'String', hasChildren: false },
      maxbucketspervisitor: {
        name: 'maxbucketspervisitor',
        type: 'Integer',
        hasChildren: false,
      },
    },
    trace: {
      timestamps: { name: 'timestamps', type: 'Boolean', hasChildren: false },
    },
    tracelevel: {
      rules: { name: 'rules', type: 'Integer', hasChildren: false },
    },
  };

  const firstChoice = levelZeroParameters[Object.keys(levelZeroParameters)[0]];

  const [inputs, setInputs] = useState([
    {
      id: '1',
      type: firstChoice.name,
      typeof: firstChoice.type,
      input: '',
      hasChildren: false,
      children: [],
    },
  ]);

  const [selectedItems, setSelectedItems] = useState([]);

  return (
    <QueryInputContext.Provider
      value={{
        inputs,
        setInputs,
        id,
        setId,
        levelZeroParameters,
        childMap,
        selectedItems,
        setSelectedItems,
      }}
    >
      {prop.children}
    </QueryInputContext.Provider>
  );
};
