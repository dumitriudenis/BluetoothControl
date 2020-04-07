package com.example.denis.helper;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    static String TAG = "MainActivity";
    static {
        if(OpenCVLoader.initDebug())
        {
            Log.i(TAG,"open cv loaded successfully");
        }
        else {
            Log.i(TAG, "Opencv not loaded");

        }
    }
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int minimum_Frames=5;
    private byte[] commandToBeSent=null;
    private int nr_frames=0;
    BluetoothAdapter bluetoothAdapter;

    ArrayList<BluetoothDevice> pairedDeviceArrayList;
    ArrayAdapter<BluetoothDevice> pairedDeviceAdapter;
    private static UUID myUUID;
    private final String UUID_STRING_WELL_KNOWN_SPP =
            "00001101-0000-1000-8000-00805F9B34FB";

    ThreadConnectBTdevice myThreadConnectBTdevice;
    static ThreadConnected myThreadConnected;

    static TextView body;
    Button fab;
    private CameraBridgeViewBase mOpenCvCameraView;
    //  private static final String TAG = "MainActivity";
    private Mat mRgba;
    public static int orientation;
    Mat mat1,mat2,mat3;
    CameraBridgeViewBase cameraBridgeViewBase;


    private CascadeClassifier stop_sign;
    private CascadeClassifier pedestrian_crossing;
    private CascadeClassifier parking;
    private CascadeClassifier charging_station;
    private int mDetectorType = JAVA_DETECTOR;
    public static final int JAVA_DETECTOR = 0;
    private float mRelativeBananaSize = 0.2f;
    private int mAbsoluteBananaSize = 0;
    double xCenter = -1;
    double yCenter = -1;
    private static final Scalar BANANA_RECT_COLOR = new Scalar(0, 255, 0, 255);
    Boolean s1= false;
    public BluetoothSocket bt_demo;

    static int direction =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        Button next6 = findViewById(R.id.buttonC);
        //next6.setBackgroundResource(R.drawable.ic_launcher_background);
        next6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(myThreadConnected != null){
                    showCmdLineDialog();
                }else{
                    //showCmdLineDialog();
                }
            }
        });

        body = findViewById(R.id.body);
        body.setMovementMethod(new ScrollingMovementMethod());

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                showSettingDialog();
            }
        });
        Button btnStart = (Button)findViewById(R.id.start_main);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(myThreadConnected != null){
                    myThreadConnected.write(("F\n").getBytes());
                    body.append("F");
                }else{
                    // showCmdLineDialog();
                }
            }
        });
        Button btnStop = (Button)findViewById(R.id.stop_main);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(myThreadConnected != null){
                    myThreadConnected.write(("S\n").getBytes());
                    body.append("S");
                }else{
                    // showCmdLineDialog();
                }
            }
        });
        InputStream is = getResources().openRawResource(R.raw.haar_classifier);
        pedestrian_crossing=upload_cascade(is,pedestrian_crossing);
        is = getResources().openRawResource(R.raw.stopsign_classifier);
        stop_sign=upload_cascade(is,stop_sign);
        is = getResources().openRawResource(R.raw.cascade);
        parking=upload_cascade(is,parking);
        is = getResources().openRawResource(R.raw.cascade_depedestrian);
        charging_station=upload_cascade(is,charging_station);


        cameraBridgeViewBase= (JavaCameraView) findViewById(R.id.AndriveLaneView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.enableView();



        //fab.setEnabled(false);
        // fab.setVisibility(FloatingActionButton.GONE);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this,
                    "FEATURE_BLUETOOTH NOT support",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //using the well-known SPP UUID
        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this,
                    "Bluetooth is not supported on this hardware platform",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String strInfo = bluetoothAdapter.getName() + "\n" +
                bluetoothAdapter.getAddress();
        Toast.makeText(getApplicationContext(), strInfo, Toast.LENGTH_LONG).show();

    }

    //functie care incarca un fisier xml pentru detectia semnelor
    CascadeClassifier upload_cascade(InputStream is, CascadeClassifier sign) {

        try {
            //InputStream is = getResources().openRawResource(R.raw.cascade);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascade = new File(cascadeDir, "cascade.xml");
            FileOutputStream os = new FileOutputStream(cascade);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            sign = new CascadeClassifier(cascade.getAbsolutePath());
            if ( sign.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                stop_sign = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascade.getAbsolutePath());

            cascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
        return sign;
    }

    @Override
    protected void onStart() {
        super.onStart();

        //Turn ON BlueTooth if it is OFF
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeThreads();
    }

    private void closeThreads(){
        if(myThreadConnectBTdevice!=null){
            myThreadConnectBTdevice.cancel();
            myThreadConnectBTdevice = null;
        }

        if(myThreadConnected!=null){
            myThreadConnected.cancel();
            myThreadConnected = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==REQUEST_ENABLE_BT){
            if(resultCode == Activity.RESULT_OK){

            }else{
                Toast.makeText(this,
                        "BlueTooth NOT enabled",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        return super.onOptionsItemSelected(item);
    }

    void showSettingDialog() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        DialogFragment newFragment = SettingDialogFragment.newInstance(MainActivity.this);
        newFragment.show(ft, "dialog");

    }

    void showCmdLineDialog() {

        if(myThreadConnected == null){
            Toast.makeText(MainActivity.this,
                    "myThreadConnected == null",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("cmdline");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        DialogFragment newFragment = TypingDialogFragment.newInstance(MainActivity.this, myThreadConnected);
        newFragment.show(ft, "cmdline");

    }

    private void setup(ListView lv, final Dialog dialog) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            pairedDeviceArrayList = new ArrayList<BluetoothDevice>();

            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceArrayList.add(device);
            }

            pairedDeviceAdapter = new ArrayAdapter<BluetoothDevice>(this,
                    android.R.layout.simple_list_item_1, pairedDeviceArrayList);
            lv.setAdapter(pairedDeviceAdapter);

            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {
                    BluetoothDevice device =
                            (BluetoothDevice) parent.getItemAtPosition(position);
                    Toast.makeText(MainActivity.this,
                            "Name: " + device.getName() + "\n"
                                    + "Address: " + device.getAddress() + "\n"
                                    + "BondState: " + device.getBondState() + "\n"
                                    + "BluetoothClass: " + device.getBluetoothClass() + "\n"
                                    + "Class: " + device.getClass(),
                            Toast.LENGTH_LONG).show();

                    Toast.makeText(MainActivity.this, "start ThreadConnectBTdevice", Toast.LENGTH_LONG).show();
                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device, dialog);
                    myThreadConnectBTdevice.start();
                }
            });
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mat1=new Mat(width,height,CvType.CV_8UC4);
        mat2=new Mat(width,height,CvType.CV_8UC4);
        mat3=new Mat(width,height,CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mat1.release();
        mat2.release();
        mat3.release();
    }

    //compara un frame cu imaginea reprezentata de fisierul xml
    public int process_image(Mat mGray,CascadeClassifier sign) {

        if (mAbsoluteBananaSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeBananaSize) > 0) {
                mAbsoluteBananaSize = Math.round(height * mRelativeBananaSize);
            }
        }

            MatOfRect sign_detect = new MatOfRect();
        if (mDetectorType == JAVA_DETECTOR) {
            if (sign != null) {
                sign.detectMultiScale(mGray, sign_detect, 1.1, 2, 2, //TODO: objdetect.CV_HAAR_SCALE_IMAGE)
                        new Size(mAbsoluteBananaSize, mAbsoluteBananaSize), new Size());
            }
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] sign_array = sign_detect.toArray();

        for (int i = 0; i < sign_array.length; i++) {
            Imgproc.rectangle(mRgba, sign_array[i].tl(), sign_array[i].br(), BANANA_RECT_COLOR, 3);
        }
        return  sign_array.length;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        boolean processingCorrect = true;
        nr_frames++;
        Mat imgRectROI = inputFrame.rgba();
        Imgproc.cvtColor(imgRectROI, imgRectROI, Imgproc.COLOR_RGB2GRAY);
        Imgproc.Canny(imgRectROI, imgRectROI, 100, 400);
        Mat lines = new Mat();
        Imgproc.HoughLinesP(imgRectROI, lines, 2.0, Math.PI / 180, 300, 10, 15);
        mRgba = inputFrame.rgba();
