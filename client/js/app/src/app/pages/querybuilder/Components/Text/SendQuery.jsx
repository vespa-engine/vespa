import React, { useContext, useEffect, useState } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import { ResponseContext } from '../Contexts/ResponseContext';
import { QueryContext } from '../Contexts/QueryContext';
import SimpleForm from './SimpleForm';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function SendQuery() {
  const { inputs } = useContext(QueryInputContext);
  const { setResponse } = useContext(ResponseContext);
  const { showQuery, setQuery } = useContext(QueryContext);

  const messageMethods = { post: { name: 'POST' }, get: { name: 'GET' } };
  const [method, setMethod] = useState(messageMethods.post.name);
  const [url, setUrl] = useState('http://localhost:8080/search/');

  useEffect(() => {
    const query = buildJSON(inputs, {});
    setQuery(JSON.stringify(query, undefined, 4));
  }, [showQuery]);

  const updateMethod = (e) => {
    e.preventDefault();
    const newMethod = e.target.value;
    setMethod(newMethod);
  };

  function handleClick() {
    const json = buildJSON(inputs, {});
    send(json);
  }

  async function send(json) {
    let responses = await fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json;charset=utf-8' },
      body: JSON.stringify(json),
    });
    if (responses.ok) {
      let result = await responses.json();
      let resultObject = JSON.stringify(result, undefined, 4);
      setResponse(resultObject);
    }
  }

  function buildJSON(inputs, json) {
    let queryJson = json;
    for (let i = 0; i < inputs.length; i++) {
      let current = inputs[i];
      let key = current.type;
      if (current.hasChildren) {
        let child = {};
        child = buildJSON(current.children, child);
        queryJson[key] = child;
      } else {
        queryJson[key] = parseInput(current.input, current.typeof);
      }
    }
    return queryJson;
  }

  function parseInput(input, type) {
    switch (type) {
      case 'Integer':
      case 'Long':
        return parseInt(input);

      case 'Float':
        return parseFloat(input);

      case 'Boolean':
        return input.toLowerCase() === 'true';

      default:
        return input;
    }
  }

  const updateUrl = (e) => {
    const newUrl = e.target.value;
    setUrl(newUrl);
  };

  return (
    <>
      <SimpleDropDownForm
        choices={messageMethods}
        id="method"
        className="methodselector"
        onChange={updateMethod}
      />
      <SimpleForm
        id="url"
        className="textbox"
        initial={url}
        size="30"
        onChange={updateUrl}
      />
      <button className="button" onClick={handleClick}>
        Send
      </button>
    </>
  );
}
