package com.ckt.io.wifidirect.provider;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by admin on 2016/3/24.
 */
public class RecordManager implements Record.OnStateChangeListener{
    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static RecordManager manager;

    private ArrayList<Record> recordArrayList = new ArrayList<>();
    private ArrayList<OnRecordsChangedListener> listenerArrayList = new ArrayList<>();

    public static RecordManager getInstance() {
        if(manager == null) {
            manager = new RecordManager();
        }
        return manager;
    }

    public void addOnRecordsChangedListener(OnRecordsChangedListener listener) {
        if(!listenerArrayList.contains(listener)) {
            listenerArrayList.add(listener);
        }
    }

    public void removeOnRecordsChangedListener(OnRecordsChangedListener listener) {
        listenerArrayList.remove(listener);
    }

    //����record
    public void add(Record record) {
        if(!recordArrayList.contains(record)) {
            recordArrayList.add(record);
            //�ص�����
            for(int i=0; i<listenerArrayList.size(); i++) {
                OnRecordsChangedListener listener = listenerArrayList.get(i);
                if(listener != null) {
                    ArrayList<Record> changedRecords = new ArrayList<>();
                    changedRecords.add(record);
                    listener.onRecordListChanged(ACTION_ADD, changedRecords);
                }
            }
        }
    }

    //�������ڷ��͵ļ�¼
    public void addNewSendingRecord(ArrayList<File> list) {
        for(int i=0; i<list.size(); i++) {
            File f = list.get(i);
            Record record = new Record(
                    f.getPath(),
                    f.length(),
                    0,
                    Record.STATE_WAIT_FOR_TRANSPORT,
                    0,
                    true);
            this.add(record);
        }
    }

    //�Ƴ�
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

    //record ״̬�ı�
    @Override
    public void onStateChanged(Record record, int state_old, int state_new) {
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
