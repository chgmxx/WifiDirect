package com.ckt.io.wifidirect.fragment;

import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;

import com.ckt.io.wifidirect.MainActivity;
import com.ckt.io.wifidirect.R;
import com.ckt.io.wifidirect.adapter.MyExpandableListViewAdapter;
import com.ckt.io.wifidirect.p2p.WifiP2pHelper;
import com.ckt.io.wifidirect.provider.Record;
import com.ckt.io.wifidirect.provider.RecordManager;
import com.ckt.io.wifidirect.utils.FileResLoaderUtils;
import com.ckt.io.wifidirect.utils.FileTypeUtils;
import com.ckt.io.wifidirect.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by admin on 2016/3/16.
 */
public class HistoryFragment extends Fragment implements
        RecordManager.OnRecordsChangedListener, FileResLoaderUtils.OnLoadFinishedListener{

    public static final int RECEVING_GROUP = R.string.group_recevieing_task;
    public static final int SENDING_GROUP = R.string.group_sending_task;
    public static final int FINISHED_GROUP = R.string.group_finished_task;
    public static final int FAILED_GROUP = R.string.group_failed_task;
    public static final int PAUSED_GROUP = R.string.group_paused_task;
    static final int groups [] = new int[]{SENDING_GROUP, RECEVING_GROUP, PAUSED_GROUP, FAILED_GROUP, FINISHED_GROUP};

    private ExpandableListView expandableListView;
    private MyExpandableListViewAdapter adapter;

    private FileResLoaderUtils drawLoader;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.history_fragment, container, false);
        expandableListView = (ExpandableListView) view.findViewById(R.id.expand_listview);
        boolean isFirstOnCrate = false;
        if(adapter == null) {
            //��ӷ���,������ʱ�����ÿ�����������item
            ArrayList<Integer> groupIds = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            ArrayList<ArrayList<Record>> records = new ArrayList<>();
            for(int i=0; i<groups.length; i++) {
                ArrayList<Record> recordList = new ArrayList<>();
//                recordList.addAll(getGroupRecordFromRecordManager(groups[i]));
                groupIds.add(groups[i]);
                names.add(getResources().getString(groups[i]));
                records.add(recordList);
            }

            adapter = new MyExpandableListViewAdapter(getContext(), groupIds, names, records);
            RecordManager.getInstance(getContext()).addOnRecordsChangedListener(this);//ע�����
            isFirstOnCrate = true;
            drawLoader = FileResLoaderUtils.getInstance(this);

            expandableListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) { //stop
                        expandableListView.setTag(false);
                        ((BaseAdapter) expandableListView.getAdapter()).notifyDataSetChanged();
                    } else { //scrolling
                        expandableListView.setTag(true);
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
            });
            loadDrawable();
        }
        expandableListView.setChildDivider(getContext().getResources().getDrawable(R.drawable.expandablelistview_child_divider));
        expandableListView.setAdapter(adapter);
        //Ĭ��չ�� ���ڷ��� �� ���ڽ��յ�  ����
        if(isFirstOnCrate) {
            expandGroup(SENDING_GROUP);
            expandGroup(RECEVING_GROUP);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateExpandableListView();
    }

    //����ÿ������
    public void updateExpandableListView() {
        if(adapter == null) return;
        for(int i=0; i<adapter.getGroupList().size(); i++) {
            MyExpandableListViewAdapter.ExpandableListViewGroup group = adapter.getGroupList().get(i);
            group.getRecordList().clear();
            group.getRecordList().addAll(getGroupRecordFromRecordManager(groups[i]));
        }
        adapter.notifyDataSetChanged();
    }

    public ArrayList<Record> getGroupRecordFromRecordManager(int id) {
        RecordManager manager = RecordManager.getInstance(getContext());
        ArrayList<Record> ret = null;
        if(id == SENDING_GROUP) {
            ret = new ArrayList<>();
            ArrayList<Record> temp = manager.getRecords(Record.STATE_TRANSPORTING);
            for(int i=0; i<temp.size(); i++) {
                Record record = temp.get(i);
                if(record.isSend()) {
                    ret.add(record);
                }
            }
            temp = manager.getRecords(Record.STATE_WAIT_FOR_TRANSPORT);
            for(int i=0; i<temp.size(); i++) {
                Record record = temp.get(i);
                if(record.isSend()) {
                    ret.add(record);
                }
            }
        }else if(id == RECEVING_GROUP) {
            ret = new ArrayList<>();
            ArrayList<Record> temp = manager.getRecords(Record.STATE_TRANSPORTING);
            for(int i=0; i<temp.size(); i++) {
                Record record = temp.get(i);
                if(!record.isSend()) {
                    ret.add(record);
                }
            }
            temp = manager.getRecords(Record.STATE_WAIT_FOR_TRANSPORT);
            for(int i=0; i<temp.size(); i++) {
                Record record = temp.get(i);
                if(!record.isSend()) {
                    ret.add(record);
                }
            }
        }else if(id == PAUSED_GROUP) {
            ret = manager.getRecords(Record.STATE_PAUSED);
        }else if(id == FAILED_GROUP) {
            ret = manager.getRecords(Record.STATE_FAILED);
        }else if(id == FINISHED_GROUP) {
            ret = manager.getRecords(Record.STATE_FINISHED);
        }
        return ret;
    }

    //չ����Ӧ�ķ���
    public void expandGroup(int nameStrId) {
        for(int i=0; i<adapter.getGroupList().size(); i++) {
            String s = getResources().getString(nameStrId);
            MyExpandableListViewAdapter.ExpandableListViewGroup group = adapter.getGroupList().get(i);
            if(group.getName().equals(s)) {
                expandableListView.expandGroup(i, true);
                break;
            }
        }
    }

    public MyExpandableListViewAdapter.ExpandableListViewGroup getOwnerGroup(Record record) {
        int groupId = -1;
        switch (record.getState()) {
            case Record.STATE_TRANSPORTING:
            case Record.STATE_WAIT_FOR_TRANSPORT:
                if (record.isSend()) {
                    groupId = SENDING_GROUP;
                } else {
                    groupId = RECEVING_GROUP;
                }
                break;
            case Record.STATE_FAILED:
                groupId = FAILED_GROUP;
                break;
            case Record.STATE_FINISHED:
                groupId = FINISHED_GROUP;
                break;
            case Record.STATE_PAUSED:
                groupId = PAUSED_GROUP;
                break;
        }
        return adapter.getGroupById(groupId);
    }

    public void loadDrawable() {
        for(int i=0; i<adapter.getGroupList().size(); i++) {
            MyExpandableListViewAdapter.ExpandableListViewGroup group = adapter.getGroupList().get(i);
            for(int j=0; j<group.getRecordList().size(); j++) {
                Record record = group.getRecordList().get(j);
                drawLoader.load(getContext(), record.getPath());
            }
        }
    }

    //************������һЩ�ص�**************************************************************
    @Override
    public void onRecordListChanged(int action, ArrayList<Record> changedRecordList) {
        if(adapter == null) return;
        if(action == RecordManager.ACTION_ADD) {
           for(int i=0; i<changedRecordList.size(); i++) {
               Record record = changedRecordList.get(i);
               int state = record.getState();
               if(state == Record.STATE_WAIT_FOR_TRANSPORT || state == Record.STATE_TRANSPORTING) {
                   MyExpandableListViewAdapter.ExpandableListViewGroup group = getOwnerGroup(record);
                   if(this.isResumed()) {
                       expandableListView.expandGroup(adapter.getGroupPostion(group), true);
                   }
               }
           }
        }

        updateExpandableListView();
        LogUtils.i(WifiP2pHelper.TAG, "onRecordListChanged");
    }

    @Override
    public void onRecordChanged(Record record, int state_old, int state_new) {
        if(state_new == Record.STATE_FINISHED) {
            drawLoader.load(getContext(), record.getPath());
        }
        updateExpandableListView();
        LogUtils.i(WifiP2pHelper.TAG, "onRecordChanged");
    }

    @Override
    public void onRecordDataChanged(Record record) {
        Object object = expandableListView.getTag();
        if(object == null || !(boolean)(object)) { //expandableListViewû�л���
            ((BaseAdapter)(expandableListView.getAdapter())).notifyDataSetChanged();
        }
    }

    //�������һ��ͼƬ�Ļص�
    @Override
    public void onLoadOneFinished(String path, Object obj, boolean isAllFinished) {
        Object object = expandableListView.getTag();
        if(object == null || !(boolean)(object)) { //expandableListViewû�л���
            ((BaseAdapter)(expandableListView.getAdapter())).notifyDataSetChanged();
        }
    }
}
