// https://docs.vespa.ai/en/reference/query-api-reference.html
export default {
  yql: { name: 'yql', type: 'String' },

  // Native Execution Parameters
  hits: { name: 'hits', type: 'Integer' },
  offset: { name: 'offset', type: 'Integer' },
  queryProfile: { name: 'queryProfile', type: 'String' },
  groupingSessionCache: { name: 'groupingSessionCache', type: 'Boolean' },
  searchChain: { name: 'searchChain', type: 'String' },
  timeout: { name: 'timeout', type: 'Double' },
  noCache: { name: 'noCache', type: 'Boolean' },

  // Query Model
  model: {
    name: 'model',
    type: 'Parent',
    children: {
      defaultIndex: { name: 'defaultIndex', type: 'String' },
      encoding: { name: 'encoding', type: 'String' },
      filter: { name: 'filter', type: 'String' },
      locale: { name: 'locale', type: 'String' },
      language: { name: 'language', type: 'String' },
      queryString: { name: 'queryString', type: 'String' },
      restrict: { name: 'restrict', type: 'String' },
      searchPath: { name: 'searchPath', type: 'String' },
      sources: { name: 'sources', type: 'String' },
      type: { name: 'type', type: 'String' },
    },
  },

  // Ranking
  ranking: {
    name: 'ranking',
    type: 'Parent',
    children: {
      location: { name: 'location', type: 'String' },
      features: { name: 'features', type: 'Parent', children: 'String' },
      listFeatures: { name: 'listFeatures', type: 'Boolean' },
      profile: { name: 'profile', type: 'String' },
      properties: { name: 'properties', type: 'String' },
      sorting: { name: 'sorting', type: 'String' },
      freshness: { name: 'freshness', type: 'String' },
      queryCache: { name: 'queryCache', type: 'Boolean' },
      rerankCount: { name: 'rerankCount', type: 'Integer' },
      matching: {
        name: 'matching',
        type: 'Parent',
        children: {
          numThreadsPerSearch: { name: 'numThreadsPerSearch', type: 'Integer' },
          minHitsPerThread: { name: 'minHitsPerThread', type: 'Integer' },
          numSearchPartitions: { name: 'numSearchPartitions', type: 'Integer' },
          termwiseLimit: { name: 'termwiseLimit', type: 'Float' },
          postFilterThreshold: { name: 'postFilterThreshold', type: 'Float' },
          approximateThreshold: {
            name: 'approximateThreshold',
            type: 'Float',
          },
        },
      },
      matchPhase: {
        name: 'matchPhase',
        type: 'Parent',
        children: {
          attribute: { name: 'attribute', type: 'String' },
          maxHits: { name: 'maxHits', type: 'Integer' },
          ascending: { name: 'ascending', type: 'Boolean' },
          diversity: {
            name: 'diversity',
            type: 'Parent',
            children: {
              attribute: { name: 'attribute', type: 'String' },
              minGroups: { name: 'minGroups', type: 'Integer' },
            },
          },
        },
      },
    },
  },

  // Grouping
  collapsesize: { name: 'collapsesize', type: 'Integer' },
  collapsefield: { name: 'collapsefield', type: 'String' },
  collapse: {
    name: 'collapse',
    type: 'Parent',
    children: {
      summary: { name: 'summary', type: 'String' },
    },
  },
  grouping: {
    name: 'grouping',
    type: 'Parent',
    children: {
      defaultMaxGroups: { name: 'defaultMaxGroups', type: 'Integer' },
      defaultMaxHits: { name: 'defaultMaxHits', type: 'Integer' },
      globalMaxGroups: { name: 'globalMaxGroups', type: 'Integer' },
      defaultPrecisionFactor: {
        name: 'defaultPrecisionFactor',
        type: 'Float',
      },
    },
  },

  // Presentation
  presentation: {
    name: 'presentation',
    type: 'Parent',
    children: {
      bolding: { name: 'bolding', type: 'Boolean' },
      format: {
        name: 'format',
        type: 'Parent',
        children: {
          tensors: { name: 'tensors', type: 'String' },
        },
      },
      template: { name: 'template', type: 'String' },
      summary: { name: 'summary', type: 'String' },
      timing: { name: 'timing', type: 'Boolean' },
    },
  },

  // Tracing
  trace: {
    name: 'trace',
    type: 'Parent',
    children: {
      level: { name: 'level', type: 'Integer' },
      explainLevel: { name: 'explainLevel', type: 'Integer' },
      profileDepth: { name: 'profileDepth', type: 'Integer' },
      timestamps: { name: 'timestamps', type: 'Boolean' },
      query: { name: 'query', type: 'Boolean' },
    },
  },

  // Semantic Rules
  rules: {
    name: 'rules',
    type: 'Parent',
    children: {
      off: { name: 'off', type: 'Boolean' },
      rulebase: { name: 'rulebase', type: 'String' },
    },
  },
  tracelevel: {
    name: 'tracelevel',
    type: 'Parent',
    children: {
      rules: { name: 'rules', type: 'Integer' },
    },
  },

  // Dispatch
  dispatch: {
    name: 'dispatch',
    type: 'Parent',
    children: {
      topKProbability: { name: 'topKProbability', type: 'Float' },
    },
  },

  // Other
  recall: { name: 'recall', type: 'String' },
  user: { name: 'user', type: 'String' },
  hitcountestimate: { name: 'hitcountestimate', type: 'Boolean' },
  metrics: {
    name: 'metrics',
    type: 'Parent',
    children: {
      ignore: { name: 'ignore', type: 'Boolean' },
    },
  },
  weakAnd: {
    name: 'weakAnd',
    type: 'Parent',
    children: {
      replace: { name: 'replace', type: 'Boolean' },
    },
  },
  wand: {
    name: 'wand',
    type: 'Parent',
    children: {
      hits: { name: 'hits', type: 'Integer' },
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
};
