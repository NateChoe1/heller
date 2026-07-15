package dev.natechoe.heller;

import dev.natechoe.crc32.CRC32Engine;
import java.util.*;
import kotlin.Pair;
import kotlin.ULong;

public class App {
    static void p(byte[] b) {
        for (byte bb: b) {
            System.out.printf("\\x%02x", bb);
        }
    }
    public static void main(String[] args) {
        byte[] file1 = "xxxxdeadbeefyyyy".getBytes();
        byte[] file2 = "deadbeefxxxxyyyy".getBytes();

        Map<Integer, Integer> map1 = new HashMap<>();
        Map<Integer, Integer> map2 = new HashMap<>();

        map1.put(0, 0);
        map1.put(12, 1);

        map2.put(8, 0);
        map2.put(12, 1);

        Pair<byte[], Map<Integer, Integer>> p1 = new Pair<>(file1, map1);
        Pair<byte[], Map<Integer, Integer>> p2 = new Pair<>(file2, map2);

        List<Pair<byte[], Map<Integer, Integer>>> pairs = new ArrayList<>();
        pairs.add(p1);
        pairs.add(p2);

        List<byte[]> solutions = CRC32Engine.solveCRCSystem(new byte[]
                {(byte) 0x6e, (byte) 0x63, (byte) 0x3c, (byte) 0x33},
                pairs);
        p(solutions.get(0));
        System.out.print("deadbeef");
        p(solutions.get(1));

        System.out.println();

        System.out.print("deadbeef");
        p(solutions.get(0));
        p(solutions.get(1));

        System.out.println();
    }
}
