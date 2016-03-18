package com.ckt.io.wifidirect.utils;

import android.content.Context;

import com.ckt.io.wifidirect.R;

import java.io.File;

public class FileTypeUtils {

    public static boolean isApk(String path) {

        return path.toLowerCase().endsWith(".apk");
    }
    public static boolean isMusic(String path) {
        String s = path.toLowerCase();
        return path.endsWith(".mp3");
    }
    public static boolean isPhoto(String path) {
        String s = path.toLowerCase();
        return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".bmp") || s.endsWith(".gif") || s.endsWith(".png");
    }
    public static boolean isMovie(String path) {
        String s = path.toLowerCase();
        return s.endsWith(".3pg") || s.endsWith(".mp4") || s.endsWith(".rmvb");
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

    public static String getFileTitle(Context context, String path) {
        String title = "";
        if(isApk(path)) { //apk�ļ�
            title = ApkUtils.getApkLable(context, path);
        }else if(isMusic(path)) { //�����ļ�
            title = AudioUtils.getMusicName(path);
        }else {
            title = (new File(path)).getName();
        }
        return title;
    }
}
