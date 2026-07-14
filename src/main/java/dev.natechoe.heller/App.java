package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    public static void main(String[] args) {
        List<Bytes> repeatPossibilities = Deflate.repeat(17, 17, 10, false);
        System.out.println(repeatPossibilities.size());
        for (Bytes block: repeatPossibilities) {
            for (Byte b: block) {
                System.out.printf("%02x ", b);
            }
            System.out.println();
        }
    }
}
