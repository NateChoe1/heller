package dev.natechoe.heller;

public class Deflate {
    /* Lx takes 5 bytes, assuming initial byte alignment */
    public static final int CMD_SIZE = 5;

    public static Bytes literalHeader(int length, boolean bfinal) {
        if (length > 0xffff) {
            return null;
        }

        Bytes ret = new Bytes();

        /* literal block header
         * first bit is bfinal
         * next 2 bits are 00 for "no compression"
         * then we pad to the nearest byte */
        if (bfinal) {
            ret.append((byte) 0b00000001);
        } else {
            ret.append((byte) 0b00000000);
        }

        byte[] lengthBytes = new byte[] {
            (byte) (length & 0xff),
            (byte) ((length >> 8) & 0xff),
        };
        ret.append(lengthBytes);
        lengthBytes[0] ^= 0xff;
        lengthBytes[1] ^= 0xff;
        ret.append(lengthBytes);

        return ret;
    }

    public static Bytes literalHeader(int length) {
        return Deflate.literalHeader(length, false);
    }

    public static Bytes literal(Bytes data, boolean bfinal) {
        Bytes ret = literalHeader(data.size(), bfinal);
        ret.append(data);
        return ret;
    }

    public static Bytes literal(Bytes data) {
        return Deflate.literal(data, false);
    }
}
