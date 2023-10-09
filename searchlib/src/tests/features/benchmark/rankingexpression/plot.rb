# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
require '../plotlib'

folder = ARGV[0]
extra = ""
extra = ARGV[1] if ARGV.size == 2
trees = [1, 5, 10, 50, 100, 200, 400, 800]

dat = folder + "/plot.dat"
png = folder + "/plot.png"

file = File.open(dat, "w")
trees.each do |t|
  file.write("#{t} ")
  file.write(extract_data(folder + "/c-#{t}.out") + " ")
  file.write("\n")
end
file.close

titles = ["expression"]

plot_graph(dat, titles, png, "rankingExpression feature (#{extra})", "number of trees", "execution time per document (ms)", folder)
