package com.ysm.parser;

/**
 * JNI wrapper for low-level native algorithms used by YSM.
 *
 * <p>Exposes CityHash, Zstd, XChaCha20, and MT19937 primitives
 * directly to Java. All methods are static and thread-safe.
 */
public class YSMNative {

    static {
        System.loadLibrary("YSMParserJNI");
    }

    // ── CityHash ──────────────────────────────────────────────────────────

    public static native long cityHash64(byte[] data);
    public static native long cityHash64WithSeed(byte[] data, long seed);
    public static native long[] cityHash128(byte[] data);
    public static native long[] cityHash128WithSeed(byte[] data, long seedLow, long seedHigh);

    // ── Zstd ──────────────────────────────────────────────────────────────

    public static native byte[] zstdDecompress(byte[] data);
    public static native byte[] zstdCompress(byte[] data, int level);

    // ── XChaCha20 ─────────────────────────────────────────────────────────

    /**
     * @param key   32-byte key
     * @param iv    24-byte nonce
     * @param rounds number of rounds (10, 20, or 30)
     */
    public static native byte[] xchacha20Encrypt(byte[] data, byte[] key, byte[] iv, int rounds);

    /**
     * Decryption is the same operation as encryption for XChaCha20.
     */
    public static native byte[] xchacha20Decrypt(byte[] data, byte[] key, byte[] iv, int rounds);

    // ── MT19937 (stateful) ────────────────────────────────────────────────

    /**
     * Create a new MT19937-64 RNG instance.
     * @return opaque handle for subsequent calls
     */
    public static native long mt19937Create(long seed);

    /** Return the next 64-bit random value from the generator. */
    public static native long mt19937Next(long handle);

    /** Fill and return {@code count} random bytes from the generator. */
    public static native byte[] mt19937GenerateBytes(long handle, int count);

    /** Destroy the generator and release native resources. */
    public static native void mt19937Destroy(long handle);
}