//    n  }
//        if(process_image(mRgba,charging_station)!=0){
//            System.out.println("Charging station Sign detected");
//            commandToBeSent = "4".getBytes();
//            body.append("\nCharging station Sign detected");
//        }
        for (int i = 0; i < lines.cols(); i++)
            for (int j = 0; j < lines.rows(); j++) {
                double[] data = lines.get(i, j);
                if(data!=null && data.length>=4){
                try {
//                    System.out.println(data[0] + " " + data[1] + " " + data[2] + " " + data[3] + " ");
//                    Imgproc.line(mat1, new Point(data[0], data[1]), new Point(data[2], data[3]), Scalar.all(255), 20);
                    Point start = new Point(data[0], data[1]);
                    Point end = new Point(data[2], data[3]);
                    double dx = data[0] - data[2];
                    double dy = data[2] - data[3];
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double slope = (data[3] - data[1]) / (data[2] - data[0]);
                    if (dist > 10.d && (slope < -0.5 || slope > 0.5    )) {// show those lines that have length greater than 300
                        Imgproc.line(mRgba, start, end, new Scalar(0,0,255,255), 20);// here initimg is the original image.
                        if(slope<-0.1) {
                            System.out.println("Negative slope !! --   " + slope);
                            commandToBeSent="R".getBytes();
                        }
                        else if(slope>0.1) {
                                System.out.println("Positive slope !! --   " + slope);
                                commandToBeSent="L".getBytes();
                        }
                    }
                }
                catch (NullPointerException e)
                {
                    System.out.println("OnCameraFrame !! --" );
                    e.printStackTrace();
                    processingCorrect=false;
                }
            }
        }



