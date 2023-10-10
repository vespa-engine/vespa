# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if ARGV.size == 0
  puts "must specify folder"
  exit
end

folder = ARGV[0]
cases = [20, 100, 1000, 10000]
segmentations = [1, 5, 10, 50, 100, 500, 1000, 10000]
cases.each do |c|
  segmentations.each do |s|
    file = "c-#{c}-#{s}"
    cmd = "script -c \"../../featurebenchmark -c #{file}.txt\" " + folder + "/#{file}.out"
    puts cmd
    `#{cmd}`
  end
end
