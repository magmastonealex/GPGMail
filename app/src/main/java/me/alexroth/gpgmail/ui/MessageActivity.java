package me.alexroth.gpgmail.ui;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;


import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import me.alexroth.gpgmail.R;
import me.alexroth.gpgmail.db.BinaryMessage;
import me.alexroth.gpgmail.db.CompactMessage;
import me.alexroth.gpgmail.db.MailHandler;
import me.alexroth.gpgmail.mailparse.MimePart;

public class MessageActivity extends AppCompatActivity{

    private static final String TAG = "MessageActivity";

    public static final String UID_TAG = "message_uid";
    public static final String FOLDER_TAG = "message_folder";


    private class ContentTypedString {
        public String content;
        public String contentType;
    }

    private long uid;
    private String folder;
    MailHandler mailHandler;
    WebView messageView;

    private MimePart topPart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        uid = getIntent().getLongExtra(UID_TAG, 0);
        folder = getIntent().getStringExtra(FOLDER_TAG);
        mailHandler = new MailHandler(getApplicationContext());
        BinaryMessage message = mailHandler.getCachedMessage(uid, folder);
        CompactMessage messageDetails = mailHandler.getMessageWithFolderAndUid(folder, uid);
        if (message == null || message.message == null) {
            //The message wasn't fetched for some reason. That's probably because it was very large.
            Log.e(TAG, "Could not display, not fetched");
        } else {
            Log.e(TAG, "Should be displaying: " + messageDetails.subject);
        }

        String s = new String(message.message, StandardCharsets.UTF_8);
        MimePart p = new MimePart(s);
        topPart = p;
        messageView = (WebView) findViewById(R.id.message_view);
        messageView.getSettings().setBuiltInZoomControls(true);
        messageView.getSettings().setSupportZoom(true);
        messageView.getSettings().setDisplayZoomControls(false);
        messageView.getSettings().setDefaultTextEncodingName("utf-8");
        messageView.setWebViewClient(new WebViewClient(){
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                if(Build.VERSION.SDK_INT > 21) {
                    Uri uri = request.getUrl();
                    if(uri.getScheme().equalsIgnoreCase("cid")){
                        Log.e(TAG, "Need to find: " + uri.toString().split("cid:",2)[1]);
                        return getResponseFromCid(uri.toString().split("cid:",2)[1]);
                    }else{
                        return  null;
                    }

                }else {
                    return null;
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest (WebView view, String url){
                Log.e(TAG, "Old method called...");
                if(url.substring(0,10).contains("cid:")) {
                    return getResponseFromCid(url.split("cid:", 2)[1]);
                }else{
                    return null;
                }
            }

            private WebResourceResponse getResponseFromCid(String contentId){
                MimePart p = topPart.findMimePartForContentId(contentId);
                if(p == null){
                    Log.e(TAG, "Error, "+contentId+" was not found.");
                    return null;
                }
                String charset = null;
                if (p.contentParams.containsKey("charset")) {
                    charset = p.contentParams.get("charset");
                    Log.e(TAG, "Found a specific charset: " + charset);
                }

                byte[] content = new byte[0];
                if (p.headers.containsKey("content-transfer-encoding")) {
                    String encoding = p.headers.get("content-transfer-encoding");
                    if (encoding.equalsIgnoreCase("base64")) {
                        content = Base64.decode(p.partContent,0);
                    }else{
                        Log.e(TAG, "Weird, requested a string!?");
                        content = p.partContent.getBytes();
                    }
                }

                return new WebResourceResponse(p.contentType,charset, new ByteArrayInputStream(content));
            }
        });

        if (p.contentType.toLowerCase().contains("multipart/".toLowerCase())) {
            //Here we check if it's a multipart type we can parse safely.

            doMultipart(p);
        } else {
            //We shoot for the skies and throw it straight into a webview and pray for the best.

            doSinglepart(p);

        }


    }

    private void doMultipart(MimePart p) {
        messageView.loadDataWithBaseURL(null,doMultipartResultingInString(p), "text/html", null,null);
    }

