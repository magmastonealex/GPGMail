package me.alexroth.gpgmail.db;

/**
 * A short-form message as would be displayed on the main email view.
 *
 * @author alex
 * @since 7/26/16
 */
public class CompactMessage {
    public long dateReceived;
    public String subject;
    public String fromEmail;
    public String fromName;
    public String shortDescription;
    public String folder;
    public long uid;
    public MailInfo.GpgStatus gpgStatus;
    public String[] flags;
    public MailInfo.SyncStatus syncStatus;
}
