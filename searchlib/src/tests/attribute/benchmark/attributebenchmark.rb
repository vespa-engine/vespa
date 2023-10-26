# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vectors = ["sv-num-new", "mv-num-new", "sv-string-new", "mv-string-new"]#, "sv-num-old", "mv-num-old", "sv-string-old", "mv-string-old"]
num_docs = [500000, 1000000, 2000000, 4000000, 8000000, 16000000]
unique_percent = [0.001, 0.01, 0.05, 0.20, 0.50]

vectors.each do |vector|
  num_docs.each do |num|
    unique_percent.each do |percent|
      unique = num * percent
      command = "./attributebenchmark -n #{num} -u 1000000 -v #{unique} -p 2 -r 1 -s 1 -q 1000 #{vector} > 03-27-full/#{vector}-n#{num}-v#{unique}-p2-r1-s1-q1000.log 2>&1"
      puts command
      `#{command}`
      s = 1
      5.times do
        command = "./attributebenchmark -n #{num} -v #{unique} -p 1 -r 0 -s #{s} -q 100 -b #{vector} > 03-27-full/#{vector}-n#{num}-v#{unique}-s#{s}-q100-b.log 2>&1"
        puts command
        `#{command}`
        s = s*2;
      end
    end
  end
end
