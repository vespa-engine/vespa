# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
attribute = ARGV[0]

dat = File.open(attribute + ".dat", "r")
puts "opened " + attribute + ".dat"
dat_buffer = []
dat.each_byte do |byte|
  dat_buffer.push(byte)
end

string = []
strings = []
dat_buffer.each do |byte|
  if byte == 0
    strings.push(string.pack("c*"))
    string.clear
  else
    string.push(byte)
  end
end
puts "num strings: #{strings.size}"

idx = File.open(attribute + ".idx", "r")
puts "opened " + attribute + ".idx"
idx_buffer = []
while not idx.eof
  idx_buffer.push((idx.read(4).unpack("I")).first)
end
puts "num docs: #{idx_buffer.size - 1}"
puts "num values: #{idx_buffer.last}"

out = File.open(attribute + ".out", "w")
for i in 0...(idx_buffer.size - 1)
  count = idx_buffer[i + 1]. - idx_buffer[i]
  out.write("doc #{i}: count = #{count}\n")
  for j in 0...count
    if idx_buffer[i] + j >= strings.size
      raise "ERROR: idx_buffer[i] + j (#{idx_buffer[i] + j}) >= strings.size (#{strings.size})"
    end
    out.write("    #{j}: #{strings[idx_buffer[i] + j]}\n")
  end
end

