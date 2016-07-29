package me.alexroth.gpgmail.mailfetchers;

import android.util.Log;

import com.libmailcore.IMAPFetchContentOperation;
import com.libmailcore.IMAPFetchMessagesOperation;
import com.libmailcore.IMAPFolderInfo;
import com.libmailcore.IMAPFolderInfoOperation;
import com.libmailcore.IMAPMessage;
import com.libmailcore.IMAPMessagesRequestKind;
import com.libmailcore.IMAPSession;
import com.libmailcore.IndexSet;
import com.libmailcore.MailException;
import com.libmailcore.MessageParser;
import com.libmailcore.OperationCallback;
import com.libmailcore.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.alexroth.gpgmail.db.CachedFolder;
import me.alexroth.gpgmail.db.CompactMessage;
import me.alexroth.gpgmail.db.MailHandler;
import me.alexroth.gpgmail.db.MailInfo;

/**
 * Handles synchronization of an IMAP mailbox.
 *
 * @author Alex Roth
 */
public class ImapSynchronizer {

    private static final String TAG = "IMAPSynchronizer";
    private IMAPSession session;
    private MailHandler dbHandler;

    public interface CompletionCallback{
        void complete();
        void error(String error);
    }


    public ImapSynchronizer(MailHandler dbHandler, IMAPSession session){
        this.dbHandler = dbHandler;
        this.session = session;
    }

    public void sync(final String folder, final CompletionCallback callback){
        final CachedFolder dbfolder = dbHandler.getFolderForName(folder);
        final long uidvalidity = dbfolder == null ? -1 : dbfolder.uid_validity;

        final IMAPFolderInfoOperation fetchInfo = session.folderInfoOperation(folder);
        fetchInfo.start(new OperationCallback() {
            @Override
            public void succeeded() {
                IMAPFolderInfo info = fetchInfo.info();
                Log.i(TAG, "Info fetch success, validity" + info.uidValidity());
                if(info.uidValidity() != uidvalidity){
                    Log.i(TAG, "Wiping folder, starting fresh");
                    clearAndResync(folder,info, callback);
                }else{
                    Log.i(TAG, "Synchronizing folder");
                    downloadSync(info,dbfolder, callback);
                }
            }

            @Override
            public void failed(MailException e) {
                //TODO: handle
                callback.error(e.getLocalizedMessage());
            }
        });
    }

