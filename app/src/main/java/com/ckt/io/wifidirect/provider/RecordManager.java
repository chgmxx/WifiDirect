package com.ckt.io.wifidirect.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.ckt.io.wifidirect.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by admin on 2016/3/24.
 */
public class RecordManager implements Record.OnStateChangeListener{
    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static RecordManager manager;

    private ArrayList<Record> recordArrayList = new ArrayList<>();
    private ArrayList<OnRecordsChangedListener> listenerArrayList = new ArrayList<>();
    private HashMap<Record, Uri> uris = new HashMap<>(); //����ÿ��record��Ӧ��URI
    private Context context;

    public static RecordManager getInstance(Context context) {
        if(manager == null) {
            manager = new RecordManager(context);
        }
        return manager;
    }

    public RecordManager(Context context) {
        this.context = context;
    }

    //�������ڷ��͵ļ�¼
    public void addNewSendingRecord(ArrayList<File> list) {
        ArrayList<Record> changedRecords = new ArrayList<>();
        for(int i=0; i<list.size(); i++) {
            File f = list.get(i);
            Record record = new Record(
                    f.getPath(),
                    f.length(),
                    0,
                    Record.STATE_WAIT_FOR_TRANSPORT,
                    Record.DIRECTION_OUT);
            this.add(record, false);
            changedRecords.add(record);
        }
        //�����ص�
        for(int i=0; i<listenerArrayList.size(); i++) {
            OnRecordsChangedListener listener = listenerArrayList.get(i);
            if(listener != null) {
                listener.onRecordListChanged(ACTION_ADD, changedRecords);
            }
        }
    }

    //�������ڽ��յļ�¼
    public Record addNewRecevingRecord(File f) {
        Record record = new Record(f.getPath(),
                f.length(),
                0,
                Record.STATE_TRANSPORTING,
                Record.DIRECTION_IN);
        this.add(record);
        return record;
    }

    //��Ӽ���
    public void addOnRecordsChangedListener(OnRecordsChangedListener listener) {
        if(!listenerArrayList.contains(listener)) {
            listenerArrayList.add(listener);
        }
    }

    //�Ƴ�����
    public void removeOnRecordsChangedListener(OnRecordsChangedListener listener) {
        listenerArrayList.remove(listener);
    }

    //����record
    public void add(Record record) {
        add(record, true);
    }

    private void add(Record record, boolean isCallListener) {
        if(!recordArrayList.contains(record)) {
            record.setListener(this);
            recordArrayList.add(record);
            //�ص�����
            if(isCallListener) {
                for(int i=0; i<listenerArrayList.size(); i++) {
                    OnRecordsChangedListener listener = listenerArrayList.get(i);
                    if(listener != null) {
                        ArrayList<Record> changedRecords = new ArrayList<>();
                        changedRecords.add(0, record);
                        listener.onRecordListChanged(ACTION_ADD, changedRecords);
                    }
                }
            }
        }
    }



    //�Ƴ�record
    public void remove(Record record) {
        if(recordArrayList.remove(record)) {
            //�ص�����
            for(int i=0; i<listenerArrayList.size(); i++) {
                OnRecordsChangedListener listener = listenerArrayList.get(i);
                if(listener!=null) {
                    ArrayList<Record> changedRecords = new ArrayList<>();
                    changedRecords.add(record);
                    listener.onRecordListChanged(ACTION_REMOVE, changedRecords);
                }
            }
        }
    }

    //��ȡ ״̬Ϊstate�� record�б�
    public ArrayList<Record> getRecords(int state) {
        ArrayList<Record> ret = new ArrayList<>();
        for(int i=0; i<recordArrayList.size(); i++) {
            Record record = recordArrayList.get(i);
            if(record.getState() == state) {
                ret.add(record);
            }
        }
        return ret;
    }

    public Record findRecord(String path, int state, boolean isSend) {
        ArrayList<Record> recordList = getRecords(state);
        for(int i=0; i<recordList.size(); i++) {
            Record record = recordList.get(i);
            if(record.getTransport_direction() == Record.DIRECTION_OUT && record.getPath().equals(path)) {
                return record;
            }
        }
        return null;
    }

    //���ݿ����
    private void addToDB(Record record) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.NAME, record.getName());

        Uri uri = resolver.insert(Constants.InstanceColumns.CONTENT_URI, contentValues);
        this.uris.put(record, uri);
    }

    //record ״̬�ı�
    @Override
    public void onStateChanged(Record record, int state_old, int state_new) {
        //��recordλ�õ�������
        recordArrayList.remove(record);
        recordArrayList.add(0, record);
        //�ص�����
        for(int i=0; i<listenerArrayList.size(); i++) {
            OnRecordsChangedListener listener = listenerArrayList.get(i);
            if(listener!=null) {
                listener.onRecordChanged(record, state_old, state_new);
            }
        }
    }

    //��¼�����ı�ļ���
    public interface OnRecordsChangedListener {
        public abstract void onRecordListChanged(int action, ArrayList<Record> changedRecordList);
        public abstract void onRecordChanged(Record record, int state_old, int state_new);
    }
}
