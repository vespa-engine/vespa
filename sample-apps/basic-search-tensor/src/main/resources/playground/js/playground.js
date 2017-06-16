// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

contexts = {
    VIEW: "view",
    EDIT: "edit",
    SETUP: "setup"
};

var show_setup = false;
var setup = { "frames": [] };
var results = { "frames": [] };
var variables = d3.map();
var selected = null;
var converter = new showdown.Converter();
var context = contexts.VIEW;

///////////////////////////////////////////////////////////////////////////////
// Operations and UI
///////////////////////////////////////////////////////////////////////////////

var operations = {

    ////////////////////////////////////////////////////////////
    "comment" : {
        "params" : {
            "text" : ""
        },
        "setup_ui" : function(param, element, header, content) {
            clear(header);
            header.append("div").attr("class", "block").html("Edit comment");
            header.append("div").attr("class", "block right").html("[<b>ctrl + enter</b>]: save and execute, [<b>escape</b>]: cancel");

            clear(content);
            content.append("textarea")
                .attr("rows", "3")
                .attr("cols", "100")
                .text(param["text"])
                .node().select();
        },
        "execute" : function(param, result, next) {
            var html = converter.makeHtml(param["text"]);
            result.set("text", html);
            next();
        },
        "result_ui" : function(result, element, header, content) {
            header.html("Comment");
            clear(content);
            content.append("b").html(result.get("text"));
        },
        "save" : function(param, element) {
            if (element.select("textarea").node() != null) {
                param["text"] = element.select("textarea").property("value").trim();
            }
        },
    },

    ////////////////////////////////////////////////////////////
    "variable" : {
        "params" : {
            "name" : "",
            "type" : "",
            "value" : ""
        },
        "setup_ui" : function(param, element, header, content) {
            clear(header);
            header.append("div").attr("class", "block").html("Edit variable");
            header.append("div").attr("class", "block right").html("[<b>ctrl + enter</b>]: save and execute, [<b>escape</b>]: cancel");

            clear(content);
            add_table(content);
            add_input_field(content, "Name", "variable_name", param["name"], true);
            add_input_field(content, "Type", "variable_type", param["type"], false, "Valid types: <b>double</b> or <b>tensor(...)</b>");
            add_textarea_field(content, "Value", "variable_value", param["value"], false, "For tensors use literal form: <b>{{x:0}:1,{x:1}:2}</b>");
        },

        "execute" : function(param, result, next) {
            variables.set(param["name"], {
                "name" : param["name"],
                "type" : param["type"],
                "value": param["value"]
            });
            if (param["type"].startsWith("tensor")) {
                expression = {
                    "expression" : param["name"],
                    "arguments" : [ variables.get(param["name"]) ]
                };
                d3.json("/playground/eval?json=" + encodeURIComponent(JSON.stringify(expression)),
                    function(response) {
                        if (!has_error(response, result)) {
                            result.set("name", param["name"]);
                            result.set("type", response["type"]);
                            result.set("data", response)
                        }
                        next();
                    });
            } else {
                result.set("name", param["name"]);
                result.set("type", param["type"]);
                result.set("data", variables.get(param["name"]));
                next();
            }
        },
        "result_ui" : function(result, element, header, content) {
            clear(content);
            if (result.empty()) {
                header.html("Variable");
                content.html("Not executed yet...");
                return;
            }

            if (result.has("error")) {
                clear(content);
                content.append("div").attr("class", "error").html(result.get("error"));
            } else {
                var data = result.get("data");
                if (typeof data === "object") {
                    drawtable(content, data)
                } else {
                    content.html(data);
                }
            }

            clear(header);
            header.append("div").attr("class", "block").html("Variable <b>" + result.get("name") + "</b>");
            header.append("div").attr("class", "block right").html("Type: <b>" + result.get("type") + "</b>");
        },

        "save" : function(param, element) {
            if (element.select(".variable_name").node() != null) {
                param["name"] = get_input_field_value(element, "variable_name");
                param["type"] = get_input_field_value(element, "variable_type");
                param["value"] = get_textarea_field_value(element, "variable_value");
            }
        },
    },

    ////////////////////////////////////////////////////////////
    "expression" : {
        "params" : {
            "name" : "",
            "expression" : ""
        },
        "setup_ui" : function(param, element, header, content) {
            clear(header);
            header.append("div").attr("class", "block")
                .html("Edit expression");
            header.append("div").attr("class", "block right")
                .html("[<b>ctrl + enter</b>]: save and execute, [<b>escape</b>]: cancel");

            clear(content);
            add_table(content);
            add_input_field(content, "Name", "expression_name", param["name"], true, "Only required if using in other expressions");
            add_textarea_field(content, "Expression", "expression_expression", param["expression"]);
        },
        "execute" : function(param, result, next) {
            expression = {
                "expression" : param["expression"],
                "arguments" : []
            };
            variables.forEach(function (name, value, map) {
                if (expression["expression"].includes(name)) {
                    expression["arguments"].push(value);
                }
            });
            d3.json("/playground/eval?json=" + encodeURIComponent(JSON.stringify(expression)),
                function(response) {
                    if (!has_error(response, result)) {
                        if (param["name"].length > 0) {
                            variables.set(param["name"], {
                                "name" : param["name"],
                                "type" : response["type"],
                                "value": response["type"].includes("tensor") ? response["value"]["literal"] : response["value"]
                            });
                        }
                        result.set("result", response)
                        result.set("name", param["name"])
                        result.set("type", response["type"])
                        result.set("expression", param["expression"])
                    }
                    next();
                });
            result.set("result", "Executing...");
            result.set("name", param["name"])
            result.set("expression", param["expression"])
        },
        "result_ui" : function(result, element, header, content) {
            header.html("Expression");
            clear(content);
            if (result.empty()) {
                content.html("Not executed yet...");
                return;
            }

            var headerLeft = "Expression";
            if (result.has("name") && result.get("name").length > 0) {
                headerLeft += " <b>" + result.get("name") + "</b>";
            }
            headerLeft += ": <b>" + result.get("expression") + "</b>";

            var headerRight = "";
            if (result.has("type") && result.get("type").length > 0) {
                headerRight += " Type: <b>" + result.get("type") + "</b>";
            }

            clear(header);
            header.append("div").attr("class", "block").html(headerLeft);
            header.append("div").attr("class", "block right").html(headerRight);

            if (result.has("error")) {
                content.html("");
                content.append("div").attr("class", "error").html(result.get("error"));
            } else {
                var data = result.get("result");
                if (typeof data === "object") {
                    drawtable(content, data)
                } else {
                    content.html(data);
                }
            }

        },
        "save" : function(param, element) {
            if (element.select(".expression_name").node() != null) {
                param["name"] = get_input_field_value(element, "expression_name");
                param["expression"] = get_textarea_field_value(element, "expression_expression");
            }
        },
    }

}

