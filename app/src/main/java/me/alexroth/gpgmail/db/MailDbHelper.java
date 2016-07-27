package me.alexroth.gpgmail.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * CLASS DESCRIPTION
 *
 * @author alex
 * @since 7/26/16
 */
public class MailDbHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Mail.db";

    private static final String TEXT_TYPE = " TEXT";
    private static final String LONG_TYPE = " UNSIGNED BIG INT";
    private static final String SEP = ",";
    private static final String SQL_CREATE_MAIL_TABLE =
            "CREATE TABLE" + MailContract.MailEntry.TABLE_NAME + " ("+
                    MailContract.MailEntry._ID + " INTEGER PRIMARY KEY,"+
                    MailContract.MailEntry.MAIL_DATE_RECEIVED + LONG_TYPE + SEP +
                    MailContract.MailEntry.MAIL_FROM_EMAIL + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_FROM_NAME + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_SHORT + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_SUBJECT + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_FOLDER + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_GPG_STATUS + " INTEGER" + SEP +
                    MailContract.MailEntry.MAIL_UID + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_FLAGS + TEXT_TYPE + SEP +
                    MailContract.MailEntry.MAIL_SYNCED + " INTEGER" + SEP +
                    " )";
    private static final String SQL_CREATE_STATUS_TABLE =
            "CREATE TABLE" + MailContract.MailStatus.TABLE_NAME + " ("+
                    MailContract.MailStatus._ID + " INTEGER PRIMARY KEY,"+
                    MailContract.MailStatus.MAIL_UIDVALIDITY + LONG_TYPE + SEP +
                    MailContract.MailStatus.MAIL_FOLDER + TEXT_TYPE + SEP +
                    " )";


    private static final String SQL_DELETE_MAIL_ENTRIES = "DROP TABLE IF EXISTS "+ MailContract.MailEntry.TABLE_NAME;
    private static final String SQL_DELETE_STATUS_ENTRIES = "DROP TABLE IF EXISTS "+ MailContract.MailStatus.TABLE_NAME;

    public MailDbHelper(Context context){
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(SQL_CREATE_MAIL_TABLE);
        sqLiteDatabase.execSQL(SQL_CREATE_STATUS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL(SQL_DELETE_MAIL_ENTRIES);
        sqLiteDatabase.execSQL(SQL_DELETE_STATUS_ENTRIES);
        onCreate(sqLiteDatabase);
    }
}
