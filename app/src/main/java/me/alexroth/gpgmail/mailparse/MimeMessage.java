package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 
 *
 * @author Alex Roth
 */
public class MimeMessage {
    public String messageContent;
    public HashMap<String, String> headers;
    public String contentType;
    public HashMap<String, String> contentParams = new HashMap<>();


    public MimeMessage(String message) {
        this.messageContent = HeaderSplitter.getMessageBody(message);
        this.headers = HeaderSplitter.getMessageHeaders(message);
        if (headers.containsKey("mime-version") && headers.get("mime-version").equals("1.0")) {
            String cType = headers.get("content-type");
            this.contentParams = MimeArgumentParser.getMimeArgument(cType);
            this.contentType = contentParams.get("content-type");
        } else {
            throw new NotMimeException(headers.get("MIME-Version"));
        }

        // For single-part messages here we need to perform decoding to get it into byte[] or String form which is displayable.
        // For now, we just parse a multipart message to get the signed part and the signature, and use those.
    }
}
