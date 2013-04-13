/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gcm.demo.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main user interface for the Sample application.  All Bluetooth health-related
 * operations happen in {@link BluetoothHDPService}.  This activity passes messages to and from
 * the service.
 */
public class BluetoothHDPActivity extends Activity {
    private static final String TAG = "bp";
    private static boolean isDebug = false;
    // Use the appropriate IEEE 11073 data types based on the devices used.
    // Below are some examples.  Refer to relevant Bluetooth HDP specifications for detail.
    //     0x1007 - blood pressure meter
    //     0x1008 - body thermometer
    //     0x100F - body weight scale
    private static final int HEALTH_PROFILE_SOURCE_DATA_TYPE = 0x1007;

    private static final int REQUEST_ENABLE_BT = 1;

    private TextView mConnectIndicator;
    private ImageView mDataIndicator;
    private TextView mStatusMessage;
    private TextView mResultMessage;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice[] mAllBondedDevices;
    private BluetoothDevice mDevice;
    private int mDeviceIndex = 0;
    private Resources mRes;
    private Messenger mHealthService;
    private boolean mHealthServiceBound;

    // Handles events sent by {@link HealthHDPService}.
    private Handler mIncomingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // Application registration complete.
                case BluetoothHDPService.STATUS_HEALTH_APP_REG:
                    mStatusMessage.setText(
                            String.format(mRes.getString(R.string.status_reg),
                            msg.arg1));
                    //mResultMessage.setText(BluetoothHDPService.result);
                    break;
                // Application unregistration complete.
                case BluetoothHDPService.STATUS_HEALTH_APP_UNREG:
                    mStatusMessage.setText(
                            String.format(mRes.getString(R.string.status_unreg),
                            msg.arg1));
                    break;
                // Reading data from HDP device.
                case BluetoothHDPService.STATUS_READ_DATA:
                    mStatusMessage.setText(mRes.getString(R.string.read_data));
                    mDataIndicator.setImageLevel(1);
                    break;
                // Finish reading data from HDP device.
                case BluetoothHDPService.STATUS_READ_DATA_DONE:
                    mStatusMessage.setText(mRes.getString(R.string.read_data_done));
                    mDataIndicator.setImageLevel(0);
                    break;
                // Channel creation complete.  Some devices will automatically establish
                // connection.
                case BluetoothHDPService.STATUS_CREATE_CHANNEL:
                    mStatusMessage.setText(
                            String.format(mRes.getString(R.string.status_create_channel),
                            msg.arg1));
                    mConnectIndicator.setText(R.string.connected);
                    break;
                // Channel destroy complete.  This happens when either the device disconnects or
                // there is extended inactivity.
                case BluetoothHDPService.STATUS_DESTROY_CHANNEL:
                    mStatusMessage.setText(
                            String.format(mRes.getString(R.string.status_destroy_channel),
                            msg.arg1));
                    mConnectIndicator.setText(R.string.disconnected);
                    break;
                case BluetoothHDPService.SHOW_RESULT:
                	//show result in UI: (textView)mResultMessage
                	String prev = mResultMessage.getText().toString();
                	//mResultMessage.setText(prev+"\n"+BluetoothHDPService.result); //append results
                	mResultMessage.setText("SYSTOLIC:  "+ BluetoothHDPService.results[0]
                			+"\nDIASTOLIC: "+BluetoothHDPService.results[1]
                					+"\nPULSE:     "+BluetoothHDPService.results[2]);
                	sendToHub();
                	break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private final Messenger mMessenger = new Messenger(mIncomingHandler);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check for Bluetooth availability on the Android platform.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_available, Toast.LENGTH_LONG);
            finish();
            return;
        }
        setContentView(R.layout.console);
        mConnectIndicator = (TextView) findViewById(R.id.connect_ind);
        mStatusMessage = (TextView) findViewById(R.id.status_msg);
        mResultMessage =(TextView)findViewById(R.id.result_msg);
        mDataIndicator = (ImageView) findViewById(R.id.data_ind);
        mRes = getResources();
        mHealthServiceBound = false;

        // Initiates application registration through {@link BluetoothHDPService}.
        Button registerAppButton = (Button) findViewById(R.id.button_register_app);
        registerAppButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMessage(BluetoothHDPService.MSG_REG_HEALTH_APP,
                        HEALTH_PROFILE_SOURCE_DATA_TYPE);
                Log.d(TAG, "register button pressed");
            }
        });

        // Initiates application unregistration through {@link BluetoothHDPService}.
        Button unregisterAppButton = (Button) findViewById(R.id.button_unregister_app);
        unregisterAppButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendMessage(BluetoothHDPService.MSG_UNREG_HEALTH_APP, 0);
                Log.d(TAG, "unregister button pressed");
            }
        });

        // Initiates channel creation through {@link BluetoothHDPService}.  Some devices will
        // initiate the channel connection, in which case, it is not necessary to do this in the
        // application.  When pressed, the user is asked to select from one of the bonded devices
        // to connect to.
        Button connectButton = (Button) findViewById(R.id.button_connect_channel);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	Log.d(TAG, "clickConnect1");
                mAllBondedDevices =
                        (BluetoothDevice[]) mBluetoothAdapter.getBondedDevices().toArray(
                                new BluetoothDevice[0]);
                Log.d(TAG, "clickConnect2");
                if (mAllBondedDevices.length > 0) {
                    int deviceCount = mAllBondedDevices.length;
                    if (mDeviceIndex < deviceCount) mDevice = mAllBondedDevices[mDeviceIndex];
                    else {
                        mDeviceIndex = 0;
                        mDevice = mAllBondedDevices[0];
                    }
                    String[] deviceNames = new String[deviceCount];
                    int i = 0;
                    for (BluetoothDevice device : mAllBondedDevices) {
                        deviceNames[i++] = device.getName();
                    }
                    SelectDeviceDialogFragment deviceDialog =
                            SelectDeviceDialogFragment.newInstance(deviceNames, mDeviceIndex);
                    deviceDialog.show(getFragmentManager(), "deviceDialog");
                    //sendMessage(BluetoothHDPService.MSG_CONNECT_CHANNEL, 0); //#########
                    Log.d(TAG, "connect channel #####");
                }
            }
        });

        // Initiates channel disconnect through {@link BluetoothHDPService}.
        Button disconnectButton = (Button) findViewById(R.id.button_disconnect_channel);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                disconnectChannel();
            }
        });
        registerReceiver(mReceiver, initIntentFilter());
    }

    // Sets up communication with {@link BluetoothHDPService}.
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
        	Log.d(TAG, "HDP service connected");
            mHealthServiceBound = true;
            Message msg = Message.obtain(null, BluetoothHDPService.MSG_REG_CLIENT);
            msg.replyTo = mMessenger;
            mHealthService = new Messenger(service);
            try {
                mHealthService.send(msg);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to register client to service.");
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mHealthService = null;
            mHealthServiceBound = false;
        }
    };

    @Override
    protected void onDestroy() {
    	Log.d(TAG, "onDestroy()");
        super.onDestroy();
        if (mHealthServiceBound) unbindService(mConnection);
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If Bluetooth is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            initialize();
        }
    }

    /**
     * Ensures user has turned on Bluetooth on the Android device.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            if (resultCode == Activity.RESULT_OK) {
                initialize();
            } else {
                finish();
                return;
            }
        }
    }

    /**
     * Used by {@link SelectDeviceDialogFragment} to record the bonded Bluetooth device selected
     * by the user.
     *
     * @param position Position of the bonded Bluetooth device in the array.
     */
    public void setDevice(int position) {
        mDevice = this.mAllBondedDevices[position];
        mDeviceIndex = position;
    }

    private void connectChannel() {
        sendMessageWithDevice(BluetoothHDPService.MSG_CONNECT_CHANNEL);
    }

    private void disconnectChannel() {
        sendMessageWithDevice(BluetoothHDPService.MSG_DISCONNECT_CHANNEL);
    }

    private void initialize() {
        // Starts health service.
        Intent intent = new Intent(this, BluetoothHDPService.class);
        startService(intent);
        getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    // Intent filter and broadcast receive to handle Bluetooth on event.
    private IntentFilter initIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ==
                    BluetoothAdapter.STATE_ON) {
                    initialize();
                }
            }
        }
    };

    // Sends a message to {@link BluetoothHDPService}.
    private void sendMessage(int what, int value) {
        if (mHealthService == null) {
            Log.d(TAG, "Health Service not connected.");
            return;
        }

        try {
            mHealthService.send(Message.obtain(null, what, value, 0));
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach service.");
            e.printStackTrace();
        }
    }

    // Sends an update message, along with an HDP BluetoothDevice object, to
    // {@link BluetoothHDPService}.  The BluetoothDevice object is needed by the channel creation
    // method.
    private void sendMessageWithDevice(int what) {
        if (mHealthService == null) {
            Log.d(TAG, "Health Service not connected.");
            return;
        }

        try {
            mHealthService.send(Message.obtain(null, what, mDevice));
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach service.");
            e.printStackTrace();
        }
    }
    
    //function to send data to bp hub
    public void sendToHub(){
    	Log.d(TAG, "send to bp hub after new measurement.");
    	
    	(new SendDataAsycTask()).execute();
    }
    /*send function for send button
     * if in debugging-> send dummy data to MongoDB, else-> clear displayed BP results
     */
    public void send(View view){
    	mResultMessage.setText(""); // clear displayed results
    	if(isDebug){
	    	Log.d(TAG, "send dummy data to bp hub.");
	    	for(int i=0;i<8;i++)
	    		BluetoothHDPService.results[i] = i+"";
	    	
	    	(new SendDataAsycTask()).execute();
    	}
    }
    class SendDataAsycTask extends AsyncTask<Void,Void,String>{

		@Override
		protected String doInBackground(Void... arg0) {
			HttpClient httpclient = new DefaultHttpClient();
	    	HttpPost httppost = new HttpPost("https://api.mongolab.com/api/1/databases/bp/collections/measurement?apiKey=fEVne_u88CbaAX6Rv2YsnCdWIfKD5JP-");
	    	httppost.addHeader("Content-Type","application/json");
	    	//httppost.addHeader("url", "https://api.mongolab.com/api/1/databases/bp/collections/measurement?apiKey=fEVne_u88CbaAX6Rv2YsnCdWIfKD5JP-");
	    	//httppost.addHeader("type", "POST");
	    	
	    	try {
	    	    // Add your data
	    		JSONObject object = new JSONObject();
	    		  try {
	    			  object.put("systolic", BluetoothHDPService.results[0]);
	    			  object.put("diastolic", BluetoothHDPService.results[1]);
	    			  object.put("pulse", BluetoothHDPService.results[2]);
	    			  object.put("year", BluetoothHDPService.results[3]);
	    			  object.put("month", BluetoothHDPService.results[4]);
	    			  object.put("day", BluetoothHDPService.results[5]);
	    			  object.put("hour", BluetoothHDPService.results[6]);
	    			  object.put("minute", BluetoothHDPService.results[7]);
	    		  } catch (JSONException e) {
	    			  Log.d(TAG, e.getMessage());
	    		  }
	    		  
	    		  StringEntity s = new StringEntity(object.toString());
	    		  //s.setContentEncoding((Header)(new BasicHeader(HTTP.CONTENT_TYPE, "application/json")));
	    		/*
	    	    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(8);
	    	    nameValuePairs.add(new BasicNameValuePair("systolic", "1"));
	    	    nameValuePairs.add(new BasicNameValuePair("diastolic", "2"));
	    	    nameValuePairs.add(new BasicNameValuePair("pulse", "3"));
	    	    nameValuePairs.add(new BasicNameValuePair("year", "4"));
	    	    nameValuePairs.add(new BasicNameValuePair("month", "5"));
	    	    nameValuePairs.add(new BasicNameValuePair("day", "6"));
	    	    nameValuePairs.add(new BasicNameValuePair("hour", "7"));
	    	    nameValuePairs.add(new BasicNameValuePair("minute", "8"));
	    	    
	    	    httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
	    	    */
	    	    httppost.setEntity(s);

	    	    // Execute HTTP Post Request
	    	    HttpResponse response = httpclient.execute(httppost);
	    	    //logging down the response from Mongo Lab
	    	    byte[] buffer = new byte[400];
	    	    response.getEntity().getContent().read(buffer);
	    	    String str = new String(buffer, "UTF8");
	    	    Log.d(TAG,str);
				httpclient.getConnectionManager().shutdown();

	    	} catch (ClientProtocolException e) {
	    		Log.d(TAG, e.getMessage());
	    	} catch (IOException e) {
	    		Log.d(TAG, e.getMessage());
	    	}
			
			return null;
		}
		
		protected void onPostExecute(String result) {
	        Log.d(TAG, "onPostExecute : "+result);
	     }
    	
    }

    /**
     * Dialog to display a list of bonded Bluetooth devices for user to select from.  This is
     * needed only for channel connection initiated from the application.
     */
    public static class SelectDeviceDialogFragment extends DialogFragment {

        public static SelectDeviceDialogFragment newInstance(String[] names, int position) {
            SelectDeviceDialogFragment frag = new SelectDeviceDialogFragment();
            Bundle args = new Bundle();
            args.putStringArray("names", names);
            args.putInt("position", position);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String[] deviceNames = getArguments().getStringArray("names");
            int position = getArguments().getInt("position", -1);
            if (position == -1) position = 0;
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.select_device)
                    .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ((BluetoothHDPActivity) getActivity()).connectChannel();
                            }
                        })
                    .setSingleChoiceItems(deviceNames, position,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ((BluetoothHDPActivity) getActivity()).setDevice(which);
                            }
                        }
                    )
                    .create();
        }
    }
}
