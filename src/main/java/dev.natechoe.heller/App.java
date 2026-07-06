package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    static void init(int[] l, int f, int e, int b) {
        for (int i = f; i < e; ++i) l[i] = b;
    }
    public static void main(String[] args) {
        int[] l = new int[288];
        init(l, 0, 144, 8);
        init(l, 144, 256, 9);
        init(l, 256, 280, 7);
        init(l, 280, 288, 8);
        String[] b = Deflate.genTree(l);
        for (int i = 0; i < l.length; ++i) {
            System.out.printf("%3d: %s\n", i, b[i]);
        }
    }
}
