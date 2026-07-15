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
            byte[] nameBytes = files[i].filename.getBytes();
            ret.append(intToBytes(nameBytes.length, 2));

            /* extra field length (0) */
            ret.append(new byte[] { 0, 0 });

            /* file name */
            ret.append(nameBytes);

            /* file data */
            ret.append(compressedData);
        }

        return ret;
    }

    /* nc<3 */
    private final static byte[] crc = new byte[] { 0x6e, 0x63, 0x3c, 0x33 };

    private static enum SegmentType {
        HEADER,
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
        List<Segment> file = new ArrayList<>();
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
                (byte) 0x4b,
                (byte) 0x50,
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
            localHeader.append(intToBytes(quineLayerNames[i].length, 2));

            /* extra field length */
            localHeader.append(new byte[] {0, 0});

            /* file name */
            localHeader.append(quineLayerNames[i]);

            localHeaders[i] = localHeader;
        }

        padUniformly(localHeaders, localHeaderLengthOffsets);

        return null;
    }
}
