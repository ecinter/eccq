package com.inesv.ecchain.kernel.http;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;



public final class DecipherQRCode extends APIRequestHandler {

    static final DecipherQRCode instance = new DecipherQRCode();

    private DecipherQRCode() {
        super(new APITag[]{APITag.UTILS}, "qrCodeBase64");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws EcException {

        String qrCodeBase64 = Convert.nullToEmpty(request.getParameter("qrCodeBase64"));

        JSONObject response = new JSONObject();
        try {
            BinaryBitmap binaryBitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(
                            ImageIO.read(new ByteArrayInputStream(
                                    Base64.getDecoder().decode(qrCodeBase64)
                            ))
                    ))
            );

            Map hints = new HashMap();
            hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

            Result qrCodeData = new MultiFormatReader().decode(binaryBitmap, hints);
            response.put("qrCodeData", qrCodeData.getText());
        } catch (IOException ex) {
            String errorMessage = "Error reading base64 byte stream";
            LoggerUtil.logError(errorMessage, ex);
            JSONData.putException(response, ex, errorMessage);
        } catch (NullPointerException ex) {
            String errorMessage = "Invalid base64 image";
            LoggerUtil.logError(errorMessage, ex);
            JSONData.putException(response, ex, errorMessage);
        } catch (NotFoundException ex) {
            response.put("qrCodeData", "");
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
