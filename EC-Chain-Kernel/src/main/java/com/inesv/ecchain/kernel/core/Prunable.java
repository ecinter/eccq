package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.util.EcTime;

public interface Prunable {
    byte[] getHash();

    boolean hasPrunableData();

    void restorePrunableData(Transaction transaction, int blockTimestamp, int height);

    default boolean shouldLoadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
        return new EcTime.EpochEcTime().getTime() - transaction.getTimestamp() <
                (includeExpiredPrunable && Constants.INCLUDE_EXPIRED_PRUNABLE ?
                        Constants.EC_MAX_PRUNABLE_LIFETIME : Constants.EC_MIN_PRUNABLE_LIFETIME);
    }
}
