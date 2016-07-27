package me.alexroth.gpgmail.db;

import android.provider.BaseColumns;

/**
 * Defines the SQLite schema used to store the cleartext portions of the email.
 *
 * @author alex
 * @since 7/26/16
 */
public final class MailContract {


    public static final int GPGSTATUS_NONE = 0;
    public static final int GPGSTATUS_CLEARSIGN = 1;
    public static final int GPGSTATUS_ENCRYPTED = 2;
    public static final int GPGSTATUS_SIGNED_VALID = 3;

    public MailContract() {};

    public static abstract class MailEntry implements BaseColumns{
        public static final String TABLE_NAME = "mail";
        public static final String MAIL_DATE_RECEIVED = "date_received";
        public static final String MAIL_SUBJECT = "subject";
        public static final String MAIL_FROM_EMAIL = "from_email";
        public static final String MAIL_FROM_NAME = "from_name";
        public static final String MAIL_SHORT = "description";
        public static final String MAIL_FOLDER = "folder";
        public static final String MAIL_GPG_STATUS = "gpg_status";
        public static final String MAIL_UID = "uid";
        public static final String MAIL_FLAGS = "flags";
        public static final String MAIL_SYNCED = "sync";
    }

    public static abstract class MailStatus implements BaseColumns{
        public static final String TABLE_NAME = "status";
        public static final String MAIL_UIDVALIDITY = "uid_validity";
        public static final String MAIL_UIDNEXT = "uid_next";
        public static final String MAIL_FOLDER = "folder";
    }
}
