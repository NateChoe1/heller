package dev.natechoe.heller;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

public class Deflate {
    /* Lx takes 5 bytes, assuming initial byte alignment */
    public static final int CMD_SIZE = 5;

    private static String[] fixedCodes;
    private static String[] distanceCodes;

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

    /* expresses v as an l bit binary string, including leading zeros and
     * truncating higher order bits */
    private static String expand(long v, int l, boolean lsbf) {
        char[] d = new char[l];
        int start = lsbf ? 0 : l-1;
        int offset = lsbf ? 1 : -1;
        for (int i = 0; i < l; ++i) {
            d[start + offset*i] = (char) ((v & 1) + '0');
            v >>= 1;
        }
        return new String(d);
    }

    private static String expand(long v, int l) {
        return expand(v, l, false);
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
                String s = Deflate.expand(b, length);
                ret[i] = s;
                ++b;
            }
        }

        if (b != (1l << pl)) {
            return null;
        }
        return ret;
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

    private static class RepeatEntry {
        Map<Integer, BitTree> noLiteral;
        Map<Integer, BitTree> literal;
        public RepeatEntry(Map<Integer, BitTree> noLiteral, Map<Integer, BitTree> literal) {
            this.noLiteral = noLiteral;
            this.literal = literal;
        }

        public RepeatEntry() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    static {
        int[] fixedLengths = new int[288];
        for (int i = 0; i < 144; ++i) fixedLengths[i] = 8;
        for (int i = 144; i < 256; ++i) fixedLengths[i] = 9;
        for (int i = 256; i < 280; ++i) fixedLengths[i] = 7;
        for (int i = 280; i < 288; ++i) fixedLengths[i] = 8;

        Deflate.fixedCodes = genTree(fixedLengths);

        int[] distanceLengths = new int[32];
        for (int i = 0; i < 32; ++i) distanceLengths[i] = 5;
        Deflate.distanceCodes = genTree(distanceLengths);
    }

    /* TODO: maybe some duplication here, it's probably fine */
    public static String repeatCode(int length, int distance) {
        if (length < 3 || length > 258) {
            return null;
        }

        StringBuilder sb = new StringBuilder("");

        getLength: {
            int[] bits = new int[] {
                0, 0, 0, 0, 0, 0, 0, 0,
                1, 1, 1, 1,
                2, 2, 2, 2,
                3, 3, 3, 3,
                4, 4, 4, 4,
                5, 5, 5, 5,
                0,
            };
            int firstCode = 257;
            int firstLength = 3;
            for (int i = 0; i < bits.length; ++i) {
                int endLength = firstLength + (1 << bits[i]);
                if (length >= endLength) {
                    firstLength = endLength;
                    continue;
                }

                int code = firstCode + i;
                sb.append(Deflate.fixedCodes[code]);

                int offset = length - firstLength;
                sb.append(Deflate.expand(offset, bits[i], true));
                break getLength;
            }
            return null;
        }

        getDistance: {
            int[] bits = new int[] {
                0, 0, 0, 0,
                1,  1,
                2,  2,
                3,  3,
                4,  4,
                5,  5,
                6,  6,
                7,  7,
                8,  8,
                9,  9,
                10, 10,
                11, 11,
                12, 12,
                13, 13,
            };
            int firstDistance = 1;
            for (int i = 0; i < bits.length; ++i) {
                int endDistance = firstDistance + (1 << bits[i]);
                if (distance >= endDistance) {
                    firstDistance = endDistance;
                    continue;
                }

                sb.append(Deflate.distanceCodes[i]);
                int offset = distance - firstDistance;
                sb.append(Deflate.expand(offset, bits[i], true));
                break getDistance;
            }
        }

        return sb.toString();
    }

    public static List<Bytes> repeat(int length, int offset,
            boolean bfinal) {
        List<RepeatEntry> cache = new ArrayList<>();

        /* initialize cache */
        cache.add(new RepeatEntry());
        cache.add(new RepeatEntry());
        cache.add(new RepeatEntry());
        Map<Integer, BitTree> emptyFixed = new HashMap<>();
        emptyFixed.put(0, new BitTree("001", bfinal));
        cache.add(new RepeatEntry(emptyFixed, new HashMap<>()));

        return null;
    }

    public static List<Bytes> repeat(int length, int offset) {
        return Deflate.repeat(length, offset, false);
    }
}
