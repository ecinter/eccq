

package com.inesv.ecchain.common.crypto;

public enum HashFunction {


    SHA256((byte) 2) {
        @Override
        public byte[] hash(byte[] input) {
            return Crypto.sha256().digest(input);
        }
    },
    SHA3((byte) 3) {
        @Override
        public byte[] hash(byte[] input) {
            return Crypto.sha3().digest(input);
        }
    },
    SCRYPT((byte) 5) {
        @Override
        public byte[] hash(byte[] input) {
            return THREAD_LOCAL_SCRYPT.get().hash(input);
        }
    },
    Keccak25((byte) 25) {
        @Override
        public byte[] hash(byte[] input) {
            return EC.hash(input);
        }
    },
    RIPEMD160((byte) 6) {
        @Override
        public byte[] hash(byte[] input) {
            return Crypto.ripemd160().digest(input);
        }
    },
    RIPEMD160_SHA256((byte) 62) {
        @Override
        public byte[] hash(byte[] input) {
            return Crypto.ripemd160().digest(Crypto.sha256().digest(input));
        }
    };
    private final byte id;

    private static final ThreadLocal<Scrypt> THREAD_LOCAL_SCRYPT = new ThreadLocal<Scrypt>() {
        @Override
        protected Scrypt initialValue() {
            return new Scrypt();
        }
    };

    HashFunction(byte id) {
        this.id = id;
    }

    public static HashFunction getHashFunction(byte id) {
        for (HashFunction function : values()) {
            if (function.id == id) {
                return function;
            }
        }
        throw new IllegalArgumentException(String.format("illegal algorithm %d", id));
    }

    public byte getId() {
        return id;
    }

    public abstract byte[] hash(byte[] input);
}