function has_error(response, result) {
    result.remove("error");
    if (response == null) {
        result.set("error", "Did not receive a response.")
        return true;
    } else if ("error" in response) {
        result.set("error", response["error"])
        return true;
    }
    return false;
}

function clear(root) {
    root.html("");
}


function add_table(root) {
    root.append("table");
}


function add_label_field(root, label) {
    root = root.select("table");
    var field = root.append("tr");
    field.append("td")
        .attr("colspan", "3")
        .attr("class", "header")
        .html(label);
}


function add_input_field(root, label, classname, value, focus, helptext) {
    root = root.select("table");
    var field = root.append("tr");
    field.append("td").html(label);
    field.append("td")
        .attr("class", classname)
        .append("input")
            .attr("value", value)
            .attr("size", "50");
    field.append("td").html("<i>" + (helptext == null ? "" : helptext) + "</i>");
    if (focus) {
        field.select("input").node().select();
    }
}


function add_textarea_field(root, label, classname, value, focus, helptext) {
    root = root.select("table");
    var field = root.append("tr");
    field.append("td").html(label);
    field.append("td")
        .attr("class", classname)
        .append("textarea")
            .attr("rows", "2")
            .attr("cols", "50")
            .text(value);
    field.append("td").html("<i>" + (helptext == null ? "" : helptext) + "</i>");
    if (focus) {
        field.select("textarea").node().select();
    }
}


