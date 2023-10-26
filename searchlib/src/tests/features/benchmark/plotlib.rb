# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
def plot_graph(dat, titles, png, title, xlabel, ylabel, folder)
  plot_cmd = "";
  plot_cmd += "set terminal png\n"
  plot_cmd += "set output \"#{png}\"\n"
  plot_cmd += "set title \"#{title}\"\n"
  plot_cmd += "set xlabel \"#{xlabel}\"\n"
  plot_cmd += "set ylabel \"#{ylabel}\"\n"
  plot_cmd += "set logscale\n"

  plots = []
  c = 2
  titles.each do |title|
    plots.push("\"#{dat}\" using 1:#{c} title \"#{title}\" with linespoints")
    c += 1
  end
  plot_cmd += "plot "
  plot_cmd += plots.join(", ")

  plot_cmd_file = File.open(folder + "/plot.cmd", "w")
  plot_cmd_file.write(plot_cmd);
  plot_cmd_file.close
  cmd = "gnuplot " + folder + "/plot.cmd"
  puts cmd
  puts `#{cmd}`
end

def extract_data(file_name)
    content = IO.readlines(file_name).join
    r = /ETPD:\s*(\d+\.\d+)/
    if content =~ r
      return $1
    end
    return "0"
end

