// TODO copyright

// Class representing a USB MIDI keyboard

package com.levien.synthesizer.android.usb;

import android.annotation.TargetApi;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Build;
import android.util.Log;

import com.levien.synthesizer.android.ui.PianoActivity2;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class UsbMidiDevice {
  private final PianoActivity2 mActivity;
  private final UsbDeviceConnection mDeviceConnection;
  private final UsbEndpoint mEndpoint;

  private final WaiterThread mWaiterThread = new WaiterThread();

  public UsbMidiDevice(PianoActivity2 activity, UsbDeviceConnection connection, UsbInterface intf) {
    mActivity = activity;
    mDeviceConnection = connection;

    mEndpoint = getInputEndpoint(intf);
  }

  private UsbEndpoint getInputEndpoint(UsbInterface usbIf) {
    Log.d("synth", "interface:" + usbIf.toString());
    int nEndpoints = usbIf.getEndpointCount();
    for (int i = 0; i < nEndpoints; i++) {
      UsbEndpoint endpoint = usbIf.getEndpoint(i);
      if (endpoint.getDirection() == UsbConstants.USB_DIR_IN &&
              endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
        return endpoint;
      }
    }
    return null;
  }

  public void start() {
    Log.d("synth", "midi USB waiter thread starting");
    mWaiterThread.start();
  }

  public void stop() {
    synchronized (mWaiterThread) {
      mWaiterThread.mStop = true;
    }
  }

  private class WaiterThread extends Thread {
    public boolean mStop;

    public void run() {
      byte[] buf = new byte[mEndpoint.getMaxPacketSize()];
      while (true) {
        synchronized (this) {
          if (mStop) {
            Log.d("synth", "midi USB waiter thread shutting down");
            return;
          }
        }
        final int TIMEOUT = 1000;
        int nBytes = mDeviceConnection.bulkTransfer(mEndpoint, buf, buf.length, TIMEOUT);
        if (nBytes < 0) {
          Log.e("synth", "bulkTransfer error " + nBytes);
          //  break;
        }
        for (int i = 0; i < nBytes; i += 4) {
          int codeIndexNumber = buf[i] & 0xf;
          int payloadBytes = 0;
          if (codeIndexNumber == 8 || codeIndexNumber == 9 || codeIndexNumber == 11 ||
                  codeIndexNumber == 14) {
            payloadBytes = 3;
          } else if (codeIndexNumber == 12) {
            payloadBytes = 2;
          }
          if (payloadBytes > 0) {
            byte[] newBuf = new byte[payloadBytes];
            System.arraycopy(buf, i + 1, newBuf, 0, payloadBytes);
            Log.d("synth", "sending midi");
            mActivity.sendMidiBytes(newBuf);
          }
        }
      }
    }
  }
}
