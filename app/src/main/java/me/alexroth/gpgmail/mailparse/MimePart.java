package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Represents a single part of a MIME message.
 *
 * @author alex
 * @since 8/10/16
 */
public class MimePart {
    public String rawPartContent;
    public String partContent;
    public HashMap<String, String> headers;
    public String contentType;
    public HashMap<String, String> contentParams = new HashMap<>();

    public ArrayList<MimePart> subParts;

    public MimePart(String message) {
        if (message.substring(0, 2).contains("\n")) {
            contentType = "text/plain";
            partContent = message.split("\\r?\\n", 2)[1];
        } else {
            this.headers = HeaderSplitter.getMessageHeaders(message);
            if (headers.containsKey("content-type")) {
                String cType = headers.get("content-type");
                this.contentParams = MimeArgumentParser.getMimeArgument(cType);
                this.contentType = contentParams.get("content-type");
            } else {
                this.contentType = "text/plain";
            }
            this.partContent = HeaderSplitter.getMessageBody(message);
        }

        //Log.i("MimePart", "Found ctype: " + contentType);

        this.rawPartContent = message;

        if (contentType.contains("multipart/")) {
            subParts = new ArrayList<>();
            String boundary = this.contentParams.get("boundary").trim();
            String boundaryEscaped = Pattern.quote(boundary);
            //Log.i("MimePart", "Multipart detected, boundary: " + boundary);
            //TODO: error/bounds checking here!

            String restContent = partContent.split("\\r?\\n--" + boundaryEscaped + "\\r?\\n--", 2)[0];
            String[] parts = restContent.split("\\r?\\n--" + boundaryEscaped + "\\r?\\n");

            for (int i = 1; i < parts.length; i++) {
                String stringPart = parts[i];
                //Log.i("MimePart", " Adding new mime part.");
                subParts.add(new MimePart(stringPart));
            }


        } else {
            //We don't need to do any more processing here. User may choose to call methods to deocde contents, etc.
        }

    }

    public String getCRLFNormalizedRawText(){
        return this.rawPartContent.replaceAll("\\r?\\n", "\r\n");
    }
}
