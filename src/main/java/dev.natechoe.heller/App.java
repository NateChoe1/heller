package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;

public class App {
    public static void main(String[] args) {
        Bytes quineHeader = Deflate.literalHeader(0, false);
        Bytes quine = Deflate.quine(quineHeader);
        quine.append(Deflate.literalHeader(0, true));
        Bytes b = new Bytes();
        b.append(quineHeader);
        b.append(quine);
        for (Byte bb: b) {
            System.out.printf("%02x ", bb);
        }
        System.out.println();
    }
}
