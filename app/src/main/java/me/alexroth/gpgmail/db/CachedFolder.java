package me.alexroth.gpgmail.db;

/**
 * Represents a folder on the IMAP server, and our local cache representation of it.
 *
 * @author Alex Roth
 */
public class CachedFolder {
    public long uid_validity;
    public long uid_next;
    public long max_uid;
    public String folder;
}
