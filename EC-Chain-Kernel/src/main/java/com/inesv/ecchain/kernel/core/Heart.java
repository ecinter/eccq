package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.kernel.H2.H2Iterator;
import com.inesv.ecchain.kernel.H2.H2Key;
import com.inesv.ecchain.kernel.H2.H2KeyLongKeyFactory;
import com.inesv.ecchain.kernel.H2.VersionedEntityH2Table;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Heart {

    private static final H2KeyLongKeyFactory<Heart> HUB_DB_KEY_FACTORY = null;
    private static final VersionedEntityH2Table<Heart> HUB_TABLE = null;
    private static long lastBlockId;
    private static List<Hit> lastHits;
    private final long accountId;
    private final H2Key h2Key;
    private final long minFeePerByteNQT;
    private final List<String> uris;


    private Heart(Transaction transaction, Mortgaged.MessagingHubAnnouncement attachment) {
        this.accountId = transaction.getSenderId();
        this.h2Key = HUB_DB_KEY_FACTORY.newKey(this.accountId);
        this.minFeePerByteNQT = attachment.getMinFeePerByteNQT();
        this.uris = Collections.unmodifiableList(Arrays.asList(attachment.getUris()));
    }

    static void addOrUpdateHub(Transaction transaction, Mortgaged.MessagingHubAnnouncement attachment) {
        HUB_TABLE.insert(new Heart(transaction, attachment));
    }

    public static List<Hit> getHubHits(EcBlock ecBlock) {

        synchronized (Heart.class) {
            if (ecBlock.getECId() == lastBlockId && lastHits != null) {
                return lastHits;
            }
            List<Hit> currentHits = new ArrayList<>();
            long currentLastBlockId;

            EcBlockchainImpl.getInstance().readECLock();
            try {
                currentLastBlockId = EcBlockchainImpl.getInstance().getLastECBlock().getECId();
                if (currentLastBlockId != ecBlock.getECId()) {
                    return Collections.emptyList();
                }
                try (H2Iterator<Heart> hubs = HUB_TABLE.getAll(0, -1)) {
                    while (hubs.hasNext()) {
                        Heart heart = hubs.next();
                        Account account = Account.getAccount(heart.getAccountId());
                        if (account != null) {
                            long effectiveBalance = account.getEffectiveBalanceEC(ecBlock.getHeight());
                            if (effectiveBalance >= Constants.EC_MIN_HUB_EFFECTIVE_BALANCE) {
                                currentHits.add(new Hit(heart, FoundryMachine.getHitTime(BigInteger.valueOf(effectiveBalance),
                                        FoundryMachine.getHit(Account.getPublicKey(heart.getAccountId()), ecBlock), ecBlock)));
                            }
                        }
                    }
                }
            } finally {
                EcBlockchainImpl.getInstance().readECUnlock();
            }

            Collections.sort(currentHits);
            lastHits = currentHits;
            lastBlockId = currentLastBlockId;
        }
        return lastHits;

    }

    public static void start() {
    }

    public long getAccountId() {
        return accountId;
    }

    public long getMinFeePerByteNQT() {
        return minFeePerByteNQT;
    }

    public List<String> getUris() {
        return uris;
    }

    public static class Hit implements Comparable<Hit> {

        public final Heart heart;
        public final long hitTime;

        private Hit(Heart heart, long hitTime) {
            this.heart = heart;
            this.hitTime = hitTime;
        }

        @Override
        public int compareTo(Hit hit) {
            if (this.hitTime < hit.hitTime) {
                return -1;
            } else if (this.hitTime > hit.hitTime) {
                return 1;
            } else {
                return Long.compare(this.heart.accountId, hit.heart.accountId);
            }
        }

    }

}
