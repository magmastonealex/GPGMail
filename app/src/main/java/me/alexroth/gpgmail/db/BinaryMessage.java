package me.alexroth.gpgmail.db;

/**
 * Holds the binary message blob that the IMAP server returns, which can be parsed by MessageParser.
 *
 * @author Alex Roth
 */
public class BinaryMessage {
    public byte[] message;
    public String folder;
    public long uid;
}
