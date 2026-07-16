# Heller

Heller is a program to generate even loopier zip quines. Loopy zip quines were
first created by Ruben Van Mello and Pieter Audenaert in their paper "[A
Generator for Recursive Zip Files](https://doi.org/10.3390/app14219797)"
([generator](https://github.com/ruvmello/zip-quine-generator)). A loopy zip
quine is a zip file which contains another zip file which contains the first zip
file. Van Mello and Audenaert were only able to create quines of order two, this
program can generate zip quines of any order using a more generalized method.

## Usage:

    heller +f1 +f2 +f3 @1.zip -f1 @2.zip -f2 @3.zip -f3
