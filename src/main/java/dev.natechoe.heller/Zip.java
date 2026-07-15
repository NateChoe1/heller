package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.zip.Deflater;

public class Zip {
    public static class ZipEntry {
        String filename;
        Bytes content;
        boolean isPlaintext;
        byte[] crc;
        int offset;
        int compressedSize;
        ZipEntry(String filename, Bytes content, boolean isPlaintext) {
            this.filename = filename;
            this.content = content;
            this.isPlaintext = isPlaintext;
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

    public static Bytes createZip(ZipEntry[] files) {
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

        int cdStart = ret.size();
        for (int i = 0; i < files.length; ++i) {
            /* central directory header magic bytes */
            ret.append(new byte[] {
                (byte) 0x50,
                (byte) 0x4b,
                (byte) 0x01,
                (byte) 0x02,
            });

            /* version made with (6.3 on unix) */
            ret.append(new byte[] { 63, 3 });

            /* version needed to extract */
            ret.append(new byte[] { 2, 0 });

            /* general purpose flags, max compression */
            ret.append(new byte[] { 2, 0 });

            /* compression method, deflate */
            ret.append(new byte[] { 8, 0 });

            /* last mod date and time
             * TODO: with the one above, set these to something meaningful
             * */
            ret.append(new byte[] { 0, 0 });  /* last mod time */
            ret.append(new byte[] { 0, 0 });  /* last mod date */

            /* crc */
            ret.append(files[i].crc);

            /* compressed size */
            ret.append(intToBytes(files[i].compressedSize, 4));

            /* decompressed size */
            ret.append(intToBytes(files[i].content.size(), 4));

            /* filename length */
            byte[] nameBytes = files[i].filename.getBytes();
            ret.append(intToBytes(nameBytes.length, 2));

            /* extra field length */
            ret.append(new byte[] { 0, 0 });

            /* file comment length */
            ret.append(new byte[] { 0, 0 });

            /* disk number start */
            ret.append(new byte[] { 0, 0 });

            /* internal file attributes */
            ret.append(new byte[] { (byte) (files[i].isPlaintext ? 1:0), 0 });

            /* external file attributes */
            ret.append(new byte[] { 0, 0, 0, 0 });

            /* relative offset of local header */
            ret.append(intToBytes(files[i].offset, 4));

            /* file name */
            ret.append(nameBytes);
        }
        int cdEnd = ret.size();
        int cdSize = cdEnd - cdStart;

        /* end of central directory record magic bytes */
        ret.append(new byte[] {0x50, 0x4b, 0x05, 0x06});

        /* number of this disk */
        ret.append(new byte[] { 0, 0 });

        /* number of the disk with the start of the central directory */
        ret.append(new byte[] { 0, 0 });

        /* total entries in the central directory on this disk */
        ret.append(intToBytes(files.length, 2));

        /* total entries in the central directory */
        ret.append(intToBytes(files.length, 2));

        /* size of the central directory */
        ret.append(intToBytes(cdSize, 4));

        /* offset of the start of central directory */
        ret.append(intToBytes(cdStart, 4));

        /* .zip file comment length */
        ret.append(new byte[] { 0, 0 });

        return ret;
    }
}
