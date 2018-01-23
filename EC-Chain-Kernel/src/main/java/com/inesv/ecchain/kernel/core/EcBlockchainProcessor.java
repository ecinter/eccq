package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Observable;
import com.inesv.ecchain.kernel.H2.DerivedH2Table;
import com.inesv.ecchain.kernel.peer.Peer;
import org.json.simple.JSONObject;

import java.util.List;

public interface EcBlockchainProcessor extends Observable<EcBlock, EcBlockchainProcessorEvent> {

    Peer getLastECBlockchainFeeder();

    int getLastECBlockchainFeederHeight();

    boolean isScanning();

    boolean isDownloading();

    boolean isProcessingBlock();

    int getMinRollbackHeight();

    int getInitialScanHeight();

    void processPeerBlock(JSONObject request) throws EcException;

    void fullReset();

    void scan(int height, boolean validate);

    void fullScanWithShutdown();

    void setGetMoreBlocks(boolean getMoreBlocks);

    List<? extends EcBlock> popOffTo(int height);

    void registerDerivedTable(DerivedH2Table table);

    void trimDerivedTables();

    int restorePrunedData();

    Transaction restorePrunedTransaction(long transactionId);

}