    private String doMultipartResultingInString(MimePart p) {

        //Mixed is easy - just iterate over every part and display it in order, separated by a line or something.
        //Related is easyish. See RFC2387 for details.
        //encrypted/signed - we already know how to deal with those, they just need to be implemented.
        //Mixed is the final default if we don't know how to parse it otherwise.
        Log.e(TAG, "Message of content type: " + p.contentType);
        if (p.contentType.equalsIgnoreCase("multipart/alternative")) {
            for (int i = p.subParts.size() - 1; i >= 0; i--) {
                MimePart subPart = p.subParts.get(i);
                Log.e(TAG, "has type: " +subPart.contentType);
                if (subPart.contentType.equalsIgnoreCase("text/html") || subPart.contentType.equalsIgnoreCase("text/plain")) {

                    Log.e(TAG, "We're using type: " + subPart.contentType);
                    return extractHtmlFromSinglepart(subPart);
                }
            }
            return "<b> Could not find a part we understand.</b>";
        } else if(p.contentType.equalsIgnoreCase("multipart/related")) {
            MimePart firstSubPart = p.subParts.get(0);
            if(firstSubPart.contentType.toLowerCase().contains("multipart/")){
                return doMultipartResultingInString(firstSubPart);
            }else{
                return extractHtmlFromSinglepart(firstSubPart);
            }
        }else{
            Log.e(TAG, "Treating " + p.contentType + " as mixed!");
            //Treat as mixed.
            StringBuilder stringToReturn = new StringBuilder();
            for (MimePart subPart : p.subParts) {
                Log.e(TAG, "Part of content type: " + subPart.contentType);
                if (subPart.contentType.toLowerCase().contains("multipart/".toLowerCase())) {
                    stringToReturn.append("<hr />");
                    stringToReturn.append(doMultipartResultingInString(subPart));
                } else {
                    stringToReturn.append("<hr />");
                    stringToReturn.append(extractHtmlFromSinglepart(subPart));
                }
            }
            return stringToReturn.toString();
        }
    }

    private String transformPlain(String plainText) {
        plainText = plainText.replaceAll("\\r?\\n", "\r\n");
        plainText = Html.escapeHtml(plainText);
        plainText = plainText.replaceAll("&#13;&#10;", "<br />");
        return plainText;
    }

    //TODO: combine this and the other one somehow.
    private String extractHtmlFromSinglepart(MimePart p) {

        String partContent = p.partContent;

        if (p.headers.containsKey("content-transfer-encoding")) {
            String encoding = p.headers.get("content-transfer-encoding");
            if (encoding.equalsIgnoreCase("quoted-printable")) {
                partContent = p.getQuotedPrintableText();
                if (p.contentType.equalsIgnoreCase("text/plain")) {
                    partContent = transformPlain(partContent);
                }
                return partContent;
            } else if (encoding.equalsIgnoreCase("base64")) {
                if (p.contentType.toLowerCase().contains("image/")) {
                    Log.e(TAG, "pc: " + partContent.length());

                    return "<img src=\"" + "data:" + p.contentType + ";base64," + partContent + "\"" + " />";

                } else if (p.contentType.equalsIgnoreCase("text/plain") || p.contentType.equalsIgnoreCase("text/html")) {
                    byte[] decodedBase64 = Base64.decode(partContent.getBytes(), 0);
                    String charsetName = "UTF-8";

                    if (p.contentParams.containsKey("charset")) {
                        charsetName = p.contentParams.get("charset");
                    }

                    Charset charsetToUse = Charset.forName(charsetName);

                    String baseConverted = new String(decodedBase64, charsetToUse);

                    if (p.contentType.equalsIgnoreCase("text/plain")) {
                        baseConverted = transformPlain(baseConverted);
                    }

                    return baseConverted;
                } else {

                    return "<b><i>Base64 Encoded Data.</i></b>";
                }
            }
        }
        if (p.contentType.equals("text/html")) {
            return partContent;
        } else {
            partContent = transformPlain(partContent);
            return partContent;
        }


    }

    private void doSinglepart(MimePart p) {

        String charset = "US-ASCII";
        if (p.contentParams.containsKey("charset")) {
            charset = p.contentParams.get("charset");
            Log.e(TAG, "Found a specific charset: " + charset);
        }


        messageView.loadDataWithBaseURL(null,extractHtmlFromSinglepart(p), "text/html", charset,null);
    }

}
