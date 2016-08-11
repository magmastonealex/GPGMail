package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents a complete top-level MIME message.
 * Note that it does no multipart processing, it just lets you verify that this message is a MIME message, and looks up the content type for you.
 * Pass the same text to a MimePart in order to do further processing.
 *
 * It also exposes common fields for you to use - subject, from, to, etc, unlike MimePart.
 *
 * @author Alex Roth
 */
public class MimeMessage {
    public String messageContent;
    public HashMap<String, String> headers;
    public String contentType;
    public HashMap<String, String> contentParams = new HashMap<>();

    public String rawMessageContent;

    public MimeMessage(String message) {
        this.rawMessageContent = message;
        this.messageContent = HeaderSplitter.getMessageBody(message);
        this.headers = HeaderSplitter.getMessageHeaders(message);
        if (headers.containsKey("mime-version") && headers.get("mime-version").equals("1.0")) {
            String cType = headers.get("content-type");
            this.contentParams = MimeArgumentParser.getMimeArgument(cType);
            this.contentType = contentParams.get("content-type");
        } else {
            throw new NotMimeException(headers.get("MIME-Version"));
        }
    }

    public MimePart recreateAsMimePart(){
       return new MimePart(this.rawMessageContent);
    }
}
