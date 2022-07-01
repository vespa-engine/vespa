import React, { useContext, useState } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';
import SimpleButton from '../Buttons/SimpleButton';
import SimpleForm from './SimpleForm';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import { ResponseContext } from '../Contexts/ResponseContext';

export default function SendQuery() {
  const { inputs } = useContext(QueryInputContext);
  const { response, setResponse } = useContext(ResponseContext);
  const messageMethods = { post: { name: 'POST' }, get: { name: 'GET' } };
  const [method, setMethod] = useState(messageMethods.post.name);
  const [url, setUrl] = useState('http://localhost:8080/search/');

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
      let resultObject = JSON.stringify(result);
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
        break;

      case 'Float':
        return parseFloat(input);
        break;

      case 'Boolean':
        return input.toLowerCase() === 'true' ? true : false;
        break;

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
      <SimpleButton id="send" className="button" onClick={handleClick}>
        Send
      </SimpleButton>
    </>
  );
}
