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
                    s[0] = '1';
                }
                for (char c: s) {
                    byte b = (byte) (c == '1' ? 1 : 0);
                    b <<= (length % 8);
                    thisByte |= b;
                    ++length;

                    if (length % 8 == 0) {
                        ret.append(thisByte);
                        thisByte = 0;
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
        Map<Integer, BitTree> inRepeat;
        Map<Integer, BitTree> noRepeat;
        public RepeatEntry(Map<Integer, BitTree> inRepeat, Map<Integer, BitTree> noRepeat) {
            this.inRepeat = inRepeat;
            this.noRepeat = noRepeat;
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
    private static String repeatCode(int length, int distance) {
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

    public static List<Bytes> repeat(int length, int offset, int limit,
            boolean bfinal) {
        List<RepeatEntry> cache = new ArrayList<>();

        /* initialize cache */
        Map<Integer, BitTree> initialState = new HashMap<>();
        initialState.put(0, new BitTree("", false));
        cache.add(new RepeatEntry(new HashMap<>(), initialState));

        String[] repeatCodes = new String[259];
        for (int i = 0; i < repeatCodes.length; ++i) {
            repeatCodes[i] = Deflate.repeatCode(i, offset);
        }

        String endCode = Deflate.fixedCodes[256];
        List<Bytes> ret = new ArrayList<>();

        for (;;) {
            int currLength = cache.size();

            Map<Integer, BitTree> inRepeat = new HashMap<>();
            Map<Integer, BitTree> noRepeat = new HashMap<>();

            /* start every possible repeat block */
            if (currLength >= 3) {
                for (Map.Entry<Integer, BitTree> prevCode: cache.get(currLength-3).noRepeat.entrySet()) {
                    int prevLength = prevCode.getKey();
                    BitTree prevBits = prevCode.getValue();
                    BitTree newTree = new BitTree("010", bfinal, prevBits);
                    inRepeat.put(prevLength, newTree);
                }
            }

            for (int l = 0; l < repeatCodes.length; ++l) {
                String repeatCode = repeatCodes[l];
                if (repeatCode == null || repeatCode.length() > currLength) {
                    continue;
                }

                for (Map.Entry<Integer, BitTree> prevCode:
                        cache.get(currLength - repeatCode.length()).inRepeat.entrySet()) {
                    int prevLength = prevCode.getKey();
                    BitTree prevBits = prevCode.getValue();

                    int newLength = prevLength + l;

                    /* adding this code to this candidate overshoots the target
                     * length*/
                    if (newLength > length) {
                        continue;
                    }

                    /* we already have a code with this length */
                    if (inRepeat.containsKey(newLength)) {
                        continue;
                    }

                    /* this is a new (encoded length, decoded length) combo, add
                     * it */
                    inRepeat.put(newLength, new BitTree(repeatCode, false, prevBits));
                }
            }

            /* end every possible repeat block */
            if (currLength >= endCode.length()) {
                for (Map.Entry<Integer, BitTree> prevCode: cache.get(currLength-endCode.length()).inRepeat.entrySet()) {
                    int prevLength = prevCode.getKey();
                    BitTree prevBits = prevCode.getValue();
                    if (noRepeat.containsKey(prevLength)) {
                        continue;
                    }
                    BitTree newBits = new BitTree(endCode, false, prevBits);
                    noRepeat.put(prevLength, newBits);
                }
            }

            /* start every print 0 block */
            if (currLength % 8 == 0) {
                for (int padding = 0; padding < 8; ++padding) {
                    /* print 0 goes like this:
                     * 0   (bfinal)
                     * 00  (literal)
                     * [padding]
                     * 0000000000000000  (len)
                     * 1111111111111111  (nlen)
                     * this is 35 bits + the amount of padding
                     * we pad to the nearest byte, so padding can be anywhere
                     * from 0 to 7 inclusive.
                     * */
                    int lookBehind = padding + 35;
                    StringBuilder sb = new StringBuilder("000");
                    for (int i = 0; i < padding; ++i) {
                        sb.append('0');
                    }
                    sb.append("0000000000000000");
                    sb.append("1111111111111111");
                    String print0 = sb.toString();
                    if (print0.length() > currLength) {
                        break;
                    }
                    for (Map.Entry<Integer, BitTree> prevCode: cache.get(currLength - lookBehind).noRepeat.entrySet()) {
                        int prevLength = prevCode.getKey();
                        BitTree prevBits = prevCode.getValue();
                        if (noRepeat.containsKey(prevLength)) {
                            continue;
                        }
                        BitTree newBits = new BitTree(print0, bfinal, prevBits);
                        noRepeat.put(prevLength, newBits);
                    }
                }
            }

            if (currLength % 8 == 0) {
                for (Map.Entry<Integer, BitTree> encoding: noRepeat.entrySet()) {
                    if (encoding.getKey() != length) {
                        continue;
                    }
                    ret.add(encoding.getValue().reconstruct());
                    break;
                }
            }

            cache.add(new RepeatEntry(inRepeat, noRepeat));

            if (currLength >= limit*8) {
                break;
            }
        }

        return ret;
    }

    public static List<Bytes> repeat(int length, int offset, int limit) {
        return Deflate.repeat(length, offset, limit, false);
    }

    public static Bytes repeatExact(int length, int offset, int target, boolean bfinal) {
        List<Bytes> candidates = Deflate.repeat(length, offset, target, bfinal);
        for (Bytes b: candidates) {
            if (b.size() == target) {
                return b;
            }
        }
        return null;
    }

    public static Bytes repeatExact(int length, int offset, int target) {
        return Deflate.repeatExact(length, offset, target, false);
    }

    public static Bytes repeatGreedy(int length, int offset, boolean bfinal) {
        BitTree ret = new BitTree("010", bfinal);
        int encodedLength = 3;

        while (length > 0) {
            /* maximum repeat length */
            int partLength;
            if (length > 258 && length % 258 < 3) {
                partLength = 255;
            } else {
                partLength = Math.min(length, 258);
            }

            String part = Deflate.repeatCode(partLength, offset);
            encodedLength += part.length();
            ret = new BitTree(part, false, ret);
            length -= partLength;
        }

        /* end repeat block */
        ret = new BitTree(fixedCodes[256], false, ret);
        encodedLength += fixedCodes[256].length();

        if (encodedLength % 8 == 0) {
            return ret.reconstruct();
        }

        /* pad with a print 0 */
        ret = new BitTree("000", bfinal, ret);
        encodedLength += 3;
        StringBuilder padding = new StringBuilder("");
        while (encodedLength % 8 != 0) {
            padding.append('0');
            ++encodedLength;
        }
        padding.append("0000000000000000");
        padding.append("1111111111111111");
        ret = new BitTree(padding.toString(), false, ret);
        return ret.reconstruct();
    }

    public static Bytes repeatGreedy(int length, int offset) {
        return Deflate.repeatGreedy(length, offset, false);
    }

    public static Bytes quine(Bytes header) {
        /* this is an idealized lz77 quine, where each line takes up exactly the
         * same amount of space. i've labeled one section which repeats twice
         * "R".
         *
         * print H+1
         *   H
         *   print H+1
         * repeat H+1 H+1    
         * print 0           
         * print 0           
         * print 4
         *   repeat H+1 H+1  
         *   print 0         
         *   print 0         
         *   print 4
         * repeat 4 4
         * print 4
         *   repeat 4 4
         *   print 4
         *   repeat 4 4
         *   print 4
         * repeat 4 4
         * print 4
         *   a     (this can be any arbitrary data)
         *   b
         *   c
         *   d
         *
         * this entire quine can be constructed using 5 bytes per line, adding
         * some padding whenever necessary. the "repeat 4 4" construct can be
         * implemented as "repeat 10 20; repeat 10 20" and be padded to exactly
         * 5 bytes. the only challenging part is Rh, since repeat blocks can
         * have variable compressed length. the only constraint is that Rh fits
         * into exactly 15 bytes.
         *
         * it turns out that any repeat length from 3-808 bytes can be encoded
         * using precisely 15 bytes. for anything bigger than that we need
         * another trick.
         *
         * print H+1
         *   H
         *   print H+1
         * repeat H+1 H+1    (L = length of this line)
         * print L+1
         *   repeat H+1 H+1
         *   print L+1
         * repeat L+1 L+1
         * print 0
         * print 0
         * print 4
         * ...
         *
         * it's possible to add an arbitrary tail, but for the quine pattern i'm
         * envisioning i don't need one
         * */

        Bytes ret = new Bytes();

        int augmentedHeaderSize = header.size() + Deflate.CMD_SIZE;
        Bytes printH1 = Deflate.literalHeader(augmentedHeaderSize);
        ret.append(printH1);
        ret.append(header);
        ret.append(printH1);

        /* the header is too long for a 15 byte repeat, shrink the header and
         * recurse */
        if (header.size() + Deflate.CMD_SIZE > 808) {
            header = Deflate.repeatGreedy(augmentedHeaderSize, augmentedHeaderSize);
            ret.append(header);
            ret.append(Deflate.quine(header));
            return ret;
        }

        Bytes RH1 = Deflate.repeatExact(augmentedHeaderSize, augmentedHeaderSize, Deflate.CMD_SIZE*3);
        Bytes R44 = Deflate.repeatExact(Deflate.CMD_SIZE*4, Deflate.CMD_SIZE*4, Deflate.CMD_SIZE);
        Bytes P4 = Deflate.literalHeader(Deflate.CMD_SIZE*4);
        ret.append(RH1);
        ret.append(P4);
        ret.append(RH1);
        ret.append(P4);
        ret.append(R44);
        ret.append(P4);
        ret.append(R44);
        ret.append(P4);
        ret.append(R44);
        ret.append(P4);
        ret.append(R44);
        ret.append(P4);

        /* 20 bytes of arbitrary data, this could be anything */
        ret.append(new Bytes("https://natechoe.dev".getBytes()));

        return ret;
    }
}
