package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses headers and splits body according to RFC822.
 *
 * @author Alex Roth
 */
public final class HeaderSplitter {

    /**
     * Gets the message body. No MIME/RFC2045 parsing is done here, just simple removal of RFC822-style headers.
     *
     * @param messageContents The entire message as a string.
     * @return The String message body.
     */
    public static String getMessageBody(String messageContents) {
        String[] strings = messageContents.split("\\r?\\n\\r?\\n");
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < strings.length; i++) {
            builder.append(strings[i]);
            if (i != strings.length - 1) {
                builder.append("\r\n\r\n");
            }
        }
        return builder.toString();
    }

    /**
     * Gets the key-value header data, parsing headers according to RFC822, stripping comments on the way.
     *
     * @param messageContents the full message
     * @return A HashMap of the processed headers.
     */
    public static HashMap<String, String> getMessageHeaders(String messageContents) {

        HashMap<String, String> headerMap = new HashMap<>();

        //Note this less-strict version - it's alright if lines are terminated by an \n, but if they aren't we deal with that too.
        String[] strings = messageContents.split("\\r?\\n\\r?\\n");
        String headers = strings[0];
        //Step 1: Unfold each header.
        headers = headers.replaceAll("\\r?\\n[ \\t]+", " ");
        //Step 2: Split.
        String[] everyHeader = headers.split("\\r?\\n");
        for (String header : everyHeader) {
            String[] headerSplit = header.split(":");

            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < headerSplit.length; i++) {
                builder.append(headerSplit[i]);
                if (i != headerSplit.length - 1) {
                    builder.append(":");
                }
            }
            String fieldName = headerSplit[0].toLowerCase();
            //Step 3: remove comments.
            String field = builder.toString();

            //TODO: this works for the most basic interpretation of RFC822 only - we need to deal with nested comments, which Regex doesn't really do.

            Pattern p = Pattern.compile("\\((?:\\\\\\)|.)*?\\)");
            field = p.matcher(field).replaceAll("");
            field = field.trim();

            headerMap.put(fieldName, field);

        }
        return headerMap;
    }

}
