# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
require '../plotlib'

if ARGV.size == 0
  puts "must specify folder"
  exit
end

folder = ARGV[0]
extra = ""
extra = ARGV[1] if ARGV.size == 2
field = [20, 100, 1000, 10000]
segmentation = [1, 5, 10, 50, 100, 500, 1000, 10000]

dat = folder + "/plot.dat"
png = folder + "/plot.png"

file = File.open(dat, "w")
segmentation.each do |s|
  file.write("#{s} ")
  field.each do |f|
    file.write(extract_data(folder + "/c-#{f}-#{s}.out") + " ")
  end
  file.write("\n")
end
file.close

titles = ["fl-20", "fl-100", "fl-1000", "fl-10000"]

plot_graph(dat, titles, png, "fieldMatch feature (#{extra})", "maxAlternativeSegmentations", "execution time per document (ms)", folder)
