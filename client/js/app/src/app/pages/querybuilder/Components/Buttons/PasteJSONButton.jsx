import React, { useContext, useState } from 'react';
import ImageButton from './ImageButton';
import pasteImage from '../../assets/img/paste.svg';
import { QueryInputContext } from '../Contexts/QueryInputContext';

export default function PasteJSONButton() {
  const { inputs, setInputs, id, setId, levelZeroParameters, childMap } =
    useContext(QueryInputContext);
  const [paste, setPaste] = useState(false);

  const handleClick = (e) => {
    //alert('Button is non-functional');
    setPaste(true);
    window.addEventListener('paste', handlePaste);
  };

  const handlePaste = (e) => {
    setPaste(false);
    const pastedData = e.clipboardData.getData('text');
    alert('Converting JSON: \n\n ' + pastedData);
    window.removeEventListener('paste', handlePaste);
    convertPastedJSON(pastedData);
  };

  const convertPastedJSON = (pastedData) => {
    try {
      var json = JSON.parse(pastedData);
      const newInputs = buildFromJSON(json, id + 1);
      setInputs(newInputs);
    } catch (error) {
      console.log(error);
      alert('Could not parse JSON, with error-message: \n\n' + error.message);
    }
  };

  const buildFromJSON = (json, id, parentTypeof) => {
    let newInputs = [];
    let keys = Object.keys(json);
    for (let i = 0; i < keys.length; i++) {
      let childId = 1;
      let newInput = { id: `${id}`, type: keys[i] };
      if (typeof json[keys[i]] === 'object') {
        newInput['typeof'] = 'Parent';
        newInput['input'] = '';
        newInput['hasChildren'] = true;
        let tempId = id + '.' + childId;
        childId += 1;
        let type;
        if (id.length > 1) {
          type = parentTypeof + '_' + keys[i];
        } else {
          type = keys[i];
        }
        newInput['children'] = buildFromJSON(json[keys[i]], tempId, type);
      } else {
        if (id.length > 1) {
          const choices = childMap[parentTypeof];
          newInput['typeof'] = choices[keys[i]].type;
        } else {
          newInput['typeof'] = levelZeroParameters[keys[i]].type;
        }
        newInput['input'] = json[keys[i]];
        newInput['hasChildren'] = false;
        newInput['children'] = [];
      }
      setId(id + 1);
      newInputs.push(newInput);
    }
    return newInputs;
  };

  return (
    <>
      <ImageButton
        id="pasteJSON"
        className="pasteJSON"
        image={pasteImage}
        style={{ marginTop: '-2px', marginRight: '3px' }}
        onClick={handleClick}
      >
        {paste ? 'Press CMD + V' : 'Paste JSON'}
      </ImageButton>
    </>
  );
}
