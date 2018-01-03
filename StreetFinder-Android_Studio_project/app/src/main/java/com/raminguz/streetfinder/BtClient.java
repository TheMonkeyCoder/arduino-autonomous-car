package com.raminguz.streetfinder;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.util.UUID;

public class BtClient implements Parcelable {

    private final String HC_05_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private BluetoothDevice mBtDevice = null;
    private BluetoothSocket mMaster = null;

    public BtClient (BluetoothDevice btDevice) {
        mBtDevice = btDevice;
    }

    public BtClient (Parcel in) {
       mBtDevice = (BluetoothDevice) in.readParcelable(BtClient.class.getClassLoader());
    }

    public void openConnection () {
        try {
            mMaster = mBtDevice.createRfcommSocketToServiceRecord(UUID.fromString(HC_05_UUID));
            mMaster.connect();
        } catch (IOException e) {
            e.getMessage();
        }
    }

    public void closeConnection () {
        try {
            mMaster.close();
        } catch (IOException e) {
            e.getMessage();
        }
    }

    public boolean isConnected () {
        return mMaster != null && mMaster.isConnected();
    }

    public void writeData (String data) {
        try {
            mMaster.getOutputStream().write(data.getBytes());
            mMaster.getOutputStream().flush();
        } catch (IOException e) {
            e.getMessage();
        }
    }

    public String readChar () {
        try {
            return String.valueOf((char) mMaster.getInputStream().read());
        } catch (IOException e) {
            e.getMessage();
        }
        return "";
    }

    public boolean availableData () {
        try {
            return mMaster.getInputStream().available() != 0;
        } catch (IOException e) {
            e.getMessage();
        }
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mBtDevice, flags);
    }

    public static final Parcelable.Creator<BtClient> CREATOR = new Parcelable.Creator<BtClient>() {
        public BtClient createFromParcel(Parcel in) {
            return new BtClient((BluetoothDevice) in.readParcelable(BtClient.class.getClassLoader()));
        }

        public BtClient[] newArray(int size) {
            return new BtClient[size];
        }
    };
}
