package com.inesv.ecchain.kernel.core;


import com.inesv.ecchain.common.core.EcNotValidExceptionEc;
import com.inesv.ecchain.common.core.EcValidationException;
import com.inesv.ecchain.common.util.Filter;
import org.json.simple.JSONObject;

import java.util.List;

public interface Transaction {

    long getTransactionId();

    String getStringId();

    long getSenderId();

    byte[] getSenderPublicKey();

    long getRecipientId();

    int getTransactionHeight();

    long getBlockId();

    EcBlock getBlock();

    short getTransactionIndex();

    int getTimestamp();

    int getBlockTimestamp();

    short getDeadline();

    int getExpiration();

    long getAmountNQT();

    long getFeeNQT();

    String getReferencedTransactionFullHash();

    byte[] getSignature();

    String getFullHash();

    TransactionType getTransactionType();

    Mortgaged getAttachment();

    boolean verifySignature();

    void validate() throws EcValidationException;

    byte[] getBytes();

    byte[] getUnsignedBytes();

    JSONObject getJSONObject();

    JSONObject getPrunableAttachmentJSON();

    byte getVersion();

    int getFullSize();

    Message getMessage();

    Enclosure.EncryptedMessage getEncryptedMessage();

    Enclosure.EncryptToSelfMessage getEncryptToSelfMessage();

    Phasing getPhasing();

    PrunablePlainMessage getPrunablePlainMessage();

    Enclosure.PrunableEncryptedMessage getPrunableEncryptedMessage();

    List<? extends Enclosure> getAppendages();

    List<? extends Enclosure> getAppendages(boolean includeExpiredPrunable);

    List<? extends Enclosure> getAppendages(Filter<Enclosure> filter, boolean includeExpiredPrunable);

    int getECBlockHeight();

    long getECBlockId();

}
