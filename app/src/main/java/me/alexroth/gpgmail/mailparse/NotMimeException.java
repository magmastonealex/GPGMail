package me.alexroth.gpgmail.mailparse;

/**
 * CLASS DESCRIPTION!
 *
 * @author Alex Roth
 */
public class NotMimeException extends RuntimeException {
    public NotMimeException(){
        this("");
    }
    public NotMimeException(String s){
        super("Message does not comply with RFC2045. Detail: " + s);
    }
}
