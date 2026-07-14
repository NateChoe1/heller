package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    public static void main(String[] args) {
        for (int l = 5; l <= 40; ++l) {
            List<Bytes> r = Deflate.repeat(l, l, 20, false);
            System.out.printf("%d: ", l);
            for (Bytes b: r) {
                System.out.printf("%d, ", b.size());
            }
            System.out.println();
        }
    }
}
