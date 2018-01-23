package com.inesv.ecchain.kernel.core;

import com.inesv.ecchain.common.core.EcNotValidExceptionEc;


public interface Builder {

    Builder recipientId(long recipientId);

    Builder referencedTransactionFullHash(String referencedTransactionFullHash);

    Builder appendix(Message message);

    Builder appendix(Enclosure.EncryptedMessage encryptedMessage);

    Builder appendix(Enclosure.EncryptToSelfMessage encryptToSelfMessage);

    Builder appendix(PublicKeyAnnouncement publicKeyAnnouncement);

    Builder appendix(PrunablePlainMessage prunablePlainMessage);

    Builder appendix(Enclosure.PrunableEncryptedMessage prunableEncryptedMessage);

    Builder appendix(Phasing phasing);

    Builder timestamp(int timestamp);

    Builder ecBlockHeight(int height);

    Builder ecBlockId(long blockId);

    Transaction build() throws EcNotValidExceptionEc;

    Transaction build(String secretPhrase) throws EcNotValidExceptionEc;

}
