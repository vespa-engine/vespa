import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useCallback, useContext, useState } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownForm({
  choices,
  id,
  child = false,
  initial,
}) {
  const {
    inputs,
    setInputs,
    levelZeroParameters,
    childMap,
    selectedItems,
    setSelectedItems,
  } = useContext(QueryInputContext);
  const [choice, setChoice] = useState();

  /**
   * Update the state of inputs to reflect the method chosen from the dropdown.
   * If the prevoiusly chosen method had children they are removed.
   * @param {Event} e Event containing the new type.
   */
  const updateType = (e) => {
    e.preventDefault();
    const newType = e.target.value;
    const newInputs = inputs.slice();
    let currentId = id.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId);
    if (child) {
      let parentTypes = newInputs[index].type;
      let children = newInputs[index].children;
      let childChoices = childMap[parentTypes];
      for (let i = 3; i < id.length; i += 2) {
        currentId = id.substring(0, i);
        index = children.findIndex((element) => element.id === currentId);
        let child = children[index];
        parentTypes = parentTypes + '_' + child.type;
        childChoices = childMap[parentTypes];
        children = child.children;
      }
      index = children.findIndex((element) => element.id === id);
      children[index].type = newType;
      children[index].hasChildren = childChoices[newType].hasChildren;
      children[index].children = [];
      children[index].typeof = childChoices[newType].type;
      setSelectedItems([...selectedItems, newType]);
    } else {
      newInputs[index].type = newType;
      let hasChildren = levelZeroParameters[newType].hasChildren;
      newInputs[index].hasChildren = hasChildren;
      newInputs[index].children = [];
      newInputs[index].typeof = levelZeroParameters[newType].type;
      setSelectedItems([...selectedItems, newType]);
    }
    setInputs(newInputs);
    setChoice(newType);
  };

  //TODO: do not display options that have been chosen

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateType}
      choices={choices}
      value={choice}
      initial={initial}
    ></SimpleDropDownForm>
  );
}
