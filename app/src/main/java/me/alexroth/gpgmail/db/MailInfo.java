package me.alexroth.gpgmail.db;

/**
 * Holds all of the enums used by the DB.
 *
 * @author alex
 * @since 7/26/16
 */
public class MailInfo {

    /**
     * Order how messages can be retrieved from the DB.
     */
    public enum MailSortOrder{
        SORT_ORDER_RECENT,
    }

    /**
     * GPG encryption/signature status of a particular message.
     */
    public enum GpgStatus{
        GPG_STATUS_NONE,
        GPG_STATUS_CLEARSIGN_UNVERIFIED,
        GPG_STATUS_ENCRYPTED,
        GPG_STATUS_CLEARSIGN_VALID,
    }

    /**
     * The IMAP syncronization status of a particular message.
     */
    public enum SyncStatus {
        SYNC_STATUS_FULL,
        SYNC_STATUS_DELETED,
        SYNC_STATUS_FLAGSUPDATE,
        SYNC_STATUS_PURGED,
    }

}
