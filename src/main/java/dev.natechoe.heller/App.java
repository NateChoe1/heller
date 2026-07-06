package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    public static void main(String[] args) {
        System.out.println(Arrays.toString(CRC32Engine.calculateCRC(
                        new byte[] { 65, 66, 67 })));
    }
}
