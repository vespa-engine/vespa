import React, { useContext, useState } from 'react';
import pasteImage from '../../assets/img/paste.svg';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import ImageButton from './ImageButton';
import {
  childMap,
  levelZeroParameters,
} from 'app/pages/querybuilder/parameters';

export default function PasteJSONButton() {
  const { setInputs, setId } = useContext(QueryInputContext);
  const [paste, setPaste] = useState(false);

  //TODO: fix that the second-level dropdowns do not get set properly when pasting a JSON query

  const handleClick = () => {
    setPaste(true);
    window.addEventListener('paste', handlePaste);
  };

  const handlePaste = (e) => {
    setPaste(false);
    // Stop data actually being pasted into div
    e.stopPropagation();
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text');
    alert('Converting JSON: \n\n ' + pastedData);
    window.removeEventListener('paste', handlePaste);
    convertPastedJSON(pastedData);
  };

  const convertPastedJSON = (pastedData) => {
    try {
      var json = JSON.parse(pastedData);
      const newInputs = buildFromJSON(json, 2);
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
      //If the value for the key is a child object
      if (typeof json[keys[i]] === 'object') {
        newInput['typeof'] = 'Parent';
        newInput['input'] = '';
        newInput['hasChildren'] = true;
        // Construct the id of the correct pattern
        let tempId = id + '.' + childId;
        childId += 1;
        let type;
        if (id.length > 1) {
          //Used to get the correct value from childMap
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
      id += 1;
      newInputs.push(newInput);
    }
    setId(id);
    return newInputs;
  };

  return (
    <>
      <ImageButton
        className="pasteJSON"
        image={pasteImage}
        //style={{ marginTop: '-2px', marginRight: '3px' }}
        onClick={handleClick}
      >
        {paste ? 'Press CMD + V' : 'Paste JSON'}
      </ImageButton>
    </>
  );
}