function add_select_field(root, label, classname, value, options) {
    root = root.select("table");
    var field = root.append("tr");
    field.append("td").html(label);
    var field_select = field.append("td")
        .attr("class", classname)
        .append("select");
    for (var i=0; i < options.length; ++i) {
        var v = options[i][0];
        var t = options[i][1];
        field_select.append("option").attr("value", v).text(t);
    }
    field_select.property("value", value);
}


function get_input_field_value(root, classname) {
    return root.select("." + classname).select("input").property("value").trim();
}


function get_textarea_field_value(root, classname) {
    return root.select("." + classname).select("textarea").property("value").trim();
}


function get_select_field_value(root, classname) {
    return root.select("." + classname).select("select").property("value").trim();
}


function drawtable(element, variable) {
    if (variable === null || typeof variable !== "object") {
        return;
    }

    var type = variable["type"];
    var columns = new Set();
    columns.add("__value__");

    var data = [ { "__value__": variable["value"]} ]
    if (type.includes("tensor")) {
        data = variable["value"]["cells"].map(function(cell) {
            var entry = new Object();
            var address = cell["address"];
            for (var dim in address) {
                entry[dim] = address[dim];
                columns.add(dim);
            }
            entry["__value__"] = cell["value"];
            return entry;
        });
    }

    columns = [...columns]; // sort "value" to back
    columns.sort(function(a, b) {
        var _a = a.toLowerCase(); // ignore upper and lowercase
        var _b = b.toLowerCase(); // ignore upper and lowercase
        if (_a.startsWith("__value__") && !_b.startsWith("__value__")) {
            return 1;
        }
        if (_b.startsWith("__value__") && !_a.startsWith("__value__")) {
            return -1;
        }
        if (_a < _b) {
            return -1;
        }
        if (_a > _b) {
            return 1;
        }
        return 0;
    });

    var table = element.append("table"),
        thead = table.append("thead"),
        tbody = table.append("tbody");

    thead.append("tr")
        .selectAll("th")
        .data(columns)
        .enter()
        .append("th")
        .text(function(column) { return column.startsWith("__value__") ? "value" : column; });

    var rows = tbody.selectAll("tr")
        .data(data)
        .enter()
        .append("tr");

    var cells = rows.selectAll("td")
        .data(function(row) {
            return columns.map(function(column) {
                return {column: column, value: row[column]};
            });
        })
        .enter()
        .append("td")
            .classed("data", true)
            .text(function(d) { return d.value; });

    return table;
}

///////////////////////////////////////////////////////////////////////////////
// Setup handling
///////////////////////////////////////////////////////////////////////////////

function load_setup() {
    if (window.location.hash) {
        var compressed = window.location.hash.substring(1);
        var decompressed = LZString.decompressFromEncodedURIComponent(compressed);
        setup = JSON.parse(decompressed);
        d3.select("#setup-content").text(JSON.stringify(setup, null, 2));
        d3.select("#setup-input").attr("value", compressed);
    }
}

function on_setup_input_change() {
    var compressed = d3.select("#setup-input").property("value").trim();
    var decompressed = LZString.decompressFromEncodedURIComponent(compressed);
    setup = JSON.parse(decompressed);
    d3.select("#setup-content").text(JSON.stringify(setup, null, 2));
    save_setup();
    clear_results();
    execute_frame(0);
    document.activeElement.blur();
}


function apply_setup() {
    var setup_string = d3.select("#setup-content").property("value");
    setup = JSON.parse(setup_string);
    save_setup();
    clear_results();
    execute_frame(0);
    toggle_show_setup();
}


function save_setup() {
    var setup_string = JSON.stringify(setup, null, 2);
    d3.select("#setup-content").text(setup_string);
    var compressed = LZString.compressToEncodedURIComponent(setup_string);
    window.location.hash = compressed;
    d3.select("#setup-input").attr("value", compressed);
}


