package me.alexroth.gpgmail.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Acts as a wrapper around the DB so that consumers don't need to be concerned with how the DB handles everything.
 *
 * @author alex
 * @since 7/26/16
 */
public class MailHandler {
    private MailDbHelper helper;


    public MailHandler(Context c) {
        helper = new MailDbHelper(c);
    }

    /**
     * Add a single message to the database.
     *
     * @param m The message to add.
     */
    public void addMessage(CompactMessage m) {
        addMessage(m, helper.getWritableDatabase());
    }

    /**
     * Add a single message to the given database. For many message adds, this is more performant.
     *
     * @param m  The message to insert
     * @param db The DB (possible in a transaction state) to insert using.
     */
    public void addMessage(CompactMessage m, SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MailContract.MailEntry.MAIL_DATE_RECEIVED, m.dateReceived);
        values.put(MailContract.MailEntry.MAIL_SUBJECT, m.subject);
        values.put(MailContract.MailEntry.MAIL_FROM_EMAIL, m.fromEmail);
        values.put(MailContract.MailEntry.MAIL_FROM_NAME, m.fromName);
        values.put(MailContract.MailEntry.MAIL_SHORT, m.shortDescription);
        values.put(MailContract.MailEntry.MAIL_FOLDER, m.folder);
        values.put(MailContract.MailEntry.MAIL_GPG_STATUS, m.gpgStatus.toString());
        values.put(MailContract.MailEntry.MAIL_UID, m.uid);
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String s : m.flags) {
            if (!first) {
                builder.append(" ");
            } else {
                first = false;
            }
            builder.append(s);
        }
        values.put(MailContract.MailEntry.MAIL_FLAGS, builder.toString());
        values.put(MailContract.MailEntry.MAIL_SYNCED, m.syncStatus.toString());

        db.insert(MailContract.MailEntry.TABLE_NAME, null, values);
    }

    /**
     * Add an array of messages to the DB in a single exclusive transaction.
     */
    public void addMessagesAsTransaction(CompactMessage[] messages) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        for (CompactMessage message : messages) {
            addMessage(message, db);
        }
        db.endTransaction();
    }

    /**
     * Add a list of messages to the DB in a single exclusive transaction.
     */
    public void addMessagesAsTransaction(List<CompactMessage> messages) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        for (CompactMessage message : messages) {
            addMessage(message, db);
        }
        db.endTransaction();
    }

    /**
     * Get a message cursor to iterate over the rows in the DB for a particular sort order.
     *
     * @param order The order to use
     * @return A MessageCursor which is open and can be used to get the data out.
     */
    public MessageCursor getMessageCursorForSortOrder(MailInfo.MailSortOrder order) {
        String[] projection = {
                MailContract.MailEntry.MAIL_DATE_RECEIVED,
                MailContract.MailEntry.MAIL_SUBJECT,
                MailContract.MailEntry.MAIL_FROM_EMAIL,
                MailContract.MailEntry.MAIL_FROM_NAME,
                MailContract.MailEntry.MAIL_SHORT,
                MailContract.MailEntry.MAIL_FOLDER,
                MailContract.MailEntry.MAIL_GPG_STATUS,
                MailContract.MailEntry.MAIL_UID,
                MailContract.MailEntry.MAIL_FLAGS,
                MailContract.MailEntry.MAIL_SYNCED,
        };
        String sortOrder = "";
        if (order == MailInfo.MailSortOrder.SORT_ORDER_RECENT) {
            sortOrder = MailContract.MailEntry.MAIL_DATE_RECEIVED + " DESC";
        }
        Cursor c = helper.getReadableDatabase().query(MailContract.MailEntry.TABLE_NAME, projection, null, null, null, null, sortOrder);
        return new MessageCursor(c);
    }

    /**
     * Get the given folder by it's IMAP name.
     * @param folderName The name of the folder.
     * @return null if not in DB, the folder otherwise.
     */
    public CachedFolder getFolderForName(String folderName) {
        String[] projection = {
                MailContract.MailStatus.MAIL_FOLDER,
                MailContract.MailStatus.MAIL_UIDNEXT,
                MailContract.MailStatus.MAIL_UIDVALIDITY,
                MailContract.MailStatus.MAIL_MAXUID,
        };

        String sortOrder = MailContract.MailEntry.MAIL_FOLDER + " DESC";

        Cursor c = helper.getReadableDatabase().query(MailContract.MailStatus.TABLE_NAME,projection, MailContract.MailStatus.MAIL_FOLDER+" LIKE ?", new String[] {folderName},null,null,sortOrder);

        if(c.getCount() > 0) {

            CachedFolder folder = new CachedFolder();

            folder.folder = folderName;
            folder.uid_next = c.getLong(c.getColumnIndex(MailContract.MailStatus.MAIL_UIDNEXT));
            folder.uid_validity = c.getLong(c.getColumnIndex(MailContract.MailStatus.MAIL_UIDVALIDITY));
            folder.max_uid = c.getLong(c.getColumnIndex(MailContract.MailStatus.MAIL_MAXUID));
            c.close();
            return folder;
        }else{
            return null;
        }
    }

    /**
     * Update a folder that should already be in the DB.
     * @param folder The folder to update in the DB.
     */
    public void updateFolder(CachedFolder folder){
        ContentValues values = new ContentValues();
        values.put(MailContract.MailStatus.MAIL_UIDNEXT,folder.uid_next);
        values.put(MailContract.MailStatus.MAIL_UIDVALIDITY,folder.uid_validity);
        values.put(MailContract.MailStatus.MAIL_MAXUID,folder.max_uid);

        String selection = MailContract.MailStatus.MAIL_FOLDER + " LIKE ?";
        String[] selectArgs = {folder.folder};
        helper.getWritableDatabase().update(MailContract.MailStatus.TABLE_NAME,values,selection,selectArgs);
    }

    /**
     * Insert a folder into the DB
     * @param folder The folder to insert
     */
    public void putFolder(CachedFolder folder){
        ContentValues values = new ContentValues();
        values.put(MailContract.MailStatus.MAIL_UIDNEXT,folder.uid_next);
        values.put(MailContract.MailStatus.MAIL_UIDVALIDITY,folder.uid_validity);
        values.put(MailContract.MailStatus.MAIL_MAXUID,folder.max_uid);
        values.put(MailContract.MailStatus.MAIL_FOLDER, folder.folder);

        helper.getWritableDatabase().insert(MailContract.MailStatus.TABLE_NAME,null,values);

    }

    /**
     * Delete a folder and all of it's cached mail. Folder does not have to exist.
     * @param folder Folder name.
     */
    public void deleteFolder(String folder){

        helper.getWritableDatabase().delete(MailContract.MailStatus.TABLE_NAME, MailContract.MailStatus.MAIL_FOLDER + " LIKE ?", new String[] {folder});
        helper.getWritableDatabase().delete(MailContract.MailEntry.TABLE_NAME, MailContract.MailEntry.MAIL_FOLDER + " LIKE ?", new String[] {folder});

    }

    /**
     * When you know it's safe to allocate room for every message in the cache, you can use this method to get a list.
     * Note that it can be rather dangerous, as it pulls <i>every</i> message from the cursor!
     *
     * @param order Sort order to use
     * @return An array of messages.
     */
    public CompactMessage[] getMessagesArrayForSortOrder(MailInfo.MailSortOrder order) {
        MessageCursor cursor = getMessageCursorForSortOrder(order);
        CompactMessage[] messages = new CompactMessage[cursor.getCount()];
        for (int i = 0; i < cursor.getCount(); i++) {
            messages[i] = cursor.getNext();
        }
        return messages;
    }

    /**
     * Allows iteration over a large list of messages without the overhead of putting it all in an array.
     */
    public class MessageCursor {
        private Cursor c;

        public MessageCursor(Cursor c) {
            this.c = c;
            this.c.moveToFirst();
        }

        public void close() {
            if (!c.isClosed()) {
                c.close();
            }
        }

        public int getCount() {
            return c.getCount();
        }

        public CompactMessage getNext() {
            CompactMessage m = new CompactMessage();
            m.dateReceived = c.getLong(c.getColumnIndex(MailContract.MailEntry.MAIL_DATE_RECEIVED));
            m.subject = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_SUBJECT));
            m.fromEmail = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_FROM_EMAIL));
            m.fromName = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_FROM_NAME));
            m.shortDescription = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_SHORT));
            m.folder = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_FOLDER));
            m.gpgStatus = MailInfo.GpgStatus.valueOf(c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_GPG_STATUS)));
            m.uid = c.getLong(c.getColumnIndex(MailContract.MailEntry.MAIL_UID));
            m.flags = c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_FLAGS)).split(" ");
            m.syncStatus = MailInfo.SyncStatus.valueOf(c.getString(c.getColumnIndex(MailContract.MailEntry.MAIL_SYNCED)));
            c.moveToNext();
            return m;
        }

    }

}
