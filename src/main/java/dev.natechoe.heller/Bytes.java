package dev.natechoe.heller;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/* appendable byte blob class */
public class Bytes implements Iterable<Byte> {
    private List<Byte> data;

    public Bytes(byte[] data) {
        this();
        this.append(data);
    }

    public Bytes(Bytes data) {
        this();
        this.append(data);
    }

    public Bytes() {
        this.data = new ArrayList<>();
    }

    public void append(byte[] data) {
        for (byte b: data) {
            this.data.add(b);
        }
    }

    public void append(Bytes data) {
        for (Byte b: data) {
            this.data.add(b);
        }
    }

    public void append(byte b) {
        this.data.add(b);
    }

    public byte[] toArray(byte[] dst) {
        if (dst == null) {
            dst = new byte[this.data.size()];
        }
        for (int i = 0; i < Math.min(dst.length, this.data.size()); ++i) {
            dst[i] = this.data.get(i);
        }
        return dst;
    }

    public int size() {
        return this.data.size();
    }

    @Override
    public Iterator<Byte> iterator() {
        return this.data.iterator();
    }
}