function save_changes() {
    d3.selectAll(".setup").each(function (d,i) {
        var element = d3.select(this);
        var op = d["op"];
        var param = d["param"];
        operations[op]["save"](param, element);
    });
    save_setup();
}


function clear_results() {
    results["frames"] = [];
    for (var i = 0; i < setup["frames"].length; ++i) {
        results["frames"][i] = d3.map();
    }
}


function clear_all() {
    setup = { "frames": [] };
    save_setup();
    clear_results();
    update();
}


function toggle_show_setup() {
    show_setup = !show_setup;
    d3.select("#setup-container").classed("hidden", !show_setup);
    d3.select("#frames").classed("hidden", show_setup);
    if (show_setup) {
        d3.select("#setup-content").node().focus();
        context = contexts.SETUP;
    } else {
        context = contexts.VIEW;
    }
}


function num_frames() {
    return setup["frames"].length;
}


function swap(frame1, frame2) {
    var setup_frame_1 = setup["frames"][frame1];
    setup["frames"][frame1] = setup["frames"][frame2];
    setup["frames"][frame2] = setup_frame_1;

    var result_frame_1 = results["frames"][frame1];
    results["frames"][frame1] = results["frames"][frame2];
    results["frames"][frame2] = result_frame_1;
}


function remove(frame) {
    setup["frames"].splice(frame, 1);
    results["frames"].splice(frame, 1);
}


///////////////////////////////////////////////////////////////////////////////
// UI handling
///////////////////////////////////////////////////////////////////////////////

function add_new_operation() {
    var new_operation = d3.select("#setup_new_operation");

    var select = new_operation.append("select");
    for (operation in operations) {
        select.append("option").attr("value", operation).text(operation);
    }

    new_operation.append("button")
        .text("Add")
        .on("click", function() {
            var operation = select.property("value");
            new_frame(operation);
        });
}

function new_frame(operation) {
    if (operation == null) {
        operation = d3.select("#setup_new_operation").select("select").node().value;
    }
    if (operation == null) {
        operation = "comment";
    }

    var default_params = JSON.stringify(operations[operation]["params"]);
    setup["frames"].push({
        "op" : operation,
        "param" : JSON.parse(default_params)
    });
    results["frames"].push(d3.map());

    var insert_as_index = find_selected_frame_index() + 1;
    var current_index = num_frames() - 1;
    if (current_index > 0) {
        while (current_index > insert_as_index) {
            swap(current_index, current_index - 1);
            current_index -= 1;
        }
    }

    save_setup();
    update();
    select_frame_by_index(insert_as_index);
    document.activeElement.blur();

    edit_selected();

    d3.select("#setup_new_operation").select("select").property("value", operation);
}


function update() {
    var all_data = d3.zip(setup["frames"], results["frames"]);

    var rows = d3.select("#frames").selectAll(".frame").data(all_data);
    rows.exit().remove();
    var frames = rows.enter()
        .append("div")
            .on("click", function() { select_frame(this); })
            .on("dblclick", function(e) {
                select_frame(this);
                edit_selected();
             })
            .attr("class", "frame");
    frames.append("div").attr("class", "frame-header").html("header");
    frames.append("div").attr("class", "frame-content").html("content");

     rows.each(function (d, i) {
        var element = d3.select(this);
        var op = d[0]["op"];
        var param = d[0]["param"];
        var result = d[1];

        var header = element.select(".frame-header");
        var content = element.select(".frame-content");

        operations[op]["result_ui"](result, element, header, content);
     });
}


function remove_selected() {
    var frame = find_selected_frame_index();
    remove(frame);
    save_setup();
    update();
    select_frame_by_index(frame);
}


function move_selected_up() {
    var frame = find_selected_frame_index();
    if (frame == 0) {
        return;
    }
    swap(frame, frame-1);
    save_setup();
    update();
    select_frame_by_index(frame-1);
}


function move_selected_down() {
    var frame = find_selected_frame_index();
    if (frame == setup["frames"].length - 1) {
        return;
    }
    swap(frame, frame+1);
    save_setup();
    update();
    select_frame_by_index(frame+1);
}


