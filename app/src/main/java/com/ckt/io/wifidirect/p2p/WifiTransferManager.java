package com.ckt.io.wifidirect.p2p;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Looper;
import android.renderscript.ScriptGroup;
import android.util.Log;

import com.ckt.io.wifidirect.Constants;
import com.ckt.io.wifidirect.R;
import com.ckt.io.wifidirect.utils.ApkUtils;
import com.ckt.io.wifidirect.utils.DataTypeUtils;
import com.ckt.io.wifidirect.utils.FileTypeUtils;
import com.ckt.io.wifidirect.utils.LogUtils;
import com.ckt.io.wifidirect.utils.SdcardUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Handler;

/**
 * Created by admin on 2016/7/29.
 */
public class WifiTransferManager {

    public static final String TAG = "WifiTransferManager";

    public static final int UPDATE_SPEED_INTERVAL = 500;

    public static String TEMP_FILE_SUFFIX = ".tmp";

    public static final int SOCKET_TIMEOUT = 5000;
    public static final int TASK_TIMEOUT = 3000;
    public static final int MAX_SEND_TASK = 5;

    public static final byte MSG_REQEUST_FILE_INFO = 0;
    public static final byte MSG_RESPONSE_FILE_INFO = 1;
    public static final byte MSG_SEND_FILE = 2;
    public static final byte MSG_SEND_CLIENT_IP = 10;
    public static final byte MSG_RESPONSE_SEND_CLIENT_IP = 11;

    public static final String PARAM_TRANSFERED_LEN = "transferedLen";
    public static final String PARAM_SIZE = "size";
    public static final String PARAM_PATH = "path";
    public static final String PARAM_FILE_NAME = "name";
    public static final String PARAM_IS_HAS_FILE_EXTRA = "hasFileExtra";
    public static final String PARAM_ID = "id";
    public static final String PARAM_RET = "ret";
    public static final String PARAM_MAC = "mac";

    private File receiveFileDir;

    private Context context;
    private InetAddress peerAddr;
    private int peerPort;
    private WifiP2pDevice mThisDevice;

    private ArrayList<DataTranferTask> mWaitingTasks = new ArrayList<>();
    private ArrayList<DataTranferTask> mDoingTasks = new MyArrayList<>();

    private SendTaskHandleThread sendTaskHandleThread;

    private android.os.Handler handler;

    private FileSendStateListener fileSendStateListener;
    private FileReceiveStateListener fileReceiveStateListener;

    private OnSendClientIpResponseListener onSendClientIpResponseListener;

    private UpdateSpeedRunnbale mUpdateSpeedRunnable = new UpdateSpeedRunnbale();

