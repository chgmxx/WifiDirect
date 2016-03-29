package com.ckt.io.wifidirect.utils;

import android.content.Context;

import com.ckt.io.wifidirect.R;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;

public class FileTypeUtils {

    public static boolean isApk(String path) {
        return path.toLowerCase().endsWith(".apk");
    }
    public static boolean isMusic(String path) {
        String s = path.toLowerCase();
        return s.endsWith(".mp3") || s.endsWith(".m4a") || s.endsWith(".mid")
                || s.endsWith(".ogg") || s.endsWith(".wav") || s.endsWith(".xmf");
    }
    public static boolean isPhoto(String path) {
        String s = path.toLowerCase();
        return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".bmp") || s.endsWith(".gif") || s.endsWith(".png");
    }
    public static boolean isMovie(String path) {
        String s = path.toLowerCase();
        return s.endsWith(".3pg") || s.endsWith(".mp4") || s.endsWith(".rmvb");
    }

    //��ȡ�ļ����� �ַ���
    public static String getTypeString(Context context, String path) {
        String ret;
        if(isApk(path)) { //apk�ļ�
            ret = context.getResources().getString(R.string.type_application);
        }else if(isMusic(path)) { //�����ļ�
            ret = context.getResources().getString(R.string.type_music);
        }else if(isPhoto(path)) {//ͼƬ�ļ�
            ret = context.getResources().getString(R.string.type_photo);
        }else if(isMovie(path)) { //��Ƶ�ļ�
            ret = context.getResources().getString(R.string.type_movie);
        }else {
            ret = context.getResources().getString(R.string.type_other);
        }
        return ret;
    }

    //��ȡ��ʽ��Ĭ��ͼƬ
    public static int getDefaultFileIcon(String path) {
        String s = path.toLowerCase();
        int id = R.drawable.file_icon;
        if(isApk(path)) { //apk�ļ�
            id = R.drawable.apk_icon;
        }else if(isMusic(path)) { //�����ļ�
            id = R.drawable.music_icon;
        }else if(isPhoto(path)) {//ͼƬ�ļ�
            id = R.drawable.photo_icon;
        }else if(isMovie(path)) { //��Ƶ�ļ�
            id = R.drawable.film_icon;
        }
        return id;
    }

    //�ж��ļ��Ƿ���Ҫ����ͼƬ
    public static boolean isNeedToLoadDrawable(String path) {
        String s = path.toLowerCase();
        //��Ҫ����ͼƬ��һЩ�ļ�
        return isApk(path) || isMusic(path) || isPhoto(path) || isMovie(path);
    }

    /* �ж��ļ�MimeType��method */
    public static String getMIMEType(String path)
    {
        /* ����չ�������;���MimeType */
        String type = "";
        if(isMusic(path)) {
            type = "audio";
        }else if(isMovie(path)) {
            type = "video";
        }else if(isPhoto(path)) {
            type = "image";
        }else if(isApk(path)) {
            /* android.permission.INSTALL_PACKAGES */
            type = "application/vnd.android.package-archive";
        }else {
            type="*";
        }
        /*����޷�ֱ�Ӵ򿪣�����������б���û�ѡ�� */
        if(!isApk(path)) {
            type += "/*";
        }
        return type;
    }

    public static String getFileSize(String path) {
        long size = new File(path).length();
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format(size / 1024.0 / 1024.0) + "M";
    }
}
