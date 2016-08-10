package me.alexroth.gpgmail.mailparse;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * CLASS DESCRIPTION!
 *
 * @author Alex Roth
 */
public class MimePart {
    public String messageContent;
    public HashMap<String, String> headers;
    public String contentType;
    public HashMap<String, String> contentParams = new HashMap<>();

    public MimePart(String message) {
        messageContent = HeaderSplitter.getMessageBody(message);
        headers = HeaderSplitter.getMessageHeaders(message);
        if (headers.containsKey("mime-version") && headers.get("mime-version").equals("1.0")) {
            String cType = headers.get("content-type");
            String[] contentParams = cType.split(";");
            for (int i = 0; i < contentParams.length; i++) {
                String part = contentParams[i].trim();
                if (i == 0) {
                    this.contentType = part;
                } else if (i > 0) {
                    String[] bits = part.split("[ ]*=[ ]*");
                    String key = bits[0].trim().toLowerCase(); //Guaranteeed that it's at least one non-whitespace character.
                    //There may have been other =s.

                    StringBuilder builder = new StringBuilder();

                    for(int z=1; z < bits.length; z++){
                        builder.append(bits[z]);
                        if(z != bits.length-1){
                            builder.append("=");
                        }
                    }

                    String value = builder.toString();
                    //So, value may or may not be quoted.
                    //If it *is* quoted, let's strip off the quotes, then postprocess to clear tspecials.
                    if (value.charAt(0) == '"') {
                        value = value.substring(1, value.length() - 1);

                        //( ) < > @ , ; : \ / [ ] ? = "
                        value = value.replace("\\\\", "\\").replace("\\(", "(").replace("\\)", ")").replace("\\<", "<").replace("\\>", ">").replace("\\@", "@").replace("\\,", ",").replace("\\;", ";").replace("\\:", ":").replace("\\/", "/").replace("\\[", "[").replace("\\]", "]").replace("\\?", "?").replace("\\=", "=").replace("\\\"", "\"");
                    }
                    //If the first char after trimming isn't ", then it's unquoted. Note that RFC2045 specifically states that tspecials are only allowed in quoted-string, AKA, can't just unescape.
                    this.contentParams.put(key, value);
                }
            }
        } else {
            throw new NotMimeException(headers.get("MIME-Version"));
        }
    }
}
