** Running the benchmark **
ruby run.rb folder
folder is the place to store the output files.


** Generating gnu plots **
ruby plot.rb folder "description"
folder contains the output files and description are used when setting the title of the graph.


** Config file format **
c-x-y.txt
x is the length of the field and y is the value for maxAlternativeSegmentations.


** Running callgrind **
valgrind --tool=callgrind ../../featurebenchmark -c c-1000-1-callgrind.txt 
valgrind --tool=callgrind ../../featurebenchmark -c c-1000-100-callgrind.txt 
The numruns config value is reduced in these two config files.

The output after running callgrind is two files: callgrind.out.x and callgrind.out.y.
Use kcachegrind to look at these two files.
