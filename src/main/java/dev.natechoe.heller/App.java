/* Heller: even loopier zip quines
 * Copyright (C) 2026  Nate Choe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;
import java.io.FileOutputStream;
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

        Zip.QuineLayer layer1 = new Zip.QuineLayer("l1.zip", Arrays.asList(new Integer[] {0, 1}));
        Zip.QuineLayer layer2 = new Zip.QuineLayer("layer2.zip", Arrays.asList(new Integer[] {0, 1}));

        Bytes b = Zip.createZip(new Zip.ZipEntry[] {file1, file2}, new Zip.QuineLayer[] {layer1, layer2});

        FileOutputStream f = new FileOutputStream("l1.zip");
        f.write(b.toArray(null));
    }
}
