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

import me.alexroth.gpgmail.db.MailDbHelper;

/**
 * IMAP synchronization implementation & message fetching.
 *
 * @author alex
 * @since 7/25/16
 */
public class ImapHandler {
    public static final String TAG = "ImapHandler";


    public IMAPSession session;

    /**
     * ImapHandler acts as a frontend to the Imap mail system. It doesn't implement the synchronization at all. Does not take connection options, though it probably should.
     */
    public ImapHandler(String username, String password, int port, String host){
        final IMAPSession session = new IMAPSession();

        session.setUsername(username);
        session.setPassword(password);
        session.setHostname(host);
        session.setPort(port);
        session.setConnectionType(ConnectionType.ConnectionTypeTLS);

        this.session = session;
    }


}
