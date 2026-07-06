package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    public static void main(String[] args) {
        Bytes data = new Bytes(new byte[] {65, 66, 67});
        Bytes deflated = Deflate.literal(data, true);
        for (byte b: deflated) {
            System.out.printf("%02x", b);
        }
        System.out.println();
        System.out.println(Arrays.toString(CRC32Engine.calculateCRC(
                        new byte[] { 65, 66, 67 })));
    }
}
