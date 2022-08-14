export default {
  yql: { name: 'yql', type: 'String' },
  hits: { name: 'hits', type: 'Integer' },
  offset: { name: 'offset', type: 'Integer' },
  queryProfile: { name: 'queryProfile', type: 'String' },
  noCache: { name: 'noCache', type: 'Boolean' },
  groupingSessionCache: { name: 'groupingSessionCache', type: 'Boolean' },
  searchChain: { name: 'searchChain', type: 'String' },
  timeout: { name: 'timeout', type: 'Float' },
  trace: {
    name: 'trace',
    type: 'Parent',
    children: {
      timestamps: { name: 'timestamps', type: 'Boolean' },
    },
  },
  tracelevel: {
    name: 'tracelevel',
    type: 'Parent',
    children: {
      rules: { name: 'rules', type: 'Integer' },
    },
  },
  traceLevel: { name: 'traceLevel', type: 'Integer' },
  explainLevel: { name: 'explainLevel', type: 'Integer' },
  explainlevel: { name: 'explainlevel', type: 'Integer' },
  model: {
    name: 'model',
    type: 'Parent',
    children: {
      defaultIndex: { name: 'defaultIndex', type: 'String' },
      encoding: { name: 'encoding', type: 'String' },
      language: { name: 'language', type: 'String' },
      queryString: { name: 'queryString', type: 'String' },
      restrict: { name: 'restrict', type: 'List' },
      searchPath: { name: 'searchPath', type: 'String' },
      sources: { name: 'sources', type: 'List' },
      type: { name: 'type', type: 'String' },
    },
  },
  ranking: {
    name: 'ranking',
    type: 'Parent',
    children: {
      location: { name: 'location', type: 'String' },
      features: { name: 'features', type: 'String' },
      listFeatures: { name: 'listFeatures', type: 'Boolean' },
      profile: { name: 'profile', type: 'String' },
      properties: { name: 'properties', type: 'String' },
      sorting: { name: 'sorting', type: 'String' },
      freshness: { name: 'freshness', type: 'String' },
      queryCache: { name: 'queryCache', type: 'Boolean' },
      matchPhase: {
        name: 'matchPhase',
        type: 'Parent',
        children: {
          maxHits: { name: 'maxHits', type: 'Long' },
          attribute: { name: 'attribute', type: 'String' },
          ascending: { name: 'ascending', type: 'Boolean' },
          diversity: {
            name: 'diversity',
            type: 'Parent',
            children: {
              attribute: { name: 'attribute', type: 'String' },
              minGroups: { name: 'minGroups', type: 'Long' },
            },
          },
        },
      },
    },
  },
  collapse: {
    name: 'collapse',
    type: 'Parent',
    children: {
      summary: { name: 'summary', type: 'String' },
    },
  },
  collapsesize: { name: 'collapsesize', type: 'Integer' },
  collapsefield: { name: 'collapsefield', type: 'String' },
  presentation: {
    name: 'presentation',
    type: 'Parent',
    children: {
      bolding: { name: 'bolding', type: 'Boolean' },
      format: { name: 'format', type: 'String' },
      summary: { name: 'summary', type: 'String' },
      template: { name: 'template', type: 'String' },
      timing: { name: 'timing', type: 'Boolean' },
    },
  },
  streaming: {
    name: 'streaming',
    type: 'Parent',
    children: {
      userid: { name: 'userid', type: 'Integer' },
      groupname: { name: 'groupname', type: 'String' },
      selection: { name: 'selection', type: 'String' },
      priority: { name: 'priority', type: 'String' },
      maxbucketspervisitor: { name: 'maxbucketspervisitor', type: 'Integer' },
    },
  },
  rules: {
    name: 'rules',
    type: 'Parent',
    children: {
      off: { name: 'off', type: 'Boolean' },
      rulebase: { name: 'rulebase', type: 'String' },
    },
  },
  recall: { name: 'recall', type: 'List' },
  user: { name: 'user', type: 'String' },
  metrics: {
    name: 'metrics',
    type: 'Parent',
    children: {
      ignore: { name: 'ignore', type: 'Boolean' },
    },
  },
};
