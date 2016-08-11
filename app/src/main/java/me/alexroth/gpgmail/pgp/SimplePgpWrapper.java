package me.alexroth.gpgmail.pgp;

import android.content.Context;
import android.content.Intent;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Acts as a very simple wrapper around the OpenPGP API. It also generates the base intents for some actions.
 *
 * @author alex
 * @since 8/10/16
 */
public class SimplePgpWrapper {

    public interface ConnectionCallback{
        void success(OpenPgpApi api);
        void failed(String error);
    }


    public Context appContext;
    public OpenPgpServiceConnection serviceConnection;
    public OpenPgpApi pgpApi;

    public String defaultProviderName = "org.sufficientlysecure.keychain";

    public SimplePgpWrapper(Context appContext){
        this.appContext = appContext;
    }

    public void connect(final ConnectionCallback callback){
        if(serviceConnection != null && serviceConnection.isBound()){
            callback.success(pgpApi);
        }else {
            serviceConnection = new OpenPgpServiceConnection(appContext, defaultProviderName, new OpenPgpServiceConnection.OnBound() {
                @Override
                public void onBound(IOpenPgpService2 service) {
                    pgpApi = new OpenPgpApi(appContext,service);
                    callback.success(pgpApi);
                }

                @Override
                public void onError(Exception e) {
                    callback.failed(e.getLocalizedMessage());
                }
            });
            serviceConnection.bindToService();
        }
    }

    public void disconnect(){
        pgpApi = null;
        if(serviceConnection.isBound()) {
            serviceConnection.unbindFromService();
        }
    }





}
