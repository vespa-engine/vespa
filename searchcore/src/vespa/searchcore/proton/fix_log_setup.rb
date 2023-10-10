# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
def log_setup_name(fname)
  m = fname.match("(.*)\/(.*)\.cpp")
  return ".proton.#{m[1]}.#{m[2]}"
end

def fix_log_setup(fname)
  nname = "#{fname}.new"
  puts "fix '#{fname}': #{log_setup_name(fname)}"
  nfile = File.open(nname, "w")
  File.open(fname, "r").each_line do |line|
    if (line.match("LOG_SETUP"))
      nfile.write("LOG_SETUP(\"#{log_setup_name(fname)}\");\n")
    else
      nfile.write(line)
    end
  end
  nfile.close
  File.rename(nname, fname)
end

Dir.glob("*/*.cpp").each do |file|
  fix_log_setup(file)
end
