package me.alexroth.gpgmail.mailparse;

import java.util.HashMap;

/**
 * CLASS DESCRIPTION
 *
 * @author alex
 * @since 8/9/16
 */
public final class MimeArgumentParser {

    public static HashMap<String,String> getMimeArgument(String field){
        HashMap<String,String> contentParams = new HashMap<>();
        String[] splitContentParams = field.split(";");
        for (int i = 0; i < splitContentParams.length; i++) {
            String part = splitContentParams[i].trim();
            if (i == 0) {
                contentParams.put("content-type",part);
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
                contentParams.put(key, value);
            }
        }
        return contentParams;
    }
}
