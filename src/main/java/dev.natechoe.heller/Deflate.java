package dev.natechoe.heller;

import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;

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

    private static class BitTree {
        /* literally just a string of ASCII '0' and '1' characters */
        String data;

        /* the bit data that comes /before/ this node */
        BitTree prev;

        boolean bfinal;

        BitTree(String data, boolean bfinal, BitTree prev) {
            this.data = data;
            this.bfinal = bfinal;
            this.prev = prev;
        }

        BitTree(String data, boolean bfinal) {
            this(data, bfinal, null);
        }

        BitTree(String data) {
            this(data, false);
        }

        Bytes reconstruct() {
            List<String> fullData = new ArrayList<>();
            BitTree iter = this;
            int bfinalIdx = -1;
            while (iter != null) {
                if (bfinalIdx == -1 && iter.bfinal) {
                    bfinalIdx = fullData.size();
                }
                fullData.add(iter.data);
                iter = iter.prev;
            }

            Bytes ret = new Bytes();
            byte thisByte = 0;
            int length = 0;
            for (int i = fullData.size()-1; i >= 0; --i) {
                char[] s = fullData.get(i).toCharArray();
                if (i == bfinalIdx) {
                    s[0] = 1;
                }
                for (char c: s) {
                    byte b = (byte) (c == '1' ? 1 : 0);
                    b <<= (length % 8);
                    thisByte |= b;
                    ++length;

                    if (length % 8 == 0) {
                        ret.append(thisByte);
                    }
                }
            }

            if (length % 8 != 0) {
                ret.append(thisByte);
            }

            return ret;
        }
    }

    /* expresses v as an l bit binary string, including leading zeros and
     * truncating higher order bits */
    private static String expand(long v, int l) {
        char[] d = new char[l];
        for (int i = l-1; i >= 0; --i) {
            d[i] = (char) ((v & 1) + '0');
            v >>= 1;
        }
        return new String(d);
    }

    /* converts from a set of code lengths into a set of huffman codes
     * assumes that no code is longer than 63 bits */
    static String[] genTree(int[] lengths) {
        TreeMap<Integer, List<Integer>> lm = new TreeMap<>();
        for (int i = 0; i < lengths.length; ++i) {
            if (!lm.containsKey(lengths[i])) {
                lm.put(lengths[i], new ArrayList<>());
            }
            lm.get(lengths[i]).add(i);
        }

        long b = 0;
        int pl = 0;
        String[] ret = new String[lengths.length];
        for (Map.Entry<Integer, List<Integer>> lengthClass: lm.entrySet()) {
            int length = lengthClass.getKey();
            List<Integer> values = lengthClass.getValue();

            if (pl != 0) {
                b <<= (length - pl);
            }
            pl = length;

            for (int i: values) {
                String s = expand(b, length);
                ret[i] = s;
                ++b;
            }
        }

        if (b != (1l << pl)) {
            return null;
        }
        return ret;
    }

    /**/
    public static Map<Integer, Bytes> repeat(int length, int offset,
            boolean bfinal) {
        return null;
    }

    public static Map<Integer, Bytes> repeat(int length, int offset) {
        return Deflate.repeat(length, offset, false);
    }
}
