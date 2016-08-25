package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
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
    public HashMap<String, MimePart> subPartsByContentId;

    public MimePart(String message) {
        this.rawPartContent = message;
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

        if (contentType.contains("multipart/")) {
            subParts = new ArrayList<>();
            subPartsByContentId = new HashMap<>();
            String boundary = this.contentParams.get("boundary").trim();
            String boundaryEscaped = boundary.replaceAll("([^a-zA-Z0-9])", "\\\\$1");
            //TODO: error/bounds checking here!
            String regex = "\\r?\\n?--" + boundaryEscaped + "--\\r?\\n?";
            Log.e("MimePart", "Using the escape boundary: " + regex);
            Matcher m = Pattern.compile(regex).matcher(partContent);

            Log.e("MimePart", "Found? " + m.find());

            String[] bottomSplit = partContent.split(regex, 2);
            String restContent =  bottomSplit[0];
            Log.e("MimePart", "After escape, boundary: " + bottomSplit.length + " bottom: " + restContent.substring(0,20));
            String boundaryRegex = "\\r?\\n?--" + boundaryEscaped + "\\r?\\n";
            Log.e("MimePart", "boundRegex: " + boundaryRegex);
            String[] parts = restContent.split(boundaryRegex);

            Log.i("MimePart", "total parts: " + parts.length);
            for (int i = 1; i < parts.length; i++) {
                String stringPart = parts[i];
                Log.e("MimePart", "thisPart: " + stringPart.contains("This is a"));
                if (stringPart.length() > 2) {
                    MimePart p = new MimePart(stringPart);
                    if (p.headers.containsKey("content-id")) {
                        String contentId = p.headers.get("content-id");
                        contentId = contentId.substring(1, contentId.length() - 1);
                        Log.i("MimePart", " Has content ID: " + contentId);
                        subPartsByContentId.put(contentId, p);
                    }
                    subParts.add(p);
                }
            }


        } else {
            //We don't need to do any more processing here. User may choose to call methods to deocde contents, etc.
        }

    }

    public MimePart findMimePartForContentId(String contentId) {
        //Are *we* the part?
        if (subParts == null) {
            String ourContentId = headers.get("content-id");
            ourContentId = ourContentId.substring(1, ourContentId.length() - 1);
            if (contentId.equalsIgnoreCase(ourContentId)) {
                return this;
            } else {
                return null;
            }
        }
        //Is one of our children that we know of the part?
        if (subPartsByContentId != null && subPartsByContentId.containsKey(contentId)) {
            return subPartsByContentId.get(contentId);
        }

        //I guess it's a subchild then.
        for (MimePart subPart : subParts) {
            MimePart foundPart = subPart.findMimePartForContentId(contentId);
            //A child at some point has it.
            if (foundPart != null) {
                return foundPart;
            }
        }
        //We don't have it.
        return null;
    }

    public String getQuotedPrintableText() {
        String[] splitString = this.partContent.split("=[ \\t]*\\r?\\n");
        //So splitString has split on all  all of the soft line breaks
        StringBuilder builder = new StringBuilder();
        for (String s : splitString) {
            builder.append(s);
        }
        String unbrokenBody = builder.toString();


        //We've dealt with all of the soft-line-breaks.

        //We now need to find all of the not-printable cha

        Pattern equalsOperator = Pattern.compile("=([0-9A-F][0-9A-F])");

        Matcher equalsMatcher = equalsOperator.matcher(unbrokenBody);

        StringBuffer sb = new StringBuffer();
        String charsetName = "UTF-8";

        if (contentParams.containsKey("charset")) {
            charsetName = contentParams.get("charset");
        }
        Log.e("MimePart", "Found an alternative charset: " + charsetName);
        Charset charsetToUse = Charset.forName(charsetName);

        while (equalsMatcher.find()) {
            String hexCharValue = equalsMatcher.group(1);
            int intValue = Integer.valueOf(hexCharValue, 16);

            byte[] thisCharacterByte = new byte[1];
            thisCharacterByte[0] = (byte) intValue;


            String characterRealValue = new String(thisCharacterByte, charsetToUse);

            equalsMatcher.appendReplacement(sb, characterRealValue);
        }
        equalsMatcher.appendTail(sb);

        return sb.toString();
    }

    public String getCRLFNormalizedRawText() {
        return this.rawPartContent.replaceAll("\\r?\\n", "\r\n");
    }
}
