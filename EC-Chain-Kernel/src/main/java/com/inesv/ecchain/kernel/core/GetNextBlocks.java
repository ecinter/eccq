package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.peer.Peer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;


class GetNextBlocks implements Callable<List<EcBlockImpl>> {

    /**
     * EcBlock identifier list
     */
    private final List<Long> blockIds;
    /**
     * Callable future
     */
    private Future<List<EcBlockImpl>> future;
    /**
     * Peer
     */
    private Peer peer;
    /**
     * Start index
     */
    private int start;

    /**
     * Stop index
     */
    private int stop;

    /**
     * Request count
     */
    private int requestCount;

    /**
     * EcTime it took to return getNextBlocks
     */
    private long responseTime;

    /**
     * Create the callable future
     *
     * @param blockIds EcBlock identifier list
     * @param start    Start index within the list
     * @param stop     Stop index within the list
     */
    public GetNextBlocks(List<Long> blockIds, int start, int stop) {
        this.blockIds = blockIds;
        this.start = start;
        this.stop = stop;
        this.requestCount = 0;
    }

    /**
     * Return the result
     *
     * @return List of blocks or null if an error occurred
     */
    @Override
    public List<EcBlockImpl> call() {
        requestCount++;
        //
        // Build the block request list
        //
        JSONArray idList = new JSONArray();
        for (int i = start + 1; i <= stop; i++) {
            idList.add(Long.toUnsignedString(blockIds.get(i)));
        }
        JSONObject request = new JSONObject();
        request.put("requestType", "getNextBlocks");
        request.put("blockIds", idList);
        request.put("blockId", Long.toUnsignedString(blockIds.get(start)));
        long startTime = System.currentTimeMillis();
        JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
        responseTime = System.currentTimeMillis() - startTime;
        if (response == null) {
            return null;
        }
        //
        // Get the list of blocks.  We will stop parsing blocks if we encounter
        // an invalid block.  We will return the valid blocks and reset the stop
        // index so no more blocks will be processed.
        //
        List<JSONObject> nextBlocks = (List<JSONObject>) response.get("nextBlocks");
        if (nextBlocks == null)
            return null;
        if (nextBlocks.size() > 36) {
            LoggerUtil.logDebug("Obsolete or rogue peer " + peer.getPeerHost() + " sends too many nextBlocks, blacklisting");
            peer.blacklist("Too many nextBlocks");
            return null;
        }
        List<EcBlockImpl> blockList = new ArrayList<>(nextBlocks.size());
        try {
            int count = stop - start;
            for (JSONObject blockData : nextBlocks) {
                blockList.add(EcBlockImpl.parseBlock(blockData));
                if (--count <= 0) {
                    break;
                }
            }
        } catch (RuntimeException | EcNotValidExceptionEc e) {
            LoggerUtil.logError("Failed to parse block: " + e.toString(), e);
            peer.blacklist(e);
            stop = start + blockList.size();
        }
        return blockList;
    }

    /**
     * Return the callable future
     *
     * @return Callable future
     */
    public Future<List<EcBlockImpl>> getFuture() {
        return future;
    }

    /**
     * Set the callable future
     *
     * @param future Callable future
     */
    public void setFuture(Future<List<EcBlockImpl>> future) {
        this.future = future;
    }

    /**
     * Return the peer
     *
     * @return Peer
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * Set the peer
     *
     * @param peer Peer
     */
    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    /**
     * Return the start index
     *
     * @return Start index
     */
    public int getStart() {
        return start;
    }

    /**
     * Set the start index
     *
     * @param start Start index
     */
    public void setStart(int start) {
        this.start = start;
    }

    /**
     * Return the stop index
     *
     * @return Stop index
     */
    public int getStop() {
        return stop;
    }

    /**
     * Return the request count
     *
     * @return Request count
     */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Return the response time
     *
     * @return Response time
     */
    public long getResponseTime() {
        return responseTime;
    }
}
