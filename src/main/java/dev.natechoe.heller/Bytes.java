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

    public Byte get(int i) {
        return this.data.get(i);
    }

    public Byte set(int i, Byte b) {
        return this.data.set(i, b);
    }

    @Override
    public Iterator<Byte> iterator() {
        return this.data.iterator();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b: this.data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
