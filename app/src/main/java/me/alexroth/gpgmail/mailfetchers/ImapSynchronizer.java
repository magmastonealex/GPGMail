package me.alexroth.gpgmail.mailfetchers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.libmailcore.IMAPFetchMessagesOperation;
import com.libmailcore.IMAPFolderInfo;
import com.libmailcore.IMAPFolderInfoOperation;
import com.libmailcore.IMAPMessage;
import com.libmailcore.IMAPMessagesRequestKind;
import com.libmailcore.IMAPSession;
import com.libmailcore.IndexSet;
import com.libmailcore.MailException;
import com.libmailcore.OperationCallback;
import com.libmailcore.Range;

import java.util.ArrayList;
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

    private IMAPSession session;
    private MailHandler dbHandler;

    public ImapSynchronizer(MailHandler dbHandler, IMAPSession session){
        this.dbHandler = dbHandler;
        this.session = session;
    }

    public void sync(final String folder){
        final CachedFolder dbfolder = dbHandler.getFolderForName(folder);
        final long uidvalidity = dbfolder == null ? -1 : dbfolder.uid_validity;

        final IMAPFolderInfoOperation fetchInfo = session.folderInfoOperation(folder);
        fetchInfo.start(new OperationCallback() {
            @Override
            public void succeeded() {
                IMAPFolderInfo info = fetchInfo.info();
                if(info.uidValidity() != uidvalidity){
                    clearAndResync(folder,info);
                }else{
                    downloadSync(info,dbfolder);
                }
            }

            @Override
            public void failed(MailException e) {
                //TODO: handle
            }
        });
    }

    public void clearAndResync(String folder, IMAPFolderInfo info){
        dbHandler.deleteFolder(folder);
        CachedFolder newFolder = new CachedFolder();
        newFolder.folder=folder;
        newFolder.uid_validity=info.uidValidity();
        newFolder.uid_next = info.uidNext();
        newFolder.max_uid=1;
        dbHandler.putFolder(newFolder);
        downloadSync(info,newFolder);
    }

    public void downloadSync(IMAPFolderInfo info, final CachedFolder folder){
        final IMAPFetchMessagesOperation fetchOp = session.fetchMessagesByUIDOperation(folder.folder, IMAPMessagesRequestKind.IMAPMessagesRequestKindFlags|IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaders,IndexSet.indexSetWithRange(new Range(folder.max_uid,Range.RangeMax)));
        fetchOp.start(new OperationCallback() {
            @Override
            public void succeeded() {
                List<IMAPMessage> messages = fetchOp.messages();
                ArrayList<CompactMessage> readyMessages = new ArrayList<CompactMessage>(messages.size());
                int i = 0;
                int total = messages.size();
                for(IMAPMessage message : messages){
                    CompactMessage dbMessage = new CompactMessage();
                    dbMessage.dateReceived = message.header().receivedDate().getTime();
                    String[] flags = new String[message.customFlags().size()+1];
                    message.customFlags().toArray(flags);
                    flags[flags.length-1] = Integer.toHexString(message.flags());
                    dbMessage.flags = flags;
                    dbMessage.folder = folder.folder;
                    dbMessage.fromEmail = message.header().from().RFC822String();
                    dbMessage.fromName = message.header().from().displayName();
                    dbMessage.shortDescription = null;
                    dbMessage.subject = message.header().subject();
                    dbMessage.uid = message.uid();
                    dbMessage.syncStatus = MailInfo.SyncStatus.SYNC_STATUS_NEED_CONTENT;
                    dbMessage.gpgStatus = MailInfo.GpgStatus.GPG_STATUS_UNKNOWN;
                    i++;
                    Log.e("IMAPSynchronizer", "Adding message, subject " + dbMessage.subject+ ", "+i+"/"+total);
                    readyMessages.add(dbMessage);
                }
            }

            @Override
            public void failed(MailException e) {

            }
        });
    }
}
