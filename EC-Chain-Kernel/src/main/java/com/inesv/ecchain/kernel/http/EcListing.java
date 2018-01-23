package com.inesv.ecchain.kernel.http;


import com.inesv.ecchain.common.core.Constants;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.JSON;
import com.inesv.ecchain.common.util.Search;
import com.inesv.ecchain.kernel.core.Account;
import com.inesv.ecchain.kernel.core.Mortgaged;
import com.inesv.ecchain.kernel.core.PrunablePlainMessage;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static com.inesv.ecchain.kernel.http.JSONResponses.*;


public final class EcListing extends CreateTransaction {

    static final EcListing instance = new EcListing();
    private static final JSONStreamAware EC_MESSAGE_NOT_BINARY;
    private static final JSONStreamAware EC_MESSAGE_NOT_IMAGE;

    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Only binary message attachments accepted as DGS listing images");
        EC_MESSAGE_NOT_BINARY = JSON.prepare(response);
        response.clear();
        response.put("errorCode", 9);
        response.put("errorDescription", "Message attachment is not an image");
        EC_MESSAGE_NOT_IMAGE = JSON.prepare(response);
    }


    private EcListing() {
        super("messageFile", new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION},
                "name", "description", "tags", "quantity", "priceNQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws EcException {

        String name = Convert.emptyToNull(req.getParameter("name"));
        String description = Convert.nullToEmpty(req.getParameter("description"));
        String tags = Convert.nullToEmpty(req.getParameter("tags"));
        long priceNQT = ParameterParser.getPriceNQT(req);
        int quantity = ParameterParser.getGoodsQuantity(req);

        if (name == null) {
            return MISSING_NAME;
        }
        name = name.trim();
        if (name.length() > Constants.EC_MAX_DGS_LISTING_NAME_LENGTH) {
            return INCORRECT_DGS_LISTING_NAME;
        }

        if (description.length() > Constants.EC_MAX_DGS_LISTING_DESCRIPTION_LENGTH) {
            return INCORRECT_DGS_LISTING_DESCRIPTION;
        }

        if (tags.length() > Constants.EC_MAX_DGS_LISTING_TAGS_LENGTH) {
            return INCORRECT_DGS_LISTING_TAGS;
        }

        PrunablePlainMessage prunablePlainMessage = (PrunablePlainMessage) ParameterParser.getPlainMessage(req, true);
        if (prunablePlainMessage != null) {
            if (prunablePlainMessage.isText()) {
                return EC_MESSAGE_NOT_BINARY;
            }
            byte[] image = prunablePlainMessage.getMessage();
            String mediaType = Search.checkMimeType(image);
            if (mediaType == null || !mediaType.startsWith("image/")) {
                return EC_MESSAGE_NOT_IMAGE;
            }
        }

        Account account = ParameterParser.getSenderAccount(req);
        Mortgaged mortgaged = new Mortgaged.DigitalGoodsListing(name, description, tags, quantity, priceNQT);
        return createTransaction(req, account, mortgaged);

    }

}
