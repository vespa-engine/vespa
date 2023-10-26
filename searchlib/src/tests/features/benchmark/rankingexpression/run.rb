# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if ARGV.size == 0
  puts "must specify folder"
  exit
end

folder = ARGV[0]
trees = [1, 5, 10, 50, 100, 200, 400, 800]
trees.each do |t|
  file = "c-#{t}"
  cmd = "script -c \"../../featurebenchmark -c #{file}.txt\" " + folder + "/#{file}.out"
  puts cmd
    `#{cmd}`
end