    public void clearAndResync(final String folder, final IMAPFolderInfo info, final CompletionCallback callback){
        dbHandler.deleteFolder(folder);
        final CachedFolder newFolder = new CachedFolder();
        newFolder.folder=folder;
        newFolder.uid_validity=0;
        newFolder.uid_next = info.uidNext();
        newFolder.max_uid=1;
        dbHandler.putFolder(newFolder);
        Log.e(TAG, "Added folder " + folder +"to db. Fetching...");
        final IMAPFetchMessagesOperation fetchOp = session.fetchMessagesByUIDOperation(folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindFlags,IndexSet.indexSetWithRange(new Range(0,Range.RangeMax)));
        fetchOp.start(new OperationCallback() {
            @Override
            public void succeeded() {
                for(IMAPMessage mes : fetchOp.messages()){
                    Log.e(TAG, "message: " +mes.sequenceNumber());
                    long numInPos = mes.sequenceNumber();
                    final IMAPFetchMessagesOperation fetchByPosOp = session.fetchMessagesByNumberOperation(folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindFlags | IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaders,IndexSet.indexSetWithRange(new Range(numInPos-20,numInPos)));
                    fetchByPosOp.start(new OperationCallback() {
                        @Override
                        public void succeeded() {
                            addMessagesAndUpdateFolder(newFolder,fetchByPosOp.messages(), info);
                            callback.complete();
                        }

                        @Override
                        public void failed(MailException e) {
                            //At this point, we basically can't do anything but destroy the folder and hope for the best.
                            dbHandler.deleteFolder(folder);
                            callback.error(e.getLocalizedMessage());
                        }
                    });
                }
            }

            @Override
            public void failed(MailException e) {
                //At this point, we basically can't do anything but destroy the folder and hope for the best.
                //So then the known state is that we can retry sync when we have a good chance at being successful.
                dbHandler.deleteFolder(folder);
                callback.error(e.getLocalizedMessage());
            }
        });

    }
    private void addMessagesAndUpdateFolder(CachedFolder folder, List<IMAPMessage> messages, IMAPFolderInfo info){
        ArrayList<CompactMessage> readyMessages = new ArrayList<CompactMessage>(messages.size());
        int i = 0;
        int total = messages.size();
        for(IMAPMessage message : messages){
            CompactMessage dbMessage = new CompactMessage();
            dbMessage.dateReceived = message.header().receivedDate().getTime();
            if(message.customFlags() != null) {
                String[] flags = new String[message.customFlags().size() + 1];
                message.customFlags().toArray(flags);
                flags[flags.length - 1] = Integer.toHexString(message.flags());
                dbMessage.flags = flags;
            }else{
                String[] flags = new String[1];
                flags[0] = Integer.toHexString(message.flags());
                dbMessage.flags = flags;
            }

            dbMessage.folder = folder.folder;
            dbMessage.fromEmail = message.header().from().RFC822String();
            dbMessage.fromName = message.header().from().displayName();
            dbMessage.shortDescription = null;
            dbMessage.subject = message.header().subject();
            dbMessage.uid = message.uid();
            dbMessage.syncStatus = MailInfo.SyncStatus.SYNC_STATUS_NEED_CONTENT;
            dbMessage.gpgStatus = MailInfo.GpgStatus.GPG_STATUS_UNKNOWN;
            i++;
            Log.e(TAG, "Adding message, subject " + dbMessage.subject+ ", "+i+"/"+total);
            readyMessages.add(dbMessage);
        }
        boolean didSucceed = dbHandler.addMessagesAsTransaction(readyMessages);
        if(didSucceed) {
            long maxUid = 1;
            for (CompactMessage mes : readyMessages) {
                if (mes.uid > maxUid) {
                    maxUid = mes.uid;
                }
            }
            if (maxUid == 1) {
                Log.e(TAG, "Didn't add to DB?");
            } else {
                Log.e(TAG, "Added up to UID: " + maxUid);
                folder.max_uid = maxUid;
            }

            folder.uid_validity=info.uidValidity();
            dbHandler.updateFolder(folder);
        }else{
            //TODO: handle nicer.
            Log.e(TAG, "Adding messages failed - not updating folder status.");
        }
    }

    public void downloadSync(final IMAPFolderInfo info, final CachedFolder folder, final CompletionCallback callback){
        //Fetch in forward direction - all new emails.
        if(info.uidNext() != folder.uid_next) {
            final IMAPFetchMessagesOperation fetchOp = session.fetchMessagesByUIDOperation(folder.folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindFlags | IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaders, IndexSet.indexSetWithRange(new Range(folder.max_uid+1, Range.RangeMax)));
            fetchOp.start(new OperationCallback() {
                @Override
                public void succeeded() {
                    List<IMAPMessage> messages = fetchOp.messages();
                    long oldUid = folder.max_uid;
                    addMessagesAndUpdateFolder(folder, messages, info);
                    //TODO: Trim based on date. Don't want to keep > 100 emails or so at a time.
                    updateOldMessages(info, folder, oldUid, callback);
                }

                @Override
                public void failed(MailException e) {

                }
            });
        }else{
            Log.e(TAG, "Skipping fetching new messages, refreshing old cached.");
            updateOldMessages(info,folder,folder.max_uid, callback);
        }

    }

