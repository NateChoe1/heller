package dev.natechoe.heller;

import java.util.List;
import java.util.ArrayList;
import java.util.zip.Deflater;

import dev.natechoe.crc32.CRC32Engine;

public class Zip {
    public static class ZipEntry {
        String filename;
        Bytes content;
        boolean isPlaintext;
        byte[] crc;
        int offset;
        int compressedSize;
        int modtime;
        int moddate;
        byte[] rawFileName;
        ZipEntry(String filename, Bytes content, boolean isPlaintext) {
            this.filename = filename;
            this.content = content;
            this.isPlaintext = isPlaintext;
        }
    }

    public static class QuineLayer {
        String filename;
        List<Integer> members;

        QuineLayer(String filename, List<Integer> members) {
            this.filename = filename;
            this.members = members;
        }
    }

    private static byte[] intToBytes(int v, int l) {
        byte[] ret = new byte[l];
        for (int i = 0; i < l; ++i) {
            ret[i] = (byte) (v & 0xff);
            v >>>= 8;
        }
        return ret;
    }

    private static final int LH_BASE = 30; /* local header base size */
    private static final int CH_BASE = 46; /* central header base size */

    private static Bytes createLocalHeaders(ZipEntry[] files) {
        Bytes ret = new Bytes();

        for (int i = 0; i < files.length; ++i) {
            /* local file header */
            files[i].offset = ret.size();
            ret.append(new Bytes(new byte[] {
                (byte) 0x50,
                (byte) 0x4b,
                (byte) 0x03,
                (byte) 0x04,
            }));

            /* version needed to extract (minimum) */
            ret.append((byte) 20);   /* version 2.0 for deflate */
            ret.append((byte) 0);    /* 0 for msdos compatibility */

            /* general purpose flags, max compression */
            ret.append(new byte[] { 2, 0 });

            /* compression method, deflate */
            ret.append(new byte[] { 8, 0 });

            /* TODO: maybe set these to something meaningful */
            ret.append(new byte[] { 0, 0 });  /* last mod time */
            ret.append(new byte[] { 0, 0 });  /* last mod date */
            files[i].modtime = 0;
            files[i].moddate = 0;

            /* crc-32 */
            byte[] content = files[i].content.toArray(null);
            files[i].crc = CRC32Engine.calculateCRC(content);
            ret.append(files[i].crc);

            /* compressed size */
            Deflater compressor = new Deflater(Deflater.BEST_COMPRESSION, true);
            compressor.setInput(content);
            compressor.finish();

            Bytes compressedData = new Bytes();
            byte[] buffer = new byte[1024];
            for (;;) {
                int rlen = compressor.deflate(buffer);
                if (rlen == 0) {
                    break;
                }
                for (int b = 0; b < rlen; ++b) {
                    compressedData.append(buffer[b]);
                }
            }
            compressor.end();
            files[i].compressedSize = compressedData.size();
            ret.append(intToBytes(compressedData.size(), 4));

            /* uncompressed size */
            ret.append(intToBytes(content.length, 4));

            /* file name length */
            files[i].rawFileName = files[i].filename.getBytes();
            ret.append(intToBytes(files[i].rawFileName.length, 2));

            /* extra field length (0) */
            ret.append(new byte[] { 0, 0 });

            /* file name */
            ret.append(files[i].rawFileName);

            /* file data */
            ret.append(compressedData);
        }

        return ret;
    }

    /* nc<3 */
    private final static byte[] crc = new byte[] { 0x6e, 0x63, 0x3c, 0x33 };

    /* how long is the repeat block before each cd */
    private final static int cdRepeatLen = 20;

    private static enum SegmentType {
        LITERAL,
        LH,
        CD
    }

    private static class Segment {
        SegmentType type;
        Bytes data;
        int offset;

        Segment(SegmentType type, Bytes data, int offset) {
            this.type = type;
            this.data = data;
            this.offset = offset;
        }
    }