    public WifiTransferManager(Context context,
                               InetAddress addr,
                               int port,
                               WifiP2pDevice thisDevice,
                               FileSendStateListener fileSendStateListener,
                               FileReceiveStateListener fileReceiveStateListener,
                               OnSendClientIpResponseListener onSendClientIpResponseListener) {
        if(context == null) {
            try {
                throw new Exception();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.context = context;
        this.peerAddr = addr;
        this.peerPort = port;
        this.mThisDevice = thisDevice;
        File sdcard = SdcardUtils.getInnerSDcardFile(context);
        if(sdcard != null) {
            receiveFileDir = new File(sdcard, context.getString(R.string.received_file_dir));
        }
        handler = new android.os.Handler(Looper.getMainLooper());
        this.fileSendStateListener= fileSendStateListener;
        this.fileReceiveStateListener = fileReceiveStateListener;
        this.onSendClientIpResponseListener = onSendClientIpResponseListener;
    }

    public boolean sendFile(int id, String path) {
        File f = new File(path);
        String mac = null;
        if(mThisDevice != null) {
            mac = mThisDevice.deviceAddress;
        }
        if(id < 0 || path == null || "".endsWith(path) || !f.exists() || !f.isFile() || mac == null) {
            LogUtils.d(TAG, "Send file failed: The param is null or the file dose't exist!! id="+id+"path="+path);
            return false;
        }

        DataTranferTask temp = null;
        if(getWaittingTaskById(id) != null || (((temp = getDoingTaskById(id))!=null) && temp instanceof FileSendTask) ) {
            LogUtils.d(TAG, "Send file failed: There have a watting or doing task with the same id!! id="+id+"path="+path);
            return false;
        }

        FileSendTask task = new FileSendTask(id, path, mac);
        synchronized (task) {
            mWaitingTasks.add(task);
        }

        //start sendTaskHandleThread if need
        startHandleSendTaskThread();

        return true;
    }

    private boolean doSend(OutputStream out, byte msgType, HashMap<String, String> paramMap, String extraFile, DataTranferTask task) {
        LogUtils.d(TAG, "doSend  msgType=" + msgType + " paramMap=" + paramMap.toString() + "extraFile=" + extraFile + " peerIp:" + peerAddr + "  localIP:");
        boolean ret = true;
        //handle param
        boolean hasExtraFile = false;
        File f = null;
        if(extraFile != null ) {
            f = new File(extraFile);
            if(f.exists() && f.isFile() && !paramMap.containsKey(PARAM_IS_HAS_FILE_EXTRA)) {
                paramMap.put(PARAM_IS_HAS_FILE_EXTRA, String.valueOf(true));
                paramMap.put(PARAM_SIZE, String.valueOf(f.length()));
            }
            hasExtraFile = true;
        }
        String paramStr = DataTypeUtils.toJsonStr(paramMap);

        InputStream in = null;
        try {
            //step 1: send msgType
            out.write(msgType);
            //step 2: send param-len
            byte paramBuf [] = paramStr.getBytes();
            out.write(DataTypeUtils.intToBytes2(paramBuf.length));
            //step 3: send param
            out.write(paramBuf);
            //step 4: send extra file if it has
            if(hasExtraFile) {
                long transferedSize = 0;
                try {//if paramMap.get return a null, Long.valueOf(..) will be fatal.
                    transferedSize = Long.valueOf(paramMap.get(PARAM_TRANSFERED_LEN));
                }catch (Exception e){}
                if(transferedSize >= f.length()) {
                    transferedSize = 0;
                }
                in = new FileInputStream(f);
                //skip transfered content
                in.skip(transferedSize);
                byte buf [] = new byte [2048];
                int len = 0;
                while((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    if(task != null) {
                        task.transferedSize += len;
                    }
                }
                LogUtils.d(TAG, "do send file successed! ret=");
            }
//            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            ret = false;
        } finally {
            /*if s is not null do not release it in this method*/
            if(in == null) {
                try {
                    in.close();
                } catch (Exception e) {}
            }
        }
        LogUtils.d(TAG, "do Finished--->ret=" + ret);
        return ret;
    }

    public boolean receive(final Socket s) {
        FileReceiveTask task = new FileReceiveTask(s);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return true;
    }

    private boolean doReceive(DataTranferTask task, InputStream inputstream) {
        LogUtils.d(TAG, "doReceive--->"+task.toString());
        if (inputstream == null) return false;
        boolean ret = true;
        OutputStream out = null;
        int len = 0;
        byte msgType = -1;
        File f = null;
        boolean isHasFileExtra = false;
        try {
            //step 1: recevice the msg_type
            byte msg_type[] = new byte[1];//the buffer to recevice the msg_type
            len = inputstream.read(msg_type);
            if (len <= 0) {
                LogUtils.d(TAG, "receive msg type failed!!!");
                throw new Exception();
            }
            msgType = msg_type[0];
//            LogUtils.d(TAG, "do receive: msg type = " + msgType);
            //step 2: recevice the param len
            byte param_len_buf[] = new byte[4]; //the buffer to recevie the param_len
            len = inputstream.read(param_len_buf);
            if (len <= 0) {
                LogUtils.d(TAG, "receive msg param-len failed!!!");
                throw new Exception();
            }
            int paramLen = DataTypeUtils.byteToInt2(param_len_buf);
            LogUtils.d(TAG, "do receive msgType="+msgType+" param-len="+paramLen);
            //step 3: receive the param_str and parse it
            byte param_buf[] = new byte[1024];
            int left = paramLen;
            StringBuffer sb = new StringBuffer();
            while(true) {
                if(left >= 1024) {
                    len = inputstream.read(param_buf);
                    if(len == -1) throw new Exception();
                    sb.append(new String(param_buf));
                    left -= len;
                }else {
                    byte tempBuf [] = new byte [left];
                    len = inputstream.read((tempBuf));
                    if(len == -1) throw new Exception();
                    sb.append(new String(tempBuf));
                    break;
                }
            }
            LogUtils.d(TAG, "do receive: paramStr="+sb.toString());
            final HashMap<String, String> paramMap = DataTypeUtils.toHashmap(sb.toString());
            //step 4: handle the recevied msg
            switch (msg_type[0]) {
                case MSG_REQEUST_FILE_INFO: //another device request the file info
                    FileReceiveTask fileReceiveTask = (FileReceiveTask) task;
                    fileReceiveTask.onFileInfoRequest(paramMap);
                    break;
                case MSG_RESPONSE_FILE_INFO://another device response our reqeust for file info
                    FileSendTask fileSendTask = (FileSendTask) task;
                    fileSendTask.onRequestFileInfoResponsed((HashMap<String, String>) paramMap.clone());
                    break;
                case MSG_SEND_FILE://another device send a file to here.

                    break;
            }
            try {
                isHasFileExtra = Boolean.valueOf(paramMap.get(PARAM_IS_HAS_FILE_EXTRA));
            }catch (Exception e){}

            if(msg_type[0] == MSG_SEND_FILE && isHasFileExtra) {
                //get important params
                String name = paramMap.get(PARAM_FILE_NAME);
                long size = 0;
                try {
                    size = Long.valueOf(paramMap.get(PARAM_SIZE));
                }catch (Exception e) {}
                long transferedSize = 0;
                try {
                    transferedSize = Long.valueOf(paramMap.get(PARAM_TRANSFERED_LEN));
                }catch (Exception e) {}
                String mac = null;
                mac = paramMap.get(PARAM_MAC);
                if (name == null || size == 0 || mac == null) {
                    LogUtils.d(TAG, "receive file content: miss important params!!!");
                    throw new Exception();
                }
                f = getTempFile(name, mac);
                LogUtils.d(TAG, "do receive file:"+f.getPath());
                task.f = getFiniallyFile(name);
                task.mac = mac;
                task.transferedSize = transferedSize;
                task.size = size;
                onReceiveFileStarted(task, f.getPath()); /*.............................*/
                File parent = f.getParentFile();
                if(!parent.exists()) {
                    parent.mkdirs();
                }
                if(transferedSize == 0) {//new file
                    f.createNewFile();
                    out = new FileOutputStream(f);
                }else {
                    if(!f.exists()) {
                        LogUtils.d(TAG, "recevie file [" + f.getPath() + "] failed: the tmp file does't exist in breakpoint-resume mode" );
                        throw new Exception();
                    }else if(f.length() != transferedSize) {
                        LogUtils.d(TAG, "recevie file [" + f.getPath() + "] failed: the tmp file size done't match the transfered size");
                        throw new Exception();
                    }
                    out = new FileOutputStream(f, true);//true--->append file
                }
                byte buf [] = new byte[2048];
                while((len = inputstream.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    transferedSize += len;
                }
                LogUtils.d(TAG, "do receive file: while exist  receivedSize="+transferedSize+ " szie="+size);
                if(transferedSize == size) {
                    //remove the ".tmp" suffix
                    File newFile = getFiniallyFile(name);
                    parent = newFile.getParentFile();
                    if(!parent.exists()) {
                        parent.mkdirs();
                    }
                    newFile.delete();
                    f.renameTo(newFile);
                    LogUtils.d(TAG, "do receive file successed--->rename to:" + newFile.getPath());
                    ret = true;
                }
            }

        }catch (Exception e) {
            e.printStackTrace();
            ret = false;
        }

        if(isHasFileExtra) {
            onReceiveFileFinished(task, task.getFile().getPath(), ret);
        }
        return ret;
    }

    public void onSendClientIpResponse(HashMap<String, String> map) {
        boolean ret = false;
        try {
            ret= Boolean.valueOf(map.get(PARAM_RET));
        }catch (Exception e) {}
        if(onSendClientIpResponseListener != null) {
            onSendClientIpResponseListener.onSendClientIpResponse(ret);
        }
    }

    public void onSendFileStarted(DataTranferTask task, int id) {
        LogUtils.d(TAG, "onSendFileStarted --> id=" + id);
        if(fileSendStateListener != null) {
            fileSendStateListener.onStart(id, task.f.getPath(), task.transferedSize);
        }

        //update db
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.STATE, Constants.State.STATE_TRANSFERING);
        Uri uri = ContentUris.withAppendedId(Constants.InstanceColumns.CONTENT_URI, id);
        context.getContentResolver().update(uri,contentValues,null,null);
    }

    public void onSendFileFinished(DataTranferTask task, int id, boolean ret) {
        mDoingTasks.remove(task);
        LogUtils.d(TAG, "onSendFileFinished --> id=" + id + "ret=" + ret);
        startHandleSendTaskThread();
        if(fileSendStateListener != null) {
            fileSendStateListener.onFinished(id, task.f.getPath(), task.transferedSize, ret);
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.STATE, Constants.State.STATE_TRANSFER_DONE);
        Uri uri = ContentUris.withAppendedId(Constants.InstanceColumns.CONTENT_URI, id);
        context.getContentResolver().update(uri,contentValues,null,null);
    }

    public void onReceiveFileStarted(DataTranferTask task, String path) {
        LogUtils.d(TAG, "receive File started: " + path);
        if(fileReceiveStateListener != null) {
            fileReceiveStateListener.onStart(path, task.transferedSize, task.size);
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.PATH, path);
        contentValues.put(Constants.InstanceColumns.NAME, new File(path).getName());
        contentValues.put(Constants.InstanceColumns.LENGTH, task.size);
        WifiP2pState wifiP2pState = WifiP2pState.getInstance(context);
        contentValues.put(Constants.InstanceColumns.TRANSFER_MAC, wifiP2pState.getConnectedDeviceInfo().connectedDevice.deviceAddress);
        contentValues.put(Constants.InstanceColumns.TRANSFER_DIRECTION, Constants.DIRECTION_IN);
        contentValues.put(Constants.InstanceColumns.STATE, Constants.State.STATE_TRANSFERING);
        contentValues.put(Constants.InstanceColumns.TRANSFER_LENGTH, 0);
        Uri uri = context.getContentResolver().insert(Constants.InstanceColumns.CONTENT_URI, contentValues);
        if(uri != null) {
            task.id = (int) ContentUris.parseId(uri);
        }
    }

    public void onReceiveFileFinished(DataTranferTask task, String path, boolean ret) {
        mDoingTasks.remove(task);
        LogUtils.d(TAG, "receive File finished: " + path + " ret:" + ret);
        if(fileReceiveStateListener != null) {
            fileReceiveStateListener.onFinished(path, task.transferedSize, task.size, ret);
        }

        //update db
        ContentValues contentValues = new ContentValues();
        contentValues.put(Constants.InstanceColumns.STATE, Constants.State.STATE_TRANSFER_DONE);
        Uri uri = ContentUris.withAppendedId(Constants.InstanceColumns.CONTENT_URI, task.id);
        context.getContentResolver().update(uri,contentValues,null,null);
    }

    private void startHandleSendTaskThread() {
        if(sendTaskHandleThread != null && sendTaskHandleThread.isRunning) {
            //the thread is running now, do nothing
        }else {
            sendTaskHandleThread = new SendTaskHandleThread();
            sendTaskHandleThread.start();
        }
    }

    public File getTempFile(String name, String mac) {
        String newPath = receiveFileDir.getPath() +
                File.separator + TEMP_FILE_SUFFIX.toUpperCase() +
                File.separator + mac +
                File.separator + name + TEMP_FILE_SUFFIX;
        return new File(newPath);
    }

    public File getFiniallyFile(String name) {
        String typeStr = FileTypeUtils.getTypeString(context, name);
        String newPath = receiveFileDir.getPath() + File.separator + typeStr + File.separator + name;
        return new File(newPath);
    }


    private DataTranferTask getDoingTaskById(int id) {
        for(DataTranferTask task : mDoingTasks) {
            if(task.id == id) {
                return task;
            }
        }
        return null;
    }

    private DataTranferTask getWaittingTaskById(int id) {
        for(DataTranferTask task : mWaitingTasks) {
            if(task.id == id) {
                return task;
            }
        }
        return null;
    }

    public void startUpdateSpeed() {
        stopUpdateSpeed();
        handler.post(mUpdateSpeedRunnable);
    }

    public void stopUpdateSpeed() {
        handler.removeCallbacks(mUpdateSpeedRunnable);
    }

    class MyArrayList<E> extends ArrayList<E> {
        @Override
        public boolean add(E object) {
            startUpdateSpeed();
            return super.add(object);
        }

        @Override
        public boolean remove(Object object) {
            boolean ret = super.remove(object);
            if(this.size() == 0) {
                stopUpdateSpeed();
            }
            return ret;
        }

        @Override
        public E remove(int index) {
            E ret = super.remove(index);
            if(this.size() == 0) {
                stopUpdateSpeed();
            }
            return ret;
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            stopUpdateSpeed();
            return super.removeAll(collection);
        }
    }

    class UpdateSpeedRunnbale implements Runnable {

        @Override
        public void run() {
            ArrayList<DataTranferTask> sendingList = new ArrayList<>();
            ArrayList<DataTranferTask> receivingList = new ArrayList<>();
            for(DataTranferTask task : mDoingTasks) {
                if(task != null && task.isRunning) {
                    task.calculateSpeed(UPDATE_SPEED_INTERVAL);
                    if(task instanceof FileSendTask) {
                        sendingList.add(task);
                    }else {
                        receivingList.add(task);
                    }
                }
            }

            if(fileSendStateListener != null) {
                fileReceiveStateListener.onUpdate(sendingList);
            }

            if(fileReceiveStateListener != null) {
                fileReceiveStateListener.onUpdate(receivingList);
            }

            handler.postDelayed(mUpdateSpeedRunnable, UPDATE_SPEED_INTERVAL);
        }
    }


    class SendTaskHandleThread extends Thread {
        boolean isRunning = false;
        @Override
        public void run() {
            isRunning = true;
            /*
            * Do while if
            *    there are some task to do or doing
            *
            * */
            while((mWaitingTasks.size() > 0 && mDoingTasks.size() < MAX_SEND_TASK)  && peerAddr!=null) {

                final DataTranferTask task = mWaitingTasks.remove(0);
                if(!mDoingTasks.contains(task) || task != null) {
                    mDoingTasks.add(task);
                }
                LogUtils.d(TAG, "A new Send task begin:" +task.toString());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                });
                try {
                    sleep(UPDATE_SPEED_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isRunning = false;
        }
    }

    abstract class DataTranferTask extends AsyncTask<Object, Integer, Boolean> {
        public long size;
        public long transferedSize;
        protected long tempTransferdSize = 0; /*use to calculate speed*/
        public int id = -1;
        public File f;
        public boolean isRunning = false;
        public double speed;
        public String mac;

        void onGetFileInfo(HashMap<String, String> map){}
        public long getTransferedSize() {
            return transferedSize;
        }

        private double calculateSpeed(int ms) {
            if(tempTransferdSize >= transferedSize) {
                return 0;
            }
            speed = (transferedSize-tempTransferdSize) * 1000.0 / ms / 1024 /1204;
            tempTransferdSize = transferedSize;
            return speed;
        }

        public double getSpeed() {
            return speed;
        }

        public int getId() {
            return id;
        }

        public long getSize() {
            return size;
        }

        public File getFile() {
            return f;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public String getMac() {
            return mac;
        }

        @Override
        public String toString() {
            return "DataTranferTask{" +
                    "size=" + size +
                    ", transferedSize=" + transferedSize +
                    ", id=" + id +
                    ", f=" + f +
                    ", mac=" + mac +
                    ", isRunning=" + isRunning +
                    ", speed=" + getSpeed() +
                    '}';
        }
    }


    /*Send a file need 3 step
    * step 1: send to request the file info
    * step 2: the another device response the reqeust sended in step 1(this will callback the method: onGetFileInfo())
    * step 3: send the file now
    * */
    class FileSendTask extends DataTranferTask {
        boolean isFileInfoGeted = false;
        public FileSendTask(int id, String path, String mac) {
            this.id = id;
            f = new File(path);
            size = f.length();
            this.mac = mac;
        }
        @Override
        protected Boolean doInBackground(Object... params) {
            isRunning = true;
            boolean ret = true;
            String name = f.getName();
            if(name.toString().endsWith(".apk")) {
                name = ApkUtils.getApkLable(context, f.getPath()) + ".apk";
            }
            Socket s = new Socket();
            OutputStream out = null;
            InputStream in = null;
            try {
                s.bind(null);
                s.connect((new InetSocketAddress(peerAddr, peerPort)), SOCKET_TIMEOUT);
                out = s.getOutputStream();
                in = s.getInputStream();
                final HashMap<String, String> paramMap = new HashMap<>();
                paramMap.put(PARAM_ID, String.valueOf(id));
                paramMap.put(PARAM_FILE_NAME, name);
                paramMap.put(PARAM_MAC, mac);
                //step 1: request the file info
                onSendFileStarted(this, id); /*..................................*/
                doSend(out, MSG_REQEUST_FILE_INFO, paramMap, null, null);
                //step 2: wait for the file info
                if(doReceive(this, in)) {
                    //step 3: send the file now
                    paramMap.put(PARAM_TRANSFERED_LEN, String.valueOf(transferedSize));
                    ret = doSend(out, MSG_SEND_FILE, paramMap,f.getPath(), this);
                }
            } catch (Exception e) {
                e.printStackTrace();
                ret = false;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                }
                if(out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {}
                }
                if(s != null) {
                    try {
                        s.close();
                    } catch (IOException e) {}
                }
            }
            onSendFileFinished(this, id, ret);
            isRunning = false;
            return ret;
        }

        /*
        * Called when get the file info send by another device
        * main that the another device have response the file info
        * */
        public void onRequestFileInfoResponsed(HashMap<String, String> map) {
            int id = -1;
            try {
                id = Integer.valueOf(map.get(PARAM_ID));
            }catch (Exception e){}
            if(id < 0) {
                LogUtils.d(TAG, "received a request_file_info response, but the id is invalide, so ignore the receive!!!!");
                return ;
            }
            LogUtils.d(TAG, "onRequestFileInfoResponsed() --> !!!!" + map.toString());

            isFileInfoGeted = true;
            try {
                transferedSize = Long.valueOf(map.get(PARAM_TRANSFERED_LEN));
                if(transferedSize >= size) {
                    transferedSize = 0;
                }
                tempTransferdSize = transferedSize;
            }catch (Exception e){}
        }

        @Override
        protected void onPostExecute(Boolean ret) {
//            onSendFileFinished(this, id, ret);
        }
    }

    /*
    * This task only to receive file
    * */
    class FileReceiveTask extends DataTranferTask {
        Socket socket;
        HashMap<String, String> paramMap;
        public FileReceiveTask(Socket socket) {
            this.socket = socket;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            boolean ret = false;
            isRunning = true;
            if(socket != null) {
                OutputStream out = null;
                InputStream inputStream = null;
                try {
                    out = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                    /*step 1: wait for the file-info request*/
                    doReceive(this, inputStream);
                    /*step 2: receive the file*/
                    doReceive(this, inputStream);
                }catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        inputStream.close();
                    } catch (Exception e) {}
                    try {
                        socket.close();
                    }catch (Exception e){}
                    try {
                        out.close();
                    } catch (Exception e) {}
                }
            }
            isRunning = false;
            return ret;
        }

        @Override
        protected void onPostExecute(Boolean ret) {
//            onReceiveFileFinished(this, f.getPath(), ret);
        }

        /*
        ** handle request
        */
        public void onFileInfoRequest(HashMap<String, String> map) {
            String name = map.get(PARAM_FILE_NAME);
            if(name == null) {
                LogUtils.d(TAG, "received a request of file info, but file name is null, so ignore the receive!!!!");
                return ;
            }
            String mac = map.get(PARAM_MAC);
            if(mac == null) {
                LogUtils.d(TAG, "received a request of file info, but the MAC is null, so ignore the receive!!!!");
                return ;
            }
            File f = getTempFile(name, mac);
            long transferedLen = 0;
            if(f.exists()) {
                transferedLen = f.length();
            }
            map.put(PARAM_TRANSFERED_LEN, String.valueOf(transferedLen));
            OutputStream out = null;
            try {
                out = socket.getOutputStream();
                //response it
                doSend(out, MSG_RESPONSE_FILE_INFO, map, null, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface FileSendStateListener {
        public void onStart(int id, String path, long transferedSize);
        public void onUpdate(ArrayList<DataTranferTask> taskList);
        public void onFinished(int id, String path, long transferedSize, boolean ret);
    }

    public interface FileReceiveStateListener {
        public void onStart(String path, long transferedSize, long size);
        public void onUpdate(ArrayList<DataTranferTask> taskList);
        public void onFinished(String path, long transferedSize, long size, boolean ret);
    }

    public interface OnSendClientIpResponseListener {
        public void onSendClientIpResponse(boolean ret);
    }
}