    public void updateOldMessages(final IMAPFolderInfo info, final CachedFolder folder, final long oldUidMax, final CompletionCallback callback){
        CompactMessage oldMessage = dbHandler.getOldestMessageInFolder(folder.folder);
        Log.e(TAG, "Got old message with uid" + oldMessage.uid + " fetching with folder UID: " + folder.max_uid);
        final IMAPFetchMessagesOperation fetchOp = session.fetchMessagesByUIDOperation(folder.folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindFlags, IndexSet.indexSetWithRange(new Range(oldMessage.uid, folder.max_uid)));
        fetchOp.start(new OperationCallback() {
            @Override
            public void succeeded() {
                Log.e(TAG, "Success, checking for deleted messages....");
                CompactMessage[] messages = dbHandler.getMessagesArrayForSortOrderAndFolder(MailInfo.MailSortOrder.SORT_ORDER_RECENT,folder.folder);
                ArrayList<Long> messageUids = new ArrayList<Long>();
                HashMap<Long,CompactMessage> messageMap = new HashMap<>();
                for(CompactMessage message : messages){
                    messageUids.add(message.uid);
                    messageMap.put(message.uid,message);
                }
                for(IMAPMessage message : fetchOp.messages()){
                    messageUids.remove(messageUids.indexOf(message.uid()));
                    CompactMessage dbMessage = messageMap.get(message.uid());

                    if(message.customFlags() != null) {
                        String[] flags = new String[message.customFlags().size() + 1];
                        message.customFlags().toArray(flags);
                        flags[flags.length - 1] = Integer.toHexString(message.flags());
                        dbMessage.flags = flags;
                    }else{
                        String[] flags = new String[1];
                        flags[0] = Integer.toHexString(message.flags());
                        dbMessage.flags = flags;
                    }

                    //Log.e(TAG, "Updating message:" +dbMessage.subject);
                    for(String s: dbMessage.flags){
                        //Log.e(TAG, "   Flag: "+s);
                    }
                    dbHandler.updateMessage(dbMessage,message.uid(),folder.folder);

                }
                Log.e(TAG, "Remaining messages: " + messageUids.size());
                if(Math.abs(messageUids.size() - messages.length) < 5){
                    //It seems kinda unlikey that we actually deleted almost everything.
                    //So lets wipe everything and redo.
                    //TODO: Figure out why this happens and fix it!
                    Log.e(TAG, "Looks like we just deleted the whole inbox again...");
                    //clearAndResync(folder.folder,info);
                    //return;
                }
                for(long l : messageUids){
                    dbHandler.deleteMessageInFolderWithUid(folder.folder,l);
                }
                callback.complete();
            }

            @Override
            public void failed(MailException e) {

            }
        });
    }


    /**
     * Fetches headers for all emails in the given UID range.
     * Does so transactionally - if the error callback is called, then all the changes will have already been rolled back, so you can rely on that to keep state about which messages still need data.
     */
    public void fetchHeaders(long messageLowerUid, long messageUpperUid, final String folder, final CompletionCallback callback){
        final IMAPFetchMessagesOperation fetchOp = session.fetchMessagesByUIDOperation(folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindStructure | IMAPMessagesRequestKind.IMAPMessagesRequestKindSize, IndexSet.indexSetWithRange(new Range(messageLowerUid, messageUpperUid)));
        fetchOp.start(new OperationCallback() {
            @Override
            public void succeeded() {
                for(IMAPMessage message : fetchOp.messages()){
                    //Hmm, this is hard.
                    //We want to fetch a summary here, but if we're not careful we'll also download all the attachments, which could potentially be massive.
                    Log.e(TAG, "Part mime type: " +message.mainPart().mimeType());
                    String mimeType = message.mainPart().mimeType();
                    if(mimeType.equals("multipart/ENCRYPTED")){
                        Log.e(TAG, "Encrypted message - can't download contents in advance.");
                    }else if(message.size() > 20000){
                        Log.e(TAG, "Massive main section - don't want to download.");
                    }else{
                        Log.i(TAG, "Attempting to download main message content....");
                        final IMAPFetchContentOperation contentOp = session.fetchMessageByUIDOperation(folder, message.uid());
                        contentOp.start(new OperationCallback() {
                            @Override
                            public void succeeded() {
                                MessageParser parser = MessageParser.messageParserWithData(contentOp.data());
                                Log.e(TAG, "Message content: " + new String(parser.data()));
                            }

                            @Override
                            public void failed(MailException e) {

                            }
                        });
                    }


                }
            }

            @Override
            public void failed(MailException e) {
                callback.error(e.getLocalizedMessage());
            }
        });
    }

}