    private static void padUniformly(Bytes[] payloads, List<List<Integer>> offsets) {
        int l = 0;
        for (Bytes b: payloads) {
            l = Math.max(l, b.size());
        }

        for (int i = 0; i < payloads.length; ++i) {
            int padding = l - payloads[i].size();
            Bytes padded = new Bytes();
            for (int j = 0; j < padding; ++j) {
                padded.append((byte) 0);
            }
            padded.append(payloads[i]);
            payloads[i] = padded;

            for (int j = 0; j < offsets.get(i).size(); ++j) {
                offsets.get(i).set(j, offsets.get(i).get(j) + padding);
            }
        }
    }

    /* to make things easier, every loopy quine layer has the same crc checksum
     * and the same length. it's definitely possible to remove these
     * constraints, but it's unnecessary and makes things more complicated than
     * i'd like.
     *
     * simplified, zip files look like this:
     *   local file header 1
     *   file data 1
     *   local file header 2
     *   file data 2
     *   local file header 3
     *   file data 3
     *   ...
     *   local file header n
     *   file data n
     *   central directory record 1
     *   central directory record 2
     *   ...
     *   central directory record n
     *   end of central directory record
     *
     * zip files are entirely identified by their end of central directory
     * record. this means that local file headers and file data can be spread
     * throughout the zip archive, potentially including empty space between
     * them or even overlapping.
     *
     * in each loopy zip quine there are two classes of files: the zip quine
     * itself and the auxiliary files we include with it. every layer includes
     * every auxiliary file, and we select which files to include/exclude in
     * each layer through the central directory. the zip quine itself is always
     * the last zip file in the archive.
     *
     * define the "header" to be the common data between every zip layer. this
     * includes the first n-1 local file headers and file data, as well as 4k
     * extra bytes to fix crcs (where k is the number of layers).
     *
     * we want to iterate between the following:
     *
     *   H
     *   LH1
     *   [layer 1 data]
     *   CD1
     *
     * =>
     *
     *   H
     *   LH2
     *   [layer 2 data]
     *   CD2
     *
     * =>
     *
     *   H
     *   LH3
     *   [layer 3 data]
     *   CD3
     *
     * =>
     *
     *   ...
     *
     * =>
     *
     *   H
     *   LHk
     *   [layer k data]
     *   CDk
     *
     * =>
     *
     *   H
     *   LH1
     *   [layer 1 data]
     *   CD1
     *
     * this is done as follows:
     *
     * H
     * LH1
     *   print H
     *   H          -- label: o
     *   print 2
     *   LH2        -- label: l
     *   print H
     *   repeat H, o
     *
     *   print 4
     *   print 2
     *   LH3
     *   print H
     *   repeat H, o
     *
     *   print 5
     *   print 4
     *   print 2
     *   LH4
     *   print H
     *   repeat H, o
     *
     *   ..
     *
     *   print k+1
     *   print k
     *   print k-1
     *   ...
     *   print 4
     *   print 2
     *   LHk
     *   print H
     *   repeat H, o
     *
     *   print k+2
     *   print k+1
     *   ...
     *   print 4
     *   print 2
     *   LH1
     *   print H
     *   repeat H, o
     *
     *   print k
     *     print k+2
     *     print k+1
     *     ...
     *     print 4
     *     print 2
     *   repeat 1, l
     *   print 2
     *     print H
     *     repeat H, o
     *
     *   quine with header
     *     print k
     *     print k+2
     *     print k+1
     *     ...
     *     repeat H, o
     *
     *   -- at this point we have the header and local file header
     *
     *   junk containing
     *     repeat* cdl, cd2   (cd1)
     *     cd1
     *     repeat* cdl, cd3   (cd2)
     *     cd2
     *     repeat* cdl, cd4   (cd3)
     *     cd3
     *     ...
     *     repeat* cdl, cdk   (cdk-1)
     *     cdk-1
     *     repeat* cdl, cd1   (cdk)
     *     cdk
     *   repeat cdl, cd2
     *   cd1
     *
     *   -- this gives us the central directory. each file's central directory
     *   -- is the same size
     *
     * this entire file is composed of a few components:
     *   the header
     *   local file headers
     *   central directories
     *   fixed data (data that doesn't change between decompressions)
     *
     * we construct a list of these components, then combine them into a set of
     * files after calculating the full length.
     * */
    public static Bytes createZip(ZipEntry[] files, QuineLayer[] layers) {
        int k = layers.length;

        Bytes header = createLocalHeaders(files);

        /* offset of the crc fixing bytes within the header */
        int crcBufferStart = header.size();
        for (int i = 0; i < layers.length; ++i) {
            header.append(new byte[] {0, 0, 0, 0});
        }

        Bytes[] localHeaders = new Bytes[k];
        List<List<Integer>> localHeaderLengthOffsets = new ArrayList<>();
        byte[][] quineLayerNames = new byte[k][];

        for (int i = 0; i < k; ++i) {
            quineLayerNames[i] = layers[i].filename.getBytes();
            localHeaderLengthOffsets.add(new ArrayList<>());
        }

        /* generate local file headers */
        for (int i = 0; i < k; ++i) {
            Bytes localHeader = new Bytes();

            /* magic bytes */
            localHeader.append(new byte[] {
                (byte) 0x50,
                (byte) 0x4b,
                (byte) 0x04,
                (byte) 0x03,
            });

            /* version needed to extract */
            localHeader.append(new byte[] {20, 0});

            /* general purpose flags, normal compression */
            localHeader.append(new byte[] { 0, 0 });

            /* compression method, deflate */
            localHeader.append(new byte[] { 8, 0 });

            localHeader.append(new byte[] { 0, 0 });  /* last mod time */
            localHeader.append(new byte[] { 0, 0 });  /* last mod date */

            /* constant crc value */
            localHeader.append(crc);

            /* compressed size. we don't know how long the final file will be so
             * we're just putting in zeros for now and marking that we'll fill
             * in the true size later. */
            localHeaderLengthOffsets.get(i).add(localHeader.size());
            localHeader.append(new byte[] { 0, 0, 0, 0 });

            /* uncompressed size, same as above */
            localHeaderLengthOffsets.get(i).add(localHeader.size());
            localHeader.append(new byte[] { 0, 0, 0, 0 });

            /* file name length */
            localHeader.append(intToBytes(quineLayerNames[(i+1)%k].length, 2));

            /* extra field length */
            localHeader.append(new byte[] {0, 0});

            /* file name */
            localHeader.append(quineLayerNames[(i+1)%k]);

            localHeaders[i] = localHeader;
        }
        padUniformly(localHeaders, localHeaderLengthOffsets);

        int lhSize = localHeaders[0].size();

        Bytes[] centralDirectories = new Bytes[k];
        List<List<Integer>> centralDirectoryLengthOffsets = new ArrayList<>();

        /* generate central directories */
        for (int i = 0; i < k; ++i) {
            centralDirectoryLengthOffsets.add(new ArrayList<>());
            Bytes centralDirectory = new Bytes();

            for (int member: layers[i].members) {
                centralDirectory.append(new byte[] {
                    (byte) 0x50,
                    (byte) 0x4b,
                    (byte) 0x01,
                    (byte) 0x02,
                });

                /* version made with (6.3 on dos) */
                centralDirectory.append(new byte[] {63, 0});

                /* version needed to extract */
                centralDirectory.append(new byte[] {2, 0});

                /* general purpose flags, max compression to match the output of
                 * createLocalHeaders */
                centralDirectory.append(new byte[] {2, 0});

                /* compression method, deflate */
                centralDirectory.append(new byte[] { 8, 0 });

                centralDirectory.append(new byte[] { 0, 0 }); /* mod time */
                centralDirectory.append(new byte[] { 0, 0 }); /* mod date */

                /* crc */
                centralDirectory.append(files[member].crc);

                /* compressed size */
                centralDirectory.append(intToBytes(files[member].compressedSize, 4));

                /* decompressed size */
                centralDirectory.append(intToBytes(files[member].content.size(), 4));

                /* filename length */
                centralDirectory.append(intToBytes(files[member].rawFileName.length, 2));

                /* extra field length */
                centralDirectory.append(new byte[] { 0, 0 });

                /* file comment length */
                centralDirectory.append(new byte[] { 0, 0 });

                /* disk number start */
                centralDirectory.append(new byte[] { 0, 0 });

                /* internal file attributes */
                centralDirectory.append(new byte[] { (byte) (files[member].isPlaintext ? 1:0), 0 });

                /* external file attributes */
                centralDirectory.append(new byte[] { 0, 0, 0, 0 });

                /* relative offset of local header */
                centralDirectory.append(intToBytes(files[member].offset, 4));

                /* file name */
                centralDirectory.append(files[member].rawFileName);
            }

            /* one last record for the quine file */

            centralDirectory.append(new byte[] {
                (byte) 0x50,
                (byte) 0x4b,
                (byte) 0x01,
                (byte) 0x02,
            });

            /* version made with (6.3 on dos) */
            centralDirectory.append(new byte[] {63, 0});

            /* version needed to extract */
            centralDirectory.append(new byte[] {2, 0});

            /* general purpose flags, default compression */
            centralDirectory.append(new byte[] {0, 0});

            /* compression method, deflate */
            centralDirectory.append(new byte[] { 8, 0 });

            centralDirectory.append(new byte[] { 0, 0 }); /* mod time */
            centralDirectory.append(new byte[] { 0, 0 }); /* mod date */

            /* crc */
            centralDirectory.append(crc);

            /* compressed size, we just put in a nonce value for now */
            centralDirectoryLengthOffsets.get(i).add(centralDirectory.size());
            centralDirectory.append(new byte[] { 0, 0, 0, 0 });

            /* decompressed size */
            centralDirectoryLengthOffsets.get(i).add(centralDirectory.size());
            centralDirectory.append(intToBytes(files[i].content.size(), 4));

            /* filename length. note that we use the _next_ file because file i
             * contains file i+1 */
            centralDirectory.append(intToBytes(quineLayerNames[(i+1)%k].length, 2));

            /* extra field length */
            centralDirectory.append(new byte[] { 0, 0 });

            /* file comment length */
            centralDirectory.append(new byte[] { 0, 0 });

            /* disk number start */
            centralDirectory.append(new byte[] { 0, 0 });

            /* internal file attributes */
            centralDirectory.append(new byte[] { 0, 0 });

            /* external file attributes */
            centralDirectory.append(new byte[] { 0, 0, 0, 0 });

            /* relative offset of local header. this is just the length of the
             * header + the amount of padding in the local header. */
            int padding = 0;
            byte[] localHeader = localHeaders[i].toArray(null);
            while (localHeader[padding] == 0) {
                ++padding;
            }
            centralDirectory.append(intToBytes(header.size() + padding, 4));

            /* file name */
            centralDirectory.append(quineLayerNames[(i+1)%k]);

            int cdSize = centralDirectory.size();

            /* end of central directory record (magic bytes) */
            centralDirectory.append(new byte[] {
                (byte) 0x50,
                (byte) 0x4b,
                (byte) 0x05,
                (byte) 0x06,
            });

            /* number of this disk */
            centralDirectory.append(new byte[] {0, 0});

            /* number of the disk with the start of the central directory */
            centralDirectory.append(new byte[] {0, 0});

            /* total entries in the central directory on this disk */
            centralDirectory.append(intToBytes(layers[i].members.size()+1, 2));

            /* total entries in the central directory */
            centralDirectory.append(intToBytes(layers[i].members.size()+1, 2));

            /* size of the central directory */
            centralDirectory.append(intToBytes(cdSize, 4));

            /* offset of the start of the central directory
             * by convention the last value in the centralDirectoryLengthOffsets
             * really refers to the true start of the central directory and not
             * the total length of the file
             * */
            centralDirectoryLengthOffsets.get(i).add(centralDirectory.size());
            centralDirectory.append(new byte[] {0, 0, 0, 0});

            /* comment length and comment */
            centralDirectory.append(new byte[] {0, 0});

            centralDirectories[i] = centralDirectory;
        }
        padUniformly(centralDirectories, centralDirectoryLengthOffsets);
        int cdSize = centralDirectories[0].size();

        /* prepend repeat cdSize, offset to each cd */
        cdSize += cdRepeatLen;
        int totalCdSize = cdSize * k;
        for (int i = 0; i < k; ++i) {
            int repeatTarget = (i+1) % k;
            int repeatOffset = repeatTarget * cdSize;
            int repeatDistance = totalCdSize - repeatOffset;

            Bytes repeat = Deflate.repeatExact(cdSize, repeatDistance, cdRepeatLen, true);
            repeat.append(centralDirectories[i]);
            centralDirectories[i] = repeat;

            List<Integer> offsets = centralDirectoryLengthOffsets.get(i);
            for (int j = 0; j < offsets.size(); ++j) {
                offsets.set(j, offsets.get(j) + cdRepeatLen);
            }
        }

        List<Segment> file = new ArrayList<>();
        List<Integer> headerLocations = new ArrayList<>();
        int fileLen = 0;

        headerLocations.add(fileLen);
        file.add(new Segment(SegmentType.LITERAL, header, 0));
        fileLen += header.size();

        int myLhOffset = fileLen;
        file.add(new Segment(SegmentType.LH, null, 0));
        fileLen += lhSize;

        file.add(new Segment(SegmentType.LITERAL, Deflate.literalHeader(header.size()), 0));
        fileLen += Deflate.CMD_SIZE;
        int HoOffset = fileLen;
        Bytes repeatHoBytes = Deflate.repeatGreedy(header.size(), HoOffset);

        headerLocations.add(fileLen);
        file.add(new Segment(SegmentType.LITERAL, header, 0));
        fileLen += header.size();

        int p2Len = lhSize + Deflate.CMD_SIZE;
        int headerBaseStart = fileLen;
        int headerBaseLen = -1;
        int headersEnd = -1;

        for (int i = 1; i <= k; ++i) {
            /* we want something like this
             *
             * print 6
             * print 5
             * print 4
             * print 2
             * LH5
             * print H
             * repeat H, o*/
            for (int j = i-2; j >= 0; --j) {
                int thisLen = headerBaseLen + j*Deflate.CMD_SIZE;
                file.add(new Segment(SegmentType.LITERAL, Deflate.literalHeader(thisLen), 0));
                fileLen += Deflate.CMD_SIZE;
            }

            file.add(new Segment(SegmentType.LITERAL, Deflate.literalHeader(p2Len), 0));
            fileLen += Deflate.CMD_SIZE;

            file.add(new Segment(SegmentType.LH, null, i));
            fileLen += lhSize;

            file.add(new Segment(SegmentType.LITERAL, Deflate.literalHeader(header.size()), 0));
            fileLen += Deflate.CMD_SIZE;

            file.add(new Segment(SegmentType.LITERAL, repeatHoBytes, 0));
            fileLen += repeatHoBytes.size();

            headersEnd = fileLen;
            if (headerBaseLen == -1) {
                headerBaseLen = headersEnd - headerBaseStart;
            }
        }

        /* print k */
        file.add(new Segment(SegmentType.LITERAL, Deflate.literalHeader(k * Deflate.CMD_SIZE), 0));
        fileLen += Deflate.CMD_SIZE;

        Bytes quinePart = new Bytes();
        /* print k+2
         * print k+1
         * ...
         * print 4
         * */
        for (int i = k+2; k >= 4; --i) {
            quinePart.append(Deflate.literalHeader(i * Deflate.CMD_SIZE));
            fileLen += Deflate.CMD_SIZE;
        }

        /* print 2 */
        quinePart.append(Deflate.literalHeader(2 * Deflate.CMD_SIZE));
        fileLen += Deflate.CMD_SIZE;

        /* repeat 1, l */
        int firstHeaderDistance = fileLen - myLhOffset;
        Bytes r1l = Deflate.repeatMinimum(lhSize, firstHeaderDistance);
        quinePart.append(r1l);
        fileLen += r1l.size();

        /* print h */
        quinePart.append(Deflate.literalHeader(header.size()));
        fileLen += Deflate.CMD_SIZE;

        /* repeat H, o */
        quinePart.append(repeatHoBytes);
        fileLen += repeatHoBytes.size();

        Bytes quine = Deflate.quine(quinePart);
        fileLen += quine.size();

        file.add(new Segment(SegmentType.LITERAL, quinePart, 0));
        file.add(new Segment(SegmentType.LITERAL, quine, 0));

        Bytes cdDumpster = Deflate.dumpster(totalCdSize);
        file.add(new Segment(SegmentType.LITERAL, cdDumpster, 0));
        fileLen += cdDumpster.size();

        for (int i = 0; i < k; ++i) {
            file.add(new Segment(SegmentType.CD, null, i));
            fileLen += cdSize;
        }

        int cdStart = fileLen;
        file.add(new Segment(SegmentType.CD, null, -1));
        fileLen += cdSize;

        /* we've now built the zip file structure, now we have to construct the
         * zip files */

        int[] cdStarts = new int[k];

        for (int i = 0; i < k; ++i) {
            for (cdStarts[i] = cdRepeatLen; cdStarts[i] < cdSize; ++cdStarts[i]) {
                if (centralDirectories[i].get(cdStarts[i]) != 0) {
                    break;
                }
            }
            cdStarts[i] += cdStart;
        }

        /* since we know the total file length we can start filling in the
         * unknowns from earlier */
        byte[] fileLenBytes = intToBytes(fileLen, 4);
        for (int i = 0; i < k; ++i) {
            for (int offset: localHeaderLengthOffsets.get(i)) {
                for (int j = 0; j < 4; ++j) {
                    localHeaders[i].set(offset+j, fileLenBytes[j]);
                }
            }

            List<Integer> cdOffsets = centralDirectoryLengthOffsets.get(i);
            for (int offset: cdOffsets) {
                for (int j = 0; j < 4; ++j) {
                    centralDirectories[i].set(offset+j, fileLenBytes[j]);
                }
            }

            int finalOffset = cdOffsets.get(cdOffsets.size()-1);
            /* the final value in centralDirectoryLengthOffsets refers to the
             * true start of the central directory and not the total length of
             * the file */
            byte[] cdStartBytes = intToBytes(cdStarts[i], 4);
            for (int j = 0; j < 4; ++j) {
                centralDirectories[i].set(finalOffset+j, cdStartBytes[j]);
            }
        }

        Bytes[] finalFiles = new Bytes[k];
        for (int i = 0; i < k; ++i) {
            finalFiles[i] = new Bytes();
            for (Segment s: file) {
                int x;
                switch (s.type) {
                    case LITERAL:
                        finalFiles[i].append(s.data);
                        break;
                    case LH:
                        x = (i + s.offset) % k;
                        finalFiles[i].append(localHeaders[x]);
                        break;
                    case CD:
                        if (s.offset == -1) {
                            x = i;
                        } else {
                            x = s.offset;
                        }
                        finalFiles[i].append(centralDirectories[x]);
                        break;
                }
            }
            System.out.println(finalFiles[i].size());
        }

        return finalFiles[0];
    }
}