function execute_selected() {
    var frame = d3.select(selected);
    var data = frame.data();
    var setup = data[0][0]; // because of zip in update
    var op = setup["op"];
    var param = setup["param"];

    operations[op]["save"](param, frame);
    save_setup();

    execute_frame(find_selected_frame_index());
    exit_edit_selected();
}


function execute_frame(i) {
    if (i < 0) {
        return;
    }
    if (i >= setup["frames"].length) {
        update();
        return;
    }
    var op = setup["frames"][i]["op"];
    var params = setup["frames"][i]["param"];
    var result = results["frames"][i];
    operations[op]["execute"](params, result, function(){ execute_frame(i+1); });
}


function find_selected_frame_index() {
    var result = -1;
    d3.select("#frames").selectAll(".frame")
        .each(function (d, i) {
            if (this.classList.contains("selected")) {
                result = i;
            }
        });
    return result;
}


function find_frame_index(frame) {
    var result = null;
    d3.select("#frames").selectAll(".frame")
        .each(function (d, i) {
            if (this == frame) {
                result = i;
            }
        });
    return result;
}


function is_element_entirely_visible(el) {
    var rect = el.getBoundingClientRect();
    var height = window.innerHeight || doc.documentElement.clientHeight;
    return !(rect.top < 100 || rect.bottom > height);
}


function select_frame(frame) {
    if (selected == frame) {
        return;
    }
    if (context === contexts.EDIT) {
        exit_edit_selected();
    }
    if (selected != null) {
        selected.classList.remove("selected");
    }
    selected = frame;
    selected.classList.add("selected");
    if (!is_element_entirely_visible(selected)) {
        selected.scrollIntoView();
        document.body.scrollTop -= 110;
    }

    selected_frame_index = find_selected_frame_index();
    d3.select("#up-button").attr("disabled", function() { return selected_frame_index == 0 ? "true" : null});
    d3.select("#down-button").attr("disabled", function() { return selected_frame_index == num_frames() - 1 ? "true" : null});
    d3.select("#frame-label").html("#" + (selected_frame_index+1));
}


function select_frame_by_index(i) {
    if (i >= num_frames()) {
        i = num_frames() - 1;
    }
    if (i < 0) {
        i = 0;
    }
    d3.select("#frames").selectAll(".frame")
        .each(function (datum, index) {
            if (i == index) {
                select_frame(this);
            }
        });
}


function edit_selected() {
    if (context === contexts.EDIT) {
        exit_edit_selected();
        return;
    }
    var frame = d3.select(selected);
    var data = frame.data();
    var setup = data[0][0]; // because of zip in update
    var result = data[0][1];

    var op = setup["op"];
    var param = setup["param"];

    var header = frame.select(".frame-header");
    var content = frame.select(".frame-content");

    operations[op]["setup_ui"](param, frame, header, content);

    d3.select("#up-button").attr("disabled", "true");
    d3.select("#down-button").attr("disabled", "true");
    d3.select("#remove-button").attr("disabled", "true");
    d3.select("#edit-button").text("Cancel");

    context = contexts.EDIT;
}


function exit_edit_selected() {
    if (context !== contexts.EDIT) {
        return;
    }

    var frame = d3.select(selected);
    var data = frame.data();
    var setup = data[0][0]; // because of zip in update
    var result = data[0][1];

    var op = setup["op"];
    var param = setup["param"];

    var header = frame.select(".frame-header");
    var content = frame.select(".frame-content");

    operations[op]["result_ui"](result, frame, header, content);

    d3.select("#edit-button").attr("disabled", null);
    d3.select("#up-button").attr("disabled", null);
    d3.select("#down-button").attr("disabled", null);
    d3.select("#remove-button").attr("disabled", null);

    d3.select("#edit-button").text("Edit");

    context = contexts.VIEW;
}


function event_in_input() {
    var tag_name = d3.select(d3.event.target).node().tagName;
    return (tag_name == 'INPUT' || tag_name == 'SELECT' || tag_name == 'TEXTAREA' || tag_name == 'BUTTON');
}

