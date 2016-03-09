package com.ckt.io.wifidirect.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.ckt.io.wifidirect.R;

import java.util.ArrayList;

/**
 * Created by admin on 2016/3/8.
 */
public class MyListViewAdapter extends BaseAdapter {
    private ArrayList<String> mNameList;
    private ArrayList<Drawable> mIconList;
    private LayoutInflater inflater;
    private Context mContext;

    public MyListViewAdapter() {
        super();
    }

    public MyListViewAdapter(Context context, ArrayList<String> nameList, ArrayList<Drawable> iconList) {
        super();
        mNameList = nameList;
        mIconList = iconList;
        mContext = context;
        inflater = LayoutInflater.from(mContext);
    }

    @Override
    public int getCount() {
        return mNameList.size();
    }

    @Override
    public Object getItem(int position) {
        return mNameList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemViewTag viewTag;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.listview_item, null);
            viewTag = new ItemViewTag((ImageView) convertView.findViewById(R.id.list_item_Image),
                    (TextView) convertView.findViewById(R.id.list_item_Title));
            convertView.setTag(viewTag);
        } else {
            viewTag = (ItemViewTag) convertView.getTag();
        }
        viewTag.mName.setText(mNameList.get(position));
        viewTag.mIcon.setImageDrawable(mIconList.get(position));
        return convertView;
    }

    public class ItemViewTag {
        protected ImageView mIcon;
        protected TextView mName;

        public ItemViewTag(ImageView icon, TextView name) {
            mName = name;
            mIcon = icon;
        }
    }
}
