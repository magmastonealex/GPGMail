package me.alexroth.gpgmail.mailfetchers;

import android.util.Log;

import com.libmailcore.ConnectionLogger;
import com.libmailcore.ConnectionType;
import com.libmailcore.IMAPFetchFoldersOperation;
import com.libmailcore.IMAPFetchMessagesOperation;
import com.libmailcore.IMAPFolder;
import com.libmailcore.IMAPFolderStatusOperation;
import com.libmailcore.IMAPMessage;
import com.libmailcore.IMAPMessagesRequestKind;
import com.libmailcore.IMAPSession;
import com.libmailcore.IndexSet;
import com.libmailcore.MailException;
import com.libmailcore.OperationCallback;
import com.libmailcore.Range;

/**
 * Simple IMAP mail fetcher.
 *
 * @author alex
 * @since 7/25/16
 */
public class ImapHandler {
    public static final String TAG = "ImapHandler";
    // Things that a consumer would need to know:
    // First message sequence number (to know to fetch, say, 300 back from that)
    // First message uid (to know where to start fetching from)


    // How to create a list of emails:
    // Initial:
    // Get all emails to find the max UID of all of them.
    // Use that to find a sequence number.
    // This sequence number can be used to fetch, say, the next 50 emails.
    // Save the UID as the last fetched email.
    //
    // Every other iteration:
    // Get the last-fetched UID
    // Fetch from that UID to the new max UID
    // Save that UID
    // Repeat with sequence number to get the rest of the emails.
    //
    public ImapHandler(String username, String password, int port, String host){
        final IMAPSession session = new IMAPSession();

        session.setUsername(username);
        session.setPassword(password);
        session.setHostname(host);
        session.setPort(port);
        session.setConnectionType(ConnectionType.ConnectionTypeTLS);

        IMAPFolderStatusOperation op1 = session.folderStatusOperation("INBOX");
        op1.status().uidValidity();

        final IMAPFetchMessagesOperation op = session.fetchMessagesByUIDOperation("INBOX", IMAPMessagesRequestKind.IMAPMessagesRequestKindHeaders, IndexSet.indexSetWithRange(new Range(11900, Range.RangeMax)));
        op.start(new OperationCallback() {
            @Override
            public void succeeded() {
                Log.i(TAG, "Success fetching all messages");
                long maxUid = 0;
                for (IMAPMessage message : op.messages()) {
                    if(message.uid() > maxUid){
                        maxUid = message.uid();
                    }

                    Log.i(TAG, "test:" + message.header().subject()+ " " + message.sequenceNumber());
                }
                Log.i(TAG, "Completion: " + maxUid);
            }

            @Override
            public void failed(MailException e) {
                Log.e(TAG, "Failure fetching" + e.getLocalizedMessage());
            }
        });


    }
}
