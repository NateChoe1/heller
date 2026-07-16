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
import java.nio.file.*;
import java.io.*;
import kotlin.Pair;
import kotlin.ULong;

public class App {
    public static void main(String[] args) throws IOException {
        List<String> inCd = new ArrayList<>();
        Map<String, Integer> ordering = new HashMap<>();
        List<String> zipNames = new ArrayList<>();
        List<List<String>> zipContents = new ArrayList<>();
        for (int i = 0; i < args.length; ++i) {
            String filename = args[i].substring(1);
            switch (args[i].charAt(0)) {
                case '+':
                    ordering.put(filename, inCd.size());
                    inCd.add(filename);
                    break;
                case '@':
                    zipNames.add(filename);
                    zipContents.add(new ArrayList<>());
                    break;
                case '-':
                    zipContents.get(zipContents.size()-1).add(filename);
            }
        }

        int numCommon = inCd.size();
        int numLayers = zipNames.size();

        Zip.ZipEntry[] commonFiles = new Zip.ZipEntry[numCommon];
        for (int i = 0; i < numCommon; ++i) {
            commonFiles[i] = makeZipEntry(inCd.get(i));
        }

        Zip.QuineLayer[] quines = new Zip.QuineLayer[numLayers];
        for (int i = 0; i < numLayers; ++i) {
            List<Integer> contentsIndexed = new ArrayList<>();
            for (String f: zipContents.get(i)) {
                contentsIndexed.add(ordering.get(f));
            }
            quines[i] = new Zip.QuineLayer(zipNames.get(i), contentsIndexed);
        }

        Bytes b = Zip.createZip(commonFiles, quines);
        FileOutputStream f = new FileOutputStream(zipNames.get(0));
        f.write(b.toArray(null));
    }

    private static Zip.ZipEntry makeZipEntry(String filename) throws IOException {
        Path path = FileSystems.getDefault().getPath(filename);
        Bytes data = new Bytes(Files.readAllBytes(path));

        boolean isPlaintext = true;
        for (Byte b: data) {
            if (Character.isISOControl(b) && b != '\n' && b != '\r') {
                isPlaintext = false;
                break;
            }
        }

        return new Zip.ZipEntry(filename, data, isPlaintext);
    }
}
