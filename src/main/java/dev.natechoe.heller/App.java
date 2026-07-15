package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;
import kotlin.Pair;
import kotlin.ULong;

public class App {
    public static void main(String[] args) throws java.io.IOException {
        Zip.ZipEntry file1 = new Zip.ZipEntry(
            "hi.txt",
            new Bytes("hello world! this is a test of my .zip encoder".getBytes()),
            true
        );

        Zip.ZipEntry file2 = new Zip.ZipEntry(
            "data.bin",
            new Bytes(new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef, (byte) 0xba, (byte) 0xbe, (byte) 0xca, (byte) 0xfe}),
            false
        );

        Bytes b = Zip.createZip(new Zip.ZipEntry[] {file1, file2});
        System.err.println(b.size());
        System.out.write(b.toArray(null));
    }
}
