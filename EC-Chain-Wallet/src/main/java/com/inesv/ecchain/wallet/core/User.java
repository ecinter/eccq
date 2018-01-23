package com.inesv.ecchain.wallet.core;


import com.inesv.ecchain.common.crypto.Crypto;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.LoggerUtil;
import com.inesv.ecchain.kernel.core.FoundryMachine;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ConcurrentLinkedQueue;

final class User {

    private volatile String secretECPhrase;
    private volatile byte[] publicECKey;
    private volatile boolean isInactive;
    private final String ecUserId;
    private AsyncContext asyncContext;
    private final ConcurrentLinkedQueue<JSONStreamAware> concurrentLinkedQueue = new ConcurrentLinkedQueue<>();

    User(String ecUserId) {
        this.ecUserId = ecUserId;
    }

    String getEcUserId() {
        return this.ecUserId;
    }

    byte[] getPublicECKey() {
        return publicECKey;
    }

    String getSecretECPhrase() {
        return secretECPhrase;
    }

    boolean isInactive() {
        return isInactive;
    }

    void setInactive(boolean inactive) {
        this.isInactive = inactive;
    }

    void enqueue(JSONStreamAware response) {
        concurrentLinkedQueue.offer(response);
    }

    void lockAccount() {
        FoundryMachine.stopForging(secretECPhrase);
        secretECPhrase = null;
    }

    long unlockAccount(String secretPhrase) {
        this.publicECKey = Crypto.getPublicKey(secretPhrase);
        this.secretECPhrase = secretPhrase;
        return FoundryMachine.startForging(secretPhrase).getAccountId();
    }

    synchronized void processPendingResponses(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JSONArray responses = new JSONArray();
        JSONStreamAware pendingResponse;
        while ((pendingResponse = concurrentLinkedQueue.poll()) != null) {
            responses.add(pendingResponse);
        }
        if (responses.size() > 0) {
            JSONObject combinedResponse = new JSONObject();
            combinedResponse.put("responses", responses);
            if (asyncContext != null) {
                asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                try (Writer writer = asyncContext.getResponse().getWriter()) {
                    combinedResponse.writeJSONString(writer);
                }
                asyncContext.complete();
                asyncContext = req.startAsync();
                asyncContext.addListener(new UserAsyncListener());
                asyncContext.setTimeout(5000);
            } else {
                resp.setContentType("text/plain; charset=UTF-8");
                try (Writer writer = resp.getWriter()) {
                    combinedResponse.writeJSONString(writer);
                }
            }
        } else {
            if (asyncContext != null) {
                asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                try (Writer writer = asyncContext.getResponse().getWriter()) {
                    JSON.EMPTY_JSON.writeJSONString(writer);
                }
                asyncContext.complete();
            }
            asyncContext = req.startAsync();
            asyncContext.addListener(new UserAsyncListener());
            asyncContext.setTimeout(5000);
        }
    }

    synchronized void send(JSONStreamAware response) {
        if (asyncContext == null) {

            if (isInactive) {
                // user not seen recently, no responses should be collected
                return;
            }
            if (concurrentLinkedQueue.size() > 1000) {
                concurrentLinkedQueue.clear();
                // stop collecting responses for this user
                isInactive = true;
                if (secretECPhrase == null) {
                    // but only completely remove users that don't have unlocked accounts
                    Users.remove(this);
                }
                return;
            }

            concurrentLinkedQueue.offer(response);

        } else {

            JSONArray responses = new JSONArray();
            JSONStreamAware pendingResponse;
            while ((pendingResponse = concurrentLinkedQueue.poll()) != null) {

                responses.add(pendingResponse);

            }
            responses.add(response);

            JSONObject combinedResponse = new JSONObject();
            combinedResponse.put("responses", responses);

            asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

            try (Writer writer = asyncContext.getResponse().getWriter()) {
                combinedResponse.writeJSONString(writer);
            } catch (IOException e) {
                LoggerUtil.logError("Error sending response to user", e);
            }

            asyncContext.complete();
            asyncContext = null;

        }

    }

    private final class UserAsyncListener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent asyncEvent) throws IOException {
        }

        @Override
        public void onError(AsyncEvent asyncEvent) throws IOException {

            synchronized (User.this) {
                asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

                try (Writer writer = asyncContext.getResponse().getWriter()) {
                    JSON.EMPTY_JSON.writeJSONString(writer);
                }

                asyncContext.complete();
                asyncContext = null;
            }

        }

        @Override
        public void onStartAsync(AsyncEvent asyncEvent) throws IOException {
        }

        @Override
        public void onTimeout(AsyncEvent asyncEvent) throws IOException {

            synchronized (User.this) {
                asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");

                try (Writer writer = asyncContext.getResponse().getWriter()) {
                    JSON.EMPTY_JSON.writeJSONString(writer);
                }

                asyncContext.complete();
                asyncContext = null;
            }

        }

    }

}
