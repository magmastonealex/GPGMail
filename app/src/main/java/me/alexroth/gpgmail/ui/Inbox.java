package me.alexroth.gpgmail.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import me.alexroth.gpgmail.R;
import me.alexroth.gpgmail.db.BinaryMessage;
import me.alexroth.gpgmail.db.CompactMessage;
import me.alexroth.gpgmail.db.MailDbHelper;
import me.alexroth.gpgmail.db.MailHandler;
import me.alexroth.gpgmail.db.MailInfo;
import me.alexroth.gpgmail.mailfetchers.ImapHandler;
import me.alexroth.gpgmail.mailfetchers.ImapSynchronizer;
import me.alexroth.gpgmail.mailparse.HeaderSplitter;
import me.alexroth.gpgmail.mailparse.MimePart;

public class Inbox extends AppCompatActivity {

    private static final String TAG = "InboxActivity";
    MailHandler mailHandler;

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

        BinaryMessage message = mailHandler.getCachedMessage(11975, "INBOX");
        try {
            String s = new String(message.message, "UTF-8");
            MimePart messagePart = new MimePart(s);
            Log.e(TAG, "messagePart type: "  + messagePart.contentType);
            Log.e(TAG, "params: ");
            for(String key : messagePart.contentParams.keySet()){
                Log.e(TAG, " "+key+":"+messagePart.contentParams.get(key));
            }

        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unsupported encoding??");
        }

        LinearLayoutManager llm = new LinearLayoutManager(this);
        RecyclerView rView = (RecyclerView) findViewById(R.id.mail);
        rView.setLayoutManager(llm);
        rView.setAdapter(adapter);
    }

    private void doGpgVerification() {
        Log.i(TAG, "Starting verification...");

        CompactMessage[] messages = mailHandler.getUncheckedMessages("INBOX");
        Log.i(TAG, "Need to verify: " + messages.length + " messages");

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