function event_in_frame() {
    var node = d3.select(d3.event.target).node();
    while (node != null) {
        if (d3.select(node).attr("class") != null) {
            if (d3.select(node).attr("class").includes("frame")) {
                return true
            }
        }
        node = node.parentElement;
    }
    return false;
}




function setup_keybinds() {

    var previous_keydown = { "key" : null, "ts" : 0 };

    key_binds = {}
    key_binds[contexts.VIEW] = {}
    key_binds[contexts.EDIT] = {}
    key_binds[contexts.SETUP] = {}

    key_binds[contexts.VIEW]["up"] =
    key_binds[contexts.VIEW]["k"]  = function() { select_frame_by_index(find_selected_frame_index() - 1); };
    key_binds[contexts.VIEW]["down"] =
    key_binds[contexts.VIEW]["j"]    = function() { select_frame_by_index(find_selected_frame_index() + 1); };

    key_binds[contexts.VIEW]["shift + up"] =
    key_binds[contexts.VIEW]["shift + k"]  = function() { move_selected_up(); };
    key_binds[contexts.VIEW]["shift + down"] =
    key_binds[contexts.VIEW]["shift + j"]  = function() { move_selected_down(); };

    key_binds[contexts.VIEW]["backspace"] =
    key_binds[contexts.VIEW]["x"]   = function() { remove_selected(); };

    key_binds[contexts.VIEW]["enter"]   = function() { edit_selected(); };
    key_binds[contexts.VIEW]["ctrl + enter"]   = function() { execute_selected(); };

    key_binds[contexts.VIEW]["n,c"] = function() { new_frame("comment"); };
    key_binds[contexts.VIEW]["n,v"] = function() { new_frame("variable"); };
    key_binds[contexts.VIEW]["n,e"] = function() { new_frame("expression"); };
    key_binds[contexts.VIEW]["a"]   = function() { new_frame(null); };

    key_binds[contexts.VIEW]["esc"] = function() { document.activeElement.blur(); };
    key_binds[contexts.SETUP]["esc"] = function() { document.activeElement.blur(); };

    key_binds[contexts.EDIT]["esc"] = function() { document.activeElement.blur(); exit_edit_selected(); };
    key_binds[contexts.EDIT]["ctrl + enter"] = function() { execute_selected(); };


    d3.select('body').on('keydown', function() {
        var combo = [];

        if (d3.event.shiftKey) combo.push("shift");
        if (d3.event.ctrlKey) combo.push("ctrl");
        if (d3.event.altKey) combo.push("alt");
        if (d3.event.metaKey) combo.push("meta");

        var key_code = d3.event.keyCode;

        if (key_code == 8) combo.push("backspace");
        if (key_code == 13) combo.push("enter");
        if (key_code == 27) combo.push("esc");
        if (key_code == 32) combo.push("space");
        if (key_code == 46) combo.push("del");

        if (key_code == 37) combo.push("left");
        if (key_code == 38) combo.push("up");
        if (key_code == 39) combo.push("right");
        if (key_code == 40) combo.push("down");

        // a-z
        if (key_code >= 64 && key_code < 91) combo.push(String.fromCharCode(key_code).toLowerCase());

        var key = combo.join(" + ");
        if (event_in_input() && !event_in_frame() && key !== "esc") {
            return;
        }

        // Check if combo combined with previous key is bound
        if (Date.now() - previous_keydown["ts"] < 1000) {
            var two_key = previous_keydown["key"] + "," + key;
            if (two_key in key_binds[context]) {
                key_binds[context][two_key]();
                d3.event.preventDefault();
                return;
            }
        }

        if (key in key_binds[context]) {
            key_binds[context][key]();
            d3.event.preventDefault();
        }

        previous_keydown = { "key":key, "ts": Date.now() };
    });
}


function main() {
    load_setup();
    add_new_operation();
    clear_results();
    execute_frame(0);
    update();
    select_frame_by_index(0);
    setup_keybinds();
}

