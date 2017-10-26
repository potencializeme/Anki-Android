/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.async;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Xml;

import com.ankipro.model.Produto;
import com.ichi2.anki.AnkiProApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.MediaSyncException;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sync.FullSyncer;
import com.ichi2.libanki.sync.HttpSyncer;
import com.ichi2.libanki.sync.MediaSyncer;
import com.ichi2.libanki.sync.RemoteMediaServer;
import com.ichi2.libanki.sync.RemoteServer;
import com.ichi2.libanki.sync.Syncer;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class Connection extends BaseAsyncTask<Connection.Payload, Object, Connection.Payload> {

    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_SYNC = 1;
    public static final int TASK_TYPE_LOGIN_NINJA =2;
    public static final int CONN_TIMEOUT = 30000;

    private static final String ns = null;

    private static Connection sInstance;
    private TaskListener mListener;
    private static boolean sIsCancelled;
    private static boolean sIsCancellable;

    /**
     * Before syncing, we acquire a wake lock and then release it once the sync is complete.
     * This ensures that the device remains awake until the sync is complete. Without it,
     * the process will be paused and the sync can fail due to timing conflicts with AnkiWeb.
     */
    private final PowerManager.WakeLock mWakeLock;

    public static synchronized boolean getIsCancelled() {
        return sIsCancelled;
    }

    public Connection() {
        sIsCancelled = false;
        sIsCancellable = false;
        Context context = AnkiProApp.getInstance().getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Connection");
    }

    private static Connection launchConnectionTask(TaskListener listener, Payload data) {

        if (!isOnline()) {
            data.success = false;
            listener.onDisconnected();
            return null;
        }

        try {
            if ((sInstance != null) && (sInstance.getStatus() != Status.FINISHED)) {
                sInstance.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sInstance = new Connection();
        sInstance.mListener = listener;

        sInstance.execute(data);
        return sInstance;
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onCancelled() {
        super.onCancelled();
        Timber.i("Connection onCancelled() method called");
        // Sync has ended so release the wake lock
        mWakeLock.release();
        if (mListener instanceof CancellableTaskListener) {
            ((CancellableTaskListener) mListener).onCancelled();
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // Acquire the wake lock before syncing to ensure CPU remains on until the sync completes.
        mWakeLock.acquire();
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    /*
     * Runs on GUI thread
     */
    protected void onPostExecute(Payload data) {
        super.onPostExecute(data);
        // Sync has ended so release the wake lock
        mWakeLock.release();
        if (mListener != null) {
            mListener.onPostExecute(data);
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onProgressUpdate(values);
        }
    }


    public static Connection login(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_LOGIN;
        return launchConnectionTask(listener, data);
    }


    public static Connection sync(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC;
        return launchConnectionTask(listener, data);
    }


    protected Payload doInBackground(Payload... params) {
        super.doInBackground(params);
        if (params.length != 1) {
            throw new IllegalArgumentException();
        }
        return doOneInBackground(params[0]);
    }


    private Payload doOneInBackground(Payload data) {
        switch (data.taskType) {
            case TASK_TYPE_LOGIN:
                return doInBackgroundLogin(data);

            case TASK_TYPE_SYNC:
                return doInBackgroundSync(data);

            case TASK_TYPE_LOGIN_NINJA:
                return doInBackgroundRefreshProducts(data);
            default:
                return null;
        }
    }


    private Payload doInBackgroundLogin(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        String onlineUrl = "http://ankipro.com/ws";
        HttpURLConnection urlConnection = null;
        try {
            String charset = "UTF-8";
            String query = String.format("username=%s&passwd=%s",
                    URLEncoder.encode(username, charset),
                    URLEncoder.encode(password, charset));
            //String dataUrlParameters = "?username="+username+"&password="+password;

            URL url = new URL(onlineUrl+ "?" + query);//use a proper url instead of onlineUrl
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept-Charset", charset);
            urlConnection.setDoInput(true); // true if we want to read server's response
            urlConnection.setDoOutput(false); // false indicates this is a GET request
            urlConnection.setRequestProperty("Content-Type", "text/xml");


            int responseCode = urlConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                data.success = false;
                data.result = new Object[]{"error", urlConnection.getResponseCode(), urlConnection.getResponseMessage()};
                return data;
            }

            InputStream inputStream = urlConnection.getInputStream();
            StringBuilder result = new StringBuilder();

            try {

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = "";

                while ((line = bufferedReader.readLine()) != null)
                    result.append(line);
            }finally {
                inputStream.close();

            }

            if (result.toString().contains("<products>")){
                boolean valid = false;
                try {
                    Timber.d( "doInBackgroundLoginNinja() responseText");
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    InputStream ins = new ByteArrayInputStream(result.toString().getBytes());

                    parser.setInput(ins, null);

                    List <Produto> produtoList = readProdutos(parser);
                    data.result = produtoList;
                    valid = true;
                } finally {
                    inputStream.close();
                }

                data.success = true;
                data.data = new String[] { username, "ninjalogado",password };
            } else {
                data.success = false;

                if(result.toString().contains("blocked")){
                    data.returnType = 401;
                    data.result = new Object[] {result.toString()};
                }else{
                    data.returnType = responseCode;
                }

            }
            return data;

        }  catch (IOException | XmlPullParserException e2) {
            // Ask user to report all bugs which aren't timeout errors
            Timber.e("doInBackgroundLoginNinja - error: "+e2.getMessage());
            if (!timeoutOccured(e2)) {
                AnkiProApp.sendExceptionReport(e2, "doInBackgroundLogin");
            }
            data.success = false;
            data.result = new Object[] {"ninjaConnectionError" };
            return data;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }


    private boolean timeoutOccured(Exception e) {
        String msg = e.getMessage();
        return msg.contains("UnknownHostException") ||
                msg.contains("HttpHostConnectException") ||
                msg.contains("SSLException while building HttpClient") ||
                msg.contains("SocketTimeoutException") ||
                msg.contains("ClientProtocolException") ||
                msg.contains("TimeoutException");
    }


    private Payload doInBackgroundSync(Payload data) {
        sIsCancellable = true;
        Timber.d("doInBackgroundSync()");
        // Block execution until any previous background task finishes, or timeout after 5s
        boolean ok = DeckTask.waitToFinish(5);

        String hkey = (String) data.data[0];
        boolean media = (Boolean) data.data[1];
        String conflictResolution = (String) data.data[2];
        // Use safe version that catches exceptions so that full sync is still possible
        Collection col = CollectionHelper.getInstance().getColSafe(AnkiProApp.getInstance());

        boolean colCorruptFullSync = false;
        if (!CollectionHelper.getInstance().colIsOpen() || !ok) {
            if (conflictResolution != null && conflictResolution.equals("download")) {
                colCorruptFullSync = true;
            } else {
                data.success = false;
                data.result = new Object[] { "genericError" };
                return data;
            }
        }
        try {
            CollectionHelper.getInstance().lockCollection();
            HttpSyncer server = new RemoteServer(this, hkey);
            Syncer client = new Syncer(col, server);

            // run sync and check state
            boolean noChanges = false;
            if (conflictResolution == null) {
                Timber.i("Sync - starting sync");
                publishProgress(R.string.sync_prepare_syncing);
                Object[] ret = client.sync(this);
                data.message = client.getSyncMsg();
                if (ret == null) {
                    data.success = false;
                    data.result = new Object[] { "genericError" };
                    return data;
                }
                String retCode = (String) ret[0];
                if (!retCode.equals("noChanges") && !retCode.equals("success")) {
                    data.success = false;
                    data.result = ret;
                    // Check if there was a sanity check error
                    if (retCode.equals("sanityCheckError")) {
                        // Force full sync next time
                        col.modSchemaNoCheck();
                        col.save();
                    }
                    return data;
                }
                // save and note success state
                if (retCode.equals("noChanges")) {
                    // publishProgress(R.string.sync_no_changes_message);
                    noChanges = true;
                } else {
                    // publishProgress(R.string.sync_database_acknowledge);
                }
            } else {
                try {
                    // Disable sync cancellation for full-sync
                    sIsCancellable = false;
                    server = new FullSyncer(col, hkey, this);
                    if (conflictResolution.equals("upload")) {
                        Timber.i("Sync - fullsync - upload collection");
                        publishProgress(R.string.sync_preparing_full_sync_message);
                        Object[] ret = server.upload();
                        col.reopen();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            return data;
                        }
                        if (!ret[0].equals(HttpSyncer.ANKIWEB_STATUS_OK)) {
                            data.success = false;
                            data.result = ret;
                            return data;
                        }
                    } else if (conflictResolution.equals("download")) {
                        Timber.i("Sync - fullsync - download collection");
                        publishProgress(R.string.sync_downloading_message);
                        Object[] ret = server.download();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            return data;
                        }
                        if (ret[0].equals("success")) {
                            data.success = true;
                            col.reopen();
                        }
                        if (!ret[0].equals("success")) {
                            data.success = false;
                            data.result = ret;
                            if (!colCorruptFullSync) {
                                col.reopen();
                            }
                            return data;
                        }
                    }
                } catch (OutOfMemoryError e) {
                    AnkiProApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                    data.success = false;
                    data.result = new Object[] { "OutOfMemoryError" };
                    return data;
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else if (e.getMessage().equals("UserAbortedSync")) {
                        data.result = new Object[] {"UserAbortedSync" };
                    } else {
                        AnkiProApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                        data.result = new Object[] { "IOException" };
                    }
                    data.success = false;
                    return data;
                }
            }

            // clear undo to avoid non syncing orphans (because undo resets usn too
            if (!noChanges) {
                col.clearUndo();
            }
            // then move on to media sync
            sIsCancellable = true;
            boolean noMediaChanges = false;
            String mediaError = null;
            if (media) {
                server = new RemoteMediaServer(col, hkey, this);
                MediaSyncer mediaClient = new MediaSyncer(col, (RemoteMediaServer) server, this);
                String ret;
                try {
                    ret = mediaClient.sync();
                    if (ret == null) {
                        mediaError = AnkiProApp.getAppResources().getString(R.string.sync_media_error);
                    } else {
                        if (ret.equals("noChanges")) {
                            publishProgress(R.string.sync_media_no_changes);
                            noMediaChanges = true;
                        }
                        if (ret.equals("sanityFailed")) {
                            mediaError = AnkiProApp.getAppResources().getString(R.string.sync_media_sanity_failed);
                        } else {
                            publishProgress(R.string.sync_media_success);
                        }
                    }
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else if (e.getMessage().equals("UserAbortedSync")) {
                        data.result = new Object[] {"UserAbortedSync" };
                    } else {
                        AnkiProApp.sendExceptionReport(e, "doInBackgroundSync-mediaSync");
                    }
                    mediaError = e.getLocalizedMessage();
                }
            }
            if (noChanges && (!media || noMediaChanges)) {
                data.success = false;
                data.result = new Object[] { "noChanges" };
                return data;
            } else {
                data.success = true;
                data.data = new Object[] { conflictResolution, col, mediaError };
                return data;
            }
        } catch (MediaSyncException e) {
            Timber.e("Media sync rejected by server");
            data.success = false;
            data.result = new Object[] {"mediaSyncServerError"};
            AnkiProApp.sendExceptionReport(e, "doInBackgroundSync");
            return data;
        } catch (UnknownHttpResponseException e) {
            Timber.e("doInBackgroundSync -- unknown response code error");
            e.printStackTrace();
            data.success = false;
            Integer code = e.getResponseCode();
            String msg = e.getLocalizedMessage();
            data.result = new Object[] { "error", code , msg };
            return data;
        } catch (Exception e) {
            // Global error catcher.
            // Try to give a human readable error, otherwise print the raw error message
            Timber.e("doInBackgroundSync error");
            e.printStackTrace();
            data.success = false;
            if (timeoutOccured(e)) {
                data.result = new Object[]{"connectionError"};
            } else if (e.getMessage().equals("UserAbortedSync")) {
                data.result = new Object[] {"UserAbortedSync" };
            } else {
                AnkiProApp.sendExceptionReport(e, "doInBackgroundSync");
                data.result = new Object[] {e.getLocalizedMessage()};
            }
            return data;
        } finally {
            // don't bump mod time unless we explicitly save
            if (col != null) {
                col.close(false);
            }
            CollectionHelper.getInstance().unlockCollection();
        }
    }


    public void publishProgress(int id) {
        super.publishProgress(id);
    }


    public void publishProgress(String message) {
        super.publishProgress(message);
    }


    public void publishProgress(int id, long up, long down) {
        super.publishProgress(id, up, down);
    }

    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) AnkiProApp.getInstance().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected() || !netInfo.isAvailable()) {
            return false;
        }
        return true;
    }


    public static interface TaskListener {
        public void onPreExecute();


        public void onProgressUpdate(Object... values);


        public void onPostExecute(Payload data);


        public void onDisconnected();
    }

    public static interface CancellableTaskListener extends TaskListener {
        public void onCancelled();
    }

    public static class Payload{
        public int taskType;
        public Object[] data;
        public Object result;
        public boolean success;
        public int returnType;
        public Exception exception;
        public String message;
        public Collection col;


        public Payload() {
            data = null;
            success = true;
        }


        public Payload(Object[] data) {
            this.data = data;
            success = true;
        }


        public Payload(int taskType, Object[] data) {
            this.taskType = taskType;
            this.data = data;
            success = true;
        }

        public Payload(int taskType, Object[] data, String path) {
            this.taskType = taskType;
            this.data = data;
            success = true;
        }
    }

    public synchronized static void cancel() {
        Timber.d("Cancelled Connection task");
        sInstance.cancel(true);
        sIsCancelled = true;
    }

    public synchronized static boolean isCancellable() {
        return sIsCancellable;
    }

    public class CancelCallback {
        private WeakReference<ThreadSafeClientConnManager> mConnectionManager = null;


        public void setConnectionManager(ThreadSafeClientConnManager connectionManager) {
            mConnectionManager = new WeakReference<>(connectionManager);
        }


        public void cancelAllConnections() {
            Timber.d("cancelAllConnections()");
            if (mConnectionManager != null) {
                ThreadSafeClientConnManager connectionManager = mConnectionManager.get();
                if (connectionManager != null) {
                    connectionManager.shutdown();
                }
            }
        }
    }

    private List<Produto> readProdutos(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        Timber.d("ConcurNinjaConnection > readProdutos");
        List<Produto> productList = new ArrayList<>();

        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, ns, "products");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tag = parser.getName();

            if (tag.equals("products_from_mine")) {
                parser.require(XmlPullParser.START_TAG, ns, "products_from_mine");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }



                    parser.require(XmlPullParser.START_TAG, ns, "product_from_mine");
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.getEventType() != XmlPullParser.START_TAG) {
                            continue;
                        }
                        Timber.d("tag == 'product_from_mine'");
                        Produto pr = readProduto(parser);
                        pr.setMine(true);
                        productList.add(pr);
                    }
                }

            } else if (tag.equals("products_from_other")) {
                parser.require(XmlPullParser.START_TAG, ns, "products_from_other");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    parser.require(XmlPullParser.START_TAG, ns, "product_from_other");
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.getEventType() != XmlPullParser.START_TAG) {
                            continue;
                        }
                        Timber.d("tag == 'product_from_other'");

                        Produto pr = readProduto(parser);
                        pr.setMine(false);
                        productList.add(pr);
                    }
                }


            } else {
                skip(parser);
            }

        }

        return productList;
    }
    private Produto readProduto(XmlPullParser parser)
            throws XmlPullParserException, IOException {


        Timber.d("ConcurNinjaConnection > readFeed");
        Produto produto = new Produto();

        String tag = parser.getName();

        int prod_id = 0;
        String prod_key = "";

        if (tag.equals("id")) {
            Timber.d("tag == 'id'");
            produto.setId(Integer.parseInt(readId(parser)));

            parser.nextTag();
            tag = parser.getName();
            if (tag.equals("name")) {
                Timber.d("tag == 'name'");
                produto.setKey(readKey(parser));

            } else {
                skip(parser);
            }
        }


        return produto;
    }
    // Processes title tags in the feed.
    private String readId(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "id");
        String str_key = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "id");
        return str_key;
    }

    private String readKey(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String title = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return title;
    }

    // For the tags title and summary, extracts their text values.
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public static Connection refreshProducts(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_LOGIN_NINJA;
        return launchConnectionTask(listener, data);
    }

    private Payload doInBackgroundRefreshProducts(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];

        String onlineUrl = "http://ankipro.com/ws";
        HttpURLConnection urlConnection = null;
        try {
            String charset = "UTF-8";
            String query = String.format("username=%s&passwd=%s",
                    URLEncoder.encode(username, charset),
                    URLEncoder.encode(password, charset));
            //String dataUrlParameters = "?username="+username+"&password="+password;

            URL url = new URL(onlineUrl+ "?" + query);//use a proper url instead of onlineUrl
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept-Charset", charset);
            urlConnection.setDoInput(true); // true if we want to read server's response
            urlConnection.setDoOutput(false); // false indicates this is a GET request
            urlConnection.setRequestProperty("Content-Type", "text/xml");


            int responseCode = urlConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                data.success = false;
                data.result = new Object[]{"error", urlConnection.getResponseCode(), urlConnection.getResponseMessage()};
                return data;
            }

            InputStream inputStream = urlConnection.getInputStream();
            StringBuilder result = new StringBuilder();

            try {

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = "";

                while ((line = bufferedReader.readLine()) != null)
                    result.append(line);
            }finally {
                inputStream.close();

            }

            if (result.toString().contains("<products>")){
                boolean valid = false;
                try {
                    Timber.d( "doInBackgroundLoginNinja() responseText");
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    InputStream ins = new ByteArrayInputStream(result.toString().getBytes());

                    parser.setInput(ins, null);


                    data.result = readProdutos(parser);
                    valid = true;
                } finally {
                    inputStream.close();
                }

                data.success = true;
                data.data = new String[] { username, "ninjalogado" };
            } else {
                data.success = false;

                if(result.toString().contains("blocked")){
                    data.returnType = 401;
                    data.result = new Object[] {result.toString()};
                }else{
                    data.returnType = responseCode;
                }
            }

            return data;

        }  catch (IOException | XmlPullParserException e2) {
            // Ask user to report all bugs which aren't timeout errors
            Timber.e("doInBackgroundLoginNinja - error: "+e2.getMessage());
            if (!timeoutOccured(e2)) {
                AnkiProApp.sendExceptionReport(e2, "doInBackgroundLogin");
            }
            data.success = false;
            data.result = new Object[] {"ninjaConnectionError" };
            return data;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
