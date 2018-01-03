package com.raminguz.streetfinder;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import java.util.Set;

public class MainActivity extends Activity {

    private static final int ENABLE_BT_REQUEST = 1;
    private static final String ARDUINO_BT_NAME = "AUTONOMOUS-CAR";

    private BluetoothAdapter mBtAdapter = null;
    private BluetoothDevice mBtArduino = null;
    private BtClient mBtClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBtAdapter.isEnabled()) {
            Intent btRequest = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(btRequest, ENABLE_BT_REQUEST);

        } else {
            findBtArduinoDevice();

            mBtClient = new BtClient(mBtArduino);
            mBtClient.openConnection();

            Intent camera = new Intent(this, CameraActivity.class);
            camera.putExtra("client", mBtClient);
            startActivity(camera);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ENABLE_BT_REQUEST:
                if (resultCode == RESULT_CANCELED) finish();

                else{
                    findBtArduinoDevice();

                    mBtClient = new BtClient(mBtArduino);

                    Intent camera = new Intent(this, CameraActivity.class);
                    camera.putExtra("client", mBtClient);
                    startActivity(camera);
                    finish();
                }
                break;
        }
    }

    private void findBtArduinoDevice () {
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices)
                if (device.getName().equals(ARDUINO_BT_NAME)) {
                    mBtArduino = device;
                    return;
                }
        }
    }
}
