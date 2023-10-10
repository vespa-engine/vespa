# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
require 'rexml/document'

def plot_graph(plot_data, plot_png, title, xlabel, ylabel, graph_titles)
  plot_cmd = "";
  plot_cmd += "set terminal png\n"
  plot_cmd += "set output \"#{plot_png}\"\n"
  plot_cmd += "set title \"#{title}\"\n"
  plot_cmd += "set xlabel \"#{xlabel}\"\n"
  plot_cmd += "set ylabel \"#{ylabel}\"\n"
  c = 2
  plots = []
  plot_cmd += "plot "
  graph_titles.each do |title|
    plots.push("\"#{plot_data}\" using 1:#{c} title \"#{title}\" with linespoints")
    c += 1
  end
  plot_cmd += plots.join(", ")

  plot_cmd_file = File.open("plot_graph.cmd", "w")
  plot_cmd_file.write(plot_cmd);
  plot_cmd_file.close
  cmd = "gnuplot plot_graph.cmd"
  puts cmd
  puts `#{cmd}`
end

def extract_alpha(num_docs, percentages, input, output, xml_getter)
  plot_data = File.open(output, "w");
  num_docs.each do |num|
    data_line = "#{num} "
    percentages.each do |prc|
      unique = num * prc
      filename = input.sub("#N", "#{num}").sub("#V", "#{unique}")
      value = 0
      begin
        xml_root = REXML::Document.new(File.open(filename)).root
        value = send(xml_getter, xml_root)
      rescue REXML::ParseException
        puts "Could not parse file: #{filename}"
      end
      data_line += "#{value} "
    end
    plot_data.write(data_line + "\n")
  end
  plot_data.close
end

def extract_beta(num_docs, percentage, num_threads, input, output, xml_getter)
  plot_data = File.open(output, "w");
  num_docs.each do |num|
    data_line = "#{num} "
    unique = num * percentage
    num_threads.each do |thread|
      filename = input.sub("#N", "#{num}").sub("#V", "#{unique}").sub("#S", "#{thread}")
      value = 0
      begin
        xml_root = REXML::Document.new(File.open(filename)).root
        value = send(xml_getter, xml_root)
      rescue REXML::ParseException
        puts "Could not parse file: #{filename}"
      end
      data_line += "#{value} "
    end
    plot_data.write(data_line + "\n")
  end
  plot_data.close
end

def xml_getter_update_0_throughput(xml_root)
  return xml_root.elements["update[@id='0']"].elements["throughput"].text
end

def xml_getter_search_throughput(xml_root)
  return xml_root.elements["total-searcher-summary"].elements["search-throughput"].text
end

def xml_getter_updater_thread_throughput(xml_root)
  return throughput = xml_root.elements["updater-summary"].elements["throughput"].text
end


vectors = ["mv-num-new"]#, "mv-num-new", "sv-string-new", "mv-string-new"]#, "sv-num-old", "mv-num-old", "sv-string-old", "mv-string-old"]
num_docs = [500000, 1000000, 2000000, 4000000, 8000000, 16000000]
unique_percentages = [0.001, 0.01, 0.05, 0.20, 0.50]
num_threads = [1, 2, 4, 8, 16]

inputs = ["03-27-full/#AV-n#N-v#V-p2-r1-s1-q1000.log",
          "03-27-full/#AV-n#N-v#V-s#S-q100-b.log"]
graph_titles = [[], []]
unique_percentages.each do |percentage|
  graph_titles[0].push("#{percentage * 100} % uniques")
end
num_threads.each do |thread|
  graph_titles[1].push("#{thread} searcher thread(s)")
end

vectors.each do |vector|
  extract_alpha(num_docs, unique_percentages,
                inputs[0].sub("#AV", vector),
                "#{vector}-update-speed.dat",
                :xml_getter_update_0_throughput)
  plot_graph("#{vector}-update-speed.dat",
             "#{vector}-update-speed.png",
             "Update speed when applying 1M updates",
             "Number of documents", "Updates per/sec", graph_titles[0])

  extract_alpha(num_docs, unique_percentages,
                inputs[0].sub("#AV", vector),
                "#{vector}-search-speed.dat",
                :xml_getter_search_throughput)
  plot_graph("#{vector}-search-speed.dat",
             "#{vector}-search-speed.png",
             "Search speed with 1 searcher thread",
             "Number of documents", "Queries per/sec", graph_titles[0])

  extract_beta(num_docs, 0.01, num_threads,
               inputs[1].sub("#AV", vector),
               "#{vector}-search-speed-multiple.dat",
               :xml_getter_search_throughput)
  plot_graph("#{vector}-search-speed-multiple.dat",
             "#{vector}-search-speed-multiple.png",
             "Search speed with 1 update thread and X searcher threads",
             "Number of documents", "Queries per/sec", graph_titles[1])

  extract_beta(num_docs, 0.01, num_threads,
               inputs[1].sub("#AV", vector),
               "#{vector}-update-speed-multiple.dat",
               :xml_getter_updater_thread_throughput)
  plot_graph("#{vector}-update-speed-multiple.dat",
             "#{vector}-update-speed-multiple.png",
             "Update speed with 1 update thread and X searcher threads",
             "Number of documents", "Updates per/sec", graph_titles[1])
end
