package me.alexroth.gpgmail.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.TextView;


import com.facebook.drawee.view.SimpleDraweeView;
import com.libmailcore.MessageFlag;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;

import me.alexroth.gpgmail.R;
import me.alexroth.gpgmail.db.BinaryMessage;
import me.alexroth.gpgmail.db.CompactMessage;
import me.alexroth.gpgmail.db.MailHandler;
import me.alexroth.gpgmail.db.MailInfo;
import me.alexroth.gpgmail.mailfetchers.ImapHandler;
import me.alexroth.gpgmail.mailfetchers.ImapSynchronizer;
import me.alexroth.gpgmail.mailparse.MimePart;
import me.alexroth.gpgmail.pgp.SimplePgpWrapper;

public class Inbox extends AppCompatActivity {

    private static final String TAG = "InboxActivity";
    MailHandler mailHandler;
    SimplePgpWrapper pgpWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ImapHandler handler = new ImapHandler("alex@magmastone.net", "3m3zwiiear0b", 993, "imappro.zoho.com");
        mailHandler = new MailHandler(getApplicationContext());
        final ImapSynchronizer synchronizer = new ImapSynchronizer(mailHandler, handler.session);
        final MailAdapter adapter = new MailAdapter(mailHandler);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                synchronizer.sync("INBOX", new ImapSynchronizer.CompletionCallback() {
                    @Override
                    public void complete() {
                        adapter.refresh();

                    }

                    @Override
                    public void progress() {

                    }

                    @Override
                    public void error(String error) {
                        Log.e(TAG, "Failed  sync: " + error);
                    }
                });


            }
        });
        pgpWrapper = new SimplePgpWrapper(getApplicationContext());

        synchronizer.sync("INBOX", new ImapSynchronizer.CompletionCallback() {
            @Override
            public void complete() {
                adapter.refresh();
                CompactMessage[] messages = mailHandler.getMessagesArrayForSortOrderAndFolder(MailInfo.MailSortOrder.SORT_ORDER_RECENT, "INBOX");
                long toUid = messages[0].uid;
                long fromUid = messages[messages.length - 1].uid;
                synchronizer.fetchHeaders(fromUid, toUid, "INBOX", new ImapSynchronizer.CompletionCallback() {
                    @Override
                    public void complete() {
                        Log.i(TAG, "Header fetch complete");
                        doGpgVerification();
                    }

                    @Override
                    public void progress() {
                        adapter.refresh();
                    }

                    @Override
                    public void error(String error) {
                        Log.e(TAG, "Failed headers: " + error);
                    }
                });
            }

            @Override
            public void progress() {

            }

            @Override
            public void error(String error) {
                Log.e(TAG, "Failed  sync: " + error);
            }
        });



        BinaryMessage message = mailHandler.getCachedMessage(12054, "INBOX");


        LinearLayoutManager llm = new LinearLayoutManager(this);
        RecyclerView rView = (RecyclerView) findViewById(R.id.mail);
        rView.setLayoutManager(llm);

        rView.setAdapter(adapter);
    }
    /*
            Intent i = new Intent();
        i.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        i.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, signature.getBytes());
        return i;
     */

    ListIterator<CompactMessage> verifyIterator;

    private void doGpgVerification() {
        if (verifyIterator == null) {
            Log.i(TAG, "Starting verification...");

            CompactMessage[] messages = mailHandler.getUncheckedMessages("INBOX");
            Log.i(TAG, "Need to verify: " + messages.length + " messages");
            verifyIterator = Arrays.asList(messages).listIterator();
        }
        if (verifyIterator.hasNext()) {
            CompactMessage message = verifyIterator.next();

            Intent vIntent = new Intent();

            vIntent.putExtra("message_uid", message.uid);
            vIntent.putExtra("message_folder", message.folder);

            Log.i(TAG, "verifiying: " + message.subject);
            runVerificationIntent(vIntent);

        }
    }

    //This function has to be able to be called with only the things that are passed in.
    private void runVerificationIntent(final Intent i) {
        i.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        //This feels a little hacky and ugly. Oh well.
        CompactMessage currentMessage = verifyIterator.previous();
        verifyIterator.next();

        long messageUid = currentMessage.uid;
        String messageFolder = currentMessage.folder;

        BinaryMessage binMessage = mailHandler.getCachedMessage(messageUid, messageFolder);
        if (binMessage != null) {


            //TODO: This almost definitely is *not* safe!
            String s = new String(binMessage.message, StandardCharsets.UTF_8);

            MimePart messagePart = new MimePart(s);

            //TODO: is this safe? Check the RFC in more detail.

            MimePart signedContentPart = messagePart.subParts.get(0);
            MimePart detachedPart = messagePart.subParts.get(1);

            final InputStream stream = new ByteArrayInputStream(signedContentPart.getCRLFNormalizedRawText().getBytes(StandardCharsets.UTF_8));

            i.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, detachedPart.partContent.getBytes());

            pgpWrapper.connect(new SimplePgpWrapper.ConnectionCallback() {
                @Override
                public void success(OpenPgpApi api) {
                    api.executeApiAsync(i, stream, null, new OpenPgpApi.IOpenPgpCallback() {
                        @Override
                        public void onReturn(Intent result) {
                            handleSignatureCompletion(result);
                        }
                    });
                }

                @Override
                public void failed(String error) {
                    Log.e(TAG, "Failed to connect to PGP service: " + error);
                }
            });

        } else {
            //The binary message was null. Chances are this message hasn't been fetched yet. The message will be verified on a later download pass.
            Log.e(TAG, "Binary message missing! Skipping.");
            doGpgVerification();
        }

    }

    private void handleSignatureCompletion(Intent result){

        Log.e(TAG, "Returned from verification intent.");

        //This feels a little hacky and ugly. Oh well.
        CompactMessage currentMessage = verifyIterator.previous();
        verifyIterator.next();

        long messageUid = currentMessage.uid;
        String messageFolder = currentMessage.folder;

        int rescode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR);
        if (rescode == OpenPgpApi.RESULT_CODE_SUCCESS) {

            OpenPgpSignatureResult sigResult = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
            Log.e(TAG, "Verification complete: " + sigResult);

            //Next message.
            doGpgVerification();

        } else if (rescode == OpenPgpApi.RESULT_CODE_ERROR) {

            OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
            Log.e(TAG, "error decoding! " + error.getMessage());

            //Next Message
            doGpgVerification();

        } else if (rescode == OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED) {
            Log.i(TAG, "User interaction is required. Dispatching intent...");
            PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
            try {
                Inbox.this.startIntentSenderFromChild(
                        Inbox.this, pi.getIntentSender(),
                        0, null, 0, 0, 0);

                //TODO: this is rather dangerous, shortening the long messageUid to an int.
                //Realistically, probably not a problem. But we may wind up with someone with a stupidly large mailbox at some point.
                startIntentSenderForResult(pi.getIntentSender(),(int)messageUid,null,0,0,0);

            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, "SendIntentException", e);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK){
            runVerificationIntent(data);
        }else{
            Log.e(TAG, "PGP intent failed!!!");
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_inbox, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class InboxItemViewHolder extends RecyclerView.ViewHolder {
        private TextView personName;
        private TextView subject;
        private TextView mailDescription;
        private SimpleDraweeView profilePic;

        public InboxItemViewHolder(View itemView) {
            super(itemView);
            personName = (TextView) itemView.findViewById(R.id.sender);
            subject = (TextView) itemView.findViewById(R.id.subject);
            mailDescription = (TextView) itemView.findViewById(R.id.description);
            profilePic = (SimpleDraweeView) itemView.findViewById(R.id.profilePic);
        }
    }

    private class MailAdapter extends RecyclerView.Adapter<InboxItemViewHolder> {

        private MailHandler dbHelper;
        private CompactMessage[] messages;

        public MailAdapter(MailHandler dbHelper) {
            this.dbHelper = dbHelper;
            messages = dbHelper.getMessagesArrayForSortOrderAndFolder(MailInfo.MailSortOrder.SORT_ORDER_RECENT, "INBOX");
        }

        @Override
        public InboxItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.row_mail_message, parent, false);
            return new InboxItemViewHolder(v);
        }

        public void refresh() {
            messages = dbHelper.getMessagesArrayForSortOrderAndFolder(MailInfo.MailSortOrder.SORT_ORDER_RECENT, "INBOX");
            notifyDataSetChanged();
        }

        @Override
        public void onBindViewHolder(InboxItemViewHolder holder, int position) {
            CompactMessage message = messages[position];
            holder.subject.setText(message.subject);
            holder.mailDescription.setText(message.shortDescription);
            holder.personName.setText(message.fromName);
            String standardFlags = message.flags[message.flags.length - 1];
            int flags = Integer.decode(standardFlags);
            if ((flags & MessageFlag.MessageFlagSeen) != 0) {
                holder.subject.setTextColor(Color.argb(255, 0xAA, 0xAA, 0xAA));
                holder.personName.setTextColor(Color.argb(255, 0xAA, 0xAA, 0xAA));
            } else {
                holder.subject.setTextColor(Color.argb(255, 0, 0, 0));
                holder.personName.setTextColor(Color.argb(255, 0, 0, 0));
            }
        }

        @Override
        public int getItemCount() {
            return messages.length;
        }
    }
}
