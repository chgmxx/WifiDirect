package com.ckt.io.wifidirect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.RemoteViews;

import com.ckt.io.wifidirect.p2p.WifiP2pServer;
import com.ckt.io.wifidirect.p2p.WifiP2pState;
import com.ckt.io.wifidirect.p2p.WifiTransferManager;
import com.ckt.io.wifidirect.utils.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by guotao.deng on 2016/7/28.
 */
public class WifiDirectService extends Service implements WifiP2pState.OnConnectStateChangeListener
        , WifiTransferManager.FileSendStateListener, WifiTransferManager.FileReceiveStateListener {

    private static final String TAG = "WifiDirectService";

    private static final String[] PROJECTION = {Constants.InstanceColumns.ID, Constants.InstanceColumns.NAME,
            Constants.InstanceColumns.PATH, Constants.InstanceColumns.STATE, Constants.InstanceColumns.TRANSFER_LENGTH,
            Constants.InstanceColumns.TRANSFER_DIRECTION, Constants.InstanceColumns.TRANSFER_MAC};

    private Object mLock = new Object();

    private WifiP2pState mP2pState;
    private WifiP2pServer mServer;
    private WifiTransferManager mWifiTransferManager;
    private WifiP2pState.ConnectedDeviceInfo mConnectedDeviceInfo;

    private UpdateTask mUpdateTask;
    private boolean mPendingUpdate;

    private NotificationManager nm;

    private ContentObserver contentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateFromProvider();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //maybe will use
        return null;
    }

    @Override
    public void onCreate() {
        LogUtils.d(TAG, "WifiDirectService onCreate()");
        mP2pState = WifiP2pState.getInstance(getApplicationContext());
        mP2pState.registerOnConnectChangeListener(this);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        getContentResolver().registerContentObserver(Constants.InstanceColumns.CONTENT_URI,
                true, contentObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mServer != null) {
            mServer.interrupt();
        }
        getContentResolver().unregisterContentObserver(contentObserver);
    }

    private void updateFromProvider() {
        synchronized (mLock) {
            mPendingUpdate = true;
            if (mUpdateTask == null && mP2pState.isConnected()) {
                mUpdateTask = new UpdateTask();
                mUpdateTask.run();
            }
        }
    }

    @Override
    public void onConnected(WifiP2pState.ConnectedDeviceInfo connectedDeviceInfo) {
        mConnectedDeviceInfo = connectedDeviceInfo;
        mWifiTransferManager = new WifiTransferManager(this, mConnectedDeviceInfo.connectedDeviceAddr,
                Constants.PORT, mP2pState.getThisDevice(), this, this);
        mServer = new WifiP2pServer(mWifiTransferManager);
        mServer.startListen();
        updateFromProvider();
    }

    @Override
    public void onDisConnected() {
        if (mServer != null) {
            mServer.interrupt();
        }
        mWifiTransferManager = null;
        mConnectedDeviceInfo = null;
    }

    @Override
    public void onStart(TransferFileInfo info) {
        updateNotification(info);
    }

    @Override
    public void onUpdate(ArrayList<WifiTransferManager.DataTranferTask> taskList) {
        for (WifiTransferManager.DataTranferTask task : taskList) {
            updateNotification(task.transferFileInfo);
        }
    }

    @Override
    public void onFinished(TransferFileInfo info, boolean ret) {
        updateNotification(info);
    }

    private void updateNotification(TransferFileInfo transferFileInfo) {
        synchronized (nm) {
            Notification.Builder builder = new Notification.Builder(this);
            RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.transfer_notification_layout);
            switch (transferFileInfo.state) {
                case Constants.State.STATE_TRANSFERING:
                    remoteViews.setTextViewText(R.id.title, transferFileInfo.direction == Constants.DIRECTION_OUT ?
                            "sending" : "receiving");
                    break;
                case Constants.State.STATE_TRANSFER_DONE:
                    remoteViews.setTextViewText(R.id.title, "done");
                case Constants.State.STATE_TRANSFER_FAILED:
                    remoteViews.setTextViewText(R.id.title, "fail");
                default:
                    remoteViews.setTextViewText(R.id.title, "unknow");
            }

            remoteViews.setTextViewText(R.id.file_name, transferFileInfo.name);
            remoteViews.setProgressBar(R.id.progress, 100,
                    (int) (transferFileInfo.transferedLength / transferFileInfo.length * 100), false);
            builder.setContent(remoteViews);
            nm.notify(transferFileInfo.id, builder.build());
        }
    }

    private class UpdateTask extends Thread {

        @Override
        public void run() {
            if (!mP2pState.isConnected()) {
                mUpdateTask = null;
                return;
            }
            for (; ; ) {
                synchronized (mLock) {
                    if (mUpdateTask != this) {
                        LogUtils.e(TAG, "error in update task.");
                        return;
                    }
                    if (!mPendingUpdate) {
                        mUpdateTask = null;
                        return;
                    }
                    mPendingUpdate = false;
                }
                Cursor cursor = getContentResolver().query(Constants.InstanceColumns.CONTENT_URI, PROJECTION,
                        Constants.InstanceColumns.TRANSFER_DIRECTION + "=?",
                        new String[]{Constants.DIRECTION_OUT + ""}, Constants.InstanceColumns.ID);
                if (cursor == null) {
                    LogUtils.d(TAG, "cursor is null");
                    continue;
                }
                cursor.moveToFirst();
                while (!cursor.isAfterLast() && mP2pState.isConnected()) {
                    int state = cursor.getInt(cursor.getColumnIndex(Constants.InstanceColumns.STATE));
                    if (state == Constants.State.STATE_IDEL) {
                        String mac = cursor.getString(cursor.getColumnIndex(Constants.InstanceColumns.TRANSFER_MAC));
                        if (mConnectedDeviceInfo.connectedDevice.deviceAddress.equals(mac)) {
                            TransferFileInfo transferFileInfo = new TransferFileInfo(cursor, getContentResolver());
                            LogUtils.d(TAG,"transfer file "+transferFileInfo.name+" to "+mac);
                            mWifiTransferManager.sendFile(transferFileInfo);
                        }
                    }
                    cursor.moveToNext();
                }
                cursor.close();
            }
        }
    }
}
