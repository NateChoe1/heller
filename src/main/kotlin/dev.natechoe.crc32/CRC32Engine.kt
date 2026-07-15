/* Written by Nate Choe, sourced from
 * https://github.com/ruvmello/zip-quine-generator
 *
 * I've modified this slightly to improve JVM interoperability, nothing of
 * substance has really changed though. The original zip quine generator was
 * released under the MIT license, so this package is released under the same
 * license. This license doesn't apply to the rest of heller, only this package.
 *
 * MIT License
 *
 * Copyright (c) 2025-2026 Nate Choe
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * */

package dev.natechoe.crc32

class CRC32Engine {
    companion object {
        val POLYNOMIAL = 0x104c11db7UL

        /**
         * Calculates a CRC
         *
         * @param data The data to calculate
         * @return The CRC32 checksum
         */
        @JvmStatic
        fun calculateCRC(data: ByteArray): ByteArray {
            var ret = 0xffffffffU
            for (byte in data) {
                ret = ret xor (byte.toUInt() and 0xffU)
                for (i in 0 until 8) {
                    ret = if (ret and 1U == 1U) {
                        (ret shr 1) xor 0xEDB88320U
                    } else {
                        ret shr 1
                    }
                }
            }
            return getByteArrayOf4Bytes(ret.inv().toInt())
        }

        /**
         * Solves a CRC system with multiple files which potentially reference
         * each others's CRCs using Gauss-Jordan elimination.
         *
         * @param files A list of files to solve for CRCs. The associated map
         * goes from offsets to file IDs, where 0 is the first file, 1 is the
         * second, and so on.
         * @return The CRCs of each file
         */
        @JvmStatic
        public fun solveCRCSystem(target: ByteArray, files: List<Pair<ByteArray, Map<Int, Int>>>): List<ByteArray> {
            val n = files.size

            // Augmented matrix of polynomials
            var matrix = Array(n) { Array(n+1) {0UL} }

            // Construct augmented matrix
            for (file in 0 until n) {
                var i = 0
                val data = files[file].first
                val offsets = files[file].second
                matrix[file][n] = 0xffffffffUL

                while (i < data.size) {
                    if (offsets.containsKey(i)) {
                        val offset = offsets.get(i)!!
                        matrix[file][offset] = matrix[file][offset] xor 1UL
                        for (j in 0..n) {
                            matrix[file][j] = multiply(matrix[file][j], 0x100000000UL, POLYNOMIAL)
                        }
                        i += 4
                        continue
                    }

                    for (j in 0 until 8) {
                        if (((data[i].toInt() and 0xff) and (1 shl j)) != 0) {
                            matrix[file][n] = matrix[file][n] xor 0x80000000UL
                        }
                        matrix[file][n] = multiply(matrix[file][n], 2UL, POLYNOMIAL)
                    }
                    for (j in 0 until n) {
                        matrix[file][j] = multiply(matrix[file][j], 0x100UL, POLYNOMIAL)
                    }

                    i++
                }
            }

            var targetOffset = 0UL
            if (target != null) {
                for (byte in target) {
                    var bu = byte.toUByte().toULong()
                    for (i in 0..<8) {
                        targetOffset = targetOffset shl 1
                        targetOffset = targetOffset or ((bu shr i) and 1UL)
                    }
                }
            }

            for (file in 0 until n) {
                matrix[file][n] = matrix[file][n] xor 0xffffffffUL
                if (target == null) {
                    matrix[file][file] = matrix[file][file] xor 1UL
                } else {
                    matrix[file][n] = matrix[file][n] xor targetOffset
                }
            }

            // Convert to upper triangular matrix
            for (solvingRow in 0 until n) {
                var exchange = solvingRow
                while (exchange < n) {
                    if (matrix[exchange][solvingRow] != 0UL) {
                        break
                    }
                    exchange++
                }

                // The matrix rank is less than n, so there isn't a unique solution.
                // This assertion will almost never fail, although it technically could.
                // If it does fail, that means that there is no solution or more than
                // one solution.
                assert(exchange < n) {"Gauss-Jordan elimination failed while solving CRC"}

                // Swap in a good row
                if (solvingRow != exchange) {
                    val backup = matrix[solvingRow]
                    matrix[solvingRow] = matrix[exchange]
                    matrix[exchange] = backup
                }

                // Eliminate all the other rows

                val inverse = minv(matrix[solvingRow][solvingRow], POLYNOMIAL)
                for (eliminatingRow in solvingRow+1 until n) {
                    val anchor = matrix[eliminatingRow][solvingRow]
                    val multiplier = multiply(inverse, anchor, POLYNOMIAL)
                    for (eliminatingColumn in 0..n) {
                        matrix[eliminatingRow][eliminatingColumn] = matrix[eliminatingRow][eliminatingColumn] xor multiply(multiplier, matrix[solvingRow][eliminatingColumn], POLYNOMIAL)
                    }
                }
            }

            // Solve the simplified system
            var results = Array(n) { 0UL }
            for (row in n-1 downTo 0) {
                results[row] = matrix[row][n]
                for (column in row+1 until n) {
                    results[row] = results[row] xor multiply(results[column], matrix[row][column], POLYNOMIAL)
                }
                results[row] = divide(results[row], matrix[row][row], POLYNOMIAL)
            }

            return List(n,
                fun(i: Int): ByteArray {
                    var implementationValue = 0UL
                    val mathematicalValue = results[i]
                    for (bit in 0 until 32) {
                        if ((mathematicalValue and (1UL shl bit)) != 0UL) {
                            implementationValue = implementationValue or (1UL shl (31 - bit))
                        }
                    }
                    return getByteArrayOf4Bytes(implementationValue.toInt())
                }
            )
        }

        /**
         * Multiplies two polynomials
         *
         * @param p1 Polynomial 1
         * @param p2 Polynomial 2
         * @return p1*p2 as a polynomial multiplication with no modulus
         * */
        private fun multiplyRaw(p1: ULong, p2: ULong): ULong {
            var ret: ULong = 0UL
            val probe = findProbe(p1)

            for (i in 0 until 64) {
                if ((p2 and (1UL shl i)) != 0UL) {
                    assert(probe + i < 64) {"Polynomial multiplication had an overflow"}
                    ret = ret xor (p1 shl i)
                }
            }

            return ret
        }

        /**
         * Finds the highest bit of p
         *
         * @param p Some ULong
         * @return The greatest value b such that (1LU shl b) and p, or 0 if p==0
         * */
        private fun findProbe(p: ULong): Int {
            var ret = 0

            for (i in 0 until 64) {
                if ((1UL shl i) and p != 0UL) {
                    ret = i
                }
            }

            return ret
        }

        /**
         * Divides two polynomials, and returns the pair (quotient, remainder)
         *
         * @param dividend The numerator
         * @param divisor The denominator
         * @return The pair (quotient, remainder)
         */
        private fun divmod(dividend: ULong, divisor: ULong): Pair<ULong, ULong> {
            val probe = findProbe(divisor)
            val probeBit = 1UL shl probe
            var shiftAmount = 63 - probe
            var quot = 0UL
            var rem = dividend

            for (i in shiftAmount downTo 0) {
                if ((rem and (probeBit shl i)) != 0UL) {
                    quot = quot or (1UL shl i)
                    rem = rem xor (divisor shl i)
                }
            }

            return Pair(quot, rem)
        }

        /**
         * Multiplies two polynomials with a modulus
         *
         * @param p1 The first polynomial
         * @param p2 The second polynomial
         * @param mod The modulus
         * @return p1*p2%mod as polynomials
         */
        private fun multiply(p1: ULong, p2: ULong, mod: ULong): ULong {
            val rawProduct = multiplyRaw(p1, p2)
            return divmod(rawProduct, mod).second

        }

        /**
         * Calculates Bezout coefficients of two polynomials with the extended Euclidean
         * algorithm
         *
         * @param p1 The first polynomial
         * @param p2 The second polynomial
         * @return (k1, k2, gcd) where p1*k1 + p2*k2 = gcd */
        private fun xgcd(p1: ULong, p2: ULong): Triple<ULong, ULong, ULong> {
            if (findProbe(p1) < findProbe(p2)) {
                val r = xgcd(p2, p1)
                return Triple(r.second, r.first, r.third)
            }

            if (p2 == 0UL) {
                return Triple(p1, 0UL, p1)
            }

            val nextArgs = divmod(p1, p2)
            val nextLevel = xgcd(p2, nextArgs.second)

            val c1 = nextLevel.second
            val c2 = nextLevel.first xor multiplyRaw(nextLevel.second, nextArgs.first)
            return Triple(c1, c2, nextLevel.third)
        }

        /**
         * Calculates the multiplicative inverse of a polynomial over a modulus
         *
         * @param p The polynomial
         * @param mod The modulus
         * @return p^-1 over mod, or 0 if there isn't one
         */
        private fun minv(p: ULong, mod: ULong): ULong {
            val r = xgcd(p, mod)
            if (r.third != 1UL) {
                return 0UL
            }

            return divmod(r.first, mod).second
        }

        /**
         * Divides two polynomials over a modulus
         *
         * @param dividend The numerator
         * @param divisor The denominator
         * @param mod The modulus
         * @return dividend/divisor over mod, or 0 if this isn't possible
         */
        private fun divide(dividend: ULong, divisor: ULong, mod: ULong): ULong {
            return multiply(dividend, minv(divisor, mod), mod)
        }

        /**
         * An integer has a size of 32 bits, get a UByteArray of the size four
         * with the least significant byte first
         *
         * Taken from https://github.com/ruvmello/zip-quine-generator
         *
         * @param input the integer for which we construct a ByteArray of size
         * four
         * @return the bytearray of size four
         */
        private fun getByteArrayOf4Bytes(input: Int): ByteArray {
            return byteArrayOf(
                (input shr 0).toByte(),
                (input shr 8).toByte(),
                (input shr 16).toByte(),
                (input shr 24).toByte())
        }
    }
}
