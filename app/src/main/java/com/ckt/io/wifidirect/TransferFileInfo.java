package com.ckt.io.wifidirect;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.ckt.io.wifidirect.utils.LogUtils;

import java.io.File;

/**
 * Created by admin on 2016/8/10.
 */

public class TransferFileInfo {

    private static final String TAG = "TransferFileInfo";

    public int id;
    public String path;
    public String name;
    public long length = 0;
    public int state;
    public long transferLength = 0;
    public int direction;
    public String transferMac;

    private ContentResolver mContentResolver;
    private Uri uri;

    public TransferFileInfo(Cursor cursor, ContentResolver contentResolver) {
        id = cursor.getInt(cursor.getColumnIndex(Constants.InstanceColumns.ID));
        if (id > 0) {
            uri = ContentUris.withAppendedId(Constants.InstanceColumns.CONTENT_URI, id);
        }
        name = cursor.getString(cursor.getColumnIndex(Constants.InstanceColumns.NAME));
        path = cursor.getString(cursor.getColumnIndex(Constants.InstanceColumns.PATH));
        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                //length = file.get
                length = file.length();
            }
        }
        state = cursor.getInt(cursor.getColumnIndex(Constants.InstanceColumns.STATE));
        transferLength = cursor.getLong(cursor.getColumnIndex(Constants.InstanceColumns.TRANSFER_LENGTH));
        direction = cursor.getInt(cursor.getColumnIndex(Constants.InstanceColumns.TRANSFER_DIRECTION));
        transferMac = cursor.getString(cursor.getColumnIndex(Constants.InstanceColumns.TRANSFER_MAC));
        mContentResolver = contentResolver;
    }

    public boolean updateState(int state) {
        if (uri == null) {
            LogUtils.e(TAG, "update state failed, reson: uri is null");
            return false;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.STATE, state);
        mContentResolver.update(uri, contentValues, null, null);
        return true;
    }

    public boolean updateTransferSize(long size) {
        if (uri == null) {
            LogUtils.e(TAG, "update state failed, reson: uri is null");
            return false;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.TRANSFER_LENGTH, size);
        mContentResolver.update(uri, contentValues, null, null);
        return true;
    }
}