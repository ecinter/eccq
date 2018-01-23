

package com.inesv.ecchain.common.util;

public interface EcTime {

    int getTime();

    final class EpochEcTime implements EcTime {

        @Override
        public int getTime() {
            return Convert.toepochtime(System.currentTimeMillis());
        }

    }
}
