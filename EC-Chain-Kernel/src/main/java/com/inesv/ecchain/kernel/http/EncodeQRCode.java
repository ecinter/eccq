package com.inesv.ecchain.kernel.http;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.inesv.ecchain.common.core.EcException;
import com.inesv.ecchain.common.util.Convert;
import com.inesv.ecchain.common.util.LoggerUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public final class EncodeQRCode extends APIRequestHandler {

    static final EncodeQRCode instance = new EncodeQRCode();

    private EncodeQRCode() {
        super(new APITag[]{APITag.UTILS}, "qrCodeData", "width", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws EcException {

        JSONObject response = new JSONObject();

        String qrCodeData = Convert.nullToEmpty(request.getParameter("qrCodeData"));

        int width = ParameterParser.getInt(request, "width", 0, 5000, false);
        int height = ParameterParser.getInt(request, "height", 0, 5000, false);

        try {
            Map hints = new HashMap();
            // Error correction level: L (7%), M (15%), Q (25%), H (30%) -- Default L.
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0); // Default 4
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new MultiFormatWriter().encode(qrCodeData,
                    BarcodeFormat.QR_CODE,
                    width,
                    height,
                    hints
            );
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "jpeg", os);
            byte[] bytes = os.toByteArray();
            os.close();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            response.put("qrCodeBase64", base64);
        } catch (WriterException | IOException ex) {
            String errorMessage = "Error creating image from qrCodeData";
            LoggerUtil.logError(errorMessage, ex);
            JSONData.putException(response, ex, errorMessage);
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