//        if(nr_frames>=minimum_Frames && commandToBeSent!=null && processingCorrect) {
            if(TypingDialogFragment.cmdThreadConnected!=null)
                TypingDialogFragment.cmdThreadConnected.write(commandToBeSent);
            nr_frames=0;
//        }

        return mRgba;
        //return mRgba;
    }

    public static class SettingDialogFragment extends DialogFragment {

        ListView listViewPairedDevice;
        static MainActivity parentActivity;

        static SettingDialogFragment newInstance(MainActivity parent){
            parentActivity = parent;
            SettingDialogFragment f = new SettingDialogFragment();
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater,
                                 ViewGroup container, Bundle savedInstanceState) {
            getDialog().setTitle("Setting");
            getDialog().setCanceledOnTouchOutside(false);
            View settingDialogView = inflater.inflate(R.layout.setting_layout, container, false);

            listViewPairedDevice = settingDialogView.findViewById(R.id.pairedlist);

            parentActivity.setup(listViewPairedDevice, getDialog());

            return settingDialogView;
        }
    }

    public static class TypingDialogFragment extends DialogFragment {

        EditText cmdLine;
        static MainActivity parentActivity;
        static public ThreadConnected cmdThreadConnected;

        static TypingDialogFragment newInstance(MainActivity parent, ThreadConnected thread){
            parentActivity = parent;
            cmdThreadConnected = thread;
            TypingDialogFragment f = new TypingDialogFragment();
            return f;
        }
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater,
                                 ViewGroup container, Bundle savedInstanceState) {
            getDialog().setTitle("Cmd Line");
            getDialog().setCanceledOnTouchOutside(false);
            View typingDialogView = inflater.inflate(R.layout.typing_layout, container, false);

            cmdLine = (EditText)typingDialogView.findViewById(R.id.cmdline);

            ImageView imgCleaarCmd = (ImageView)typingDialogView.findViewById(R.id.clearcmd);
            imgCleaarCmd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cmdLine.setText("");
                }
            });

            Button btnEnter = (Button)typingDialogView.findViewById(R.id.enter);
            btnEnter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(cmdThreadConnected!=null){
                        byte[] bytesToSend = cmdLine.getText().toString().getBytes();
                        cmdThreadConnected.write(bytesToSend);
                        byte[] NewLine = "\n".getBytes();
                        cmdThreadConnected.write(NewLine);
                    }
                }
            });
            Button btnStop = (Button)typingDialogView.findViewById(R.id.stop);
            btnStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(myThreadConnected != null){
                        myThreadConnected.write(("2\n").getBytes());
                        body.append("s");
                    }else{
                        // showCmdLineDialog();
                    }
                }
            });
            Button btnStart = (Button)typingDialogView.findViewById(R.id.start);
            btnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(myThreadConnected != null){
                        myThreadConnected.write(("3\n").getBytes());
                        body.append("F");
                    }else{
                        // showCmdLineDialog();
                    }
                }
            });
            Button btnRight = (Button)typingDialogView.findViewById(R.id.right);
            btnRight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(myThreadConnected != null){
                        myThreadConnected.write(("R\n").getBytes());
                        body.append("R");
                    }else{
                        // showCmdLineDialog();
                    }
                }
            });
            Button btnLeft = (Button)typingDialogView.findViewById(R.id.left);
            btnLeft.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(myThreadConnected != null){
                        myThreadConnected.write(("5\n").getBytes());
                        body.append("L");
                    }else{
                        // showCmdLineDialog();
                    }
                }
            });


            return typingDialogView;
        }
    }

    //Called in ThreadConnectBTdevice once connect successed
    //to start ThreadConnected
    private void startThreadConnected(BluetoothSocket socket){

        myThreadConnected = new ThreadConnected(socket);
        myThreadConnected.start();
    }

    /*
    ThreadConnectBTdevice:
    Background Thread to handle BlueTooth connecting
    */
    private class ThreadConnectBTdevice extends Thread {

        public BluetoothSocket bluetoothSocket = null;
        private final BluetoothDevice bluetoothDevice;
        Dialog dialog;

        private ThreadConnectBTdevice(BluetoothDevice device, Dialog dialog) {
            this.dialog = dialog;
            bluetoothDevice = device;

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
                Toast.makeText(MainActivity.this,
                        "bluetoothSocket: \n" + bluetoothSocket,
                        Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            boolean success = false;
            try {
                if(bluetoothSocket!=null && !bluetoothSocket.isConnected()) {
                    bluetoothSocket.connect();
                    success = true;
                }
            } catch (IOException e) {
                e.printStackTrace();

                final String eMessage = e.getMessage();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "something wrong bluetoothSocket.connect(): \n" + eMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                });

                try {
                    if(bluetoothSocket!=null)
                        bluetoothSocket.close();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }

            if(success){
                //connect successful
                final String msgconnected = "connect successful:\n"
                        + "BluetoothSocket: " + bluetoothSocket + "\n"
                        + "BluetoothDevice: " + bluetoothDevice;
                bt_demo=bluetoothSocket;
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
//                        fab.setEnabled(true);
//                        fab.setVisibility(FloatingActionButton.VISIBLE);
                        body.append("\n");
                        Toast.makeText(MainActivity.this, msgconnected, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                });

                startThreadConnected(bluetoothSocket);

            }else{
                //fail
            }
        }

        public void cancel() {

            Toast.makeText(getApplicationContext(),
                    "close bluetoothSocket",
                    Toast.LENGTH_LONG).show();
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    /*
    ThreadConnected:
    Background Thread to handle Bluetooth data communication
    after connected
     */
    private class ThreadConnected extends Thread {
        private final BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;

        boolean running;

        public ThreadConnected(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream in = null;
            OutputStream out = null;
            running = true;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            String strRx = "";

            while (running) {
                try {
                    bytes = connectedInputStream.read(buffer);
                    final String strReceived = new String(buffer, 0, bytes);
                    final String strByteCnt = String.valueOf(bytes) + " bytes received.\n";

                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            body.append(strReceived);
                        }});

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();

                    final String msgConnectionLost = "Connection lost:\n"
                            + e.getMessage();
                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, msgConnectionLost, Toast.LENGTH_LONG).show();

                        }});
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                if(connectedOutputStream!=null && connectedBluetoothSocket.isConnected())
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void cancel() {
            running = false;
            try {
                if(connectedBluetoothSocket!=null && connectedBluetoothSocket.isConnected())
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
