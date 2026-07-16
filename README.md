# Heller

**NOTE**: This project is very much unfinished. I've put in a substantial amount
of work into it already though, and I'd rather not have the only existing copy
be on my laptop.

Heller is a program to generate even loopier zip quines. Loopy zip quines were
first created by Ruben Van Mello and Pieter Audenaert in their paper "[A
Generator for Recursive Zip Files](https://doi.org/10.3390/app14219797)"
([generator](https://github.com/ruvmello/zip-quine-generator)). A loopy zip
quine is a zip file which contains another zip file which contains the first zip
file. Van Mello and Audenaert were only able to create quines of order two, this
program can generate zip quines of any order using a more generalized method.

In its current state the program is unusable, but I was able to get a prototype
.zip file, albeit with a bad CRC.
