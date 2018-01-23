package com.inesv.ecchain.kernel.peer;

public enum PeerService {
    HALLMARK(1),                    // Hallmarked node
    PRUNABLE(2),                    // Stores expired prunable messages
    API(4),                         // Provides open API access over http
    API_SSL(8),                     // Provides open API access over https
    CORS(16);                       // API CORS enabled

    private final long code;        // Service code - must be a power of 2

    PeerService(int code) {
        this.code = code;
    }

    public long getCode() {
        return code;
    }
}
