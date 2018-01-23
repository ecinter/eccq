package com.inesv.ecchain.kernel.core;

public final class Genesis {

    public static final long EC_GENESIS_BLOCK_ID = 8245992596443569158L;
    public static final long EC_CREATOR_ID = -1578547525259302553L;
    public static final byte[] EC_CREATOR_PUBLIC_KEY = {
            -122, -59, -26, -9, -81, -115, -44, 23, 82, -97, 19, -9, 73, 113, -41, -52, -97,
            110, 69, 10, 21, -122, 25, 98, -89, -60, -47, -105, -120, 10, -72, 109
    };

    //15250584400727881437
    public static final long[] EC_GENESIS_RECIPIENTS = {
            Long.parseUnsignedLong("15250584400727881437"),
    };


    public static final int[] EC_GENESIS_AMOUNTS = {
            70000000,
    };

    public static final byte[][] EC_GENESIS_SIGNATURES = {
            {-29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85},
    };

    public static final byte[] EC_GENESIS_BLOCK_SIGNATURE = new byte[]{
            105, -44, 38, -60, -104, -73, 10, -58, -47, 103, -127, -128, 53, 101, 39, -63, -2, -32, 48, -83, 115, 47, -65, 118, 114, -62, 38, 109, 22, 106, 76, 8, -49, -113, -34, -76, 82, 79, -47, -76, -106, -69, -54, -85, 3, -6, 110, 103, 118, 15, 109, -92, 82, 37, 20, 2, 36, -112, 21, 72, 108, 72, 114, 17
    };

}
